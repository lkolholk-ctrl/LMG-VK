#!/usr/bin/env python3
"""Independent finite-domain SPECIFICATION oracle. Not original ARM execution.

Writes a TSV fixture; --check checks an existing fixture without rewriting it.
No C++ import, subprocess, network, original-binary execution or repository writes
apart from an explicitly supplied --output. Original source anchors are recorded
in PLANNER_INPUT_PRODUCERS_IMPLEMENTATION.md.
"""
from __future__ import annotations
import argparse
import math
from pathlib import Path
import random


def number(value):
    return "_" if value is None else repr(float(value))


def numbers(values):
    return ",".join(number(v) for v in values)


def normalized(v):
    return v if v is not None and 0.0 <= v <= 1.0 else None


def slope(points):
    x = xx = y = xy = 0.0
    for time, value in points:
        x = x + time
        xx = xx + time * time
        y = y + value
        xy = xy + time * value
    n = float(len(points))
    d = n * xx - x * x
    return None if not points or d == 0.0 else (n * xy - x * y) / d


def seed_fixture(out_bars, in_bars, out_bpm, in_bpm, meter, suffix, min_out, max_in):
    count = 4 if suffix is None else suffix
    a_ends = [b for b in range(4, out_bars + 1, 4) if b * meter * (60.0/out_bpm) >= min_out]
    z_ends = [b for b in range(0, in_bars + 1, 4) if b * meter * (60.0/in_bpm) <= max_in]
    if z_ends and z_ends[0] < count:
        z_ends = z_ends[1:]  # Not a filter of EVERY short endpoint.
    if not count:
        return "_"
    # These fixture tempos are EXACT half/unity/double, no libm tie ambiguity.
    scale = {2.0: 0, 1.0: 1, 0.5: 2}[in_bpm/out_bpm]
    result = []
    for az in a_ends:
        aa = max(0, az - count)
        for zz in z_ends:
            za = max(0, zz - count)
            if az == aa or za == zz:
                continue
            if scale == 0:
                za = zz - min(zz, 2 * (zz - za))
            a0, a1, z0, z1 = aa*meter, az*meter, za*meter, zz*meter
            if scale == 2:
                z0 = z1 - (z1-z0)//2
            budget = (a1-a0)*2 if scale == 0 else ((a1-a0)//2 if scale == 2 else a1-a0)
            z0 = z1-min(z1-z0, budget)
            result.append(f"{a0},{a1},{z0},{z1},{scale}")
    return ";".join(result) if result else "_"


def make_rows():
    rows = ["# LMG producer specification oracle v1; binary64 finite domain; NOT ARM execution"]
    rng = random.Random(0x3C2026)
    durations = [0.125, 1., 2., 30., math.nextafter(60., 0.), 60., math.nextafter(60., math.inf),
                 90., 120., 150., 300., 3600.] + [rng.randrange(1, 100000)/8. for _ in range(200)]
    for t in durations:
        preferred = max(0., t-30.) * .5 if t >= 60. else min(t, 2.)
        rows.append("\t".join(["D",number(t),numbers([preferred,min(t,60.),min(preferred,60.)])]))
    values = [None, -.1, -0.0, .1, .25, .75, 1., math.nextafter(1., math.inf), 1.2]
    for a in values:
        for b in values:
            for c in values:
                main = normalized(a)
                expected = "_" if main is None else numbers([main, normalized(b), normalized(c)])
                rows.append("\t".join(["S", numbers([a,b,c]), expected]))
    points_sets = [[], [(0.,-20.)],[(1.,-20.),(1.,-18.)],[(0.,-20.),(1.,-19.)],
                   [(0.,-30.),(1.,-30.)],[(0.,math.nextafter(-30.,0.))]]
    for _ in range(200):
        points = [(rng.randrange(0,64)/4., rng.randrange(-200,1)/4.) for _ in range(rng.randrange(1,12))]
        points_sets.append(points)
    for points in points_sets:
        encoded = ";".join(numbers(p) for p in points) or "_"
        for window in [(0.,16.),(1.,4.),(4.,4.)]:
            selected = [(t,v) for t,v in points if window[0] <= t <= window[1]]
            trend = slope(selected)
            mean = sum(v for _,v in selected)/len(selected) if selected else None
            keep = (trend is None or abs(trend) < 1.) and mean is not None and mean > -30.
            rows.append("\t".join(["L",encoded,numbers(window),number(slope(points)),str(int(keep))]))
    for meter in [3,4]:
        for out_bars in [16,24,32]:
            for in_bpm in [60.,120.,240.]:
                in_bars = int(32 * in_bpm/120.)
                # At most three outgoing ends and three incoming ends.
                min_out = (out_bars-8)*meter*.5
                max_in = 12*meter*60./in_bpm
                for suffix in [None,0,2,4,8,9,16]:
                    params = [out_bars,in_bars,120.,in_bpm,meter,suffix,min_out,max_in]
                    rows.append("\t".join(["G",numbers(params),seed_fixture(*params)]))
    return "\n".join(rows)+"\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--output",type=Path)
    group.add_argument("--check",type=Path)
    args = parser.parse_args()
    payload = make_rows().encode("utf-8")
    path = args.check or args.output
    if args.check:
        if path.read_bytes() != payload:
            raise SystemExit("Producer reference fixture mismatch")
    else:
        with path.open("xb") as stream:
            stream.write(payload)
    print(f"Producer specification fixture: {len(payload.splitlines())-1} cases verified; not original ARM execution")

if __name__ == "__main__":
    main()
