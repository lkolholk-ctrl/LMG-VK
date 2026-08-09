package com.lmg.vk.network.dto.music

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Восстановлено из старого `ua.lmg.vkapi2.objects.music.playlist.AudioPlaylist`
 * (31 ключ Moshi) и `playlist.album.AudioAlbum`.
 * Новый `bruhcollective...AudioPlaylistDto` хранится отдельно: это другой DTO.
 */
@JsonClass(generateAdapter = true)
data class AudioPlaylist(
    val id: Int = 0,
    val owner_id: Long = 0L,
    val type: String? = null,
    val album: AlbumMeta? = null,
    val title: String = "",
    val description: String? = null,
    val count: Int = 0,
    val followers: Int = 0,
    val plays: Int = 0,
    val create_time: Long = 0L,
    val update_time: Long? = null,
    val genres: List<Genre>? = null,
    val is_following: Boolean? = null,
    val is_curator: Boolean? = null,
    val audios: List<AudioTrack>? = null,
    val year: Int = 0,
    val followed: FollowedMetadata? = null,
    val original: OriginalPlaylist? = null,
    val photo: AlbumThumb? = null,
    val thumbs: List<AlbumThumb>? = null,
    val access_key: String? = null,
    val is_explicit: Boolean? = null,
    val subtitle: String? = null,
    val main_artists: List<MainArtist>? = null,
    val subtitle_badge: Boolean = false,
    val no_discover: Boolean = false,
    val audio_chart_info: AudioChartInfo? = null,
    val meta: AudioPlaylistMeta? = null,
    val restriction: MusicDynamicRestriction? = null,
    val permissions: AudioPlaylistPermissions? = null,
    val main_color: String? = null,
    /**
     * Суммарная длительность плейлиста в СЕКУНДАХ, если VK её прислал.
     *
     * ВАЖНО: `duration` НЕ входит в допустимые `extra_fields` (проверено по
     * AudioGetPlaylistByIdExtraFieldsDto в декомпиле 8.185 — там только
     * album_parts_first_audios, audio_ids, extra_recommendations_section_id,
     * owner). Поле объявлено на случай, если VK отдаёт его по умолчанию;
     * запрашивать его через extra_fields бессмысленно.
     */
    val duration: Int? = null,
    /**
     * Список id треков плейлиста без полных объектов.
     *
     * Приходит только при `extra_fields=audio_ids`. Дешевле полной выдачи, когда
     * нужен лишь порядок и состав — например, для построения очереди.
     */
    val audio_ids: List<String>? = null,
) {
    val fullId: String get() = "${owner_id}_$id"

    /**
     * Длительность в миллисекундах — в единицах, которыми оперирует плеер.
     * `null`, если VK поле не присылал (не запрошено или пустой плейлист).
     */
    val durationMs: Long? get() = duration?.takeIf { it > 0 }?.times(1000L)

    /** Из вложенного `AudioPlaylist.AlbumMeta`. */
    @JsonClass(generateAdapter = true)
    data class AlbumMeta(
        val id: String? = null,
        val title: String? = null,
        /**
         * Вид релиза: `album`, `collection`, `ep`, `single`.
         *
         * Сверено с официальным клиентом 8.185 (`AudioPlaylistAlbumItemDto`).
         * Без него сингл и EP подписывались как «Плейлист» — VK эту разницу
         * присылает, а мы её теряли.
         */
        val type: String? = null,
        /** Как VK показывает релиз: `collection`, `main_feat`, `main_only`, `playlist`. */
        val view: String? = null,
    ) {
        /**
         * Подпись для UI по [type]. `null` — VK вида не прислал, и подставлять
         * «Альбом» наугад нельзя: у сборников и участий это было бы неверно.
         */
        val typeLabel: String?
            get() = when (type) {
                "single" -> "Сингл"
                "ep" -> "EP"
                "album" -> "Альбом"
                "collection" -> "Сборник"
                else -> null
            }
    }
}

