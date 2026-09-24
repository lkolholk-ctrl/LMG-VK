#!/usr/bin/env python3
"""Read-only attribution checks for the supplied extracted image. No disassembly/execution/output binary.
Requires the exact research target, not an IPSW/shared cache. Hash checks establish
which bytes were inspected; they do not prove full planner behavioral parity.
"""
from pathlib import Path
import argparse
import hashlib
import json
import struct
import uuid

ROOT = Path(__file__).resolve().parents[1]

def verify(path: Path, manifest: Path) -> dict:
    source = json.loads(manifest.read_text())
    image = source['image']
    with path.open('rb') as stream:
        data = stream.read(2 * 1024 * 1024 + 1)
    def need(ok, message):
        if not ok: raise ValueError(message)
    need(len(data) == image['size'] and hashlib.sha256(data).hexdigest() == image['sha256'], 'Image size/SHA-256 mismatch')
    need(len(data) >= 32 and struct.unpack_from('<II', data, 0) == (0xfeedfacf, 0x100000c), 'Not the ARM64 Mach-O target')
    ncmds, sizeofcmds = struct.unpack_from('<II', data, 16)
    need(ncmds <= 1024 and sizeofcmds <= len(data)-32, 'Invalid load commands')
    at=32; segments=[]; identity=None
    for _ in range(ncmds):
        need(at+8 <= 32+sizeofcmds, 'Truncated load command')
        cmd, size=struct.unpack_from('<II', data, at)
        need(size >= 8 and at+size <= 32+sizeofcmds, 'Invalid load command length')
        if cmd == 0x1b:
            need(size >= 24, 'Truncated UUID'); identity=str(uuid.UUID(bytes=data[at+8:at+24]))
        if cmd == 0x19:
            need(size >= 72, 'Truncated segment')
            addr, length, offset, stored=struct.unpack_from('<QQQQ', data, at+24)
            need(offset+stored <= len(data) and stored <= length, 'Invalid segment extent')
            segments.append((addr,stored,offset))
        at+=size
    need(at==32+sizeofcmds and identity==image['uuid'], 'Load commands/UUID mismatch')
    def raw(addr, size):
        matches=[off+addr-base for base,n,off in segments if base <= addr and addr+size <= base+n]
        need(len(matches)==1, 'Missing or ambiguous VM range')
        return data[matches[0]:matches[0]+size]
    def rel(addr): return addr+struct.unpack('<i',raw(addr,4))[0]
    def cstr(addr):
        out=bytearray()
        for i in range(128):
            b=raw(addr+i,1)
            if b==b'\0': return out.decode('ascii')
            out+=b
        raise ValueError('Unbounded field name')
    for w in source['functionWindows']:
        content=raw(int(w['address'],16),w['size'])
        need(hashlib.sha256(content).hexdigest()==w['sha256'], 'Function window mismatch')
        need(content==data[w['fileOffset']:w['fileOffset']+w['size']], 'VM/file mapping mismatch')
    need(rel(0x27229a8c0)==0x27224eab0 and rel(0x27229a3b0)==0x27224d8fc, 'Decoder witnesses differ')
    offsets=struct.unpack('<5I',raw(0x2884ae360+16,20))
    need(offsets==(0,8,24,40,48), 'TransitionStyle layout differs')
    base=0x272294c58
    need(struct.unpack('<I',raw(base+12,4))[0]==5, 'Field count mismatch')
    names=[cstr(rel(base+16+i*12+8)) for i in range(5)]
    need(names==['id','startTime','maximumBarCount','outgoingSchedule','incomingSchedule'], 'Field names differ')
    need(raw(rel(base+16+2*12+4),5)==b'SiSg\0','maximumBarCount is not optional signed Int')
    # Decode the straight-line MOVZ/MOVK sequence materializing x0 for the duration key.
    value=0
    for addr in (0x27224ceac,0x27224ceb0,0x27224ceb4,0x27224ceb8):
        op=struct.unpack('<I',raw(addr,4))[0]
        need(op & 31 == 0 and op>>31==1, 'Unexpected duration literal destination')
        shift=((op>>21)&3)*16; immediate=(op>>5)&0xffff
        kind=op & 0xff800000
        if kind==0xd2800000: value=immediate<<shift
        elif kind==0xf2800000: value=(value & ~(0xffff<<shift)) | immediate<<shift
        else: raise ValueError('Unexpected literal instruction')
    need(value.to_bytes(8,'little')==b'duration','Coding-key string differs')
    table=[struct.unpack('<Q',raw(0x2884ace78+offset,8))[0] for offset in (0x18,0x20,0x28)]
    need(table==[0x2722246f8,0x272224708,0x272224718], 'Provider witness targets differ')
    for address,field in zip(table,(0x1c,0x20,0x24)):
        instruction=struct.unpack('<I',raw(address,4))[0]
        need(instruction & 0xffc00000 == 0xb9800000 and instruction & 31 == 9 and (instruction>>5)&31==0,
             'Provider is not the expected signed-offset getter')
        need(((instruction>>10)&0xfff)*4==field,'Provider offset field differs')
    return dict(status='EXACT_IMAGE_AND_SELECTED_BINDINGS_VERIFIED',uuid=identity,sha256=image['sha256'],
                functionWindows=len(source['functionWindows']),originalInstructionExecution=False,fullPlannerParity=False)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--image',required=True,type=Path)
    args=parser.parse_args()
    print(json.dumps(verify(args.image,ROOT/'native/automix/evidence/SOURCE_CONTEXT_PROVENANCE.json'),indent=2))
if __name__=='__main__': main()
