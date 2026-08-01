package com.lmg.vk.engine.backend

import com.lmg.vk.engine.Track
import com.lmg.vk.engine.LyricsParser
import com.lmg.vk.engine.backend.wave.WaveBatchResponse
import com.lmg.vk.engine.backend.wave.WaveSessionStartResponse
import com.lmg.vk.network.VkApiClient
import com.lmg.vk.network.VkResult
import com.lmg.vk.network.VkSessionStore
import com.lmg.vk.network.dto.music.AudioPlaylist
import com.lmg.vk.network.dto.music.AudioAlbum
import com.lmg.vk.network.dto.music.AlbumThumb
import com.lmg.vk.network.dto.music.AudioPlaylistDto
import com.lmg.vk.network.dto.music.AudioAudioDto
import com.lmg.vk.network.dto.music.AudioArtistDto
import com.lmg.vk.network.dto.music.AudioStreamMix
import com.lmg.vk.network.dto.music.AudioTrack
import com.lmg.vk.network.dto.music.MainArtist
import com.lmg.vk.network.dto.music.VkArtistDto
import com.lmg.vk.network.methods.VkAudioApi
import com.lmg.vk.network.methods.VkCatalogApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonPrimitive
import android.net.Uri
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/**
 * Нейтральный фасад музыкального бэкенда (замена api.icm.*).
 * Все вызовы UI/engine к бэкенду сходятся сюда; реализация — поверх
 * VK-слоя (com.lmg.vk.network.*). Методы помечены TODO(vk-wire).
 *
 * StreamInfo / модели — в BackendModels.kt, wave-модели — в backend/wave.
 */

/** Ошибка бэкенда (бывш. IcmApiException). */
class BackendException(
    val code: Int,
    message: String,
    val errorCode: String? = null,
    val requiredRegion: String? = null,
) : Exception(message)

/** Человекочитаемое описание ошибки по коду. */
fun backendUserMessage(kind: Int, code: Int): String = when (code) {
    401 -> "Требуется авторизация"
    403 -> "Доступ запрещён"
    404 -> "Не найдено"
    429 -> "Слишком много запросов"
    451 -> "Недоступно в вашем регионе"
    else -> "Ошибка бэкенда ($code)"
}

/** Человекочитаемое описание ошибки для UI (из исключения). */
fun backendUserMessage(exception: Throwable?): String = when (exception) {
    is BackendException -> backendUserMessage(0, exception.code)
    else -> exception?.message ?: "Что-то пошло не так"
}

object MusicBackend {

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError
    private val _lastApiException = MutableStateFlow<BackendException?>(null)
    val lastApiException: StateFlow<BackendException?> = _lastApiException

    var isInitialized: Boolean = false; private set
    val region: String get() = "ru"

    private lateinit var audioApi: VkAudioApi
    private lateinit var catalogApi: VkCatalogApi
    private lateinit var sessionStore: VkSessionStore
    private val trackCache = ConcurrentHashMap<String, AudioTrack>()
    private data class CachedStream(val info: StreamInfo, val cachedAtMs: Long)
    private val streamCache = ConcurrentHashMap<String, CachedStream>()
    private val waveMutex = Mutex()
    private val waveQueue = ArrayDeque<AudioTrack>()
    private var activeWaveMix: AudioStreamMix? = null
    private var activeWaveAppend = false
    private var waveSessionId: String? = null
    private var recommendationOffset = 0

    fun init(client: VkApiClient, sessions: VkSessionStore) {
        audioApi = VkAudioApi(client)
        catalogApi = VkCatalogApi(client)
        sessionStore = sessions
        isInitialized = true
        MusicAuth.init(sessions)
    }

    fun getInstance(): MusicBackend = this
    fun getLastErrorCode(): String? = null
    fun getLastHttpCode(): Int = 200

    // ---------- стрим ----------
    suspend fun getTrackInfo(trackId: String, quality: String = "lossless", region: String? = null): StreamInfo {
        requireInitialized()
        streamCache[normalizeTrackId(trackId)]?.let { cached ->
            if (System.currentTimeMillis() - cached.cachedAtMs < STREAM_CACHE_TTL_MS) {
                return cached.info
            }
        }
        val track = resolveTrack(trackId, forceNetwork = true)
        if (track.url.isBlank()) throw backendFailure(404, "VK не вернул URL трека")
        return track.toStreamInfo(quality).also {
            streamCache[track.fullId] = CachedStream(it, System.currentTimeMillis())
        }
    }

    fun getTrackInfoSync(trackId: String, quality: String = "lossless"): StreamInfo {
        requireInitialized()
        val id = normalizeTrackId(trackId)
        return streamCache[id]
            ?.takeIf { System.currentTimeMillis() - it.cachedAtMs < STREAM_CACHE_TTL_MS }
            ?.info
            ?: throw backendFailure(404, "Трек ещё не загружен в сетевой кэш")
    }

    suspend fun getStreamUrl(trackId: String, source: String? = null): String? =
        runCatching { getTrackInfo(trackId, streamQuality).url }.getOrNull()

