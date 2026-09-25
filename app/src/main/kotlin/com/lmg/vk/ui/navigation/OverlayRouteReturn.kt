package com.lmg.vk.ui.navigation

internal data class OverlayRouteReturn<T>(
    val suspendedOverlays: List<T>,
    val originEntryId: String,
    val destinationEntered: Boolean = false,
) {
    fun visibleOverlays(stack: List<T>): List<T> = stack.filterNot { it in suspendedOverlays }

    fun followEntry(entryId: String?): OverlayRouteReturn<T>? = when {
        entryId == null -> this
        entryId != originEntryId -> if (destinationEntered) this else copy(destinationEntered = true)
        destinationEntered -> null
        else -> this
    }
}
