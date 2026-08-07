package com.lmg.vk.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lmg.vk.network.VkApiLocator
import com.lmg.vk.network.VkResult
import com.lmg.vk.network.dto.music.AnnualResultValue
import com.lmg.vk.network.dto.music.AudioGetAnnualResultBlockDto
import com.lmg.vk.network.dto.music.Y25CBlock
import com.lmg.vk.network.dto.music.Y25Title
import com.lmg.vk.network.methods.VkYearStatsApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Одна строка внутри блока: подпись + значение. Всё — из ответа VK. */
data class YearRecapLine(
    val title: String = "",
    val value: String = "",
    val caption: String = "",
    val coverUrl: String? = null,
) {
    /** Пустая строка не должна занимать место на экране. */
    val isEmpty: Boolean
        get() = title.isBlank() && value.isBlank() && caption.isBlank() && coverUrl.isNullOrBlank()
}

/**
 * Блок «Итогов года» в виде, пригодном для отрисовки. Оба серверных источника
 * (метрики пользователя и итоги артиста) сводятся сюда — так же, как в VK X,
 * где `studio.getArtistYearRecapData` конвертируется в модель метрик
 * (`FRESH C4271e.java:32-125`, функция `m1510this`).
 */
data class YearRecapBlock(
    val key: String,
    val type: String,
    val name: String,
    val titles: List<YearRecapLine> = emptyList(),
    val subtitles: List<YearRecapLine> = emptyList(),
    val metrics: List<YearRecapLine> = emptyList(),
    val photoUrls: List<String> = emptyList(),
    val backgroundUrl: String? = null,
    val playlistId: Long? = null,
    val playlistTitle: String? = null,
    val playlistPhotoUrl: String? = null,
) {
    /** Есть ли что показать: блок без единой строки и картинки рисовать нечего. */
    val hasContent: Boolean
        get() = titles.any { !it.isEmpty } || subtitles.any { !it.isEmpty } ||
            metrics.any { !it.isEmpty } || photoUrls.any { it.isNotBlank() } ||
            !playlistTitle.isNullOrBlank()
}

/** Состояние кнопки «Собрать плейлист». */
sealed interface PlaylistCreationState {
    data object Idle : PlaylistCreationState
    data object InProgress : PlaylistCreationState

    /** Плейлист уже существует — id получен от VK (созданный сейчас или ранее). */
    data class Created(val playlistId: Int) : PlaylistCreationState
    data class Failed(val message: String) : PlaylistCreationState
}

data class YearRecapUiState(
    val isLoading: Boolean = false,
    val blocks: List<YearRecapBlock> = emptyList(),
    val audioTooltip: String = "",
    /** Заголовки действий из ответа метрик (`actions`). */
    val actionTitles: List<String> = emptyList(),
    val error: String? = null,
    val playlistTitle: String? = null,
    val creation: PlaylistCreationState = PlaylistCreationState.Idle,
) {
    /** Загрузка закончилась, ошибки нет, но VK не дал ни одного блока. */
    val isEmpty: Boolean
        get() = !isLoading && error == null && blocks.isEmpty()

    /** Создавать плейлист можно, только если VK прислал для этого блок. */
    val canCreatePlaylist: Boolean
        get() = playlistTitle != null && creation !is PlaylistCreationState.Created
}

/**
 * ViewModel экрана «Итоги года».
 *
 * Данных не изобретает: показывается ровно то, что пришло от VK. Если метод
 * недоступен (нет токена мини-приложения, ошибка, пустой список блоков) — экран
 * получает либо текст ошибки, либо честный признак пустоты.
 *
 * [artistId] задаётся, только когда экран открыт для конкретного артиста: метод
 * `studio.*` — это API «Студии» ВКонтакте, то есть итоги года ДЛЯ артиста. Без
 * него грузятся метрики самого пользователя (`musicStatResults.getMetrics`).
 */
class YearRecapViewModel : ViewModel() {

    // lazy, а не сразу: VkApiLocator.apiClient() бросает исключение, пока
    // LmgApplication не поднял сетевое ядро. ViewModel не должна падать в
    // конструкторе только из-за порядка инициализации.
    private val api by lazy { VkYearStatsApi(VkApiLocator.apiClient()) }

    private val _state = MutableStateFlow(YearRecapUiState())
    val state: StateFlow<YearRecapUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var creationJob: Job? = null

    /** Артист, для которого грузились итоги; null — метрики пользователя. */
    private var artistId: String? = null