    suspend fun getTrackMeta(trackId: String): TrackMeta? =
        runCatching { resolveTrack(trackId).toTrackMeta() }.getOrNull()

    suspend fun getBatchTrackMeta(ids: List<String>): Result<BatchTrackMetaResponse> = runCatching {
        requireInitialized()
        val requested = ids.map(::normalizeTrackId)
        val missing = requested.filterNot(trackCache::containsKey)
        if (missing.isNotEmpty()) cacheTracks(audioApi.getById(missing).requireData())
        BatchTrackMetaResponse(
            count = requested.size,
            items = requested.map { id ->
                trackCache[id]?.let { track ->
                    BatchTrackMetaItem(
                        id = id,
                        title = track.title,
                        artist = track.artist,
                        cover = track.coverUrl(),
                        duration = track.duration.toLong(),
                        collectionId = track.album?.id?.toString(),
                        trackId = id,
                    )
                } ?: BatchTrackMetaItem(id = id, trackId = id, error = "track_not_found")
            },
        )
    }

    // ---------- клипы (Apple Music video) ----------
    suspend fun searchClips(query: String): Result<ClipSearchResponse> = TODO("vk-wire")
    suspend fun resolveClipStreamUrl(clipId: String): Result<String> = TODO("vk-wire")

    // ---------- поиск ----------
    suspend fun searchTracks(query: String, source: String = "vk", limit: Int = 30, region: String? = null): List<SearchItem> =
        audioApi.searchAudios(
            query = query,
            ownerId = currentUserId(),
            offset = 0,
            count = limit,
        ).requireData().also(::cacheTracks).map(AudioTrack::toSearchItem)

    suspend fun searchAll(query: String, region: String? = null, source: String = SearchSource.ALL, limit: Int = 30): SearchResponse? =
        runCatching {
            requireInitialized()
            val result = audioApi.searchMain(query = query, offset = 0, count = limit).requireData()
            val tracks = (result.audios.items + result.own_audios.items)
                .distinctBy(AudioAudioDto::fullId)
                .map(AudioAudioDto::toAudioTrack)
                .also(::cacheTracks)
            val albums = (result.albums.items + result.own_albums.items)
                .distinctBy(AudioPlaylistDto::fullId)
            val playlists = (result.playlists.items + result.own_playlists.items)
                .distinctBy(AudioPlaylistDto::fullId)
            SearchResponse(
                query = query,
                region = this.region,
                source = "vk",
                items = buildList {
                    addAll(tracks.map(AudioTrack::toSearchItem))
                    addAll(result.artists.items.distinctBy { it.id ?: it.domain ?: it.name }
                        .map(AudioArtistDto::toSearchItem))
                    addAll(albums.map(AudioPlaylistDto::toSearchItem))
                    addAll(playlists.map(AudioPlaylistDto::toSearchItem))
                },
            )
        }.getOrNull()

    fun clearSearchCache() {
        trackCache.clear()
        streamCache.clear()
    }

    // ---------- home / charts ----------
    suspend fun loadHomeContent(region: String? = null): HomeResponse {
        requireInitialized()
        val catalog = catalogApi.getAudioAuto().requireData()
        val catalogTracks = catalog.audios.orEmpty().also(::cacheTracks)
        val popularTracks = when (val result = audioApi.getPopular(count = 50)) {
            is VkResult.Success -> result.data.also(::cacheTracks)
            is VkResult.Error -> emptyList()
        }

        val blocks = buildList {
            val releases = catalog.playlists.orEmpty().map(AudioPlaylist::toHomeItem)
            if (releases.isNotEmpty()) {
                add(HomeBlock("vk_new_releases", "Новые релизы", "new_releases", releases))
            }
            if (popularTracks.isNotEmpty()) {
                add(HomeBlock("vk_charts", "Популярное", "charts", popularTracks.map(AudioTrack::toHomeItem)))
            }
            if (catalogTracks.isNotEmpty()) {
                add(HomeBlock("vk_recommendations", "Рекомендации", "recommendations", catalogTracks.map(AudioTrack::toHomeItem)))
            }
            val artists = catalog.artists.orEmpty().map(VkArtistDto::toHomeItem)
            if (artists.isNotEmpty()) {
                add(HomeBlock("vk_artists", "Исполнители", "artists", artists))
            }
        }
        return HomeResponse(blocks = blocks, updatedAt = System.currentTimeMillis())
    }

    suspend fun loadCharts(region: String? = null): List<Chart> {
        requireInitialized()
        val tracks = audioApi.getPopular(count = 100).requireData().also(::cacheTracks)
        if (tracks.isEmpty()) return emptyList()
        return listOf(
            Chart(
                id = "vk_popular",
                name = "VK Музыка",
                query = "popular",
                cover = tracks.firstNotNullOfOrNull(AudioTrack::coverUrl),
                tracks = tracks.map(AudioTrack::toSearchItem),
            ),
        )
    }

