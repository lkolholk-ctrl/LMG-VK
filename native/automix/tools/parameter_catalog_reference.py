#!/usr/bin/env python3
"""Extract parameter descriptors by running their original ARM initializers."""
import hashlib,json,re,struct,sys
from pathlib import Path
from unicorn import Uc,UC_ARCH_ARM64,UC_MODE_ARM,UC_HOOK_MEM_WRITE
from unicorn.arm64_const import *
b=Path(sys.argv[1]).read_bytes();symbols=Path(sys.argv[2]).read_text().splitlines()
u=Uc(UC_ARCH_ARM64,UC_MODE_ARM);o=32
for _ in range(struct.unpack_from('<I',b,16)[0]):
 cmd,n=struct.unpack_from('<II',b,o)
 if cmd==25 and b[o+8:o+24].rstrip(b'\0')==b'__TEXT':
  va,vs,fo,fs=struct.unpack_from('<QQQQ',b,o+24)
  u.mem_map(va,(vs+4095)&~4095);u.mem_write(va,b[fo:fo+fs])
 o+=n
u.mem_map(0x100000,0x10000);u.mem_map(0x280c99000,0x3000)
writes=[]
def written(uc,access,address,size,value,data):writes.append(address)
u.hook_add(UC_HOOK_MEM_WRITE,written,begin=0x280c99000,end=0x280c9bfff)
def string(raw):
 if raw[15]&0xe0==0xe0:return raw[:raw[15]&15].decode('ascii')
 length,pointer=struct.unpack('<QQ',raw)
 assert length>>60==0xd and pointer>>63==1,raw.hex()
 return bytes(u.mem_read((pointer&0x7fffffffffffffff)+32,length&0xffffffffffff)).decode('utf8')
rows=[];previous=0
for line in symbols:
 m=re.match(r'([0-9a-f]+) [tT] (.+)',line)
 if not m:continue
 addr=int(m[1],16);symbol=m[2]
 if 0x27226d2cc<=addr<=0x27226e558 and 'AutomationEffectParameterV' in symbol and symbol.endswith('AEvgZ'):
  writes.clear();u.reg_write(UC_ARM64_REG_SP,0x10e000);u.reg_write(UC_ARM64_REG_LR,0x10f000)
  u.emu_start(previous,0x10f000,count=1000)
  assert u.reg_read(UC_ARM64_REG_PC)==0x10f000
  target=min(writes);raw=bytes(u.mem_read(target,56));lo,hi,default=struct.unpack_from('<3d',raw,16)
  rows.append(dict(id=string(raw[:16]),minimum=lo,maximum=hi,defaultValue=default,
   styleParameterId=string(raw[40:56]),initializer=hex(previous),raw_hex=raw.hex()))
 previous=addr
print(json.dumps(dict(binary_sha256=hashlib.sha256(b).hexdigest(),hooks=[],parameters=rows),indent=2))
