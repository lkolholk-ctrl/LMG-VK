#!/usr/bin/env python3
"""Only assertion-detected semantic mutations count. Compiler failures and timeouts do not."""
import argparse
import pathlib
import subprocess
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
MAIN = ROOT / 'app/src/main/kotlin/com/lmg/vk/engine/automix/render'
TEST = ROOT / 'app/src/test/kotlin/com/lmg/vk/engine/automix/render/PcmCueGateScenarios.kt'
MUTATIONS = [
    ('cue-crossing-not-held', 'requireNotNull(end) > cue', 'false', 'oneBufferHeldNoSinkCalls'),
    ('prefix-consumed-before-commit', 'hold = h; return false',
     'buffer.position(h.position + h.info.prefixFrames * requireNotNull(f).bytesPerFrame); hold = h; return false', 'wholePrefixHeld'),
    ('timeout-ignored', 'nowNanos() - (if (a.deferredDeadline) a.firstHoldNanos!! else a.startNanos) >= a.maxWaitNanos', 'false', 'timeoutAtExactBoundary'),
    ('epoch-check-removed', 'attempt.get() !== a || e !== a.epoch', 'false', 'revisionChangeReleasesBoth'),
    ('probe-falsely-executable', 'val canExecute: Boolean get() = false', 'val canExecute: Boolean get() = true', 'snapshotIsRedactedAndNeverExecutable'),
    ('diagnostic-copy-consumes-codec', 'd0.put(h0.buffer.duplicate())', 'd0.put(h0.buffer)', 'copyPreservesOriginalBytesAndCursors'),
    ('sample-seam-rounded-down', 'floor(seconds * f.sampleRate + 0.5)', 'floor(seconds * f.sampleRate)', 'cueRoundingHalfUpExplicit'),
    ('same-epoch-rearm-extends-hold', 'old?.epoch?.generation == e.generation && old.epoch.revision == e.revision', 'false', 'sameRevisionRepublishCannotRearm'),
]

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--case', choices=[m[0] for m in MUTATIONS])
    args = p.parse_args()
    chosen = [m for m in MUTATIONS if args.case is None or args.case == m[0]]
    source = (MAIN / 'PcmCueGate.kt').read_text()
    with tempfile.TemporaryDirectory(prefix='lmg-cue-mutants-') as folder:
        out = pathlib.Path(folder)
        baseline = out / 'baseline.jar'
        build = subprocess.run(['kotlinc', str(MAIN/'RenderBoundary.kt'), str(MAIN/'BoundaryForwarding.kt'),
            str(MAIN/'PcmCueGate.kt'), str(TEST), '-include-runtime', '-d', str(baseline)],
            capture_output=True, text=True, timeout=180)
        if build.returncode:
            raise SystemExit('Baseline compilation failed\n' + build.stderr)
        passed = subprocess.run(['java','-jar',str(baseline)], capture_output=True,text=True,timeout=30)
        if passed.returncode:
            raise SystemExit('Unmodified baseline must pass before mutation\n' + passed.stdout + passed.stderr)
        print('PASS unmodified 70-case baseline', flush=True)
        for name, old, new, test in chosen:
            if source.count(old) != (3 if name == 'probe-falsely-executable' else 1):
                raise SystemExit(f'Mutation anchor count changed: {name}')
            mutant = out / 'PcmCueGate.kt'
            mutant.write_text(source.replace(old, new, 1))
            jar = out / 'mutant.jar'
            # The same original test/ledger bytecode is used for every mutant. The mutant
            # jar precedes it on the classpath; the scenarios assert and print its origin.
            build = subprocess.run(['kotlinc', str(mutant), '-cp', str(baseline),
                '-Xfriend-paths=' + str(baseline), '-d', str(jar)],
                capture_output=True, text=True, timeout=90)
            if build.returncode:
                raise SystemExit(name + ': compilation failure is NOT detection\n' + build.stderr)
            result = subprocess.run(['java','-Dlmg.cue.expectedJar='+str(jar),
                '-cp',str(jar)+':'+str(baseline),
                'com.lmg.vk.engine.automix.render.PcmCueGateScenarios',test],
                capture_output=True,text=True,timeout=20)
            loaded = 'CUE_IMPLEMENTATION=' + str(jar.resolve())
            if loaded not in result.stdout or result.returncode <= 0 or 'IllegalStateException' not in result.stderr or not any(
                marker in result.stderr for marker in ('Check failed','Expected ')):
                raise SystemExit(name + ': NOT assertion-detected\n' + result.stdout + result.stderr)
            print('CAUGHT ' + name, flush=True)
    print(f'Cue gate mutations: {len(chosen)}/{len(chosen)} detected by assertions')

if __name__ == '__main__':
    main()
