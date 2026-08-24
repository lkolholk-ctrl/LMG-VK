package com.lmg.vk.network.dto.music

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AudioLyricsContainer(
    val text: String? = null,
    val md5: String? = null,
    val lyrics: AudioLyrics? = null,
    val credits: String? = null,
)

@JsonClass(generateAdapter = true)
data class VkRootItems<T>(
    val count: Int? = null,
    val items: List<T> = emptyList(),
)

/** Точный ответ актуального `audio.add` из официального клиента VK. */
@JsonClass(generateAdapter = true)
data class AudioAddResponse(
    val items_count: Int = 0,
    val errors_count: Int = 0,
    val items: List<AudioAddResult>? = null,
    val errors: List<AudioAddError>? = null,
)

@JsonClass(generateAdapter = true)
data class AudioAddResult(
    val new_audio_id: Int,
    val audio_raw_id: String = "",
    val new_owner_id: Long,
) {
    val fullId: String get() = "${new_owner_id}_${new_audio_id}"
}

@JsonClass(generateAdapter = true)
data class AudioAddError(
    val audio_raw_id: String = "",
    val error_code: String = "",
    val error_msg: String = "",
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
    val background_animation_url: String? = null,
    val image_url: String? = null,
    val settings: AudioStreamMixSettings? = null,
    val stream_mix: AudioStreamMixLink? = null,
    val is_tunable: Boolean? = null,
    val titles: AudioStreamMixTitles? = null,
) {
    /**
     * `id` identifies the CatalogKit item, while official VK starts and
     * continues playback with the nested `stream_mix.id`.
     */
    val playbackMixId: String
        get() = stream_mix?.id?.takeIf(String::isNotBlank) ?: id
}

