package com.lmg.vk.ui.lyrics

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsPlaybackTest {
    private fun replay(initial: Long, positions: List<Long>) = runBlocking {
        var audioPosition = initial
        val frames = Channel<Unit>()
        val samples = Channel<Long>(Channel.UNLIMITED)
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            followLyricsPlayback({ audioPosition }, { frames.receive() }, { samples.trySend(it) })
        }
        val result = mutableListOf(samples.receive())
        positions.forEach { position ->
            audioPosition = position
            frames.send(Unit)
            result += samples.receive()
        }
        job.cancel()
        job.join()
        result
    }

    @Test fun openingMidTrackUsesTheAudioPositionImmediately() {
        assertEquals(listOf(45_000L, 45_016L, 45_032L), replay(45_000, listOf(45_016, 45_032)))
    }

    @Test fun backwardsSeekDoesNotNeedADiscontinuityEvent() {
        assertEquals(listOf(90_000L, 12_000L, 12_016L), replay(90_000, listOf(12_000, 12_016)))
    }

    @Test fun nextTrackResetsInsteadOfKeepingThePreviousTrackPosition() {
        assertEquals(listOf(180_000L, 0L, 16L, 32L), replay(180_000, listOf(0, 16, 32)))
    }

    @Test fun pauseAndBufferingNeverAdvanceLyricsWithoutAudio() {
        val frames = List(120) { 20_000L } + listOf(20_016L, 20_032L)
        assertEquals(listOf(20_000L) + frames, replay(20_000, frames))
    }

    @Test fun timelineCorrectionsAndSpeedChangesFollowAudioExactly() {
        val frames = listOf(9990L, 10_022L, 10_054L, 10_062L, 60_000L)
        assertEquals(listOf(10_000L) + frames, replay(10_000, frames))
    }
}
