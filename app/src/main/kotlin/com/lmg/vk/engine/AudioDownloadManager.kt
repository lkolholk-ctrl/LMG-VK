package com.lmg.vk.engine

import android.content.Context
import com.lmg.vk.data.local.db.DownloadedTrackEntity
import com.lmg.vk.data.local.db.FavoriteTrackDatabase
import com.lmg.vk.engine.backend.MusicAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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

    fun downloadTrack(context: Context, track: Track, onComplete: (Boolean) -> Unit = {}) {
        // Enforce aggregator rule: PREMIUM ONLY
        if (!MusicAuth.isPremium.value) {
            onComplete(false)
            return
        }

        val trackId = track.id
        // Атомарный барьер (P1, аудит): прежний check-then-act по прогресс-мапе
        // (isDownloading → флаг ставился уже ВНУТРИ корутины) пропускал два
        // быстрых тапа Download → два конкурентных writer'а в один temp-файл =
        // битый файл в downloads/ и в БД.
        if (!activeDownloads.add(trackId)) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
            // Диск-проверки — на IO, не на потоке вызывающего (main из
            // onCustomCommand нотификации; P2, аудит).
            val db = FavoriteTrackDatabase.getInstance(context)
            if (db.isDownloaded(trackId)) {
                onComplete(true)
                return@launch
            }

            val quality = MusicAuth.maxQuality.value ?: "256K"
            val ext = if (quality.uppercase() == "ALAC") ".m4a" else ".mp3"

            updateProgress(trackId, 0.0f)
            val success = performDownload(context, track, ext)
            if (success) {
                val downloadsDir = File(context.filesDir, "downloads")
                val finalFile = File(downloadsDir, "$trackId$ext")
                // Публичные Загрузки (MediaStore) — основной путь; при неудаче
                // остаёмся на приватном файле (см. PublicDownloads).
                val publicUri = com.lmg.vk.data.local.PublicDownloads.exportAudio(
                    context, finalFile,
                    com.lmg.vk.data.local.PublicDownloads
                        .displayName(track.artist, track.title).ifBlank { trackId },
                    ext,
                )
                val storedPath = if (publicUri != null) {
                    finalFile.delete()
                    publicUri
                } else finalFile.absolutePath
                db.insertDownloaded(
                    DownloadedTrackEntity(
                        trackId = trackId,
                        title = track.title,
                        artistName = track.artist,
                        albumTitle = track.albumName,
                        durationMs = track.durationMs,
                        imageUrl = track.coverUrl,
                        localPath = storedPath,
                        localCoverPath = null, // Single-track download doesn't cache cover locally yet
                        quality = quality
                    )
                )
                updateProgress(trackId, null) // remove from active downloading map
                onComplete(true)
            } else {
                updateProgress(trackId, null)
                onComplete(false)
            }
            } finally {
                activeDownloads.remove(trackId)
            }
        }
    }

    /**
     * Скачать ВИДЕОКЛИП (Apple Music) в публичные Загрузки как mp4.
     * [track] — псевдо-трек клипа из PlayerController (id = "clip_<clipId>").
     * В БД downloaded-треков НЕ пишем: клип — не аудио-трек (локальный плеер
     * JUCE его не сыграет), файл живёт в Download/LiquidMusicGlass, открывается
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
                    val connection = URL(streamUrl).openConnection() as HttpURLConnection
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

    fun deleteDownloadedTrack(context: Context, trackId: String) {
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

    /**
     * Clears ALL downloaded tracks from both the database and the file system.
     * Deletes everything in Downloads/LiquidMusicGlass/ including the .covers/ folder.
     * Runs on Dispatchers.IO to avoid ANR when deleting thousands of files.
     */
    suspend fun clearAllDownloads(context: Context) = withContext(Dispatchers.IO) {
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
    }

    private suspend fun performDownload(context: Context, track: Track, ext: String): Boolean = withContext(Dispatchers.IO) {
        val trackId = track.id
        val tempFile = File(context.filesDir, "downloads/${trackId}.temp")

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
            val resolvedUri = PlayerController.resolveStreamUrlSync(trackId)
            android.util.Log.d("DOWNLOAD", "resolvedUri=$resolvedUri")
            if (resolvedUri == null) {
                android.util.Log.e("DOWNLOAD", "resolveStreamUrlSync returned null for $trackId")
                return@withContext false
            }
            val urlString = resolvedUri.toString()
            android.util.Log.d("DOWNLOAD", "urlString=$urlString")

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
                downloadViaUrlConnection(urlString, tempFile, trackId)
            }
            if (!downloaded) {
                if (tempFile.exists()) tempFile.delete()
                return@withContext false
            }

            // 2.5. ID3-теги и обложка — только для mp3.
            //
            // Пишем ДО переноса в итоговое место: если запись тега сорвётся, у
            // пользователя останется корректный mp3 без тегов, а не битый файл в
            // его музыке. Сам `Mp3TagWriter.write` тоже атомарен (пишет рядом и
            // подменяет), так что двойная страховка ничего не стоит.
            //
            // m4a/mp4 пропускаем осознанно: ID3 туда не пишется, там MP4-атомы —
            // другой формат тегов, и попытка дописать ID3 сделала бы файл битым.
            if (ext.equals(".mp3", ignoreCase = true) && tempFile.length() > 0L) {
                runCatching { writeTagsForDownload(tempFile, track) }
                    .onFailure {
                        android.util.Log.w("DOWNLOAD", "теги не записались: ${it.message}")
                    }
            }

            // 3. Move temp file to final location
            val finalFile = File(downloadsDir, "$trackId$ext")
            if (finalFile.exists()) {
                finalFile.delete()
            }
            val renamed = tempFile.renameTo(finalFile)
            android.util.Log.d("DOWNLOAD", "Download complete trackId=$trackId finalFile=${finalFile.absolutePath} size=${finalFile.length()} renamed=$renamed")
            renamed
        } catch (e: Exception) {
            android.util.Log.e("DOWNLOAD", "Download failed trackId=$trackId error=${e.message}")
            e.printStackTrace()
            if (tempFile.exists()) {
                tempFile.delete()
            }
            false
        }
    }

    /**
     * Дописывает ID3v2.3-тег и обложку в скачанный mp3.
     *
     * Зачем: без тегов файл в сторонних плеерах выглядит как «неизвестный
     * исполнитель», а обложки нет вовсе — то есть скачанное фактически теряет
     * половину смысла. Реализация тега — [com.lmg.vk.audio.Mp3TagWriter]
     * (перенос из VK MP3 Mod), кириллица там пишется в UTF-16 с BOM, потому что
     * ISO-8859-1 её не вмещает.
     *
     * Год и номер трека не заполняем: `Track` их не несёт, а у VK они приходят
     * отдельным запросом. Пустое поле честнее выдуманного года — по нему
     * сортируют библиотеку.
     */
    private suspend fun writeTagsForDownload(file: File, track: Track) {
        val cover = track.coverUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { url ->
                val client = tagHttpClient ?: return@let null
                // Обложка не обязательна: нет сети или VK отдал ошибку — трек
                // всё равно получит текстовые теги.
                runCatching { com.lmg.vk.audio.Mp3TagWriter.fetchCover(client, url) }
                    .getOrNull()
            }
        com.lmg.vk.audio.Mp3TagWriter.write(
            file = file,
            meta = com.lmg.vk.audio.Mp3TagWriter.Meta(
                title = track.title.takeIf { it.isNotBlank() },
                artist = track.artist.takeIf { it.isNotBlank() },
                album = track.albumName.takeIf { it.isNotBlank() },
                year = null,
                trackNumber = null,
                genre = track.genre?.takeIf { it.isNotBlank() },
                lyrics = null,
                comment = null,
                coverBytes = cover,
            ),
        )
    }

    /**
     * HTTP-клиент для обложек — тот же, что обслуживает API, вместе с
     * `installVkProxy`.
     *
     * Свой клиент здесь был бы ошибкой: он пошёл бы мимо интерцептора, то есть
     * мимо обхода блокировок. Пока приложение не поднялось, локатор отдаёт
     * `null` — тогда обложку просто не встраиваем, трек всё равно получит
     * текстовые теги.
     */
    private val tagHttpClient: io.ktor.client.HttpClient?
        get() = com.lmg.vk.network.VkApiLocator.mediaClientOrNull()

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
            val response: io.ktor.client.statement.HttpResponse = client.get(url)
            if (response.status.value !in 200..299) {
                android.util.Log.e("DOWNLOAD", "HTTP ${response.status.value} для $trackId")
                return false
            }
            // Длину берём из заголовка напрямую: extension-свойство
            // `HttpResponse.contentLength()` в проекте нигде не используется, и
            // ставить сборку на непроверенный импорт незачем — на `isSuccess`
            // мы уже так обжигались.
            val total = response.headers["Content-Length"]?.toLongOrNull() ?: -1L
            val channel = response.bodyAsChannel()
            FileOutputStream(dest).use { out ->
                val buffer = ByteArray(64 * 1024)
                var written = 0L
                while (true) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                    written += read
                    if (total > 0) updateProgress(trackId, written.toFloat() / total)
                }
                out.flush()
            }
            dest.length() > 0L
        } catch (e: Exception) {
            android.util.Log.e("DOWNLOAD", "Ktor-загрузка сорвалась: ${e.message}")
            false
        }
    }

    /**
     * Прежний путь на `HttpURLConnection` — запасной, когда сетевое ядро ещё не
     * поднялось. Обхода блокировок здесь нет, поэтому это именно фолбэк.
     */
    private fun downloadViaUrlConnection(url: String, dest: File, trackId: String): Boolean {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.requestMethod = "GET"
            connection.connect()
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                return false
            }
            val fileLength = connection.contentLength
            connection.inputStream.use { input ->
                FileOutputStream(dest).use { out ->
                    val data = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(data)
                        if (count == -1) break
                        total += count
                        if (fileLength > 0) updateProgress(trackId, total.toFloat() / fileLength)
                        out.write(data, 0, count)
                    }
                    out.flush()
                }
            }
            connection.disconnect()
            dest.length() > 0L
        } catch (e: Exception) {
            android.util.Log.e("DOWNLOAD", "HttpURLConnection-загрузка сорвалась: ${e.message}")
            false
        }
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
