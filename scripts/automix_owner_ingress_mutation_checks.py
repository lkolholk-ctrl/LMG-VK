#!/usr/bin/env python3
"""Independent assertion-detected mutations. Uses real ingress JNI, not DSP or a device."""
from pathlib import Path
import argparse
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'app/src/main/kotlin/com/lmg/vk/engine/automix/render'
TEST = ROOT / 'app/src/test/kotlin/com/lmg/vk/engine/automix/render/PcmOwnerTransactionScenarios.kt'
NATIVE = ROOT / 'native/automix'
NATIVE_CASES = [
    ('one-sided-commit', 'q.samples.swap(q.staging); q.head = 0;',
        'if (&q != &queues_[0]) continue; q.samples.swap(q.staging); q.head = 0;'),
    ('prefix-crosses-cue', 'if (prefix) delivered =', 'if (false) delivered ='),
    ('pcm16-half-level', '/ 32768.0f;', '/ 65536.0f;'),
    ('source-continuity-ignored', 'if (first != q.stats.nextWriteFrame)', 'if (false && first != q.stats.nextWriteFrame)'),
    ('postcommit-replay-enabled', 'phase_ = OwnerIngressPhase::aborted;', 'phase_ = OwnerIngressPhase::empty;'),
    ('queue-overwrite', 'std::min(count, capacity_ - q.stats.queued)', 'count'),
]
KOTLIN_CASES = [
    ('codec-ack-not-advanced', 'PcmCueGate.kt', 'buffer.position(h.limit)', 'Unit', 'eachOriginalAcknowledgedOnItsCallback'),
    ('offered-frames-acknowledged', 'PcmCueGate.kt', 'buffer.position(buffer.position()+accepted*f.bytesPerFrame)', 'buffer.position(buffer.position()+frames*f.bytesPerFrame)', 'backpressureAcceptsOnlyFramesWithCapacity'),
    ('foreign-receipt-accepted', 'PcmCueGate.kt', 'if(t.receipt !== receipt)return false', 'if(false)return false', 'foreignReceiptCannotCommitPreparedInput'),
    ('native-commit-refusal-ignored', 'PcmCueGate.kt', 'if(!t.admission.commit(t.nativeTicket) ||',
        'if((t.admission.commit(t.nativeTicket) && false) ||', 'nativeCommitFailureAfterClaimQuarantines'),
    ('publication-not-atomic-with-claim', 'RenderBoundary.kt', 'mail.get().epoch === expected && !expected.closed && claim()',
        '!expected.closed && claim()', 'publicationWinsBetweenValidationAndClaim'),
    ('ingress-falsely-claims-live-dsp', 'PcmCueGate.kt', 'val liveDspInstalled: Boolean get() = false',
        'val liveDspInstalled: Boolean get() = true', 'ownerResultNeverClaimsAudioOrDSP'),
]

def run(command, timeout=180):
    return subprocess.run([str(x) for x in command], capture_output=True, text=True, timeout=timeout)

def built(command):
    result=run(command)
    if result.returncode:
        raise RuntimeError('Compilation is NOT mutation detection\n'+result.stdout+result.stderr)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--case', choices=[c[0] for c in NATIVE_CASES+KOTLIN_CASES])
    args=parser.parse_args()
    cxx=os.environ.get('CXX','c++')
    import shutil
    java=Path(shutil.which('javac') or '/missing').resolve().parent.parent
    flags=['-std=c++17','-Wall','-Wextra','-Wpedantic','-ffp-contract=off','-pthread','-I',NATIVE/'include']
    source=(NATIVE/'src/pcm_owner_ingress.cpp').read_text()
    count=0
    with tempfile.TemporaryDirectory(prefix='lmg-owner-mutants-') as temp:
        folder=Path(temp)
        base=folder/'baseline-native'
        built([cxx,*flags,NATIVE/'src/pcm_owner_ingress.cpp',NATIVE/'tests/pcm_owner_ingress_test.cpp','-o',base])
        good=run([base]); random=run([base,'--random'])
        if good.returncode or random.returncode: raise RuntimeError('Unmodified native baseline failed')
        print('PASS unmodified native: 26 groups + 512 random streams',flush=True)
        for name,old,new in NATIVE_CASES:
            if args.case and args.case!=name: continue
            if source.count(old)!=1: raise RuntimeError('Anchor mismatch: '+name)
            altered=folder/'mutant.cpp';altered.write_text(source.replace(old,new))
            exe=folder/'mutant-native'
            built([cxx,*flags,altered,NATIVE/'tests/pcm_owner_ingress_test.cpp','-o',exe])
            result=run([exe],30)
            if result.returncode!=1 or 'FAIL ASSERT:' not in result.stderr:
                raise RuntimeError(name+': NOT assertion-detected\n'+result.stdout+result.stderr)
            print('CAUGHT '+name,flush=True);count+=1
        if not args.case or args.case in [c[0] for c in KOTLIN_CASES]:
            lib=folder/'liblmg_automix_jni.so'
            built([cxx,*flags,'-shared','-fPIC','-I',java/'include','-I',java/'include/linux',
                NATIVE/'src/pcm_owner_ingress.cpp',NATIVE/'android/pcm_owner_ingress_jni.cpp','-o',lib])
            jar=folder/'baseline.jar'
            core=[MAIN/'RenderBoundary.kt',MAIN/'BoundaryForwarding.kt',MAIN/'PcmCueGate.kt']
            built(['kotlinc',*core,MAIN/'NativeCueOwnerIngress.kt',
                NATIVE/'android/src/main/kotlin/com/lmg/vk/engine/automix/nativecore/NativePcmOwnerIngress.kt',
                TEST,'-include-runtime','-d',jar])
            cmd=['java','-Djava.library.path='+str(folder),'-cp',jar,
                 'com.lmg.vk.engine.automix.render.PcmOwnerTransactionScenarios']
            good=run(cmd,60)
            if good.returncode: raise RuntimeError('Unmodified JNI baseline failed\n'+good.stdout+good.stderr)
            print('PASS unmodified 40 real-JNI cases (output lease is a test double)',flush=True)
            for name,file,old,new,case in KOTLIN_CASES:
                if args.case and args.case!=name: continue
                paths=[]
                for origin in core:
                    code=origin.read_text()
                    if origin.name==file:
                        if code.count(old)!=1: raise RuntimeError('Anchor mismatch: '+name)
                        code=code.replace(old,new)
                    destination=folder/origin.name;destination.write_text(code);paths.append(destination)
                mutant=folder/'mutant.jar'
                built(['kotlinc',*paths,'-cp',jar,'-Xfriend-paths='+str(jar),'-d',mutant])
                result=run(['java','-Djava.library.path='+str(folder),'-Dlmg.owner.expectedJar='+str(mutant),
                    '-cp',str(mutant)+os.pathsep+str(jar),
                    'com.lmg.vk.engine.automix.render.PcmOwnerTransactionScenarios',case],30)
                if result.returncode!=1 or 'OWNER_IMPLEMENTATION='+str(mutant.resolve()) not in result.stdout or \
                    'IllegalStateException: Check failed.' not in result.stderr:
                    raise RuntimeError(name+': NOT assertion-detected\n'+result.stdout+result.stderr)
                print('CAUGHT '+name,flush=True);count+=1
    expected=1 if args.case else 12
    if count!=expected:raise RuntimeError('Incomplete mutation execution')
    print(f'Owner admission mutations: {count}/{expected} assertion-detected')

if __name__=='__main__':main()