/** Из `ua.lmg.vkapi2.objects.music.playlist.AudioPlaylistPermissions`. */
@JsonClass(generateAdapter = true)
data class AudioPlaylistPermissions(
    @Json(name = "save_as_copy") val saveAsCopy: Boolean = false,
    @Json(name = "follow") val follow: Boolean = false,
    @Json(name = "delete") val delete: Boolean = false,
    @Json(name = "edit") val edit: Boolean = false,
    @Json(name = "share") val share: Boolean = false,
    @Json(name = "play") val play: Boolean = false,
)

/** Из `ua.lmg.vkapi2.objects.music.playlist.album.AudioAlbum`. */
@JsonClass(generateAdapter = true)
data class AudioAlbum(
    val id: Int? = null,
    val owner_id: Long? = null,
    val access_key: String? = null,
    val title: String? = null,
    val thumb: AlbumThumb? = null,
    val main_color: String? = null,
)

@JsonClass(generateAdapter = true)
data class AlbumThumb(
    val width: Int = 0,
    val height: Int = 0,
    val src: String = "",
    val photo_34: String? = null,
    val photo_68: String? = null,
    val photo_135: String? = null,
    val photo_270: String? = null,
    val photo_300: String? = null,
    val photo_600: String? = null,
    val photo_1200: String? = null,
    val sizes: List<AudioPhotoSizesDto>? = null,
) {
    /**
     * Ссылка на обложку для КРУПНЫХ мест: полноэкранный плеер, шапка альбома.
     *
     * `sizes` проверяется ПЕРВЫМ, а не последним: фиксированные `photo_*`
     * обрываются на 1200px, тогда как в `sizes` VK кладёт варианты крупнее
     * (у обложек это `w`/`z` до 2560px) и в WebP. Прежний порядок брал
     * `photo_1200` всегда, даже когда рядом лежал вариант вдвое больше.
     *
     * Сравнение по ПЛОЩАДИ, а не по буквенному `type`: у VK порядок букв
     * (`s m x o p q r y z w`) не совпадает с порядком величины, и новый тип
     * сломал бы сортировку по алфавиту.
     *
     * ДЛЯ СПИСКОВ использовать [thumbUrlFor]: тянуть 2560px в строку на 48dp —
     * лишний трафик и память. Виджет для этого имеет свой `widgetThumbUrl()`
     * (там ограничение биндера, см. VkWidgetModels).
     */
    val bestUrl: String
        get() = sizes.orEmpty()
            .filter { it.src.isNotBlank() }
            .maxByOrNull { it.width.toLong() * it.height.toLong() }
            ?.src
            ?: fixedPhotoUrl()
            ?: src

    /**
     * Наименьшая ссылка, которая покрывает [minSidePx] по меньшей стороне.
     *
     * Нужна там, где обложка мелкая: строка списка, карточка карусели. Берём не
     * максимум и не минимум, а первый достаточный вариант — картинка не мылит и
     * не тащит лишние мегабайты.
     */
    fun thumbUrlFor(minSidePx: Int): String {
        val candidates = sizes.orEmpty().filter { it.src.isNotBlank() }
        if (candidates.isEmpty()) return fixedPhotoUrl() ?: src
        val sorted = candidates.sortedBy { minOf(it.width, it.height) }
        return sorted.firstOrNull { minOf(it.width, it.height) >= minSidePx }?.src
            // Всё меньше запрошенного — отдаём самый крупный, что есть.
            ?: sorted.last().src
    }

    private fun fixedPhotoUrl(): String? = listOfNotNull(
        photo_1200,
        photo_600,
        photo_300,
        photo_270,
        photo_135,
        photo_68,
        photo_34,
    ).firstOrNull { it.isNotBlank() }
}

/** Из `playlist.metadata.FollowedMetadata` / `OriginalPlaylist` / `AudioPlaylistMeta`. */
@JsonClass(generateAdapter = true)
data class FollowedMetadata(
    val follower_id: Long = 0L,
    val is_followed: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class OriginalPlaylist(
    val id: Int = 0,
    val owner_id: Long = 0L,
    val access_key: String? = null,
    val title: String? = null,
)

@JsonClass(generateAdapter = true)
data class AudioPlaylistMeta(
    val view: String? = null,
    val variant: String? = null,
)