    // ---------- альбом/артист ----------
    suspend fun getAlbum(albumId: String): AlbumResponse? = runCatching {
        requireInitialized()
        val (ownerId, id) = parsePlaylistId(albumId)
        val playlist = audioApi.getPlaylistById(ownerId, id).requireData()
        val tracks = audioApi.getAudios(
            ownerId = ownerId,
            playlistId = id,
            offset = 0,
            count = 6000,
        ).requireData().also(::cacheTracks)
        AlbumResponse(playlist.toAlbum(), tracks.map(AudioTrack::toAlbumTrack))
    }.getOrNull()

    suspend fun getArtist(artistId: String): ArtistResponse? = runCatching {
        requireInitialized()
        val normalizedId = artistId.removePrefix("vk_")
        val catalog = catalogApi.getAudioArtist(normalizedId).requireData()
        val tracks = catalog.audios.orEmpty().ifEmpty {
            audioApi.getAudiosByArtist(normalizedId).requireData()
        }.also(::cacheTracks)
        val artist = catalog.artists.orEmpty().firstOrNull { it.id == normalizedId }
            ?: catalog.artists.orEmpty().firstOrNull()
            ?: VkArtistDto(
                id = normalizedId,
                name = tracks.firstOrNull()?.main_artists?.firstOrNull()?.name
                    ?: tracks.firstOrNull()?.artist.orEmpty(),
            )
        val related = when (val response = audioApi.getRelatedArtistsById(normalizedId)) {
            is VkResult.Success -> response.data.artists
            is VkResult.Error -> emptyList()
        }
        val albums = catalog.playlists.orEmpty().map(AudioPlaylist::toArtistAlbum)
        ArtistResponse(
            id = artist.id,
            name = artist.name,
            genre = artist.genres.orEmpty().joinToString(", ") { it.name }.takeIf(String::isNotBlank),
            url = artist.domain?.let { "https://vk.com/artist/$it" },
            image = artist.coverUrl(),
            cover = artist.coverUrl(),
            bio = artist.bio,
            topSongs = tracks.map(AudioTrack::toArtistSong),
            latestRelease = albums.maxByOrNull { it.year.orEmpty() },
            albums = albums.filterNot { it.type.equals("single", ignoreCase = true) },
            singles = albums.filter { it.type.equals("single", ignoreCase = true) },
            similarArtists = related.map {
                SimilarArtist(id = it.id, name = it.name, url = it.domain, cover = it.coverUrl())
            },
            source = "vk",
        )
    }.getOrNull()

    suspend fun getArtistTopTracks(artistId: String): List<Track> =
        audioApi.getAudiosByArtist(artistId.removePrefix("vk_")).requireData()
            .also(::cacheTracks)
            .map(AudioTrack::toEngineTrack)

    // ---------- лайки / библиотека ----------
    suspend fun getLibraryLikes(source: String = "all", limit: Int = 500, offset: Int = 0): LibraryLikesResponse? =
        runCatching {
            val tracks = audioApi.getAudios(
                ownerId = currentUserId(),
                offset = offset,
                count = limit.coerceIn(1, 6000),
            ).requireData().also(::cacheTracks)
            LibraryLikesResponse(
                items = tracks.map(AudioTrack::toLibraryTrack),
                count = tracks.size,
                offset = offset,
                limit = limit,
            )
        }.getOrNull()

    suspend fun likeTrack(trackId: String, liked: Boolean = true): Boolean {
        if (!liked) return unlikeTrack(trackId)
        return runCatching {
            val track = resolveTrack(trackId)
            audioApi.add(track.fullId, track.access_key).requireData()
            true
        }.getOrDefault(false)
    }

    suspend fun unlikeTrack(trackId: String): Boolean = runCatching {
        audioApi.delete(normalizeTrackId(trackId)).requireData()
        true
    }.getOrDefault(false)

    // ---------- плейлисты пользователя ----------
    suspend fun getUserPlaylists(limit: Int = 100): UserPlaylistsResponse {
        val ownerId = currentUserId()
        val playlists = audioApi.getPlaylists(
            ownerId = ownerId,
            offset = 0,
            count = limit.coerceIn(1, 100),
        ).requireData()
        return UserPlaylistsResponse(
            count = playlists.size,
            items = playlists.map { playlist -> playlist.toUserPlaylist() },
        )
    }

    suspend fun getUserPlaylistTracks(playlistId: String, limit: Int = 200, offset: Int = 0): PlaylistTracksResponse? =
        runCatching {
            val (ownerId, id) = parsePlaylistId(playlistId)
            val tracks = audioApi.getAudios(
                ownerId = ownerId,
                offset = offset,
                count = limit.coerceIn(1, 6000),
                playlistId = id,
            ).requireData().also(::cacheTracks)
            val playlist = if (offset == 0) {
                audioApi.getPlaylistById(ownerId, id).requireData().toAlbum()
            } else null
            PlaylistTracksResponse(
                playlist = playlist,
                tracks = tracks.map(AudioTrack::toPlaylistTrack),
            )
        }.getOrNull()
    suspend fun deleteUserPlaylist(playlistId: String): Boolean = runCatching {
        val (ownerId, id) = parsePlaylistId(playlistId)
        audioApi.deletePlaylist(ownerId, id).requireData()
        true
    }.getOrDefault(false)
    suspend fun previewPlaylist(source: String, url: String): PlaylistPreviewResponse? = TODO("vk-wire")
    suspend fun importPlaylist(source: String, url: String, name: String?): PlaylistImportResponse? = TODO("vk-wire")
    suspend fun getImportJobStatus(jobId: String): PlaylistImportJobResponse? = TODO("vk-wire")

