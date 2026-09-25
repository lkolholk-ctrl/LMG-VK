package com.lmg.vk.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadDismissalTest {
    private fun item(id: String) = DownloadedItem(id, id, "Artist", "Album", "file://$id", 10L, 100L, null)
    private val a = item("a")
    private val b = item("b")
    private val c = item("c")

    @Test fun retainsRemovedRowUntilAnimationFinishes() {
        assertEquals(listOf(a, b, c), mergeDismissingDownloads(listOf(a, c), listOf(DownloadDismissal(b, 1))))
    }

    @Test fun doesNotDuplicateRowWhileStorageDeletionRuns() {
        assertEquals(listOf(a, b, c), mergeDismissingDownloads(listOf(a, b, c), listOf(DownloadDismissal(b, 1))))
    }

    @Test fun hidesFinishedRowUntilStorageUpdates() {
        assertEquals(listOf(a, c), mergeDismissingDownloads(listOf(a, b, c), listOf(DownloadDismissal(b, 1, true))))
    }

    @Test fun bulkRemovalRetainsOnlyAnimatedRowsInOrder() {
        assertEquals(listOf(a, c), mergeDismissingDownloads(emptyList(), listOf(DownloadDismissal(c, 2), DownloadDismissal(a, 0))))
    }

    @Test fun failedDeletionCanRestoreRowWithoutDroppingNewDownloads() {
        val pending = listOf(DownloadDismissal(b, 1, true))
        assertEquals(listOf(a, c), mergeDismissingDownloads(listOf(a, b, c), pending))
        assertEquals(listOf(a, b, c), mergeDismissingDownloads(listOf(a, b, c), emptyList()))
    }
}
