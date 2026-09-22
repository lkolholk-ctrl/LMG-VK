#!/usr/bin/env python3
"""Original ARM loudness-map timestamps, inclusive selection, ordered mean.
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
    for first,last in [(0x272226584,0x2722265c4),(0x272223e74,0x272223f1c),
                       (0x272216414,0x2722164d8),(0x272216c00,0x272216c74),
                       (0x272215af4,0x272215b6c),(0x272215bbc,0x272215c88)]:
        raw=bytes(u.mem_read(first,last-first));hashes[hex(first)]=hashlib.sha256(raw).hexdigest()
        lines=[f'; Original 23A341, SHA256 {hashes[hex(first)]}']
        for ins in Cs(CS_ARCH_ARM64,CS_MODE_LITTLE_ENDIAN).disasm(raw,first):
            lines.append(f'{ins.address:x}: {ins.bytes.hex()} {ins.mnemonic} {ins.op_str}')
            if ins.mnemonic=='pacibsp':u.mem_write(ins.address,bytes.fromhex('1f2003d5'))
            elif ins.mnemonic=='retab':u.mem_write(ins.address,bytes.fromhex('c0035fd6'))
        (args.evidence_dir/f'{first:x}.asm').write_text('\n'.join(lines)+'\n')
    for address,size in [(0x2743dd000,4096),(0x2780e3000,4096),(0x100000,0x100000)]:u.mem_map(address,size)
    u.mem_write(0x2780e3c50,struct.pack('<Q',0x100000));heap=0x140000
    def bits(v):return struct.unpack('<Q',struct.pack('<d',v))[0]
    def r64(address):return struct.unpack('<Q',u.mem_read(address,8))[0]
    def hook(uc,address,size,data):
        nonlocal heap
        if address in (0x272263348,0x2722632b8):
            pointer=uc.reg_read(UC_ARM64_REG_X20);old=r64(pointer)
            stride=16 if address==0x272263348 else 8
            count=r64(old+16);assert count<128
            u.mem_write(heap,bytes(u.mem_read(old,32+count*stride)))
            u.mem_write(heap+24,struct.pack('<Q',256));u.mem_write(pointer,struct.pack('<Q',heap));heap+=0x1000
        elif address==0x2743dde70:uc.reg_write(UC_ARM64_REG_X0,1)
        elif address==0x2743ddce0:pass
        elif address==0x272223f38:
            uc.reg_write(UC_ARM64_REG_X0,uc.reg_read(UC_ARM64_REG_X20))
            uc.reg_write(UC_ARM64_REG_PC,0x1f0000);return
        else:return
        uc.reg_write(UC_ARM64_REG_PC,uc.reg_read(UC_ARM64_REG_LR))
    u.hook_add(UC_HOOK_CODE,hook)
    def call(address,reset=True):
        nonlocal heap
        if reset:heap=0x140000
        u.reg_write(UC_ARM64_REG_SP,0x1e0000);u.reg_write(UC_ARM64_REG_X29,0x1e0100)
        u.reg_write(UC_ARM64_REG_LR,0x1f0000)
        u.emu_start(address,0x1f0000,count=100000)
        assert u.reg_read(UC_ARM64_REG_PC)==0x1f0000
    rng=random.Random(31);maps=[];builder=[]
    for count in [1,2,3,17,64]:
        for present,rate in [(1,.5),(1,2.),(1,3.),(1,7.25),(0,1.499999999999),
                             (0,1.5),(0,1.99),(0,2.49),(0,2.5),(0,3.125)]:
            values=[rng.uniform(-60,-1) for _ in range(count)];duration=count/rate
            if not present:
                u.reg_write(UC_ARM64_REG_X0,count);u.reg_write(UC_ARM64_REG_D0,bits(duration));call(0x272226584)
                assert u.reg_read(UC_ARM64_REG_X1)==0
                frequency=u.reg_read(UC_ARM64_REG_X0)
            else:frequency=bits(rate)
            raw=struct.pack('<QQQQ',0,0,count,count*2)+b''.join(struct.pack('<d',v) for v in values)
            u.mem_write(0x110000,raw);u.reg_write(UC_ARM64_REG_X22,0x110000)
            u.reg_write(UC_ARM64_REG_X23,count);u.reg_write(UC_ARM64_REG_D8,frequency)
            call(0x272223e74)
            result=u.reg_read(UC_ARM64_REG_X0);assert r64(result+16)==count
            points=bytes(u.mem_read(result+32,count*16));maps.append(points)
            builder.append(struct.pack('<IIdd',count,present,rate,duration)+
                           b''.join(struct.pack('<d',v) for v in values)+points)
    (args.output_dir/'planner_loudness_maps.bin').write_bytes(struct.pack('<I',len(builder))+b''.join(builder))
    # Deliberately unordered and cancellation-sensitive: preserve input order.
    maps.append(b''.join(struct.pack('<dd',v,t) for v,t in [(1e16,2),(-1e16,0),(1,1)]))
    records=[]
    for points in maps:
        n=len(points)//16;times=[struct.unpack_from('<d',points,i*16+8)[0] for i in range(n)]
        windows=[(-1.,1e9),(0.,0.),(times[-1],times[-1]),(1e9,1e9),
                 (0.,times[-1]),(-1.,math.nextafter(0.,-math.inf))]
        for _ in range(4):
            a,b=sorted([rng.choice(times),rng.choice(times)]);windows.append((a,b))
        for a,b in windows:
            u.mem_write(0x110000,struct.pack('<QQQQ',0,0,n,n*2)+points)
            u.mem_write(0x120000,struct.pack('<dd',a,b))
            u.reg_write(UC_ARM64_REG_X0,0x120000);u.reg_write(UC_ARM64_REG_X1,0x110000)
            call(0x272216414)
            selected=u.reg_read(UC_ARM64_REG_X0);count=r64(selected+16)
            selected_raw=bytes(u.mem_read(selected+32,count*16))
            call(0x272215af4,reset=False)
            mean=u.reg_read(UC_ARM64_REG_X0);missing=u.reg_read(UC_ARM64_REG_X1)
            records.append(struct.pack('<IddI',n,a,b,count)+points+selected_raw+struct.pack('<QI',mean,missing))
    (args.output_dir/'planner_loudness_means.bin').write_bytes(struct.pack('<I',len(records))+b''.join(records))
    manifest=dict(binary_sha256=hashlib.sha256(binary).hexdigest(),function_sha256=hashes,
                  map_cases=len(builder),window_mean_cases=len(records),hooks=['Swift arrays and refcounts'],
                  scope='Original inference, timestamp loop, closed-window filter, ordered average. No full ratio caller.')
    (args.output_dir/'planner_loudness.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print(json.dumps(manifest,indent=2))
if __name__=='__main__':main()
