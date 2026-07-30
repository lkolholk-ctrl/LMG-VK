package com.lmg.vk.network

import com.lmg.vk.LmgApplication

/**
 * Восстановлено из `defpackage.AbstractC1831e` — сервис-локатор LMG VK.
 *
 * Статический доступ к синглтонам, инициализируемым в [LmgApplication]:
 *  - [apiClient]  — сетевое ядро (C8221e)
 *  - [playerService] — сервис плеера (C18046e)
 *  - [sessionManager] — менеджер сессии/аккаунтов (C11328e)
 */
object VkApiLocator {

    @Volatile
    private var apiClientInstance: VkApiClient? = null

    /** LmgApplication.f36536e — синглтон сетевого ядра. */
    @JvmStatic
    fun apiClient(): VkApiClient =
        apiClientInstance ?: error("VkApiClient not initialized — call VkApiLocator.init() from LmgApplication")

    @JvmStatic
    fun init(client: VkApiClient) {
        apiClientInstance = client
    }
}
