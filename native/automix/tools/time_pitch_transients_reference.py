#!/usr/bin/env python3
"""Execute complete original coherence and transient functions; no DSP hooks."""
import argparse, hashlib, json, random, struct
from pathlib import Path
from unicorn import Uc, UC_ARCH_ARM64, UC_MODE_ARM, UC_HOOK_CODE
from unicorn.arm64_const import *
BASE,OBJ,DATA,STOP=0x234f04000,0x100000,0x200000,0x3ff000

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('cache_dir',type=Path);p.add_argument('output_dir',type=Path);a=p.parse_args();a.output_dir.mkdir(parents=True,exist_ok=True)
 with (a.cache_dir/'dyld_shared_cache_arm64e.48').open('rb') as f:f.seek(0x4dfc000);code=f.read(0x120000)
 u=Uc(UC_ARCH_ARM64,UC_MODE_ARM);u.mem_map(BASE,0x120000);u.mem_write(BASE,code);u.mem_map(OBJ,0x300000)
 def write(off,fmt,*v):u.mem_write(OBJ+off,struct.pack('<'+fmt,*v))
 def read(off,fmt):return struct.unpack('<'+fmt,u.mem_read(OBJ+off,struct.calcsize('<'+fmt)))
 def run(start):
  u.reg_write(UC_ARM64_REG_X0,OBJ);u.reg_write(UC_ARM64_REG_X1,0);u.reg_write(UC_ARM64_REG_LR,STOP);u.emu_start(start,STOP,count=2000000);assert u.reg_read(UC_ARM64_REG_PC)==STOP
 def f32(v):return struct.unpack('<f',struct.pack('<f',v))[0]
 def packed(v):return struct.pack(f'<{len(v)}f',*v)
 rng=random.Random(23444);coherence=[];transients=[];coverage=set()
 u.hook_add(UC_HOOK_CODE,lambda uc,addr,size,data:coverage.add(addr),begin=0x234f44d54,end=0x234f45220)
 pointers=[DATA+i*0x10000 for i in range(8)]
 for bins in [128,256]:
  shapes=[[0.]*bins,[1.]*bins,[float(i) for i in range(bins)], [float(bins-i) for i in range(bins)],
          [f32(rng.random()*5) for _ in range(bins)], [100. if i%9==4 else 1. for i in range(bins)],
          [100. if i in [2,3,4,bins-4,bins-3,bins-2] else .01 for i in range(bins)],
          [1e12 if i%8==4 else 1. for i in range(bins)]]
  for magnitude in shapes:
   for pitch in [.75,1.,1.3]:
    pitch=f32(pitch);correction=[f32(rng.uniform(-20,20)) for _ in range(bins)]
    u.mem_write(pointers[0],packed(magnitude));u.mem_write(pointers[1],packed(correction))
    write(0x894,'I',bins);write(0x9b8,'Q',pointers[0]);write(0xa20,'Q',pointers[1]);write(0x9e8,'Q',pointers[2]);write(0xa30,'QQQ',pointers[3],pointers[4],pointers[5]);write(0xa50,'Q',OBJ+0x2000);write(0x888,'f',pitch)
    run(0x234f44b58)
    count=read(0x2028,'I')[0];maximum=read(0x8e8,'I')[0]
    region_bytes=[bytes(u.mem_read(ptr,count*4)) for ptr in pointers[3:6]]
    coherence.append(struct.pack('<IfII',bins,pitch,count,maximum)+packed(magnitude)+packed(correction)+bytes(u.mem_read(pointers[1],bins*4))+bytes(u.mem_read(pointers[2],bins*4))+b''.join(region_bytes))
    if pitch!=1:continue
    starts=struct.unpack(f'<{count}I',region_bytes[1]);ends=struct.unpack(f'<{count}I',region_bytes[2])
    for mode in [0,1,2]:
     phases=[f32(rng.uniform(-.5,.5)) for _ in range(bins)] if mode==0 else [f32((i*(.4 if mode==1 else -.4)+.5)%1-.5) for i in range(bins)]
     for rate in [.15,.73,1.,1.3]:
      for position,active,debt in [(0.,0,0),(.2,1,0),(.38,1,30),(.58,1,-30),(.7,1,-500)]:
       position=f32(position);previous=round((bins*2/8)*rate);effective=previous+3.125;output=bins*2/8
       scratch=[-123.0]*bins
       u.mem_write(pointers[1],packed(correction));u.mem_write(pointers[6],packed(phases));u.mem_write(pointers[7],packed(scratch))
       write(0x2018,'Q',pointers[6]);write(0xa28,'Q',pointers[7]);write(0x890,'I',bins*2);write(0x8a8,'d',rate);write(0x898,'d',bins*2);write(0x8a0,'f',1/(bins*2));write(0x8e0,'fI',position,active);write(0x8ec,'i',debt);write(0x8f8,'ddd',previous,effective,output)
       run(0x234f44d54)
       final_pos=read(0x8e0,'f')[0];final_active=read(0x8e4,'B')[0];final_debt=read(0x8ec,'i')[0];final_hop=read(0x900,'d')[0]
       transients.append(struct.pack('<III4dfIi',bins,count,maximum,rate,output,previous,effective,position,active,debt)+b''.join(region_bytes[1:])+packed(magnitude)+packed(phases)+packed(correction)+struct.pack('<fIid',final_pos,final_active,final_debt,final_hop)+bytes(u.mem_read(pointers[1],bins*4))+bytes(u.mem_read(pointers[7],bins*4)))
 for name,records in [('time_pitch_coherence',coherence),('time_pitch_transients',transients)]:
  (a.output_dir/(name+'.bin')).write_bytes(struct.pack('<I',len(records))+b''.join(records))
 info={'coherence_cases':len(coherence),'transient_cases':len(transients),'transient_instruction_addresses_visited':len(coverage),'hooks':'read-only coverage collector; no substitutions','code_sha256':hashlib.sha256(code).hexdigest(),'visited':[hex(x) for x in sorted(coverage)]}
 (a.output_dir/'time_pitch_transients.json').write_text(json.dumps(info,indent=2)+'\n');print({k:v for k,v in info.items() if k!='visited'})
if __name__=='__main__':main()
