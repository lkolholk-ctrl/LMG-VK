package com.lmg.vk.network

import com.lmg.vk.network.dto.VKError
import com.lmg.vk.network.dto.VKResponse

/**
 * Восстановлено из `defpackage.InterfaceC11962e` (`mo600this`).
 *
 * Парсер сырого HTTP-ответа в типизированный результат конкретного метода.
 * Реализации — Moshi-адаптеры соответствующих DTO (vkapi2/methods/ *).
 */
fun interface VkResponseParser<T> {
    suspend fun parse(raw: RawHttpResponse): VkParsedResponse<T>
}

/ ** Сырой HTTP-ответ (обёртка над Ktor HttpResponse — `AbstractC16824e`). */
interface RawHttpResponse {
    val statusCode: Int
    val url: String
    suspend fun bodyText(): String
}

/ **
 * Результат разбора конверта VK: данные либо ошибка.
 * (в jadx: `C11464e` — { ad: data, vip: VKError })
 */
data class VkParsedResponse<T>(
    val data: T?,
    val error: VKError?,
)

/ ** Базовый парсер конверта VKResponse<T> через Moshi. */
class MoshiVkResponseParser<T>(
    private val decode: (String) -> VKResponse<T>,
) : VkResponseParser<T> {
    override suspend fun parse(raw: RawHttpResponse): VkParsedResponse<T> {
        val envelope = decode(raw.bodyText())
        return VkParsedResponse(envelope.response, envelope.error)
    }
}
