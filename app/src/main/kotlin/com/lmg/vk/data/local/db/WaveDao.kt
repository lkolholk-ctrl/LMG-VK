package com.lmg.vk.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WaveDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTrack(track: CachedTrack): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTracks(tracks: List<CachedTrack>): List<Long>

    @Query("SELECT * FROM cached_tracks WHERE accountId = :accountId AND id = :trackId LIMIT 1")
    fun getTrackById(accountId: Long, trackId: String): CachedTrack?

    @Query("SELECT * FROM cached_tracks WHERE accountId = :accountId AND genre IN (:genres) ORDER BY cachedAt DESC LIMIT :limit")
    fun getTracksByGenres(accountId: Long, genres: List<String>, limit: Int = 50): List<CachedTrack>

    @Query("SELECT * FROM cached_tracks WHERE accountId = :accountId AND isFavorite = 1 ORDER BY cachedAt DESC")
    fun getFavoriteTracksFlow(accountId: Long): Flow<List<CachedTrack>>

    @Query("SELECT id FROM cached_tracks WHERE accountId = :accountId AND (isFavorite = 1 OR source = 'FAVORITES') ORDER BY RANDOM() LIMIT 1")
    fun getRandomFavoriteTrackId(accountId: Long): String?

    @Query("SELECT * FROM cached_tracks WHERE accountId = :accountId AND isDownloaded = 1 ORDER BY cachedAt DESC")
    fun getDownloadedTracksFlow(accountId: Long): Flow<List<CachedTrack>>

    @Query("DELETE FROM cached_tracks WHERE accountId = :accountId AND id = :trackId")
    fun deleteTrack(accountId: Long, trackId: String): Int

    @Query("DELETE FROM cached_tracks WHERE accountId = :accountId AND cachedAt < :olderThanMs")
    fun deleteOldTracks(accountId: Long, olderThanMs: Long): Int

    @Insert
    fun insertListeningRecord(record: ListeningHistory): Long

    @Query("""
        SELECT genre, COUNT(*) as count
        FROM listening_history
        WHERE accountId = :accountId AND timestamp > :sinceMs
          AND genre IS NOT NULL
          AND genre != ''
        GROUP BY genre
        ORDER BY count DESC
        LIMIT :limit
    """)
    fun getTopGenres(accountId: Long, sinceMs: Long, limit: Int = 10): List<GenreCount>

    @Query("""
        SELECT genre, COUNT(*) as count
        FROM listening_history
        WHERE accountId = :accountId AND genre IS NOT NULL
          AND genre != ''
        GROUP BY genre
        ORDER BY count DESC
        LIMIT :limit
    """)
    fun getTopGenresAllTime(accountId: Long, limit: Int = 10): List<GenreCount>

    @Query("SELECT * FROM listening_history WHERE accountId = :accountId AND trackId = :trackId ORDER BY timestamp DESC LIMIT 1")
    fun getLastListen(accountId: Long, trackId: String): ListeningHistory?

    @Query("SELECT COUNT(*) FROM listening_history WHERE accountId = :accountId AND timestamp > :sinceMs")
    fun getRecentListenCount(accountId: Long, sinceMs: Long): Int

    @Query("DELETE FROM listening_history WHERE accountId = :accountId AND timestamp < :olderThanMs")
    fun deleteOldHistory(accountId: Long, olderThanMs: Long): Int

    @Query("SELECT COUNT(DISTINCT trackId) FROM listening_history WHERE accountId = :accountId AND timestamp > :sinceMs")
    fun getUniqueTracksCount(accountId: Long, sinceMs: Long): Int

    @Query("SELECT SUM(durationPlayedMs) FROM listening_history WHERE accountId = :accountId AND timestamp > :sinceMs")
    fun getTotalListenTimeMs(accountId: Long, sinceMs: Long): Long?

    @Query("UPDATE listening_history SET accountId = :accountId WHERE accountId = 0")
    suspend fun claimLegacyHistory(accountId: Long)

    @Query("UPDATE OR IGNORE cached_tracks SET accountId = :accountId WHERE accountId = 0")
    suspend fun claimLegacyTracks(accountId: Long)

    @Query("DELETE FROM cached_tracks WHERE accountId = 0")
    suspend fun deleteLegacyTracks()
}
