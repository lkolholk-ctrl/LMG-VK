package com.lmg.vk.network.proxy

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.security.cert.X509Certificate

/**
 * Конфиг обхода блокировок, перенесённый из VK X.
 *
 * В VK X он лежит в Firebase Remote Config под ключами `config_network_proxy`
 * (`VkConfigNetworkProxyProduct`) и `config_network_proxy_certs`
 * (`VkConfigNetworkProxyCertificatesProduct`). Мы Firebase не поднимаем: обе
 * части слиты в один документ на нашем сервере, чтобы конфиг менялся без
 * пересборки приложения — как и остальная серверная логика.
 *
 * Ключ `mnc_for_proxy` (`["255","260-07","260-08"]`) в дампе есть, но в коде
 * VK X он не читается ни разу: это условие таргетинга на стороне Firebase, а не
 * гейт включения. Включение — ручное, тумблером в настройках.
 */
@Serializable
data class VkProxyConfigDocument(
    val schema: Int = 1,
    @SerialName("config_version") val configVersion: Int = 1,
    val source: String? = null,
    /** Как часто перечитывать документ; в дампе VK X — 360 минут. */
    @SerialName("update_delay_minutes") val updateDelayMinutes: Int = 360,
    /** Общий рубильник со стороны сервера: false — прокси недоступен вообще. */
    val enabled: Boolean = true,
    @SerialName("pinning_enabled") val pinningEnabled: Boolean = true,
    @SerialName("proxy_endpoints") val proxyEndpoints: List<VkProxyEndpoint> = emptyList(),
    @SerialName("proxy_domains") val proxyDomains: List<String> = emptyList(),
    /**
     * `api.vk.com` → `api.r.vk.com`: имя, которое уедет в заголовке `Host`.
     * Разобранный ключ VK `override_api_domain`; у самого VK это отдельный
     * эксперимент с выкаткой на 50% (`override_domain_part`), поэтому мы
     * применяем его только на проксируемом пути — см. [VkProxyInterceptor].
     */
    @SerialName("domain_overrides") val domainOverrides: Map<String, String> = emptyMap(),
    val certificates: List<VkProxyCertificate> = emptyList(),
)

/**
 * `weight` приходит из конфига VK, но сам VK X его не использует: интерцептор
 * берёт `list.get(0)` (`AbstractC13480e.m3591interface`). Мы сортируем по весу
 * при разборе — порядок при равных весах остаётся исходным.
 */
@Serializable
data class VkProxyEndpoint(
    val ip: String,
    val weight: Int = 1,
)

@Serializable
data class VkProxyCertificate(
    val id: Int = 0,
    /** SPKI-пин в форме HPKP; для сверки цепочки не нужен, хранится как метка. */
    val hpkp: String? = null,
    val pem: String,
)

/**
 * Состояние загрузки конфига — повторяет иерархию VK X (`Loading` / `Available`
 * / `FailedToLoad`), потому что на неё завязан текст статуса в настройках.
 */
sealed interface VkProxyState {

    data object Loading : VkProxyState

    data object FailedToLoad : VkProxyState

    /**
     * @param ips адреса в порядке убывания веса; первый — основной.
     * @param allowedDomains домены, запросы к которым уводятся на [ips].
     * @param domainOverrides подмена имени хоста до подстановки адреса.
     * @param certificates закреплённые сертификаты; пусто — пиннинг выключен.
     */
    data class Available(
        val ips: List<String>,
        val allowedDomains: List<String>,
        val domainOverrides: Map<String, String>,
        val certificates: List<X509Certificate>,
    ) : VkProxyState
}
