#!/usr/bin/env python3
"""Execute original AU property setter and bypass control flow, not DSP math.

The active DSP body is skipped at 0x234fe393c; the independently validated tank
is not under test here. Bus lookup, memcpy and reset/fade calls are intercepted.
No Android emulator, OS boot, network or audio service is involved.
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
        f.seek(0x4dfc000)
        u.mem_write(base, f.read(0x11ee74))
    u.mem_map(obj, 0x10000)
    u.mem_map(stack, 0x10000)
    u.mem_map(stop, 0x1000)
    u.mem_map(0x2780e4000, 0x1000)
    u.mem_write(0x2780e4a88, struct.pack('<Q', obj + 0xf000))
    hashes = {}
    decoder = Cs(CS_ARCH_ARM64, CS_MODE_LITTLE_ENDIAN)
    for begin, end in [(0x234fe37c8, 0x234fe3ff4), (0x234fe4d2c, 0x234fe4ec8)]:
        raw = bytes(u.mem_read(begin, end - begin))
        hashes[hex(begin)] = hashlib.sha256(raw).hexdigest()
        for instruction in decoder.disasm(raw, begin):
            if instruction.mnemonic == 'pacibsp':
                u.mem_write(instruction.address, bytes.fromhex('1f2003d5'))
            elif instruction.mnemonic == 'retab':
                u.mem_write(instruction.address, bytes.fromhex('c0035fd6'))

    events = []
    def hook(uc, address, size, data):
        if address == 0x234f0db38:
            bus = obj + (0x1000 if uc.reg_read(UC_ARM64_REG_X0) == obj + 0x50 else 0x1100)
            uc.reg_write(UC_ARM64_REG_X0, bus)
            uc.reg_write(UC_ARM64_REG_W1, 1)
        elif address == 0x234ffc4fc:
            dst, src, count = (uc.reg_read(r) for r in (UC_ARM64_REG_X0, UC_ARM64_REG_X1, UC_ARM64_REG_X2))
            uc.mem_write(dst, bytes(uc.mem_read(src, count)))
            events.append('copy')
        elif address == 0x234fe393c:
            events.append('active')
            uc.reg_write(UC_ARM64_REG_PC, 0x234fe3fa8)
            return
        elif address == 0x234fe3ff4:
            events.append('reset')
        elif address == 0x234fe4a94:
            events.append('fade_in' if uc.reg_read(UC_ARM64_REG_W2) else 'fade_out')
        else:
            raise AssertionError(hex(address))
        uc.reg_write(UC_ARM64_REG_PC, uc.reg_read(UC_ARM64_REG_LR))

    for address in (0x234f0db38, 0x234ffc4fc, 0x234fe393c, 0x234fe3ff4, 0x234fe4a94):
        u.hook_add(UC_HOOK_CODE, hook, begin=address, end=address)

    def call(address, registers):
        for register, value in registers.items():
            u.reg_write(register, value)
        u.reg_write(UC_ARM64_REG_SP, stack + 0xf000)
        u.reg_write(UC_ARM64_REG_LR, stop)
        u.emu_start(address, stop, count=10000)
        assert u.reg_read(UC_ARM64_REG_PC) == stop
        assert u.reg_read(UC_ARM64_REG_W0) == 0

    records = []
    for inputs, outputs in [(1, 1), (1, 2), (2, 2)]:
        u.mem_write(obj, bytes(0x10000))
        u.mem_write(obj + 0x106c, struct.pack('<I', inputs))
        u.mem_write(obj + 0x116c, struct.pack('<I', outputs))
        # AudioBufferList headers: count, padding, channel count, byte count, data.
        for at, channels, left, right in [(0x2000, inputs, 0x4000, 0x5000),
                                          (0x2100, outputs, 0x6000, 0x7000)]:
            u.mem_write(obj + at, struct.pack('<IIIIQIIQ', channels, 0, 1, 28,
                                             obj + left, 1, 28, obj + right))
        u.mem_write(obj + 0x3000, struct.pack('<QQ', obj + 0x2000, obj + 0x2100))
        left = struct.pack('<7f', 1, -.5, .25, -0., 0., 2, -3)
        right = struct.pack('<7f', -.25, 2, -1, 0., -0., 4, -2)
        u.mem_write(obj + 0x4000, left)
        u.mem_write(obj + 0x5000, right)
        for bypass in [0, 1, 1, 0, 1, 0, 0]:
            u.mem_write(obj + 0x3100, struct.pack('<I', bypass))
            call(0x234fe4d2c, {UC_ARM64_REG_X0: obj, UC_ARM64_REG_W1: 21,
                              UC_ARM64_REG_W2: 0, UC_ARM64_REG_W3: 0,
                              UC_ARM64_REG_X4: obj + 0x3100, UC_ARM64_REG_W5: 4})
            events.clear()
            call(0x234fe37c8, {UC_ARM64_REG_X0: obj, UC_ARM64_REG_X1: 0,
                              UC_ARM64_REG_W2: 7, UC_ARM64_REG_W3: 1,
                              UC_ARM64_REG_X4: obj + 0x3000, UC_ARM64_REG_W5: 1,
                              UC_ARM64_REG_X6: obj + 0x3008})
            flags = list(u.mem_read(obj + 0x230, 2))
            assert flags == [bypass, 0], flags
            assert events == (['copy'] * outputs if bypass else ['active']), events
            if bypass:
                assert bytes(u.mem_read(obj + 0x6000, 28)) == left
                if outputs == 2:
                    assert bytes(u.mem_read(obj + 0x7000, 28)) == (right if inputs == 2 else left)
            records.append(dict(inputs=inputs, outputs=outputs, bypass=bypass,
                                flags=flags, events=list(events)))
    args.output.write_text(json.dumps(dict(code_sha256=hashes,
        scope='Setter and serial block-boundary bypass control flow; active DSP body skipped',
        cases=records), indent=2) + '\n')
    print(f'{len(records)} original ARM bypass control-flow cases passed')


if __name__ == '__main__':
    main()
