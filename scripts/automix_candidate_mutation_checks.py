#!/usr/bin/env python3
"""Check that native selector tests detect targeted semantic regressions.

Builds isolated temporary copies of ONE new source file. The checkout is never
modified. A compiler failure is an error, not a 'detected' behavioral mutation.
"""
from __future__ import annotations
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT=Path(__file__).resolve().parents[1]
NATIVE=ROOT/'native/automix'
MUTATIONS={
    'reversed_endpoint_delta': [('const double v = out - in;', 'const double v = in - out;')],
    'prefix_vocal_alignment': [('out[out.size() - n + i], z = in[in.size() - n + i]', 'out[i], z = in[i]')],
    'wrong_half_bar_scaling': [('if (s == TempoBinaryScale::half) return v / 2;', 'if (s == TempoBinaryScale::half) return v * 2;')],
    'even_beat_instead_of_enumeration': [('(ordinal & 1U) == 0', '((s.events[i].beatIndex + (ordinal & 0U)) & 1U) == 0')],
    'inclusive_stable_end': [('ordinal < *s.events[r.events.endEvent].downbeatIndex', 'ordinal <= *s.events[r.events.endEvent].downbeatIndex')],
    'leading_window_last_seconds': [('PlannerVocalWindow{0.0, s.events[i].songTime}', 'PlannerVocalWindow{s.events[i].songTime, s.events[ref.events().startEvent].songTime}')],
    'no_style12_forward_shift': [('plannerIncomingBeatBudget(16, pair.incomingScale)', 'plannerIncomingBeatBudget(0, pair.incomingScale)')],
    'very_low_is_vocal_conflict': [('return maximum && *maximum != 0;', 'return maximum.has_value();')],
}


def main() -> int:
    compiler=os.environ.get('CXX','c++')
    if not shutil.which(compiler):raise RuntimeError('C++ compiler not found')
    original=(NATIVE/'src/planner_candidate_selector.cpp').read_text()
    shared=[str(NATIVE/'src'/f'{name}.cpp') for name in
            ('planner_scoring','planner_region_algebra','planner_vocals','planner_loudness')]
    test=str(NATIVE/'tests/planner_candidate_selector_test.cpp')
    with tempfile.TemporaryDirectory(prefix='lmg-candidate-mutations-') as temp:
        base=Path(temp)
        def run(label: str,source: str) -> int:
            cpp=base/f'{label}.cpp';exe=base/label;cpp.write_text(source)
            result=subprocess.run([compiler,'-std=c++17','-O1','-Wall','-Wextra','-Wpedantic','-Werror',
                '-ffp-contract=off','-I',str(NATIVE/'include'),str(cpp),*shared,test,'-o',str(exe)],
                capture_output=True,text=True,timeout=90)
            if result.returncode:raise RuntimeError(f'{label} compile failure (not a mutation success): {result.stderr}')
            return subprocess.run([str(exe)],capture_output=True,text=True,timeout=30).returncode
        if run('baseline',original):raise RuntimeError('Baseline tests failed; mutation results would be meaningless')
        print('Mutation baseline: PASSED',flush=True)
        for label,replacements in MUTATIONS.items():
            source=original
            for before,after in replacements:
                if source.count(before)!=1:raise RuntimeError(f'{label}: source anchor changed')
                source=source.replace(before,after,1)
            if run(label,source)==0:raise RuntimeError(f'Behavioral mutation escaped: {label}')
            print(f'DETECTED {label}',flush=True)
    print(f'Candidate semantic mutations: {len(MUTATIONS)}/{len(MUTATIONS)} detected')
    return 0


if __name__=='__main__':raise SystemExit(main())
