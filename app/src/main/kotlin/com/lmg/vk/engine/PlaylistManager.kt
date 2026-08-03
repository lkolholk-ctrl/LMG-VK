package com.lmg.vk.engine

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Playlist Manager — создание, редактирование, удаление плейлистов.
 *
 * Хранит в SharedPreferences как JSON.
 */
object PlaylistManager {

    private lateinit var prefs: SharedPreferences
    private const val PREFS_NAME = "playlists"

    private fun safePrefs(): SharedPreferences? {
        return if (::prefs.isInitialized) prefs else null
    }

    /**
     * Track entry stored in a playlist with full metadata.
     */
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
        val coverTrackId: String?, // первый трек для обложки
        /** Полный VK id (`owner_id_playlist_id`), если локальная копия связана с облаком. */
        val remoteId: String? = null,
        /** Время последнего локального изменения. */
        val modifiedAt: Long = createdAt,
        /** Последний известный `update_time` VK в миллисекундах. */
        val remoteUpdatedAt: Long = 0L,
        /** Момент успешного слияния локальной и VK-копии. */
        val lastSyncedAt: Long = 0L,
    ) {
        /** Backward compat: list of track IDs */
        val trackIds: List<String> get() = tracks.map { it.id }
    }

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Локальные пользовательские изменения; облачные apply/markSynced сюда не попадают. */
    val changes: SharedFlow<Unit> = _changes

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadFromPrefs()
    }

    /**
     * Создать новый плейлист.
     */
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

    /**
     * Создать плейлист из импортированных треков.
     * Bulk insert — все треки записываются за одну транзакцию.
     *
     * @param name Название плейлиста
     * @param tracks Список треков с метаданными
     * @param sourceType Источник импорта (для префикса названия)
     * @return Созданный плейлист
     */
    fun createFromImport(
        name: String,
        tracks: List<PlaylistTrack>,
        sourceType: String = "Imported"
    ): Playlist {
        val id = "pl_${System.currentTimeMillis()}"
        val displayName = if (name.startsWith("$sourceType:") || name.startsWith("Yandex Music:")) {
            name
        } else {
            "$sourceType: $name"
        }
        val playlist = Playlist(
            id = id,
            name = displayName,
            tracks = tracks.toList(), // defensive copy
            createdAt = System.currentTimeMillis(),
            coverTrackId = tracks.firstOrNull()?.id,
            modifiedAt = System.currentTimeMillis(),
        )
        val list = _playlists.value.toMutableList()
        list.add(0, playlist)
        _playlists.value = list
        saveToPrefs()
        _changes.tryEmit(Unit)
        return playlist
    }

    /**
     * Переименовать плейлист.
     */
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

    /**
     * Удалить плейлист.
     */
    fun delete(playlistId: String): Playlist? {
        val deleted = _playlists.value.firstOrNull { it.id == playlistId }
        _playlists.value = _playlists.value.filter { it.id != playlistId }
        saveToPrefs()
        _changes.tryEmit(Unit)
        return deleted
    }

    /**
     * Добавить трек в плейлист.
     */
    fun addTrack(playlistId: String, track: PlaylistTrack) {
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
            }
        }
    }

    /**
     * Убрать трек из плейлиста.
     */
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

    /**
     * Переместить трек в плейлисте (drag & drop).
     */
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

    /**
     * Получить плейлист по ID.
     */
    fun getById(playlistId: String): Playlist? {
        return _playlists.value.find { it.id == playlistId }
    }

    fun getByRemoteId(remoteId: String): Playlist? =
        _playlists.value.find { it.remoteId == remoteId }

    /** Связать созданный в VK плейлист с его локальным оригиналом. */
    fun markSynced(
        playlistId: String,
        remoteId: String,
        remoteUpdatedAt: Long = System.currentTimeMillis(),
    ) {
        update(playlistId) {
            it.copy(
                remoteId = remoteId,
                remoteUpdatedAt = remoteUpdatedAt,
                lastSyncedAt = System.currentTimeMillis(),
            )
        }
    }

    /** Применить облачную версию, не помечая её новым локальным изменением. */
    fun applyRemote(
        remoteId: String,
        name: String,
        tracks: List<PlaylistTrack>,
        remoteUpdatedAt: Long,
    ): Playlist {
        val existing = getByRemoteId(remoteId)
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
                id = "pl_vk_${remoteId.replace('-', '_')}",
                name = name,
                tracks = tracks,
                createdAt = now,
                coverTrackId = tracks.firstOrNull()?.id,
                remoteId = remoteId,
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

    /**
     * Получить треки плейлиста как List<Track>.
     */
    fun getPlaylistTracks(playlistId: String, allTracks: List<Track>): List<Track> {
        val pl = getById(playlistId) ?: return emptyList()
        val trackMap = allTracks.associateBy { it.id }
        return pl.trackIds.mapNotNull { trackMap[it] }
    }

    // ── Persistence ──

    private fun saveToPrefs() {
        val p = safePrefs() ?: return
        val arr = JSONArray()
        _playlists.value.forEach { pl ->
            arr.put(JSONObject().apply {
                put("id", pl.id)
                put("name", pl.name)
                put("created", pl.createdAt)
                put("cover", pl.coverTrackId ?: "")
                put("remoteId", pl.remoteId ?: "")
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
        p.edit().putString("data", arr.toString()).apply()
    }

    private fun loadFromPrefs() {
        val p = safePrefs() ?: return
        try {
            val str = p.getString("data", null) ?: return
            val arr = JSONArray(str)
            val list = mutableListOf<Playlist>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)

                // Try new format first (tracks array with objects)
                val tracks = mutableListOf<PlaylistTrack>()
                try {
                    val trackArr = obj.getJSONArray("tracks")
                    for (j in 0 until trackArr.length()) {
                        when (val item = trackArr.get(j)) {
                            is JSONObject -> {
                                tracks.add(PlaylistTrack(
                                    id = item.getString("id"),
                                    title = item.optString("title", ""),
                                    artist = item.optString("artist", ""),
                                    coverUrl = item.optString("coverUrl", "").takeIf { it.isNotEmpty() },
                                    durationMs = item.optLong("durationMs", 0L)
                                ))
                            }
                            is String -> {
                                // Legacy format: just track ID string
                                tracks.add(PlaylistTrack(id = item, title = "", artist = ""))
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Fallback: try legacy "trackIds" array
                    try {
                        val legacyArr = obj.getJSONArray("trackIds")
                        for (j in 0 until legacyArr.length()) {
                            tracks.add(PlaylistTrack(id = legacyArr.getString(j), title = "", artist = ""))
                        }
                    } catch (_: Exception) {}
                }

                val coverId = obj.optString("cover", "")
                val remoteId = obj.optString("remoteId", "")
                val createdAt = obj.optLong("created", 0L)
                list.add(Playlist(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    tracks = tracks,
                    createdAt = createdAt,
                    coverTrackId = if (coverId.isEmpty()) null else coverId,
                    remoteId = remoteId.takeIf { it.isNotEmpty() },
                    modifiedAt = obj.optLong("modifiedAt", createdAt),
                    remoteUpdatedAt = obj.optLong("remoteUpdatedAt", 0L),
                    lastSyncedAt = obj.optLong("lastSyncedAt", 0L),
                ))
            }
            _playlists.value = list
        } catch (_: Exception) {}
    }
}
