package com.lmg.vk.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lmg.vk.network.RawHttpResponse
import com.lmg.vk.network.VkApiClient
import com.lmg.vk.network.VkApiLocator
import com.lmg.vk.network.VkJson
import com.lmg.vk.network.VkMethod
import com.lmg.vk.network.VkParsedResponse
import com.lmg.vk.network.VkResponseParser
import com.lmg.vk.network.VkResult
import com.lmg.vk.network.dto.VKError
import com.lmg.vk.network.dto.music.VkArtistDto
import com.lmg.vk.network.dto.music.VkRootItems
import com.lmg.vk.network.methods.VkAudioApi
import com.squareup.moshi.Types
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Один исполнитель в списке онбординга — ровно то, что пришло от VK.
 *
 * [id] обязателен: именно он уходит в `artist_ids` при завершении онбординга.
 * Артистов без id мы не показываем вовсе — выбрать их всё равно нельзя, а
 * рисовать невыбираемую карточку значит обманывать пользователя.
 */
data class OnboardingArtist(
    val id: String,
    val name: String,
    val coverUrl: String?,
    /** Жанры от VK — подпись под именем. Своих ярлыков не придумываем. */
    val genres: List<String> = emptyList(),
)

/** Чем закончилась отправка выбора (`audio.finishRecomsOnboarding`). */
sealed interface OnboardingSubmitState {
    data object Idle : OnboardingSubmitState
    data object InProgress : OnboardingSubmitState

    /** VK принял список — рекомендации перестроятся на его стороне. */
    data object Done : OnboardingSubmitState
    data class Failed(val message: String) : OnboardingSubmitState
}

data class OnboardingUiState(
    val isLoading: Boolean = false,
    /** Что показываем сейчас: подсказки VK либо результат поиска по запросу. */
    val artists: List<OnboardingArtist> = emptyList(),
    val query: String = "",
    /** Порядок важен: в `artist_ids` он уходит как есть, как в VK X. */
    val selectedIds: List<String> = emptyList(),
    val error: String? = null,
    val submit: OnboardingSubmitState = OnboardingSubmitState.Idle,
) {
    val isSearching: Boolean get() = query.isNotBlank()

    /** Загрузка кончилась, ошибки нет, но VK не дал ни одного исполнителя. */
    val isEmpty: Boolean
        get() = !isLoading && error == null && artists.isEmpty()

    /**
     * Прогресс шкалы — как в VK X (`C13752e.java:668`): каждый выбранный
     * исполнитель даёт 0.2, значение зажато сверху единицей.
     */
    val progress: Float
        get() = minOf(1f, selectedIds.size * PROGRESS_PER_ARTIST)

    /**
     * Кнопку «Готово» VK X включает от пяти исполнителей
     * (`C13752e.java:721`, `:805` — `size >= 5`).
     */
    val canFinish: Boolean
        get() = selectedIds.size >= MIN_ARTISTS && submit !is OnboardingSubmitState.InProgress

    /** Сколько ещё нужно выбрать, чтобы разблокировать отправку. */
    val remaining: Int get() = (MIN_ARTISTS - selectedIds.size).coerceAtLeast(0)

    companion object {
        const val MIN_ARTISTS = 5
        const val PROGRESS_PER_ARTIST = 0.2f
    }
}

/**
 * ViewModel онбординга музыкальных рекомендаций — порт экрана VK X `C14197e`
 * (спека `docs/vkx-port/01-music.md` §11).
 *
 * Логика оригинала воспроизведена один в один: пустая строка поиска — список от
 * `audio.recommendationsOnboarding`, непустая — `audio.searchArtists` с
 * `count=100` и дебаунсом 300 мс (`C3523e.java:117-119`). Выбранные id уходят в
 * `audio.finishRecomsOnboarding` через запятую (`C13029e.java:212-222`, id=4).
 *
 * Никаких заготовленных артистов и жанров здесь нет: список целиком приходит от
 * VK, и если VK его не дал — экран получает признак пустоты или текст ошибки.
 */
class OnboardingViewModel : ViewModel() {

    // lazy — VkApiLocator.apiClient() бросает, пока LmgApplication не поднял
    // сетевое ядро; ViewModel не должна падать в конструкторе из-за порядка init.
    private val client: VkApiClient by lazy { VkApiLocator.apiClient() }
    private val audioApi: VkAudioApi by lazy { VkAudioApi(client) }
    private val onboardingApi: VkOnboardingApi by lazy { VkOnboardingApi(client) }

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    /** Отдельный поток запроса — на нём висит дебаунс, как в оригинале. */
    private val queryFlow = MutableStateFlow("")

    private var loadJob: Job? = null
    private var submitJob: Job? = null

    init {
        observeQuery()
    }

