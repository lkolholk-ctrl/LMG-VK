#!/usr/bin/env python3
"""Isolated source-binding mutations; only a test assertion is a detection.
Does not modify repository inputs, run firmware, download tools or touch playback.
"""
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]

def main():
    src = (ROOT / 'native/automix/src/planner_source_bindings.cpp').read_text()
    header = (ROOT / 'native/automix/include/lmg/automix/planner_source_bindings.h').read_text()
    test = ROOT / 'native/automix/tests/planner_source_bindings_test.cpp'
    mutations = [
        ('profile-order', 'source', 'ids{8, 9, 12}', 'ids{9, 8, 12}', 1),
        ('inclusive-end', 'source', 'if(c.time > d)', 'if(c.time >= d)', 2),
        ('within-upper', 'source', 'return resolved(c.lower,c.upper);', 'return resolved(c.lower,d);', 1),
        ('missing-duration', 'source', 'return failure(PlannerCriteriaResolution::durationUnavailable);', 'return resolved(0.0,0.0);', 2),
        ('late-invented-range', 'source', 'return failure(PlannerCriteriaResolution::latePlacementUnresolved);', 'return resolved(0.0,d);', 1),
        ('nonfinite-duration', 'source', 'std::isfinite(v) && v >= 0.0', 'v >= 0.0', 1),
        ('unclosed-bits', 'header', '(1ULL << 1) | (1ULL << 2)', '0ULL', 1),
        ('signed-zero', 'source', 'return resolved(c.time,d);', 'return resolved(std::abs(c.time),d);', 2),
    ]
    with tempfile.TemporaryDirectory(prefix='lmg-source-binding-mut-') as directory:
        out = Path(directory)
        include = out / 'include/lmg/automix'
        include.mkdir(parents=True)
        for name, which, before, after, expected in [('baseline', '', '', '', 0), *mutations]:
            text, hdr = src, header
            if name != 'baseline':
                original = src if which == 'source' else header
                if original.count(before) != expected:
                    raise RuntimeError('Mutation anchor differs: ' + name)
                if which == 'source': text = original.replace(before, after)
                else: hdr = original.replace(before, after)
            file = out / 'source.cpp'
            file.write_text(text)
            (include / 'planner_source_bindings.h').write_text(hdr)
            exe = out / 'test'
            compiled = subprocess.run([os.environ.get('CXX','c++'), '-std=c++17', '-O1', '-Wall', '-Wextra',
                '-Wpedantic', '-Werror', '-ffp-contract=off', '-I', str(out / 'include'), str(file), str(test), '-o', str(exe)],
                capture_output=True, text=True, timeout=45)
            if compiled.returncode: raise RuntimeError('Compilation failure is not a detection: '+name+'\n'+compiled.stderr)
            result = subprocess.run([str(exe)], capture_output=True, text=True, timeout=10)
            if name == 'baseline':
                if result.returncode != 0: raise RuntimeError('Baseline failed: '+result.stdout+result.stderr)
            elif result.returncode != 1 or 'source binding assertion' not in result.stderr:
                raise RuntimeError('Mutation escaped or failed without assertion: '+name+'\n'+result.stdout+result.stderr)
            else: print('DETECTED', name)
    print('Source binding mutations: 8/8 detected by assertions')

if __name__ == '__main__': main()
