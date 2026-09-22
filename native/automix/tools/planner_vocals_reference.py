#!/usr/bin/env python3
"""Original ARM vocal overlap, strength aggregation, leading-vocal significance.
Swift allocation/refcount helpers are hooked; arithmetic executes original code.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import random
import struct
from capstone import Cs, CS_ARCH_ARM64, CS_MODE_LITTLE_ENDIAN
from unicorn import Uc, UC_ARCH_ARM64, UC_MODE_ARM, UC_HOOK_CODE
from unicorn.arm64_const import *


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('macho',type=Path);p.add_argument('output_dir',type=Path)
    p.add_argument('evidence_dir',type=Path);args=p.parse_args()
    args.output_dir.mkdir(parents=True,exist_ok=True);args.evidence_dir.mkdir(parents=True,exist_ok=True)
    binary=args.macho.read_bytes();u=Uc(UC_ARCH_ARM64,UC_MODE_ARM);offset=32
    for _ in range(struct.unpack_from('<I',binary,16)[0]):
        command,size=struct.unpack_from('<II',binary,offset)
        if command==25 and binary[offset+8:offset+24].rstrip(b'\0')==b'__TEXT':
            vm,vs,fo,fs=struct.unpack_from('<QQQQ',binary,offset+24)
            u.mem_map(vm,(vs+4095)&~4095);u.mem_write(vm,binary[fo:fo+fs])
        offset+=size
    hashes={}
    for first,last in [(0x27221f218,0x27221f3e8),(0x27221f4e4,0x27221f524),
                       (0x272235198,0x27223550c)]:
        raw=bytes(u.mem_read(first,last-first));hashes[hex(first)]=hashlib.sha256(raw).hexdigest()
        lines=[f'; Original 23A341, SHA256 {hashes[hex(first)]}']
        for ins in Cs(CS_ARCH_ARM64,CS_MODE_LITTLE_ENDIAN).disasm(raw,first):
            lines.append(f'{ins.address:x}: {ins.bytes.hex()} {ins.mnemonic} {ins.op_str}')
            if ins.mnemonic=='pacibsp':u.mem_write(ins.address,bytes.fromhex('1f2003d5'))
            elif ins.mnemonic=='retab':u.mem_write(ins.address,bytes.fromhex('c0035fd6'))
        (args.evidence_dir/f'{first:x}.asm').write_text('\n'.join(lines)+'\n')
    for address,size in [(0x2743dd000,4096),(0x2780e3000,4096),(0x280c99000,4096),(0x100000,0x100000)]:u.mem_map(address,size)
    u.mem_write(0x2780e3c50,struct.pack('<Q',0x100000));heap=0x140000
    u.mem_write(0x280c99320,struct.pack('<Q',2**64-1))
    active_window=(0.,1.);window_present=True
    def r64(address):return struct.unpack('<Q',u.mem_read(address,8))[0]
    def hook(uc,address,size,data):
        nonlocal heap
        if address in (0x2722634f8,0x2722634c8):
            pointer=uc.reg_read(UC_ARM64_REG_X20);old=r64(pointer)
            stride=24 if address==0x2722634f8 else 1
            count=r64(old+16);assert count<128
            u.mem_write(heap,bytes(u.mem_read(old,32+count*stride)))
            u.mem_write(heap+24,struct.pack('<Q',256));u.mem_write(pointer,struct.pack('<Q',heap));heap+=0x1000
        elif address==0x2743dde70:uc.reg_write(UC_ARM64_REG_X0,1)
        elif address in (0x2743ddce0,0x2743ddea0,0x2743ddb90):pass
        elif address in (0x2743dd380,0x272213280,0x2743dd360,0x2743dd660,0x2743ddc40):
            uc.reg_write(UC_ARM64_REG_X0,0)
        elif address==0x272237058:
            u.mem_write(uc.reg_read(UC_ARM64_REG_X8),struct.pack('<ddB',*active_window,not window_present))
        else:return
        uc.reg_write(UC_ARM64_REG_PC,uc.reg_read(UC_ARM64_REG_LR))
    u.hook_add(UC_HOOK_CODE,hook)
    def call(address):
        nonlocal heap
        heap=0x140000
        u.reg_write(UC_ARM64_REG_SP,0x1e0000);u.reg_write(UC_ARM64_REG_LR,0x1f0000)
        u.emu_start(address,0x1f0000,count=100000)
        assert u.reg_read(UC_ARM64_REG_PC)==0x1f0000
        return u.reg_read(UC_ARM64_REG_X0)&255
    rng=random.Random(31);cases=[]
    for strength in range(5):
        for kind in range(3):
            for a,b in [(0.,0.),(1.,1.),(0.,1.),(-1.,0.),(1.,2.),
                        (math.nextafter(1.,math.inf),2.),(-2.,-1.)]:
                cases.append(([(a,b,kind,strength)],(0.,1.)))
    cases.append(([],(0.,1.)))
    for _ in range(150):
        rows=[]
        for _ in range(rng.randrange(20)):
            a,b=sorted([rng.uniform(-3,3),rng.uniform(-3,3)])
            rows.append((a,b,rng.randrange(3),rng.randrange(5)))
        cases.append((rows,tuple(sorted([rng.uniform(-3,3),rng.uniform(-3,3)]))))
    records=[]
    for rows,active_window in cases:
        raw=b''.join(struct.pack('<ddBB6x',*r) for r in rows)
        u.mem_write(0x110000,struct.pack('<QQQQ',0,0,len(rows),len(rows)*2)+raw)
        u.mem_write(0x120000,struct.pack('<dd',*active_window))
        u.reg_write(UC_ARM64_REG_X0,0x120000);u.reg_write(UC_ARM64_REG_X1,0x110000)
        strength=call(0x27221f218);assert strength<=5
        results=[]
        for map_present,window_present in [(True,True),(True,False),(False,True)]:
            u.reg_write(UC_ARM64_REG_X2,0x110000 if map_present else 0)
            results.append(call(0x272235198))
        records.append(struct.pack('<IddIIII',len(rows),*active_window,strength,*results)+raw)
    (args.output_dir/'planner_vocals.bin').write_bytes(struct.pack('<I',len(records))+b''.join(records))
    manifest=dict(binary_sha256=hashlib.sha256(binary).hexdigest(),function_sha256=hashes,
                  overlap_cases=len(records),leading_predicate_cases=len(records)*3,
                  hooks=['Swift arrays/refcounts','logging disabled','caller-supplied resolved leading window'],
                  scope='Original interval filter, max strength, full leading significance predicate; excludes beat-window selection and cloud enum decoding.')
    (args.output_dir/'planner_vocals.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print(json.dumps(manifest,indent=2))
if __name__=='__main__':main()
