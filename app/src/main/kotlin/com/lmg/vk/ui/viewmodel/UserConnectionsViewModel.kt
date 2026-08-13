package com.lmg.vk.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lmg.vk.network.VkApiLocator
import com.lmg.vk.network.VkResult
import com.lmg.vk.network.dto.VkFriend
import com.lmg.vk.network.dto.VkGroup
import com.lmg.vk.network.methods.VkMethodsRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class UserConnectionsKind(val routeValue: String, val title: String) {
    MUTUAL("mutual", "Mutual friends"),
    FOLLOWERS("followers", "Followers"),
    SUBSCRIPTIONS("subscriptions", "Subscriptions");

    companion object {
        fun fromRoute(value: String): UserConnectionsKind? = entries.firstOrNull { it.routeValue == value }
    }
}

sealed interface UserConnectionEntry {
    val stableId: String

    data class User(val value: VkFriend) : UserConnectionEntry {
        override val stableId: String = "user:${value.id}"
    }

    data class Group(val value: VkGroup) : UserConnectionEntry {
        override val stableId: String = "group:${value.id}"
    }
}

data class UserConnectionsUiState(
    val userId: Long = 0L,
    val kind: UserConnectionsKind = UserConnectionsKind.FOLLOWERS,
    val items: List<UserConnectionEntry> = emptyList(),
    val totalCount: Int = 0,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val error: String? = null,
)

class UserConnectionsViewModel : ViewModel() {
    private val registry by lazy { VkMethodsRegistry(VkApiLocator.apiClient()) }
    private val _state = MutableStateFlow(UserConnectionsUiState())
    val state: StateFlow<UserConnectionsUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun load(userId: Long, kind: UserConnectionsKind, force: Boolean = false) {
        val current = _state.value
        if (!force && current.userId == userId && current.kind == kind &&
            (current.items.isNotEmpty() || current.isLoading)
        ) return
        job?.cancel()
        _state.value = UserConnectionsUiState(userId = userId, kind = kind, isLoading = true)
        job = viewModelScope.launch { loadPage(reset = true) }
    }

    fun loadMore() {
        val current = _state.value
        if (!current.hasMore || current.isLoading || current.isLoadingMore) return
        job?.cancel()
        _state.value = current.copy(isLoadingMore = true, error = null)
        job = viewModelScope.launch { loadPage(reset = false) }
    }

    private suspend fun loadPage(reset: Boolean) {
        val start = _state.value
        val offset = if (reset) 0 else start.items.size
        try {
            when (start.kind) {
                UserConnectionsKind.MUTUAL -> loadMutual(start)
                UserConnectionsKind.FOLLOWERS -> {
                    when (val result = registry.usersGetFollowers(start.userId, offset, PAGE_SIZE)) {
                        is VkResult.Success -> publish(
                            start = start,
                            reset = reset,
                            newItems = result.data.items.map { UserConnectionEntry.User(it) },
                            total = result.data.count,
                        )
                        is VkResult.Error -> publishError(start, result)
                    }
                }
                UserConnectionsKind.SUBSCRIPTIONS -> {
                    when (val result = registry.usersGetSubscriptions(start.userId, offset, PAGE_SIZE)) {
                        is VkResult.Success -> publish(
                            start = start,
                            reset = reset,
                            newItems = buildList {
                                addAll(result.data.users.map { UserConnectionEntry.User(it) })
                                addAll(result.data.groups.map { UserConnectionEntry.Group(it) })
                            },
                            total = result.data.count,
                        )
                        is VkResult.Error -> publishError(start, result)
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            val latest = _state.value
            if (latest.userId == start.userId && latest.kind == start.kind) {
                _state.value = latest.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    error = failure.message ?: "Couldn't load this list",
                )
            }
        }
    }

    private suspend fun loadMutual(start: UserConnectionsUiState) {
        when (val ids = registry.friendsGetMutual(start.userId, MUTUAL_LIMIT)) {
            is VkResult.Error -> publishError(start, ids)
            is VkResult.Success -> when (val users = registry.usersGetConnections(ids.data)) {
                is VkResult.Error -> publishError(start, users)
                is VkResult.Success -> publish(
                    start = start,
                    reset = true,
                    newItems = users.data.map { UserConnectionEntry.User(it) },
                    total = ids.data.size,
                    allowMore = false,
                )
            }
        }
    }

    private fun publish(
        start: UserConnectionsUiState,
        reset: Boolean,
        newItems: List<UserConnectionEntry>,
        total: Int,
        allowMore: Boolean = true,
    ) {
        val latest = _state.value
        if (latest.userId != start.userId || latest.kind != start.kind) return
        val merged = if (reset) newItems else (latest.items + newItems).distinctBy { it.stableId }
        _state.value = latest.copy(
            items = merged,
            totalCount = total,
            isLoading = false,
            isLoadingMore = false,
            hasMore = allowMore && newItems.isNotEmpty() && merged.size < total,
            error = null,
        )
    }

    private fun publishError(start: UserConnectionsUiState, error: VkResult.Error) {
        val latest = _state.value
        if (latest.userId != start.userId || latest.kind != start.kind) return
        _state.value = latest.copy(
            isLoading = false,
            isLoadingMore = false,
            error = error.message.ifBlank { "VK error ${error.code}" },
        )
    }

    private companion object {
        const val PAGE_SIZE = 40
        const val MUTUAL_LIMIT = 100
    }
}
