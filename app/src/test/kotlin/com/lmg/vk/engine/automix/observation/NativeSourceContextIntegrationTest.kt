package com.lmg.vk.engine.automix.observation

import com.lmg.vk.engine.automix.TransitionStyleCatalog
import com.lmg.vk.engine.automix.nativecore.NativeObservationBridge
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test

/** Full server gate: actual canonical bytes, raw JSON parser, native catalog/context and JNI. */
class NativeSourceContextIntegrationTest {
    companion object {
        @BeforeClass @JvmStatic fun requireNative() {
            assumeTrue("Pass -PautomixHostLibraryPath", System.getProperty("automix.requireJni") == "true")
            val context = MusicKitSourceContext.create(MusicKitOutgoingCriteria.LateInSong, MusicKitIncomingCriteria.InSong, 3)
            val q = PlannerSourceContextWire.request(0, 1, context, null, null)
            val r = NativeObservationBridge.selectMusicKitSourcePairV1(byteArrayOf(), byteArrayOf(), byteArrayOf(), byteArrayOf(), byteArrayOf(), q)
            check(PlannerSourceContextWire.decode(r, q, emptySet()).sourceContextResolution == PlannerSourceContextResolution.ELIGIBILITY_UNRESOLVED)
        }
    }
    private fun response(id: String, duration: Int = 120000, bars: Int = 60, bpm: Double = 120.0): ByteArray {
        val times = (0..bars * 4).joinToString(",") { (it * (60.0 / bpm)).toString() }
        val scores = (0..bars * 4).joinToString(",") { when { it % 16 == 0 -> "800"; it % 8 == 0 -> "600"; it % 4 == 0 -> "400"; else -> "200" } }
        return """{"data":[{"type":"songs","id":"$id","attributes":{"durationInMillis":$duration,"supportsSmartTransitions":true},
          "relationships":{"audio-analysis":{"data":[{"type":"audio-analysis","id":"aa"}]},
          "flexml-analysis":{"data":[{"type":"flexml-analysis","id":"ff"}]}}}],"included":[
          {"type":"audio-analysis","id":"aa","attributes":{"bpm":{"main":7},"key":{"main":{"tonic":"C","mode":"minor"}},"melodicness":{"main":0.8},"vocalActivity":[]}},
          {"type":"flexml-analysis","id":"ff","attributes":{"videoEvents":{"timeInSeconds":[$times],"score":[$scores]}}}]}""".encodeToByteArray()
    }
    private fun catalogBytes() = File(requireNotNull(System.getProperty("automix.catalogPath"))).readBytes()
    private fun catalog() = (TransitionStyleCatalog.loadBundled { catalogBytes().inputStream() } as TransitionStyleCatalog.LoadResult.Loaded).catalog
    private fun context() = PlannerSourceTransportScenarios.context()
    private fun pair(bound: PlannerObservationScope? = context(), duration: Long = 120000) = ObservationPair(
        ObservationTrack("a", 0, ObservationSource("same", "memory:a"), duration),
        ObservationTrack("b", 1, ObservationSource("same", "memory:b"), duration), 0, false,
        selectionScope = bound, selectionGeneration = 71, selectionRevision = if (bound == null) 0 else 1)
    private fun calculator(tamper: Boolean = false) = AppleObservationCalculator { path ->
        check(path == TransitionStyleCatalog.ASSET_PATH)
        catalogBytes().also { if (tamper) it[0] = 0 }.inputStream()
    }
    private fun request(c: MusicKitSourceContext = context(), duration: Long? = 120000) =
        PlannerSourceContextWire.request(71, 1, c, duration, duration)
    private fun raw(q: LongArray, a: ByteArray = response("a"), b: ByteArray = response("b"),
                    aId: String = "a", bytes: ByteArray = catalogBytes()) =
        NativeObservationBridge.selectMusicKitSourcePairV1(a, aId.encodeToByteArray(), b, "b".encodeToByteArray(), bytes, q)
    private fun direct(q: LongArray, a: ByteArray = response("a"), b: ByteArray = response("b")) =
        PlannerSourceContextWire.decode(raw(q, a, b), q, catalog().styles.map { it.id.toLong() }.toSet())
    private fun rejects(block: () -> Unit) { var rejected = false; try { block() } catch (_: IllegalArgumentException) { rejected = true }; check(rejected) }

