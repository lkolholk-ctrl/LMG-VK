package com.lmg.vk.network.dto.music

import com.squareup.moshi.JsonClass

/**
 * `audio_chart_info` из `AudioAudioDto` — позиция трека в чарте.
 * Сериализатор `C11752e` (`C11752e.java:9-12,25-27`), оба ключа опциональны.
 *
 * `state` в оригинале — enum `StateDto` с `@SerialName` `"0".."3"`, то есть
 * на проводе это число. Оставлен `Int?`: значения состояний доками не
 * расшифрованы, и придумывать им имена было бы догадкой.
 */
@JsonClass(generateAdapter = true)
data class AudioChartInfoDto(
    val position: Int? = null,
    val state: Int? = null,
)
