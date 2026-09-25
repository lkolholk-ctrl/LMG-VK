package com.lmg.vk.ui.lyrics

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.runtime.structuralEqualityPolicy
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.lyricAnimationPositionMs
import org.junit.Assert.*
import org.junit.Test

class LyricsRenderSchedulingTest {
    @Test fun inactiveRowsDoNotInvalidateOnEachAudioFrameButSeeksReactivateThem() {
        val position = mutableIntStateOf(0)
        val drawPosition = derivedStateOf(structuralEqualityPolicy()) {
            lyricAnimationPositionMs(position.intValue, 10_000, 14_000)
        }
        val observer = SnapshotStateObserver { it() }
        val scope = Any()
        var invalidations = 0
        val onChanged: (Any) -> Unit = { invalidations++ }
        fun observe() = observer.observeReads(scope, onChanged) { drawPosition.value }
        fun move(time: Int) {
            Snapshot.withMutableSnapshot { position.intValue = time }
            Snapshot.sendApplyNotifications()
            observe()
        }
        observer.start()
        try {
            observe()
            for (time in 16..9_984 step 16) move(time)
            assertEquals(0, invalidations)
            move(11_000)
            assertEquals(1, invalidations)
            repeat(60) { move(11_000) }
            assertEquals(1, invalidations)
            move(20_000)
            assertEquals(2, invalidations)
            for (time in 20_016..22_000 step 16) move(time)
            assertEquals(2, invalidations)
            move(12_000)
            assertEquals(3, invalidations)
            assertEquals(12_000, drawPosition.value)
        } finally {
            observer.stop()
            observer.clear()
        }
    }

    @Test fun releaseTailRemainsAnimatedWithoutOverflowOnLongTracks() {
        assertEquals(15_500, lyricAnimationPositionMs(15_500, 10_000, 14_000))
        assertEquals(18_000, lyricAnimationPositionMs(30_000, 10_000, 14_000))
        assertEquals(Int.MAX_VALUE, lyricAnimationPositionMs(Int.MAX_VALUE, 0, Int.MAX_VALUE - 10))
    }
}
