#!/usr/bin/env python3
"""Original vDSP magnitude and exhaustive normalized ARM estimate table oracle."""
import argparse,hashlib,json,math,random,struct
from pathlib import Path
from unicorn import Uc,UC_ARCH_ARM64,UC_MODE_ARM
from unicorn.arm64_const import *

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('cache_dir',type=Path);p.add_argument('output_dir',type=Path);p.add_argument('--table',type=Path,required=True);a=p.parse_args();a.output_dir.mkdir(parents=True,exist_ok=True)
 u=Uc(UC_ARCH_ARM64,UC_MODE_ARM)
 with (a.cache_dir/'dyld_shared_cache_arm64e.48').open('rb') as f:
  f.seek(16);off,count=struct.unpack('<II',f.read(8));f.seek(off);maps=[struct.unpack('<QQQII',f.read(32)) for _ in range(count)]
  start=0x234acf000;vm,size,off,*_=next(m for m in maps if m[0]<=start<m[0]+m[1]);f.seek(off+start-vm);code=f.read(0x2000);u.mem_map(start,0x2000);u.mem_write(start,code)
 u.mem_map(0x100000,0x100000)
 def estimate(bits):u.reg_write(UC_ARM64_REG_S0,bits);u.emu_start(0x234acfc20,0x234acfc24);return u.reg_read(UC_ARM64_REG_S16)
 table=[estimate(((127+parity)<<23)|(i<<15)) for parity in range(2) for i in range(256)]
 text='// Generated from original ARM FRSQRTE instruction 0x234acfc20.\n// Index: exponent parity * 256 + high eight normalized mantissa bits.\nconstexpr std::uint32_t timePitchRsqrtEstimate[512] = {\n'
 text+='\n'.join('  '+', '.join(f'0x{x:08x}u' for x in table[i:i+8])+',' for i in range(0,512,8))+'\n};\n';a.table.write_text(text)
 # Verify the table's discarded mantissa bits at every bucket boundary and
 # representative normal exponents, then randomized positive subnormals.
 checked=0
 for exp in [1,2,3,126,127,128,129,253,254]:
  for i in range(256):
   for low in [0,1,0x3fff,0x7fff]:
    bits=(exp<<23)|(i<<15)|low;v=struct.unpack('<f',struct.pack('<I',bits))[0];m,e=math.frexp(v);m*=2;e-=1;mb=struct.unpack('<I',struct.pack('<f',m))[0]
    seed=struct.unpack('<f',struct.pack('<I',table[(e%2)*256+((mb&0x7fffff)>>15)]))[0];expected=struct.unpack('<I',struct.pack('<f',math.ldexp(seed,-(e//2))))[0]
    assert expected==estimate(bits);checked+=1
 rng=random.Random(23446020);records=[]
 lengths=list(range(34))+[63,64,65,127,128,256,1024,4096]
 for n in lengths:
  for align in range(4):
   for profile in range(3):
    if profile==0:real=[rng.uniform(-10,10) for _ in range(n)];imag=[rng.uniform(-10,10) for _ in range(n)]
    elif profile==1:real=[(-1 if i%2 else 1)*10**rng.uniform(-24,18) for i in range(n)];imag=[10**rng.uniform(-24,18) for i in range(n)]
    else:real=[0. if i%3 else 1. for i in range(n)];imag=[-0. if i%4 else -1. for i in range(n)]
    real_data=struct.pack(f'<{n}f',*real);imag_data=struct.pack(f'<{n}f',*imag)
    if n:u.mem_write(0x110000,real_data);u.mem_write(0x120000,imag_data)
    u.mem_write(0x100000,struct.pack('<QQ',0x110000,0x120000));output=0x130000+align*4
    for reg,value in [(UC_ARM64_REG_X0,0x100000),(UC_ARM64_REG_X1,1),(UC_ARM64_REG_X2,output),(UC_ARM64_REG_X3,1),(UC_ARM64_REG_X4,n),(UC_ARM64_REG_LR,0x1ff000)]:u.reg_write(reg,value)
    u.emu_start(0x234acfba4,0x1ff000,count=2000000)
    records.append(struct.pack('<II',n,align)+real_data+imag_data+(bytes(u.mem_read(output,n*4)) if n else b''))
 (a.output_dir/'time_pitch_magnitude.bin').write_bytes(struct.pack('<I',len(records))+b''.join(records))
 info={'magnitude_cases':len(records),'estimate_bucket_boundary_checks':checked,'table_entries':len(table),'code_sha256':hashlib.sha256(code).hexdigest(),'hooks':[]}
 (a.output_dir/'time_pitch_magnitude.json').write_text(json.dumps(info,indent=2)+'\n');print(json.dumps(info))
if __name__=='__main__':main()
