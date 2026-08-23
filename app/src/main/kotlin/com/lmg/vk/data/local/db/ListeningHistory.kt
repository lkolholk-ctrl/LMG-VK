package com.lmg.vk.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "listening_history",
    indices = [
        Index(value = ["accountId", "trackId"]),
        Index(value = ["accountId", "genre"]),
        Index(value = ["accountId", "timestamp"]),
        Index(value = ["accountId", "source"]),
    ],
)
data class ListeningHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(defaultValue = "0") val accountId: Long = 0L,
    val trackId: String,
    val title: String,
    val artist: String,
    val genre: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val source: String = "UNKNOWN",
    val durationPlayedMs: Long = 0,
)
