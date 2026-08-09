package com.lmg.vk.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lmg.vk.network.VkApiLocator
import com.lmg.vk.network.VkItems
import com.lmg.vk.network.VkResult
import com.lmg.vk.network.dto.VkErrorCodes
import com.lmg.vk.network.dto.VkFriend
import com.lmg.vk.network.dto.VkGroup
import com.lmg.vk.network.dto.music.AudioPlaylist
import com.lmg.vk.network.dto.music.AudioTrack
import com.lmg.vk.network.dto.music.mergeAudioTracksById
import com.lmg.vk.network.methods.VkAudioApi
import com.lmg.vk.network.methods.VkMethodsRegistry
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Состояние экрана сообщества.
 *
 * Ошибки СЕКЦИОННЫЕ, а не одна на экран: закрытая музыка сообщества — обычное
 * дело, и она не должна прятать шапку, описание и участников, которые VK отдал
 * нормально. Единственная фатальная ошибка — не загрузилось само сообщество:
 * без него показывать нечего.
 */
data class GroupUiState(
    /** owner_id (ОТРИЦАТЕЛЬНЫЙ, как ждут музыкальные методы). */
    val ownerId: Long = 0L,
    val isLoading: Boolean = false,
    val group: VkGroup? = null,
    /** Фатально: сообщество получить не удалось. */
    val error: String? = null,
    /** VK ответил успехом, но сообщества с таким id нет. */
    val notFound: Boolean = false,

    val tracks: List<AudioTrack> = emptyList(),
    val tracksTotal: Int? = null,
    val isLoadingMoreTracks: Boolean = false,
    /** Музыка закрыта настройками сообщества — это не ошибка, а состояние. */
    val audioClosed: Boolean = false,
    val audioError: String? = null,

    val playlists: List<AudioPlaylist> = emptyList(),
    val playlistsError: String? = null,

    val members: List<VkFriend> = emptyList(),
    /** Реальное число участников из `groups.getMembers`; шапка предпочитает его. */
    val membersTotal: Int? = null,

    /** Идёт groups.join/groups.leave — кнопку надо заблокировать. */
    val isMembershipChanging: Boolean = false,
    /** Почему не удалось подписаться/отписаться; показывается у самой кнопки. */
    val membershipError: String? = null,
) {
    /** Есть ли ещё треки: считаем только когда VK сообщил реальный total. */
    val hasMoreTracks: Boolean
        get() = tracksTotal?.let { tracks.size < it } ?: false

    /**
     * Число участников. `groups.getMembers.count` точнее `members_count`: второе
     * VK иногда не присылает вовсе, а показывать «—» при живом списке участников
     * незачем.
     */
    val membersCount: Int?
        get() = membersTotal ?: group?.membersCount

    /** Подписан ли текущий пользователь; `null` — VK не сказал. */
    val isMember: Boolean?
        get() = group?.isMemberOrNull

    /** Загрузка кончилась, музыки нет, и это не запрет доступа. */
    val audioIsEmpty: Boolean
        get() = !isLoading && !audioClosed && audioError == null && tracks.isEmpty()
}

/**
 * ViewModel экрана сообщества.
 *
 * Почему своя, а не `VkProfileRepository`: тот держит РОВНО ОДНО состояние
 * «аудио владельца» на всё приложение (`_ownerAudio`), и открыть сообщество,
 * не затирая уже открытый подэкран профиля, через него нельзя. Экранов
 * сообществ в бэкстеке может быть несколько (из сообщества — в сообщество), а у
 * ViewModel состояние живёт per-destination, как и требуется.
 *
 * `VkProfileRepository.ownerAudio` при этом сознательно НЕ трогается — иначе
 * возврат в профиль показал бы чужое сообщество.
 */
class GroupViewModel : ViewModel() {

    // lazy: сетевое ядро поднимает LmgApplication, а ViewModel может быть
    // создана раньше — падать в конструкторе из-за порядка инициализации нельзя.
    private val registry by lazy { VkMethodsRegistry(VkApiLocator.apiClient()) }
    private val audio by lazy { VkAudioApi(VkApiLocator.apiClient()) }

