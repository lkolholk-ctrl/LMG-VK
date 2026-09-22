package com.lmg.vk.artwork

data class ArtworkLoadState(val isReady: Boolean, val coverUrl: String?) {
    companion object {
        val Pending = ArtworkLoadState(false, null)

        fun initial(query: ArtworkQuery?, fallback: String?, cached: ArtworkLoadState?): ArtworkLoadState =
            if (query?.usable != true) ArtworkLoadState(true, fallback)
            else cached?.let { it.copy(coverUrl = it.coverUrl ?: fallback) } ?: Pending
    }
}
