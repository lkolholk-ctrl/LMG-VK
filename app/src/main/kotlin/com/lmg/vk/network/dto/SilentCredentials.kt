package com.lmg.vk.network.dto

import com.squareup.moshi.JsonClass

/** Ответ `auth.getCredentialsForService`, восстановленный из SilentCreds. */
@JsonClass(generateAdapter = true)
data class SilentCredentials(
    val token: String? = null,
    val first_name: String? = null,
    val last_name: String? = null,
    val ttl: Int? = null,
    val photo_50: String? = null,
    val photo_100: String? = null,
    val photo_200: String? = null,
    val phone: String? = null,
    val weight: Int? = null,
    val user_hash: String? = null,
    val app_service_id: Int? = null,
)
