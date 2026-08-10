package com.lmg.vk.data.local.db

import android.content.Context
import com.lmg.vk.engine.backend.MusicBackend
import com.lmg.vk.engine.backend.MusicAuth
import com.lmg.vk.engine.backend.LibraryTrack
import com.lmg.vk.engine.PlayerController
import com.lmg.vk.engine.Track
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
    fun isFavoriteFlow(trackId: String): Flow<Boolean> = db.isFavoriteFlow(trackId)

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

            val cloudIds = cloudLikes.map { it.id }.toSet()
            val localEntities = db.getAllFavorites()
            val localPendingDeleteIds = localEntities.filter { it.pendingDelete }.map { it.trackId }.toSet()

            // 2. Add cloud likes that are not in local DB (or were pending delete)
            for (cloudTrack in cloudLikes) {
                val existing = db.getByTrackId(cloudTrack.id)
                if (existing == null || existing.pendingDelete) {
                    db.insert(
                        FavoriteTrackEntity(
                            trackId = cloudTrack.id,
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
                } else if (!existing.isSynced) {
                    // Local was pending insert, now confirmed by cloud
                    db.markSynced(cloudTrack.id)
                } else {
                    // audio.get is the source of truth for display metadata. Without
                    // this branch renamed tracks, artist links and covers stayed stale.
                    db.update(
                        existing.copy(
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
                            accessKey = cloudTrack.accessKey ?: existing.accessKey,
                            isSynced = true,
                        )
                    )
                }
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
            for (pendingId in localPendingDeleteIds) {
                if (pendingId !in cloudIds) {
                    db.deleteByTrackId(pendingId)
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
                    val success = MusicBackend.likeTrack(insert.trackId)
                    if (success) {
                        db.markSynced(insert.trackId)
                    }
                } finally {
                    inFlight.remove(insert.trackId)
                }
            }

            val pendingDeletes = db.getPendingDeletes()
            for (delete in pendingDeletes) {
                if (!inFlight.add(delete.trackId)) continue
                try {
                    val success = MusicBackend.unlikeTrack(delete.trackId)
                    if (success) {
                        db.deleteByTrackId(delete.trackId)
                    }
                } finally {
                    inFlight.remove(delete.trackId)
                }
            }

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
            val parts = track.id.split('_')
            val bareId = if (parts.size >= 3) "${parts[0]}_${parts[1]}" else track.id
            val keyFromId = parts.getOrNull(2)?.takeIf { it.isNotBlank() }

            val existing = db.getByTrackId(bareId)
            if (existing != null) {
                if (existing.pendingDelete) {
                    // Was pending delete, restore it
                    db.insert(existing.copy(pendingDelete = false, isSynced = false))
                }
                // Уже лайкнут. Ключ всё же обновим, если он появился только
                // сейчас: у записей, добавленных до v6, колонка пустая.
                if (keyFromId != null && existing.accessKey.isNullOrBlank()) {
                    db.update(existing.copy(accessKey = keyFromId))
                }
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

            // Update PlayerController favorite IDs for reactive UI
            updatePlayerControllerFavorites()

            // Asynchronously push to cloud
            CoroutineScope(Dispatchers.IO).launch {
                // inFlight занимаем ДО сети: пока запрос летит, syncWithCloud
                // видит запись в pendingInserts и отправил бы audio.add второй
                // раз — VK создал бы дубль (см. поле inFlight).
                if (!inFlight.add(track.id)) return@launch
                try {
                    val success = MusicBackend.likeTrack(track.id)
                    if (success) {
                        // markSynced ОБЯЗАТЕЛЕН: без него локальный pending-флаг
                        // повторно отправит уже применённое изменение.
                        db.markSynced(track.id)
                    }
                } catch (_: Exception) {
                } finally {
                    inFlight.remove(track.id)
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
            val existing = db.getByTrackId(trackId) ?: return@withContext Result.success(Unit)

            // Soft delete: mark pendingDelete, actual removal after cloud sync
            db.update(existing.copy(pendingDelete = true, isSynced = false))

            // Update PlayerController favorite IDs for reactive UI
            updatePlayerControllerFavorites()

            // Asynchronously push delete to cloud
            CoroutineScope(Dispatchers.IO).launch {
                // Та же защита, что у лайка: повторный audio.delete на уже
                // удалённый трек — лишний запрос, а при гонке с очередью ещё и
                // снос записи, которую пользователь успел вернуть.
                if (!inFlight.add(trackId)) return@launch
                try {
                    val success = MusicBackend.unlikeTrack(trackId)
                    if (success) {
                        db.deleteByTrackId(trackId)
                    }
                } catch (_: Exception) {
                } finally {
                    inFlight.remove(trackId)
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
        val isCurrentlyLiked = db.isFavorite(track.id)
        if (isCurrentlyLiked) {
            unlikeTrack(track.id)
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
        val isCurrentlyLiked = db.isFavorite(trackId)
        if (isCurrentlyLiked) {
            unlikeTrack(trackId)
            false
        } else {
            // Need to fetch track metadata to insert
            // Try to get from current queue first
            val trackFromQueue = PlayerController.queueFlow.value.firstOrNull { it.id == trackId }
            if (trackFromQueue != null) {
                likeTrack(trackFromQueue)
            } else {
                // Minimal insert with just ID — will be enriched on next sync
                db.insert(
                    FavoriteTrackEntity(
                        trackId = trackId,
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
        return Track(
            // access_key дописываем третьим сегментом id — ровно в той форме,
            // которую ждёт MusicBackend.resolveTrack (как AudioFile.asIdWithKey()
            // в VK MP3 Mod). Без ключа audio.getById возвращает трек БЕЗ поля
            // url, и библиотека отвечала «трек не найден», хотя трек есть.
            // Раньше ключ жил только в trackCache в памяти, поэтому музыка из
            // библиотеки играла лишь до перезапуска приложения.
            id = accessKey?.takeIf { it.isNotBlank() && !trackId.contains("_$it") }
                ?.let { "${trackId}_$it" }
                ?: trackId,
            title = title,
            artist = artistName.orEmpty(),
            albumName = albumTitle ?: "",
            uri = com.lmg.vk.engine.VkAudioIdentity.playbackUri(),
            durationMs = durationMs,
            albumId = collectionId?.hashCode()?.toLong() ?: trackId.hashCode().toLong(),
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
