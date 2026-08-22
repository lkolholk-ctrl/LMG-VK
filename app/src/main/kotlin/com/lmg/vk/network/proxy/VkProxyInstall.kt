package com.lmg.vk.network.proxy

import com.lmg.vk.network.VkRequestIdentityInterceptor
import okhttp3.OkHttpClient
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager

/**
 * Подключает прокси к OkHttp-движку Ktor: интерцептор подмены хоста плюс TLS,
 * умеющий проверять закреплённые сертификаты.
 *
 * Ставится один раз при сборке клиента и ничего не делает, пока прокси выключен
 * или конфиг не загружен: интерцептор в этом случае просто пропускает запрос, а
 * оба TLS-объекта сводятся к системной проверке (список пинов пуст).
 *
 * Делегат проверки имени хоста берётся у платформы, а не из internal-класса
 * OkHttp: непроксируемые соединения должны проверяться штатно и не зависеть от
 * непубличного API.
 */
internal fun OkHttpClient.Builder.installVkProxy(): OkHttpClient.Builder {
    val pins = { VkProxyRepository.pinnedCertificates() }

    addInterceptor(VkRequestIdentityInterceptor())

    // Редиректы уводим в интерцептор. Иначе OkHttp следовал бы им сам, ниже
    // прикладных интерцепторов, и следующий шаг ушёл бы на домен из `Location`
    // напрямую — мимо подмены, то есть в блокировку. Интерцептор доводит их
    // сам для всех запросов, поэтому поведение не теряется и когда прокси выкл.
    followRedirects(false)
    followSslRedirects(false)

    addInterceptor(
        VkProxyInterceptor(
            enabled = { VkProxyRepository.enabled.value },
            state = { VkProxyRepository.state.value },
        )
    )

    val trustManager = runCatching { VkProxyTrustManager(pins) }.getOrNull() ?: return this
    val sslContext = runCatching {
        SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trustManager), null) }
    }.getOrNull() ?: return this

    sslSocketFactory(sslContext.socketFactory, trustManager)
    hostnameVerifier(
        VkProxyHostnameVerifier(pins, HttpsURLConnection.getDefaultHostnameVerifier())
    )
    return this
}