    @Test fun unboundDefaultRetainsOnlyContextBlocker() {
        val calc = calculator(); val r = calc.calculate(calc.decode(response("a"), "a"), calc.decode(response("b"), "b"), pair(null))
        val selection = requireNotNull(r.selection)
        check(selection.missingSourceBindings == 4 && selection.status == PlannerSelectionStatus.NEEDS_SOURCE_BINDINGS && !selection.canExecute)
    }
    @Test fun rawSourceCriteriaSelectStyleNine() {
        val calc = calculator(); val r = calc.calculate(calc.decode(response("a"), "a"), calc.decode(response("b"), "b"), pair())
        val selection = requireNotNull(r.selection)
        check(selection.selectedStyleId == 9 && selection.candidate?.score == 15.104 && !selection.canExecute)
        check(selection.scopeKind == "SOURCE_MUSICKIT_SUBSET" && selection.sourceContextResolution == PlannerSourceContextResolution.RESOLVED)
        check(!selection.explicitResolvedScope && selection.completeForResolvedScope)
    }
    @Test fun rawSourceCriteriaSelectStyleTwelve() {
        val q = request(duration = 96000)
        val r = direct(q, response("a", 96000, 48, 120.0), response("b", 96000, 60, 150.0))
        val candidate = requireNotNull(r.candidate)
        check(r.selectedStyleId == 12 && candidate.score == 10.064 && candidate.normalTempoTag == 252 && !r.canExecute)
    }
    @Test fun canonicalCatalogProducesInternalCounts() {
        val q = request(); val r = raw(q)
        check(r[5] == 0L && r[12+11] == 3L && r[12+12] == 14L && r.slice(36..38) == listOf(8L, 9L, 12L))
        val records = (12+27 until 12+r[6].toInt() step 3).associate { at -> r[at] to (r[at+1] to r[at+2]) }
        check(records[8] == (1L to 8L) && records[9] == (1L to 8L) && records[12] == (1L to 16L) && records[0] == (0L to 0L))
    }
    @Test fun sourceRawIdentityIsStrict() { rejects { raw(request(), aId = "wrong") } }
    @Test fun malformedSourceContractIsRejected() {
        for (q in listOf(longArrayOf(), request().copyOf(25), request().also { it[8] = 1 }, request().also { it[4] = 0 }, request().also { it[11] = 3 })) rejects { raw(q) }
    }
    @Test fun unresolvedFactsCannotAuthorizeMusic() {
        val unknown = context().copy(outgoingSpatial = MusicKitSourceKnowledge.UNKNOWN)
        val r = direct(request(unknown))
        check(r.sourceContextResolution == PlannerSourceContextResolution.SOURCE_FACTS_UNKNOWN && r.candidate == null && !r.canExecute)
        val denied = direct(request(context().copy(upperEligibility = ResolvedPlannerEligibility.DENIED)))
        check(denied.status == PlannerSelectionStatus.INELIGIBLE && denied.candidate == null)
    }
    @Test fun spatialAndPreviousStateRemainUnsupported() {
        val a = direct(request(context().copy(incomingSpatial = MusicKitSourceKnowledge.PRESENT)))
        val b = direct(request(context().copy(outgoingPreviousState = MusicKitSourceKnowledge.PRESENT)))
        check(a.sourceContextResolution == PlannerSourceContextResolution.SPATIAL_UNSUPPORTED && b.sourceContextResolution == PlannerSourceContextResolution.PREVIOUS_STATE_UNSUPPORTED)
        check(a.candidate == null && b.candidate == null)
    }
    @Test fun referenceAndPlaybackDurationsAreNotSubstituted() {
        val mismatch = direct(request(duration = 120001))
        check(mismatch.sourceContextResolution == PlannerSourceContextResolution.DURATION_MAPPING_REQUIRED && mismatch.candidate == null)
        val missing = direct(request(duration = null))
        check(missing.sourceContextResolution == PlannerSourceContextResolution.PLAYBACK_DURATION_MISSING)
    }
    @Test fun invalidCatalogAndChecksumCannotPublishCandidate() {
        rejects { raw(request(), bytes = "broken".encodeToByteArray()) }
        val calc = calculator(true); var rejected = false
        try { calc.calculate(calc.decode(response("a"), "a"), calc.decode(response("b"), "b"), pair()) }
        catch (e: ObservationFailure) { check(e.reason == ObservationReason.CATALOG_REJECTED); rejected = true }
        check(rejected)
    }
    @Test fun ownedSourceResponsesSurviveCallerMutation() {
        val calc = calculator(); val bytes = response("a"); val a = calc.decode(bytes, "a"); bytes.fill(0)
        val r = calc.calculate(a, calc.decode(response("b"), "b"), pair())
        val selection = requireNotNull(r.selection)
        check(selection.selectedStyleId == 9 && selection.bindingRevision == 1L && !r.canExecute)
    }
    @Test fun realPipelineBindsSourceContextWithoutRefetch() = runBlocking {
        val calc = calculator(); val decodes = AtomicInteger()
        val p = ObservationPipeline(this, { b, id -> decodes.incrementAndGet(); calc.decode(b, id) }, calc::calculate)
        try { p.update(pair(null).copy(selectionGeneration = 0)); val t = p.state.value.requests
            check(p.submitOwned(t[0], "a", response("a")) == ObservationSubmission.ACCEPTED)
            check(p.submitOwned(t[1], "b", response("b")) == ObservationSubmission.ACCEPTED)
            withTimeout(10000) { p.state.first { it.phase == ObservationPhase.OBSERVED } }
            check(p.bindResolvedScope(t[1], context()) == ObservationScopeSubmission.ACCEPTED)
            val result = withTimeout(10000) { p.state.first { it.phase == ObservationPhase.OBSERVED } }
            check(result.result?.selection?.selectedStyleId == 9 && decodes.get() == 2 && result.generation == t[0].generation)
            check(result.result?.selection?.sourceContextResolution == PlannerSourceContextResolution.RESOLVED)
        } finally { p.close() }
    }
}
