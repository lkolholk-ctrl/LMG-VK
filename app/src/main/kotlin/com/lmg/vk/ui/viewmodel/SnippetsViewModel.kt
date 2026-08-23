package com.lmg.vk.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lmg.vk.engine.backend.MusicAuth
import com.lmg.vk.network.VkApiLocator
import com.lmg.vk.network.VkResult
import com.lmg.vk.network.dto.music.AudioSnippetEntry
import com.lmg.vk.network.dto.music.AudioTrack
import com.lmg.vk.network.dto.music.coverUrl
import com.lmg.vk.network.dto.music.SnippetPageUi
import com.lmg.vk.network.dto.music.SnippetTrackUi
import com.lmg.vk.network.methods.VkAudioApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Состояние ленты сниппетов. Загрузка/ошибка/пустота — три РАЗНЫХ состояния:
 * пустой ответ VK это не ошибка, и экран обязан честно сказать, что подборок
 * нет, а не показывать вечный спиннер.
 */
data class SnippetsUiState(
    val isLoading: Boolean = false,
    val pages: List<SnippetPageUi> = emptyList(),
    val error: String? = null,
) {
    /** Загрузка кончилась, ошибки нет, но VK не дал ни одной подборки. */
    val isEmpty: Boolean
        get() = !isLoading && error == null && pages.isEmpty()
}

class SnippetsViewModel : ViewModel() {

    // lazy: VkApiLocator.apiClient() бросает, пока LmgApplication не поднял
    // сетевое ядро. ViewModel не должна падать в конструкторе из-за порядка init.
    private val api by lazy { VkAudioApi(VkApiLocator.apiClient()) }

    private val _state = MutableStateFlow(SnippetsUiState())
    val state: StateFlow<SnippetsUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    /**
     * [force] нужен кнопке «Повторить»: обычный вход на экран не должен
     * перезапрашивать VK, если лента уже получена.
     */
    fun load(force: Boolean = false) {
        val current = _state.value
        if (!force && (current.pages.isNotEmpty() || current.isLoading)) return
        val accountId = MusicAuth.profileId.value

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = if (force) {
                SnippetsUiState(isLoading = true)
            } else {
                current.copy(isLoading = true, error = null)
            }
            // Сеть и разбор ответа могут бросить — экран показывает текст
            // ошибки с повтором, а не роняет приложение.
            runCatching { requestSnippets(accountId) }
                .onFailure { failure ->
                    if (failure is kotlinx.coroutines.CancellationException) throw failure
                    if (MusicAuth.profileId.value != accountId) return@onFailure
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = failure.message?.takeIf(String::isNotBlank)
                            ?: "Не удалось загрузить сниппеты",
                    )
                }
        }
    }

    private suspend fun requestSnippets(accountId: Long?) {
        when (val result = api.getSnippets(count = SNIPPETS_COUNT)) {
            is VkResult.Success -> {
                val pages = result.data
                    .mapIndexedNotNull { index, entry -> entry.toPageUi(index) }
                if (MusicAuth.profileId.value != accountId) return
                _state.value = _state.value.copy(
                    isLoading = false,
                    pages = pages,
                    error = null,
                )
            }

            is VkResult.Error -> if (MusicAuth.profileId.value == accountId) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = result.message.takeIf(String::isNotBlank)
                        ?: "VK вернул ошибку ${result.code}",
                )
            }
        }
    }

    /**
     * Подборка без единого играбельного трека выбрасывается (`null`): в фиде
     * такая страница была бы чёрным экраном, по которому нечего листать.
     */
    private fun AudioSnippetEntry.toPageUi(index: Int): SnippetPageUi? {
        val playable = audios.orEmpty().mapNotNull { it.toSnippetTrackUi() }
        if (playable.isEmpty()) return null
        return SnippetPageUi(
            // track_code у VK уникален на блок, но пустым тоже бывает —
            // индекс в хвосте гарантирует стабильный ключ для пейджера.
            key = "${track_code.orEmpty()}_$index",
            title = title.orEmpty(),
            text = text.orEmpty(),
            imageUrl = image?.takeIf(String::isNotBlank),
            navUrl = nav_url?.takeIf(String::isNotBlank),
            trackCode = track_code.orEmpty(),
            tracks = playable,
        )
    }

    /**
     * Трек без URL играть нечем: в этой ленте VK отдаёт уже подписанный
     * короткий поток, и повторный резолв по id вернул бы ПОЛНЫЙ трек, то есть
     * сломал бы саму идею сниппета. Поэтому пустой `url` — причина пропустить
     * трек, а не поле для подстановки.
     */
    private fun AudioTrack.toSnippetTrackUi(): SnippetTrackUi? {
        val directUrl = url.takeIf(String::isNotBlank) ?: return null
        if (!isAvailable) return null
        return SnippetTrackUi(
            fullId = fullId,
            title = title,
            artist = artist.ifBlank { main_artists.orEmpty().joinToString(", ") { it.name } },
            coverUrl = coverUrl(),
            directUrl = directUrl,
            fullDurationMs = duration * 1000L,
            // stream_duration — секунды, и это ЕДИНСТВЕННОЕ реальное поле про
            // длину фрагмента, которое VK присылает для сниппетов.
            snippetDurationMs = stream_duration.coerceAtLeast(0) * 1000L,
            isExplicit = is_explicit,
            trackCode = track_code,
        )
    }

    private companion object {
        const val SNIPPETS_COUNT = 3
    }
}
