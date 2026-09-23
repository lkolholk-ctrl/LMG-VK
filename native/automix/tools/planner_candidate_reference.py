#!/usr/bin/env python3
"""Independent finite-domain specification fixtures for candidate predicates.

No C++ is imported/executed. This is NOT ARM emulation or an Apple output capture.
The expressions are independently written from the source branches documented in
PLANNER_CANDIDATE_SELECTOR_IMPLEMENTATION.md. Native tempo/score math is tested
through its existing implementation, not reimplemented here.
"""
from __future__ import annotations
import argparse
import itertools
from pathlib import Path
import random
import struct

FIXTURE = Path(__file__).resolve().parents[1] / 'tests/fixtures/planner_candidate_reference.tsv'


def bits(value: float) -> str:
    return f'{struct.unpack(">Q", struct.pack(">d", value))[0]:016x}'


def generate() -> bytes:
    lines = ['# Finite-domain specification oracle; NOT original ARM execution.',
             '# op outgoing incoming scale-or-unused expected (binary64 hex for R/D)']
    values = [None, 0, 1, 2, 3, 7, 8, 9, 16, 17, 2**31-1, 2**52-1, 2**52+1, 2**62-1]
    for outgoing, incoming, scale in itertools.product(values, values, range(3)):
        scaled = None if incoming is None else (incoming//2 if scale == 0 else incoming if scale == 1 else incoming*2)
        a, b = '-' if outgoing is None else str(outgoing), '-' if incoming is None else str(incoming)
        ratio = '-' if outgoing is None or scaled is None or outgoing == 0 or scaled == 0 else bits(float(scaled)/float(outgoing))
        match = str(outgoing) if outgoing is not None and scaled == outgoing else '-'
        lines.extend((f'R {a} {b} {scale} {ratio}', f'M {a} {b} {scale} {match}'))
    def vocal(a: list[int], b: list[int]) -> None:
        # Walking backwards pairs each tail directly; no slicing/max reducer from
        # the production implementation is copied into this independent oracle.
        conflict = any(x != 5 and y != 5 and x >= 1 and y >= 1 for x,y in zip(reversed(a), reversed(b)))
        sa = ''.join(map(str,a)) or '-'; sb = ''.join(map(str,b)) or '-'
        lines.append(f'V {sa} {sb} - {int(conflict)}')
    for a,b,c,d in itertools.product(range(6),repeat=4):
        vocal([a,b], [c,d])
    rng=random.Random(0xCADA2026)
    for _ in range(512):
        vocal([rng.randrange(6) for _ in range(rng.randrange(21))],
              [rng.randrange(6) for _ in range(rng.randrange(21))])
    times=[-0.0,0.0,.125,1.0,16.0,32.0,-100.0,1000000.0,float(2**52+1)]
    for a,b in itertools.product(times,repeat=2):
        lines.append(f'D {repr(a)} {repr(b)} - {bits(a-b)}')
    return ('\n'.join(lines)+'\n').encode()


def main() -> int:
    parser=argparse.ArgumentParser(description=__doc__)
    modes=parser.add_mutually_exclusive_group(required=True)
    modes.add_argument('--check',action='store_true');modes.add_argument('--write',action='store_true')
    args=parser.parse_args();data=generate()
    if args.write:
        FIXTURE.parent.mkdir(parents=True,exist_ok=True);FIXTURE.write_bytes(data)
    elif not FIXTURE.is_file() or FIXTURE.read_bytes()!=data:
        parser.exit(1,'Candidate specification fixture mismatch; no file was modified.\n')
    print(f'Candidate specification fixture: {len(data.splitlines())-2} cases; '+('written' if args.write else 'reproducible'))
    return 0


if __name__=='__main__':raise SystemExit(main())
