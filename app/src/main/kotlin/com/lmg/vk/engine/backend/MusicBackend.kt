package com.lmg.vk.engine.backend

import com.lmg.vk.engine.Track
import com.lmg.vk.engine.backend.wave.WaveBatchResponse
import com.lmg.vk.engine.backend.wave.WaveSessionStartResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

    fun getInstance(): MusicBackend = this
    fun getLastErrorCode(): String? = null
    fun getLastHttpCode(): Int = 200

    // ---------- стрим ----------
    suspend fun getTrackInfo(trackId: String, quality: String = "lossless", region: String? = null): StreamInfo =
        TODO("vk-wire: resolveStream (AudioTrack.url)")

    fun getTrackInfoSync(trackId: String, quality: String = "lossless"): StreamInfo =
        TODO("vk-wire: sync resolve из кэша")

    suspend fun getStreamUrl(trackId: String, source: String? = null): String? =
        TODO("vk-wire")

    suspend fun getTrackMeta(trackId: String): TrackMeta? = TODO("vk-wire")

    suspend fun getBatchTrackMeta(ids: List<String>): Result<BatchTrackMetaResponse> = TODO("vk-wire")

    // ---------- клипы (Apple Music video) ----------
    suspend fun searchClips(query: String): Result<ClipSearchResponse> = TODO("vk-wire")
    suspend fun resolveClipStreamUrl(clipId: String): Result<String> = TODO("vk-wire")

    // ---------- поиск ----------
    suspend fun searchTracks(query: String, source: String = "vk", limit: Int = 30, region: String? = null): List<SearchItem> =
        TODO("vk-wire: VkAudioApi.searchAudios")

    suspend fun searchAll(query: String, region: String? = null, source: String = SearchSource.ALL, limit: Int = 30): SearchResponse? =
        TODO("vk-wire")

    fun clearSearchCache() {}

    // ---------- home / charts ----------
    suspend fun loadHomeContent(region: String? = null): HomeResponse = TODO("vk-wire")
    suspend fun loadCharts(region: String? = null): List<Chart> = TODO("vk-wire")

    // ---------- альбом/артист ----------
    suspend fun getAlbum(albumId: String): AlbumResponse? = TODO("vk-wire")
    suspend fun getArtist(artistId: String): ArtistResponse? = TODO("vk-wire")
    suspend fun getArtistTopTracks(artistId: String): List<Track> = TODO("vk-wire")

    // ---------- лайки / библиотека ----------
    suspend fun getLibraryLikes(source: String = "all", limit: Int = 500, offset: Int = 0): LibraryLikesResponse? = TODO("vk-wire")
    suspend fun likeTrack(trackId: String, liked: Boolean = true): Boolean = TODO("vk-wire: audio.add")
    suspend fun unlikeTrack(trackId: String): Boolean = TODO("vk-wire: audio.delete")

    // ---------- плейлисты пользователя ----------
    suspend fun getUserPlaylists(limit: Int = 100): UserPlaylistsResponse = TODO("vk-wire")
    suspend fun getUserPlaylistTracks(playlistId: String, limit: Int = 200, offset: Int = 0): PlaylistTracksResponse? =
        TODO("vk-wire: VkAudioApi.getPlaylistById")
    suspend fun deleteUserPlaylist(playlistId: String): Boolean = TODO("vk-wire: audio.deletePlaylist")
    suspend fun previewPlaylist(source: String, url: String): PlaylistPreviewResponse? = TODO("vk-wire")
    suspend fun importPlaylist(source: String, url: String, name: String?): PlaylistImportResponse? = TODO("vk-wire")
    suspend fun getImportJobStatus(jobId: String): PlaylistImportJobResponse? = TODO("vk-wire")

    // ---------- тексты ----------
    suspend fun getLyricsResult(trackId: String): Result<com.lmg.vk.engine.LyricsParser.Lyrics?> =
        TODO("vk-wire: audio.getLyrics по lyrics_id трека VK")

    // ---------- волна / радио ----------
    suspend fun startWave(seedTrackId: String? = null): Result<WaveResponse> = TODO("vk-wire")
    suspend fun startSession(
        source: String? = null,
        region: String? = null,
        diversity: Double? = null
    ): Result<WaveSessionStartResponse> = TODO("vk-wire")
    suspend fun nextBatch(
        limit: Int? = null,
        diversity: Double? = null,
        excludeTrackIds: List<String> = emptyList(),
        excludeArtistIds: List<String> = emptyList(),
        playedTrackIds: List<String> = emptyList()
    ): Result<WaveBatchResponse> = TODO("vk-wire")
    suspend fun nextSessionBatch(
        sessionId: String,
        limit: Int? = null,
        diversity: Double? = null,
        excludeTrackIds: List<String> = emptyList(),
        excludeArtistIds: List<String> = emptyList(),
        playedTrackIds: List<String> = emptyList()
    ): Result<WaveBatchResponse> = TODO("vk-wire")
    suspend fun genreBatch(
        genre: String,
        limit: Int? = null,
        diversity: Double? = null,
        source: String? = null,
        region: String? = null,
        excludeTrackIds: List<String> = emptyList(),
        excludeArtistIds: List<String> = emptyList(),
        playedTrackIds: List<String> = emptyList()
    ): Result<WaveBatchResponse> = TODO("vk-wire")
    suspend fun moodBatch(
        mood: String,
        limit: Int? = null,
        diversity: Double? = null,
        source: String? = null,
        region: String? = null,
        excludeTrackIds: List<String> = emptyList(),
        excludeArtistIds: List<String> = emptyList(),
        playedTrackIds: List<String> = emptyList()
    ): Result<WaveBatchResponse> = TODO("vk-wire")
    suspend fun nextTrackStation(
        seedTrackId: String?,
        exclude: Collection<String>? = null
    ): Result<WaveResponse> = TODO("vk-wire: радио по треку")
    suspend fun getWaveNext(seedTrackId: String?, exclude: List<String>? = null): Result<WaveResponse> =
        TODO("vk-wire")
    suspend fun getWaveOnboarding(): List<WaveOnboardingArtist> = TODO("vk-wire")
    suspend fun saveWaveOnboarding(payload: List<WaveOnboardingArtistSave>): Boolean = TODO("vk-wire")
    suspend fun getWavePopularArtists(): List<WaveOnboardingArtist> = TODO("vk-wire")
    suspend fun resetWave(): Boolean = false

    fun isAppleSeedTrackId(id: String): Boolean = id.matches(Regex("-?\\d+_\\d+"))

    fun getUserRegion(): RegionResponse? = null

    suspend fun updateUserRegion(code: String): RegionResponse? = TODO("vk-wire")

    var region: String = "ru"

    suspend fun getLibrarySubscriptions(limit: Int = 50): LibrarySubscriptionsResponse? = TODO("vk-wire")

    suspend fun updateUserPreferences(prefs: UserPreferences): UserPreferences? = TODO("vk-wire")
    suspend fun getUserPreferences(): UserPreferences? = TODO("vk-wire")

    var streamQuality: String = "256K"
}

/** Ответ подписок на артистов (для профиля). */
data class LibrarySubscriptionsResponse(
    val items: List<LibraryArtist> = emptyList()
)

/** Ответ облачных лайков /library/likes. */
data class LibraryLikesResponse(
    val items: List<LibraryTrack> = emptyList(),
    val source: String = "all"
)

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

    suspend fun fetchUserData() { /* TODO(vk-wire: users.get) */ }
    fun getEffectiveQuality(requested: String, premium: Boolean): String = if (premium) requested else "128K"
    fun reissueSessionToken() { /* TODO(vk-wire) */ }
    fun logout() { _isLoggedIn.value = false }
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
