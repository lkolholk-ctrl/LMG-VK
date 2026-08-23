package com.lmg.vk.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "listen_history",
    primaryKeys = ["accountId", "trackId"],
    indices = [Index(value = ["accountId", "playedAt"])],
)
data class ListenHistoryEntity(
    @ColumnInfo(defaultValue = "0") val accountId: Long = 0L,
    val trackId: String,
    val title: String,
    val artist: String,
    val coverUrl: String? = null,
    val durationMs: Long = 0,
    val playedAt: Long = 0,
)
