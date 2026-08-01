package com.lmg.vk.network.dto.music

import com.squareup.moshi.JsonClass
import com.lmg.vk.network.dto.podcasts.PodcastInfo

/**
 * Восстановлено из `ua.lmg.vkapi2.objects.music.AudioTrack`.
 * Маппинг: порядок ключей Moshi-адаптера + типы ctor + Kotlin Metadata.
 *
 * В оригинале расширяет доменные интерфейсы приложения (AbstractC16049e +
 * Interface*: идентичность/плейбэк/лайки). Здесь — чистая DTO + рантайм-состояние.
 */
@JsonClass(generateAdapter = true)
data class AudioTrack(
    val artist: String = "",
    val id: Int = 0,
    val owner_id: Long = 0L,
    val title: String = "",
    val duration: Int = 0,
    val access_key: String? = null,
    val is_explicit: Boolean = false,
    val is_licensed: Boolean = false,
    val track_code: String = "",
    val url: String = "",
    val date: Long = 0L,
    val genre_id: Int? = null,
    val content_restricted: Int = 0,
    val album: AudioAlbum? = null,
    val lyrics_id: Int? = null,
    val main_artists: List<MainArtist>? = null,
    val featured_artists: List<MainArtist>? = null,
    val subtitle: String? = null,
    val track_genre_id: Int? = null,
    val album_part_number: Int? = null,
    val is_hq: Boolean = false,
    val is_focus_track: Boolean = false,
    val has_lyrics: Boolean = false,
    val dislike: Boolean = false,
    val podcast_info: PodcastInfo? = null,
    val audio_chart_info: AudioChartInfo? = null,
    val stream_duration: Int = 0,
    val release_audio_id: String? = null,
    val like: Boolean? = null,
) {
    /** Полный id VK: "ownerId_audioId" — используется в audio.getById/плеере. */
    val fullId: String get() = "${owner_id}_$id"

    val isPodcast: Boolean get() = podcast_info != null

    /** Рантайм-состояние лайка (в оригинале — делегаты C11956e; liked/disliked). */
    @Transient
    var likedOverride: Boolean? = null

    val isLiked: Boolean get() = likedOverride ?: (like == true)
    val isDisliked: Boolean get() = likedOverride?.let { !it } ?: dislike
}

/** Из `ua.lmg.vkapi2.objects.music.playlist.metadata.MainArtist`. */
@JsonClass(generateAdapter = true)
data class MainArtist(
    val id: String = "",
    val domain: String = "",
    val name: String = "",
    val photo: List<AlbumThumb>? = null,
    val is_cached: Boolean = false,
)

/** Из `ua.lmg.vkapi2.objects.music.AudioChartInfo`. */
@JsonClass(generateAdapter = true)
data class AudioChartInfo(
    val position: Int = 0,
    val state: Int = 0, // 0=без изменений, 1=вверх, 2=вниз, 3=new (по константам приложения)
)
