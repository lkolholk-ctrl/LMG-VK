package com.lmg.vk.network.dto.music

import com.squareup.moshi.JsonClass

/**
 * DTO, подтверждённые восстановлением Priority 1 из VK X 8.12.1.
 * Имена JSON-полей взяты из сгенерированных адаптеров APK, а не подобраны
 * по ответам сервера.
 */

@JsonClass(generateAdapter = true)
data class AudioLyricsContainer(
    val md5: String = "",
    val lyrics: AudioLyrics,
    val credits: String = "",
)

@JsonClass(generateAdapter = true)
data class VkRootItems<T>(
    val count: Int? = null,
    val items: List<T> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class VkArtistPhoto(
    val height: Int = 0,
    val url: String = "",
    val width: Int = 0,
    val id: String? = null,
)

@JsonClass(generateAdapter = true)
data class VkArtistPhotosContainer(
    val type: String? = null,
    val photo: List<VkArtistPhoto> = emptyList(),
)

/** `AudioArtistDto`/`CatalogArtist`: общий подтверждённый набор полей. */
@JsonClass(generateAdapter = true)
data class VkArtistDto(
    val name: String = "",
    val domain: String? = null,
    val id: String = "",
    val is_album_cover: Boolean? = null,
    val photo: List<VkArtistPhoto>? = null,
    val photos: List<VkArtistPhotosContainer>? = null,
    val is_followed: Boolean? = null,
    val can_follow: Boolean? = null,
    val can_play: Boolean? = null,
    val genres: List<Genre>? = null,
    val bio: String? = null,
    val track_code: String? = null,
) {
    fun coverUrl(): String? = sequence {
        photo.orEmpty().forEach { yield(it) }
        photos.orEmpty().flatMap { it.photo }.forEach { yield(it) }
    }.filter { it.url.isNotBlank() }
        .maxByOrNull { it.width * it.height }
        ?.url
}

@JsonClass(generateAdapter = true)
data class AudioRelatedArtistsResponse(
    val artists: List<VkArtistDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class AudioStreamMixLink(
    val id: String,
    val title: String = "",
)

@JsonClass(generateAdapter = true)
data class AudioStreamMixTitles(
    val common_state: String = "",
    val playing_state: String = "",
)

@JsonClass(generateAdapter = true)
data class AudioStreamMix(
    val id: String,
    val title: String,
    val description: String,
    val stream_mix: AudioStreamMixLink? = null,
    val is_tunable: Boolean? = null,
    val titles: AudioStreamMixTitles? = null,
)

@JsonClass(generateAdapter = true)
data class AudioStreamMixSettingsOption(
    val id: String,
    val icon: String,
    val selected: Boolean,
    val title: String,
)

@JsonClass(generateAdapter = true)
data class AudioStreamMixSettingsCategory(
    val id: String,
    val title: String,
    val type: String,
    val options: List<AudioStreamMixSettingsOption>,
)

@JsonClass(generateAdapter = true)
data class AudioStreamMixSettings(
    val title: String,
    val subtitle: String,
    val mix_categories: List<AudioStreamMixSettingsCategory>,
)

@JsonClass(generateAdapter = true)
data class AudioStreamMixSettingsResponse(
    val settings: AudioStreamMixSettings,
)

/**
 * Нужный LMG поднабор `Catalog2Response`. Неизвестные поля блоков Moshi
 * пропускает, а сущности каталога остаются типизированными.
 */
@JsonClass(generateAdapter = true)
data class VkCatalogResponse(
    val audios: List<AudioTrack>? = null,
    val playlists: List<AudioPlaylist>? = null,
    val artists: List<VkArtistDto>? = null,
    val radio_stations: List<RadioStation>? = null,
    val audio_stream_mixes: List<AudioStreamMix>? = null,
)
