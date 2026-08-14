package com.lmg.vk.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lmg.vk.engine.backend.MusicAuth
import com.lmg.vk.engine.ProfileImageKind
import com.lmg.vk.engine.VkProfileMediaUploader
import com.lmg.vk.network.VkApiLocator
import com.lmg.vk.network.VkResult
import com.lmg.vk.network.dto.VkAccountProfile
import com.lmg.vk.network.dto.music.AudioPlaylist
import com.lmg.vk.network.dto.music.AudioTrack
import com.lmg.vk.network.methods.VkAudioApi
import com.lmg.vk.network.methods.VkMethodsRegistry
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UserProfileUiState(
    val userId: Long = 0L,
    val isLoading: Boolean = false,
    val profile: VkAccountProfile? = null,
    val isOwnProfile: Boolean = false,
    val isFriendActionLoading: Boolean = false,
    val friendActionError: String? = null,
    val musicTracks: List<AudioTrack> = emptyList(),
    val musicPlaylists: List<AudioPlaylist> = emptyList(),
    val musicTotal: Int = 0,
    val playlistTotal: Int = 0,
    val isMusicPreviewLoading: Boolean = false,
    val musicPreviewError: String? = null,
    val isSavingProfile: Boolean = false,
    val saveProfileError: String? = null,
    val isUploadingImage: Boolean = false,
    val imageUploadError: String? = null,
    val notFound: Boolean = false,
    val error: String? = null,
)

/** Public VK user profile with `users.getFullProfile` and a `users.get` fallback. */
class UserProfileViewModel : ViewModel() {
    private val registry by lazy { VkMethodsRegistry(VkApiLocator.apiClient()) }
    private val audioApi by lazy { VkAudioApi(VkApiLocator.apiClient()) }

    private val _state = MutableStateFlow(UserProfileUiState())
    val state: StateFlow<UserProfileUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun load(userId: Long, force: Boolean = false) {
        if (userId <= 0L) {
            _state.value = UserProfileUiState(userId = userId, error = "Invalid VK user id")
            return
        }
        val current = _state.value
        if (!force && current.userId == userId && (current.profile != null || current.isLoading)) return

        loadJob?.cancel()
        _state.value = UserProfileUiState(userId = userId, isLoading = true)
        loadJob = viewModelScope.launch {
            val isOwnProfile = MusicAuth.profileId.value == userId
            val fullResult = runCatching { registry.usersGetFullProfile(userId, isOwnProfile) }
                .getOrElse { failure ->
                    if (failure is kotlinx.coroutines.CancellationException) throw failure
                    VkResult.Error(0, failure.message.orEmpty())
                }
            val result: VkResult<VkAccountProfile?> = when (fullResult) {
                is VkResult.Success -> VkResult.Success(fullResult.data)
                is VkResult.Error -> when (val fallback = runCatching {
                    registry.usersGetProfile(userId)
                }.getOrElse { VkResult.Error(0, it.message.orEmpty()) }) {
                    is VkResult.Success -> VkResult.Success(fallback.data.firstOrNull())
                    is VkResult.Error -> fallback
                }
            }

            if (_state.value.userId != userId) return@launch
            _state.value = when (result) {
                is VkResult.Success -> {
                    val profile = result.data
                    UserProfileUiState(
                        userId = userId,
                        profile = profile,
                        isOwnProfile = isOwnProfile,
                        notFound = profile == null,
                    )
                }
                is VkResult.Error -> UserProfileUiState(
                    userId = userId,
                    error = result.message.ifBlank { "VK error ${result.code}" },
                )
            }
            val profile = _state.value.profile
            if (profile != null && profile.isAccessible && profile.isAudioVisible) {
                loadMusicPreview(userId)
            }
        }
    }

    private suspend fun loadMusicPreview(userId: Long) = coroutineScope {
        val current = _state.value
        if (current.userId != userId) return@coroutineScope
        _state.value = current.copy(isMusicPreviewLoading = true, musicPreviewError = null)
        val tracksTask = async {
            runCatching { audioApi.getAudiosPage(userId, 0, MUSIC_PREVIEW_TRACKS) }
                .getOrElse { VkResult.Error(0, it.message.orEmpty()) }
        }
        val playlistsTask = async {
            runCatching { audioApi.getPlaylistsPage(userId, 0, count = MUSIC_PREVIEW_PLAYLISTS) }
                .getOrElse { VkResult.Error(0, it.message.orEmpty()) }
        }
        val tracks = tracksTask.await()
        val playlists = playlistsTask.await()
        val latest = _state.value
        if (latest.userId != userId) return@coroutineScope
        _state.value = latest.copy(
            musicTracks = (tracks as? VkResult.Success)?.data?.items.orEmpty(),
            musicPlaylists = (playlists as? VkResult.Success)?.data?.items.orEmpty(),
            musicTotal = (tracks as? VkResult.Success)?.data?.count ?: 0,
            playlistTotal = (playlists as? VkResult.Success)?.data?.count ?: 0,
            isMusicPreviewLoading = false,
            musicPreviewError = listOfNotNull(
                (tracks as? VkResult.Error)?.message,
                (playlists as? VkResult.Error)?.message,
            ).firstOrNull(String::isNotBlank),
        )
    }

