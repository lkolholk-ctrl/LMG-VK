package com.lmg.vk.engine

/**
 * VK wire values from `MixCategoryType` in the official client.
 *
 * Hidden categories still take part in the `options` request, but the settings
 * sheet does not render them.
 */
enum class VkMixCategoryType(val wireValue: String) {
    BUTTONS("button_horizontal_group"),
    ICONS("pictured_button_horizontal_group"),
    HIDDEN("hidden_button_horizontal_group"),
    ;

    companion object {
        fun fromWire(value: String): VkMixCategoryType =
            entries.firstOrNull { it.wireValue == value } ?: BUTTONS
    }
}

data class VkMixOption(
    val id: String,
    val title: String,
    val icon: String,
    val badgeIconUrl: String?,
    val isSelected: Boolean,
)

data class VkMixCategory(
    val id: String,
    val title: String,
    val type: VkMixCategoryType,
    val options: List<VkMixOption>,
)

/**
 * Tunable VK Mix settings.
 *
 * Official VK's `multi_select` does not permit several options inside one
 * category. It controls whether different visible categories may remain
 * selected simultaneously. Tapping the selected option turns that category
 * off; hidden categories are preserved by the editor.
 */
data class VkMixSettings(
    val title: String,
    val subtitle: String,
    val multiSelect: Boolean,
    val categories: List<VkMixCategory>,
) {
    fun selectedOptions(): Map<String, List<String>> = buildMap {
        categories.forEach { category ->
            category.options
                .firstOrNull(VkMixOption::isSelected)
                ?.id
                ?.let { put(category.id, listOf(it)) }
        }
    }

    fun hasVisibleSelection(): Boolean = categories.any { category ->
        category.type != VkMixCategoryType.HIDDEN &&
            category.options.any(VkMixOption::isSelected)
    }

    fun clearVisibleSelections(): VkMixSettings = copy(
        categories = categories.map { category ->
            if (category.type == VkMixCategoryType.HIDDEN) category
            else category.copy(options = category.options.map { it.copy(isSelected = false) })
        },
    )

    fun toggle(categoryId: String, optionId: String): VkMixSettings {
        val targetWasSelected = categories
            .firstOrNull { it.id == categoryId }
            ?.options
            ?.firstOrNull { it.id == optionId }
            ?.isSelected == true

        val base = if (multiSelect) {
            categories
        } else {
            // The VK editor excludes HIDDEN categories from its working map,
            // therefore its global single-select clear leaves them untouched.
            categories.map { category ->
                if (category.type == VkMixCategoryType.HIDDEN) category
                else category.copy(options = category.options.map { it.copy(isSelected = false) })
            }
        }

        return copy(
            categories = base.map { category ->
                if (category.id != categoryId || category.type == VkMixCategoryType.HIDDEN) {
                    category
                } else {
                    category.copy(
                        options = category.options.map { option ->
                            option.copy(
                                isSelected = !targetWasSelected && option.id == optionId,
                            )
                        },
                    )
                }
            },
        )
    }
}

/** Relevant fields of official VK's `StartPlayVkMixSource`. */
data class VkMixSession(
    val blockId: String = "",
    val sectionId: String = "",
    val mixId: String,
    val isTunable: Boolean,
    val title: String,
    val settings: VkMixSettings?,
    val entityId: String? = null,
    val catalogItemId: String? = null,
    val id: String? = null,
    val sourceRes: Int? = null,
    val mixOptionsId: Long? = null,
    val sourceRef: String = "vk_mix",
    val options: Map<String, List<String>> = settings?.selectedOptions().orEmpty(),
)
