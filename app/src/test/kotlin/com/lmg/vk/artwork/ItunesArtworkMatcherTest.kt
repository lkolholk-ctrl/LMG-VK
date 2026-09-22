package com.lmg.vk.artwork

import org.junit.Assert.*
import org.junit.Test

class ItunesArtworkMatcherTest {
    private val query = ArtworkQuery("No Love", "Eminem п.у. Lil Wayne", 299_000)
    private fun candidate(title: String = "No Love (feat. Lil Wayne)", artist: String = "Eminem", duration: Long = 299_100) =
        ItunesArtworkCandidate(title, artist, duration, "Recovery", "https://is1-ssl.mzstatic.com/image.jpg", "https://music.apple.com/track")

    @Test fun matchesFeaturedArtistsAcrossMetadataFields() {
        val match = candidate()
        assertEquals(match, ItunesArtworkMatcher.match(query, listOf(match)))
    }

    @Test fun rejectsWrongArtistsAndDifferentVersions() {
        for (wrong in listOf(candidate(artist = "Other"), candidate(title = "No Love (Live)"),
            candidate(title = "No Love (Remix)"), candidate(duration = 310_000))) {
            assertNull(ItunesArtworkMatcher.match(query, listOf(wrong)))
        }
    }

    @Test fun doesNotRemoveVersionNamesToForceMatch() {
        val original = candidate("Sweet Dreams", "Dwin, Echo", 180_000)
        assertNull(ItunesArtworkMatcher.match(ArtworkQuery("Sweet Dreams (Sped Up)", "Dwin & Echo", 180_000), listOf(original)))
    }

    @Test fun toleratesCasePunctuationAndArtistSeparators() {
        val match = candidate("SWEET DREAMS", "Dwin & Echo", 180_000)
        assertEquals(match, ItunesArtworkMatcher.match(ArtworkQuery("Sweet Dreams!", "Dwin, Echo", 181_000), listOf(match)))
    }

    @Test fun prefersKnownAlbumAndChecksDuration() {
        val album = candidate().copy(album = "Recovery")
        val compilation = album.copy(album = "Hits", durationMs = 299_000)
        assertEquals(album, ItunesArtworkMatcher.match(query.copy(album = "Recovery"), listOf(compilation, album)))
        assertNull(ItunesArtworkMatcher.match(query.copy(durationMs = 0), listOf(album)))
    }

    @Test fun missingOrUnsafeArtworkFallsBack() {
        assertNull(ItunesArtworkMatcher.match(query, emptyList()))
        assertNull(ItunesArtworkMatcher.match(query, listOf(candidate().copy(artworkUrl = ""))))
        assertNull(ItunesArtworkMatcher.match(query, listOf(candidate().copy(artworkUrl = "http://example.com/art"))))
    }

    @Test fun ordinaryLossMatchesDespiteVkAdvertisingAndQualityTags() {
        val raw = ArtworkQuery("ORDINARY LOSS [vk.com/hithotmusic] Electronic [320 kbps]",
            "HEALTH [https://vk.com/hithotmusic]", 240_000, "CONFLICT DLC [320 kbps]")
        val clean = ItunesArtworkMatcher.lookupQuery(raw)
        assertEquals(ArtworkQuery("ordinary loss", "health", 240_000, "conflict dlc"), clean)
        assertEquals(clean, ItunesArtworkMatcher.lookupQuery(clean))
        val official = candidate("ORDINARY LOSS", "HEALTH", 240_000).copy(album = "CONFLICT DLC")
        assertEquals(official, ItunesArtworkMatcher.match(raw, listOf(official)))
        assertEquals(official, ItunesArtworkMatcher.match(clean, listOf(official)))
    }

    @Test fun cleanupPreservesVersionsFeaturesAndRealGenreWords() {
        val raw = ArtworkQuery("Song (Live) (feat. Guest) [vk.com/public] Electronic", "Artist", 100_000)
        assertEquals("song live", ItunesArtworkMatcher.lookupQuery(raw).title)
        assertEquals("artist, guest", ItunesArtworkMatcher.lookupQuery(raw).artist)
        assertNull(ItunesArtworkMatcher.match(raw, listOf(candidate("Song (feat. Guest)", "Artist", 100_000))))
        for (title in listOf("Electronic", "Pop", "Song (Remix)", "Song (Sped Up)", "Song (Radio Edit)")) {
            assertEquals(ItunesArtworkMatcher.normalize(title),
                ItunesArtworkMatcher.lookupQuery(ArtworkQuery("$title [320 kbps]", "Artist", 100_000)).title)
        }
    }

    @Test fun matchesWhenCollaboratorMissingInQueryOrCandidate() {
        val candidateWithFeat = candidate("Fade", "Sub Focus & Inéz", 272_678).copy(album = "Contact")
        val queryOnlyMainArtist = ArtworkQuery("Fade", "Sub Focus", 271_500)
        assertEquals(candidateWithFeat, ItunesArtworkMatcher.match(queryOnlyMainArtist, listOf(candidateWithFeat)))

        val candidateOnlyMain = candidate("Fade", "Sub Focus", 272_678).copy(album = "Contact")
        val queryWithFeat = ArtworkQuery("Fade", "Sub Focus & Inéz", 271_500)
        assertEquals(candidateOnlyMain, ItunesArtworkMatcher.match(queryWithFeat, listOf(candidateOnlyMain)))
    }
}
