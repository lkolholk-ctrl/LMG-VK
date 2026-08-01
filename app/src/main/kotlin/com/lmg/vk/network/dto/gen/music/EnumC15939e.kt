package com.lmg.vk.network.dto.gen.music

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Из `defpackage.EnumC15939e` (AudioContentCard.entity_type):
 * PODCASTS("podcasts"), AUDIOBOOKS("audiobooks").
 */
@JsonClass(generateAdapter = false)
enum class EnumC15939e {
    @Json(name = "podcasts") PODCASTS,
    @Json(name = "audiobooks") AUDIOBOOKS,
}
