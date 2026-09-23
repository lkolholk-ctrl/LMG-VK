package com.lmg.vk.engine.automix.observation

import com.lmg.vk.engine.automix.TransitionStyleCatalog
import com.lmg.vk.engine.automix.nativecore.NativeObservationBridge
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test

/** CI must execute this class with the complete production host JNI library. */
class NativeMetadataProbeIntegrationTest {
    companion object {
        @BeforeClass @JvmStatic fun requireNative() {
            assumeTrue("Pass -PautomixHostLibraryPath for real JNI tests", System.getProperty("automix.requireJni") == "true")
            NativeObservationBridge.probePair(DoubleArray(10), 0, 0)
        }
    }
    private fun calculator(tamper: Boolean = false) = AppleObservationCalculator { path ->
        check(path == TransitionStyleCatalog.ASSET_PATH)
        val bytes = File(requireNotNull(System.getProperty("automix.catalogPath"))).readBytes()
        if (tamper) bytes[0] = 0
        bytes.inputStream()
    }
    private fun pair() = ObservationPair(
        ObservationTrack("out", 0, ObservationSource("vk-out", "asset://out"), 180000),
        ObservationTrack("in", 1, ObservationSource("vk-in", "asset://in"), 200000), 0, false,
    )
    private fun response(id: String, duration: Int, bpm: Int, beginning: Int, ending: Int) =
        """{"data":[{"type":"songs","id":"$id","attributes":{"durationInMillis":$duration,
        "supportsSmartTransitions":true},"relationships":{"audio-analysis":{"data":[{"type":"audio-analysis","id":"analysis-$id"}]}}}],
        "included":[{"type":"audio-analysis","id":"analysis-$id","attributes":{"bpm":{"main":$bpm,"beginning":$beginning,"ending":$ending}}}]}""".encodeToByteArray()

    @Test fun typedNativeTransportAndPresence() {
        NativeMetadataProbeScenarios.typedRoundTrip()
        NativeMetadataProbeScenarios.absentZeroAndFalseRemainDistinct()
    }
    @Test fun nativeBoundaries() = NativeMetadataProbeScenarios.nativeBoundariesArePreserved()
    @Test fun rejectsMalformedAbiAndSnapshots() {
        NativeMetadataProbeScenarios.invalidNativeAbiIsRejected()
        NativeMetadataProbeScenarios.bridgeSnapshotsAreStrict()
    }
    @Test fun stage1ParserAndCatalogFeedNativeCalculation() {
        val calc = calculator()
        val a = calc.decode(response("a", 180000, 120, 120, 90), "a")
        val b = calc.decode(response("b", 200000, 60, 180, 60), "b")
        val result = calc.calculate(a, b, pair())
        check(result.catalogSha256 == TransitionStyleCatalog.BUNDLED_SHA256)
        check(result.mainNormalTempoTag == 2 && result.edgeNormalTempoTag == 0)
        check(result.selectedStyleId == null && result.issues == setOf(MetadataProbeIssue.REGIONS_REQUIRED))
    }
    @Test fun missingAnalysisHasExplicitRefusalReasons() {
        val calc = calculator()
        val absent = calc.decode("""{"data":[{"type":"songs","id":"absent"}]}""".encodeToByteArray(), "absent")
        val report = calc.calculate(absent, absent, pair())
        check(MetadataProbeIssue.OUT_AUDIO_ABSENT in report.issues && MetadataProbeIssue.IN_AUDIO_ABSENT in report.issues)
        check(report.mainNormalTempoTag == null && report.selectedStyleId == null)
    }
    @Test fun alteredCatalogCannotEnterCalculation() {
        val calc = calculator(tamper = true)
        val absent = calc.decode("""{"data":[{"type":"songs","id":"absent"}]}""".encodeToByteArray(), "absent")
        var rejected = false
        try { calc.calculate(absent, absent, pair()) } catch (failure: ObservationFailure) {
            check(failure.reason == ObservationReason.CATALOG_REJECTED && failure.detailCode == "CHECKSUM_MISMATCH")
            rejected = true
        }
        check(rejected)
    }
}
