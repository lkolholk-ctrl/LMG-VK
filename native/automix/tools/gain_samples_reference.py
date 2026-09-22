#!/usr/bin/env python3
"""Run the original bounded ramp multiplier. No libm or sample-operation hooks."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
from capstone import Cs, CS_ARCH_ARM64, CS_MODE_LITTLE_ENDIAN
from unicorn import Uc, UC_ARCH_ARM64, UC_MODE_ARM
from unicorn.arm64_const import *


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('cache_dir', type=Path)
    parser.add_argument('output_dir', type=Path)
    args = parser.parse_args()
    u = Uc(UC_ARCH_ARM64, UC_MODE_ARM)
    with (args.cache_dir / 'dyld_shared_cache_arm64e.48').open('rb') as f:
        f.seek(16); offset, count = struct.unpack('<II', f.read(8))
        f.seek(offset); mappings = [struct.unpack('<QQQII', f.read(32)) for _ in range(count)]
        for address in [0x234b33000, 0x234aec000, 0x234acb000, 0x234ae6000, 0x234ae7000]:
            vm, size, file_offset, *_ = next(m for m in mappings if m[0] <= address < m[0] + m[1])
            f.seek(file_offset + address - vm)
            u.mem_map(address, 0x1000); u.mem_write(address, f.read(0x1000))
    raw = bytes(u.mem_read(0x234b33380, 0x300))
    digest = hashlib.sha256(raw).hexdigest()
    assert digest == '8721c0c6d2b733c6cbb7dbb92e7b886701688cefca03912db86c36117baa5edc'
    for ins in Cs(CS_ARCH_ARM64, CS_MODE_LITTLE_ENDIAN).disasm(raw, 0x234b33380):
        if ins.mnemonic == 'pacibsp': u.mem_write(ins.address, bytes.fromhex('1f2003d5'))
        elif ins.mnemonic == 'retab': u.mem_write(ins.address, bytes.fromhex('c0035fd6'))
    steady_hashes = {}
    for begin, end in [(0x234acb870, 0x234acbc50), (0x234ae6db0, 0x234ae7020)]:
        code = bytes(u.mem_read(begin, end - begin))
        steady_hashes[hex(begin)] = hashlib.sha256(code).hexdigest()
        for ins in Cs(CS_ARCH_ARM64, CS_MODE_LITTLE_ENDIAN).disasm(code, begin):
            if ins.mnemonic == 'pacibsp': u.mem_write(ins.address, bytes.fromhex('1f2003d5'))
            elif ins.mnemonic == 'retab': u.mem_write(ins.address, bytes.fromhex('c0035fd6'))
    u.mem_map(0x100000, 0x10000)
    lengths = list(range(68)) + [95, 96, 97, 127, 128, 129, 255, 256, 257, 1024]
    records = []
    for length in lengths:
        for alignment in range(4):
            for start, step in [(.713, -.00037), (.25, .001303), (-0.0, 0.0)]:
                source, output = 0x102004, 0x104000 + alignment * 4
                values = [((i * 37) % 251 - 125) / 256 for i in range(length)]
                if length: u.mem_write(source, struct.pack(f'<{length}f', *values))
                u.mem_write(0x100000, struct.pack('<ff', start, step))
                for reg, value in [(UC_ARM64_REG_X0, source), (UC_ARM64_REG_X1, 1),
                                   (UC_ARM64_REG_X2, 0x100000), (UC_ARM64_REG_X3, 0x100004),
                                   (UC_ARM64_REG_X4, output), (UC_ARM64_REG_X5, 1),
                                   (UC_ARM64_REG_X6, length), (UC_ARM64_REG_SP, 0x10e000),
                                   (UC_ARM64_REG_LR, 0x10f000)]:
                    u.reg_write(reg, value)
                u.emu_start(0x234b33380, 0x10f000, count=100000)
                assert u.reg_read(UC_ARM64_REG_PC) == 0x10f000
                records.append(struct.pack('<IIff', length, alignment, start, step) +
                               bytes(u.mem_read(0x100000, 4)) +
                               (bytes(u.mem_read(output, length * 4)) if length else b''))
    payload = struct.pack('<I', len(records)) + b''.join(records)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    (args.output_dir / 'gain_ramp.bin').write_bytes(payload)
    (args.output_dir / 'gain_ramp.json').write_text(json.dumps(dict(
        code_sha256=digest, fixture_sha256=hashlib.sha256(payload).hexdigest(),
        cases=len(records), lengths=lengths, destination_offsets=[0,4,8,12],
        source_offset=4, replacements=['PAC entry/return only']), indent=2) + '\n')
    print(f'{len(records)} ARM ramp cases, {len(payload)} fixture bytes')
    records = []
    for length in lengths:
        for alignment in range(4):
            gain = .713
            source, addend, output = 0x102004, 0x106000, 0x104000 + alignment * 4
            values = [((i * 37) % 251 - 125) / 256 for i in range(length)]
            additions = [((i * 19) % 127 - 63) / 128 for i in range(length)]
            if length:
                u.mem_write(source, struct.pack(f'<{length}f', *values))
                u.mem_write(addend, struct.pack(f'<{length}f', *additions))
            u.mem_write(0x100000, struct.pack('<f', gain))
            outputs = []
            for entry, args_values in [
                (0x234acb870, [source,1,0x100000,output,1,length]),
                (0x234ae6db0, [source,1,0x100000,addend,1,output,1,length])]:
                for reg, value in zip([UC_ARM64_REG_X0,UC_ARM64_REG_X1,UC_ARM64_REG_X2,
                                       UC_ARM64_REG_X3,UC_ARM64_REG_X4,UC_ARM64_REG_X5,
                                       UC_ARM64_REG_X6,UC_ARM64_REG_X7], args_values): u.reg_write(reg, value)
                u.reg_write(UC_ARM64_REG_SP, 0x10e000); u.reg_write(UC_ARM64_REG_LR, 0x10f000)
                u.emu_start(entry, 0x10f000, count=100000)
                assert u.reg_read(UC_ARM64_REG_PC) == 0x10f000
                outputs.append(bytes(u.mem_read(output, length * 4)) if length else b'')
            records.append(struct.pack('<IIf', length, alignment, gain) + b''.join(outputs))
    payload = struct.pack('<I', len(records)) + b''.join(records)
    (args.output_dir / 'gain_steady.bin').write_bytes(payload)
    (args.output_dir / 'gain_steady.json').write_text(json.dumps(dict(
        code_sha256=steady_hashes, fixture_sha256=hashlib.sha256(payload).hexdigest(),
        cases=len(records), replacements=['PAC entry/return only']), indent=2) + '\n')
    print(f'{len(records)} ARM steady/mix cases, {len(payload)} fixture bytes')


if __name__ == '__main__': main()
