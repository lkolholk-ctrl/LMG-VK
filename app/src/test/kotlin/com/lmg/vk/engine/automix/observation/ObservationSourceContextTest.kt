package com.lmg.vk.engine.automix.observation

import kotlinx.coroutines.runBlocking
import org.junit.Test

class ObservationSourceContextTest {
    @Test fun sourceBeforeReplies() = runBlocking { ObservationSourceContextScenarios.sourceBeforeReplies() }
    @Test fun sourceAfterRepliesReusesAnalysis() = runBlocking { ObservationSourceContextScenarios.sourceAfterRepliesReusesAnalysis() }
    @Test fun explicitAndSourceScopesConflict() = runBlocking { ObservationSourceContextScenarios.explicitAndSourceScopesConflict() }
    @Test fun duplicateAndConflictingSourceContexts() = runBlocking { ObservationSourceContextScenarios.duplicateAndConflictingSourceContexts() }
    @Test fun seekRevokesSourceFacts() = runBlocking { ObservationSourceContextScenarios.seekRevokesSourceFacts() }
    @Test fun lateFailureCannotRevokeSourceBinding() = runBlocking { ObservationSourceContextScenarios.lateFailureCannotRevokeSourceBinding() }
    @Test fun suspendAndCloseRevokeSourceContext() = runBlocking { ObservationSourceContextScenarios.suspendAndCloseRevokeSourceContext() }
    @Test fun sourceContextCannotAcquireImplicitFacts() = ObservationSourceContextScenarios.sourceContextCannotAcquireImplicitFacts()
}
