package com.lmg.vk.engine.automix.observation

import com.lmg.vk.engine.automix.TransitionStyleCatalog
import com.lmg.vk.engine.automix.nativecore.NativeObservationBridge
import java.io.File
import java.security.MessageDigest
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test

/** Must execute, not skip, in the APK verifier with the full production JNI. */
class NativePlannerPreparationIntegrationTest {
    companion object {
        @BeforeClass @JvmStatic fun requireNative() {
            assumeTrue("Pass -PautomixHostLibraryPath for real JNI tests", System.getProperty("automix.requireJni") == "true")
            val absent = """{"data":[{"type":"songs","id":"a"}]}""".encodeToByteArray()
            NativeObservationBridge.preparePair(absent, "a".encodeToByteArray(), absent, "a".encodeToByteArray(), longArrayOf(0, 0), 0)
        }
    }
    private fun calculator(tamper: Boolean = false) = AppleObservationCalculator { path ->
        check(path == TransitionStyleCatalog.ASSET_PATH)
        File(requireNotNull(System.getProperty("automix.catalogPath"))).readBytes()
            .also { if (tamper) it[0] = 0 }.inputStream()
    }
    private fun pair(duration: Long? = 16000) = ObservationPair(
        ObservationTrack("out", 0, ObservationSource("same", "asset://out"), duration),
        ObservationTrack("in", 1, ObservationSource("same", "asset://in"), duration), 0, false,
    )
    private fun response(id: String = "a", count: Int = 33, duplicate: Boolean = false,
                         fractionalVocal: Boolean = false): ByteArray {
        val times = (0 until count).joinToString(",") { if (duplicate && it == 1) "0" else (it * .5).toString() }
        val scores = (0 until count).joinToString(",") { when { it % 16 == 0 -> "800"; it % 8 == 0 -> "600"; it % 4 == 0 -> "400"; else -> "200" } }
        return """{"data":[{"type":"songs","id":"$id","attributes":{"durationInMillis":16000,"supportsSmartTransitions":true},
            "relationships":{"audio-analysis":{"data":[{"type":"audio-analysis","id":"audio-$id"}]},
            "flexml-analysis":{"data":[{"type":"flexml-analysis","id":"flex-$id"}]}}}],"included":[
            {"type":"audio-analysis","id":"audio-$id","attributes":{"bpm":{"main":13},
            "key":{"main":{"tonic":"C","mode":"major"}},"loudnessCurve":{"value":[-10,-20,-30,-40],"samplingFrequency":2},
            "vocalActivity":[{"startInMilliseconds":${if (fractionalVocal) "0.5" else "500"},"endInMilliseconds":1500}]}},
            {"type":"flexml-analysis","id":"flex-$id","attributes":{"videoEvents":{"timeInSeconds":[$times],"score":[$scores]}}}]}""".encodeToByteArray()
    }
    private fun inspect(a: ByteArray, b: ByteArray = response("b"), duration: Long? = 16000): PlannerPreparationReport {
        val calc = calculator()
        return requireNotNull(calc.calculate(calc.decode(a, "a"), calc.decode(b, "b"), pair(duration)).preparation)
    }
    @Test fun completeResponsesUseNativeFlexStructure() {
        val r = inspect(response())
        check(r.outgoing.status == PlannerPreparationStatus.PREPARED && !r.canExecute && r.selectedStyleId == null)
        val s = requireNotNull(r.outgoing.structure)
        check(s.events == 33 && s.bars == 8 && s.segments == 4 && s.sections == 2 && s.stableRegions == 1)
        check(s.stablePreview.single().averageTempoBpm == 120.0) // cloud BPM is deliberately 13
        check(s.stablePreview.single().endSeconds == 16.0 && r.outgoing.tonality?.main == PreparedTonality(4, 0))
        check(r.outgoing.loudnessPoints == 4 && r.outgoing.vocalIntervals == 1)
    }
    @Test fun missingAndEmptyStayDifferent() {
        val absent = """{"data":[{"type":"songs","id":"a"}]}""".encodeToByteArray()
        check(inspect(absent).outgoing.flexEvents == null)
        val empty = inspect(response(count = 0)).outgoing
        check(empty.flexEvents == 0 && empty.structure?.events == 0 && empty.status == PlannerPreparationStatus.INSUFFICIENT_ANALYSIS)
        val linkage = """{"data":[{"type":"songs","id":"a","relationships":{"flexml-analysis":{"data":[{"type":"flexml-analysis","id":"f"}]}}}]}""".encodeToByteArray()
        check(inspect(linkage).outgoing.flexAvailability == PreparedAnalysisAvailability.LINKAGE_ONLY)
    }
    @Test fun sourceOrderAndDuplicateTimesArePreserved() {
        val r = inspect(response(duplicate = true))
        check(r.outgoing.structure?.events == 33 && r.outgoing.structure?.duplicateTimes == 1)
        check(PlannerPreparationIssue.DUPLICATE_EVENTS in r.outgoing.issues)
    }
    @Test fun limitsRejectBeforeStructureExpansion() {
        val r = inspect(response(count = 4097))
        check(r.outgoing.status == PlannerPreparationStatus.RESOURCE_LIMIT && r.outgoing.structure == null)
        check(r.incoming.status == PlannerPreparationStatus.PREPARED)
    }
    @Test fun rawIdentityAndAbiAreStrict() {
        val a = response(); val b = response("b"); val aid = "a".encodeToByteArray(); val bid = "b".encodeToByteArray()
        fun rejected(call: () -> Unit) {
            var rejected = false
            try { call() } catch (_: IllegalArgumentException) { rejected = true }
            check(rejected)
        }
        rejected { NativeObservationBridge.preparePair(a, "wrong".encodeToByteArray(), b, bid, longArrayOf(16000, 16000), 3) }
        rejected { NativeObservationBridge.preparePair(a, aid, b, bid, longArrayOf(16000), 3) }
        rejected { NativeObservationBridge.preparePair(a, aid, b, bid, longArrayOf(0, 0), 4) }
        rejected { NativeObservationBridge.preparePair(a, aid, b, bid, longArrayOf(1, 0), 0) }
        rejected { NativeObservationBridge.preparePair(a, byteArrayOf(), b, bid, longArrayOf(0, 0), 0) }
    }
    @Test fun durationAndCatalogAreNotSubstituted() {
        val unknown = inspect(response(), duration = null).outgoing
        check(unknown.resolvedDurationMs == null && unknown.structure?.boundedStableRegions == 0)
        check(unknown.structure?.stablePreview?.single()?.insideResolvedTrack == null)
        val calc = calculator(tamper = true)
        val a = calc.decode(response(), "a"); val b = calc.decode(response("b"), "b")
        var rejected = false
        try { calc.calculate(a, b, pair()) } catch (f: ObservationFailure) { check(f.reason == ObservationReason.CATALOG_REJECTED); rejected = true }
        check(rejected)
    }
    @Test fun capturedBytesSurviveCallerMutation() {
        val calc = calculator(); val bytes = response()
        val expected = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        val a = calc.decode(bytes, "a"); bytes.fill(0)
        val b = calc.decode(response("b"), "b")
        val r = requireNotNull(calc.calculate(a, b, pair()).preparation)
        check(r.outgoingResponseSha256 == expected && r.outgoing.structure?.events == 33)
        check(a.toString() == "DecodedObservationSong(redacted)" && !r.toString().contains("videoEvents"))
    }
    @Test fun failureDoesNotExposePartiallyPreparedMaps() {
        val r = inspect(response(fractionalVocal = true)).outgoing
        check(r.status == PlannerPreparationStatus.INVALID_ANALYSIS && r.tonality == null && r.vocalIntervals == null && r.structure == null)
        check(PlannerPreparationIssue.INVALID_ANALYSIS in r.issues)
    }
}
