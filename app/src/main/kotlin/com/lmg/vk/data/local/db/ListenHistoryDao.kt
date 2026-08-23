package com.lmg.vk.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ListenHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: ListenHistoryEntity)

    @Query("SELECT * FROM listen_history WHERE accountId = :accountId ORDER BY playedAt DESC LIMIT :limit")
    fun observe(accountId: Long, limit: Int = 300): Flow<List<ListenHistoryEntity>>

    @Query("DELETE FROM listen_history WHERE accountId = :accountId")
    suspend fun clear(accountId: Long)

    @Query("UPDATE OR IGNORE listen_history SET accountId = :accountId WHERE accountId = 0")
    suspend fun claimLegacy(accountId: Long)

    @Query("DELETE FROM listen_history WHERE accountId = 0")
    suspend fun deleteLegacy()
}
