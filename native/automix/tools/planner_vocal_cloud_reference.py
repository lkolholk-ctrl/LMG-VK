#!/usr/bin/env python3
"""Read original cloud enum getters and execute their Swift small-string constants."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
from capstone import Cs,CS_ARCH_ARM64,CS_MODE_LITTLE_ENDIAN
from unicorn import Uc,UC_ARCH_ARM64,UC_MODE_ARM
from unicorn.arm64_const import *
p=argparse.ArgumentParser(description=__doc__);p.add_argument('cache_dir',type=Path)
p.add_argument('evidence_dir',type=Path);a=p.parse_args();a.evidence_dir.mkdir(parents=True,exist_ok=True)
def read_vm(path,address,size):
    with path.open('rb') as f:
        h=f.read(104);o,n=struct.unpack_from('<II',h,16);f.seek(o);maps=f.read(n*32)
        for i in range(n):
            vm,length,fo,_,_=struct.unpack_from('<QQQII',maps,i*32)
            if vm<=address and address+size<=vm+length:
                f.seek(fo+address-vm);return f.read(size)
    raise ValueError(hex(address))
cache=a.cache_dir/'dyld_shared_cache_arm64e.40'
page=read_vm(cache,0x21600f000,4096)
u=Uc(UC_ARCH_ARM64,UC_MODE_ARM);u.mem_map(0x21600f000,4096);u.mem_write(0x21600f000,page)
rows=[]
for address,expected in [(0x21600f4cc,'very-low'),(0x21600f4e4,'low'),(0x21600f4f4,'medium'),
                         (0x21600f508,'high'),(0x21600f518,'very-high'),(0x21600f2bc,'singing'),
                         (0x21600f2d4,'speech'),(0x21600f2e8,'rapping')]:
    u.emu_start(address,0x215be63d0,count=100)
    assert u.reg_read(UC_ARM64_REG_PC)==0x215be63d0
    raw=struct.pack('<QQ',u.reg_read(UC_ARM64_REG_X9),u.reg_read(UC_ARM64_REG_X10))
    value=raw[:raw[-1]&15].decode();assert value==expected
    rows.append(dict(address=hex(address),value=value,small_string=raw.hex()))
lines=[]
for start,end in [(0x21600f4cc,0x21600f538),(0x21600f2bc,0x21600f300)]:
    for ins in Cs(CS_ARCH_ARM64,CS_MODE_LITTLE_ENDIAN).disasm(read_vm(cache,start,end-start),start):
        lines.append(f'{ins.address:x}: {ins.bytes.hex()} {ins.mnemonic} {ins.op_str}')
(a.evidence_dir/'cloud_vocal_strings.asm').write_text('\n'.join(lines)+'\n')
manifest=dict(text_page_sha256=hashlib.sha256(page).hexdigest(),getters=rows)
(a.evidence_dir/'cloud_vocal_strings.json').write_text(json.dumps(manifest,indent=2)+'\n')
print(json.dumps(rows,indent=2))
