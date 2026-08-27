package com.lmg.vk.engine

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import androidx.glance.appwidget.updateAll
import com.lmg.vk.R
import com.lmg.vk.debug.DebugLog
import com.lmg.vk.engine.backend.BackendException
import com.lmg.vk.engine.backend.MusicBackend
import com.lmg.vk.engine.backend.StreamInfo
import com.lmg.vk.engine.backend.VkAutoflowSource
import com.lmg.vk.data.local.WaveRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 * Playback source identity, kept separately from [PlaybackQueueConfig] as in
 * the official VK player. Regular music sources are finite; VK Mix and an
 * explicitly configured Wave source may request their own next batch.
 */
sealed class PlaybackContext {
    object Downloads : PlaybackContext()
    data class Catalog(val blockId: String) : PlaybackContext()
    data class Playlist(val id: String) : PlaybackContext()
    data class Album(val id: String) : PlaybackContext()
    data class Artist(val id: String) : PlaybackContext()
    data class OwnerAudio(val ownerId: Long) : PlaybackContext()
    data class VkMix(val session: VkMixSession) : PlaybackContext() {
        val mixId: String get() = session.mixId
        val entityId: String? get() = session.entityId
    }
    object Global : PlaybackContext()
}

private fun PlaybackContext.playbackRef(): String = when (this) {
    PlaybackContext.Downloads -> "downloads"
    is PlaybackContext.Catalog -> "catalog"
    is PlaybackContext.Playlist -> "playlist"
    is PlaybackContext.Album -> "album"
    is PlaybackContext.Artist -> "artist"
    is PlaybackContext.OwnerAudio -> "user_profile"
    is PlaybackContext.VkMix -> session.sourceRef
    PlaybackContext.Global -> "other"
}

enum class PlaybackBackend {
    EXO_STREAMING,
    JUCE_LOCAL
}

/**
 * PlayerController — единая точка управления воспроизведением.
 */
object PlayerController {

