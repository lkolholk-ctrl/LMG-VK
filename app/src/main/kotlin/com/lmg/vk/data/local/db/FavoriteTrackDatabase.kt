package com.lmg.vk.data.local.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * SQLite-backed database for storing favorite tracks locally and tracking downloads.
 * Uses raw SQLite instead of Room to avoid KSP/kapt annotation processor issues.
 */
class FavoriteTrackDatabase private constructor(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DB_NAME,
    null,
    DB_VERSION
) {

    private val _favoritesFlow = MutableStateFlow<List<FavoriteTrackEntity>>(emptyList())
    val favoritesFlow: Flow<List<FavoriteTrackEntity>> = _favoritesFlow

    private val _favoriteIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIdsFlow: Flow<Set<String>> = _favoriteIdsFlow

    // ConcurrentHashMap (P0/P1, аудит): getOrPut дёргается с main (composition в
    // FullPlayer/QueueSheet/мини-плеере), а forEach в reloadFavorites — с IO.
    // Обычная HashMap давала гонку (CME/потерянные записи).
    private val _favoriteStatusFlows = java.util.concurrent.ConcurrentHashMap<String, MutableStateFlow<Boolean>>()

    // --- Downloaded Tracks flows ---
    private val _downloadsFlow = MutableStateFlow<List<DownloadedTrackEntity>>(emptyList())
    val downloadsFlow: Flow<List<DownloadedTrackEntity>> = _downloadsFlow

    private val _downloadedIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    val downloadedIdsFlow: Flow<Set<String>> = _downloadedIdsFlow

    private val _downloadStatusFlows = java.util.concurrent.ConcurrentHashMap<String, MutableStateFlow<Boolean>>()

    @Volatile
    private var isLoaded = false

    /**
     * Load data from SQLite asynchronously. Must be called from IO dispatcher.
     * Safe to call multiple times — subsequent calls are no-ops.
     */
    fun loadAsync() {
        if (isLoaded) return
        synchronized(this) {
            if (isLoaded) return
            reloadFavorites()
            reloadDownloads()
            isLoaded = true
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS favorite_tracks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                accountId INTEGER NOT NULL DEFAULT 0,
                trackId TEXT NOT NULL,
                cloudTrackId TEXT,
                title TEXT NOT NULL,
                artistName TEXT,
                albumTitle TEXT,
                durationMs INTEGER DEFAULT 0,
                genre TEXT,
                imageUrl TEXT,
                streamUrl TEXT,
                artistId TEXT,
                collectionId TEXT,
                isExplicit INTEGER DEFAULT 0,
                source TEXT,
                isAvailable INTEGER DEFAULT 1,
                accessKey TEXT,
                likedAt INTEGER DEFAULT 0,
                isSynced INTEGER DEFAULT 0,
                pendingDelete INTEGER DEFAULT 0,
                UNIQUE(accountId, trackId)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_track_id ON favorite_tracks(accountId, trackId)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS idx_cloud_track_id " +
                "ON favorite_tracks(accountId, cloudTrackId) WHERE cloudTrackId IS NOT NULL",
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS downloaded_tracks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                trackId TEXT NOT NULL UNIQUE,
                title TEXT NOT NULL,
                artistName TEXT,
                albumTitle TEXT,
                durationMs INTEGER DEFAULT 0,
                imageUrl TEXT,
                localPath TEXT NOT NULL,
                localCoverPath TEXT,
                quality TEXT,
                downloadedAt INTEGER DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_downloaded_track_id ON downloaded_tracks(trackId)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS downloaded_tracks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    trackId TEXT NOT NULL UNIQUE,
                    title TEXT NOT NULL,
                    artistName TEXT,
                    albumTitle TEXT,
                    durationMs INTEGER DEFAULT 0,
                    imageUrl TEXT,
                    localPath TEXT NOT NULL,
                    localCoverPath TEXT,
                    quality TEXT,
                    downloadedAt INTEGER DEFAULT 0
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_downloaded_track_id ON downloaded_tracks(trackId)")
        }
        if (oldVersion < 3 && !db.hasColumn("downloaded_tracks", "quality")) {
            // Migration: add quality column to existing downloaded_tracks table
            db.execSQL("ALTER TABLE downloaded_tracks ADD COLUMN quality TEXT")
        }
        if (oldVersion < 4 && !db.hasColumn("downloaded_tracks", "localCoverPath")) {
            // Migration: add localCoverPath column to existing downloaded_tracks table
            db.execSQL("ALTER TABLE downloaded_tracks ADD COLUMN localCoverPath TEXT")
        }
        if (oldVersion < 5 && !db.hasColumn("favorite_tracks", "isAvailable")) {
            db.execSQL("ALTER TABLE favorite_tracks ADD COLUMN isAvailable INTEGER DEFAULT 1")
        }
        // v6: accessKey. Без него audio.getById отдаёт трек без поля url, и
        // музыка из библиотеки играла только пока трек лежал в памяти после
        // поиска/каталога. У существующих записей колонка останется NULL —
        // ключ подставится при первом успешном резолве (см. LibraryRepository).
        if (oldVersion < 6 && !db.hasColumn("favorite_tracks", "accessKey")) {
            db.execSQL("ALTER TABLE favorite_tracks ADD COLUMN accessKey TEXT")
        }
        if (oldVersion < 7) {
            if (!db.hasColumn("favorite_tracks", "cloudTrackId")) {
                db.execSQL("ALTER TABLE favorite_tracks ADD COLUMN cloudTrackId TEXT")
                // Уже подтверждённые облаком строки раньше хранили облачный id прямо
                // в trackId. Сохраняем эту связь при миграции; pending-вставки
                // останутся NULL и будут согласованы следующим syncWithCloud.
                db.execSQL("UPDATE favorite_tracks SET cloudTrackId = trackId WHERE isSynced = 1")
            }
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_cloud_track_id ON favorite_tracks(cloudTrackId)")
        }
        if (oldVersion < 8) {
            db.execSQL("DROP INDEX IF EXISTS idx_track_id")
            db.execSQL("DROP INDEX IF EXISTS idx_cloud_track_id")
            db.execSQL(
                """
                CREATE TABLE favorite_tracks_v8 (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    accountId INTEGER NOT NULL DEFAULT 0,
                    trackId TEXT NOT NULL,
                    cloudTrackId TEXT,
                    title TEXT NOT NULL,
                    artistName TEXT,
                    albumTitle TEXT,
                    durationMs INTEGER DEFAULT 0,
                    genre TEXT,
                    imageUrl TEXT,
                    streamUrl TEXT,
                    artistId TEXT,
                    collectionId TEXT,
                    isExplicit INTEGER DEFAULT 0,
                    source TEXT,
                    isAvailable INTEGER DEFAULT 1,
                    accessKey TEXT,
                    likedAt INTEGER DEFAULT 0,
                    isSynced INTEGER DEFAULT 0,
                    pendingDelete INTEGER DEFAULT 0,
                    UNIQUE(accountId, trackId)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO favorite_tracks_v8 (
                    id, accountId, trackId, cloudTrackId, title, artistName, albumTitle,
                    durationMs, genre, imageUrl, streamUrl, artistId, collectionId,
                    isExplicit, source, isAvailable, accessKey, likedAt, isSynced, pendingDelete
                )
                SELECT id, 0, trackId, cloudTrackId, title, artistName, albumTitle,
                    durationMs, genre, imageUrl, streamUrl, artistId, collectionId,
                    isExplicit, source, isAvailable, accessKey, likedAt, isSynced, pendingDelete
                FROM favorite_tracks
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE favorite_tracks")
            db.execSQL("ALTER TABLE favorite_tracks_v8 RENAME TO favorite_tracks")
            db.execSQL("CREATE INDEX idx_track_id ON favorite_tracks(accountId, trackId)")
            db.execSQL(
                "CREATE UNIQUE INDEX idx_cloud_track_id " +
                    "ON favorite_tracks(accountId, cloudTrackId) WHERE cloudTrackId IS NOT NULL",
            )
        }
    }

    private fun SQLiteDatabase.hasColumn(table: String, column: String): Boolean =
        rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            generateSequence { if (cursor.moveToNext()) cursor.getString(nameIndex) else null }
                .any { it == column }
        }

    private fun reloadFavorites() {
        val list = readableDatabase.rawQuery(
            "SELECT * FROM favorite_tracks WHERE accountId = ? AND pendingDelete = 0 ORDER BY likedAt DESC",
            arrayOf(ACTIVE_ACCOUNT_ID.toString()),
        ).use { cursor ->
            val result = mutableListOf<FavoriteTrackEntity>()
            while (cursor.moveToNext()) {
                result.add(cursorToEntity(cursor))
            }
            result
        }
        _favoritesFlow.value = list
        val ids = list.flatMap { entity ->
            listOfNotNull(entity.trackId, entity.cloudTrackId)
        }.toSet()
        _favoriteIdsFlow.value = ids
        // Update individual status flows
        _favoriteStatusFlows.forEach { (trackId, flow) ->
            flow.value = trackId in ids
        }
    }

    private fun reloadDownloads() {
        val list = readableDatabase.rawQuery(
            "SELECT * FROM downloaded_tracks ORDER BY downloadedAt DESC",
            null
        ).use { cursor ->
            val result = mutableListOf<DownloadedTrackEntity>()
            while (cursor.moveToNext()) {
                result.add(cursorToDownloadedEntity(cursor))
            }
            result
        }
        _downloadsFlow.value = list
        val ids = list.map { it.trackId }.toSet()
        _downloadedIdsFlow.value = ids
        // Update individual status flows
        _downloadStatusFlows.forEach { (trackId, flow) ->
            flow.value = trackId in ids
        }
    }

    fun getAllFavorites(): List<FavoriteTrackEntity> {
        return readableDatabase.rawQuery(
            "SELECT * FROM favorite_tracks WHERE accountId = ? AND pendingDelete = 0 ORDER BY likedAt DESC",
            arrayOf(ACTIVE_ACCOUNT_ID.toString()),
        ).use { cursor ->
            val result = mutableListOf<FavoriteTrackEntity>()
            while (cursor.moveToNext()) {
                result.add(cursorToEntity(cursor))
            }
            result
        }
    }

    fun getFavoriteTrackIds(): Set<String> {
        return readableDatabase.rawQuery(
            "SELECT trackId, cloudTrackId FROM favorite_tracks WHERE accountId = ? AND pendingDelete = 0",
            arrayOf(ACTIVE_ACCOUNT_ID.toString()),
        ).use { cursor ->
            val result = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                result.add(cursor.getString(0))
                cursor.getString(1)?.takeIf(String::isNotBlank)?.let(result::add)
            }
            result
        }
    }

    fun getByTrackId(trackId: String): FavoriteTrackEntity? {
        return readableDatabase.rawQuery(
            "SELECT * FROM favorite_tracks WHERE accountId = ? AND (trackId = ? OR cloudTrackId = ?) LIMIT 1",
            arrayOf(ACTIVE_ACCOUNT_ID.toString(), trackId, trackId)
        ).use { cursor ->
            if (cursor.moveToFirst()) cursorToEntity(cursor) else null
        }
    }

    fun isFavorite(trackId: String): Boolean {
        return readableDatabase.rawQuery(
            "SELECT 1 FROM favorite_tracks WHERE accountId = ? AND " +
                "(trackId = ? OR cloudTrackId = ?) AND pendingDelete = 0 LIMIT 1",
            arrayOf(ACTIVE_ACCOUNT_ID.toString(), trackId, trackId)
        ).use { it.moveToFirst() }
    }

    fun isFavoriteFlow(trackId: String): Flow<Boolean> {
        return _favoriteStatusFlows.getOrPut(trackId) {
            // P0 (аудит): раньше здесь был isFavorite(trackId) — СИНХРОННЫЙ
            // SQLite-запрос на потоке вызова, а вызов идёт из composition (main);
            // самый первый ещё и открывал БД на main. Берём снапшот из памяти:
            // _favoriteIdsFlow всегда актуален (каждая мутация зовёт
            // reloadFavorites), а до loadAsync значение само поправится —
            // reloadFavorites обновляет все пер-трековые flows.
            MutableStateFlow(trackId in _favoriteIdsFlow.value)
        }
    }

    fun insert(entity: FavoriteTrackEntity) {
        val values = android.content.ContentValues().apply {
            put("accountId", entity.accountId.takeIf { it != 0L } ?: ACTIVE_ACCOUNT_ID)
            put("trackId", entity.trackId)
            put("cloudTrackId", entity.cloudTrackId)
            put("title", entity.title)
            put("artistName", entity.artistName)
            put("albumTitle", entity.albumTitle)
            put("durationMs", entity.durationMs)
            put("genre", entity.genre)
            put("imageUrl", entity.imageUrl)
            put("streamUrl", entity.streamUrl)
            put("artistId", entity.artistId)
            put("collectionId", entity.collectionId)
            put("isExplicit", if (entity.isExplicit) 1 else 0)
            put("source", entity.source)
            put("isAvailable", if (entity.isAvailable) 1 else 0)
            put("accessKey", entity.accessKey)
            put("likedAt", entity.likedAt)
            put("isSynced", if (entity.isSynced) 1 else 0)
            put("pendingDelete", if (entity.pendingDelete) 1 else 0)
        }
        writableDatabase.insertWithOnConflict(
            "favorite_tracks", null, values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
        reloadFavorites()
    }

    fun insertAll(entities: List<FavoriteTrackEntity>) {
        writableDatabase.beginTransaction()
        try {
            for (entity in entities) {
                insert(entity)
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun update(entity: FavoriteTrackEntity) {
        val values = android.content.ContentValues().apply {
            put("cloudTrackId", entity.cloudTrackId)
            put("title", entity.title)
            put("artistName", entity.artistName)
            put("albumTitle", entity.albumTitle)
            put("durationMs", entity.durationMs)
            put("genre", entity.genre)
            put("imageUrl", entity.imageUrl)
            put("streamUrl", entity.streamUrl)
            put("artistId", entity.artistId)
            put("collectionId", entity.collectionId)
            put("isExplicit", if (entity.isExplicit) 1 else 0)
            put("source", entity.source)
            put("isAvailable", if (entity.isAvailable) 1 else 0)
            put("accessKey", entity.accessKey)
            put("likedAt", entity.likedAt)
            put("isSynced", if (entity.isSynced) 1 else 0)
            put("pendingDelete", if (entity.pendingDelete) 1 else 0)
        }
        writableDatabase.update(
            "favorite_tracks", values, "accountId = ? AND trackId = ?",
            arrayOf(ACTIVE_ACCOUNT_ID.toString(), entity.trackId)
        )
        reloadFavorites()
    }

    fun deleteByTrackId(trackId: String) {
        writableDatabase.execSQL(
            "DELETE FROM favorite_tracks WHERE accountId = ? AND (trackId = ? OR cloudTrackId = ?)",
            arrayOf<Any?>(ACTIVE_ACCOUNT_ID, trackId, trackId)
        )
        reloadFavorites()
    }

    fun clearAll() {
        writableDatabase.execSQL(
            "DELETE FROM favorite_tracks WHERE accountId = ?",
            arrayOf(ACTIVE_ACCOUNT_ID),
        )
        reloadFavorites()
    }

    fun getPendingInserts(): List<FavoriteTrackEntity> {
        return readableDatabase.rawQuery(
            "SELECT * FROM favorite_tracks WHERE accountId = ? AND isSynced = 0 AND pendingDelete = 0",
            arrayOf(ACTIVE_ACCOUNT_ID.toString()),
        ).use { cursor ->
            val result = mutableListOf<FavoriteTrackEntity>()
            while (cursor.moveToNext()) {
                result.add(cursorToEntity(cursor))
            }
            result
        }
    }

    fun getPendingDeletes(): List<FavoriteTrackEntity> {
        return readableDatabase.rawQuery(
            "SELECT * FROM favorite_tracks WHERE accountId = ? AND pendingDelete = 1",
            arrayOf(ACTIVE_ACCOUNT_ID.toString()),
        ).use { cursor ->
            val result = mutableListOf<FavoriteTrackEntity>()
            while (cursor.moveToNext()) {
                result.add(cursorToEntity(cursor))
            }
            result
        }
    }

    fun markSynced(trackId: String) {
        writableDatabase.execSQL(
            "UPDATE favorite_tracks SET isSynced = 1 WHERE accountId = ? AND (trackId = ? OR cloudTrackId = ?)",
            arrayOf<Any?>(ACTIVE_ACCOUNT_ID, trackId, trackId)
        )
    }

    fun clearPendingDelete(trackId: String) {
        writableDatabase.execSQL(
            "UPDATE favorite_tracks SET pendingDelete = 0 WHERE accountId = ? AND " +
                "(trackId = ? OR cloudTrackId = ?)",
            arrayOf<Any?>(ACTIVE_ACCOUNT_ID, trackId, trackId)
        )
    }

    fun getCount(): Int {
        return readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM favorite_tracks WHERE accountId = ? AND pendingDelete = 0",
            arrayOf(ACTIVE_ACCOUNT_ID.toString()),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    // --- Downloaded Tracks operations ---
    fun getDownloadedTracks(): List<DownloadedTrackEntity> {
        return readableDatabase.rawQuery(
            "SELECT * FROM downloaded_tracks ORDER BY downloadedAt DESC",
            null
        ).use { cursor ->
            val result = mutableListOf<DownloadedTrackEntity>()
            while (cursor.moveToNext()) {
                result.add(cursorToDownloadedEntity(cursor))
            }
            result
        }
    }

    fun isDownloaded(trackId: String): Boolean {
        return readableDatabase.rawQuery(
            "SELECT 1 FROM downloaded_tracks WHERE trackId = ? LIMIT 1",
            arrayOf(trackId)
        ).use { it.moveToFirst() }
    }

    /** Точечная выборка одной записи — вместо getDownloadedTracks().find{}
     *  (полная выборка таблицы на КАЖДЫЙ трек пачки = O(N²); P2, аудит). */
    fun getDownloadedTrack(trackId: String): DownloadedTrackEntity? {
        return readableDatabase.rawQuery(
            "SELECT * FROM downloaded_tracks WHERE trackId = ? LIMIT 1",
            arrayOf(trackId)
        ).use { cursor ->
            if (cursor.moveToFirst()) cursorToDownloadedEntity(cursor) else null
        }
    }

    fun isDownloadedFlow(trackId: String): Flow<Boolean> {
        return _downloadStatusFlows.getOrPut(trackId) {
            // См. isFavoriteFlow: без синхронного SQLite на потоке вызова (main).
            MutableStateFlow(trackId in _downloadedIdsFlow.value)
        }
    }

    fun insertDownloaded(entity: DownloadedTrackEntity) {
        val values = android.content.ContentValues().apply {
            put("trackId", entity.trackId)
            put("title", entity.title)
            put("artistName", entity.artistName)
            put("albumTitle", entity.albumTitle)
            put("durationMs", entity.durationMs)
            put("imageUrl", entity.imageUrl)
            put("localPath", entity.localPath)
            put("localCoverPath", entity.localCoverPath)
            put("quality", entity.quality)
            put("downloadedAt", entity.downloadedAt)
        }
        writableDatabase.insertWithOnConflict(
            "downloaded_tracks", null, values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
        reloadDownloads()
    }

    fun deleteDownloaded(trackId: String) {
        writableDatabase.execSQL(
            "DELETE FROM downloaded_tracks WHERE trackId = ?",
            arrayOf(trackId)
        )
        reloadDownloads()
    }

    /**
     * Обновить путь файла трека (миграция приватная папка → публичные
     * Загрузки). [reload] = false для батча (миграция сотен треков — не
     * перечитывать таблицу на каждый), затем один [refreshDownloads].
     */
    fun updateDownloadedLocalPath(trackId: String, localPath: String, reload: Boolean = true) {
        val values = android.content.ContentValues().apply {
            put("localPath", localPath)
        }
        writableDatabase.update("downloaded_tracks", values, "trackId = ?", arrayOf(trackId))
        if (reload) reloadDownloads()
    }

    /** Публичная перечитка списка загрузок (для батч-операций). */
    fun refreshDownloads() = reloadDownloads()

    fun clearAllDownloads() {
        writableDatabase.execSQL("DELETE FROM downloaded_tracks")
        reloadDownloads()
    }

    private fun cursorToEntity(cursor: android.database.Cursor): FavoriteTrackEntity {
        return FavoriteTrackEntity(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            accountId = cursor.getColumnIndex("accountId")
                .takeIf { it >= 0 }
                ?.let(cursor::getLong)
                ?: 0L,
            trackId = cursor.getString(cursor.getColumnIndexOrThrow("trackId")),
            cloudTrackId = cursor.getColumnIndex("cloudTrackId")
                .takeIf { it >= 0 }
                ?.let(cursor::getString),
            title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
            artistName = cursor.getString(cursor.getColumnIndexOrThrow("artistName")),
            albumTitle = cursor.getString(cursor.getColumnIndexOrThrow("albumTitle")),
            durationMs = cursor.getLong(cursor.getColumnIndexOrThrow("durationMs")),
            genre = cursor.getString(cursor.getColumnIndexOrThrow("genre")),
            imageUrl = cursor.getString(cursor.getColumnIndexOrThrow("imageUrl")),
            streamUrl = cursor.getString(cursor.getColumnIndexOrThrow("streamUrl")),
            artistId = cursor.getString(cursor.getColumnIndexOrThrow("artistId")),
            collectionId = cursor.getString(cursor.getColumnIndexOrThrow("collectionId")),
            isExplicit = cursor.getInt(cursor.getColumnIndexOrThrow("isExplicit")) == 1,
            source = cursor.getString(cursor.getColumnIndexOrThrow("source")),
            isAvailable = cursor.getInt(cursor.getColumnIndexOrThrow("isAvailable")) == 1,
            accessKey = cursor.getColumnIndex("accessKey")
                .takeIf { it >= 0 }
                ?.let(cursor::getString),
            likedAt = cursor.getLong(cursor.getColumnIndexOrThrow("likedAt")),
            isSynced = cursor.getInt(cursor.getColumnIndexOrThrow("isSynced")) == 1,
            pendingDelete = cursor.getInt(cursor.getColumnIndexOrThrow("pendingDelete")) == 1
        )
    }

    private fun cursorToDownloadedEntity(cursor: android.database.Cursor): DownloadedTrackEntity {
        return DownloadedTrackEntity(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            trackId = cursor.getString(cursor.getColumnIndexOrThrow("trackId")),
            title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
            artistName = cursor.getString(cursor.getColumnIndexOrThrow("artistName")),
            albumTitle = cursor.getString(cursor.getColumnIndexOrThrow("albumTitle")),
            durationMs = cursor.getLong(cursor.getColumnIndexOrThrow("durationMs")),
            imageUrl = cursor.getString(cursor.getColumnIndexOrThrow("imageUrl")),
            localPath = cursor.getString(cursor.getColumnIndexOrThrow("localPath")),
            localCoverPath = cursor.getString(cursor.getColumnIndexOrThrow("localCoverPath")),
            quality = cursor.getString(cursor.getColumnIndexOrThrow("quality")),
            downloadedAt = cursor.getLong(cursor.getColumnIndexOrThrow("downloadedAt"))
        )
    }

    companion object {
        private const val DB_NAME = "favorite_tracks.db"
        private const val DB_VERSION = 8

        @Volatile
        private var ACTIVE_ACCOUNT_ID: Long = 0L
        private val ACCOUNT_SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        @Volatile
        private var INSTANCE: FavoriteTrackDatabase? = null

        fun getInstance(context: Context): FavoriteTrackDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FavoriteTrackDatabase(context.applicationContext).also {
                    INSTANCE = it
                    ACCOUNT_SCOPE.launch { it.activateAccountInternal(ACTIVE_ACCOUNT_ID) }
                }
            }
        }

        fun activateAccount(userId: Long) {
            ACTIVE_ACCOUNT_ID = userId.coerceAtLeast(0L)
            INSTANCE?.let { database ->
                database.clearActiveAccountSnapshot()
                ACCOUNT_SCOPE.launch { database.activateAccountInternal(ACTIVE_ACCOUNT_ID) }
            }
        }
    }

    private fun clearActiveAccountSnapshot() {
        _favoritesFlow.value = emptyList()
        _favoriteIdsFlow.value = emptySet()
        _favoriteStatusFlows.values.forEach { it.value = false }
    }

    private fun activateAccountInternal(userId: Long) {
        if (userId != 0L) {
            writableDatabase.execSQL(
                "UPDATE OR IGNORE favorite_tracks SET accountId = ? WHERE accountId = 0",
                arrayOf(userId),
            )
            writableDatabase.execSQL("DELETE FROM favorite_tracks WHERE accountId = 0")
        }
        if (isLoaded) reloadFavorites()
    }
}
