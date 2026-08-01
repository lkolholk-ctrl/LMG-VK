package com.lmg.vk.network.dto.music

import com.squareup.moshi.JsonClass

/**
 * Новое kotlinx-семейство `bruhcollective.itaysonlab.vkapi.objects.audio.*`.
 * Оно намеренно не объединено со старыми `ua.lmg.vkapi2` Moshi-моделями.
 */

@JsonClass(generateAdapter = true)
data class AudioSearchMainResponse(
    val albums: VkRootItems<AudioPlaylistDto> = VkRootItems(),
    val audios: VkRootItems<AudioAudioDto> = VkRootItems(),
    val artists: VkRootItems<AudioArtistDto> = VkRootItems(),
    val playlists: VkRootItems<AudioPlaylistDto> = VkRootItems(),
    val own_audios: VkRootItems<AudioAudioDto> = VkRootItems(),
    val own_playlists: VkRootItems<AudioPlaylistDto> = VkRootItems(),
    val own_albums: VkRootItems<AudioPlaylistDto> = VkRootItems(),
)

/** Минимально нужный, но wire-точный поднабор 39-польного C18422e/C14729e. */
@JsonClass(generateAdapter = true)
data class AudioAudioDto(
    val artist: String,
    val id: Int,
    val owner_id: Long,
    val title: String,
    val duration: Int,
    val access_key: String? = null,
    val is_explicit: Boolean? = null,
    val is_focus_track: Boolean? = null,
    val is_licensed: Boolean? = null,
    val track_code: String? = null,
    val url: String? = null,
    val date: Int? = null,
    val album_id: Int? = null,
    val has_lyrics: Boolean? = null,
    val album: AudioAudioAlbumDto? = null,
    val main_artists: List<AudioArtistDto>? = null,
    val featured_artists: List<AudioArtistDto>? = null,
    val subtitle: String? = null,
    val release_audio_id: String? = null,
) {
    val fullId: String get() = "${owner_id}_$id"
}

@JsonClass(generateAdapter = true)
data class AudioAudioAlbumDto(
    val id: Int,
    val title: String,
    val owner_id: Long,
    val access_key: String,
    val thumb: AudioPhotoDto? = null,
)

/** 15 ключей C0004e/C5992e; тяжёлые социальные секции остаются opaque. */
@JsonClass(generateAdapter = true)
data class AudioArtistDto(
    val name: String,
    val domain: String? = null,
    val id: String? = null,
    val is_album_cover: Boolean? = null,
    val photo: List<BaseImageDto>? = null,
    val photos: List<AudioPhotosByTypeDto>? = null,
    val is_followed: Boolean? = null,
    val can_follow: Boolean? = null,
    val can_play: Boolean? = null,
    val genres: List<AudioGenreDto>? = null,
    val bio: String? = null,
    val pages: List<Any?>? = null,
    val profiles: List<Any?>? = null,
    val groups: List<Any?>? = null,
    val track_code: String? = null,
)

@JsonClass(generateAdapter = true)
data class BaseImageDto(
    val url: String,
    val width: Int,
    val height: Int,
    val id: String? = null,
    val theme: String? = null,
)

@JsonClass(generateAdapter = true)
data class AudioPhotosByTypeDto(
    val type: String,
    val photo: List<BaseImageDto>,
)

@JsonClass(generateAdapter = true)
data class AudioGenreDto(
    val id: Int,
    val name: String,
)

/** Полный 43-key `AudioPlaylistDto` C9885e/C1471e (в отчёте P2 было указано 41). */
@JsonClass(generateAdapter = true)
data class AudioPlaylistDto(
    val id: Int,
    val owner_id: Long,
    val type: String,
    val title: String,
    val description: String,
    val count: Int,
    val followers: Int,
    val plays: Int,
    val create_time: Int,
    val update_time: Int,
    val playlist_id: Int? = null,
    val genres: List<AudioGenreDto>? = null,
    val is_following: Boolean? = null,
    val no_discover: Boolean? = null,
    val audios: List<AudioAudioDto>? = null,
    val is_curator: Boolean? = null,
    val year: Int? = null,
    val original: AudioPlaylistReferenceDto? = null,
    val followed: AudioPlaylistReferenceDto? = null,
    val photo: AudioPhotoDto? = null,
    val permissions: AudioPlaylistPermissionsDto? = null,
    val subtitle_badge: Boolean? = null,
    val play_button: Boolean? = null,
    val thumbs: List<AudioPhotoDto>? = null,
    val access_key: String? = null,
    val uma_album_id: Int? = null,
    val subtitle: String? = null,
    val original_year: Int? = null,
    val is_explicit: Boolean? = null,
    val artists: List<AudioArtistDto>? = null,
    val main_artists: List<AudioArtistDto>? = null,
    val main_artist: String? = null,
    val featured_artists: List<AudioArtistDto>? = null,
    val album_type: String? = null,
    val meta: AudioPlaylistMetaDto? = null,
    val restriction: Any? = null,
    val track_code: String? = null,
    val audio_chart_info: AudioChartInfoDto? = null,
    val match_score: Float? = null,
    val actions: List<AudioPlaylistActionDto>? = null,
    val audios_total_file_size: Float? = null,
    val exclusive: Boolean? = null,
    val icon: String? = null,
) {
    val fullId: String get() = "${owner_id}_$id"
    fun coverUrl(): String? = photo?.bestUrl
        ?: thumbs.orEmpty().maxByOrNull { it.width * it.height }?.bestUrl
}

@JsonClass(generateAdapter = true)
data class AudioPlaylistReferenceDto(
    val playlist_id: Int,
    val owner_id: Long,
    val access_key: String? = null,
)

@JsonClass(generateAdapter = true)
data class AudioPlaylistPermissionsDto(
    val play: Boolean? = null,
    val share: Boolean? = null,
    val edit: Boolean? = null,
    val follow: Boolean? = null,
    val delete: Boolean? = null,
    val boom_download: Boolean? = null,
    val save_as_copy: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class AudioPlaylistActionDto(
    val type: String,
    val location: String,
)

@JsonClass(generateAdapter = true)
data class AudioPhotoDto(
    val width: Int,
    val height: Int,
    val id: String? = null,
    val photo_34: String? = null,
    val photo_68: String? = null,
    val photo_135: String? = null,
    val photo_270: String? = null,
    val photo_300: String? = null,
    val photo_600: String? = null,
    val photo_1200: String? = null,
    val sizes: List<AudioPhotoSizesDto>? = null,
) {
    val bestUrl: String? get() = photo_1200 ?: photo_600 ?: photo_300 ?: photo_270
        ?: photo_135 ?: photo_68 ?: photo_34
        ?: sizes.orEmpty().maxByOrNull { it.width * it.height }?.src
}

@JsonClass(generateAdapter = true)
data class AudioPhotoSizesDto(
    val src: String,
    val width: Int,
    val height: Int,
    val type: String,
)

@JsonClass(generateAdapter = true)
data class AudioPlaylistMetaDto(val view: String? = null)

@JsonClass(generateAdapter = true)
data class AudioChartInfoDto(
    val position: Int? = null,
    val state: String? = null,
)
