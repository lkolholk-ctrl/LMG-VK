package com.lmg.vk.engine.lyrics

import android.content.Context
import com.lmg.vk.engine.LyricsParser

enum class LyricsSource(
    val id: String,
    val title: String,
    val description: String,
) {
    APPLE_TTML(
        id = "apple_ttml",
        title = "Apple TTML",
        description = "Оригинальная послоговая синхронизация",
    ),
    LYRICS_PLUS(
        id = "lyrics_plus",
        title = "LyricsPlus",
        description = "Плавная пословная синхронизация",
    ),
    BETTER_LYRICS(
        id = "better_lyrics",
        title = "BetterLyrics",
        description = "Тайминги слов из Apple Music",
    ),
    LRCLIB(
        id = "lrclib",
        title = "LRCLIB",
        description = "Надежная построчная синхронизация",
    ),
}

object LyricsSourceStore {
    private const val PREFS = "lyrics_sources"
    private const val KEY = "enabled"
    private const val KEY_APPLE_TTML_ADDED = "apple_ttml_added"

    fun enabled(context: Context): Set<LyricsSource> {
        val preferences = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = preferences.getStringSet(KEY, null)
            ?: return LyricsSource.entries.toSet()
        val result = stored.mapNotNullTo(linkedSetOf()) { id ->
            LyricsSource.entries.firstOrNull { it.id == id }
        }
        if (!preferences.getBoolean(KEY_APPLE_TTML_ADDED, false)) {
            result += LyricsSource.APPLE_TTML
            preferences.edit()
                .putStringSet(KEY, result.mapTo(linkedSetOf()) { it.id })
                .putBoolean(KEY_APPLE_TTML_ADDED, true)
                .apply()
        }
        return result
    }

    fun setEnabled(context: Context, sources: Set<LyricsSource>) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY, sources.mapTo(linkedSetOf()) { it.id })
            .putBoolean(KEY_APPLE_TTML_ADDED, true)
            .apply()
        LyricsParser.trimCache()
    }
}

fun String.lyricsSourceTitle(): String = when (this) {
    "vk" -> "VK Музыка"
    LyricsSource.APPLE_TTML.id -> LyricsSource.APPLE_TTML.title
    LyricsSource.LYRICS_PLUS.id -> LyricsSource.LYRICS_PLUS.title
    LyricsSource.BETTER_LYRICS.id -> LyricsSource.BETTER_LYRICS.title
    LyricsSource.LRCLIB.id -> LyricsSource.LRCLIB.title
    "embedded" -> "Встроенный текст"
    "mine_word" -> "Моя синхронизация"
    else -> "Источник не указан"
}
