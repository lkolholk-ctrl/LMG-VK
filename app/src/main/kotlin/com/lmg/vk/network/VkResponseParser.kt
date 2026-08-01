package com.lmg.vk.network

import com.lmg.vk.network.dto.VKError
import com.lmg.vk.network.dto.VKResponse
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.lang.reflect.Type

/**
 * Восстановлено из `defpackage.InterfaceC11962e` (`mo600this`).
 *
 * Парсер сырого HTTP-ответа в типизированный результат конкретного метода.
 * Реализации — Moshi-адаптеры соответствующих DTO (vkapi2/methods).
 */
fun interface VkResponseParser<T> {
    suspend fun parse(raw: RawHttpResponse): VkParsedResponse<T>
}

/** Сырой HTTP-ответ (обёртка над Ktor HttpResponse — `AbstractC16824e`). */
interface RawHttpResponse {
    val statusCode: Int
    val url: String
    suspend fun bodyText(): String
}

/**
 * Результат разбора конверта VK: данные либо ошибка.
 * (в jadx: `C11464e` — { ad: data, vip: VKError })
 */
data class VkParsedResponse<T>(
    val data: T?,
    val error: VKError?,
)

/** Базовый парсер конверта VKResponse<T> через Moshi. */
class MoshiVkResponseParser<T>(
    private val decode: (String) -> VKResponse<T>,
) : VkResponseParser<T> {
    override suspend fun parse(raw: RawHttpResponse): VkParsedResponse<T> {
        val envelope = decode(raw.bodyText())
        return VkParsedResponse(envelope.response, envelope.error)
    }
}

/** Единый Moshi экземпляр для восстановленного VK API. */
object VkJson {
    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
}

/** Стандартный ответ списочных методов VK: `{ "count": n, "items": [...] }`. */
@JsonClass(generateAdapter = true)
data class VkItems<T>(
    val count: Int? = null,
    val items: List<T> = emptyList(),
)

/**
 * Типобезопасный разбор стандартного конверта `{response, error}`.
 * [responseType] обязан описывать именно содержимое поля `response`.
 */
class MoshiEnvelopeParser<T>(
    responseType: Type,
    moshi: Moshi = VkJson.moshi,
) : VkResponseParser<T> {
    private val adapter: JsonAdapter<VKResponse<T>> = moshi.adapter<VKResponse<T>>(
        Types.newParameterizedType(VKResponse::class.java, responseType),
    )

    override suspend fun parse(raw: RawHttpResponse): VkParsedResponse<T> {
        val envelope = requireNotNull(adapter.fromJson(raw.bodyText())) {
            "Empty VK response from ${raw.url}"
        }
        return VkParsedResponse(envelope.response, envelope.error)
    }
}

/**
 * Парсер OAuth-ответов без VK-конверта `{response: ...}`.
 * Такие ответы возвращают данные непосредственно в корне JSON.
 */
class MoshiDirectParser<T>(
    type: Type,
    moshi: Moshi = VkJson.moshi,
) : VkResponseParser<T> {
    private val adapter: JsonAdapter<T> = moshi.adapter(type)

    override suspend fun parse(raw: RawHttpResponse): VkParsedResponse<T> {
        val data = requireNotNull(adapter.fromJson(raw.bodyText())) {
            "Empty OAuth response from ${raw.url}"
        }
        return VkParsedResponse(data, null)
    }
}

/** Преобразует успешные данные парсера, не теряя VKError из конверта. */
class MappingVkResponseParser<I, O>(
    private val delegate: VkResponseParser<I>,
    private val transform: (I) -> O,
) : VkResponseParser<O> {
    override suspend fun parse(raw: RawHttpResponse): VkParsedResponse<O> {
        val parsed = delegate.parse(raw)
        return VkParsedResponse(parsed.data?.let(transform), parsed.error)
    }
}
