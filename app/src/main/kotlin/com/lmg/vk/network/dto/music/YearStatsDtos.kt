package com.lmg.vk.network.dto.music

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Wire-DTO «Итогов года» ВКонтакте (порт VK X 8.12.1, см.
 * `docs/vkx-port/04-periphery.md` §4 — `studio.getArtistYearRecapData`,
 * §5 — `musicStatResults.getMetrics`, §6 — `musicStatResults.createPlaylist`).
 *
 * Имена JSON-полей взяты из kotlinx-дескрипторов оригинала, а не угаданы: они
 * приходят от VK как есть, поэтому переименовывать их нельзя.
 *
 * ОТСТУПЛЕНИЕ ОТ VK X (осознанное). В оригинале обязательные поля описаны
 * маской kotlinx и падают при отсутствии, а `type`/`color_type` — enum, который
 * бросает исключение на незнакомом значении. Здесь:
 *  - обязательные строки/списки имеют дефолты, а не kotlinx-маску;
 *  - `type` и `color_type` — строки, а не enum.
 * Причина: ответ живой и «Итоги года» — сезонная серверная фича. Один новый тип
 * блока или отсутствующий тултип не должны ронять весь экран в ошибку. Значения
 * при этом НЕ выдумываются: пустое поле остаётся пустым, и UI показывает
 * честный текст «нет данных» вместо придуманного числа.
 * Известные значения `type` перечислены в [YearStatsBlockTypes] — они нужны для
 * раскладки, а не для валидации.
 *
 * Nullable там, где VK может прислать явный `null`: Moshi роняет разбор, если в
 * non-nullable поле пришёл `null`, а дефолт спасает только от отсутствия ключа.
 */

// ─────────────────── §4: studio.getArtistYearRecapData ───────────────────

@JsonClass(generateAdapter = true)
data class AudioGetAnnualResultBlocksDto(
    @Json(name = "blocks") val blocks: List<AudioGetAnnualResultBlockDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class AudioGetAnnualResultBlockDto(
    @Json(name = "name") val name: String? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "order") val order: Int = 0,
    @Json(name = "is_visible") val isVisible: Boolean = false,
    @Json(name = "is_sharing_enabled") val isSharingEnabled: Boolean = false,
    @Json(name = "background_url") val backgroundUrl: String? = null,
    @Json(name = "story_bg") val storyBg: String? = null,
    @Json(name = "fallback_background_url") val fallbackBackgroundUrl: String? = null,
    @Json(name = "audio_preview_url") val audioPreviewUrl: String? = null,
    @Json(name = "titles") val titles: List<AnnualResultValue> = emptyList(),
    @Json(name = "subtitles") val subtitles: List<AnnualResultValue> = emptyList(),
    @Json(name = "metrics") val metrics: List<AnnualResultValue> = emptyList(),
    @Json(name = "photo_urls") val photoUrls: List<String> = emptyList(),
    @Json(name = "playlist_photo_url") val playlistPhotoUrl: String? = null,
    @Json(name = "playlist_title") val playlistTitle: String? = null,
    @Json(name = "playlist_audio_raw_ids") val playlistAudioRawIds: List<String> = emptyList(),
    @Json(name = "screen_caption") val screenCaption: String? = null,
    @Json(name = "screen_title") val screenTitle: String? = null,
    @Json(name = "screen_subtitle") val screenSubtitle: String? = null,
    @Json(name = "artist") val artist: AnnualResultValue? = null,
)

/** Оригинальное имя — вложенный `AudioGetAnnualResultBlockDto.Value`. */
@JsonClass(generateAdapter = true)
data class AnnualResultValue(
    @Json(name = "title") val title: String? = null,
    @Json(name = "subtitle") val subtitle: String? = null,
    @Json(name = "caption") val caption: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "value") val value: String? = null,
    @Json(name = "photo_url") val photoUrl: String? = null,
    @Json(name = "photo_urls") val photoUrls: List<String> = emptyList(),
)

// ─────────────────── §5: musicStatResults.getMetrics ───────────────────