    // Любое необработанное исключение в корутине воспроизведения раньше валило
    // всё приложение (у scope только SupervisorJob, без обработчика). Теперь —
    // логируем, снимаем индикатор загрузки и живём дальше: тап по треку,
    // который не смог стартовать, больше не крашит приложение.
    private val crashGuard = CoroutineExceptionHandler { _, e ->
        android.util.Log.e("VOIDPIXEL_MEDIA", "Unhandled playback coroutine error", e)
        _isBuffering.value = false
    }

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + crashGuard)
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob() + crashGuard)

    private var appContext: Context? = null
    val context: Context? get() = appContext
    private var controller: MediaController? = null
    private var isConnectingController = false
    // Не вечный флаг, а бэкофф (P1, аудит): один 6-сек таймаут коннекта на
    // холодном старте НАВСЕГДА выключал MediaController — play/skip/seek были
    // мертвы до перезапуска процесса. Теперь повторная попытка через 30с.
    @Volatile private var mediaControllerRetryAfterMs = 0L

    // ── Queue ──
    // Media3 creates one MediaSource/period per MediaItem and compares the whole
    // concatenated timeline from its playback thread (including the preload
    // path). Feeding a fully paginated library here can therefore exhaust the
    // 384 MB app heap even though only one track is decoded at a time.
    //
    // Keep the *player* queue deliberately bounded. A tap on an item outside
    // this window builds a fresh window around that item, while an endless
    // source discards an already-played prefix before appending its next page.
    internal const val MAX_PLAYER_QUEUE_ITEMS = 500
    private const val RETAIN_PLAYED_QUEUE_ITEMS = 50

    private data class BoundedQueue(
        val tracks: List<Track>,
        val startIndex: Int,
        val droppedBefore: Int,
    )

    private fun boundQueue(tracks: List<Track>, startIndex: Int): BoundedQueue {
        if (tracks.size <= MAX_PLAYER_QUEUE_ITEMS) {
            return BoundedQueue(tracks.toList(), startIndex, 0)
        }
        val safeStart = startIndex.coerceIn(tracks.indices)
        val from = (safeStart - RETAIN_PLAYED_QUEUE_ITEMS).coerceAtLeast(0)
        val to = (from + MAX_PLAYER_QUEUE_ITEMS).coerceAtMost(tracks.size)
        return BoundedQueue(
            tracks = tracks.subList(from, to).toList(),
            startIndex = safeStart - from,
            droppedBefore = from,
        )
    }

    private var queue = listOf<Track>()
    private var currentIndex = -1

    // ── Playback Context (isolation gate) ──
    private var _playbackContext: PlaybackContext = PlaybackContext.Global
    val playbackContext: PlaybackContext get() = _playbackContext
    private var _playbackQueueConfig: PlaybackQueueConfig = PlaybackQueueConfig.DEFAULT
    val playbackQueueConfig: PlaybackQueueConfig get() = _playbackQueueConfig
    private val _playbackBackend = MutableStateFlow(PlaybackBackend.EXO_STREAMING)
    val playbackBackend: StateFlow<PlaybackBackend> = _playbackBackend
    val isLocalJucePlaybackActive: Boolean
        get() = _playbackBackend.value == PlaybackBackend.JUCE_LOCAL

    // Official VK preloads only the future StartPlayVkMixSource near the end
    // of a finite queue. It does not turn that queue into VK_MIX_CONFIG early.
    private data class AutoflowSeed(
        val playbackContext: PlaybackContext,
        val queueCount: Int,
        val lastAudioIds: List<String>,
        val source: VkAutoflowSource,
        val title: String,
    )

    private data class PrefetchedAutoflow(
        val seed: AutoflowSeed,
        val session: VkMixSession,
    )

    private val autoflowLock = Any()
    private var autoflowGeneration = 0L
    private var autoflowJob: Job? = null
    private var autoflowJobSeed: AutoflowSeed? = null
    private var prefetchedAutoflow: PrefetchedAutoflow? = null
    private var autoflowTransitioning = false
    private var failedAutoflowSeed: AutoflowSeed? = null

    private const val AUTOFLOW_PRELOAD_TRACKS_LEFT = 3

    private data class MixPromptPlayback(
        val session: VkMixSession,
        val track: Track,
    )

    private enum class MixPromptDirection { NEXT, PREVIOUS }

    private val mixPromptPlaybackLock = Any()
    private var mixPromptPlayback: MixPromptPlayback? = null
    private var pendingMixPromptDirection: MixPromptDirection? = null

    private fun setMixPromptPlayback(context: PlaybackContext, track: Track) {
        synchronized(mixPromptPlaybackLock) {
            val previous = mixPromptPlayback
            val session = (context as? PlaybackContext.VkMix)?.session
            if (previous != null && (session != previous.session || track.id != previous.track.id)) {
                MusicBackend.recordVkMixPromptEvent(
                    session = previous.session,
                    track = previous.track,
                    eventType = "end",
                    eventSubtype = "change_source",
                )
                mixPromptPlayback = null
            }
            if (session != null && mixPromptPlayback == null) {
                MusicBackend.recordVkMixPromptEvent(
                    session = session,
                    track = track,
                    eventType = "start",
                    eventSubtype = "fastplay",
                )
                mixPromptPlayback = MixPromptPlayback(session, track)
            }
            pendingMixPromptDirection = null
        }
    }

    private fun transitionMixPromptPlayback(mediaId: String?, reason: Int) {
        synchronized(mixPromptPlaybackLock) {
            val previous = mixPromptPlayback ?: return
            if (mediaId == previous.track.id) return
            val direction = pendingMixPromptDirection
            pendingMixPromptDirection = null
            val stopSubtype = when {
                direction == MixPromptDirection.NEXT -> "next"
                direction == MixPromptDirection.PREVIOUS -> "prev"
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> "autoplay"
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> "repeat"
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> "change_source"
                else -> "unhandled_on_client"
            }
            MusicBackend.recordVkMixPromptEvent(
                session = previous.session,
                track = previous.track,
                eventType = "end",
                eventSubtype = stopSubtype,
            )
            val next = mediaId?.let { id -> queue.firstOrNull { it.id == id } }
            val activeSession = (_playbackContext as? PlaybackContext.VkMix)?.session
            if (next != null && activeSession != null) {
                val startSubtype = when {
                    direction == MixPromptDirection.NEXT -> "next_btn"
                    direction == MixPromptDirection.PREVIOUS -> "prev_btn"
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> "autoplay"
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> "repeat"
                    else -> "fastplay"
                }
                MusicBackend.recordVkMixPromptEvent(
                    session = activeSession,
                    track = next,
                    eventType = "start",
                    eventSubtype = startSubtype,
                )
                mixPromptPlayback = MixPromptPlayback(activeSession, next)
            } else {
                mixPromptPlayback = null
            }
        }
    }

    private fun recordMixPromptPause(subtype: String) {
        synchronized(mixPromptPlaybackLock) {
            val current = mixPromptPlayback ?: return
            MusicBackend.recordVkMixPromptEvent(
                session = current.session,
                track = current.track,
                eventType = "pause",
                eventSubtype = subtype,
            )
        }
    }

    private fun recordMixPromptResume(subtype: String) {
        synchronized(mixPromptPlaybackLock) {
            val current = mixPromptPlayback ?: return
            MusicBackend.recordVkMixPromptEvent(
                session = current.session,
                track = current.track,
                eventType = "start",
                eventSubtype = subtype,
            )
        }
    }

    private fun endMixPromptPlayback(subtype: String) {
        synchronized(mixPromptPlaybackLock) {
            val current = mixPromptPlayback ?: return
            MusicBackend.recordVkMixPromptEvent(
                session = current.session,
                track = current.track,
                eventType = "end",
                eventSubtype = subtype,
            )
            mixPromptPlayback = null
            pendingMixPromptDirection = null
        }
    }

    /** Тип трека по схеме URI: file/content — локальный файл, всё прочее — сеть. */
    enum class TrackKind { ONLINE, LOCAL }

    fun kindOf(track: Track): TrackKind {
        val scheme = track.uri.scheme
        return if (scheme == "file" || scheme == "content") TrackKind.LOCAL else TrackKind.ONLINE
    }

    private fun invalidateAutoflow() {
        synchronized(autoflowLock) {
            autoflowGeneration++
            autoflowJob?.cancel()
            autoflowJob = null
            autoflowJobSeed = null
            prefetchedAutoflow = null
            autoflowTransitioning = false
            failedAutoflowSeed = null
        }
    }

    /**
     * Reconstruct the official queue source fields from LMG's saved source.
     * Albums are VK audio playlists on the wire; a source-less track launch is
     * treated as a one-track playlist whose entity is that full audio id.
     */
    private fun currentAutoflowSeed(): AutoflowSeed? {
        if (_playbackQueueConfig != PlaybackQueueConfig.MUSIC_CONFIG &&
            _playbackQueueConfig != PlaybackQueueConfig.MUSIC_WITHOUT_SOURCE_CONFIG
        ) return null

        val currentQueue = queue
        val track = currentQueue.getOrNull(currentIndex) ?: return null
        if (kindOf(track) != TrackKind.ONLINE || !track.isAvailable) return null
        if (!VkAudioIdentity.isFullId(track.id) || !MusicBackend.isAutoflowEligible(track.id)) return null

        val playbackContext = _playbackContext
        val source = when (playbackContext) {
            is PlaybackContext.Catalog -> VkAutoflowSource(
                queueType = "catalog",
                queueEntityId = playbackContext.blockId,
            )
            is PlaybackContext.Playlist -> {
                val entity = playbackContext.id.removePrefix("vk_")
                if (entity.startsWith("local_")) return null
                VkAutoflowSource(queueType = "playlist", queueEntityId = entity)
            }
            is PlaybackContext.Album -> VkAutoflowSource(
                queueType = "playlist",
                queueEntityId = playbackContext.id.removePrefix("vk_"),
            )
            is PlaybackContext.Artist -> VkAutoflowSource(
                queueType = "artist",
                queueEntityId = playbackContext.id.removePrefix("vk_"),
            )
            is PlaybackContext.OwnerAudio -> VkAutoflowSource(
                queueType = "playlist",
                queueEntityId = "${playbackContext.ownerId}_-1",
            )
            is PlaybackContext.Global -> VkAutoflowSource(
                queueType = "playlist",
                queueEntityId = MusicBackend.autoflowTrackEntityId(track.id),
            )
            is PlaybackContext.Downloads,
            is PlaybackContext.VkMix -> return null
        }
        val lastAudioIds = currentQueue.asSequence()
            .map(Track::id)
            .filter(VkAudioIdentity::isFullId)
            .toList()
            .takeLast(50)
        if (lastAudioIds.isEmpty()) return null

        return AutoflowSeed(
            playbackContext = playbackContext,
            queueCount = currentQueue.size,
            lastAudioIds = lastAudioIds,
            source = source,
            title = currentQueue.takeLast(50).firstOrNull()?.title ?: track.title,
        )
    }

    /** Preload only `{mix_id, entity_id}` when the current track is in the last three. */
    private fun maybePreloadAutoflow() {
        if (!PlayerSettings.autoplay.value) return
        val tracksIncludingCurrent = (queue.size - currentIndex).coerceAtLeast(0)
        if (tracksIncludingCurrent > AUTOFLOW_PRELOAD_TRACKS_LEFT) return
        val seed = currentAutoflowSeed() ?: return

        val generation: Long
        synchronized(autoflowLock) {
            if (prefetchedAutoflow?.seed == seed) return
            if (failedAutoflowSeed == seed) return
            if (autoflowJobSeed == seed && autoflowJob?.isActive == true) return
            autoflowGeneration++
            generation = autoflowGeneration
            autoflowJob?.cancel()
            autoflowJobSeed = seed
            autoflowJob = ioScope.launch {
                val resolved = runCatching {
                    MusicBackend.resolveAutoflowMixSession(
                        queueCount = seed.queueCount,
                        audioIds = seed.lastAudioIds,
                        source = seed.source,
                        title = seed.title,
                    )
                }
                synchronized(autoflowLock) {
                    if (generation == autoflowGeneration && currentAutoflowSeed() == seed) {
                        resolved.onSuccess { prefetchedAutoflow = PrefetchedAutoflow(seed, it) }
                        resolved.onFailure {
                            failedAutoflowSeed = seed
                            DebugLog.add("VK AUTOFLOW preload failed: ${it.message ?: it.javaClass.simpleName}")
                        }
                    }
                    if (generation == autoflowGeneration) {
                        autoflowJob = null
                        autoflowJobSeed = null
                    }
                }
            }
        }
    }

    /** Replace an exhausted finite queue with the official VK Mix source. */
    private suspend fun startAutoflowAtQueueEnd(context: Context): Boolean {
        if (!PlayerSettings.autoplay.value) return false
        // VK may preload the future source while repeat is enabled, but its
        // final hand-off gate requires LoopMode.NONE.
        if (_repeatMode.value != 0) return false
        val seed = currentAutoflowSeed() ?: return false
        if (currentIndex + 1 < queue.size) return false

        val preloadJob: Job?
        synchronized(autoflowLock) {
            if (autoflowTransitioning) return false
            autoflowTransitioning = true
            preloadJob = autoflowJob.takeIf { autoflowJobSeed == seed }
        }

        return try {
            preloadJob?.join()
            val session = synchronized(autoflowLock) {
                prefetchedAutoflow?.takeIf { it.seed == seed }?.session
            } ?: runCatching {
                MusicBackend.resolveAutoflowMixSession(
                    queueCount = seed.queueCount,
                    audioIds = seed.lastAudioIds,
                    source = seed.source,
                    title = seed.title,
                )
            }.onFailure {
                synchronized(autoflowLock) { failedAutoflowSeed = seed }
                DebugLog.add("VK AUTOFLOW resolve failed: ${it.message ?: it.javaClass.simpleName}")
            }.getOrNull() ?: return false

            val mixSource = runCatching { MusicBackend.startVkMix(session) }
                .onFailure {
                    DebugLog.add("VK AUTOFLOW Mix start failed: ${it.message ?: it.javaClass.simpleName}")
                }
                .getOrNull() ?: return false
            if (mixSource.tracks.isEmpty()) {
                DebugLog.add("VK AUTOFLOW Mix start returned 0 tracks")
                return false
            }

            withContext(Dispatchers.Main) {
                if (!PlayerSettings.autoplay.value || currentAutoflowSeed() != seed || currentIndex + 1 < queue.size) {
                    false
                } else {
                    DebugLog.add(
                        "VK AUTOFLOW start mixId=${mixSource.mixId} tracks=${mixSource.tracks.size}",
                    )
                    playFromList(
                        context = context,
                        tracks = mixSource.tracks,
                        startIndex = 0,
                        playbackContext = PlaybackContext.VkMix(mixSource.session),
                    )
                    true
                }
            }
        } finally {
            synchronized(autoflowLock) { autoflowTransitioning = false }
        }
    }

    /**
     * Единая точка старта воспроизведения: бэкенд выбирается по СТАРТОВОМУ треку,
     * чужие треки в плеер не попадают.
     *
     * Стриминговый плеер физически умеет открыть content://, а JUCE — нет, поэтому
     * раньше локальные треки молча игрались через ExoPlayer мимо JUCE, а онлайн-трек
     * в локальной очереди вставал намертво (JUCE_LOAD_FAILED без скипа).
     */
    fun play(
        context: Context,
        tracks: List<Track>,
        startIndex: Int = 0,
        autoRefillType: String? = null,
        autoRefillId: String? = null,
        autoRefillName: String? = null,
        seedTrackId: String? = null,
        seedPool: List<String> = emptyList(),
        playbackContext: PlaybackContext? = null,
    ) {
        if (tracks.isEmpty() || startIndex !in tracks.indices) return
        val startTrack = tracks[startIndex]
        val kind = kindOf(startTrack)
        val pure = tracks.filter { kindOf(it) == kind }
        val newStart = pure.indexOfFirst { it.id == startTrack.id }.coerceAtLeast(0)
        if (pure.size != tracks.size) {
            DebugLog.add("PC.play kind=$kind отброшено чужих: ${tracks.size - pure.size}")
        }
        if (kind == TrackKind.LOCAL) {
            playLocalOnJuce(
                context = context,
                tracks = pure,
                startIndex = newStart,
                playbackContext = playbackContext ?: PlaybackContext.Playlist("local_audio"),
            )
        } else {
            playFromList(
                context, pure, newStart,
                autoRefillType, autoRefillId, autoRefillName, seedTrackId, seedPool,
                playbackContext,
            )
        }
    }

    // ── Endless Playback (AutoMix) ──
    private val endlessEngine = EndlessPlaybackEngine(
        scope = ioScope,
        getController = { controller },
        getCompanionPlayer = { null }
    )

    /**
     * Единая точка входа для очереди с разрешённым endless listening. Безопасно
     * дёргать из нескольких триггеров (UI-bridge и service-listener) —
     * EndlessPlaybackEngine дедуплицирует через свой lock + throttle.
     */
    fun ensureWaveRefill() {
        if (!_playbackQueueConfig.endlessListeningEnabled) return
        ioScope.launch { endlessEngine.checkAndRefillIfNeeded() }
    }

    /** Public accessor for the endless engine's refill context (mood/genre) */
    val waveRefillContext: kotlinx.coroutines.flow.StateFlow<EndlessPlaybackEngine.RefillContext?>
        get() = endlessEngine.refillContext

    // ── Stream URL cache ──
    private val streamUrlCache = java.util.concurrent.ConcurrentHashMap<String, CachedStreamUrl>()
    private var networkRouteJob: Job? = null
    // In-flight резолвы: один и тот же трек резолвится максимум ОДНОЙ корутиной,
    // остальные ждут тот же результат — без дублирующих POST /track (их раньше
    // могло уходить 2-3 на трек: префетч + загрузчик + handleExpiredUrl).
    private val inFlightResolves = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Deferred<StreamResult>>()

    fun getValidCachedUri(trackId: String): Uri? {
        return streamUrlCache[trackId]?.uri
    }

    // ── Playback logging state ──
    private var playbackStartTimeMs: Long = 0L
    private var totalPlayedMs: Long = 0L
    private var lastPositionMs: Long = 0L
    // Индекс трека, для которого уже запущена предзагрузка следующего (раз на трек).
    private var preloadDoneForIndex: Int = -1

    // ── Consecutive Skips ──
    private var _consecutiveSkips = 0
    val consecutiveSkips: Int get() = _consecutiveSkips

    fun resetConsecutiveSkips() {
        _consecutiveSkips = 0
    }

    // ── StateFlow (UI observes these) ──
    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack

    // Текущий трек — видеоклип (Apple Music): в плеере вместо обложки Surface.
    private val _isVideoClip = MutableStateFlow(false)
    val isVideoClip: StateFlow<Boolean> = _isVideoClip

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs

    private val _positionDiscontinuity = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val positionDiscontinuity: SharedFlow<Long> = _positionDiscontinuity.asSharedFlow()

    private var lastPlayerPositionMs: Long = 0L
    private var lastSyncTimeMs: Long = 0L
    private var lastIsPlaying: Boolean = false

    fun getSmoothPositionMs(): Long {
        if (!lastIsPlaying) return lastPlayerPositionMs
        val elapsed = SystemClock.elapsedRealtime() - lastSyncTimeMs
        return lastPlayerPositionMs + (elapsed * _playbackSpeed.value).toLong()
    }

    /** Переякорить интерполяцию на текущей сглаженной позиции (play/pause/seek/speed). */
    private fun reanchorSmoothPosition() {
        lastPlayerPositionMs = getSmoothPositionMs()
        lastSyncTimeMs = SystemClock.elapsedRealtime()
    }

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs

    private val _queueFlow = MutableStateFlow<List<Track>>(emptyList())
    val queueFlow: StateFlow<List<Track>> = _queueFlow

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled

    /** Порядок очереди (id) до перемешивания — по нему восстанавливаемся. */
    private var preShuffleOrder: List<String>? = null

    /**
     * Границы секций очереди.
     *
     *  [currentIndex+1, manualEnd) — добавлено вручную («Играть следующим», «В очередь»)
     *  [manualEnd, autoStart)      — остаток исходной подборки (альбом, плейлист, поиск)
     *  [autoStart, queue.size)     — подобрано волной
     *
     * Держим два числа, а не список происхождений на каждый трек: поддерживать
     * его пришлось бы в тех же девяти точках мутации очереди, но пропущенная
     * точка сдвигала бы все секции разом, а не портила одну границу.
     */
    data class QueueSections(val manualEnd: Int, val autoStart: Int)

    private var manualEnd = 0
    private var autoStart = 0
    private val _queueSections = MutableStateFlow(QueueSections(0, 0))
    val queueSections: StateFlow<QueueSections> = _queueSections
    val autoplayEnabled: StateFlow<Boolean> = PlayerSettings.autoplay

    fun toggleAutoplay() {
        val enabled = !PlayerSettings.autoplay.value
        PlayerSettings.setAutoplay(enabled)
        if (enabled) maybePreloadAutoflow() else invalidateAutoflow()
    }

    /**
     * Приводит границы в согласованное состояние и публикует их.
     *
     * Самопроверка дешёвая и намеренно грубая: если инвариант нарушен (например,
     * фоновая дозаправка разъехалась с перестановкой), секции схлопываются в
     * одну. Показать очередь одним списком честнее, чем разрезать её не в тех
     * местах.
     */
    private fun publishSections() {
        val lo = (currentIndex + 1).coerceIn(0, queue.size)
        if (manualEnd < lo || autoStart < manualEnd || autoStart > queue.size) {
            manualEnd = lo
            autoStart = queue.size
        }
        _queueSections.value = QueueSections(manualEnd, autoStart)
    }

    /** Новая подборка: ручного ничего нет, волна ещё не добавляла. */
    private fun resetSections(startIndex: Int, size: Int) {
        manualEnd = (startIndex + 1).coerceIn(0, size)
        autoStart = size
        publishSections()
    }

    private val _repeatMode = MutableStateFlow(0)
    val repeatMode: StateFlow<Int> = _repeatMode

    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds

    private val _recentlyPlayed = MutableStateFlow<List<Track>>(emptyList())
    val recentlyPlayed: StateFlow<List<Track>> = _recentlyPlayed

    // Тема персистится в DataStore (PlayerSettings) — реактивно и переживает рестарт.
    val themeMode: StateFlow<Int> get() = PlayerSettings.themeMode
    fun setThemeMode(mode: Int) = PlayerSettings.setThemeMode(mode)

    private val _volume = MutableStateFlow(1f)
    val volume: StateFlow<Float> = _volume
    fun setVolume(value: Float) { _volume.value = value.coerceIn(0f, 1f) }

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed

    @Volatile
    var audioServiceRef: AudioService? = null

    fun setPlaybackSpeed(speed: Float) {
        // re-anchor at current speed before switching, so position doesn't jump
        reanchorSmoothPosition()
        // нижняя граница 0.1 — для медленной разметки лирики (успеть тапать слова)
        _playbackSpeed.value = speed.coerceIn(0.1f, 2.0f)
        audioServiceRef?.setPlaybackSpeed(_playbackSpeed.value)
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    @Volatile
    private var activeAccountId = Long.MIN_VALUE
    @Volatile
    private var queueAccountId = 0L
    private var playbackStartJob: Job? = null

    fun playbackAccountId(): Long = queueAccountId

    fun activateAccount(userId: Long) {
        val resolvedUserId = userId.coerceAtLeast(0L)
        if (activeAccountId == resolvedUserId) return
        val hadAccount = activeAccountId != Long.MIN_VALUE
        val hasOnlinePlayback = _currentTrack.value?.isOnlineTrack == true ||
            _queueFlow.value.any { it.isOnlineTrack }
        if (hadAccount && hasOnlinePlayback) finishPlaybackForNewStart()
        activeAccountId = resolvedUserId
        playbackStartJob?.cancel()
        playbackStartJob = null
        _favoriteIds.value = emptySet()
        _recentlyPlayed.value = emptyList()
        streamUrlCache.clear()
        inFlightResolves.values.forEach { it.cancel() }
        inFlightResolves.clear()
        if (!hadAccount) {
            queueAccountId = resolvedUserId
            return
        }
        if (!hasOnlinePlayback) {
            queueAccountId = resolvedUserId
            return
        }
        preCacheJob?.cancel()
        _currentTrack.value = null
        _queueFlow.value = emptyList()
        _currentPositionMs.value = 0L
        _durationMs.value = 0L
        _isPlaying.value = false
        _isBuffering.value = false
        mainScope.launch {
            if (queueAccountId == resolvedUserId) return@launch
            queue = emptyList()
            currentIndex = -1
            manualEnd = 0
            autoStart = 0
            publishSections()
            clearAutoRefillContext()
            controller?.stop()
            controller?.clearMediaItems()
            queueAccountId = 0L
        }
    }



    // ═══════════════════════════════════════════════════════════
    //  Playback Control
    // ═══════════════════════════════════════════════════════════

    /**
     * Play a track by its unique ID using the FULL native timeline.
     * NEVER inject a single MediaItem — always seek inside the populated timeline.
     */
    fun playTrackById(context: Context, trackId: String) {
        android.util.Log.d("VOIDPIXEL_MEDIA", "UI requested Playback for Track ID: $trackId")
        _isVideoClip.value = false

        val currentQueue = queue
        val queueIndex = currentQueue.indexOfFirst { it.id == trackId }
        if (queueIndex == -1) {
            android.util.Log.e("VOIDPIXEL_MEDIA", "Track ID $trackId NOT FOUND in local queue!")
            return
        }

        // Тап по треку чужого типа = смена бэкенда. Раньше команды уходили в тот
        // плеер, который стоял в сессии, и после локального сеанса первый онлайн-трек
        // просто не запускался (play() съедал JUCE, а Exo оставался на паузе).
        val tapped = currentQueue[queueIndex]
        val wantBackend =
            if (kindOf(tapped) == TrackKind.LOCAL) PlaybackBackend.JUCE_LOCAL
            else PlaybackBackend.EXO_STREAMING
        if (_playbackBackend.value != wantBackend) {
            DebugLog.add("PC.playTrackById смена бэкенда → $wantBackend")
            play(context, currentQueue, queueIndex)
            return
        }

        ioScope.launch {
            val track = currentQueue.getOrNull(queueIndex) ?: return@launch
            val previousTrackId = finishPlaybackForNewStart()

            withContext(Dispatchers.Main) {
                currentIndex = queueIndex
                _currentTrack.value = track
                _durationMs.value = track.durationMs
                _currentPositionMs.value = 0L
                _isBuffering.value = true
            }

            // ResolvingDataSource handles URL resolution on demand
            withContext(Dispatchers.Main) {
                val player = getPlayer(context)
                if (player != null) {
                    val hasFullTimeline = player.mediaItemCount == currentQueue.size &&
                        currentQueue.indices.all { idx ->
                            player.getMediaItemAt(idx).mediaId == currentQueue[idx].id
                        }

                    if (hasFullTimeline) {
                        player.seekTo(queueIndex, 0L)
                        player.prepare()
                        player.play()
                    } else {
                        // Timeline missing items or only contains a lazy-loaded slice.
                        // Rebuild from the full app queue so manual next/previous can
                        // move through every visible queue item, not just loaded mediaItems.
                        val allMediaItems = currentQueue.map { t -> buildMediaItem(t) }
                        player.stop()
                        player.clearMediaItems()
                        player.setMediaItems(allMediaItems, queueIndex, 0L)
                        player.prepare()
                        player.play()
                    }
                    resetPlaybackLogging(track.durationMs)
                    recordPlaybackStart(track, previousTrackId)
                    prefetchAhead(context, queueIndex, depth = 3)
                    maybePreloadAutoflow()
                } else {
                    android.util.Log.e("VOIDPIXEL_MEDIA", "No player available for trackId=$trackId")
                    _isBuffering.value = false
                }
            }
            addToRecent(track)
        }
    }

    fun playTrack(context: Context, index: Int) {
        val currentQueue = queue
        if (index !in currentQueue.indices) {
            android.util.Log.e("VOIDPIXEL_MEDIA", "playTrack called with invalid index=$index, queue size=${currentQueue.size}")
            return
        }
        val trackId = currentQueue[index].id
        playTrackById(context, trackId)
    }

    /**
     * Прогрев URL следующих треков очереди (быстрый скип). Полную закачку
     * аудио в кэш делает [scheduleAudioPreCache] — отдельный агрессивный
     * контур, стартующий сразу после смены трека.
     */
    private fun prefetchAhead(
        context: Context,
        currentIndex: Int,
        depth: Int = 3
    ) {
        val currentQueue = queue.toList()
        if (currentQueue.isEmpty()) return

        val endIndex = (currentIndex + 1 + depth).coerceAtMost(currentQueue.size)
        val indicesToPrefetch = (currentIndex + 1 until endIndex)

        ioScope.launch {
            indicesToPrefetch.forEach { idx ->
                val track = currentQueue.getOrNull(idx) ?: return@forEach
                if (track.isOnlineTrack) {
                    val result = resolveStreamUrl(track.id)
                    // ВАЖНО для бесшовного перехода. Прогрев кэша ссылок сам по
                    // себе перехода не даёт: элемент очереди у ExoPlayer остаётся
                    // с пустым uri (`liquid://track?trackId=…` без PARAM_URL), и
                    // его предзагрузка всё равно упирается в открытие DataSource,
                    // то есть в блокирующий резолв — уже в момент перехода.
                    //
                    // Поэтому подменяем элемент готовым: с прямой ссылкой и
                    // правильным MIME. Тогда `PreloadConfiguration` (30 с) успевает
                    // подтянуть начало следующего трека заранее, и переход идёт
                    // без паузы — а нативный кроссфейд форка получает то, что ему
                    // нужно: уже открытый источник.
                    if (result is StreamResult.Success) {
                        withContext(Dispatchers.Main) {
                            runCatching {
                                val ctrl = controller ?: return@runCatching
                                // Индекс мог сдвинуться, пока шёл резолв: сверяем
                                // mediaId, иначе подменим чужой трек.
                                if (ctrl.getMediaItemAt(idx).mediaId == track.id) {
                                    ctrl.replaceMediaItem(idx, buildMediaItem(track, result.uri))
                                }
                            }
                        }
                    }
                }
            }
            android.util.Log.d("PlayerController", "Pre-warmed caches for indices $indicesToPrefetch")
        }
    }

    // ── Предзагрузка аудио v2: два трека вперёд, сразу после смены трека ──
    private var preCacheJob: kotlinx.coroutines.Job? = null

    /**
     * Полная закачка СЛЕДУЮЩИХ ДВУХ онлайн-треков в медиа-кэш. Стартует через
     * [initialDelayMs] после смены трека (текущему треку — приоритет сети на
     * старте), качает последовательно: next, потом next+1 — двойной скип тоже
     * мгновенный. До 3 попыток на трек с бэкоффом (URL ре-резолвится на каждой).
     *
     * Раньше закачка шла только по таймеру «за N сек до конца» и один раз:
     * ручной скип оставлял следующий трек без кэша, а провал сети хоронил
     * попытку без ретрая (полевой фидбек: «треки не предзагружаются вообще»).
     */
    /** Сколько ждём докачки текущего трека, прежде чем начать предзагрузку следующих. */
    private const val PRECACHE_WAIT_TIMEOUT_MS = 60_000L

    /**
     * Ждёт, пока буфер плеера не дотянется до конца текущего трека, но не
     * дольше [timeoutMs]. Возвращается сразу, если плеера нет или длительность
     * ещё не известна дольше таймаута.
     */
    private suspend fun awaitCurrentBuffered(timeoutMs: Long) {
        val deadline = android.os.SystemClock.uptimeMillis() + timeoutMs
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            val done = withContext(Dispatchers.Main) {
                val p = controller ?: appContext?.let { getPlayer(it) } ?: return@withContext true
                val dur = p.duration
                dur > 0 && dur != C.TIME_UNSET && p.bufferedPosition >= dur - 1_000L
            }
            if (done) return
            kotlinx.coroutines.delay(2_000L)
        }
    }

    private fun scheduleAudioPreCache(
        context: Context,
        fromIndex: Int,
        initialDelayMs: Long = 8_000L
    ) {
        if (!MediaCacheManager.isCacheEnabled()) return
        preCacheJob?.cancel()
        preCacheJob = ioScope.launch {
            kotlinx.coroutines.delay(initialDelayMs)
            // Ждём, пока дочитается ТЕКУЩИЙ трек. Раньше единственным условием
            // была фиксированная задержка, и на слабой сети предзагрузка
            // следующих отбирала полосу у того, что играет прямо сейчас —
            // конец трека начинал заикаться. Таймаут страхует от длинных
            // треков и залипшего буфера: лучше начать позже, чем не начать.
            awaitCurrentBuffered(PRECACHE_WAIT_TIMEOUT_MS)
            val snapshot = queue.toList()
            val lastIdx = kotlin.math.min(fromIndex + 2, snapshot.lastIndex)
            for (idx in (fromIndex + 1)..lastIdx) {
                if (!isActive) break
                val track = snapshot.getOrNull(idx) ?: break
                if (!track.isOnlineTrack) continue
                var ok = false
                var attempt = 0
                while (!ok && attempt < 3 && isActive) {
                    if (attempt > 0) kotlinx.coroutines.delay(1_500L * attempt)
                    attempt++
                    val result = resolveStreamUrl(track.id)
                    if (result is StreamResult.Success) {
                        ok = MediaCacheManager.preCacheTrack(track.id, result.uri)
                    } else if (result is StreamResult.Error && result.code in TERMINAL_RESOLVE_ERRORS) {
                        // Терминальная ошибка (404 track_not_found / 403 source_not_allowed /
                        // 451 region / early_access): ретраить бессмысленно — трек не
                        // появится. Не жжём 3 попытки с бэкоффом (это и был «провал
                        // (3 попыт.)» в логе на битых треках волны); плеер такой трек
                        // авто-скипнет при проигрывании.
                        break
                    }
                }
                if (isActive || ok) {
                    DebugLog.add(
                        "PRELOAD [+${idx - fromIndex}] ${track.title.take(28)}: " +
                            if (ok) "в кэше целиком" else "провал ($attempt попыт.)"
                    )
                }
            }
        }
    }

    fun addTracksToQueue(newTracks: List<Track>) {
        if (newTracks.isEmpty()) return
        // Волна/эндлесс отдаёт онлайн-треки: в локальную очередь их подмешивать
        // нельзя — JUCE такой трек не откроет и воспроизведение встанет.
        @Suppress("NAME_SHADOWING") val newTracks = run {
            val kind = _currentTrack.value?.let { kindOf(it) } ?: TrackKind.ONLINE
            newTracks.filter { kindOf(it) == kind }
        }
        if (newTracks.isEmpty()) return
        mainScope.launch {
            // Anti-repeat: не добавляем то, что уже есть в очереди (защита от дублей,
            // даже если сервер/refill вернул пересекающийся трек).
            val existingIds = queue.mapTo(HashSet()) { it.id }
            val fresh = newTracks.filterNot { it.id in existingIds }
            if (fresh.isEmpty()) return@launch

            // Make room by releasing only an already-played prefix. This keeps
            // Previous useful (50 tracks), preserves the whole upcoming tail,
            // and prevents an endless Mix/Wave session from growing Media3's
            // ConcatenatedTimeline forever.
            val overflow = (queue.size + fresh.size - MAX_PLAYER_QUEUE_ITEMS)
                .coerceAtLeast(0)
            val droppablePrefix = (currentIndex - RETAIN_PLAYED_QUEUE_ITEMS)
                .coerceAtLeast(0)
            val dropCount = overflow.coerceAtMost(droppablePrefix)
            if (dropCount > 0) {
                val droppedIds = queue.take(dropCount).mapTo(HashSet()) { it.id }
                queue = queue.drop(dropCount)
                currentIndex -= dropCount
                manualEnd = (manualEnd - dropCount).coerceAtLeast(0)
                autoStart = (autoStart - dropCount).coerceAtLeast(0)
                preShuffleOrder = preShuffleOrder?.filterNot { it in droppedIds }
            }

            val accepted = fresh.take(
                (MAX_PLAYER_QUEUE_ITEMS - queue.size).coerceAtLeast(0)
            )
            if (accepted.isEmpty()) {
                android.util.Log.w(
                    "VOIDPIXEL_MEDIA",
                    "[QUEUE_CAP] refill skipped: size=${queue.size}, current=$currentIndex"
                )
                return@launch
            }
            queue = queue + accepted
            _queueFlow.value = queue
            // Волна дописывает в хвост: autoStart уже стоит там, где начинается
            // подобранное, двигать его не нужно — только проверить инвариант.
            publishSections()

            // Анти-повтор волны: всё, что попало в очередь, регистрируем в
            // playedIds движка — иначе следующий рефилл может запросить у
            // сервера то, что уже стоит в очереди (дубль отсеется, но
            // wave/next-запрос сгорит зря).
            if (_playbackContext is PlaybackContext.Global) {
                endlessEngine.registerTracks(accepted.map { it.id })
            }

            withContext(Dispatchers.Main) {
                val mediaItems = accepted.map { track ->
                    buildMediaItem(track, track.uri)
                }
                val player = controller ?: appContext?.let { getPlayer(it) }
                if (player != null) {
                    if (dropCount > 0) {
                        player.removeMediaItems(
                            0,
                            dropCount.coerceAtMost(player.mediaItemCount)
                        )
                    }
                    // MediaController -> MediaSession.Callback.onAddMediaItems -> AudioService.
                    // Direct service append is only a fallback; doing both duplicates timeline items.
                    player.addMediaItems(mediaItems)
                } else {
                    audioServiceRef?.addToQueue(mediaItems, removeFromStart = dropCount)
                }

                android.util.Log.d(
                    "VOIDPIXEL_MEDIA",
                    "[QUEUE_BOUND] dropped=$dropCount added=${accepted.size} total=${queue.size} current=$currentIndex"
                )

                // Сразу обновляем плейсхолдеры для свежих элементов
                appContext?.let { prefetchAhead(it, currentIndex, depth = 3) }
            }
        }
    }

    fun addTracksFromService(newTracks: List<Track>, mediaItems: List<MediaItem>) {
        val capacity = (MAX_PLAYER_QUEUE_ITEMS - queue.size).coerceAtLeast(0)
        queue = queue + newTracks.take(capacity)
        _queueFlow.value = queue
        publishSections()
        appContext?.let { prefetchAhead(it, currentIndex, depth = 3) }
        android.util.Log.d("VOIDPIXEL_MEDIA", "Sync queue from service: added ${newTracks.size} tracks, total=${queue.size}")
    }

    fun playFromList(
        context: Context,
        tracks: List<Track>,
        startIndex: Int = 0,
        autoRefillType: String? = null,
        autoRefillId: String? = null,
        autoRefillName: String? = null,
        seedTrackId: String? = null,
        seedPool: List<String> = emptyList(),
        playbackContext: PlaybackContext? = null,
    ) {
        invalidateAutoflow()
        if (tracks.isEmpty() || startIndex !in tracks.indices) {
            android.util.Log.e("VOIDPIXEL_MEDIA", "playFromList called with empty tracks or invalid startIndex=$startIndex")
            return
        }
        queueAccountId = activeAccountId.coerceAtLeast(0L)

        // Однородность очереди по СТАРТОВОМУ треку: смешивать локальное и
        // онлайн в одной очереди нельзя (у онлайна свой резолв, у локального —
        // готовый content://). Раньше здесь безусловно оставляли только ONLINE
        // и выходили по `return` на локальной очереди — а после того, как
        // playLocalOnJuce стал перенаправлять локальные треки сюда (JUCE в
        // сборке нет), это означало: тап по скачанному треку не делает НИЧЕГО.
        // Жалоба пользователя: «тыкаю на скачанное аудио, вообще ничего не
        // срабатывает и не включается».
        //
        // Ниже локальный путь уже полностью рабочий: резолв пропускается
        // (isOnlineTrack == false → StreamResult.Success(track.uri)), а
        // buildMediaItem отдаёт content:// как есть, не оборачивая в liquid://.
        val startTrackId = tracks[startIndex].id
        val startKind = kindOf(tracks[startIndex])
        val filteredTracks = tracks.filter { kindOf(it) == startKind }
        if (filteredTracks.isEmpty()) return
        // Индекс ищем ПО ID, а не coerceAtMost: если фильтр выбросил треки
        // ДО стартового, «обрезка» индекса заиграла бы чужой трек.
        val filteredStartIndex =
            filteredTracks.indexOfFirst { it.id == startTrackId }.coerceAtLeast(0)
        val sourceQueueSize = filteredTracks.size
        val boundedQueue = boundQueue(filteredTracks, filteredStartIndex)
        @Suppress("NAME_SHADOWING") val tracks = boundedQueue.tracks
        @Suppress("NAME_SHADOWING") val startIndex = boundedQueue.startIndex
        val startTrack = tracks[startIndex]
        if (sourceQueueSize > tracks.size) {
            android.util.Log.d(
                "VOIDPIXEL_MEDIA",
                "[QUEUE_BOUND] initial source capped at ${tracks.size}, start=$startIndex, droppedBefore=${boundedQueue.droppedBefore}"
            )
        }
        DebugLog.add("PC.playFromList(EXO) n=${tracks.size} start=$startIndex online=${startTrack.isOnlineTrack} | ${DebugLog.caller()}")

        // Любой НЕ-YWAVE плейбек завершает волну ЯМ: её дозаправка/фидбек
        // не должны продолжаться под чужой очередью.
        if (!autoRefillType.equals("YWAVE", ignoreCase = true)) {
            Unit
        }

        // ── Determine playback context BEFORE any async work ──
        val newContext = playbackContext ?: when {
            autoRefillType.equals("library", ignoreCase = true) && autoRefillId.equals("downloads", ignoreCase = true) ->
                PlaybackContext.Downloads
            autoRefillType.equals("playlist", ignoreCase = true) && autoRefillId != null ->
                PlaybackContext.Playlist(autoRefillId)
            autoRefillType.equals("album", ignoreCase = true) && autoRefillId != null ->
                PlaybackContext.Album(autoRefillId)
            autoRefillType.equals("artist", ignoreCase = true) && autoRefillId != null ->
                PlaybackContext.Artist(autoRefillId)
            else -> PlaybackContext.Global
        }
        // Official VK derives queue capabilities from StartPlaySource. LMG keeps
        // the source in PlaybackContext, so preserve the same distinction here:
        // regular music is finite; only an explicit mix/refill source is endless.
        val newQueueConfig = when {
            newContext is PlaybackContext.VkMix -> PlaybackQueueConfig.VK_MIX_CONFIG
            newContext !is PlaybackContext.Global -> PlaybackQueueConfig.MUSIC_CONFIG
            autoRefillType != null -> PlaybackQueueConfig.VK_MIX_CONFIG
            else -> PlaybackQueueConfig.MUSIC_WITHOUT_SOURCE_CONFIG
        }

        _isVideoClip.value = false   // обычный трек — не видеоклип
        playbackStartJob?.cancel()
        playbackStartJob = ioScope.launch {
            // ── ABSOLUTE QUEUE PURGE: wipe old queue before loading ──
            withContext(Dispatchers.Main) {
                endMixPromptPlayback("change_source")
                val player = getPlayer(context)
                player?.let {
                    it.stop()
                    it.clearMediaItems()
                }
            }
            val previousTrackId = finishPlaybackForNewStart()

            _playbackContext = newContext
            _playbackQueueConfig = newQueueConfig
            _playbackBackend.value = PlaybackBackend.EXO_STREAMING
            android.util.Log.d(
                "VOIDPIXEL_MEDIA",
                "[CONTEXT_SET] $newContext queueConfig=$newQueueConfig",
            )

            endlessEngine.reset()
            if (newQueueConfig.endlessListeningEnabled &&
                newContext is PlaybackContext.Global &&
                autoRefillType != null
            ) {
                val type = try {
                    EndlessPlaybackEngine.RefillContext.Type.valueOf(autoRefillType.uppercase())
                } catch (e: Exception) {
                    EndlessPlaybackEngine.RefillContext.Type.WAVE
                }
                endlessEngine.setRefillContext(
                    EndlessPlaybackEngine.RefillContext(
                        type = type,
                        id = autoRefillId,
                        name = autoRefillName,
                        seedTrackId = seedTrackId,
                        seedPool = seedPool
                    )
                )
                endlessEngine.registerTracks(tracks.map { it.id })
            }

            val refreshedStreams = if (startTrack.isOnlineTrack) {
                try {
                    tracks.forEach { track ->
                        streamUrlCache.remove(track.id)
                        streamUrlCache.remove(VkAudioIdentity.stableFullId(track.id))
                    }
                    MusicBackend.refreshPlaybackStreams(
                        trackIds = tracks.map(Track::id),
                        ref = newContext.playbackRef(),
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emptyMap()
                }
            } else {
                emptyMap()
            }
            val refreshedUris = buildMap {
                tracks.forEach { track ->
                    val info = refreshedStreams[VkAudioIdentity.stableFullId(track.id)]
                        ?: return@forEach
                    val result = cacheAndReturn(track.id, info)
                    if (result is StreamResult.Success) put(track.id, result.uri)
                }
            }

            val startStreamResult = if (startTrack.isOnlineTrack) {
                refreshedUris[startTrack.id]?.let { StreamResult.Success(it) }
                    ?: resolveStreamUrl(startTrack.id)
            } else {
                StreamResult.Success(startTrack.uri)
            }

            when (startStreamResult) {
                is StreamResult.Success -> {
                    val immutableTracks = tracks.toList()

                    val mediaItems = tracks.mapIndexed { i, track ->
                        val uri = refreshedUris[track.id]
                            ?: if (i == startIndex) startStreamResult.uri else track.uri
                        buildMediaItem(track, uri)
                    }

                    withContext(Dispatchers.Main) {
                        // Мутации queue/currentIndex — ТОЛЬКО на main (P0, аудит):
                        // раньше писались здесь с IO, а addTracksToQueue/move/remove
                        // мутируют с main — plain var без синхронизации давал lost
                        // update (треки рефилла волны исчезали) и битую видимость.
                        // Инвариант «queue до prepare» сохранён: setMediaItems ниже
                        // дергает resolveStreamUrlSync только при реальном открытии
                        // источника, к этому моменту queue уже выставлена.
                        queue = immutableTracks
                        _queueFlow.value = immutableTracks
                        currentIndex = startIndex
                        resetSections(startIndex, immutableTracks.size)

                        _currentTrack.value = startTrack
                        _durationMs.value = startTrack.durationMs
                        _currentPositionMs.value = 0L
                        _isBuffering.value = true
                        setMixPromptPlayback(newContext, startTrack)

                        val player = getPlayer(context)
                        if (player != null) {
                            player.stop()
                            player.clearMediaItems()
                            player.setMediaItems(mediaItems, startIndex, 0L)
                            player.prepare()
                            player.play()
                        } else {
                            // Редкий fallback до подключения MediaController.
                            // Одновременно оба пути вызывать нельзя: это дважды
                            // пересоздаёт весь timeline в AudioService.
                            audioServiceRef?.setQueue(mediaItems, startIndex, 0L)
                        }
                        resetPlaybackLogging(startTrack.durationMs)
                        recordPlaybackStart(startTrack, previousTrackId)
                        maybePreloadAutoflow()
                    }
                    addToRecent(startTrack)

                    if (newQueueConfig.endlessListeningEnabled) {
                        launch {
                            kotlinx.coroutines.delay(3000)
                            endlessEngine.checkAndRefillIfNeeded()
                        }
                    }

                    prefetchAhead(context, startIndex, depth = 3)
                    scheduleAudioPreCache(context, startIndex)
                }
                is StreamResult.Error -> {
                    android.util.Log.e("PlayerController", "Stream error for ${startTrack.id}: ${startStreamResult.code}")
                    DebugLog.add(
                        "PC.stream ОШИБКА ${startTrack.id}: ${startStreamResult.code}" +
                            (startStreamResult.message?.let { " ($it)" } ?: ""),
                    )
                    withContext(Dispatchers.Main) {
                        _isBuffering.value = false
                        val msg = streamErrorMessage(startStreamResult)
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    /**
     * Stage 8b — играть ЛОКАЛЬНУЮ очередь полностью через JUCE-движок.
     *
     * В отличие от [playFromList] (ExoPlayer + стриминговый резолв), здесь звук
     * даёт нативный JUCE: AudioService переставляет MediaSession на JuceLocalPlayer
     * (нотификация / экран блокировки / MediaController продолжают работать), а
     * затем AutoMix (Стадия 8c) делает кроссфейд ВНУТРИ движка без швов.
     *
     * Только локальные файлы (content:// / file://) — JUCE читает их с диска.
     * Онлайн-рефилл «волны» здесь не запускаем: очередь статична.
     */
    fun playLocalOnJuce(
        context: Context,
        tracks: List<Track>,
        startIndex: Int = 0,
        playbackContext: PlaybackContext = PlaybackContext.Playlist("local_audio")
    ) {
        invalidateAutoflow()
        if (tracks.isEmpty() || startIndex !in tracks.indices) {
            android.util.Log.e("VOIDPIXEL_MEDIA", "playLocalOnJuce: empty tracks or bad startIndex=$startIndex")
            return
        }
        playbackStartJob?.cancel()
        playbackStartJob = null
        queueAccountId = activeAccountId.coerceAtLeast(0L)
        val startTrackId = tracks[startIndex].id
        val localTracks = tracks.filter { kindOf(it) == TrackKind.LOCAL }
        if (localTracks.isEmpty()) return // онлайн-очередь в JUCE не отдаём
        // По ID, а не coerceAtMost: отброшенные онлайн-треки ДО стартового
        // сдвигают индекс, и заиграл бы не тот трек, по которому тапнули.
        val localStartIndex =
            localTracks.indexOfFirst { it.id == startTrackId }.coerceAtLeast(0)
        val boundedQueue = boundQueue(localTracks, localStartIndex)
        @Suppress("NAME_SHADOWING") val tracks = boundedQueue.tracks
        @Suppress("NAME_SHADOWING") val startIndex = boundedQueue.startIndex
        val startTrack = tracks[startIndex]
        DebugLog.add("PC.playLocalOnJuce n=${tracks.size} start=$startIndex id=${startTrack.id} | ${DebugLog.caller()}")

        // JUCE-библиотеки в сборке НЕТ: собирается только liblmg.so, а
        // System.loadLibrary("automix_juce") падает в catch. Поэтому локальный
        // путь через JUCE не заиграет ничего — трек показывался в плеере и молчал
        // (жалоба пользователя: «скачал трек, он не воспроизводится, хотя плеер
        // его показывает»).
        //
        // ExoPlayer умеет и file://, и content:// (через ContentDataSource в
        // DefaultDataSource, см. StreamingDataSource.open) — значит скачанные
        // треки надо играть им. Когда JUCE появится, условие снова пустит
        // локальные треки в него.
        //
        // isAvailable(), а не isLoaded: последний до первой попытки загрузки
        // отдаёт false даже при наличии библиотеки, и локальные треки ушли бы в
        // ExoPlayer впустую.
        if (!com.lmg.vk.engine.automix.AutoMixNativeEngine.isAvailable()) {
            DebugLog.add("JUCE недоступен — локальные треки играем через ExoPlayer")
            // Оригинальный VK передаёт источник отдельно от refill-конфига.
            // Пробрасываем его напрямую: локальная очередь остаётся конечной и
            // не превращается в глобальную волну из-за строковой эвристики.
            playFromList(
                context, tracks, startIndex,
                playbackContext = playbackContext,
            )
            return
        }

        ioScope.launch {
            val previousTrackId = finishPlaybackForNewStart()
            // Статичная локальная очередь — без онлайн-рефилла.
            _playbackContext = playbackContext
            _playbackQueueConfig = PlaybackQueueConfig.MUSIC_CONFIG
            _playbackBackend.value = PlaybackBackend.JUCE_LOCAL
            endlessEngine.reset()

            val immutableTracks = tracks.toList()

            val mediaItems = immutableTracks.map { track -> buildMediaItem(track, track.uri) }

            withContext(Dispatchers.Main) {
                // Мутации queue/currentIndex — только на main (см. playFromList).
                queue = immutableTracks
                _queueFlow.value = immutableTracks
                currentIndex = startIndex
                resetSections(startIndex, immutableTracks.size)

                _currentTrack.value = startTrack
                _durationMs.value = startTrack.durationMs
                _currentPositionMs.value = 0L
                _isBuffering.value = false
                _isVideoClip.value = false   // локальные файлы — не видеоклип
                setMixPromptPlayback(playbackContext, startTrack)

                // Поднять сервис/контроллер (после этого audioServiceRef установлен).
                getPlayer(context)
                DebugLog.add("PC.playLocalOnJuce -> svc ref=${if (audioServiceRef==null) "NULL" else "ok"} items=${mediaItems.size}")
                audioServiceRef?.playLocalQueue(mediaItems, startIndex)
                resetPlaybackLogging(startTrack.durationMs)
                recordPlaybackStart(startTrack, previousTrackId)
            }
            addToRecent(startTrack)
        }
    }

    /** Пауза без тоггла (для sleep timer): по истечении таймера музыка должна
     *  ТОЛЬКО останавливаться, никогда не включаться. */
    fun pause(context: Context) {
        mainScope.launch {
            val player = getPlayer(context)
            if (player?.isPlaying == true) {
                recordMixPromptPause("pause_btn")
                player.pause()
            }
        }
    }

    fun togglePlayPause(context: Context) {
        mainScope.launch {
            val player = getPlayer(context) ?: return@launch
            if (player.isPlaying) {
                recordMixPromptPause("pause_btn")
                player.pause()
            } else {
                recordMixPromptResume("play_btn")
                if (player.mediaItemCount == 0 && queue.isNotEmpty()) {
                    val trackId = queue.getOrNull(currentIndex)?.id ?: queue.firstOrNull()?.id
                    if (trackId != null) {
                        playTrackById(context, trackId)
                    }
                } else {
                    player.play()
                }
            }
        }
    }

    fun skipNext(context: Context) {
        mainScope.launch {
            var currentQueue = queue
            if (currentQueue.isEmpty()) return@launch
            val remaining = (currentQueue.size - currentIndex).coerceAtLeast(0)
            val atQueueEnd = currentIndex + 1 >= currentQueue.size

            if (remaining <= EndlessPlaybackEngine.REFILL_THRESHOLD) {
                // Ждём рефилл ТОЛЬКО на реальном конце очереди (P1, аудит):
                // порог 40 при батчах по 30 — типовое состояние, и каждый тап
                // «next» синхронно ждал сетевой рефилл (до секунд). Если впереди
                // ещё есть треки — рефилл уходит в фон, скип мгновенный.
                if (!atQueueEnd) {
                    ioScope.launch {
                        runCatching {
                            endlessEngine.checkAndRefillIfNeeded(
                                remainingCount = remaining,
                                force = false
                            )
                        }
                    }
                }
                val refilled = if (!atQueueEnd) false else withContext(Dispatchers.IO) {
                    endlessEngine.checkAndRefillIfNeeded(
                        remainingCount = remaining,
                        force = true
                    )
                }
                if (refilled) {
                    var waitAttempts = 0
                    while (waitAttempts < 5) {
                        currentQueue = queue
                        if (currentIndex + 1 < currentQueue.size) break
                        waitAttempts++
                        kotlinx.coroutines.delay(50)
                    }
                } else {
                    currentQueue = queue
                }
            }

            val nextIndex = when {
                currentIndex + 1 < currentQueue.size -> currentIndex + 1
                else -> {
                    val autoflowStarted = withContext(Dispatchers.IO) {
                        startAutoflowAtQueueEnd(context)
                    }
                    if (autoflowStarted) return@launch
                    android.util.Log.w("PlayerController", "skipNext reached queue end and refill did not add a next track")
                    return@launch
                }
            }
            val nextTrackId = currentQueue.getOrNull(nextIndex)?.id ?: return@launch
            synchronized(mixPromptPlaybackLock) {
                pendingMixPromptDirection = MixPromptDirection.NEXT
            }
            val player = getPlayer(context)
            if (player != null) {
                val targetIndex = (0 until player.mediaItemCount).indexOfFirst {
                    player.getMediaItemAt(it).mediaId == nextTrackId
                }
                if (targetIndex != -1) {
                    player.playWhenReady = true
                    player.seekTo(targetIndex, 0L)
                } else {
                    playTrackById(context, nextTrackId)
                }
            } else {
                playTrackById(context, nextTrackId)
            }
        }
    }

    fun skipPrevious(context: Context) {
        mainScope.launch {
            val player = getPlayer(context)
            if (player != null && player.currentPosition > 3000L) {
                player.seekTo(0L)
                _currentPositionMs.value = 0L
                return@launch
            }
            val currentQueue = queue
            if (currentQueue.isEmpty()) return@launch
            val prevIndex = if (currentIndex > 0) currentIndex - 1 else {
                android.util.Log.w("PlayerController", "skipPrevious reached queue start")
                return@launch
            }
            val prevTrackId = currentQueue.getOrNull(prevIndex)?.id ?: return@launch
            synchronized(mixPromptPlaybackLock) {
                pendingMixPromptDirection = MixPromptDirection.PREVIOUS
            }
            if (player != null) {
                val targetIndex = (0 until player.mediaItemCount).indexOfFirst {
                    player.getMediaItemAt(it).mediaId == prevTrackId
                }
                if (targetIndex != -1) {
                    player.playWhenReady = true
                    player.seekTo(targetIndex, 0L)
                } else {
                    playTrackById(context, prevTrackId)
                }
            } else {
                playTrackById(context, prevTrackId)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val safePosition = positionMs.coerceIn(0L, (_durationMs.value - 500L).coerceAtLeast(0L))
        _positionDiscontinuity.tryEmit(safePosition)
        mainScope.launch {
            getPlayer(appContext ?: return@launch)?.seekTo(safePosition)
            _currentPositionMs.value = safePosition
            lastPositionMs = safePosition
            // re-anchor smooth position to the new seek target
            lastPlayerPositionMs = safePosition
            lastSyncTimeMs = SystemClock.elapsedRealtime()
        }
    }

    /**
     * Перерисовать виджет на домашнем экране.
     *
     * Дёргаем на смене трека и на паузе/воспроизведении: у виджета
     * updatePeriodMillis = 0, то есть система его сама не будит — иначе он
     * показывал бы трек, который закончился полчаса назад.
     */
    private fun refreshHomeWidget() {
        // Виджет живёт в чужом процессе (лончер), и обновление у Glance
        // suspend — уходим на ioScope. Вызывающие (setPlaying / смена трека)
        // работают на главном потоке и ждать здесь не должны.
        //
        // Ошибки глотаем сознательно: самый частый случай — виджет вообще не
        // добавлен на домашний экран, и тогда updateAll законно бросает. Ронять
        // из-за этого воспроизведение нельзя, поэтому runCatching внутри
        // VkMusicWidget.refreshAll.
        val ctx = appContext ?: return
        ioScope.launch { com.lmg.vk.widget.VkMusicWidget.refreshAll(ctx) }
    }

    fun setPlaying(playing: Boolean) {
        // re-anchor smooth position at the play/pause transition (uses old state)
        reanchorSmoothPosition()
        _isPlaying.value = playing
        lastIsPlaying = playing
        if (lastWidgetPlaying != playing) {
            lastWidgetPlaying = playing
            refreshHomeWidget()
        }
        if (!playing && _isBuffering.value) {
            _isBuffering.value = false
        }
    }

    // ── Кроссфейд ──
    // Длительность задаёт пользователь (Settings → Crossfade). ML-модель решала
    // это сама, но на стриминге себя не оправдала и со стриминга убрана; оффлайн
    // (JUCE) продолжает работать по своей модели и сюда не ходит.
    @Volatile private var lastAppliedCrossfadeMs = -1
    @Volatile private var lastWidgetPlaying: Boolean? = null
    @Volatile private var lastWidgetTrackId: String? = null

    private fun applyCrossfadeSetting() {
        if (isLocalJucePlaybackActive) return // у локального движка свой кроссфейд
        val ms = PlayerSettings.crossfadeMs.value
        if (ms == lastAppliedCrossfadeMs) return
        val service = audioServiceRef
        if (service == null) {
            // Сервис ещё не поднят — не «запоминаем» применение, иначе настройка
            // потеряется до следующего её изменения.
            return
        }
        lastAppliedCrossfadeMs = ms
        // Параметры свода принадлежат плееру: сервис применит их к своему экземпляру.
        service.applyCrossfade(ms)
        DebugLog.add("Crossfade ${if (ms > 0) "${ms}ms" else "off"}")
    }

    fun updatePosition(positionMs: Long, durationMs: Long) {
        applyCrossfadeSetting()
        _currentPositionMs.value = positionMs
        lastPlayerPositionMs = positionMs
        lastSyncTimeMs = SystemClock.elapsedRealtime()

        if (durationMs > 0L && _durationMs.value != durationMs) {
            _durationMs.value = durationMs
            _currentTrack.value?.let { track ->
                if (track.durationMs != durationMs) {
                    _currentTrack.value = track.copy(durationMs = durationMs)
                }
            }
        }

        if (_isPlaying.value) {
            val delta = positionMs - lastPositionMs
            if (delta > 0 && delta < 2000L) { // Only log actual playing time, ignore seek jumps
                totalPlayedMs += delta
            }
            lastPositionMs = positionMs

            // ── Предзагрузка следующего трека за настраиваемые N секунд до конца ──
            // Когда до конца остаётся ≤ preloadLeadSeconds — заранее резолвим/прогреваем
            // следующие треки, чтобы переход был без паузы. Один раз на трек.
            val effDur = if (durationMs > 0L) durationMs else _durationMs.value
            if (effDur > 0L && preloadDoneForIndex != currentIndex) {
                val remaining = effDur - positionMs
                val leadMs = AppSettings.preloadLeadSeconds.value * 1000L
                if (remaining in 1..leadMs) {
                    preloadDoneForIndex = currentIndex
                    // Бэкстоп: если агрессивный контур не докачал (сеть моргала
                    // всю дорогу) — под конец трека даём ему свежий заход.
                    appContext?.let {
                        prefetchAhead(it, currentIndex, depth = 2)
                        scheduleAudioPreCache(it, currentIndex, initialDelayMs = 0L)
                    }
                }
            }
        }
    }

    fun onTrackChanged(mediaId: String) {
        // Клип ↔ музыка: mediaId клипа всегда "clip_<id>", поэтому флаг видео
        // выводим из самого id на КАЖДОЙ смене медиа (bridge зовёт нас на любой
        // transition). Раньше флаг сбрасывала только пара play*-путей — после
        // клипа переход на музыку оставлял чёрный Surface вместо обложки.
        _isVideoClip.value = mediaId.startsWith("clip_")
        if (lastWidgetTrackId != mediaId) {
            lastWidgetTrackId = mediaId
            refreshHomeWidget()
        }
        val currentQueue = queue
        val index = currentQueue.indexOfFirst { it.id == mediaId }
        if (index == -1) {
            android.util.Log.w("VOIDPIXEL_MEDIA", "[TRACK_CHANGED] mediaId=$mediaId not found in local queue")
            return
        }
        // Идемпотентность: тот же трек на том же индексе уже активен — выходим.
        // Иначе тройной источник (jucePlayerListener + MediaController-bridge +
        // прямой bridge из JuceLocalPlayer) трижды сбрасывал позицию в 0 и дёргал
        // префетч. Теперь полезную работу делает только ПЕРВЫЙ вызов на смену трека.
        if (_currentTrack.value?.id == mediaId && currentIndex == index) return

        val track = currentQueue[index]
        val previousTrackId = finishPlaybackForNewStart()
        currentIndex = index
        // Проигранное перестаёт быть «добавленным вручную» — окно схлопывается
        // за играющим треком, иначе секция «Далее» будет тянуться назад.
        val lo = (currentIndex + 1).coerceIn(0, currentQueue.size)
        manualEnd = manualEnd.coerceAtLeast(lo)
        autoStart = autoStart.coerceAtLeast(manualEnd)
        publishSections()
        _currentTrack.value = track
        _durationMs.value = track.durationMs
        _currentPositionMs.value = 0L

        // Трансляция играющего трека в статус ВКонтакте (audio.setBroadcast).
        // Одна точка вызова: менеджер сам подписан и на currentTrack, и на тумблер
        // настроек, поэтому здесь достаточно гарантировать, что подписка поднята.
        // Вызов идемпотентный и в сеть не ходит, пока тумблер выключен; ошибки
        // трансляции воспроизведение не ломают — они глохнут внутри менеджера.
        VkBroadcastManager.ensureStarted()

        resetPlaybackLogging(track.durationMs)
        recordPlaybackStart(track, previousTrackId)
        appContext?.let {
            // URL-прогрев ближайшего — для мгновенного скипа.
            prefetchAhead(it, index, depth = 1)
            // Аудио следующих двух — в кэш, сразу (не ждём конца трека).
            scheduleAudioPreCache(it, index)
        }
        maybePreloadAutoflow()
    }

    fun onTrackEnded() {
        val currentQueue = queue
        val nextIndex = currentIndex + 1
        if (nextIndex < currentQueue.size) {
            val nextTrackId = currentQueue.getOrNull(nextIndex)?.id
            android.util.Log.d("VOIDPIXEL_MEDIA", "onTrackEnded: nextIndex=$nextIndex, nextTrackId=$nextTrackId")
        } else {
            val context = appContext ?: return
            ioScope.launch { startAutoflowAtQueueEnd(context) }
        }
    }

    fun onPlaybackError(errorCodeName: String) {
        android.util.Log.e("PlayerController", "Playback error: $errorCodeName")
        _isBuffering.value = false
        _isPlaying.value = false

        // ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED почти всегда означает одно: VK
        // отдал HLS-плейлист, а элемент очереди собирался без MIME, и фабрика
        // выбрала progressive-экстрактор. Стартовый трек получает MIME сразу (его
        // ссылка резолвится до сборки очереди), а вот следующие резолвятся на ходу
        // — для них тип заранее неизвестен. Поэтому пересобираем текущий элемент,
        // когда ссылка уже в кэше и точно оказалась m3u8.
        if (errorCodeName == "ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED") {
            retryCurrentAsHlsIfNeeded()
        }
    }

    /**
     * Пересобрать текущий элемент очереди с явным HLS-типом и продолжить.
     *
     * Однократно на трек: если и после пересборки не заиграло, дело не в MIME, и
     * повторять бессмысленно — иначе получится цикл.
     */
    private fun retryCurrentAsHlsIfNeeded() {
        val trackId = queue.getOrNull(currentIndex)?.id ?: return
        if (!hlsRetriedTrackIds.add(trackId)) return
        val resolved = streamUrlCache[trackId]?.uri?.toString() ?: return
        if (!com.lmg.vk.audio.HlsDownloader.isHlsUrl(resolved)) return

        DebugLog.add("HLS: пересобираю $trackId с APPLICATION_M3U8")
        ioScope.launch(Dispatchers.Main) {
            val ctrl = controller ?: return@launch
            val track = queue.getOrNull(currentIndex) ?: return@launch
            // ВАЖНО: элемент строим через buildMediaItem, а не с нуля. Своя сборка
            // теряла MediaMetadata (название, артист, обложка) — и тогда в шторке и
            // на экране блокировки вместо карточки трека показывалась системная
            // заглушка «Запущено приложение LMG VK». Симптом был характерный:
            // первый трек очереди выглядел правильно (он собирается в
            // playFromList), а все последующие — нет, потому что проходили здесь.
            //
            // Прямую ссылку передаём вторым аргументом: buildMediaItem сам увидит
            // http-схему, положит её в PARAM_URL и выставит MIME по isHlsUrl.
            val item = buildMediaItem(track, Uri.parse(resolved))
            runCatching {
                ctrl.replaceMediaItem(currentIndex, item)
                ctrl.prepare()
                ctrl.play()
            }.onFailure { DebugLog.add("HLS: пересборка не удалась: ${it.message}") }
        }
    }

    /** Треки, для которых пересборка под HLS уже пробовалась — защита от цикла. */
    private val hlsRetriedTrackIds = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    )

    fun setQueue(tracks: List<Track>, startIndex: Int = 0) {
        invalidateAutoflow()
        if (tracks.isEmpty()) return
        val boundedQueue = boundQueue(tracks, startIndex.coerceIn(tracks.indices))
        val immutableTracks = boundedQueue.tracks
        queue = immutableTracks
        _queueFlow.value = immutableTracks
        currentIndex = boundedQueue.startIndex
        resetSections(currentIndex, immutableTracks.size)

        // Also register tracks to endlessEngine if context is global
        if (_playbackContext is PlaybackContext.Global) {
            endlessEngine.registerTracks(immutableTracks.map { it.id })
        }
    }

    fun getCurrentQueue(): List<Track> = queue
    fun getCurrentIndex(): Int = currentIndex

    /** 0 — ручная секция, 1 — остаток подборки, 2 — волна. */
    private fun sectionIndexOf(i: Int): Int = when {
        i < manualEnd -> 0
        i < autoStart -> 1
        else -> 2
    }

    /**
     * Очистить ручную секцию (кнопка в заголовке «Далее»). Играющий трек и всё
     * остальное не трогаем.
     */
    fun clearManualSection() {
        val lo = (currentIndex + 1).coerceIn(0, queue.size)
        if (manualEnd <= lo) return
        val count = manualEnd - lo
        queue = queue.toMutableList().apply { subList(lo, manualEnd).clear() }
        manualEnd = lo
        autoStart = (autoStart - count).coerceAtLeast(lo)
        _queueFlow.value = queue
        publishSections()
        maybePreloadAutoflow()
        mainScope.launch {
            val player = controller ?: appContext?.let { getPlayer(it) } ?: return@launch
            if (lo < player.mediaItemCount) {
                player.removeMediaItems(lo, (lo + count).coerceAtMost(player.mediaItemCount))
            }
        }
    }

    fun clearQueueAfterCurrent() {
        val from = (currentIndex + 1).coerceIn(0, queue.size)
        if (from >= queue.size) return
        queue = queue.take(from)
        manualEnd = from
        autoStart = from
        _queueFlow.value = queue
        publishSections()
        invalidateAutoflow()
        maybePreloadAutoflow()
        mainScope.launch {
            val player = controller ?: appContext?.let { getPlayer(it) } ?: return@launch
            if (from < player.mediaItemCount) player.removeMediaItems(from, player.mediaItemCount)
        }
    }

    /**
     * «В очередь» из меню трека — в конец РУЧНОЙ секции, а не в самый хвост.
     *
     * В хвосте трек оказывался за остатком альбома и за всем, что подобрала
     * волна, то есть «в очередь» на деле означало «когда-нибудь потом».
     */
    fun addToQueue(track: Track) {
        insertManual(track, (currentIndex + 1).coerceAtLeast(manualEnd))
    }

    /** Вставить трек СЛЕДУЮЩИМ после текущего (контекст-меню «Play next»).
     *  В отличие от [playNext], не трогает воспроизведение и не кидает в конец. */
    fun insertNext(track: Track) {
        insertManual(track, currentIndex + 1)
    }

    /**
     * Вернуть удалённый свайпом трек на его место (кнопка «Undo»).
     *
     * Отдельно от [addToQueue]: тот кладёт в конец ручной секции, а здесь нужно
     * именно исходное место, иначе отмена возвращает трек не туда, откуда его
     * убрали.
     */
    fun restoreToQueue(track: Track, index: Int) {
        insertManual(track, index)
    }

    /**
     * Ручная вставка: трек попадает в ручную секцию, её граница едет вправе,
     * а вместе с ней и начало волны — иначе секции разъедутся на один трек.
     *
     * Анти-дубль здесь намеренно не применяем (в отличие от [addTracksToQueue]):
     * поставить руками трек, который уже стоит где-то дальше, — законное желание.
     */
    private fun insertManual(track: Track, at: Int) {
        var idx = at.coerceIn(0, queue.size)
        var evictedIndex: Int? = null
        if (queue.size >= MAX_PLAYER_QUEUE_ITEMS) {
            // Manual additions are rare but must obey the same hard bound. Drop
            // the oldest played item when possible; otherwise replace the most
            // distant tail item so the requested Play next/Add to queue action
            // is never ignored.
            val removedIndex = if (currentIndex > RETAIN_PLAYED_QUEUE_ITEMS) {
                0
            } else {
                queue.lastIndex
            }
            evictedIndex = removedIndex
            val removedId = queue[removedIndex].id
            queue = queue.toMutableList().apply { removeAt(removedIndex) }
            if (removedIndex < currentIndex) currentIndex--
            if (removedIndex < manualEnd) manualEnd--
            if (removedIndex < autoStart) autoStart--
            if (removedIndex < idx) idx--
            preShuffleOrder = preShuffleOrder?.toMutableList()?.apply {
                val orderIndex = indexOf(removedId)
                if (orderIndex >= 0) removeAt(orderIndex)
            }
        }
        queue = queue.toMutableList().apply { add(idx, track) }
        if (idx <= manualEnd) manualEnd++
        if (idx <= autoStart) autoStart++
        _queueFlow.value = queue
        publishSections()
        maybePreloadAutoflow()
        mainScope.launch {
            val player = controller ?: appContext?.let { getPlayer(it) }
            evictedIndex?.let { removed ->
                if (player != null && removed < player.mediaItemCount) {
                    player.removeMediaItem(removed)
                }
            }
            if (player != null && idx <= player.mediaItemCount)
                player.addMediaItem(idx, buildMediaItem(track, track.uri))
            else
                player?.addMediaItem(buildMediaItem(track, track.uri))
        }
    }

    /** Переставить трек в очереди (drag-reorder в шторке Queue). Текущий не двигаем. */
    fun moveQueueItem(from: Int, to: Int) {
        if (from == to || from !in queue.indices || to !in queue.indices) return
        if (from == currentIndex) return
        val playingId = _currentTrack.value?.id
        // Трек, перетащенный в другую секцию, должен в ней и остаться: двигаем
        // ту границу, через которую он перешёл. Считаем ДО перестановки, пока
        // индексы ещё соответствуют границам.
        val fromSec = sectionIndexOf(from)
        val toSec = sectionIndexOf(to)
        if (fromSec != toSec) {
            if (toSec < fromSec) {
                if (toSec == 0) manualEnd++
                if (toSec <= 1 && fromSec == 2) autoStart++
            } else {
                if (fromSec == 0) manualEnd--
                if (fromSec <= 1 && toSec == 2) autoStart--
            }
        }
        queue = queue.toMutableList().apply { add(to, removeAt(from)) }
        // Текущий индекс мог сдвинуться — восстанавливаем по id играющего трека.
        queue.indexOfFirst { it.id == playingId }.takeIf { it >= 0 }?.let { currentIndex = it }
        _queueFlow.value = queue
        publishSections()
        maybePreloadAutoflow()
        mainScope.launch {
            val player = controller ?: appContext?.let { getPlayer(it) }
            if (player != null && from < player.mediaItemCount && to < player.mediaItemCount) {
                player.moveMediaItem(from, to)
            }
        }
    }

    /** Убрать трек из очереди (свайп в шторке Queue). Текущий не убираем. */
    fun removeQueueItem(index: Int) {
        if (index !in queue.indices || index == currentIndex) return
        val playingId = _currentTrack.value?.id
        if (index < manualEnd) manualEnd--
        if (index < autoStart) autoStart--
        queue = queue.toMutableList().apply { removeAt(index) }
        queue.indexOfFirst { it.id == playingId }.takeIf { it >= 0 }?.let { currentIndex = it }
        _queueFlow.value = queue
        publishSections()
        maybePreloadAutoflow()
        mainScope.launch {
            val player = controller ?: appContext?.let { getPlayer(it) }
            if (player != null && index < player.mediaItemCount) player.removeMediaItem(index)
        }
    }

    fun setAutoRefillContext(type: String, id: String, name: String, seedTrackId: String? = null) {
        val newContext = when {
            type.equals("library", ignoreCase = true) && id.equals("downloads", ignoreCase = true) ->
                PlaybackContext.Downloads
            type.equals("playlist", ignoreCase = true) ->
                PlaybackContext.Playlist(id)
            type.equals("album", ignoreCase = true) ->
                PlaybackContext.Album(id)
            type.equals("artist", ignoreCase = true) ->
                PlaybackContext.Artist(id)
            else -> PlaybackContext.Global
        }
        _playbackContext = newContext
        _playbackQueueConfig = if (newContext is PlaybackContext.Global) {
            PlaybackQueueConfig.VK_MIX_CONFIG
        } else {
            PlaybackQueueConfig.MUSIC_CONFIG
        }
        android.util.Log.d(
            "VOIDPIXEL_MEDIA",
            "[CONTEXT_SET] setAutoRefillContext: type=$type, id=$id, name=$name, " +
                "seedTrackId=$seedTrackId -> context=$newContext queueConfig=$_playbackQueueConfig",
        )

        endlessEngine.reset()
        if (newContext is PlaybackContext.Global) {
            val refillType = try {
                EndlessPlaybackEngine.RefillContext.Type.valueOf(type.uppercase())
            } catch (e: Exception) {
                EndlessPlaybackEngine.RefillContext.Type.WAVE
            }
            endlessEngine.setRefillContext(
                EndlessPlaybackEngine.RefillContext(
                    type = refillType,
                    id = id,
                    name = name,
                    seedTrackId = seedTrackId
                )
            )
            endlessEngine.registerTracks(queue.map { it.id })
        }
    }

    fun clearAutoRefillContext() {
        _playbackContext = PlaybackContext.Global
        _playbackQueueConfig = PlaybackQueueConfig.MUSIC_WITHOUT_SOURCE_CONFIG
        endlessEngine.reset()
        android.util.Log.d("VOIDPIXEL_MEDIA", "[CONTEXT_CLEAR] Context cleared, reset to Global")
    }

    fun playNext(track: Track, context: Context) {
        addToQueue(track)
        mainScope.launch {
            val player = controller ?: appContext?.let { getPlayer(it) }
            if (player != null && !player.isPlaying && player.mediaItemCount > 0) {
                playTrackById(context, track.id)
            }
        }
    }

    suspend fun getValidStreamUri(trackId: String): Uri? {
        return when (val result = resolveStreamUrl(trackId)) {
            is StreamResult.Success -> result.uri
            else -> null
        }
    }

    private sealed class StreamResult {
        data class Success(val uri: Uri) : StreamResult()
        data class Error(val code: String, val message: String?) : StreamResult()
    }

    /**
     * Текст ошибки резолва стрима для пользователя.
     *
     * ПОЧЕМУ ОТДЕЛЬНАЯ ФУНКЦИЯ. Раньше здесь звался
     * `backendUserMessage(0, code.toIntOrNull() ?: 0)`, но `StreamResult.Error.code`
     * — СТРОКА («region_unavailable», «track_not_found», «network_error»…), и
     * `toIntOrNull()` на ней ВСЕГДА даёт null. То есть подставлялся код 0, а он
     * означает «нет сети» — пользователь при любой причине видел «Нет
     * подключения к интернету», даже когда интернет есть. Ровно эта жалоба и
     * пришла с устройства.
     */
    private fun streamErrorMessage(error: StreamResult.Error): String = when (error.code) {
        "region_unavailable" -> "Трек недоступен в вашем регионе"
        "source_not_allowed" -> "VK не даёт доступ к этой записи"
        "track_not_found" -> "Трек не найден у VK"
        "early_access" -> "Трек ещё не вышел — ранний доступ"
        "unsupported_source" -> "Этот трек не из VK, воспроизведение недоступно"
        "network_error" -> "Нет подключения к интернету или сеть недоступна"
        // Не выдумываем причину: показываем то, что вернул VK, а если он молчит —
        // говорим прямо, что причина неизвестна.
        else -> error.message?.takeIf { it.isNotBlank() }
            ?: "Не удалось получить ссылку на трек (${error.code})"
    }

    // Коды резолва стрима, при которых повтор бесполезен — трек не появится.
    // Используются предзагрузкой, чтобы не жечь 3 попытки на битом треке.
    private val TERMINAL_RESOLVE_ERRORS = setOf(
        "track_not_found", "source_not_allowed", "region_unavailable", "early_access"
    )

    private data class CachedStreamUrl(
        val uri: Uri,
        val fileId: String?
    )

    fun resolveStreamUrlSync(trackId: String): Uri? {
        val cached = streamUrlCache[trackId]

        if (cached != null) {
            UiLogger.log("[SYNC] Cache hit for $trackId")
            return cached.uri
        }

        if (!VkAudioIdentity.isFullId(trackId)) return null

        return try {
            val quality = getEffectiveQuality(trackId)
            // Здесь была та же ошибка, что и в асинхронном пути: `getTrackInfoSync`
            // возвращает NON-NULL и при отказе бросает, поэтому `if (trackInfo != null)`
            // всегда истинно, а `else` был мёртвым. Функционально это ничего не
            // меняло (исключение всё равно уходило в catch → null), но читать код
            // так, будто ветка работает, нельзя.
            val trackInfo = MusicBackend.getTrackInfoSync(trackId, quality = quality ?: "lossless")
            val uri = Uri.parse(trackInfo.url)

            streamUrlCache[trackId] = CachedStreamUrl(
                uri = uri,
                fileId = trackInfo.fileId
            )
            uri
        } catch (e: Exception) {
            // Синхронный путь работает только по кэшу и вызывается из
            // StreamingDataSource. Причину пишем в лог: без неё «молчащий плеер»
            // невозможно отличить от «трек ещё не резолвился».
            DebugLog.add("resolveStreamUrlSync($trackId) неудача: ${e.message}")
            null
        }
    }

    private suspend fun resolveStreamUrl(trackId: String): StreamResult {
        val cached = streamUrlCache[trackId]
        if (cached != null) {
            return StreamResult.Success(cached.uri)
        }

        // Уже резолвится этот трек — присоединяемся к тому же результату.
        inFlightResolves[trackId]?.let { return it.await() }

        val deferred = ioScope.async(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            doResolveStreamUrl(trackId)
        }
        val winner = inFlightResolves.putIfAbsent(trackId, deferred) ?: deferred
        if (winner !== deferred) {
            // Проиграли гонку постановки — ждём чужой (уже запущенный) резолв.
            return winner.await()
        }
        deferred.start()
        return try {
            deferred.await()
        } finally {
            inFlightResolves.remove(trackId, deferred)
        }
    }

    private suspend fun doResolveStreamUrl(trackId: String): StreamResult {
        if (!VkAudioIdentity.isFullId(trackId)) {
            return StreamResult.Error("unsupported_source", "Only VK audio ids are supported")
        }
        return try {
            withTimeout(15_000) {
                val quality = getEffectiveQuality(trackId)
                // `getTrackInfo` возвращает NON-NULL `StreamInfo` и при отказе
                // БРОСАЕТ `BackendException`. Раньше здесь стояла проверка
                // `if (trackInfo != null)`, из-за которой весь разбор ошибок ниже
                // был мёртвым кодом: условие всегда истинно, а исключение улетало
                // в общий catch и любая причина превращалась в "network_error".
                // Поэтому ловим явно и классифицируем по коду.
                val trackInfo = runCatching {
                    MusicBackend.getTrackInfo(trackId, quality = quality ?: "lossless")
                }
                trackInfo.getOrNull()?.let { return@withTimeout cacheAndReturn(trackId, it) }

                val failure = trackInfo.exceptionOrNull()
                val apiException = MusicBackend.lastApiException.value
                // Код берём из самого исключения: `lastApiException` — общее
                // состояние и к моменту разбора могло быть перезаписано другим
                // запросом. Текст оставляем как дополнение к коду.
                val code = (failure as? BackendException)?.code ?: apiException?.code
                val error = failure?.message ?: MusicBackend.lastError.value

                when {
                    code == 451 || error?.contains("region_unavailable") == true -> {
                        val requiredRegion = apiException?.requiredRegion
                        val retried = if (requiredRegion != null) {
                            runCatching {
                                MusicBackend.getTrackInfo(
                                    trackId,
                                    quality = quality ?: "lossless",
                                    region = requiredRegion,
                                )
                            }.getOrNull()
                        } else null
                        when {
                            retried != null -> cacheAndReturn(trackId, retried)
                            // 451 — это и «ограничение на прослушивание», ради
                            // которого существует обходной путь.
                            else -> tryAudioRipFallback(trackId, error)
                                ?: StreamResult.Error("region_unavailable", error)
                        }
                    }
                    code == 403 || error?.contains("source_not_allowed") == true -> {
                        // Отказ по доступу — второй случай, где уместен audio_rip.
                        tryAudioRipFallback(trackId, error)
                            ?: StreamResult.Error("source_not_allowed", error)
                    }
                    code == 404 || error?.contains("track_not_found") == true -> {
                        // 404 бывает и когда VK не отдал URL вовсе — тогда обходной
                        // путь тоже имеет смысл, решает shouldFallback.
                        tryAudioRipFallback(trackId, error)
                            ?: StreamResult.Error("track_not_found", error)
                    }
                    error?.contains("early_access") == true -> {
                        StreamResult.Error("early_access", error)
                    }
                    else -> {
                        tryAudioRipFallback(trackId, error)
                            ?: StreamResult.Error("unknown", error)
                    }
                }
            }
        } catch (e: Exception) {
            StreamResult.Error("network_error", e.message)
        }
    }

    /**
     * Последняя попытка получить ссылку, когда обычный API отказал по доступу или
     * ограничению: обходной путь `audio_rip` из VK MP3 Mod.
     *
     * Вызывается ТОЛЬКО из error-ветки [doResolveStreamUrl] и никогда параллельно
     * основному пути — механизм публикует комментарий от имени пользователя, и
     * дёргать его «на всякий случай» нельзя. Сетевые сбои и капча сюда не
     * попадают: их отбирает [AudioRipFallback.shouldFallback], а повторы делает
     * сам `VkApiClient`.
     *
     * Возвращает `null`, если фолбэк не применим или тоже не смог — тогда наверх
     * уходит исходная ошибка, а не выдуманный успех.
     */
    private suspend fun tryAudioRipFallback(trackId: String, error: String?): StreamResult? {
        val code = MusicBackend.lastApiException.value?.code
        if (!com.lmg.vk.audio.AudioRipFallback.shouldFallback(code, error)) return null
        val ripped = runCatching { com.lmg.vk.audio.AudioRipFallback.resolveUrl(trackId) }
            .getOrNull()
            ?: return null
        DebugLog.add("audio_rip: ссылка получена обходным путём для $trackId")
        // Собираем StreamInfo вручную: обходной путь отдаёт только URL, а
        // остальные поля берём из запроса. `expiresAt = 0` означает «срок
        // неизвестен», и кэш применит к ней штатный TTL.
        return cacheAndReturn(
            trackId,
            StreamInfo(
                trackId = trackId,
                source = "audio_rip",
                quality = "unknown",
                url = ripped,
                expiresAt = 0L,
            ),
        )
    }

    private fun cacheAndReturn(trackId: String, trackInfo: StreamInfo): StreamResult {
        val uri = Uri.parse(trackInfo.url)

        streamUrlCache[trackId] = CachedStreamUrl(
            uri = uri,
            fileId = trackInfo.fileId
        )
        return StreamResult.Success(uri)
    }

    fun handleExpiredUrl(context: Context, trackId: String) {
        ioScope.launch {
            streamUrlCache.remove(trackId)
            val result = resolveStreamUrl(trackId)
            if (result is StreamResult.Success) {
                withContext(Dispatchers.Main) {
                    val player = getPlayer(context) ?: return@withContext
                    val currentMediaItem = player.currentMediaItem
                    if (currentMediaItem?.mediaId == trackId) {
                        val currentQueue = queue
                        val track = currentQueue.find { it.id == trackId } ?: return@withContext
                        val currentPosition = player.currentPosition
                        val newItem = buildMediaItem(track, result.uri)
                        val targetIndex = (0 until player.mediaItemCount).indexOfFirst {
                            player.getMediaItemAt(it).mediaId == trackId
                        }
                        if (targetIndex != -1) {
                            player.replaceMediaItem(targetIndex, newItem)
                            player.seekTo(targetIndex, currentPosition)
                            player.prepare()
                            player.playWhenReady = true
                        }
                    }
                }
            }
        }
    }

    fun onNetworkRouteChanged() {
        streamUrlCache.clear()
        inFlightResolves.values.forEach { it.cancel() }
        inFlightResolves.clear()
        MusicBackend.clearStreamCache()
        networkRouteJob?.cancel()
        networkRouteJob = mainScope.launch {
            delay(750)
            val player = controller ?: return@launch
            val trackId = player.currentMediaItem?.mediaId ?: return@launch
            val track = queue.firstOrNull { it.id == trackId } ?: return@launch
            if (!track.isOnlineTrack || !player.playWhenReady) return@launch
            val position = player.currentPosition
            val result = resolveStreamUrl(trackId)
            if (result !is StreamResult.Success) return@launch
            if (player.currentMediaItem?.mediaId != trackId || !player.playWhenReady) return@launch
            val targetIndex = (0 until player.mediaItemCount).indexOfFirst {
                player.getMediaItemAt(it).mediaId == trackId
            }
            if (targetIndex < 0) return@launch
            player.replaceMediaItem(targetIndex, buildMediaItem(track, result.uri))
            player.seekTo(targetIndex, position)
            player.prepare()
            player.playWhenReady = true
        }
    }

    private fun getEffectiveQuality(trackId: String): String? {
        val premium = com.lmg.vk.engine.backend.MusicAuth.isPremium.value
        return com.lmg.vk.engine.backend.MusicAuth.getEffectiveQuality("lossless", premium)
    }

    // ═══════════════════════════════════════════════════════════
    //  Playback Logging
    // ═══════════════════════════════════════════════════════════

    private fun resetPlaybackLogging(durationMs: Long) {
        playbackStartTimeMs = System.currentTimeMillis()
        totalPlayedMs = 0L
        lastPositionMs = 0L
        preloadDoneForIndex = -1
    }

    private fun logPreviousTrack(track: Track, playedMs: Long) {
        val playbackAccountId = queueAccountId
        val playedSec = playedMs / 1000f

        val isCompleted = playedMs >= 30_000L
        val isSkipped = !isCompleted

        // Determine if it was skipped for the recommendation engine (less than 15% played)
        val isSkippedForServer = if (track.durationMs > 0L) playedMs < 0.15f * track.durationMs else isSkipped
        if (isSkippedForServer) {
            _consecutiveSkips++
        } else {
            _consecutiveSkips = 0
        }

        val sourceStr = playbackSourceName()

        android.util.Log.d("PlayerController", "[LOG_PREVIOUS] track=${track.title} | played=${playedMs}ms | isCompleted=$isCompleted | consecutiveSkips=$_consecutiveSkips | source=$sourceStr")

        appContext?.let { ctx ->
            ioScope.launch {
                try {
                    val repo = WaveRepository.getInstance(ctx)
                    if (isCompleted) {
                        repo.logListening(playbackAccountId, track, playedMs, sourceStr)
                        repo.logTrackPlayed(playbackAccountId, track, playedMs, sourceStr)
                    } else {
                        repo.logTrackSkipped(playbackAccountId, track, playedMs, sourceStr)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PlayerController", "Room logging failed for ${track.title}", e)
                }
            }
        }

        val isWaveContext = _playbackContext is PlaybackContext.Global
        ioScope.launch {
            try {
                com.lmg.vk.engine.backend.WaveSignalQueue.sendPlayback(
                    accountId = playbackAccountId,
                    trackId = track.id,
                    playedSeconds = playedSec.toDouble(),
                    completed = if (track.durationMs > 0L) playedMs >= 0.85f * track.durationMs else isCompleted,
                    skipped = isSkippedForServer && isWaveContext,
                    source = sourceStr,
                    shuffle = _shuffleEnabled.value,
                    repeat = repeatStatName(),
                    streamingType = streamingType(track),
                    streamingUrlType = streamingUrlType(track),
                )
            } catch (_: Exception) {}
        }

    }

    fun logFinalPlayback() {
        val track = _currentTrack.value
        endMixPromptPlayback("session_terminated")
        if (track != null && totalPlayedMs > 0L) {
            logPreviousTrack(track, totalPlayedMs)
            resetPlaybackLogging(0L)
            _currentTrack.value = null
        }
    }

    private fun finishPlaybackForNewStart(): String? {
        val previous = _currentTrack.value ?: return null
        if (totalPlayedMs > 0L) logPreviousTrack(previous, totalPlayedMs)
        return previous.id
    }

    private fun recordPlaybackStart(track: Track, previousTrackId: String?) {
        com.lmg.vk.engine.backend.WaveSignalQueue.sendStart(
            accountId = queueAccountId,
            trackId = track.id,
            source = playbackSourceName(),
            shuffle = _shuffleEnabled.value,
            repeat = repeatStatName(),
            streamingType = streamingType(track),
            streamingUrlType = streamingUrlType(track),
            previousTrackId = previousTrackId,
        )
    }

    private fun playbackSourceName(): String = when (_playbackContext) {
        is PlaybackContext.Downloads -> "downloads"
        is PlaybackContext.Catalog -> "catalog"
        is PlaybackContext.Playlist -> "playlist"
        is PlaybackContext.Album -> "album"
        is PlaybackContext.Artist -> "artist"
        is PlaybackContext.OwnerAudio -> "playlist"
        is PlaybackContext.VkMix -> "vk_mix"
        is PlaybackContext.Global -> "wave"
    }

    private fun repeatStatName(): String = when (_repeatMode.value) {
        1 -> "all"
        2 -> "one"
        else -> "none"
    }

    private fun streamingType(track: Track): String = when {
        !VkAudioIdentity.isFullId(track.id) -> "none"
        track.isOnlineTrack -> "online"
        else -> "offline"
    }

    private fun streamingUrlType(track: Track): String {
        val value = streamUrlCache[track.id]?.uri?.toString() ?: track.uri.toString()
        return when {
            com.lmg.vk.audio.HlsDownloader.isHlsUrl(value) -> "hls"
            value.substringBefore('?').endsWith(".mp3", ignoreCase = true) -> "mp3"
            value.substringBefore('?').endsWith(".aac", ignoreCase = true) -> "aac"
            else -> "none"
        }
    }

    /**
     * Воспроизвести видеоклип (Apple Music) как обычный трек: mp4-[streamUrl]
     * содержит и видео, и аудио, играет основным ExoPlayer. В FullPlayer при
     * isVideoClip обложка заменяется на Surface (см. attachVideoSurface).
     */
    fun playClip(
        context: Context,
        streamUrl: String,
        clipId: String,
        title: String,
        artist: String,
        thumbnail: String?,
    ) {
        ioScope.launch {
            endlessEngine.reset()
            _playbackContext = PlaybackContext.Downloads
            _playbackQueueConfig = PlaybackQueueConfig.MUSIC_CONFIG
            withContext(Dispatchers.Main) {
                val player = getPlayer(context) ?: return@withContext
                val item = MediaItem.Builder()
                    .setMediaId("clip_$clipId")
                    .setUri(Uri.parse(streamUrl))   // прямой подписанный mp4
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(title)
                            .setArtist(artist)
                            .apply { thumbnail?.let { setArtworkUri(Uri.parse(it)) } }
                            .build()
                    )
                    .build()
                player.stop()
                player.clearMediaItems()
                player.setMediaItem(item)
                player.prepare()
                player.play()

                _playbackBackend.value = PlaybackBackend.EXO_STREAMING
                _currentTrack.value = Track(
                    id = "clip_$clipId", title = title, artist = artist, albumName = "",
                    uri = Uri.parse(streamUrl), durationMs = 0L, albumId = -1L,
                    coverUrl = thumbnail, source = "clip"
                )
                _currentPositionMs.value = 0L
                _isBuffering.value = true
                _isVideoClip.value = true
                _videoAspect.value = 16f / 9f   // до прихода onVideoSizeChanged
            }
        }
    }

    /**
     * Проиграть ОДИН сниппет-фрагмент из ленты `audio.getSnippets` по уже
     * подписанному VK URL.
     *
     * Почему нельзя обойтись [playFromList] (единственная причина правки
     * PlayerController — зона не моя, поэтому точка одна и максимально узкая):
     *  1) Сниппет — это КОРОТКИЙ поток, который VK отдаёт прямо в `audio.url`.
     *     [buildMediaItem] для онлайн-трека подменяет uri на схему `liquid://`,
     *     и [StreamingDataSource] по id трека резолвит URL заново через
     *     `audio.getById` — то есть возвращает ПОЛНЫЙ трек. Фрагмент при этом
     *     молча превращался бы в обычное воспроизведение целиком.
     *  2) Очередь из одного трека мгновенно попадает под порог дозаправки
     *     (`REFILL_THRESHOLD = 40`), и EndlessPlaybackEngine дописал бы в фид
     *     40 треков «волны». В ленте сниппетов очередь обязана быть ровно та,
     *     что листает пользователь.
     *
     * Поэтому здесь: прямой uri без `liquid://`, `PlaybackContext.Downloads`,
     * конечный `MUSIC_CONFIG` и пустой refill-контекст.
     *
     */
    fun playSnippet(
        context: Context,
        trackId: String,
        streamUrl: String,
        title: String,
        artist: String,
        coverUrl: String?,
        durationMs: Long,
    ) {
        val uri = Uri.parse(streamUrl)
        val track = Track(
            id = trackId,
            title = title,
            artist = artist,
            albumName = "",
            uri = uri,
            durationMs = durationMs,
            albumId = -1L,
            coverUrl = coverUrl,
            source = "vk",
        )
        _isVideoClip.value = false
        ioScope.launch {
            endlessEngine.reset()
            _playbackContext = PlaybackContext.Downloads
            _playbackQueueConfig = PlaybackQueueConfig.MUSIC_CONFIG
            _playbackBackend.value = PlaybackBackend.EXO_STREAMING

            withContext(Dispatchers.Main) {
                val player = getPlayer(context) ?: return@withContext
                // Прямой mediaId = id трека: onTrackChanged найдёт его в очереди
                // и корректно обновит UI/виджет, как для обычного трека.
                val metadata = MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumArtist(artist)
                    .apply {
                        val cover = com.lmg.vk.ui.glass.ArtworkSourceResolver.realCoverOrNull(coverUrl)
                        if (cover != null) {
                            setArtworkUri(Uri.parse(cover))
                        }
                        if (durationMs > 0L) setDurationMs(durationMs)
                    }
                    .build()
                val item = MediaItem.Builder()
                    .setMediaId(trackId)
                    .setUri(uri)   // подписанный VK URL как есть, без liquid://
                    .setMediaMetadata(metadata)
                    .build()

                queue = listOf(track)
                _queueFlow.value = queue
                currentIndex = 0
                resetSections(0, 1)
                _currentTrack.value = track
                _durationMs.value = durationMs
                _currentPositionMs.value = 0L
                _isBuffering.value = true

                player.stop()
                player.clearMediaItems()
                player.setMediaItem(item)
                player.prepare()
                player.play()
                resetPlaybackLogging(durationMs)
            }
        }
    }

    /** Привязать/отвязать Surface для вывода видеоклипа (фуллскрин — как у Apple:
     *  SurfaceView, отдельный слой, быстрее). */
    fun attachVideoSurface(surfaceView: android.view.SurfaceView?) {
        val c = controller ?: return
        if (surfaceView != null) c.setVideoSurfaceView(surfaceView) else c.clearVideoSurface()
    }

    /** Инлайн-видео в карточке плеера — TextureView (как у Apple в
     *  NowPlayingContentView): клипается по скруглённым углам и морфит с
     *  карточкой, в отличие от SurfaceView. */
    fun attachVideoTextureView(textureView: android.view.TextureView?) {
        val c = controller ?: return
        if (textureView != null) c.setVideoTextureView(textureView) else c.clearVideoSurface()
    }

    // Аспект видеоклипа из реального размера потока (onVideoSizeChanged), а не
    // хардкод 16:9 — как AspectRatioFrameLayout.setAspectRatio у Apple.
    private val _videoAspect = MutableStateFlow(16f / 9f)
    val videoAspect: StateFlow<Float> = _videoAspect

    private fun buildMediaItem(track: Track, uri: Uri = track.uri): MediaItem {
        val mediaUri = if (track.isOnlineTrack && uri.scheme != "file") {
            Uri.Builder()
                .scheme(StreamingDataSource.SCHEME_LIQUID)
                .authority("track")
                .appendQueryParameter(StreamingDataSource.PARAM_TRACK_ID, track.id)
                .apply {
                    // Прямую ссылку кладём ТОЛЬКО если она есть. У онлайн-трека
                    // почти со всех экранов `uri` = Uri.EMPTY
                    // (`VkAudioIdentity.playbackUri()` без аргумента), и пустой
                    // PARAM_URL раньше уезжал в DataSource как «ссылка есть» —
                    // тот пытался её открыть и падал уже внутри, вместо того чтобы
                    // сразу пойти за резолвом по trackId.
                    val direct = uri.toString()
                    if (direct.startsWith("http://") || direct.startsWith("https://")) {
                        appendQueryParameter(StreamingDataSource.PARAM_URL, direct)
                    }
                }
                .build()
        } else {
            uri
        }

        val metaBuilder = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumArtist(track.artist)

        // Stock-placeholder VK не является artwork. VK-generated thumb имеет
        // другой CDN URL и проходит эту проверку как полноценная обложка.
        val cover = com.lmg.vk.ui.glass.ArtworkSourceResolver.realCoverOrNull(track.coverUrl)
        if (cover != null) {
            metaBuilder.setArtworkUri(track.displayArtUri)
        }

        if (track.durationMs > 0) {
            metaBuilder.setDurationMs(track.durationMs)
        }

        val item = MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(mediaUri)
            .apply {
                // ЗАЧЕМ ЭТО НУЖНО. VK отдаёт часть треков (а на некоторых
                // аккаунтах — все) не прямой ссылкой на mp3, а плейлистом HLS
                // (`.m3u8`). Наш URI имеет схему `liquid://` и расширения не
                // несёт, поэтому `DefaultMediaSourceFactory` определял тип по
                // содержимому и выбирал progressive-экстрактор. Итог в логе:
                // ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED и перечисление всех
                // экстракторов, среди которых HLS нет вовсе — HLS это отдельный
                // тип источника (`HlsMediaSource`), а не экстрактор.
                //
                // Явный MIME заставляет фабрику взять HlsMediaSource. Модуль
                // `media3-exoplayer-hls` в проекте уже подключён.
                //
                // Тип берём из уже резолвленной ссылки: к моменту сборки очереди
                // трек мог быть резолвлен ранее (кэш). Если нет — оставляем без
                // MIME, и тогда сработает второй путь: DataSource, обнаружив
                // m3u8, сообщит об этом, и элемент будет пересобран (см.
                // onPlaybackError → handleUnsupportedContainer).
                val known = streamUrlCache[track.id]?.uri?.toString()
                    ?: uri.toString().takeIf { it.startsWith("http") }
                if (known != null && com.lmg.vk.audio.HlsDownloader.isHlsUrl(known)) {
                    setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                }
            }
            .setMediaMetadata(metaBuilder.build())
            .build()

        return item
    }

    private suspend fun getPlayer(context: Context): MediaController? {
        controller?.let { return it }
        if (System.currentTimeMillis() < mediaControllerRetryAfterMs) return null

        try {
            val serviceIntent = android.content.Intent(context.applicationContext, AudioService::class.java)
            // startService, НЕ startForegroundService (P0, аудит): FGS-старт вешает
            // контракт «startForeground за 5-10с», а media3 зовёт startForeground
            // только когда музыка РЕАЛЬНО заиграла. Медленный сетевой резолв (>10с),
            // ошибка резолва (Toast без плейбека) или шаффл-тумблер без плейбека →
            // ForegroundServiceDidNotStartInTimeException убивал ВСЁ приложение.
            // Обычный startService контракта не вешает; MediaController тут же
            // биндит сервис, а FGS-промоушен media3 делает сам при старте плейбека.
            context.applicationContext.startService(serviceIntent)
        } catch (_: Exception) {}

        while (isConnectingController) {
            delay(50)
            controller?.let { return it }
        }

        isConnectingController = true
        return try {
            val sessionToken = SessionToken(
                context.applicationContext,
                ComponentName(context.applicationContext, AudioService::class.java)
            )
            val builtController = try {
                withTimeout(6_000) {
                    suspendCancellableCoroutine<MediaController?> { continuation ->
                        val future = MediaController.Builder(
                            context.applicationContext, sessionToken
                        ).buildAsync()
                        future.addListener({
                            try {
                                val result = future.get()
                                if (continuation.isActive) continuation.resume(result)
                            } catch (_: Throwable) {
                                if (continuation.isActive) continuation.resume(null)
                            }
                        }, MoreExecutors.directExecutor())
                    }
                }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                mediaControllerRetryAfterMs = System.currentTimeMillis() + 30_000L
                null
            }
            builtController?.let {
                controller = it
                it.addListener(PlayerStateBridge())
            }
            builtController
        } finally {
            isConnectingController = false
        }
    }

    private class PlayerStateBridge : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            _isBuffering.value = (playbackState == Player.STATE_BUFFERING)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            setPlaying(isPlaying)
        }

        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
            val w = videoSize.width * videoSize.pixelWidthHeightRatio
            val h = videoSize.height.toFloat()
            if (w > 0f && h > 0f) _videoAspect.value = (w / h).coerceIn(0.4f, 3.0f)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val transitionedMediaId = mediaItem?.mediaId
            transitionMixPromptPlayback(transitionedMediaId, reason)

            if (mediaItem != null) {
                mediaItem.mediaId?.let { mediaId ->
                    android.util.Log.d("VOIDPIXEL_MEDIA", "[BRIDGE_TRANSITION] Transitioned to mediaId=$mediaId, reason=$reason")
                    onTrackChanged(mediaId)

                    // ── Endless refill background monitoring ──
                    // Callback общий для всех очередей; PlaybackQueueConfig
                    // внутри движка разрешит запрос только VK Mix/явной Wave.
                    val player = controller
                    if (player != null) {
                        val total = player.mediaItemCount
                        val current = player.currentMediaItemIndex
                        val remaining = if (total > 0 && current >= 0) (total - current) else 0
                        if (remaining < EndlessPlaybackEngine.REFILL_THRESHOLD) {
                            ioScope.launch {
                                endlessEngine.checkAndRefillIfNeeded(remaining)
                            }
                        }
                    }
                }
            } else {
                android.util.Log.d("VOIDPIXEL_MEDIA", "[BRIDGE_TRANSITION] Transitioned to null (playback stopped/ended), reason=$reason")
                _currentTrack.value = null
                _currentPositionMs.value = 0L
                _durationMs.value = 0L
                _isVideoClip.value = false
            }
        }
    }

    fun toggleFavorite(trackId: String, ref: String = "player") {
        ioScope.launch {
            val repo = appContext?.let {
                com.lmg.vk.data.local.db.LibraryRepository.getInstance(it)
            } ?: return@launch
            val track = _currentTrack.value
            if (track != null && track.id == trackId) {
                repo.toggleFavorite(track, ref)
            } else {
                repo.toggleFavoriteById(trackId, ref)
            }
        }
    }

    /**
     * «Волна по треку» (станция, как у Яндекса): строит очередь вокруг [seedTrack]
     * через backend `wave/next?seed_track_id`, ставит сам трек первым и продолжает
     * похожими; авто-рефилл держит ту же станцию по seed.
     */
    fun startTrackWave(context: Context, seedTrack: Track, seedPool: List<String> = emptyList()) {
        ioScope.launch {
            val stationSeedId = seedTrack.id.takeIf { canUseAsVkStationSeed(seedTrack) }
            val stationSeedPool = if (stationSeedId != null) {
                seedPool.filter { MusicBackend.isVkAudioId(it) }
            } else {
                emptyList()
            }
            if (stationSeedId == null) {
                DebugLog.add("WAVE track station skipped: non-VK seed ${seedTrack.id}")
            }

            // VK 8.185 has two official sources behind the same menu action.
            // Most regular tracks use track_mix; context-flagged tracks use
            // StartPlaySimilarTracksSource (the existing path below).
            val trackMixSession = stationSeedId?.let {
                MusicBackend.resolveTrackWaveMixSession(it, seedTrack.title)
            }
            if (trackMixSession != null) {
                val source = runCatching { MusicBackend.startVkMix(trackMixSession) }
                    .onFailure { error ->
                        DebugLog.add(
                            "TRACK_WAVE track_mix failed: ${error.message ?: error.javaClass.simpleName}",
                        )
                    }
                    .getOrNull()
                val tracks = source?.tracks.orEmpty().filter { it.isAvailable }
                if (source != null && tracks.isNotEmpty()) {
                    playFromList(
                        context = context,
                        tracks = tracks,
                        startIndex = 0,
                        playbackContext = PlaybackContext.VkMix(source.session),
                    )
                    return@launch
                }
                DebugLog.add("TRACK_WAVE track_mix empty; falling back to recommendations")
            }

            // Мгновенный старт: seed-трек играет СРАЗУ (ноль сетевых запросов),
            // станция вокруг него добирается фоном и доклеивается в очередь.
            // Если seed не Apple numeric id, по доке station недоступна: играем
            // текущий трек и дальше мягко продолжаем личной волной.
            playFromList(
                context = context,
                tracks = listOf(seedTrack),
                startIndex = 0,
                autoRefillType = "WAVE",
                seedTrackId = stationSeedId,
                seedPool = stationSeedPool
            )
            val repo = com.lmg.vk.data.local.WaveRepository.getInstance(context)
            val station = if (stationSeedId != null) {
                repo.buildWaveQueue(seedTrackId = stationSeedId)
            } else {
                repo.buildWaveQueue(seedTrackId = null, exclude = listOf(seedTrack.id))
            }
            val rest = station.filter { it.id != seedTrack.id }
            if (rest.isNotEmpty()) {
                withContext(Dispatchers.Main) { addTracksToQueue(rest) }
            }
            // Добить очередь до «сытого» запаса сразу, не дожидаясь перехода.
            endlessEngine.checkAndRefillIfNeeded()
        }
    }

    private fun canUseAsVkStationSeed(track: Track): Boolean {
        val scheme = track.uri.scheme?.lowercase()
        if (scheme == "content" || scheme == "file" || track.isCustom) return false
        if (!MusicBackend.isVkAudioId(track.id)) return false
        val source = track.source?.lowercase()
        return source == null || source == "vk"
    }

    /**
     * Волна по артисту. API не имеет seed_artist_id, поэтому берём топ-трек артиста как
     * seed и строим вокруг него станцию (≈ радио по артисту, как у Яндекса).
     */
    fun startArtistWave(context: Context, artistId: String, artistName: String? = null) {
        ioScope.launch {
            // Пул топ-треков артиста: первый — мгновенный seed, все вместе —
            // якоря ротации (чётные рефиллы тянут станцию обратно к артисту).
            val topTracks = try {
                com.lmg.vk.engine.backend.MusicBackend.getArtistTopTracks(artistId).take(10)
            } catch (_: Exception) {
                emptyList()
            }
            val seed = topTracks.firstOrNull()
            if (seed == null) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        R.string.artist_wave_failed,
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                return@launch
            }
            startTrackWave(context, seed, seedPool = topTracks.map { it.id })
        }
    }

    /**
     * Вызывается при восстановлении сети. Если воспроизведение встало из-за ошибки сети
     * (плеер ушёл в STATE_IDLE — в отличие от пользовательской паузы, где он остаётся
     * READY), переподготавливаем текущий трек и продолжаем — без действий пользователя.
     */
    fun retryCurrentIfStalled(context: Context) {
        ioScope.launch {
            val player = getPlayer(context) ?: return@launch
            withContext(Dispatchers.Main) {
                if (_currentTrack.value != null &&
                    player.mediaItemCount > 0 &&
                    player.playbackState == Player.STATE_IDLE
                ) {
                    android.util.Log.d("PlayerController", "[NET] Network back — retrying stalled playback")
                    player.prepare()
                    player.play()
                }
            }
        }
    }

    fun setFavoriteIds(ids: Set<String>) { _favoriteIds.value = ids }
    fun isFavorite(trackId: String): Boolean {
        return VkAudioIdentity.stableFullId(trackId) in _favoriteIds.value
    }

    private fun addToRecent(track: Track) {
        val current = _recentlyPlayed.value.toMutableList()
        current.removeAll { it.id == track.id }
        current.add(0, track)
        _recentlyPlayed.value = current.take(50)

        appContext?.let { ctx ->
            com.lmg.vk.data.local.LocalStorage.addToHistory(
                ctx,
                com.lmg.vk.data.local.HistoryEntry(
                    trackId = track.id,
                    title = track.title,
                    artist = track.artist,
                    coverUrl = track.coverUrl,
                    durationMs = track.durationMs
                )
            )
            // Реальная история прослушивания в Room (для экрана «История»).
            ioScope.launch {
                runCatching {
                    com.lmg.vk.data.local.db.AppDatabase.getInstance(ctx)
                        .listenHistoryDao()
                        .upsert(
                            com.lmg.vk.data.local.db.ListenHistoryEntity(
                                accountId = com.lmg.vk.data.local.db.AppDatabase.activeAccountId(),
                                trackId = track.id,
                                title = track.title,
                                artist = track.artist,
                                coverUrl = track.coverUrl,
                                durationMs = track.durationMs,
                                playedAt = System.currentTimeMillis()
                            )
                        )
                }
            }
        }
    }

    fun toggleShuffle() = setShuffle(!_shuffleEnabled.value)

    fun setShuffle(enabled: Boolean) {
        if (_shuffleEnabled.value == enabled) return
        _shuffleEnabled.value = enabled
        applyShuffleToQueue(enabled)
        // VK Autoflow supports shuffled finite queues. Their reordered tail is
        // the new source of the last 50 ids, so refresh a near-end preload.
        maybePreloadAutoflow()
    }

    /**
     * Перемешивание применяем к самой очереди, а не к флагу плеера.
     *
     * Раньше ставили player.shuffleModeEnabled, но свой список и currentIndex
     * оставляли в исходном порядке — а skipNext ходит именно по нашему списку.
     * Выходило три порядка сразу: автопереход по перемешанному, кнопка «вперёд»
     * по нашему, а экран очереди рисовал третий. Теперь источник правды один.
     *
     * Трогаем только хвост после играющего трека: сам он остаётся на месте, и
     * замена участка не задевает уже подготовленный период — звук не рвётся.
     */
    private fun applyShuffleToQueue(enabled: Boolean) {
        val head = currentIndex
        val tail = if (head >= 0 && head < queue.lastIndex) {
            queue.subList(head + 1, queue.size).toList()
        } else emptyList()

        if (tail.size < 2) {
            preShuffleOrder = if (enabled) queue.map { it.id } else null
            return
        }

        val rank: Map<String, Int>? = if (enabled) {
            preShuffleOrder = queue.map { it.id }
            null
        } else {
            val order = preShuffleOrder ?: return
            preShuffleOrder = null
            HashMap<String, Int>().apply {
                order.forEachIndexed { i, id -> if (!containsKey(id)) put(id, i) }
            }
        }

        // Каждую секцию мешаем отдельно: размеры секций не меняются, значит
        // границы остаются верными, и добавленное вручную не растворяется среди
        // подобранного волной.
        val lo = head + 1
        val mid = manualEnd.coerceIn(lo, queue.size)
        val hi = autoStart.coerceIn(mid, queue.size)
        val newTail = ArrayList<Track>(tail.size)
        listOf(lo to mid, mid to hi, hi to queue.size).forEach { (a, b) ->
            if (b <= a) return@forEach
            val seg = queue.subList(a, b).toList()
            newTail += if (rank == null) {
                seg.shuffled()
            } else {
                // Добавленного во время перемешивания в снимке нет — уводим в
                // конец своей секции, сохраняя относительный порядок.
                seg.sortedBy { rank[it.id] ?: Int.MAX_VALUE }
            }
        }

        queue = queue.toMutableList().apply {
            newTail.forEachIndexed { i, t -> set(head + 1 + i, t) }
        }
        _queueFlow.value = queue

        mainScope.launch {
            val player = controller ?: appContext?.let { getPlayer(it) } ?: return@launch
            // Флаг плеера держим выключенным всегда — иначе он перемешает ещё раз,
            // уже поверх нашего порядка.
            player.shuffleModeEnabled = false
            val from = head + 1
            if (from >= player.mediaItemCount) return@launch
            val items = newTail.map { buildMediaItem(it) }
            try {
                player.replaceMediaItems(from, player.mediaItemCount, items)
            } catch (e: Exception) {
                android.util.Log.w("VOIDPIXEL_MEDIA", "[SHUFFLE] replaceMediaItems: ${e.message}")
                player.removeMediaItems(from, player.mediaItemCount)
                player.addMediaItems(from, items)
            }
        }
    }

    fun cycleRepeatMode() {
        val next = (_repeatMode.value + 1) % 3
        val wasOn = _repeatMode.value != 0
        _repeatMode.value = next
        mainScope.launch {
            getPlayer(appContext ?: return@launch)?.repeatMode = when (next) {
                1 -> Player.REPEAT_MODE_ALL
                2 -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
        // Пока повтор был включён, дозаправка волной пропускалась — вернувшись,
        // проверяем хвост сразу, иначе очередь кончится молча.
        if (wasOn && next == 0) ensureWaveRefill()
        if (next == 0) maybePreloadAutoflow()
    }

    fun setRepeatMode(mode: Int) {
        val clamped = mode.coerceIn(0, 2)
        _repeatMode.value = clamped
        mainScope.launch {
            getPlayer(appContext ?: return@launch)?.repeatMode = when (clamped) {
                1 -> Player.REPEAT_MODE_ALL
                2 -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
        if (clamped == 0) maybePreloadAutoflow()
    }
}