    // ---------- тексты ----------
    suspend fun getLyricsResult(trackId: String): Result<LyricsParser.Lyrics?> = runCatching {
        requireInitialized()
        val track = resolveTrack(trackId)
        if (!track.has_lyrics && track.lyrics_id == null) return@runCatching null
        val container = audioApi.getLyrics(track.fullId).requireData()
        val lyrics = container.lyrics
        val timestamps = lyrics.timestamps.orEmpty()
        val lines = if (timestamps.isNotEmpty()) {
            timestamps.map {
                LyricsParser.LyricLine(
                    timeMs = it.begin,
                    text = it.line,
                    endMs = it.end,
                )
            }
        } else {
            lyrics.text.orEmpty().map { LyricsParser.LyricLine(timeMs = -1L, text = it) }
        }
        LyricsParser.Lyrics(
            lines = lines,
            isSynced = timestamps.isNotEmpty(),
            title = track.title,
            artist = track.artist,
            source = "vk",
        )
    }

    // ---------- волна / радио ----------
    suspend fun startWave(seedTrackId: String? = null): Result<WaveResponse> = runCatching {
        val track = takeWaveTracks(count = 1, reset = true, seedTrackId = seedTrackId).firstOrNull()
        WaveResponse(track = track?.toWaveTrack(), status = if (track != null) "ok" else "empty", region = region)
    }
    suspend fun startSession(
        source: String? = null,
        region: String? = null,
        diversity: Double? = null
    ): Result<WaveSessionStartResponse> = runCatching {
        requireInitialized()
        waveMutex.withLock {
            resetWaveLocked()
            ensureWaveSourceLocked(seedTrackId = null)
            val id = "vk_mix_${activeWaveMix?.id ?: "recommendations"}_${System.currentTimeMillis()}"
            waveSessionId = id
            WaveSessionStartResponse(
                sessionId = id,
                expiresIn = 3600,
                region = this.region,
                source = "vk",
                diversity = diversity,
            )
        }
    }
    suspend fun nextBatch(
        limit: Int? = null,
        diversity: Double? = null,
        excludeTrackIds: List<String> = emptyList(),
        excludeArtistIds: List<String> = emptyList(),
        playedTrackIds: List<String> = emptyList()
    ): Result<WaveBatchResponse> = runCatching {
        val requested = (limit ?: 30).coerceIn(1, 100)
        val excluded = (excludeTrackIds + playedTrackIds).map(::normalizeTrackId).toSet()
        val tracks = takeWaveTracks(requested + excluded.size.coerceAtMost(20))
            .filterNot { it.fullId in excluded }
            .take(requested)
        WaveBatchResponse(
            sessionId = waveSessionId,
            count = tracks.size,
            status = if (tracks.isEmpty()) "empty" else "ok",
            region = this.region,
            source = "vk",
            tracks = tracks.map(AudioTrack::toWaveTrack),
        )
    }
    suspend fun nextSessionBatch(
        sessionId: String,
        limit: Int? = null,
        diversity: Double? = null,
        excludeTrackIds: List<String> = emptyList(),
        excludeArtistIds: List<String> = emptyList(),
        playedTrackIds: List<String> = emptyList()
    ): Result<WaveBatchResponse> {
        if (waveSessionId != null && sessionId != waveSessionId) {
            return Result.failure(backendFailure(404, "Сессия VK Mix устарела"))
        }
        return nextBatch(limit, diversity, excludeTrackIds, excludeArtistIds, playedTrackIds)
    }
    suspend fun genreBatch(
        genre: String,
        limit: Int? = null,
        diversity: Double? = null,
        source: String? = null,
        region: String? = null,
        excludeTrackIds: List<String> = emptyList(),
        excludeArtistIds: List<String> = emptyList(),
        playedTrackIds: List<String> = emptyList()
    ): Result<WaveBatchResponse> = runCatching {
        val requested = (limit ?: 30).coerceIn(1, 100)
        val excluded = (excludeTrackIds + playedTrackIds).map(::normalizeTrackId).toSet()
        val tracks = genre.toIntOrNull()?.let { genreId ->
            audioApi.getPopular(count = requested, genreId = genreId).requireData()
        } ?: takeWaveTracks(requested + excluded.size.coerceAtMost(20))
        cacheTracks(tracks)
        val filtered = tracks.filterNot { it.fullId in excluded }.take(requested)
        WaveBatchResponse(
            sessionId = waveSessionId,
            genre = genre,
            count = filtered.size,
            status = if (filtered.isEmpty()) "empty" else "ok",
            region = this.region,
            source = "vk",
            tracks = filtered.map(AudioTrack::toWaveTrack),
        )
    }
    suspend fun moodBatch(
        mood: String,
        limit: Int? = null,
        diversity: Double? = null,
        source: String? = null,
        region: String? = null,
        excludeTrackIds: List<String> = emptyList(),
        excludeArtistIds: List<String> = emptyList(),
        playedTrackIds: List<String> = emptyList()
    ): Result<WaveBatchResponse> = runCatching {
        val requested = (limit ?: 30).coerceIn(1, 100)
        val excluded = (excludeTrackIds + playedTrackIds).map(::normalizeTrackId).toSet()
        val tracks = takeWaveTracks(requested + excluded.size.coerceAtMost(20))
            .filterNot { it.fullId in excluded }
            .take(requested)
        WaveBatchResponse(
            sessionId = waveSessionId,
            mood = mood,
            count = tracks.size,
            status = if (tracks.isEmpty()) "empty" else "ok",
            region = this.region,
            source = "vk",
            tracks = tracks.map(AudioTrack::toWaveTrack),
        )
    }
    suspend fun nextTrackStation(
        seedTrackId: String?,
        exclude: Collection<String>? = null
    ): Result<WaveResponse> = runCatching {
        val excluded = exclude.orEmpty().map(::normalizeTrackId).toSet()
        val track = takeWaveTracks(
            count = 1 + excluded.size.coerceAtMost(20),
            reset = seedTrackId != null,
            seedTrackId = seedTrackId,
        ).firstOrNull { it.fullId !in excluded }
        WaveResponse(track = track?.toWaveTrack(), status = if (track != null) "ok" else "empty", region = region)
    }
    suspend fun getWaveNext(seedTrackId: String?, exclude: List<String>? = null): Result<WaveResponse> =
        nextTrackStation(seedTrackId, exclude)
    suspend fun getWaveOnboarding(): List<WaveOnboardingArtist> =
        audioApi.recommendationsOnboarding().requireData().items.map {
            WaveOnboardingArtist(id = it.id, name = it.name, image = it.coverUrl())
        }
    suspend fun saveWaveOnboarding(payload: List<WaveOnboardingArtistSave>): Boolean = runCatching {
        audioApi.finishRecommendationsOnboarding(payload.map { it.id }).requireData()
        true
    }.getOrDefault(false)
    suspend fun getWavePopularArtists(): List<WaveOnboardingArtist> = getWaveOnboarding()
    suspend fun resetWave(): Boolean {
        waveMutex.withLock { resetWaveLocked() }
        return true
    }

