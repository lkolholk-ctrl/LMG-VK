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
    // --- ключи нового слоя C3961e/C9432e, типы подтверждены ---
    val rss_guid: String? = null,
    val restriction_description: String? = null,
    val restriction_text: String? = null,
    val is_random: Boolean? = null,
    val post: String? = null,
    val is_donut: Boolean? = null,
    val podcast_id: Int? = null,
    // Ключи `restriction_button` и `friends_liked` в C3961e есть, но их типы
    // (BaseLinkButtonDto / элемент списка) доками не подтверждены. Moshi
    // неизвестные ключи игнорирует, поэтому пропуск безопаснее догадки.
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
