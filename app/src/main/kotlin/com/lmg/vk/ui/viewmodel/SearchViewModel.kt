package com.lmg.vk.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lmg.vk.engine.backend.MusicAuth
import com.lmg.vk.engine.backend.MusicBackend
import com.lmg.vk.engine.backend.backendUserMessage
import com.lmg.vk.engine.backend.SearchItem
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
 * Manages the VK search query and result state.
 */
@OptIn(FlowPreview::class)
class SearchViewModel : ViewModel() {

    // ─── Search Query ───
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    // ─── Search Results ───
    private val _searchResults = MutableStateFlow<List<SearchItem>>(emptyList())
    val searchResults: StateFlow<List<SearchItem>> = _searchResults

    // ─── Loading / Error ───
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore

    private val _loadMoreError = MutableStateFlow<String?>(null)
    val loadMoreError: StateFlow<String?> = _loadMoreError

    private val _pagingKey = MutableStateFlow<Int?>(null)
    val pagingKey: StateFlow<Int?> = _pagingKey

    private var nextOffset: Int? = null
    private var previousPageKeys: List<String>? = null
    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null

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
        MusicAuth.profileId
            .onEach {
                searchJob?.cancel()
                loadMoreJob?.cancel()
                _query.value = ""
                _searchResults.value = emptyList()
                _isLoading.value = false
                _error.value = null
                resetPaging()
            }
            .launchIn(viewModelScope)
    }

    /**
     * Update search query. Triggers debounced search automatically.
     */
    fun setQuery(newQuery: String) {
        _query.value = newQuery
        if (newQuery.isBlank()) {
            loadMoreJob?.cancel()
            _searchResults.value = emptyList()
            _error.value = null
            resetPaging()
        }
    }

    /**
     * Clear search query and results.
     */
    fun clearQuery() {
        _query.value = ""
        loadMoreJob?.cancel()
        _searchResults.value = emptyList()
        _error.value = null
        resetPaging()
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

    private fun performSearch(q: String) {
        // Гонка ответов: без отмены медленный ответ на СТАРЫЙ запрос мог
        // прийти позже свежего и затереть результаты (при быстром вводе —
        // «правильное название, а список пустой/чужой»).
        searchJob?.cancel()
        loadMoreJob?.cancel()
        resetPaging()
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
                nextOffset = result?.nextOffset
                _pagingKey.value = nextOffset
                _hasMore.value = result?.hasMore == true && nextOffset != null
                if (result == null) {
                    // Человекочитаемое сообщение вместо сырого JSON тела ответа.
                    _error.value = backendUserMessage(MusicBackend.lastApiException.value)
                }
                // Длительности треков дотягиваем батчем /tracks/meta и вливаем в выдачу
                enrichDurations(q, items)
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

    /** Request and append the next `audio.search` page. */
    fun loadMore() {
        val q = _query.value.trim()
        val offset = nextOffset
        if (q.length < 2 || offset == null || !_hasMore.value ||
            _isLoading.value || _isLoadingMore.value
        ) return

        loadMoreJob = viewModelScope.launch {
            _isLoadingMore.value = true
            _loadMoreError.value = null
            try {
                val result = MusicBackend.searchAll(q, offset = offset)
                if (q != _query.value.trim()) return@launch
                if (result == null) {
                    // A failed page remains retryable; it is not the end of results.
                    _loadMoreError.value = backendUserMessage(MusicBackend.lastApiException.value)
                    return@launch
                }

                val pageItems = result.items
                val pageKeys = pageItems.filter { it.isTrack }.map { it.pagingKey() }
                val repeatedPage = pageKeys.isNotEmpty() && pageKeys == previousPageKeys
                previousPageKeys = pageKeys
                _searchResults.value = mergeSearchPages(_searchResults.value, pageItems)
                nextOffset = result.nextOffset
                _pagingKey.value = nextOffset
                _hasMore.value = result.hasMore && nextOffset != null && !repeatedPage
                enrichDurations(q, pageItems)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _loadMoreError.value = backendUserMessage(e)
            } finally {
                if (isActive) _isLoadingMore.value = false
            }
        }
    }

    private fun resetPaging() {
        nextOffset = null
        previousPageKeys = null
        _pagingKey.value = null
        _hasMore.value = false
        _isLoadingMore.value = false
        _loadMoreError.value = null
    }

    private fun mergeSearchPages(
        current: List<SearchItem>,
        page: List<SearchItem>,
    ): List<SearchItem> {
        val seen = current.mapTo(HashSet()) { it.pagingKey() }
        return current + page.filter { seen.add(it.pagingKey()) }
    }

    private fun SearchItem.pagingKey(): String = when {
        isArtist -> "artist:${artistId?.takeIf(String::isNotBlank) ?: id}"
        isAlbum -> "album:$id"
        else -> "track:$id"
    }

    /**
     * Дотянуть длительности треков, у которых их нет в выдаче, батчем.
     */
    private fun enrichDurations(q: String, items: List<SearchItem>) {
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
                if (q != _query.value.trim()) return@launch
                val byId = meta.items
                    .filter { it.isSuccess && it.durationMs > 0L }
                    .associateBy { it.trackId ?: it.id }
                if (byId.isEmpty()) return@launch
                _searchResults.value = _searchResults.value.map { item ->
                    val m = byId[item.id]
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
