package com.lmg.vk.engine.backend

import com.lmg.vk.engine.Track
import com.lmg.vk.engine.AccountSyncManager
import com.lmg.vk.engine.LyricsParser
import com.lmg.vk.engine.PlaylistManager
import com.lmg.vk.engine.PlayerController
import com.lmg.vk.engine.VkMixCategory
import com.lmg.vk.engine.VkMixCategoryType
import com.lmg.vk.engine.VkMixOption
import com.lmg.vk.engine.VkMixSession
import com.lmg.vk.engine.VkMixSettings
import com.lmg.vk.engine.backend.wave.WaveBatchResponse
import com.lmg.vk.engine.backend.wave.WaveSessionStartResponse
import com.lmg.vk.data.local.db.FavoriteTrackDatabase
import com.lmg.vk.data.local.db.AppDatabase
import com.lmg.vk.data.local.db.LibraryRepository
import com.lmg.vk.data.local.WaveRepository
import com.lmg.vk.engine.PlaylistSyncManager
import com.lmg.vk.network.VkApiClient
import com.lmg.vk.network.VkAuthSession
import com.lmg.vk.network.GlobalCaptchaManager
import com.lmg.vk.network.VkResult
import com.lmg.vk.network.VkMultiSessionStore
import com.lmg.vk.network.VkSessionStore
import com.lmg.vk.network.getOrNull
import com.lmg.vk.network.dto.AuthFlowName
import com.lmg.vk.network.dto.AuthValidationType
import com.lmg.vk.network.dto.AuthVerificationMethod
import com.lmg.vk.network.dto.RequestTokenResponse
import com.lmg.vk.network.dto.music.AudioPlaylist
import com.lmg.vk.network.dto.music.AudioAlbum
import com.lmg.vk.network.dto.music.AlbumThumb
import com.lmg.vk.network.dto.music.AudioPlaylistDto
import com.lmg.vk.network.dto.music.AudioRecommendedPlaylistDto
import com.lmg.vk.network.dto.music.AudioAudioDto
import com.lmg.vk.network.dto.music.AudioArtistDto
import com.lmg.vk.network.dto.music.AudioPhotoDto
import com.lmg.vk.network.dto.music.AudioSearchMainResponse
import com.lmg.vk.network.dto.music.AudioStreamMix
import com.lmg.vk.network.dto.music.AudioStreamMixSettings
import com.lmg.vk.network.dto.music.AudioTrack
import com.lmg.vk.network.dto.music.coverUrl
import com.lmg.vk.network.dto.music.mergeAudioTracksById
import com.lmg.vk.network.dto.music.withVkArtworkFallback
import com.lmg.vk.network.dto.music.MainArtist
import com.lmg.vk.network.dto.music.RadioStation
import com.lmg.vk.network.dto.music.VkArtistDto
import com.lmg.vk.network.dto.music.VkCatalogBlock
import com.lmg.vk.network.dto.music.VkCatalogButton
import com.lmg.vk.network.dto.music.VkCatalogBanner
import com.lmg.vk.network.dto.music.VkAudioContentCard
import com.lmg.vk.network.dto.music.VkCatalogLink
import com.lmg.vk.network.dto.music.VkCatalogProfile
import com.lmg.vk.network.dto.music.VkCatalogResponse
import com.lmg.vk.network.dto.music.VkCatalogVideo
import com.lmg.vk.network.dto.music.VkCatalogAudioBook
import com.lmg.vk.network.dto.music.VkCatalogAudioBookPerson
import com.lmg.vk.network.dto.music.VkCatalogFollowingsUpdateInfo
import com.lmg.vk.network.dto.music.VkCatalogLongread
import com.lmg.vk.network.dto.music.VkCatalogPodcastEntry
import com.lmg.vk.network.methods.VkAudioApi
import com.lmg.vk.network.methods.VkCatalogApi
import com.lmg.vk.network.methods.VkMethodsRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
    // -1 ставит VkApiClient на НЕсетевое исключение (разбор ответа и подобное).
    // Раньше такие ошибки тоже получали 0 и показывались как «нет интернета» —
    // пользователь искал проблему в сети, которой нет.
    -1 -> "Сбой обработки ответа VK"
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

/**
 * The identity VK needs to continue an `audio.getStreamMixAudios` queue.
 * Mirrors the relevant fields of official VK's `StartPlayVkMixSource` instead
 * of losing the mix id after the first five tracks have been loaded.
 */
data class VkMixPlaybackSource(
    val session: VkMixSession,
    val tracks: List<Track>,
) {
    val mixId: String get() = session.mixId
    val entityId: String? get() = session.entityId
}

/** One official VK similar-track response after conversion to the LMG model. */
data class TrackWaveRecommendations(
    val returnedIdsCount: Int,
    val tracks: List<Track>,
)

/** Source fields which official VK derives from the finite StartPlaySource. */
data class VkAutoflowSource(
    val queueType: String,
    val queueEntityId: String,
)

object MusicBackend {

    private data class VkMixPromptEvent(
        val sequence: Long,
        val eventType: String,
        val eventSubtype: String,
        val ownerId: Long,
        val audioId: Int,
        val blockId: String,
        val playbackDuration: Int,
        val eventTimestampMs: Long,
        val trackCode: String,
    ) {
        fun toJson() = org.json.JSONObject()
            .put("event_type", eventType)
            .put("event_subtype", eventSubtype)
            .put("owner_id", ownerId)
            .put("audio_id", audioId)
            .put("block_id", blockId)
            .put("playback_duration", playbackDuration)
            .put("event_timestamp_ms", eventTimestampMs)
            .put("track_code", trackCode)
    }

    private data class VkMixPromptBatch(
        val lastSequence: Long,
        val count: Int,
        val json: String,
    )

    private val mixPromptLock = Any()
    private val mixPromptEvents = ArrayDeque<VkMixPromptEvent>()
    private var mixPromptSequence = 0L

    /**
     * Same compact event object official VK derives from CommonAudioStat before
     * calling `audio.getStreamMixAudios`. Events remain queued across failures.
     */
    fun recordVkMixPromptEvent(
        session: VkMixSession,
        track: Track,
        eventType: String,
        eventSubtype: String,
    ) {
        if (eventType !in setOf("start", "end", "pause") || eventSubtype.isBlank()) return
        val bareId = com.lmg.vk.engine.VkAudioIdentity.bareFullId(track.id) ?: return
        val separator = bareId.lastIndexOf('_')
        if (separator <= 0 || separator >= bareId.lastIndex) return
        val ownerId = bareId.substring(0, separator).toLongOrNull() ?: return
        val audioId = bareId.substring(separator + 1).toIntOrNull() ?: return
        val cached = trackCache[bareId]
        synchronized(mixPromptLock) {
            mixPromptSequence++
            mixPromptEvents.addLast(
                VkMixPromptEvent(
                    sequence = mixPromptSequence,
                    eventType = eventType,
                    eventSubtype = eventSubtype,
                    ownerId = ownerId,
                    audioId = audioId,
                    blockId = session.blockId,
                    playbackDuration = (track.durationMs / 1_000L)
                        .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
                    eventTimestampMs = System.currentTimeMillis(),
                    trackCode = cached?.track_code.orEmpty(),
                ),
            )
            while (mixPromptEvents.size > 200) mixPromptEvents.removeFirst()
        }
    }

    private fun snapshotVkMixPromptEvents(): VkMixPromptBatch? = synchronized(mixPromptLock) {
        val events = mixPromptEvents.take(100)
        if (events.isEmpty()) return@synchronized null
        val json = org.json.JSONArray()
        events.forEach { json.put(it.toJson()) }
        VkMixPromptBatch(
            lastSequence = events.last().sequence,
            count = events.size,
            json = json.toString(),
        )
    }

    private fun acknowledgeVkMixPromptEvents(batch: VkMixPromptBatch) {
        synchronized(mixPromptLock) {
            while (
                mixPromptEvents.isNotEmpty() &&
                mixPromptEvents.peekFirst().sequence <= batch.lastSequence
            ) {
                mixPromptEvents.removeFirst()
            }
        }
        com.lmg.vk.debug.DebugLog.add("VK MIX prompt events accepted=${batch.count}")
    }

    fun clearVkMixPromptEvents() {
        synchronized(mixPromptLock) {
            mixPromptEvents.clear()
            mixPromptSequence = 0L
        }
    }

    private suspend fun loadVkMixAudioTracks(
        session: VkMixSession,
        append: Boolean,
    ): List<AudioTrack> {
        val promptBatch = snapshotVkMixPromptEvents()
        val tracks = audioApi.getStreamMixAudios(
            mixId = session.mixId,
            entityId = session.entityId,
            append = append,
            options = session.options,
            mixOptionsId = session.mixOptionsId,
            sourceRef = session.sourceRef,
            promptEvents = promptBatch?.json,
        ).requireData()
        promptBatch?.let(::acknowledgeVkMixPromptEvents)
        return tracks
    }

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError
    private val _lastApiException = MutableStateFlow<BackendException?>(null)
    val lastApiException: StateFlow<BackendException?> = _lastApiException

    var isInitialized: Boolean = false; private set
    val region: String get() = "ru"

    private lateinit var audioApi: VkAudioApi
    private lateinit var catalogApi: VkCatalogApi
    private lateinit var methodsRegistry: VkMethodsRegistry
    private lateinit var sessionStore: VkSessionStore
    private const val PERSONAL_VK_MIX_ID = "common"
    private val trackCache = ConcurrentHashMap<String, AudioTrack>()
    private data class CachedStream(val info: StreamInfo, val cachedAtMs: Long)
    private val streamCache = ConcurrentHashMap<String, CachedStream>()
    private val waveMutex = Mutex()
    private val waveQueue = ArrayDeque<AudioTrack>()
    private var activeWaveMix: AudioStreamMix? = null
    private var activeWaveAppend = false

    /**
     * Источник волны уже выбран в этой сессии.
     *
     * Без флага ensureWaveSourceLocked уходил в сеть на КАЖДЫЙ вызов, когда
     * подходящего микса нет: activeWaveMix остаётся null, recommendationOffset
     * ещё 0 — условие раннего выхода не срабатывает. А takeWaveTracks зовётся на
     * каждом переходе трека и каждом рефилле очереди, то есть тяжёлый
     * catalog.getAudioAuto() повторялся без конца.
     */
    private var waveSourceResolved = false
    private var waveSessionId: String? = null
    private var recommendationOffset = 0
    private var waveAccountId = Long.MIN_VALUE

    fun init(client: VkApiClient, sessions: VkSessionStore) {
        audioApi = VkAudioApi(client)
        catalogApi = VkCatalogApi(client)
        methodsRegistry = VkMethodsRegistry(client)
        sessionStore = sessions
        isInitialized = true
        MusicAuth.init(client, sessions)
        VkProfileRepository.init(client)
    }

    fun getInstance(): MusicBackend = this
    fun getLastErrorCode(): String? = null
    fun getLastHttpCode(): Int = 200

    // ---------- стрим ----------
    suspend fun getTrackInfo(trackId: String, quality: String = "lossless", region: String? = null): StreamInfo {
        requireInitialized()
        streamCache[streamCacheKey(trackId)]?.let { cached ->
            // Свежесть по TTL — не единственное условие. В кэш исторически попадал
            // плейсхолдер `audio_api_unavailable.mp3` (см. cacheTracks), и тогда
            // этот ранний return отдавал плееру заведомо неиграбельный URL, минуя
            // проверку isAvailable ниже. Именно так «рабочая» ссылка доходила до
            // ExoPlayer и трек молчал: URL есть, звука нет.
            if (System.currentTimeMillis() - cached.cachedAtMs < STREAM_CACHE_TTL_MS &&
                cached.info.url.isPlayableStreamUrl()
            ) {
                return cached.info
            }
        }
        val track = resolveTrack(trackId, forceNetwork = true)
        if (!track.isAvailable) throw backendFailure(451, "Аудиозапись недоступна")
        // 451, а не 404: трек НАЙДЕН, просто VK не дал ссылку (чаще всего нет
        // access_key либо запись ограничена). Код 404 превращался в «Трек не
        // найден у VK» — сообщение врало, и пользователь искал причину не там.
        if (track.url.isBlank()) {
            throw backendFailure(451, "VK не дал ссылку на трек (нет access_key или доступ закрыт)")
        }
        // Плейсхолдер отличается от пустого url: VK ответил успехом и отдал строку,
        // поэтому без явной проверки ошибка выглядела бы как успешный резолв.
        if (!track.url.isPlayableStreamUrl()) {
            throw backendFailure(451, "VK отдал audio_api_unavailable вместо ссылки")
        }
        return track.toStreamInfo(quality).also {
            streamCache[track.fullId] = CachedStream(it, System.currentTimeMillis())
        }
    }

