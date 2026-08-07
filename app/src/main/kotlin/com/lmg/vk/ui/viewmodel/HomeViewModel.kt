package com.lmg.vk.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lmg.vk.engine.backend.MusicAuth
import com.lmg.vk.engine.backend.BlockPagingState
import com.lmg.vk.engine.backend.CatalogTabState
import com.lmg.vk.engine.backend.Chart
import com.lmg.vk.engine.backend.HomeBlock
import com.lmg.vk.engine.backend.HomeResponse
import com.lmg.vk.engine.backend.MusicBackend
import com.lmg.vk.data.local.HomeCacheManager
import com.lmg.vk.data.local.WaveRepository
import com.lmg.vk.data.wave.WaveMode
import com.lmg.vk.engine.PlayerController
import com.lmg.vk.engine.Track
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * ViewModel for the Home (Listen Now) screen.
 * 
 * Offline-first: loads from cache immediately, then refreshes from API in background.
 * Uses WaveRepository for "My Wave" analytics and queue building.
 */
class HomeViewModel : ViewModel() {

    private var homeLoadJob: Job? = null
    private var chartsLoadJob: Job? = null
    private var genresLoadJob: Job? = null
    private var waveLoadJob: Job? = null

    /** Когда /home успешно загружен из сети (для TTL в loadHomeContent). */
    private var homeLoadedAtMs = 0L

    private companion object {
        const val HOME_TTL_MS = 5 * 60_000L
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
     * курсору из последнего ответа. Пустая порция при непустом курсоре — это
     * конец списка (`exhausted`), а не ошибка: VK так закрывает выдачу.
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
                        exhausted = fresh.isEmpty() || page.nextFrom.isNullOrBlank(),
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

    /**
     * Builds the expanded personal wave. Fast start: a small one-shot batch
     * starts the music after a single request; EndlessPlaybackEngine tops the
     * queue up through the session that WaveRepository warms up in parallel.
     */
    fun buildWaveQueue(context: Context) {
        if (waveLoadJob?.isActive == true) return
        if (!isLinked()) { _needsLink.value = true; return }
        _isBuildingWave.value = true

        waveLoadJob = viewModelScope.launch {
            try {
                val repo = WaveRepository.getInstance(context)
                val tracks = repo.startPersonalWave()
                android.util.Log.d("Wave", "buildWaveQueue: startPersonalWave -> ${tracks.size} tracks")

                if (tracks.isNotEmpty()) {
                    _waveTracks.value = tracks
                    PlayerController.playFromList(
                        context = context,
                        tracks = tracks,
                        startIndex = 0,
                        autoRefillType = "WAVE"
                    )
                    _isBuildingWave.value = false
                    PlayerController.ensureWaveRefill()
                } else {
                    // Персональная волна backend пуста НА СЕРВЕРЕ: он Apple-only и у
                    // аккаунта нет apple-сидов (лайки Y/кастомные), поэтому на все
                    // /wave/* приходит status:"empty", tracks:[]. Чтобы «Моя волна»
                    // не молчала и не откатывалась — падаем в бесконечную ЖАНРОВУЮ
                    // волну по топ-жанрам юзера: /wave/genre/{genre} это Apple-каталог,
                    // не зависит от персональных сидов. GENRE-дозаправка (см.
                    // EndlessPlaybackEngine) держит её бесконечной.
                    val fallback = buildGenreFallbackQueue(repo)
                    if (fallback != null) {
                        val (genres, genreTracks) = fallback
                        android.util.Log.d(
                            "Wave",
                            "buildWaveQueue: personal empty -> search genre fallback $genres -> ${genreTracks.size} tracks"
                        )
                        _waveTracks.value = genreTracks
                        // autoRefillType=SEARCH + seedPool=жанры → EndlessPlaybackEngine
                        // дозаправляет волну поиском по этим жанрам (см. SEARCH-ветку).
                        // Персональная волна пуста (Apple-only), поэтому идём поиском;
                        // /wave/genre backend починил (2026-07-11). Волна бесконечна.
                        PlayerController.playFromList(
                            context = context,
                            tracks = genreTracks,
                            startIndex = 0,
                            autoRefillType = "SEARCH",
                            autoRefillId = genres.firstOrNull(),
                            autoRefillName = genres.firstOrNull(),
                            seedPool = genres
                        )
                        _isBuildingWave.value = false
                        PlayerController.ensureWaveRefill()
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
     * Fallback для пустой персональной волны. Берёт топ-жанры юзера и набирает
     * очередь ПОИСКОМ (buildGenreSearchQueue): персональная волна пуста (Apple-
     * only), а поиск стабилен. (/wave/genre backend починил 2026-07-11 — раньше он
     * висел; теперь это просто наш fallback.) Возвращает список жанров (для
     * SEARCH-дозаправки через seedPool) и стартовую пачку. getTopGenres сам
     * отдаёт дефолты (Electronic/Techno…), если истории нет — так что волна
     * заведётся даже у нового юзера.
     */
    private suspend fun buildGenreFallbackQueue(
        repo: WaveRepository
    ): Pair<List<String>, List<Track>>? {
        val genres = try {
            repo.getTopGenres(limit = 5)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        if (genres.isEmpty()) return null
        // Первую пачку берём через ПРАВИЛЬНЫЙ /wave/genre/{genre} (round-robin по
        // жанрам): жанрово классифицированные треки, а не результат текст-поиска по
        // слову-жанру. Сырой поиск остаётся внутри buildMultiGenreWaveQueue как
        // аварийный фолбэк, если сервер вернёт пусто по всем жанрам.
        suspend fun search(): List<Track> = try {
            repo.buildMultiGenreWaveQueue(
                genres = genres,
                count = WaveRepository.WAVE_QUEUE_SIZE
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        var tracks = search()
        if (tracks.isEmpty()) {
            // Первый заход мог совпасть со сменой сети на старте (OkHttp сбрасывает
            // пулы — «NET revive»), из-за чего волна не стартовала с первого раза.
            // Один повтор после короткой паузы обычно уже попадает в живые пулы.
            delay(600)
            tracks = search()
        }
        return if (tracks.isNotEmpty()) genres to tracks else null
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
