package com.lmg.vk.network.dto.music

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Модели ленты сниппетов (`audio.getSnippets`).
 *
 * Отдельный файл, а не правка `MusicMisc.kt`, чтобы не конфликтовать с чужими
 * батчами: `AudioSnippetEntry` там трогают и другие агенты.
 *
 * ══════════════════════════════════════════════════════════════════════
 * ВАЖНО про «границы обрезки» (`clip_from`/`clip_to`)
 * ══════════════════════════════════════════════════════════════════════
 * В задаче предполагалось, что эти поля есть у сниппета `audio.getSnippets`.
 * Проверка по реверсу VK X это НЕ подтверждает, и добавлять их в
 * [AudioSnippetEntry] было бы выдумкой (нарушение «всё должно быть реальное»).
 *
 * Что реально в VK X:
 *  - Адаптер `AudioSnippetEntryJsonAdapter` знает РОВНО 8 полей:
 *    `type`, `title`, `text`, `nav_url`, `image`, `track_code`, `audios`,
 *    `audio_ids`. Ни `clip_from`, ни `clip_to` там нет — наш DTO уже полный.
 *  - `clip_from`/`clip_to` живут в ДРУГОЙ модели —
 *    `AudioPlaylistSnippetEntry.StreamUrl` (у нас это
 *    `network/dto/gen/music/StreamUrl.kt`). Она приходит не из
 *    `audio.getSnippets`, а из execute-запроса «микса плейлиста»
 *    (`C11459e` + `audio.get`, сборка в `C5814e`).
 *  - В `C5814e` VK X эти границы НЕ отдаёт плееру как точки обрезки: он считает
 *    `(clip_to - clip_from) / 1000` и кладёт результат в поле `stream_duration`
 *    трека, подменяя `url` на укороченный поток. То есть фрагмент режет СЕРВЕР,
 *    а клиент лишь знает его длительность.
 *  - Поиск по всему деобфусцированному VK X не дал ни одного
 *    `ClippingConfiguration` / `ClippingMediaSource`: media3 у них не обрезает
 *    ничего. Границы приходят уже применёнными к `url`.
 *
 * Вывод: для `audio.getSnippets` источник длительности фрагмента — поле
 * `stream_duration` у `AudioTrack` (оно у нас уже есть), а URL от VK уже
 * обрезан. Поэтому DTO сниппета расширять НЕЧЕМ и не нужно; ниже — только
 * доменные хелперы поверх существующих полей.
 */

/**
 * Один трек ленты, приведённый к тому, что нужно экрану.
 *
 * Длительность фрагмента отделена от длительности трека: `stream_duration`
 * (секунды) — это ровно тот отрезок, который VK отдаёт в снippet-URL, и он
 * может быть в разы короче `duration`. Прогресс-бар обязан считаться по
 * фрагменту, иначе полоса почти не движется.
 */
data class SnippetTrackUi(
    val fullId: String,
    val title: String,
    val artist: String,
    val coverUrl: String?,
    val directUrl: String,
    /** Полная длительность трека, мс. */
    val fullDurationMs: Long,
    /** Длительность ФРАГМЕНТА, мс. 0 — VK не прислал, играем сколько дадут. */
    val snippetDurationMs: Long,
    val isExplicit: Boolean,
    val trackCode: String,
) {
    /** По какой длительности рисовать прогресс: фрагмент важнее полной. */
    val effectiveDurationMs: Long
        get() = if (snippetDurationMs > 0L) snippetDurationMs else fullDurationMs

    /** Реально ли VK урезал поток (влияет на подпись «фрагмент N с»). */
    val isClipped: Boolean
        get() = snippetDurationMs > 0L && snippetDurationMs < fullDurationMs
}

/** Одна «страница» вертикального фида: подводка VK + её треки. */
data class SnippetPageUi(
    val key: String,
    val title: String,
    val text: String,
    val imageUrl: String?,
    val navUrl: String?,
    val trackCode: String,
    val tracks: List<SnippetTrackUi>,
)

/**
 * Границы обрезки из «микса плейлиста» VK X. Отдельный DTO с ЯВНЫМ именем,
 * чтобы никто не спутал его с полями `audio.getSnippets`.
 *
 * Не используется лентой сниппетов — оставлен как честная точка расширения на
 * случай порта микса плейлиста (`C11459e`/`C5814e`), где `stream_url` реально
 * приходит. Дублировать `gen/music/StreamUrl.kt` не стал.
 */
@JsonClass(generateAdapter = true)
data class AudioPlaylistSnippetEntryDto(
    val track: AudioTrack? = null,
    @Json(name = "stream_url") val streamUrl: PlaylistSnippetStreamUrl? = null,
)

/** `AudioPlaylistSnippetEntry.StreamUrl` — url + границы фрагмента в мс. */
@JsonClass(generateAdapter = true)
data class PlaylistSnippetStreamUrl(
    val url: String? = null,
    @Json(name = "clip_from") val clipFrom: Int = 0,
    @Json(name = "clip_to") val clipTo: Int = 0,
) {
    /**
     * Длительность фрагмента в СЕКУНДАХ — ровно та арифметика, что в VK X
     * (`C5814e`: `(clip_to - clip_from) / 1000`), результат кладётся в
     * `stream_duration` трека.
     */
    val clipDurationSeconds: Int
        get() = ((clipTo - clipFrom) / 1000).coerceAtLeast(0)
}
