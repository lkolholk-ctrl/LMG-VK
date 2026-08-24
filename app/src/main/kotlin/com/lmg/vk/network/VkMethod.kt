package com.lmg.vk.network

class VkMethod<T>(
    val name: String,
    val parser: VkResponseParser<T>,
) {
    var apiVersion: String = DEFAULT_API_VERSION

    var endpoint: VkEndpoint = VkEndpoint.API_METHOD

    var httpMethod: VkHttpMethod = VkHttpMethod.POST

    var userAgent: String? = null

    var authorizationToken: String? = null

    var isOneShot: Boolean = false

    internal var tokenRefreshRetried: Boolean = false

    internal var transientRetryCount: Int = 0

    internal var rateLimitRetryCount: Int = 0

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

    companion object {
        const val DEFAULT_API_VERSION = "5.286"
        const val AUTH_API_VERSION = "5.272"
    }
}

enum class VkEndpoint {
    API_METHOD,

    API_OAUTH,

    OAUTH,
}

enum class VkHttpMethod { GET, POST }
