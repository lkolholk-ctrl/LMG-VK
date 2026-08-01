package com.lmg.vk.network.dto.gen.auth

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Recovered from C14007e/C18165e (`ValidatePhoneResponse`, 10 json keys). */
@JsonClass(generateAdapter = true)
data class ValidatePhoneResponse(
    @Json(name = "next_sid") val nextSid: String? = null,
    @Json(name = "validation_type") val validationType: ValidationTypeConfirmation? = null,
    @Json(name = "validation_resend") val validationResend: ValidationTypeConfirmation? = null,
    val delay: Int = 120,
    @Json(name = "external_id") val externalId: String? = null,
    val phone: String? = null,
    @Json(name = "phone_mask") val phoneMask: String? = null,
    @Json(name = "masked_email") val maskedEmail: String? = null,
    @Json(name = "code_length") val codeLength: Int = 0,
    @Json(name = "device_name") val deviceName: String? = null,
)

/** `EnumC8519e`, wire-имена подтверждены C8462e. */
enum class ValidationTypeConfirmation {
    @Json(name = "sms")
    Sms,

    @Json(name = "push")
    Push,

    @Json(name = "email")
    Email,

    @Json(name = "callreset")
    CallReset,
}
