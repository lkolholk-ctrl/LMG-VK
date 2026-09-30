package com.lmg.vk.network.methods

import com.lmg.vk.network.RawHttpResponse
import com.lmg.vk.engine.backend.nextArtistTrackOffset
import com.squareup.moshi.JsonDataException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ArtistAudioPageParserTest {
    private fun parse(body: String) = runBlocking {
        ArtistAudioPageParser.parse(object : RawHttpResponse {
            override val statusCode = 200
            override val url = "https://example.invalid/audio.getAudiosByArtist"
            override suspend fun bodyText() = body
        })
    }

    @Test fun parsesCountAndItemsInsteadOfReportingAnError() {
        val result = parse("""{"response":{"count":2,"items":[{"id":1,"owner_id":7},{"id":2,"owner_id":7}]}}""")
        assertNull(result.error)
        assertEquals(2, result.data?.count)
        assertEquals(listOf("7_1", "7_2"), result.data?.items?.map { it.fullId })
    }

    @Test fun preservesLegacyArrayAndSuccessfulEmptyPage() {
        val legacy = parse("""{"response":[{"id":1,"owner_id":7}]}""").data!!
        assertNull(legacy.count)
        assertEquals(1, legacy.items.size)
        assertTrue(parse("""{"response":[]} """).data!!.items.isEmpty())
        assertEquals(0, parse("""{"response":{"count":0,"items":[]}}""").data!!.count)
    }

    @Test fun realErrorRemainsRetryableAndIsNotAnEmptySuccess() {
        val result = parse("""{"error":{"error_code":6,"error_msg":"Too many requests"}}""")
        assertNull(result.data)
        assertNotNull(result.error)
    }

    @Test(expected = JsonDataException::class) fun malformedPayloadIsNotEndOfList() {
        parse("""{"response":{"count":200}}""")
    }

    @Test fun finalFullPageStopsWithoutAnExtraRequest() {
        assertEquals(100, nextArtistTrackOffset(0, 100, 200))
        assertNull(nextArtistTrackOffset(100, 100, 200))
        assertNull(nextArtistTrackOffset(200, 17, 217))
        assertNull(nextArtistTrackOffset(0, 0, 0))
    }

    @Test fun unknownTotalWaitsForEmptyResponseRatherThanTruncatingTracks() {
        assertEquals(17, nextArtistTrackOffset(0, 17, null))
        assertNull(nextArtistTrackOffset(17, 0, null))
    }
}
