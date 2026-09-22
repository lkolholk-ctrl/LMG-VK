package com.lmg.vk.artwork

import org.junit.Assert.*
import org.junit.Test

class MotionArtworkTest {
    private val antidote = ArtworkQuery("ANTIDOTE", "HEALTH", 182_000)
    private fun fixture(name: String) = javaClass.getResourceAsStream("/artwork/$name")!!
        .bufferedReader().use { it.readText() }

    @Test fun backendRequestUsesCleanTitleArtistAndOptionalAlbum() {
        val request = ArtworkQuery("ORDINARY LOSS [vk.com/hithotmusic] Electronic",
            "HEALTH [320 kbps]", 240_000, "CONFLICT DLC [vk.com/public]")
        val url = MotionArtwork.requestUrl(request)
        assertEquals("https", url.scheme)
        assertEquals("/v2/motion", url.encodedPath)
        assertEquals("ordinary loss", url.queryParameter("title"))
        assertEquals("health", url.queryParameter("artist"))
        assertEquals("conflict dlc", url.queryParameter("album"))
        assertNull(MotionArtwork.requestUrl(request.copy(album = "")).queryParameter("album"))
    }

    @Test fun cleanBackendResponseMatchesOriginalVkTitle() {
        val json = fixture("motion-antidote.json").replace("ANTIDOTE", "ORDINARY LOSS")
        assertNotNull(MotionArtwork.parse(json,
            ArtworkQuery("ORDINARY LOSS [vk.com/hithotmusic] Electronic", "HEALTH", 240_000)))
        assertNull(MotionArtwork.parse(json,
            ArtworkQuery("ORDINARY LOSS (Live) [vk.com/hithotmusic] Electronic", "HEALTH", 240_000)))
    }

    @Test fun acceptsMatchingLiveBackendResponse() {
        val result = MotionArtwork.parse(fixture("motion-antidote.json"), antidote)
        assertNotNull(result)
        assertTrue(result!!.tall)
        assertTrue(result.mp4!!.endsWith("664x886-.mp4"))
        assertTrue(result.preview!!.endsWith("1200x1600bb.jpg"))
        assertEquals(result.hls, result.playbackUrl)
        assertTrue(result.hls!!.endsWith("default.m3u8"))
    }

    @Test fun backendWithoutVideoKeepsStaticArtwork() {
        assertNull(MotionArtwork.parse(fixture("motion-godzilla.json"),
            ArtworkQuery("Godzilla", "Eminem feat. Juice WRLD", 211_000)))
    }

    @Test fun rejectsWrongArtistAndAlternateVersion() {
        val json = fixture("motion-antidote.json")
        assertNull(MotionArtwork.parse(json, antidote.copy(artist = "Travis Scott")))
        assertNull(MotionArtwork.parse(json, antidote.copy(title = "ANTIDOTE (Live)")))
    }

    @Test fun featuredArtistCanAppearInTitleOrArtist() {
        val json = """{"has_motion":true,"title":"Song (feat. Guest)","artist":"Artist",
            "square":{"mp4":"https://example.org/video.mp4"}}"""
        assertNotNull(MotionArtwork.parse(json, ArtworkQuery("Song", "Artist feat. Guest", 100_000)))
        assertNotNull(MotionArtwork.parse(json, ArtworkQuery("Song", "Artist", 100_000)))
    }

    @Test fun malformedOrInsecureVideoUrlsAreRejected() {
        val json = fixture("motion-antidote.json").replace("https://", "http://")
        assertNull(MotionArtwork.parse(json, antidote))
        assertNull(MotionArtwork.parse("""{"has_motion":true,"title":"ANTIDOTE","artist":"HEALTH"}""", antidote))
    }

    @Test fun unavailableTallFallsBackToSquare() {
        val json = fixture("motion-antidote.json").replace("\"tall\":", "\"unused\":")
        val result = MotionArtwork.parse(json, antidote)!!
        assertFalse(result.tall)
        assertTrue(result.mp4!!.endsWith("768x768-.mp4"))
        assertEquals(result.hls, result.playbackUrl)
    }

    @Test fun insecureTallDoesNotHideValidSquare() {
        val json = """{"has_motion":true,"title":"ANTIDOTE","artist":"HEALTH",
            "tall":{"m3u8":"http://example.org/tall.m3u8"},
            "square":{"m3u8":"https://example.org/square.m3u8"}}"""
        val result = MotionArtwork.parse(json, antidote)!!
        assertFalse(result.tall)
        assertEquals("https://example.org/square.m3u8", result.playbackUrl)
    }

    @Test fun rawFragmentedMp4IsNotPreferredOverHlsTimeline() {
        val result = MotionArtwork.parse(fixture("motion-antidote.json"), antidote)!!
        assertNotEquals(result.mp4, result.playbackUrl)
        assertEquals(result.hls, result.playbackUrl)
        assertEquals(0xFF000000L, result.background)
    }
}
