#!/usr/bin/env python3
"""Run original AQRateChangeHistory arithmetic; omit locking only."""
import argparse, random, struct
from pathlib import Path
from unicorn import Uc, UC_ARCH_ARM64, UC_MODE_ARM
from unicorn.arm64_const import *
p=argparse.ArgumentParser();p.add_argument('image',type=Path);p.add_argument('output',type=Path);a=p.parse_args()
with a.image.open('rb') as f:h=f.read(65536)
o=32;maps=[]
for _ in range(struct.unpack_from('<I',h,16)[0]):
    c,s=struct.unpack_from('<II',h,o)
    if c==25:maps.append(struct.unpack_from('<QQQQ',h,o+24))
    o+=s
def read(addr,n):
    vm,_,fo,_=next(m for m in maps if m[0]<=addr and addr+n<=m[0]+m[3])
    with a.image.open('rb') as f:f.seek(fo+addr-vm);return f.read(n)
def dbits(v):return struct.unpack('<Q',struct.pack('<d',v))[0]
u=Uc(UC_ARCH_ARM64,UC_MODE_ARM)
for addr in [0x1b9056000,0x1b9066000]:u.mem_map(addr,4096);u.mem_write(addr,read(addr,4096))
u.mem_map(0x100000,16384)
rng=random.Random(6524);records=[]
for case in range(1024):
    nodes=[];x=y=-1000
    for _ in range(case%17):
        x+=rng.choice([0,1,100,300]);y+=rng.choice([0,1,100,300])
        nodes.append((x,y,y+rng.choice([-.5,0,.125]),rng.choice([.03125,.75,1,1.25,32])))
    scaled=rng.uniform(-3000,6000);unscaled=rng.randrange(-3000,6000)
    if nodes and case%3==0:
        n=rng.choice(nodes);scaled=n[1]+rng.choice([-.5,0,.5]);unscaled=n[0]
    if case%19==0:scaled=rng.choice([-1e30,1e30]);unscaled=rng.choice([-(1<<63),(1<<63)-1])
    high=rng.randrange(-3000,6000)
    data=b''.join(struct.pack('<qqdd',*n) for n in nodes)
    def setup():
        u.mem_write(0x100010,struct.pack('<QQ',0x101000,0x101000+len(data)))
        u.mem_write(0x100040,struct.pack('<q',high))
        if data:u.mem_write(0x101000,data)
        u.reg_write(UC_ARM64_REG_X19,0x100000)
    setup();u.reg_write(UC_ARM64_REG_W0,0);u.reg_write(UC_ARM64_REG_D8,dbits(scaled))
    u.emu_start(0x1b9056550,0x1b90565f4,count=10000)
    assert u.reg_read(UC_ARM64_REG_PC)==0x1b90565f4
    result=struct.pack('<Q',u.reg_read(UC_ARM64_REG_X20))+bytes(u.mem_read(0x100040,8))
    setup();u.reg_write(UC_ARM64_REG_X8,0x100000);u.reg_write(UC_ARM64_REG_X21,unscaled&((1<<64)-1));u.reg_write(UC_ARM64_REG_X20,0x103000)
    u.emu_start(0x1b9066228,0x1b90662cc,count=10000)
    assert u.reg_read(UC_ARM64_REG_PC)==0x1b90662cc
    result+=struct.pack('<Q',u.reg_read(UC_ARM64_REG_D8))+bytes(u.mem_read(0x103000,8))+bytes(u.mem_read(0x100040,8))
    records.append(struct.pack('<Idqq',len(nodes),scaled,unscaled,high)+data+result)
a.output.write_bytes(struct.pack('<I',len(records))+b''.join(records))
print(f'{len(records)} pairs of original queue clock conversions captured')
