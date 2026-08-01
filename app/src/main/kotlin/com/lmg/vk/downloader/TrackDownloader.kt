package com.lmg.vk.downloader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.lmg.vk.network.dto.music.AudioTrack
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import java.io.File

/**
 * Низкоуровневый загрузчик потока в файл.
 * Восстановлено из `defpackage.C11047e` (метод `smaato`).
 *
 * Стримит audioTrack.url в файл с колбэком прогресса и поддержкой отмены
 * (CancellationSignal из DownloaderService).
 */
interface TrackDownloader {
    suspend fun download(
        url: String,
        dest: File,
        progress: DownloadProgress?,
        cancellation: CancellationSignal,
    ): Boolean
}

fun interface DownloadProgress {
    /** (downloadedBytes, totalBytes) */
    fun onProgress(downloaded: Long, total: Long)
}

/** Обёртка над android.os.CancellationSignal. */
interface CancellationSignal {
    val isCanceled: Boolean
    fun cancel()
}

/**
 * Резолвер путей загрузки (в оригинале — методы `startapp()/loadAd()` сервиса).
 * Корень: Music/LMG VK (или настраиваемая директория).
 */
interface DownloadPathResolver {
    /** Полный путь файла: root/[artistFolder/]fileName. */
    fun resolve(rootName: String, relative: String): File

    /** Проверка существования (пропуск повторной загрузки). */
    fun exists(rootName: String, relative: String): Boolean
}

/**
 * Шаблон имён файлов (восстановлен из `DownloaderService.mopub`).
 *
 * Базовый вид:  "(album) artist - title (subtitle).mp3"
 * Опции (настройки приложения):
 *  - "[playlist] " префикс имени файла
 *  - подпапка по артисту
 */
object DownloadNaming {

    fun trackFileName(track: AudioTrack, sanitize: (String) -> String): String {
        val sb = StringBuilder()
        track.album?.title?.let { sb.append("(").append(it).append(") ") }
        if (track.artist.isNotEmpty()) sb.append(track.artist).append(" - ")
        sb.append(track.title)
        track.subtitle?.takeIf { it.isNotEmpty() }?.let { sb.append(" (").append(it).append(")") }
        return sanitize(sb.toString()) + ".mp3"
    }

    fun withPlaylistPrefix(playlistName: String?, fileName: String, enabled: Boolean): String =
        if (enabled && playlistName != null) "[$playlistName] $fileName" else fileName

    fun withArtistFolder(artist: String?, fileName: String, enabled: Boolean, sanitize: (String) -> String): String =
        if (enabled && !artist.isNullOrEmpty()) "${sanitize(artist)}/$fileName" else fileName
}

/** Реализация загрузчика на Ktor (стриминг в файл чанками). */
class KtorTrackDownloader(
    private val httpClient: io.ktor.client.HttpClient,
) : TrackDownloader {

    override suspend fun download(
        url: String,
        dest: File,
        progress: DownloadProgress?,
        cancellation: CancellationSignal,
    ): Boolean = withContext(Dispatchers.IO) {
        dest.parentFile?.mkdirs()
        if (dest.exists()) dest.delete()

        try {
            val response = httpClient.get(url)
            val total = response.headers["Content-Length"]?.toLongOrNull() ?: -1L
            var downloaded = 0L

            val channel = response.bodyAsChannel()
            try {
                dest.outputStream().buffered().use { out ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (!cancellation.isCanceled) {
                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        downloaded += read
                        progress?.onProgress(downloaded, total)
                    }
                }
            } finally {
                channel.cancel(null)
            }
            !cancellation.isCanceled
        } catch (e: Exception) {
            dest.delete()
            false
        }
    }
}
