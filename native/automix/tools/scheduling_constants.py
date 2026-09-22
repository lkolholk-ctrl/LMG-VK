#!/usr/bin/env python3
"""Read recovered scheduling constants from the extracted source Mach-O."""
import argparse
import hashlib
import json
import struct
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('binary', type=Path)
args = parser.parse_args()
binary = args.binary.read_bytes()
if struct.unpack_from('<I', binary)[0] != 0xfeedfacf:
    raise ValueError('Expected little-endian 64-bit Mach-O')
segments = []
offset = 32
for _ in range(struct.unpack_from('<I', binary, 16)[0]):
    command, size = struct.unpack_from('<II', binary, offset)
    if command == 0x19:
        address, _, file_offset, file_size = struct.unpack_from('<QQQQ', binary, offset + 24)
        segments.append((address, file_offset, file_size))
    offset += size

def read(address, fmt):
    size = struct.calcsize(fmt)
    for start, file_offset, file_size in segments:
        if start <= address and address + size <= start + file_size:
            return struct.unpack_from(fmt, binary, file_offset + address - start)[0]
    raise ValueError(f'Unmapped address {address:#x}')

print(json.dumps({
    'source_sha256': hashlib.sha256(binary).hexdigest(),
    'minimum_step': {'address': '0x27229b338', 'value': read(0x27229b338, '<d')},
    'default_step': {'address': '0x2722a1010', 'value': read(0x2722a1010, '<d')},
    'stepped_case': {'address': '0x27229b3ec', 'value': read(0x27229b3ec, '<I')},
    'continuous_case': {'address': '0x27229b3f0', 'value': read(0x27229b3f0, '<I')},
    'maximum_step': {'instruction': '0x272283f48: fmov d1, #1.0', 'value': 1.0},
    'default_policy': {'instruction': '0x2722549d0: mov w9, #1', 'case': 'continuous'}
}, indent=2))
