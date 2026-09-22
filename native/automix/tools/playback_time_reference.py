#!/usr/bin/env python3
"""Execute recovered ARM time calculators with host log/exp imports only."""
import hashlib, json, math, struct, sys
from pathlib import Path
from capstone import Cs, CS_ARCH_ARM64, CS_MODE_LITTLE_ENDIAN
from unicorn import Uc, UC_ARCH_ARM64, UC_MODE_ARM, UC_HOOK_CODE
from unicorn.arm64_const import *
b = Path(sys.argv[1]).read_bytes()
out = Path(sys.argv[2]); out.mkdir(parents=True, exist_ok=True)
u = Uc(UC_ARCH_ARM64, UC_MODE_ARM)
o = 32
for _ in range(struct.unpack_from('<I', b, 16)[0]):
    cmd, size = struct.unpack_from('<II', b, o)
    if cmd == 25 and b[o+8:o+24].rstrip(b'\0') == b'__TEXT':
        va, vs, fo, fs = struct.unpack_from('<QQQQ', b, o+24)
        u.mem_map(va, (vs+4095)&~4095); u.mem_write(va, b[fo:fo+fs])
    o += size
ranges = [(0x27224fb08,0x27224fd64),(0x272250300,0x2722504fc),(0x2722509cc,0x272250a44),(0x27225157c,0x272251628),(0x27220f754,0x27220f860),(0x27227965c,0x2722797b8)]
hashes = {}
for start,end in ranges:
    raw = bytes(u.mem_read(start,end-start)); hashes[hex(start)] = hashlib.sha256(raw).hexdigest()
    for ins in Cs(CS_ARCH_ARM64, CS_MODE_LITTLE_ENDIAN).disasm(raw,start):
        if ins.mnemonic in ('pacibsp','autibsp'): u.mem_write(ins.address,bytes.fromhex('1f2003d5'))
        if ins.mnemonic == 'retab': u.mem_write(ins.address,bytes.fromhex('c0035fd6'))
u.mem_map(0x2743dd000,4096)
u.mem_map(0x100000,0x20000)
def hook(uc, address, size, data):
    if address not in (0x2743ddb00,0x2743ddb20): return
    value = struct.unpack('<d',struct.pack('<Q',uc.reg_read(UC_ARM64_REG_D0)))[0]
    value = (math.exp if address == 0x2743ddb00 else math.log)(value)
    uc.reg_write(UC_ARM64_REG_D0,struct.unpack('<Q',struct.pack('<d',value))[0])
    uc.reg_write(UC_ARM64_REG_PC,uc.reg_read(UC_ARM64_REG_LR))
u.hook_add(UC_HOOK_CODE,hook,begin=0x2743ddb00,end=0x2743ddb20)
records=[]
for anchor in [(0.,0.,0.),(123.25,130.5,7.75)]:
 for rate0,rate1,duration in [(1.,1.,8.),(.8,1.2,8.),(1.2,.8,8.),(.5,.5,8.),(1.,1.000001,8.),(1.,1.000008,8.),(1.,1.000009,8.),(.8,1.2,0.)]:
  ramp=(rate0,rate1,anchor[0]+2,anchor[0]+2+duration)
  for delta in [0.,.001,1.,2.,2.00001,4.,10.,20.]:
   for inverse in [0,1]:
    query=anchor[2 if inverse else 0]+delta
    u.mem_write(0x100000,struct.pack('<d',query))
    u.mem_write(0x100100,struct.pack('<4dB',*ramp,0x80))
    u.mem_write(0x100200,struct.pack('<7dB',*anchor,*ramp,0x80))
    for reg,value in [(UC_ARM64_REG_X0,0x100000),(UC_ARM64_REG_X1,0x100100),
        (UC_ARM64_REG_X8,0x100300),(UC_ARM64_REG_X20,0x100200),
        (UC_ARM64_REG_SP,0x11e000),(UC_ARM64_REG_LR,0x11f000)]:u.reg_write(reg,value)
    u.emu_start(0x27224fc0c if inverse else 0x27224fb08,0x11f000,count=20000)
    assert u.reg_read(UC_ARM64_REG_PC)==0x11f000
    records.append(struct.pack('<I8d',inverse,*anchor,*ramp,query)+bytes(u.mem_read(0x100300,24)))
payload=struct.pack('<I',len(records))+b''.join(records)
(out/'playback_time.bin').write_bytes(payload)
(out/'playback_time.json').write_text(json.dumps({'binary_sha256':hashlib.sha256(b).hexdigest(),
 'code_sha256':hashes,'cases':len(records),'fixture_sha256':hashlib.sha256(payload).hexdigest(),
 'hooks':['PAC instructions only','log and exp imported functions use host libm'],
 'scope':'Recovered active-domain calculators; not absent-ramp/earlier-time planner dispatch'},indent=2)+'\n')
