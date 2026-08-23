package com.lmg.vk.data.local.db

/**
 * Entity for storing liked/favorite tracks locally.
 * Fields mirror LibraryTrack for seamless mapping.
 */
data class FavoriteTrackEntity(
    val id: Long = 0,
    val accountId: Long = 0L,
    val trackId: String,
    val title: String,
    val artistName: String? = null,
    val albumTitle: String? = null,
    val durationMs: Long = 0,
    val genre: String? = null,
    val imageUrl: String? = null,
    val streamUrl: String? = null,
    val artistId: String? = null,
    val collectionId: String? = null,
    val isExplicit: Boolean = false,
    val source: String? = null,
    val isAvailable: Boolean = true,
    val accessKey: String? = null,
    val likedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val pendingDelete: Boolean = false,
    val cloudTrackId: String? = null,
)
