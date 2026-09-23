package com.lmg.vk.engine.automix.observation

import java.security.MessageDigest

/** Owned response retained for native preparation. Never published in StateFlow.
 * Copies/hashing/parsing happen in the pipeline's serialized worker section.
 */
internal class DecodedObservationSong private constructor(
    val analysis: AppleSongAnalysis,
    private val sourceBytes: ByteArray,
    private val requestedSongId: String,
    val responseSha256: String,
) {
    fun prepareWith(other: DecodedObservationSong, pair: ObservationPair): PlannerPreparationReport =
        PlannerPreparation.calculate(sourceBytes, requestedSongId, other.sourceBytes, other.requestedSongId,
            pair.outgoing.durationMs, pair.incoming.durationMs, responseSha256, other.responseSha256)

    override fun toString(): String = "DecodedObservationSong(redacted)"

    companion object {
        fun capture(input: ByteArray, requestedSongId: String,
                    parse: (ByteArray, String) -> AppleSongAnalysis): DecodedObservationSong {
            if (input.size > ObservationPipeline.MAX_RESPONSE_BYTES)
                throw ObservationFailure(ObservationReason.ANALYSIS_REJECTED, "INPUT_TOO_LARGE")
            // Capture BEFORE parsing: the typed result, hash and later native call
            // must all refer to the same immutable response. Do not reserialize DTOs.
            val owned = input.copyOf()
            val analysis = parse(owned, requestedSongId)
            val hash = MessageDigest.getInstance("SHA-256").digest(owned)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            return DecodedObservationSong(analysis, owned, requestedSongId, hash)
        }
    }
}
