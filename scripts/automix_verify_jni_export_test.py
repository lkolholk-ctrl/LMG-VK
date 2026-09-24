#!/usr/bin/env python3
"""ELF guard fixtures only. Tiny exported test functions are never executed."""
from pathlib import Path
import os
import struct
import subprocess
import tempfile
import unittest
from automix_verify_jni_export import SYMBOL, verify_jni_export

class ExportTests(unittest.TestCase):
 def test_guard(self):
  accepted=0;rejected=0
  with tempfile.TemporaryDirectory() as d:
   root=Path(d);source=root/'symbol.c';binary=root/'symbol.so'
   source.write_text('void '+SYMBOL.decode()+'(void) {}\n')
   flags=[[]]
   while flags:
    extra=flags.pop(0)
    subprocess.run([os.environ.get('CC','cc'),*extra,'-shared','-fPIC',str(source),'-o',str(binary)],check=True)
    data=binary.read_bytes();cls=data[4];machine=struct.unpack_from('<H',data,18)[0]
    verify_jni_export(data,cls,machine);accepted+=1
    # An actually linked v1 JNI export must not satisfy the new v2 APK gate.
    stale_source=root/'stale.c';stale_binary=root/'stale.so'
    stale_source.write_text('void '+SYMBOL.decode().removesuffix('V2')+'(void) {}\n')
    subprocess.run([os.environ.get('CC','cc'),*extra,'-shared','-fPIC',str(stale_source),'-o',str(stale_binary)],check=True)
    with self.assertRaises(ValueError):verify_jni_export(stale_binary.read_bytes(),cls,machine)
    rejected+=1
    # Linux x86-64 GCC/binutils can emit a no-libc ELF32 fixture without multilib headers.
    if not extra and cls==2 and machine==62:flags.append(['-m32','-nostdlib'])
    wrong=[]
    for n in (0,16,48,len(data)-1):wrong.append(data[:n])
    b=bytearray(data);b[:4]=b'nope';wrong.append(bytes(b))
    b=bytearray(data);b[5]=2;wrong.append(bytes(b))
    b=bytearray(data);b[4]=3;wrong.append(bytes(b))
    wrong.append(data.replace(SYMBOL,b'X'+SYMBOL[1:]))
    for b in wrong:
     with self.assertRaises(ValueError):verify_jni_export(b,cls,machine)
     rejected+=1
    with self.assertRaises(ValueError):verify_jni_export(data,cls,0)
    rejected+=1
   print(f'JNI ELF export guard: {accepted} compiled symbol fixtures accepted, {rejected} corrupt/stale variants rejected')
if __name__=='__main__':unittest.main()
