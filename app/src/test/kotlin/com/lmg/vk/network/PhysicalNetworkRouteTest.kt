package com.lmg.vk.network

import org.junit.Assert.*
import org.junit.Test

class PhysicalNetworkRouteTest {
    private fun wifi(id: String = "wifi") = PhysicalNetworkRoute(id, true, false, false, true, 30)
    private val mobile = PhysicalNetworkRoute("mobile", true, false, false, true, 10)

    @Test fun vpnBlockedAndSuspendedNetworksCannotBeSelected() {
        val routes = listOf(wifi().copy(network = "vpn", physical = false),
            wifi().copy(blocked = true), mobile.copy(suspended = true))
        assertNull(selectPhysicalNetwork(routes, "wifi"))
        assertNull(selectPhysicalNetwork(emptyList<PhysicalNetworkRoute<String>>(), "lost"))
    }

    @Test fun workingMobileWinsOverWifiWithoutValidatedInternet() {
        assertEquals("mobile", selectPhysicalNetwork(listOf(wifi().copy(validated = false), mobile), "wifi"))
        assertEquals("wifi", selectPhysicalNetwork(listOf(wifi(), mobile), "mobile"))
    }

    @Test fun equalNetworksKeepTheExistingRouteRegardlessOfCallbackOrder() {
        val routes = listOf(wifi("first"), wifi("second"))
        assertEquals("second", selectPhysicalNetwork(routes, "second"))
        assertEquals("second", selectPhysicalNetwork(routes.reversed(), "second"))
    }

    @Test fun lossOfTheChosenRouteFallsBackWithoutHoldingADeadNetwork() {
        assertEquals("mobile", selectPhysicalNetwork(listOf(mobile), "wifi"))
        assertEquals("mobile", selectPhysicalNetwork(listOf(wifi().copy(blocked = true), mobile), "wifi"))
    }
}
