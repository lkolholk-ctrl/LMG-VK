package com.lmg.vk.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

/**
 * ViewModel полноэкранной ленты сниппетов (`audio.getSnippets`).
 *
 * ── Что это в VK X ───────────────────────────────────────────────────
 * Разобранный класс фида — `C1718e` (см. реверс). Там это НЕ виджет в списке:
 * фрагмент держит список `AudioSnippetEntry` (поле-список внутри класса) и ДВА
 * индекса-состояния (`C16330e`) — внешний (какая подборка) и внутренний (какой
 * трек внутри подборки). Отсюда и вложенные пейджеры на экране.
 *
 * Обработчик `strictfp(...)` берёт `AudioSnippetEntry.audios` и по внутреннему
 * индексу достаёт `AudioTrack`, а `C13721e` (ветка `case 1`) на смене страницы
 * сравнивает пару индексов с текущей и, если она изменилась, пересобирает
 * источник и стартует плеер. Ключевое: URI берётся напрямую из
 * `AudioTrack.url` (поле `adcel`), без единого `ClippingConfiguration` — по
 * всему VK X таких вызовов нет вообще.
 *
 * ── Почему обрезку НЕ делаем сами ────────────────────────────────────
 * Обрезает СЕРВЕР: в `audio.getSnippets` VK сразу отдаёт короткий `url`, а его
 * длину сообщает полем `stream_duration` (секунды). Границы `clip_from`/
 * `clip_to` относятся к другому методу (микс плейлиста, `C5814e`), и даже там
 * VK X не режет поток, а лишь считает `(clip_to - clip_from) / 1000` и пишет
 * в то же `stream_duration`. Подробный разбор — в `dto/music/SnippetsFeed.kt`.
 *
 * Практический вывод: плеер трогать не нужно. Нам достаточно играть
 * присланный URL и рисовать прогресс по `stream_duration`.
 */
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

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = current.copy(isLoading = true, error = null)
            // Сеть и разбор ответа могут бросить — экран показывает текст
            // ошибки с повтором, а не роняет приложение.
            runCatching { requestSnippets() }
                .onFailure { failure ->
                    if (failure is kotlinx.coroutines.CancellationException) throw failure
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = failure.message?.takeIf(String::isNotBlank)
                            ?: "Не удалось загрузить сниппеты",
                    )
                }
        }
    }

    private suspend fun requestSnippets() {
        // count как в VK X (`C13029e`, ветка getSnippets): ровно 3. Больше VK
        // для этой ленты не отдаёт, а завышать параметр — гадание.
        when (val result = api.getSnippets(count = SNIPPETS_COUNT)) {
            is VkResult.Success -> {
                val pages = result.data
                    .mapIndexedNotNull { index, entry -> entry.toPageUi(index) }
                _state.value = _state.value.copy(
                    isLoading = false,
                    pages = pages,
                    error = null,
                )
            }

            is VkResult.Error -> _state.value = _state.value.copy(
                isLoading = false,
                error = result.message.takeIf(String::isNotBlank)
                    ?: "VK вернул ошибку ${result.code}",
            )
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
