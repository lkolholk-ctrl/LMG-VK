package com.lmg.vk.engine.automix.observation

import com.lmg.vk.engine.automix.TransitionStyleCatalog
import com.lmg.vk.engine.automix.nativecore.NativeObservationBridge

/** Serialized-worker stateless call. Default mappings are deliberately NOT fabricated. */
internal object PlannerSelectionBinding {
    fun calculate(outgoing: ByteArray, outgoingId: String, incoming: ByteArray, incomingId: String,
                  pair: ObservationPair, catalog: TransitionStyleCatalog): PlannerSelectionReport {
        val bound = pair.selectionScope
        if (bound is MusicKitSourceContext) {
            return PlannerSourceContextBinding.calculate(outgoing, outgoingId, incoming, incomingId, pair, catalog, bound)
        }
        val scope = bound as? ResolvedPlannerScope
        // The existing loader owns canonical bytes+SHA and full native catalog validation.
        // The explicit path preserves caller records; the source-context path binds catalog fields natively.
        if (catalog.sha256 != TransitionStyleCatalog.BUNDLED_SHA256 ||
            scope?.records?.any { catalog.style(it.id.toInt()) == null } == true ||
            scope?.requestedIds?.any { catalog.style(it.toInt()) == null } == true
        ) throw ObservationFailure(ObservationReason.CATALOG_REJECTED, "SCOPE_CATALOG_MISMATCH")
        val request = try {
            PlannerSelectionWire.request(pair.selectionGeneration, pair.selectionRevision, scope,
                pair.outgoing.durationMs, pair.incoming.durationMs)
        } catch (_: IllegalArgumentException) {
            throw ObservationFailure(ObservationReason.ANALYSIS_REJECTED, "SELECTION_CONTEXT_INVALID")
        }
        val result = try {
            NativeObservationBridge.selectResolvedPairV2(outgoing, outgoingId.toByteArray(Charsets.UTF_8),
                incoming, incomingId.toByteArray(Charsets.UTF_8), request)
        } catch (_: LinkageError) {
            throw ObservationFailure(ObservationReason.NATIVE_UNAVAILABLE)
        } catch (_: SecurityException) {
            throw ObservationFailure(ObservationReason.NATIVE_UNAVAILABLE)
        } catch (_: IllegalArgumentException) {
            throw ObservationFailure(ObservationReason.ANALYSIS_REJECTED, "SELECTION_INPUT_INVALID")
        } catch (_: IllegalStateException) {
            throw ObservationFailure(ObservationReason.NATIVE_FAILURE)
        }
        return PlannerSelectionWire.decode(result, request)
    }
}
