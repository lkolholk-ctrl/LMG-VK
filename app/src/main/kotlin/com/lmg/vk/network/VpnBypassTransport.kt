package com.lmg.vk.network

import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory

/** Socket and DNS routing leave the VK proxy's URL rewriting and TLS configuration intact. */
internal fun OkHttpClient.Builder.installVpnBypass(): OkHttpClient.Builder =
    socketFactory(RouteSocketFactory {
        VpnBypassManager.currentNetwork()?.socketFactory ?: SocketFactory.getDefault()
    }).dns(object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val network = VpnBypassManager.currentNetwork()
            return if (network == null) Dns.SYSTEM.lookup(hostname) else network.getAllByName(hostname).toList()
        }
    })

internal class RouteSocketFactory(private val current: () -> SocketFactory) : SocketFactory() {
    override fun createSocket(): Socket = current().createSocket()
    override fun createSocket(host: String, port: Int): Socket = current().createSocket(host, port)
    override fun createSocket(host: String, port: Int, local: InetAddress, localPort: Int): Socket =
        current().createSocket(host, port, local, localPort)
    override fun createSocket(host: InetAddress, port: Int): Socket = current().createSocket(host, port)
    override fun createSocket(host: InetAddress, port: Int, local: InetAddress, localPort: Int): Socket =
        current().createSocket(host, port, local, localPort)
}