    fun isAppleSeedTrackId(id: String): Boolean = id.matches(Regex("-?\\d+_\\d+"))

    fun getUserRegion(): RegionResponse? = null

    suspend fun updateUserRegion(code: String): RegionResponse? = TODO("vk-wire")

    suspend fun getLibrarySubscriptions(limit: Int = 50): LibrarySubscriptionsResponse? = TODO("vk-wire")

    suspend fun updateUserPreferences(prefs: UserPreferences): UserPreferences? = TODO("vk-wire")
    suspend fun getUserPreferences(): UserPreferences? = TODO("vk-wire")

    var streamQuality: String = "256K"

    private fun requireInitialized() {
        check(isInitialized) { "MusicBackend is not initialized" }
    }

    private fun currentUserId(): Long {
        requireInitialized()
        return sessionStore.session.userId.takeIf { it != 0L }
            ?: throw backendFailure(401, "VK-сессия не содержит user_id")
    }

    private suspend fun resolveTrack(trackId: String, forceNetwork: Boolean = false): AudioTrack {
        requireInitialized()
        val id = normalizeTrackId(trackId)
        if (!forceNetwork) trackCache[id]?.let { return it }
        val track = audioApi.getById(listOf(id)).requireData().firstOrNull()
            ?: throw backendFailure(404, "Трек $id не найден")
        trackCache[track.fullId] = track
        return track
    }

    private fun cacheTracks(tracks: Collection<AudioTrack>) {
        tracks.forEach { track ->
            trackCache[track.fullId] = track
            if (track.url.isNotBlank()) {
                streamCache[track.fullId] = CachedStream(
                    track.toStreamInfo(streamQuality),
                    System.currentTimeMillis(),
                )
            }
        }
    }

    /**
     * Единая очередь личной волны. В оригинале StreamMix отдаёт по пять треков,
     * поэтому большой batch наполняется несколькими последовательными вызовами.
     */
    private suspend fun takeWaveTracks(
        count: Int,
        reset: Boolean = false,
        seedTrackId: String? = null,
    ): List<AudioTrack> = waveMutex.withLock {
        requireInitialized()
        if (reset) resetWaveLocked()
        ensureWaveSourceLocked(seedTrackId)

        var attempts = 0
        while (waveQueue.size < count && attempts < ((count + 4) / 5 + 3)) {
            attempts++
            val loaded = activeWaveMix?.let { mix ->
                audioApi.getStreamMixAudios(
                    mixId = mix.id,
                    entityId = null,
                    append = activeWaveAppend,
                ).requireData().also { activeWaveAppend = true }
            } ?: audioApi.getRecommendations(
                targetAudio = seedTrackId,
                offset = recommendationOffset,
                count = count.coerceIn(5, 100),
                userId = currentUserId(),
            ).requireData().also { recommendationOffset += it.size }

            if (loaded.isEmpty()) break
            cacheTracks(loaded)
            val queuedIds = waveQueue.asSequence().map(AudioTrack::fullId).toHashSet()
            loaded.filterNot { it.fullId in queuedIds }.forEach(waveQueue::addLast)
        }

        buildList(count.coerceAtMost(waveQueue.size)) {
            repeat(count.coerceAtMost(waveQueue.size)) { add(waveQueue.removeFirst()) }
        }
    }

