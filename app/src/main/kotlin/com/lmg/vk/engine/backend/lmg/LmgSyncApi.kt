package com.lmg.vk.engine.backend.lmg

/**
 * Compatibility surface for UI that was copied from the separate LMG/ICM project.
 *
 * The VK X and VK MP3 MOD reference archives contain no VK methods for this
 * broker-specific collaborative-playlist or external credits service. Network
 * implementations are therefore intentionally disabled: inventing VK endpoints or
 * silently retaining the foreign service would violate the VK-only contract of
 * this project.
 *
 * Continuity и комнаты совместного прослушивания (Listen Together) вырезаны
 * вместе с их UI: рабочей реализации за ними не было.
 *
 * The data types stay temporarily so the copied UI can be separated in a later
 * small batch without breaking unrelated VK screens.
 */
@Suppress("UNUSED_PARAMETER")
object LmgSyncApi {

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
