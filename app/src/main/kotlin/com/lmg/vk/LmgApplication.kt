package com.lmg.vk

import android.app.Application
import android.net.ConnectivityManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.kyant.fishnet.Fishnet
import com.lmg.vk.data.local.db.LibraryRepository
import com.lmg.vk.debug.UiWatchdog
import com.lmg.vk.engine.AppSettings
import com.lmg.vk.engine.AudioFxController
import com.lmg.vk.engine.AudioRouteMonitor
import com.lmg.vk.engine.LyricsFxController
import com.lmg.vk.engine.LyricsParser
import com.lmg.vk.engine.NetworkVitality
import com.lmg.vk.engine.PlayerController
import com.lmg.vk.engine.PlayerSettings
import com.lmg.vk.engine.automix.AudioTelemetry
import com.lmg.vk.engine.automix.HapticMusicEngine
import com.lmg.vk.engine.automix.RemoteQuirks
import com.lmg.vk.engine.backend.MusicAuth
import com.lmg.vk.engine.backend.MusicBackend
import com.lmg.vk.engine.backend.WaveSignalQueue
import com.lmg.vk.logging.CrashHandler
import com.lmg.vk.network.EncryptedVkSessionStore
import com.lmg.vk.network.VkApiClient
import com.lmg.vk.network.VkApiLocator
import com.lmg.vk.ui.DeviceTier
import com.lmg.vk.ui.PowerSaveMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import io.ktor.client.HttpClient as KtorHttpClient
import io.ktor.client.engine.okhttp.OkHttp as KtorOkHttp
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Точка входа приложения (слияние восстановленного VKXApplication + LMG App).
 *
 * Порядок инициализации — как в LMG: сначала лёгкое (settings), потом engine,
 * сетевые оживители и фоновый drain. ICM-части заменены фасадом engine/backend.
 */
