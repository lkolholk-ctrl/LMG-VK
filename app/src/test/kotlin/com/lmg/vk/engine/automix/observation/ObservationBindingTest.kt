package com.lmg.vk.engine.automix.observation

import kotlinx.coroutines.runBlocking
import org.junit.Test

class ObservationBindingTest {
    @Test fun bindingBeforeReplies() = runBlocking { ObservationBindingScenarios.bindingBeforeReplies() }
    @Test fun bindingAfterObservedReusesResponses() = runBlocking { ObservationBindingScenarios.bindingAfterObservedReusesResponses() }
    @Test fun bindingDuringNativeCall() = runBlocking { ObservationBindingScenarios.bindingDuringNativeCall() }
    @Test fun lateFailureCannotRejectReboundScope() = runBlocking { ObservationBindingScenarios.lateFailureCannotRejectReboundScope() }
    @Test fun duplicateBinding() = runBlocking { ObservationBindingScenarios.duplicateBinding() }
    @Test fun conflictingBinding() = runBlocking { ObservationBindingScenarios.conflictingBinding() }
    @Test fun staleBinding() = runBlocking { ObservationBindingScenarios.staleBinding() }
    @Test fun forgedTicket() = runBlocking { ObservationBindingScenarios.forgedTicket() }
    @Test fun bindingAfterClose() = runBlocking { ObservationBindingScenarios.bindingAfterClose() }
    @Test fun newGenerationClearsScope() = runBlocking { ObservationBindingScenarios.newGenerationClearsScope() }
    @Test fun samePairRefreshPreservesScope() = runBlocking { ObservationBindingScenarios.samePairRefreshPreservesScope() }
    @Test fun onePendingReplyIsPreserved() = runBlocking { ObservationBindingScenarios.onePendingReplyIsPreserved() }
    @Test fun scopeCopiesCallerLists() = runBlocking { ObservationBindingScenarios.scopeCopiesCallerLists() }
    @Test fun requestCopiesOwnedLists() = runBlocking { ObservationBindingScenarios.requestCopiesOwnedLists() }
    @Test fun pauseRevokesScope() = runBlocking { ObservationBindingScenarios.pauseRevokesScope() }
    @Test fun backendChangeRevokesScope() = runBlocking { ObservationBindingScenarios.backendChangeRevokesScope() }
}
