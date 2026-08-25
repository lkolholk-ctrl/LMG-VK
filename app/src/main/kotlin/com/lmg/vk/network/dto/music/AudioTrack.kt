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
    val audio_streams: List<AudioStreamDto>? = null,
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
    val thumb: AlbumThumb? = null,
    val main_color: String? = null,
    val nft_info: Any? = null,
    val external_audio: Any? = null,
    val audiobook_chapter: Any? = null,
    val flags_context: Int = 0,
    val ads: AudioAdsDto? = null,
    val stories_allowed: Boolean? = null,
    val short_videos_allowed: Boolean? = null,
    val stories_cover_allowed: Boolean? = null,
    val audio_voice_assistant: AudioVoiceAssistantDto? = null,
    val original_sound_video_id: String? = null,
    val in_clips_favorite_allowed: Boolean? = null,
    val in_clips_favorite: Boolean? = null,
    val special_project_id: Int? = null,
    val legal_notices_type: Int? = null,
    val can_download_short_video: Boolean? = null,
    val preview_url: AudioPreviewUrlDto? = null,
    val audio_loudness: AudioLoudnessDto? = null,
) {
    /** Полный id VK: "ownerId_audioId" — используется в audio.getById/плеере. */
    val fullId: String get() = "${owner_id}_$id"

    val playbackUrl: String?
        get() = sequence {
            yield(url)
            val streams = audio_streams.orEmpty()
                .sortedBy { stream ->
                    AUDIO_STREAM_PRIORITY.indexOf(stream.type).takeIf { it >= 0 } ?: Int.MAX_VALUE
                }
            for (stream in streams) {
                yield(stream.url.orEmpty())
                yield(stream.fallbackUrl.orEmpty())
            }
        }.map(String::trim).firstOrNull(String::isPlayableAudioUrl)

    val isPodcast: Boolean get() = podcast_info != null

    /** Рантайм-состояние лайка (в оригинале — делегаты C11956e; liked/disliked). */
    @Transient
    var likedOverride: Boolean? = null

    val isLiked: Boolean get() = likedOverride ?: (like == true)
    val isDisliked: Boolean get() = likedOverride?.let { !it } ?: dislike

    /** VK MP3 MOD treats the service placeholder as an unavailable audio. */
    val isAvailable: Boolean
        get() = content_restricted == 0 &&
            !url.contains("audio_api_unavailable.mp3", ignoreCase = true)
}

private val AUDIO_STREAM_PRIORITY = listOf("mp3", "aac", "hls_range", "hls", "hls_ts", "dash")

private fun String.isPlayableAudioUrl(): Boolean =
    (startsWith("https://") || startsWith("http://")) &&
        !contains("audio_api_unavailable", ignoreCase = true)

private const val VK_LEGACY_AUDIO_PLACEHOLDER =
    "https://vk.com/images/audio_row_placeholder.png"

private fun String?.realVkCoverOrNull(): String? =
    this?.takeIf { it.isNotBlank() && !it.startsWith(VK_LEGACY_AUDIO_PLACEHOLDER) }

private fun AlbumThumb?.realVkThumbOrNull(): AlbumThumb? =
    this?.takeIf { it.bestUrl.realVkCoverOrNull() != null }

/**
 * Тот же приоритет, что у `MusicTrack.Db(size)` в VK:
 * album.thumb → верхнеуровневый track.thumb → локальный fallback вызывающего.
 */
fun AudioTrack.coverUrl(): String? =
    album?.thumb?.bestUrl.realVkCoverOrNull()
        ?: album?.thumb?.src.realVkCoverOrNull()
        ?: thumb?.bestUrl.realVkCoverOrNull()
        ?: thumb?.src.realVkCoverOrNull()

/**
 * Сохраняет сгенерированную VK обложку при склейке одного трека из разных
 * ответов API. Например, обычный `audio.search` может вернуть URL потока без
 * `thumb`, а `audio.searchMain` — тот же трек с цветной обложкой.
 */
fun AudioTrack.withVkArtworkFallback(fallback: AudioTrack?): AudioTrack {
    if (fallback == null || fallback.fullId != fullId) return this

    val mergedAlbum = when {
        album == null -> fallback.album
        fallback.album == null -> album
        else -> album.copy(
            thumb = album.thumb.realVkThumbOrNull()
                ?: fallback.album.thumb.realVkThumbOrNull()
                ?: album.thumb
                ?: fallback.album.thumb,
            main_color = album.main_color ?: fallback.album.main_color,
        )
    }
    return copy(
        access_key = access_key ?: fallback.access_key,
        track_code = track_code.ifBlank { fallback.track_code },
        album = mergedAlbum,
        thumb = thumb.realVkThumbOrNull()
            ?: fallback.thumb.realVkThumbOrNull()
            ?: thumb
            ?: fallback.thumb,
        main_color = main_color ?: fallback.main_color,
    ).also { merged ->
        // likedOverride лежит вне primary constructor, поэтому обычный copy()
        // его не переносит.
        merged.likedOverride = likedOverride ?: fallback.likedOverride
    }
}

/**
 * Убирает дубли треков, но не выбрасывает более полные `thumb/main_color` из
 * следующего ответа. Порядок и остальные поля первого экземпляра сохраняются.
 */
fun Iterable<AudioTrack>.mergeAudioTracksById(): List<AudioTrack> {
    val merged = linkedMapOf<String, AudioTrack>()
    forEach { track ->
        merged[track.fullId] = merged[track.fullId]?.withVkArtworkFallback(track) ?: track
    }
    return merged.values.toList()
}

/** Из `ua.lmg.vkapi2.objects.music.playlist.metadata.MainArtist`. */
@JsonClass(generateAdapter = true)
data class MainArtist(
    val id: String = "",
    val domain: String = "",
    val name: String = "",
    val photo: List<AlbumThumb>? = null,
    val is_cached: Boolean = false,
    val bio: String? = null,
    val genres: List<Genre>? = null,
    val is_album_cover: Boolean? = null,
    val can_follow: Boolean? = null,
    val is_followed: Boolean? = null,
    val track_code: String? = null,
    val can_play: Boolean? = null,
    val video_owner_id: Long? = null,
    val flags_context: Int? = null,
    val listeners_count: Int? = null,
)

/** Из `ua.lmg.vkapi2.objects.music.AudioChartInfo`. */
@JsonClass(generateAdapter = true)
data class AudioChartInfo(
    val position: Int = 0,
    val state: Int = 0, // 0=без изменений, 1=вверх, 2=вниз, 3=new (по константам приложения)
)