    fun load(artistId: String? = null, force: Boolean = false) {
        val changedTarget = artistId != this.artistId
        if (!force && !changedTarget && (_state.value.blocks.isNotEmpty() || _state.value.isLoading)) {
            return
        }
        this.artistId = artistId

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            // Сеть/инициализация могут бросить: экран обязан показать текст
            // ошибки, а не уронить приложение.
            runCatching {
                if (artistId.isNullOrBlank()) loadUserMetrics() else loadArtistRecap(artistId)
            }.onFailure { failure ->
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = failure.message?.takeIf { it.isNotBlank() }
                        ?: "Не удалось получить итоги года",
                )
            }
        }
    }

    private suspend fun loadUserMetrics() {
        when (val result = api.getMetrics()) {
            is VkResult.Success -> {
                val blocks = result.data.blocks
                    .filter { it.isVisible }
                    .sortedBy { it.order }
                    .mapIndexed { index, block -> block.toUiBlock(index) }
                    .filter { it.hasContent }

                // Заголовок будущего плейлиста берём из блока, как VK X
                // (`FRESH C4673e.java:232-248`): своё имя, иначе дефолт "My 2025".
                val playlistBlock = result.data.blocks.firstOrNull { it.playlist != null }
                val playlistTitle = playlistBlock?.playlist?.title
                    ?.takeIf { it.isNotBlank() }
                    ?: playlistBlock?.let { VkYearStatsApi.DEFAULT_PLAYLIST_TITLE }

                _state.value = _state.value.copy(
                    isLoading = false,
                    blocks = blocks,
                    audioTooltip = result.data.audioTooltip.orEmpty(),
                    actionTitles = result.data.actions.mapNotNull { action ->
                        action.title?.takeIf { it.isNotBlank() }
                    },
                    error = null,
                    playlistTitle = playlistTitle,
                )
                restoreCreatedPlaylist()
            }

            is VkResult.Error -> _state.value = _state.value.copy(
                isLoading = false,
                error = result.message.ifBlank { "ВКонтакте не вернул итоги года" },
            )
        }
    }

    private suspend fun loadArtistRecap(artistId: String) {
        when (val result = api.getArtistYearRecap(artistId)) {
            is VkResult.Success -> {
                val blocks = result.data.blocks
                    .filter { it.isVisible }
                    .sortedBy { it.order }
                    .mapIndexed { index, block -> block.toUiBlock(index) }
                    .filter { it.hasContent }

                _state.value = _state.value.copy(
                    isLoading = false,
                    blocks = blocks,
                    audioTooltip = "",
                    actionTitles = emptyList(),
                    error = null,
                    // Плейлист по метрикам — фича раздела метрик, не итогов артиста.
                    playlistTitle = null,
                )
            }

            is VkResult.Error -> _state.value = _state.value.copy(
                isLoading = false,
                error = result.message.ifBlank { "ВКонтакте не вернул итоги года артиста" },
            )
        }
    }

    /**
     * Если плейлист уже создавался раньше, VK помнит его id в своём `storage`
     * (§2.2). Читаем, чтобы не предлагать создать второй такой же.
     */
    private suspend fun restoreCreatedPlaylist() {
        val existing = runCatching { api.createdPlaylistId() }.getOrNull() ?: return
        _state.value = _state.value.copy(creation = PlaylistCreationState.Created(existing))
    }

    /** Действие пользователя из VK X: собрать плейлист по результатам метрик. */
    fun createPlaylist() {
        val title = _state.value.playlistTitle ?: return
        if (_state.value.creation is PlaylistCreationState.InProgress) return

        creationJob?.cancel()
        creationJob = viewModelScope.launch {
            _state.value = _state.value.copy(creation = PlaylistCreationState.InProgress)
            val result = runCatching { api.createPlaylist(title) }
                .getOrElse { failure ->
                    if (failure is kotlinx.coroutines.CancellationException) throw failure
                    VkResult.Error(0, failure.message.orEmpty())
                }
            _state.value = when (result) {
                is VkResult.Success -> _state.value.copy(
                    creation = PlaylistCreationState.Created(result.data.id),
                )

                is VkResult.Error -> _state.value.copy(
                    creation = PlaylistCreationState.Failed(
                        result.message.ifBlank { "Не удалось создать плейлист" },
                    ),
                )
            }
        }
    }

    /** Сбросить сообщение об ошибке создания, чтобы можно было повторить. */
    fun dismissCreationError() {
        if (_state.value.creation is PlaylistCreationState.Failed) {
            _state.value = _state.value.copy(creation = PlaylistCreationState.Idle)
        }
    }

    fun retry() = load(artistId, force = true)
}

