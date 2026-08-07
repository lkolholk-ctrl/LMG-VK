package com.lmg.vk.engine.backend

import com.lmg.vk.network.VkApiClient
import com.lmg.vk.network.VkItems
import com.lmg.vk.network.VkResult
import com.lmg.vk.network.dto.VkAccountProfile
import com.lmg.vk.network.dto.VkErrorCodes
import com.lmg.vk.network.dto.VkFriend
import com.lmg.vk.network.dto.VkGroup
import com.lmg.vk.network.dto.music.AudioPlaylist
import com.lmg.vk.network.dto.music.AudioTrack
import com.lmg.vk.network.methods.VkAudioApi
import com.lmg.vk.network.methods.VkMethodsRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Состояние экрана профиля: всё, что показывается, приходит из VK API.
 * Держится отдельно от [MusicBackend], чтобы тот не разрастался дальше.
 *
 * Ничего не кэшируется на диск: профиль, друзья, сообщества и музыка
 * перезапрашиваются при открытии экрана и по pull-to-refresh.
 */
object VkProfileRepository {

    /** Одна страница списка друзей/сообществ. */
    const val PAGE_SIZE = 40

    /** Сколько треков тянем за раз в аудио друга. */
    const val AUDIO_PAGE_SIZE = 100

    data class ProfileState(
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val profile: VkAccountProfile? = null,
        val friends: List<VkFriend> = emptyList(),
        val friendsTotal: Int? = null,
        val friendsError: String? = null,
        val groups: List<VkGroup> = emptyList(),
        val groupsTotal: Int? = null,
        val groupsError: String? = null,
        val playlists: List<AudioPlaylist> = emptyList(),
        val playlistsTotal: Int? = null,
        val audioTotal: Int? = null,
        val musicError: String? = null,
        val error: String? = null,
    ) {
        val hasMoreFriends: Boolean
            get() = friendsTotal?.let { friends.size < it } ?: false

        val hasMoreGroups: Boolean
            get() = groupsTotal?.let { groups.size < it } ?: false

        val isEmpty: Boolean
            get() = profile == null && friends.isEmpty() && groups.isEmpty()
    }

    /** Аудиозаписи конкретного друга (или сообщества) — отдельный подэкран. */
    data class OwnerAudioState(
        val ownerId: Long = 0L,
        val title: String = "",
        val subtitle: String = "",
        val avatarUrl: String = "",
        val isLoading: Boolean = false,
        val isLoadingMore: Boolean = false,
        val tracks: List<AudioTrack> = emptyList(),
        val total: Int? = null,
        val playlists: List<AudioPlaylist> = emptyList(),
        /** Владелец закрыл музыку — это не ошибка сети, показывается отдельным текстом. */
        val isClosed: Boolean = false,
        val error: String? = null,
    ) {
        val hasMore: Boolean
            get() = total?.let { tracks.size < it } ?: false
    }

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    private val _ownerAudio = MutableStateFlow<OwnerAudioState?>(null)

    /** `null` — подэкран аудио закрыт. */
    val ownerAudio: StateFlow<OwnerAudioState?> = _ownerAudio.asStateFlow()

    private var audioApi: VkAudioApi? = null
    private var methods: VkMethodsRegistry? = null

    private val refreshMutex = Mutex()
    private val pagingMutex = Mutex()
    private val audioMutex = Mutex()

    internal fun init(client: VkApiClient) {
        audioApi = VkAudioApi(client)
        methods = VkMethodsRegistry(client)
    }

    /**
     * Профиль, уже полученный [MusicAuth.fetchUserData], — чтобы карточка была
     * заполнена сразу после входа и не ждала первого [refresh].
     */
    internal fun seedProfile(profile: VkAccountProfile) {
        _state.value = _state.value.copy(profile = profile, error = null)
    }

    /** Полный сброс при выходе из аккаунта. */
    fun clear() {
        _state.value = ProfileState()
        _ownerAudio.value = null
    }

