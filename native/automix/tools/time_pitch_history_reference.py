#!/usr/bin/env python3
"""Original 234f46810 history interpolation; arithmetic is not hooked."""
import argparse,random,struct
from pathlib import Path
from unicorn import Uc,UC_ARCH_ARM64,UC_MODE_ARM
from unicorn.arm64_const import *
p=argparse.ArgumentParser();p.add_argument('cache',type=Path);p.add_argument('output',type=Path);a=p.parse_args()
with a.cache.open('rb') as f:f.seek(0x4dfc000+0x42000);code=f.read(4096)
u=Uc(UC_ARCH_ARM64,UC_MODE_ARM);u.mem_map(0x234f46000,4096);u.mem_write(0x234f46000,code);u.mem_map(0x100000,65536)
rng=random.Random(46810);records=[]
for case in range(256):
 n=rng.choice([256,512,1024]);hop=float(round(n/rng.choice([3,8,17,32])))
 index=rng.randrange(0,1000);base=rng.randrange(-3000,3000)
 sourceAnchor=rng.uniform(-2000,2000);inputAnchor=rng.randrange(-500,500)
 outputAnchor=rng.uniform(-2000,2000);outputFrame=rng.randrange(-500,500)
 query=outputAnchor+base-outputFrame+rng.choice([-.5,0,.5,1.125])
 window=[rng.random() if case%7 else 0.0 for _ in range(n)]
 history=[]
 for i in range(64):
  age=(index-i)&63
  history.append((int(case%11!=0 and age<40),0.0,base-int(age*hop*1.25),base-int(age*hop)))
 def w(off,fmt,*v):u.mem_write(0x100000+off,struct.pack('<'+fmt,*v))
 w(0x890,'I',n);w(0x908,'d',hop);w(0x40,'d',outputAnchor);w(0x48,'q',outputFrame)
 w(0x20,'d',sourceAnchor);w(0x28,'q',inputAnchor);w(0x858,'I',index);w(0xa48,'Q',0x104000)
 for i,(active,time,source,out) in enumerate(history):w(0x58+i*32,'B7xdqq',active,time,source,out)
 data=struct.pack('<'+'f'*n,*window);u.mem_write(0x104000,data)
 u.reg_write(UC_ARM64_REG_X0,0x100000);u.reg_write(UC_ARM64_REG_D0,struct.unpack('<Q',struct.pack('<d',query))[0])
 u.emu_start(0x234f46810,0x234f468ec,count=10000);assert u.reg_read(UC_ARM64_REG_PC)==0x234f468ec
 records.append(struct.pack('<IdIdqdqd',n,hop,index,sourceAnchor,inputAnchor,outputAnchor,outputFrame,query)+
   b''.join(struct.pack('<Bqq',active,source,out) for active,_,source,out in history)+data+struct.pack('<Q',u.reg_read(UC_ARM64_REG_D0)))
a.output.write_bytes(struct.pack('<I',len(records))+b''.join(records));print(len(records),'original history queries captured')