    /**
     * Синхронный резолв ссылки — его зовёт `StreamingDataSource` в момент, когда
     * ExoPlayer открывает поток.
     *
     * ПОЧЕМУ ЗДЕСЬ БЛОКИРУЮЩИЙ СЕТЕВОЙ ВЫЗОВ. Раньше функция только заглядывала в
     * кэш и бросала, если там пусто. А кэш заполняется выдачей поиска и каталога,
     * то есть при первом обращении к треку его там нет — и поток не открывался
     * ВООБЩЕ НИКОГДА. Именно поэтому воспроизведение не работало с самого начала
     * проекта. В родственном рабочем проекте (LiquidMusicGlass,
     * `IcmRepository.getTrackInfoSync`) этот путь тоже делает реальный запрос —
     * `api.getTrackSync(...)`.
     *
     * Блокировка потока здесь допустима: `DataSource.open()` по контракту media3
     * вызывается на загрузочном потоке ExoPlayer, а не на главном, и обязан
     * возвращаться только когда поток готов. Ограничение по времени обязательно,
     * иначе мёртвая сеть подвесила бы загрузчик плеера.
     */
    fun getTrackInfoSync(trackId: String, quality: String = "lossless"): StreamInfo {
        requireInitialized()
        val id = streamCacheKey(trackId)
        streamCache[id]
            ?.takeIf { System.currentTimeMillis() - it.cachedAtMs < STREAM_CACHE_TTL_MS }
            ?.info
            ?.takeIf { it.url.isPlayableStreamUrl() }
            ?.let { return it }

        // В кэше нет играбельной ссылки — идём в сеть. `resolveTrack` уже умеет
        // подставлять access_key и различает плейсхолдер.
        return kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withTimeout(SYNC_RESOLVE_TIMEOUT_MS) {
                val track = resolveTrack(trackId, forceNetwork = true)
                // Пишем в DebugLog ЧТО ИМЕННО вернул VK: без этого «не играет»
                // неотличимо от «ссылка не пришла», а adb у пользователя нет.
                com.lmg.vk.debug.DebugLog.add(
                    "resolveSync $id: available=${track.isAvailable} " +
                        "url=${if (track.url.isBlank()) "ПУСТО" else track.url.take(60)}"
                )
                if (!track.isAvailable) throw backendFailure(451, "Аудиозапись недоступна")
                // 451, а не 404: трек НАЙДЕН, просто VK не дал ссылку (чаще всего нет
        // access_key либо запись ограничена). Код 404 превращался в «Трек не
        // найден у VK» — сообщение врало, и пользователь искал причину не там.
        if (track.url.isBlank()) {
            throw backendFailure(451, "VK не дал ссылку на трек (нет access_key или доступ закрыт)")
        }
                if (!track.url.isPlayableStreamUrl()) {
                    throw backendFailure(451, "VK отдал audio_api_unavailable вместо ссылки")
                }
                track.toStreamInfo(quality).also { info ->
                    streamCache[id] = CachedStream(info, System.currentTimeMillis())
                }
            }
        }
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
        ).requireData().map(::cacheTrack).map { it.toSearchItem() }

    suspend fun searchCurrentProfileLibrary(query: String): ProfileLibrarySearch {
        requireInitialized()
        val normalizedQuery = query.trim()
        require(normalizedQuery.isNotEmpty()) { "Search query must not be blank" }
        val response = methodsRegistry
            .searchInProfile(ownerId = currentUserId(), query = normalizedQuery)
            .requireData()
        val tracks = response.audios.map(::cacheTrack)
        return ProfileLibrarySearch(
            query = normalizedQuery,
            tracks = tracks.map { it.toEngineTrack() },
            playlists = response.playlists.items.map { playlist ->
                ProfileLibraryPlaylist(
                    id = playlist.fullId,
                    title = playlist.title,
                    trackCount = playlist.count,
                    cover = playlist.photo?.bestUrl
                        ?: playlist.thumbs?.maxByOrNull { it.width * it.height }?.bestUrl,
                )
            },
        )
    }

    suspend fun searchAll(
        query: String,
        region: String? = null,
        source: String = SearchSource.ALL,
        limit: Int = 30,
        offset: Int = 0,
    ): SearchResponse? =
        runCatching {
            requireInitialized()
            val requestedCount = limit.coerceIn(1, 300)
            val audioResult = coroutineScope {
                val audios = async {
                    audioApi.searchAudiosPage(
                        query = query,
                        ownerId = currentUserId(),
                        offset = offset,
                        count = requestedCount,
                    )
                }
                if (offset == 0) {
                    val main = async { audioApi.searchMain(query, offset = 0, count = requestedCount) }
                    val artists = async {
                        audioApi.searchArtists(query, offset = 0, count = requestedCount.coerceAtMost(100))
                    }
                    Triple(main.await(), audios.await(), artists.await())
                } else {
                    Triple(null, audios.await(), null)
                }
            }

            val mainResult = audioResult.first
            val trackPageResult = audioResult.second
            val artistResult = audioResult.third

            val main: AudioSearchMainResponse? = mainResult?.getOrNull()
            val directPage = trackPageResult.getOrNull()
            val directTracks: List<AudioTrack> = directPage?.items.orEmpty()
            val mainTracks = main?.let { response ->
                (response.audios.items + response.own_audios.items).map { it.toAudioTrack() }
            }.orEmpty()
            val tracks = (directTracks + mainTracks)
                .mergeAudioTracksById()
                .map(::cacheTrack)

            val directArtists: List<VkArtistDto> = artistResult?.getOrNull()?.items.orEmpty()
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
            val fallbackAlbums: List<AudioPlaylist> = if (offset == 0 && mainAlbums.isEmpty()) {
                audioApi.searchPlaylists(
                    query = query,
                    ownerId = currentUserId(),
                    offset = 0,
                    count = requestedCount.coerceAtMost(100),
                ).getOrNull().orEmpty().filter { it.isAlbumRelease() }
            } else {
                emptyList()
            }

            if (directPage == null && main == null && directArtists.isEmpty() && fallbackAlbums.isEmpty()) {
                val failure = sequenceOf(mainResult, trackPageResult, artistResult)
                    .filterNotNull()
                    .filterIsInstance<VkResult.Error>()
                    .firstOrNull()
                throw backendFailure(failure?.code ?: 0, failure?.message ?: "VK search failed")
            }

            // Advance by what VK actually returned. If `count` is absent, only
            // an empty next page proves EOF; a short page alone does not.
            val nextOffset = offset + directTracks.size
            val totalTracks = directPage?.count
            val hasMoreTracks = directPage != null && directTracks.isNotEmpty() &&
                (totalTracks?.let { nextOffset < it } ?: true)

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
                nextOffset = nextOffset.takeIf { hasMoreTracks },
                hasMore = hasMoreTracks,
            )
        }.getOrNull()

    fun clearSearchCache() {
        trackCache.clear()
        streamCache.clear()
    }

    fun clearStreamCache() {
        streamCache.clear()
    }

    // ---------- home / charts ----------
    suspend fun loadHomeContent(region: String? = null): HomeResponse {
        requireInitialized()
        // Current VK and VK Music clients bind the root music catalog to the
        // active account. Without owner_id VK may return only the generic
        // Popular showcase instead of the complete personalised sections.
        val catalog = catalogApi.getAudioAuto(currentUserId()).requireData()
        val sections = buildList {
            catalog.catalog?.sections.orEmpty().forEach { section ->
                if (section.id.isNotBlank()) {
                    add(HomeCatalogSection(section.id, section.title.ifBlank { "VK Музыка" }))
                }
            }
            // Some accounts receive root navigation as header actions rather
            // than populated catalog.sections. Only header actions are root
            // tabs; regular block open_section buttons remain show-all links.
            catalog.allBlocks()
                .filter { it.layout?.name.orEmpty().startsWith("header") }
                .forEach { header ->
                    header.actions.orEmpty().forEach buttonLoop@{ button ->
                        val id = (button.action?.section_id ?: button.section_id)
                            ?.takeIf(String::isNotBlank) ?: return@buttonLoop
                        val title = (button.action?.title ?: button.title)
                            ?.takeIf(String::isNotBlank)
                            ?: header.layout?.title?.takeIf(String::isNotBlank)
                            ?: "VK Музыка"
                        add(HomeCatalogSection(id, title))
                    }
                }
        }.distinctBy { it.id }
        val selectedSectionId = catalog.catalog?.default_section?.takeIf(String::isNotBlank)
            ?: catalog.section?.id?.takeIf(String::isNotBlank)
            ?: sections.firstOrNull()?.id

        // Official VK opens one selected catalog section. The old code fetched
        // every section (up to 50 pages each) and merged them into a single New
        // feed, which mixed unrelated showcases and delayed first content.
        val firstPage = if (selectedSectionId != null && catalog.section?.id != selectedSectionId) {
            catalogApi.getSection(selectedSectionId).requireData()
        } else {
            catalog
        }
        val catalogPages = if (firstPage === catalog) listOf(catalog) else listOf(catalog, firstPage)
        val catalogBlocks = catalogPages.toHomeBlocks(selectedSectionId)
        val blocks = catalogBlocks.ifEmpty { loadHomeFallbackBlocks() }
        catalogPages.flatMap { it.audios.orEmpty() }
            .mergeAudioTracksById()
            .also(::cacheTracks)
        return HomeResponse(
            blocks = blocks,
            updatedAt = System.currentTimeMillis(),
            sections = sections,
            selectedSectionId = selectedSectionId,
            sectionNextFrom = firstPage.sectionNextFrom(selectedSectionId),
        )
    }

    /** One page of a selected root catalog section (`catalog.getSection`). */
    suspend fun loadHomeSection(
        sectionId: String,
        startFrom: String? = null,
    ): HomeSectionPage {
        requireInitialized()
        require(sectionId.isNotBlank()) { "Catalog section id is blank" }
        val response = catalogApi.getSection(sectionId, startFrom).requireData()
        response.audios.orEmpty().mergeAudioTracksById().also(::cacheTracks)
        return HomeSectionPage(
            blocks = listOf(response).toHomeBlocks(sectionId),
            nextFrom = response.sectionNextFrom(sectionId)
                ?.takeIf { it.isNotBlank() && it != startFrom },
            sectionId = sectionId,
        )
    }

    /** Initial official curator catalog; continuation uses its returned section id. */
    suspend fun loadCuratorCatalog(curatorId: String): HomeSectionPage {
        requireInitialized()
        require(curatorId.isNotBlank()) { "Curator id is blank" }
        val response = catalogApi.getAudioCurator(curatorId).requireData()
        response.audios.orEmpty().mergeAudioTracksById().also(::cacheTracks)
        val sectionId = response.section?.id?.takeIf(String::isNotBlank)
            ?: response.catalog?.default_section?.takeIf(String::isNotBlank)
            ?: response.catalog?.sections.orEmpty().firstOrNull { it.id.isNotBlank() }?.id
        return HomeSectionPage(
            blocks = listOf(response).toHomeBlocks(sectionId),
            nextFrom = response.sectionNextFrom(sectionId),
            sectionId = sectionId,
        )
    }

    suspend fun loadCatalogTab(replacementId: String): List<HomeBlock> {
        requireInitialized()
        val request = parseCatalogTabRequest(replacementId)
            ?: throw backendFailure(404, "VK не дал идентификатор раздела")
        val response = when (request) {
            is CatalogTabRequest.Section -> catalogApi.getSection(request.sectionId)
            is CatalogTabRequest.Replacement ->
                catalogApi.replaceBlocks(listOf(request.replacementId))
        }.requireData()
        response.audios.orEmpty().mergeAudioTracksById().also(::cacheTracks)
        return listOf(response).toHomeBlocks()
    }

    /**
     * Следующая порция элементов блока: `catalog.getBlockItems(block_id, start_from)`.
     *
     * Официальный VK 8.185 отправляет `catalog.getBlockItems` с `block_id` и
     * `start_from`; ответ содержит обновлённый `block`, extended entities и
     * следующий `block.next_from`. Ошибка запроса должна дойти до UI, иначе она
     * неотличима от настоящего конца списка и кнопка Retry никогда не появится.
     */
    suspend fun loadBlockItemsPage(
        blockId: String,
        startFrom: String,
        ref: String? = null,
        usedCursors: Set<String> = emptySet(),
    ): HomeBlockPage {
        requireInitialized()
        if (blockId.isBlank() || startFrom.isBlank()) return HomeBlockPage(emptyList(), null)
        val response = catalogApi.getBlockItems(blockId, startFrom, ref).requireData()
        response.audios.orEmpty().mergeAudioTracksById().also(::cacheTracks)
        // Разбираем страницу тем же путём, что и главную выдачу, а потом берём
        // элементы блока с нужным id: сущности лежат в корне ответа, и склеить их
        // с ссылками блока умеет только toHomeBlocks().
        val blocks = listOf(response).toHomeBlocks()
        val page = blocks.firstOrNull { it.id == blockId }
        val nextFrom = (
            response.block?.next_from
                ?: response.allBlocks().firstOrNull { it.id == blockId }?.next_from
            )?.takeIf { it.isNotBlank() && it != startFrom && it !in usedCursors }
        return HomeBlockPage(items = page?.items.orEmpty(), nextFrom = nextFrom)
    }

    /**
     * Official StartPlayCatalogSource used by VK Music Signal.
     *
     * VK does not page the visible Signal card itself. Its play action carries
     * a catalog block id, which is resolved through `audio.getIdsBySource`;
     * those ids are then hydrated with the regular `audio.getById` contract.
     */
    suspend fun getCatalogSourceTracks(
        blockId: String,
        ref: String? = null,
    ): List<Track> {
        requireInitialized()
        require(blockId.isNotBlank()) { "Catalog block id is blank" }
        val ids = when (
            val result = methodsRegistry.getIdsBySource(
                source = "catalog",
                entityId = blockId,
                ref = ref,
            )
        ) {
            is VkResult.Success -> result.data
            is VkResult.Error -> {
                com.lmg.vk.debug.DebugLog.add(
                    "VK CATALOG SOURCE api_error block=$blockId code=${result.code} message=${result.message}",
                )
                throw backendFailure(result.code, result.message)
            }
        }.map(::normalizeTrackId).filter(String::isNotBlank).distinct()

        if (ids.isEmpty()) {
            com.lmg.vk.debug.DebugLog.add("VK CATALOG SOURCE success_empty block=$blockId")
            return emptyList()
        }

        val hydrated = buildList {
            ids.chunked(100).forEach { page ->
                addAll(audioApi.getById(page).requireData().map(::cacheTrack))
            }
        }
        val byId = hydrated.associateBy {
            com.lmg.vk.engine.VkAudioIdentity.stableFullId(it.fullId)
        }
        val tracks = ids.mapNotNull { id ->
            byId[com.lmg.vk.engine.VkAudioIdentity.stableFullId(id)]
                ?.takeIf { it.isAvailable }
                ?.toEngineTrack()
        }
        com.lmg.vk.debug.DebugLog.add(
            "VK CATALOG SOURCE resolved block=$blockId ids=${ids.size} tracks=${tracks.size}",
        )
        return tracks
    }

    /**
     * Подтверждённый VK fallback для редких ответов без CatalogKit item IDs.
     * Это не локальные карточки: оба списка приходят из audio.* текущей сессии.
     */    private suspend fun loadHomeFallbackBlocks(): List<HomeBlock> = coroutineScope {
        val recommendations = async {
            audioApi.getRecommendations(
                count = 50,
                userId = currentUserId(),
            ).getOrNull().orEmpty().map(::cacheTrack)
        }
        val popular = async {
            audioApi.getPopular(count = 50).getOrNull().orEmpty().map(::cacheTrack)
        }
        listOfNotNull(
            recommendations.await().takeIf { it.isNotEmpty() }?.let { tracks ->
                HomeBlock(
                    id = "vk_recommendations",
                    title = "Рекомендации VK",
                    type = "recommendations",
                    items = tracks.map { it.toHomeItem() },
                )
            },
            popular.await().takeIf { it.isNotEmpty() }?.let { tracks ->
                HomeBlock(
                    id = "vk_popular",
                    title = "Популярное в VK",
                    type = "popular",
                    items = tracks.map { it.toHomeItem() },
                )
            },
        )
    }

    suspend fun loadCharts(region: String? = null): List<Chart> {
        requireInitialized()
        val tracks = audioApi.getPopular(count = 100).requireData().map(::cacheTrack)
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
        ).requireData().map(::cacheTrack)
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
                val page = catalogApi.getBlockItems(block.id, startFrom, block.ref).getOrNull() ?: break
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
        val tracks = catalogPages.flatMap { it.audios.orEmpty() }.ifEmpty {
            audioApi.getAudiosByArtist(normalizedId).requireData()
        }.mergeAudioTracksById().map(::cacheTrack)
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
                it.audio_stream_mixes.orEmpty().firstOrNull()?.playbackMixId
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

    suspend fun getArtistMixSource(artistId: String, mixId: String?): VkMixPlaybackSource? = runCatching {
        val normalizedId = artistId.removePrefix("vk_")
        val artistCatalog = catalogApi.getAudioArtist(normalizedId).getOrNull()
        val catalogMixes = artistCatalog?.audio_stream_mixes.orEmpty()
        val catalogMix = if (mixId == null) {
            catalogMixes.firstOrNull()
        } else {
            catalogMixes.firstOrNull { it.playbackMixId == mixId }
        }
        val resolvedMixId = mixId ?: catalogMix?.playbackMixId ?: return@runCatching null
        val settings = catalogMix?.settings?.toVkMixSettings()
        val session = VkMixSession(
            blockId = artistCatalog?.allBlocks()?.firstOrNull { block ->
                catalogMix != null && catalogMix.id in block.audio_stream_mixes_ids.orEmpty()
            }?.id.orEmpty(),
            sectionId = artistCatalog?.section?.id?.takeIf(String::isNotBlank)
                ?: artistCatalog?.catalog?.default_section.orEmpty(),
            mixId = resolvedMixId,
            isTunable = catalogMix?.is_tunable == true,
            title = catalogMix?.playbackTitle(settings).orEmpty(),
            settings = settings,
            entityId = normalizedId,
            catalogItemId = catalogMix?.id,
        )
        val tracks = loadVkMixAudioTracks(session, append = false)
            .map(::cacheTrack).map { it.toEngineTrack() }
        VkMixPlaybackSource(
            session = session,
            tracks = tracks,
        )
    }.getOrNull()

    /**
     * The interactive VK Mix holder uses the stable `common` playback id for
     * the account's personal mix. Catalog blocks only describe how VK renders
     * that entry point; they are not required by either stream-mix endpoint.
     */
    suspend fun resolvePersonalMixSession(): VkMixSession {
        requireInitialized()
        return VkMixSession(
            mixId = PERSONAL_VK_MIX_ID,
            isTunable = true,
            title = "VK Mix",
            settings = null,
        )
    }

    /** Start a fresh VK Mix queue with `append=false`. */
    suspend fun startVkMix(session: VkMixSession): VkMixPlaybackSource {
        val tracks = loadVkMixAudioTracks(session, append = false)
            .map(::cacheTrack).map { it.toEngineTrack() }
        return VkMixPlaybackSource(session = session, tracks = tracks)
    }

    suspend fun getPersonalMixSource(): VkMixPlaybackSource =
        startVkMix(resolvePersonalMixSession())

    /**
     * Resolve the official Autoflow hand-off without loading Mix tracks yet.
     * VK preloads this identity near the end of a finite queue and only starts
     * `getStreamMixAudios(append=false)` after that queue is exhausted.
     */
    suspend fun resolveAutoflowMixSession(
        queueCount: Int,
        audioIds: List<String>,
        source: VkAutoflowSource,
        title: String,
    ): VkMixSession {
        requireInitialized()
        val normalizedIds = audioIds
            .map(::normalizeTrackId)
            .filter { it.isVkAudioFullId() }
            .takeLast(50)
        require(normalizedIds.isNotEmpty()) { "Autoflow queue has no VK audio ids" }

        com.lmg.vk.debug.DebugLog.add(
            "VK AUTOFLOW request count=$queueCount queueType=${source.queueType} " +
                "entity=${source.queueEntityId} audioIds=${normalizedIds.size}",
        )
        val response = audioApi.getAutoflowMixParams(
            count = queueCount,
            queueType = source.queueType,
            audioIds = normalizedIds,
            queueEntityId = source.queueEntityId,
        ).requireData()
        require(response.mix_id.isNotBlank() && response.entity_id.isNotBlank()) {
            "VK returned empty Autoflow Mix identity"
        }
        com.lmg.vk.debug.DebugLog.add(
            "VK AUTOFLOW resolved mixId=${response.mix_id} entityId=${response.entity_id}",
        )
        return VkMixSession(
            mixId = response.mix_id,
            isTunable = false,
            title = title,
            settings = null,
            entityId = response.entity_id,
        )
    }

    /** Mirrors the normal-audio part of official `MusicTrack.isAutoflowSuitable`. */
    fun isAutoflowEligible(trackId: String): Boolean {
        val cached = trackCache[streamCacheKey(trackId)] ?: return true
        return cached.isAvailable &&
            !cached.isPodcast &&
            cached.nft_info == null &&
            cached.external_audio == null &&
            cached.audiobook_chapter == null
    }

    /** Official `MusicTrack.Bb()`: owner_audio plus access_key when present. */
    fun autoflowTrackEntityId(trackId: String): String {
        val bareId = streamCacheKey(trackId)
        val accessKey = trackCache[bareId]?.access_key?.takeIf(String::isNotBlank)
        return accessKey?.let { "${bareId}_$it" } ?: bareId
    }

    /** Refresh settings exactly through `audio.getStreamMixSettings`. */
    suspend fun getVkMixSettings(session: VkMixSession): VkMixSettings? {
        requireInitialized()
        return when (val result = audioApi.getStreamMixSettings(session.mixId)) {
            is VkResult.Success -> result.data.settings
                ?.toVkMixSettings()
                ?.withSelectedOptions(session.options)
                ?: session.settings
            is VkResult.Error -> {
                // The catalog's AudioStreamMix carries the same official
                // settings payload. Some accounts return 404 from the refresh
                // endpoint while that catalog snapshot remains usable.
                if (result.code == 404 && session.settings != null) {
                    com.lmg.vk.debug.DebugLog.add(
                        "VK MIX settings refresh returned 404; using CatalogKit settings",
                    )
                    session.settings
                } else {
                    throw backendFailure(result.code, result.message)
                }
            }
        }
    }

    /** Continue the same VK Mix session; regular album/playlist queues never call this. */
    suspend fun appendVkMix(session: VkMixSession): List<Track> = runCatching {
        loadVkMixAudioTracks(session, append = true)
            .map(::cacheTrack)
            .map { it.toEngineTrack() }
            .filter { it.isAvailable }
    }.getOrDefault(emptyList())

    /** Official negative feedback. `audio.addDislike` returns the updated track. */
    suspend fun dislikeTrack(trackId: String) {
        val updated = audioApi.addDislike(normalizeTrackId(trackId)).requireData()
        cacheTrack(updated.toAudioTrack())
    }

    /** The response form is unconfirmed, therefore removeDislike remains Unit. */
    suspend fun removeTrackDislike(trackId: String) {
        audioApi.removeDislike(normalizeTrackId(trackId)).requireData()
    }

    suspend fun getArtistTopTracks(artistId: String): List<Track> =
        audioApi.getAudiosByArtist(artistId.removePrefix("vk_")).requireData()
            .map(::cacheTrack)
            .map { it.toEngineTrack() }

    /** One lazily requested artist page; an API failure is not treated as EOF. */
    suspend fun getArtistTracksPage(
        artistId: String,
        offset: Int,
        limit: Int = 100,
    ): ArtistTrackPage? = runCatching {
        requireInitialized()
        val pageSize = limit.coerceIn(1, 100)
        val tracks = audioApi.getAudiosByArtist(
            artistId = artistId.removePrefix("vk_"),
            // VK's artist endpoint expects a concrete group. Omitting `type`
            // makes this page request fail, so Retry only repeated the same
            // invalid call. `top` is the confirmed mode used by the working
            // getArtistTopTracks path; offset still advances page by page.
            type = "top",
            offset = offset.coerceAtLeast(0),
            count = pageSize,
        ).requireData()
        // This response has no total/cursor. A short non-empty page is not
        // reliable EOF; request once more and stop only on an empty page.
        val nextOffset = offset.coerceAtLeast(0) + tracks.size
        ArtistTrackPage(
            tracks = tracks.map(::cacheTrack).map { it.toEngineTrack() },
            nextOffset = nextOffset.takeIf { tracks.isNotEmpty() },
            hasMore = tracks.isNotEmpty(),
        )
    }.getOrNull()

    /**
     * Все аудио исполнителя. `topSongs` на странице артиста остаётся быстрым
     * превью, а этот метод постранично собирает полный список для See all и
     * реального счётчика.
     */
    suspend fun getArtistAllTracks(artistId: String): List<Track> {
        requireInitialized()
        val normalizedId = artistId.removePrefix("vk_")
        val tracks = mutableListOf<AudioTrack>()
        val pageSize = 100

        // В оригинальном клиенте каталог артиста разделён на три типа:
        // main, featured и top. Один запрос без type возвращает только часть
        // каталога (на практике часто ровно 200 аудио), поэтому собираем все
        // подтверждённые группы и дедуплицируем их по полному VK id.
        suspend fun loadType(type: String?) {
            var offset = 0
            var previousPageIds: List<String>? = null

            while (offset < 6_000) {
                val page = audioApi.getAudiosByArtist(
                    artistId = normalizedId,
                    type = type,
                    offset = offset,
                    count = pageSize,
                ).getOrNull() ?: break

                val pageIds = page.map { it.fullId }
                // Защита от API, которое проигнорировало offset и вернуло ту же
                // страницу повторно. Между разными type повторы допустимы.
                if (pageIds == previousPageIds) break
                previousPageIds = pageIds

                // Один трек бывает сразу в main/featured/top. Оставляем все
                // варианты до финальной склейки, чтобы не потерять `thumb` из
                // более полного ответа следующей группы.
                tracks += page
                if (page.size < pageSize) break
                offset += pageSize
            }
        }

        for (type in listOf("main", "featured", "top")) {
            loadType(type)
        }
        // Совместимость на случай, если конкретный сервер не понимает type.
        if (tracks.isEmpty()) loadType(null)

        return tracks.mergeAudioTracksById().map(::cacheTrack).map { it.toEngineTrack() }
    }

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

    suspend fun getLibraryLikes(source: String = "all", limit: Int = 500, offset: Int = 0): LibraryLikesResponse? =
        runCatching {
            val tracks = audioApi.getAudios(
                ownerId = currentUserId(),
                offset = offset,
                count = limit.coerceIn(1, 6000),
            ).requireData().map(::cacheTrack)
            LibraryLikesResponse(
                items = tracks.map { it.toLibraryTrack() },
                count = tracks.size,
                offset = offset,
                limit = limit,
            )
        }.getOrNull()

    suspend fun addTracksToLibrary(trackIds: Collection<String>): Set<String> = runCatching {
        requireInitialized()
        val requests = trackIds.mapNotNull { trackId ->
            val normalized = normalizeTrackId(trackId)
            val parts = normalized.split('_', limit = 3)
            if (parts.size < 2) return@mapNotNull null
            val fullId = parts.take(2).joinToString("_")
            val accessKey = parts.getOrNull(2)?.takeIf(String::isNotBlank)
                ?: trackCache[fullId]?.access_key?.takeIf(String::isNotBlank)
            val requestId = accessKey?.let { "${fullId}_$it" } ?: fullId
            fullId to requestId
        }.distinctBy { it.first }
        audioApi.addBatch(requests.map { it.second }).requireData()
        requests.mapTo(linkedSetOf()) { it.first }
    }.getOrDefault(emptySet())

    suspend fun addTrackToLibrary(trackId: String): Boolean = runCatching {
        val normalized = normalizeTrackId(trackId)
        val parts = normalized.split('_', limit = 3)
        val fullId = parts.take(2).joinToString("_")
        val accessKey = parts.getOrNull(2)?.takeIf(String::isNotBlank)
            ?: trackCache[fullId]?.access_key?.takeIf(String::isNotBlank)
        audioApi.add(fullId, accessKey).requireData()
        true
    }.getOrDefault(false)

    suspend fun likeTrack(trackId: String, liked: Boolean = true): Boolean {
        if (!liked) return unlikeTrack(trackId)
        return addTrackToLibrary(trackId)
    }

    suspend fun unlikeTrack(trackId: String): Boolean = runCatching {
        audioApi.delete(normalizeTrackId(trackId)).requireData()
        true
    }.getOrDefault(false)

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
            ).requireData().map(::cacheTrack)
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
    /**
     * Кредиты трека — то, что VK отдаёт в `audio.getLyrics` рядом с текстом.
     *
     * Поле `credits` восстановлено из адаптеров APK (см. `AudioLyricsContainer`),
     * его формат в доках не зафиксирован: у одних записей это одна строка вида
     * «Автор музыки: … / Автор слов: …», у других пусто. Поэтому НИЧЕГО не
     * парсим по шаблону — отдаём как есть, а разбор на строки делает UI.
     *
     * Возвращает null, когда у трека нет ни текста, ни кредитов: пустой лист
     * лучше выдуманной структуры.
     */
    suspend fun getTrackCredits(trackId: String): String? = runCatching {
        requireInitialized()
        val track = resolveTrack(trackId)
        // credits приходят В ОТВЕТЕ getLyrics, поэтому без lyrics_id спрашивать
        // нечего — VK ответит ошибкой, а не пустым полем.
        if (!track.has_lyrics && track.lyrics_id == null) return@runCatching null
        audioApi.getLyrics(track.fullId).requireData().credits.takeIf { it.isNotBlank() }
    }.getOrNull()

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
            val id = "vk_mix_${activeWaveMix?.playbackMixId ?: "recommendations"}_${System.currentTimeMillis()}"
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
        val filtered = tracks.map(::cacheTrack)
            .filterNot { it.fullId in excluded }
            .take(requested)
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

    /**
     * Official VK 8.185 Track Wave source.
     *
     * `StartPlaySimilarTracksSource` uses `MusicTrack.yb()` as the entity: an
     * `owner_id_audio_id` string without access key. It then calls
     * `audio.getRecommendations(target_audio=..., count=100, is_child=false)`.
     * The response already contains full Audio DTOs, so no `audio.getById`
     * metadata round-trip is needed.
     */
    suspend fun getTrackWaveRecommendations(seedTrackId: String): TrackWaveRecommendations {
        requireInitialized()
        val normalized = normalizeTrackId(seedTrackId)
        val parts = normalized.split('_', limit = 3)
        val entityId = com.lmg.vk.engine.VkAudioIdentity.bareFullId(normalized)
            ?: throw backendFailure(400, "Некорректный VK audio id для волны")
        val ownerId = parts.getOrNull(0).orEmpty()
        val audioId = parts.getOrNull(1).orEmpty()
        val accessKey = parts.getOrNull(2)?.takeIf(String::isNotBlank)
            ?: trackCache[entityId]?.access_key?.takeIf(String::isNotBlank)
        val accessKeyState = if (accessKey == null) "absent" else "present"
        val requestDetails =
            "method=audio.getRecommendations source=similar_track " +
                "entity_id(target_audio)=$entityId ref=none " +
                "ownerId=$ownerId audioId=$audioId accessKey=$accessKeyState"
        com.lmg.vk.debug.DebugLog.add("TRACK_WAVE request $requestDetails")

        val response = when (val result = audioApi.getSimilarTrackRecommendations(entityId)) {
            is VkResult.Success -> result.data
            is VkResult.Error -> {
                com.lmg.vk.debug.DebugLog.add(
                    "TRACK_WAVE A api_error code=${result.code} message=${result.message} $requestDetails",
                )
                throw backendFailure(result.code, result.message)
            }
        }

        if (response.isEmpty()) {
            com.lmg.vk.debug.DebugLog.add(
                "TRACK_WAVE B success_empty returnedIds=0 resolvedTracks=0 $requestDetails",
            )
            return TrackWaveRecommendations(returnedIdsCount = 0, tracks = emptyList())
        }

        val resolved = buildList {
            response.forEach { audio ->
                runCatching { cacheTrack(audio).toEngineTrack() }
                    .onSuccess { track -> if (track.isAvailable) add(track) }
                    .onFailure { error ->
                        com.lmg.vk.debug.DebugLog.add(
                            "TRACK_WAVE metadata_failed id=${audio.fullId} " +
                                "error=${error.message.orEmpty()}",
                        )
                    }
            }
        }
        com.lmg.vk.debug.DebugLog.add(
            "TRACK_WAVE response returnedIds=${response.size} resolvedTracks=${resolved.size}",
        )
        if (resolved.isEmpty()) {
            com.lmg.vk.debug.DebugLog.add(
                "TRACK_WAVE C metadata_to_queue_failed returnedIds=${response.size} resolvedTracks=0",
            )
        }
        return TrackWaveRecommendations(returnedIdsCount = response.size, tracks = resolved)
    }

    /**
     * Official VK 8.185 chooses `track_mix` unless ContextFlags satisfy
     * `(bit 1 && bit 2) || bit 8`; the latter keeps the recommendations route.
     */
    fun resolveTrackWaveMixSession(seedTrackId: String, title: String): VkMixSession? {
        val entityId = com.lmg.vk.engine.VkAudioIdentity.bareFullId(seedTrackId) ?: return null
        val flags = trackCache[entityId]?.flags_context ?: 0
        val useRecommendations =
            ((flags and 1) != 0 && (flags and 2) != 0) || (flags and 8) != 0
        com.lmg.vk.debug.DebugLog.add(
            "TRACK_WAVE official source flags=$flags route=" +
                if (useRecommendations) "recommendations" else "track_mix",
        )
        if (useRecommendations) return null
        return VkMixSession(
            mixId = "track_mix",
            isTunable = false,
            title = title,
            settings = null,
            entityId = entityId,
            sourceRef = "similar_tracks",
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
        val id = streamCacheKey(trackId)
        if (!forceNetwork) trackCache[id]?.let { return it }
        // Третий сегмент `_access_key` обязателен для чужих/ограниченных записей:
        // без него VK на audio.getById возвращает трек, но БЕЗ поля url (либо с
        // плейсхолдером). Ключ приходит в выдаче поиска/каталога и лежит в
        // trackCache, а `fullId` его теряет — поэтому берём его отсюда, как
        // `AudioFile.asIdWithKey()` в VK MP3 Mod.
        val explicitKey = normalizeTrackId(trackId).split('_').getOrNull(2)?.takeIf { it.isNotBlank() }
        val accessKey = explicitKey ?: trackCache[id]?.access_key?.takeIf { it.isNotBlank() }
        val requestId = accessKey?.let { "${id}_$it" } ?: id
        val track = audioApi.getById(listOf(requestId)).requireData().firstOrNull()
            ?: throw backendFailure(404, "Трек $id не найден")
        return cacheTrack(track)
    }

    /**
     * Треки, полученные в обход обычных экранов (аудио друга или сообщества),
     * кладутся в кэш и переводятся в UI-модель — так они играются тем же путём,
     * что и результаты поиска, без повторного `audio.getById`.
     */
    fun adoptTracks(tracks: Collection<AudioTrack>): List<SearchItem> {
        if (tracks.isEmpty()) return emptyList()
        return tracks.map(::cacheTrack).map { it.toSearchItem() }
    }

    /** Full generated audio DTOs embedded in profile status blocks. */
    fun adoptAudioDtos(tracks: Collection<AudioAudioDto>): List<SearchItem> =
        adoptTracks(tracks.map { it.toAudioTrack() })

    private fun cacheTracks(tracks: Collection<AudioTrack>) {
        tracks.forEach(::cacheTrack)
    }

    private fun cacheTrack(track: AudioTrack): AudioTrack {
        // Не даём короткому ответу audio.getById/audio.get затереть цветную
        // обложку, которую раньше прислал каталог или searchMain.
        val enriched = synchronized(trackCache) {
            track.withVkArtworkFallback(trackCache[track.fullId]).also { merged ->
                trackCache[merged.fullId] = merged
            }
        }
        // ВАЖНО: в stream-кэш идёт только реально играбельный URL. Раньше
        // условием было `isNotBlank()`, и плейсхолдер VK
        // (`audio_api_unavailable.mp3`) из выдачи поиска/каталога оседал тут
        // как готовая ссылка. Дальше getTrackInfo отдавал его из кэша, а
        // getTrackInfoSync — тем более (он только читает кэш), так что
        // плеер получал URL, который физически не воспроизводится.
        if (enriched.isAvailable && enriched.url.isPlayableStreamUrl()) {
            streamCache[enriched.fullId] = CachedStream(
                enriched.toStreamInfo(streamQuality),
                System.currentTimeMillis(),
            )
        }
        return enriched
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
        val accountId = currentUserId()
        if (waveAccountId != accountId) {
            resetWaveLocked()
            waveAccountId = accountId
        }
        if (reset) resetWaveLocked()
        ensureWaveSourceLocked(seedTrackId)

        var attempts = 0
        while (waveQueue.size < count && attempts < ((count + 4) / 5 + 3)) {
            attempts++
            val mix = activeWaveMix
            val loaded = if (mix != null) {
                // entity_id — это и есть «микс ВОКРУГ конкретного трека». Раньше
                // при seed микс отключался совсем (activeWaveMix = null), и всё
                // уходило в audio.getRecommendations без привязки к треку: он
                // отдаёт общие рекомендации аккаунта, поэтому «Волна по треку»
                // подбирала что угодно, кроме похожего.
                val result = audioApi.getStreamMixAudios(
                    mixId = mix.playbackMixId,
                    entityId = seedTrackId,
                    append = activeWaveAppend,
                )
                val tracks = when (result) {
                    is VkResult.Success -> result.data.also { activeWaveAppend = true }
                    is VkResult.Error -> {
                        com.lmg.vk.debug.DebugLog.add(
                            "WAVE микс ${mix.playbackMixId} отказал (${result.code}) → рекомендации",
                        )
                        emptyList()
                    }
                }
                // Пусто ИЛИ ошибка — микс исчерпан, дальше идём рекомендациями.
                // Сброс здесь, а не в ветке ошибки: успешный пустой ответ иначе
                // заставил бы цикл долбить тот же микс до конца попыток.
                if (tracks.isEmpty()) activeWaveMix = null
                tracks
            } else {
                audioApi.getRecommendations(
                    targetAudio = seedTrackId,
                    offset = recommendationOffset,
                    count = count.coerceIn(5, 100),
                    userId = currentUserId(),
                ).requireData().also { recommendationOffset += it.size }
            }

            if (loaded.isEmpty()) {
                // Микс мог отдать пусто, а рекомендации ещё не пробовали —
                // тогда продолжаем: activeWaveMix уже сброшен выше.
                if (mix != null) continue
                break
            }
            val enriched = loaded.map(::cacheTrack)
            val queuedIds = waveQueue.asSequence().map(AudioTrack::fullId).toHashSet()
            enriched.filterNot { it.fullId in queuedIds }.forEach(waveQueue::addLast)
        }

        if (currentUserId() != accountId) {
            resetWaveLocked()
            return@withLock emptyList()
        }

        buildList(count.coerceAtMost(waveQueue.size)) {
            repeat(count.coerceAtMost(waveQueue.size)) { add(waveQueue.removeFirst()) }
        }
    }

    /**
     * Выбирает источник волны: настраиваемый микс VK либо рекомендации.
     *
     * ДЛЯ ТРЕКА берём микс с `is_tunable = true` — только такие принимают
     * `entity_id`, то есть умеют строиться вокруг заданной сущности. Обычный
     * микс на `entity_id` ответит своей лентой, и «волна по треку» ничем не
     * отличалась бы от общей.
     *
     * ЗАПРОС ДЕЛАЕТСЯ ОДИН РАЗ на сессию волны. Без флага [waveSourceResolved]
     * получалось так: если подходящего микса нет (или каталог ответил ошибкой),
     * `activeWaveMix` остаётся null, `recommendationOffset` ещё 0 — и условие
     * выхода в начале не срабатывает. А `takeWaveTracks` зовётся на КАЖДОМ
     * переходе трека и на каждом рефилле очереди, то есть тяжёлый
     * `catalog.getAudioAuto()` уходил в сеть снова и снова. Именно это и вешало
     * приложение вместе с телефоном.
     */
    private suspend fun ensureWaveSourceLocked(seedTrackId: String?) {
        if (activeWaveMix != null || recommendationOffset > 0 || waveSourceResolved) return
        waveSourceResolved = true
        val mixes = when (val catalog = catalogApi.getAudioAuto()) {
            is VkResult.Success -> catalog.data.audio_stream_mixes.orEmpty()
            is VkResult.Error -> emptyList()
        }
        activeWaveMix = if (seedTrackId != null) {
            mixes.firstOrNull { it.is_tunable == true }
        } else {
            mixes.firstOrNull()
        }
        com.lmg.vk.debug.DebugLog.add(
            "WAVE источник: " + (activeWaveMix?.let { "микс ${it.playbackMixId} \"${it.title}\"" }
                ?: "рекомендации") + (seedTrackId?.let { " seed=$it" } ?: " (личная)"),
        )
    }

    private fun resetWaveLocked() {
        waveQueue.clear()
        activeWaveMix = null
        activeWaveAppend = false
        // Новая волна — заново выбираем источник (у станции по треку он другой,
        // чем у личной волны).
        waveSourceResolved = false
        recommendationOffset = 0
        waveSessionId = null
    }

    private fun normalizeTrackId(id: String): String = id.removePrefix("vk_")

    /**
     * Ключ stream/track-кэша: всегда `owner_id_audio_id`, без `access_key`.
     *
     * Кэш заполняется по `AudioTrack.fullId` (двухсегментный), а звать резолв
     * могут с ключом доступа третьим сегментом. Без срезания ключа такие id
     * никогда не попадали в кэш: каждый раз промах, поход в сеть, а в
     * синхронном пути — сразу ошибка «нет в кэше» и тишина вместо музыки.
     */
    private fun streamCacheKey(id: String): String {
        val normalized = normalizeTrackId(id)
        val parts = normalized.split('_')
        return if (parts.size >= 2) "${parts[0]}_${parts[1]}" else normalized
    }

    /**
     * Годится ли строка как ссылка на поток.
     *
     * Отдельная проверка нужна потому, что VK на недоступный клиенту трек
     * отвечает УСПЕХОМ и кладёт в `url` служебный плейсхолдер
     * `audio_api_unavailable.mp3` (разобрано в VK MP3 Mod, §2.1 AudioGetLink).
     * Такой URL скачивается, но не содержит музыки, поэтому для плеера он
     * равносилен отсутствию ссылки и должен приводить к честной ошибке.
     */
    private fun String.isPlayableStreamUrl(): Boolean =
        isNotBlank() &&
            !contains("audio_api_unavailable", ignoreCase = true) &&
            (startsWith("http://") || startsWith("https://"))

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

    private fun AudioPhotoDto.toAlbumThumb() = AlbumThumb(
        width = width,
        height = height,
        src = bestUrl.orEmpty(),
        photo_34 = photo_34,
        photo_68 = photo_68,
        photo_135 = photo_135,
        photo_270 = photo_270,
        photo_300 = photo_300,
        photo_600 = photo_600,
        photo_1200 = photo_1200,
        sizes = sizes,
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
                thumb = value.thumb?.toAlbumThumb(),
                main_color = value.main_color,
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
        thumb = thumb?.toAlbumThumb(),
        main_color = main_color,
    )

    /**
     * Строка исполнителя для плейлиста/альбома. У редакторских и пользовательских
     * плейлистов VK `main_artists` пуст — тогда показываем собственный подзаголовок
     * VK, а если и его нет, возвращаем null: пусть UI скроет строку.
     */
    private fun AudioPlaylistDto.playlistArtistLine(): String? =
        main_artist?.takeIf { it.isNotBlank() }
            ?: main_artists?.joinToString(", ") { it.name }?.takeIf { it.isNotBlank() }
            ?: subtitle?.takeIf { it.isNotBlank() }

    private fun AudioPlaylistDto.toSearchItem() = SearchItem(
        id = fullId,
        title = title,
        // joinToString по пустому списку даёт "", а не null: без takeIf строка
        // проходила дальше как «есть исполнитель» и упиралась в заглушку.
        artist = playlistArtistLine(),
        artistName = playlistArtistLine(),
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

    /** См. [AudioPlaylistDto.playlistArtistLine] — та же логика для старой модели. */
    private fun AudioPlaylist.playlistArtistLine(): String? =
        main_artists?.joinToString(", ") { it.name }?.takeIf { it.isNotBlank() }
            ?: subtitle?.takeIf { it.isNotBlank() }

    private fun AudioPlaylist.toSearchItem() = SearchItem(
        id = fullId,
        title = title,
        artist = playlistArtistLine(),
        artistName = playlistArtistLine(),
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
        isAvailable = isAvailable,
    )

    private fun AudioPlaylist.toHomeItem(): HomeItem {
        val albumRelease = isAlbumRelease()
        val cover = photo?.bestUrl?.takeIf(String::isNotBlank)
            ?: thumbs.orEmpty().asSequence()
                .sortedByDescending { it.width * it.height }
                .map { it.bestUrl }
                .firstOrNull { it.isNotBlank() }
        return HomeItem(
            id = fullId,
            title = title,
            artist = main_artists?.joinToString(", ") { it.name }?.takeIf { it.isNotBlank() },
            artistId = main_artists?.firstOrNull()?.id,
            cover = cover,
            collectionId = fullId,
            isExplicit = is_explicit == true,
            source = "vk",
            subtitle = subtitle,
            isAlbum = albumRelease,
            isPlaylist = !albumRelease,
        )
    }

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

    private fun VkCatalogBanner.toHomeItem() = HomeItem(
        id = "catalog_banner_$id",
        title = title?.takeIf(String::isNotBlank)
            ?: text?.takeIf(String::isNotBlank)
            ?: "VK Музыка",
        artist = subtext?.takeIf(String::isNotBlank),
        cover = coverUrl(),
        source = "vk",
        // Баннер — реальная сущность CatalogKit, но не аудиозапись. Не
        // пытаемся передать его в плеер до восстановления click_action.
        isCustom = true,
    )

    private fun VkCatalogVideo.toHomeItem() = HomeItem(
        id = "catalog_video_$fullId",
        title = title.ifBlank { "Клип VK" },
        artist = "VK Клипы",
        cover = coverUrl(),
        source = "vk",
        isCustom = true,
        isClip = true,
    )

    private fun VkCatalogProfile.toHomeItem() = HomeItem(
        id = "curator_$id",
        title = displayName.ifBlank { "VK Музыка" },
        cover = photo_base?.takeIf(String::isNotBlank),
        source = "vk",
        isCustom = true,
        musicOwnerId = id.takeIf { it != 0L },
    )

    private fun VkCatalogProfile.toGroupHomeItem() = toHomeItem().copy(
        musicOwnerId = id.takeIf { it != 0L }?.let { if (it > 0L) -it else it },
    )

    private fun AudioRecommendedPlaylistDto.toHomeItem(): HomeItem? {
        val fullId = fullId ?: return null
        val match = percentage_title?.takeIf(String::isNotBlank)
            ?: percentage?.takeIf { it > 0f }?.let { "Совпадение ${it.toInt()}%" }
            ?: "Рекомендация VK"
        return HomeItem(
            id = "recommended_playlist_$fullId",
            title = match,
            artist = "Рекомендованный плейлист",
            cover = cover?.takeIf(String::isNotBlank) ?: photo?.bestUrl,
            collectionId = fullId,
            source = "vk",
            isPlaylist = true,
        )
    }

    private fun VkCatalogLink.toHomeItem() = HomeItem(
        id = "catalog_link_$id",
        title = title.ifBlank { "VK Музыка" },
        artist = subtitle.takeIf(String::isNotBlank),
        cover = coverUrl(),
        source = "vk",
        isCustom = true,
        catalogUrl = url.takeIf(::isSupportedMusicCatalogUrl),
    )

    private fun isSupportedMusicCatalogUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val path = runCatching { android.net.Uri.parse(url).path.orEmpty() }
            .getOrDefault(url)
            .lowercase()
        return path.contains("/artist/") ||
            path.contains("/music/curator/") ||
            path.contains("/music/album/") ||
            path.contains("/music/playlist/") ||
            path.contains("/audio_playlist") ||
            Regex("""/audios-?\d+""").containsMatchIn(path)
    }

    private fun VkAudioContentCard.toHomeItem() = HomeItem(
        id = "content_card_$fullId",
        title = editor_annotation?.takeIf(String::isNotBlank)
            ?: editor_tag?.takeIf(String::isNotBlank)
            ?: entity_type.takeIf(String::isNotBlank)
            ?: "VK Музыка",
        cover = coverUrl(),
        source = "vk",
        isCustom = true,
    )

    private fun VkCatalogPodcastEntry.toHomeItem() = HomeItem(
        id = "podcast_${aliases.firstOrNull() ?: displayTitle}",
        title = displayTitle,
        artist = artist ?: podcastTitle,
        subtitle = subtitle ?: description,
        cover = bestCatalogImageUrl(thumbs),
        duration = duration.takeIf { it > 0 }?.toLong(),
        source = "vk",
        isCustom = true,
    )

    private fun VkCatalogLongread.toHomeItem() = HomeItem(
        id = "longread_$id",
        title = title?.takeIf(String::isNotBlank) ?: "История VK Музыки",
        artist = ownerName,
        subtitle = subtitle,
        cover = photo?.sizes.orEmpty()
            .asSequence()
            .sortedByDescending { it.width * it.height }
            .mapNotNull { it.src?.takeIf(String::isNotBlank) }
            .firstOrNull(),
        source = "vk",
        isCustom = true,
    )

    private fun VkCatalogAudioBook.toHomeItem() = HomeItem(
        id = "audiobook_$id",
        title = title?.takeIf(String::isNotBlank) ?: "Аудиокнига VK",
        artist = bestCatalogText(authors).orEmpty().takeIf(String::isNotBlank),
        subtitle = publisher?.title,
        cover = bestCatalogImageUrl(cover),
        duration = duration.takeIf { it > 0 }?.toLong(),
        source = "vk",
        isCustom = true,
    )

    private fun VkCatalogAudioBookPerson.toHomeItem() = HomeItem(
        id = "audiobook_person_$id",
        title = name?.takeIf(String::isNotBlank) ?: "Автор аудиокниг",
        subtitle = description,
        cover = bestCatalogImageUrl(photo),
        source = "vk",
        isCustom = true,
    )

    private fun VkCatalogFollowingsUpdateInfo.toHomeItem() = HomeItem(
        id = "following_update_$id",
        title = title?.takeIf(String::isNotBlank) ?: "Новые обновления",
        cover = bestCatalogImageUrl(covers),
        source = "vk",
        isCustom = true,
    )

    /** Moshi's opaque catalog image lists contain maps with url/src/sizes. */
    private fun bestCatalogImageUrl(values: List<Any?>): String? {
        fun find(value: Any?): String? = when (value) {
            is String -> value.takeIf { it.startsWith("http", ignoreCase = true) }
            is Map<*, *> -> {
                listOf("url", "src", "uri").asSequence()
                    .mapNotNull { (value[it] as? String)?.takeIf { url -> url.startsWith("http", true) } }
                    .firstOrNull()
                    ?: value.values.asSequence().mapNotNull(::find).firstOrNull()
            }
            is Iterable<*> -> value.asSequence().mapNotNull(::find).firstOrNull()
            else -> null
        }
        return values.asSequence().mapNotNull(::find).firstOrNull()
    }

    private fun bestCatalogText(values: List<Any?>): String? {
        fun find(value: Any?): String? = when (value) {
            is String -> value.takeIf(String::isNotBlank)
            is Map<*, *> -> listOf("name", "title").asSequence()
                .mapNotNull { value[it] as? String }
                .firstOrNull { it.isNotBlank() }
            is Iterable<*> -> value.asSequence().mapNotNull(::find).firstOrNull()
            else -> null
        }
        return values.asSequence().mapNotNull(::find).firstOrNull()
    }

    private fun RadioStation.toHomeItem() = HomeItem(
        id = "radio_$id",
        title = name,
        cover = logo_png_url?.takeIf(String::isNotBlank) ?: logo_url,
        source = "vk_radio",
        isCustom = true,
        isAvailable = is_enabled != false && !stream_url.isNullOrBlank(),
        radioStreamUrl = stream_url?.takeIf(String::isNotBlank),
        isRadio = true,
    )

    private fun AudioStreamMix.toHomeItem() = HomeItem(
        id = "stream_mix_$id",
        title = title,
        artist = description.takeIf(String::isNotBlank),
        cover = image_url?.takeIf(String::isNotBlank),
        source = "vk",
        isCustom = true,
        streamMixId = playbackMixId,
        streamMixTunable = is_tunable == true,
        streamMixCatalogItemId = id,
        streamMixAnimationUrl = background_animation_url?.takeIf(String::isNotBlank),
        streamMixOptions = settings?.toVkMixSettings()?.selectedOptions().orEmpty(),
    )

    private fun VkCatalogButton.toPlayMixHomeItem(): HomeItem? {
        if ((action?.type ?: type) != "play_vk_mix") return null
        val playbackMixId = mix_id?.takeIf(String::isNotBlank) ?: return null
        val catalogId = id?.takeIf(String::isNotBlank)
        val cover = (images.orEmpty() + foreground_images.orEmpty())
            .asSequence()
            .filter { it.url.isNotBlank() }
            .maxByOrNull { it.width * it.height }
            ?.url
        return HomeItem(
            id = "play_mix_${catalogId ?: playbackMixId}",
            title = title?.takeIf(String::isNotBlank) ?: "VK Mix",
            artist = description?.takeIf(String::isNotBlank),
            cover = cover,
            source = "vk",
            isCustom = true,
            streamMixId = playbackMixId,
            streamMixTunable = !mix_options.isNullOrBlank(),
            streamMixEntityId = entity_id?.takeIf(String::isNotBlank),
            streamMixSectionId = section_id?.takeIf(String::isNotBlank),
            streamMixCatalogItemId = catalogId,
            streamMixOptions = parseMixOptions(mix_options),
            streamMixResolveSettings = true,
        )
    }

    private fun parseMixOptions(raw: String?): Map<String, List<String>> = runCatching {
        val json = raw?.takeIf(String::isNotBlank)?.let { org.json.JSONObject(it) }
            ?: return@runCatching emptyMap()
        buildMap {
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val values = json.optJSONArray(key) ?: continue
                val selected = buildList {
                    for (index in 0 until values.length()) {
                        values.optString(index).takeIf(String::isNotBlank)?.let { add(it) }
                    }
                }
                if (selected.isNotEmpty()) put(key, selected)
            }
        }
    }.getOrDefault(emptyMap())

    /** Exact DTO -> editor model mapping recovered from official VK. */
    private fun AudioStreamMixSettings.toVkMixSettings() = VkMixSettings(
        title = title,
        subtitle = subtitle,
        multiSelect = multi_select ?: false,
        categories = mix_categories.map { category ->
            VkMixCategory(
                id = category.id,
                title = category.title,
                type = VkMixCategoryType.fromWire(category.type),
                options = category.options.map { option ->
                    VkMixOption(
                        id = option.id,
                        title = option.title,
                        icon = option.icon,
                        badgeIconUrl = option.iconBadge,
                        isSelected = option.selected ?: false,
                    )
                },
            )
        },
    )

    /** Official VK applies the CatalogKit `mix_options` JSON over fresh settings. */
    private fun VkMixSettings.withSelectedOptions(
        selected: Map<String, List<String>>,
    ): VkMixSettings {
        if (selected.isEmpty()) return this
        return copy(
            categories = categories.map { category ->
                val categorySelection = selected[category.id] ?: return@map category
                category.copy(
                    options = category.options.map { option ->
                        option.copy(isSelected = option.id in categorySelection)
                    },
                )
            },
        )
    }

    /** VK shows the selected option before the generic and catalog titles. */
    private fun AudioStreamMix.playbackTitle(settings: VkMixSettings?): String =
        settings
            ?.categories
            ?.asSequence()
            ?.flatMap { it.options.asSequence() }
            ?.firstOrNull(VkMixOption::isSelected)
            ?.title
            ?.takeIf(String::isNotBlank)
            ?: titles?.common_state?.takeIf(String::isNotBlank)
            ?: title

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
        discNumber = album_part_number?.takeIf { it > 0 },
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
        // Ключ доступа сохраняем вместе с треком: он приходит в выдаче audio.get
        // и нужен потом при резолве ссылки. Раньше он жил только в trackCache в
        // памяти и терялся при перезапуске приложения.
        accessKey = access_key?.takeIf { it.isNotBlank() },
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
        type = releaseType(),
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
        mainColor = main_color?.takeIf { it.isNotBlank() },
    )

    /**
     * Вид релиза: `album`, `collection`, `ep`, `single`.
     *
     * Берём из вложенного `album`, а НЕ из `AudioPlaylist.type`: у плейлиста
     * верхний `type` — это его сорт («плейлист»/«альбом»), из него нельзя узнать,
     * сингл перед нами или сборник. Именно поэтому сингл и EP подписывались как
     * «Playlist», а фильтр дискографии по `single`/`ep` в ArtistDetailScreen
     * никогда не срабатывал. Верхний `type` остаётся запасным вариантом — на
     * случай выдачи, где вложенного `album` нет вовсе.
     */
    private fun AudioPlaylist.releaseType(): String? =
        album?.type?.takeIf { it.isNotBlank() } ?: type?.takeIf { it.isNotBlank() }

    private fun AudioPlaylist.toArtistAlbum() = ArtistAlbum(
        id = fullId,
        title = title,
        artist = main_artists?.joinToString(", ") { it.name }.orEmpty(),
        artists = main_artists.orEmpty().map { MiniArtist(it.id, it.name) },
        year = year.takeIf { it > 0 }?.toString() ?: create_time.takeIf { it > 0 }?.let { (it / 31536000 + 1970).toString() },
        cover = photo?.bestUrl ?: photo?.src ?: thumbs?.firstOrNull()?.bestUrl ?: thumbs?.firstOrNull()?.src.orEmpty(),
        type = releaseType(),
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

    private fun VkCatalogButton.actionType(): String? = action?.type ?: type

    private fun VkCatalogResponse.blocksForSection(sectionId: String?): List<VkCatalogBlock> {
        if (sectionId.isNullOrBlank()) return allBlocks()
        return buildList {
            block?.let(::add)
            section?.takeIf { it.id == sectionId }?.blocks.orEmpty().let(::addAll)
            catalog?.sections.orEmpty()
                .firstOrNull { it.id == sectionId }
                ?.blocks.orEmpty()
                .let(::addAll)
        }.distinctBy { it.id }
    }

    private fun VkCatalogResponse.sectionNextFrom(sectionId: String?): String? =
        section?.takeIf { sectionId == null || it.id == sectionId }?.next_from
            ?.takeIf(String::isNotBlank)
            ?: catalog?.sections.orEmpty().firstOrNull { it.id == sectionId }?.next_from
                ?.takeIf(String::isNotBlank)

    private fun VkCatalogBlock.subsectionTabs(): List<HomeSubsectionTab> =
        actions.orEmpty()
            .firstOrNull { !it.options.isNullOrEmpty() }
            ?.options.orEmpty()
            .mapNotNull { option ->
                val replacementId = option.replacement_id?.takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                // Таб без подписи нажать невозможно — рисовать его нечестно.
                val title = option.text?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                HomeSubsectionTab(
                    replacementId = replacementId,
                    title = title,
                    icon = option.icon?.takeIf(String::isNotBlank),
                    selected = option.selected == 1,
                )
            }
            .distinctBy(HomeSubsectionTab::replacementId)
    private fun VkCatalogBlock.mergeWith(other: VkCatalogBlock): VkCatalogBlock {
        fun mergeIds(left: List<String>?, right: List<String>?): List<String>? =
            (left.orEmpty() + right.orEmpty()).distinct().takeIf { it.isNotEmpty() }
        return copy(
            data_type = data_type.ifBlank { other.data_type },
            ref = other.ref ?: ref,
            layout = layout ?: other.layout,
            actions = (actions.orEmpty() + other.actions.orEmpty())
                // Раньше здесь стоял distinctBy { section_id }. У кнопок-табов
                // (`subsection_tabs`) section_id всегда null, поэтому ВСЕ они
                // считались одной и той же кнопкой и от блока оставалась ровно
                // одна — вместе с ней терялись options, то есть сами табы.
                .distinct().takeIf { it.isNotEmpty() },
            // The newest page is authoritative, including a null cursor which
            // explicitly closes pagination. Retaining the previous cursor here
            // caused one extra request after the final page.
            next_from = other.next_from,
            audios_ids = mergeIds(audios_ids, other.audios_ids),
            playlists_ids = mergeIds(playlists_ids, other.playlists_ids),
            artists_ids = mergeIds(artists_ids, other.artists_ids),
            artist_videos_ids = mergeIds(artist_videos_ids, other.artist_videos_ids),
            videos_ids = mergeIds(videos_ids, other.videos_ids),
            links_ids = mergeIds(links_ids, other.links_ids),
            catalog_banner_ids = mergeIds(catalog_banner_ids, other.catalog_banner_ids),
            curators_ids = mergeIds(curators_ids, other.curators_ids),
            group_ids = mergeIds(group_ids, other.group_ids),
            audio_content_card_ids = mergeIds(audio_content_card_ids, other.audio_content_card_ids),
            music_owners_ids = mergeIds(music_owners_ids, other.music_owners_ids),
            suggestions_ids = mergeIds(suggestions_ids, other.suggestions_ids),
            text_ids = mergeIds(text_ids, other.text_ids),
            podcast_episodes_ids = mergeIds(podcast_episodes_ids, other.podcast_episodes_ids),
            podcast_slider_items_ids = mergeIds(podcast_slider_items_ids, other.podcast_slider_items_ids),
            podcast_items_ids = mergeIds(podcast_items_ids, other.podcast_items_ids),
            longreads_ids = mergeIds(longreads_ids, other.longreads_ids),
            audio_book_ids = mergeIds(audio_book_ids, other.audio_book_ids),
            audio_books_person_ids = mergeIds(audio_books_person_ids, other.audio_books_person_ids),
            audio_followings_update_info_ids = mergeIds(
                audio_followings_update_info_ids,
                other.audio_followings_update_info_ids,
            ),
            placeholder_ids = mergeIds(placeholder_ids, other.placeholder_ids),
            radio_stations_ids = mergeIds(radio_stations_ids, other.radio_stations_ids),
            audio_stream_mixes_ids = mergeIds(audio_stream_mixes_ids, other.audio_stream_mixes_ids),
            audio_signal_common_info_id = mergeIds(
                audio_signal_common_info_id,
                other.audio_signal_common_info_id,
            ),
        )
    }

    private fun List<VkCatalogResponse>.toHomeBlocks(
        sectionId: String? = null,
    ): List<HomeBlock> {
        val first = firstOrNull() ?: return emptyList()
        val audios = flatMap { it.audios.orEmpty() }
            .mergeAudioTracksById()
            .map(::cacheTrack)
        val playlists = flatMap { it.playlists.orEmpty() }
        val recommended_playlists = flatMap { it.recommended_playlists.orEmpty() }
        val artists = flatMap { it.artists.orEmpty() }
        val artist_videos = flatMap { it.artist_videos.orEmpty() }
        val videos = flatMap { it.videos.orEmpty() }
        val links = flatMap { it.links.orEmpty() }
        val catalog_banners = flatMap { it.catalog_banners.orEmpty() }
        val curators = flatMap { it.curators.orEmpty() }
        val groups = flatMap { it.groups.orEmpty() }
        val music_owners = flatMap { it.music_owners.orEmpty() }
        val podcast_episodes = flatMap { it.podcast_episodes.orEmpty() }
        val podcast_slider_items = flatMap { it.podcast_slider_items.orEmpty() }
        val podcasts = flatMap { it.podcasts.orEmpty() }
        val longreads = flatMap { it.longreads.orEmpty() }
        val audio_books = flatMap { it.audio_books.orEmpty() }
        val audio_books_persons = flatMap { it.audio_books_persons.orEmpty() }
        val following_updates = flatMap { it.audio_followings_update_info.orEmpty() }
        val audio_content_cards = flatMap { it.audio_content_cards.orEmpty() }
        val radio_stations = flatMap { it.radio_stations.orEmpty() }
        val audio_stream_mixes = flatMap { it.audio_stream_mixes.orEmpty() }
        val signal_infos = flatMap { it.audio_signal_common_info.orEmpty() }
        val audiosById = audios.orEmpty().associateBy { it.fullId }
        val playlistsById = playlists.orEmpty().associateBy { it.fullId }
        val recommendedPlaylistsById = recommended_playlists.mapNotNull { recommended ->
            recommended.fullId?.let { it to recommended }
        }.toMap()
        val artistsById = artists.orEmpty().associateBy { it.id }
        val videosById = buildMap {
            (artist_videos + videos).forEach { video ->
                put(video.fullId, video)
                put(video.id.toString(), video)
            }
        }
        val linksById = links.orEmpty().associateBy { it.id }
        val bannersById = catalog_banners.orEmpty().associateBy { it.id.toString() }
        val curatorsById = curators.orEmpty().associateBy { it.id.toString() }
        val groupsById = groups.orEmpty().associateBy { it.id.toString() }
        val ownersById = music_owners.orEmpty().associateBy { it.id.toString() }
        fun <T> indexCatalogEntries(
            entries: List<T>,
            aliases: (T) -> Iterable<String>,
        ): Map<String, T> = buildMap {
            entries.forEach { entry -> aliases(entry).forEach { key -> if (key.isNotBlank()) put(key, entry) } }
        }
        val podcastEntries = podcasts + podcast_episodes + podcast_slider_items
        val podcastsById = indexCatalogEntries(podcastEntries) { it.aliases }
        val longreadsById = longreads.orEmpty().associateBy { it.id.toString() }
        val audioBooksById = audio_books.orEmpty().associateBy { it.id.toString() }
        val audioBookPersonsById = audio_books_persons.orEmpty().associateBy { it.id.toString() }
        val followingUpdatesById = following_updates.orEmpty().associateBy { it.id.toString() }
        val contentCardsById = buildMap {
            audio_content_cards.orEmpty().forEach { card ->
                put(card.fullId, card)
                card.entity_id.takeIf(String::isNotBlank)?.let { put(it, card) }
            }
        }
        val stationsById = radio_stations.orEmpty().associateBy { it.id.toString() }
        val streamMixesById = audio_stream_mixes.orEmpty().associateBy { it.id }
        val signalsById = signal_infos.orEmpty().associateBy { it.id }
        val catalogPages = this
        val sectionIdByBlockId = buildMap {
            catalogPages.forEach { response ->
                response.section?.let { section ->
                    section.blocks.orEmpty().forEach { block ->
                        if (block.id.isNotBlank() && section.id.isNotBlank()) put(block.id, section.id)
                    }
                }
                response.catalog?.sections.orEmpty().forEach { section ->
                    section.blocks.orEmpty().forEach { block ->
                        if (block.id.isNotBlank() && section.id.isNotBlank()) put(block.id, section.id)
                    }
                }
            }
        }

        fun <T> Map<String, T>.byCatalogId(value: String): T? =
            this[value] ?: this[value.removePrefix("vk_")]
        fun Map<String, VkCatalogProfile>.byGroupCatalogId(value: String): VkCatalogProfile? {
            val numeric = value.removePrefix("vk_").removePrefix("-")
            return byCatalogId(value) ?: this[numeric] ?: this["-$numeric"]
        }
        val orderedBlocks = linkedMapOf<String, VkCatalogBlock>()
        flatMap { it.blocksForSection(sectionId) }
            .filter { it.id.isNotBlank() }
            .forEach { block ->
                orderedBlocks[block.id] = orderedBlocks[block.id]?.mergeWith(block) ?: block
            }
        fun HomeItem.catalogDedupeKey(): String = when {
            isStreamMix -> "mix:${streamMixId}:${streamMixEntityId.orEmpty()}"
            isArtist -> "artist:$id"
            isAlbum -> "album:${collectionId ?: id}"
            isPlaylist -> "playlist:${collectionId ?: id}"
            isTrack -> "audio:${trackId ?: id}"
            else -> "card:$id"
        }

        var pendingHeaderTitle: String? = null
        val catalogBlocks = orderedBlocks.values.mapNotNull { block ->
            val layoutName = block.layout?.name.orEmpty()
            if (layoutName == "header" || layoutName == "header_compact" ||
                layoutName == "header_large" || layoutName == "header_extended") {
                pendingHeaderTitle = block.layout?.title?.takeIf(String::isNotBlank)
                    ?: pendingHeaderTitle
                return@mapNotNull null
            }
            val signal = block.audio_signal_common_info_id.orEmpty()
                .mapNotNull(signalsById::byCatalogId)
                .firstOrNull()
            val signalAudioIds = signal?.audios.orEmpty()
            val playBlockAction = block.actions.orEmpty().firstOrNull { action ->
                action.actionType() in setOf(
                    "play_audios_from_block",
                    "play_shuffled_audios_from_block",
                    "play_shuffled_audio_from_block",
                )
            }
            val openSectionAction = block.actions.orEmpty().firstOrNull { action ->
                action.actionType() == "open_section"
            }
            val catalogActions = HomeCatalogActions(
                playBlockId = (playBlockAction?.action?.block_id
                    ?: playBlockAction?.block_id)?.takeIf(String::isNotBlank),
                playRef = (playBlockAction?.action?.consume_reason
                    ?: playBlockAction?.consume_reason)?.takeIf(String::isNotBlank),
                shuffled = playBlockAction?.actionType() in setOf(
                    "play_shuffled_audios_from_block",
                    "play_shuffled_audio_from_block",
                ),
                openSectionId = (openSectionAction?.action?.section_id
                    ?: openSectionAction?.section_id)?.takeIf(String::isNotBlank),
                openSectionTitle = (openSectionAction?.action?.title
                    ?: openSectionAction?.title)?.takeIf(String::isNotBlank),
            )
            val signalInfo = signal?.let { info ->
                HomeSignalInfo(
                    id = info.id,
                    cover = info.cover?.takeIf(String::isNotBlank),
                    title = info.title.orEmpty(),
                    subtitle = info.subtitle.orEmpty(),
                    currentMonth = info.current_month.orEmpty(),
                    audioIds = signalAudioIds,
                    playBlockId = catalogActions.playBlockId,
                    openSectionId = catalogActions.openSectionId,
                    ref = catalogActions.playRef,
                    shuffled = catalogActions.shuffled,
                )
            }
            val items = buildList {
                block.actions.orEmpty().mapNotNull { it.toPlayMixHomeItem() }
                    .forEach(::add)
                block.audios_ids.orEmpty().mapNotNull(audiosById::byCatalogId)
                    .forEach { add(it.toHomeItem()) }
                block.playlists_ids.orEmpty().forEach { id ->
                    playlistsById.byCatalogId(id)?.let { add(it.toHomeItem()) }
                        ?: recommendedPlaylistsById.byCatalogId(id)?.toHomeItem()?.let(::add)
                }
                block.artists_ids.orEmpty().mapNotNull(artistsById::byCatalogId)
                    .forEach { add(it.toHomeItem()) }
                block.artist_videos_ids.orEmpty().mapNotNull(videosById::byCatalogId)
                    .forEach { add(it.toHomeItem()) }
                block.videos_ids.orEmpty().mapNotNull(videosById::byCatalogId)
                    .forEach { add(it.toHomeItem()) }
                block.links_ids.orEmpty().mapNotNull(linksById::byCatalogId)
                    .forEach { add(it.toHomeItem()) }
                block.catalog_banner_ids.orEmpty().mapNotNull(bannersById::byCatalogId)
                    .forEach { add(it.toHomeItem()) }
                block.curators_ids.orEmpty().mapNotNull(curatorsById::byCatalogId)
                    .forEach { add(it.toHomeItem()) }
                block.group_ids.orEmpty().mapNotNull(groupsById::byGroupCatalogId)
                    .forEach { add(it.toGroupHomeItem()) }
                block.music_owners_ids.orEmpty().mapNotNull(ownersById::byCatalogId)
                    .forEach { add(it.toHomeItem()) }
                block.podcast_items_ids.orEmpty().mapNotNull(podcastsById::byCatalogId)
                    .forEach { add(it.toHomeItem()) }
                block.podcast_episodes_ids.orEmpty().mapNotNull(podcastsById::byCatalogId)
                    .forEach { add(it.toHomeItem()) }
                block.podcast_slider_items_ids.orEmpty().mapNotNull(podcastsById::byCatalogId)
                    .forEach { add(it.toHomeItem()) }
                block.longreads_ids.orEmpty().mapNotNull(longreadsById::byCatalogId)
                    .forEach { add(it.toHomeItem()) }
                block.audio_book_ids.orEmpty().mapNotNull(audioBooksById::byCatalogId)
                    .forEach { add(it.toHomeItem()) }
                block.audio_books_person_ids.orEmpty().mapNotNull(audioBookPersonsById::byCatalogId)
                    .forEach { add(it.toHomeItem()) }
                block.audio_followings_update_info_ids.orEmpty()
                    .mapNotNull(followingUpdatesById::byCatalogId)
                    .forEach { add(it.toHomeItem()) }
                block.audio_content_card_ids.orEmpty().mapNotNull(contentCardsById::byCatalogId)
                    .forEach { add(it.toHomeItem()) }
                block.radio_stations_ids.orEmpty().mapNotNull(stationsById::byCatalogId)
                    .forEach { add(it.toHomeItem()) }
                block.audio_stream_mixes_ids.orEmpty().mapNotNull(streamMixesById::byCatalogId)
                    .forEach { add(it.toHomeItem()) }
                signalAudioIds.mapNotNull(audiosById::byCatalogId)
                    .forEach { add(it.toHomeItem()) }
            // Deduplicate only inside this block. VK can intentionally repeat
            // the same release in several thematic blocks; global filtering
            // made later server sections incomplete or entirely empty.
            }.distinctBy { it.catalogDedupeKey() }

            val title = pendingHeaderTitle
                ?: block.layout?.title?.takeIf(String::isNotBlank)
                ?: block.data_type.takeIf(String::isNotBlank)
                ?: "VK Музыка"
            val contentType = block.data_type.takeIf(String::isNotBlank) ?: when {
                block.curators_ids.orEmpty().isNotEmpty() -> "curators"
                block.catalog_banner_ids.orEmpty().isNotEmpty() -> "catalog_banners"
                block.radio_stations_ids.orEmpty().isNotEmpty() -> "radio"
                block.audio_stream_mixes_ids.orEmpty().isNotEmpty() -> "stream_mixes"
                block.audio_signal_common_info_id.orEmpty().isNotEmpty() -> "signal"
                block.audio_content_card_ids.orEmpty().isNotEmpty() -> "content_cards"
                block.longreads_ids.orEmpty().isNotEmpty() -> "longreads"
                block.podcast_items_ids.orEmpty().isNotEmpty() ||
                    block.podcast_episodes_ids.orEmpty().isNotEmpty() ||
                    block.podcast_slider_items_ids.orEmpty().isNotEmpty() -> "podcasts"
                block.audio_book_ids.orEmpty().isNotEmpty() ||
                    block.audio_books_person_ids.orEmpty().isNotEmpty() -> "audiobooks"
                block.audio_followings_update_info_ids.orEmpty().isNotEmpty() -> "following_updates"
                block.artist_videos_ids.orEmpty().isNotEmpty() ||
                    block.videos_ids.orEmpty().isNotEmpty() -> "videos"
                block.artists_ids.orEmpty().isNotEmpty() -> "artists"
                block.playlists_ids.orEmpty().isNotEmpty() -> "playlists"
                block.audios_ids.orEmpty().isNotEmpty() -> "audios"
                else -> ""
            }
            pendingHeaderTitle = null
            // `subsection_tabs` — блок БЕЗ entity-идентификаторов: всё его
            // содержимое это `actions[0].options` (см. HomeSubsectionTab). Общее
            // правило «пустой блок выбрасываем» съедало его целиком, поэтому
            // переключатель подразделов не доходил до UI вообще. Пропускаем такой
            // блок только если и табов в нём не оказалось.
            val tabs = block.subsectionTabs()
            if (layoutName == "subsection_tabs") {
                return@mapNotNull tabs.takeIf { it.isNotEmpty() && block.id.isNotBlank() }
                    ?.let {
                        HomeBlock(
                            id = block.id,
                            title = title,
                            type = contentType,
                            items = items.map { item ->
                                item.copy(
                                    catalogBlockId = block.id,
                                    streamMixSectionId = item.streamMixSectionId
                                        ?: sectionIdByBlockId[block.id],
                                )
                            },
                            layoutName = layoutName,
                            nextFrom = block.next_from?.takeIf(String::isNotBlank),
                            catalogRef = block.ref?.takeIf(String::isNotBlank),
                            subsectionTabs = it,
                            actions = catalogActions,
                        )
                    }
            }
            items.takeIf { (it.isNotEmpty() || signalInfo != null) && block.id.isNotBlank() }
                ?.let {
                    HomeBlock(
                        id = block.id,
                        title = title,
                        type = contentType,
                        items = it.map { item ->
                            item.copy(
                                catalogBlockId = block.id,
                                streamMixSectionId = item.streamMixSectionId
                                    ?: sectionIdByBlockId[block.id],
                            )
                        },
                        layoutName = layoutName,
                        // Курсор блока раньше терялся: HomeBlock его не хранил, из-за
                        // чего шторка «показать все» навсегда оставалась с первой
                        // порцией. Пустую строку не пропускаем — она не курсор.
                        nextFrom = block.next_from?.takeIf(String::isNotBlank),
                        catalogRef = block.ref?.takeIf(String::isNotBlank),
                        subsectionTabs = tabs,
                        signalInfo = signalInfo,
                        actions = catalogActions,
                    )
                }
        }
        if (catalogBlocks.isNotEmpty()) return catalogBlocks

        // Некоторые аккаунты/версии API не присылают ссылки blocks, но оставляют
        // сущности в корне. Это тот же ответ VK, не локальная подмена контента.
        val fallbackPrefix = first.section?.id?.takeIf(String::isNotBlank)
            ?: first.catalog?.default_section?.takeIf(String::isNotBlank)
            ?: "root"
        val fallbackTitle = first.section?.title?.takeIf(String::isNotBlank) ?: "VK Музыка"
        return buildList {
            catalog_banners.orEmpty().map { it.toHomeItem() }.takeIf { it.isNotEmpty() }?.let {
                add(HomeBlock("${fallbackPrefix}_banners", fallbackTitle, "catalog_banners", it))
            }
            playlists.orEmpty().map { it.toHomeItem() }.takeIf { it.isNotEmpty() }?.let {
                add(HomeBlock("${fallbackPrefix}_playlists", fallbackTitle, "playlists", it))
            }
            audios.orEmpty().map { it.toHomeItem() }.takeIf { it.isNotEmpty() }?.let {
                add(HomeBlock("${fallbackPrefix}_audios", fallbackTitle, "audios", it))
            }
            artists.orEmpty().map { it.toHomeItem() }.takeIf { it.isNotEmpty() }?.let {
                add(HomeBlock("${fallbackPrefix}_artists", fallbackTitle, "artists", it))
            }
            (artist_videos + videos).map { it.toHomeItem() }.takeIf { it.isNotEmpty() }?.let {
                add(HomeBlock("${fallbackPrefix}_videos", "Клипы VK", "videos", it))
            }
            curators.orEmpty().map { it.toHomeItem() }.takeIf { it.isNotEmpty() }?.let {
                add(HomeBlock("${fallbackPrefix}_curators", "Собрано редакцией", "curators", it))
            }
            groups.orEmpty().map { it.toHomeItem() }.takeIf { it.isNotEmpty() }?.let {
                add(HomeBlock("${fallbackPrefix}_groups", fallbackTitle, "groups", it))
            }
            links.orEmpty().map { it.toHomeItem() }.takeIf { it.isNotEmpty() }?.let {
                add(HomeBlock("${fallbackPrefix}_links", "Разделы VK Музыки", "links", it))
            }
            audio_content_cards.orEmpty().map { it.toHomeItem() }.takeIf { it.isNotEmpty() }?.let {
                add(HomeBlock("${fallbackPrefix}_content_cards", "Выбор редакции", "content_cards", it))
            }
            radio_stations.orEmpty().map { it.toHomeItem() }.takeIf { it.isNotEmpty() }?.let {
                add(HomeBlock("${fallbackPrefix}_radio", "Радиостанции", "radio", it))
            }
            audio_stream_mixes.orEmpty().map { it.toHomeItem() }.takeIf { it.isNotEmpty() }?.let {
                add(HomeBlock("${fallbackPrefix}_mixes", "Миксы VK", "stream_mixes", it))
            }
            longreads.orEmpty().map { it.toHomeItem() }.takeIf { it.isNotEmpty() }?.let {
                add(HomeBlock("${fallbackPrefix}_longreads", "Истории VK Музыки", "longreads", it))
            }
            podcasts.orEmpty().map { it.toHomeItem() }.takeIf { it.isNotEmpty() }?.let {
                add(HomeBlock("${fallbackPrefix}_podcasts", "Подкасты", "podcasts", it))
            }
            audio_books.orEmpty().map { it.toHomeItem() }.takeIf { it.isNotEmpty() }?.let {
                add(HomeBlock("${fallbackPrefix}_audiobooks", "Аудиокниги", "audiobooks", it))
            }
        }
    }

    private fun VkCatalogBlock.matchesSection(vararg markers: String): Boolean {
        val haystack = listOf(id, data_type, layout?.name, layout?.title, layout?.subtitle)
            .joinToString(" ")
            .lowercase()
        return markers.any { marker -> marker.lowercase() in haystack }
    }

    private const val STREAM_CACHE_TTL_MS = 8L * 60L * 1000L

    /**
     * Предел ожидания для синхронного резолва (`getTrackInfoSync`).
     *
     * Он блокирует загрузочный поток ExoPlayer, поэтому висеть бесконечно не имеет
     * права: при мёртвой сети плеер должен получить ошибку и отдать её наверх, а не
     * замереть. 10 секунд — заметно меньше, чем таймаут самого плеера на открытие
     * источника, так что причину увидит именно наш код.
     */
    private const val SYNC_RESOLVE_TIMEOUT_MS = 10_000L
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

    data class NeedPassword(
        val validationSid: String,
    ) : VkLoginResult

    data class Failure(val message: String) : VkLoginResult
}

