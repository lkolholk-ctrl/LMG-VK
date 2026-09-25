package com.lmg.vk.ui.navigation

import org.junit.Assert.*
import org.junit.Test

class OverlayRouteReturnTest {
    private val profileStack = listOf("settings", "profile")

    @Test fun openingPlaylistFromProfileHidesSettingsAndProfileTogether() {
        val pending = OverlayRouteReturn(profileStack, "library-entry")
        assertTrue(pending.visibleOverlays(profileStack).isEmpty())
    }

    @Test fun backRestoresTheWholeProfileStackAfterLeavingDetail() {
        val pending = OverlayRouteReturn(profileStack, "library-entry")
        val detail = pending.followEntry("playlist-entry")!!
        assertTrue(detail.destinationEntered)
        assertTrue(detail.visibleOverlays(profileStack).isEmpty())
        val restored = detail.followEntry("library-entry")
        assertNull(restored)
        assertEquals(profileStack, restored?.visibleOverlays(profileStack) ?: profileStack)
    }

    @Test fun originIsNotRestoredBeforeDestinationIsEntered() {
        val pending = OverlayRouteReturn(profileStack, "origin")
        assertSame(pending, pending.followEntry("origin"))
        assertSame(pending, pending.followEntry(null))
    }

    @Test fun anotherEntryOfTheSameRouteDoesNotRestoreOverlays() {
        val pending = OverlayRouteReturn(profileStack, "user-profile-entry-1")
        val next = pending.followEntry("user-profile-entry-2")!!
        assertTrue(next.destinationEntered)
        assertSame(next, next.followEntry("user-profile-entry-3"))
        assertNull(next.followEntry("user-profile-entry-1"))
    }

    @Test fun newOverlayAboveDetailRemainsVisible() {
        val pending = OverlayRouteReturn(profileStack, "origin")
        assertEquals(listOf("search"), pending.visibleOverlays(profileStack + "search"))
    }

    @Test fun switchingTabsDiscardsAllSuspendedOverlays() {
        val pending = OverlayRouteReturn(profileStack, "origin")
        assertEquals(emptyList<String>(), pending.visibleOverlays(profileStack))
    }
}
