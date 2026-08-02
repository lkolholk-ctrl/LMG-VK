package com.lmg.vk.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lmg.vk.engine.backend.MusicAuth
import com.lmg.vk.engine.backend.MusicBackend
import com.lmg.vk.engine.backend.backendUserMessage
import com.lmg.vk.engine.backend.SearchItem
import com.lmg.vk.engine.backend.SearchSource
import com.lmg.vk.engine.backend.WaveOnboardingArtist
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ViewModel for the Search screen.
 * Manages search query with debounce, search results, and genre categories.
 */
@OptIn(FlowPreview::class)
class SearchViewModel : ViewModel() {

    // ─── Search Query ───
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    // ─── Search Results ───
    private val _searchResults = MutableStateFlow<List<SearchItem>>(emptyList())
    val searchResults: StateFlow<List<SearchItem>> = _searchResults

    // ─── Categories (Popular Artists from wave onboarding) ───
    private val _categories = MutableStateFlow<List<WaveOnboardingArtist>>(emptyList())
    val categories: StateFlow<List<WaveOnboardingArtist>> = _categories

    // ─── Loading / Error ───
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // ─── Is Search Active (query not empty) ───
    val isSearchActive: Boolean
        get() = _query.value.isNotBlank()

    init {
        // Debounce 300мс после остановки ввода. БЕЗ distinctUntilChanged:
        // он сравнивал с последним ПРОПУЩЕННЫМ значением, поэтому «стёр и
        // набрал то же название» глотался молча — поиск не запускался вообще
        // (главная причина «пишу правильно, а не ищет»).
        _query
            .debounce(300)
            .onEach { q ->
                val t = q.trim()
                if (t.length >= 2) performSearch(t)
            }
            .launchIn(viewModelScope)
    }

    /**
     * Update search query. Triggers debounced search automatically.
     */
    fun setQuery(newQuery: String) {
        _query.value = newQuery
        if (newQuery.isBlank()) {
            _searchResults.value = emptyList()
            _error.value = null
        }
    }

    /**
     * Clear search query and results.
     */
    fun clearQuery() {
        _query.value = ""
        _searchResults.value = emptyList()
        _error.value = null
    }

    /**
     * Set search source (Apple, VK, All).
     */
    fun setSource(source: String) {
        _selectedSource.value = source
        // Re-trigger search if query is not empty
        if (_query.value.isNotBlank()) {
            performSearch(_query.value.trim())
        }
    }

    /**
     * Load categories (popular artists) for the idle state.
     */
    fun loadCategories() {
        viewModelScope.launch {
            try {
                val artists = MusicBackend.getWavePopularArtists()
                _categories.value = artists
            } catch (e: Exception) {
                // Silently fail — categories are decorative
                _categories.value = emptyList()
            }
        }
    }

    /**
     * Perform search immediately (bypass debounce).
     */
    fun searchNow() {
        val q = _query.value.trim()
        if (q.length >= 2) {
            performSearch(q)
        }
    }

    /** Активный поисковый запрос — новый всегда отменяет предыдущий. */
    private var searchJob: Job? = null

    private fun performSearch(q: String) {
        // Гонка ответов: без отмены медленный ответ на СТАРЫЙ запрос мог
        // прийти позже свежего и затереть результаты (при быстром вводе —
        // «правильное название, а список пустой/чужой»).
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                var result = MusicBackend.searchAll(q)
                // Транзиент от быстрого ввода: один ТИХИЙ повтор через 1.2с при сбое
                if (result == null && q == _query.value.trim()) {
                    kotlinx.coroutines.delay(1200)
                    if (q == _query.value.trim())
                        result = MusicBackend.searchAll(q)
                }
                if (q != _query.value.trim() && _query.value.trim().length >= 2)
                    return@launch

                val items = result?.items ?: emptyList()
                _searchResults.value = items
                if (result == null) {
                    // Человекочитаемое сообщение вместо сырого JSON тела ответа.
                    _error.value = backendUserMessage(MusicBackend.lastApiException.value)
                }
                // Apple-поиск не отдаёт duration ВООБЩЕ (по доке /search) —
                // длительности треков дотягиваем батчем /tracks/meta и вливаем
                // в выдачу (иначе в строках пусто/0:00 — полевой фидбек).
                enrichDurations(q, requestedSource, items)
            } catch (ce: CancellationException) {
                throw ce   // отмена — не ошибка, не показываем её пользователю
            } catch (e: Exception) {
                _error.value = backendUserMessage(e)
            } finally {
                // Отменённый job не гасит спиннер нового (тот уже поставил true).
                if (isActive) _isLoading.value = false
            }
        }
    }

    /**
     * Дотянуть длительности треков, у которых их нет в выдаче (Apple /search
     * не отдаёт duration), батчем POST /tracks/meta (до 50 id). Ошибки тихие —
     * длительность декоративна; без неё просто не показываем время.
     */
    private fun enrichDurations(q: String, source: String, items: List<SearchItem>) {
        val needIds = items
            .filter { it.isTrack && it.durationMs <= 0L }
            .map { it.id }
            .distinct()
            .take(50)
        if (needIds.isEmpty()) return
        viewModelScope.launch {
            try {
                val meta = com.lmg.vk.engine.backend.MusicBackend.getInstance()
                    .getBatchTrackMeta(needIds).getOrNull() ?: return@launch
                // Пользователь уже ищет другое / сменил сегмент — не вливаем.
                if (q != _query.value.trim() || source != _selectedSource.value) return@launch
                val byId = meta.items
                    .filter { it.isSuccess && it.durationMs > 0L }
                    .associateBy { it.trackId ?: it.id }
                if (byId.isEmpty()) return@launch
                _searchResults.value = _searchResults.value.map { item ->
                    val m = byId[item.id]
                    // durationMs уже нормализован в мс; source не трогаем —
                    // значение > 30с не попадёт под повторную конвертацию.
                    if (m != null && item.durationMs <= 0L) item.copy(duration = m.durationMs)
                    else item
                }
            } catch (_: Exception) { /* тихо — время появится в детальных экранах */ }
        }
    }

    /**
     * Check if user has premium for max quality streaming.
     */
    val isPremium: Boolean
        get() = MusicAuth.isPremium.value
}
