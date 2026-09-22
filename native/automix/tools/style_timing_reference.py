#!/usr/bin/env python3
"""Execute original normalized placement and ramp-time arithmetic blocks."""
import hashlib, json, struct, sys
from pathlib import Path
from unicorn import Uc, UC_ARCH_ARM64, UC_MODE_ARM
from unicorn.arm64_const import *
b=Path(sys.argv[1]).read_bytes(); out=Path(sys.argv[2]); out.mkdir(parents=True,exist_ok=True)
u=Uc(UC_ARCH_ARM64,UC_MODE_ARM); o=32
for _ in range(struct.unpack_from('<I',b,16)[0]):
 cmd,n=struct.unpack_from('<II',b,o)
 if cmd==25 and b[o+8:o+24].rstrip(b'\0')==b'__TEXT':
  va,vs,fo,fs=struct.unpack_from('<QQQQ',b,o+24)
  u.mem_map(va,(vs+4095)&~4095);u.mem_write(va,b[fo:fo+fs])
 o+=n
u.mem_map(0x100000,0x10000)
def put(reg,v):u.reg_write(reg,struct.unpack('<Q',struct.pack('<d',v))[0])
def get(reg):return struct.unpack('<d',struct.pack('<Q',u.reg_read(reg)))[0]
hashes={hex(a):hashlib.sha256(bytes(u.mem_read(a,z-a))).hexdigest()
        for a,z in [(0x2722106fc,0x27221075c),(0x2722108bc,0x272210920)]}
records=[]
placements=[(0.,0.,1.,0.),(.25,0.,.75,0.),(.75,-.3,.75,0.),(0.,-2.,1.,2.),(1.,0.,0.,0.),(0.,100.,1.,-100.),(.1,.123,.9,-.321)]
ramps=[(0.,0.,1.,0.),(1.,0.,0.,0.),(.5,-.1,.5,.1),(0.,-99.,1.,99.),(.25,.125,.75,-.125),(1.,99.,0.,99.)]
for parent in [(0.,8.),(123.456,139.789),(10.,10.)]:
 for placement in placements:
  for ramp in ramps:
   u.reg_write(UC_ARM64_REG_SP,0x108000);u.reg_write(UC_ARM64_REG_X8,0x100000)
   u.mem_write(0x100000,struct.pack('<4d',*placement))
   u.mem_write(0x108010,struct.pack('<d',parent[1]-parent[0]))
   put(UC_ARM64_REG_D14,parent[0]);put(UC_ARM64_REG_D4,parent[1])
   u.emu_start(0x2722106fc,0x27221075c,count=100)
   begin,end=get(UC_ARM64_REG_D14),get(UC_ARM64_REG_D13)
   u.mem_write(0x108070,struct.pack('<2d',end-begin,end))
   u.mem_write(0x108098,struct.pack('<2d',ramp[2],ramp[3]))
   put(UC_ARM64_REG_D13,ramp[0]);put(UC_ARM64_REG_D8,ramp[1])
   u.emu_start(0x2722108bc,0x272210920,count=100)
   records.append(struct.pack('<12d',*parent,*placement,*ramp,get(UC_ARM64_REG_D8),get(UC_ARM64_REG_D9)))
payload=struct.pack('<I',len(records))+b''.join(records)
(out/'style_timing.bin').write_bytes(payload)
(out/'style_timing.json').write_text(json.dumps({'binary_sha256':hashlib.sha256(b).hexdigest(),'code_sha256':hashes,
 'cases':len(records),'fixture_sha256':hashlib.sha256(payload).hexdigest(),'hooks':[],
 'scope':'Two original arithmetic blocks; parent and normalized times supplied, no full planner execution'},indent=2)+'\n')
print(len(records),'original ARM style timing cases')
