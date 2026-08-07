package com.lmg.vk.network.dto.music

import com.squareup.moshi.Json
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
    // Catalog2ResponseJsonAdapter VK X: banner and curator blocks are part of
    // the main music catalog (for example, "Сегодня в плеере" and editorial
    // selections), not application-owned recommendations.
    val catalog_banners: List<VkCatalogBanner>? = null,
    val curators: List<VkCatalogProfile>? = null,
    val audio_content_cards: List<VkAudioContentCard>? = null,
    val music_owners: List<VkCatalogProfile>? = null,
    val audios: List<AudioTrack>? = null,
    val playlists: List<AudioPlaylist>? = null,
    val artists: List<VkArtistDto>? = null,
    val suggestions: List<VkCatalogSuggestion>? = null,
    val texts: List<VkCatalogText>? = null,
    val podcast_episodes: List<VkCatalogPodcastEntry>? = null,
    val podcast_slider_items: List<VkCatalogPodcastEntry>? = null,
    val longreads: List<VkCatalogLongread>? = null,
    val podcasts: List<VkCatalogPodcastEntry>? = null,
    val audio_books: List<VkCatalogAudioBook>? = null,
    val audio_books_persons: List<VkCatalogAudioBookPerson>? = null,
    val audio_followings_update_info: List<VkCatalogFollowingsUpdateInfo>? = null,
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
    /** Catalog2Block.actions: VK X extracts section_id from header actions. */
    val actions: List<VkCatalogButton>? = null,
    val next_from: String? = null,
    val audios_ids: List<String>? = null,
    val playlists_ids: List<String>? = null,
    val artists_ids: List<String>? = null,
    val artist_videos_ids: List<String>? = null,
    val videos_ids: List<String>? = null,
    val links_ids: List<String>? = null,
    val catalog_banner_ids: List<String>? = null,
    val curators_ids: List<String>? = null,
    val group_ids: List<String>? = null,
    val audio_content_card_ids: List<String>? = null,
    val music_owners_ids: List<String>? = null,
    val suggestions_ids: List<String>? = null,
    val text_ids: List<String>? = null,
    val podcast_episodes_ids: List<String>? = null,
    val podcast_slider_items_ids: List<String>? = null,
    val podcast_items_ids: List<String>? = null,
    val longreads_ids: List<String>? = null,
    val audio_book_ids: List<String>? = null,
    val audio_books_person_ids: List<String>? = null,
    val audio_followings_update_info_ids: List<String>? = null,
    val placeholder_ids: List<String>? = null,
    val radio_stations_ids: List<String>? = null,
    val audio_stream_mixes_ids: List<String>? = null,
)

/**
 * `Catalog2Button` — the recovered root catalog navigation item.
 *
 * `options` — это табы блока `subsection_tabs`: VK X берёт первый элемент
 * `Catalog2Block.actions` и рисует именно его `options`
 * (`src-deobf/C2077e.java:645-672`). Порядок ключей и типы — из
 * `ua_itaysonlab_catalogkit_objects_Catalog2ButtonJsonAdapter.java:22-42`.
 */
@JsonClass(generateAdapter = true)
data class VkCatalogButton(
    val section_id: String? = null,
    val title: String? = null,
    val block_id: String? = null,
    val options: List<VkCatalogReplacementOption>? = null,
)

/**
 * `Catalog2ReplacementOption` — один таб подраздела.
 *
 * Ключи и типы подтверждены адаптером
 * `ua_itaysonlab_catalogkit_objects_Catalog2ReplacementOptionJsonAdapter.java:14-25`.
 * `selected` объявлен там как `Integer.class`, то есть на проводе это ЧИСЛО 0/1,
 * а не JSON-литерал `true`/`false` — `Boolean?` здесь уронил бы разбор блока.
 */
@JsonClass(generateAdapter = true)
data class VkCatalogReplacementOption(
    val replacement_id: String? = null,
    val text: String? = null,
    val icon: String? = null,
    val selected: Int? = null,
)

/** `Catalog2Banner` from the recovered CatalogKit response adapter. */
@JsonClass(generateAdapter = true)
data class VkCatalogBanner(
    val id: Int = 0,
    val images: List<VkArtistPhoto>? = null,
    val text: String? = null,
    val title: String? = null,
    val subtext: String? = null,
    val image_mode: String? = null,
) {
    fun coverUrl(): String? = images.orEmpty()
        .filter { it.url.isNotBlank() }
        .maxByOrNull { it.width * it.height }
        ?.url
}