    private val _state = MutableStateFlow(GroupUiState())
    val state: StateFlow<GroupUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    /**
     * [ownerId] — отрицательный id сообщества (как в ссылках и в
     * `VkGroup.audioOwnerId`). [force] нужен кнопке «Повторить»: обычный вход на
     * экран не должен перезапрашивать VK, если данные уже есть.
     */
    fun load(ownerId: Long, force: Boolean = false) {
        if (ownerId == 0L) {
            _state.value = GroupUiState(error = "Не передан id сообщества")
            return
        }
        val current = _state.value
        // Возврат по бэкстеку на тот же экран не должен дёргать сеть заново.
        if (!force && current.ownerId == ownerId && (current.group != null || current.isLoading)) return

        loadJob?.cancel()
        // Полный сброс: остатки прежнего сообщества на экране — это ложные данные.
        _state.value = GroupUiState(ownerId = ownerId, isLoading = true)
        loadJob = viewModelScope.launch {
            runCatching { fetch(ownerId) }.onFailure { failure ->
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = failure.message?.takeIf(String::isNotBlank)
                        ?: "Не удалось загрузить сообщество",
                )
            }
        }
    }

    /**
     * Всё грузится параллельно: четыре независимых запроса последовательно дали
     * бы четырёхкратное ожидание на медленной сети. Частичный отказ не рушит
     * экран — каждая секция несёт свою ошибку.
     */
    private suspend fun fetch(ownerId: Long) {
        val loaded = coroutineScope {
            val groupTask = async { registry.groupsGetById(ownerId) }
            val tracksTask = async { audio.getAudiosPage(ownerId, offset = 0, count = AUDIO_PAGE_SIZE) }
            val playlistsTask = async { audio.getPlaylistsPage(ownerId, offset = 0, count = PLAYLIST_COUNT) }
            val membersTask = async { registry.groupsGetMembers(ownerId, offset = 0, count = MEMBERS_PREVIEW) }
            Loaded(
                group = groupTask.await(),
                tracks = tracksTask.await(),
                playlists = playlistsTask.await(),
                members = membersTask.await(),
            )
        }

        // Экран мог быть переиспользован под другое сообщество, пока шли запросы.
        if (_state.value.ownerId != ownerId) return

        val group = (loaded.group as? VkResult.Success)?.data?.group
        val groupError = (loaded.group as? VkResult.Error)?.let(::messageOf)

        val tracksFailure = loaded.tracks as? VkResult.Error
        // `code in CLOSED_CONTENT` пишем от НЕнулевого кода: у Set<Int> оператор
        // `in` с Int? не собирается, а тернарник тут читается хуже явной проверки.
        val tracksClosed = tracksFailure != null && tracksFailure.code in VkErrorCodes.CLOSED_CONTENT

        _state.value = GroupUiState(
            ownerId = ownerId,
            isLoading = false,
            group = group,
            error = groupError,
            // Успех с пустым ответом = такого сообщества у VK нет. Отличать от
            // ошибки обязательно: пользователю нужно разное сообщение.
            notFound = groupError == null && group == null,

            tracks = (loaded.tracks as? VkResult.Success)?.data?.items.orEmpty(),
            tracksTotal = (loaded.tracks as? VkResult.Success)?.let {
                it.data.count ?: it.data.items.size
            },
            audioClosed = tracksClosed,
            audioError = tracksFailure?.takeIf { !tracksClosed }?.let(::messageOf),

            playlists = (loaded.playlists as? VkResult.Success)?.data?.items.orEmpty(),
            // Закрытая музыка уже объяснена в блоке аудио — второй раз то же
            // самое под плейлистами было бы шумом.
            playlistsError = (loaded.playlists as? VkResult.Error)
                ?.takeIf { !tracksClosed && it.code !in VkErrorCodes.CLOSED_CONTENT }
                ?.let(::messageOf),

            members = (loaded.members as? VkResult.Success)?.data?.items.orEmpty(),
            membersTotal = (loaded.members as? VkResult.Success)?.data?.count,
        )
    }

    private class Loaded(
        val group: VkResult<VkMethodsRegistry.GroupsByIdResponse>,
        val tracks: VkResult<VkItems<AudioTrack>>,
        val playlists: VkResult<VkItems<AudioPlaylist>>,
        val members: VkResult<VkItems<VkFriend>>,
    )

    /** Догрузка следующей страницы аудиозаписей сообщества. */
    fun loadMoreTracks() {
        val current = _state.value
        if (!current.hasMoreTracks || current.isLoadingMoreTracks || current.isLoading) return
        val ownerId = current.ownerId

        _state.value = current.copy(isLoadingMoreTracks = true)
        viewModelScope.launch {
            val result = runCatching {
                audio.getAudiosPage(ownerId, offset = current.tracks.size, count = AUDIO_PAGE_SIZE)
            }.getOrElse { failure ->
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                VkResult.Error(0, failure.message.orEmpty())
            }

            val base = _state.value.takeIf { it.ownerId == ownerId } ?: return@launch
            _state.value = when (result) {
                is VkResult.Success -> base.copy(
                    isLoadingMoreTracks = false,
                    // У VK на страницах бывают повторы. При их склейке сохраняем
                    // расширенный thumb, чтобы очередь не теряла цветную обложку.
                    tracks = (base.tracks + result.data.items).mergeAudioTracksById(),
                    tracksTotal = result.data.count ?: base.tracksTotal,
                    audioError = null,
                )
                is VkResult.Error -> base.copy(
                    isLoadingMoreTracks = false,
                    audioError = messageOf(result),
                )
            }
        }
    }

    /**
     * Подписка/отписка. Состояние кнопки меняется ТОЛЬКО после успешного ответа
     * VK: оптимистичное переключение на отказе (закрытое сообщество, бан, лимит
     * подписок) показало бы «Subscribed» при фактической неудаче.
     */
    fun toggleMembership() {
        val current = _state.value
        val group = current.group ?: return
        val wasMember = current.isMember ?: return
        if (current.isMembershipChanging) return

        _state.value = current.copy(isMembershipChanging = true, membershipError = null)
        viewModelScope.launch {
            val result = runCatching {
                if (wasMember) registry.groupsLeave(group.id) else registry.groupsJoin(group.id)
            }.getOrElse { failure ->
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                VkResult.Error(0, failure.message.orEmpty())
            }

            val base = _state.value.takeIf { it.ownerId == current.ownerId } ?: return@launch
            _state.value = when (result) {
                is VkResult.Success -> base.copy(
                    isMembershipChanging = false,
                    membershipError = null,
                    // is_member — часть DTO сообщества, поэтому правим его копию:
                    // иначе кнопка вернулась бы в прежний вид на рекомпозиции.
                    group = base.group?.copy(isMember = if (wasMember) 0 else 1),
                    // Счётчик участников сдвигаем на себя же — VK его тут не
                    // пересылает, а расхождение на единицу видно сразу.
                    membersTotal = base.membersTotal?.plus(if (wasMember) -1 else 1),
                )
                is VkResult.Error -> base.copy(
                    isMembershipChanging = false,
                    membershipError = messageOf(result),
                )
            }
        }
    }

    /** Текст ошибки для пользователя. Закрытый доступ — отдельная формулировка. */
    private fun messageOf(error: VkResult.Error): String = when (error.code) {
        in VkErrorCodes.CLOSED_CONTENT -> "Закрыто настройками сообщества"
        else -> error.message.ifBlank { "VK вернул ошибку ${error.code}" }
    }

    private companion object {
        /** Столько же, сколько тянет аудио друга в VkProfileRepository. */
        const val AUDIO_PAGE_SIZE = 100
        const val PLAYLIST_COUNT = 50

        /** Участников показываем строкой аватаров — больше горстки не нужно. */
        const val MEMBERS_PREVIEW = 20
    }
}
