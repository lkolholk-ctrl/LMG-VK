package com.lmg.vk.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "cached_tracks",
    primaryKeys = ["accountId", "id"],
    indices = [
        Index(value = ["accountId", "genre"]),
        Index(value = ["accountId", "isFavorite"]),
        Index(value = ["accountId", "isDownloaded"]),
        Index(value = ["accountId", "source"]),
    ],
)
data class CachedTrack(
    @ColumnInfo(defaultValue = "0") val accountId: Long = 0L,
    val id: String,
    val title: String,
    val artist: String,
    val genre: String? = null,
    val streamUrl: String? = null,
    val coverUrl: String? = null,
    val durationMs: Long = 0L,
    val isDownloaded: Boolean = false,
    val isFavorite: Boolean = false,
    val source: String = "UNKNOWN",
    val cachedAt: Long = System.currentTimeMillis(),
)
