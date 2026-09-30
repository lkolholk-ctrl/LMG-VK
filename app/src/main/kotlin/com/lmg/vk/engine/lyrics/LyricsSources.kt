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
    LMG_LYRICS_PLUS(
        id = "lmg_lyrics_plus",
        title = "LMG Lyrics Plus",
        description = "Пословная синхронизация",
    ),
    BINI_LYRICS(
        id = "bini_lyrics",
        title = "BiniLyrics",
        description = "Пословная и построчная лирика TTML",
    ),
    LYRICS_PLUS(
        id = "lyrics_plus",
        title = "LyricsPlus",
        description = "Плавная пословная синхронизация",
    ),
    LRCLIB(
        id = "lrclib",
        title = "LRCLIB",
        description = "Надежная построчная синхронизация",
    ),
    BETTER_LYRICS(
        id = "better_lyrics",
        title = "BetterLyrics",
        description = "Тайминги слов из Apple Music",
    ),
}

object LyricsSourceStore {
    private const val PREFS = "lyrics_sources"
    private const val KEY = "enabled"
    private const val KEY_APPLE_TTML_ADDED = "apple_ttml_added"
    private const val KEY_LMG_LYRICS_PLUS_ADDED = "lmg_lyrics_plus_added"
    private const val KEY_BINI_LYRICS_ADDED = "bini_lyrics_added"

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
        if (!preferences.getBoolean(KEY_BINI_LYRICS_ADDED, false)) {
            result += LyricsSource.BINI_LYRICS
            preferences.edit()
                .putStringSet(KEY, result.mapTo(linkedSetOf()) { it.id })
                .putBoolean(KEY_BINI_LYRICS_ADDED, true)
                .apply()
        }
        if (!preferences.getBoolean(KEY_LMG_LYRICS_PLUS_ADDED, false)) {
            result += LyricsSource.LMG_LYRICS_PLUS
            preferences.edit()
                .putStringSet(KEY, result.mapTo(linkedSetOf()) { it.id })
                .putBoolean(KEY_LMG_LYRICS_PLUS_ADDED, true)
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
            .putBoolean(KEY_BINI_LYRICS_ADDED, true)
            .putBoolean(KEY_LMG_LYRICS_PLUS_ADDED, true)
            .apply()
        LyricsParser.trimCache()
    }
}

object LyricsDisplayStore {
    private const val PREFS = "lyrics_display"
    private const val KEY_TRANSLATION = "translation"
    private const val KEY_PRONUNCIATION = "pronunciation"

    fun translation(context: Context): Boolean = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_TRANSLATION, true)

    fun pronunciation(context: Context): Boolean = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_PRONUNCIATION, false)

    fun setTranslation(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_TRANSLATION, enabled).apply()
    }

    fun setPronunciation(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PRONUNCIATION, enabled).apply()
    }
}

fun String.lyricsSourceTitle(): String = when (this) {
    "vk" -> "VK Музыка"
    LyricsSource.APPLE_TTML.id -> LyricsSource.APPLE_TTML.title
    LyricsSource.LMG_LYRICS_PLUS.id -> LyricsSource.LMG_LYRICS_PLUS.title
    LyricsSource.BINI_LYRICS.id -> LyricsSource.BINI_LYRICS.title
    LyricsSource.LYRICS_PLUS.id -> LyricsSource.LYRICS_PLUS.title
    LyricsSource.BETTER_LYRICS.id -> LyricsSource.BETTER_LYRICS.title
    LyricsSource.LRCLIB.id -> LyricsSource.LRCLIB.title
    "embedded" -> "Встроенный текст"
    "mine_word" -> "Моя синхронизация"
    else -> "Источник не указан"
}
