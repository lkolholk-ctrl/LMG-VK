package com.lmg.vk.network.dto.gen.newsfeed

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Auto-recovered from vkapi2/objects/newsfeed/NewsfeedListJsonAdapter (2 json keys). */
@JsonClass(generateAdapter = true)
data class NewsfeedList(
    val id: Int = 0,
    val title: String? = null,
)
