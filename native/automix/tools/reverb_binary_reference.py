#!/usr/bin/env python3
"""Execute bounded recovered ARM64 functions to make offline reverb fixtures.

Requires capstone + unicorn and the iOS 23A341 cache parts already extracted by
inspect_system_aus.py. No OS, AudioUnit host, network, or Android emulator.
Only PAC/stack guard infrastructure and allocation/libm calls are replaced.
libm uses the host pow: fixtures validate algorithm/order, not Apple's libm ULPs.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import struct

from capstone import Cs, CS_ARCH_ARM64, CS_MODE_LITTLE_ENDIAN
from unicorn import Uc, UC_ARCH_ARM64, UC_MODE_ARM, UC_HOOK_CODE
from unicorn.arm64_const import *

BASE = 0x234F04000
OBJECT, RINGS, STACK, STOP = 0x100000, 0x200000, 0x400000, 0x500000
FLOAT = lambda v: struct.unpack('<f', struct.pack('<f', v))[0]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('cache_dir', type=Path)
    parser.add_argument('output_dir', type=Path)
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    u = Uc(UC_ARCH_ARM64, UC_MODE_ARM)
    decoder = Cs(CS_ARCH_ARM64, CS_MODE_LITTLE_ENDIAN)
    u.mem_map(BASE, 0x120000)
    with (args.cache_dir / 'dyld_shared_cache_arm64e.48').open('rb') as f:
        f.seek(0x4dfc000)
        u.mem_write(BASE, f.read(0x11ee74))
    u.mem_map(0x18ce2c000, 0x6900000)
    with (args.cache_dir / 'dyld_shared_cache_arm64e.05').open('rb') as f:
        f.seek(16); offset, count = struct.unpack('<II', f.read(8))
        f.seek(offset); mappings = [struct.unpack('<QQQII', f.read(32)) for _ in range(count)]
        # Only required text pages; mapped virtual range is mostly uncommitted.
        for address, size in [(0x18e6a6000, 0x1000), (0x18e6f2000, 0x1000)]:
            vm, length, file_offset, *_ = next(m for m in mappings if m[0] <= address < m[0] + m[1])
            f.seek(file_offset + address - vm); u.mem_write(address, f.read(size))
    u.mem_map(OBJECT, 0x10000)
    u.mem_map(RINGS, 0x100000)
    u.mem_map(STACK, 0x10000)
    u.mem_map(STOP, 0x1000)
    u.mem_map(0x236f45000, 0x2000)
    u.mem_map(0x1eae9f000, 0x1000)
    u.mem_map(0x2780e4000, 0x1000)
    u.mem_write(0x2780e4a88, struct.pack('<Q', OBJECT + 0xf000))
    # Exact default RNG configuration from .33.dylddata: type 3, degree 31,
    # separation 3. State pointer relocation is infrastructure, not RNG math.
    rng = OBJECT + 0xe000
    u.mem_write(0x1eae9f410, struct.pack('<QiiiIQ', rng, 3, 31, 3, 0, rng + 124))
    ranges = [(0x234fe3ff4, 0x234fe4ce4), (0x234fd4ac0, 0x234fd4b78),
              (0x18e6a67d4, 0x18e6a6894), (0x18e6f2804, 0x18e6f28e8)]
    hashes = {}
    for begin, end in ranges:
        raw = bytes(u.mem_read(begin, end - begin))
        hashes[hex(begin)] = hashlib.sha256(raw).hexdigest()
        for instruction in decoder.disasm(raw, begin):
            if instruction.mnemonic == 'pacibsp':
                u.mem_write(instruction.address, bytes.fromhex('1f2003d5'))
            elif instruction.mnemonic == 'retab':
                u.mem_write(instruction.address, bytes.fromhex('c0035fd6'))

    def dget(register):
        return struct.unpack('<d', struct.pack('<Q', u.reg_read(register)))[0]
    def dset(register, value):
        u.reg_write(register, struct.unpack('<Q', struct.pack('<d', value))[0])
    def sset(register, value):
        u.reg_write(register, struct.unpack('<I', struct.pack('<f', value))[0])
    cursor = RINGS
    def hook(uc, address, size, data):
        nonlocal cursor
        if address == 0x236f45bd0:
            dset(UC_ARM64_REG_D0, math.pow(dget(UC_ARM64_REG_D0), dget(UC_ARM64_REG_D1)))
        elif address == 0x236f454f0:
            dset(UC_ARM64_REG_D0, math.pow(10, dget(UC_ARM64_REG_D0)))
        elif address == 0x236f45cd0:
            uc.reg_write(UC_ARM64_REG_PC, 0x18e6f2804); return
        elif address == 0x236f45c50:
            uc.reg_write(UC_ARM64_REG_PC, 0x18e6a67d4); return
        elif address == 0x236f45670:
            uc.mem_write(uc.reg_read(UC_ARM64_REG_X0), bytes(uc.reg_read(UC_ARM64_REG_X1)))
        elif address == 0x234fe4bcc:
            # Execute the original prime selector separately (no nested emu),
            # equivalently use its raw complete uint16 prime table here.
            n = uc.reg_read(UC_ARM64_REG_W1)
            prime = next(p for p in primes if p >= n)
            capacity = 1 << (prime - 1).bit_length()
            line = uc.reg_read(UC_ARM64_REG_X0)
            uc.mem_write(line, struct.pack('<Q', cursor))
            uc.mem_write(line + 0x24, struct.pack('<II', capacity, capacity - 1))
            uc.mem_write(line + 0x34, struct.pack('<Id', prime, prime / dget(UC_ARM64_REG_D0)))
            uc.mem_write(cursor, bytes(capacity * 4))
            cursor += capacity * 4
        else:
            return
        uc.reg_write(UC_ARM64_REG_PC, uc.reg_read(UC_ARM64_REG_LR))

    primes = struct.unpack('<6542H', bytes(u.mem_read(0x235002f04, 6542 * 2)))
    # Hooks only on replaced entrypoints, keeping instruction execution cheap.
    for a in [0x236f45bd0, 0x236f454f0, 0x236f45cd0, 0x236f45c50, 0x236f45670, 0x234fe4bcc]:
        u.hook_add(UC_HOOK_CODE, hook, begin=a, end=a)

    def call(address):
        u.reg_write(UC_ARM64_REG_SP, STACK + 0xf000)
        u.reg_write(UC_ARM64_REG_LR, STOP)
        u.emu_start(address, STOP, count=10000000)
        if u.reg_read(UC_ARM64_REG_PC) != STOP:
            raise RuntimeError('Instruction limit reached')

    reports = []
    cases = [(48000, .008, .05, 1, 1, .5, 100, 2),
             (44100, .008, .05, 0, 1.7, .2, 37, 1),
             (48000, .0001, .0001, 0xffffffff, .8, 1.2, 65, 3)]
    for case_id, (rate, minimum, maximum, seed, low, high, wet, layout) in enumerate(cases):
        cursor = RINGS
        u.mem_write(OBJECT, bytes(0x1000))
        u.reg_write(UC_ARM64_REG_X0, OBJECT); u.reg_write(UC_ARM64_REG_W1, seed)
        sset(UC_ARM64_REG_S0, minimum); sset(UC_ARM64_REG_S1, maximum); dset(UC_ARM64_REG_D2, rate)
        call(0x234fe4068)
        delays = [struct.unpack('<I', u.mem_read(OBJECT + 0x70 + i * 0x48 + 0x34, 4))[0] for i in range(16)]
        u.reg_write(UC_ARM64_REG_X0, OBJECT)
        sset(UC_ARM64_REG_S0, low); sset(UC_ARM64_REG_S1, high)
        u.mem_write(OBJECT + 0x18, struct.pack('<f', 1))
        call(0x234fe41c8)
        u.reg_write(UC_ARM64_REG_X0, OBJECT); call(0x234fe3ff4)
        u.mem_write(OBJECT + 0x20, struct.pack('<f', FLOAT(FLOAT(wet) * FLOAT(.01))))
        count = 4096
        left = [FLOAT(((i * 37) % 251 - 125) / 256) if i < 600 else 0 for i in range(count)]
        right = [FLOAT(((i * 19) % 127 - 63) / 128) if i < 900 else 0 for i in range(count)]
        ptrs = [OBJECT + offset for offset in (0x1000, 0x5000, 0x9000, 0x10000)]
        # Last output needs its own mapped range.
        if case_id == 0: u.mem_map(OBJECT + 0x10000, 0x4000)
        u.mem_write(ptrs[0], struct.pack('<4096f', *left))
        u.mem_write(ptrs[1], struct.pack('<4096f', *right))
        u.reg_write(UC_ARM64_REG_X0, OBJECT); u.reg_write(UC_ARM64_REG_W1, count)
        u.reg_write(UC_ARM64_REG_X2, ptrs[0])
        if layout == 2:
            for reg, ptr in zip((UC_ARM64_REG_X3, UC_ARM64_REG_X4, UC_ARM64_REG_X5), ptrs[1:]): u.reg_write(reg, ptr)
            entry = 0x234fe431c
        else:
            u.reg_write(UC_ARM64_REG_X3, ptrs[2]); u.reg_write(UC_ARM64_REG_X4, ptrs[3])
            entry = 0x234fe4834 if layout == 1 else 0x234fe45b0
        call(entry)
        output = bytes(u.mem_read(ptrs[2], count * 4))
        if layout != 1: output += bytes(u.mem_read(ptrs[3], count * 4))
        filename = f'reverb_{case_id}.bin'
        (args.output_dir / filename).write_bytes(output)
        reports.append(dict(rate=rate, minimum=minimum, maximum=maximum, seed=seed,
                            low=low, high=high, wet=wet, layout=layout, delays=delays,
                            filename=filename, sha256=hashlib.sha256(output).hexdigest()))
    (args.output_dir / 'reverb_reference.json').write_text(json.dumps(dict(code_hashes=hashes, cases=reports), indent=2) + '\n')
    print(json.dumps(reports, indent=2))


if __name__ == '__main__':
    main()
