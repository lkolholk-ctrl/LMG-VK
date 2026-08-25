package com.lmg.vk.engine.lyrics

import android.content.Context
import com.lmg.vk.engine.LyricsParser

enum class LyricsSource(
    val id: String,
    val title: String,
    val description: String,
) {
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

    fun enabled(context: Context): Set<LyricsSource> {
        val stored = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY, null)
            ?: return LyricsSource.entries.toSet()
        return stored.mapNotNullTo(linkedSetOf()) { id ->
            LyricsSource.entries.firstOrNull { it.id == id }
        }
    }

    fun setEnabled(context: Context, sources: Set<LyricsSource>) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY, sources.mapTo(linkedSetOf()) { it.id })
            .apply()
        LyricsParser.trimCache()
    }
}

fun String.lyricsSourceTitle(): String = when (this) {
    "vk" -> "VK Музыка"
    LyricsSource.LYRICS_PLUS.id -> LyricsSource.LYRICS_PLUS.title
    LyricsSource.BETTER_LYRICS.id -> LyricsSource.BETTER_LYRICS.title
    LyricsSource.LRCLIB.id -> LyricsSource.LRCLIB.title
    "embedded" -> "Встроенный текст"
    "mine_word" -> "Моя синхронизация"
    else -> "Источник не указан"
}
