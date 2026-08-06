package com.lmg.vk.ui.screens

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf

/**
 * Закрытые пользователем баннеры каталога (`close_catalog_banner` у VK X).
 *
 * Хранится отдельно от [com.lmg.vk.engine.AppSettings] намеренно: это не
 * настройка, а состояние выдачи, живущее по id блока. Смешивать их в одном
 * файле prefs — значит однажды выгрести чужие ключи при сбросе настроек.
 *
 * Закрытие обязано переживать перезапуск: баннер, возвращающийся при каждом
 * старте, раздражает сильнее, чем помогает. При этом список не растёт
 * бесконечно — VK меняет id блоков, поэтому хранится не более [MAX_ENTRIES]
 * последних, старые вытесняются.
 */
internal object NewDismissedBanners {

    private const val PREFS = "new_dismissed_banners"
    private const val KEY_IDS = "ids"
    private const val MAX_ENTRIES = 60
    private const val SEPARATOR = "\n"

    /**
     * Compose-состояние, чтобы экран перерисовался сразу после нажатия крестика,
     * а не при следующем заходе.
     */
    private val dismissed: SnapshotStateMap<String, Boolean> = mutableStateMapOf()
    private var appContext: Context? = null
    private val loaded = mutableStateOf(false)

    fun init(context: Context) {
        appContext = context.applicationContext
        if (loaded.value) return
        val stored = prefs()?.getString(KEY_IDS, null).orEmpty()
        if (stored.isNotEmpty()) {
            stored.split(SEPARATOR).filter { it.isNotBlank() }.forEach { dismissed[it] = true }
        }
        loaded.value = true
    }

    fun isDismissed(blockId: String): Boolean = dismissed.containsKey(blockId)

    fun dismiss(blockId: String) {
        if (blockId.isBlank()) return
        dismissed[blockId] = true
        // Порядок вставки в SnapshotStateMap не гарантирован, поэтому при
        // переполнении просто отбрасываем лишнее — потеря означает, что баннер
        // однажды покажется снова, и это дешевле бесконечного роста ключа.
        val ids = dismissed.keys.toList().let { if (it.size > MAX_ENTRIES) it.takeLast(MAX_ENTRIES) else it }
        prefs()?.edit()?.putString(KEY_IDS, ids.joinToString(SEPARATOR))?.apply()
    }

    private fun prefs() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