    /**
     * Дебаунс поиска — 300 мс, как в оригинале (`C3523e.java:117-119`).
     * Отдельная функция, а не тело `init`: аннотацию `@OptIn` на
     * инициализирующий блок Kotlin не пускает.
     */
    @OptIn(FlowPreview::class)
    private fun observeQuery() {
        // drop(1) — стартовое пустое значение не должно дублировать первую
        // загрузку подсказок, её запускает сам экран через load().
        viewModelScope.launch {
            queryFlow
                .drop(1)
                .debounce(SEARCH_DEBOUNCE_MS)
                .collectLatest { query -> fetch(query) }
        }
    }

    /** Первая загрузка подсказок VK. Повторно не дёргает сеть без нужды. */
    fun load(force: Boolean = false) {
        if (!force && (_state.value.artists.isNotEmpty() || _state.value.isLoading)) return
        fetchIn(_state.value.query)
    }

    fun onQueryChange(value: String) {
        _state.value = _state.value.copy(query = value)
        queryFlow.value = value
    }

    fun clearQuery() = onQueryChange("")

    /** Тап по карточке: VK X просто переключает наличие id в списке выбранных. */
    fun toggle(artistId: String) {
        if (artistId.isBlank()) return
        val current = _state.value.selectedIds
        val updated = if (artistId in current) current - artistId else current + artistId
        _state.value = _state.value.copy(
            selectedIds = updated,
            // Прошлая неудача отправки больше не актуальна — выбор изменился.
            submit = if (_state.value.submit is OnboardingSubmitState.Failed) {
                OnboardingSubmitState.Idle
            } else {
                _state.value.submit
            },
        )
    }

    fun retry() = fetchIn(_state.value.query)

    /** Отправить выбор: `audio.finishRecomsOnboarding` c `artist_ids`. */
    fun finish() {
        val ids = _state.value.selectedIds
        if (ids.size < OnboardingUiState.MIN_ARTISTS) return
        if (_state.value.submit is OnboardingSubmitState.InProgress) return

        submitJob?.cancel()
        submitJob = viewModelScope.launch {
            _state.value = _state.value.copy(submit = OnboardingSubmitState.InProgress)
            val result = runCatching { onboardingApi.finishOnboarding(ids) }
                .getOrElse { failure ->
                    if (failure is kotlinx.coroutines.CancellationException) throw failure
                    VkResult.Error(0, failure.message.orEmpty())
                }
            _state.value = when (result) {
                is VkResult.Success -> _state.value.copy(submit = OnboardingSubmitState.Done)
                is VkResult.Error -> _state.value.copy(
                    submit = OnboardingSubmitState.Failed(
                        result.message.ifBlank { "Не удалось сохранить выбор" },
                    ),
                )
            }
        }
    }

    fun dismissSubmitError() {
        if (_state.value.submit is OnboardingSubmitState.Failed) {
            _state.value = _state.value.copy(submit = OnboardingSubmitState.Idle)
        }
    }

    private fun fetchIn(query: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { fetch(query) }
    }

    /**
     * Одна загрузка списка. Своих корутин не запускает: вызывается либо из
     * [fetchIn], либо из `collectLatest` дебаунса — в обоих случаях отмена
     * предыдущего запроса уже обеспечена снаружи.
     */
    private suspend fun fetch(query: String) {
        _state.value = _state.value.copy(isLoading = true, error = null)

        val result = runCatching {
            // Ветвление ровно как в оригинале: непустой запрос — поиск,
            // пустой — подсказки онбординга.
            if (query.isBlank()) {
                onboardingApi.recommendationsOnboarding()
            } else {
                when (val found = audioApi.searchArtists(query, offset = 0, count = 100)) {
                    is VkResult.Success -> VkResult.Success(found.data.items)
                    is VkResult.Error -> found
                }
            }
        }.getOrElse { failure ->
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            VkResult.Error(0, failure.message.orEmpty())
        }

        // Ответ мог прийти, когда запрос уже сменился — тогда он не нужен.
        if (query != _state.value.query) return

        _state.value = when (result) {
            is VkResult.Success -> _state.value.copy(
                isLoading = false,
                artists = result.data.toOnboardingArtists(),
                error = null,
            )

            is VkResult.Error -> _state.value.copy(
                isLoading = false,
                error = result.message.ifBlank {
                    if (query.isBlank()) {
                        "ВКонтакте не вернул исполнителей для настройки"
                    } else {
                        "Поиск исполнителей не удался"
                    }
                },
            )
        }
    }

