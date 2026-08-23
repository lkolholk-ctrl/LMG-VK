package com.lmg.vk.ui.screens

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf

internal object NewDismissedBanners {

    private const val PREFS = "new_dismissed_banners"
    private const val LEGACY_KEY_IDS = "ids"
    private const val MAX_ENTRIES = 60
    private const val SEPARATOR = "\n"

    private val dismissed: SnapshotStateMap<String, Boolean> = mutableStateMapOf()
    private var appContext: Context? = null
    private val loaded = mutableStateOf(false)
    private var activeAccountId = Long.MIN_VALUE

    fun init(context: Context, userId: Long) {
        appContext = context.applicationContext
        val resolvedUserId = userId.coerceAtLeast(0L)
        if (loaded.value && activeAccountId == resolvedUserId) return
        activeAccountId = resolvedUserId
        dismissed.clear()
        val p = prefs()
        val key = accountKey(resolvedUserId)
        val stored = p?.getString(key, null) ?: p?.getString(LEGACY_KEY_IDS, null)?.also {
            if (resolvedUserId != 0L) {
                p?.edit()?.putString(key, it)?.remove(LEGACY_KEY_IDS)?.apply()
            }
        }.orEmpty()
        if (stored.isNotEmpty()) {
            stored.split(SEPARATOR).filter { it.isNotBlank() }.forEach { dismissed[it] = true }
        }
        loaded.value = true
    }

    fun isDismissed(blockId: String): Boolean = dismissed.containsKey(blockId)

    fun dismiss(blockId: String) {
        if (blockId.isBlank()) return
        dismissed[blockId] = true
        val ids = dismissed.keys.toList().let { if (it.size > MAX_ENTRIES) it.takeLast(MAX_ENTRIES) else it }
        prefs()?.edit()?.putString(accountKey(activeAccountId), ids.joinToString(SEPARATOR))?.apply()
    }

    private fun accountKey(userId: Long): String = "ids_account_$userId"

    private fun prefs() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
