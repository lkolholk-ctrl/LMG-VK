package com.lmg.vk.network.dto.music

import com.squareup.moshi.JsonClass

/** Из `ua.lmg.vkapi2.objects.music.AudioLyrics` + `AudioLyricTimestamp`. */
@JsonClass(generateAdapter = true)
data class AudioLyrics(
    val timestamps: List<AudioLyricTimestamp>? = null,
    val text: List<String>? = null,
    val language: String? = null,
) {
    val isSynced: Boolean get() = !timestamps.isNullOrEmpty()
}

@JsonClass(generateAdapter = true)
data class AudioLyricTimestamp(
    val begin: Long,
    val end: Long,
    val line: String,
    val interlude: Boolean = false,
)

/** Из `ua.lmg.vkapi2.objects.music.Genre`. */
@JsonClass(generateAdapter = true)
data class Genre(
    val id: Int,
    val name: String,
)

/** Из `ua.lmg.vkapi2.objects.music.restriction.MusicDynamicRestriction`. */
@JsonClass(generateAdapter = true)
data class MusicDynamicRestriction(
    val title: String? = null,
    val text: String? = null,
    /**
     * В разных ответах VK элементы приходят и строками, и объектами изображения.
     * Поле сейчас только сохраняется вместе с restriction и UI его не читает,
     * поэтому не сужаем серверный union до одного формата: иначе один новый
     * объект иконки роняет разбор всей подборки/страницы артиста.
     */
    val icons: List<Any?>? = null,
)

/** Из `ua.lmg.vkapi2.objects.radio.RadioStation`. */
@JsonClass(generateAdapter = true)
data class RadioStation(
    val id: Int,
    val name: String,
    val logo_url: String? = null,
    val logo_png_url: String? = null,
    val background_color: String? = null,
    val is_followed: Boolean = false,
    val is_enabled: Boolean? = null,
    val stream_url: String? = null,
) {
    /** liked — рантайм-поле (в адаптере вычисляется, не из JSON). */
    @Transient
    var liked: Boolean = false
}

/** Из `ua.lmg.vkapi2.objects.music.AudioSnippetEntry`. */
@JsonClass(generateAdapter = true)
data class AudioSnippetEntry(
    val type: String? = null,
    val title: String? = null,
    val text: String? = null,
    val nav_url: String? = null,
    val image: String? = null,
    val track_code: String? = null,
    val audios: List<AudioTrack>? = null,
    val audio_ids: List<String>? = null,
)
