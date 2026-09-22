#!/usr/bin/env python3
"""Original scalar reset and per-hop history/cursor ownership; no DSP hooks."""
import argparse,struct,random,json,hashlib
from pathlib import Path
from unicorn import Uc,UC_ARCH_ARM64,UC_MODE_ARM
from unicorn.arm64_const import *
BASE,OBJ,CTX,STACK=0x234f04000,0x100000,0x110000,0x120000
FIELDS=[(8,'q'),(16,'q'),(24,'B'),(32,'d'),(40,'q'),(48,'q'),(56,'B'),(64,'d'),(72,'q'),(80,'q'),(0x858,'I'),(0x8c0,'d'),(0x8c8,'d'),(0x8d0,'d'),(0x8d8,'d'),(0x8f0,'B'),(0x938,'d'),(0x940,'d'),(0x948,'d'),(0x950,'q'),(0x958,'q'),(0x960,'q'),(0x968,'q'),(0x970,'q'),(0x978,'q'),(0x980,'q'),(0x988,'I'),(0x9a1,'B'),(0x9a2,'B'),(0xa82,'B')]
def main():
 p=argparse.ArgumentParser();p.add_argument('cache_dir',type=Path);p.add_argument('output_dir',type=Path);a=p.parse_args()
 with (a.cache_dir/'dyld_shared_cache_arm64e.48').open('rb') as f:f.seek(0x4dfc000);code=f.read(0x120000)
 u=Uc(UC_ARCH_ARM64,UC_MODE_ARM);u.mem_map(BASE,0x120000);u.mem_write(BASE,code);u.mem_map(OBJ,0x30000)
 def w(off,fmt,*v):u.mem_write(OBJ+off,struct.pack('<'+fmt,*v))
 def run(start,end):u.emu_start(start,end,count=10000);assert u.reg_read(UC_ARM64_REG_PC)==end
 def snapshot():
  result=b''.join(bytes(u.mem_read(OBJ+off,struct.calcsize(fmt))) for off,fmt in FIELDS)
  for i in range(64):
   for off,fmt in [(0x58,'B'),(0x60,'d'),(0x68,'q'),(0x70,'q')]:result+=bytes(u.mem_read(OBJ+i*32+off,struct.calcsize(fmt)))
  return result
 rng=random.Random(46208);cases=[]
 for n in [256,1024,4096,8192]:
  for scheduled in [0,1]:
   for initial_input,initial_output in [(0.,0.),(123456.125,-8192.75)]:
    u.mem_write(OBJ,b'\0'*0xb00);w(0x890,'II',n,n//2);w(0xa81,'B',scheduled);w(0xa88,'Q',CTX)
    u.mem_write(CTX+0x28,struct.pack('<dd',initial_input,initial_output));w(0x940,'d',71.125)
    u.reg_write(UC_ARM64_REG_X0,OBJ);u.reg_write(UC_ARM64_REG_SP,STACK+0xf000)
    run(0x234f43a6c,0x234f43bd0)
    record=struct.pack('<IBdd',n,scheduled,initial_input,initial_output)+snapshot()
    for step in range(80):
     ih=rng.uniform(.125,1000);oh=rng.uniform(1,1024);ni=rng.uniform(-100,1e6);no=rng.uniform(-100,1e6)
     pull=rng.uniform(0,1e6);iw=rng.randrange(0,1000000)
     w(0x900,'dd',ih,oh);w(0x8c0,'dd',ni,no);w(0x938,'d',pull);w(0x950,'q',iw)
     u.reg_write(UC_ARM64_REG_X0,OBJ);run(0x234f46208,0x234f462a0)
     record+=struct.pack('<5dq',ih,oh,ni,no,pull,iw)+snapshot()
    cases.append(record)
 a.output_dir.mkdir(parents=True,exist_ok=True)
 (a.output_dir/'time_pitch_state.bin').write_bytes(struct.pack('<II',len(cases),80)+b''.join(cases))
 info={'reset_cases':len(cases),'commit_cases':len(cases)*80,'hooks':[],'code_sha256':hashlib.sha256(code).hexdigest()}
 (a.output_dir/'time_pitch_state.json').write_text(json.dumps(info,indent=2)+'\n');print(info)
if __name__=='__main__':main()
