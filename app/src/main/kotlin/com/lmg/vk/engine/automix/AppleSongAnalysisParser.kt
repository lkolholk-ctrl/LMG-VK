package com.lmg.vk.engine.automix

import com.lmg.vk.engine.automix.nativecore.NativeObservationBridge
import com.lmg.vk.engine.automix.observation.AppleSongAnalysis
import java.nio.charset.CharacterCodingException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Worker/control-thread API. The MediaAPI schema is decoded exactly once, in C++.
 * Kotlin only deserializes the versioned JNI snapshot. No HTTP, player access,
 * beat inference, time conversion, fallback analysis, or DSP processing occurs here.
 */
class AppleSongAnalysisParser internal constructor(
    private val nativeDecode: (ByteArray, ByteArray) -> ByteArray,
) {
    constructor() : this({ input, id -> NativeObservationBridge.describeSong(input, id) })

    sealed interface Result {
        data class Parsed(val analysis: AppleSongAnalysis) : Result
        data class Rejected(val reason: Reason) : Result
    }
    enum class Reason {
        INPUT_TOO_LARGE, INVALID_SONG_ID, INVALID_INPUT,
        NATIVE_UNAVAILABLE, NATIVE_FAILURE, BRIDGE_CONTRACT_MISMATCH,
    }

    fun parse(input: ByteArray, requestedSongId: String): Result {
        if (input.size > MAX_INPUT_BYTES) return Result.Rejected(Reason.INPUT_TOO_LARGE)
        if (requestedSongId.isBlank() || requestedSongId.length > MAX_ID_BYTES) {
            return Result.Rejected(Reason.INVALID_SONG_ID)
        }
        val id = try {
            requestedSongId.encodeToByteArray(throwOnInvalidSequence = true)
        } catch (_: CharacterCodingException) {
            return Result.Rejected(Reason.INVALID_SONG_ID)
        }
        if (id.size > MAX_ID_BYTES) return Result.Rejected(Reason.INVALID_SONG_ID)
        val snapshot = try {
            nativeDecode(input.copyOf(), id)
        } catch (_: IllegalArgumentException) {
            return Result.Rejected(Reason.INVALID_INPUT)
        } catch (_: LinkageError) {
            // Covers both initial library-load failure and subsequent failed class initialization.
            return Result.Rejected(Reason.NATIVE_UNAVAILABLE)
        } catch (_: SecurityException) {
            return Result.Rejected(Reason.NATIVE_UNAVAILABLE)
        } catch (_: IllegalStateException) {
            return Result.Rejected(Reason.NATIVE_FAILURE)
        }
        if (snapshot.size > MAX_SNAPSHOT_BYTES) return Result.Rejected(Reason.BRIDGE_CONTRACT_MISMATCH)
        return try {
            val envelope = json.decodeFromString<Envelope>(snapshot.decodeToString(throwOnInvalidSequence = true))
            if (envelope.schemaVersion != 1 || envelope.analysis.id != requestedSongId) {
                Result.Rejected(Reason.BRIDGE_CONTRACT_MISMATCH)
            } else {
                // Missing/unresolved analysis and supportsSmartTransitions=false remain valid data.
                Result.Parsed(envelope.analysis)
            }
        } catch (_: SerializationException) {
            Result.Rejected(Reason.BRIDGE_CONTRACT_MISMATCH)
        } catch (_: CharacterCodingException) {
            Result.Rejected(Reason.BRIDGE_CONTRACT_MISMATCH)
        }
    }

    @Serializable
    private data class Envelope(val schemaVersion: Int, val analysis: AppleSongAnalysis)

    companion object {
        const val MAX_INPUT_BYTES = 4 * 1024 * 1024
        private const val MAX_ID_BYTES = 4096
        private const val MAX_SNAPSHOT_BYTES = 8 * 1024 * 1024
        private val json = Json { ignoreUnknownKeys = false }
    }
}