    private suspend fun ensureWaveSourceLocked(seedTrackId: String?) {
        if (seedTrackId != null) {
            activeWaveMix = null
            return
        }
        if (activeWaveMix != null || recommendationOffset > 0) return
        activeWaveMix = when (val catalog = catalogApi.getAudioAuto()) {
            is VkResult.Success -> catalog.data.audio_stream_mixes.orEmpty().firstOrNull()
            is VkResult.Error -> null
        }
    }

    private fun resetWaveLocked() {
        waveQueue.clear()
        activeWaveMix = null
        activeWaveAppend = false
        recommendationOffset = 0
        waveSessionId = null
    }

    private fun normalizeTrackId(id: String): String = id.removePrefix("vk_")

    private fun parsePlaylistId(value: String): Pair<Long, Int> {
        val normalized = value.removePrefix("vk_")
        val separator = normalized.indexOf('_')
        return if (separator > 0 && separator < normalized.lastIndex) {
            normalized.substring(0, separator).toLong() to
                normalized.substring(separator + 1).toInt()
        } else {
            currentUserId() to normalized.toInt()
        }
    }

    private fun backendFailure(code: Int, message: String): BackendException {
        val failure = BackendException(code, message)
        _lastError.value = "$code: $message"
        _lastApiException.value = failure
        return failure
    }

    private fun <T> VkResult<T>.requireData(): T = when (this) {
        is VkResult.Success -> {
            _lastError.value = null
            _lastApiException.value = null
            data
        }
        is VkResult.Error -> throw backendFailure(code, message)
    }

    private fun AudioTrack.coverUrl(): String? =
        album?.thumb?.src?.takeIf(String::isNotBlank)

    private fun AudioTrack.toMiniArtists(): List<MiniArtist> =
        main_artists.orEmpty().map { MiniArtist(id = it.id, name = it.name) }

    private fun AudioTrack.toStreamInfo(quality: String) = StreamInfo(
        trackId = fullId,
        fileId = fullId,
        source = "vk",
        quality = quality,
        artistId = main_artists?.firstOrNull()?.id,
        url = url,
        expiresAt = 0L,
    )

    private fun AudioTrack.toSearchItem() = SearchItem(
        id = fullId,
        title = title,
        artist = artist,
        artistName = artist,
        artistId = main_artists?.firstOrNull()?.id,
        artists = toMiniArtists(),
        cover = coverUrl(),
        collectionId = album?.id?.toString(),
        album = album?.title,
        isExplicit = is_explicit,
        duration = duration.toLong(),
        source = "vk",
        trackId = fullId,
    )

    private fun AudioAudioDto.toAudioTrack() = AudioTrack(
        artist = artist,
        id = id,
        owner_id = owner_id,
        title = title,
        duration = duration,
        access_key = access_key,
        is_explicit = is_explicit == true,
        is_licensed = is_licensed == true,
        track_code = track_code.orEmpty(),
        url = url.orEmpty(),
        date = date?.toLong() ?: 0L,
        album = album?.let { value ->
            AudioAlbum(
                id = value.id,
                owner_id = value.owner_id,
                access_key = value.access_key,
                title = value.title,
                thumb = value.thumb?.let { photo ->
                    AlbumThumb(
                        width = photo.width,
                        height = photo.height,
                        src = photo.bestUrl.orEmpty(),
                    )
                },
            )
        },
        main_artists = main_artists.orEmpty().map {
            MainArtist(id = it.id.orEmpty(), domain = it.domain.orEmpty(), name = it.name)
        },
        featured_artists = featured_artists.orEmpty().map {
            MainArtist(id = it.id.orEmpty(), domain = it.domain.orEmpty(), name = it.name)
        },
        subtitle = subtitle,
        is_focus_track = is_focus_track == true,
        has_lyrics = has_lyrics == true,
        release_audio_id = release_audio_id,
    )

    private fun AudioPlaylistDto.toSearchItem() = SearchItem(
        id = fullId,
        title = title,
        artist = main_artist ?: main_artists?.joinToString(", ") { it.name },
        artistName = main_artist ?: main_artists?.joinToString(", ") { it.name },
        artistId = main_artists?.firstOrNull()?.id,
        artists = main_artists.orEmpty().map {
            MiniArtist(id = it.id.orEmpty(), name = it.name)
        },
        cover = coverUrl(),
        collectionId = fullId,
        album = title,
        isExplicit = is_explicit == true,
        source = "vk",
        isAlbum = true,
    )

    private fun AudioArtistDto.toSearchItem(): SearchItem {
        val artistId = id ?: domain ?: name
        return SearchItem(
            id = artistId,
            title = name,
            artist = name,
            artistName = name,
            artistId = artistId,
            cover = photo.orEmpty().maxByOrNull { it.width * it.height }?.url,
            source = "vk",
            isArtist = true,
        )
    }

