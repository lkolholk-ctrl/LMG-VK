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

    /** Идти на oauth-хост (path /oauth/<name> вместо /method/<name>). */
    var useOAuth: Boolean = false

    /**
     * "Обычный" метод с контентом. В execute() есть проверка:
     * !isContentMethod -> Error(993, "BH.VkApi - One-Shot methods have no content")
     */
    var isContentMethod: Boolean = false

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
