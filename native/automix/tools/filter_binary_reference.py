#!/usr/bin/env python3
"""Offline coefficient/cascade fixtures from bounded original AUFilter ARM code.

No OS is booted. AU parameter/rate getters supply test inputs; libm calls use
host math. Actual coefficient update, dispatcher, and five-stage sample routine
execute in Unicorn. PAC is removed only from the selected routines.
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


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('cache_dir', type=Path)
    parser.add_argument('output_dir', type=Path)
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    u = Uc(UC_ARCH_ARM64, UC_MODE_ARM)
    base = 0x234f04000
    u.mem_map(base, 0x120000)
    with (args.cache_dir / 'dyld_shared_cache_arm64e.48').open('rb') as f:
        f.seek(0x4dfc000); u.mem_write(base, f.read(0x11ee74))
    u.mem_map(0x236f45000, 0x2000)
    u.mem_map(0x100000, 0x10000)
    decoder = Cs(CS_ARCH_ARM64, CS_MODE_LITTLE_ENDIAN)
    ranges = [(0x234f39304, 0x234f39574), (0x234f395e8, 0x234f39724),
              (0x234f896c4, 0x234f899a8), (0x234fee094, 0x234fee570)]
    hashes = {}
    for begin, end in ranges:
        raw = bytes(u.mem_read(begin, end - begin))
        hashes[hex(begin)] = hashlib.sha256(raw).hexdigest()
        for instruction in decoder.disasm(raw, begin):
            if instruction.mnemonic in ('pacibsp', 'autibsp'):
                u.mem_write(instruction.address, bytes.fromhex('1f2003d5'))
            elif instruction.mnemonic == 'retab':
                u.mem_write(instruction.address, bytes.fromhex('c0035fd6'))

    functions = {0x236f454f0: lambda x: math.pow(10, x), 0x236f45690: math.cos,
                 0x236f45c80: math.sin, 0x236f45ca0: math.sinh}
    parameters, rate = [], 48000
    def hook(uc, address, size, data):
        if address == 0x234f395a0:
            value = parameters[uc.reg_read(UC_ARM64_REG_W1)]
            uc.reg_write(UC_ARM64_REG_S0, struct.unpack('<I', struct.pack('<f', value))[0])
        else:
            if address == 0x234f39574:
                value = rate
            else:
                arg = struct.unpack('<d', struct.pack('<Q', uc.reg_read(UC_ARM64_REG_D0)))[0]
                value = functions[address](arg)
            uc.reg_write(UC_ARM64_REG_D0, struct.unpack('<Q', struct.pack('<d', value))[0])
        uc.reg_write(UC_ARM64_REG_PC, uc.reg_read(UC_ARM64_REG_LR))
    for address in list(functions) + [0x234f395a0, 0x234f39574]:
        u.hook_add(UC_HOOK_CODE, hook, begin=address, end=address)

    def call(address):
        u.reg_write(UC_ARM64_REG_SP, 0x10e000)
        u.reg_write(UC_ARM64_REG_LR, 0x10f000)
        u.emu_start(address, 0x10f000, count=10000000)
        if u.reg_read(UC_ARM64_REG_PC) != 0x10f000:
            raise RuntimeError('Instruction limit reached')

    defaults = [0,100,0,625,0,2,2500,0,2,5000,0,2,0,10000,0]
    cases = [(48000, {}, 1024),
             (44100, {3:2500,4:-18,5:1.7}, 1024),
             (48000.125, {2:6,4:12,7:-3,10:9,14:-12}, 1024),
             (48000, {0:1,1:40,2:3,12:1,13:7000,14:6}, 1024),
             (8000, {1:0,3:9000,4:4,5:.05,8:0,9:-2,13:20000,14:3}, 73)]
    report = []
    for index, (rate, changes, count) in enumerate(cases):
        parameters = defaults.copy()
        for key, value in changes.items(): parameters[key] = value
        parameters = list(struct.unpack('<15f', struct.pack('<15f', *parameters)))
        # Kernel at 0x100000, its AU owner at 0x101000. Force initial update.
        u.mem_write(0x100000, bytes(0x2000))
        u.mem_write(0x100008, struct.pack('<Q', 0x101000))
        u.mem_write(0x100180, struct.pack('<IIQ', 0xffffffff, 0, 0x101000))
        u.reg_write(UC_ARM64_REG_X0, 0x100000)
        u.reg_write(UC_ARM64_REG_X1, 0x100018)
        u.reg_write(UC_ARM64_REG_W2, 0)
        call(0x234f393b0)
        coefficients = b''.join(bytes(u.mem_read(0x100018 + i * 0x48, 40)) for i in range(5))
        signal = [((i * 37) % 251 - 125) / 256 if i < 600 else 0 for i in range(count)]
        u.mem_write(0x102000, struct.pack(f'<{count}f', *signal))
        u.reg_write(UC_ARM64_REG_X0, 0x100000)
        u.reg_write(UC_ARM64_REG_X1, 0x102000)
        u.reg_write(UC_ARM64_REG_X2, 0x102000)
        u.reg_write(UC_ARM64_REG_W3, count)
        call(0x234f39304)
        payload = struct.pack('<d15fI', rate, *parameters, count) + coefficients
        payload += bytes(u.mem_read(0x102000, count * 4))
        name = f'filter_{index}.bin'
        (args.output_dir / name).write_bytes(payload)
        report.append(dict(filename=name, rate=rate, parameters=parameters, frames=count,
                           sha256=hashlib.sha256(payload).hexdigest()))
    (args.output_dir / 'filter_reference.json').write_text(
        json.dumps(dict(code_hashes=hashes, cases=report), indent=2) + '\n')
    print(f'{len(cases)} original ARM coefficient/cascade fixtures generated')


if __name__ == '__main__':
    main()
