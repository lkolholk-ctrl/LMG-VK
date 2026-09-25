package com.lmg.vk.engine

import android.content.Context
import com.lmg.vk.data.local.db.DownloadedTrackEntity
import com.lmg.vk.data.local.db.FavoriteTrackDatabase
import com.lmg.vk.engine.backend.MusicAuth
import com.lmg.vk.network.applyVkRequestIdentity
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.withLock
import com.lmg.vk.audio.DownloadQueue
import com.lmg.vk.artwork.OfflineMotionStore
import com.lmg.vk.artwork.OfflineMotionIndex
import java.io.IOException
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Singleton manager to coordinate offline audio downloading.
 * Respects strict premium boundaries enforced by aggregator rules.
 */
object AudioDownloadManager {

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress

    /** Активные закачки — атомарный барьер от дублей (см. downloadTrack). */
    private val activeDownloads: MutableSet<String> =
        java.util.concurrent.ConcurrentHashMap.newKeySet()

    fun isDownloading(trackId: String): Boolean {
        return _downloadProgress.value.containsKey(trackId)
    }

    fun getDownloadProgressValue(trackId: String): Float? {
        return _downloadProgress.value[trackId]
    }

    private val warnings = java.util.concurrent.ConcurrentHashMap<String, String>()
    internal fun reportWarning(id: String, message: String?) { if (message == null) warnings.remove(id) else warnings[id] = message }
    fun warning(id: String): String? = warnings[id]
    internal fun reportProgress(id: String, value: Float?) = updateProgress(id, value)

    fun downloadTrack(context: Context, track: Track, onComplete: (Boolean) -> Unit = {}) {
        if (!MusicAuth.isPremium.value) { onComplete(false); return }
        DownloadQueue.enqueue(context.applicationContext, track, onComplete)
    }
    fun cancel(context: Context, id: String) = DownloadQueue.cancel(context.applicationContext, id)

