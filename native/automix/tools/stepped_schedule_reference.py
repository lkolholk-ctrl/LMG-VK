#!/usr/bin/env python3
"""Original ARM grid, compaction and combined stepped sampler references.

Swift allocation/array-copy/refcount helpers are replaced. Grid math, value
comparisons and point selection execute original instructions. For compaction
only, 0x27227b000 returns supplied sampled points: curve/time evaluation is NOT
covered by that fixture. Combined cases execute the original grid, time map,
ramp zipper/evaluator and compactor with host libm imports. No OS boot or
Android emulator is used.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import random
import struct
from capstone import Cs, CS_ARCH_ARM64, CS_MODE_LITTLE_ENDIAN
from unicorn import Uc, UC_ARCH_ARM64, UC_MODE_ARM, UC_HOOK_CODE
from unicorn.arm64_const import *


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('macho', type=Path)
    parser.add_argument('output_dir', type=Path)
    args = parser.parse_args()
    binary = args.macho.read_bytes()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    u = Uc(UC_ARCH_ARM64, UC_MODE_ARM)
    offset = 32
    for _ in range(struct.unpack_from('<I', binary, 16)[0]):
        command, size = struct.unpack_from('<II', binary, offset)
        if command == 25 and binary[offset+8:offset+24].rstrip(b'\0') == b'__TEXT':
            vm, vs, fo, fs = struct.unpack_from('<QQQQ', binary, offset+24)
            u.mem_map(vm, (vs+4095)&~4095); u.mem_write(vm, binary[fo:fo+fs])
        offset += size
    hashes = {}
    for start, end in [(0x272252970, 0x272252adc), (0x272279d20, 0x27227a020),
                       (0x27227b000, 0x27227b2fc), (0x27227086c, 0x272270b28),
                       (0x272275f84, 0x272276144), (0x27224fb08, 0x27224fd64),
                       (0x272250300, 0x2722504fc), (0x2722509cc, 0x272250a44),
                       (0x27227a05c, 0x27227a3f4), (0x27227b34c, 0x27227b3c8),
                       (0x27227e888, 0x27227e8d8),
                       (0x272281200, 0x272281544), (0x2722815e0, 0x2722817bc),
                       (0x272282538, 0x27228255c)]:
        raw = bytes(u.mem_read(start, end-start))
        hashes[hex(start)] = hashlib.sha256(raw).hexdigest()
        for ins in Cs(CS_ARCH_ARM64, CS_MODE_LITTLE_ENDIAN).disasm(raw, start):
            if ins.mnemonic in ('pacibsp', 'autibsp'):
                u.mem_write(ins.address, bytes.fromhex('1f2003d5'))
            elif ins.mnemonic == 'retab':
                u.mem_write(ins.address, bytes.fromhex('c0035fd6'))
    for address, size in [(0x2743dd000, 4096), (0x2780e3000, 4096), (0x100000, 0x1000000)]:
        u.mem_map(address, size)
    empty, source, context, stop = 0x100000, 0x110000, 0x120000, 0x130000
    u.mem_write(0x2780e3c50, struct.pack('<Q', empty))
    heap = 0x200000
    sample_mode = False
    ramp_mode = False
    points_address = 0x140000
    def read64(address):
        return struct.unpack('<Q', u.mem_read(address, 8))[0]
    def alloc():
        nonlocal heap
        result = heap; heap += 0x10000
        if heap > 0x1000000:
            raise RuntimeError('Reference allocation limit')
        u.mem_write(result, bytes(32))
        return result
    def grow(old, stride):
        result = alloc()
        count = read64(old + 16)
        assert count < 1024
        u.mem_write(result, bytes(u.mem_read(old, 32 + count * stride)))
        u.mem_write(result+24, struct.pack('<Q', 2048))
        return result
    def hook(uc, address, size, data):
        x0, x1, x20 = (uc.reg_read(r) for r in (UC_ARM64_REG_X0, UC_ARM64_REG_X1, UC_ARM64_REG_X20))
        if address == 0x27227a2f4:
            if not ramp_mode:
                return
            uc.reg_write(UC_ARM64_REG_X0, x20)
            uc.reg_write(UC_ARM64_REG_PC, stop)
            return
        elif address == 0x272253f54:
            uc.reg_write(UC_ARM64_REG_X0, grow(uc.reg_read(UC_ARM64_REG_X3), 32))
        elif address in (0x2722545e4, 0x27227b2fc):
            uc.reg_write(UC_ARM64_REG_X0, 0)  # metadata only
        elif address == 0x2743ddc90:
            uc.reg_write(UC_ARM64_REG_X0, alloc())
        elif address == 0x27225470c:
            uc.reg_write(UC_ARM64_REG_X0, grow(uc.reg_read(UC_ARM64_REG_X3), 8))
        elif address in (0x272263c70, 0x272263c90, 0x272263278, 0x272263cf0, 0x272263cd0):
            old = read64(x20)
            stride = {0x272263c70: 32, 0x272263c90: 64, 0x272263278: 40,
                      0x272263cf0: 24, 0x272263cd0: 48}[address]
            uc.mem_write(x20, struct.pack('<Q', grow(old, stride)))
        elif address == 0x27227b000:
            if sample_mode:
                return  # execute original complete sampler
            uc.reg_write(UC_ARM64_REG_X0, source)
        elif address == 0x272290d8c:
            uc.mem_write(x0, bytes(uc.mem_read(x1, uc.reg_read(UC_ARM64_REG_W2))))
        elif address == 0x272271d7c:
            count = read64(x1+16)
            uc.reg_write(UC_ARM64_REG_X0, x1)
            uc.reg_write(UC_ARM64_REG_X1, x1+32)
            uc.reg_write(UC_ARM64_REG_X2, min(x0, count))
            uc.reg_write(UC_ARM64_REG_X3, count << 1)
        elif address == 0x27227b540:
            dst = read64(x20)
            n, m = read64(dst+16), read64(x0+16)
            uc.mem_write(dst+32+n*32, bytes(uc.mem_read(x0+32, m*32)))
            uc.mem_write(dst+16, struct.pack('<QQ', n+m, (n+m)*2))
        elif address == 0x2743dde70:
            uc.reg_write(UC_ARM64_REG_X0, int(x0 != empty))
        elif address in (0x2743ddb00, 0x2743ddb10, 0x2743ddb20, 0x2743ddb30, 0x2743ddc50, 0x2743ddc60):
            value = struct.unpack('<d', struct.pack('<Q', uc.reg_read(UC_ARM64_REG_D0)))[0]
            if address == 0x2743ddc50:
                exponent = struct.unpack('<d', struct.pack('<Q', uc.reg_read(UC_ARM64_REG_D1)))[0]
                result = math.pow(value, exponent)
            else:
                result = {0x2743ddb00: math.exp, 0x2743ddb10: math.exp2,
                          0x2743ddb20: math.log, 0x2743ddb30: math.log2,
                          0x2743ddc60: math.sqrt}[address](value)
            uc.reg_write(UC_ARM64_REG_D0, struct.unpack('<Q', struct.pack('<d', result))[0])
        elif address not in (0x2743ddd00, 0x2743ddd10, 0x2743ddce0, 0x2743ddea0, 0x2743ddf20, 0x2743ddf40):
            raise AssertionError(hex(address))
        uc.reg_write(UC_ARM64_REG_PC, uc.reg_read(UC_ARM64_REG_LR))
    hooks = [0x272263cf0, 0x272263cd0, 0x27227a2f4, 0x272253f54, 0x2722545e4, 0x27227b2fc, 0x2743ddc90, 0x27225470c,
             0x272263c70, 0x272263c90, 0x27227b000, 0x272290d8c,
             0x272271d7c, 0x27227b540, 0x2743dde70, 0x2743ddd00,
             0x2743ddce0, 0x2743ddea0, 0x2743ddf20, 0x2743ddf40, 0x2743ddd10,
             0x272263278, 0x2743ddb00, 0x2743ddb10, 0x2743ddb20,
             0x2743ddb30, 0x2743ddc50, 0x2743ddc60]
    for address in hooks:
        u.hook_add(UC_HOOK_CODE, hook, begin=address, end=address)
    def call(address, values, stride, step=0):
        nonlocal heap
        heap = 0x200000
        u.mem_write(empty, bytes(32))
        u.mem_write(source, bytes(16)+struct.pack('<QQ', len(values), len(values)*2))
        payload = b''.join(struct.pack('<'+'d'*(stride//8), *v) for v in values)
        if payload: u.mem_write(source+32, payload)
        u.reg_write(UC_ARM64_REG_X0, source)
        u.reg_write(UC_ARM64_REG_X1, context)
        u.reg_write(UC_ARM64_REG_X20, context)
        u.reg_write(UC_ARM64_REG_D0, struct.unpack('<Q', struct.pack('<d', step))[0])
        u.reg_write(UC_ARM64_REG_SP, 0x1f0000)
        u.reg_write(UC_ARM64_REG_LR, stop)
        try:
            u.emu_start(address, stop, count=1000000)
        except Exception as error:
            registers = [hex(u.reg_read(r)) for r in (UC_ARM64_REG_X11, UC_ARM64_REG_X19,
                         UC_ARM64_REG_X23, UC_ARM64_REG_X25, UC_ARM64_REG_X27, UC_ARM64_REG_X28)]
            raise RuntimeError(f'ARM failure at {u.reg_read(UC_ARM64_REG_PC):#x}; x11/19/23/25/27/28={registers}') from error
        assert u.reg_read(UC_ARM64_REG_PC) == stop
        result = u.reg_read(UC_ARM64_REG_X0)
        count = read64(result+16)
        assert count < 16384
        return payload, bytes(u.mem_read(result+32, count*stride)), count

    rng = random.Random(31)
    cases = []
    for step in [.0001, .2, .5, 1.]:
        for start in [-3.25, 0., 123.456789]:
            for span in [0., step*.5, step, step*1.5, step*2, step*5.7]:
                for fraction in [.01, .25, .5, .99]:
                    times = sorted(set([start, start+span*fraction, start+span]))
                    cases.append((step, times))
    cases += [(.2, []), (.2, [0.]), (.2, [0., .19999999999999998, .4]),
              (.2, [0., .2, math.nextafter(.4, 0.), 1.])]
    for _ in range(80):
        step = rng.choice([.0001, .2, .5, 1.])
        cases.append((step, sorted({rng.uniform(-1, 1)*step*20 for _ in range(4)})))
    records = []
    for step, times in cases:
        inp, out, count = call(0x272252970, [(v,) for v in times], 8, step)
        records.append(struct.pack('<dII', step, len(times), count)+inp+out)
    grid = struct.pack('<I', len(records))+b''.join(records)
    (args.output_dir/'stepped_grid.bin').write_bytes(grid)
    sequences = [[], [1.], [1., 1., 1.], [0., 1., 1., 0., 0.],
                 [0., -0., 0., 1., 1., -0.], [1., 1.+2**-52, 1., 1.]]
    for _ in range(90):
        sequences.append([rng.choice([0., .25, 1.]) for _ in range(rng.randrange(1, 80))])
    records = []
    for values in sequences:
        points = [(v, 100.+i*.25, 105.+i*.3, -2.+i*.3) for i,v in enumerate(values)]
        inp, out, count = call(0x272279d20, points, 32)
        records.append(struct.pack('<II', len(points), count)+inp+out)
    compact = struct.pack('<I', len(records))+b''.join(records)
    (args.output_dir/'stepped_compact.bin').write_bytes(compact)
    # Run original raw-point selection up to the zip stage, then execute the
    # original pair closure separately. Swift generic zip allocation is omitted.
    ramp_mode = True
    ramp_records = []
    for values in sequences:
        points = [(v, 100.+i*.25, 105.+i*.3, -2.+i*.3) for i,v in enumerate(values)]
        inp, selected, count = call(0x27227a05c, points, 32)
        ramps = []
        for i in range(max(0, count-1)):
            u.mem_write(0x150000, selected[i*32:(i+2)*32])
            u.reg_write(UC_ARM64_REG_X0, 0x150000)
            u.reg_write(UC_ARM64_REG_X1, 0x150020)
            u.reg_write(UC_ARM64_REG_X8, 0x160000)
            u.reg_write(UC_ARM64_REG_SP, 0x1f0000)
            u.reg_write(UC_ARM64_REG_LR, stop)
            u.emu_start(0x27227b34c, stop, count=10000)
            assert u.reg_read(UC_ARM64_REG_PC) == stop
            ramps.append(bytes(u.mem_read(0x160000, 64)))
        ramp_records.append(struct.pack('<II', len(points), len(ramps))+inp+b''.join(ramps))
    ramp_bytes = struct.pack('<I', len(ramp_records))+b''.join(ramp_records)
    (args.output_dir/'stepped_ramps.bin').write_bytes(ramp_bytes)
    ramp_mode = False
    sample_mode = True
    records = []
    for anchor in [(100., 105., 0.), (123.25, 120.5, 2.25)]:
        for rate in [None, (1., 1., anchor[0]+1, anchor[0]+5),
                     (.8, 1.2, anchor[0]+1, anchor[0]+5),
                     (1.2, .8, anchor[0]+1, anchor[0]+5)]:
            for curve in [0, 1, 2, 0x40, 0x41, 0x42, 0x80, 0x81, 0xfa]:
                for shape in range(3):
                    points = ([] if shape == 0 else
                              [(1., anchor[0]+1, curve), (8., anchor[0]+5, curve)] if shape == 1 else
                              [(1., anchor[0]+1, curve), (2., anchor[0]+1, curve),
                               (2., anchor[0]+3, curve), (1., anchor[0]+3, curve)])
                    times = [anchor[2]-1, anchor[2]+1, anchor[2]+4, anchor[2]+8]
                    step = .2
                    raw = bytearray(0x90)
                    struct.pack_into('<d', raw, 0x20, .75)
                    struct.pack_into('<QQd3d4dB', raw, 0x38, points_address, source,
                                     step, *anchor, *(rate or (0., 0., 0., 0.)), 0x80 if rate else 0xfc)
                    u.mem_write(context, bytes(raw))
                    u.mem_write(points_address, bytes(16)+struct.pack('<QQ', len(points), 2*len(points)))
                    for i, (v, t, c) in enumerate(points):
                        u.mem_write(points_address+32+i*24, struct.pack('<ddB7x', v, t, c))
                    # call() supplies landmarks, but the returned point stride is 32.
                    call(0x272279d20, [(t,) for t in times], 8, step)
                    ptr = u.reg_read(UC_ARM64_REG_X0); n = read64(ptr+16)
                    output = bytes(u.mem_read(ptr+32, n*32))
                    records.append(struct.pack('<IIId3d4d', int(rate is not None), curve, shape,
                                               step, *anchor, *(rate or (0., 0., 0., 0.)))+
                                   struct.pack('<I', n)+output)
    sampled = struct.pack('<I', len(records))+b''.join(records)
    (args.output_dir/'stepped_sampled.bin').write_bytes(sampled)
    speed_records = []
    for anchor in [(100., 105., 0.), (123.25, 120.5, 2.25)]:
        for rate in [None, (1., 1., anchor[0]+1, anchor[0]+5),
                     (.8, 1.2, anchor[0]+1, anchor[0]+5),
                     (1.2, .8, anchor[0]+1, anchor[0]+5)]:
            for step in [.2, .5, 1.]:
                for span in [.1, 3., 8.]:
                    times = [anchor[2]-1, anchor[2]-1+span*.3, anchor[2]-1+span]
                    u.mem_write(context, struct.pack('<3d4dB', *anchor,
                        *(rate or (0., 0., 0., 0.)), 0x80 if rate else 0xfc))
                    call(0x2722815e0, [(t,) for t in times], 8, step)
                    ptr = u.reg_read(UC_ARM64_REG_X0); n = read64(ptr+16)
                    output = bytes(u.mem_read(ptr+32, n*48))
                    rates = []
                    for i in range(n):
                        u.mem_write(0x150000, output[i*48:(i+1)*48])
                        u.reg_write(UC_ARM64_REG_X20, 0x150000)
                        u.reg_write(UC_ARM64_REG_LR, stop)
                        u.emu_start(0x272282538, stop, count=100)
                        assert u.reg_read(UC_ARM64_REG_PC) == stop
                        rates.append(struct.pack('<Q', u.reg_read(UC_ARM64_REG_D0)))
                    speed_records.append(struct.pack('<I3d4dddI', int(rate is not None),
                        *anchor, *(rate or (0., 0., 0., 0.)), step, span, n)+output+b''.join(rates))
    speed_bytes = struct.pack('<I', len(speed_records))+b''.join(speed_records)
    (args.output_dir/'stepped_speed.bin').write_bytes(speed_bytes)
    report = dict(binary_sha256=hashlib.sha256(binary).hexdigest(), code_sha256=hashes,
        speed_cases=len(speed_records), speed_sha256=hashlib.sha256(speed_bytes).hexdigest(),
        speed_scope="Original grid, mapping, time-range zip and playback-rate getter with host libm and Swift infrastructure hooks",
        grid_cases=len(cases), compact_cases=len(sequences),
        ramp_cases=len(ramp_records), ramps_sha256=hashlib.sha256(ramp_bytes).hexdigest(),
        ramp_scope="Original raw-point selection and pair-to-ramp closure; raw sampler supplied by fixture",
        sampled_cases=len(records), sampled_sha256=hashlib.sha256(sampled).hexdigest(),
        grid_sha256=hashlib.sha256(grid).hexdigest(), compact_sha256=hashlib.sha256(compact).hexdigest(),
        hooks=[hex(a) for a in hooks], scope=__doc__,
        sampled_scope='Original grid, time mapping, ramp zip/evaluation and compaction execute together; only Swift infrastructure and host libm are substituted')
    (args.output_dir/'stepped_schedule.json').write_text(json.dumps(report, indent=2)+'\n')
    print(f'{len(speed_records)} speed schedules; {len(ramp_records)} ramp sequences; {len(cases)} original grid cases; {len(sequences)} original compaction cases; {len(records)} complete sampler cases')


if __name__ == '__main__':
    main()