    private fun AudioPlaylist.toSearchItem() = SearchItem(
        id = fullId,
        title = title,
        artist = main_artists?.joinToString(", ") { it.name },
        artistName = main_artists?.joinToString(", ") { it.name },
        artistId = main_artists?.firstOrNull()?.id,
        artists = main_artists.orEmpty().map { MiniArtist(id = it.id, name = it.name) },
        cover = photo?.src ?: thumbs?.maxByOrNull { it.width * it.height }?.src,
        collectionId = fullId,
        album = title,
        isExplicit = is_explicit == true,
        source = "vk",
        isAlbum = true,
    )

    private fun VkArtistDto.toSearchItem() = SearchItem(
        id = id,
        title = name,
        artist = name,
        artistName = name,
        artistId = id,
        cover = coverUrl(),
        source = "vk",
        isArtist = true,
    )

    private fun AudioTrack.toHomeItem() = HomeItem(
        id = fullId,
        title = title,
        artist = artist,
        artistName = artist,
        artistId = main_artists?.firstOrNull()?.id,
        cover = coverUrl(),
        collectionId = album?.id?.toString(),
        album = album?.title,
        isExplicit = is_explicit,
        duration = duration.toLong(),
        source = "vk",
        trackId = fullId,
    )

    private fun AudioPlaylist.toHomeItem() = HomeItem(
        id = fullId,
        title = title,
        artist = main_artists?.joinToString(", ") { it.name },
        artistId = main_artists?.firstOrNull()?.id,
        cover = photo?.src ?: thumbs?.firstOrNull()?.src,
        collectionId = fullId,
        isExplicit = is_explicit == true,
        source = "vk",
        subtitle = subtitle,
        isAlbum = true,
    )

    private fun VkArtistDto.toHomeItem() = HomeItem(
        id = id,
        title = name,
        artist = name,
        artistName = name,
        artistId = id,
        cover = coverUrl(),
        source = "vk",
        isArtist = true,
    )

    private fun AudioTrack.toTrackMeta() = TrackMeta(
        id = fullId,
        collectionId = album?.id?.toString(),
        title = title,
        artist = artist,
        cover = coverUrl().orEmpty(),
        duration = duration.toLong(),
        genre = track_genre_id?.toString() ?: genre_id?.toString(),
    )

    private fun AudioTrack.toAlbumTrack() = AlbumTrack(
        id = fullId,
        title = title,
        artist = artist,
        artistId = main_artists?.firstOrNull()?.id,
        cover = coverUrl().orEmpty(),
        collectionId = album?.id?.toString(),
        isExplicit = is_explicit,
        duration = duration.toLong(),
        source = "vk",
    )

    private fun AudioTrack.toArtistSong() = ArtistSong(
        id = fullId,
        title = title,
        artist = artist,
        artistId = main_artists?.firstOrNull()?.id,
        artists = toMiniArtists(),
        cover = coverUrl().orEmpty(),
        albumName = album?.title,
        isExplicit = is_explicit,
        source = "vk",
        duration = duration.toLong(),
    )

    private fun AudioTrack.toWaveTrack() = WaveTrack(
        id = fullId,
        title = title,
        artist = artist,
        artistId = main_artists?.firstOrNull()?.id,
        cover = coverUrl(),
        duration = duration.toLong(),
        collectionId = album?.id?.toString(),
        isExplicit = is_explicit,
        source = "vk",
    )

    private fun AudioTrack.toEngineTrack() = Track(
        id = fullId,
        title = title,
        artist = artist,
        albumName = album?.title.orEmpty(),
        uri = Uri.parse("https://byicloud.online/track/$fullId"),
        durationMs = duration * 1000L,
        albumId = album?.id?.toLong() ?: fullId.hashCode().toLong(),
        coverUrl = coverUrl(),
        artists = toMiniArtists(),
        isExplicit = is_explicit,
        source = "vk",
        genre = track_genre_id?.toString() ?: genre_id?.toString(),
    )

    private fun AudioTrack.toLibraryTrack() = LibraryTrack(
        id = fullId,
        trackId = fullId,
        title = title,
        artist = artist,
        artistId = main_artists?.firstOrNull()?.id,
        cover = coverUrl(),
        duration = duration.toLong(),
        collectionId = album?.id?.toString(),
        isExplicit = is_explicit,
        source = "vk",
        likedAt = date.takeIf { it > 0 },
    )

    private fun AudioTrack.toPlaylistTrack() = PlaylistTrack(
        id = fullId,
        title = title,
        artist = artist,
        artistId = main_artists?.firstOrNull()?.id.orEmpty(),
        cover = coverUrl().orEmpty(),
        collectionId = album?.id?.toString().orEmpty(),
        duration = duration.toLong(),
        isExplicit = is_explicit,
    )

    private fun AudioPlaylist.toUserPlaylist() = UserPlaylist(
        idRaw = JsonPrimitive(fullId),
        name = title,
        source = "vk",
        trackCount = count,
        cover = photo?.src ?: thumbs?.firstOrNull()?.src,
        createdAt = create_time,
        updatedAt = update_time,
    )

