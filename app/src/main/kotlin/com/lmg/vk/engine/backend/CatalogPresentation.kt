package com.lmg.vk.engine.backend

import com.lmg.vk.network.dto.music.AudioRecommendedPlaylistDto
import com.lmg.vk.network.dto.music.VkCatalogProfile
import kotlin.math.roundToInt

internal fun HomeItem.catalogIdentity(): String = when {
    isStreamMix -> "mix:$id"
    isArtist -> "artist:$id"
    isAlbum -> "album:${collectionId ?: id}"
    isPlaylist -> "playlist:${collectionId ?: id}"
    isTrack -> "audio:${trackId ?: id}"
    else -> "card:$id"
}

internal fun AudioRecommendedPlaylistDto.toCatalogRecommendation(
    playlist: HomeItem?,
    owner: VkCatalogProfile?,
    tracksById: Map<String, HomeItem>,
): HomeItem? {
    val playlistId = fullId ?: return null
    return HomeItem(
        id = "recommended_playlist_$playlistId",
        title = playlist?.title?.takeIf(String::isNotBlank) ?: owner?.displayName.orEmpty(),
        cover = cover?.takeIf(String::isNotBlank) ?: photo?.bestUrl ?: playlist?.cover,
        collectionId = playlist?.collectionId ?: playlistId,
        source = "vk",
        isPlaylist = true,
        recommendation = HomeRecommendation(
            percentage = percentage,
            percentageTitle = percentage_title,
            ownerName = owner?.displayName?.takeIf(String::isNotBlank),
            ownerAvatar = owner?.photo_base,
            tracks = audios.orEmpty().mapNotNull { tracksById[it] ?: tracksById[it.removePrefix("vk_")] },
        ),
    )
}

internal fun catalogMatchPercent(value: Float?): Int? {
    if (value == null || !value.isFinite() || value < 0f) return null
    return (if (value <= 1f) value * 100f else value).roundToInt().coerceIn(0, 100)
}

internal fun bestCatalogImageUrl(values: List<Any?>): String? {
    val candidates = mutableListOf<Pair<String, Double>>()
    fun collect(value: Any?) {
        when (value) {
            is String -> if (value.startsWith("https://", true) || value.startsWith("http://", true)) {
                candidates += value to 0.0
            }
            is Map<*, *> -> {
                val area = ((value["width"] as? Number)?.toDouble() ?: 0.0) *
                    ((value["height"] as? Number)?.toDouble() ?: 0.0)
                listOf("url", "src", "uri").mapNotNull { value[it] as? String }
                    .filter { it.startsWith("https://", true) || it.startsWith("http://", true) }
                    .forEach { candidates += it to area }
                value.filterKeys { it !in setOf("url", "src", "uri") }.values.forEach(::collect)
            }
            is Iterable<*> -> value.forEach(::collect)
        }
    }
    values.forEach(::collect)
    return candidates.maxByOrNull { it.second }?.first
}
