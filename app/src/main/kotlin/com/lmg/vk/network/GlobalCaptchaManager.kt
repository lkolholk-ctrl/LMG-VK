package com.lmg.vk.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Описание запроса капчи от VK API (Error 14).
 */
data class CaptchaPrompt(
    val imageUrl: String,
    val captchaSid: String?,
    val captchaTs: Double?,
    val captchaAttempt: Int?,
    val deferred: CompletableDeferred<String?>,
)

/**
 * Описание запроса интерактивной SmartCaptcha/валидации (Error 17: Validation Required).
 */
data class ValidationPrompt(
    val redirectUri: String,
    val deferred: CompletableDeferred<Map<String, String>?>,
)

/**
 * GlobalCaptchaManager — глобальный обработчик капчи и валидаций безопасности VK.
 *
 * Перехватывает:
 * - Error 14 (Captcha required): буквенно-цифровая капча
 * - Error 17 (Validation required): интерактивная SmartCaptcha, чекбокс «Я не робот»,
 *   проверка безопасности или подтверждение аккаунта
 *
 * Бесшовно повторяет исходный API-вызов после успешного прохождения проверки.
 */
object GlobalCaptchaManager {

    private val _activePrompt = MutableStateFlow<CaptchaPrompt?>(null)
    val activePrompt: StateFlow<CaptchaPrompt?> = _activePrompt

    private val _activeValidation = MutableStateFlow<ValidationPrompt?>(null)
    val activeValidation: StateFlow<ValidationPrompt?> = _activeValidation

    suspend fun requestCaptcha(
        imageUrl: String,
        captchaSid: String?,
        captchaTs: Double?,
        captchaAttempt: Int?,
    ): Map<String, String>? {
        val deferred = CompletableDeferred<String?>()
        _activePrompt.value = CaptchaPrompt(
            imageUrl = imageUrl,
            captchaSid = captchaSid,
            captchaTs = captchaTs,
            captchaAttempt = captchaAttempt,
            deferred = deferred,
        )
        val key = try {
            deferred.await()
        } finally {
            if (_activePrompt.value?.deferred == deferred) {
                _activePrompt.value = null
            }
        }

        return if (!key.isNullOrBlank()) {
            buildMap {
                if (!captchaSid.isNullOrBlank()) put("captcha_sid", captchaSid)
                captchaTs?.let { put("captcha_ts", it.toString()) }
                captchaAttempt?.let { put("captcha_attempt", it.toString()) }
                put("captcha_key", key)
            }
        } else null
    }

    fun submit(key: String) {
        _activePrompt.value?.deferred?.complete(key)
    }

    fun dismiss() {
        _activePrompt.value?.deferred?.complete(null)
        _activePrompt.value = null
    }

    suspend fun requestValidation(redirectUri: String): Map<String, String>? {
        val deferred = CompletableDeferred<Map<String, String>?>()
        _activeValidation.value = ValidationPrompt(redirectUri, deferred)
        val result = try {
            deferred.await()
        } finally {
            if (_activeValidation.value?.deferred == deferred) {
                _activeValidation.value = null
            }
        }
        return result
    }

    fun submitValidation(extraParams: Map<String, String> = emptyMap()) {
        _activeValidation.value?.deferred?.complete(extraParams)
        _activeValidation.value = null
    }

    fun dismissValidation() {
        _activeValidation.value?.deferred?.complete(null)
        _activeValidation.value = null
    }
}
