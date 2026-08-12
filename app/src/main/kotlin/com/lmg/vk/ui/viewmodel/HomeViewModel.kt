package com.lmg.vk.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lmg.vk.engine.backend.MusicAuth
import com.lmg.vk.engine.backend.BlockPagingState
import com.lmg.vk.engine.backend.CatalogTabState
import com.lmg.vk.engine.backend.Chart
import com.lmg.vk.engine.backend.HomeBlock
import com.lmg.vk.engine.backend.HomeSignalInfo
import com.lmg.vk.engine.backend.HomeResponse
import com.lmg.vk.engine.backend.MusicBackend
import com.lmg.vk.engine.backend.BackendException
import com.lmg.vk.data.local.HomeCacheManager
import com.lmg.vk.data.local.WaveRepository
import com.lmg.vk.data.wave.WaveMode
import com.lmg.vk.engine.PlaybackBackend
import com.lmg.vk.engine.PlaybackContext
import com.lmg.vk.engine.PlayerController
import com.lmg.vk.engine.Track
import com.lmg.vk.engine.VkMixSession
import com.lmg.vk.engine.VkMixSettings
import com.lmg.vk.debug.DebugLog
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class VkMixOperation { LOAD_SETTINGS, RESOLVE_START, START, APPLY }

sealed interface VkMixUiState {
    data object Idle : VkMixUiState
    data object Loading : VkMixUiState

    data class Ready(
        val session: VkMixSession,
        val original: VkMixSettings?,
        val draft: VkMixSettings?,
        val applying: Boolean = false,
        val settingsLoaded: Boolean = false,
    ) : VkMixUiState {
        val hasChanges: Boolean get() = draft != null && draft != original
    }

    data class Empty(val session: VkMixSession? = null) : VkMixUiState

    data class Error(
        val message: String,
        val sessionExpired: Boolean,
        val operation: VkMixOperation,
        val session: VkMixSession? = null,
        val original: VkMixSettings? = session?.settings,
        val draft: VkMixSettings? = original,
    ) : VkMixUiState
}

sealed interface VkMixFeedbackState {
    data object Idle : VkMixFeedbackState
    data class Submitting(val trackId: String) : VkMixFeedbackState
    data class UndoAvailable(val trackId: String) : VkMixFeedbackState
    data class Undoing(val trackId: String) : VkMixFeedbackState
    data class Error(
        val trackId: String,
        val message: String,
        val retryUndo: Boolean,
    ) : VkMixFeedbackState
}

/**
 * ViewModel for the Home (Listen Now) screen.
 * 
 * Offline-first: loads catalog content from cache for the New tab. The Aura
 * starts VK Mix directly; WaveRepository remains only for explicit legacy
 * mood/station modes outside the personal Aura entry point.
 */
class HomeViewModel : ViewModel() {

    private var homeLoadJob: Job? = null
    private var chartsLoadJob: Job? = null
    private var genresLoadJob: Job? = null
    private var waveLoadJob: Job? = null
    private var mixSettingsJob: Job? = null
    private var feedbackJob: Job? = null
    private var pendingDislikeSkipJob: Job? = null
    private var signalLoadJob: Job? = null
    private val _loadingSignalBlocks = MutableStateFlow<Set<String>>(emptySet())
    val loadingSignalBlocks: StateFlow<Set<String>> = _loadingSignalBlocks

    private val restoredMixSession =
        (PlayerController.playbackContext as? PlaybackContext.VkMix)?.session
    private val _vkMixState = MutableStateFlow<VkMixUiState>(
        restoredMixSession?.let { session ->
            VkMixUiState.Ready(
                session,
                session.settings,
                session.settings,
                settingsLoaded = session.mixOptionsId != null,
            )
        } ?: VkMixUiState.Idle,
    )
    val vkMixState: StateFlow<VkMixUiState> = _vkMixState

    private val _vkMixFeedback = MutableStateFlow<VkMixFeedbackState>(VkMixFeedbackState.Idle)
    val vkMixFeedback: StateFlow<VkMixFeedbackState> = _vkMixFeedback

    /** Когда /home успешно загружен из сети (для TTL в loadHomeContent). */
    private var homeLoadedAtMs = 0L

    private companion object {
        const val HOME_TTL_MS = 5 * 60_000L
        const val VK_MIX_OPTIONS_ID_BOUND = 9_999_999_999_999L
        const val VK_MIX_DISLIKE_UNDO_MS = 2_000L
    }

    init {
        Log.d("HomeViewModel", "HomeViewModel created")
    }

    // ─── Home Content ───

    private val _homeContent = MutableStateFlow<HomeResponse?>(null)
    val homeContent: StateFlow<HomeResponse?> = _homeContent

