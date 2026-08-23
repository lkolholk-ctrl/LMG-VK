package com.lmg.vk.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lmg.vk.engine.Track
import com.lmg.vk.engine.VkAudioIdentity
import com.lmg.vk.engine.backend.MusicAuth
import com.lmg.vk.engine.backend.MusicBackend
import com.lmg.vk.engine.backend.backendUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class VkHistoryUiState(
    val accountId: Long? = null,
    val tracks: List<Track> = emptyList(),
    val blockId: String? = null,
    val ref: String? = null,
    val nextFrom: String? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val removingIds: Set<String> = emptySet(),
    val error: String? = null,
)

class VkHistoryViewModel : ViewModel() {
    private val _state = MutableStateFlow(VkHistoryUiState())
    val state: StateFlow<VkHistoryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            MusicAuth.profileId.collectLatest { accountId ->
                _state.value = VkHistoryUiState(accountId = accountId, isLoading = accountId != null)
                if (accountId != null) loadInitial(accountId)
            }
        }
    }

    fun refresh() {
        val accountId = MusicAuth.profileId.value ?: return
        viewModelScope.launch {
            _state.value = VkHistoryUiState(accountId = accountId, isLoading = true)
            loadInitial(accountId)
        }
    }

    fun loadMore() {
        val snapshot = _state.value
        val accountId = snapshot.accountId ?: return
        val blockId = snapshot.blockId ?: return
        val cursor = snapshot.nextFrom ?: return
        if (snapshot.isLoading || snapshot.isLoadingMore) return
        viewModelScope.launch {
            _state.value = snapshot.copy(isLoadingMore = true, error = null)
            try {
                val page = MusicBackend.getMoreVkPlaybackHistory(blockId, cursor, snapshot.ref)
                if (MusicAuth.profileId.value != accountId) return@launch
                val current = _state.value
                val existing = current.tracks.mapTo(mutableSetOf()) { VkAudioIdentity.stableFullId(it.id) }
                val added = page.tracks.filter { existing.add(VkAudioIdentity.stableFullId(it.id)) }
                _state.value = current.copy(
                    tracks = current.tracks + added,
                    blockId = page.blockId ?: current.blockId,
                    ref = page.ref ?: current.ref,
                    nextFrom = page.nextFrom?.takeUnless { it == cursor },
                    isLoadingMore = false,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (MusicAuth.profileId.value == accountId) {
                    _state.value = _state.value.copy(
                        isLoadingMore = false,
                        error = backendUserMessage(error),
                    )
                }
            }
        }
    }

    fun remove(track: Track) {
        val accountId = _state.value.accountId ?: return
        val stableId = VkAudioIdentity.stableFullId(track.id)
        if (stableId in _state.value.removingIds) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                removingIds = _state.value.removingIds + stableId,
                error = null,
            )
            try {
                MusicBackend.removeFromVkPlaybackHistory(track.id)
                if (MusicAuth.profileId.value == accountId) {
                    _state.value = _state.value.copy(
                        tracks = _state.value.tracks.filterNot {
                            VkAudioIdentity.stableFullId(it.id) == stableId
                        },
                        removingIds = _state.value.removingIds - stableId,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (MusicAuth.profileId.value == accountId) {
                    _state.value = _state.value.copy(
                        removingIds = _state.value.removingIds - stableId,
                        error = backendUserMessage(error),
                    )
                }
            }
        }
    }

    private suspend fun loadInitial(accountId: Long) {
        try {
            val page = MusicBackend.getVkPlaybackHistory()
            if (MusicAuth.profileId.value != accountId) return
            _state.value = VkHistoryUiState(
                accountId = accountId,
                tracks = page.tracks.distinctBy { VkAudioIdentity.stableFullId(it.id) },
                blockId = page.blockId,
                ref = page.ref,
                nextFrom = page.nextFrom,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (MusicAuth.profileId.value == accountId) {
                _state.value = VkHistoryUiState(
                    accountId = accountId,
                    error = backendUserMessage(error),
                )
            }
        }
    }
}
