#!/usr/bin/env python3
"""Execute original tempo matching and candidate score arithmetic on ARM64.

Only PAC, Swift array allocation/refcounts, and host libm log are substituted.
No Android emulator, Apple OS, or network is involved.
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
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('macho', type=Path)
    p.add_argument('output_dir', type=Path)
    p.add_argument('evidence_dir', type=Path)
    args = p.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    args.evidence_dir.mkdir(parents=True, exist_ok=True)
    binary = args.macho.read_bytes()
    u = Uc(UC_ARCH_ARM64, UC_MODE_ARM)
    offset = 32
    for _ in range(struct.unpack_from('<I', binary, 16)[0]):
        command, size = struct.unpack_from('<II', binary, offset)
        if command == 25 and binary[offset+8:offset+24].rstrip(b'\0') == b'__TEXT':
            vm, vs, fo, fs = struct.unpack_from('<QQQQ', binary, offset+24)
            u.mem_map(vm, (vs+4095)&~4095); u.mem_write(vm, binary[fo:fo+fs])
        offset += size
    hashes = {}
    for start, end in [(0x272219ff0, 0x27221a0c8), (0x27221a300, 0x27221a6a4),
                       (0x27221a79c, 0x27221a7bc), (0x27222d644, 0x27222d6a0)]:
        raw = bytes(u.mem_read(start, end-start))
        hashes[hex(start)] = hashlib.sha256(raw).hexdigest()
        lines = [f'; Original iOS 23A341, SHA256 {hashes[hex(start)]}']
        for ins in Cs(CS_ARCH_ARM64, CS_MODE_LITTLE_ENDIAN).disasm(raw, start):
            lines.append(f'{ins.address:x}: {ins.bytes.hex()} {ins.mnemonic} {ins.op_str}')
            if ins.mnemonic == 'pacibsp': u.mem_write(ins.address, bytes.fromhex('1f2003d5'))
            elif ins.mnemonic == 'retab': u.mem_write(ins.address, bytes.fromhex('c0035fd6'))
        (args.evidence_dir/f'{start:x}.asm').write_text('\n'.join(lines)+'\n')
    for address, size in [(0x2743dd000,4096), (0x2780e3000,4096),
                          (0x2884aa000,4096), (0x100000,0x100000)]:
        u.mem_map(address, size)
    # Only the three allCases bytes are needed from authenticated data.
    u.mem_write(0x2884aa458, bytes([0,1,2]))
    u.mem_write(0x2780e3c50, struct.pack('<Q',0x100000))
    heap = 0x120000
    def bits(v): return struct.unpack('<Q',struct.pack('<d',v))[0]
    def floating(v): return struct.unpack('<d',struct.pack('<Q',v))[0]
    def hook(uc, address, size, data):
        nonlocal heap
        if address in (0x272263388,0x272263368):
            # Every original array holds exactly three elements. Reserve enough
            # capacity initially, so source growth paths are not entered.
            pointer = uc.reg_read(UC_ARM64_REG_X20)
            u.mem_write(heap, bytes(256))
            u.mem_write(heap+24, struct.pack('<Q',16))
            u.mem_write(pointer, struct.pack('<Q',heap))
            heap += 0x1000
        elif address in (0x2743ddce0,0x2743ddea0): pass
        elif address == 0x2743ddb20:
            uc.reg_write(UC_ARM64_REG_D0,bits(math.log(floating(uc.reg_read(UC_ARM64_REG_D0)))))
        else: return
        uc.reg_write(UC_ARM64_REG_PC,uc.reg_read(UC_ARM64_REG_LR))
    u.hook_add(UC_HOOK_CODE,hook)
    def call(address):
        nonlocal heap
        heap = 0x120000
        u.reg_write(UC_ARM64_REG_SP,0x1e0000)
        u.reg_write(UC_ARM64_REG_LR,0x1f0000)
        u.emu_start(address,0x1f0000,count=100000)
        assert u.reg_read(UC_ARM64_REG_PC)==0x1f0000
    pairs = [(120.,60.),(120.,120.),(120.,240.),(120.,90.),(90.,120.)]
    for base in [30.,60.,120.,180.,300.]:
        for ratio in [0.25,0.5,0.75,1.,1.1,math.exp(.16),math.exp(.287),math.sqrt(2),2.,3.,4.]:
            value = base*ratio
            pairs.extend((base,v) for v in [math.nextafter(value,0.),value,math.nextafter(value,math.inf)])
    rng = random.Random(31)
    pairs += [(rng.uniform(20,300),rng.uniform(20,300)) for _ in range(100)]
    records = []
    for a,b in pairs:
        for a_tag,b_tag in [(0,0),(0,1),(1,0),(1,1)]:
            a_value = 60/a if a_tag else a
            b_value = 60/b if b_tag else b
            for tolerance in [0.,.16,.287]:
                for reg,value in [(UC_ARM64_REG_X0,bits(a_value)),(UC_ARM64_REG_X1,a_tag),
                                  (UC_ARM64_REG_X2,bits(b_value)),(UC_ARM64_REG_X3,b_tag),
                                  (UC_ARM64_REG_D0,bits(tolerance))]:u.reg_write(reg,value)
                call(0x272219ff0)
                tag = u.reg_read(UC_ARM64_REG_X0)&255
                assert tag in (0,1,2,0x80,0x81,0x82,0xfc)
                records.append(struct.pack('<dIdIdI',a_value,a_tag,b_value,b_tag,tolerance,tag))
    (args.output_dir/'planner_tempos.bin').write_bytes(struct.pack('<I',len(records))+b''.join(records))
    tempo_count=len(records)
    score_cases = [(10.,0.,[]),(15.,90000.,[0.]),(10.,8.,[1.,.75,.5,1.2]),
                   (1.,-10000.,[]),(-0.,5.,[1.]),(-2.,10000.,[1.])]
    for _ in range(300):
        score_cases.append((rng.uniform(-20,20),rng.uniform(-1e5,1e5),
                            [rng.uniform(-2,2) for _ in range(rng.randrange(8))]))
    records=[]
    for base,delta,weights in score_cases:
        u.mem_write(0x110000,bytes(32)+b''.join(struct.pack('<d',w) for w in weights))
        u.mem_write(0x110010,struct.pack('<Q',len(weights)))
        u.reg_write(UC_ARM64_REG_X0,0x110000)
        u.reg_write(UC_ARM64_REG_D0,bits(delta));u.reg_write(UC_ARM64_REG_D1,bits(base))
        call(0x27222d644)
        expected=u.reg_read(UC_ARM64_REG_D0)
        records.append(struct.pack('<ddI',base,delta,len(weights))+
                       b''.join(struct.pack('<d',w) for w in weights)+struct.pack('<Q',expected))
    (args.output_dir/'planner_scores.bin').write_bytes(struct.pack('<I',len(records))+b''.join(records))
    manifest=dict(binary_sha256=hashlib.sha256(binary).hexdigest(),function_sha256=hashes,
                  tempo_cases=tempo_count,score_cases=len(records),
                  hooks=['Swift array allocation (capacity 8)', 'Swift refcounts', 'host libm log'],
                  patched=['pacibsp->nop','retab->ret'],
                  note='Tempo source tags are representation tags, not optional-presence flags.')
    (args.output_dir/'planner_scoring.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print(json.dumps(manifest,indent=2))

if __name__=='__main__': main()