    private val _charts = MutableStateFlow<List<Chart>>(emptyList())
    val charts: StateFlow<List<Chart>> = _charts

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isLoadingCharts = MutableStateFlow(false)
    val isLoadingCharts: StateFlow<Boolean> = _isLoadingCharts

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // ─── Wave State ───

    private val _waveTracks = MutableStateFlow<List<Track>>(emptyList())
    val waveTracks: StateFlow<List<Track>> = _waveTracks

    private val _isBuildingWave = MutableStateFlow(false)
    val isBuildingWave: StateFlow<Boolean> = _isBuildingWave

    // Персонализация волны работает только при залинкованном TG-аккаунте
    // (partner_user_id). Без него сервер отдаёт общую выдачу — это и есть «отсебятина».
    private val _needsLink = MutableStateFlow(false)
    val needsLink: StateFlow<Boolean> = _needsLink

    fun clearLinkFlag() { _needsLink.value = false }

    private fun isLinked(): Boolean =
        MusicAuth.partnerUserId.value != null

    private val _topGenres = MutableStateFlow<List<String>>(emptyList())
    val topGenres: StateFlow<List<String>> = _topGenres

    private fun activeLoadCount(): Int = listOf(homeLoadJob, chartsLoadJob, genresLoadJob, waveLoadJob).count { it?.isActive == true }

    fun cancelHomeLoad() {
        if (homeLoadJob?.isActive == true) Log.d("HomeViewModel", "homeLoadJob cancelled; active=${activeLoadCount()}")
        homeLoadJob?.cancel()
        homeLoadJob = null
        _isLoading.value = false
    }

    fun cancelChartsLoad() {
        if (chartsLoadJob?.isActive == true) Log.d("HomeViewModel", "chartsLoadJob cancelled; active=${activeLoadCount()}")
        chartsLoadJob?.cancel()
        chartsLoadJob = null
        _isLoadingCharts.value = false
    }

    /**
     * Load home content — offline first.
     */
    fun loadHomeContent(force: Boolean = false) {
        if (!force && homeLoadJob?.isActive == true) {
            Log.d("HomeViewModel", "loadHomeContent ignored: already active; active=${activeLoadCount()}")
            return
        }
        // TTL свежести (P1, аудит): LaunchedEffect на Wave/New перезапускает
        // загрузку при КАЖДОМ входе в таб — свежий /home перегружался по кругу
        // (до 3 сетевых попыток на свитч). Если контент уже есть и моложе TTL —
        // не ходим в сеть. force (pull-to-refresh/логин) обходит.
        if (!force && _homeContent.value != null &&
            System.currentTimeMillis() - homeLoadedAtMs < HOME_TTL_MS
        ) {
            Log.d("HomeViewModel", "loadHomeContent ignored: fresh (TTL)")
            return
        }
        homeLoadJob?.cancel()
        if (force) {
            // A forced catalog refresh can reuse block ids with completely new
            // cursors. Keeping derived pages from the previous response would
            // make the sheet append stale items or request an obsolete cursor.
            tabJobs.values.forEach { it.cancel() }
            tabJobs.clear()
            pagingJobs.values.forEach { it.cancel() }
            pagingJobs.clear()
            usedCursors.clear()
            _tabContent.value = emptyMap()
            _selectedTab.value = emptyMap()
            _blockPaging.value = emptyMap()
        }
        _isLoading.value = true
        _error.value = null
        homeLoadJob = viewModelScope.launch {
            Log.d("HomeViewModel", "homeLoadJob started; active=${activeLoadCount()}")
            try {
                val cached = HomeCacheManager.load()
                if (cached != null && _homeContent.value == null) _homeContent.value = cached

                var lastException: Exception? = null
                repeat(3) { attempt ->
                    try {
                        val response = MusicBackend.loadHomeContent()
                        _homeContent.value = response
                        homeLoadedAtMs = System.currentTimeMillis()
                        _error.value = null
                        HomeCacheManager.save(response)
                        // The home response already contains its charts block. Do not issue
                        // another chart search unless that block is genuinely absent.
                        if (response.blocks.none { it.type == "charts" }) loadCharts()
                        return@launch
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        lastException = e
                        if (attempt < 2) delay(1000L * (attempt + 1))
                    }
                }
                _error.value = com.lmg.vk.engine.backend.backendUserMessage(lastException)
                if (_homeContent.value == null) _homeContent.value = HomeResponse(blocks = emptyList())
            } catch (e: CancellationException) {
                Log.d("HomeViewModel", "homeLoadJob cancelled")
                throw e
            } finally {
                if (homeLoadJob === coroutineContext[Job]) {
                    _isLoading.value = false
                    homeLoadJob = null
                    Log.d("HomeViewModel", "homeLoadJob finished; active=${activeLoadCount()}")
                }
            }
        }
    }

