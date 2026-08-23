package com.lmg.vk.network

import okhttp3.Interceptor
import okhttp3.Response
import java.net.HttpURLConnection
import java.util.Locale

internal object VkRequestIdentity {
    private val domains = arrayOf(
        "userapi.com",
        "vk-cdn.net",
        "vk.com",
        "vk.ru",
        "vkuser.net",
        "vkuseraudio.com",
        "vkuseraudio.net",
        "vkuserlive.com",
        "vkuserlive.net",
        "vkuservideo.com",
        "vkuservideo.net",
    )

    fun isVkHost(host: String): Boolean {
        val normalized = host.trimEnd('.').lowercase(Locale.US)
        return domains.any { domain ->
            normalized == domain || normalized.endsWith(".$domain")
        }
    }
}

internal class VkRequestIdentityInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!VkRequestIdentity.isVkHost(request.url.host) || request.header("User-Agent") != null) {
            return chain.proceed(request)
        }
        return chain.proceed(
            request.newBuilder()
                .header("User-Agent", VkUserAgents.api)
                .build(),
        )
    }
}

internal fun HttpURLConnection.applyVkRequestIdentity(): HttpURLConnection {
    if (VkRequestIdentity.isVkHost(url.host)) {
        setRequestProperty("User-Agent", VkUserAgents.api)
    }
    return this
}
