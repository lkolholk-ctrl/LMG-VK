package com.lmg.vk.network

import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory
import org.junit.Assert.*
import org.junit.Test

class RouteSocketFactoryTest {
    private class RecordingFactory : SocketFactory() {
        val calls = mutableListOf<List<Any>>()
        private fun record(vararg args: Any): Socket {
            calls += args.toList()
            return Socket()
        }
        override fun createSocket() = record()
        override fun createSocket(host: String, port: Int) = record(host, port)
        override fun createSocket(host: String, port: Int, local: InetAddress, localPort: Int) = record(host, port, local, localPort)
        override fun createSocket(host: InetAddress, port: Int) = record(host, port)
        override fun createSocket(host: InetAddress, port: Int, local: InetAddress, localPort: Int) = record(host, port, local, localPort)
    }

    @Test fun eachNewSocketUsesTheCurrentRouteIncludingAfterBypassIsDisabled() {
        val physical = RecordingFactory()
        val system = RecordingFactory()
        var route: SocketFactory = physical
        val factory = RouteSocketFactory { route }
        factory.createSocket().close()
        route = system
        factory.createSocket().close()
        assertEquals(1, physical.calls.size)
        assertEquals(1, system.calls.size)
    }

    @Test fun allConnectedOverloadsPreserveTheirOriginalDestinationAndLocalBind() {
        val delegate = RecordingFactory()
        val factory = RouteSocketFactory { delegate }
        val address = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
        factory.createSocket("example.invalid", 443).close()
        factory.createSocket("example.invalid", 443, address, 42).close()
        factory.createSocket(address, 443).close()
        factory.createSocket(address, 443, address, 42).close()
        assertEquals(listOf(listOf("example.invalid", 443), listOf("example.invalid", 443, address, 42),
            listOf(address, 443), listOf(address, 443, address, 42)), delegate.calls)
    }
}
