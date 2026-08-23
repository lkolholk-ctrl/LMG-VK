package com.lmg.vk.engine

import android.content.Context
import com.lmg.vk.data.local.db.LibraryRepository
import com.lmg.vk.engine.backend.MusicAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object AccountSyncManager {

    data class SyncState(
        val accountId: Long = 0L,
        val isSyncing: Boolean = false,
        val lastSuccessAt: Long? = null,
        val error: String? = null,
    )

    private val mutex = Mutex()
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    @Synchronized
    fun activateAccount(userId: Long) {
        val accountId = userId.coerceAtLeast(0L)
        if (_state.value.accountId == accountId) return
        _state.value = SyncState(accountId = accountId)
    }

    suspend fun syncAll(cleanupLibraryDuplicates: Boolean = false): Result<Unit> = mutex.withLock {
        if (MusicAuth.isAuthorizationInProgress) return@withLock Result.success(Unit)
        val context = appContext
            ?: return@withLock Result.failure(IllegalStateException("Account sync is not initialized"))
        val accountId = MusicAuth.profileId.value
            ?: return@withLock Result.success(Unit)
        _state.value = SyncState(accountId = accountId, isSyncing = true)
        try {
            val failures = mutableListOf<Throwable>()
            PlaylistSyncManager.sync().exceptionOrNull()?.let(failures::add)
            if (MusicAuth.isAuthorizationInProgress) {
                _state.value = SyncState(accountId = accountId)
                return@withLock Result.success(Unit)
            }
            if (MusicAuth.profileId.value != accountId) {
                throw CancellationException("VK account changed during synchronization")
            }
            LibraryRepository.getInstance(context).syncWithCloud(cleanupLibraryDuplicates)
                .exceptionOrNull()
                ?.let(failures::add)
            if (MusicAuth.profileId.value != accountId) {
                throw CancellationException("VK account changed during synchronization")
            }
            if (failures.isEmpty()) {
                _state.value = SyncState(
                    accountId = accountId,
                    lastSuccessAt = System.currentTimeMillis(),
                )
                Result.success(Unit)
            } else {
                val message = failures.joinToString("; ") {
                    it.message ?: it.javaClass.simpleName
                }
                _state.value = SyncState(accountId = accountId, error = message)
                Result.failure(IllegalStateException(message, failures.first()))
            }
        } catch (cancelled: CancellationException) {
            _state.value = SyncState(accountId = MusicAuth.profileId.value ?: 0L)
            throw cancelled
        } catch (error: Throwable) {
            _state.value = SyncState(
                accountId = accountId,
                error = error.message ?: "Account sync failed",
            )
            Result.failure(error)
        }
    }
}
