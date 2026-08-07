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

    @Volatile
    private var mediaClientInstance: io.ktor.client.HttpClient? = null

    /** LmgApplication.f36536e — синглтон сетевого ядра. */
    @JvmStatic
    fun apiClient(): VkApiClient =
        apiClientInstance ?: error("VkApiClient not initialized — call VkApiLocator.init() from LmgApplication")

    /**
     * Тот же Ktor-клиент, что обслуживает API, — с установленным
     * `installVkProxy`, то есть с обходом блокировок и пиннингом.
     *
     * Зачем отдельная точка доступа: скачивание байтов (треки, обложки) идёт не
     * через [VkApiClient], а напрямую по подписанному URL с CDN, но обходной слой
     * ему нужен ровно так же — иначе при блокировке `userapi.com` воспроизведение
     * работает, а загрузка молча падает. Свой клиент здесь плодить нельзя: он
     * пойдёт мимо интерцептора.
     *
     * Возвращает `null`, пока приложение не поднялось; вызывающий обязан иметь
     * запасной путь, а не падать.
     */
    @JvmStatic
    fun mediaClientOrNull(): io.ktor.client.HttpClient? = mediaClientInstance

    @JvmStatic
    fun init(client: VkApiClient) {
        apiClientInstance = client
    }

    /** Регистрирует сетевой клиент с обходом. Зовётся из [LmgApplication]. */
    @JvmStatic
    fun initMediaClient(client: io.ktor.client.HttpClient) {
        mediaClientInstance = client
    }
}