    /**
     * Загружает профиль, друзей, сообщества и музыку аккаунта параллельно.
     * Частичный отказ не рушит экран: каждая секция несёт свою ошибку.
     */
    suspend fun refresh(userId: Long) {
        val registry = methods ?: return
        val audio = audioApi ?: return
        if (userId == 0L) {
            _state.value = _state.value.copy(error = "No VK account id")
            return
        }
        if (!refreshMutex.tryLock()) return
        try {
            val hadData = !_state.value.isEmpty
            _state.value = _state.value.copy(
                isLoading = !hadData,
                isRefreshing = hadData,
                error = null,
            )

            val loaded = coroutineScope {
                val profileTask = async { registry.usersGetCurrent() }
                val friendsTask = async { registry.friendsGet(offset = 0, count = PAGE_SIZE) }
                val groupsTask = async { registry.groupsGet(offset = 0, count = PAGE_SIZE) }
                val playlistsTask = async { audio.getPlaylistsPage(userId, offset = 0, count = 50) }
                // Треки не нужны целиком — берём только реальное count из ответа.
                val audioCountTask = async { audio.getAudiosPage(userId, offset = 0, count = 1) }

                Loaded(
                    profile = profileTask.await(),
                    friends = friendsTask.await(),
                    groups = groupsTask.await(),
                    playlists = playlistsTask.await(),
                    audioCount = audioCountTask.await(),
                )
            }

            val profile = (loaded.profile as? VkResult.Success)?.data?.firstOrNull()
            val profileError = (loaded.profile as? VkResult.Error)?.let(::messageOf)

            _state.value = ProfileState(
                isLoading = false,
                isRefreshing = false,
                profile = profile ?: _state.value.profile,
                friends = loaded.friends.itemsOrPrevious(_state.value.friends),
                friendsTotal = loaded.friends.countOrNull() ?: _state.value.friendsTotal,
                friendsError = loaded.friends.errorMessage(),
                groups = loaded.groups.itemsOrPrevious(_state.value.groups),
                groupsTotal = loaded.groups.countOrNull() ?: _state.value.groupsTotal,
                groupsError = loaded.groups.errorMessage(),
                playlists = loaded.playlists.itemsOrPrevious(_state.value.playlists),
                playlistsTotal = loaded.playlists.countOrNull() ?: _state.value.playlistsTotal,
                audioTotal = loaded.audioCount.countOrNull() ?: _state.value.audioTotal,
                musicError = loaded.playlists.errorMessage() ?: loaded.audioCount.errorMessage(),
                error = profileError.takeIf { profile == null },
            )
        } finally {
            refreshMutex.unlock()
        }
    }

    private class Loaded(
        val profile: VkResult<List<VkAccountProfile>>,
        val friends: VkResult<VkItems<VkFriend>>,
        val groups: VkResult<VkItems<VkGroup>>,
        val playlists: VkResult<VkItems<AudioPlaylist>>,
        val audioCount: VkResult<VkItems<AudioTrack>>,
    )

    suspend fun loadMoreFriends() {
        val registry = methods ?: return
        val current = _state.value
        if (!current.hasMoreFriends) return
        if (!pagingMutex.tryLock()) return
        try {
            when (val result = registry.friendsGet(offset = current.friends.size, count = PAGE_SIZE)) {
                is VkResult.Success -> {
                    val merged = (current.friends + result.data.items).distinctBy(VkFriend::id)
                    _state.value = _state.value.copy(
                        friends = merged,
                        friendsTotal = result.data.count ?: current.friendsTotal,
                        friendsError = null,
                    )
                }
                is VkResult.Error ->
                    _state.value = _state.value.copy(friendsError = messageOf(result))
            }
        } finally {
            pagingMutex.unlock()
        }
    }

    suspend fun loadMoreGroups() {
        val registry = methods ?: return
        val current = _state.value
        if (!current.hasMoreGroups) return
        if (!pagingMutex.tryLock()) return
        try {
            when (val result = registry.groupsGet(offset = current.groups.size, count = PAGE_SIZE)) {
                is VkResult.Success -> {
                    val merged = (current.groups + result.data.items).distinctBy(VkGroup::id)
                    _state.value = _state.value.copy(
                        groups = merged,
                        groupsTotal = result.data.count ?: current.groupsTotal,
                        groupsError = null,
                    )
                }
                is VkResult.Error ->
                    _state.value = _state.value.copy(groupsError = messageOf(result))
            }
        } finally {
            pagingMutex.unlock()
        }
    }

    // ------------------------- аудио друга / сообщества -------------------------

    suspend fun openFriendAudio(friend: VkFriend) = openOwnerAudio(
        ownerId = friend.id,
        title = friend.displayName,
        subtitle = friend.screenName.ifBlank { friend.domain },
        avatarUrl = friend.avatarUrl,
    )

    suspend fun openGroupAudio(group: VkGroup) = openOwnerAudio(
        ownerId = group.audioOwnerId,
        title = group.name,
        subtitle = group.screenName,
        avatarUrl = group.avatarUrl,
    )

    fun closeOwnerAudio() {
        _ownerAudio.value = null
    }

