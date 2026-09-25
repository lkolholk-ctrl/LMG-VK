package com.lmg.vk.ui.effects

import org.junit.Assert.assertEquals
import org.junit.Test

class DustRemovalListTest {
    @Test fun keepsRemovedRowAtOriginalPosition() {
        assertEquals(listOf("a", "b", "c"), retainDustRows(listOf("a", "c"), listOf(DustRetainedItem("b", 1))) { it })
    }

    @Test fun waitingForDeletionDoesNotDuplicateRow() {
        assertEquals(listOf("a", "b"), retainDustRows(listOf("a", "b"), listOf(DustRetainedItem("b", 1))) { it })
    }

    @Test fun retainsLastRowUntilParticlesFinish() {
        assertEquals(listOf("a"), retainDustRows(emptyList(), listOf(DustRetainedItem("a", 0))) { it })
        assertEquals(emptyList<String>(), retainDustRows(emptyList<String>(), emptyList()) { it })
    }

    @Test fun handlesMultipleRemovalsAndNewIncomingRows() {
        assertEquals(listOf("a", "b", "c", "new"), retainDustRows(listOf("a", "new"), listOf(DustRetainedItem("c", 2), DustRetainedItem("b", 1))) { it })
    }
}
