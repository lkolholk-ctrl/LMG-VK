#!/usr/bin/env python3
"""Exercise original complete real FFT, including radix and real pre/post kernels."""
import argparse, struct, random, json, hashlib
from pathlib import Path
from time_pitch_fft_reference import TimePitchFftReference
from unicorn.arm64_const import *
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('cache_dir');p.add_argument('data_cache_dir');p.add_argument('output_dir',type=Path)
a=p.parse_args(); ref=TimePitchFftReference(a.cache_dir,a.data_cache_dir);u=ref.u
records=[];rng=random.Random(310)
for logn in range(8,14):
 n=1<<logn;setup=ref.alloc(72);tw=ref.alloc(4*n)
 u.mem_write(setup,struct.pack('<Q',logn));u.mem_write(setup+64,struct.pack('<Q',tw))
 for e in range(2,logn+1):
  u.reg_write(UC_ARM64_REG_X0,tw+2*(1<<e));u.reg_write(UC_ARM64_REG_X1,1<<e);ref.call(0x234ac3068)
 real=ref.alloc(n*2);imag=ref.alloc(n*2);split=ref.alloc(16);u.mem_write(split,struct.pack('<QQ',real,imag))
 inputs=[[1.]+[0.]*(n-1),[1.]*n,[rng.uniform(-1,1) for _ in range(n)]]
 for values in inputs:
  for inverse in [0,1]:
   # Forward input is adjacent time samples; inverse input is packed planes.
   rb=struct.pack('<%df'%(n//2),*(values[:n//2] if inverse else values[::2]))
   ib=struct.pack('<%df'%(n//2),*(values[n//2:] if inverse else values[1::2]))
   u.mem_write(real,rb);u.mem_write(imag,ib)
   for r,v in [(UC_ARM64_REG_X0,setup),(UC_ARM64_REG_X1,split),(UC_ARM64_REG_X2,1),(UC_ARM64_REG_X3,logn),(UC_ARM64_REG_X4,-1 if inverse else 1)]:u.reg_write(r,v)
   ref.call(0x234ace2a8)
   records.append(struct.pack('<II',n,inverse)+rb+ib+bytes(u.mem_read(real,n*2))+bytes(u.mem_read(imag,n*2)))
 print('real FFT',n,'complete',flush=True)
payload=struct.pack('<I',len(records))+b''.join(records)
a.output_dir.mkdir(parents=True,exist_ok=True);(a.output_dir/'time_pitch_fft_real.bin').write_bytes(payload)
(a.output_dir/'time_pitch_fft_real.json').write_text(json.dumps(dict(cases=len(records),entry='0x234ace2a8',text_sha256=ref.text_sha256,fixture_sha256=hashlib.sha256(payload).hexdigest(),hooks=['allocation','host libm'],scope='Complete original forward and inverse real FFT, N=256..8192'),indent=2)+'\n')
