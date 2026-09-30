#!/usr/bin/env python3
"""Reintroduce concrete fixed bugs in isolated copies; compilation/timeouts are not detections.
Requires the real freshly built native static/shared libraries. Does not edit the checkout.
"""
from pathlib import Path
import argparse
import os
import subprocess
import tempfile

ROOT=Path(__file__).resolve().parents[1]
R=Path('app/src/main/kotlin/com/lmg/vk/engine/automix/render')
E=R.parent.parent
N=Path('native/automix/android/src/main/kotlin/com/lmg/vk/engine/automix/nativecore')
T=Path('app/src/test/kotlin/com/lmg/vk/engine/automix/render')
MAIN='com.lmg.vk.engine.automix.render.DspStabilityScenarios'

def run(args, *, fail=False, env=None):
    p=subprocess.run([str(x) for x in args],cwd=ROOT,text=True,stdout=subprocess.PIPE,
                     stderr=subprocess.STDOUT,timeout=120,env=env)
    if fail:
        if p.returncode!=1 or not ('ASSERT ' in p.stdout or 'java.lang.IllegalStateException' in p.stdout):
            raise RuntimeError('Mutation not detected by test assertion:\n'+p.stdout)
    elif p.returncode:
        raise RuntimeError('Build/baseline failed (not a mutation detection):\n'+p.stdout)
    return p.stdout

def changed(path, before, after, directory):
    text=(ROOT/path).read_text()
    if text.count(before)!=1:raise RuntimeError('Mutation anchor is not unique: '+str(path))
    target=directory/path.name;target.write_text(text.replace(before,after));return target

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('build',type=Path)
    parser.add_argument('--only',nargs='+',help='Subset for bounded CI jobs; default is all seven')
    args=parser.parse_args();build=args.build.resolve();selected=set(args.only or ());passed=0
    library=build/'liblmg_automix.a'
    if not library.is_file() or not (build/'android/liblmg_automix_jni.so').is_file():
        raise SystemExit('Build complete native core and JNI first')
    with tempfile.TemporaryDirectory(prefix='automix-dsp-mutations-') as temp:
        work=Path(temp)
        natives=[
          ('fractional-hop-loss',Path('native/automix/src/time_pitch_stream.cpp'),
           'preciseInputRead_ += transient_.effectiveInputHop;\n    state_.inputRead=static_cast<std::int64_t>(preciseInputRead_);',
           '// mutation: lose fractional source-frame carry', 'clock'),
          ('replayed-cold-preroll',Path('native/automix/src/live_pcm_executor.cpp'),
           'if(s.outputCursor<0){', 'if(false && s.outputCursor<0){', 'warm'),
        ]
        for name,path,before,after,mode in natives:
            if selected and name not in selected:continue
            d=work/name;d.mkdir();source=changed(path,before,after,d);exe=d/'test'
            run([os.environ.get('CXX','c++'),'-std=c++17','-O1','-ffp-contract=off',
                 '-I',ROOT/'native/automix/include',source,ROOT/'native/automix/tests/dsp_stability_test.cpp',
                 library,'-pthread','-o',exe])
            run([exe,mode],fail=True);passed+=1;print('PASS mutation '+name,flush=True)
        sources=[E/f for f in ('SinkAudioRouting.kt','AudioReactor.kt','DjStreamFx.kt','PcmBandMeter.kt','PcmNormalizationKernel.kt')]
        sources += [R/f for f in ('RenderBoundary.kt','PcmCueGate.kt','BoundaryForwarding.kt','NativeCueOwnerIngress.kt',
                        'SameSinkOutputPort.kt','SameSinkPairOutput.kt','LivePcmPump.kt','PreparedResourceTask.kt')]
        sources += [N/'NativeLivePcmExecutor.kt',N/'NativePcmOwnerIngress.kt',T/'LivePcmPumpScenarios.kt',T/'DspStabilityScenarios.kt']
        jar=work/'baseline.jar'
        run(['kotlinc',*sources,'-include-runtime','-d',jar])
        java=['java','-Djava.library.path='+str(build/'android')]
        run([*java,'-cp',jar,MAIN])
        kotlin=[
          ('master-resets-fade',R/'SameSinkOutputPort.kt',
           'master = value\n        // This typed message', 'master = value; fade = 1f\n        // This typed message',
           'confirmedMasterPreservesManualFade'),
          ('stereo-counts-as-time',E/'PcmNormalizationKernel.kt','framesSeen++; windowFrames++',
           'framesSeen+=channels; windowFrames++','normalizationMeasuresFramesNotChannelSamples'),
          ('spin-blocked-sink',R/'LivePcmPump.kt','if (packetId != 0L && !writePending()) break',
           'if (packetId != 0L && !writePending()) continue','blockedSinkIsAttemptedOnlyOncePerTick'),
          ('stale-prepared-resource',R/'PreparedResourceTask.kt','if (!current() || !state.compareAndSet(PENDING, Ready(resource)))',
           'if (!state.compareAndSet(PENDING, Ready(resource)))','stalePreparationNeverPublished'),
          ('history-after-cue',R/'RenderBoundary.kt','val last=minOf(first+frames,stop)',
           'val last=first+frames','historyStopsExactlyAtCueAndResetsOnGap'),
        ]
        for name,path,before,after,scenario in kotlin:
            if selected and name not in selected:continue
            d=work/name;d.mkdir();source=changed(path,before,after,d);mutant=d/'mutant.jar'
            run(['kotlinc',source,'-cp',jar,'-Xfriend-paths='+str(jar),'-d',mutant])
            run([*java,'-cp',str(mutant)+os.pathsep+str(jar),MAIN,scenario],fail=True)
            passed+=1;print('PASS mutation '+name,flush=True)
        expected=len(selected) if selected else 7
        if passed!=expected:raise RuntimeError('Unknown/unrun mutation')
        print(f'DSP_STABILITY_MUTATIONS: {passed}/{expected} assertion-detected; no original file edited')

if __name__=='__main__':main()
