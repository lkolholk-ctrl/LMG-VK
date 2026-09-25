package com.lmg.vk.ui.screens

internal data class DownloadDismissal(
    val item: DownloadedItem,
    val index: Int,
    val finished: Boolean = false,
)

internal fun mergeDismissingDownloads(
    downloads: List<DownloadedItem>,
    dismissals: Collection<DownloadDismissal>,
): List<DownloadedItem> {
    if (dismissals.isEmpty()) return downloads
    val result = downloads.toMutableList()
    val ids = downloads.mapTo(hashSetOf()) { it.trackId }
    dismissals.sortedBy { it.index }.forEach { pending ->
        if (!pending.finished && ids.add(pending.item.trackId)) {
            result.add(pending.index.coerceIn(0, result.size), pending.item)
        }
    }
    val finished = dismissals.filter { it.finished }.mapTo(hashSetOf()) { it.item.trackId }
    return result.filterNot { it.trackId in finished }
}
