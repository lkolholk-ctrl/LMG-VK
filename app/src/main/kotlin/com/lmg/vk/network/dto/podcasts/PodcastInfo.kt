package com.lmg.vk.network.dto.podcasts

import com.squareup.moshi.JsonClass
import com.lmg.vk.network.dto.music.AlbumThumb

/**
 * Из `ua.lmg.vkapi2.objects.podcasts.PodcastInfo`
 * (+ transient AlbumThumb, вычисляемый из cover).
 */
@JsonClass(generateAdapter = true)
data class PodcastInfo(
    val cover: PodcastCover? = null,
    val plays: Int = 0,
    val is_favorite: Boolean? = null,
    val description: String? = null,
    val position: Int? = null,
) {
    /** Преобразованная обложка (в оригинале — transient-геттер `ad()`). */
    val thumb: AlbumThumb?
        get() = cover?.sizes?.maxByOrNull { it.width }?.let {
            AlbumThumb(width = it.width, height = it.height, src = it.src)
        }
}

/** Из `ua.lmg.vkapi2.objects.podcasts.PodcastCover` (+ PodcastCoverSize). */
@JsonClass(generateAdapter = true)
data class PodcastCover(
    val sizes: List<PodcastCoverSize> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class PodcastCoverSize(
    val width: Int = 0,
    val height: Int = 0,
    val src: String = "",
)