class LmgApplication : Application(), ImageLoaderFactory {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Свой OkHttp для обложек с таймаутами: зависший CDN не должен копить
    // потоки (в ANR-дампе обложки висели в TLS-handshake). Пул эвиктим при
    // смене сети (VPN/Wi-Fi↔моб.).
    private val coverHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .dispatcher(Dispatcher().apply { maxRequests = 6; maxRequestsPerHost = 4 })
            .connectionPool(ConnectionPool(4, 60, TimeUnit.SECONDS))
            .build()
    }

    fun evictImageConnections() {
        runCatching {
            coverHttpClient.dispatcher.cancelAll()
            coverHttpClient.connectionPool.evictAll()
        }
    }

    private var netReconnectJob: kotlinx.coroutines.Job? = null

    /**
     * Application-level колбэк дефолтной сети: смена сети → NetworkVitality
     * оживляет пулы; с дебаунсом 1.5с — ресинк лайков/очереди сигналов.
     */
    private fun registerAppNetworkCallback() {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                private var currentNetwork: android.net.Network? = null

                override fun onAvailable(network: android.net.Network) {
                    if (currentNetwork == network) return
                    currentNetwork = network
                    NetworkVitality.onDefaultNetworkChanged()
                    netReconnectJob?.cancel()
                    netReconnectJob = appScope.launch {
                        kotlinx.coroutines.delay(1500)
                        resyncAfterNetworkChange()
                    }
                }

                override fun onLost(network: android.net.Network) {
                    if (currentNetwork == network) currentNetwork = null
                }
            })
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient { coverHttpClient }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.30)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_cache"))
                    .maxSizeBytes(512L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        connectivityManager = getSystemService(ConnectivityManager::class.java)

        // Java-крэши: синхронно и ПЕРВЫМ — Fishnet ниже подшивается к уже
        // установленному дефолтному хендлеру.
        CrashHandler.install(this)
        // Чистим устаревшие ui_freeze-логи ДО MainActivity (hasCrashLog).
        CrashHandler.purgeStaleFreezeLogs(this)

        // Native/ANR крэши — в отдельном потоке (не в критическом пути 1-го кадра).
        val logDir = File(filesDir, "crash_logs").apply { mkdirs() }
        Thread { Fishnet.init(this@LmgApplication, logDir.absolutePath) }
            .apply { name = "fishnet-init"; start() }

        // Ловушка зависаний UI: Fishnet-дампы ANR приходят БЕЗ стека main —
        // вачдог сам пишет полный Java-дамп в watchdog_diag/.
        UiWatchdog.start(this)

        // Удалённая карта аудио-причуд — ДО AppSettings.
        RemoteQuirks.preload(this)
        AudioTelemetry.init(this)

        // Настройки (SharedPreferences/DataStore) — лёгкие, можно на main.
        AppSettings.init(this)
        PlayerSettings.init(this)
        AudioFxController.init(this)
        LyricsFxController.init(this)

        // Железо/энергосбережение → режимы эффектов.
        AudioRouteMonitor.init(this)
        PowerSaveMonitor.init(this)
        DeviceTier.init(this)

        // Haptic Music — тактильные удары в такт (спит, если выкл).
        HapticMusicEngine.init(this)

        // PlayerController — просто сохраняет context.
        PlayerController.init(this)

        // VK API: Ktor-клиент + зашифрованная сессия + доменный фасад UI.
        val vkSessionStore = EncryptedVkSessionStore(this)
        val vkApiClient = VkApiClient(
            httpClient = KtorHttpClient(KtorOkHttp) {
                expectSuccess = false
            },
            sessionStore = vkSessionStore,
            deviceIdProvider = ::resolveVkDeviceId,
        )
        VkApiLocator.init(vkApiClient)
        MusicBackend.init(vkApiClient, vkSessionStore)

        // ── Сетевая живучесть ─────────────────────────────────────────────
        NetworkVitality.registerReviver("covers") { evictImageConnections() }
        // TODO(vk-wire): reviver для Ktor-клиента VkApiClient (evict пула).

        // Колбэк дефолтной сети — на уровне ПРИЛОЖЕНИЯ.
        registerAppNetworkCallback()

        // Фоновые стартовые задачи (без таймаута главного потока).
        appScope.launch {
            // C9616e: api.vk.com/ping.txt -> api.vk.ru/ping.txt.
            vkApiClient.probeAndSelectApiDomain()
            // Дослать сигналы волны, не доставленные в прошлой сессии.
            runCatching { WaveSignalQueue.drain() }
            // Подтянуть серверные лайки в локальное избранное.
            if (MusicAuth.isLoggedIn.value) {
                runCatching { LibraryRepository.getInstance(this@LmgApplication).syncWithCloud() }
            }
        }

        isInitialized = true
    }

    /** Ресинк после смены сети: профиль/лайки/очередь (дебаунс 1.5с сверху). */
    private suspend fun resyncAfterNetworkChange() {
        if (MusicAuth.isLoggedIn.value) {
            runCatching { MusicAuth.fetchUserData() }
            runCatching { LibraryRepository.getInstance(this).syncWithCloud() }
        }
    }

    /**
     * Давление на память: сбрасываем in-memory кэши (обложки Coil, лирика),
     * чтобы LMK не выбивал процесс с играющей музыкой из фона.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            runCatching { coil.Coil.imageLoader(this).memoryCache?.clear() }
            runCatching { LyricsParser.trimCache() }
        }
    }

    private fun resolveVkDeviceId(): String {
        val preferences = getSharedPreferences("lmg_vk_device", MODE_PRIVATE)
        preferences.getString("device_id", null)?.takeIf { it.isNotBlank() }?.let { return it }
        val generated = java.util.UUID.randomUUID().toString()
        preferences.edit().putString("device_id", generated).apply()
        return generated
    }

    companion object {
        /** Мониторинг сети (бывш. VKXApplication.f36537e). */
        @JvmStatic
        var connectivityManager: ConnectivityManager? = null
            private set

        /** Флаг готовности приложения. */
        @JvmStatic
        var isInitialized: Boolean = false
            private set
    }
}
