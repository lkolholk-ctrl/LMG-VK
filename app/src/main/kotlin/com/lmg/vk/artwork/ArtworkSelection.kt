package com.lmg.vk.artwork

data class ArtworkSelection(val query: ArtworkQuery, val coverUrl: String?, val isReady: Boolean = true) {
    fun matches(other: ArtworkQuery): Boolean =
        ItunesArtworkMatcher.lookupQuery(query).copy(album = "") ==
            ItunesArtworkMatcher.lookupQuery(other).copy(album = "")
}