    private fun AudioPlaylist.toAlbum() = Album(
        id = fullId,
        title = title,
        artist = main_artists?.joinToString(", ") { it.name }.orEmpty(),
        artistId = main_artists?.firstOrNull()?.id,
        cover = photo?.src ?: thumbs?.firstOrNull()?.src.orEmpty(),
        year = year.takeIf { it > 0 }?.toString(),
        type = type,
        description = description,
        trackCount = count,
    )

    private fun AudioPlaylist.toArtistAlbum() = ArtistAlbum(
        id = fullId,
        title = title,
        artist = main_artists?.joinToString(", ") { it.name }.orEmpty(),
        artists = main_artists.orEmpty().map { MiniArtist(it.id, it.name) },
        year = year.takeIf { it > 0 }?.toString(),
        cover = photo?.src ?: thumbs?.firstOrNull()?.src.orEmpty(),
        type = type,
        isAlbum = true,
    )

    private const val STREAM_CACHE_TTL_MS = 8L * 60L * 1000L
}

/** Ответ треков плейлиста пользователя. */
data class PlaylistTracksResponse(
    val playlist: Album? = null,
    val tracks: List<PlaylistTrack> = emptyList()
)

/** Авторизация/подписка (бывш. IcmAuthRepository). */
object MusicAuth {
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn
    private val _isPremium = MutableStateFlow(true) // TODO(vk): статус подписки
    val isPremium: StateFlow<Boolean> = _isPremium
    private val _maxQuality = MutableStateFlow<String?>("lossless")
    val maxQuality: StateFlow<String?> = _maxQuality
    private val _profileName = MutableStateFlow<String?>(null)
    val profileName: StateFlow<String?> = _profileName
    private val _avatarUrl = MutableStateFlow<String?>(null)
    val avatarUrl: StateFlow<String?> = _avatarUrl
    private val _userEmail = MutableStateFlow<String?>(null)
    val userEmail: StateFlow<String?> = _userEmail
    private val _premiumExpiresAt = MutableStateFlow<Long?>(null)
    val premiumExpiresAt: StateFlow<Long?> = _premiumExpiresAt
    private val _subscription = MutableStateFlow<SubscriptionInfo?>(null)
    val subscription: StateFlow<SubscriptionInfo?> = _subscription
    private val _partnerUserId = MutableStateFlow<Long?>(null)
    val partnerUserId: StateFlow<Long?> = _partnerUserId
    private val _telegramId = MutableStateFlow<Long?>(null)
    val telegramId: StateFlow<Long?> = _telegramId

    private var sessionStore: VkSessionStore? = null

    internal fun init(store: VkSessionStore) {
        sessionStore = store
        applySession(store.session)
    }

    /** Точка передачи аккаунта из восстанавливаемого VK auth-флоу. */
    fun installSession(session: com.lmg.vk.network.VkAuthSession) {
        val store = checkNotNull(sessionStore) { "MusicAuth is not initialized" }
        store.session = session
        applySession(session)
    }

    private fun applySession(session: com.lmg.vk.network.VkAuthSession) {
        _isLoggedIn.value = session.accessToken.isNotBlank()
        _partnerUserId.value = session.userId.takeIf { it != 0L }
        val displayName = listOf(session.firstName, session.lastName)
            .filter(String::isNotBlank)
            .joinToString(" ")
            .ifBlank { session.username }
        _profileName.value = displayName.takeIf(String::isNotBlank)
        _avatarUrl.value = session.avatar.takeIf(String::isNotBlank)
    }

    suspend fun fetchUserData() { /* TODO(vk-wire: users.get) */ }
    fun getEffectiveQuality(requested: String, premium: Boolean): String = if (premium) requested else "128K"
    fun reissueSessionToken() { /* TODO(vk-wire) */ }
    fun logout() {
        sessionStore?.session = com.lmg.vk.network.VkAuthSession.EMPTY
        _partnerUserId.value = null
        _isLoggedIn.value = false
    }
}

/** Оффлайн-очередь сигналов прослушивания (бывш. WaveSignalQueue). */
object WaveSignalQueue {
    fun init(context: android.content.Context) { /* TODO(vk): prefs-очередь */ }
    fun sendPlayback(
        trackId: String,
        playedSeconds: Double = 0.0,
        totalSeconds: Double = 0.0,
        completed: Boolean = false,
        skipped: Boolean = false,
    ) { /* TODO(vk): stats.trackEvents */ }
    fun sendFeedback(trackId: String, kind: String) { /* TODO(vk) */ }
    /** Дослать недоставленные сигналы (вызывается при старте/смене сети). */
    fun drain() { /* TODO(vk) */ }
}

/** Мета подписки (для MusicAuth.subscription). */
data class SubscriptionInfo(
    val active: Boolean,
    val expiresAt: Long?,
    val planType: String = "",
    val isFamilyOwner: Boolean = false,
    val expiresAtIso: String = "",
    val daysLeft: Long = 0L,
    val regions: List<AvailableRegion> = emptyList(),
)
