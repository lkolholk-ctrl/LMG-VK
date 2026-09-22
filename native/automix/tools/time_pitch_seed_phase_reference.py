#!/usr/bin/env python3
"""Capture original aligned vDSP_zvphas and its reciprocal-estimate table."""
import argparse,struct,random,json
from pathlib import Path
from time_pitch_fft_reference import TimePitchFftReference
from unicorn.arm64_const import *
p=argparse.ArgumentParser(description=__doc__);p.add_argument('cache_dir');p.add_argument('data_cache_dir');p.add_argument('output_dir',type=Path);a=p.parse_args()
r=TimePitchFftReference(a.cache_dir,a.data_cache_dir);u=r.u
u.mem_write(0x102000,r.read_vm(0x234b37044,4)+bytes.fromhex('c0035fd6'))
table=[]
for i in range(256):
 bits=0x3f000000+(i<<15);u.reg_write(UC_ARM64_REG_Q2,sum(bits<<(32*j) for j in range(4)));r.call(0x102000);table.append(u.reg_read(UC_ARM64_REG_Q0)&0xffffffff)
rng=random.Random(31);records=[]
for n in [128,256,2048]:
 rp=r.alloc(n*4);ip=r.alloc(n*4);out=r.alloc(n*4);split=r.alloc(16);u.mem_write(split,struct.pack('<QQ',rp,ip))
 for kind in range(4):
  real=[rng.uniform(-10,10) for _ in range(n)];imag=[rng.uniform(-10,10) for _ in range(n)]
  if kind==1:real=[0.]*n
  if kind==2:imag=[0.]*n
  if kind==3:
   choices=[0.,-0.,1.,-1.,1e-30,-1e-30,1e30,-1e30]
   real=[choices[j%8] for j in range(n)];imag=[choices[(j//8)%8] for j in range(n)]
  rb=struct.pack('<%df'%n,*real);ib=struct.pack('<%df'%n,*imag);u.mem_write(rp,rb);u.mem_write(ip,ib)
  for reg,val in [(UC_ARM64_REG_X0,split),(UC_ARM64_REG_X1,1),(UC_ARM64_REG_X2,out),(UC_ARM64_REG_X3,1),(UC_ARM64_REG_X4,n)]:u.reg_write(reg,val)
  r.call(0x234b36dec);records.append(struct.pack('<I',n)+rb+ib+bytes(u.mem_read(out,n*4)))
 print('phase seed',n,flush=True)
a.output_dir.mkdir(exist_ok=True,parents=True)
(a.output_dir/'time_pitch_seed_phase.bin').write_bytes(struct.pack('<I',len(records))+b''.join(records))
(a.output_dir/'time_pitch_reciprocal.json').write_text(json.dumps(table)+'\n')
