#!/usr/bin/env python3
"""Execute original tonality relationship and table lookup; PAC bypass only."""
import argparse,struct
from pathlib import Path
from unicorn import Uc,UC_ARCH_ARM64,UC_MODE_ARM
from unicorn.arm64_const import *
p=argparse.ArgumentParser();p.add_argument('image',type=Path);p.add_argument('output',type=Path);a=p.parse_args()
f=a.image.open('rb');h=f.read(65536);o=32;maps=[]
for _ in range(struct.unpack_from('<I',h,16)[0]):
 c,s=struct.unpack_from('<II',h,o)
 if c==25:maps.append(struct.unpack_from('<QQQQ',h,o+24))
 o+=s
def read(addr,size):
 vm,_,fo,_=next(m for m in maps if m[0]<=addr and addr+size<=m[0]+m[3]);f.seek(fo+addr-vm);return f.read(size)
u=Uc(UC_ARCH_ARM64,UC_MODE_ARM)
for addr in [0x27221e000,0x2884aa000]:u.mem_map(addr,4096);u.mem_write(addr,read(addr,4096))
u.mem_write(0x27221eb58,struct.pack('<I',0xd503201f));u.mem_map(0x100000,8192)
records=[]
for at in range(14):
 for am in range(3):
  for bt in range(14):
   for bm in range(3):
    u.reg_write(UC_ARM64_REG_X0,at|(am<<8));u.reg_write(UC_ARM64_REG_X1,bt|(bm<<8));u.reg_write(UC_ARM64_REG_SP,0x101000)
    u.emu_start(0x27221eb58,0x27221ec28,count=2000);assert u.reg_read(UC_ARM64_REG_PC)==0x27221ec28
    records.append(bytes([at,am,bt,bm,u.reg_read(UC_ARM64_REG_W0)]))
a.output.write_bytes(struct.pack('<I',len(records))+b''.join(records));print(len(records),'original tonality pairs')