data class VkAccountSummary(
    val userId: Long,
    val displayName: String,
    val username: String,
    val avatarUrl: String,
    val isActive: Boolean,
    val isExpired: Boolean,
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
    private val _profileId = MutableStateFlow<Long?>(null)
    val profileId: StateFlow<Long?> = _profileId
    private val _profileDomain = MutableStateFlow<String?>(null)
    val profileDomain: StateFlow<String?> = _profileDomain
    private val _isProfileRefreshing = MutableStateFlow(false)
    val isProfileRefreshing: StateFlow<Boolean> = _isProfileRefreshing
    private val _profileSessionExpiresAt = MutableStateFlow<Long?>(null)
    /** Expiry of the VK session in epoch seconds; never exposes token material. */
    val profileSessionExpiresAt: StateFlow<Long?> = _profileSessionExpiresAt
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
    private val _accounts = MutableStateFlow<List<VkAccountSummary>>(emptyList())
    /** Saved account metadata only. Token material never leaves the encrypted store. */
    val accounts: StateFlow<List<VkAccountSummary>> = _accounts

    private val authScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sessionStore: VkSessionStore? = null
    private var apiClient: VkApiClient? = null
    private var methods: VkMethodsRegistry? = null
    private val anonymousTokenMutex = Mutex()
    private val signInMutex = Mutex()
    private var anonymousToken: String = ""
    private var anonymousTokenExpiresAt: Long = 0L
    private var activeAuthAttempt: AuthAttempt? = null
    private var activeContentAccountId = Long.MIN_VALUE

    private data class AuthAttempt(
        val username: String,
        var anonymousToken: String,
        var sid: String,
        var verificationMethod: AuthVerificationMethod = AuthVerificationMethod.PASSWORD,
        var grantType: String = "password",
        var canSkipPassword: Boolean = false,
        var captchaSid: String? = null,
        var captchaTs: Double? = null,
        var captchaAttempt: Int? = null,
        var legacyTokenValidation: Boolean = false,
        var oauthCode: String? = null,
        var awaitingPassword: Boolean = false,
    )

    internal fun init(client: VkApiClient, store: VkSessionStore) {
        apiClient = client
        methods = VkMethodsRegistry(client)
        sessionStore = store
        applySession(store.session)
    }

    suspend fun signIn(
        username: String,
        password: String = "",
        validationSid: String? = null,
        code: String? = null,
        captchaSid: String? = null,
        captchaKey: String? = null,
    ): VkLoginResult {
        if (username.isBlank()) {
            return VkLoginResult.Failure("Введите номер телефона или логин")
        }

        val registry = methods ?: return VkLoginResult.Failure("VK API is not initialized")
        return signInMutex.withLock {
            val normalizedUsername = username.trim()
            val isCaptchaContinuation = !captchaKey.isNullOrBlank() && !captchaSid.isNullOrBlank()
            val currentAttempt = activeAuthAttempt
            val isPasswordContinuation = !isCaptchaContinuation &&
                !validationSid.isNullOrBlank() &&
                password.isNotBlank() &&
                currentAttempt?.awaitingPassword == true
            val isOtpContinuation = !isCaptchaContinuation && !isPasswordContinuation &&
                !code.isNullOrBlank() && !validationSid.isNullOrBlank()

            if (!isOtpContinuation && !isCaptchaContinuation && !isPasswordContinuation) {
                activeAuthAttempt = null
                startAuthAttempt(registry, normalizedUsername, password)?.let { return@withLock it }
            }

            val attempt = activeAuthAttempt
                ?: return@withLock VkLoginResult.Failure("VK authorization session expired. Start again.")
            if (attempt.username != normalizedUsername) {
                activeAuthAttempt = null
                return@withLock VkLoginResult.Failure("The login changed. Start authorization again.")
            }
            if ((isOtpContinuation || isPasswordContinuation) && validationSid != attempt.sid) {
                return@withLock VkLoginResult.Failure("VK verification session changed. Start again.")
            }

            if (isOtpContinuation) {
                if (attempt.legacyTokenValidation) {
                    attempt.oauthCode = requireNotNull(code)
                } else {
                    when (val token = getAnonymousToken(registry)) {
                        is VkResult.Error -> return@withLock token.asLoginFailure(
                            "VK anonymous authorization session expired",
                        )
                        is VkResult.Success -> attempt.anonymousToken = token.data
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
                            attempt.awaitingPassword = !attempt.canSkipPassword
                            attempt.grantType = if (attempt.canSkipPassword) {
                                "without_password"
                            } else {
                                "phone_confirmation_sid"
                            }
                            if (!attempt.canSkipPassword && password.isBlank()) {
                                return@withLock VkLoginResult.NeedPassword(attempt.sid)
                            }
                        }
                    }
                }
            }

            val captchaParams = if (isCaptchaContinuation) {
                if (captchaSid != attempt.captchaSid) {
                    return@withLock VkLoginResult.Failure("VK captcha session changed. Start again.")
                }
                buildMap {
                    put("captcha_sid", requireNotNull(captchaSid))
                    put("captcha_key", requireNotNull(captchaKey))
                    attempt.captchaTs?.let { put("captcha_ts", it.toString()) }
                    attempt.captchaAttempt?.let { put("captcha_attempt", it.toString()) }
                }
            } else {
                emptyMap()
            }

            requestOAuthToken(registry, attempt, password, captchaParams)
        }
    }

    private suspend fun startAuthAttempt(
        registry: VkMethodsRegistry,
        username: String,
        password: String = "",
    ): VkLoginResult? {
        val currentAnonymousToken = when (val result = getAnonymousToken(registry)) {
            is VkResult.Success -> result.data
            is VkResult.Error -> return result.asLoginFailure(
                "VK did not issue an anonymous authorization token",
            )
        }
        val validation = when (val result = registry.validateAccount(
            login = username,
            anonymousToken = currentAnonymousToken,
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

        val method = validation.nextStep?.verificationMethod ?: AuthVerificationMethod.PASSWORD
        val attempt = AuthAttempt(
            username = username,
            anonymousToken = currentAnonymousToken,
            sid = sid,
            verificationMethod = method,
            awaitingPassword = method == AuthVerificationMethod.PASSWORD,
        )
        activeAuthAttempt = attempt

        if (method != AuthVerificationMethod.PASSWORD) {
            return prepareTwoFactor(registry, attempt)
        }
        if (password.isBlank()) {
            return VkLoginResult.NeedPassword(attempt.sid)
        }
        return null
    }

    private suspend fun prepareTwoFactor(
        registry: VkMethodsRegistry,
        attempt: AuthAttempt,
    ): VkLoginResult {
        var destination = attempt.verificationMethod.destination
        var codeLength = 0
        val otpKind = when (attempt.verificationMethod) {
            AuthVerificationMethod.SMS -> VkMethodsRegistry.OtpKind.Sms
            AuthVerificationMethod.CALLRESET -> VkMethodsRegistry.OtpKind.CallReset
            AuthVerificationMethod.EMAIL -> VkMethodsRegistry.OtpKind.Email
            AuthVerificationMethod.PUSH -> VkMethodsRegistry.OtpKind.Push
            else -> null
        }
        if (otpKind != null) {
            when (val token = getAnonymousToken(registry)) {
                is VkResult.Error -> return token.asLoginFailure(
                    "VK anonymous authorization session expired",
                )
                is VkResult.Success -> attempt.anonymousToken = token.data
            }
            when (val sent = registry.ecosystemSendOtp(otpKind, attempt.sid, attempt.anonymousToken)) {
                is VkResult.Success -> {
                    destination = sent.data.info.ifBlank { destination }
                    codeLength = sent.data.codeLength.coerceAtLeast(0)
                }
                is VkResult.Error -> {
                    return sent.asLoginFailure("VK did not send the verification code")
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
    ): VkLoginResult {
        when (val token = getAnonymousToken(registry)) {
            is VkResult.Error -> return token.asLoginFailure(
                "VK anonymous authorization session expired",
            )
            is VkResult.Success -> attempt.anonymousToken = token.data
        }
        return when (val result = registry.oauthToken(
            username = attempt.username,
            password = if (attempt.canSkipPassword) "" else password,
            sid = attempt.sid,
            anonymousToken = attempt.anonymousToken,
            grantType = attempt.grantType,
            code = attempt.oauthCode,
            extraParams = captchaParams,
        )) {
            is VkResult.Error -> result.asLoginFailure("VK authorization failed")
            is VkResult.Success -> when (val response = result.data) {
                is RequestTokenResponse.Success -> finishSignIn(registry, response)
                is RequestTokenResponse.CaptchaRequired -> {
                    if (response.redirectUri.isNotBlank()) {
                        continueAfterSmartCaptcha(
                            registry = registry,
                            attempt = attempt,
                            password = password,
                            captchaParams = captchaParams,
                            redirectUri = response.redirectUri,
                        )
                    } else if (response.captchaSid.isNotBlank() && response.captchaImg.isNotBlank()) {
                        attempt.captchaSid = response.captchaSid
                        attempt.captchaTs = response.captchaTs
                        attempt.captchaAttempt = response.captchaAttempt
                        VkLoginResult.Captcha(response.captchaSid, response.captchaImg)
                    } else {
                        VkLoginResult.Failure("VK returned an incomplete captcha challenge")
                    }
                }
                is RequestTokenResponse.TwoFactorRequired -> {
                    if (response.validationSid.isBlank()) {
                        VkLoginResult.Failure("VK returned an empty verification session")
                    } else {
                        attempt.sid = response.validationSid
                        attempt.legacyTokenValidation = true
                        attempt.awaitingPassword = false
                        VkLoginResult.TwoFactor(
                            validationSid = response.validationSid,
                            destination = response.legacyDestination,
                            codeLength = response.codeLength.coerceAtLeast(1),
                        )
                    }
                }
                is RequestTokenResponse.ClientError -> {
                    com.lmg.vk.debug.DebugLog.add(
                        "VK OAuth token rejected: error=${response.error.ifBlank { "none" }}, " +
                            "type=${response.errorType.ifBlank { "none" }}, grant=${attempt.grantType}",
                    )
                    VkLoginResult.Failure(
                        response.errorDescription.ifBlank {
                            response.error.ifBlank { "VK authorization failed" }
                        },
                    )
                }
                is RequestTokenResponse.NestedApiError -> {
                    val error = response.error
                    when {
                        error.error_code in setOf(14, 17) && !error.redirectUri.isNullOrBlank() -> {
                            continueAfterSmartCaptcha(
                                registry = registry,
                                attempt = attempt,
                                password = password,
                                captchaParams = captchaParams,
                                redirectUri = error.redirectUri,
                            )
                        }
                        error.error_code == 14 &&
                            !error.captchaSid.isNullOrBlank() &&
                            !error.captchaImg.isNullOrBlank() -> {
                            attempt.captchaSid = error.captchaSid
                            attempt.captchaTs = error.captchaTs
                            attempt.captchaAttempt = error.captchaAttempt
                            VkLoginResult.Captcha(error.captchaSid, error.captchaImg)
                        }
                        else -> VkLoginResult.Failure(
                            error.error_msg.ifBlank { "VK error ${error.error_code}" },
                        )
                    }
                }
                is RequestTokenResponse.UnknownError -> VkLoginResult.Failure(
                    response.errorDescription.ifBlank {
                        response.error.ifBlank { "Unknown VK authorization response" }
                    },
                )
            }
        }
    }

    private suspend fun continueAfterSmartCaptcha(
        registry: VkMethodsRegistry,
        attempt: AuthAttempt,
        password: String,
        captchaParams: Map<String, String>,
        redirectUri: String,
    ): VkLoginResult {
        val proof = GlobalCaptchaManager.requestValidation(redirectUri)
            ?: return VkLoginResult.Failure("Проверка безопасности VK отменена")
        return requestOAuthToken(
            registry = registry,
            attempt = attempt,
            password = password,
            captchaParams = captchaParams + proof,
        )
    }

    private suspend fun getAnonymousToken(registry: VkMethodsRegistry): VkResult<String> =
        anonymousTokenMutex.withLock {
            val nowSeconds = System.currentTimeMillis() / 1_000
            if (anonymousToken.isNotBlank() && nowSeconds < anonymousTokenExpiresAt) {
                return@withLock VkResult.Success(anonymousToken)
            }
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
        if (!canChangeAccount()) {
            return VkLoginResult.Failure("Wait for library synchronization to finish")
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
            ),
        )
        when (val result = registry.getUserExchangeTokens(response.accessToken)) {
            is VkResult.Error -> Unit
            is VkResult.Success -> {
                val exchangeToken = result.data.usersExchangeTokens.orEmpty()
                    .firstOrNull { it.userId == response.userId }
                    ?.commonToken
                    .orEmpty()
                val store = sessionStore
                if (exchangeToken.isNotBlank() && store?.session?.accessToken == response.accessToken) {
                    store.session = store.session.copy(exchangeToken = exchangeToken)
                }
            }
        }
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

    private val RequestTokenResponse.TwoFactorRequired.legacyDestination: String
        get() = when (validationType) {
            AuthValidationType.Sms, AuthValidationType.CallReset -> phoneMask
            AuthValidationType.Email -> maskedEmail
            AuthValidationType.Push -> deviceName
            AuthValidationType.App -> "приложение для кодов"
            AuthValidationType.LibVerify -> "проверка VK"
            AuthValidationType.ReserveCode -> "резервные коды"
        }

    private fun VkResult.Error.asLoginFailure(fallback: String): VkLoginResult.Failure {
        val detail = message.trim()
        return VkLoginResult.Failure(if (detail.isBlank()) "$fallback [$code]" else "[$code] $detail")
    }

    fun installSession(session: VkAuthSession) {
        val store = checkNotNull(sessionStore) { "MusicAuth is not initialized" }
        store.session = session
        applySession(session)
        authScope.launch {
            runCatching { fetchUserData() }
            if (session.userId != 0L) {
                runCatching { VkProfileRepository.refresh(session.userId) }
            }
        }
    }

    private fun applySession(session: VkAuthSession) {
        val accountChanged = activeContentAccountId != Long.MIN_VALUE &&
            activeContentAccountId != session.userId
        if (accountChanged) {
            clearAccountScopedState()
            _isProfileRefreshing.value = false
            _userEmail.value = null
            _subscription.value = null
            _premiumExpiresAt.value = null
            _telegramId.value = null
        }
        activeContentAccountId = session.userId
        PlaylistManager.activateAccount(session.userId)
        PlaylistSyncManager.activateAccount(session.userId)
        AccountSyncManager.activateAccount(session.userId)
        FavoriteTrackDatabase.activateAccount(session.userId)
        AppDatabase.activateAccount(session.userId)
        WaveRepository.activateAccount(session.userId)
        PlayerController.activateAccount(session.userId)
        _isLoggedIn.value = session.accessToken.isNotBlank()
        _partnerUserId.value = session.userId.takeIf { it != 0L }
        val displayName = listOf(session.firstName, session.lastName)
            .filter(String::isNotBlank)
            .joinToString(" ")
            .ifBlank { session.username }
        _profileName.value = displayName.takeIf(String::isNotBlank)
        _avatarUrl.value = session.avatar.takeIf(String::isNotBlank)
        _profileId.value = session.userId.takeIf { it != 0L }
        _profileDomain.value = session.username.takeIf(String::isNotBlank)
        _profileSessionExpiresAt.value = session.expiresAt.takeIf { it > 0L }
        if (session.accessToken.isBlank()) {
            _userEmail.value = null
            _subscription.value = null
            _premiumExpiresAt.value = null
        }
        updateAccountSummaries()
    }

    fun switchAccount(userId: Long): Boolean {
        if (!canChangeAccount()) return false
        val store = sessionStore as? VkMultiSessionStore ?: return false
        if (store.session.userId == userId) return true
        val session = store.activate(userId) ?: return false
        activeAuthAttempt = null
        applySession(session)
        authScope.launch {
            runCatching { fetchUserData() }
            runCatching { VkProfileRepository.refresh(userId) }
        }
        return true
    }

    fun removeAccount(userId: Long): Boolean {
        val store = sessionStore as? VkMultiSessionStore ?: return false
        val wasActive = store.session.userId == userId
        if (wasActive && !canChangeAccount()) return false
        val remaining = store.remove(userId)
        if (wasActive) {
            activeAuthAttempt = null
            applySession(remaining)
            if (remaining.userId != 0L) {
                authScope.launch {
                    runCatching { fetchUserData() }
                    runCatching { VkProfileRepository.refresh(remaining.userId) }
                }
            }
        } else {
            updateAccountSummaries()
        }
        return true
    }

    private fun updateAccountSummaries() {
        val store = sessionStore
        val activeId = store?.session?.userId ?: 0L
        val sessions = (store as? VkMultiSessionStore)?.sessions
            ?: listOfNotNull(store?.session?.takeIf { it.accessToken.isNotBlank() })
        _accounts.value = sessions
            .filter { it.userId != 0L && it.accessToken.isNotBlank() }
            .sortedByDescending { it.userId == activeId }
            .map { account ->
                VkAccountSummary(
                    userId = account.userId,
                    displayName = listOf(account.firstName, account.lastName)
                        .filter(String::isNotBlank)
                        .joinToString(" ")
                        .ifBlank { account.username.ifBlank { "VK ID ${account.userId}" } },
                    username = account.username,
                    avatarUrl = account.avatar,
                    isActive = account.userId == activeId,
                    isExpired = account.isExpired,
                )
            }
    }

    private fun clearAccountScopedState() {
        MusicBackend.clearVkMixPromptEvents()
        MusicBackend.clearSearchCache()
        VkProfileRepository.clear()
    }

    private fun canChangeAccount(): Boolean =
        !LibraryRepository.isCloudSyncInProgress &&
            !PlaylistSyncManager.state.value.isSyncing &&
            !AccountSyncManager.state.value.isSyncing

    suspend fun fetchUserData(): Boolean {
        val store = sessionStore ?: return false
        val initialSession = store.session
        if (initialSession.accessToken.isBlank()) return false
        val registry = methods ?: return false
        _isProfileRefreshing.value = true
        return try {
            val profile = when (val result = registry.usersGetCurrent()) {
                is VkResult.Success -> result.data.firstOrNull()
                is VkResult.Error -> null
            } ?: return false

            if (store.session.userId != initialSession.userId) return false
            VkProfileRepository.seedProfile(profile)
            val updated = initialSession.copy(
                userId = profile.id.takeIf { it != 0L } ?: initialSession.userId,
                username = profile.domain.ifBlank { initialSession.username },
                firstName = profile.firstName.ifBlank { profile.displayName },
                lastName = profile.lastName,
                avatar = profile.bestPhotoUrl,
            )
            store.session = updated
            applySession(updated)
            true
        } finally {
            _isProfileRefreshing.value = false
        }
    }
    fun getEffectiveQuality(requested: String, premium: Boolean): String = if (premium) requested else "128K"
    suspend fun reissueSessionToken() {
        val client = apiClient ?: return
        if (client.refreshToken()) {
            sessionStore?.session?.let(::applySession)
            fetchUserData()
        }
    }
    fun logout(): Boolean {
        if (!canChangeAccount()) return false
        val store = sessionStore
        val currentUserId = store?.session?.userId ?: 0L
        val next = if (store is VkMultiSessionStore && currentUserId != 0L) {
            store.remove(currentUserId)
        } else {
            store?.session = VkAuthSession.EMPTY
            VkAuthSession.EMPTY
        }
        applySession(next)
        anonymousToken = ""
        anonymousTokenExpiresAt = 0L
        activeAuthAttempt = null
        if (next.userId != 0L) {
            authScope.launch {
                runCatching { fetchUserData() }
                runCatching { VkProfileRepository.refresh(next.userId) }
            }
        }
        return true
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
