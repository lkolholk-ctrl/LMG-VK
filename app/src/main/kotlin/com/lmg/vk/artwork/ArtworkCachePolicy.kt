package com.lmg.vk.artwork

internal object ArtworkCachePolicy {
    const val LOOKUP_VERSION = 2
    const val MISS_TTL_MS = 15 * 60 * 1_000L

    fun canReuse(positive: Boolean, expires: Long, lookupVersion: Int, now: Long): Boolean =
        expires > now && (positive || lookupVersion == LOOKUP_VERSION)
}
