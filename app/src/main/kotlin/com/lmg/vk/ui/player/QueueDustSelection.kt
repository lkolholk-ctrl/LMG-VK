package com.lmg.vk.ui.player

internal fun queueDustRemovalIndices(
    queueSize: Int,
    currentIndex: Int,
    visibleLazyIndices: List<Int>,
    autoplayStart: Int,
    headingShown: Boolean,
): Set<Int> = visibleLazyIndices.mapNotNullTo(linkedSetOf()) { lazyIndex ->
    if (headingShown && lazyIndex == autoplayStart) return@mapNotNullTo null
    val index = lazyIndex - if (headingShown && lazyIndex > autoplayStart) 1 else 0
    index.takeIf { it in 0 until queueSize && it > currentIndex }
}
