package com.lmg.vk.engine

import com.lmg.vk.engine.backend.MusicAuth
import com.lmg.vk.engine.backend.MusicBackend
import com.lmg.vk.engine.backend.UserPlaylist
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Двустороннее слияние локальных плейлистов с плейлистами текущего VK-аккаунта.
 *
 * Автоматически ничего не удаляет: отсутствие сущности в одном сетевом ответе
 * может быть временной ошибкой или неполной страницей. Удаление обеих копий
 * выполняется только через [deleteEverywhere] после явного действия пользователя.
 */
object PlaylistSyncManager {

    data class SyncReport(
        val pushed: Int = 0,
        val pulled: Int = 0,
        val unchanged: Int = 0,
        val unsupportedTracks: Int = 0,
        val failed: Int = 0,
        val deleted: Int = 0,
    )

    data class SyncState(
        val isSyncing: Boolean = false,
        val lastReport: SyncReport? = null,
        val lastSuccessAt: Long? = null,
        val error: String? = null,
    )

    private val mutex = Mutex()
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state
    private var activeAccountId = 0L

    @Synchronized
    fun activateAccount(userId: Long) {
        val resolvedUserId = userId.coerceAtLeast(0L)
        if (activeAccountId == resolvedUserId) return
        activeAccountId = resolvedUserId
        _state.value = SyncState()
    }

    suspend fun sync(): Result<SyncReport> = mutex.withLock {
        if (!MusicAuth.isLoggedIn.value) {
            val failure = IllegalStateException("Sign in to sync playlists")
            _state.value = _state.value.copy(error = failure.message)
            return@withLock Result.failure(failure)
        }

        _state.value = _state.value.copy(isSyncing = true, error = null)
        try {
            val report = merge()
            _state.value = SyncState(
                isSyncing = false,
                lastReport = report,
                lastSuccessAt = System.currentTimeMillis(),
            )
            Result.success(report)
        } catch (cancelled: CancellationException) {
            _state.value = _state.value.copy(isSyncing = false)
            throw cancelled
        } catch (error: Throwable) {
            _state.value = _state.value.copy(
                isSyncing = false,
                error = error.message ?: "Playlist sync failed",
            )
            Result.failure(error)
        }
    }

    /** Локально удаляет сразу; неудачное VK-удаление сохраняет в офлайн-очередь. */
    suspend fun deleteEverywhere(localId: String): Result<Unit> = mutex.withLock {
        _state.value = _state.value.copy(isSyncing = true, error = null)
        try {
            val local = PlaylistManager.getById(localId)
                ?: return@withLock Result.success(Unit)
            val remoteId = local.remoteId
            val activeOwnerId = MusicAuth.profileId.value ?: 0L
            PlaylistManager.delete(localId)
            if (remoteId != null && local.remoteOwnerId == activeOwnerId) {
                if (MusicBackend.deleteUserPlaylist(remoteId)) {
                    PlaylistManager.confirmRemoteDelete(activeOwnerId, remoteId)
                } else {
                    PlaylistManager.queueRemoteDelete(activeOwnerId, remoteId)
                }
            }
            Result.success(Unit)
        } finally {
            _state.value = _state.value.copy(isSyncing = false)
        }
    }

    suspend fun deleteRemote(remoteId: String): Result<Unit> = mutex.withLock {
        _state.value = _state.value.copy(isSyncing = true, error = null)
        try {
            if (remoteId.isBlank()) return@withLock Result.success(Unit)
            val activeOwnerId = MusicAuth.profileId.value
                ?: return@withLock Result.failure(IllegalStateException("No active VK account"))
            if (MusicBackend.deleteUserPlaylist(remoteId)) {
                PlaylistManager.confirmRemoteDelete(activeOwnerId, remoteId)
            } else {
                PlaylistManager.queueRemoteDelete(activeOwnerId, remoteId)
            }
            Result.success(Unit)
        } finally {
            _state.value = _state.value.copy(isSyncing = false)
        }
    }

