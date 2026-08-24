package com.lmg.vk.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Восстановлено из `ua.lmg.vkapi2.internal.objects.VKError`
 * (имена — по Kotlin Metadata, JSON-ключи — по @Json аннотациям).
 *
 * Тело объекта "error" в конверте ответа VK API.
 */
@JsonClass(generateAdapter = true)
data class VKError(
    val error_code: Int,
    val error_msg: String,
    val method: String? = null,
    val request_params: List<VKRequestParameter>? = null,
    @Json(name = "captcha_sid") val captchaSid: String? = null,
    @Json(name = "captcha_img") val captchaImg: String? = null,
    @Json(name = "captcha_ts") val captchaTs: Double? = null,
    @Json(name = "captcha_ratio") val captchaRatio: Double? = null,
    @Json(name = "captcha_attempt") val captchaAttempt: Int? = null,
    @Json(name = "redirect_uri") val redirectUri: String? = null,
)

/** Восстановлено из `ua.lmg.vkapi2.internal.objects.VKRequestParameter`. */
@JsonClass(generateAdapter = true)
data class VKRequestParameter(
    val key: String,
    val value: String,
)

/**
 * Восстановлено из `ua.lmg.vkapi2.internal.objects.VKResponse`.
 * Конверт ответа VK API: { "response": ..., "error": {...}, "execute_errors": [...] }.
 */
@JsonClass(generateAdapter = true)
data class VKResponse<T>(
    val response: T? = null,
    val error: VKError? = null,
    val execute_errors: List<VKError>? = null,
)

/** Известные коды ошибок (поведение — по `C8221e.license` и `C15802e`). */
object VkErrorCodes {
    /** Капча: возвращаем captcha_sid/ts/attempt + captcha_key и ретраим. */
    const val CAPTCHA_REQUIRED = 14

    /** Требуется валидация (redirect_uri): обработчик собирает параметры, ретрай. */
    const val VALIDATION_REQUIRED = 17

    /** Токен протух/отозван: делаем auth.refreshTokens и один ретрай. */
    const val TOKEN_EXPIRED = 1117

    const val INVALID_SIGNATURE = 4

    const val AUTHORIZATION_FAILED = 5

    const val ACCESS_TOKEN_INVALID = 3610

    const val UNKNOWN_ERROR = 1

    const val INTERNAL_SERVER_ERROR = 10

    const val DATABASE_ERROR = 13

    const val TOO_MANY_REQUESTS = 6

    val TOKEN_REFRESH_REQUIRED = setOf(
        INVALID_SIGNATURE,
        AUTHORIZATION_FAILED,
        ACCESS_TOKEN_INVALID,
        TOKEN_EXPIRED,
    )

    val TRANSIENT = setOf(UNKNOWN_ERROR, INTERNAL_SERVER_ERROR, DATABASE_ERROR)

    /** Внутренний код: one-shot метод без контента. */
    const val NO_CONTENT = 993

    /** Нет прав на метод — в т.ч. закрытые аудиозаписи пользователя. */
    const val ACCESS_DENIED = 15

    /** Профиль приватный: доступ к содержимому запрещён настройками владельца. */
    const val PRIVATE_PROFILE = 30

    /** Доступ к аудио владельца запрещён его настройками приватности. */
    const val AUDIO_ACCESS_DENIED = 201

    /** Нет доступа к сообществу (закрытая или заблокированная группа). */
    const val GROUP_ACCESS_DENIED = 203

    /** Коды, которые для нас означают одно: владелец закрыл свою музыку. */
    val CLOSED_CONTENT = setOf(ACCESS_DENIED, PRIVATE_PROFILE, AUDIO_ACCESS_DENIED, GROUP_ACCESS_DENIED)
}
