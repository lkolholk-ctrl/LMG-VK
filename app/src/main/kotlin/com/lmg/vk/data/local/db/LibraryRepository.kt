package com.lmg.vk.data.local.db

import android.content.Context
import com.lmg.vk.engine.backend.MusicBackend
import com.lmg.vk.engine.backend.MusicAuth
import com.lmg.vk.engine.backend.LibraryTrack
import com.lmg.vk.engine.PlayerController
import com.lmg.vk.engine.Track
import com.lmg.vk.engine.VkAudioIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LibraryRepository private constructor(context: Context) {

    private val db = FavoriteTrackDatabase.getInstance(context)
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncRequests = Channel<Unit>(Channel.CONFLATED)
    private val syncMutex = Mutex()

    val favoritesFlow: Flow<List<FavoriteTrackEntity>> = db.favoritesFlow

    val favoriteIdsFlow: Flow<Set<String>> = db.favoriteIdsFlow

    fun isFavoriteFlow(trackId: String): Flow<Boolean> = db.isFavoriteFlow(stableTrackId(trackId))

    suspend fun getAllFavoritesAsTracks(): List<Track> = withContext(Dispatchers.IO) {
        db.getAllFavorites().filter(FavoriteTrackEntity::isAvailable).map { it.toTrack() }
    }

    suspend fun getFavoriteCount(): Int = withContext(Dispatchers.IO) {
        db.getCount()
    }

    init {
        syncScope.launch {
            db.loadAsync()
            db.favoriteIdsFlow.collectLatest { ids -> PlayerController.setFavoriteIds(ids) }
        }
        syncScope.launch {
            while (true) {
                syncRequests.receive()
                for (retryDelay in SYNC_RETRY_DELAYS_MS) {
                    if (retryDelay > 0L) delay(retryDelay)
                    if (!MusicAuth.isLoggedIn.value || syncWithCloud().isSuccess) break
                }
            }
        }
    }

    private val inFlight = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>(),
    )

    private fun requestCloudSync() {
        if (MusicBackend.isInitialized && MusicAuth.isLoggedIn.value) {
            syncRequests.trySend(Unit)
        }
    }

    private fun stableTrackId(rawId: String): String =
        VkAudioIdentity.stableFullId(rawId)

    private fun accessKeyFromId(rawId: String): String? =
        VkAudioIdentity.normalizeFullId(rawId)
            .split('_', limit = 3)
            .getOrNull(2)
            ?.takeIf(String::isNotBlank)

    private fun FavoriteTrackEntity.addRequestId(): String =
        accessKey?.takeIf(String::isNotBlank)?.let { "${trackId}_$it" } ?: trackId

    private suspend fun finalizeCloudIdentity(localTrackId: String, addedTrackId: String) {
        val stableLocalId = stableTrackId(localTrackId)
        val stableCloudId = stableTrackId(addedTrackId)
        val local = db.getByTrackId(stableLocalId) ?: return
        db.update(
            local.copy(
                cloudTrackId = stableCloudId,
                accessKey = null,
                isSynced = !local.pendingDelete,
                pendingDelete = local.pendingDelete,
            ),
        )
        updatePlayerControllerFavorites()
    }

    private fun FavoriteTrackEntity.matchesCloud(track: LibraryTrack): Boolean {
        val sameTitle = title.trim().equals(track.title.trim(), ignoreCase = true)
        val sameArtist = artistName.orEmpty().trim()
            .equals(track.artist.orEmpty().trim(), ignoreCase = true)
        val sameDuration = durationMs <= 0L || track.durationMs <= 0L ||
            kotlin.math.abs(durationMs - track.durationMs) <= 2_000L
        return sameTitle && sameArtist && sameDuration
    }

    private suspend fun fetchCloudLikes(): List<LibraryTrack> {
        val result = mutableListOf<LibraryTrack>()
        var offset = 0
        val limit = 500
        while (true) {
            val response = MusicBackend.getLibraryLikes(
                source = "all",
                limit = limit,
                offset = offset,
            ) ?: error("likes page fetch failed at offset=$offset")
            if (response.items.isEmpty()) break
            result.addAll(response.items)
            if (response.items.size < limit) break
            offset += response.items.size
        }
        return result
    }

    suspend fun syncWithCloud(): Result<Unit> = syncMutex.withLock {
        if (!MusicBackend.isInitialized || !MusicAuth.isLoggedIn.value) {
            return@withLock Result.success(Unit)
        }
        val accountId = MusicAuth.profileId.value ?: return@withLock Result.success(Unit)
        CLOUD_SYNCS.incrementAndGet()
        try {
            withContext(Dispatchers.IO) {
            try {
                val cloudLikes = fetchCloudLikes()
                if (MusicAuth.profileId.value != accountId) {
                    return@withContext Result.failure(
                        IllegalStateException("VK account changed during library synchronization"),
                    )
                }

                val cloudIds = cloudLikes.map { stableTrackId(it.id) }.toSet()
                val localEntities = db.getAllFavorites()
                val pendingDeletesBeforePull = db.getPendingDeletes()
                val mergeCandidates = (localEntities + pendingDeletesBeforePull)
                    .distinctBy { it.trackId }
                    .filter { entity ->
                        val storedCloudId = entity.cloudTrackId?.let(::stableTrackId)
                        storedCloudId == null || storedCloudId !in cloudIds
                    }
                val claimedLocalIds = mutableSetOf<String>()

                for (cloudTrack in cloudLikes) {
                    val cloudId = stableTrackId(cloudTrack.id)
                    val cloudExisting = db.getByTrackId(cloudId)
                    val mergeMatch = if (cloudExisting?.pendingDelete == true) {
                        null
                    } else {
                        mergeCandidates.firstOrNull {
                            it.trackId !in claimedLocalIds && it.matchesCloud(cloudTrack)
                        }?.also { claimedLocalIds += it.trackId }
                    }
                    val existing = if (mergeMatch != null) {
                        if (cloudExisting != null && cloudExisting.trackId != mergeMatch.trackId) {
                            db.deleteByTrackId(cloudExisting.trackId)
                        }
                        mergeMatch
                    } else {
                        cloudExisting
                    }

                    if (existing == null) {
                        db.insert(
                            FavoriteTrackEntity(
                                trackId = cloudId,
                                cloudTrackId = cloudId,
                                title = cloudTrack.title,
                                artistName = cloudTrack.artist,
                                albumTitle = null,
                                durationMs = cloudTrack.durationMs,
                                imageUrl = cloudTrack.cover,
                                artistId = cloudTrack.artistId,
                                collectionId = cloudTrack.collectionId,
                                isExplicit = cloudTrack.isExplicit,
                                source = cloudTrack.source,
                                isAvailable = cloudTrack.isAvailable,
                                accessKey = cloudTrack.accessKey,
                                likedAt = cloudTrack.likedAt ?: System.currentTimeMillis(),
                                isSynced = true,
                                pendingDelete = false
                            )
                        )
                    } else if (existing.pendingDelete) {
                        if (mergeMatch != null) {
                            db.update(
                                existing.copy(
                                    cloudTrackId = cloudId,
                                    accessKey = cloudTrack.accessKey,
                                    isSynced = false,
                                ),
                            )
                        }
                    } else {
                        val current = db.getByTrackId(existing.trackId)
                        if (current == null || current.pendingDelete) continue
                        db.update(
                            current.copy(
                                cloudTrackId = cloudId,
                                title = cloudTrack.title,
                                artistName = cloudTrack.artist,
                                durationMs = cloudTrack.durationMs,
                                imageUrl = cloudTrack.cover,
                                artistId = cloudTrack.artistId,
                                collectionId = cloudTrack.collectionId,
                                isExplicit = cloudTrack.isExplicit,
                                source = cloudTrack.source,
                                likedAt = cloudTrack.likedAt ?: current.likedAt,
                                isAvailable = cloudTrack.isAvailable,
                                accessKey = cloudTrack.accessKey
                                    ?: current.accessKey.takeIf { current.cloudTrackId == cloudId },
                                isSynced = true,
                            )
                        )
                    }
                }

                for (candidate in mergeCandidates) {
                    if (candidate.trackId in claimedLocalIds) continue
                    val current = db.getByTrackId(candidate.trackId) ?: continue
                    if (!current.pendingDelete) {
                        db.update(current.copy(cloudTrackId = null, isSynced = false))
                    }
                }

                for (pending in pendingDeletesBeforePull) {
                    val current = db.getByTrackId(pending.trackId) ?: continue
                    val cloudId = current.cloudTrackId?.let(::stableTrackId)
                    if (cloudId != null && cloudId !in cloudIds) {
                        db.deleteByTrackId(current.trackId)
                    }
                }

                val failedMutations = mutableListOf<String>()
                val submittedInserts = mutableListOf<FavoriteTrackEntity>()
                val pendingInserts = db.getPendingInserts()
                for ((batchIndex, batch) in pendingInserts.chunked(ADD_BATCH_SIZE).withIndex()) {
                    if (batchIndex > 0) delay(kotlin.random.Random.nextLong(1_500L, 2_501L))
                    val claimed = batch.mapNotNull { insert ->
                        if (!inFlight.add(insert.trackId)) return@mapNotNull null
                        val current = db.getByTrackId(insert.trackId)
                        if (current == null || current.pendingDelete || current.isSynced) {
                            inFlight.remove(insert.trackId)
                            null
                        } else {
                            current
                        }
                    }
                    if (claimed.isEmpty()) continue
                    val submittedIds = MusicBackend.addTracksToLibrary(claimed.map { it.addRequestId() })
                    for (current in claimed) {
                        if (stableTrackId(current.trackId) in submittedIds) {
                            submittedInserts += current
                        } else {
                            failedMutations += current.trackId
                            inFlight.remove(current.trackId)
                        }
                    }
                }

                if (submittedInserts.isNotEmpty()) {
                    try {
                        val unconfirmed = submittedInserts.toMutableList()
                        val claimedCloudIds = mutableSetOf<String>()
                        for (confirmationDelay in CONFIRMATION_RETRY_DELAYS_MS) {
                            delay(confirmationDelay)
                            val refreshedCloud = fetchCloudLikes()
                            val iterator = unconfirmed.iterator()
                            while (iterator.hasNext()) {
                                val submitted = iterator.next()
                                val current = db.getByTrackId(submitted.trackId)
                                if (current == null) {
                                    iterator.remove()
                                    continue
                                }
                                val match = refreshedCloud.firstOrNull { cloudTrack ->
                                    val cloudId = stableTrackId(cloudTrack.id)
                                    cloudId !in cloudIds &&
                                        cloudId !in claimedCloudIds &&
                                        current.matchesCloud(cloudTrack)
                                }
                                if (match != null) {
                                    val cloudId = stableTrackId(match.id)
                                    claimedCloudIds += cloudId
                                    finalizeCloudIdentity(current.trackId, cloudId)
                                    iterator.remove()
                                }
                            }
                            if (unconfirmed.isEmpty()) break
                        }
                        failedMutations += unconfirmed.map { it.trackId }
                    } finally {
                        submittedInserts.forEach { inFlight.remove(it.trackId) }
                    }
                }

                val pendingDeletes = db.getPendingDeletes()
                for (delete in pendingDeletes) {
                    if (!inFlight.add(delete.trackId)) continue
                    try {
                        val cloudId = delete.cloudTrackId
                        if (cloudId == null) {
                            failedMutations += delete.trackId
                            continue
                        }
                        val success = MusicBackend.unlikeTrack(cloudId)
                        if (success) {
                            val current = db.getByTrackId(delete.trackId)
                            if (current?.pendingDelete == true) {
                                db.deleteByTrackId(delete.trackId)
                            } else if (current != null) {
                                db.update(current.copy(cloudTrackId = null, isSynced = false))
                                requestCloudSync()
                            }
                        } else {
                            failedMutations += delete.trackId
                        }
                    } finally {
                        inFlight.remove(delete.trackId)
                    }
                }

                updatePlayerControllerFavorites()
                com.lmg.vk.debug.DebugLog.add(
                    "LIBRARY SYNC cloud=${cloudLikes.size} local=${localEntities.size} " +
                        "pending=${pendingInserts.size} submitted=${submittedInserts.size} " +
                        "failed=${failedMutations.distinct().size}",
                )

                if (failedMutations.isEmpty()) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("VK library sync failed for ${failedMutations.size} tracks"))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Result.failure(e)
            }
            }
        } finally {
            CLOUD_SYNCS.decrementAndGet()
            inFlight.clear()
        }
    }

    suspend fun likeTrack(track: Track): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val bareId = stableTrackId(track.id)
            val keyFromId = accessKeyFromId(track.id)

            val existing = db.getByTrackId(bareId)
            if (existing != null) {
                var current = existing
                if (existing.pendingDelete) {
                    current = existing.copy(
                        pendingDelete = false,
                        isSynced = existing.cloudTrackId != null,
                    )
                    db.update(current)
                }
                if (keyFromId != null && current.accessKey.isNullOrBlank()) {
                    current = current.copy(accessKey = keyFromId)
                    db.update(current)
                }
                if (!current.isSynced) requestCloudSync()
                return@withContext Result.success(Unit)
            }

            db.insert(
                FavoriteTrackEntity(
                    trackId = bareId,
                    title = track.title,
                    artistName = track.artist,
                    albumTitle = track.albumName.takeIf { it.isNotBlank() },
                    durationMs = track.durationMs,
                    imageUrl = track.coverUrl,
                    artistId = track.artists.firstOrNull()?.id,
                    collectionId = track.albumName.takeIf { it.isNotBlank() },
                    genre = track.genre,
                    isExplicit = track.isExplicit,
                    source = track.source,
                    isAvailable = track.isAvailable,
                    accessKey = keyFromId,
                    isSynced = false,
                    pendingDelete = false
                )
            )
            updatePlayerControllerFavorites()
            requestCloudSync()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unlikeTrack(trackId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val stableInputId = stableTrackId(trackId)
            val existing = db.getByTrackId(stableInputId) ?: return@withContext Result.success(Unit)
            val localId = existing.trackId

            if (existing.cloudTrackId == null && !existing.isSynced && localId !in inFlight) {
                db.deleteByTrackId(localId)
                updatePlayerControllerFavorites()
                return@withContext Result.success(Unit)
            }

            db.update(existing.copy(pendingDelete = true, isSynced = false))

            updatePlayerControllerFavorites()

            requestCloudSync()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleFavorite(track: Track): Boolean = withContext(Dispatchers.IO) {
        val bareId = stableTrackId(track.id)
        val isCurrentlyLiked = db.isFavorite(bareId)
        if (isCurrentlyLiked) {
            unlikeTrack(bareId)
            false
        } else {
            likeTrack(track)
            true
        }
    }

    suspend fun toggleFavoriteById(trackId: String): Boolean = withContext(Dispatchers.IO) {
        val bareId = stableTrackId(trackId)
        val isCurrentlyLiked = db.isFavorite(bareId)
        if (isCurrentlyLiked) {
            unlikeTrack(bareId)
            false
        } else {
            val trackFromQueue = PlayerController.queueFlow.value.firstOrNull {
                stableTrackId(it.id) == bareId
            }
            if (trackFromQueue != null) {
                likeTrack(trackFromQueue)
            } else {
                val metadata = MusicBackend.getTrackMeta(trackId) ?: return@withContext false
                db.insert(
                    FavoriteTrackEntity(
                        trackId = bareId,
                        title = metadata.title,
                        artistName = metadata.artist,
                        albumTitle = metadata.collectionId,
                        durationMs = metadata.durationMs,
                        imageUrl = metadata.cover,
                        collectionId = metadata.collectionId,
                        genre = metadata.genre,
                        accessKey = accessKeyFromId(trackId),
                        isSynced = false
                    )
                )
                updatePlayerControllerFavorites()
                requestCloudSync()
            }
            true
        }
    }

    private fun FavoriteTrackEntity.toTrack(): Track {
        val playbackId = cloudTrackId ?: trackId
        return Track(
            id = accessKey?.takeIf { it.isNotBlank() && !playbackId.contains("_$it") }
                ?.let { "${playbackId}_$it" }
                ?: playbackId,
            title = title,
            artist = artistName.orEmpty(),
            albumName = albumTitle ?: "",
            uri = com.lmg.vk.engine.VkAudioIdentity.playbackUri(),
            durationMs = durationMs,
            albumId = collectionId?.hashCode()?.toLong() ?: playbackId.hashCode().toLong(),
            coverUrl = imageUrl,
            artists = artistId?.let {
                listOf(com.lmg.vk.engine.backend.MiniArtist(id = it, name = artistName))
            } ?: emptyList(),
            isExplicit = isExplicit,
            source = source,
            genre = genre,
            isAvailable = isAvailable,
        )
    }

    private suspend fun updatePlayerControllerFavorites() {
        val ids = db.getFavoriteTrackIds()
        PlayerController.setFavoriteIds(ids)
    }

    companion object {
        private val CLOUD_SYNCS = java.util.concurrent.atomic.AtomicInteger(0)
        private const val ADD_BATCH_SIZE = 25
        private val CONFIRMATION_RETRY_DELAYS_MS = longArrayOf(1_500L, 3_000L, 6_000L)
        private val SYNC_RETRY_DELAYS_MS = longArrayOf(0L, 2_000L, 10_000L, 30_000L)
        val isCloudSyncInProgress: Boolean get() = CLOUD_SYNCS.get() > 0

        @Volatile
        private var INSTANCE: LibraryRepository? = null

        fun getInstance(context: Context): LibraryRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LibraryRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
}
