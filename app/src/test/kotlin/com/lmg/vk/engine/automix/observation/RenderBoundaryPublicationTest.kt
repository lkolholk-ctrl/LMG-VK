package com.lmg.vk.engine.automix.observation
import org.junit.Test
class RenderBoundaryPublicationTest {
    @Test fun compiledPublication() = RenderBoundaryPublicationScenarios.compiledPublication()
    @Test fun mismatchedEpoch() = RenderBoundaryPublicationScenarios.mismatchedEpoch()
    @Test fun mismatchedRevision() = RenderBoundaryPublicationScenarios.mismatchedRevision()
    @Test fun missingPair() = RenderBoundaryPublicationScenarios.missingPair()
    @Test fun rejectedSchedule() = RenderBoundaryPublicationScenarios.rejectedSchedule()
    @Test fun closePublication() = RenderBoundaryPublicationScenarios.closePublication()
    @Test fun barriersBeforeFlow() = RenderBoundaryPublicationScenarios.barriersBeforeFlow()
    @Test fun sameEpochBindInvalidatesBeforeRecalculation() = RenderBoundaryPublicationScenarios.sameEpochBindInvalidatesBeforeRecalculation()
}
