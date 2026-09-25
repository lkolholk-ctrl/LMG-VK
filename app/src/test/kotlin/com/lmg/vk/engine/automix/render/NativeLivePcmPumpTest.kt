package com.lmg.vk.engine.automix.render

import org.junit.Test

/** Requires the complete freshly built host JNI library; never skipped for missing native code. */
class NativeLivePcmPumpTest {
    @Test fun realDspPrefixAndBContinuation() = LivePcmPumpScenarios.run("realDspPrefixAndBContinuation")
    @Test fun realDspStarvationRetainsOutgoing() = LivePcmPumpScenarios.run("realDspStarvationRetainsOutgoing")
    @Test fun partialByteWritesDoNotReplay() = LivePcmPumpScenarios.run("partialByteWritesDoNotReplay")
    @Test fun pcm16ExplicitConversion() = LivePcmPumpScenarios.run("pcm16ExplicitConversion")
    @Test fun unequalRatesReachTrueEof() = LivePcmPumpScenarios.run("unequalRatesReachTrueEof")
    @Test fun immutablePlanCopy() = LivePcmPumpScenarios.run("immutablePlanCopy")
    @Test fun malformedPlanRejected() = LivePcmPumpScenarios.run("malformedPlanRejected")
    @Test fun nativeOwnerThreadEnforced() = LivePcmPumpScenarios.run("nativeOwnerThreadEnforced")
    @Test fun exactCueAfterAcceptedPrefix() = LivePcmPumpScenarios.run("exactCueAfterAcceptedPrefix")
    @Test fun cancelInsidePartialWrite() = LivePcmPumpScenarios.run("cancelInsidePartialWrite")
    @Test fun postCueOfferCannotBeReserved() = LivePcmPumpScenarios.run("postCueOfferCannotBeReserved")
    @Test fun nonfiniteInputRejected() = LivePcmPumpScenarios.run("nonfiniteInputRejected")
    @Test fun stereoFramesAndChannelsPreserved() = LivePcmPumpScenarios.run("stereoFramesAndChannelsPreserved")
}