/** `AudioContentCard` from the recovered VK X Catalog2Response adapter. */
@JsonClass(generateAdapter = true)
data class VkAudioContentCard(
    val editor_annotation: String? = null,
    val editor_background_image: List<VkArtistPhoto>? = null,
    val editor_gradient_image: List<VkArtistPhoto>? = null,
    val editor_tag: String? = null,
    val entity_id: String = "",
    val entity_owner_id: String = "",
    val entity_type: String = "",
) {
    val fullId: String get() = "${entity_owner_id}_${entity_id}"

    fun coverUrl(): String? = (editor_background_image.orEmpty() + editor_gradient_image.orEmpty())
        .filter { it.url.isNotBlank() }
        .maxByOrNull { it.width * it.height }
        ?.url
}

@JsonClass(generateAdapter = true)
data class VkCatalogLayout(
    val name: String? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val style: String? = null,
    /**
     * Единственное поле layout'а `owner_cell` (`Catalog2Layout.OwnerCell`).
     *
     * Зачем оно нам: у `owner_cell` НЕТ ни заголовка, ни списка — только этот
     * id. Именно он и отличает «своё» сообщество артиста от «похожих»: VK
     * помечает `owner_cell`-ом ровно один блок страницы, и `owner_id` в нём
     * указывает на официальную страницу самого артиста. У блоков с похожими
     * сообществами layout обычный (`slider`/`list`) и `owner_id` отсутствует.
     * Без этого поля различить их в ответе нечем — оба блока приходят как
     * `group_ids` + `groups`.
     *
     * Источник: `src-deobf/ua_itaysonlab_catalogkit_objects_seals_Catalog2Layout_OwnerCellJsonAdapter.java:13`
     * (`firebase("owner_id")`, тип `Long.class`) и
     * `…Catalog2LayoutJsonAdapter.java:86` (`OwnerCell.class → "owner_cell"`).
     * Тип Long, потому что owner может быть отрицательным (сообщество).
     */
    val owner_id: Long? = null,
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

/** Editorial entities that CatalogKit returns in addition to audio cards. */
@JsonClass(generateAdapter = true)
data class VkCatalogSuggestion(
    val title: String? = null,
    val subtitle: String? = null,
    val type: String? = null,
    val context: String? = null,
    val id: String? = null,
)

@JsonClass(generateAdapter = true)
data class VkCatalogText(
    val id: String? = null,
    val text: String? = null,
    val collapsed_lines: Int? = null,
)

/** Safe wire subset of Podcasts/PodcastEpisode; fields are optional by design. */
@JsonClass(generateAdapter = true)
data class VkCatalogPodcastEntry(
    @Json(name = "podcast_title") val podcastTitle: String? = null,
    @Json(name = "owner_id") val ownerId: Long = 0L,
    val id: Int = 0,
    @Json(name = "playlist_id") val playlistId: Int = 0,
    val title: String? = null,
    val subtitle: String? = null,
    val artist: String? = null,
    val duration: Int = 0,
    val description: String? = null,
    val thumbs: List<Any?> = emptyList(),
) {
    val aliases: List<String>
        get() = listOfNotNull(
            id.takeIf { it != 0 }?.toString(),
            playlistId.takeIf { it != 0 }?.toString(),
            if (ownerId != 0L && id != 0) "${ownerId}_$id" else null,
            if (ownerId != 0L && playlistId != 0) "${ownerId}_$playlistId" else null,
        )

    val displayTitle: String
        get() = title?.takeIf(String::isNotBlank)
            ?: podcastTitle?.takeIf(String::isNotBlank)
            ?: "Подкаст VK"
}

@JsonClass(generateAdapter = true)
data class VkCatalogLongread(
    val id: Int = 0,
    @Json(name = "owner_id") val ownerId: Long = 0L,
    @Json(name = "owner_name") val ownerName: String? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val url: String? = null,
    @Json(name = "view_url") val viewUrl: String? = null,
    val views: Int = 0,
    val shares: Int = 0,
    val photo: VkCatalogPodcastCover? = null,
)

@JsonClass(generateAdapter = true)
data class VkCatalogPodcastCover(
    val sizes: List<VkCatalogPodcastCoverSize> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class VkCatalogPodcastCoverSize(
    val width: Int = 0,
    val height: Int = 0,
    val src: String? = null,
)

@JsonClass(generateAdapter = true)
data class VkCatalogAudioBook(
    val id: Int = 0,
    val title: String? = null,
    val duration: Int = 0,
    val publisher: VkCatalogBookPublisher? = null,
    val cover: List<Any?> = emptyList(),
    val authors: List<Any?> = emptyList(),
    val narrators: List<Any?> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class VkCatalogBookPublisher(
    val title: String? = null,
)

@JsonClass(generateAdapter = true)
data class VkCatalogAudioBookPerson(
    val id: Int = 0,
    val name: String? = null,
    val description: String? = null,
    val photo: List<Any?> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class VkCatalogFollowingsUpdateInfo(
    val id: Long = 0L,
    val title: String? = null,
    val covers: List<Any?> = emptyList(),
)