    private companion object {
        /** 300 мс — дебаунс поиска из оригинала (`C3523e.java:117`). */
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}

/** Артисты без id отбрасываются: их нельзя отправить в `artist_ids`. */
private fun List<VkArtistDto>.toOnboardingArtists(): List<OnboardingArtist> =
    mapNotNull { dto ->
        val id = dto.id.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val name = dto.name.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        OnboardingArtist(
            id = id,
            name = name,
            coverUrl = dto.coverUrl(),
            genres = dto.genres.orEmpty().mapNotNull { genre ->
                genre.name.takeIf { it.isNotBlank() }
            },
        )
    }.distinctBy { it.id }

/**
 * Два метода онбординга, которых в типизированном виде в проекте нет.
 *
 * Почему отдельный класс, а не правки `VkMethodsRegistry`: там
 * `recommendationsOnboarding()` заведён как `execute<Any>` (то есть в рабочем
 * виде его всё равно писать заново), а `finishRecomsOnboarding` отсутствует
 * вовсе. Отдельный файл к тому же не конфликтует с параллельными правками
 * реестра — тот же приём уже применён в `VkYearStatsApi`.
 */
private class VkOnboardingApi(private val client: VkApiClient) {

    /**
     * `audio.recommendationsOnboarding` — параметров нет вовсе
     * (`C14197e.java:104-109`: ни `.ad`, ни `.vip`, ни `.metrica`).
     */
    suspend fun recommendationsOnboarding(): VkResult<List<VkArtistDto>> {
        val method = VkMethod("audio.recommendationsOnboarding", OnboardingArtistsParser)
        return client.execute(method)
    }

    /**
     * `audio.finishRecomsOnboarding` (`C13029e`, id=4). Единственный параметр —
     * `artist_ids`, склейка id через запятую (`C13029e.java:212-222`).
     *
     * Тело ответа в оригинале — `List<MainArtist>`, но оно нигде не читается:
     * важен только факт успеха, поэтому и здесь оно игнорируется.
     */
    suspend fun finishOnboarding(artistIds: List<String>): VkResult<Unit> {
        val method = VkMethod("audio.finishRecomsOnboarding", AckParser).apply {
            param("artist_ids", artistIds.joinToString(","))
        }
        return client.execute(method)
    }
}

/**
 * Парсер списка исполнителей, устойчивый к обеим формам ответа.
 *
 * Источники реверса расходятся (спека §11, «Уверенность: частично»):
 * `P1:241` обещает один объект артиста, а место вызова
 * (`C14197e.java:115-124`) кастует ответ к `RootItemsResponseDto` и берёт
 * `.items`. Пока форма не проверена на живом API, разбираем обе — иначе рабочий
 * ответ VK мог бы превратиться в пустой экран.
 */
private object OnboardingArtistsParser : VkResponseParser<List<VkArtistDto>> {
    private val artistAdapter = VkJson.moshi.adapter(VkArtistDto::class.java)
    private val itemsAdapter = VkJson.moshi.adapter<VkRootItems<VkArtistDto>>(
        Types.newParameterizedType(VkRootItems::class.java, VkArtistDto::class.java),
    )
    private val errorAdapter = VkJson.moshi.adapter(VKError::class.java)

    override suspend fun parse(raw: RawHttpResponse): VkParsedResponse<List<VkArtistDto>> {
        val body = raw.bodyText()
        val root = JSONObject(body)

        // Ошибку конверта отдаём наверх как есть: её обработка (капча, refresh
        // токена) живёт в VkApiClient и здесь дублироваться не должна.
        root.optJSONObject("error")?.let { error ->
            return VkParsedResponse(null, errorAdapter.fromJson(error.toString()))
        }

        val items = when {
            // Форма из места вызова: {count, items:[…]}.
            root.optJSONObject("response")?.has("items") == true ->
                itemsAdapter.fromJson(root.getJSONObject("response").toString())?.items

            // Форма из P1: один объект артиста в response.
            root.optJSONObject("response") != null ->
                artistAdapter.fromJson(root.getJSONObject("response").toString())
                    ?.let { listOf(it) }

            // Третий возможный вид — массив прямо в response.
            root.optJSONArray("response") != null -> {
                val array = root.getJSONArray("response")
                (0 until array.length()).mapNotNull { index ->
                    artistAdapter.fromJson(array.getJSONObject(index).toString())
                }
            }

            else -> null
        }

        // Пустой список, а не null: «VK ответил, но исполнителей нет» — это
        // честная пустота на экране, а не ошибка сети.
        return VkParsedResponse(items ?: emptyList(), null)
    }
}

/** Ответ-подтверждение: важен только факт отсутствия ошибки в конверте. */
private object AckParser : VkResponseParser<Unit> {
    private val errorAdapter = VkJson.moshi.adapter(VKError::class.java)

    override suspend fun parse(raw: RawHttpResponse): VkParsedResponse<Unit> {
        val root = JSONObject(raw.bodyText())
        root.optJSONObject("error")?.let { error ->
            return VkParsedResponse(null, errorAdapter.fromJson(error.toString()))
        }
        return VkParsedResponse(Unit, null)
    }
}
