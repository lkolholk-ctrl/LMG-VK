package com.lmg.vk.engine.backend

import com.lmg.vk.engine.Track
import com.lmg.vk.engine.LyricsParser
import com.lmg.vk.engine.backend.wave.WaveBatchResponse
import com.lmg.vk.engine.backend.wave.WaveSessionStartResponse
import com.lmg.vk.network.VkApiClient
import com.lmg.vk.network.VkAuthSession
import com.lmg.vk.network.VkResult
import com.lmg.vk.network.VkSessionStore
import com.lmg.vk.network.getOrNull
import com.lmg.vk.network.dto.AuthFlowName
import com.lmg.vk.network.dto.AuthVerificationMethod
import com.lmg.vk.network.dto.RequestTokenResponse
import com.lmg.vk.network.dto.music.AudioPlaylist
import com.lmg.vk.network.dto.music.AudioAlbum
import com.lmg.vk.network.dto.music.AlbumThumb
import com.lmg.vk.network.dto.music.AudioPlaylistDto
import com.lmg.vk.network.dto.music.AudioAudioDto
import com.lmg.vk.network.dto.music.AudioArtistDto
import com.lmg.vk.network.dto.music.AudioSearchMainResponse
import com.lmg.vk.network.dto.music.AudioStreamMix
import com.lmg.vk.network.dto.music.AudioTrack
import com.lmg.vk.network.dto.music.MainArtist
import com.lmg.vk.network.dto.music.VkArtistDto
import com.lmg.vk.network.dto.music.VkCatalogBlock
import com.lmg.vk.network.dto.music.VkCatalogResponse
import com.lmg.vk.network.methods.VkAudioApi
import com.lmg.vk.network.methods.VkCatalogApi
import com.lmg.vk.network.methods.VkMethodsRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonPrimitive
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
    0 -> "Нет подключения к интернету или сеть недоступна"
    401, 1117 -> "Сессия VK истекла. Пожалуйста, выполните повторный вход"
    15, 403 -> "Доступ к аудио ограничен или запрещён VK"
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
        MusicAuth.init(client, sessions)
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
        if (!track.isAvailable) throw backendFailure(451, "Аудиозапись недоступна")
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
        ).requireData().also(::cacheTracks).map { it.toSearchItem() }

    suspend fun searchAll(query: String, region: String? = null, source: String = SearchSource.ALL, limit: Int = 30): SearchResponse? =
        runCatching {
            requireInitialized()
            val requestedCount = limit.coerceIn(1, 300)
            val (mainResult, audioResult, artistResult) = coroutineScope {
                val main = async { audioApi.searchMain(query, offset = 0, count = requestedCount) }
                val audios = async {
                    audioApi.searchAudios(query, currentUserId(), offset = 0, count = requestedCount)
                }
                val artists = async {
                    audioApi.searchArtists(query, offset = 0, count = requestedCount.coerceAtMost(100))
                }
                Triple(main.await(), audios.await(), artists.await())
            }

            val main: AudioSearchMainResponse? = mainResult.getOrNull()
            val directTracks: List<AudioTrack> = audioResult.getOrNull().orEmpty()
            val mainTracks = main?.let { response ->
                (response.audios.items + response.own_audios.items).map { it.toAudioTrack() }
            }.orEmpty()
            val tracks = (directTracks + mainTracks)
                .distinctBy(AudioTrack::fullId)
                .also(::cacheTracks)

            val directArtists: List<VkArtistDto> = artistResult.getOrNull()?.items.orEmpty()
            // searchArtists возвращает VkArtistDto, а searchMain — другой wire DTO,
            // AudioArtistDto. Объединяем только после нормализации в SearchItem,
            // иначе Kotlin выводит List<Any> и теряет id/domain/name.
            val artists: List<SearchItem> = buildList {
                addAll(directArtists.map { it.toSearchItem() })
                addAll(main?.artists?.items.orEmpty().map { it.toSearchItem() })
            }.distinctBy {
                it.artistId?.takeIf(String::isNotBlank)
                    ?: it.id.takeIf(String::isNotBlank)
                    ?: it.title.lowercase()
            }

            val mainAlbums = main?.let { response ->
                response.albums.items + response.own_albums.items
            }.orEmpty()
            val fallbackAlbums: List<AudioPlaylist> = if (mainAlbums.isEmpty()) {
                audioApi.searchPlaylists(
                    query = query,
                    ownerId = currentUserId(),
                    offset = 0,
                    count = requestedCount.coerceAtMost(100),
                ).getOrNull().orEmpty().filter { it.isAlbumRelease() }
            } else {
                emptyList()
            }

            if (main == null && directTracks.isEmpty() && directArtists.isEmpty() && fallbackAlbums.isEmpty()) {
                val failure = sequenceOf(mainResult, audioResult, artistResult)
                    .filterIsInstance<VkResult.Error>()
                    .firstOrNull()
                throw backendFailure(failure?.code ?: 0, failure?.message ?: "VK search failed")
            }

            SearchResponse(
                query = query,
                region = this.region,
                source = "vk",
                items = buildList {
                    addAll(tracks.map { it.toSearchItem() })
                    addAll(artists)
                    addAll(mainAlbums.distinctBy(AudioPlaylistDto::fullId).map { it.toSearchItem() })
                    addAll(fallbackAlbums.distinctBy(AudioPlaylist::fullId).map { it.toSearchItem() })
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
            val releases = catalog.playlists.orEmpty().map { it.toHomeItem() }
            if (releases.isNotEmpty()) {
                add(HomeBlock("vk_new_releases", "Новые релизы", "new_releases", releases))
            }
            if (popularTracks.isNotEmpty()) {
                add(HomeBlock("vk_charts", "Популярное", "charts", popularTracks.map { it.toHomeItem() }))
            }
            if (catalogTracks.isNotEmpty()) {
                add(HomeBlock("vk_recommendations", "Рекомендации", "recommendations", catalogTracks.map { it.toHomeItem() }))
            }
            val artists = catalog.artists.orEmpty().map { it.toHomeItem() }
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
                cover = tracks.firstNotNullOfOrNull { it.coverUrl() },
                tracks = tracks.map { it.toSearchItem() },
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
        AlbumResponse(playlist.toAlbum(), tracks.map { it.toAlbumTrack() })
    }.getOrNull()

    suspend fun followAlbum(albumId: String): Boolean = runCatching {
        requireInitialized()
        val (ownerId, id) = parsePlaylistId(albumId)
        val playlist = audioApi.getPlaylistById(ownerId, id).requireData()
        audioApi.followPlaylist(id, ownerId, playlist.access_key).requireData()
        true
    }.getOrDefault(false)

    suspend fun followPlaylist(playlistId: String): Boolean = followAlbum(playlistId)

    suspend fun getArtist(artistId: String): ArtistResponse? = runCatching {
        requireInitialized()
        val normalizedId = artistId.removePrefix("vk_")
        val catalog = catalogApi.getAudioArtist(normalizedId).requireData()
        suspend fun loadSectionPages(sectionId: String): List<VkCatalogResponse> {
            val pages = mutableListOf<VkCatalogResponse>()
            val seenOffsets = HashSet<String>()
            var startFrom: String? = null
            while (pages.size < 50) {
                val page = catalogApi.getSection(sectionId, startFrom).getOrNull() ?: break
                pages += page
                val next = page.section?.next_from?.takeIf(String::isNotBlank) ?: break
                if (!seenOffsets.add(next)) break
                startFrom = next
            }
            return pages
        }

        val sectionCatalogs = coroutineScope {
            catalog.catalog?.sections.orEmpty()
                .map { it.id }
                .filter(String::isNotBlank)
                .distinct()
                .map { sectionId -> async { loadSectionPages(sectionId) } }
                .flatMap { it.await() }
        }

        suspend fun loadBlockPages(block: VkCatalogBlock): List<VkCatalogResponse> {
            val pages = mutableListOf<VkCatalogResponse>()
            val seenOffsets = HashSet<String>()
            var startFrom = block.next_from?.takeIf(String::isNotBlank)
            while (startFrom != null && pages.size < 50) {
                if (!seenOffsets.add(startFrom)) break
                val page = catalogApi.getBlockItems(block.id, startFrom).getOrNull() ?: break
                pages += page
                startFrom = (
                    page.block?.next_from
                        ?: page.allBlocks().firstOrNull { it.id == block.id }?.next_from
                    )?.takeIf(String::isNotBlank)
            }
            return pages
        }

        val sectionPages = listOf(catalog) + sectionCatalogs
        val blockCatalogs = coroutineScope {
            sectionPages
                .flatMap { it.allBlocks() }
                .filter { it.id.isNotBlank() && !it.next_from.isNullOrBlank() }
                .distinctBy { it.id }
                .map { block -> async { loadBlockPages(block) } }
                .flatMap { it.await() }
        }
        val catalogPages = sectionPages + blockCatalogs
        val tracks = catalogPages.flatMap { it.audios.orEmpty() }.distinctBy(AudioTrack::fullId).ifEmpty {
            audioApi.getAudiosByArtist(normalizedId).requireData()
        }.also(::cacheTracks)
        val catalogArtists = catalogPages.flatMap { it.artists.orEmpty() }
        val artist = catalogArtists.firstOrNull { it.id == normalizedId }
            ?: catalogArtists.firstOrNull()
            ?: VkArtistDto(
                id = normalizedId,
                name = tracks.firstOrNull()?.main_artists?.firstOrNull()?.name
                    ?: tracks.firstOrNull()?.artist.orEmpty(),
            )
        val (related, searchedAlbums, legacyAlbums) = coroutineScope {
            val relatedRequest = async {
                audioApi.getRelatedArtistsById(normalizedId, count = 100)
            }
            val mainRequest = async { audioApi.searchMain(artist.name, count = 300) }
            val playlistRequest = async {
                audioApi.searchPlaylists(
                    query = artist.name,
                    ownerId = currentUserId(),
                    offset = 0,
                    count = 100,
                )
            }
            val relatedResult = relatedRequest.await()
            val mainResult = mainRequest.await()
            val playlistResult = playlistRequest.await()
            val relatedArtists: List<VkArtistDto> =
                relatedResult.getOrNull()?.artists.orEmpty()
            val mainAlbums: List<AudioPlaylistDto> = mainResult.getOrNull()?.let {
                it.albums.items + it.own_albums.items
            }.orEmpty()
            val foundPlaylists: List<AudioPlaylist> = playlistResult.getOrNull().orEmpty()
            Triple(
                relatedArtists,
                mainAlbums,
                foundPlaylists,
            )
        }
        val catalogBlocks = catalogPages.flatMap { it.allBlocks() }
        val appearsOnIds = catalogBlocks
            .filter { it.matchesSection("appears", "particip", "участ") }
            .flatMap { it.playlists_ids.orEmpty() }
            .map(::normalizeTrackId)
            .toSet()
        val artistPlaylistIds = catalogBlocks
            .filter { it.matchesSection("playlist", "плейлист") }
            .flatMap { it.playlists_ids.orEmpty() }
            .map(::normalizeTrackId)
            .toSet()
        val linkedArtistIds = catalogBlocks
            .filter { it.matchesSection("link", "связ", "artist") }
            .flatMap { it.artists_ids.orEmpty() }
            .map(::normalizeTrackId)
            .toSet()

        val albumCandidates = buildList {
            addAll(catalogPages.flatMap { it.playlists.orEmpty() }
                .filter { it.isAlbumRelease() }
                .map { it.toArtistAlbum() })
            addAll(searchedAlbums
                .filter { it.belongsToArtist(normalizedId, artist.name) }
                .map { it.toArtistAlbum() })
            addAll(legacyAlbums
                .filter { it.isAlbumRelease() && it.belongsToArtist(normalizedId, artist.name) }
                .map { it.toArtistAlbum() })
        }.distinctBy(ArtistAlbum::id)
        val appearsOn = albumCandidates.filter { normalizeTrackId(it.id) in appearsOnIds }
        val albums = albumCandidates.filterNot { normalizeTrackId(it.id) in appearsOnIds }

        val artistPlaylists = catalogPages.flatMap { it.playlists.orEmpty() }
            .filterNot { it.isAlbumRelease() }
            .filter { artistPlaylistIds.isEmpty() || normalizeTrackId(it.fullId) in artistPlaylistIds }
            .distinctBy(AudioPlaylist::fullId)

        val catalogProfiles = catalogPages.flatMap { it.profiles.orEmpty() }
        val catalogGroups = catalogPages.flatMap { it.groups.orEmpty() }
        val musicOwners = catalogPages.flatMap { it.music_owners.orEmpty() }
        val knownArtists = (catalogArtists + related)
            .filter { it.id.isNotBlank() && it.name.isNotBlank() }
            .distinctBy { it.id }
        val ownerArtistMatches = musicOwners.mapNotNull { owner ->
            val match = knownArtists.firstOrNull {
                it.name.trim().equals(owner.displayName.trim(), ignoreCase = true)
            } ?: return@mapNotNull null
            owner.id to SimilarArtist(
                id = match.id,
                name = owner.displayName,
                url = match.domain,
                cover = owner.photo_base ?: match.coverUrl(),
            )
        }
        val linkedOwnerIds = ownerArtistMatches.map { it.first }.toSet()
        val groupIds = catalogGroups.flatMap { listOf(it.id, -it.id) }.toSet()

        val officialProfiles = (catalogProfiles + musicOwners.filter {
            it.id !in linkedOwnerIds && it.id !in groupIds && it.id >= 0L
        })
            .mapNotNull {
                if (it.id == 0L || it.displayName.isBlank()) return@mapNotNull null
                ArtistOfficialPage(
                    id = it.id,
                    name = it.displayName,
                    cover = it.photo_base,
                    subtitle = "Official profile",
                    isFollowed = it.is_followed == true,
                    isCommunity = false,
                )
            }
        val communities = (catalogGroups + musicOwners.filter {
            it.id !in linkedOwnerIds && (it.id < 0L || it.id in groupIds)
        })
            .mapNotNull {
                if (it.id == 0L || it.displayName.isBlank()) return@mapNotNull null
                ArtistOfficialPage(
                    id = it.id,
                    name = it.displayName,
                    cover = it.photo_base,
                    subtitle = "Community",
                    isFollowed = it.is_followed == true,
                    isCommunity = true,
                )
            }
        val officialPages = (officialProfiles + communities).distinctBy { it.id }
        val blockLinkedArtists = catalogArtists
            .filter { it.id != normalizedId && it.id.isNotBlank() && it.name.isNotBlank() }
            .filter { linkedArtistIds.isEmpty() || normalizeTrackId(it.id) in linkedArtistIds }
            .distinctBy { it.id }
            .map {
                SimilarArtist(id = it.id, name = it.name, url = it.domain, cover = it.coverUrl())
            }
        val linkedArtists = (ownerArtistMatches.map { it.second } + blockLinkedArtists)
            .filter { it.id != normalizedId }
            .distinctBy { it.id }
        // Часть link-блоков VK приходит без собственной картинки, хотя тот же
        // исполнитель уже есть среди artist/music_owner сущностей страницы.
        // Подставляем только подтверждённую обложку совпавшего id/domain/name.
        fun artistKey(value: String?): String =
            value.orEmpty().trim().lowercase()

        val artistCovers = mutableMapOf<String, String>()
        fun rememberArtistCover(key: String?, cover: String?) {
            val normalized = artistKey(key)
            val validCover = cover?.takeIf(String::isNotBlank)
            if (normalized.isNotEmpty() && validCover != null && normalized !in artistCovers) {
                artistCovers[normalized] = validCover
            }
        }
        knownArtists.forEach { candidate ->
            val cover = candidate.coverUrl()
            rememberArtistCover(candidate.id, cover)
            rememberArtistCover(candidate.domain, cover)
            rememberArtistCover(candidate.name, cover)
        }
        musicOwners.forEach { owner ->
            rememberArtistCover(owner.id.toString(), owner.photo_base)
            rememberArtistCover(owner.displayName, owner.photo_base)
        }

        val links = catalogPages.flatMap { it.links.orEmpty() }
            .filter { it.id.isNotBlank() && it.url.isNotBlank() }
            .distinctBy { it.id }
            .map {
                val urlKey = it.url
                    .substringAfterLast('/')
                    .substringBefore('?')
                    .substringBefore('#')
                ArtistLink(
                    id = it.id,
                    title = it.title,
                    subtitle = it.subtitle.takeIf(String::isNotBlank),
                    url = it.url,
                    cover = it.coverUrl()?.takeIf(String::isNotBlank)
                        ?: artistCovers[artistKey(it.id)]
                        ?: artistCovers[artistKey(urlKey)]
                        ?: artistCovers[artistKey(it.title)],
                )
            }
        val videos = catalogPages.flatMap { it.artist_videos.orEmpty() + it.videos.orEmpty() }
            .filter { it.id != 0 }
            .distinctBy { it.fullId }
            .map {
                ArtistVideo(
                    id = it.fullId,
                    title = it.title,
                    cover = it.coverUrl(),
                    duration = it.duration.toLong(),
                    url = it.direct_url,
                )
            }
        ArtistResponse(
            id = artist.id,
            name = artist.name,
            genre = artist.genres.orEmpty().joinToString(", ") { it.name }.takeIf(String::isNotBlank),
            url = artist.domain?.let { "https://vk.com/artist/$it" },
            image = artist.coverUrl(),
            cover = artist.coverUrl(),
            bio = artist.bio,
            isFollowed = artist.is_followed == true,
            canFollow = artist.can_follow == true,
            mixId = catalogPages.firstNotNullOfOrNull {
                it.audio_stream_mixes.orEmpty().firstOrNull()?.id
            },
            topSongs = tracks.map { it.toArtistSong() },
            latestRelease = albums.maxWithOrNull(
                compareBy<ArtistAlbum> { it.timestamp ?: 0L }.thenBy { it.year.orEmpty() },
            ),
            albums = albums.filterNot { it.isSingleOrEp() },
            singles = albums.filter { it.isSingleOrEp() },
            featuring = appearsOn,
            similarArtists = related.map {
                SimilarArtist(id = it.id, name = it.name, url = it.domain, cover = it.coverUrl())
            },
            playlists = artistPlaylists.map {
                ArtistPlaylist(
                    id = it.fullId,
                    title = it.title,
                    cover = it.photo?.bestUrl ?: it.photo?.src
                        ?: it.thumbs?.firstOrNull()?.bestUrl,
                )
            },
            appearsOn = appearsOn,
            officialPages = officialPages,
            linkedArtists = linkedArtists,
            links = links,
            videos = videos,
            source = "vk",
        )
    }.getOrNull()

    suspend fun setArtistFollowed(artistId: String, followed: Boolean): Boolean = runCatching {
        val normalizedId = artistId.removePrefix("vk_")
        val userId = currentUserId()
        val result = if (followed) {
            audioApi.followArtist(userId, normalizedId)
        } else {
            audioApi.unfollowArtist(userId, normalizedId)
        }
        result.requireData()
        true
    }.getOrDefault(false)

    suspend fun getArtistMix(artistId: String, mixId: String?): List<Track> = runCatching {
        val normalizedId = artistId.removePrefix("vk_")
        val resolvedMixId = mixId ?: catalogApi.getAudioArtist(normalizedId)
            .requireData()
            .audio_stream_mixes.orEmpty()
            .firstOrNull()
            ?.id
            ?: return@runCatching emptyList()
        audioApi.getStreamMixAudios(
            mixId = resolvedMixId,
            entityId = normalizedId,
            append = false,
        ).requireData().also(::cacheTracks).map { it.toEngineTrack() }
    }.getOrDefault(emptyList())

    suspend fun getArtistTopTracks(artistId: String): List<Track> =
        audioApi.getAudiosByArtist(artistId.removePrefix("vk_")).requireData()
            .also(::cacheTracks)
            .map { it.toEngineTrack() }

    /**
     * Все аудио исполнителя. `topSongs` на странице артиста остаётся быстрым
     * превью, а этот метод постранично собирает полный список для See all и
     * реального счётчика.
     */
    suspend fun getArtistAllTracks(artistId: String): List<Track> = runCatching {
        requireInitialized()
        val normalizedId = artistId.removePrefix("vk_")
        val tracks = mutableListOf<AudioTrack>()
        val seen = HashSet<String>()
        var offset = 0
        val pageSize = 100

        while (offset < 6_000) {
            val page = audioApi.getAudiosByArtist(
                artistId = normalizedId,
                type = null,
                offset = offset,
                count = pageSize,
            ).requireData()
            val fresh = page.filter { seen.add(it.fullId) }
            tracks.addAll(fresh)
            if (page.size < pageSize || fresh.isEmpty()) break
            offset += page.size
        }

        tracks.also(::cacheTracks).map { it.toEngineTrack() }
    }.getOrDefault(emptyList())

    /** Полная дискография исполнителя, а не только релизы из первых catalog-блоков. */
    suspend fun getArtistReleases(artistId: String): List<ArtistAlbum> = runCatching {
        requireInitialized()
        val normalizedId = artistId.removePrefix("vk_")
        val releases = mutableListOf<AudioPlaylist>()
        val seen = HashSet<String>()
        var offset = 0
        val pageSize = 100

        while (offset < 2_000) {
            val page = audioApi.getAlbumsByArtist(
                artistId = normalizedId,
                offset = offset,
                count = pageSize,
            ).requireData()
            val fresh = page.filter { seen.add(it.fullId) }
            releases.addAll(fresh)
            if (page.size < pageSize || fresh.isEmpty()) break
            offset += page.size
        }

        releases.map { it.toArtistAlbum() }
    }.getOrDefault(emptyList())

    // ---------- лайки / библиотека ----------
    suspend fun getLibraryLikes(source: String = "all", limit: Int = 500, offset: Int = 0): LibraryLikesResponse? =
        runCatching {
            val tracks = audioApi.getAudios(
                ownerId = currentUserId(),
                offset = offset,
                count = limit.coerceIn(1, 6000),
            ).requireData().also(::cacheTracks)
            LibraryLikesResponse(
                items = tracks.map { it.toLibraryTrack() },
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
        val requested = limit.coerceIn(1, 1000)
        val playlists = buildList<AudioPlaylist> {
            var offset = 0
            while (size < requested) {
                val pageSize = minOf(100, requested - size)
                val page = audioApi.getPlaylists(
                    ownerId = ownerId,
                    offset = offset,
                    count = pageSize,
                ).requireData()
                addAll(page)
                if (page.size < pageSize) break
                offset += page.size
            }
        }.distinctBy { it.fullId }
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
                tracks = tracks.map { it.toPlaylistTrack() },
            )
        }.getOrNull()
    suspend fun deleteUserPlaylist(playlistId: String): Boolean = runCatching {
        val (ownerId, id) = parsePlaylistId(playlistId)
        audioApi.deletePlaylist(ownerId, id).requireData()
        true
    }.getOrDefault(false)

    /** Создать плейлист в текущем VK-аккаунте и вернуть полный id owner_playlist. */
    suspend fun createUserPlaylist(name: String, trackIds: List<String>): String? = runCatching {
        val ownerId = currentUserId()
        val created = audioApi.createPlaylist(ownerId, name.trim()).requireData()
        val playlistId = created.id
        require(playlistId != 0) { "VK returned an empty playlist id" }
        val resolvedOwnerId = created.owner_id.takeIf { it != 0L } ?: ownerId
        val audioIds = trackIds.map(::normalizeTrackId).filter { it.isVkAudioFullId() }
        if (audioIds.isNotEmpty()) {
            audioApi.editPlaylist(resolvedOwnerId, playlistId, name.trim(), audioIds).requireData()
        }
        "${resolvedOwnerId}_$playlistId"
    }.getOrNull()

    /** Полностью применить локальное имя и порядок треков к связанному VK-плейлисту. */
    suspend fun updateUserPlaylist(
        playlistId: String,
        name: String,
        trackIds: List<String>,
    ): Boolean = runCatching {
        val (ownerId, id) = parsePlaylistId(playlistId)
        val audioIds = trackIds.map(::normalizeTrackId).filter { it.isVkAudioFullId() }
        audioApi.editPlaylist(ownerId, id, name.trim(), audioIds).requireData()
        true
    }.getOrDefault(false)

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
            tracks = tracks.map { it.toWaveTrack() },
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
            tracks = filtered.map { it.toWaveTrack() },
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
            tracks = tracks.map { it.toWaveTrack() },
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
    suspend fun resetWave(): Boolean {
        waveMutex.withLock { resetWaveLocked() }
        return true
    }

    fun isVkAudioId(id: String): Boolean = com.lmg.vk.engine.VkAudioIdentity.isFullId(id)

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

    private fun String.isVkAudioFullId(): Boolean {
        val parts = split('_')
        return parts.size >= 2 && parts[0].toLongOrNull() != null && parts[1].toLongOrNull() != null
    }

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

    private const val VK_OFFICIAL_DEFAULT_COVER_URL = "https://vk.com/images/audio_row_placeholder.png"

    private fun AudioTrack.coverUrl(): String? =
        album?.thumb?.bestUrl?.takeIf(String::isNotBlank)
            ?: album?.thumb?.src?.takeIf(String::isNotBlank)
            ?: main_artists.orEmpty().firstNotNullOfOrNull { artist ->
                artist.photo.orEmpty().firstOrNull { it.bestUrl.isNotBlank() }?.bestUrl
            }
            ?: VK_OFFICIAL_DEFAULT_COVER_URL

    private fun AudioTrack.toMiniArtists(): List<MiniArtist> =
        main_artists.orEmpty().map { MiniArtist(id = it.id, name = it.name) }

    private fun AudioTrack.resolvedArtist(): String =
        artist.ifBlank { main_artists.orEmpty().joinToString(", ") { it.name } }

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
        artist = resolvedArtist(),
        artistName = resolvedArtist(),
        artistId = main_artists?.firstOrNull()?.id,
        artists = toMiniArtists(),
        cover = coverUrl(),
        collectionId = album?.id?.toString(),
        album = album?.title,
        isExplicit = is_explicit,
        duration = duration.toLong(),
        source = "vk",
        trackId = fullId,
        isAvailable = isAvailable,
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
        content_restricted = content_restricted ?: 0,
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
            cover = sequence {
                yieldAll(photo.orEmpty())
                photos.orEmpty().forEach { yieldAll(it.photo) }
            }.filter { it.url.isNotBlank() }
                .maxByOrNull { it.width * it.height }
                ?.url,
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
        cover = photo?.bestUrl ?: thumbs?.maxByOrNull { it.width * it.height }?.bestUrl,
        collectionId = fullId,
        album = title,
        isExplicit = is_explicit == true,
        source = "vk",
        isAlbum = true,
    )

    private fun AudioPlaylist.isAlbumRelease(): Boolean =
        type.orEmpty().contains("album", ignoreCase = true) ||
            album != null ||
            meta?.view.orEmpty().contains("album", ignoreCase = true)

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
        isAvailable = isAvailable,
    )

    private fun AudioTrack.toArtistSong() = ArtistSong(
        id = fullId,
        title = title,
        artist = resolvedArtist(),
        artistId = main_artists?.firstOrNull()?.id,
        artists = toMiniArtists(),
        cover = coverUrl().orEmpty(),
        albumName = album?.title,
        isExplicit = is_explicit,
        source = "vk",
        duration = duration.toLong(),
        isAvailable = isAvailable,
    )

    private fun AudioTrack.toWaveTrack() = WaveTrack(
        id = fullId,
        title = title,
        artist = resolvedArtist(),
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
        artist = resolvedArtist(),
        albumName = album?.title.orEmpty(),
        uri = com.lmg.vk.engine.VkAudioIdentity.playbackUri(url),
        durationMs = duration * 1000L,
        albumId = album?.id?.toLong() ?: fullId.hashCode().toLong(),
        coverUrl = coverUrl(),
        artists = toMiniArtists(),
        isExplicit = is_explicit,
        source = "vk",
        genre = track_genre_id?.toString() ?: genre_id?.toString(),
        isAvailable = isAvailable,
    )

    private fun AudioTrack.toLibraryTrack() = LibraryTrack(
        id = fullId,
        trackId = fullId,
        title = title,
        artist = resolvedArtist(),
        artistId = main_artists?.firstOrNull()?.id,
        cover = coverUrl(),
        duration = duration.toLong(),
        collectionId = album?.id?.toString(),
        isExplicit = is_explicit,
        source = "vk",
        likedAt = date.takeIf { it > 0 }?.times(1000L),
        isAvailable = isAvailable,
    )

    private fun AudioTrack.toPlaylistTrack() = PlaylistTrack(
        id = fullId,
        title = title,
        artist = resolvedArtist(),
        artistId = main_artists?.firstOrNull()?.id.orEmpty(),
        cover = coverUrl().orEmpty(),
        collectionId = album?.id?.toString().orEmpty(),
        duration = duration.toLong(),
        isExplicit = is_explicit,
        isAvailable = isAvailable,
    )

    private fun AudioPlaylist.toUserPlaylist() = UserPlaylist(
        idRaw = JsonPrimitive(fullId),
        name = title,
        source = "vk",
        trackCount = count,
        cover = photo?.bestUrl ?: photo?.src ?: thumbs?.firstOrNull()?.bestUrl ?: thumbs?.firstOrNull()?.src,
        createdAt = create_time,
        updatedAt = update_time,
    )

    private fun AudioPlaylist.toAlbum() = Album(
        id = fullId,
        title = title,
        artist = main_artists?.joinToString(", ") { it.name }
            ?.takeIf(String::isNotBlank) ?: subtitle.orEmpty(),
        artistId = main_artists?.firstOrNull()?.id,
        artists = main_artists.orEmpty().map { MiniArtist(id = it.id, name = it.name) },
        cover = photo?.bestUrl ?: photo?.src ?: thumbs?.firstOrNull()?.bestUrl ?: thumbs?.firstOrNull()?.src.orEmpty(),
        year = year.takeIf { it > 0 }?.toString() ?: create_time.takeIf { it > 0 }?.let { (it / 31536000 + 1970).toString() },
        type = type,
        genre = genres.orEmpty().joinToString(", ") { it.name }.takeIf(String::isNotBlank),
        description = description,
        trackCount = count,
        plays = plays,
        followers = followers,
        createdAt = create_time.takeIf { it > 0 },
        updatedAt = update_time?.takeIf { it > 0 },
        isFollowing = is_following == true,
        canFollow = permissions?.follow == true,
        isOwned = permissions?.edit == true || permissions?.delete == true,
    )

    private fun AudioPlaylist.toArtistAlbum() = ArtistAlbum(
        id = fullId,
        title = title,
        artist = main_artists?.joinToString(", ") { it.name }.orEmpty(),
        artists = main_artists.orEmpty().map { MiniArtist(it.id, it.name) },
        year = year.takeIf { it > 0 }?.toString() ?: create_time.takeIf { it > 0 }?.let { (it / 31536000 + 1970).toString() },
        cover = photo?.bestUrl ?: photo?.src ?: thumbs?.firstOrNull()?.bestUrl ?: thumbs?.firstOrNull()?.src.orEmpty(),
        type = type,
        isAlbum = true,
        timestamp = update_time?.takeIf { it > 0 } ?: create_time.takeIf { it > 0 },
    )

    private fun AudioPlaylistDto.toArtistAlbum() = ArtistAlbum(
        id = fullId,
        title = title,
        artist = main_artist ?: main_artists.orEmpty().joinToString(", ") { it.name },
        artists = main_artists.orEmpty().map { MiniArtist(it.id, it.name) },
        year = year?.takeIf { it > 0 }?.toString()
            ?: original_year?.takeIf { it > 0 }?.toString(),
        cover = coverUrl().orEmpty(),
        type = album_type ?: type,
        isAlbum = true,
        timestamp = update_time.takeIf { it > 0 }?.toLong()
            ?: create_time.takeIf { it > 0 }?.toLong(),
    )

    private fun AudioPlaylistDto.belongsToArtist(artistId: String, artistName: String): Boolean {
        val candidates = main_artists.orEmpty() + artists.orEmpty()
        return candidates.any {
            it.id == artistId || it.name.equals(artistName, ignoreCase = true)
        } || main_artist.equals(artistName, ignoreCase = true)
    }

    private fun AudioPlaylist.belongsToArtist(artistId: String, artistName: String): Boolean =
        main_artists.orEmpty().any {
            it.id == artistId || it.name.equals(artistName, ignoreCase = true)
        }

    private fun ArtistAlbum.isSingleOrEp(): Boolean {
        val releaseType = type.orEmpty()
        return releaseType.contains("single", ignoreCase = true) ||
            releaseType.equals("ep", ignoreCase = true) ||
            releaseType.contains("extended_play", ignoreCase = true)
    }

    private fun VkCatalogResponse.allBlocks(): List<VkCatalogBlock> = buildList {
        block?.let(::add)
        section?.blocks.orEmpty().let(::addAll)
        catalog?.sections.orEmpty().flatMap { it.blocks.orEmpty() }.let(::addAll)
    }.distinctBy { it.id }

    private fun VkCatalogBlock.matchesSection(vararg markers: String): Boolean {
        val haystack = listOf(id, data_type, layout?.name, layout?.title, layout?.subtitle)
            .joinToString(" ")
            .lowercase()
        return markers.any { marker -> marker.lowercase() in haystack }
    }

    private const val STREAM_CACHE_TTL_MS = 8L * 60L * 1000L
}

