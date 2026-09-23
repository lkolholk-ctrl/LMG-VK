package com.lmg.vk.engine.automix

import com.lmg.vk.engine.automix.observation.AppleSongAnalysis.Availability
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import org.junit.Assert.*
import org.junit.Test

class ObservationValidationTest {
    private val minimal = "{}".encodeToByteArray()

    @Test fun preflightDoesNotLoadOrInvokeJni() {
        val parser = AppleSongAnalysisParser { _, _ -> error("JNI must not run") }
        assertEquals(AppleSongAnalysisParser.Result.Rejected(AppleSongAnalysisParser.Reason.INVALID_SONG_ID), parser.parse(minimal, ""))
        assertEquals(AppleSongAnalysisParser.Result.Rejected(AppleSongAnalysisParser.Reason.INVALID_SONG_ID), parser.parse(minimal, "\uD800"))
        assertEquals(AppleSongAnalysisParser.Result.Rejected(AppleSongAnalysisParser.Reason.INPUT_TOO_LARGE),
            parser.parse(ByteArray(AppleSongAnalysisParser.MAX_INPUT_BYTES + 1), "wanted"))
    }

    @Test fun missingAndUnresolvedStayDistinct() {
        val parser = AppleSongAnalysisParser { _, _ ->
            """{"schemaVersion":1,"analysis":{"id":"wanted","supportsSmartTransitions":false,"audio":{"id":"a"}}}""".encodeToByteArray()
        }
        val result = parser.parse(minimal, "wanted") as AppleSongAnalysisParser.Result.Parsed
        assertEquals(Availability.LINKAGE_ONLY, result.analysis.audioAvailability)
        assertEquals(Availability.ABSENT, result.analysis.flexAvailability)
        assertNull(result.analysis.durationInMillis)
        assertEquals(false, result.analysis.supportsSmartTransitions)
    }

    @Test fun bridgeVersionAndIdentityMustMatch() {
        for (response in listOf(
            """{"schemaVersion":2,"analysis":{"id":"wanted"}}""",
            """{"schemaVersion":1,"analysis":{"id":"other"}}""",
            "not JSON",
        )) {
            val parser = AppleSongAnalysisParser { _, _ -> response.encodeToByteArray() }
            assertEquals(AppleSongAnalysisParser.Result.Rejected(AppleSongAnalysisParser.Reason.BRIDGE_CONTRACT_MISMATCH),
                parser.parse(minimal, "wanted"))
        }
    }

    @Test fun nativeFailuresHaveStableNonPayloadReasons() {
        val invalid = AppleSongAnalysisParser { _, _ -> throw IllegalArgumentException("sensitive payload") }
        assertEquals(AppleSongAnalysisParser.Result.Rejected(AppleSongAnalysisParser.Reason.INVALID_INPUT), invalid.parse(minimal, "wanted"))
        for (failure in listOf(UnsatisfiedLinkError("missing"), NoClassDefFoundError("failed initialization"))) {
            val parser = AppleSongAnalysisParser { _, _ -> throw failure }
            assertEquals(AppleSongAnalysisParser.Result.Rejected(AppleSongAnalysisParser.Reason.NATIVE_UNAVAILABLE), parser.parse(minimal, "wanted"))
        }
    }

    @Test fun catalogChecksumIsCheckedBeforeJni() {
        val result = TransitionStyleCatalog.verify("[]".encodeToByteArray(), TransitionStyleCatalog.BUNDLED_SHA256) {
            error("Unverified catalog must never reach JNI")
        }
        assertEquals(TransitionStyleCatalog.LoadResult.Rejected(TransitionStyleCatalog.Reason.CHECKSUM_MISMATCH), result)
    }

    @Test fun boundedAssetReaderClosesOnRejection() {
        var closed = false
        val input = object : ByteArrayInputStream(ByteArray(TransitionStyleCatalog.MAX_INPUT_BYTES + 1)) {
            override fun close() { closed = true; super.close() }
        }
        assertEquals(TransitionStyleCatalog.LoadResult.Rejected(TransitionStyleCatalog.Reason.INPUT_TOO_LARGE),
            TransitionStyleCatalog.loadBundled { input })
        assertTrue(closed)
        assertEquals(TransitionStyleCatalog.LoadResult.Rejected(TransitionStyleCatalog.Reason.RESOURCE_UNAVAILABLE),
            TransitionStyleCatalog.loadBundled { throw FileNotFoundException() })
    }

    @Test fun identityRetainsMicrosAndRepeatInstance() {
        val uid = Any()
        val first = AutoMixStreamIdentity("same", uid, 1, 100)
        val repeated = first.copy(windowSequenceNumber = 2)
        assertNotEquals(first, repeated)
        assertEquals(160L, first.mediaItemTimeUs(1000, 940))
        assertNull(AutoMixStreamIdentity.from(null, null))
    }

    @Test(expected = ArithmeticException::class)
    fun identityRejectsClockOverflow() {
        AutoMixStreamIdentity("id", Any(), 1, 1).mediaItemTimeUs(Long.MAX_VALUE, 0)
    }
}
