package com.lmg.vk.engine.automix

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Catalog analysis transport. The caller supplies a resolved Apple catalog ID
 * and a current developer token. No title-based matching or embedded token.
 * Execute on an IO worker; call cancel() when its owning track job is cancelled.
 * The response must pass the native strict decoder before it enters planning.
 */
class AppleAnalysisClient(
    private val openConnection: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    },
) {
    fun newCall(songId: String, storefront: String, developerToken: String): AnalysisCall {
        require(songId.isNotEmpty() && songId.length <= 32 && songId.all { it in '0'..'9' })
        require(storefront.length == 2 && storefront.all { it in 'a'..'z' })
        require(developerToken.isNotBlank() && developerToken.length <= 16384 &&
            developerToken.all { it.code in 33..126 })
        val url = URL("https://amp-api.music.apple.com/v1/catalog/$storefront/songs/$songId" +
            "?extend=supportsSmartTransitions&include=audio-analysis,flexml-analysis" +
            "&extend%5Baudio-analysis%5D=fades,loudnessCurve")
        return AnalysisCall(songId, url, developerToken, openConnection)
    }

    class AnalysisCall internal constructor(
        val songId: String,
        val url: URL,
        private val token: String,
        private val openConnection: (URL) -> HttpURLConnection,
    ) {
        private val executed = AtomicBoolean()
        private val cancelled = AtomicBoolean()
        private val connection = AtomicReference<HttpURLConnection?>()

        fun cancel() {
            cancelled.set(true)
            connection.getAndSet(null)?.disconnect()
        }
        private fun ensureActive() {
            if (cancelled.get() || Thread.currentThread().isInterrupted) throw IOException("Analysis request cancelled")
        }

        /** Single-use call. HTTP failures are not treated as missing analysis.
         * Redirects are rejected so the bearer header stays at the catalog host.
         * Body size is bounded before decoding, including chunked responses.
         */
        fun execute(): String {
            check(executed.compareAndSet(false, true)) { "Analysis call already executed" }
            ensureActive()
            val conn = openConnection(url)
            connection.set(conn)
            try {
                ensureActive()
                conn.requestMethod = "GET"
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("Origin", "https://music.apple.com")
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("Accept-Encoding", "identity")
                val code = conn.responseCode
                ensureActive()
                if (code != HttpURLConnection.HTTP_OK) throw IOException("Analysis HTTP $code")
                val declared = conn.contentLengthLong
                if (declared > MAX_BYTES) throw IOException("Analysis response exceeds size limit")
                val bytes = ByteArrayOutputStream()
                conn.inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (bytes.size() + count > MAX_BYTES) throw IOException("Analysis response exceeds size limit")
                        bytes.write(buffer, 0, count)
                    }
                }
                ensureActive()
                if (bytes.size() == 0) throw IOException("Empty analysis response")
                // Reject invalid UTF-8 rather than silently modifying JSON strings.
                return Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes.toByteArray())).toString()
            } finally {
                connection.compareAndSet(conn, null)
                conn.disconnect()
            }
        }
        private companion object { const val MAX_BYTES = 4 * 1024 * 1024 }
    }
}
