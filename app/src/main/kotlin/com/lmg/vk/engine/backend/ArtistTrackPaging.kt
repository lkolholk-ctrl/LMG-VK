package com.lmg.vk.engine.backend

/** Respect a known total even when the last page is exactly full. */
internal fun nextArtistTrackOffset(offset: Int, received: Int, total: Int?): Int? {
    if (received == 0) return null
    val next = offset + received
    return next.takeIf { total == null || it < total }
}
