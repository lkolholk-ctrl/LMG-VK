#!/usr/bin/env python3
"""Bounded original ARM phase-analysis and mapped/unmapped hop arithmetic.
No libm or DSP hooks: original SIMD polynomial, rounding and FMA instructions.
"""
import argparse, hashlib, json, math, random, struct
from pathlib import Path
from unicorn import Uc, UC_ARCH_ARM64, UC_MODE_ARM
from unicorn.arm64_const import *
BASE, OBJ, DATA, STACK=0x234f04000,0x100000,0x200000,0x300000

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('cache_dir',type=Path);p.add_argument('output_dir',type=Path);a=p.parse_args();a.output_dir.mkdir(parents=True,exist_ok=True)
 with (a.cache_dir/'dyld_shared_cache_arm64e.48').open('rb') as f:f.seek(0x4dfc000);code=f.read(0x120000)
 u=Uc(UC_ARCH_ARM64,UC_MODE_ARM);u.mem_map(BASE,0x120000);u.mem_write(BASE,code);u.mem_map(OBJ,0x300000)
 def write(off,fmt,*values):u.mem_write(OBJ+off,struct.pack('<'+fmt,*values))
 def f64(reg,value):u.reg_write(reg,struct.unpack('<Q',struct.pack('<d',value))[0])
 def read(off,fmt):return struct.unpack('<'+fmt,u.mem_read(OBJ+off,struct.calcsize('<'+fmt)))
 def run(begin,end):u.emu_start(begin,end,count=2000000);assert u.reg_read(UC_ARM64_REG_PC)==end
 def f32(v):return struct.unpack('<f',struct.pack('<f',v))[0]
 rng=random.Random(234448);records=[]
 for bins in [128,256,512,2048]:
  for input_hop in [3.,31.,128.,513.125]:
   for output_hop in [16.,128.,511.]:
    for pitch in [0.75,1.,1.3]:
     pitch=f32(pitch);inv=f32(1/(bins*2))
     real=[f32(rng.uniform(-3,3)) for _ in range(bins)];imag=[f32(rng.uniform(-3,3)) for _ in range(bins)]
     axes=[(0.,0.),(-0.,0.),(0.,-0.),(-0.,-0.),(1.,0.),(-1.,0.),(0.,1.),(0.,-1.),(1.,1.),(-1.,-1.),(-1.,1.),(1.,-1.)]
     for i,(r,m) in enumerate(axes):real[i]=r;imag[i]=m
     previous=[f32(rng.uniform(-.5,.5)) for _ in range(bins)];synthesis=[f32(rng.uniform(-.5,.5)) for _ in range(bins)]
     arrays=[real,imag,previous,synthesis]
     pointers=[DATA+i*0x10000 for i in range(5)]
     for ptr,values in zip(pointers,arrays):u.mem_write(ptr,struct.pack(f'<{bins}f',*values))
     write(0x8a0,'f',inv);write(0x900,'dd',input_hop,output_hop);write(0x888,'f',pitch);write(0x894,'I',bins)
     write(0xa50,'Q',OBJ+0x2000);write(0x2018,'QQ',pointers[2],pointers[3]);write(0x9e0,'Q',DATA+0x60000);write(0x9b8,'Q',DATA+0x70000)
     write(0xa20,'Q',pointers[4]);write(0x9f0,'QQ',pointers[0],pointers[1]);write(0x9a6,'B',1)
     u.reg_write(UC_ARM64_REG_X0,OBJ);u.reg_write(UC_ARM64_REG_X20,OBJ);u.reg_write(UC_ARM64_REG_X1,0);u.reg_write(UC_ARM64_REG_SP,STACK+0x8000)
     run(0x234f448cc,0x234f44928);run(0x234f44940,0x234f44b34)
     records.append(struct.pack('<Ifddf',bins,inv,input_hop,output_hop,pitch)+b''.join(struct.pack(f'<{bins}f',*v) for v in arrays)+bytes(u.mem_read(pointers[2],bins*4))+bytes(u.mem_read(pointers[4],bins*4)))
 (a.output_dir/'time_pitch_phase.bin').write_bytes(struct.pack('<I',len(records))+b''.join(records))
 hops=[]
 for mapped in [0,1]:
  for n in [256,512,4096,8192]:
   for smooth in [3.,8.,13.7,32.]:
    smooth=f32(smooth)
    for rate in [.03125,.73,1.,1.03,32.]:
     for state in [(0.,0.,0.,0.),(120000.125,90000.25,101.25,101.5)]:
      input_time,output_time,previous,effective=state
      write(0x890,'I',n);write(0x8b0,'f',smooth);write(0x8a8,'d',rate);write(0x8d0,'dd',input_time,output_time);write(0x8f8,'dd',previous,effective)
      u.reg_write(UC_ARM64_REG_X19,OBJ)
      target=input_time+(math.floor(n/smooth+.5)*rate)+.125
      if mapped:
       run(0x234f46440,0x234f46470);f64(UC_ARM64_REG_D0,target);run(0x234f46480,0x234f46500)
      else:run(0x234f46498,0x234f46500)
      expected=read(0x8c0,'dd')+read(0x8f8,'ddd')
      hops.append(struct.pack('<IIfdd4d5d',mapped,n,smooth,rate,target,*state,*expected))
 (a.output_dir/'time_pitch_hop.bin').write_bytes(struct.pack('<I',len(hops))+b''.join(hops))
 info={'phase_cases':len(records),'hop_cases':len(hops),'phase_bins':[128,256,512,2048],'hooks':[],'code_sha256':hashlib.sha256(code).hexdigest()}
 (a.output_dir/'time_pitch_phase.json').write_text(json.dumps(info,indent=2)+'\n');print(json.dumps(info))
if __name__=='__main__':main()
