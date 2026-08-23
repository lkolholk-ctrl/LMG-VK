package com.lmg.vk.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playback_history",
    indices = [
        Index(value = ["accountId", "trackId"]),
        Index(value = ["accountId", "timestamp"]),
    ],
)
data class PlaybackHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(defaultValue = "0") val accountId: Long = 0L,
    val trackId: String,
    val timestamp: Long = 0,
    val playedMs: Long = 0,
    val totalDurationMs: Long = 0,
    val wasSkipped: Int = 0,
    val source: String = "unknown",
)
