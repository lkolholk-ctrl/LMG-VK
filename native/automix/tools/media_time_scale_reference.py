#!/usr/bin/env python3
"""Original CMTimeConvertScale(method=1), including its integer helpers."""
import argparse,random,struct
from pathlib import Path
from capstone import Cs,CS_ARCH_ARM64,CS_MODE_LITTLE_ENDIAN
from unicorn import Uc,UC_ARCH_ARM64,UC_MODE_ARM
from unicorn.arm64_const import *
p=argparse.ArgumentParser();p.add_argument('cache',type=Path);p.add_argument('output',type=Path);a=p.parse_args()
base,size=0x196bc2000,0x1eb040
with a.cache.open('rb') as f:f.seek(0x3456000);code=f.read(size)
assert code[:4]==bytes.fromhex('cffaedfe')
u=Uc(UC_ARCH_ARM64,UC_MODE_ARM);u.mem_map(base,(size+4095)&~4095);u.mem_write(base,code)
u.mem_map(0x100000,0x20000)
for begin,end in [(0x196bf7a0c,0x196bf7ba4),(0x196bc50c4,0x196bc51fc)]:
    for i in Cs(CS_ARCH_ARM64,CS_MODE_LITTLE_ENDIAN).disasm(bytes(u.mem_read(begin,end-begin)),begin):
        if i.mnemonic=='pacibsp':u.mem_write(i.address,bytes.fromhex('1f2003d5'))
        elif i.mnemonic=='retab':u.mem_write(i.address,bytes.fromhex('c0035fd6'))
rng=random.Random(7);cases=[]
scales=[1,2,3,48000,44100,1000000000,2147483647]
for source in scales:
    for target in scales:
        for value in [0,1,-1,source//2,-(source//2),source+1,-source-1,(1<<63)-1,-(1<<63),-(1<<63)+1]:
            cases.append((value,source,rng.choice([1,3]),rng.randrange(-3,4),target))
for _ in range(512):cases.append((rng.randrange(-(1<<62),1<<62),rng.choice(scales),1,0,rng.choice(scales)))
records=[]
for value,source,flags,epoch,target in cases:
    raw=struct.pack('<qiIq',value,source,flags,epoch);u.mem_write(0x100000,raw)
    for reg,val in [(UC_ARM64_REG_X0,0x100000),(UC_ARM64_REG_X1,target),(UC_ARM64_REG_X2,1),
                    (UC_ARM64_REG_X8,0x100100),(UC_ARM64_REG_SP,0x11e000),(UC_ARM64_REG_LR,0x11f000)]:u.reg_write(reg,val)
    u.emu_start(0x196bf7a0c,0x11f000,count=10000);assert u.reg_read(UC_ARM64_REG_PC)==0x11f000
    records.append(raw+struct.pack('<i',target)+bytes(u.mem_read(0x100100,24)))
a.output.write_bytes(struct.pack('<I',len(records))+b''.join(records))
print(f'{len(records)} original timescale conversions captured')
