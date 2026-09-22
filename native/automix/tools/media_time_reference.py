#!/usr/bin/env python3
"""Execute original CoreMedia numeric time conversion/comparison instructions.

Only PAC instructions are patched. No replacement math, allocation hooks,
CoreMedia OS runtime, Android emulator or network is used.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import random
import struct
from capstone import Cs, CS_ARCH_ARM64, CS_MODE_LITTLE_ENDIAN
from unicorn import Uc, UC_ARCH_ARM64, UC_MODE_ARM
from unicorn.arm64_const import *


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('cache_dir', type=Path)
    parser.add_argument('output_dir', type=Path)
    parser.add_argument('evidence_dir', type=Path)
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    args.evidence_dir.mkdir(parents=True, exist_ok=True)
    cache = args.cache_dir/'dyld_shared_cache_arm64e.07'
    base, length, file_offset = 0x196bc2000, 0x1eb040, 0x3456000
    with cache.open('rb') as f:
        header = f.read(104)
        f.seek(file_offset); text = f.read(length)
    assert text[:4] == bytes.fromhex('cffaedfe')
    u = Uc(UC_ARCH_ARM64, UC_MODE_ARM)
    u.mem_map(base, (length+4095)&~4095); u.mem_write(base, text)
    u.mem_map(0x100000, 0x20000)
    hashes = {}
    for first, last in [(0x196bf782c, 0x196bf79bc), (0x196bf8574, 0x196bf8620),
                        (0x196bf90dc, 0x196bf9154)]:
        raw = bytes(u.mem_read(first, last-first))
        hashes[hex(first)] = hashlib.sha256(raw).hexdigest()
        lines = [f'; CoreMedia iOS 23A341, SHA256 {hashes[hex(first)]}']
        for ins in Cs(CS_ARCH_ARM64, CS_MODE_LITTLE_ENDIAN).disasm(raw, first):
            lines.append(f'{ins.address:x}: {ins.bytes.hex()} {ins.mnemonic} {ins.op_str}')
            if ins.mnemonic == 'pacibsp':
                u.mem_write(ins.address, bytes.fromhex('1f2003d5'))
            elif ins.mnemonic == 'retab':
                u.mem_write(ins.address, bytes.fromhex('c0035fd6'))
        (args.evidence_dir/f'{first:x}.asm').write_text('\n'.join(lines)+'\n')
    def call(address):
        u.reg_write(UC_ARM64_REG_SP, 0x11e000)
        u.reg_write(UC_ARM64_REG_LR, 0x11f000)
        u.emu_start(address, 0x11f000, count=10000)
        assert u.reg_read(UC_ARM64_REG_PC) == 0x11f000
    values = [0., -0., 1e-300, -1e-300, 123.456789, -123.456789]
    for whole in [0., 1., 100., 3600., 100000.]:
        for nano in [0., .1, .49, .5, .51, .9, 1., 1.5, 2., 2.5, 7.9]:
            value = whole + nano*1e-9
            for near in [math.nextafter(value, -math.inf), value, math.nextafter(value, math.inf)]:
                values.extend([near, -near])
    for value in [(2**63)/1e9, (2**63)/5e8, 1e12, 1e16, 1e18, float(2**63)]:
        for near in [math.nextafter(value, 0.), value, math.nextafter(value, math.inf)]:
            if near <= 2**63:
                values.extend([near, -near])
    rng = random.Random(31)
    values += [rng.uniform(-100000, 100000) for _ in range(200)]
    records = []; converted = []
    for value in values:
        u.reg_write(UC_ARM64_REG_X8, 0x100000)
        u.reg_write(UC_ARM64_REG_W0, 1000000000)
        u.reg_write(UC_ARM64_REG_D0, struct.unpack('<Q', struct.pack('<d', value))[0])
        call(0x196bf782c)
        raw = bytes(u.mem_read(0x100000, 24))
        decoded = struct.unpack('<qiIq', raw)
        assert decoded[1] > 0 and decoded[2] in (1, 3) and decoded[3] == 0
        records.append(struct.pack('<d', value)+raw)
        converted.append(decoded)
    payload = struct.pack('<I', len(records))+b''.join(records)
    (args.output_dir/'media_time.bin').write_bytes(payload)

    pairs = []
    for value in [-2**63, -2**63+1, -1000000001, -1, 0, 1, 1000000001, 2**63-1]:
        for scale in [1, 3, 44100, 1000000000, 2**31-1]:
            for other in [1, 48000, 2**31-1]:
                pairs.append(((value, scale, 1, 0), (value, other, 3, 0)))
    pairs += [((1, 2, 1, 0), (2, 4, 3, 0)), ((-1, 2, 1, 0), (-2, 4, 3, 0)),
              ((1, 2, 1, -1), (-999, 3, 3, 0)), ((1, 2, 1, 1), (999, 3, 3, 0))]
    pairs += [(rng.choice(converted), rng.choice(converted)) for _ in range(300)]
    comparison = []
    for a, b in pairs:
        first, second = struct.pack('<qiIq', *a), struct.pack('<qiIq', *b)
        u.mem_write(0x100000, first); u.mem_write(0x100100, second)
        u.reg_write(UC_ARM64_REG_X0, 0x100000); u.reg_write(UC_ARM64_REG_X1, 0x100100)
        call(0x196bf8574)
        result = struct.unpack('<i', struct.pack('<I', u.reg_read(UC_ARM64_REG_W0)))[0]
        assert result in (-1, 0, 1)
        comparison.append(first+second+struct.pack('<i', result))
    compared = struct.pack('<I', len(comparison))+b''.join(comparison)
    (args.output_dir/'media_time_compare.bin').write_bytes(compared)
    (args.output_dir/'media_time.json').write_text(json.dumps(dict(
        cache_uuid=header[88:104].hex(), coremedia_text_sha256=hashlib.sha256(text).hexdigest(),
        code_sha256=hashes, conversion_cases=len(records), comparison_cases=len(comparison),
        conversion_sha256=hashlib.sha256(payload).hexdigest(), comparison_sha256=hashlib.sha256(compared).hexdigest(),
        scope='Numeric CMTime only, requested timescale 1e9; no hooks beyond PAC instructions'), indent=2)+'\n')
    print(f'{len(records)} original time conversions and {len(comparison)} original comparisons')


if __name__ == '__main__':
    main()