    /**
     * Load charts (top charts from Apple Music).
     */
    fun loadCharts() {
        if (chartsLoadJob?.isActive == true) return
        _isLoadingCharts.value = true
        chartsLoadJob = viewModelScope.launch {
            Log.d("HomeViewModel", "chartsLoadJob started; active=${activeLoadCount()}")
            try {
                val charts = MusicBackend.loadCharts()
                _charts.value = charts
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Silently fail — charts are decorative
            } finally {
                if (chartsLoadJob === coroutineContext[Job]) {
                    _isLoadingCharts.value = false
                    chartsLoadJob = null
                    Log.d("HomeViewModel", "chartsLoadJob finished; active=${activeLoadCount()}")
                }
            }
        }
    }

    /**
     * Refresh home content (pull-to-refresh).
     * Clears the in-memory wave exclude set so recommendations can include
     * previously-played tracks again.
     */
    fun refresh() {
        _homeContent.value = null
        HomeCacheManager.clear()
        // Выдача табов и догруженные порции привязаны к id блоков и курсорам
        // ПРЕЖНЕГО ответа: VK меняет их при каждой выдаче. Не сбросив, мы бы
        // показали под новыми блоками содержимое старых.
        tabJobs.values.forEach { it.cancel() }
        tabJobs.clear()
        pagingJobs.values.forEach { it.cancel() }
        pagingJobs.clear()
        usedCursors.clear()
        _tabContent.value = emptyMap()
        _selectedTab.value = emptyMap()
        _blockPaging.value = emptyMap()
        loadHomeContent(force = true)
    }

    /**
     * Get a specific block by type.
     */
    fun getBlockByType(type: String): HomeBlock? {
        return _homeContent.value?.blocks?.find { it.type == type }
    }

    // ─── subsection_tabs: своя выдача на каждый таб ───

    /**
     * Выбранный таб на блок. Ключ — id блока, значение — `replacementId` таба.
     * Держим в VM, а не в composable: при уходе с таба New и возврате обратно
     * выбор пользователя должен сохраниться, а `remember` в LazyColumn его теряет
     * вместе с прокрученным за экран блоком.
     */
    private val _selectedTab = MutableStateFlow<Map<String, String>>(emptyMap())
    val selectedTab: StateFlow<Map<String, String>> = _selectedTab

    /** Выдача табов: ключ — `replacementId`, а не id блока. */
    private val _tabContent = MutableStateFlow<Map<String, CatalogTabState>>(emptyMap())
    val tabContent: StateFlow<Map<String, CatalogTabState>> = _tabContent

    private val tabJobs = HashMap<String, Job>()

    /**
     * Выбор таба подраздела. Грузит выдачу таба ОДИН раз: успешный результат
     * кэшируется по `replacementId` в пределах жизни VM — переключение туда-обратно
     * не должно бить в сеть повторно.
     */
    fun selectSubsectionTab(blockId: String, replacementId: String) {
        if (blockId.isBlank() || replacementId.isBlank()) return
        _selectedTab.value = _selectedTab.value + (blockId to replacementId)
        loadSubsectionTab(replacementId)
    }

    /** Повтор после ошибки: сбрасывает кэш неудачи и грузит заново. */
    fun retrySubsectionTab(replacementId: String) {
        _tabContent.value = _tabContent.value - replacementId
        loadSubsectionTab(replacementId)
    }

    private fun loadSubsectionTab(replacementId: String) {
        // Готовую выдачу не перезапрашиваем; активный запрос не дублируем.
        if (_tabContent.value[replacementId] is CatalogTabState.Ready) return
        if (tabJobs[replacementId]?.isActive == true) return

        _tabContent.value = _tabContent.value + (replacementId to CatalogTabState.Loading)
        tabJobs[replacementId] = viewModelScope.launch {
            try {
                val blocks = MusicBackend.loadCatalogTab(replacementId)
                _tabContent.value = _tabContent.value + (
                    replacementId to if (blocks.isEmpty()) {
                        // Пустая выдача — это ответ сервера, а не сбой: так и пишем,
                        // вместо того чтобы оставить пустое место под табами.
                        CatalogTabState.Failed("VK не вернул содержимое раздела")
                    } else {
                        CatalogTabState.Ready(blocks)
                    }
                    )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _tabContent.value = _tabContent.value + (
                    replacementId to CatalogTabState.Failed(
                        com.lmg.vk.engine.backend.backendUserMessage(e)
                    )
                    )
            } finally {
                if (tabJobs[replacementId] === coroutineContext[Job]) {
                    tabJobs.remove(replacementId)
                }
            }
        }
    }

    // ─── Пагинация внутри блока (шторка «показать все») ───