    /** Worker owns cancellation, retries and the per-track lock. Publish only a complete audio file. */
    internal suspend fun downloadAndStore(context: Context, track: Track) {
        val db = FavoriteTrackDatabase.getInstance(context)
        val dir = File(context.filesDir, "downloads").apply { mkdirs() }
        val key = OfflineMotionIndex.key(track.id)
        val ready = File(dir, ".ready/$key")
        val existing = db.getDownloadedTrack(track.id)?.takeIf {
            com.lmg.vk.data.local.PublicDownloads.exists(context, it.localPath)
        }
        if (existing != null && ready.isFile && ready.readText() == "3") return
        updateProgress(track.id, 0f)
        val quality = MusicAuth.maxQuality.value ?: "256K"
        var file: File? = null
        var published: String? = null
        var committed = false
        var staging: File? = null
        try {
            file = if (existing != null) {
                // Work on a private copy. Cancellation or tag failure cannot damage the saved song.
                val extension = existing.localPath.substringAfterLast('.', "mp3").takeIf { it.length <= 5 } ?: "mp3"
                val copy = File(dir, "$key.repair.$extension").also { staging = it }
                context.contentResolver.openInputStream(com.lmg.vk.data.local.PublicDownloads.toPlayableUri(existing.localPath))?.use { input ->
                    copy.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                    }
                } ?: throw IOException("Saved audio is unreadable")
                runInterruptible { com.lmg.vk.audio.AudioContainer.normalize(copy) }
            } else {
                var ext: String? = null
                for (attempt in 0..1) {
                    ext = performDownload(context, track, ".mp3", attempt > 0)
                    if (ext != null) break
                    currentCoroutineContext().ensureActive()
                    if (attempt == 0) kotlinx.coroutines.delay(750)
                }
                File(dir, "$key${ext ?: throw IOException("Audio download failed")}")
            }
            currentCoroutineContext().ensureActive()
            val query = com.lmg.vk.artwork.ArtworkQuery(track.title, track.artist, track.durationMs, track.albumName)
            var assetFailure: Exception? = null
            val catalog = try { com.lmg.vk.artwork.AppleArtworkRepository.find(context, query) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { assetFailure = e; null }
            val artwork = try {
                com.lmg.vk.artwork.DownloadedArtwork.load(context, query,
                    com.lmg.vk.ui.glass.ArtworkSourceResolver.realCoverOrNull(track.coverUrl), File(dir, ".covers/$key.jpg"))
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { assetFailure = e; null }
            if (com.lmg.vk.artwork.ItunesArtworkRepository.cached(query) == null)
                assetFailure = IOException("Cover lookup did not complete")
            if (catalog?.cover != null && (artwork == null || artwork.localPath == null || !artwork.preferred))
                assetFailure = IOException("Cover was found but could not be saved")
            val lyrics = com.lmg.vk.audio.EmbeddedLyrics.validTtml(com.lmg.vk.audio.EmbeddedLyrics.read(file))
                ?: com.lmg.vk.audio.DownloadLyrics.fetch(context, track)
            val tagged = writeTagsForDownload(file, track, artwork?.bytes, lyrics)
            if (!tagged) assetFailure = IOException("Audio tags could not be written")
            currentCoroutineContext().ensureActive()
            updateProgress(track.id, .92f)
            published = com.lmg.vk.data.local.PublicDownloads.exportAudio(context, file,
                com.lmg.vk.data.local.PublicDownloads.displayName(track.artist, track.title), "." + file.extension)
            val stored = published ?: file.absolutePath
            currentCoroutineContext().ensureActive()
            db.insertDownloaded(DownloadedTrackEntity(trackId = track.id, title = track.title, artistName = track.artist,
                albumTitle = track.albumName, durationMs = track.durationMs, imageUrl = artwork?.url ?: existing?.imageUrl ?: track.coverUrl,
                localPath = stored, localCoverPath = artwork?.localPath ?: existing?.localCoverPath, quality = quality))
            committed = true
            if (published != null) file.delete()
            if (existing != null && existing.localPath != stored) com.lmg.vk.data.local.PublicDownloads.delete(context, existing.localPath)
            try { OfflineMotionStore.save(context, track.id, query, catalog?.motion) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { assetFailure = e }
            assetFailure?.let { throw it }
            // Missing lyrics must never fail a saved audio download. Permit a later explicit retry.
            ready.parentFile?.mkdirs(); ready.writeText(if (lyrics != null) "3" else "2")
            updateProgress(track.id, 1f)
        } finally {
            if (!committed) {
                published?.let { com.lmg.vk.data.local.PublicDownloads.delete(context, it) }
                file?.delete()
                staging?.delete()
            }
            val preserved = if (committed && published == null) file?.absolutePath else existing?.localPath
            dir.listFiles { candidate -> candidate.isFile && candidate.name.startsWith("$key.") }
                ?.filter { it.absolutePath != preserved }?.forEach { it.delete() }
        }
    }

    /**
     * Скачать ВИДЕОКЛИП (Apple Music) в публичные Загрузки как mp4.
     * [track] — псевдо-трек клипа из PlayerController (id = "clip_<clipId>").
     * В БД downloaded-треков НЕ пишем: клип — не аудио-трек (локальный плеер
     * JUCE его не сыграет), файл живёт в Download/LMG-VK, открывается
     * галереей/видеоплеером. Прогресс — та же мапа, что у треков (ключ = id),
     * поэтому кольцо прогресса в FullPlayer работает без правок.
     */
    fun downloadClip(context: Context, track: Track, onComplete: (Boolean) -> Unit = {}) {
        if (!MusicAuth.isPremium.value) { onComplete(false); return }
        val trackId = track.id
        val clipId = trackId.removePrefix("clip_")
        if (!activeDownloads.add(trackId)) return

        CoroutineScope(Dispatchers.IO).launch {
            val tempFile = File(context.cacheDir, "clip_dl_$clipId.tmp")
            var ok = false
            try {
                updateProgress(trackId, 0.0f)
                // Свежий подписанный URL (TTL 10 мин — тот, с которым играем,
                // мог протухнуть). Тёплый клип резолвится мгновенно.
                val streamUrl = com.lmg.vk.engine.backend.MusicBackend.getInstance()
                    .resolveClipStreamUrl(clipId).getOrNull()
                if (streamUrl != null) {
                    val connection = (URL(streamUrl).openConnection() as HttpURLConnection)
                        .applyVkRequestIdentity()
                    connection.connectTimeout = 15000
                    connection.readTimeout = 30000
                    connection.connect()
                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        val fileLength = connection.contentLength
                        connection.inputStream.use { input ->
                            FileOutputStream(tempFile).use { out ->
                                val data = ByteArray(8192)
                                var total = 0L
                                var count: Int
                                while (input.read(data).also { count = it } != -1) {
                                    total += count
                                    if (fileLength > 0) updateProgress(trackId, total.toFloat() / fileLength)
                                    out.write(data, 0, count)
                                }
                            }
                        }
                        connection.disconnect()
                        val publicUri = com.lmg.vk.data.local.PublicDownloads.exportAudio(
                            context, tempFile,
                            com.lmg.vk.data.local.PublicDownloads
                                .displayName(track.artist, track.title).ifBlank { clipId },
                            ".mp4",
                        )
                        ok = publicUri != null
                    } else connection.disconnect()
                }
            } catch (e: Exception) {
                android.util.Log.e("DOWNLOAD", "Clip download failed $clipId: ${e.message}")
            } finally {
                tempFile.delete()
                updateProgress(trackId, null)
                activeDownloads.remove(trackId)
            }
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(
                    context,
                    if (ok) "Clip saved to Downloads" else "Clip download failed",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                onComplete(ok)
            }
        }
    }

    suspend fun deleteDownloadedTrack(context: Context, trackId: String) {
        DownloadQueue.cancelAndWait(context, trackId)
        DownloadQueue.lock(trackId).withLock {
        OfflineMotionStore.remove(context, trackId)
        File(context.filesDir, "downloads/.ready/${OfflineMotionIndex.key(trackId)}").delete()
        val db = FavoriteTrackDatabase.getInstance(context)
        val entity = db.getDownloadedTrack(trackId)
        val ext = if (entity?.quality?.uppercase() == "ALAC") ".m4a" else ".mp3"

        // Delete physical audio file: localPath понимает оба вида пути
        // (content:// из публичных Загрузок и легаси-файл в приватной папке).
        entity?.localPath?.let {
            com.lmg.vk.data.local.PublicDownloads.delete(context, it)
        }
        // Легаси-подстраховка: файл по старой схеме имён в приватной папке.
        val audioFile = File(context.filesDir, "downloads/$trackId$ext")
        if (audioFile.exists()) {
            audioFile.delete()
        }

        // Delete physical cover art file if it exists
        entity?.localCoverPath?.let { coverPath ->
            val coverFile = File(coverPath)
            if (coverFile.exists()) {
                coverFile.delete()
            }
        }

        // Remove from database
        db.deleteDownloaded(trackId)
        }
    }

    /**
     * Clears ALL downloaded tracks from both the database and the file system.
     * Deletes everything in Downloads/LMG-VK/ including the .covers/ folder.
     * Runs on Dispatchers.IO to avoid ANR when deleting thousands of files.
     */
    suspend fun clearAllDownloads(context: Context) = withContext(Dispatchers.IO) {
        DownloadQueue.cancelAll(context)
        DownloadQueue.slots.acquire()
        try {
        DownloadQueue.slots.acquire()
        try {
        OfflineMotionStore.clear(context)
        val db = FavoriteTrackDatabase.getInstance(context)

        // 1. Delete every tracked file: content:// (публичные Загрузки, через
        // MediaStore — прямой File-доступ туда на 10+ запрещён) и легаси-файлы.
        db.getDownloadedTracks().forEach { entity ->
            com.lmg.vk.data.local.PublicDownloads.delete(context, entity.localPath)
            entity.localCoverPath?.let { cover ->
                runCatching { File(cover).takeIf { it.exists() }?.delete() }
            }
        }

        // 2. Delete all physical files in the private app downloads directory
        val privateDir = File(context.filesDir, "downloads")
        if (privateDir.exists()) {
            privateDir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    // .covers/ и прочие подпапки
                    file.listFiles()?.forEach { it.delete() }
                }
                file.delete()
            }
        }

        // 3. Clear the database table
        db.clearAllDownloads()
        } finally { DownloadQueue.slots.release() }
        } finally { DownloadQueue.slots.release() }
    }

    /**
     * Качает трек и возвращает ФАКТИЧЕСКОЕ расширение итогового файла
     * (`.mp3` / `.m4a`) либо null при неудаче. Расширение — результат, а не
     * параметр, потому что у HLS без FFmpeg оно может отличаться от ожидаемого
     * (см. HlsDownloader): вернуть true и оставить вызывающего с неверным именем
     * файла означало бы «успешную» загрузку, которой нет на диске.
     */
    private suspend fun performDownload(context: Context, track: Track, ext: String, refresh: Boolean = false): String? = withContext(Dispatchers.IO) {
        val trackId = track.id
        val diskKey = OfflineMotionIndex.key(trackId)
        val tempFile = File(context.filesDir, "downloads/${diskKey}.temp")

        android.util.Log.d("DOWNLOAD", "performDownload START trackId=$trackId ext=$ext")

        try {
            val downloadsDir = File(context.filesDir, "downloads")
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }

            if (tempFile.exists()) {
                tempFile.delete()
            }

            // 1. Resolve signed streaming URL
            val resolvedUri = if (track.isOnlineTrack) {
                (if (!refresh) PlayerController.getValidCachedUri(trackId) else null)
                    ?: kotlinx.coroutines.withTimeoutOrNull(15_000) {
                        android.net.Uri.parse(com.lmg.vk.engine.backend.MusicBackend.getTrackInfo(
                            trackId, quality = MusicAuth.maxQuality.value ?: "lossless").url)
                    }
            } else track.uri

            if (resolvedUri == null) {
                android.util.Log.e("DOWNLOAD", "resolveStreamUrlSync returned null for $trackId")
                return@withContext null
            }
            val urlString = resolvedUri.toString()


            // 1.5. HLS-ветка.
            //
            // VK на части треков отдаёт не прямую ссылку, а m3u8 — прежний код
            // качал такой «файл» как есть и получал текстовый плейлист вместо
            // музыки. Здесь поток собирается из сегментов и ремуксится в mp3
            // (подробности и почему это возможно без реэнкода — в HlsDownloader).
            //
            // Расширение возвращает сам загрузчик: если внутри TS оказался AAC,
            // без FFmpeg он останется m4a, и назвать его mp3 было бы обманом.
            if (com.lmg.vk.audio.HlsDownloader.isHlsUrl(urlString)) {
                return@withContext performHlsDownload(context, track, urlString, downloadsDir)
            }

            // 2. Скачивание байтов.
            //
            // Идёт через тот же Ktor-клиент, что обслуживает API, то есть через
            // `installVkProxy` — обход блокировок и пиннинг. Раньше здесь стоял
            // голый HttpURLConnection: воспроизведение обход получало, а загрузка
            // нет, и при блокировке CDN скачивание падало без внятной причины.
            //
            // Если клиент ещё не поднят (приложение только стартует), падаем на
            // прежний HttpURLConnection — лучше скачать без обхода, чем не
            // скачать вовсе.
            val mediaClient = com.lmg.vk.network.VkApiLocator.mediaClientOrNull()
            val downloaded = if (mediaClient != null) {
                downloadViaKtor(mediaClient, urlString, tempFile, trackId)
            } else {
                android.util.Log.w("DOWNLOAD", "медиа-клиент не готов, качаю без обхода")
                runInterruptible { downloadViaUrlConnection(urlString, tempFile, trackId) }
            }
            if (!downloaded) {
                if (tempFile.exists()) tempFile.delete()
                return@withContext null
            }

            val finalFile = runInterruptible { com.lmg.vk.audio.AudioContainer.normalize(tempFile) }
            "." + finalFile.extension
        } catch (e: CancellationException) { tempFile.delete(); throw e
        } catch (e: Exception) {
            android.util.Log.e("DOWNLOAD", "Download failed trackId=$trackId error=${e.message}")
            e.printStackTrace()
            if (tempFile.exists()) {
                tempFile.delete()
            }
            null
        }
    }

    /**
     * HLS-путь: сегменты → единый поток → mp3.
     *
     * Вынесено из [performDownload], чтобы не разносить её пошаговую структуру:
     * тут другой транспорт (плейлисты, ключи, ремукс), и в общий поток шагов
     * 1-2-2.5-3 он не укладывается.
     *
     */
    private suspend fun performHlsDownload(
        context: Context,
        track: Track,
        url: String,
        downloadsDir: File,
    ): String? {
        val trackId = track.id
        // Обход блокировок обязателен и здесь: плейлисты и сегменты живут на том
        // же CDN, что и прямые ссылки. Своего клиента не создаём — он пошёл бы
        // мимо installVkProxy.
        val client = com.lmg.vk.network.VkApiLocator.mediaClientOrNull()
        if (client == null) {
            // Честная ошибка вместо тихой деградации: без обхода HLS у
            // заблокированного CDN всё равно не соберётся, а фолбэка тут нет.
            android.util.Log.e("DOWNLOAD", "HLS: медиа-клиент не готов, отменяю $trackId")
            return null
        }

        val base = File(downloadsDir, OfflineMotionIndex.key(trackId))
        val outcome = com.lmg.vk.audio.HlsDownloader.download(
            context = context,
            client = client,
            url = url,
            destWithoutExt = base,
            onProgress = { updateProgress(trackId, it * .85f) },
        )

        return when (outcome) {
            is com.lmg.vk.audio.HlsDownloader.Outcome.Failure -> {
                android.util.Log.e("DOWNLOAD", "HLS не собрался ($trackId): ${outcome.reason}")
                null
            }
            is com.lmg.vk.audio.HlsDownloader.Outcome.Success -> {
                // Файл уже лежит как <trackId><ext> в downloads/ — именно там его
                // ждёт вызывающий, переносить нечего.
                "." + runInterruptible { com.lmg.vk.audio.AudioContainer.normalize(outcome.file) }.extension
            }
        }
    }

    private fun writeTagsForDownload(file: File, track: Track, cover: ByteArray?, lyrics: String?): Boolean {
        val written = com.lmg.vk.audio.DownloadedAudioTags.write(
            file = file,
            meta = com.lmg.vk.audio.Mp3TagWriter.Meta(
                title = track.title.takeIf { it.isNotBlank() },
                artist = track.artist.takeIf { it.isNotBlank() },
                album = track.albumName.takeIf { it.isNotBlank() },
                year = null,
                trackNumber = null,
                genre = track.genre?.takeIf { it.isNotBlank() },
                lyrics = lyrics,
                comment = null,
                coverBytes = cover,
            ),
        )
        if (!written) android.util.Log.w("DOWNLOAD", "Теги не записаны: ${file.name}")
        return written
    }

    /**
     * Скачивание через Ktor-клиент с обходом блокировок.
     *
     * Тело читается каналом по частям, а не целиком в память: трек на 10 МБ в
     * heap ещё влез бы, но у скачивания плейлиста они пошли бы подряд.
     */
    private suspend fun downloadViaKtor(
        client: io.ktor.client.HttpClient,
        url: String,
        dest: File,
        trackId: String,
    ): Boolean {
        return try {
            com.lmg.vk.audio.StreamingAudioTransfer.download(client, url, dest) { updateProgress(trackId, it * .85f) }
            true
        } catch (e: CancellationException) { throw e }
          catch (e: Exception) { android.util.Log.w("DOWNLOAD", "Audio transfer failed: ${e.message}"); false }
    }

    /**
     * Прежний путь на `HttpURLConnection` — запасной, когда сетевое ядро ещё не
     * поднялось. Обхода блокировок здесь нет, поэтому это именно фолбэк.
     */
    private fun downloadViaUrlConnection(url: String, dest: File, trackId: String): Boolean {
        val connection = (URL(url).openConnection() as HttpURLConnection).applyVkRequestIdentity()
        return try {
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.connect()
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return false
            val fileLength = connection.contentLengthLong
            connection.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val data = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw InterruptedException()
                        val count = input.read(data)
                        if (count < 0) break
                        total += count
                        if (fileLength > 0) updateProgress(trackId, .85f * total / fileLength)
                        out.write(data, 0, count)
                    }
                }
            }
            com.lmg.vk.audio.StreamingAudioTransfer.requireComplete(dest.length(), fileLength)
            true
        } catch (e: Exception) {
            dest.delete()
            false
        } finally { connection.disconnect() }
    }

    private fun updateProgress(trackId: String, progress: Float?) {
        // synchronized (P1, аудит): неатомарный read-modify-write StateFlow-мапы
        // с трёх IO-потоков терял апдейты прогресса.
        synchronized(this) {
            val current = _downloadProgress.value.toMutableMap()
            if (progress == null) {
                current.remove(trackId)
            } else {
                current[trackId] = progress
            }
            _downloadProgress.value = current
        }
    }
}
