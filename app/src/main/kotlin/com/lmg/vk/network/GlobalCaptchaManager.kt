package com.lmg.vk.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Описание запроса капчи от VK API.
 */
data class CaptchaPrompt(
    val imageUrl: String,
    val captchaSid: String?,
    val deferred: CompletableDeferred<String?>,
)

/**
 * GlobalCaptchaManager — глобальный обработчик капчи (VK Error 14: Captcha required).
 *
 * Перехватывает ошибки капчи из любого сетевого запроса приложения (поиск, добавление
 * треков, альбомы, плейлисты), отображает глобальный стеклянный диалог ввода капчи
 * и прозрачно повторяет исходный API-вызов с полученным ключом.
 */
object GlobalCaptchaManager {

    private val _activePrompt = MutableStateFlow<CaptchaPrompt?>(null)
    val activePrompt: StateFlow<CaptchaPrompt?> = _activePrompt

    suspend fun requestCaptcha(imageUrl: String, captchaSid: String?): Map<String, String>? {
        val deferred = CompletableDeferred<String?>()
        _activePrompt.value = CaptchaPrompt(imageUrl, captchaSid, deferred)
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
}
