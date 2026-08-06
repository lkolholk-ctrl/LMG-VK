package com.lmg.vk.network.proxy

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * Загрузка и кэш конфига прокси.
 *
 * VK X держит конфиг в Firebase Remote Config; у нас он лежит на своём сервере,
 * потому что весь остальной серверный слой уже там и менять адреса без релиза
 * иначе нечем. Формат — [VkProxyConfigDocument], слитый из обоих ключей VK X.
 *
 * Загрузка идёт голым `HttpURLConnection`, а не общим Ktor-клиентом, намеренно:
 * этот запрос обслуживает сам сетевой слой и не должен проходить через
 * интерцептор прокси, иначе конфиг зависел бы от прокси, который он настраивает.
 */
object VkProxyRepository {

    private const val CONFIG_URL = "https://gsgit.org/lmg-vk/network-config.json"
    private const val PREFS = "vk_proxy"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_CACHED_DOC = "cached_document"
    private const val KEY_FETCHED_AT = "fetched_at"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var prefs: SharedPreferences

    private val _state = MutableStateFlow<VkProxyState>(VkProxyState.Loading)

    /** Loading / Available / FailedToLoad — на это завязан статус в настройках. */
    val state: StateFlow<VkProxyState> = _state

    private val _enabled = MutableStateFlow(false)

    /**
     * Пользовательский тумблер. В VK X это `C15601e.vkProxyEnabled` — включение
     * ручное, автоматического определения блокировки в клиенте нет.
     */
    val enabled: StateFlow<Boolean> = _enabled

    /** Сертификаты активного состояния; пусто — пиннинга нет. */
    internal fun pinnedCertificates(): List<X509Certificate> =
        (_state.value as? VkProxyState.Available)?.certificates.orEmpty()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _enabled.value = prefs.getBoolean(KEY_ENABLED, false)

        // Кэш поднимаем сразу: без сети прокси должен работать на прошлом
        // документе, иначе выключение интернета выключало бы и обход блокировок.
        prefs.getString(KEY_CACHED_DOC, null)?.let { cached ->
            parse(cached)?.let { _state.value = it }
        }
        scope.launch { refreshIfStale() }
    }

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        if (value) scope.launch { refreshIfStale() }
    }

    /** Перечитать документ, если истёк `update_delay_minutes` из него самого. */
    suspend fun refreshIfStale() {
        if (!::prefs.isInitialized) return
        val delayMinutes = (_state.value as? VkProxyState.Available)
            ?.let { cachedDelayMinutes() }
            ?: 0
        val age = System.currentTimeMillis() - prefs.getLong(KEY_FETCHED_AT, 0L)
        if (age in 0 until delayMinutes * 60_000L) return
        refresh()
    }

    suspend fun refresh() {
        if (!::prefs.isInitialized) return
        if (_state.value !is VkProxyState.Available) _state.value = VkProxyState.Loading
        val body = withContext(Dispatchers.IO) { download() }
        if (body == null) {
            // Кэш ценнее ошибки: он мог быть загружен до блокировки.
            if (_state.value !is VkProxyState.Available) _state.value = VkProxyState.FailedToLoad
            return
        }
        val parsed = parse(body)
        if (parsed == null) {
            if (_state.value !is VkProxyState.Available) _state.value = VkProxyState.FailedToLoad
            return
        }
        prefs.edit()
            .putString(KEY_CACHED_DOC, body)
            .putLong(KEY_FETCHED_AT, System.currentTimeMillis())
            .apply()
        _state.value = parsed
    }

    private fun cachedDelayMinutes(): Int {
        val cached = prefs.getString(KEY_CACHED_DOC, null) ?: return 0
        return runCatching {
            json.decodeFromString(VkProxyConfigDocument.serializer(), cached).updateDelayMinutes
        }.getOrDefault(0)
    }

    private fun download(): String? = runCatching {
        val connection = URL(CONFIG_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.requestMethod = "GET"
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
            connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    /**
     * Разбор документа в состояние. Возвращает null, если документ непригоден:
     * выключен сервером, без адресов или без доменов — включать в таком виде
     * нечего, и честнее показать FailedToLoad, чем молча ничего не проксировать.
     */
    private fun parse(body: String): VkProxyState.Available? {
        val document = runCatching {
            json.decodeFromString(VkProxyConfigDocument.serializer(), body)
        }.getOrNull() ?: return null

        if (!document.enabled) return null
        val ips = document.proxyEndpoints
            .sortedByDescending { it.weight }
            .map { it.ip }
            .filter { it.isNotBlank() }
        val domains = document.proxyDomains.filter { it.isNotBlank() }
        if (ips.isEmpty() || domains.isEmpty()) return null

        val certificates = if (document.pinningEnabled) {
            parseCertificates(document.certificates)
        } else {
            emptyList()
        }
        return VkProxyState.Available(
            ips = ips,
            allowedDomains = domains,
            domainOverrides = document.domainOverrides,
            certificates = certificates,
        )
    }

    /** PEM → X509 (`AbstractC12662e.ad` в VK X). Битый сертификат пропускаем. */
    private fun parseCertificates(raw: List<VkProxyCertificate>): List<X509Certificate> {
        if (raw.isEmpty()) return emptyList()
        val factory = runCatching { CertificateFactory.getInstance("X.509") }.getOrNull()
            ?: return emptyList()
        return raw.flatMap { certificate ->
            runCatching {
                ByteArrayInputStream(certificate.pem.toByteArray(Charsets.UTF_8)).use { stream ->
                    factory.generateCertificates(stream).filterIsInstance<X509Certificate>()
                }
            }.getOrDefault(emptyList())
        }
    }
}