// ─────────────────────────── Маппинг ответов ───────────────────────────

private fun Y25Title.toLine(): YearRecapLine = YearRecapLine(
    title = title.orEmpty(),
    value = value.orEmpty(),
    caption = caption.orEmpty(),
    coverUrl = content?.coverUrl?.takeIf { it.isNotBlank() },
)

private fun Y25CBlock.toUiBlock(index: Int): YearRecapBlock = YearRecapBlock(
    // name у VK не обязан быть уникальным, поэтому в ключ идёт и позиция.
    key = "metrics-$index-${name.orEmpty()}",
    type = type.orEmpty(),
    name = name.orEmpty(),
    titles = titles.map { it.toLine() }.filterNot { it.isEmpty },
    subtitles = subtitles.map { it.toLine() }.filterNot { it.isEmpty },
    metrics = metrics.map { it.toLine() }.filterNot { it.isEmpty },
    photoUrls = photoUrls.filter { it.isNotBlank() },
    // Для телефона берём мобильную картинку — как VK X на Android.
    backgroundUrl = background?.mobile?.coverUrl?.takeIf { it.isNotBlank() }
        ?: background?.story?.coverUrl?.takeIf { it.isNotBlank() },
    playlistId = playlist?.id?.takeIf { it != 0L },
    playlistTitle = playlist?.title?.takeIf { it.isNotBlank() },
    playlistPhotoUrl = playlist?.photoUrl?.takeIf { it.isNotBlank() },
)

private fun AnnualResultValue.toLine(): YearRecapLine = YearRecapLine(
    title = title.orEmpty(),
    value = value.orEmpty(),
    // subtitle и caption у этого DTO — разные поля; показываем то, что есть.
    caption = subtitle?.takeIf { it.isNotBlank() } ?: caption.orEmpty(),
    coverUrl = photoUrl?.takeIf { it.isNotBlank() }
        ?: photoUrls.firstOrNull { it.isNotBlank() },
)

/**
 * ОТСТУПЛЕНИЕ ОТ VK X, осознанное. Оригинал в `m1510this` раскладывает поля
 * `Value` по-разному для каждого типа блока: например для `base` в заголовок
 * строки уходит `subtitle`, а в значение — `title`, а для `extended` наоборот, и
 * `caption` там же превращается в URL обложки. Эти перестановки подогнаны под
 * сторис-карусель VK X (полноэкранные слайды с фоном).
 *
 * Здесь экран — вертикальный список, поэтому каждое поле показывается под своим
 * же смыслом: `title` как подпись, `value` как значение, `photo_url` как
 * обложка. Так ни одно значение не теряется и не подменяется другим, а порядок
 * блоков и их видимость (`order`/`is_visible`) соблюдаются как в оригинале.
 */
private fun AudioGetAnnualResultBlockDto.toUiBlock(index: Int): YearRecapBlock {
    val headline = screenTitle?.takeIf { it.isNotBlank() }
    val subhead = screenSubtitle?.takeIf { it.isNotBlank() }
    val caption = screenCaption?.takeIf { it.isNotBlank() }

    return YearRecapBlock(
        key = "artist-$index-${name.orEmpty()}",
        type = type.orEmpty(),
        name = name.orEmpty(),
        titles = listOfNotNull(headline?.let { YearRecapLine(title = it) }),
        subtitles = listOfNotNull(
            subhead?.let { YearRecapLine(title = it) },
            caption?.let { YearRecapLine(title = it) },
        ),
        metrics = (metrics + titles + subtitles).map { it.toLine() }.filterNot { it.isEmpty },
        photoUrls = photoUrls.filter { it.isNotBlank() },
        // mobile-фон оригинала = background_url, desktop = fallback
        // (`FRESH C4271e.java:37`).
        backgroundUrl = backgroundUrl?.takeIf { it.isNotBlank() }
            ?: fallbackBackgroundUrl?.takeIf { it.isNotBlank() },
        playlistId = null, // у этого ответа id плейлиста нет — только имя и обложка
        playlistTitle = playlistTitle?.takeIf { it.isNotBlank() },
        playlistPhotoUrl = playlistPhotoUrl?.takeIf { it.isNotBlank() },
    )
}
