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

/** Server gate: production raw parser, real library, existing producers/selector and epoch ownership. */
class NativePlannerSelectionIntegrationTest {
    companion object {
        @BeforeClass @JvmStatic fun requireNative() {
            assumeTrue("Pass -PautomixHostLibraryPath", System.getProperty("automix.requireJni") == "true")
            val q = PlannerSelectionWire.request(0, 0, null, null, null)
            val r = NativeObservationBridge.selectResolvedPairV2(byteArrayOf(), byteArrayOf(), byteArrayOf(), byteArrayOf(), q)
            check(PlannerSelectionWire.decode(r, q).status == PlannerSelectionStatus.NEEDS_SOURCE_BINDINGS)
        }
    }
    private fun response(id: String, bars: Int = 60, bpm: Double = 120.0): ByteArray {
        val times = (0..bars * 4).joinToString(",") { (it * (60.0 / bpm)).toString() }
        val scores = (0..bars * 4).joinToString(",") { when { it % 16 == 0 -> "800"; it % 8 == 0 -> "600"; it % 4 == 0 -> "400"; else -> "200" } }
        return """{"data":[{"type":"songs","id":"$id","attributes":{"durationInMillis":120000,"supportsSmartTransitions":true},
          "relationships":{"audio-analysis":{"data":[{"type":"audio-analysis","id":"aa"}]},
          "flexml-analysis":{"data":[{"type":"flexml-analysis","id":"ff"}]}}}],"included":[
          {"type":"audio-analysis","id":"aa","attributes":{"bpm":{"main":7},"key":{"main":{"tonic":"C","mode":"minor"}},"melodicness":{"main":0.8},"vocalActivity":[]}},
          {"type":"flexml-analysis","id":"ff","attributes":{"videoEvents":{"timeInSeconds":[$times],"score":[$scores]}}}]}""".encodeToByteArray()
    }
    private fun calculator(tamper: Boolean = false) = AppleObservationCalculator { path ->
        check(path == TransitionStyleCatalog.ASSET_PATH)
        File(requireNotNull(System.getProperty("automix.catalogPath"))).readBytes().also { if (tamper) it[0] = 0 }.inputStream()
    }
    private fun pair(scope: ResolvedPlannerScope? = null) = ObservationPair(
        ObservationTrack("out", 0, ObservationSource("same", "memory:a"), 120000),
        ObservationTrack("in", 1, ObservationSource("same", "memory:b"), 120000), 0, false,
        selectionScope = scope, selectionGeneration = 71, selectionRevision = if (scope == null) 0 else 1)
    private fun direct(q: LongArray, a: ByteArray = response("a"), b: ByteArray = response("b"), aId: String = "a") =
        PlannerSelectionWire.decode(NativeObservationBridge.selectResolvedPairV2(a, aId.encodeToByteArray(),
            b, "b".encodeToByteArray(), q), q)
    private fun request(scope: ResolvedPlannerScope? = ObservationBindingScenarios.scope(), duration: Long? = 120000) =
        PlannerSelectionWire.request(71, if (scope == null) 0 else 1, scope, 120000, duration)
    private fun rejects(block: () -> Unit) { var bad = false; try { block() } catch (_: IllegalArgumentException) { bad = true }; check(bad) }

