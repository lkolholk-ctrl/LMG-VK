package com.lmg.vk.engine.automix

import com.lmg.vk.engine.automix.nativecore.NativeObservationBridge
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.CharacterCodingException
import java.security.MessageDigest
import java.util.Collections
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Immutable, verified input to future native planning. Kotlin exposes metadata,
 * not a second implementation of effect parameters, ramps, or style math.
 */
class TransitionStyleCatalog private constructor(
    val sha256: String,
    val styles: List<StyleDescriptor>,
    private val source: ByteArray,
) {
    fun style(id: Int): StyleDescriptor? = styles.firstOrNull { it.id == id }
    /** A copy: callers cannot invalidate the verified catalog stored by this object. */
    fun nativeSourceBytes(): ByteArray = source.copyOf()

    @Serializable
    data class StyleDescriptor(
        val id: Int, val name: String, val instructionCount: Int, val automationCount: Int,
        val duration: Int? = null, val offset: StyleTime? = null,
    )
    @Serializable
    data class StyleTime(val relative: Double, val offsetInSeconds: Double? = null)
    sealed interface LoadResult {
        data class Loaded(val catalog: TransitionStyleCatalog) : LoadResult
        data class Rejected(val reason: Reason) : LoadResult
    }
    enum class Reason {
        RESOURCE_UNAVAILABLE, INPUT_TOO_LARGE, CHECKSUM_MISMATCH, INVALID_CATALOG,
        STRUCTURE_MISMATCH, NATIVE_UNAVAILABLE, NATIVE_FAILURE, BRIDGE_CONTRACT_MISMATCH,
    }
    @Serializable
    private data class Snapshot(
        val schemaVersion: Int, val styles: List<StyleDescriptor>,
        val instructionCount: Int, val automationCount: Int,
    )

    companion object {
        const val ASSET_PATH = "automix/TransitionStyles.json"
        // Provenance: research/.../10_final_closure/TRANSITION_STYLES_IMPLEMENTATION_SPEC.md.
        const val BUNDLED_SHA256 = "fe3d0a36625ccb043c519bcc5119c98190893a9911cfa58412ad60cbab04d120"
        const val MAX_INPUT_BYTES = 1024 * 1024
        private val ids = setOf(0, 1, 2, 3, 4, 6, 7, 8, 9, 10, 11, 12, 33, 44)
        private val json = Json { ignoreUnknownKeys = false }

        /** Use loadBundled(context.assets::open) off the playback/audio thread. Owns/closes the stream. */
        fun loadBundled(openAsset: (String) -> InputStream): LoadResult {
            val bytes = try {
                openAsset(ASSET_PATH).use { input ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) {
                            val one = input.read()
                            if (one < 0) break
                            if (out.size() == MAX_INPUT_BYTES) return LoadResult.Rejected(Reason.INPUT_TOO_LARGE)
                            out.write(one)
                        } else {
                            if (count > MAX_INPUT_BYTES - out.size()) return LoadResult.Rejected(Reason.INPUT_TOO_LARGE)
                            out.write(buffer, 0, count)
                        }
                    }
                    out.toByteArray()
                }
            } catch (_: IOException) {
                return LoadResult.Rejected(Reason.RESOURCE_UNAVAILABLE)
            } catch (_: SecurityException) {
                return LoadResult.Rejected(Reason.RESOURCE_UNAVAILABLE)
            }
            return load(bytes)
        }

        fun load(input: ByteArray): LoadResult = verify(input, BUNDLED_SHA256) {
            NativeObservationBridge.describeStyles(it)
        }

        internal fun verify(
            input: ByteArray, expectedSha256: String, describe: (ByteArray) -> ByteArray,
        ): LoadResult {
            if (input.size > MAX_INPUT_BYTES) return LoadResult.Rejected(Reason.INPUT_TOO_LARGE)
            val source = input.copyOf()
            val hash = MessageDigest.getInstance("SHA-256").digest(source)
                .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
            if (hash != expectedSha256) return LoadResult.Rejected(Reason.CHECKSUM_MISMATCH)
            val native = try {
                describe(source)
            } catch (_: IllegalArgumentException) {
                return LoadResult.Rejected(Reason.INVALID_CATALOG)
            } catch (_: LinkageError) {
                return LoadResult.Rejected(Reason.NATIVE_UNAVAILABLE)
            } catch (_: SecurityException) {
                return LoadResult.Rejected(Reason.NATIVE_UNAVAILABLE)
            } catch (_: IllegalStateException) {
                return LoadResult.Rejected(Reason.NATIVE_FAILURE)
            }
            if (native.size > MAX_INPUT_BYTES) return LoadResult.Rejected(Reason.BRIDGE_CONTRACT_MISMATCH)
            val snapshot = try {
                json.decodeFromString<Snapshot>(native.decodeToString(throwOnInvalidSequence = true))
            } catch (_: SerializationException) {
                return LoadResult.Rejected(Reason.BRIDGE_CONTRACT_MISMATCH)
            } catch (_: CharacterCodingException) {
                return LoadResult.Rejected(Reason.BRIDGE_CONTRACT_MISMATCH)
            }
            if (snapshot.schemaVersion != 1) return LoadResult.Rejected(Reason.BRIDGE_CONTRACT_MISMATCH)
            if (snapshot.styles.size != 14 || snapshot.styles.map { it.id }.toSet() != ids ||
                snapshot.instructionCount != 55 || snapshot.automationCount != 74 ||
                snapshot.styles.any { it.name.isBlank() || it.instructionCount < 0 || it.automationCount < 0 } ||
                snapshot.styles.sumOf { it.instructionCount.toLong() } != 55L ||
                snapshot.styles.sumOf { it.automationCount.toLong() } != 74L
            ) return LoadResult.Rejected(Reason.STRUCTURE_MISMATCH)
            return LoadResult.Loaded(TransitionStyleCatalog(hash, Collections.unmodifiableList(snapshot.styles.toList()), source))
        }
    }
}
