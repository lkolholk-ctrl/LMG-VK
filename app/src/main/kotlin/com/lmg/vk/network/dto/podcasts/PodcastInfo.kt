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
        get() = cover?.sizes
            ?.filter { it.imageUrl != null }
            ?.maxByOrNull { it.width }
            ?.let {
                AlbumThumb(width = it.width, height = it.height, src = it.imageUrl.orEmpty())
            }
}

/** Из `ua.lmg.vkapi2.objects.podcasts.PodcastCover` (+ PodcastCoverSize). */
@JsonClass(generateAdapter = true)
data class PodcastCover(
    val sizes: List<PodcastCoverSize> = emptyList(),
)

/**
 * `PodcastCoverSize` — 5 ключей по
 * `ua_itaysonlab_vkapi2_objects_podcasts_PodcastCoverSizeJsonAdapter.java:14-21`.
 * `type` и `url` раньше отсутствовали, из-за чего обложка подкаста парсилась
 * с потерями: VK отдаёт ссылку в `url` там, где нет `src`.
 */
@JsonClass(generateAdapter = true)
data class PodcastCoverSize(
    val width: Int = 0,
    val height: Int = 0,
    val src: String = "",
    val type: String? = null,
    val url: String? = null,
) {
    /** Ссылка на изображение из того ключа, который VK реально прислал. */
    val imageUrl: String?
        get() = src.takeIf { it.isNotBlank() } ?: url?.takeIf { it.isNotBlank() }
}
