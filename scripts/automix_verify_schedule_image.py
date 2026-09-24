#!/usr/bin/env python3
"""Read-only exact-image attribution and selected call/witness checks. No disassembly output or execution."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import uuid
ROOT=Path(__file__).resolve().parents[1]

def verify(path: Path):
    spec=json.loads((ROOT/'native/automix/evidence/STAGE4A_SCHEDULE_PROVENANCE.json').read_text())
    with path.open('rb') as f:data=f.read(spec['imageBytes']+1)
    if len(data)!=spec['imageBytes'] or hashlib.sha256(data).hexdigest()!=spec['imageSha256']:
        raise ValueError('Wrong/truncated image')
    if struct.unpack_from('<II',data,0)!=(0xfeedfacf,0x100000c):raise ValueError('Not ARM64 Mach-O')
    n,command_bytes=struct.unpack_from('<II',data,16);offset=32;segments=[];identity=None
    if n>4096 or 32+command_bytes>len(data):raise ValueError('Invalid load commands')
    for _ in range(n):
        cmd,size=struct.unpack_from('<II',data,offset)
        if size<8 or offset+size>32+command_bytes:raise ValueError('Bad load command')
        if cmd==0x19:
            if size<72:raise ValueError('Bad segment')
            v,vs,f,fs=struct.unpack_from('<QQQQ',data,offset+24)
            if f+fs>len(data) or fs>vs:raise ValueError('Bad mapping')
            segments.append((v,f,fs))
        if cmd==0x1b:
            if size!=24:raise ValueError('Bad UUID')
            identity=str(uuid.UUID(bytes=data[offset+8:offset+24]))
        offset+=size
    if offset!=32+command_bytes or identity!=spec['imageUuid']:raise ValueError('Image identity/mapping mismatch')
    def read(a,n):
        matches=[f+a-v for v,f,fs in segments if v<=a and a+n<=v+fs]
        if len(matches)!=1:raise ValueError('Ambiguous or absent file-backed VM range')
        p=matches[0];return p,data[p:p+n]
    for w in spec['windows']:
        p,b=read(int(w['vmAddress'],16),w['byteCount'])
        if p!=w['fileOffset'] or hashlib.sha256(b).hexdigest()!=w['sha256']:raise ValueError('Window mismatch: '+w['label'])
    for call in spec['directCalls']:
        a=int(call['site'],16);word=struct.unpack('<I',read(a,4)[1])[0]
        if word&0xfc000000!=0x94000000:raise ValueError('Not direct BL')
        imm=word&0x3ffffff
        if imm&(1<<25):imm-=1<<26
        if a+4*imm!=int(call['target'],16):raise ValueError('Direct call mismatch')
    table=int(spec['witnessTableAddress'],16)
    for i,target in enumerate(spec['witnessTargets']):
        if struct.unpack('<Q',read(table+i*8,8)[1])[0]!=int(target,16):raise ValueError('Structured witness mismatch')
    if struct.unpack('<ddd',read(0x272296638,24)[1])!=(.5,1.,2.):raise ValueError('Scale table mismatch')
    for pointer in (0x2884ab8c8,0x2884ab980):
        if struct.unpack('<Q',read(pointer,8)[1])[0]!=0x2722174dc:raise ValueError('Not the beat-index witness')
    print(f"SCHEDULE_IMAGE_ATTRIBUTION_VERIFIED: {len(spec['windows'])} windows, {len(spec['directCalls'])} calls, structured witness and beat-index bindings; no original execution")

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--image',type=Path,required=True);args=p.parse_args()
    try:verify(args.image)
    except (OSError,ValueError,KeyError,struct.error) as e:raise SystemExit('Image verification failed: '+str(e))
if __name__=='__main__':main()
