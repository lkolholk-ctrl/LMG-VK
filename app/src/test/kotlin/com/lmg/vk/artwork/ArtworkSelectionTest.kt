package com.lmg.vk.artwork

import org.junit.Assert.*
import org.junit.Test

class ArtworkSelectionTest {
    private val wave = ArtworkQuery("Godzilla", "Eminem feat. Juice WRLD", 210_000)
    private val fullPlayer = wave.copy(album = "Music To Be Murdered By - Side B (Deluxe Edition)")

    @Test fun sharedSelectionKeepsAlbumHintsOutOfTrackIdentity() {
        assertTrue(ArtworkSelection(fullPlayer, null).matches(wave))
        val session = ArtworkQuery("Godzilla (feat. Juice WRLD)", "Eminem", 210_000)
        assertEquals(ItunesArtworkMatcher.lookupQuery(wave), ItunesArtworkMatcher.lookupQuery(session))
    }

    @Test fun selectionAppliesToImageAndBackgroundWithDifferentAlbumHints() {
        val shared = ArtworkSelection(fullPlayer, "https://is1-ssl.mzstatic.com/selected.jpg")
        assertTrue(shared.matches(wave))
        assertTrue(shared.matches(fullPlayer))
        assertEquals("https://is1-ssl.mzstatic.com/selected.jpg", shared.coverUrl)
    }

    @Test fun noMatchUsesOneVkFallbackForEveryConsumer() {
        val vk = "https://userapi.com/vk-thumb.jpg"
        val shared = ArtworkSelection(fullPlayer, vk)
        assertTrue(shared.matches(wave))
        assertEquals(vk, shared.coverUrl)
        assertTrue(ArtworkSelection(fullPlayer, null).matches(wave))
    }

    @Test fun selectionDoesNotLeakToAnotherSongOrVersion() {
        val shared = ArtworkSelection(fullPlayer, "https://is1-ssl.mzstatic.com/selected.jpg")
        assertFalse(shared.matches(wave.copy(title = "Venom")))
        assertFalse(shared.matches(wave.copy(title = "Godzilla (Live)")))
        assertFalse(shared.matches(wave.copy(artist = "Other")))
        assertFalse(shared.matches(wave.copy(durationMs = 250_000)))
    }

    @Test fun canonicalQueryIsIdempotent() {
        val canonical = ItunesArtworkMatcher.lookupQuery(fullPlayer)
        assertEquals(canonical, ItunesArtworkMatcher.lookupQuery(canonical))
        assertEquals("music to be murdered by side b deluxe edition", canonical.album)
    }
}
