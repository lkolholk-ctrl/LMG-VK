package com.lmg.vk.data.local.db

import android.content.Context
import com.lmg.vk.engine.backend.MusicBackend
import com.lmg.vk.engine.backend.MusicAuth
import com.lmg.vk.engine.backend.LibraryTrack
import com.lmg.vk.engine.PlayerController
import com.lmg.vk.engine.Track
import com.lmg.vk.engine.VkAudioIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Repository for the Library (liked tracks).
 * Provides two-way sync between local SQLite DB and backend cloud API.
 *
 * Architecture:
 * - Local SQLite DB is the source of truth for UI (reactive Flow)
 * - Cloud sync happens in background on IO dispatcher
 * - Like/unlike are optimistic: local DB updates immediately, then cloud sync fires
 */
class LibraryRepository private constructor(context: Context) {

    private val db = FavoriteTrackDatabase.getInstance(context)

    /** Reactive flow of all favorite tracks — drives Compose UI */
    val favoritesFlow: Flow<List<FavoriteTrackEntity>> = db.favoritesFlow

    /** Reactive flow of favorite track IDs — drives heart icon states */
    val favoriteIdsFlow: Flow<Set<String>> = db.favoriteIdsFlow

    /** Get single favorite status reactively */
    fun isFavoriteFlow(trackId: String): Flow<Boolean> = db.isFavoriteFlow(stableTrackId(trackId))

    /** Get all favorites as Track objects for playback */
    suspend fun getAllFavoritesAsTracks(): List<Track> = withContext(Dispatchers.IO) {
        db.getAllFavorites().filter(FavoriteTrackEntity::isAvailable).map { it.toTrack() }
    }

    /** Get count of favorites */
    suspend fun getFavoriteCount(): Int = withContext(Dispatchers.IO) {
        db.getCount()
    }

    init {
        // Load DB data asynchronously on IO
        CoroutineScope(Dispatchers.IO).launch {
            db.loadAsync()
            // Sync PlayerController's in-memory favorite IDs after load
            val ids = db.getFavoriteTrackIds()
            PlayerController.setFavoriteIds(ids)
        }
    }

