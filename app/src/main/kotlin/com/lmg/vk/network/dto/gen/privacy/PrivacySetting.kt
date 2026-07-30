package com.lmg.vk.network.dto.gen.privacy

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Auto-recovered from vkapi2/objects/privacy/PrivacySettingJsonAdapter (3 json keys). */
@JsonClass(generateAdapter = true)
data class PrivacySetting(
    val key: String? = null,
    val title: String? = null,
    val value: PrivacySettingValue? = null,
)
