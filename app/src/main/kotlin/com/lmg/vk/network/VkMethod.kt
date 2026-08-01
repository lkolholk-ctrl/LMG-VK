package com.lmg.vk.network

/**
 * Восстановлено из `defpackage.C5577e` (obf).
 *
 * Обёртка над вызовом VK API: имя метода, версия, параметры и парсер ответа.
 * Создаётся как `VkMethod("apps.get", parser)`, параметры добавляются цепочкой,
 * выполняется через `VkApiClient.execute(method)`.
 *
 * Контракт из jadx:
 *  - apiVersion по умолчанию "5.272"
 *  - param(String, String?) — null значения не добавляются
 *  - boolean сериализуется как "1"/"0"
 */
class VkMethod<T>(
    val name: String,
    val parser: VkResponseParser<T>,
) {
    /** Версия API ("v" в form-body). */
    var apiVersion: String = "5.272"

    /** Точный транспорт VK: обычный API, API OAuth или выделенный OAuth-хост. */
    var endpoint: VkEndpoint = VkEndpoint.API_METHOD

    /** Большинство VK API-методов — POST; `/token` официального клиента — GET. */
    var httpMethod: VkHttpMethod = VkHttpMethod.POST

    /** User-Agent конкретной ветки официального клиента VK. */
    var userAgent: String? = null

    /** Флаг one-shot из `C5577e.appmetrica`: такой вызов не имеет тела ответа. */
    var isOneShot: Boolean = false

    /** Внутренний флаг: уже пробовали refresh token после ошибки 1117. */
    internal var tokenRefreshRetried: Boolean = false

    val params: LinkedHashMap<String, String> = LinkedHashMap()

    fun param(name: String, value: String?) {
        if (value != null) params[name] = value
    }

    fun param(name: String, value: Boolean) {
        param(name, if (value) "1" else "0")
    }

    fun param(name: String, value: Long) {
        param(name, value.toString())
    }

    fun param(name: String, value: Int) {
        param(name, value.toString())
    }
}

enum class VkEndpoint {
    /** `https://api.<domain>/method/<name>` */
    API_METHOD,

    /** `https://api.<domain>/oauth/<name>` */
    API_OAUTH,

    /** `https://oauth.<domain>/<name>` */
    OAUTH,
}

enum class VkHttpMethod { GET, POST }