@JsonClass(generateAdapter = true)
data class AudioStreamMixSettingsOption(
    val id: String,
    val icon: String,
    @Json(name = "icon_badge") val iconBadge: String? = null,
    val selected: Boolean? = null,
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
    val multi_select: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class AudioStreamMixSettingsResponse(
    // Nullable in the official VK 8.185 DTO. A successful response may state
    // that this particular Mix has no editor instead of returning an API error.
    val settings: AudioStreamMixSettings? = null,
)

/** Exact response of official VK 8.185 `audio.getAutoflowMixParams`. */
@JsonClass(generateAdapter = true)
data class AudioGetAutoflowMixParamsResponse(
    val mix_id: String,
    val entity_id: String,
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
    val catalog_banners: List<VkCatalogBanner>? = null,
    val curators: List<VkCatalogProfile>? = null,
    val audio_content_cards: List<VkAudioContentCard>? = null,
    val music_owners: List<VkCatalogProfile>? = null,
    val audios: List<AudioTrack>? = null,
    /** VK Music `music_recommended_playlists` extended entities. */
    val recommended_playlists: List<AudioRecommendedPlaylistDto>? = null,
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
    /** VK Music 8.37 Catalog2 extended entity (`audio_signal_common_info`). */
    val audio_signal_common_info: List<VkAudioSignalCommonInfo>? = null,
)

@JsonClass(generateAdapter = true)
data class VkAccountToggle(
    val name: String,
    val value: String,
    val enabled: Boolean,
    val ab_group_id: Int? = null,
    val experiment_id: Int? = null,
)

@JsonClass(generateAdapter = true)
data class VkCatalogSearchRecent(
    val entity_type: String,
    val id: String? = null,
    val owner_id: String? = null,
)

/** Exact useful subset of official VK Music `AudioRecommendedPlaylistDto`. */
@JsonClass(generateAdapter = true)
data class AudioRecommendedPlaylistDto(
    val id: Int? = null,
    val owner_id: Long? = null,
    val percentage: Float? = null,
    val percentage_title: String? = null,
    val is_curator: Boolean? = null,
    val audios: List<String>? = null,
    val color: String? = null,
    val cover: String? = null,
    val photo: AudioPhotoDto? = null,
    val withOwner: Boolean? = null,
) {
    val fullId: String? get() = owner_id?.let { ownerId ->
        id?.let { playlistId -> "${ownerId}_$playlistId" }
    }
}

/**
 * Server-owned Signal card from VK Music. `audios` contains VK full audio ids;
 * the playable queue itself is resolved by the card's catalog action through
 * `audio.getIdsBySource(source=catalog)`.
 */
@JsonClass(generateAdapter = true)
data class VkAudioSignalCommonInfo(
    val id: String = "",
    val cover: String? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val current_month: String? = null,
    val audios: List<String>? = null,
)

@JsonClass(generateAdapter = true)
data class VkCatalogRoot(
    val default_section: String = "",
    val sections: List<VkCatalogSection> = emptyList(),
    val buttons: List<VkCatalogButton>? = null,
    val footer: VkCatalogBlocksContainer? = null,
    val header: VkCatalogBlocksContainer? = null,
    val pinned_section: String? = null,
    val session_id: String? = null,
)

@JsonClass(generateAdapter = true)
data class VkCatalogBlocksContainer(
    val blocks: List<VkCatalogBlock>? = null,
)

@JsonClass(generateAdapter = true)
data class VkCatalogSection(
    val id: String = "",
    val title: String = "",
    val next_from: String? = null,
    val blocks: List<VkCatalogBlock>? = null,
    val actions: List<VkCatalogButton>? = null,
    val animated_icon_url: String? = null,
    val data_type: String? = null,
    val icon: String? = null,
    val icon_url: String? = null,
    val session_id: String? = null,
    val style: String? = null,
    val subsections: List<VkCatalogSection>? = null,
    val url: String? = null,
)

@JsonClass(generateAdapter = true)
data class VkCatalogBlock(
    val id: String = "",
    val data_type: String = "",
    /** Context propagated back to catalog.getBlockItems as optional `ref`. */
    val ref: String? = null,
    val layout: VkCatalogLayout? = null,
    val actions: List<VkCatalogButton>? = null,
    val next_from: String? = null,
    val albums_ids: List<String>? = null,
    val audios_ids: List<String>? = null,
    val playlists_ids: List<String>? = null,
    val artists_ids: List<String>? = null,
    val artist_videos_ids: List<String>? = null,
    val videos_ids: List<String>? = null,
    val links_ids: List<String>? = null,
    val catalog_banner_ids: List<String>? = null,
    val curators_ids: List<String>? = null,
    val concerts_ids: List<String>? = null,
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
    val radio_stations_ids: List<Int>? = null,
    val audio_stream_mixes_ids: List<String>? = null,
    val catalog_users_ids: List<String>? = null,
    val navigation_tab_ids: List<String>? = null,
    val owner_ids: List<String>? = null,
    val search_suggestions_ids: List<String>? = null,
    val thumbs_ids: List<String>? = null,
    val items_count: Int? = null,
    val meta: VkCatalogBlockMeta? = null,
    val subsection_id: String? = null,
    val subtype: String? = null,
    val title: String? = null,
    val track_code: String? = null,
    val url: String? = null,
    /** The official wire key is singular `_id`, but its value is an array. */
    val audio_signal_common_info_id: List<String>? = null,
)

@JsonClass(generateAdapter = true)
data class VkCatalogButtonAction(
    val type: String? = null,
    val style: String? = null,
    val action_title: String? = null,
    val consume_reason: String? = null,
    val block_id: String? = null,
    val section_id: String? = null,
    val title: String? = null,
    val hint_id: String? = null,
    val target: String? = null,
    val url: String? = null,
)

@JsonClass(generateAdapter = true)
data class VkCatalogButton(
    /** Official CatalogKit keeps the discriminator in `action.type`. */
    val action: VkCatalogButtonAction? = null,
    /** Compatibility with already flattened responses from VK-derived backends. */
    val type: String? = null,
    val section_id: String? = null,
    val title: String? = null,
    val block_id: String? = null,
    val options: List<VkCatalogReplacementOption>? = null,
    /** Official `play_vk_mix` action payload. */
    val hint_id: String? = null,
    val ref_layout_name: String? = null,
    val images: List<VkArtistPhoto>? = null,
    val foreground_images: List<VkArtistPhoto>? = null,
    val entity_id: String? = null,
    val id: String? = null,
    val mix_id: String? = null,
    val mix_options: String? = null,
    val target: String? = null,
    val click_event_type: String? = null,
    val description: String? = null,
    val style: String? = null,
    /** Compatibility with flattened CatalogButton action payloads. */
    val consume_reason: String? = null,
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
    val custom_style: String? = null,
    val grid_layout: List<List<String>>? = null,
    val icon: String? = null,
    val infinite_repeat: Boolean? = null,
    val items_ignorable: Int? = null,
    val merge_items: Boolean? = null,
    val size: String? = null,
    val type: String? = null,
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
    val top_title: VkCatalogLayoutTopTitle? = null,
)

@JsonClass(generateAdapter = true)
data class VkCatalogLayoutTopTitle(
    val icon: String? = null,
    val text: String? = null,
)

@JsonClass(generateAdapter = true)
data class VkCatalogBlockMeta(
    val campaign_name: String? = null,
    val disable_track_rec_shown: Boolean? = null,
    val no_consecutive_play: Boolean? = null,
    val show_all_info: VkCatalogShowAllInfo? = null,
    val uxpoll_trigger: String? = null,
)

@JsonClass(generateAdapter = true)
data class VkCatalogShowAllInfo(
    val section_id: String? = null,
    val title: String? = null,
)

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
