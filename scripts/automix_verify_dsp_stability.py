#!/usr/bin/env python3
"""Require named DSP stability tests and the real priming export, not a device claim."""
import argparse,json
from pathlib import Path
from zipfile import ZipFile
from automix_verify_output_port_reports import verify_report
from automix_verify_jni_export import verify_jni_export,ABI_IDENTITIES
ROOT=Path(__file__).resolve().parents[1]
CLASS='com.lmg.vk.engine.automix.render.DspStabilityTest'
NAMES=['historyPreservesPcm16AndNativeFloat', 'historyRetryDoesNotReplaySource', 'historyStopsExactlyAtCueAndResetsOnGap', 'historyRejectsNonfiniteWithoutChangingCodecCursor', 'nativeWarmupUsesRealHistoryWithoutOutputReplay', 'primedPumpUsesCapturedHistoryAndPreservesPrefix', 'routingUsesPlaylistOccurrence', 'otherSinkResetCannotBlankSelectedMeter', 'mixedOutputHasSingleMeterOwner', 'djCommandCapturesTargetNotCreationOrder', 'ambiguousOccurrenceNeverUsesConstructionOrder', 'meterStereoEnergyAndPartition', 'meterOppositeChannelsDoNotCancel', 'normalizationPartitionIndependent', 'normalizationMeasuresFramesNotChannelSamples', 'disabledNormalizationPreservesFloatBits', 'normalizationDisableSlewsRatherThanSteps', 'nativeMixCannotReceiveResidualNormalization', 'confirmedMasterPreservesManualFade', 'blockedSinkIsAttemptedOnlyOncePerTick', 'blockedSinkTimesOutWithoutFakeClock', 'nativeFloatReadWithoutCursorMutation', 'nativeForeignDestroyCannotEraseCachedIngress', 'nativeForeignDestroyCannotEraseCachedExecutor', 'cancelBeforePreparationDoesNotConstruct', 'cancelPreparedDisposesExactlyOnce', 'adoptedResourceSurvivesLateCancellation', 'stalePreparationNeverPublished', 'preparationRaceDisposesOnWorker', 'nativePreparedObjectsTransferToPlaybackOwner', 'rejectedPreparationDoesNotBuild']
SYMBOL=b'Java_com_lmg_vk_engine_automix_nativecore_NativeLivePcmExecutor_nativeCreatePrimed'
def verify(reports, apks):
    count=verify_report(reports,CLASS,NAMES)
    if not apks:raise ValueError('A newly built APK is required')
    for apk in apks:
        if not apk.is_file() or apk.is_symlink():raise ValueError('Missing/unsafe APK')
        with ZipFile(apk) as z:
            if len(z.namelist())!=len(set(z.namelist())):raise ValueError('Duplicate APK entry')
            for abi,identity in ABI_IDENTITIES.items():
                name='lib/'+abi+'/liblmg_automix_jni.so'
                if name not in z.namelist() or z.getinfo(name).file_size>256*1024*1024:raise ValueError('Missing/oversized JNI')
                verify_jni_export(z.read(name),*identity,SYMBOL)
    return dict(status='DSP_STABILITY_REPORTS_AND_PREROLL_EXPORT_VERIFIED',newJvm=count,
        physicalPlaybackVerified=False,universalAllocationFreedomProven=False)
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--reports',type=Path,default=ROOT/'app/build/test-results/testDebugUnitTest')
    p.add_argument('--apk',type=Path,action='append');a=p.parse_args()
    print(json.dumps(verify(a.reports,a.apk or list((ROOT/'app/build/outputs/apk/debug').glob('*.apk'))),indent=2))
