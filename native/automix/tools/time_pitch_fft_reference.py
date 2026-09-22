#!/usr/bin/env python3
"""Original vDSP split-complex FFT reference, source radix kernels unchanged.

PAC/authenticated branches, Swift-free native allocation, and libm imports are
adapted for a bounded Unicorn call. No OS or Android emulator is run.
"""
import argparse
import ctypes
import hashlib
import json
import math
from pathlib import Path
import random
import struct
from capstone import Cs, CS_ARCH_ARM64, CS_MODE_LITTLE_ENDIAN
from unicorn import Uc, UcError, UC_ARCH_ARM64, UC_MODE_ARM, UC_HOOK_CODE, UC_HOOK_MEM_UNMAPPED
from unicorn.arm64_const import *

class TimePitchFftReference:
 def __init__(self, cache_dir, data_cache_dir=None):
  entries=[]
  for path in [Path(cache_dir)/'dyld_shared_cache_arm64e.48',
               Path(data_cache_dir or cache_dir)/'dyld_shared_cache_arm64e.70.dylddata']:
   with path.open('rb') as f:
    header=f.read(104);offset,count=struct.unpack_from('<II',header,16)
    f.seek(offset);mappings=f.read(count*32)
    for i in range(count):
     vm,size,fo,_,_=struct.unpack_from('<QQQII',mappings,32*i)
     entries.append((vm,size,fo,path))
  def rd(address,size):
   for vm,length,fo,path in entries:
    if vm<=address and address+size<=vm+length:
     with path.open('rb') as f:
      f.seek(fo+address-vm);raw=f.read(size)
     if len(raw)!=size:raise ValueError('Truncated cache range')
     return raw
   raise ValueError(hex(address))
  libm=ctypes.CDLL('libm.so.6');libm.cosf.argtypes=[ctypes.c_float];libm.cosf.restype=ctypes.c_float
  base=0x234ac2000;header=rd(base,16384);o=32
  for _ in range(struct.unpack_from('<I',header,16)[0]):
   c,s=struct.unpack_from('<II',header,o)
   if c==25 and header[o+8:o+24].rstrip(b'\0')==b'__TEXT':vm,vs,fo,fs=struct.unpack_from('<QQQQ',header,o+24)
   o+=s
  u=Uc(UC_ARCH_ARM64,UC_MODE_ARM);u.mem_map(base,(vs+4095)&~4095);u.mem_write(base,rd(base,vs))
  u.mem_map(0x236f3d000,4096);u.mem_map(0x100000,0x4000000);heap=0x200000;stop=0x101000
  patch={};md=Cs(CS_ARCH_ARM64,CS_MODE_LITTLE_ENDIAN);md.skipdata=True
  for i in md.disasm(bytes(u.mem_read(base+0x1000,vs-0x1000)),base+0x1000):
   if i.mnemonic in ('pacibsp','autibsp','paciza','pacia','pacib','autda','autia','autdb','xpaci'):
    patch[i.address]=(i.mnemonic,i.op_str);u.mem_write(i.address,bytes.fromhex('1f2003d5'))
   elif i.mnemonic=='retab':u.mem_write(i.address,bytes.fromhex('c0035fd6'))
   elif i.mnemonic in ('blraaz','braaz','blraa','braa'):
    patch[i.address]=(i.mnemonic,i.op_str);u.mem_write(i.address,bytes.fromhex('1f2003d5'))
  def invalid(uc,access,address,size,value,data):
   page=address&~4095
   try:b=rd(page,4096)
   except ValueError:
    print('UNMAPPED',hex(address),'pc',hex(uc.reg_read(UC_ARM64_REG_PC)));return False
   if len(b)!=4096:print('SHORT',hex(page));return False
   uc.mem_map(page,4096);uc.mem_write(page,b);return True
  u.hook_add(UC_HOOK_MEM_UNMAPPED,invalid)
  def alloc(n):
   nonlocal heap
   a=heap;heap+=(n+63)&~63
   if heap>=0x3000000:raise RuntimeError('heapfull')
   return a
  def fbits(x):return struct.unpack('<Q',struct.pack('<d',x))[0]
  def dbl(x):return struct.unpack('<d',struct.pack('<Q',x))[0]
  def reg(name):return globals()['UC_ARM64_REG_'+name.upper()]
  unknown=set();calls=0
  allocsizes={}
  def code(uc,address,size,data):
   nonlocal calls
   calls+=1
   if address in patch:
    op,args=patch[address]
    if op in ('pacibsp','autibsp'):return
    name=args.split(',')[0];r=reg(name)
    if op.startswith('autd') or op=='autia' or op=='xpaci':
     v=uc.reg_read(r)
     if v>>48:uc.reg_write(r,0x180000000+(v&((1<<34)-1)))
    elif op in ('blraaz','braaz','blraa','braa'):
     v=uc.reg_read(r)
     if v>>48:v=0x180000000+(v&((1<<34)-1))
     if op.startswith('bl'):uc.reg_write(UC_ARM64_REG_LR,address+4)
     uc.reg_write(UC_ARM64_REG_PC,v)
    return
   if address==0x236f3d4a0:
    n=uc.reg_read(UC_ARM64_REG_X0);a=alloc(n);allocsizes[a]=n;uc.reg_write(UC_ARM64_REG_X0,a)
   elif address==0x236f3d490:
    n=uc.reg_read(UC_ARM64_REG_X0)*uc.reg_read(UC_ARM64_REG_X1);a=alloc(n);allocsizes[a]=n;uc.mem_write(a,bytes(n));uc.reg_write(UC_ARM64_REG_X0,a)
   elif address==0x236f3d4c0:
    old=uc.reg_read(UC_ARM64_REG_X0);n=uc.reg_read(UC_ARM64_REG_X1);a=alloc(n);allocsizes[a]=n
    if old:uc.mem_write(a,bytes(uc.mem_read(old,min(n,allocsizes[old]))))
    uc.reg_write(UC_ARM64_REG_X0,a)
   elif address==0x236f3d3e0:
    uc.mem_write(uc.reg_read(UC_ARM64_REG_X0),bytes(uc.reg_read(UC_ARM64_REG_X1)))
   elif address==0x236f3d370:
    x=dbl(uc.reg_read(UC_ARM64_REG_D0));uc.reg_write(UC_ARM64_REG_D0,fbits(math.sin(x)));uc.reg_write(UC_ARM64_REG_D1,fbits(math.cos(x)))
   elif address==0x236f3d420:
    x=struct.unpack('<f',struct.pack('<I',uc.reg_read(UC_ARM64_REG_S0)))[0]
    uc.reg_write(UC_ARM64_REG_S0,struct.unpack('<I',struct.pack('<f',libm.cosf(x)))[0])
   elif address==0x236f3d540:
    x=dbl(uc.reg_read(UC_ARM64_REG_D0));uc.reg_write(UC_ARM64_REG_D0,fbits(math.sin(x)))
   elif 0x236f3d000<=address<0x236f3e000:
    print('UNHOOKED IMPORT',hex(address), 'args', [hex(uc.reg_read(r)) for r in (UC_ARM64_REG_X0,UC_ARM64_REG_X1,UC_ARM64_REG_X2)])
    uc.emu_stop();return
   else:return
   uc.reg_write(UC_ARM64_REG_PC,uc.reg_read(UC_ARM64_REG_LR))
  u.hook_add(UC_HOOK_CODE,code)
  def call(addr):
   u.reg_write(UC_ARM64_REG_SP,0x3f00000);u.reg_write(UC_ARM64_REG_LR,stop)
   try:u.emu_start(addr,stop,count=10000000)
   except UcError:
    pc=u.reg_read(UC_ARM64_REG_PC);print('FAULT',hex(pc),bytes(u.mem_read(pc,4)).hex());raise
   if u.reg_read(UC_ARM64_REG_PC)!=stop:raise RuntimeError('Stopped '+hex(u.reg_read(UC_ARM64_REG_PC)))
  self.u=u;self.read_vm=rd;self.alloc=alloc;self.call=call
  self.text_sha256=hashlib.sha256(rd(base,vs)).hexdigest()

