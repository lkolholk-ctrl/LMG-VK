package com.lmg.vk.engine.automix.observation

import kotlinx.coroutines.runBlocking
import org.junit.Test

class ObservationPipelineTest {
    @Test fun completePair() = runBlocking { ObservationPipelineScenarios.completePair() }
    @Test fun incomingMayCompleteFirst() = runBlocking { ObservationPipelineScenarios.incomingMayCompleteFirst() }
    @Test fun duplicatesAreSingleUse() = runBlocking { ObservationPipelineScenarios.duplicatesAreSingleUse() }
    @Test fun forgedTicketIsNotAccepted() = runBlocking { ObservationPipelineScenarios.forgedTicketIsNotAccepted() }
    @Test fun samePairIsDeduplicated() = runBlocking { ObservationPipelineScenarios.samePairIsDeduplicated() }
    @Test fun repeatAndSeekGetNewGenerations() = runBlocking { ObservationPipelineScenarios.repeatAndSeekGetNewGenerations() }
    @Test fun duplicateMediaIdsKeepSeparateOccurrences() = runBlocking { ObservationPipelineScenarios.duplicateMediaIdsKeepSeparateOccurrences() }
    @Test fun lateDecodeIsDiscarded() = runBlocking { ObservationPipelineScenarios.lateDecodeIsDiscarded() }
    @Test fun lateNativeResultIsDiscardedAndWorkIsSerialized() = runBlocking { ObservationPipelineScenarios.lateNativeResultIsDiscardedAndWorkIsSerialized() }
    @Test fun pauseRevokesThenResumeReissues() = runBlocking { ObservationPipelineScenarios.pauseRevokesThenResumeReissues() }
    @Test fun closeRevokesEverything() = runBlocking { ObservationPipelineScenarios.closeRevokesEverything() }
    @Test fun closeDuringNativeWorkStaysClosed() = runBlocking { ObservationPipelineScenarios.closeDuringNativeWorkStaysClosed() }
    @Test fun oversizedResponseNeverReachesDecoder() = runBlocking { ObservationPipelineScenarios.oversizedResponseNeverReachesDecoder() }
    @Test fun invalidSongIdIsNotAccepted() = runBlocking { ObservationPipelineScenarios.invalidSongIdIsNotAccepted() }
    @Test fun parserFailureRevokesBothTickets() = runBlocking { ObservationPipelineScenarios.parserFailureRevokesBothTickets() }
    @Test fun nativeFailureDoesNotEscapeToOwner() = runBlocking { ObservationPipelineScenarios.nativeFailureDoesNotEscapeToOwner() }
    @Test fun deadlinesDiscardBlockingWork() = runBlocking { ObservationPipelineScenarios.deadlinesDiscardBlockingWork() }
    @Test fun sourceRevisionChangeInvalidatesIdentity() = runBlocking { ObservationPipelineScenarios.sourceRevisionChangeInvalidatesIdentity() }
    @Test fun durationUpdateTriggersFreshCalculation() = runBlocking { ObservationPipelineScenarios.durationUpdateTriggersFreshCalculation() }
    @Test fun diagnosticObjectsRedactSources() = runBlocking { ObservationPipelineScenarios.diagnosticObjectsRedactSources() }
}
