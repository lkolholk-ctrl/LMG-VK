#!/usr/bin/env python3
"""Execute original MEMixerChannel::_CombineVolumes; no arithmetic hooks."""
import argparse, random, struct
from pathlib import Path
from unicorn import Uc, UC_ARCH_ARM64, UC_MODE_ARM
from unicorn.arm64_const import *

p=argparse.ArgumentParser();p.add_argument('image',type=Path);p.add_argument('output',type=Path)
p.add_argument('--targets',type=Path);a=p.parse_args()
with a.image.open('rb') as f:header=f.read(65536)
offset=32;maps=[]
for _ in range(struct.unpack_from('<I',header,16)[0]):
    command,size=struct.unpack_from('<II',header,offset)
    if command==25:maps.append(struct.unpack_from('<QQQQ',header,offset+24))
    offset+=size
def read(address,size):
    vm,_,fo,_=next(m for m in maps if m[0]<=address and address+size<=m[0]+m[3])
    with a.image.open('rb') as f:f.seek(fo+address-vm);return f.read(size)
u=Uc(UC_ARCH_ARM64,UC_MODE_ARM)
for address in [0x1b8f61000,0x1b916b000,0x1b916c000]:
    u.mem_map(address,4096);u.mem_write(address,read(address,4096))
# PAC prologue is irrelevant to arithmetic. Stop before authenticated return.
u.mem_write(0x1b8f6188c,struct.pack('<I',0xd503201f))
for address,size in [(0x100000,8192),(0x200000,65536),(0x1e6bef000,4096),(0x1ebd81000,4096)]:u.mem_map(address,size)
u.mem_write(0x1e6bef758,struct.pack('<Q',0x101000))
rng=random.Random(6188);records=[]
for case in range(192):
    state=bytearray()
    for i in range(11):
        active=case%4!=0;started=case%3!=0;pending=bool(case%2)
        current=rng.uniform(0,1);start=rng.randrange(-100,100);duration=rng.choice([0,1,17,100,1000])
        state+=struct.pack('<4BfQqIffI',active,started,pending,0,current,0,start,duration,
                           rng.random(),rng.random(),case%3)
    base=rng.random();frame=rng.choice([-200,0,1,53,999,2000]);exclude=case%2
    for step in range(3):
        u.mem_write(0x100000,bytes(0x1000));u.mem_write(0x100368,bytes(state))
        u.mem_write(0x100528,struct.pack('<f',base));u.mem_write(0x101008,b'\0')
        for reg,value in [(UC_ARM64_REG_X0,0x100000),(UC_ARM64_REG_X1,frame&((1<<64)-1)),
                          (UC_ARM64_REG_X2,0x101008),(UC_ARM64_REG_X3,exclude),(UC_ARM64_REG_SP,0x20f000)]:u.reg_write(reg,value)
        u.emu_start(0x1b8f6188c,0x1b8f61bc8,count=10000)
        assert u.reg_read(UC_ARM64_REG_PC)==0x1b8f61bc8
        after=bytes(u.mem_read(0x100368,440));changed=u.mem_read(0x101008,1)[0]
        records.append(struct.pack('<fqI',base,frame,exclude)+state+
                       struct.pack('<III',u.reg_read(UC_ARM64_REG_S0),u.reg_read(UC_ARM64_REG_S1),changed)+after)
        state=after;frame+=rng.choice([1,17,1000])
a.output.write_bytes(struct.pack('<I',len(records))+b''.join(records))
print(f'{len(records)} original channel-volume state transitions captured')
if a.targets:
    for address in [0x1b8ecb000,0x1b8ecc000]:
        u.mem_map(address,4096);u.mem_write(address,read(address,4096))
    targets=[]
    for case in range(256):
        seconds=[0.0,-0.0,.02,.1234567,1e10,-.5][case%6]
        seconds=struct.unpack('<f',struct.pack('<f',seconds))[0]
        fs=[8000.,44100.,48000.,96000.][case%4]
        target=rng.random();frame=rng.randrange(-10000,10000);event=rng.randrange(-500,500)
        state=struct.pack('<4BffIqIffI',case%2,case%3!=0,case%5==0,0,rng.random(),seconds,0,
                          rng.randrange(-1000,1000),rng.randrange(1000),rng.random(),rng.random(),case%3)
        u.mem_write(0x101100,state)
        u.mem_write(0x101200,struct.pack('<4if',0,0,event,0,target))
        u.mem_write(0x100010,struct.pack('<Q',0x101300));u.mem_write(0x101348,struct.pack('<d',fs))
        u.mem_write(0x20f0b0,struct.pack('<q',frame));u.mem_write(0x20f0a0,struct.pack('<Q',0x100000))
        for reg,value in [(UC_ARM64_REG_X20,0x100000),(UC_ARM64_REG_X21,0x101200),
                          (UC_ARM64_REG_X22,0x101100),(UC_ARM64_REG_SP,0x20f000),
                          (UC_ARM64_REG_S8,struct.unpack('<I',struct.pack('<f',seconds))[0])]:u.reg_write(reg,value)
        stop=0x1b8ecc168 if seconds==0 else 0x1b8ecc204
        u.emu_start(0x1b8ecbc38,stop,count=1000);assert u.reg_read(UC_ARM64_REG_PC)==stop
        targets.append(struct.pack('<dfqi',fs,target,frame,event)+state+bytes(u.mem_read(0x101100,40)))
    a.targets.write_bytes(struct.pack('<I',len(targets))+b''.join(targets))
    print(f'{len(targets)} original volume target-event updates captured')
