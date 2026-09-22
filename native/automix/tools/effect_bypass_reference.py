#!/usr/bin/env python3
"""Verify the original AUEffectBase bypass setter and channel reset functions.

Executes bounded ARM instructions; replaces pointer authentication, virtual
dispatch infrastructure and bzero. Does not execute the outer AudioUnit host.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct
from capstone import Cs, CS_ARCH_ARM64, CS_MODE_LITTLE_ENDIAN
from unicorn import Uc, UC_ARCH_ARM64, UC_MODE_ARM, UC_HOOK_CODE
from unicorn.arm64_const import *


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('cache_dir', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    u = Uc(UC_ARCH_ARM64, UC_MODE_ARM)
    base, obj, stack, stop = 0x234f04000, 0x100000, 0x200000, 0x300000
    u.mem_map(base, 0x120000)
    with (args.cache_dir / 'dyld_shared_cache_arm64e.48').open('rb') as f:
        f.seek(0x4dfc000); u.mem_write(base, f.read(0x11ee74))
    for address in (obj, stack, stop, 0x236f45000):
        u.mem_map(address, 0x10000)
    ranges = [(0x234ff5d3c, 0x234ff5e44), (0x234f39214, 0x234f3921c),
              (0x234f684f0, 0x234f684fc), (0x234fc5788, 0x234fc5794),
              (0x234fded20, 0x234fded34), (0x234f39724, 0x234f39748)]
    hashes = {}
    decoder = Cs(CS_ARCH_ARM64, CS_MODE_LITTLE_ENDIAN)
    for begin, end in ranges:
        raw = bytes(u.mem_read(begin, end - begin))
        hashes[hex(begin)] = hashlib.sha256(raw).hexdigest()
        for instruction in decoder.disasm(raw, begin):
            if instruction.mnemonic in ('pacibsp', 'autibsp', 'autda'):
                u.mem_write(instruction.address, bytes.fromhex('1f2003d5'))
            elif instruction.mnemonic == 'retab':
                u.mem_write(instruction.address, bytes.fromhex('c0035fd6'))
            elif instruction.mnemonic == 'blraa':
                assert instruction.op_str == 'x8, x17'
                u.mem_write(instruction.address, bytes.fromhex('00013fd6'))  # blr x8
    resets = []
    def hook(uc, address, size, data):
        if address == stop + 0x100:
            resets.append(True)
        else:
            uc.mem_write(uc.reg_read(UC_ARM64_REG_X0), bytes(uc.reg_read(UC_ARM64_REG_W1)))
        uc.reg_write(UC_ARM64_REG_PC, uc.reg_read(UC_ARM64_REG_LR))
    for address in (stop + 0x100, 0x236f45670):
        u.hook_add(UC_HOOK_CODE, hook, begin=address, end=address)
    def call(address):
        u.reg_write(UC_ARM64_REG_SP, stack + 0xf000)
        u.reg_write(UC_ARM64_REG_LR, stop)
        u.emu_start(address, stop, count=1000)
        assert u.reg_read(UC_ARM64_REG_PC) == stop
    records = []
    for initialized in (0, 1):
        u.mem_write(obj, bytes(0x10000))
        u.mem_write(obj, struct.pack('<Q', obj + 0x1000))
        u.mem_write(obj + 0x1048, struct.pack('<Q', stop + 0x100))
        u.mem_write(obj + 0x1248, struct.pack('<Q', 0x234f39214))
        u.mem_write(obj + 0x11, bytes([initialized]))
        previous = False
        for value in (0, 1, 1, 0, 0, 7, 0):
            u.mem_write(obj + 0x2000, struct.pack('<I', value))
            for register, v in ((UC_ARM64_REG_X0, obj), (UC_ARM64_REG_W1, 21),
                                (UC_ARM64_REG_W2, 0), (UC_ARM64_REG_W3, 0),
                                (UC_ARM64_REG_X4, obj + 0x2000), (UC_ARM64_REG_W5, 4)):
                u.reg_write(register, v)
            resets.clear(); call(0x234ff5d3c)
            assert u.reg_read(UC_ARM64_REG_W0) == 0
            assert bytes(u.mem_read(obj + 0x228, 1)) == bytes([bool(value)])
            assert bool(resets) == bool(initialized and previous and not value)
            records.append(dict(initialized=initialized, previous=previous,
                                value=value, reset=bool(resets)))
            previous = bool(value)
    for address, offsets in [(0x234f684f0, [0x40]), (0x234fc5788, [0x40]),
                              (0x234f39724, [0x40, 0x88, 0xd0, 0x118, 0x160])]:
        raw = bytearray([0x5a] * 0x200)
        u.mem_write(obj, bytes(raw)); u.reg_write(UC_ARM64_REG_X0, obj); call(address)
        for offset in offsets:
            raw[offset:offset + 32] = bytes(32)
        assert bytes(u.mem_read(obj, len(raw))) == raw
    raw = bytearray([0x5a] * 0x80)
    struct.pack_into('<QI', raw, 0x20, obj + 0x2000, 128)
    u.mem_write(obj, bytes(raw)); u.mem_write(obj + 0x2000, bytes([0x5a] * 128))
    u.reg_write(UC_ARM64_REG_X0, obj); call(0x234fded20)
    raw[0x38:0x3c] = bytes(4)
    assert bytes(u.mem_read(obj, len(raw))) == raw  # circular indices preserved
    assert bytes(u.mem_read(obj + 0x2000, 128)) == bytes(128)
    args.output.write_text(json.dumps(dict(code_sha256=hashes, setter_cases=records,
        kernel_resets=['lowpass', 'highpass', 'filter', 'delay'],
        scope='Original setter and reset instructions; virtual reset callback recorded'), indent=2) + '\n')
    print('14 original setter cases and 4 original channel resets passed')


if __name__ == '__main__':
    main()
