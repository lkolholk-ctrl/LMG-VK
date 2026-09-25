package com.lmg.vk.artwork

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class OfflineMotionIndexTest {
    @get:Rule val temp = TemporaryFolder()
    private val query = ArtworkQuery("Bruised Skу", "Poppy", 220_750)
    private val clip = MotionArtwork(null, "https://cdn.example/clip.m3u8", null, true, 0xff123456)

    @Test fun survivesRestartWithoutNetworkOrExpiry() {
        OfflineMotionIndex(temp.root).commit("-1_2", query, clip)
        assertEquals(clip, OfflineMotionIndex(temp.root).find("-1_2", query))
        assertEquals(clip, OfflineMotionIndex(temp.root).find(null, ArtworkQuery("Bruised Sky", "Poppy", 220_837)))
    }
    @Test fun rejectsWrongTrackAndWrongDuration() {
        val index = OfflineMotionIndex(temp.root)
        index.commit("1", query, clip)
        assertNull(index.find("2", query))
        assertNull(index.find("1", ArtworkQuery("Other song", "Poppy", 220_750)))
        assertNull(index.find("1", query.copy(durationMs = 90_000)))
    }
    @Test fun partialOrDamagedManifestIsNotPublished() {
        File(temp.root, "unfinished.partial").writeText("{\"has_motion\":true}")
        File(temp.root, OfflineMotionIndex.key("1") + ".json").writeText("{")
        assertNull(OfflineMotionIndex(temp.root).find(null, query))
    }
    @Test fun sharedClipIsRetainedUntilLastOwnerIsDeleted() {
        val index = OfflineMotionIndex(temp.root)
        index.commit("1", query, clip); index.commit("2", query, clip)
        assertEquals(clip, index.remove("1"))
        assertTrue(index.containsUrl(clip.playbackUrl))
        assertEquals(clip, index.remove("2"))
        assertFalse(index.containsUrl(clip.playbackUrl))
    }
    @Test fun mp4OnlyAndUnsafeTrackIdsRoundTrip() {
        val index = OfflineMotionIndex(temp.root)
        val mp4 = clip.copy(hls = null, mp4 = "https://cdn.example/clip.mp4")
        index.commit("../../song", query, mp4)
        assertEquals(mp4, index.find("../../song", query))
        assertTrue(index.containsUrl(mp4.playbackUrl))
        assertEquals(1, temp.root.listFiles()!!.size)
        index.clear()
        assertNull(index.find(null, query))
    }
}
