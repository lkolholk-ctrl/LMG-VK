#!/usr/bin/env python3
"""Named real-JNI cases + actual dynamic definitions in every APK ABI; no device-parity claim."""
import argparse, json
from pathlib import Path
from zipfile import ZipFile
from automix_verify_output_port_reports import verify_report
ROOT=Path(__file__).resolve().parents[1]
APP_CLASS='com.lmg.vk.engine.automix.render.NativeLivePcmPumpTest'
NAMES=['realDspPrefixAndBContinuation','realDspStarvationRetainsOutgoing','partialByteWritesDoNotReplay','pcm16ExplicitConversion','unequalRatesReachTrueEof','immutablePlanCopy','malformedPlanRejected','nativeOwnerThreadEnforced','exactCueAfterAcceptedPrefix','cancelInsidePartialWrite','postCueOfferCannotBeReserved','nonfiniteInputRejected','stereoFramesAndChannelsPreserved']
FORK={
 'androidx.media3.exoplayer.LmgLiveGainReservationTest':['actualMasterAndDuckNotReplacedByUnity','attenuatedOutgoingRejectsBeforeWrites','freshProofDoesNotEnterManualFade'],
 'androidx.media3.exoplayer.LmgLivePlaybackLeaseTest':['exactGenerationAndRevisionRequired', 'actualPeriodGuardIsRechecked', 'wrongThreadCannotUseLease', 'crossThreadRevocationIsVisible', 'clockPinnedExactlyOnce', 'invalidPeriodsCannotPinClock', 'revokedPinnedLeaseIsNotCurrent', 'applicationCannotConstructPublicGrant'],
 'androidx.media3.exoplayer.LmgLiveMediaClockTest':['unavailablePcmDoesNotAdvanceStandaloneTime','explicitReleaseAnchorsNormalClock','liveClockRejectsForeignSpeed','resetCannotSilentlyDropLease'],
}
EXPORTS=['Java_com_lmg_vk_engine_automix_nativecore_NativeLivePcmExecutor_'+n for n in ['nativeProtocol','nativeCreate','nativePush','nativeEof','nativeRender','nativeEncodePrefix','nativeSourceTime','nativeStats','nativeDestroy']]
def verify(reports,apks,fork_reports=None):
 verify_report(reports,APP_CLASS,NAMES)
 if not apks:raise ValueError('No fresh APK provided')
 from automix_verify_jni_export import ABI_IDENTITIES, verify_jni_export
 abis=('arm64-v8a','armeabi-v7a','x86')
 for path in apks:
  if path.is_symlink() or not path.is_file():raise ValueError('Unsafe APK')
  with ZipFile(path) as z:
   if len(z.namelist())!=len(set(z.namelist())):raise ValueError('Duplicate APK entries')
   for abi in abis:
    n=f'lib/{abi}/liblmg_automix_jni.so'
    if n not in z.namelist() or z.getinfo(n).file_size>256*1024*1024:raise ValueError('Missing/oversized JNI for '+abi)
    data=z.read(n)
    for symbol in EXPORTS:verify_jni_export(data,*ABI_IDENTITIES[abi],symbol.encode())
 fork_count=None
 if fork_reports is not None:fork_count=sum(verify_report(fork_reports,k,v) for k,v in FORK.items())
 return dict(status='LIVE_EXECUTOR_REPORTS_AND_EXPORTS_VERIFIED',newRealJni=13,liveExportsPerAbi=9,abis=list(abis),forkTests=fork_count,physicalAudioVerified=False,automaticActivation=False)
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--reports',type=Path,default=ROOT/'app/build/test-results/testDebugUnitTest');p.add_argument('--apk',type=Path,action='append');p.add_argument('--fork-reports',type=Path);a=p.parse_args()
 try:print(json.dumps(verify(a.reports,a.apk or list((ROOT/'app/build/outputs/apk/debug').glob('*.apk')),a.fork_reports),indent=2))
 except (OSError,ValueError) as e:raise SystemExit(str(e))
