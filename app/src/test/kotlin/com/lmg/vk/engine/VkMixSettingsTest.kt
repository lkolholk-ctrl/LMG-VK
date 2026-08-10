package com.lmg.vk.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class VkMixSettingsTest {
    @Test
    fun singleSelectClearsOtherVisibleCategoriesButKeepsHidden() {
        val changed = settings(multiSelect = false).toggle("energy", "high")

        assertFalse(changed.option("mood", "calm").isSelected)
        assertTrue(changed.option("energy", "high").isSelected)
        assertTrue(changed.option("hidden", "server_seed").isSelected)
    }

    @Test
    fun multiSelectKeepsSelectionsAcrossVisibleCategories() {
        val changed = settings(multiSelect = true).toggle("energy", "high")

        assertTrue(changed.option("mood", "calm").isSelected)
        assertTrue(changed.option("energy", "high").isSelected)
    }

    @Test
    fun categoryAlwaysKeepsAtMostOneSelectedOption() {
        val changed = settings(multiSelect = true).toggle("mood", "focus")

        val selected = changed.categories
            .first { it.id == "mood" }
            .options
            .filter(VkMixOption::isSelected)
        assertEquals(listOf("focus"), selected.map(VkMixOption::id))
    }

    @Test
    fun tappingSelectedOptionTurnsItOff() {
        val changed = settings(multiSelect = true).toggle("mood", "calm")

        assertFalse(changed.option("mood", "calm").isSelected)
        assertFalse(changed.option("mood", "focus").isSelected)
    }

    @Test
    fun resetOnlyClearsVisibleCategories() {
        val changed = settings(multiSelect = true)
            .toggle("energy", "high")
            .clearVisibleSelections()

        assertFalse(changed.hasVisibleSelection())
        assertTrue(changed.option("hidden", "server_seed").isSelected)
    }

    @Test
    fun selectedOptionsUsesOfficialCategoryToArrayShape() {
        val selected = settings(multiSelect = true)
            .toggle("energy", "high")
            .selectedOptions()

        assertEquals(
            mapOf(
                "mood" to listOf("calm"),
                "energy" to listOf("high"),
                "hidden" to listOf("server_seed"),
            ),
            selected,
        )
    }

    @Test
    fun playbackContextKeepsTheWholeMixSession() {
        val mixSettings = settings(multiSelect = true)
        val session = VkMixSession(
            blockId = "block",
            sectionId = "section",
            mixId = "common",
            isTunable = true,
            title = "My Mix",
            settings = mixSettings,
            entityId = "entity",
            catalogItemId = "catalog-item",
            id = "source-id",
            sourceRes = 42,
            mixOptionsId = 123456L,
            options = mixSettings.selectedOptions(),
        )

        val context = PlaybackContext.VkMix(session)

        assertSame(session, context.session)
        assertEquals(123456L, context.session.mixOptionsId ?: -1L)
        assertEquals(listOf("calm"), context.session.options["mood"])
        assertEquals("common", context.mixId)
        assertEquals("entity", context.entityId)
    }

    private fun settings(multiSelect: Boolean) = VkMixSettings(
        title = "Tune",
        subtitle = "Choose",
        multiSelect = multiSelect,
        categories = listOf(
            VkMixCategory(
                id = "mood",
                title = "Mood",
                type = VkMixCategoryType.BUTTONS,
                options = listOf(
                    option("calm", selected = true),
                    option("focus"),
                ),
            ),
            VkMixCategory(
                id = "energy",
                title = "Energy",
                type = VkMixCategoryType.ICONS,
                options = listOf(option("high")),
            ),
            VkMixCategory(
                id = "hidden",
                title = "Server state",
                type = VkMixCategoryType.HIDDEN,
                options = listOf(option("server_seed", selected = true)),
            ),
        ),
    )

    private fun option(id: String, selected: Boolean = false) = VkMixOption(
        id = id,
        title = id,
        icon = "",
        badgeIconUrl = null,
        isSelected = selected,
    )

    private fun VkMixSettings.option(categoryId: String, optionId: String): VkMixOption =
        categories.first { it.id == categoryId }.options.first { it.id == optionId }
}
