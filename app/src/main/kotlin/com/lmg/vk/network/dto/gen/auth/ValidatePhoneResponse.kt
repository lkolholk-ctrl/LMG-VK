package com.lmg.vk.network.dto.gen.auth

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Auto-recovered from vkapi2/objects/auth/ValidatePhoneResponseJsonAdapter (9 json keys). */
@JsonClass(generateAdapter = true)
data class ValidatePhoneResponse(
    @Json(name = "next_sid") val nextSid: String? = null,
    @Json(name = "validation_type") val validationType: String? = null,
    @Json(name = "validation_resend") val validationResend: String? = null,
    val delay: Int = 0,
    @Json(name = "external_id") val externalId: String? = null,
    val phone: String? = null,
    @Json(name = "masked_email") val maskedEmail: String? = null,
    @Json(name = "code_length") val codeLength: Int = 0,
    @Json(name = "device_name") val deviceName: String? = null,
)