print(len(records),'ARM reference cases')

# Exercise the enclosing wrappers, including optional-none marker 0xfc.
records=[]
for anchor in [(0.,0.,0.),(123.25,130.5,7.75)]:
 for present in [0,1]:
  ramp=(.8,1.2,anchor[0]+2,anchor[0]+10)
  u.mem_write(0x100200,struct.pack('<7dB',*anchor,*ramp,0x80 if present else 0xfc))
  for begin,end in [(-10.,-2.),(-2.,0.),(-2.,5.),(0.,0.),(0.,5.),(2.,10.),(5.,20.),(20.,5.)]:
   for mode in [0,1]:
    first=anchor[2 if mode else 0]+begin
    last=anchor[0]+end
    u.mem_write(0x100000,struct.pack('<d',first))
    for reg,value in [(UC_ARM64_REG_X0,0x100000 if mode else 0x100200),
       (UC_ARM64_REG_X20,0x100200),(UC_ARM64_REG_X8,0x100300),
       (UC_ARM64_REG_SP,0x11e000),(UC_ARM64_REG_LR,0x11f000)]:u.reg_write(reg,value)
    for reg,value in [(UC_ARM64_REG_D0,first),(UC_ARM64_REG_D1,last)]:
       u.reg_write(reg,struct.unpack('<Q',struct.pack('<d',value))[0])
    u.emu_start(0x27225157c if mode else 0x27220f754,0x11f000,count=20000)
    assert u.reg_read(UC_ARM64_REG_PC)==0x11f000
    result=bytes(u.mem_read(0x100300,24)) if mode else struct.pack('<Q2d',u.reg_read(UC_ARM64_REG_D0),0.,0.)
    records.append(struct.pack('<II9d',mode,present,*anchor,*ramp,first,last)+result)
payload=struct.pack('<I',len(records))+b''.join(records)
(out/'playback_wrappers.bin').write_bytes(payload)
(out/'playback_wrappers.json').write_text(json.dumps({'binary_sha256':hashlib.sha256(b).hexdigest(),
 'code_sha256':hashes,'cases':len(records),'fixture_sha256':hashlib.sha256(payload).hexdigest(),
 'hooks':['PAC instructions only','log and exp imported functions use host libm'],
 'scope':'Transition-time wrapper and signed stretched-duration wrapper; with/without ramp, before/crossing/after anchor'},indent=2)+'\n')
print(len(records),'ARM wrapper reference cases')

# Original constructor, with only the automation lookup forced absent. This
# isolates the anchor carry arithmetic; it does not claim ramp selection parity.
def no_automation(uc,address,size,data):
    uc.mem_write(uc.reg_read(UC_ARM64_REG_X8),bytes(64))
    uc.reg_write(UC_ARM64_REG_PC,uc.reg_read(UC_ARM64_REG_LR))
u.hook_add(UC_HOOK_CODE,no_automation,begin=0x272278dfc,end=0x272278dfc)
records=[]
for transition,song in [(0.,0.),(7.75,123.25),(100.,987.654321)]:
 for previous in [None,(120.,125.),(120.,115.),(123.456789,124.567891)]:
    oldsong,oldstretch=previous or (0.,0.)
    context=struct.pack('<9dB',0.,1.,transition,transition+20,transition+10,0.,song,oldsong,oldstretch,0 if previous else 1)
    u.mem_write(0x100200,context)
    for reg,value in [(UC_ARM64_REG_X20,0x100200),(UC_ARM64_REG_X8,0x100300),
         (UC_ARM64_REG_SP,0x11e000),(UC_ARM64_REG_LR,0x11f000)]:u.reg_write(reg,value)
    u.emu_start(0x27227965c,0x11f000,count=20000)
    assert u.reg_read(UC_ARM64_REG_PC)==0x11f000
    assert bytes(u.mem_read(0x100338,1))==b'\xfc'
    records.append(struct.pack('<I4d',int(previous is not None),transition,song,oldsong,oldstretch)+bytes(u.mem_read(0x100300,24)))
payload=struct.pack('<I',len(records))+b''.join(records)
(out/'playback_anchors.bin').write_bytes(payload)
(out/'playback_anchors.json').write_text(json.dumps({'binary_sha256':hashlib.sha256(b).hexdigest(),
 'code_sha256':hashes,'cases':len(records),'fixture_sha256':hashlib.sha256(payload).hexdigest(),
 'hooks':['PAC instructions only','0x272278dfc automation lookup returns absent'],
 'scope':'Constructor anchor carry only; rate selection is not executed'},indent=2)+'\n')
print(len(records),'ARM anchor reference cases')
