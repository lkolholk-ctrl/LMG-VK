#!/usr/bin/env python3
"""Original synthesis/remapping/overlap-add plus original vDSP arithmetic.
Import routing and memory clearing only; no replacement DSP math.
"""
import argparse,hashlib,json,random,struct
from pathlib import Path
from capstone import Cs,CS_ARCH_ARM64,CS_MODE_LITTLE_ENDIAN
from unicorn import Uc,UC_ARCH_ARM64,UC_MODE_ARM,UC_HOOK_CODE
from unicorn.arm64_const import *
BASE,OBJ,DATA,STACK,STOP=0x234f04000,0x100000,0x200000,0x3fe000,0x3ff000

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('cache_dir',type=Path);p.add_argument('output_dir',type=Path);a=p.parse_args();a.output_dir.mkdir(parents=True,exist_ok=True)
 u=Uc(UC_ARCH_ARM64,UC_MODE_ARM)
 with (a.cache_dir/'dyld_shared_cache_arm64e.48').open('rb') as f:
  f.seek(0x4dfc000);code=f.read(0x120000);u.mem_map(BASE,0x120000);u.mem_write(BASE,code)
  f.seek(16);o,n=struct.unpack('<II',f.read(8));f.seek(o);maps=[struct.unpack('<QQQII',f.read(32)) for _ in range(n)]
  for page,size in [(0x234ad1000,0x1000),(0x234ade000,0x2000)]:
   vm,sz,off,*_=next(m for m in maps if m[0]<=page<m[0]+m[1]);f.seek(off+page-vm);u.mem_map(page,size);u.mem_write(page,f.read(size))
 u.mem_map(OBJ,0x300000);u.mem_map(0x236f45000,0x2000)
 decoder=Cs(CS_ARCH_ARM64,CS_MODE_LITTLE_ENDIAN)
 for begin,end in [(0x234f45224,0x234f4570c),(0x234ad1570,0x234ad1838)]:
  for ins in decoder.disasm(bytes(u.mem_read(begin,end-begin)),begin):
   if ins.mnemonic in ['pacibsp','autibsp']:u.mem_write(ins.address,bytes.fromhex('1f2003d5'))
   elif ins.mnemonic=='retab':u.mem_write(ins.address,bytes.fromhex('c0035fd6'))
 def hook(uc,addr,size,unused):
  if addr==0x236f46040:u.reg_write(UC_ARM64_REG_PC,0x234ad14a8)
  elif addr==0x236f45f00:u.reg_write(UC_ARM64_REG_PC,0x234adecf0)
  elif addr==0x236f45670:
   u.mem_write(u.reg_read(UC_ARM64_REG_X0),bytes(u.reg_read(UC_ARM64_REG_X1)));u.reg_write(UC_ARM64_REG_PC,u.reg_read(UC_ARM64_REG_LR))
 u.hook_add(UC_HOOK_CODE,hook,begin=0x236f45000,end=0x236f47000)
 def write(off,fmt,*v):u.mem_write(OBJ+off,struct.pack('<'+fmt,*v))
 def read(off,fmt):return struct.unpack('<'+fmt,u.mem_read(OBJ+off,struct.calcsize('<'+fmt)))
 def packed(values):return struct.pack(f'<{len(values)}f',*values)
 def run(start):
  u.reg_write(UC_ARM64_REG_X0,OBJ);u.reg_write(UC_ARM64_REG_X1,0);u.reg_write(UC_ARM64_REG_SP,STACK);u.reg_write(UC_ARM64_REG_LR,STOP);u.emu_start(start,STOP,count=2000000);assert u.reg_read(UC_ARM64_REG_PC)==STOP
 rng=random.Random(23445224);synthesis=[];remaps=[];olas=[];mappings=[]
 pointers=[DATA+i*0x10000 for i in range(9)]
 for bins in [128,256,512,2048]:
  for output in [16.,128.,511.]:
   for update in [0,1]:
    arrays=[[rng.uniform(-10,10) for _ in range(bins)] for _ in range(7)]
    for ptr,values in zip(pointers,arrays):u.mem_write(ptr,packed(values))
    write(0x894,'I',bins);write(0x908,'d',output);write(0x8a0,'f',1/(bins*2));write(0x9a6,'B',1)
    write(0xa50,'Q',OBJ+0x2000);write(0x2018,'QQ',pointers[2],pointers[4]);write(0xa20,'Q',pointers[3]);write(0x9f0,'QQ',pointers[0],pointers[1]);write(0xa10,'QQ',pointers[5],pointers[6]);u.reg_write(UC_ARM64_REG_X2,update)
    run(0x234f45224)
    synthesis.append(struct.pack('<IIdf',bins,update,output,1/(bins*2))+b''.join(packed(v) for v in arrays)+bytes(u.mem_read(OBJ+0x9a8,8))+b''.join(bytes(u.mem_read(pointers[i],bins*4)) for i in [0,1,4,5,6]))
  for pitch in [.03125,.75,1.,1.3,32.]:
   write(0x888,'ff',pitch,-1);write(0x894,'I',bins);write(0x9e8,'Q',pointers[2]);write(0x9a6,'B',1)
   run(0x234f4608c)
   mappings.append(struct.pack('<IfI',bins,pitch,read(0x9b0,'I')[0])+bytes(u.mem_read(pointers[2],bins*4)))
  patterns=[list(range(bins)),[i//2 for i in range(bins)],[i%5 for i in range(bins)],[0]*bins,[rng.randrange(bins) for _ in range(bins)]]
  for mapping in patterns:
   real=[rng.uniform(-3,3) for _ in range(bins)];imag=[rng.uniform(-3,3) for _ in range(bins)]
   u.mem_write(pointers[0],packed(real));u.mem_write(pointers[1],packed(imag));u.mem_write(pointers[2],struct.pack(f'<{bins}I',*mapping))
   write(0x894,'I',bins);write(0x9f0,'QQ',pointers[0],pointers[1]);write(0xa00,'QQ',pointers[3],pointers[4]);write(0x9e8,'Q',pointers[2]);write(0x9a6,'B',1);write(0x9a8,'ff',.125,-.25)
   run(0x234f45464)
   remaps.append(struct.pack('<I',bins)+packed(real)+packed(imag)+struct.pack(f'<{bins}I',*mapping)+bytes(u.mem_read(pointers[3],bins*4))+bytes(u.mem_read(pointers[4],bins*4)))
 for n in [256,512,4096,8192]:
  ring_size=n*2
  for cursor in [0,1,2,3,17,31,ring_size-n+1,ring_size-1,ring_size*9+23]:
   for align in [0,1,2,3]:
    frame=[rng.uniform(-2,2) for _ in range(n)];window=[rng.random() for _ in range(n)];ring=[rng.uniform(-2,2) for _ in range(ring_size)]
    u.mem_write(pointers[0],packed(frame));u.mem_write(pointers[1],packed(window));u.mem_write(pointers[2]+align*4,packed(ring))
    write(0x968,'Q',cursor);write(0x930,'Q',ring_size-1);write(0x928,'I',ring_size);write(0x890,'I',n);write(0xa48,'Q',pointers[1]);write(0x9c8,'Q',pointers[0]);write(0xa50,'Q',OBJ+0x2000);write(0x2008,'Q',pointers[2]+align*4)
    run(0x234f45614)
    olas.append(struct.pack('<IIIQ',n,ring_size,align,cursor)+packed(frame)+packed(window)+packed(ring)+bytes(u.mem_read(pointers[2]+align*4,ring_size*4)))
 for name,records in [('time_pitch_synthesis',synthesis),('time_pitch_mapping',mappings),('time_pitch_remap',remaps),('time_pitch_overlap_add',olas)]:
  (a.output_dir/(name+'.bin')).write_bytes(struct.pack('<I',len(records))+b''.join(records))
 info={'synthesis_cases':len(synthesis),'mapping_cases':len(mappings),'remap_cases':len(remaps),'overlap_add_cases':len(olas),'vDSP_complex_multiply':'0x234ad1570','vDSP_multiply_add':'0x234adecf0','hooks':['import routing to original functions','memory zeroing','PAC entry/return removed'],'code_sha256':hashlib.sha256(code).hexdigest()}
 (a.output_dir/'time_pitch_synthesis.json').write_text(json.dumps(info,indent=2)+'\n');print(json.dumps(info))
if __name__=='__main__':main()
