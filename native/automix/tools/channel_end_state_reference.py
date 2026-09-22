#!/usr/bin/env python3
"""Capture original EOS stage selection/update without logging or stop notification."""
import argparse,struct
from pathlib import Path
from unicorn import Uc,UC_ARCH_ARM64,UC_MODE_ARM,UC_HOOK_CODE
from unicorn.arm64_const import *
p=argparse.ArgumentParser();p.add_argument('image',type=Path);p.add_argument('output',type=Path);a=p.parse_args()
with a.image.open('rb') as f:h=f.read(65536)
o=32;maps=[]
for _ in range(struct.unpack_from('<I',h,16)[0]):
 c,s=struct.unpack_from('<II',h,o)
 if c==25:maps.append(struct.unpack_from('<QQQQ',h,o+24))
 o+=s
vm,_,fo,_=next(m for m in maps if m[0]<=0x1b8f63000<m[0]+m[3])
with a.image.open('rb') as f:f.seek(fo+0x1b8f63000-vm);code=f.read(4096)
u=Uc(UC_ARCH_ARM64,UC_MODE_ARM);u.mem_map(0x1b8f63000,4096);u.mem_write(0x1b8f63000,code);u.mem_map(0x100000,4096)
u.hook_add(UC_HOOK_CODE,lambda u,a,s,d:u.emu_stop(),begin=0x1b8f637d8,end=0x1b8f637d8)
records=[]
for graph in range(2):
 for upstream in range(2):
  for pitch in range(2):
   for old in range(5):
    for requested in range(1,5):
     for offset,fmt,value in [(0x330,'Q',graph),(0x320,'B',upstream),(0x570,'Q',pitch),(0x538,'i',old)]:
      u.mem_write(0x100000+offset,struct.pack('<'+fmt,value))
     u.reg_write(UC_ARM64_REG_X20,0x100000);u.reg_write(UC_ARM64_REG_W1,requested)
     u.emu_start(0x1b8f635ec,0x1b8f63660,count=200)
     # Stop before the store when advancing, or at the exit if it did not advance.
     result=u.reg_read(UC_ARM64_REG_W21) if u.reg_read(UC_ARM64_REG_PC)==0x1b8f63660 else old
     records.append(struct.pack('<6i',graph,upstream,pitch,old,requested,result))
a.output.write_bytes(struct.pack('<I',len(records))+b''.join(records));print(len(records),'original EOS transitions')
