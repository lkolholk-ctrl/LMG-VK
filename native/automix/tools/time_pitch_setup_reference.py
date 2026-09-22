#!/usr/bin/env python3
"""Execute bounded original NewTimePitch setup instructions, iOS 23A341.
Only the window's imported cosine is supplied by host libm; no DSP formula hooks.
"""
import argparse
import ctypes
import hashlib
import json
from pathlib import Path
import struct
from unicorn import Uc, UC_ARCH_ARM64, UC_MODE_ARM, UC_HOOK_CODE
from unicorn.arm64_const import *
BASE = 0x234f04000
OBJ, WINDOW, STACK = 0x100000, 0x200000, 0x300000

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('cache_dir', type=Path)
    p.add_argument('output_dir', type=Path)
    a = p.parse_args()
    a.output_dir.mkdir(parents=True, exist_ok=True)
    with (a.cache_dir / 'dyld_shared_cache_arm64e.48').open('rb') as f:
        f.seek(0x4dfc000)
        code = f.read(0x120000)
    u = Uc(UC_ARCH_ARM64, UC_MODE_ARM)
    u.mem_map(BASE, 0x120000); u.mem_write(BASE, code)
    u.mem_map(OBJ, 0x300000); u.mem_map(0x236f45000, 0x1000)
    libm = ctypes.CDLL('libm.so.6'); libm.cos.argtypes=[ctypes.c_double]; libm.cos.restype=ctypes.c_double
    def f64(reg, value): u.reg_write(reg, struct.unpack('<Q', struct.pack('<d', value))[0])
    def hook(uc, address, size, unused):
        if address == 0x236f45690:
            v = struct.unpack('<d', struct.pack('<Q', u.reg_read(UC_ARM64_REG_D0)))[0]
            f64(UC_ARM64_REG_D0, libm.cos(v))
            u.reg_write(UC_ARM64_REG_PC, u.reg_read(UC_ARM64_REG_LR))
    u.hook_add(UC_HOOK_CODE, hook)
    def run(begin,end): u.emu_start(begin,end,count=2000000)
    def word(off): return struct.unpack('<I',u.mem_read(OBJ+off,4))[0]
    geometries=[]
    rates=[8000.,8191.999,8192.,16000.,16383.999,16384.,22050.,32767.999,32768.,44100.,48000.,65535.999,65536.,96000.,192000.]
    for rate in rates:
        for quality in [0,32,33,128,0xffffffff]:
            for frames in [1,127,512,4096,16384]:
                u.mem_write(OBJ,bytes(0x1000)); u.reg_write(UC_ARM64_REG_X19,OBJ)
                u.mem_write(OBJ+0x338,struct.pack('<I',quality)); f64(UC_ARM64_REG_D8,rate)
                run(0x234f9e5e0,0x234f9e648)
                n=word(0x358)
                u.reg_write(UC_ARM64_REG_W1,n); u.reg_write(UC_ARM64_REG_W3,frames)
                run(0x234f43d48,0x234f43da0)
                run(0x234f43dc8,0x234f43e30)
                expected=[word(o) for o in [0x890,0x894,0x910,0x918,0x928]]
                inverse=struct.unpack('<f',u.mem_read(OBJ+0x8a0,4))[0]
                geometries.append(struct.pack('<dII5If',rate,frames,quality,*expected,inverse))
    (a.output_dir/'time_pitch_geometry.bin').write_bytes(struct.pack('<I',len(geometries))+b''.join(geometries))
    windows=[]
    for n in [256,512,1024,2048,4096,8192]:
        u.reg_write(UC_ARM64_REG_X19,OBJ);u.reg_write(UC_ARM64_REG_X26,OBJ+0xa48)
        u.reg_write(UC_ARM64_REG_SP,STACK+0x8000);u.reg_write(UC_ARM64_REG_X29,STACK+0x81a0)
        u.mem_write(OBJ+0x890,struct.pack('<I',n));u.mem_write(OBJ+0xa48,struct.pack('<Q',WINDOW))
        run(0x234f43fa4,0x234f44130)
        windows.append(struct.pack('<I',n)+bytes(u.mem_read(WINDOW,n*4)))
    (a.output_dir/'time_pitch_window.bin').write_bytes(struct.pack('<I',len(windows))+b''.join(windows))
    latencies=[]
    for rate in rates:
        for n in [256,512,1024,2048,4096,8192]:
            for speed in [.03125,.7,1,1.03,2,32]:
                speed=struct.unpack('<f',struct.pack('<f',speed))[0]
                u.reg_write(UC_ARM64_REG_X8,OBJ)
                u.mem_write(OBJ+0x894,struct.pack('<I',n//2))
                u.mem_write(OBJ+0x8a8,struct.pack('<d',speed));u.mem_write(OBJ+0x880,struct.pack('<d',rate))
                run(0x234f9d54c,0x234f9d570)
                result=u.reg_read(UC_ARM64_REG_D8)
                latencies.append(struct.pack('<dIfQ',rate,n,speed,result))
    (a.output_dir/'time_pitch_latency.bin').write_bytes(struct.pack('<I',len(latencies))+b''.join(latencies))
    info={'geometry_cases':len(geometries),'window_sizes':[256,512,1024,2048,4096,8192],
          'latency_cases':len(latencies),'cosine':'host libm, original call address 0x236f45690',
          'code_sha256':hashlib.sha256(code).hexdigest(),'source':'libEmbeddedSystemAUs iOS 23A341'}
    (a.output_dir/'time_pitch_setup.json').write_text(json.dumps(info,indent=2)+'\n')
    print(json.dumps(info))
if __name__=='__main__': main()
