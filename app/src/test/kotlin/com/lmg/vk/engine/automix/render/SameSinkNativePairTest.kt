package com.lmg.vk.engine.automix.render

import org.junit.Test

class SameSinkNativePairTest {
    @Test fun pairRequiresRealHeldBuffers() = SameSinkPairScenarios.run("pairRequiresRealHeldBuffers")
    @Test fun heldPairWithoutRegisteredPortsRejected() = SameSinkPairScenarios.run("heldPairWithoutRegisteredPortsRejected")
    @Test fun cannotReserveIdenticalDelegatePort() = SameSinkPairScenarios.run("cannotReserveIdenticalDelegatePort")
    @Test fun companionPendingRejectedBeforeInputClaim() = SameSinkPairScenarios.run("companionPendingRejectedBeforeInputClaim")
    @Test fun cannotWriteWithoutCommittedIngress() = SameSinkPairScenarios.run("cannotWriteWithoutCommittedIngress")
    @Test fun stagingDoesNotAuthorizeOutput() = SameSinkPairScenarios.run("stagingDoesNotAuthorizeOutput")
    @Test fun foreignIngressReceiptRejected() = SameSinkPairScenarios.run("foreignIngressReceiptRejected")
    @Test fun nativePrefixWrittenToOriginalPrimaryOnly() = SameSinkPairScenarios.run("nativePrefixWrittenToOriginalPrimaryOnly")
    @Test fun nativePrefixAndCueDataStayInOrder() = SameSinkPairScenarios.run("nativePrefixAndCueDataStayInOrder")
    @Test fun codecAckIsSeparateFromOutputConsumption() = SameSinkPairScenarios.run("codecAckIsSeparateFromOutputConsumption")
    @Test fun generationRevocationBlocksOutputAfterClaim() = SameSinkPairScenarios.run("generationRevocationBlocksOutputAfterClaim")
    @Test fun revokedClockLeaseDoesNotReachBackend() = SameSinkPairScenarios.run("revokedClockLeaseDoesNotReachBackend")
    @Test fun nativeOutputDoesNotBecomeLegacyAcceptance() = SameSinkPairScenarios.run("nativeOutputDoesNotBecomeLegacyAcceptance")
    @Test fun preclaimRollbackRestoresOriginals() = SameSinkPairScenarios.run("preclaimRollbackRestoresOriginals")
    @Test fun partialSinkWritesRetainNativeReadBuffer() = SameSinkPairScenarios.run("partialSinkWritesRetainNativeReadBuffer")
    @Test fun originalHeldOutputTimestampPreserved() = SameSinkPairScenarios.run("originalHeldOutputTimestampPreserved")
    @Test fun claimedInputsCannotRollbackEvenBeforeOutputWrite() = SameSinkPairScenarios.run("claimedInputsCannotRollbackEvenBeforeOutputWrite")
    @Test fun queuedLegacyFadePreventsOutputActivation() = SameSinkPairScenarios.run("queuedLegacyFadePreventsOutputActivation")
}
