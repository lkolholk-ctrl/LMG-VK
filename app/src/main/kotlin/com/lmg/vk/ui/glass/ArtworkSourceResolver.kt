package com.lmg.vk.ui.glass

import android.net.Uri

/** Источник изображения, общий для отрисовки и извлечения палитры. */
data class ResolvedArtworkSource(
    val model: Any,
    val cacheKey: String,
    val kind: Kind,
) {
    enum class Kind { COVER_URL, URI }

    val coverUrl: String?
        get() = (model as? String)?.takeIf { kind == Kind.COVER_URL }
}

/**
 * Разделяет реальную обложку, stock-placeholder VK и отсутствие изображения.
 * VK-generated `thumb` приходит с CDN и является обычной COVER_URL: он здесь
 * никогда не отбрасывается.
 */
object ArtworkSourceResolver {
    private const val VK_STOCK_PLACEHOLDER_PATH = "/images/audio_row_placeholder.png"

    fun isVkStockPlaceholder(value: String?): Boolean {
        val normalized = value
            ?.trim()
            ?.substringBefore('#')
            ?.substringBefore('?')
            ?: return false
        return normalized.endsWith(VK_STOCK_PLACEHOLDER_PATH, ignoreCase = true)
    }

    fun realCoverOrNull(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() && !isVkStockPlaceholder(it) }

    fun resolve(uri: Uri?, coverUrl: String?): ResolvedArtworkSource? {
        realCoverOrNull(coverUrl)?.let { resolvedCover ->
            return ResolvedArtworkSource(
                model = resolvedCover,
                cacheKey = "cover:$resolvedCover",
                kind = ResolvedArtworkSource.Kind.COVER_URL,
            )
        }

        val resolvedUri = uri?.takeUnless {
            it == Uri.EMPTY || it.toString().isBlank() || isVkStockPlaceholder(it.toString())
        }
            ?: return null
        return ResolvedArtworkSource(
            model = resolvedUri,
            cacheKey = "uri:$resolvedUri",
            kind = ResolvedArtworkSource.Kind.URI,
        )
    }
}
