package com.lmg.vk

import android.app.Application
import android.net.ConnectivityManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.kyant.fishnet.Fishnet
import com.lmg.vk.debug.UiWatchdog
import com.lmg.vk.debug.AppStartupTrace
import com.lmg.vk.data.local.HomeCacheManager
import com.lmg.vk.engine.AppSettings
import com.lmg.vk.engine.AccountSyncManager
import com.lmg.vk.engine.AudioFxController
import com.lmg.vk.engine.AudioRouteMonitor
import com.lmg.vk.engine.LyricsFxController
import com.lmg.vk.engine.LyricsParser
import com.lmg.vk.engine.NetworkVitality
import com.lmg.vk.engine.PlayerController
import com.lmg.vk.engine.PlayerSettings
import com.lmg.vk.engine.PlaylistManager
import com.lmg.vk.engine.PlaylistSyncManager
import com.lmg.vk.engine.automix.AudioTelemetry
import com.lmg.vk.engine.automix.RemoteQuirks
import com.lmg.vk.engine.backend.MusicAuth
import com.lmg.vk.engine.backend.MusicBackend
import com.lmg.vk.engine.backend.WaveSignalQueue
import com.lmg.vk.logging.CrashHandler
import com.lmg.vk.network.EncryptedVkSessionStore
import com.lmg.vk.network.VkApiClient
import com.lmg.vk.network.VkApiLocator
import com.lmg.vk.network.VkUserAgents
import com.lmg.vk.network.VkTokenRefreshWorker
import com.lmg.vk.network.proxy.VkProxyRepository
import com.lmg.vk.network.proxy.installVkProxy
import com.lmg.vk.ui.DeviceTier
import com.lmg.vk.ui.PowerSaveMonitor
import com.lmg.vk.widget.VkMusicWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import io.ktor.client.HttpClient as KtorHttpClient
import io.ktor.client.engine.okhttp.OkHttp as KtorOkHttp
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import com.lmg.vk.network.installVpnBypass
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

class LmgApplication : Application(), ImageLoaderFactory {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val vkHttpConnectionPool = ConnectionPool(8, 60, TimeUnit.SECONDS)
    private val vkHttpDispatcher = Dispatcher()

