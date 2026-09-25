package com.lmg.vk.artwork

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class AppleArtworkTest {
    private val query = ArtworkQuery("Bruised Sky", "Poppy", 200_000)
    private val still = """{"title":"Bruised Sky","artist":"Poppy","duration_ms":201000,"has_motion":false,"static_artwork":"https://is1-ssl.mzstatic.com/cover.jpg"}"""

    @Test fun staticCoverSurvivesAbsentMotion() {
        val value = AppleArtwork.parse(still, query)!!
        assertNotNull(value.cover)
        assertNull(value.motion)
    }

    @Test fun onlyExplicitNotFoundIsAConfirmedMiss() {
        assertNull(AppleArtwork.parse("""{"error":"not_found"}""", query))
        for (body in listOf("{}", """{"error":"Motion upstream error","has_motion":false}""")) {
            assertThrows(IOException::class.java) { AppleArtwork.parse(body, query) }
        }
    }

    @Test fun rejectsDifferentArtistVersionAndDuration() {
        for (body in listOf(still.replace("Poppy", "Other"),
            still.replace("Bruised Sky", "Bruised Sky (Live)"), still.replace("201000", "250000"))) {
            assertThrows(IOException::class.java) { AppleArtwork.parse(body, query) }
        }
    }

    @Test fun unknownDurationStillAllowsVerifiedTitleAndArtist() {
        assertNotNull(AppleArtwork.parse(still, query.copy(durationMs = 0)))
    }

    @Test fun actualVkBruisedSkyWithCyrillicYResolvesCoverAndMotion() {
        val vk = ArtworkQuery("Bruised Sk\u0443", "Poppy", 220750, "single")
        val body = """{"title":"Bruised Sky","artist":"Poppy","duration_ms":220837,"has_motion":true,"static_artwork":"https://is1-ssl.mzstatic.com/cover.jpg","tall":{"m3u8":"https://mvod.itunes.apple.com/video.m3u8"}}"""
        assertEquals("bruised sky", MotionArtwork.requestUrl(vk).queryParameter("title"))
        val result = AppleArtwork.parse(body, vk)!!
        assertNotNull(result.cover)
        assertNotNull(result.motion)
        assertNotEquals(MotionArtwork.requestUrl(vk).queryParameter("title"), "bruised sk\u0443")
    }

    @Test fun saoPauloMatchesCapitalAccentsAndFeaturedArtist() {
        val body = """{"title":"São Paulo","artist":"The Weeknd & Anitta","duration_ms":301623,"has_motion":false,"static_artwork":"https://is1-ssl.mzstatic.com/cover.jpg"}"""
        for (title in listOf("SÃO PAULO", "SAO PAULO", "São Paulo")) {
            assertNotNull(AppleArtwork.parse(body, ArtworkQuery(title, "The Weeknd", 301000))?.cover)
        }
    }

    @Test fun bothAssetsComeFromSameVerifiedResult() {
        val body = still.dropLast(1).replace("\"has_motion\":false", "\"has_motion\":true") +
            """, "tall":{"m3u8":"https://mvod.itunes.apple.com/motion.m3u8"}}"""
        val value = AppleArtwork.parse(body, query)!!
        assertNotNull(value.cover)
        assertEquals("https://mvod.itunes.apple.com/motion.m3u8", value.motion?.hls)
    }

    @Test fun requestCarriesDurationAndEscapesMetadataOnce() {
        val url = MotionArtwork.requestUrl(ArtworkQuery("Звезда", "Кино", 201_500))
        assertEquals("звезда", url.queryParameter("title"))
        assertEquals("кино", url.queryParameter("artist"))
        assertEquals("201.5", url.queryParameter("duration"))
        assertEquals("0", url.queryParameter("include_mp4"))
        assertNull(MotionArtwork.requestUrl(query.copy(durationMs = 0)).queryParameter("duration"))
    }
}
