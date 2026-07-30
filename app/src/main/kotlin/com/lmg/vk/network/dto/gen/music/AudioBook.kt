package com.lmg.vk.network.dto.gen.music

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Auto-recovered from vkapi2/objects/music/AudioBookJsonAdapter (19 json keys). */
@JsonClass(generateAdapter = true)
data class AudioBook(
    val id: Int = 0,
    val publisher: Link? = null,
    val narrators: List<Any?>,
    val translators: List<Any?>,
    val genres: List<Any?>,
    val authors: List<Any?>,
    val code: String? = null,
    val title: String? = null,
    val duration: Int = 0,
    @Json(name = "minimum_age") val minimumAge: Int = 0,
    @Json(name = "is_explicit") val isExplicit: Boolean = false,
    @Json(name = "in_favorites") val inFavorites: Boolean = false,
    @Json(name = "progress_percentage") val progressPercentage: Int = 0,
    @Json(name = "release_date") val releaseDate: Int = 0,
    val copyright: String? = null,
    @Json(name = "access_status") val accessStatus: String? = null,
    val cover: List<Any?>,
    val chapters: List<Any?>,
    @Json(name = "track_code") val trackCode: String? = null,
)
