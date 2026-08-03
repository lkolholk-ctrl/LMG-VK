package com.lmg.vk.engine.backend.lmg

/**
 * Compatibility surface for UI that was copied from the separate LMG/ICM project.
 *
 * The VK X and VK MP3 MOD reference archives contain no VK methods for this
 * broker-specific continuity, listening-room, collaborative-playlist or external
 * credits service. Network implementations are therefore intentionally disabled:
 * inventing VK endpoints or silently retaining the foreign service would violate
 * the VK-only contract of this project.
 *
 * The data types stay temporarily so the copied UI can be separated in a later
 * small batch without breaking unrelated VK screens.
 */
@Suppress("UNUSED_PARAMETER")
object LmgSyncApi {

    data class PlaybackState(
        val trackId: String,
        val positionMs: Long,
        val durationMs: Long,
        val title: String,
        val artist: String,
        val coverUrl: String,
        val isPlaying: Boolean,
        val deviceName: String = "",
        val updatedAt: Long = 0L,
    )

    data class Room(
        val code: String,
        val hostPid: String,
        val state: PlaybackState?,
        val memberNames: List<String>,
        val serverTimeMs: Long,
    )

    fun isHost(room: Room): Boolean = room.hostPid.isNotBlank()

    suspend fun saveState(state: PlaybackState): Boolean = false

    suspend fun fetchState(): Pair<PlaybackState, Boolean>? = null

    suspend fun createRoom(name: String): Room? = null

    suspend fun joinRoom(code: String, name: String): Room? = null

    suspend fun fetchRoom(code: String): Room? = null

    suspend fun publishRoomState(code: String, state: PlaybackState): Room? = null

    suspend fun leaveRoom(code: String): Boolean = false

    data class SharedTrack(
        val trackId: String,
        val title: String,
        val artist: String,
        val coverUrl: String,
        val addedBy: String,
    )

    data class SharedPlaylist(
        val code: String,
        val title: String,
        val ownerPid: String,
        val tracks: List<SharedTrack>,
        val editorNames: List<String>,
    )

    data class SharedPlaylistSummary(
        val code: String,
        val title: String,
        val trackCount: Int,
        val editorCount: Int,
    )

    suspend fun createPlaylist(title: String, name: String): SharedPlaylist? = null

    suspend fun listPlaylists(): List<SharedPlaylistSummary> = emptyList()

    suspend fun openPlaylist(code: String): SharedPlaylist? = null

    suspend fun joinPlaylist(code: String, name: String): SharedPlaylist? = null

    suspend fun addTrackToPlaylist(code: String, track: SharedTrack): SharedPlaylist? = null

    suspend fun removeTrackFromPlaylist(code: String, trackId: String): SharedPlaylist? = null

    suspend fun leavePlaylist(code: String): Boolean = false

    data class CreditPerson(val name: String, val role: String)

    data class TrackCredits(
        val found: Boolean,
        val label: String,
        val year: String,
        val people: List<CreditPerson>,
    )

    suspend fun fetchCredits(
        trackId: String,
        title: String,
        artist: String,
        durationMs: Long,
    ): TrackCredits? = null
}
