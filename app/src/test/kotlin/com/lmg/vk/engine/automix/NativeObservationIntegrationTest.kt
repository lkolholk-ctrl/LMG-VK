package com.lmg.vk.engine.automix

import com.lmg.vk.engine.automix.nativecore.NativeObservationBridge
import com.lmg.vk.engine.automix.observation.AppleSongAnalysis.Availability
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test

/** Real host JNI, not a mock. CI supplies -PautomixHostLibraryPath and must not skip this class. */
class NativeObservationIntegrationTest {
    companion object {
        @BeforeClass @JvmStatic fun requireNativeLibrary() {
            assumeTrue("Pass -PautomixHostLibraryPath to run host JNI integration tests",
                System.getProperty("automix.requireJni") == "true")
            // In CI, loading/symbol failures fail the tests rather than being treated as a skip.
            NativeObservationBridge.describeSong(
                """{"data":[{"type":"songs","id":"probe"}]}""".encodeToByteArray(), "probe".encodeToByteArray())
        }
    }

    @Test fun nativeDecodePreservesIncludedFractionsAndUnknownEnums() {
        val source = """{"data":[{"type":"songs","id":"other"},
          {"type":"songs","id":"тест🎵","attributes":{"durationInMillis":1250.5,"supportsSmartTransitions":false},
           "relationships":{"audio-analysis":{"data":[{"type":"audio-analysis","id":"a"}]}}}],
          "included":[{"type":"audio-analysis","id":"a","attributes":{
            "bpm":{"main":120.25},"beats":{"beatsInMilliseconds":[0,500.25],"barsInMilliseconds":[]},
            "key":{"main":{"tonic":"C#","mode":"future-mode"}},
            "loudnessCurve":{"value":[-10,-11]},"vocalActivity":[]}}]}"""
        val result = AppleSongAnalysisParser().parse(source.encodeToByteArray(), "тест🎵") as AppleSongAnalysisParser.Result.Parsed
        assertEquals("тест🎵", result.analysis.id)
        assertEquals(1250.5, result.analysis.durationInMillis!!, 0.0)
        assertEquals(Availability.RESOLVED, result.analysis.audioAvailability)
        val audio = result.analysis.audio!!.attributes!!
        assertEquals(120.25, audio.bpm!!.main!!, 0.0)
        assertNull(audio.bpm.ending)
        assertEquals(500.25, audio.beats!!.beatsInMilliseconds!![1], 0.0)
        assertTrue(audio.beats.barsInMilliseconds!!.isEmpty())
        assertEquals("future-mode", audio.key!!.main!!.mode)
        assertNull(audio.loudnessCurve!!.samplingFrequency)
        assertTrue(audio.vocalActivity!!.isEmpty())
    }

    @Test fun malformedAndWrongSongNeverFallBackToFirstResource() {
        for (source in listOf(
            """{"data":[{"type":"songs","id":"other"}]}""",
            """{"data":[],"data":[]}""",
            "[]",
        )) {
            assertEquals(AppleSongAnalysisParser.Result.Rejected(AppleSongAnalysisParser.Reason.INVALID_INPUT),
                AppleSongAnalysisParser().parse(source.encodeToByteArray(), "wanted"))
        }
    }

    @Test fun bundledCatalogIsVerifiedAndDefensivelyOwned() {
        val bytes = File(requireNotNull(System.getProperty("automix.catalogPath"))).readBytes()
        val result = TransitionStyleCatalog.load(bytes) as TransitionStyleCatalog.LoadResult.Loaded
        val catalog = result.catalog
        assertEquals(TransitionStyleCatalog.BUNDLED_SHA256, catalog.sha256)
        assertEquals(14, catalog.styles.size)
        assertEquals(55, catalog.styles.sumOf { it.instructionCount })
        assertEquals(74, catalog.styles.sumOf { it.automationCount })
        assertEquals("Ease-in Ring-out", catalog.style(4)!!.name)
        assertEquals("overlap + Reverb", catalog.style(33)!!.name)
        assertEquals(16, catalog.style(12)!!.duration!!.toInt())
        assertNull(catalog.style(5))
        val original = catalog.nativeSourceBytes()
        bytes[0] = 0
        val externalCopy = catalog.nativeSourceBytes().also { it[0] = 0 }
        assertFalse(original.contentEquals(externalCopy))
        assertArrayEquals(original, catalog.nativeSourceBytes())
        assertEquals(TransitionStyleCatalog.LoadResult.Rejected(TransitionStyleCatalog.Reason.CHECKSUM_MISMATCH),
            TransitionStyleCatalog.load(bytes))
    }
}