    private val _blockPaging = MutableStateFlow<Map<String, BlockPagingState>>(emptyMap())
    val blockPaging: StateFlow<Map<String, BlockPagingState>> = _blockPaging

    private val pagingJobs = HashMap<String, Job>()

    /**
     * Курсоры, уже отработанные по каждому блоку. Нужны, чтобы повторный
     * `next_from` от VK не пустил догрузку по кругу — сам ответ этого не
     * отслеживает.
     */
    private val usedCursors = HashMap<String, MutableSet<String>>()

    /**
     * Догрузить следующую порцию элементов блока.
     *
     * Первый вызов подхватывает `nextFrom` из самого блока, дальше идёт по
     * курсору из последнего ответа. Конец фиксируется только когда VK перестал
     * возвращать новый курсор; пустая порция с новым курсором может быть
     * промежуточной после серверной дедупликации.
     */
    fun loadMoreBlockItems(block: HomeBlock) {
        val blockId = block.id
        if (blockId.isBlank()) return
        val state = _blockPaging.value[blockId] ?: BlockPagingState(nextFrom = block.nextFrom)
        val cursor = state.nextFrom ?: block.nextFrom
        if (state.isLoading || state.exhausted || cursor.isNullOrBlank()) return
        if (pagingJobs[blockId]?.isActive == true) return

        _blockPaging.value = _blockPaging.value + (
            blockId to state.copy(nextFrom = cursor, isLoading = true, error = null)
            )
        pagingJobs[blockId] = viewModelScope.launch {
            val seen = usedCursors.getOrPut(blockId) { HashSet() }
            try {
                val page = MusicBackend.loadBlockItemsPage(
                    blockId = blockId,
                    startFrom = cursor,
                    ref = block.catalogRef,
                    usedCursors = seen,
                )
                seen += cursor
                val current = _blockPaging.value[blockId] ?: state
                // Дедупликация по id: VK при догрузке нередко повторяет хвост
                // предыдущей порции, и без неё в списке появлялись дубли, а
                // LazyColumn падал на неуникальных ключах.
                val known = (block.items + current.extraItems).mapTo(HashSet()) { it.id }
                val fresh = page.items.filter { known.add(it.id) }
                _blockPaging.value = _blockPaging.value + (
                    blockId to current.copy(
                        extraItems = current.extraItems + fresh,
                        nextFrom = page.nextFrom,
                        isLoading = false,
                        error = null,
                        // A page may consist only of the repeated tail of the
                        // previous page while still advancing the cursor.
                        exhausted = page.nextFrom.isNullOrBlank(),
                    )
                    )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val current = _blockPaging.value[blockId] ?: state
                _blockPaging.value = _blockPaging.value + (
                    blockId to current.copy(
                        isLoading = false,
                        error = com.lmg.vk.engine.backend.backendUserMessage(e),
                    )
                    )
            } finally {
                if (pagingJobs[blockId] === coroutineContext[Job]) pagingJobs.remove(blockId)
            }
        }
    }

    /** Повтор догрузки после ошибки: снимает error, курсор остаётся тот же. */
    fun retryBlockItems(block: HomeBlock) {
        val current = _blockPaging.value[block.id] ?: return
        _blockPaging.value = _blockPaging.value + (block.id to current.copy(error = null))
        loadMoreBlockItems(block)
    }

    // ─── Wave (My Wave) ───

    /**
     * Loads the user's top genres from their playback history.
     */
    fun loadTopGenres(context: Context) {
        if (genresLoadJob?.isActive == true) return
        genresLoadJob = viewModelScope.launch {
            try {
                val repo = WaveRepository.getInstance(context)
                val genres = repo.getTopGenres(limit = 5)
                _topGenres.value = genres
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Silently fail — genres are optional
            } finally {
                if (genresLoadJob === coroutineContext[Job]) genresLoadJob = null
            }
        }
    }

