package com.lmg.vk.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Wire-точные DTO, подтверждённые сериализаторами из Priority 3. */

/** `C7862e` / `AnonymTokenResponseDto` (`token`, `expired_at`). */
@JsonClass(generateAdapter = true)
data class AnonymTokenResponse(
    val token: String = "",
    @Json(name = "expired_at") val expiredAt: Int = 0,
)

/** `C0884e` / `EcosystemSendOtpResponseDto`. */
@JsonClass(generateAdapter = true)
data class EcosystemSendOtpResponse(
    val status: Int = 0,
    val sid: String = "",
    @Json(name = "code_length") val codeLength: Int = 0,
    val info: String = "",
)

/** `EnumC6664e`: kotlinx enum с wire-значениями `0` и `1`. */
enum class BaseBoolInt {
    @Json(name = "0")
    NO,

    @Json(name = "1")
    YES,
}

/** `C2610e` / `BaseResultDto`. */
@JsonClass(generateAdapter = true)
data class BaseResult(
    val result: BaseBoolInt,
)
