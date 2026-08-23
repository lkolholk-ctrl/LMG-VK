package com.lmg.vk.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface PlaybackHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackStat(stats: TrackStatsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackStats(stats: List<TrackStatsEntity>)

    @Query("UPDATE track_stats SET playCount = playCount + 1, lastPlayedTimestamp = :timestamp WHERE accountId = :accountId AND trackId = :trackId")
    suspend fun incrementPlayCountRaw(accountId: Long, trackId: String, timestamp: Long)

    @Query("UPDATE track_stats SET skippedCount = skippedCount + 1 WHERE accountId = :accountId AND trackId = :trackId")
    suspend fun incrementSkipCountRaw(accountId: Long, trackId: String)

    @Query("SELECT * FROM track_stats WHERE accountId = :accountId AND trackId = :trackId LIMIT 1")
    suspend fun getTrackStat(accountId: Long, trackId: String): TrackStatsEntity?

    @Query("SELECT * FROM track_stats WHERE accountId = :accountId ORDER BY lastPlayedTimestamp DESC LIMIT :limit")
    suspend fun getAllTrackStats(accountId: Long, limit: Int = 500): List<TrackStatsEntity>

    @Query("SELECT * FROM track_stats WHERE accountId = :accountId AND playCount > 0 ORDER BY playCount DESC LIMIT :limit")
    suspend fun getMostPlayed(accountId: Long, limit: Int = 50): List<TrackStatsEntity>

    @Query("SELECT * FROM track_stats WHERE accountId = :accountId AND skippedCount > 0 ORDER BY skippedCount DESC LIMIT :limit")
    suspend fun getMostSkipped(accountId: Long, limit: Int = 50): List<TrackStatsEntity>

    @Insert
    suspend fun insertHistoryEntry(entry: PlaybackHistoryEntity): Long

    @Query("SELECT trackId FROM playback_history WHERE accountId = :accountId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentTrackIds(accountId: Long, limit: Int): List<String>

    @Query("SELECT * FROM playback_history WHERE accountId = :accountId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentHistory(accountId: Long, limit: Int = 100): List<PlaybackHistoryEntity>

    @Query("SELECT COUNT(*) FROM playback_history WHERE accountId = :accountId AND trackId = :trackId")
    suspend fun getPlayCountForTrack(accountId: Long, trackId: String): Int

    @Query("DELETE FROM playback_history WHERE accountId = :accountId AND timestamp < :olderThanMs")
    suspend fun deleteOldHistory(accountId: Long, olderThanMs: Long): Int

    @Query("DELETE FROM track_stats WHERE accountId = :accountId AND lastPlayedTimestamp < :olderThanMs AND playCount = 0")
    suspend fun deleteStaleStats(accountId: Long, olderThanMs: Long): Int

    @Query("DELETE FROM playback_history WHERE accountId = :accountId")
    suspend fun clearAllHistory(accountId: Long): Int

    @Query("DELETE FROM track_stats WHERE accountId = :accountId")
    suspend fun clearAllStats(accountId: Long): Int

    @Transaction
    suspend fun logPlaybackEvent(
        accountId: Long,
        trackId: String,
        title: String,
        artistId: String? = null,
        playedMs: Long,
        totalDurationMs: Long,
        wasSkipped: Boolean,
        source: String,
    ) {
        val now = System.currentTimeMillis()
        insertHistoryEntry(
            PlaybackHistoryEntity(
                accountId = accountId,
                trackId = trackId,
                timestamp = now,
                playedMs = playedMs,
                totalDurationMs = totalDurationMs,
                wasSkipped = if (wasSkipped) 1 else 0,
                source = source,
            ),
        )
        val existing = getTrackStat(accountId, trackId)
        if (existing != null) {
            if (wasSkipped) {
                incrementSkipCountRaw(accountId, trackId)
            } else {
                incrementPlayCountRaw(accountId, trackId, now)
            }
        } else {
            insertTrackStat(
                TrackStatsEntity(
                    accountId = accountId,
                    trackId = trackId,
                    title = title,
                    artistId = artistId ?: "",
                    playCount = if (wasSkipped) 0 else 1,
                    skippedCount = if (wasSkipped) 1 else 0,
                    lastPlayedTimestamp = if (wasSkipped) 0L else now,
                ),
            )
        }
    }

    @Transaction
    suspend fun incrementPlayCount(
        accountId: Long,
        trackId: String,
        title: String,
        artistId: String?,
        timestamp: Long,
    ) {
        val existing = getTrackStat(accountId, trackId)
        if (existing != null) {
            incrementPlayCountRaw(accountId, trackId, timestamp)
        } else {
            insertTrackStat(
                TrackStatsEntity(
                    accountId = accountId,
                    trackId = trackId,
                    title = title,
                    artistId = artistId ?: "",
                    playCount = 1,
                    lastPlayedTimestamp = timestamp,
                ),
            )
        }
    }

    @Transaction
    suspend fun incrementSkipCount(
        accountId: Long,
        trackId: String,
        title: String,
        artistId: String?,
    ) {
        val existing = getTrackStat(accountId, trackId)
        if (existing != null) {
            incrementSkipCountRaw(accountId, trackId)
        } else {
            insertTrackStat(
                TrackStatsEntity(
                    accountId = accountId,
                    trackId = trackId,
                    title = title,
                    artistId = artistId ?: "",
                    skippedCount = 1,
                ),
            )
        }
    }

    @Query("SELECT artistId FROM track_stats WHERE accountId = :accountId AND playCount > 0 AND artistId IS NOT NULL AND artistId != '' GROUP BY artistId ORDER BY SUM(playCount) DESC LIMIT :limit")
    suspend fun getTopArtists(accountId: Long, limit: Int = 5): List<String>

    @Query("SELECT genre FROM listening_history WHERE accountId = :accountId AND genre IS NOT NULL AND genre != '' GROUP BY genre ORDER BY COUNT(*) DESC LIMIT :limit")
    suspend fun getTopGenres(accountId: Long, limit: Int = 3): List<String>

    @Query("SELECT COALESCE(SUM(durationPlayedMs), 0) FROM listening_history WHERE accountId = :accountId")
    suspend fun getTotalListenedMs(accountId: Long): Long

    @Query("SELECT COUNT(*) FROM listening_history WHERE accountId = :accountId")
    suspend fun getTotalPlayEvents(accountId: Long): Int

    @Query("SELECT COUNT(DISTINCT trackId) FROM listening_history WHERE accountId = :accountId")
    suspend fun getDistinctTrackCount(accountId: Long): Int

    @Query("SELECT COUNT(DISTINCT artist) FROM listening_history WHERE accountId = :accountId AND artist IS NOT NULL AND artist != ''")
    suspend fun getDistinctArtistCount(accountId: Long): Int

    @Query("SELECT artist AS artist, COUNT(*) AS plays, SUM(durationPlayedMs) AS listenedMs FROM listening_history WHERE accountId = :accountId AND artist IS NOT NULL AND artist != '' GROUP BY artist ORDER BY plays DESC, listenedMs DESC LIMIT :limit")
    suspend fun getTopArtistsDetailed(accountId: Long, limit: Int = 8): List<ArtistPlayStat>

    @Query("SELECT lh.trackId AS trackId, lh.title AS title, lh.artist AS artist, COUNT(*) AS plays, SUM(lh.durationPlayedMs) AS listenedMs FROM listening_history lh WHERE lh.accountId = :accountId AND lh.title IS NOT NULL AND lh.title != '' GROUP BY lh.trackId ORDER BY plays DESC, listenedMs DESC LIMIT :limit")
    suspend fun getTopTracksDetailed(accountId: Long, limit: Int = 10): List<TrackPlayStat>

    @Query("UPDATE OR IGNORE playback_history SET accountId = :accountId WHERE accountId = 0")
    suspend fun claimLegacyHistory(accountId: Long)

    @Query("UPDATE OR IGNORE track_stats SET accountId = :accountId WHERE accountId = 0")
    suspend fun claimLegacyStats(accountId: Long)

    @Query("DELETE FROM playback_history WHERE accountId = 0")
    suspend fun deleteLegacyHistory()

    @Query("DELETE FROM track_stats WHERE accountId = 0")
    suspend fun deleteLegacyStats()
}

data class ArtistPlayStat(
    val artist: String,
    val plays: Int,
    val listenedMs: Long,
)

data class TrackPlayStat(
    val trackId: String,
    val title: String,
    val artist: String,
    val plays: Int,
    val listenedMs: Long,
)
