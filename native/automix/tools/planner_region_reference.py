#!/usr/bin/env python3
"""Reproduce checked-in scalar fixtures from supplied ARM64 *text* slices.

This small interpreter does NOT execute Apple binaries. It models the listed
integer instructions and flags; source getters/refcounts are not executed.
Each injected getter boundary is declared below. With --evidence-zip, the text
slices are checked byte-for-byte against the supplied original disassembly.
No network and no dependency on emulator packages. --check never writes.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import random
import re
import sys
import zipfile

MASK = (1 << 64) - 1
MAX = (1 << 63) - 1
HERE = Path(__file__).resolve().parents[1] / 'tests' / 'fixtures'


def signed(value: int, width: int = 64) -> int:
    value &= (1 << width) - 1
    return value - (1 << width) if value >> (width - 1) else value


class Machine:
    def __init__(self, program: dict, values: dict[str, int]):
        self.program = {int(k, 16): v for k, v in program['instructions'].items()}
        self.pc = int(program['entry'], 16)
        self.end = int(program['exit'], 16)
        self.regs = {f'x{i}': 0 for i in range(31)}
        self.n = self.z = self.v = self.c = False
        for key, value in values.items():
            self.write(key, value)

    def read(self, arg: str) -> int:
        if arg.startswith('#'):
            return int(arg[1:], 0) & MASK
        if arg in ('xzr', 'wzr'):
            return 0
        if not re.fullmatch(r'[xw](?:[0-9]|[12][0-9]|30)', arg):
            raise ValueError(f'Unsupported operand: {arg}')
        return self.regs['x' + arg[1:]] & (0xffffffff if arg[0] == 'w' else MASK)

    def write(self, reg: str, value: int) -> None:
        if reg in ('xzr', 'wzr'):
            return
        if not re.fullmatch(r'[xw](?:[0-9]|[12][0-9]|30)', reg):
            raise ValueError('Unsupported destination register')
        self.regs['x' + reg[1:]] = value & (0xffffffff if reg[0] == 'w' else MASK)

    def arithmetic(self, a: int, b: int, add: bool, width: int) -> int:
        mask = (1 << width) - 1
        a &= mask
        b &= mask
        raw = a + b if add else a - b
        result = raw & mask
        self.n, self.z = bool(result >> (width - 1)), result == 0
        self.c = raw > mask if add else a >= b
        sa, sb = signed(a, width), signed(b, width)
        exact = sa + sb if add else sa - sb
        self.v = not (-(1 << (width - 1)) <= exact < (1 << (width - 1)))
        return result

    def condition(self, cond: str) -> bool:
        values = {'eq': self.z, 'ne': not self.z, 'mi': self.n, 'pl': not self.n,
                  'vs': self.v, 'lt': self.n != self.v, 'ge': self.n == self.v,
                  'le': self.z or self.n != self.v, 'gt': not self.z and self.n == self.v}
        if cond not in values:
            raise ValueError(f'Unsupported condition: {cond}')
        return values[cond]

    def step(self) -> None:
        if self.pc not in self.program:
            raise OverflowError('Source trap or unprovided instruction reached')
        operation, _, operands = self.program[self.pc].partition(' ')
        args = [a.strip() for a in operands.split(',')]
        self.pc += 4
        if operation == 'mov':
            self.write(args[0], self.read(args[1]))
        elif operation in ('cmp', 'cmn'):
            self.arithmetic(self.read(args[0]), self.read(args[1]), operation == 'cmn',
                            32 if args[0][0] == 'w' else 64)
        elif operation == 'subs':
            self.write(args[0], self.arithmetic(self.read(args[1]), self.read(args[2]), False,
                                                32 if args[0][0] == 'w' else 64))
        elif operation == 'lsl':
            self.write(args[0], self.read(args[1]) << self.read(args[2]))
        elif operation == 'sdiv':
            width = 32 if args[0][0] == 'w' else 64
            a, b = signed(self.read(args[1]), width), signed(self.read(args[2]), width)
            if b == 0:
                self.write(args[0], 0)
            else:
                q = abs(a) // abs(b)
                self.write(args[0], -q if (a < 0) != (b < 0) else q)
        elif operation == 'csel':
            self.write(args[0], self.read(args[1] if self.condition(args[3]) else args[2]))
        elif operation == 'cbz':
            if self.read(args[0]) == 0:
                self.pc = int(args[1], 16)
        elif operation == 'b':
            self.pc = int(args[0], 16)
        elif operation.startswith('b.'):
            if self.condition(operation[2:]):
                self.pc = int(args[0], 16)
        else:
            raise ValueError(f'Unsupported instruction: {operation}')


def oracle(programs: dict, op: str, first: int, last: int, count: int, scale: int) -> tuple[str, int]:
    # Values below are source getter results, not guesses about raw Swift layouts.
    if op == 'style':
        registers = {'x23': last, 'x22': count, 'x0': first}
    elif op == 'budget':
        registers = {'x19': count, 'w8': scale}
    elif op == 'incoming':
        registers = {'x20': last - first, 'x22': count}
    elif op == 'half':
        registers = {'x20': last - first}
    else:
        raise ValueError('Unknown slice')
    vm = Machine(programs[op], registers)
    traps = {'style': 0x272235154, 'budget': 0x272233884,
             'incoming': 0x272233ab8, 'half': 0x2722337e0}
    for _ in range(80):
        if vm.pc == vm.end:
            return 'ok', signed(vm.read('x19' if op == 'budget' else 'x24'))
        if vm.pc == traps[op]:
            return 'overflow', 0
        # Explicitly skip object projection/refcount/virtual getter instructions.
        if op == 'incoming' and vm.pc == 0x2722338fc:
            vm.write('x0', last)
            vm.pc = 0x27223392c
        if op == 'half' and vm.pc == 0x272233620:
            vm.pc = 0x272233624
        if op == 'half' and vm.pc == 0x272233628:
            vm.write('x0', last)
            vm.pc = 0x272233654
        vm.step()
    raise ValueError('Instruction budget exceeded')


def fixture(programs: dict) -> str:
    vectors = []
    boundaries = [0, 1, 2, 3, 4, 7, 8, 15, 16, 31, 32, (1 << 53) - 1,
                  1 << 53, (1 << 62) - 1, 1 << 62, MAX - 1, MAX]
    for last in boundaries:
        for first in sorted({0, last // 2, last}):
            for count in sorted({0, 1, 4, last // 2, last, MAX}):
                vectors.extend([('style', first, last, count, 1),
                                ('incoming', first, last, count, 1)])
            vectors.append(('half', first, last, 0, 1))
    for count in boundaries:
        for scale in range(3):
            vectors.append(('budget', 0, 0, count, scale))
    randomizer = random.Random(0x3B16)
    for _ in range(256):
        last = randomizer.randrange(MAX + 1)
        first = randomizer.randrange(last + 1)
        count = randomizer.randrange(MAX + 1)
        vectors.extend([('style', first, last, count, 1),
                        ('incoming', first, last, count, 1),
                        ('half', first, last, 0, 1),
                        ('budget', 0, 0, count, randomizer.randrange(3))])
    lines = [f'LMG_REGION_ORACLE_V1 {len(vectors)}']
    for index, (op, first, last, count, scale) in enumerate(vectors):
        status, expected = oracle(programs, op, first, last, count, scale)
        lines.append(f'{index} {op} {first} {last} {count} {scale} {status} {expected}')
    return '\n'.join(lines) + '\n'


def verify_archive(programs: dict, path: Path) -> None:
    with zipfile.ZipFile(path) as archive:
        for program in programs.values():
            info = archive.getinfo(program['sourcePath'])
            if info.file_size > 2 * 1024 * 1024:
                raise ValueError('Oversize disassembly entry')
            data = archive.read(info)
            if hashlib.sha256(data).hexdigest() != program['sourceSha256']:
                raise ValueError('Original disassembly SHA-256 mismatch')
            instructions = {}
            for line in data.decode('utf-8').splitlines():
                match = re.fullmatch(r'([0-9a-f]+): (.+)', line)
                if match:
                    instructions[match[1]] = match[2]
            for address, opcode in program['instructions'].items():
                if instructions.get(address) != opcode:
                    raise ValueError(f'Slice differs at {address}')
    print('Verified four scalar slices against uploaded disassembly and SHA-256')


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--output', type=Path)
    parser.add_argument('--evidence-zip', type=Path)
    args = parser.parse_args()
    try:
        spec = json.loads((HERE / 'planner_region_instruction_slices.json').read_text())
        if spec['schemaVersion'] != 1:
            raise ValueError('Unsupported source-slice schema')
        programs = spec['slices']
        if args.evidence_zip:
            verify_archive(programs, args.evidence_zip)
        result = fixture(programs)
        target = args.output or HERE / 'planner_region_reference.tsv'
        if args.check:
            if target.read_bytes() != result.encode():
                raise ValueError('Scalar fixture differs from instruction replay')
            print(result.splitlines()[0] + ': checked (text replay, not original ARM execution)')
        else:
            with target.open('xb') as stream:
                stream.write(result.encode())
            print(f'Created {target}: {result.splitlines()[0]}')
        return 0
    except (OSError, ValueError, KeyError, zipfile.BadZipFile, OverflowError) as error:
        print(f'Region reference failed: {error}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