@JsonClass(generateAdapter = true)
data class Y25Response(
    @Json(name = "audio_tooltip") val audioTooltip: String? = null,
    @Json(name = "blocks") val blocks: List<Y25CBlock> = emptyList(),
    @Json(name = "actions") val actions: List<Y25Action> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class Y25CBlock(
    @Json(name = "type") val type: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "is_visible") val isVisible: Boolean = false,
    @Json(name = "order") val order: Int = 0,
    @Json(name = "titles") val titles: List<Y25Title> = emptyList(),
    @Json(name = "subtitles") val subtitles: List<Y25Title> = emptyList(),
    @Json(name = "photo_urls") val photoUrls: List<String> = emptyList(),
    @Json(name = "background") val background: Y25Background? = null,
    @Json(name = "is_sharing_enabled") val isSharingEnabled: Boolean = false,
    @Json(name = "audio_preview_url") val audioPreviewUrl: String? = null,
    @Json(name = "metrics") val metrics: List<Y25Title> = emptyList(),
    @Json(name = "color_type") val colorType: String? = null,
    @Json(name = "playlist") val playlist: Y25Playlist? = null,
)

@JsonClass(generateAdapter = true)
data class Y25Title(
    @Json(name = "title") val title: String? = null,
    @Json(name = "value") val value: String? = null,
    @Json(name = "caption") val caption: String? = null,
    @Json(name = "resource") val resource: String? = null,
    @Json(name = "content") val content: Y25Content? = null,
)

@JsonClass(generateAdapter = true)
data class Y25Content(
    @Json(name = "cover") val coverUrl: String? = null,
    @Json(name = "video") val video: List<Y25ContentVideoType> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class Y25ContentVideoType(
    @Json(name = "name") val name: String? = null,
    @Json(name = "link") val link: String? = null,
)

@JsonClass(generateAdapter = true)
data class Y25Background(
    @Json(name = "desktop") val desktop: Y25Content? = null,
    @Json(name = "mobile") val mobile: Y25Content? = null,
    @Json(name = "story") val story: Y25Content? = null,
    @Json(name = "post") val post: Y25Content? = null,
)

@JsonClass(generateAdapter = true)
data class Y25Playlist(
    @Json(name = "title") val title: String? = null,
    @Json(name = "id") val id: Long = 0L,
    @Json(name = "photo_url") val photoUrl: String? = null,
)

/**
 * Действие из ответа метрик. Kotlin-свойство второго поля в оригинале названо
 * `mobile`, хотя JSON-ключ — `type` (`FRESH C13196e.java:20-28`); здесь имя
 * приведено к JSON-ключу, чтобы не тащить чужую опечатку.
 *
 * Полный перечень значений `type` в исходниках VK X не найден, поэтому строка
 * НЕ интерпретируется как команда — см. YearRecapScreen.
 */
@JsonClass(generateAdapter = true)
data class Y25Action(
    @Json(name = "title") val title: String? = null,
    @Json(name = "type") val type: String? = null,
)

// ─────────────────── §6: musicStatResults.createPlaylist ───────────────────

@JsonClass(generateAdapter = true)
data class Y25PlaylistCreateAction(
    @Json(name = "status") val status: String? = null,
    @Json(name = "id") val id: Int = 0,
)

/** Значение `status`, при котором VK X продолжает опрос (`FRESH C4673e.java:256`). */
const val Y25_STATUS_PENDING = "pending"

/**
 * Значение, которое пишется в `storage` VK X после создания плейлиста
 * (`§2.2`: `annual_result_2025_created_playlists_id` = `{"id": <id>}`).
 */
@JsonClass(generateAdapter = true)
data class Y25JsonStorageValue(
    @Json(name = "id") val id: Int,
)

/**
 * Известные значения `type` блока. Перечисление дословно из VK X: для метрик —
 * `FRESH C18420e.java:39`, для итогов артиста — строки switch в
 * `FRESH C4271e.java:47-125`. Используется только для выбора раскладки.
 */
object YearStatsBlockTypes {
    // musicStatResults.getMetrics
    const val BASE = "base"
    const val WELCOME = "welcome"
    const val NUMBER = "number"
    const val TOP = "top"
    const val SUMMARY = "summary"
    const val BASE_EXT = "base_ext"
    const val ACHIEVEMENT = "achievement"
    const val PLAYLIST = "playlist"
    const val PLACEHOLDER = "placeholder"
    const val VIDEO = "video"
    const val TOP_ARTIST = "top_artist"

    // studio.getArtistYearRecapData — свой набор строк
    const val MULTI_IMAGES = "multi_images"
    const val EXTENDED = "extended"
    const val FINAL = "final"
}
