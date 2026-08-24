package com.lmg.vk.downloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.lmg.vk.R
import com.lmg.vk.network.dto.music.AudioPlaylist
import com.lmg.vk.network.dto.music.AudioTrack
import com.lmg.vk.downloader.cache.CachedTrack
import com.lmg.vk.downloader.cache.CachedLibrary
import java.io.File

/**
 * Сервис загрузки аудио LMG VK (foreground, с уведомлениями и отменой).
 * Восстановлено из `com.lmg.vk.downloader.service.DownloaderService`
 * (2055 строк; метод `mopub` = downloadTrack).
 *
 * Конвейер одного трека:
 *  1. uid трека → Realm CachedTrack ("uid == $0"):
 *     - если запись валидна — стрим-URL берётся из кэша (не протухает)
 *  2. имя файла по шаблону + опции "[playlist]"/папка артиста
 *  3. файл уже существует → пропуск
 *  4. TrackDownloader.download(url, file, progress, cancellation)
 *  5. по завершении — запись в Realm (CachedTrack + embedded thumb)
 *
 * Плейлисты: загрузка списком, промежуточный кэш PlaylistResponse (f36552e).
 */
class DownloaderService : Service() {

    private val cancellation = object : CancellationSignal {
        @Volatile override var isCanceled: Boolean = false
        override fun cancel() { isCanceled = true }
    }

    /** Кэш уже загруженных PlaylistResponse по ключу (f36552e в оригинале). */
    private val playlistCache = HashMap<String, AudioPlaylist>()

    private lateinit var trackDownloader: TrackDownloader
    private lateinit var pathResolver: DownloadPathResolver
    private lateinit var cachedLibrary: CachedLibrary

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildProgressNotification(0, -1))
        // очередь задач из intent-extras -> downloadTrack(...) по каждой
        return START_NOT_STICKY
    }

    // ------------------------------------------------------------------
    // Конвейер одного трека (восстановленный `mopub`)
    // ------------------------------------------------------------------
    suspend fun downloadTrack(
        notificationId: Int?,
        playlistName: String?,
        artistFolder: String?,
        track: AudioTrack,
        progress: DownloadProgress?,
        cancellation: CancellationSignal,
    ) {
        // 1. uid + Realm-кэш
        val uid = track.fullId
        val cached: CachedTrack? = cachedLibrary.findTrack(uid)
        val streamUrl: String = when {
            cached != null && cached.isValid() -> cached.streamUrl
            else -> track.url
        }

        // 2. путь назначения
        val fileName = DownloadNaming.withArtistFolder(
            artistFolder,
            DownloadNaming.withPlaylistPrefix(
                playlistName,
                DownloadNaming.trackFileName(track, ::sanitizeFileName),
                enabled = false, // настройка "[playlist] " (C15409e)
            ),
            enabled = false,   // настройка папки артиста (C11999e)
            sanitize = ::sanitizeFileName,
        )
        val dest: File = pathResolver.resolve(DOWNLOAD_ROOT, fileName)

        // 3. пропуск существующего
        if (pathResolver.exists(DOWNLOAD_ROOT, fileName)) return

        // 4. загрузка
        val ok = trackDownloader.download(streamUrl, dest, progress, cancellation)
        if (!ok || cancellation.isCanceled) {
            dest.delete()
            return
        }

        // 5. запись в кэш (метаданные + embedded thumb)
        cachedLibrary.upsertTrack(
            CachedTrack.fromAudioTrack(track, dest.absolutePath, streamUrl)
        )
    }

    private fun sanitizeFileName(raw: String): String =
        raw.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()

    private fun buildProgressNotification(downloaded: Long, total: Long): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID, getString(R.string.downloads_title), NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.lmg_downloading_tracks))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "lmg_downloads"
        private const val NOTIFICATION_ID = 4201

        /** Корневая папка загрузок (в оригинале — из настроек, Music/LMG VK). */
        const val DOWNLOAD_ROOT = "LMG VK"
    }
}
