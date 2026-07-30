package com.lmg.vk

import android.app.Application
import android.net.ConnectivityManager
import com.lmg.vk.network.VkApiClient
import com.lmg.vk.network.VkApiLocator

/**
 * Точка входа приложения.
 * Восстановлено из `com.lmg.vk.LmgApplication` (+ Companion с синглтонами).
 *
 * В оригинале держит статические ссылки (инициализируются в onCreate):
 *  - f36536e -> VkApiClient (сетевое ядро)
 *  - f36532e -> сервис плеера
 *  - f36535e -> менеджер сессии
 *  - f36530e -> DI-контейнер (f36533e -> Moshi-инстанс)
 */
class LmgApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        connectivityManager = getSystemService(ConnectivityManager::class.java)

        // --- сетевое ядро (VkApiLocator.init в оригинале — f36536e) ---
        // val client = VkApiClient(ktorClient, sessionStore, deviceIdProvider)
        // VkApiLocator.init(client)

        // --- Moshi с адаптерами DTO (f36533e = C14172e) ---
        // moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

        // --- Realm (CachedTrack/CachedPlaylist — регистрация схем в Companion) ---
    }

    companion object {
        /** LmgApplication.f36537e — мониторинг сети. */
        @JvmStatic
        var connectivityManager: ConnectivityManager? = null
            private set

        /** LmgApplication.f36534e — флаг готовности. */
        @JvmStatic
        var isInitialized: Boolean = false
            private set
    }
}
