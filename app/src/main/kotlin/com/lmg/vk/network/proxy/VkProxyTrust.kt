package com.lmg.vk.network.proxy

import java.net.Socket
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager

/**
 * TLS-часть прокси, перенесённая из `C11622e` и `C12737e` VK X.
 *
 * Зачем вообще своя проверка: интерцептор подставляет в URL IP-адрес, поэтому
 * штатная сверка имени хоста заведомо не сойдётся — в сертификате узла стоит
 * доменное имя, а не адрес. VK X решает это так же: сначала пробует
 * закреплённые сертификаты, и лишь если ни один не подошёл — обычную проверку.
 *
 * Важно: системная проверка остаётся запасным путём, а не заменяется. Пока
 * прокси выключен или список сертификатов пуст, поведение ровно системное.
 */
internal class VkProxyTrustManager(
    private val pinnedCertificates: () -> List<X509Certificate>,
) : X509ExtendedTrustManager() {

    private val system: X509ExtendedTrustManager = systemTrustManager()

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        system.checkClientTrusted(chain, authType)
    }

    override fun checkClientTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        socket: Socket?,
    ) {
        system.checkClientTrusted(chain, authType, socket)
    }

    override fun checkClientTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        engine: SSLEngine?,
    ) {
        system.checkClientTrusted(chain, authType, engine)
    }

    /**
     * Цепочка принимается, если в ней есть закреплённый сертификат. Иначе —
     * системная проверка, которая бросит [CertificateException] сама.
     */
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        if (matchesPins(chain)) return
        val legacy = legacyTrustManager()
        if (legacy != null) legacy.checkServerTrusted(chain, authType)
        else system.checkServerTrusted(chain, authType)
    }

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        socket: Socket?,
    ) {
        if (matchesPins(chain)) return
        system.checkServerTrusted(chain, authType, socket)
    }

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        engine: SSLEngine?,
    ) {
        if (matchesPins(chain)) return
        system.checkServerTrusted(chain, authType, engine)
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> =
        pinnedCertificates().toTypedArray() + system.acceptedIssuers

    private fun matchesPins(chain: Array<out X509Certificate>?): Boolean {
        val pinned = pinnedCertificates()
        if (pinned.isEmpty() || chain == null) return false
        return chain.any { certificate -> pinned.any { it == certificate } }
    }

    private companion object {
        fun systemTrustManager(): X509ExtendedTrustManager {
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            factory.init(null as KeyStore?)
            return factory.trustManagers.filterIsInstance<X509ExtendedTrustManager>().firstOrNull()
                ?: throw IllegalStateException("В системе нет X509ExtendedTrustManager")
        }

        fun legacyTrustManager(): X509ExtendedTrustManager? = runCatching {
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            factory.init(KeyStore.getInstance("AndroidCAStore"))
            factory.trustManagers.filterIsInstance<X509ExtendedTrustManager>().firstOrNull()
        }.getOrNull()
    }
}

/**
 * Проверка имени хоста поверх закреплённых сертификатов (`C12737e`).
 *
 * Узел принимается, если его сертификат совпал с закреплённым или подписан им.
 * Если совпадения нет — делегируем штатному верификатору OkHttp, чтобы обычные
 * (непроксируемые) соединения проверялись как раньше.
 */
internal class VkProxyHostnameVerifier(
    private val pinnedCertificates: () -> List<X509Certificate>,
    private val delegate: HostnameVerifier,
) : HostnameVerifier {

    override fun verify(host: String, session: SSLSession): Boolean {
        val pinned = pinnedCertificates()
        if (pinned.isNotEmpty()) {
            val peers = runCatching { session.peerCertificates }.getOrNull().orEmpty()
            for (certificate in pinned) {
                for (peer in peers) {
                    if (peer == certificate) return true
                    // Узел может отдавать не сам закреплённый сертификат, а
                    // выписанный им — проверяем подпись его открытым ключом.
                    val signedByPin = runCatching { peer.verify(certificate.publicKey) }.isSuccess
                    if (signedByPin) return true
                }
            }
        }
        return delegate.verify(host, session)
    }
}
