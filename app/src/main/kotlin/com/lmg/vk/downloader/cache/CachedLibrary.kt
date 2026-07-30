package com.lmg.vk.downloader.cache

import com.lmg.vk.network.dto.music.AudioTrack

/**
 * Realm-сущность кэшированного трека.
 * Восстановлено из `com.lmg.vkreborn.cache.realm.CachedTrack`.
 *
 * Realm-запрос в оригинале: `query("uid == $0", uid)`.
 * Служит для:
 *  - офлайн-воспроизведения (DedicatedCacheService v2)
 *  - переиспользования непротухшего stream URL
 *  - хранения embedded-обложки (CachedEmbeddedThumb)
 */
data class CachedTrack(
    val uid: String,            // "ownerId_audioId" — первичный ключ
    val title: String,
    val artist: String,
    val albumTitle: String?,
    val duration: Int,
    val filePath: String,
    val streamUrl: String,
    val downloadedAt: Long = System.currentTimeMillis(),
    val lyrics: CachedTrackLyrics? = null,
    val embeddedThumb: CachedEmbeddedThumb? = null,
) {
    /** Валидность записи (в оригинале — метод crashlytics()): файл + URL живы. */
    fun isValid(): Boolean =
        filePath.isNotEmpty() && java.io.File(filePath).exists()

    companion object {
        fun fromAudioTrack(track: AudioTrack, path: String, streamUrl: String) = CachedTrack(
            uid = track.fullId,
            title = track.title,
            artist = track.artist,
            albumTitle = track.album?.title,
            duration = track.duration,
            filePath = path,
            streamUrl = streamUrl,
        )
    }
}

/** Из `CachedTrackLyrics` (+ CachedTrackLyricsSynchronizedLine). */
data class CachedTrackLyrics(
    val plain: List<String>?,
    val synchronizedLines: List<SynchronizedLine>?,
) {
    data class SynchronizedLine(
        val beginMs: Long,
        val endMs: Long,
        val text: String,
        val interlude: Boolean = false,
    )
}

/** Из `CachedEmbeddedThumb` — обложка, вшитая в файл. */
data class CachedEmbeddedThumb(
    val mimeType: String,
    val data: ByteArray,
)

/**
 * Фасад кэша (Realm). В оригинале: `C18353e.vip.m4502e()` + репозитории.
 * Реализация — на Realm Kotlin SDK (librealmc.so).
 */
interface CachedLibrary {
    fun findTrack(uid: String): CachedTrack?
    fun upsertTrack(track: CachedTrack)
    fun removeTrack(uid: String)
    fun allTracks(): List<CachedTrack>
}