/** Ответ треков плейлиста пользователя. */
data class PlaylistTracksResponse(
    val playlist: Album? = null,
    val tracks: List<PlaylistTrack> = emptyList()
)

/** Результат прямого VK OAuth-входа, который UI переводит в следующий шаг. */
sealed interface VkLoginResult {
    data object Success : VkLoginResult

    data class TwoFactor(
        val validationSid: String,
        val destination: String,
        val codeLength: Int,
    ) : VkLoginResult

    data class Captcha(
        val captchaSid: String,
        val imageUrl: String,
    ) : VkLoginResult

    data class Failure(val message: String) : VkLoginResult
}

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
    private var apiClient: VkApiClient? = null
    private var methods: VkMethodsRegistry? = null
    private val anonymousTokenMutex = Mutex()
    private val signInMutex = Mutex()
    private var anonymousToken: String = ""
    private var anonymousTokenExpiresAt: Long = 0L
    private var activeAuthAttempt: AuthAttempt? = null

    private data class AuthAttempt(
        val username: String,
        val anonymousToken: String,
        var sid: String,
        var verificationMethod: AuthVerificationMethod = AuthVerificationMethod.PASSWORD,
        var grantType: String = "password",
        var canSkipPassword: Boolean = false,
        var captchaSid: String? = null,
        var processingPolls: Int = 0,
    )

    internal fun init(client: VkApiClient, store: VkSessionStore) {
        apiClient = client
        methods = VkMethodsRegistry(client)
        sessionStore = store
        applySession(store.session)
    }

    /** Полный VK ID-флоу: anonymous token → validateAccount → OTP → token. */
    suspend fun signIn(
        username: String,
        password: String,
        validationSid: String? = null,
        code: String? = null,
        captchaSid: String? = null,
        captchaKey: String? = null,
    ): VkLoginResult {
        if (username.isBlank() || password.isBlank()) {
            return VkLoginResult.Failure("Enter your phone or email and password")
        }

        val registry = methods ?: return VkLoginResult.Failure("VK API is not initialized")
        return signInMutex.withLock {
            val normalizedUsername = username.trim()
            val isCaptchaContinuation = !captchaKey.isNullOrBlank() && !captchaSid.isNullOrBlank()
            val isOtpContinuation = !isCaptchaContinuation &&
                !code.isNullOrBlank() &&
                !validationSid.isNullOrBlank()

            if (!isOtpContinuation && !isCaptchaContinuation) {
                activeAuthAttempt = null
                startAuthAttempt(registry, normalizedUsername)?.let { return@withLock it }
            }

            val attempt = activeAuthAttempt
                ?: return@withLock VkLoginResult.Failure("VK authorization session expired. Start again.")
            if (attempt.username != normalizedUsername) {
                activeAuthAttempt = null
                return@withLock VkLoginResult.Failure("The login changed. Start authorization again.")
            }

            if (isOtpContinuation) {
                if (validationSid != attempt.sid) {
                    return@withLock VkLoginResult.Failure("VK verification session changed. Start again.")
                }
                when (val checked = registry.ecosystemCheckOtp(
                    sid = attempt.sid,
                    code = requireNotNull(code),
                    verificationMethod = attempt.verificationMethod.wireName,
                    anonymousToken = attempt.anonymousToken,
                )) {
                    is VkResult.Error -> return@withLock checked.asLoginFailure("VK rejected the verification code")
                    is VkResult.Success -> {
                        val checkedSid = checked.data.sid
                        if (checkedSid.isBlank()) {
                            return@withLock VkLoginResult.Failure("VK returned an empty verified session")
                        }
                        attempt.sid = checkedSid
                        attempt.canSkipPassword = checked.data.canSkipPassword == true
                        attempt.grantType = if (attempt.canSkipPassword) {
                            "without_password"
                        } else {
                            "phone_confirmation_sid"
                        }
                    }
                }
            }

            val captchaParams = if (isCaptchaContinuation) {
                if (captchaSid != attempt.captchaSid) {
                    return@withLock VkLoginResult.Failure("VK captcha session changed. Start again.")
                }
                mapOf(
                    "captcha_sid" to requireNotNull(captchaSid),
                    "captcha_key" to requireNotNull(captchaKey),
                )
            } else {
                emptyMap()
            }

            requestOAuthToken(registry, attempt, password, captchaParams)
        }
    }

    /** Создаёт новую попытку; один token/sid используется до конца OTP/captcha. */
    private suspend fun startAuthAttempt(
        registry: VkMethodsRegistry,
        username: String,
    ): VkLoginResult? {
        val currentAnonymousToken = when (val result = getAnonymousToken(registry)) {
            is VkResult.Success -> result.data
            is VkResult.Error -> return result.asLoginFailure(
                "VK did not issue an anonymous authorization token",
            )
        }
        val trustedHash = sessionStore?.session?.trustedHash?.takeIf(String::isNotBlank)
        val validation = when (val result = registry.validateAccount(
            login = username,
            anonymousToken = currentAnonymousToken,
            trustedHash = trustedHash,
        )) {
            is VkResult.Success -> result.data
            is VkResult.Error -> return result.asLoginFailure("VK could not validate this account")
        }
        val sid = validation.sid.orEmpty()
        if (sid.isBlank()) {
            val message = if (validation.flowName == AuthFlowName.NEED_REGISTRATION) {
                "This VK account does not exist"
            } else {
                "VK returned an empty authorization session"
            }
            return VkLoginResult.Failure(message)
        }

        var method = validation.nextStep?.verificationMethod ?: AuthVerificationMethod.PASSWORD
        if (validation.flowName == AuthFlowName.NEED_PASSWORD &&
            validation.nextStep?.hasAnotherVerificationMethods == true &&
            validation.flowNames.orEmpty().contains("password")
        ) {
            method = AuthVerificationMethod.PASSWORD
        }
        val attempt = AuthAttempt(
            username = username,
            anonymousToken = currentAnonymousToken,
            sid = sid,
            verificationMethod = method,
        )
        activeAuthAttempt = attempt

        if (method != AuthVerificationMethod.PASSWORD) {
            return prepareTwoFactor(registry, attempt)
        }
        return null
    }

    private suspend fun prepareTwoFactor(
        registry: VkMethodsRegistry,
        attempt: AuthAttempt,
    ): VkLoginResult.TwoFactor {
        var destination = attempt.verificationMethod.destination
        var codeLength = 0
        val otpKind = when (attempt.verificationMethod) {
            AuthVerificationMethod.SMS -> VkMethodsRegistry.OtpKind.Sms
            AuthVerificationMethod.CALLRESET -> VkMethodsRegistry.OtpKind.CallReset
            else -> null
        }
        if (otpKind != null) {
            when (val sent = registry.ecosystemSendOtp(otpKind, attempt.sid, attempt.anonymousToken)) {
                is VkResult.Success -> {
                    destination = sent.data.info.ifBlank { destination }
                    codeLength = sent.data.codeLength.coerceAtLeast(0)
                }
                is VkResult.Error -> {
                    // VK MP3 MOD оставляет ручной повтор доступным. Не уничтожаем sid:
                    // пользователь всё ещё может ввести уже доставленный код.
                    destination = sent.message.ifBlank { destination }
                }
            }
        }
        return VkLoginResult.TwoFactor(
            validationSid = attempt.sid,
            destination = destination,
            codeLength = codeLength,
        )
    }

    private suspend fun requestOAuthToken(
        registry: VkMethodsRegistry,
        attempt: AuthAttempt,
        password: String,
        captchaParams: Map<String, String>,
    ): VkLoginResult = when (val result = registry.oauthToken(
        username = attempt.username,
        password = if (attempt.canSkipPassword) "" else password,
        sid = attempt.sid,
        anonymousToken = attempt.anonymousToken,
        grantType = attempt.grantType,
        extraParams = captchaParams,
    )) {
        is VkResult.Error -> result.asLoginFailure("VK authorization failed")
        is VkResult.Success -> when (val response = result.data) {
            is RequestTokenResponse.Success -> finishSignIn(registry, response)
            is RequestTokenResponse.CaptchaRequired -> {
                attempt.captchaSid = response.captchaSid
                VkLoginResult.Captcha(response.captchaSid, response.captchaImg)
            }
            is RequestTokenResponse.Processing -> {
                if (++attempt.processingPolls > 6) {
                    VkLoginResult.Failure("VK authorization is still processing. Try again.")
                } else {
                    delay(1_000)
                    requestOAuthToken(registry, attempt, password, captchaParams)
                }
            }
            is RequestTokenResponse.TwoFactorRequired -> VkLoginResult.Failure(
                "VK returned a legacy verification flow (${response.validationType})",
            )
            is RequestTokenResponse.ClientError -> VkLoginResult.Failure(
                response.errorDescription.ifBlank { response.error.ifBlank { "VK authorization failed" } },
            )
            is RequestTokenResponse.NestedApiError -> VkLoginResult.Failure(
                response.error.error_msg.ifBlank { "VK error ${response.error.error_code}" },
            )
            is RequestTokenResponse.UnknownError -> VkLoginResult.Failure(
                response.errorDescription.ifBlank { response.error.ifBlank { "Unknown VK authorization response" } },
            )
        }
    }

    private suspend fun getAnonymousToken(registry: VkMethodsRegistry): VkResult<String> =
        anonymousTokenMutex.withLock {
            when (val result = registry.getAnonymToken()) {
                is VkResult.Error -> result
                is VkResult.Success -> {
                    val token = result.data.token
                    if (token.isBlank()) {
                        VkResult.Error(0, "VK returned an empty anonymous authorization token")
                    } else {
                        anonymousToken = token
                        anonymousTokenExpiresAt = result.data.expiredAt.toLong()
                        VkResult.Success(token)
                    }
                }
            }
        }

    private suspend fun finishSignIn(
        registry: VkMethodsRegistry,
        response: RequestTokenResponse.Success,
    ): VkLoginResult {
        if (response.accessToken.isBlank()) {
            return VkLoginResult.Failure("VK returned an empty access token")
        }
        val exchangeToken = when (val result = registry.getUserExchangeTokens(response.accessToken)) {
            is VkResult.Error -> return result.asLoginFailure("VK did not issue an exchange token")
            is VkResult.Success -> result.data.usersExchangeTokens.orEmpty()
                .firstOrNull()
                ?.commonToken
                .orEmpty()
                .ifBlank { return VkLoginResult.Failure("VK returned an empty exchange token") }
        }
        val nowSeconds = System.currentTimeMillis() / 1000
        installSession(
            VkAuthSession(
                userId = response.userId,
                accessToken = response.accessToken,
                expiresAt = response.accessTokenExpiresIn
                    .takeIf { it > 0 }
                    ?.let { nowSeconds + it }
                    ?: 0L,
                trustedHash = response.trustedHash,
                exchangeToken = exchangeToken,
            ),
        )
        activeAuthAttempt = null
        // Профиль не является условием валидности токена: при временной ошибке
        // users.get пользователь всё равно остаётся авторизованным.
        runCatching { fetchUserData() }
        return VkLoginResult.Success
    }

    private val AuthVerificationMethod.wireName: String
        get() = when (this) {
            AuthVerificationMethod.CALLRESET -> "callreset"
            AuthVerificationMethod.CODEGEN -> "codegen"
            AuthVerificationMethod.EMAIL -> "email"
            AuthVerificationMethod.LIBVERIFY -> "libverify"
            AuthVerificationMethod.PASSKEY -> "passkey"
            AuthVerificationMethod.PASSWORD -> "password"
            AuthVerificationMethod.PUSH -> "push"
            AuthVerificationMethod.QR_CODE -> "qr_code"
            AuthVerificationMethod.RESERVE_CODE -> "reserve_code"
            AuthVerificationMethod.SMS -> "sms"
        }

    private val AuthVerificationMethod.destination: String
        get() = when (this) {
            AuthVerificationMethod.SMS -> "SMS"
            AuthVerificationMethod.CALLRESET -> "a VK phone call"
            AuthVerificationMethod.CODEGEN -> "your authenticator app"
            AuthVerificationMethod.EMAIL -> "your email"
            AuthVerificationMethod.PUSH -> "a VK notification"
            AuthVerificationMethod.RESERVE_CODE -> "your reserve codes"
            AuthVerificationMethod.LIBVERIFY -> "the VK verification service"
            AuthVerificationMethod.PASSKEY -> "your passkey"
            AuthVerificationMethod.QR_CODE -> "the VK QR confirmation"
            AuthVerificationMethod.PASSWORD -> "your VK account"
        }

    private fun VkResult.Error.asLoginFailure(fallback: String): VkLoginResult.Failure {
        val detail = message.trim()
        return VkLoginResult.Failure(if (detail.isBlank()) "$fallback ($code)" else detail)
    }

    /** Точка передачи аккаунта из восстанавливаемого VK auth-флоу. */
    fun installSession(session: VkAuthSession) {
        val store = checkNotNull(sessionStore) { "MusicAuth is not initialized" }
        store.session = session
        applySession(session)
    }

    private fun applySession(session: VkAuthSession) {
        _isLoggedIn.value = session.accessToken.isNotBlank()
        _partnerUserId.value = session.userId.takeIf { it != 0L }
        val displayName = listOf(session.firstName, session.lastName)
            .filter(String::isNotBlank)
            .joinToString(" ")
            .ifBlank { session.username }
        _profileName.value = displayName.takeIf(String::isNotBlank)
        _avatarUrl.value = session.avatar.takeIf(String::isNotBlank)
    }

    suspend fun fetchUserData() {
        val store = sessionStore ?: return
        if (store.session.accessToken.isBlank()) return
        val registry = methods ?: return
        val profile = when (val result = registry.usersGetCurrent()) {
            is VkResult.Success -> result.data.firstOrNull()
            is VkResult.Error -> null
        } ?: return

        val updated = store.session.copy(
            userId = profile.id.takeIf { it != 0L } ?: store.session.userId,
            username = profile.domain,
            firstName = profile.firstName,
            lastName = profile.lastName,
            avatar = profile.bestPhotoUrl,
        )
        store.session = updated
        applySession(updated)
    }
    fun getEffectiveQuality(requested: String, premium: Boolean): String = if (premium) requested else "128K"
    suspend fun reissueSessionToken() {
        val client = apiClient ?: return
        if (client.refreshToken()) {
            sessionStore?.session?.let(::applySession)
            fetchUserData()
        }
    }
    fun logout() {
        sessionStore?.session = VkAuthSession.EMPTY
        _partnerUserId.value = null
        _isLoggedIn.value = false
        _profileName.value = null
        _avatarUrl.value = null
        _userEmail.value = null
        anonymousToken = ""
        anonymousTokenExpiresAt = 0L
        activeAuthAttempt = null
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
