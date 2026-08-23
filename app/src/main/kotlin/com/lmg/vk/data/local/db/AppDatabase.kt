package com.lmg.vk.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Database(
    entities = [
        CachedTrack::class,
        ListeningHistory::class,
        TrackStatsEntity::class,
        PlaybackHistoryEntity::class,
        ListenHistoryEntity::class,
        LocalTrackEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun waveDao(): WaveDao
    abstract fun playbackHistoryDao(): PlaybackHistoryDao
    abstract fun listenHistoryDao(): ListenHistoryDao
    abstract fun localTracksDao(): LocalTracksDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        @Volatile
        private var ACTIVE_ACCOUNT_ID = 0L

        private const val DB_NAME = "liquid_music_glass.db"
        private val ACCOUNT_SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `local_tracks` (" +
                        "`id` TEXT NOT NULL, `mediaStoreId` INTEGER NOT NULL, `title` TEXT NOT NULL, " +
                        "`artist` TEXT NOT NULL, `albumName` TEXT NOT NULL, `albumId` INTEGER NOT NULL, " +
                        "`durationMs` INTEGER NOT NULL, `trackNumber` INTEGER NOT NULL, `year` INTEGER NOT NULL, " +
                        "`dateAddedSec` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_tracks_artist` ON `local_tracks` (`artist`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_tracks_albumId` ON `local_tracks` (`albumId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_tracks_title` ON `local_tracks` (`title`)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cached_tracks_v5` (" +
                        "`accountId` INTEGER NOT NULL DEFAULT 0, `id` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, `artist` TEXT NOT NULL, `genre` TEXT, " +
                        "`streamUrl` TEXT, `coverUrl` TEXT, `durationMs` INTEGER NOT NULL, " +
                        "`isDownloaded` INTEGER NOT NULL, `isFavorite` INTEGER NOT NULL, " +
                        "`source` TEXT NOT NULL, `cachedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`accountId`, `id`))",
                )
                db.execSQL(
                    "INSERT INTO `cached_tracks_v5` (`accountId`, `id`, `title`, `artist`, `genre`, " +
                        "`streamUrl`, `coverUrl`, `durationMs`, `isDownloaded`, `isFavorite`, " +
                        "`source`, `cachedAt`) SELECT 0, `id`, `title`, `artist`, `genre`, " +
                        "`streamUrl`, `coverUrl`, `durationMs`, `isDownloaded`, `isFavorite`, " +
                        "`source`, `cachedAt` FROM `cached_tracks`",
                )
                db.execSQL("DROP TABLE `cached_tracks`")
                db.execSQL("ALTER TABLE `cached_tracks_v5` RENAME TO `cached_tracks`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_tracks_accountId_genre` ON `cached_tracks` (`accountId`, `genre`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_tracks_accountId_isFavorite` ON `cached_tracks` (`accountId`, `isFavorite`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_tracks_accountId_isDownloaded` ON `cached_tracks` (`accountId`, `isDownloaded`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_tracks_accountId_source` ON `cached_tracks` (`accountId`, `source`)")

                db.execSQL("ALTER TABLE `listening_history` ADD COLUMN `accountId` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("DROP INDEX IF EXISTS `index_listening_history_trackId`")
                db.execSQL("DROP INDEX IF EXISTS `index_listening_history_genre`")
                db.execSQL("DROP INDEX IF EXISTS `index_listening_history_timestamp`")
                db.execSQL("DROP INDEX IF EXISTS `index_listening_history_source`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_listening_history_accountId_trackId` ON `listening_history` (`accountId`, `trackId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_listening_history_accountId_genre` ON `listening_history` (`accountId`, `genre`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_listening_history_accountId_timestamp` ON `listening_history` (`accountId`, `timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_listening_history_accountId_source` ON `listening_history` (`accountId`, `source`)")

                db.execSQL("ALTER TABLE `playback_history` ADD COLUMN `accountId` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("DROP INDEX IF EXISTS `index_playback_history_trackId`")
                db.execSQL("DROP INDEX IF EXISTS `index_playback_history_timestamp`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_history_accountId_trackId` ON `playback_history` (`accountId`, `trackId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_history_accountId_timestamp` ON `playback_history` (`accountId`, `timestamp`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `track_stats_v5` (" +
                        "`accountId` INTEGER NOT NULL DEFAULT 0, `trackId` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, `artistId` TEXT, `playCount` INTEGER NOT NULL, " +
                        "`skippedCount` INTEGER NOT NULL, `lastPlayedTimestamp` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`accountId`, `trackId`))",
                )
                db.execSQL(
                    "INSERT INTO `track_stats_v5` (`accountId`, `trackId`, `title`, `artistId`, " +
                        "`playCount`, `skippedCount`, `lastPlayedTimestamp`) SELECT 0, `trackId`, " +
                        "`title`, `artistId`, `playCount`, `skippedCount`, `lastPlayedTimestamp` " +
                        "FROM `track_stats`",
                )
                db.execSQL("DROP TABLE `track_stats`")
                db.execSQL("ALTER TABLE `track_stats_v5` RENAME TO `track_stats`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_track_stats_accountId_artistId` ON `track_stats` (`accountId`, `artistId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_track_stats_accountId_playCount` ON `track_stats` (`accountId`, `playCount`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_track_stats_accountId_skippedCount` ON `track_stats` (`accountId`, `skippedCount`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_track_stats_accountId_lastPlayedTimestamp` ON `track_stats` (`accountId`, `lastPlayedTimestamp`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `listen_history_v5` (" +
                        "`accountId` INTEGER NOT NULL DEFAULT 0, `trackId` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, `artist` TEXT NOT NULL, `coverUrl` TEXT, " +
                        "`durationMs` INTEGER NOT NULL, `playedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`accountId`, `trackId`))",
                )
                db.execSQL(
                    "INSERT INTO `listen_history_v5` (`accountId`, `trackId`, `title`, `artist`, " +
                        "`coverUrl`, `durationMs`, `playedAt`) SELECT 0, `trackId`, `title`, " +
                        "`artist`, `coverUrl`, `durationMs`, `playedAt` FROM `listen_history`",
                )
                db.execSQL("DROP TABLE `listen_history`")
                db.execSQL("ALTER TABLE `listen_history_v5` RENAME TO `listen_history`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_listen_history_accountId_playedAt` ON `listen_history` (`accountId`, `playedAt`)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { database ->
                    INSTANCE = database
                    claimLegacy(database, ACTIVE_ACCOUNT_ID)
                }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DB_NAME
            )
                .openHelperFactory(RequerySQLiteOpenHelperFactory())
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build()
        }

        fun activateAccount(userId: Long) {
            ACTIVE_ACCOUNT_ID = userId.coerceAtLeast(0L)
            INSTANCE?.let { claimLegacy(it, ACTIVE_ACCOUNT_ID) }
        }

        fun activeAccountId(): Long = ACTIVE_ACCOUNT_ID

        private fun claimLegacy(database: AppDatabase, accountId: Long) {
            if (accountId == 0L) return
            ACCOUNT_SCOPE.launch {
                database.withTransaction {
                    database.listenHistoryDao().claimLegacy(accountId)
                    database.listenHistoryDao().deleteLegacy()
                    database.waveDao().claimLegacyHistory(accountId)
                    database.waveDao().claimLegacyTracks(accountId)
                    database.waveDao().deleteLegacyTracks()
                    database.playbackHistoryDao().claimLegacyHistory(accountId)
                    database.playbackHistoryDao().claimLegacyStats(accountId)
                    database.playbackHistoryDao().deleteLegacyHistory()
                    database.playbackHistoryDao().deleteLegacyStats()
                }
            }
        }

        fun destroyInstance() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