    /**
     * Аудио владельца по одному лишь id — вход для ссылок вида `vk.com/audios123`
     * (см. [com.lmg.vk.engine.VkLinkResolver]). Отличается от [openFriendAudio] и
     * [openGroupAudio] тем, что имени и аватара у вызывающего нет: ссылка их не
     * содержит.
     *
     * Заголовок подставляется сразу — плейсхолдером, чтобы экран открылся без
     * ожидания сети, — а имя дозапрашивается параллельно с треками и подменяется по
     * приходу. Так экран не выглядит пустым и не врёт: если VK имя не отдаст,
     * останется `id123`/`club123`, а не выдуманное название.
     */
    suspend fun openOwnerAudioById(ownerId: Long) {
        if (ownerId == 0L) return
        val isGroup = ownerId < 0
        openOwnerAudio(
            ownerId = ownerId,
            title = if (isGroup) "club${-ownerId}" else "id$ownerId",
            subtitle = "",
            avatarUrl = "",
        )
        // Имя грузим ПОСЛЕ треков: для сообществ метода в реестре нет (есть только
        // groups.get по своим), поэтому уточнить можно лишь пользователя.
        if (isGroup) return
        val registry = methods ?: return
        val profile = (runCatching { registry.usersGetProfile(ownerId) }.getOrNull()
            as? VkResult.Success)?.data?.firstOrNull() ?: return
        // Экран могли закрыть или сменить владельца, пока шёл запрос.
        val current = _ownerAudio.value?.takeIf { it.ownerId == ownerId } ?: return
        _ownerAudio.value = current.copy(
            title = profile.displayName.ifBlank { current.title },
            subtitle = profile.addressSlug,
            avatarUrl = profile.bestPhotoUrl,
        )
    }

    private suspend fun openOwnerAudio(
        ownerId: Long,
        title: String,
        subtitle: String,
        avatarUrl: String,
    ) {
        val audio = audioApi ?: return
        _ownerAudio.value = OwnerAudioState(
            ownerId = ownerId,
            title = title,
            subtitle = subtitle,
            avatarUrl = avatarUrl,
            isLoading = true,
        )
        audioMutex.withLock {
            val loaded = coroutineScope {
                val tracksTask = async { audio.getAudiosPage(ownerId, offset = 0, count = AUDIO_PAGE_SIZE) }
                val playlistsTask = async { audio.getPlaylistsPage(ownerId, offset = 0, count = 50) }
                tracksTask.await() to playlistsTask.await()
            }
            val (tracks, playlists) = loaded
            // Экран мог быть закрыт, пока шёл запрос.
            val base = _ownerAudio.value?.takeIf { it.ownerId == ownerId } ?: return@withLock
            _ownerAudio.value = when (tracks) {
                is VkResult.Success -> base.copy(
                    isLoading = false,
                    tracks = tracks.data.items,
                    total = tracks.data.count ?: tracks.data.items.size,
                    playlists = playlists.itemsOrPrevious(emptyList()),
                    isClosed = false,
                    error = null,
                )
                is VkResult.Error -> base.copy(
                    isLoading = false,
                    isClosed = tracks.code in VkErrorCodes.CLOSED_CONTENT,
                    error = if (tracks.code in VkErrorCodes.CLOSED_CONTENT) null else messageOf(tracks),
                )
            }
        }
    }

    suspend fun loadMoreOwnerAudio() {
        val audio = audioApi ?: return
        val current = _ownerAudio.value ?: return
        if (!current.hasMore || current.isLoadingMore || current.isLoading) return
        _ownerAudio.value = current.copy(isLoadingMore = true)
        audioMutex.withLock {
            val result = audio.getAudiosPage(
                ownerId = current.ownerId,
                offset = current.tracks.size,
                count = AUDIO_PAGE_SIZE,
            )
            val base = _ownerAudio.value?.takeIf { it.ownerId == current.ownerId } ?: return@withLock
            _ownerAudio.value = when (result) {
                is VkResult.Success -> base.copy(
                    isLoadingMore = false,
                    tracks = (base.tracks + result.data.items).distinctBy(AudioTrack::fullId),
                    total = result.data.count ?: base.total,
                    error = null,
                )
                is VkResult.Error -> base.copy(
                    isLoadingMore = false,
                    error = messageOf(result),
                )
            }
        }
    }

    // ------------------------------- helpers -------------------------------

    private fun <T> VkResult<VkItems<T>>.itemsOrPrevious(previous: List<T>): List<T> =
        (this as? VkResult.Success)?.data?.items ?: previous

    private fun <T> VkResult<VkItems<T>>.countOrNull(): Int? =
        (this as? VkResult.Success)?.data?.count

    private fun <T> VkResult<VkItems<T>>.errorMessage(): String? =
        (this as? VkResult.Error)?.let(::messageOf)

    // Строки пользовательские, поэтому на английском — как весь остальной UI.
    private fun messageOf(error: VkResult.Error): String = when (error.code) {
        in VkErrorCodes.CLOSED_CONTENT -> "Closed by privacy settings"
        else -> error.message.ifBlank { "VK error ${error.code}" }
    }
}