    /**
     * Треки, для которых `audio.add`/`audio.delete` УЖЕ отправляется прямо
     * сейчас.
     *
     * ЗАЧЕМ. Жалоба «добавляется два аудио вместо одного» — это гонка двух
     * путей отправки. [likeTrack] пишет запись с `isSynced = 0` и уходит в сеть
     * в отдельной корутине, снимая флаг ТОЛЬКО по успеху. Если в этот промежуток
     * (сотни миллисекунд по мобильной сети) успевает пройти [syncWithCloud], он
     * видит ту же запись в `getPendingInserts()` (условие — ровно `isSynced=0`)
     * и отправляет `audio.add` ВТОРОЙ раз. VK на повторный `audio.add` создаёт
     * НОВУЮ копию, а не игнорирует запрос — отсюда два трека в «Моей музыке».
     *
     * Флага в БД для этого недостаточно: он не различает «ещё не отправляли» и
     * «отправка идёт», а третий столбец завёл бы миграцию схемы ради состояния,
     * которое живёт секунды и не должно переживать перезапуск.
     */
    private val inFlight = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>(),
    )

    /** Один стабильный ключ для БД, inFlight и heart-state. */
    private fun stableTrackId(rawId: String): String =
        VkAudioIdentity.stableFullId(rawId)

    private fun accessKeyFromId(rawId: String): String? =
        VkAudioIdentity.normalizeFullId(rawId)
            .split('_', limit = 3)
            .getOrNull(2)
            ?.takeIf(String::isNotBlank)

    private fun FavoriteTrackEntity.addRequestId(): String =
        accessKey?.takeIf(String::isNotBlank)?.let { "${trackId}_$it" } ?: trackId

    /**
     * Связывает оптимистичную локальную строку с id копии, созданной VK.
     * trackId намеренно не меняется: по нему сердечко остаётся активным на
     * исходной карточке. Для сети и воспроизведения используется cloudTrackId.
     */
    private suspend fun finalizeCloudIdentity(localTrackId: String, addedTrackId: String) {
        val stableLocalId = stableTrackId(localTrackId)
        val stableCloudId = stableTrackId(addedTrackId)
        val local = db.getByTrackId(stableLocalId) ?: return
        db.update(
            local.copy(
                cloudTrackId = stableCloudId,
                // access_key принадлежал исходной чужой записи. Для новой
                // пользовательской копии он будет обновлён ближайшим audio.get.
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

    // ═══════════════════════════════════════════════════════════
    //  Two-way sync with cloud
    // ═══════════════════════════════════════════════════════════

    /**
     * Full sync: pull cloud likes, merge with local state, push pending changes.
     * Call on app launch or when user pulls to refresh.
     */
    suspend fun syncWithCloud(): Result<Unit> = withContext(Dispatchers.IO) {
        if (!MusicBackend.isInitialized || !MusicAuth.isLoggedIn.value) {
            return@withContext Result.success(Unit)
        }
        try {
            // 1. Pull cloud likes
            val cloudLikes = mutableListOf<LibraryTrack>()
            var offset = 0
            val limit = 500
            while (true) {
                val response = MusicBackend.getLibraryLikes(
                    source = "all",
                    limit = limit,
                    offset = offset
                )
                    ?: return@withContext Result.failure(
                        Exception("likes page fetch failed at offset=$offset")
                    )
                val items = response.items
                if (items.isEmpty()) break
                cloudLikes.addAll(items)
                if (items.size < limit) break
                offset += items.size
            }

            val cloudIds = cloudLikes.map { stableTrackId(it.id) }.toSet()
            val localEntities = db.getAllFavorites()
            val pendingDeletesBeforePull = db.getPendingDeletes()
            val mergeCandidates = localEntities.filter { entity ->
                val storedCloudId = entity.cloudTrackId?.let(::stableTrackId)
                val pendingInsert = !entity.isSynced && storedCloudId == null
                val legacyMisbound = entity.isSynced &&
                    storedCloudId == stableTrackId(entity.trackId) &&
                    storedCloudId !in cloudIds
                !entity.pendingDelete && (pendingInsert || legacyMisbound)
            }
            val claimedLocalIds = mutableSetOf<String>()

            // 2. Merge the cloud copy with the optimistic source row. The VK
            // copy has another owner/audio id, so id equality alone is not enough
            // after an app restart between audio.add and local finalization.
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
                    // Старая версия могла успеть вставить cloud-копию
                    // отдельно, но оставить исходную строку pending. Сливаем
                    // только локальные строки; сама копия в VK не трогается.
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
                            // Ключ доступа — из выдачи audio.get; без него
                            // audio.getById не отдаст url при воспроизведении.
                            accessKey = cloudTrack.accessKey,
                            likedAt = cloudTrack.likedAt ?: System.currentTimeMillis(),
                            isSynced = true,
                            pendingDelete = false
                        )
                    )
                } else if (!existing.pendingDelete) {
                    // audio.get is the source of truth for display metadata. Without
                    // this branch renamed tracks, artist links and covers stayed stale.
                    db.update(
                        existing.copy(
                            cloudTrackId = cloudId,
                            title = cloudTrack.title,
                            artistName = cloudTrack.artist,
                            durationMs = cloudTrack.durationMs,
                            imageUrl = cloudTrack.cover,
                            artistId = cloudTrack.artistId,
                            collectionId = cloudTrack.collectionId,
                            isExplicit = cloudTrack.isExplicit,
                            source = cloudTrack.source,
                            likedAt = cloudTrack.likedAt ?: existing.likedAt,
                            isAvailable = cloudTrack.isAvailable,
                            // Ключ мог появиться позже (у старых записей он NULL)
                            // либо смениться — VK их периодически перевыпускает.
                            accessKey = cloudTrack.accessKey
                                ?: existing.accessKey.takeIf { existing.cloudTrackId == cloudId },
                            isSynced = true,
                        )
                    )
                }
                // pendingDelete не воскресает только потому, что запаздывающий
                // audio.get ещё успел вернуть удаляемую запись.
            }

            // 3. НАМЕРЕННО НЕ удаляем локальные лайки, которых нет в облачном
            //    снимке. Раньше здесь был delete-loop «нет в облаке → снести» (для
            //    кросс-девайс анлайка). Из-за toggle-семантики /library/likes +
            //    запаздывания чтения он раз за разом сносил ТОЛЬКО ЧТО поставленное
            //    сердечко («лайки исчезают»). Локальный лайк теперь убирается ТОЛЬКО
            //    явным анлайком пользователя (unlikeTrack). Единственный компромисс:
            //    анлайк, сделанный на ВЕБЕ, сам не пропадёт в приложении (сними в
            //    приложении — пропадёт везде). Стабильность сердечка важнее.

            // 4. Clear any pending deletes that are already gone from cloud
            for (pending in pendingDeletesBeforePull) {
                val cloudId = pending.cloudTrackId?.let(::stableTrackId)
                if (cloudId != null && cloudId !in cloudIds) {
                    db.deleteByTrackId(pending.trackId)
                }
            }

            // 5. Push pending local changes to cloud
            // Пропускаем то, что прямо сейчас отправляет likeTrack/unlikeTrack:
            // иначе один и тот же трек уходит в VK дважды, и он создаёт дубль
            // (см. поле inFlight).
            val pendingInserts = db.getPendingInserts()
            for (insert in pendingInserts) {
                if (!inFlight.add(insert.trackId)) continue
                try {
                    val addedId = MusicBackend.addTrackToLibrary(insert.addRequestId())
                    if (addedId != null) {
                        finalizeCloudIdentity(insert.trackId, addedId)
                    }
                } finally {
                    inFlight.remove(insert.trackId)
                }
            }

            val pendingDeletes = db.getPendingDeletes()
            for (delete in pendingDeletes) {
                if (!inFlight.add(delete.trackId)) continue
                try {
                    val cloudId = delete.cloudTrackId ?: delete.trackId
                    val success = MusicBackend.unlikeTrack(cloudId)
                    if (success) {
                        db.deleteByTrackId(delete.trackId)
                    }
                } finally {
                    inFlight.remove(delete.trackId)
                }
            }

            updatePlayerControllerFavorites()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Like a track: immediate local insert, then background cloud sync.
     */
    suspend fun likeTrack(track: Track): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Track.id может нести access_key третьим сегментом (owner_audio_key).
            // Разбираем ДО поиска в БД: там trackId хранится БЕЗ ключа, и поиск
            // по полному id не нашёл бы существующую запись — трек задвоился бы.
            val bareId = stableTrackId(track.id)
            val keyFromId = accessKeyFromId(track.id)

            val existing = db.getByTrackId(bareId)
            if (existing != null) {
                var current = existing
                if (existing.pendingDelete) {
                    // Запись уже имеет облачный id — повторный audio.add создал бы
                    // ещё одну копию. Возвращаем только локальное состояние.
                    current = existing.copy(
                        pendingDelete = false,
                        isSynced = existing.cloudTrackId != null,
                    )
                    db.update(current)
                }
                // Уже лайкнут. Ключ всё же обновим, если он появился только
                // сейчас: у записей, добавленных до v6, колонка пустая.
                if (keyFromId != null && current.accessKey.isNullOrBlank()) {
                    db.update(current.copy(accessKey = keyFromId))
                }
                return@withContext Result.success(Unit)
            }

            // Занимаем ключ ДО появления pending-строки в БД. Иначе
            // syncWithCloud может вклиниться между insert и стартом
            // фоновой корутины и успеть отправить второй audio.add.
            if (!inFlight.add(bareId)) return@withContext Result.success(Unit)

            try {
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
                // Update PlayerController favorite IDs for reactive UI
                updatePlayerControllerFavorites()
            } catch (e: Exception) {
                inFlight.remove(bareId)
                throw e
            }

            // Asynchronously push to cloud
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val addedId = MusicBackend.addTrackToLibrary(track.id)
                    if (addedId != null) {
                        finalizeCloudIdentity(bareId, addedId)
                    }
                } catch (_: Exception) {
                } finally {
                    inFlight.remove(bareId)
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Unlike a track: immediate local delete (soft), then background cloud sync.
     */
    suspend fun unlikeTrack(trackId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val stableInputId = stableTrackId(trackId)
            val existing = db.getByTrackId(stableInputId) ?: return@withContext Result.success(Unit)
            val localId = existing.trackId
            val cloudId = existing.cloudTrackId ?: stableInputId

            // Soft delete: mark pendingDelete, actual removal after cloud sync
            db.update(existing.copy(pendingDelete = true, isSynced = false))

            // Update PlayerController favorite IDs for reactive UI
            updatePlayerControllerFavorites()

            // Asynchronously push delete to cloud
            CoroutineScope(Dispatchers.IO).launch {
                // Та же защита, что у лайка: повторный audio.delete на уже
                // удалённый трек — лишний запрос, а при гонке с очередью ещё и
                // снос записи, которую пользователь успел вернуть.
                if (!inFlight.add(localId)) return@launch
                try {
                    val success = MusicBackend.unlikeTrack(cloudId)
                    if (success) {
                        db.deleteByTrackId(localId)
                    }
                } catch (_: Exception) {
                } finally {
                    inFlight.remove(localId)
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Toggle like status for a track.
     */
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

    /**
     * Toggle like by track ID only (used from PlayerController when Track object not available).
     */
    suspend fun toggleFavoriteById(trackId: String): Boolean = withContext(Dispatchers.IO) {
        val bareId = stableTrackId(trackId)
        val isCurrentlyLiked = db.isFavorite(bareId)
        if (isCurrentlyLiked) {
            unlikeTrack(bareId)
            false
        } else {
            // Need to fetch track metadata to insert
            // Try to get from current queue first
            val trackFromQueue = PlayerController.queueFlow.value.firstOrNull {
                stableTrackId(it.id) == bareId
            }
            if (trackFromQueue != null) {
                likeTrack(trackFromQueue)
            } else {
                // Minimal insert with just ID — will be enriched on next sync
                db.insert(
                    FavoriteTrackEntity(
                        trackId = bareId,
                        title = "", // Will be enriched on sync
                        artistName = null,
                        isSynced = false
                    )
                )
                updatePlayerControllerFavorites()
            }
            true
        }
    }

    /**
     * Convert local entity to Track for playback.
     */
    private fun FavoriteTrackEntity.toTrack(): Track {
        val playbackId = cloudTrackId ?: trackId
        return Track(
            // access_key дописываем третьим сегментом id — ровно в той форме,
            // которую ждёт MusicBackend.resolveTrack (как AudioFile.asIdWithKey()
            // в VK MP3 Mod). Без ключа audio.getById возвращает трек БЕЗ поля
            // url, и библиотека отвечала «трек не найден», хотя трек есть.
            // Раньше ключ жил только в trackCache в памяти, поэтому музыка из
            // библиотеки играла лишь до перезапуска приложения.
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
            // artistId хранится в БД — прокидываем, чтобы тап по артисту
            // в FullPlayer работал и для лайкнутых треков.
            artists = artistId?.let {
                listOf(com.lmg.vk.engine.backend.MiniArtist(id = it, name = artistName))
            } ?: emptyList(),
            isExplicit = isExplicit,
            source = source,
            genre = genre,
            isAvailable = isAvailable,
        )
    }

    /**
     * Sync PlayerController's in-memory favorite IDs with DB state.
     */
    private suspend fun updatePlayerControllerFavorites() {
        val ids = db.getFavoriteTrackIds()
        PlayerController.setFavoriteIds(ids)
    }

    companion object {
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