    private suspend fun merge(): SyncReport {
        val activeOwnerId = MusicAuth.profileId.value
            ?: throw IllegalStateException("No active VK account")
        var deleted = 0
        var failedDeletes = 0
        PlaylistManager.pendingDeletes(activeOwnerId).forEach { remoteId ->
            if (MusicBackend.deleteUserPlaylist(remoteId)) {
                PlaylistManager.confirmRemoteDelete(activeOwnerId, remoteId)
                deleted++
            } else failedDeletes++
        }
        val pendingDeletes = PlaylistManager.pendingDeletes(activeOwnerId)
        val remotePlaylists = MusicBackend.getUserPlaylists(limit = 1000).items
            .filter { playlist ->
                playlist.id?.let { it.isNotBlank() && it !in pendingDeletes } == true
            }
        val remoteById = remotePlaylists.associateBy { it.id.orEmpty() }
        var pushed = 0
        var pulled = 0
        var unchanged = 0
        var unsupported = 0
        var failed = failedDeletes

        // Сначала разрешаем уже связанные пары.
        PlaylistManager.playlists.value.filter {
            it.remoteId != null && it.remoteOwnerId == activeOwnerId
        }.forEach { local ->
            val remote = remoteById[local.remoteId]
            if (remote == null) {
                // Не удаляем и не пересоздаём: ответ мог быть неполным.
                unchanged++
                return@forEach
            }
            val remoteStamp = remote.remoteTimestampMs()
            val localDirty = local.modifiedAt > local.lastSyncedAt
            val remoteDirty = remoteStamp > local.remoteUpdatedAt ||
                remote.name.orEmpty() != local.name ||
                (remote.trackCount != null &&
                    remote.trackCount != local.tracks.count { it.id.isVkAudioId() })

            when {
                localDirty && (!remoteDirty || local.modifiedAt >= remoteStamp) -> {
                    if (push(local, activeOwnerId)) {
                        pushed++
                        unsupported += local.tracks.count { !it.id.isVkAudioId() }
                    } else failed++
                }
                remoteDirty -> {
                    if (pull(remote, activeOwnerId)) pulled++ else failed++
                }
                else -> unchanged++
            }
        }

        // Новые локальные плейлисты создаём в текущем VK-аккаунте.
        PlaylistManager.playlists.value.filter { it.remoteId == null }.forEach { local ->
            if (push(local, activeOwnerId)) {
                pushed++
                unsupported += local.tracks.count { !it.id.isVkAudioId() }
            } else failed++
        }

        // Новые VK-плейлисты импортируем как полноценные локальные копии.
        val linkedRemoteIds = PlaylistManager.playlists.value
            .filter { it.remoteOwnerId == activeOwnerId }
            .mapNotNull { it.remoteId }
            .toSet()
        remotePlaylists.filter { remote ->
            remote.id?.let { it !in linkedRemoteIds } == true
        }.forEach { remote ->
            if (pull(remote, activeOwnerId)) pulled++ else failed++
        }

        return SyncReport(
            pushed = pushed,
            pulled = pulled,
            unchanged = unchanged,
            unsupportedTracks = unsupported,
            failed = failed,
            deleted = deleted,
        )
    }

    private suspend fun push(local: PlaylistManager.Playlist, activeOwnerId: Long): Boolean {
        val remoteId = local.remoteId
        val success = if (remoteId == null) {
            val createdId = MusicBackend.createUserPlaylist(local.name, local.trackIds)
                ?: return false
            PlaylistManager.markSynced(local.id, createdId, activeOwnerId)
            true
        } else {
            if (!MusicBackend.updateUserPlaylist(remoteId, local.name, local.trackIds)) return false
            PlaylistManager.markSynced(local.id, remoteId, activeOwnerId)
            true
        }
        return success
    }

    private suspend fun pull(remote: UserPlaylist, activeOwnerId: Long): Boolean {
        val remoteId = remote.id ?: return false
        val response = MusicBackend.getUserPlaylistTracks(
            playlistId = remoteId,
            limit = 6000,
            offset = 0,
        ) ?: return false
        val remoteTracks = response.tracks.map { track ->
            PlaylistManager.PlaylistTrack(
                id = track.id,
                title = track.title,
                artist = track.artist,
                coverUrl = track.cover,
                durationMs = track.durationMs,
            )
        }
        // VK принимает только owner_id_audio_id. Треки других источников остаются
        // локальной частью связанного плейлиста и не исчезают при pull.
        val localOnlyTracks = PlaylistManager.getByRemoteId(remoteId, activeOwnerId)
            ?.tracks.orEmpty()
            .filter { !it.id.isVkAudioId() }
        val tracks = (remoteTracks + localOnlyTracks).distinctBy { it.id }
        PlaylistManager.applyRemote(
            remoteId = remoteId,
            remoteOwnerId = activeOwnerId,
            name = remote.name.orEmpty().ifBlank { "Playlist" },
            tracks = tracks,
            remoteUpdatedAt = remote.remoteTimestampMs(),
        )
        return true
    }

    private fun UserPlaylist.remoteTimestampMs(): Long =
        ((updatedAt ?: createdAt ?: 0L) * 1000L).coerceAtLeast(0L)

    private fun String.isVkAudioId(): Boolean {
        val normalized = removePrefix("vk_")
        val parts = normalized.split('_')
        return parts.size >= 2 && parts[0].toLongOrNull() != null && parts[1].toLongOrNull() != null
    }
}