    /** Resolve the tunable source without starting playback. */
    fun prepareVkMixSettings() {
        val current = _vkMixState.value
        if (current is VkMixUiState.Ready &&
            (!current.session.isTunable || current.settingsLoaded)
        ) {
            return
        }
        if (mixSettingsJob?.isActive == true || waveLoadJob?.isActive == true) return

        val retainedSession = when (current) {
            is VkMixUiState.Ready -> current.session
            is VkMixUiState.Empty -> current.session
            is VkMixUiState.Error -> current.session
            else -> null
        } ?: (PlayerController.playbackContext as? PlaybackContext.VkMix)?.session

        _vkMixState.value = VkMixUiState.Loading
        mixSettingsJob = viewModelScope.launch {
            var resolvedSession = retainedSession
            try {
                val session = resolvedSession ?: MusicBackend.resolvePersonalMixSession()
                resolvedSession = session
                val settings = if (session.isTunable) {
                    MusicBackend.getVkMixSettings(session)
                } else {
                    session.settings
                }
                val hydrated = session.copy(
                    settings = settings,
                    // A freshly resolved source with settings supplied by the
                    // dedicated endpoint starts from those server selections.
                    options = if (
                        session.settings == null &&
                        session.mixOptionsId == null &&
                        session.options.isEmpty()
                    ) {
                        settings?.selectedOptions().orEmpty()
                    } else {
                        session.options
                    },
                )
                _vkMixState.value = VkMixUiState.Ready(
                    hydrated,
                    settings,
                    settings,
                    settingsLoaded = true,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _vkMixState.value = e.toVkMixError(
                    operation = VkMixOperation.LOAD_SETTINGS,
                    session = resolvedSession,
                )
            } finally {
                if (mixSettingsJob === coroutineContext[Job]) mixSettingsJob = null
            }
        }
    }

    fun toggleVkMixOption(categoryId: String, optionId: String) {
        val ready = _vkMixState.value as? VkMixUiState.Ready ?: return
        val draft = ready.draft ?: return
        if (ready.applying) return
        _vkMixState.value = ready.copy(draft = draft.toggle(categoryId, optionId))
    }

    fun resetVkMixOptions() {
        val ready = _vkMixState.value as? VkMixUiState.Ready ?: return
        val draft = ready.draft ?: return
        if (ready.applying) return
        _vkMixState.value = ready.copy(draft = draft.clearVisibleSelections())
    }

    /**
     * Apply is atomic from the player's point of view: the old queue remains
     * active until VK has returned a non-empty `append=false` batch.
     */
    fun applyVkMixSettings(context: Context) {
        val ready = _vkMixState.value as? VkMixUiState.Ready ?: return
        val draft = ready.draft ?: return
        if (ready.applying || waveLoadJob?.isActive == true) return

        val preparedSession = ready.session.copy(
            settings = draft,
            options = draft.selectedOptions(),
            mixOptionsId = Random.nextLong(VK_MIX_OPTIONS_ID_BOUND),
        )
        _vkMixState.value = ready.copy(applying = true)
        startKnownVkMix(
            context = context,
            session = preparedSession,
            operation = VkMixOperation.APPLY,
            original = ready.original,
            draft = draft,
        )
    }

    /**
     * Starts the official personal VK Mix inside LMG's Aura presentation.
     * The Aura remains the whole screen; VK supplies only the playback source.
     */
    fun startAuraMix(context: Context) {
        if (waveLoadJob?.isActive == true) return
        mixSettingsJob?.cancel()
        mixSettingsJob = null
        _error.value = null
        _isBuildingWave.value = true
        _vkMixState.value = VkMixUiState.Loading

        waveLoadJob = viewModelScope.launch {
            var session: VkMixSession? = null
            try {
                session = MusicBackend.resolvePersonalMixSession()
                val mixSource = MusicBackend.startVkMix(session)
                val tracks = mixSource.tracks.filter { it.isAvailable }
                android.util.Log.d("VkMix", "startAuraMix -> ${tracks.size} tracks")

                if (tracks.isNotEmpty()) {
                    _waveTracks.value = tracks
                    PlayerController.playFromList(
                        context = context,
                        tracks = tracks,
                        startIndex = 0,
                        playbackContext = com.lmg.vk.engine.PlaybackContext.VkMix(mixSource.session),
                    )
                    _vkMixState.value = VkMixUiState.Ready(
                        session = mixSource.session,
                        original = mixSource.session.settings,
                        draft = mixSource.session.settings,
                    )
                } else {
                    _vkMixState.value = VkMixUiState.Empty(session)
                    _error.value = "VK Mix недоступен"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = com.lmg.vk.engine.backend.backendUserMessage(e)
                _vkMixState.value = e.toVkMixError(
                    operation = VkMixOperation.START,
                    session = session,
                )
            } finally {
                if (waveLoadJob === coroutineContext[Job]) {
                    _isBuildingWave.value = false
                    waveLoadJob = null
                }
            }
        }
    }

    /** Start a concrete CatalogKit AudioStreamMix through the regular VK Mix path. */
    fun startCatalogVkMix(
        context: Context,
        mixId: String,
        title: String,
        isTunable: Boolean,
        blockId: String?,
        entityId: String?,
        sectionId: String?,
        catalogItemId: String?,
        options: Map<String, List<String>>,
        resolveSettings: Boolean,
    ) {
        if (mixId.isBlank() || waveLoadJob?.isActive == true) return
        mixSettingsJob?.cancel()
        mixSettingsJob = null
        _error.value = null
        val session = VkMixSession(
            blockId = blockId.orEmpty(),
            sectionId = sectionId.orEmpty(),
            mixId = mixId,
            isTunable = isTunable || resolveSettings,
            title = title,
            settings = null,
            entityId = entityId,
            catalogItemId = catalogItemId,
            id = catalogItemId,
            options = options,
        )
        if (!resolveSettings) {
            startKnownVkMix(context, session, VkMixOperation.START)
            return
        }

        resolveCatalogMixAndStart(context, session)
    }

    /** Start VK Music Signal through its official StartPlayCatalogSource chain. */
    fun startCatalogSignal(
        context: Context,
        signal: HomeSignalInfo,
        seedTrackId: String? = null,
    ) {
        val blockId = signal.playBlockId?.takeIf(String::isNotBlank) ?: return
        if (signalLoadJob?.isActive == true) return
        _loadingSignalBlocks.value = _loadingSignalBlocks.value + blockId
        signalLoadJob = viewModelScope.launch {
            try {
                val resolved = MusicBackend.getCatalogSourceTracks(blockId, signal.ref)
                val tracks = if (signal.shuffled) resolved.shuffled() else resolved
                if (tracks.isEmpty()) {
                    android.widget.Toast.makeText(
                        context,
                        "VK не вернул треки Сигнала",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                    return@launch
                }
                val stableSeed = seedTrackId?.let(com.lmg.vk.engine.VkAudioIdentity::stableFullId)
                val startIndex = stableSeed?.let { seed ->
                    tracks.indexOfFirst {
                        com.lmg.vk.engine.VkAudioIdentity.stableFullId(it.id) == seed
                    }.takeIf { it >= 0 }
                } ?: 0
                PlayerController.playFromList(
                    context = context,
                    tracks = tracks,
                    startIndex = startIndex,
                    playbackContext = PlaybackContext.Catalog(blockId),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = com.lmg.vk.engine.backend.backendUserMessage(e)
                DebugLog.add("VK SIGNAL start failed: $message")
                android.widget.Toast.makeText(
                    context,
                    message,
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            } finally {
                _loadingSignalBlocks.value = _loadingSignalBlocks.value - blockId
                if (signalLoadJob === coroutineContext[Job]) signalLoadJob = null
            }
        }
    }

    /** Hydrate a CatalogKit action and preserve that operation across Retry. */
    private fun resolveCatalogMixAndStart(context: Context, session: VkMixSession) {
        // Official CatalogKit hydrates a `play_vk_mix` action first, overlays
        // its `mix_options`, and only then creates StartPlayVkMixSource.
        _vkMixState.value = VkMixUiState.Loading
        mixSettingsJob = viewModelScope.launch {
            try {
                val settings = MusicBackend.getVkMixSettings(session)
                val hydrated = session.copy(
                    isTunable = settings != null,
                    settings = settings,
                    options = if (session.options.isEmpty()) {
                        settings?.selectedOptions().orEmpty()
                    } else {
                        session.options
                    },
                )
                startKnownVkMix(
                    context = context,
                    session = hydrated,
                    operation = VkMixOperation.START,
                    original = settings,
                    draft = settings,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _vkMixState.value = e.toVkMixError(
                    operation = VkMixOperation.RESOLVE_START,
                    session = session,
                )
            } finally {
                if (mixSettingsJob === coroutineContext[Job]) mixSettingsJob = null
            }
        }
    }

    private fun startKnownVkMix(
        context: Context,
        session: VkMixSession,
        operation: VkMixOperation,
        original: VkMixSettings? = session.settings,
        draft: VkMixSettings? = session.settings,
    ) {
        if (waveLoadJob?.isActive == true) return
        _isBuildingWave.value = true
        _vkMixState.value = if (operation == VkMixOperation.APPLY) {
            VkMixUiState.Ready(session, original, draft, applying = true)
        } else {
            VkMixUiState.Loading
        }
        waveLoadJob = viewModelScope.launch {
            try {
                val source = MusicBackend.startVkMix(session)
                val tracks = source.tracks.filter { it.isAvailable }
                if (tracks.isEmpty()) {
                    _vkMixState.value = VkMixUiState.Empty(session)
                    return@launch
                }
                _waveTracks.value = tracks
                PlayerController.playFromList(
                    context = context,
                    tracks = tracks,
                    startIndex = 0,
                    playbackContext = PlaybackContext.VkMix(session),
                )
                val accepted = draft ?: session.settings
                _vkMixState.value = VkMixUiState.Ready(
                    session,
                    accepted,
                    accepted,
                    settingsLoaded = operation == VkMixOperation.APPLY ||
                        session.mixOptionsId != null || session.settings != null,
                )
                _error.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _vkMixState.value = e.toVkMixError(
                    operation = operation,
                    session = session,
                    original = original,
                    draft = draft,
                )
            } finally {
                if (waveLoadJob === coroutineContext[Job]) {
                    _isBuildingWave.value = false
                    waveLoadJob = null
                }
            }
        }
    }

    fun retryVkMix(context: Context) {
        when (val state = _vkMixState.value) {
            is VkMixUiState.Error -> when {
                state.operation == VkMixOperation.LOAD_SETTINGS -> prepareVkMixSettings()
                state.operation == VkMixOperation.RESOLVE_START && state.session != null ->
                    resolveCatalogMixAndStart(context, state.session)
                state.session != null -> startKnownVkMix(
                    context = context,
                    session = state.session,
                    operation = state.operation,
                    original = state.original,
                    draft = state.draft,
                )
                else -> startAuraMix(context)
            }
            is VkMixUiState.Empty -> state.session?.let { session ->
                startKnownVkMix(context, session, VkMixOperation.START)
            } ?: startAuraMix(context)
            else -> Unit
        }
    }

    fun dislikeAuraTrack(context: Context, trackId: String) {
        val activeMix = PlayerController.playbackContext is PlaybackContext.VkMix
        if (!activeMix || PlayerController.playbackBackend.value != PlaybackBackend.EXO_STREAMING) return
        if (_vkMixFeedback.value is VkMixFeedbackState.Submitting ||
            _vkMixFeedback.value is VkMixFeedbackState.Undoing
        ) return

        pendingDislikeSkipJob?.cancel()
        feedbackJob?.cancel()
        _vkMixFeedback.value = VkMixFeedbackState.Submitting(trackId)
        feedbackJob = viewModelScope.launch {
            try {
                MusicBackend.dislikeTrack(trackId)
                _vkMixFeedback.value = VkMixFeedbackState.UndoAvailable(trackId)
                pendingDislikeSkipJob = viewModelScope.launch {
                    delay(VK_MIX_DISLIKE_UNDO_MS)
                    val state = _vkMixFeedback.value
                    if (state is VkMixFeedbackState.UndoAvailable &&
                        state.trackId == trackId &&
                        PlayerController.currentTrack.value?.id == trackId
                    ) {
                        PlayerController.skipNext(context)
                    }
                    if ((_vkMixFeedback.value as? VkMixFeedbackState.UndoAvailable)?.trackId == trackId) {
                        _vkMixFeedback.value = VkMixFeedbackState.Idle
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _vkMixFeedback.value = VkMixFeedbackState.Error(
                    trackId = trackId,
                    message = com.lmg.vk.engine.backend.backendUserMessage(e),
                    retryUndo = false,
                )
            } finally {
                if (feedbackJob === coroutineContext[Job]) feedbackJob = null
            }
        }
    }

    fun undoAuraDislike(trackId: String) {
        val canUndo = when (val state = _vkMixFeedback.value) {
            is VkMixFeedbackState.UndoAvailable -> state.trackId == trackId
            is VkMixFeedbackState.Error -> state.trackId == trackId && state.retryUndo
            else -> false
        }
        if (!canUndo) return

        pendingDislikeSkipJob?.cancel()
        feedbackJob?.cancel()
        _vkMixFeedback.value = VkMixFeedbackState.Undoing(trackId)
        feedbackJob = viewModelScope.launch {
            try {
                MusicBackend.removeTrackDislike(trackId)
                _vkMixFeedback.value = VkMixFeedbackState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _vkMixFeedback.value = VkMixFeedbackState.Error(
                    trackId = trackId,
                    message = com.lmg.vk.engine.backend.backendUserMessage(e),
                    retryUndo = true,
                )
            } finally {
                if (feedbackJob === coroutineContext[Job]) feedbackJob = null
            }
        }
    }

    fun retryVkMixFeedback(context: Context) {
        val error = _vkMixFeedback.value as? VkMixFeedbackState.Error ?: return
        if (error.retryUndo) undoAuraDislike(error.trackId)
        else dislikeAuraTrack(context, error.trackId)
    }

    fun onAuraTrackChanged(trackId: String?) {
        val activeSession = (PlayerController.playbackContext as? PlaybackContext.VkMix)?.session
        val stateSession = when (val state = _vkMixState.value) {
            is VkMixUiState.Ready -> state.session
            is VkMixUiState.Empty -> state.session
            is VkMixUiState.Error -> state.session
            else -> null
        }
        val stateIsBusy = _vkMixState.value is VkMixUiState.Loading ||
            (_vkMixState.value as? VkMixUiState.Ready)?.applying == true
        if (activeSession != null && activeSession != stateSession && !stateIsBusy) {
            _vkMixState.value = VkMixUiState.Ready(
                activeSession,
                activeSession.settings,
                activeSession.settings,
                settingsLoaded = activeSession.mixOptionsId != null,
            )
        }

        val feedbackTrackId = when (val state = _vkMixFeedback.value) {
            is VkMixFeedbackState.Submitting -> state.trackId
            is VkMixFeedbackState.UndoAvailable -> state.trackId
            is VkMixFeedbackState.Undoing -> state.trackId
            is VkMixFeedbackState.Error -> state.trackId
            VkMixFeedbackState.Idle -> null
        }
        if (feedbackTrackId != null && feedbackTrackId != trackId) {
            feedbackJob?.cancel()
            pendingDislikeSkipJob?.cancel()
            _vkMixFeedback.value = VkMixFeedbackState.Idle
        }
    }

    private fun Exception.toVkMixError(
        operation: VkMixOperation,
        session: VkMixSession? = null,
        original: VkMixSettings? = session?.settings,
        draft: VkMixSettings? = original,
    ): VkMixUiState.Error {
        val backendError = this as? BackendException
        DebugLog.add(
            "VK MIX ${operation.name} failed: " +
                "${javaClass.simpleName}" +
                (backendError?.let { " code=${it.code}" } ?: "") +
                ": ${message ?: "без сообщения"}",
        )
        val userMessage = when {
            backendError?.code == 404 && operation == VkMixOperation.LOAD_SETTINGS && session == null ->
                "VK не вернул персональный Mix для этого аккаунта"
            backendError?.code == 404 && operation == VkMixOperation.LOAD_SETTINGS ->
                "VK не нашёл настройки для текущего Mix"
            else -> com.lmg.vk.engine.backend.backendUserMessage(this)
        }
        return VkMixUiState.Error(
            message = userMessage,
            sessionExpired = backendError?.code?.let { it == 401 || it == 1117 } == true,
            operation = operation,
            session = session,
            original = original,
            draft = draft,
        )
    }

    /**
     * Builds an expanded mood wave through /wave/mood/{mood}.
     */
    fun buildMoodWave(context: Context, query: String, name: String? = null) {
        if (waveLoadJob?.isActive == true) return
        if (!isLinked()) { _needsLink.value = true; return }
        _isBuildingWave.value = true

        waveLoadJob = viewModelScope.launch {
            try {
                val repo = WaveRepository.getInstance(context)
                // Стартуем с маленькой пачки (1 быстрый запрос) — до полного
                // буфера очередь добивает EndlessPlaybackEngine.
                val tracks = repo.buildWaveModeQueue(
                    mode = WaveMode.Mood(
                        mood = query,
                        displayName = name,
                        source = "vk",
                        diversity = 0.5
                    ),
                    count = WaveRepository.FAST_START_COUNT
                )
                if (tracks.isNotEmpty()) {
                    _waveTracks.value = tracks
                    PlayerController.playFromList(
                        context = context,
                        tracks = tracks,
                        startIndex = 0,
                        autoRefillType = "MOOD",
                        autoRefillId = query,
                        autoRefillName = name
                    )
                    _isBuildingWave.value = false
                    PlayerController.ensureWaveRefill()
                } else {
                    // Реальный /wave/mood (его backend починил 2026-07-11) вернул пусто →
                    // страховка от молчаливого тупика: падаем в SEARCH по терму
                    // настроения (как buildWaveQueue при пустой персональной): волна и заведётся, и
                    // будет бесконечной через SEARCH-дозаправку.
                    val fallback = try {
                        repo.buildGenreSearchQueue(
                            genres = listOf(query),
                            count = WaveRepository.WAVE_QUEUE_SIZE
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        emptyList()
                    }
                    if (fallback.isNotEmpty()) {
                        _waveTracks.value = fallback
                        PlayerController.playFromList(
                            context = context,
                            tracks = fallback,
                            startIndex = 0,
                            autoRefillType = "SEARCH",
                            autoRefillId = query,
                            autoRefillName = name,
                            seedPool = listOf(query)
                        )
                        _isBuildingWave.value = false
                        PlayerController.ensureWaveRefill()
                    } else {
                        _error.value = "Failed to build wave"
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _error.value = "Failed to build wave"
            } finally {
                if (waveLoadJob === coroutineContext[Job]) {
                    _isBuildingWave.value = false
                    waveLoadJob = null
                }
            }
        }
    }

    /**
     * Stops the "My Wave" playback.
     */
    fun stopWave() {
        _waveTracks.value = emptyList()
    }

    /**
     * Check if user has premium subscription.
     */
    val isPremium: Boolean
        get() = MusicAuth.isPremium.value

    override fun onCleared() {
        Log.d("HomeViewModel", "HomeViewModel onCleared")
        super.onCleared()
    }
}
