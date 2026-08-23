package com.lmg.vk.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "track_stats",
    primaryKeys = ["accountId", "trackId"],
    indices = [
        Index(value = ["accountId", "artistId"]),
        Index(value = ["accountId", "playCount"]),
        Index(value = ["accountId", "skippedCount"]),
        Index(value = ["accountId", "lastPlayedTimestamp"]),
    ],
)
data class TrackStatsEntity(
    @ColumnInfo(defaultValue = "0") val accountId: Long = 0L,
    val trackId: String,
    val title: String,
    val artistId: String? = null,
    val playCount: Int = 0,
    val skippedCount: Int = 0,
    val lastPlayedTimestamp: Long = 0L,
)
