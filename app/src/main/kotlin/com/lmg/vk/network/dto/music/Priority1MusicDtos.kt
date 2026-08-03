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
    val catalog: VkCatalogRoot? = null,
    val section: VkCatalogSection? = null,
    val block: VkCatalogBlock? = null,
    val profiles: List<VkCatalogProfile>? = null,
    val groups: List<VkCatalogProfile>? = null,
    val artist_videos: List<VkCatalogVideo>? = null,
    val videos: List<VkCatalogVideo>? = null,
    val links: List<VkCatalogLink>? = null,
    val audios: List<AudioTrack>? = null,
    val playlists: List<AudioPlaylist>? = null,
    val artists: List<VkArtistDto>? = null,
    val radio_stations: List<RadioStation>? = null,
    val audio_stream_mixes: List<AudioStreamMix>? = null,
)

/** Структура Catalog2Response из одноимённого адаптера VK X. */
@JsonClass(generateAdapter = true)
data class VkCatalogRoot(
    val default_section: String = "",
    val sections: List<VkCatalogSection> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class VkCatalogSection(
    val id: String = "",
    val title: String = "",
    val next_from: String? = null,
    val blocks: List<VkCatalogBlock>? = null,
)

/** Общая wire-модель sealed Catalog2Block из адаптеров VK X. */
@JsonClass(generateAdapter = true)
data class VkCatalogBlock(
    val id: String = "",
    val data_type: String = "",
    val layout: VkCatalogLayout? = null,
    val next_from: String? = null,
    val audios_ids: List<String>? = null,
    val playlists_ids: List<String>? = null,
    val artists_ids: List<String>? = null,
    val artist_videos_ids: List<String>? = null,
    val videos_ids: List<String>? = null,
    val links_ids: List<String>? = null,
    val music_owners_ids: List<String>? = null,
)

@JsonClass(generateAdapter = true)
data class VkCatalogLayout(
    val name: String? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val style: String? = null,
)

/** VKProfile: один DTO используется VK X для profiles, groups и curators. */
@JsonClass(generateAdapter = true)
data class VkCatalogProfile(
    val id: Long = 0L,
    val first_name: String? = null,
    val last_name: String? = null,
    val photo_base: String? = null,
    val name: String? = null,
    val is_followed: Boolean? = null,
    val can_follow: Boolean? = null,
) {
    val displayName: String
        get() = listOfNotNull(first_name, last_name).joinToString(" ")
            .ifBlank { name.orEmpty() }
}

@JsonClass(generateAdapter = true)
data class VkCatalogLink(
    val title: String = "",
    val subtitle: String = "",
    val image: List<VkArtistPhoto>? = null,
    val url: String = "",
    val id: String = "",
) {
    fun coverUrl(): String? = image.orEmpty()
        .filter { it.url.isNotBlank() }
        .maxByOrNull { it.width * it.height }
        ?.url
}

@JsonClass(generateAdapter = true)
data class VkCatalogVideo(
    val id: Int = 0,
    val owner_id: Long? = null,
    val title: String = "",
    val image: List<VkArtistPhoto>? = null,
    val duration: Int = 0,
    val direct_url: String? = null,
) {
    val fullId: String get() = "${owner_id ?: 0L}_$id"
    fun coverUrl(): String? = image.orEmpty()
        .filter { it.url.isNotBlank() }
        .maxByOrNull { it.width * it.height }
        ?.url
}
