#!/usr/bin/env python3
"""Original AudioToolboxUtility rate mapper; no imports or math hooks."""
import argparse,struct,random
from pathlib import Path
from unicorn import Uc,UC_ARCH_ARM64,UC_MODE_ARM
from unicorn.arm64_const import *
p=argparse.ArgumentParser();p.add_argument('cache',type=Path);p.add_argument('output',type=Path);a=p.parse_args()
base=0x1dcf70000
with a.cache.open('rb') as f:
 h=f.read(32);o,n=struct.unpack_from('<II',h,16);f.seek(o);maps=[struct.unpack('<QQQII',f.read(32)) for _ in range(n)]
 vm,sz,fo,*_=next(x for x in maps if x[0]<=base<x[0]+x[1]);f.seek(fo+base-vm);code=f.read(4096)
u=Uc(UC_ARCH_ARM64,UC_MODE_ARM);u.mem_map(base,4096);u.mem_write(base,code);u.mem_map(0x100000,4096)
def dbl(v):return struct.unpack('<Q',struct.pack('<d',v))[0]
def call(addr):u.reg_write(UC_ARM64_REG_X0,0x100000);u.reg_write(UC_ARM64_REG_LR,0x100800);u.emu_start(addr,0x100800,count=1000);assert u.reg_read(UC_ARM64_REG_PC)==0x100800
rng=random.Random(3108);records=[]
for i in range(96):
 r0=rng.uniform(.25,2);r1=rng.uniform(.25,2);x=rng.uniform(-1000,1000);y=x+(0 if i%12==0 else rng.uniform(1,50000));t=rng.uniform(-1000,1000)
 for reg,v in zip([UC_ARM64_REG_D0,UC_ARM64_REG_D1,UC_ARM64_REG_D2,UC_ARM64_REG_D3,UC_ARM64_REG_D4],[r0,r1,x,y,t]):u.reg_write(reg,dbl(v))
 call(0x1dcf70308);node=bytes(u.mem_read(0x100000,56));end=struct.unpack('<7d',node)[5]
 probes=[t-13,t,(t+end)*.5,end,end+27]
 results=[]
 for v in probes:u.reg_write(UC_ARM64_REG_D0,dbl(v));call(0x1dcf704b4);results.append(struct.pack('<Q',u.reg_read(UC_ARM64_REG_D0)))
 records.append(struct.pack('<5d',r0,r1,x,y,t)+node+struct.pack('<5d',*probes)+b''.join(results))
a.output.write_bytes(struct.pack('<I',len(records))+b''.join(records));print('96 original map constructor/evaluation cases captured')
