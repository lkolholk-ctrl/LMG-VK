package com.lmg.vk.engine.automix.render

import org.junit.Test

/** Same production/JNI scenarios as the standalone host gate. */
class DspStabilityTest {
    @Test fun historyPreservesPcm16AndNativeFloat() = DspStabilityScenarios.run("historyPreservesPcm16AndNativeFloat")
    @Test fun historyRetryDoesNotReplaySource() = DspStabilityScenarios.run("historyRetryDoesNotReplaySource")
    @Test fun historyStopsExactlyAtCueAndResetsOnGap() = DspStabilityScenarios.run("historyStopsExactlyAtCueAndResetsOnGap")
    @Test fun historyRejectsNonfiniteWithoutChangingCodecCursor() = DspStabilityScenarios.run("historyRejectsNonfiniteWithoutChangingCodecCursor")
    @Test fun nativeWarmupUsesRealHistoryWithoutOutputReplay() = DspStabilityScenarios.run("nativeWarmupUsesRealHistoryWithoutOutputReplay")
    @Test fun primedPumpUsesCapturedHistoryAndPreservesPrefix() = DspStabilityScenarios.run("primedPumpUsesCapturedHistoryAndPreservesPrefix")
    @Test fun routingUsesPlaylistOccurrence() = DspStabilityScenarios.run("routingUsesPlaylistOccurrence")
    @Test fun otherSinkResetCannotBlankSelectedMeter() = DspStabilityScenarios.run("otherSinkResetCannotBlankSelectedMeter")
    @Test fun mixedOutputHasSingleMeterOwner() = DspStabilityScenarios.run("mixedOutputHasSingleMeterOwner")
    @Test fun djCommandCapturesTargetNotCreationOrder() = DspStabilityScenarios.run("djCommandCapturesTargetNotCreationOrder")
    @Test fun ambiguousOccurrenceNeverUsesConstructionOrder() = DspStabilityScenarios.run("ambiguousOccurrenceNeverUsesConstructionOrder")
    @Test fun meterStereoEnergyAndPartition() = DspStabilityScenarios.run("meterStereoEnergyAndPartition")
    @Test fun meterOppositeChannelsDoNotCancel() = DspStabilityScenarios.run("meterOppositeChannelsDoNotCancel")
    @Test fun normalizationPartitionIndependent() = DspStabilityScenarios.run("normalizationPartitionIndependent")
    @Test fun normalizationMeasuresFramesNotChannelSamples() = DspStabilityScenarios.run("normalizationMeasuresFramesNotChannelSamples")
    @Test fun disabledNormalizationPreservesFloatBits() = DspStabilityScenarios.run("disabledNormalizationPreservesFloatBits")
    @Test fun normalizationDisableSlewsRatherThanSteps() = DspStabilityScenarios.run("normalizationDisableSlewsRatherThanSteps")
    @Test fun nativeMixCannotReceiveResidualNormalization() = DspStabilityScenarios.run("nativeMixCannotReceiveResidualNormalization")
    @Test fun confirmedMasterPreservesManualFade() = DspStabilityScenarios.run("confirmedMasterPreservesManualFade")
    @Test fun blockedSinkIsAttemptedOnlyOncePerTick() = DspStabilityScenarios.run("blockedSinkIsAttemptedOnlyOncePerTick")
    @Test fun blockedSinkTimesOutWithoutFakeClock() = DspStabilityScenarios.run("blockedSinkTimesOutWithoutFakeClock")
    @Test fun nativeFloatReadWithoutCursorMutation() = DspStabilityScenarios.run("nativeFloatReadWithoutCursorMutation")
    @Test fun nativeForeignDestroyCannotEraseCachedIngress() = DspStabilityScenarios.run("nativeForeignDestroyCannotEraseCachedIngress")
    @Test fun nativeForeignDestroyCannotEraseCachedExecutor() = DspStabilityScenarios.run("nativeForeignDestroyCannotEraseCachedExecutor")
    @Test fun cancelBeforePreparationDoesNotConstruct() = DspStabilityScenarios.run("cancelBeforePreparationDoesNotConstruct")
    @Test fun cancelPreparedDisposesExactlyOnce() = DspStabilityScenarios.run("cancelPreparedDisposesExactlyOnce")
    @Test fun adoptedResourceSurvivesLateCancellation() = DspStabilityScenarios.run("adoptedResourceSurvivesLateCancellation")
    @Test fun stalePreparationNeverPublished() = DspStabilityScenarios.run("stalePreparationNeverPublished")
    @Test fun preparationRaceDisposesOnWorker() = DspStabilityScenarios.run("preparationRaceDisposesOnWorker")
    @Test fun nativePreparedObjectsTransferToPlaybackOwner() = DspStabilityScenarios.run("nativePreparedObjectsTransferToPlaybackOwner")
    @Test fun rejectedPreparationDoesNotBuild() = DspStabilityScenarios.run("rejectedPreparationDoesNotBuild")
}