    @Test fun defaultModeRequiresCatalogAndContext() {
        val calc = calculator(); val r = calc.calculate(calc.decode(response("a"), "a"), calc.decode(response("b"), "b"), pair())
        val selection = requireNotNull(r.selection)
        check(selection.status == PlannerSelectionStatus.NEEDS_SOURCE_BINDINGS && selection.missingSourceBindings == 6)
        check(r.selectedStyleId == null && !r.canExecute)
    }
    @Test fun knownDefaultProfileIsOrderedAndImmutable() {
        val r = direct(request(scope = null))
        check(r.knownBeatMatchedStyleIds == listOf(8, 9, 12))
        check(r.missingSourceBindings == 6 && r.selectedStyleId == null && !r.canExecute)
        var immutable = false
        try { (r.knownBeatMatchedStyleIds as MutableList<Int>)[0] = 99 } catch (_: UnsupportedOperationException) { immutable = true }
        check(immutable)
    }
    @Test fun explicitRequestOrderIsNotReplacedByDefault() {
        val s = ObservationBindingScenarios.scope()
        val reordered = ResolvedPlannerScope.create(listOf(9, 9, 8), s.records, s.bounds, s.eligibility)
        val r = direct(request(reordered))
        check(r.knownBeatMatchedStyleIds == listOf(8, 9, 12))
        check(r.explicitResolvedScope && r.selectedStyleId == 9 && r.candidate?.styleIndex == 0)
    }
    @Test fun resolvedRawJsonSelectsStyleNine() {
        val r = direct(request()); check(r.selectedStyleId == 9 && r.candidate?.score == 15.104 && r.completeForResolvedScope && !r.canExecute)
    }
    @Test fun expandedRawJsonSelectsStyleTwelve() {
        val r = direct(request(), b = response("b", 75, 150.0)); check(r.selectedStyleId == 12 && r.candidate?.normalTempoTag == 252)
    }
    @Test fun rawIdentityIsStrict() { rejects { direct(request(), aId = "wrong") } }
    @Test fun malformedWireIsRejected() {
        for (q in listOf(longArrayOf(), request().copyOf(81), request().also { it[6] = 8 }, request().also { it[4] = 0 })) rejects { direct(q) }
    }
    @Test fun missingTimelineDurationNotSubstituted() {
        val r = direct(request(duration = null)); check(r.status == PlannerSelectionStatus.UNRESOLVED_PLAYBACK_DURATION && r.candidate == null)
    }
    @Test fun deniedScopeNeverPublishesCandidate() {
        val r = direct(request(ObservationBindingScenarios.scope(ResolvedPlannerEligibility.DENIED)))
        check(r.status == PlannerSelectionStatus.INELIGIBLE && r.candidate == null && !r.completeForResolvedScope)
    }
    @Test fun malformedJsonCannotPublishPartialWinner() { rejects { direct(request(), a = "broken".encodeToByteArray()) } }
    @Test fun ownedResponsesSurviveMutation() {
        val calc = calculator(); val bytes = response("a"); val a = calc.decode(bytes, "a"); bytes.fill(0)
        val r = calc.calculate(a, calc.decode(response("b"), "b"), pair(ObservationBindingScenarios.scope()))
        check(r.selectedStyleId == 9 && r.selection?.generation == 71L && !r.canExecute)
    }
    @Test fun scopeMustMatchVerifiedCatalogIds() {
        val s = ObservationBindingScenarios.scope()
        val bad = ResolvedPlannerScope.create(listOf(999), listOf(ResolvedPlannerStyle(999, 8)), s.bounds, s.eligibility)
        val calc = calculator(); var failed = false
        try { calc.calculate(calc.decode(response("a"), "a"), calc.decode(response("b"), "b"), pair(bad)) }
        catch (e: ObservationFailure) { check(e.reason == ObservationReason.CATALOG_REJECTED); failed = true }
        check(failed)
    }
    @Test fun canonicalChecksumStillRequired() {
        val calc = calculator(true); var failed = false
        try { calc.calculate(calc.decode(response("a"), "a"), calc.decode(response("b"), "b"), pair(ObservationBindingScenarios.scope())) }
        catch (e: ObservationFailure) { check(e.reason == ObservationReason.CATALOG_REJECTED); failed = true }
        check(failed)
    }
    @Test fun realPipelineRebindsWithoutRefetch() = runBlocking {
        val calc = calculator(); val count = AtomicInteger()
        val pipeline = ObservationPipeline(this, { b, id -> count.incrementAndGet(); calc.decode(b, id) }, calc::calculate)
        try {
            pipeline.update(pair().copy(selectionGeneration = 0)); val t = pipeline.state.value.requests
            check(pipeline.submitOwned(t[0], "a", response("a")) == ObservationSubmission.ACCEPTED)
            check(pipeline.submitOwned(t[1], "b", response("b")) == ObservationSubmission.ACCEPTED)
            withTimeout(10000) { pipeline.state.first { it.phase == ObservationPhase.OBSERVED } }
            check(pipeline.state.value.result?.selection?.status == PlannerSelectionStatus.NEEDS_SOURCE_BINDINGS)
            check(pipeline.bindResolvedScope(t[0], ObservationBindingScenarios.scope()) == ObservationScopeSubmission.ACCEPTED)
            val selected = withTimeout(10000) { pipeline.state.first { it.phase == ObservationPhase.OBSERVED } }
            check(selected.result?.selectedStyleId == 9 && count.get() == 2 && selected.generation == t[0].generation)
            check(selected.result?.selection?.bindingRevision == 1L && selected.requests.isEmpty())
        } finally { pipeline.close() }
    }
}