    // Свой OkHttp для обложек с таймаутами: зависший CDN не должен копить
    // потоки (в ANR-дампе обложки висели в TLS-handshake). Пул эвиктим при
    // смене сети (VPN/Wi-Fi↔моб.).
    private val coverHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .dispatcher(Dispatcher().apply { maxRequests = 6; maxRequestsPerHost = 4 })
            .connectionPool(vkHttpConnectionPool)
            .installVpnBypass()
            // Картинки живут на тех же заблокированных доменах, что и API с
            // медиа (`*.userapi.com`, `*.vk-cdn.net`), поэтому обход нужен и им.
            // Без этого при включённом обходе получалась ровно жалоба «иконки не
            // прогружает»: списки друзей и сообществ приходят (API идёт через
            // проксируемый Ktor-клиент), а вот аватары к ним грузятся этим
            // клиентом напрямую — и молча падают на блокировке.
            .installVkProxy()
            .build()
    }

    fun evictImageConnections() {
        runCatching {
            coverHttpClient.dispatcher.cancelAll()
            vkHttpDispatcher.cancelAll()
            coverHttpClient.connectionPool.evictAll()
        }
    }


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
                    com.lmg.vk.network.VpnBypassManager.updateStateAndApply()
                    MusicAuth.onNetworkAvailable()
                    WaveSignalQueue.onNetworkAvailable()
                }

                override fun onLost(network: android.net.Network) {
                    if (currentNetwork == network) currentNetwork = null
                }
            })
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .eventListener(object : coil.EventListener {
                override fun onSuccess(request: coil.request.ImageRequest, result: coil.request.SuccessResult) {
                    result.diskCacheKey?.let { key ->
                        com.lmg.vk.engine.CacheCatalog.remember(this@LmgApplication, "coil:$key", "", "", request.data.toString())
                    }
                }
            })
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
        com.lmg.vk.debug.AppPerformanceCapture.initialize(this)
        AppStartupTrace.start(this)
        val startup = AppStartupTrace.beginElapsed("application_create")

        VkUserAgents.init(this)
        MusicBackend.appContext = this
        connectivityManager = getSystemService(ConnectivityManager::class.java)

        appScope.launch { runCatching {
            AppStartupTrace.elapsed("lyrics_service_ping") { com.lmg.vk.engine.lyrics.ExternalLyricsRepository.warmUp() }
        } }

        // Java-крэши: синхронно и ПЕРВЫМ — Fishnet ниже подшивается к уже
        // установленному дефолтному хендлеру.
        CrashHandler.install(this)
        // Чистим устаревшие ui_freeze-логи ДО MainActivity (hasCrashLog).
        CrashHandler.purgeStaleFreezeLogs(this)

        // Native/ANR крэши — в отдельном потоке (не в критическом пути 1-го кадра).
        val logDir = File(filesDir, "crash_logs").apply { mkdirs() }
        Thread { AppStartupTrace.measure("fishnet_init") { Fishnet.init(this@LmgApplication, logDir.absolutePath) } }
            .apply { name = "fishnet-init"; start() }

        // Ловушка зависаний UI: Fishnet-дампы ANR приходят БЕЗ стека main —
        // вачдог сам пишет полный Java-дамп в watchdog_diag/.
        UiWatchdog.start(this)

        // Удалённая карта аудио-причуд — ДО AppSettings.
        AppStartupTrace.measure("remote_quirks_init") { RemoteQuirks.preload(this) }
        AppStartupTrace.measure("audio_telemetry_init") { AudioTelemetry.init(this) }

        // Настройки (SharedPreferences/DataStore) — лёгкие, можно на main.
        AppStartupTrace.measure("app_settings_init") { AppSettings.init(this) }
        AppStartupTrace.measure("vpn_init") { com.lmg.vk.network.VpnBypassManager.init(this) }
        HomeCacheManager.init(this)
        AppStartupTrace.measure("player_settings_init") { PlayerSettings.init(this) }
        AppStartupTrace.measure("audio_fx_init") { AudioFxController.init(this) }
        AppStartupTrace.measure("player_dsp_init") { com.lmg.vk.engine.dsp.DspController.init(this) }
        AppStartupTrace.measure("lyrics_fx_init") { LyricsFxController.init(this) }

        // Железо/энергосбережение → режимы эффектов.
        AudioRouteMonitor.init(this)
        PowerSaveMonitor.init(this)
        DeviceTier.init(this)

        // PlayerController — просто сохраняет context.
        PlayerController.init(this)
        AppStartupTrace.measure("local_playlists_init") { PlaylistManager.init(this) }
        AccountSyncManager.init(this)

        // Обход блокировок: конфиг с адресами и сертификатами. Поднимаем до
        // создания Ktor-клиента — интерцептор читает состояние на каждом запросе,
        // но кэш должен быть уже в памяти к первому из них.
        AppStartupTrace.measure("vk_proxy_init") { VkProxyRepository.init(this) }

        // VK API: Ktor-клиент + зашифрованная сессия + доменный фасад UI.
        val vkSessionStore = AppStartupTrace.measure("vk_session_restore") { EncryptedVkSessionStore(this) }
        // Клиент вынесен в переменную, потому что он нужен двум потребителям:
        // самому VkApiClient и загрузке медиа-байтов (треки, обложки). Байты
        // идут напрямую по подписанному URL с CDN, но обходной слой им нужен
        // так же — иначе при блокировке userapi.com воспроизведение работает, а
        // скачивание молча падает. Отдельный клиент для медиа пошёл бы мимо
        // installVkProxy, поэтому переиспользуем этот.
        val vkNetworkClient = AppStartupTrace.measure("vk_http_init") { KtorHttpClient(KtorOkHttp) {
            expectSuccess = false
            engine {
                config {
                    connectionPool(vkHttpConnectionPool)
                    dispatcher(vkHttpDispatcher)
                    installVpnBypass()
                    installVkProxy()
                }
            }
        } }
        val vkApiClient = VkApiClient(
            httpClient = vkNetworkClient,
            sessionStore = vkSessionStore,
            deviceIdProvider = ::resolveVkDeviceId,
        ).apply {
            captchaHandler = { img, sid, ts, attempt ->
                com.lmg.vk.network.GlobalCaptchaManager.requestCaptcha(img, sid, ts, attempt)
            }
            validationHandler = { redirectUri ->
                com.lmg.vk.network.GlobalCaptchaManager.requestValidation(redirectUri)
            }
        }
        VkApiLocator.init(vkApiClient)
        VkApiLocator.initMediaClient(vkNetworkClient)
        AppStartupTrace.measure("music_backend_init") { MusicBackend.init(vkApiClient, vkSessionStore) }
        AppStartupTrace.measure("wave_signal_init") { WaveSignalQueue.init(this) }
        AppStartupTrace.measure("token_worker_schedule") { VkTokenRefreshWorker.schedule(this) }

        // ── Сетевая живучесть ─────────────────────────────────────────────
        NetworkVitality.registerReviver("vk-http") { evictImageConnections() }
        NetworkVitality.registerReviver("playback") {
            PlayerController.onNetworkRouteChanged()
        }
        // Колбэк дефолтной сети — на уровне ПРИЛОЖЕНИЯ.
        registerAppNetworkCallback()

        // Фоновые стартовые задачи (без таймаута главного потока).
        appScope.launch {
            PlaylistManager.changes.collectLatest {
                delay(900)
                if (MusicAuth.isLoggedIn.value) runCatching {
                    AppStartupTrace.elapsed("playlist_sync") { PlaylistSyncManager.sync() }
                }
            }
        }
        appScope.launch {
            MusicAuth.profileId.collectLatest { profileId ->
                AppStartupTrace.elapsed("widget_refresh") { VkMusicWidget.refreshAll(this@LmgApplication) }
                profileId?.let { AppStartupTrace.elapsed("wave_signal_drain.profile") { WaveSignalQueue.drain(it) } }
            }
        }
        appScope.launch {
            // C9616e: api.vk.com/ping.txt -> api.vk.ru/ping.txt.
            AppStartupTrace.elapsed("vk_domain_probe") { vkApiClient.probeAndSelectApiDomain() }
            // Дослать сигналы волны, не доставленные в прошлой сессии.
            runCatching { AppStartupTrace.elapsed("wave_signal_drain.startup") { WaveSignalQueue.drain() } }
        }

        isInitialized = true
        startup?.end()
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
        preferences.getString("device_id", null)
            ?.takeIf(VK_DEVICE_ID_PATTERN::matches)
            ?.let { return it }

        val generated = buildString(49) {
            repeat(16) { append(VK_DEVICE_ID_ALPHABET[secureRandom.nextInt(VK_DEVICE_ID_ALPHABET.length)]) }
            append(':')
            repeat(32) { append(VK_DEVICE_ID_ALPHABET[secureRandom.nextInt(VK_DEVICE_ID_ALPHABET.length)]) }
        }
        preferences.edit().putString("device_id", generated).apply()
        return generated
    }

    companion object {
        private const val VK_DEVICE_ID_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"
        private val VK_DEVICE_ID_PATTERN = Regex("[a-z0-9]{16}:[a-z0-9]{32}")
        private val secureRandom = SecureRandom()

        @JvmStatic
        var connectivityManager: ConnectivityManager? = null
            private set

        @JvmStatic
        var isInitialized: Boolean = false
            private set
    }
}
