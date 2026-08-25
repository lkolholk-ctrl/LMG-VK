package com.lmg.vk.network

import com.lmg.vk.network.dto.VKError
import com.lmg.vk.network.dto.VKResponse
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.json.JSONArray
import org.json.JSONObject
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

    /**
     * Заголовок ответа по имени либо `null`, если его нет.
     *
     * Зачем понадобился. Редиректы у OkHttp выключены и доводятся вручную в
     * [com.lmg.vk.network.proxy.VkProxyInterceptor], поэтому Ktor видит ровно одну
     * пару «запрос-ответ» — ИСХОДНУЮ. Финальный URL редиректа до этого уровня не
     * доходит ни через `url`, ни через `call.request`, и единственный канал снизу
     * вверх — заголовки ответа. Через них OAuth-токен мини-приложения и достаётся
     * (см. [com.lmg.vk.network.methods.VkMiniAppTokenProvider]).
     *
     * Реализация по умолчанию возвращает `null`: интерфейс реализуют снаружи, и
     * добавление абстрактного метода сломало бы такие реализации.
     */
    fun header(name: String): String? = null
}

/**
 * Результат разбора конверта VK: данные либо ошибка.
 * (в jadx: `C11464e` — { ad: data, vip: VKError })
 */
data class VkParsedResponse<T>(
    val data: T?,
    val error: VKError?,
    val executeErrors: List<VKError> = emptyList(),
)

/** Базовый парсер конверта VKResponse<T> через Moshi. */
class MoshiVkResponseParser<T>(
    private val decode: (String) -> VKResponse<T>,
) : VkResponseParser<T> {
    override suspend fun parse(raw: RawHttpResponse): VkParsedResponse<T> {
        val envelope = decode(raw.bodyText())
        return VkParsedResponse(envelope.response, envelope.error, envelope.execute_errors.orEmpty())
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
        return VkParsedResponse(envelope.response, envelope.error, envelope.execute_errors.orEmpty())
    }
}

class TolerantMoshiEnvelopeParser<T>(
    responseType: Type,
    moshi: Moshi = VkJson.moshi,
) : VkResponseParser<T> {
    private val adapter: JsonAdapter<VKResponse<T>> = moshi.adapter<VKResponse<T>>(
        Types.newParameterizedType(VKResponse::class.java, responseType),
    )

    override suspend fun parse(raw: RawHttpResponse): VkParsedResponse<T> {
        val root = JSONObject(raw.bodyText())
        repeat(MAX_SKIPPED_FIELDS) {
            try {
                val envelope = requireNotNull(adapter.fromJson(root.toString())) {
                    "Empty VK response from ${raw.url}"
                }
                return VkParsedResponse(
                    envelope.response,
                    envelope.error,
                    envelope.execute_errors.orEmpty(),
                )
            } catch (error: JsonDataException) {
                val path = error.message
                    ?.substringAfterLast(" at path ", "")
                    ?.takeIf { it.startsWith("$.response.") }
                    ?: throw error
                if (!root.removeValueAt(path)) throw error
                com.lmg.vk.debug.DebugLog.add("VK catalog ignored incompatible field path=$path")
            }
        }
        val envelope = requireNotNull(adapter.fromJson(root.toString())) {
            "Empty VK response from ${raw.url}"
        }
        return VkParsedResponse(envelope.response, envelope.error, envelope.execute_errors.orEmpty())
    }

    private fun JSONObject.removeValueAt(path: String): Boolean {
        val tokens = JSON_PATH_TOKEN.findAll(path).map { match ->
            match.groups[1]?.value ?: match.groups[2]!!.value.toInt()
        }.toList()
        if (tokens.isEmpty()) return false

        var parent: Any = this
        tokens.dropLast(1).forEach { token ->
            parent = when (token) {
                is String -> (parent as? JSONObject)?.opt(token)
                is Int -> (parent as? JSONArray)?.opt(token)
                else -> null
            } ?: return false
        }

        return when (val token = tokens.last()) {
            is String -> (parent as? JSONObject)?.let { objectValue ->
                if (!objectValue.has(token)) false else {
                    objectValue.remove(token)
                    true
                }
            } ?: false
            is Int -> (parent as? JSONArray)?.let { arrayValue ->
                if (token !in 0 until arrayValue.length()) false else {
                    arrayValue.remove(token)
                    true
                }
            } ?: false
            else -> false
        }
    }

    private companion object {
        const val MAX_SKIPPED_FIELDS = 24
        val JSON_PATH_TOKEN = Regex("""\.([A-Za-z0-9_]+)|\[(\d+)]""")
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
        return VkParsedResponse(parsed.data?.let(transform), parsed.error, parsed.executeErrors)
    }
}
