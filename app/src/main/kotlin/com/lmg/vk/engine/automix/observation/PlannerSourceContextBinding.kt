package com.lmg.vk.engine.automix.observation

import com.lmg.vk.engine.automix.TransitionStyleCatalog
import com.lmg.vk.engine.automix.nativecore.NativeObservationBridge

/** Called only inside the existing pipeline's serialized worker / epoch calculation. */
internal object PlannerSourceContextBinding {
    fun calculate(outgoing: ByteArray, outgoingId: String, incoming: ByteArray, incomingId: String,
                  pair: ObservationPair, catalog: TransitionStyleCatalog,
                  context: MusicKitSourceContext): PlannerSelectionReport {
        if (catalog.sha256 != TransitionStyleCatalog.BUNDLED_SHA256)
            throw ObservationFailure(ObservationReason.CATALOG_REJECTED, "SOURCE_CATALOG_MISMATCH")
        val request = try {
            PlannerSourceContextWire.request(pair.selectionGeneration, pair.selectionRevision, context,
                pair.outgoing.durationMs, pair.incoming.durationMs)
        } catch (_: IllegalArgumentException) {
            throw ObservationFailure(ObservationReason.ANALYSIS_REJECTED, "SOURCE_CONTEXT_INVALID")
        }
        val response = try {
            NativeObservationBridge.compileMusicKitScheduleV1(outgoing, outgoingId.encodeToByteArray(),
                incoming, incomingId.encodeToByteArray(), catalog.nativeSourceBytes(), request)
        } catch (_: LinkageError) {
            throw ObservationFailure(ObservationReason.NATIVE_UNAVAILABLE)
        } catch (_: SecurityException) {
            throw ObservationFailure(ObservationReason.NATIVE_UNAVAILABLE)
        } catch (_: IllegalArgumentException) {
            throw ObservationFailure(ObservationReason.ANALYSIS_REJECTED, "SOURCE_INPUT_INVALID")
        } catch (_: IllegalStateException) {
            throw ObservationFailure(ObservationReason.NATIVE_FAILURE)
        }
        return PlannerScheduleWire.decode(response, request, catalog.styles.map { it.id.toLong() }.toSet())
    }
}
