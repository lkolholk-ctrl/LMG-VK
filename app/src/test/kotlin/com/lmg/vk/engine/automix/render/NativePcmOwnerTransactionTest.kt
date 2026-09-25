package com.lmg.vk.engine.automix.render
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
/** Real native admission. The lease in scenarios is a test double, not an AudioTrack. */
class NativePcmOwnerTransactionTest {
    companion object {
        @BeforeClass @JvmStatic fun requireNative() {
            assumeTrue("Pass -PautomixHostLibraryPath",System.getProperty("automix.requireJni")=="true")
        }
    }
    @Test fun stageCopiesNeitherOriginalConsumed() = PcmOwnerTransactionScenarios.run("stageCopiesNeitherOriginalConsumed")
    @Test fun commitBothVisibleNoEarlyCodecAck() = PcmOwnerTransactionScenarios.run("commitBothVisibleNoEarlyCodecAck")
    @Test fun eachOriginalAcknowledgedOnItsCallback() = PcmOwnerTransactionScenarios.run("eachOriginalAcknowledgedOnItsCallback")
    @Test fun nativeConsumptionNotSinkLedger() = PcmOwnerTransactionScenarios.run("nativeConsumptionNotSinkLedger")
    @Test fun prefixSeparatelyPreserved() = PcmOwnerTransactionScenarios.run("prefixSeparatelyPreserved")
    @Test fun pcm16ConversionAndStereo() = PcmOwnerTransactionScenarios.run("pcm16ConversionAndStereo")
    @Test fun unreadNativeCopiesIndependentOfOriginals() = PcmOwnerTransactionScenarios.run("unreadNativeCopiesIndependentOfOriginals")
    @Test fun rollbackBeforeCommitReturnsOriginalExactly() = PcmOwnerTransactionScenarios.run("rollbackBeforeCommitReturnsOriginalExactly")
    @Test fun deadlineBeforeCommitRollsBack() = PcmOwnerTransactionScenarios.run("deadlineBeforeCommitRollsBack")
    @Test fun revokedLeaseBeforeCommitRollsBack() = PcmOwnerTransactionScenarios.run("revokedLeaseBeforeCommitRollsBack")
    @Test fun generationChangeBeforeCommitRollsBack() = PcmOwnerTransactionScenarios.run("generationChangeBeforeCommitRollsBack")
    @Test fun generationChangeAfterCommitNeverReplays() = PcmOwnerTransactionScenarios.run("generationChangeAfterCommitNeverReplays")
    @Test fun releaseAfterCommitRequiresFlush() = PcmOwnerTransactionScenarios.run("releaseAfterCommitRequiresFlush")
    @Test fun deadlineDoesNotCancelOwnedInput() = PcmOwnerTransactionScenarios.run("deadlineDoesNotCancelOwnedInput")
    @Test fun doubleCommitAndForeignReceiptRejected() = PcmOwnerTransactionScenarios.run("doubleCommitAndForeignReceiptRejected")
    @Test fun capacityFailureBeforeClaimReturnsOriginals() = PcmOwnerTransactionScenarios.run("capacityFailureBeforeClaimReturnsOriginals")
    @Test fun nonfiniteSecondSideAtomicStageFailure() = PcmOwnerTransactionScenarios.run("nonfiniteSecondSideAtomicStageFailure")
    @Test fun backpressureAcceptsOnlyFramesWithCapacity() = PcmOwnerTransactionScenarios.run("backpressureAcceptsOnlyFramesWithCapacity")
    @Test fun partialOwnerRetryMustUseSameOriginal() = PcmOwnerTransactionScenarios.run("partialOwnerRetryMustUseSameOriginal")
    @Test fun postcommitNativeFailureNeverFallsBack() = PcmOwnerTransactionScenarios.run("postcommitNativeFailureNeverFallsBack")
    @Test fun wrongThreadCannotForwardOwnedBuffer() = PcmOwnerTransactionScenarios.run("wrongThreadCannotForwardOwnedBuffer")
    @Test fun flushPermitsNewLegacyStreamOnlyAfterDiscard() = PcmOwnerTransactionScenarios.run("flushPermitsNewLegacyStreamOnlyAfterDiscard")
    @Test fun pauseDoesNotAuthorizeLegacyReplay() = PcmOwnerTransactionScenarios.run("pauseDoesNotAuthorizeLegacyReplay")
    @Test fun outputTokenChangeAfterCommitQuarantines() = PcmOwnerTransactionScenarios.run("outputTokenChangeAfterCommitQuarantines")
    @Test fun nativeCommitFailureAfterClaimQuarantines() = PcmOwnerTransactionScenarios.run("nativeCommitFailureAfterClaimQuarantines")
    @Test fun cancelInsideNativeCommitDiscardsBoth() = PcmOwnerTransactionScenarios.run("cancelInsideNativeCommitDiscardsBoth")
    @Test fun cancelInsideStageRollsBackWithoutClaim() = PcmOwnerTransactionScenarios.run("cancelInsideStageRollsBackWithoutClaim")
    @Test fun nonzeroOffsetsAndReadOnlyInput() = PcmOwnerTransactionScenarios.run("nonzeroOffsetsAndReadOnlyInput")
    @Test fun invalidNativeReadDoesNotConsume() = PcmOwnerTransactionScenarios.run("invalidNativeReadDoesNotConsume")
    @Test fun nativeTicketCannotCrossOwners() = PcmOwnerTransactionScenarios.run("nativeTicketCannotCrossOwners")
    @Test fun ownerResultNeverClaimsAudioOrDSP() = PcmOwnerTransactionScenarios.run("ownerResultNeverClaimsAudioOrDSP")
    @Test fun largerSubsequentBufferUsesBoundedPartialAcceptance() = PcmOwnerTransactionScenarios.run("largerSubsequentBufferUsesBoundedPartialAcceptance")
    @Test fun foreignReceiptCannotCommitPreparedInput() = PcmOwnerTransactionScenarios.run("foreignReceiptCannotCommitPreparedInput")
    @Test fun publicationWinsBetweenValidationAndClaim() = PcmOwnerTransactionScenarios.run("publicationWinsBetweenValidationAndClaim")
    @Test fun ownerPollPreservesCueSplitAndCursor() = PcmOwnerTransactionScenarios.run("ownerPollPreservesCueSplitAndCursor")
    @Test fun cancelDuringPollDoesNotPublishDestinationCursor() = PcmOwnerTransactionScenarios.run("cancelDuringPollDoesNotPublishDestinationCursor")
    @Test fun randomizedOwnerAcksConservePcmWithoutLegacy() = PcmOwnerTransactionScenarios.run("randomizedOwnerAcksConservePcmWithoutLegacy")
    @Test fun replacedAttemptDiscardsOldInvisibleStageOnCallback() = PcmOwnerTransactionScenarios.run("replacedAttemptDiscardsOldInvisibleStageOnCallback")
    @Test fun replacedAttemptDiscardsOldInvisibleStageOnFlush() = PcmOwnerTransactionScenarios.run("replacedAttemptDiscardsOldInvisibleStageOnFlush")
    @Test fun wrongSubsequentPTSQuarantines() = PcmOwnerTransactionScenarios.run("wrongSubsequentPTSQuarantines")
}
