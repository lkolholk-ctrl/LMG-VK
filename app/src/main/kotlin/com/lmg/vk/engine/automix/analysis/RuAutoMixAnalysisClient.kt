package com.lmg.vk.engine.automix.analysis

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Only transport. Search matches and response headers do NOT grant recording identity,
 * source-policy eligibility, duration remapping, or permission to execute a transition. */
sealed class AnalysisLookup {
    class ById(val songId: String) : AnalysisLookup() {
        init { require(validId(songId)) { "Invalid catalog ID" } }
        override fun equals(other: Any?) = other is ById && other.songId == songId
        override fun hashCode() = songId.hashCode()
    }
    class ByMetadata(val title: String, val artist: String, val durationMs: Long) : AnalysisLookup() {
        init {
            require(validText(title) && validText(artist)) { "Invalid search metadata" }
            require(durationMs in 1..604_800_000L) { "Invalid search duration" }
        }
        override fun equals(other: Any?) = other is ByMetadata && other.title == title &&
            other.artist == artist && other.durationMs == durationMs
        override fun hashCode() = 31 * (31 * title.hashCode() + artist.hashCode()) + durationMs.hashCode()
    }
    final override fun toString() = "AnalysisLookup(redacted)"
    companion object {
        fun validId(value: String) = value.length in 1..32 && value.all { it in '0'..'9' }
        private fun validText(value: String): Boolean {
            if (value.isBlank() || value.length > 1024 || value.any { it.code < 32 || it.code == 127 }) return false
            return try {
                Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(value))
                true
            } catch (_: java.nio.charset.CharacterCodingException) { false }
        }
    }
}

enum class AnalysisFailure {
    NO_CREDENTIAL, NOT_FOUND, UNAUTHORIZED, RATE_LIMITED, SERVER_ERROR, HTTP_ERROR, TIMEOUT,
    REDIRECT_REJECTED, NETWORK_ERROR, INVALID_RESPONSE, TOO_LARGE, CANCELLED, BUSY,
    NATIVE_UNAVAILABLE,
}

/** Worker-owned response. It owns the original HTTP bytes; callers only receive copies. */
class RawAnalysis internal constructor(val songId: String, bytes: ByteArray) {
    private val body = bytes.copyOf()
    val byteCount: Int get() = body.size
    fun copyBytes(): ByteArray = body.copyOf()
    override fun toString() = "RawAnalysis(redacted, bytes=$byteCount)"
}
sealed class AnalysisResult {
    class Ready(val analysis: RawAnalysis) : AnalysisResult()
    data class Failed(val reason: AnalysisFailure, val httpStatus: Int? = null) : AnalysisResult()
}

/** An explicit cancellable, single-use worker operation. No request is made by construction. */
interface AnalysisHttpCall {
    fun execute(): AnalysisResult
    /** Best-effort network interruption; invoke off Main/realtime threads. */
    fun cancel()
}

