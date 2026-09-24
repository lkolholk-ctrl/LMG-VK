#!/usr/bin/env python3
"""Finite-domain independent specification oracle; NOT original ARM execution.

Decimal arithmetic computes the geometric weights independently of the C++
continuous automation evaluator. File publication is explicit; --check compares
an existing checked-in fixture without replacing it.
"""
from __future__ import annotations
import argparse
from decimal import Decimal, localcontext
import itertools
from pathlib import Path
import random


def rows():
    cases = []
    for a, b, na, nb, scale in itertools.product(
        [2.0, 8.0, 16.0, 32.0], [1.6, 8.0, 12.8, 32.0],
        [8, 16, 32], [8, 16, 32], range(3)):
        cases.append((0.125, a + 0.125, 0.25, b + 0.25, na, nb, scale))
    rng = random.Random(0x4A2026)
    for _ in range(128):
        a0, b0 = rng.randrange(2000) / 8, rng.randrange(2000) / 8
        cases.append((a0, a0+rng.randrange(8, 800)/8, b0, b0+rng.randrange(8, 800)/8,
                      rng.randrange(1, 128), rng.randrange(1, 128), rng.randrange(3)))
    with localcontext() as ctx:
        ctx.prec = 60
        for a0, a1, b0, b1, na, nb, scale in cases:
            da = Decimal.from_float(a1) - Decimal.from_float(a0)
            db = Decimal.from_float(b1) - Decimal.from_float(b0)
            ratio = [Decimal('.5'), Decimal(1), Decimal(2)][scale] * nb / na
            effective = db / ratio
            # Incoming's real start lies either before the virtual rate ramp or
            # within it; compute its convex weights, without using C++ evaluator.
            progress = max(Decimal(0), min(Decimal(1), Decimal(1)-ratio))
            first_in = (Decimal(1)-progress)*(effective/da)+progress
            output = [a0,a1,b0,b1,na,nb,scale,float(ratio),float(effective),float(da/effective),float(first_in)]
            yield ' '.join(format(v, '.17g') if isinstance(v, float) else str(v) for v in output)


def content():
    return '# Specification oracle: structured beat-count geometry, not ARM execution.\n'+'\n'.join(rows())+'\n'


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--check', type=Path)
    p.add_argument('--output', type=Path)
    args=p.parse_args()
    if bool(args.check)==bool(args.output):p.error('Choose exactly --check or --output')
    data=content()
    if args.check:
        if args.check.read_text()!=data:raise SystemExit('Schedule reference mismatch')
        print(f'Schedule specification fixture verified: {len(data.splitlines())-1} rows')
    else:
        with args.output.open('x') as stream:stream.write(data)
if __name__=='__main__':main()
