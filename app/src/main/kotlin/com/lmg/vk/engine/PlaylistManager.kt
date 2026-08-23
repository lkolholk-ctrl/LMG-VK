package com.lmg.vk.engine

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

object PlaylistManager {

    private lateinit var prefs: SharedPreferences
    private const val PREFS_NAME = "playlists"
    private const val LEGACY_DATA_KEY = "data"
    private const val ACCOUNT_MIGRATION_KEY = "account_scoped_v1"
    private var activeAccountId = 0L
    data class PendingRemoteDelete(val ownerId: Long, val remoteId: String)
    private val pendingRemoteDeletes = linkedSetOf<PendingRemoteDelete>()

    private fun safePrefs(): SharedPreferences? {
        return if (::prefs.isInitialized) prefs else null
    }

    data class PlaylistTrack(
        val id: String,
        val title: String,
        val artist: String,
        val coverUrl: String? = null,
        val durationMs: Long = 0L
    )

    data class Playlist(
        val id: String,
        val name: String,
        val tracks: List<PlaylistTrack>,
        val createdAt: Long,
        val coverTrackId: String?,
        val remoteId: String? = null,
        val remoteOwnerId: Long? = null,
        val modifiedAt: Long = createdAt,
        val remoteUpdatedAt: Long = 0L,
        val lastSyncedAt: Long = 0L,
    ) {
        val trackIds: List<String> get() = tracks.map { it.id }
    }

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes: SharedFlow<Unit> = _changes

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadPendingDeletes()
        loadActivePlaylists()
    }

    @Synchronized
    fun activateAccount(userId: Long) {
        val resolvedUserId = userId.coerceAtLeast(0L)
        migrateLegacyData(resolvedUserId)
        if (activeAccountId == resolvedUserId) return
        activeAccountId = resolvedUserId
        loadActivePlaylists()
    }

    fun create(name: String): Playlist {
        val now = System.currentTimeMillis()
        val id = "pl_$now"
        val playlist = Playlist(id, name, emptyList(), now, null, modifiedAt = now)
        val list = _playlists.value.toMutableList()
        list.add(0, playlist)
        _playlists.value = list
        saveToPrefs()
        _changes.tryEmit(Unit)
        return playlist
    }

    fun rename(playlistId: String, newName: String) {
        val list = _playlists.value.toMutableList()
        val idx = list.indexOfFirst { it.id == playlistId }
        if (idx >= 0) {
            list[idx] = list[idx].copy(name = newName, modifiedAt = System.currentTimeMillis())
            _playlists.value = list
            saveToPrefs()
            _changes.tryEmit(Unit)
        }
    }

    fun delete(playlistId: String): Playlist? {
        val deleted = _playlists.value.firstOrNull { it.id == playlistId }
        _playlists.value = _playlists.value.filter { it.id != playlistId }
        saveToPrefs()
        _changes.tryEmit(Unit)
        return deleted
    }

    fun addTrack(playlistId: String, track: PlaylistTrack): Boolean {
        val list = _playlists.value.toMutableList()
        val idx = list.indexOfFirst { it.id == playlistId }
        if (idx >= 0) {
            val pl = list[idx]
            if (track.id !in pl.trackIds) {
                val newTracks = pl.tracks + track
                list[idx] = pl.copy(
                    tracks = newTracks,
                    coverTrackId = pl.coverTrackId ?: track.id,
                    modifiedAt = System.currentTimeMillis(),
                )
                _playlists.value = list
                saveToPrefs()
                _changes.tryEmit(Unit)
                return true
            }
        }
        return false
    }

    fun addTrack(playlistId: String, track: Track): Boolean = addTrack(
        playlistId = playlistId,
        track = PlaylistTrack(
            id = track.id,
            title = track.title,
            artist = track.artist,
            coverUrl = track.coverUrl,
            durationMs = track.durationMs,
        ),
    )

    fun removeTrack(playlistId: String, trackId: String) {
        val list = _playlists.value.toMutableList()
        val idx = list.indexOfFirst { it.id == playlistId }
        if (idx >= 0) {
            val pl = list[idx]
            val newTracks = pl.tracks.filter { it.id != trackId }
            list[idx] = pl.copy(
                tracks = newTracks,
                coverTrackId = if (pl.coverTrackId == trackId) newTracks.firstOrNull()?.id else pl.coverTrackId,
                modifiedAt = System.currentTimeMillis(),
            )
            _playlists.value = list
            saveToPrefs()
            _changes.tryEmit(Unit)
        }
    }

    fun moveTrack(playlistId: String, fromIndex: Int, toIndex: Int) {
        val list = _playlists.value.toMutableList()
        val idx = list.indexOfFirst { it.id == playlistId }
        if (idx >= 0) {
            val pl = list[idx]
            val tracks = pl.tracks.toMutableList()
            if (fromIndex in tracks.indices && toIndex in tracks.indices) {
                val item = tracks.removeAt(fromIndex)
                tracks.add(toIndex, item)
                list[idx] = pl.copy(tracks = tracks, modifiedAt = System.currentTimeMillis())
                _playlists.value = list
                saveToPrefs()
                _changes.tryEmit(Unit)
            }
        }
    }

    fun getById(playlistId: String): Playlist? {
        return _playlists.value.find { it.id == playlistId }
    }

    fun getByRemoteId(remoteId: String, ownerId: Long? = null): Playlist? =
        _playlists.value.find {
            it.remoteId == remoteId && (ownerId == null || it.remoteOwnerId == ownerId)
        }

    fun pendingDeletes(ownerId: Long): Set<String> = pendingRemoteDeletes
        .filterTo(linkedSetOf()) { it.ownerId == ownerId }
        .mapTo(linkedSetOf()) { it.remoteId }

    fun queueRemoteDelete(ownerId: Long, remoteId: String) {
        if (ownerId == 0L) return
        pendingRemoteDeletes += PendingRemoteDelete(ownerId, remoteId)
        savePendingDeletes()
        _changes.tryEmit(Unit)
    }

    fun confirmRemoteDelete(ownerId: Long, remoteId: String) {
        if (pendingRemoteDeletes.remove(PendingRemoteDelete(ownerId, remoteId))) savePendingDeletes()
    }

    fun markSynced(
        playlistId: String,
        remoteId: String,
        remoteOwnerId: Long,
        remoteUpdatedAt: Long = System.currentTimeMillis(),
    ) {
        update(playlistId) {
            it.copy(
                remoteId = remoteId,
                remoteOwnerId = remoteOwnerId,
                remoteUpdatedAt = remoteUpdatedAt,
                lastSyncedAt = System.currentTimeMillis(),
            )
        }
    }

    fun applyRemote(
        remoteId: String,
        remoteOwnerId: Long,
        name: String,
        tracks: List<PlaylistTrack>,
        remoteUpdatedAt: Long,
    ): Playlist {
        val existing = getByRemoteId(remoteId, remoteOwnerId)
        val now = System.currentTimeMillis()
        val updated = if (existing != null) {
            existing.copy(
                name = name,
                tracks = tracks,
                coverTrackId = tracks.firstOrNull()?.id,
                remoteUpdatedAt = remoteUpdatedAt,
                modifiedAt = now,
                lastSyncedAt = now,
            )
        } else {
            Playlist(
                id = "pl_vk_${remoteOwnerId}_${remoteId.replace('-', '_')}",
                name = name,
                tracks = tracks,
                createdAt = now,
                coverTrackId = tracks.firstOrNull()?.id,
                remoteId = remoteId,
                remoteOwnerId = remoteOwnerId,
                modifiedAt = now,
                remoteUpdatedAt = remoteUpdatedAt,
                lastSyncedAt = now,
            )
        }
        val list = _playlists.value.toMutableList()
        val index = list.indexOfFirst { it.id == updated.id }
        if (index >= 0) list[index] = updated else list.add(0, updated)
        _playlists.value = list
        saveToPrefs()
        return updated
    }

    private fun update(playlistId: String, transform: (Playlist) -> Playlist) {
        val list = _playlists.value.toMutableList()
        val index = list.indexOfFirst { it.id == playlistId }
        if (index < 0) return
        list[index] = transform(list[index])
        _playlists.value = list
        saveToPrefs()
    }

    fun getPlaylistTracks(playlistId: String, allTracks: List<Track>): List<Track> {
        val pl = getById(playlistId) ?: return emptyList()
        val trackMap = allTracks.associateBy { it.id }
        return pl.trackIds.mapNotNull { trackMap[it] }
    }

    private fun saveToPrefs() {
        val p = safePrefs() ?: return
        p.edit().putString(accountDataKey(activeAccountId), encodePlaylists(_playlists.value)).apply()
    }

    private fun encodePlaylists(playlists: List<Playlist>): String =
        JSONArray().apply {
            playlists.forEach { pl ->
                put(JSONObject().apply {
                    put("id", pl.id)
                    put("name", pl.name)
                    put("created", pl.createdAt)
                    put("cover", pl.coverTrackId ?: "")
                    put("remoteId", pl.remoteId ?: "")
                    put("remoteOwnerId", pl.remoteOwnerId ?: 0L)
                    put("modifiedAt", pl.modifiedAt)
                    put("remoteUpdatedAt", pl.remoteUpdatedAt)
                    put("lastSyncedAt", pl.lastSyncedAt)
                    val trackArr = JSONArray()
                    pl.tracks.forEach { track ->
                        trackArr.put(JSONObject().apply {
                            put("id", track.id)
                            put("title", track.title)
                            put("artist", track.artist)
                            put("coverUrl", track.coverUrl ?: "")
                            put("durationMs", track.durationMs)
                        })
                    }
                    put("tracks", trackArr)
                })
            }
        }.toString()

    private fun accountDataKey(userId: Long): String = "data_account_$userId"

    private fun migrateLegacyData(currentUserId: Long) {
        val p = safePrefs() ?: return
        if (p.getBoolean(ACCOUNT_MIGRATION_KEY, false)) return
        val raw = p.getString(LEGACY_DATA_KEY, null)
        if (raw.isNullOrBlank()) {
            p.edit()
                .remove(LEGACY_DATA_KEY)
                .putBoolean(ACCOUNT_MIGRATION_KEY, true)
                .apply()
            return
        }
        if (currentUserId == 0L) return
        val legacy = decodePlaylists(raw) ?: return
        val partitions = legacy.groupBy { playlist ->
            playlist.remoteOwnerId?.takeIf { it != 0L } ?: currentUserId
        }
        val editor = p.edit()
        partitions.forEach { (ownerId, migrated) ->
            val existing = decodePlaylists(p.getString(accountDataKey(ownerId), null)).orEmpty()
            val normalized = migrated.map { playlist ->
                if (playlist.remoteId != null && playlist.remoteOwnerId == null) {
                    playlist.copy(remoteOwnerId = ownerId)
                } else {
                    playlist
                }
            }
            val merged = (normalized + existing).associateBy { it.id }.values.toList()
            editor.putString(accountDataKey(ownerId), encodePlaylists(merged))
        }
        editor
            .remove(LEGACY_DATA_KEY)
            .putBoolean(ACCOUNT_MIGRATION_KEY, true)
            .apply()
    }

    private fun loadActivePlaylists() {
        val p = safePrefs()
        _playlists.value = if (p == null) {
            emptyList()
        } else {
            decodePlaylists(p.getString(accountDataKey(activeAccountId), null)).orEmpty()
        }
    }

    private fun decodePlaylists(raw: String?): List<Playlist>? {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val tracks = mutableListOf<PlaylistTrack>()
                    val trackArr = obj.optJSONArray("tracks")
                    if (trackArr != null) {
                        for (j in 0 until trackArr.length()) {
                            when (val item = trackArr.get(j)) {
                                is JSONObject -> tracks.add(
                                    PlaylistTrack(
                                        id = item.getString("id"),
                                        title = item.optString("title", ""),
                                        artist = item.optString("artist", ""),
                                        coverUrl = item.optString("coverUrl", "")
                                            .takeIf { it.isNotEmpty() },
                                        durationMs = item.optLong("durationMs", 0L),
                                    ),
                                )
                                is String -> tracks.add(
                                    PlaylistTrack(id = item, title = "", artist = ""),
                                )
                            }
                        }
                    } else {
                        val legacyTrackIds = obj.optJSONArray("trackIds")
                        if (legacyTrackIds != null) {
                            for (j in 0 until legacyTrackIds.length()) {
                                tracks.add(
                                    PlaylistTrack(
                                        id = legacyTrackIds.getString(j),
                                        title = "",
                                        artist = "",
                                    ),
                                )
                            }
                        }
                    }
                    val coverId = obj.optString("cover", "")
                    val remoteId = obj.optString("remoteId", "")
                    val createdAt = obj.optLong("created", 0L)
                    add(
                        Playlist(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            tracks = tracks,
                            createdAt = createdAt,
                            coverTrackId = coverId.takeIf { it.isNotEmpty() },
                            remoteId = remoteId.takeIf { it.isNotEmpty() },
                            remoteOwnerId = obj.optLong("remoteOwnerId", 0L)
                                .takeIf { it != 0L },
                            modifiedAt = obj.optLong("modifiedAt", createdAt),
                            remoteUpdatedAt = obj.optLong("remoteUpdatedAt", 0L),
                            lastSyncedAt = obj.optLong("lastSyncedAt", 0L),
                        ),
                    )
                }
            }
        }.getOrNull()
    }

    private fun loadPendingDeletes() {
        val p = safePrefs() ?: return
        pendingRemoteDeletes.clear()
        runCatching {
            val pending = JSONArray(p.getString("pending_remote_deletes", "[]").orEmpty())
            for (i in 0 until pending.length()) {
                val item = pending.optJSONObject(i) ?: continue
                val ownerId = item.optLong("ownerId", 0L)
                val remoteId = item.optString("remoteId", "")
                if (ownerId != 0L && remoteId.isNotBlank()) {
                    pendingRemoteDeletes.add(PendingRemoteDelete(ownerId, remoteId))
                }
            }
        }
    }

    private fun savePendingDeletes() {
        val p = safePrefs() ?: return
        p.edit().putString(
            "pending_remote_deletes",
            JSONArray().apply {
                pendingRemoteDeletes.forEach { pending ->
                    put(JSONObject().apply {
                        put("ownerId", pending.ownerId)
                        put("remoteId", pending.remoteId)
                    })
                }
            }.toString(),
        ).apply()
    }

}
