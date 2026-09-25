#!/usr/bin/env python3
"""Isolated output semantic mutations. No source modification; require assertion failures.

The same original tests.jar is used for every mutation, with mutant implementation first
on the classpath and implementation origin checked. Compile errors/signals/timeouts FAIL.
"""
from __future__ import annotations
import argparse
import os
from pathlib import Path
import subprocess
import tempfile

ROOT=Path(__file__).resolve().parents[1]
R=ROOT/'app/src/main/kotlin/com/lmg/vk/engine/automix/render'
T=ROOT/'app/src/test/kotlin/com/lmg/vk/engine/automix/render'
MUTATIONS=[
 ('double-transition-gain','if (gainDetached) master else fade * master','fade * master','activatedTracksDucking'),
 ('lost-player-gain','if (gainDetached) master else fade * master','if (gainDetached) 1f else fade * master','activatedTracksDucking'),
 ('queued-legacy-retroactive-gain','if (fade != 1f && backend.pending()) return false','if (false) return false','queuedFadedAudioBlocksActivation'),
 ('offered-instead-of-accepted','acceptedBytes = Math.addExact(acceptedBytes, (after - before).toLong())','acceptedBytes = Math.addExact(acceptedBytes, (pendingLimit - before).toLong())','partialBytesOnlyAdvanceCompletedFrames'),
 ('replaced-partial-buffer','pending !== bytes || pendingLimit','false || pendingLimit','retryRequiresOriginalBuffer'),
 ('lost-cancel-after-write','        valid(t)\n        if (result)','        // MUTATION omits recheck\n        if (result)','cancelInsideWriteDoesNotReplayAcceptedBytes'),
 ('rollback-after-zero-write','phase == OutputPortPhase.RESET_REQUIRED || offered','phase == OutputPortPhase.RESET_REQUIRED || false','zeroAcceptanceStillOwnsBuffer'),
 ('resumed-fade-after-revoke','if (gainDetached) master else fade * master','if (phase == OutputPortPhase.ACTIVE || phase == OutputPortPhase.WRITING) master else fade * master','revokedActiveWriterKeepsGainSeparated'),
 ('rounded-frame-timestamps','Math.multiplyExact(firstOutputFrame, 1_000_000L) / t.format.sampleRate','Math.multiplyExact(firstOutputFrame, 1_000_000L / t.format.sampleRate)','nextTimestampUsesAbsoluteFrameNotAccumulatedRounding'),
 ('clock-regression-ignored','position < requireNotNull(lastSinkPosition)','false','clockRegressionRequiresReset'),
]

def run(args:list[str], timeout:int=120)->subprocess.CompletedProcess[str]:
    return subprocess.run(args,capture_output=True,text=True,timeout=timeout)

def checked(args:list[str])->None:
    p=run(args)
    if p.returncode: raise RuntimeError('Command failed (not a detected mutation): '+p.stdout+p.stderr)

def main()->None:
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--case',action='append',choices=[m[0] for m in MUTATIONS])
    a=p.parse_args()
    selected=[m for m in MUTATIONS if not a.case or m[0] in a.case]
    actual=(R/'SameSinkOutputPort.kt').read_text()
    common=[R/n for n in ['RenderBoundary.kt','PcmCueGate.kt','BoundaryForwarding.kt','SameSinkPairOutput.kt']]
    with tempfile.TemporaryDirectory(prefix='lmg-output-mutations-') as d:
        tmp=Path(d);tests=tmp/'tests.jar'
        checked(['kotlinc',*map(str,common),str(R/'SameSinkOutputPort.kt'),str(T/'SameSinkOutputScenarios.kt'),'-include-runtime','-d',str(tests)])
        for name,before,after,scenario in selected:
            if actual.count(before)!=1:raise ValueError('Mutation anchor count mismatch: '+name)
            checked(['java','-jar',str(tests),scenario])
            source=tmp/'SameSinkOutputPort.kt';source.write_text(actual.replace(before,after))
            jar=tmp/(name+'.jar')
            checked(['kotlinc',*map(str,common),str(source),'-d',str(jar)])
            result=run(['java','-Dlmg.output.expectedJar='+str(jar),'-cp',str(jar)+os.pathsep+str(tests),
                'com.lmg.vk.engine.automix.render.SameSinkOutputScenarios',scenario],30)
            text=result.stdout+result.stderr
            if result.returncode!=1 or 'OUTPUT_IMPLEMENTATION='+str(jar) not in text or \
                not any(m in text for m in ('java.lang.IllegalStateException: Check failed.', 'java.lang.IllegalStateException: TEST_EXPECTED_FAILURE')) or 'SameSinkOutputScenarios' not in text:
                raise RuntimeError('Not detected by expected assertion: '+name+'\n'+text)
            print('PASS assertion-detected '+name,flush=True)
        print(f'OUTPUT_PORT_MUTATIONS_VERIFIED {len(selected)}/{len(selected)}; no original sources changed')
if __name__=='__main__':main()
