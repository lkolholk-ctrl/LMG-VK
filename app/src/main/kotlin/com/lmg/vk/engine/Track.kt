package com.lmg.vk.engine

import android.net.Uri

/**
 * Универсальная модель трека.
 * id: String — поддерживает и локальные треки (MediaStore Long as String),
 * и онлайн-треки VK ("ownerId_audioId").
 */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val albumName: String,
    val uri: Uri,
    val durationMs: Long,
    val albumId: Long,
    /** URL обложки (VK thumbs). Приоритет над albumArtUri. */
    val coverUrl: String? = null,
    /** Список артистов трека (для фитов/коллабораций). Первый — основной. */
    val artists: List<com.lmg.vk.engine.backend.MiniArtist> = emptyList(),
    val isExplicit: Boolean = false,
    val isCustom: Boolean = false,
    /** Music source: "apple", "vk", "wave", etc. Used for stream quality selection. */
    val source: String? = null,
    /** Жанр трека для аналитики "Моей волны". */
    val genre: String? = null,
    /** false для регионально/лицензионно недоступного аудио VK. */
    val isAvailable: Boolean = true
) {
    /** Uri обложки альбома из MediaStore (для локальных треков). */
    val albumArtUri: Uri
        get() = Uri.parse("content://media/external/audio/albumart/$albumId")

    /** Является ли трек онлайн-треком, требующим разрешения стрим-URL. */
    val isOnlineTrack: Boolean
        get() {
            val scheme = uri.scheme?.lowercase()
            if (scheme in setOf("file", "content", "asset", "android.resource", "rawresource")) {
                return false
            }
            return source.equals("vk", ignoreCase = true) || VkAudioIdentity.isFullId(id)
        }

    /** URI для отображения обложки (coverUrl имеет приоритет).
     *  Автоматически оборачивает локальные пути в file:// URI. */
    val displayArtUri: Uri
        get() = coverUrl?.let {
            if (it.startsWith("/")) {
                Uri.fromFile(java.io.File(it))
            } else {
                Uri.parse(it)
            }
        } ?: albumArtUri
}
