package com.lmg.vk.engine

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.TransferListener
import java.io.File
import java.io.IOException

/**
 * Custom DataSource for LiquidMusicGlass.
 *
 * Supports URI schemes:
 * - `liquid://track?id=TRACK_ID&url=REAL_URL` — online track with ID (automatically checks local premium offline cache)
 * - `file://...` — local file
 * - `http://...` / `https://...` — direct URL
 *
 * Automatically resolves offline downloads if the user has a valid Premium subscription.
 */
@OptIn(UnstableApi::class)
class StreamingDataSource private constructor(
    private val context: Context,
    private val httpDataSource: DataSource,
    private val fileDataSource: DataSource,
    private val cacheDataSource: DataSource?
) : DataSource {

    private var currentDataSource: DataSource? = null
    private var currentUri: Uri? = null
    private var transferListener: TransferListener? = null

    override fun addTransferListener(transferListener: TransferListener) {
        this.transferListener = transferListener
        httpDataSource.addTransferListener(transferListener)
        fileDataSource.addTransferListener(transferListener)
        cacheDataSource?.addTransferListener(transferListener)
    }

    companion object {
        const val SCHEME_LIQUID = "liquid"
        const val PARAM_TRACK_ID = "id"
        const val PARAM_URL = "url"

        fun create(
            context: Context,
            httpDataSource: DefaultHttpDataSource.Factory,
            fileDataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(context)
        ): DataSource.Factory {
            return DataSource.Factory {
                val http = httpDataSource.createDataSource()
                // ВАЖНО: file-источник создаётся НА КАЖДЫЙ экземпляр.
                // Раньше сюда по умолчанию приходил один готовый DataSource
                // (createDataSource() вычислялся один раз в default-аргументе) и
                // переиспользовался всеми StreamingDataSource сразу. DataSource не
                // реентерабелен: media3 держит открытыми несколько источников
                // одновременно (текущий трек + предзагрузка следующего), и второй
                // open() на том же инстансе рвал чтение первого.
                val file = fileDataSourceFactory.createDataSource()
                val cache = MediaCacheManager.getCacheDataSourceFactory()?.createDataSource()
                StreamingDataSource(context.applicationContext, http, file, cache)
            }
        }
    }

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        currentUri = uri

        val liquidTrackId =
            if (uri.scheme == SCHEME_LIQUID) uri.getQueryParameter(PARAM_TRACK_ID) else null
        val resolvedUri = resolveUri(uri)
        // Ключ кэша = ID ТРЕКА, а не подписанный URL: подпись живёт 10 минут
        // и меняется при каждом резолве — с ключом-URL кэш никогда не
        // переиспользовался («мёртвый кэш», всё зависело от сети).
        val resolvedSpec = dataSpec.buildUpon()
            .setUri(resolvedUri)
            .apply { if (liquidTrackId != null) setKey("lmg_$liquidTrackId") }
            .build()

        // В DebugLog, а не только в android.util.Log: у пользователя телефон без
        // adb, и экран отладки — единственный способ увидеть, чем закончился
        // резолв. Без этой строки «плеер молчит» невозможно отличить от
        // «ссылка не получена».
        com.lmg.vk.debug.DebugLog.add(
            "DataSource.open track=$liquidTrackId scheme=${resolvedUri.scheme} " +
                "host=${runCatching { resolvedUri.host }.getOrNull()}"
        )
        android.util.Log.d("StreamingDataSource", "open uri=$uri resolved=$resolvedUri scheme=${resolvedUri.scheme}")

        currentDataSource = when (resolvedUri.scheme) {
            // fileDataSource — это DefaultDataSource, он умеет file://, а также
            // content:// (через ContentDataSource), asset://, android.resource://.
            // Локальные треки из MediaStore приходят как content:// — без этой
            // ветки они уходили в httpDataSource и не играли (тишина).
            "file", "content", "asset", "android.resource", "rawresource" -> fileDataSource
            "http", "https" -> cacheDataSource ?: httpDataSource
            else -> httpDataSource
        }

        return try {
            currentDataSource!!.open(resolvedSpec)
        } catch (e: Exception) {
            // Ошибку открытия потока обязательно видеть в отладочном экране:
            // именно она объясняет тишину при формально играющем плеере.
            com.lmg.vk.debug.DebugLog.add(
                "DataSource.open ОШИБКА track=$liquidTrackId: ${e.javaClass.simpleName} ${e.message}"
            )
            throw e
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return currentDataSource?.read(buffer, offset, length)
            ?: throw IOException("No data source open")
    }

    override fun close() {
        currentDataSource?.close()
        currentDataSource = null
        currentUri = null
    }

    override fun getUri(): Uri? = currentDataSource?.uri ?: currentUri

    private fun resolveUri(uri: Uri): Uri {
        if (uri.scheme != SCHEME_LIQUID) return uri

        val trackId = uri.getQueryParameter(PARAM_TRACK_ID)
        // URL, вложенный в liquid:// при сборке MediaItem. Это ПОДПИСАННАЯ ссылка,
        // полученная перед prepare(), и для стартового трека она свежая — поэтому
        // используем её как основной фолбэк, а не как последнюю надежду (см. ниже).
        val embeddedUrl = uri.getQueryParameter(PARAM_URL)?.takeIf { it.isNotEmpty() }

        if (trackId != null) {
            // Check downloaded offline files first (regardless of premium status)
            val offlineMp3 = File(context.filesDir, "downloads/$trackId.mp3")
            val offlineM4a = File(context.filesDir, "downloads/$trackId.m4a")
            val offlineFlac = File(context.filesDir, "downloads/$trackId.flac")
            when {
                offlineMp3.exists() && offlineMp3.length() > 0 -> return Uri.fromFile(offlineMp3)
                offlineM4a.exists() && offlineM4a.length() > 0 -> return Uri.fromFile(offlineM4a)
                offlineFlac.exists() && offlineFlac.length() > 0 -> return Uri.fromFile(offlineFlac)
            }

            val cachedUri = PlayerController.getValidCachedUri(trackId)
            if (cachedUri != null) return cachedUri

            // resolveStreamUrlSync ходит ТОЛЬКО в память (MusicBackend.getTrackInfoSync
            // бросает 404, если трека нет в streamCache) — на холодном кэше он всегда
            // null. Поэтому раньше порядок «sync → embedded» означал: для любого трека,
            // кроме стартового, резолв проваливался и открывался только вложенный URL,
            // а если его не было — летел IOException. Теперь embedded идёт РАНЬШЕ
            // сетевого добора: он уже валиден и не требует запроса.
            PlayerController.resolveStreamUrlSync(trackId)?.let { return it }

            if (embeddedUrl != null) return Uri.parse(embeddedUrl)

            // Сеть лежит, свежий URL не получить. Если аудио уже в кэше
            // (ключ = id трека), апстрим не понадобится — отдаём последний
            // известный URL, даже протухший: чтение пойдёт из кэша.
            val stale = PlayerController.getStaleCachedUri(trackId)
            if (stale != null) return stale
        }

        if (embeddedUrl != null) return Uri.parse(embeddedUrl)

        throw IOException("Cannot resolve URL for track $trackId")
    }
}