    fun changeFriendship() {
        val current = _state.value
        val profile = current.profile ?: return
        if (current.isOwnProfile || current.isFriendActionLoading) return

        _state.value = current.copy(isFriendActionLoading = true, friendActionError = null)
        viewModelScope.launch {
            val remove = profile.friendStatus == 1 || profile.friendStatus == 3 || profile.isFriend == 1
            val result: VkResult<Int> = runCatching {
                if (remove) {
                    when (val deleted = registry.friendsDelete(profile.id)) {
                        is VkResult.Success -> if (deleted.data.success == 1) {
                            VkResult.Success(0)
                        } else {
                            VkResult.Error(0, "VK did not confirm the friend removal")
                        }
                        is VkResult.Error -> deleted
                    }
                } else {
                    when (val added = registry.friendsAdd(profile.id)) {
                        is VkResult.Success -> if (added.data in setOf(1, 2, 4)) {
                            added
                        } else {
                            VkResult.Error(0, "Unknown friends.add result: ${added.data}")
                        }
                        is VkResult.Error -> added
                    }
                }
            }.getOrElse { VkResult.Error(0, it.message.orEmpty()) }
            val latest = _state.value
            if (latest.userId != profile.id) return@launch
            _state.value = when (result) {
                is VkResult.Success -> {
                    val nextStatus = if (remove) {
                        0
                    } else {
                        if (result.data == 2) 3 else 1
                    }
                    latest.copy(
                        profile = latest.profile?.copy(
                            friendStatus = nextStatus,
                            isFriend = if (nextStatus == 3) 1 else 0,
                        ),
                        isFriendActionLoading = false,
                    )
                }
                is VkResult.Error -> latest.copy(
                    isFriendActionLoading = false,
                    friendActionError = result.message.ifBlank { "VK error ${result.code}" },
                )
            }
        }
    }

    fun saveOwnProfile(status: String, about: String) {
        val current = _state.value
        val profile = current.profile ?: return
        if (!current.isOwnProfile || current.isSavingProfile) return
        _state.value = current.copy(isSavingProfile = true, saveProfileError = null)
        viewModelScope.launch {
            try {
                val statusResult = if (status != profile.status) registry.statusSet(status) else VkResult.Success(Unit)
                val aboutResult = if (about != profile.about.orEmpty()) {
                    registry.accountSaveProfileAbout(about)
                } else {
                    VkResult.Success(null)
                }
                val error = (statusResult as? VkResult.Error)
                    ?: (aboutResult as? VkResult.Error)
                val latest = _state.value
                if (latest.userId != profile.id) return@launch
                _state.value = if (error == null) {
                    latest.copy(
                        profile = latest.profile?.copy(status = status, about = about),
                        isSavingProfile = false,
                    )
                } else {
                    latest.copy(
                        isSavingProfile = false,
                        saveProfileError = error.message.ifBlank { "VK error ${error.code}" },
                    )
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                val latest = _state.value
                _state.value = latest.copy(
                    isSavingProfile = false,
                    saveProfileError = failure.message ?: "Couldn't save profile",
                )
            }
        }
    }

    fun uploadOwnProfileImage(context: Context, uri: Uri, kind: ProfileImageKind) {
        val current = _state.value
        if (!current.isOwnProfile || current.isUploadingImage) return
        _state.value = current.copy(isUploadingImage = true, imageUploadError = null)
        val appContext = context.applicationContext
        viewModelScope.launch {
            val result = VkProfileMediaUploader.upload(appContext, uri, kind)
            val latest = _state.value
            if (latest.userId != current.userId) return@launch
            if (result.isSuccess) {
                _state.value = latest.copy(isUploadingImage = false)
                load(current.userId, force = true)
            } else {
                _state.value = latest.copy(
                    isUploadingImage = false,
                    imageUploadError = result.exceptionOrNull()?.message ?: "Couldn't upload image",
                )
            }
        }
    }

    private companion object {
        const val MUSIC_PREVIEW_TRACKS = 3
        const val MUSIC_PREVIEW_PLAYLISTS = 2
    }
}
