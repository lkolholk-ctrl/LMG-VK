package com.lmg.vk.engine

import android.net.Uri

/**
 * VK-only audio identity helpers.
 *
 * VK X / VK MP3 MOD identify an audio as `owner_id_audio_id[_access_key]`,
 * obtain the real stream URL from VK and share it as
 * `https://vk.ru/audio{owner_id}_{audio_id}`.
 * An unresolved online track therefore uses [Uri.EMPTY] until
 * [StreamingDataSource] resolves a fresh VK URL through `audio.getById`.
 */
object VkAudioIdentity {
    // `access_key` is the optional third segment used by audio.getById for
    // restricted/foreign records. It is opaque but VK keys are ASCII
    // alphanumeric; underscores or punctuation would make the id ambiguous.
    private val fullIdPattern = Regex("^-?\\d+_\\d+(?:_[A-Za-z0-9]+)?$")

    fun normalizeFullId(id: String): String = id.removePrefix("vk_")

    fun isFullId(id: String): Boolean = fullIdPattern.matches(normalizeFullId(id))

    /** Stable `owner_id_audio_id` without the optional access key. */
    fun bareFullId(id: String): String? {
        val normalized = normalizeFullId(id)
        if (!fullIdPattern.matches(normalized)) return null
        val parts = normalized.split('_', limit = 3)
        return "${parts[0]}_${parts[1]}"
    }

    /**
     * Preserve a real URL returned by VK; otherwise leave the URI unresolved.
     * No third-party resolver URL is used as a placeholder.
     */
    fun playbackUri(directVkUrl: String? = null): Uri =
        directVkUrl
            ?.trim()
            ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            ?.let(Uri::parse)
            ?: Uri.EMPTY

    /** Official audio link format recovered from VK MP3 MOD. */
    fun shareUrl(id: String): String? = bareFullId(id)
        ?.let { "https://vk.ru/audio$it" }

    /** Extract the VK audio id from the internal resolving URI when needed. */
    fun trackIdFromUri(uri: Uri?): String? {
        uri ?: return null
        if (uri.scheme == StreamingDataSource.SCHEME_LIQUID) {
            return uri.getQueryParameter(StreamingDataSource.PARAM_TRACK_ID)
                ?.takeIf { isFullId(it) }
        }

        val text = uri.toString()
        val sharePrefixes = listOf(
            "https://vk.ru/audio",
            "http://vk.ru/audio",
            "https://vk.com/audio",
            "http://vk.com/audio",
        )
        sharePrefixes.firstOrNull { text.startsWith(it) }?.let { prefix ->
            return text.removePrefix(prefix)
                .substringBefore('?')
                .substringBefore('#')
                .takeIf { isFullId(it) }
        }

        return uri.lastPathSegment?.takeIf { isFullId(it) }
    }
}