class RuAutoMixAnalysisClient(
    private val apiKey: () -> String?,
    private val openConnection: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) {
    fun newCall(lookup: AnalysisLookup): AnalysisHttpCall = Call(lookup)

    private inner class Call(private val lookup: AnalysisLookup) : AnalysisHttpCall {
        private val started = AtomicBoolean()
        private val cancelled = AtomicBoolean()
        private val active = AtomicReference<HttpURLConnection?>()
        override fun cancel() {
            cancelled.set(true)
            try { active.getAndSet(null)?.disconnect() } catch (_: Exception) { }
        }
        private fun checkActive() {
            if (cancelled.get() || Thread.currentThread().isInterrupted) throw IOException("Cancelled")
        }
        override fun execute(): AnalysisResult {
            check(started.compareAndSet(false, true)) { "Analysis call already used" }
            var conn: HttpURLConnection? = null
            try {
                checkActive()
                val key = apiKey()
                if (key == null || key.isBlank() || key.length > 4096 || key.any { it.code !in 33..126 })
                    return AnalysisResult.Failed(AnalysisFailure.NO_CREDENTIAL)
                conn = openConnection(requestUrl(lookup))
                active.set(conn)
                checkActive()
                conn.requestMethod = "GET"
                conn.instanceFollowRedirects = false
                conn.useCaches = false
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                conn.setRequestProperty("X-API-Key", key)
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("Accept-Encoding", "identity")
                val code = conn.responseCode
                checkActive()
                if (code != 200) return AnalysisResult.Failed(when (code) {
                    in 300..399 -> AnalysisFailure.REDIRECT_REJECTED
                    404 -> AnalysisFailure.NOT_FOUND
                    401, 403 -> AnalysisFailure.UNAUTHORIZED
                    429 -> AnalysisFailure.RATE_LIMITED
                    in 500..599 -> AnalysisFailure.SERVER_ERROR
                    else -> AnalysisFailure.HTTP_ERROR
                }, code)
                val id = conn.getHeaderField("X-Track-Id")?.trim()
                if (id == null || !AnalysisLookup.validId(id) || (lookup is AnalysisLookup.ById && id != lookup.songId))
                    return AnalysisResult.Failed(AnalysisFailure.INVALID_RESPONSE)
                val encoding = conn.getHeaderField("Content-Encoding")?.trim()
                if (!encoding.isNullOrEmpty() && !encoding.equals("identity", ignoreCase = true))
                    return AnalysisResult.Failed(AnalysisFailure.INVALID_RESPONSE)
                val type = conn.contentType?.substringBefore(';')?.trim()
                if (type != null && !type.equals("application/json", ignoreCase = true))
                    return AnalysisResult.Failed(AnalysisFailure.INVALID_RESPONSE)
                val length = conn.contentLengthLong
                if (length > MAX_BYTES) return AnalysisResult.Failed(AnalysisFailure.TOO_LARGE)
                val bytes = ByteArrayOutputStream(if (length in 1..MAX_BYTES.toLong()) length.toInt() else 8192)
                conn.inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        checkActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        if (n == 0) return AnalysisResult.Failed(AnalysisFailure.INVALID_RESPONSE)
                        if (n > MAX_BYTES - bytes.size()) return AnalysisResult.Failed(AnalysisFailure.TOO_LARGE)
                        bytes.write(buffer, 0, n)
                    }
                }
                checkActive()
                val body = bytes.toByteArray()
                if (body.isEmpty() || (length >= 0 && length != body.size.toLong()))
                    return AnalysisResult.Failed(AnalysisFailure.INVALID_RESPONSE)
                try {
                    Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(body))
                } catch (_: java.nio.charset.CharacterCodingException) {
                    return AnalysisResult.Failed(AnalysisFailure.INVALID_RESPONSE)
                }
                // The native validator in the store checks the raw {data:[...]} schema
                // and the JSON ID. A valid HTTP header is NOT that validation.
                return AnalysisResult.Ready(RawAnalysis(id, body))
            } catch (_: Exception) {
                return AnalysisResult.Failed(if (cancelled.get() || Thread.currentThread().isInterrupted)
                    AnalysisFailure.CANCELLED else AnalysisFailure.NETWORK_ERROR)
            } finally {
                if (conn != null) {
                    active.compareAndSet(conn, null)
                    try { conn.disconnect() } catch (_: Exception) { }
                }
            }
        }
    }
    companion object {
        const val MAX_BYTES = 4 * 1024 * 1024
        private const val ENDPOINT = "https://ru-lyrics.gsgit.org/v2/automix/analysis"
        internal fun requestUrl(lookup: AnalysisLookup): URL {
            fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
            val query = when (lookup) {
                is AnalysisLookup.ById -> "id=${encode(lookup.songId)}"
                is AnalysisLookup.ByMetadata -> {
                    val ms = lookup.durationMs
                    val seconds = "${ms / 1000}.${(ms % 1000).toString().padStart(3, '0')}"
                    "title=${encode(lookup.title)}&artist=${encode(lookup.artist)}&duration=$seconds"
                }
            }
            return java.net.URI("$ENDPOINT?$query&raw=1").toURL()
        }
    }
}