def main():
 p=argparse.ArgumentParser(description=__doc__)
 p.add_argument('cache_dir',type=Path);p.add_argument('output_dir',type=Path)
 p.add_argument('--data-cache-dir',type=Path)
 args=p.parse_args();args.output_dir.mkdir(parents=True,exist_ok=True)
 ref=TimePitchFftReference(args.cache_dir,args.data_cache_dir);u=ref.u
 rng=random.Random(31);records=[]
 for n in [128,256,512,1024,2048,4096]:
  setup=ref.alloc(8);real=ref.alloc(n*4);imag=ref.alloc(n*4)
  inputs=[([1.]+[0.]*(n-1),[0.]*n),([1.]*n,[0.]*n),
          ([rng.uniform(-1,1) for _ in range(n)],[rng.uniform(-1,1) for _ in range(n)]),
          ([(-1.)**j for j in range(n)],[.25*(-1.)**j for j in range(n)]),
          ([rng.uniform(-1e-25,1e-25) for _ in range(n)],[rng.uniform(-1e-25,1e-25) for _ in range(n)]),
          ([0. if j!=n//3 else .75 for j in range(n)],[-0. if j!=n//7 else -.5 for j in range(n)])]
  for r,i in inputs:
   rb=struct.pack('<%df'%n,*r);ib=struct.pack('<%df'%n,*i)
   for inverse in [0,1]:
    u.mem_write(real,rb);u.mem_write(imag,ib)
    for rr,val in [(UC_ARM64_REG_X0,setup),(UC_ARM64_REG_X1,real),(UC_ARM64_REG_X2,imag),
                   (UC_ARM64_REG_X3,1),(UC_ARM64_REG_X4,real),(UC_ARM64_REG_X5,imag),
                   (UC_ARM64_REG_X6,1),(UC_ARM64_REG_X7,n)]:u.reg_write(rr,val)
    u.mem_write(0x3f00000,struct.pack('<q',1 if inverse else -1));ref.call(0x234b224ac)
    assert u.reg_read(UC_ARM64_REG_X0)==0
    records.append(struct.pack('<II',n,inverse)+rb+ib+
                   bytes(u.mem_read(real,n*4))+bytes(u.mem_read(imag,n*4)))
  print(f'complex size {n} complete',flush=True)
 payload=struct.pack('<I',len(records))+b''.join(records)
 (args.output_dir/'time_pitch_fft_complex.bin').write_bytes(payload)
 manifest=dict(vdsp_text_sha256=ref.text_sha256,complex_cases=len(records),
               complex_sizes=[128,256,512,1024,2048,4096],
               fixture_sha256=hashlib.sha256(payload).hexdigest(),
               original_entry='0x234b224ac',
               hooks=['malloc/calloc/realloc/bzero','host libm sin/cos/cosf and sincos'],
               scope='Original split-complex radix dispatch, all stages, permutation and twiddle constructors; no real pre/post in this fixture.')
 (args.output_dir/'time_pitch_fft_complex.json').write_text(json.dumps(manifest,indent=2)+'\n')
 print(json.dumps(manifest,indent=2))
if __name__=='__main__':main()
