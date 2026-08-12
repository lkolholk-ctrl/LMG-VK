package com.lmg.vk.ui.mix

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.lmg.vk.R
import com.lmg.vk.debug.DebugLog
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Persistent, app-owned storage for the remote Lottie JSON returned by VK Mix.
 *
 * Packaged assets/raw resources are immutable at runtime. Unlike Lottie's
 * evictable network cache, filesDir survives process restarts and app updates.
 * There is intentionally no cleanup policy: captured server animations remain
 * available until the user clears app data or uninstalls the application.
 */
object VkMixLottieStore {
    private const val DIRECTORY = "vk_mix_lottie"
    private const val DOWNLOAD_DIRECTORY = "LMG VK/VK Mix Lottie"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val MAX_BYTES = 8L * 1024L * 1024L

    /**
     * The five mood ids are stable values from audio.getStreamMixSettings.
     * Their original, self-contained VK compositions are bundled so the
     * pictured mood row does not depend on the current CDN URL being alive.
     * Unknown future option ids intentionally keep using the server URL.
     */
    fun bundledResource(optionId: String): Int? = when (optionId.trim().lowercase()) {
        "active" -> R.raw.lmg_mix_mood_active
        "calm" -> R.raw.lmg_mix_mood_calm
        "happy" -> R.raw.lmg_mix_mood_happy
        "love" -> R.raw.lmg_mix_mood_love
        "sad" -> R.raw.lmg_mix_mood_sad
        else -> null
    }

    fun getOrDownload(context: Context, optionId: String, url: String): File? = runCatching {
        require(url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true))
        val directory = File(context.filesDir, DIRECTORY).also { dir ->
            check(dir.isDirectory || dir.mkdirs()) { "Cannot create ${dir.absolutePath}" }
        }
        val key = stableKey(optionId, url)
        val target = File(directory, "$key.lottie")
        if (target.isFile && target.length() > 0L) {
            DebugLog.add("VK MIX Lottie local: option=$optionId, file=${target.name}")
            return@runCatching target
        }
        if (target.exists()) quarantine(target)

        val temporary = File(directory, "$key.${System.nanoTime()}.tmp")
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.useCaches = true
            connection.setRequestProperty("Accept", "application/json, application/zip, */*")
            try {
                val responseCode = connection.responseCode
                check(responseCode in 200..299) { "HTTP $responseCode" }
                val announcedLength = connection.contentLengthLong
                check(announcedLength <= 0L || announcedLength <= MAX_BYTES) {
                    "Animation is too large: $announcedLength bytes"
                }
                var total = 0L
                connection.inputStream.use { input ->
                    FileOutputStream(temporary).buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            check(total <= MAX_BYTES) { "Animation exceeds $MAX_BYTES bytes" }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                check(total > 0L) { "Empty animation response" }
            } finally {
                connection.disconnect()
            }

            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            DebugLog.add(
                "VK MIX Lottie saved: option=$optionId, file=${target.name}, bytes=${target.length()}",
            )
            target
        } catch (error: Exception) {
            if (temporary.exists()) temporary.delete()
            throw error
        }
    }.onFailure { error ->
        DebugLog.add(
            "VK MIX Lottie save failed: option=$optionId, ${error.javaClass.simpleName}: " +
                error.message.orEmpty(),
        )
    }.getOrNull()

    /** Preserve bad bytes for diagnostics and allow a fresh download next time. */
    fun quarantine(file: File) {
        if (!file.isFile) return
        val bad = File(file.parentFile, "${file.name}.${System.currentTimeMillis()}.bad")
        if (file.renameTo(bad)) {
            DebugLog.add("VK MIX Lottie quarantined: ${bad.name}")
        }
    }

    /**
     * Keep a second, user-accessible copy for later packaging into assets/raw.
     * A valid existing export is never overwritten or removed.
     */
    fun exportValidated(context: Context, optionId: String, url: String, source: File) {
        runCatching {
            check(source.isFile && source.length() > 0L) { "Validated animation is missing" }
            val key = stableKey(optionId, url)
            val resolver = context.contentResolver
            val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$DOWNLOAD_DIRECTORY/"
            val isZip = source.inputStream().buffered().use { input ->
                input.read() == 0x50 && input.read() == 0x4B
            }
            val extension = if (isZip) "zip" else "json"
            val mimeType = if (isZip) "application/zip" else "application/json"
            val displayName = "${safeFilePart(optionId)}_${key.take(12)}.$extension"

            val existing = resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.SIZE),
                "${MediaStore.Downloads.RELATIVE_PATH}=? AND ${MediaStore.Downloads.DISPLAY_NAME}=?",
                arrayOf(relativePath, displayName),
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    null
                } else {
                    val id = cursor.getLong(0)
                    val size = cursor.getLong(1)
                    ContentUri(Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString()), size)
                }
            }
            if (existing != null && existing.size > 0L) {
                DebugLog.add("VK MIX Lottie export exists: $displayName")
                return@runCatching
            }

            val destination = existing?.uri ?: resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                },
            ) ?: error("Cannot create Download/$DOWNLOAD_DIRECTORY/$displayName")

            resolver.openOutputStream(destination, "w")?.use { output ->
                source.inputStream().buffered().use { input -> input.copyTo(output) }
            } ?: error("Cannot open Download/$DOWNLOAD_DIRECTORY/$displayName")
            resolver.update(
                destination,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null,
                null,
            )
            DebugLog.add("VK MIX Lottie exported: Download/$DOWNLOAD_DIRECTORY/$displayName")
        }.onFailure { error ->
            DebugLog.add(
                "VK MIX Lottie export failed: option=$optionId, ${error.javaClass.simpleName}: " +
                    error.message.orEmpty(),
            )
        }
    }

    private fun safeFilePart(value: String): String = value
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .trim('_')
        .ifBlank { "mix_option" }

    private data class ContentUri(val uri: Uri, val size: Long)

    private fun stableKey(optionId: String, url: String): String {
        // Signed query parameters are deliberately excluded: a renewed CDN
        // signature must resolve to the same permanent local animation.
        val stableUrl = url.substringBefore('#').substringBefore('?')
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("$optionId\n$stableUrl".toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
    }
}
