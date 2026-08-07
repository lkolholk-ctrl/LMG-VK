package com.lmg.vk.audio

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Скачивание HLS-аудио VK: master → variant → сегменты (AES-128) → единый файл.
 *
 * Портировано с `M3U8.java` из VK MP3 Mod (см. §3.2 разбора). Отличия от мода
 * отмечены по месту — там, где мод делал нечто не по RFC 8216.
 *
 * ГЛАВНОЕ НАБЛЮДЕНИЕ РЕВЕРСА, определившее конструкцию: команда FFmpeg у мода
 * расшифровывается в `-c:a copy -f mp3`, то есть БЕЗ реэнкода. Значит внутри
 * MPEG-TS у VK уже лежит готовый MP3-поток, и «конвертация» — это ремукс,
 * вынимание элементарного потока из контейнера. Такой ремукс мы умеем сделать
 * сами ([remuxTsToMp3]), поэтому настоящий mp3 с тегами получается даже там, где
 * FFmpeg недоступен, — а он сейчас недоступен всегда (см. [FfmpegProvider]).
 */
object HlsDownloader {

    private const val TAG = "lmg-hls"

    /** Ретраи на сегмент — как в моде: сеть VK на мобильном рвётся регулярно. */
    private const val SEGMENT_RETRIES = 3

    /** Размер TS-пакета. Константа стандарта, не подгонка под VK. */
    private const val TS_PACKET_SIZE = 188
    private const val TS_SYNC_BYTE = 0x47

    /** Итог: файл готов и это [ext] («.mp3» или «.m4a»/«.ts» при деградации). */
    sealed class Outcome {
        data class Success(val file: File, val ext: String, val usedFfmpeg: Boolean) : Outcome()
        data class Failure(val reason: String) : Outcome()
    }

    /** Быстрая проверка «это HLS, а не прямая ссылка». */
    fun isHlsUrl(url: String): Boolean {
        val path = url.substringBefore('?').lowercase()
        return path.endsWith(".m3u8") || path.endsWith(".m3u")
    }

    /**
     * Скачать HLS-трек в [dest] (без расширения — его выберем сами).
     *
     * [onProgress] получает 0f..1f. Бюджет как в моде: 10% на плейлисты,
     * 80% на сегменты, остаток на ремукс — иначе прогресс замирает на 100%
     * во время конвертации и выглядит как зависание.
     */
    suspend fun download(
        context: Context,
        client: HttpClient,
        url: String,
        destWithoutExt: File,
        onProgress: (Float) -> Unit = {},
    ): Outcome = withContext(Dispatchers.IO) {
        val tsFile = File(destWithoutExt.parentFile, destWithoutExt.name + ".ts")
        try {
            destWithoutExt.parentFile?.mkdirs()
            if (tsFile.exists()) tsFile.delete()

            // 1. Мастер-плейлист: берём вариант с максимальным BANDWIDTH.
            //    Мод сортирует по убыванию и берёт первый — то есть лучшее
            //    качество, что для скачивания «навсегда» единственно разумно.
            val masterText = fetchText(client, url) ?: return@withContext Outcome.Failure("мастер-плейлист недоступен")
            onProgress(0.05f)

            val mediaUrl = if (masterText.contains("#EXT-X-STREAM-INF")) {
                pickBestVariant(masterText, url)
                    ?: return@withContext Outcome.Failure("в мастер-плейлисте нет вариантов")
            } else {
                url // VK иногда отдаёт медиа-плейлист сразу, без мастера
            }

            val mediaText = if (mediaUrl == url) masterText else fetchText(client, mediaUrl)
                ?: return@withContext Outcome.Failure("медиа-плейлист недоступен")
            onProgress(0.1f)

            // 2. Разбор медиа-плейлиста: сегменты + схема шифрования.
            val playlist = parseMediaPlaylist(mediaText, mediaUrl)
            if (playlist.segments.isEmpty()) return@withContext Outcome.Failure("плейлист без сегментов")

            // 3. Ключи AES-128 кэшируем по URI: у VK он один на весь трек, а
            //    качать его на каждый сегмент — лишние сотни запросов.
            val keyCache = HashMap<String, ByteArray>()

            tsFile.outputStream().buffered().use { out ->
                playlist.segments.forEachIndexed { index, seg ->
                    val bytes = fetchSegmentWithRetry(client, seg, keyCache)
                        ?: return@withContext Outcome.Failure("сегмент ${index + 1}/${playlist.segments.size} не скачался")
                    out.write(bytes)
                    onProgress(0.1f + 0.8f * (index + 1) / playlist.segments.size)
                }
                out.flush()
            }
            if (tsFile.length() <= 0L) return@withContext Outcome.Failure("склеенный поток пуст")

            // 4. Контейнер → mp3. Сначала FFmpeg (он всеяднее), если он реально
            //    есть; иначе — собственный ремуксер.
            val mp3File = File(destWithoutExt.parentFile, destWithoutExt.name + ".mp3")
            if (mp3File.exists()) mp3File.delete()

            if (FfmpegProvider.isAvailable(context)) {
                when (val r = FfmpegProvider.remux(context, tsFile, mp3File)) {
                    is FfmpegProvider.Result.Success -> {
                        if (mp3File.length() > 0L) {
                            tsFile.delete()
                            onProgress(1f)
                            return@withContext Outcome.Success(mp3File, ".mp3", usedFfmpeg = true)
                        }
                    }
                    is FfmpegProvider.Result.Failed ->
                        android.util.Log.w(TAG, "FFmpeg rc=${r.code}, иду своим ремуксом")
                    is FfmpegProvider.Result.Unavailable ->
                        android.util.Log.i(TAG, "FFmpeg недоступен: ${r.reason}")
                }
            }

            // 5. Свой ремукс MPEG-TS → mp3.
            val demuxed = runCatching { remuxTsToMp3(tsFile, mp3File) }
                .onFailure { android.util.Log.w(TAG, "ремукс упал: ${it.message}") }
                .getOrDefault(RemuxResult.Failed)

            when (demuxed) {
                RemuxResult.Mp3 -> {
                    tsFile.delete()
                    onProgress(1f)
                    Outcome.Success(mp3File, ".mp3", usedFfmpeg = false)
                }
                // Внутри TS оказался AAC: сделать из него mp3 без реэнкода
                // невозможно, а реэнкод — это и есть тот FFmpeg, которого нет.
                // Сохраняем ADTS-поток как .aac — это РЕАЛЬНЫЙ формат данных.
                //
                // ПОЧЕМУ не «.m4a»: m4a — это MP4-контейнер с атомами moov/mdat,
                // а у нас на руках сырой ADTS. Переименовать TS в .m4a значит
                // выдать пользователю файл, который не откроется, но выглядит
                // как валидный, — это тот самый молчаливый обман.
                RemuxResult.Aac -> {
                    mp3File.delete()
                    val aacFile = File(destWithoutExt.parentFile, destWithoutExt.name + ".aac")
                    if (aacFile.exists()) aacFile.delete()
                    val extracted = runCatching { extractAacStream(tsFile, aacFile) }
                        .onFailure { android.util.Log.w(TAG, "извлечение AAC упало: ${it.message}") }
                        .getOrDefault(false)
                    onProgress(1f)
                    if (extracted) {
                        tsFile.delete()
                        Outcome.Success(aacFile, ".aac", usedFfmpeg = false)
                    } else {
                        aacFile.delete()
                        Outcome.Failure("поток AAC, а FFmpeg для конвертации в mp3 недоступен")
                    }
                }
                RemuxResult.Failed -> {
                    mp3File.delete()
                    Outcome.Failure("не удалось разобрать MPEG-TS (нужен FFmpeg)")
                }
            }
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "HLS-загрузка сорвалась: ${t.message}")
            if (tsFile.exists()) tsFile.delete()
            Outcome.Failure(t.message ?: "неизвестная ошибка")
        }
    }

    // ------------------------------------------------------------------
    // Плейлисты
    // ------------------------------------------------------------------

    private data class Segment(
        val url: String,
        val key: KeyInfo?,
    )

    private data class KeyInfo(val uri: String, val iv: ByteArray)

    private data class Playlist(val segments: List<Segment>)

    /** Вариант с максимальным BANDWIDTH (мод: сортировка по убыванию, first). */
    private fun pickBestVariant(text: String, baseUrl: String): String? {
        val lines = text.lines()
        var best: Pair<Long, String>? = null
        for (i in lines.indices) {
            val line = lines[i].trim()
            if (!line.startsWith("#EXT-X-STREAM-INF")) continue
            val bandwidth = Regex("BANDWIDTH=(\\d+)").find(line)
                ?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            // URL — первая непустая строка-не-тег после тега.
            val uri = lines.drop(i + 1).firstOrNull { it.isNotBlank() && !it.trim().startsWith("#") }
                ?.trim() ?: continue
            if (best == null || bandwidth > best!!.first) best = bandwidth to uri
        }
        return best?.second?.let { resolveUrl(baseUrl, it) }
    }

    /**
     * Разбор медиа-плейлиста.
     *
     * ОТЛИЧИЕ ОТ МОДА (осознанное): мод инкрементирует IV на КАЖДОМ сегменте
     * всегда, даже когда IV задан атрибутом. По RFC 8216 §5.2 при явном
     * `IV=0x...` он используется для всех сегментов как есть, а инкремент —
     * это поведение по умолчанию, когда IV не задан (тогда IV = номер сегмента
     * из EXT-X-MEDIA-SEQUENCE). У VK IV обычно не задан, и оба варианта там
     * совпадают, поэтому мод и работал. Делаем по стандарту: иначе на потоке с
     * явным IV мод расшифровал бы всё, кроме первого сегмента, в мусор.
     */
    private fun parseMediaPlaylist(text: String, baseUrl: String): Playlist {
        val segments = ArrayList<Segment>()
        var currentKeyUri: String? = null
        var explicitIv: ByteArray? = null
        var sequence = 0L
        var pendingSegment = false

        for (raw in text.lines()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXT-X-MEDIA-SEQUENCE") ->
                    sequence = line.substringAfter(':').trim().toLongOrNull() ?: 0L

                line.startsWith("#EXT-X-KEY") -> {
                    val attrs = parseAttributes(line.substringAfter(':'))
                    val method = attrs["METHOD"]
                    if (method == null || method == "NONE") {
                        currentKeyUri = null
                        explicitIv = null
                    } else if (method == "AES-128") {
                        currentKeyUri = attrs["URI"]?.let { resolveUrl(baseUrl, it) }
                        explicitIv = attrs["IV"]?.let { hexToBytes(it) }
                    } else {
                        // SAMPLE-AES и прочее мы не умеем — сегменты просто не
                        // расшифруются, и загрузка честно упадёт, а не выдаст шум.
                        currentKeyUri = attrs["URI"]?.let { resolveUrl(baseUrl, it) }
                        explicitIv = attrs["IV"]?.let { hexToBytes(it) }
                    }
                }

                line.startsWith("#EXTINF") -> pendingSegment = true

                line.isNotBlank() && !line.startsWith("#") -> {
                    if (pendingSegment || segments.isEmpty()) {
                        val keyInfo = currentKeyUri?.let { uri ->
                            KeyInfo(uri, explicitIv ?: sequenceToIv(sequence + segments.size))
                        }
                        segments.add(Segment(resolveUrl(baseUrl, line), keyInfo))
                    }
                    pendingSegment = false
                }
            }
        }
        return Playlist(segments)
    }

    /** IV по умолчанию — 128-битный big-endian номер сегмента (RFC 8216). */
    private fun sequenceToIv(sequence: Long): ByteArray {
        val iv = ByteArray(16)
        var v = sequence
        for (i in 15 downTo 8) {
            iv[i] = (v and 0xFF).toByte()
            v = v ushr 8
        }
        return iv
    }

    /** Разбор `KEY=VALUE,KEY="VALUE"` из строки атрибутов тега. */
    private fun parseAttributes(s: String): Map<String, String> {
        val result = HashMap<String, String>()
        var i = 0
        val sb = StringBuilder()
        var key: String? = null
        var inQuotes = false
        while (i < s.length) {
            val c = s[i]
            when {
                c == '"' -> inQuotes = !inQuotes
                c == '=' && !inQuotes && key == null -> { key = sb.toString().trim(); sb.clear() }
                c == ',' && !inQuotes -> {
                    if (key != null) result[key] = sb.toString().trim()
                    key = null; sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        if (key != null) result[key] = sb.toString().trim()
        return result
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.removePrefix("0x").removePrefix("0X")
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) {
            out[i] = ((Character.digit(clean[i * 2], 16) shl 4) or
                Character.digit(clean[i * 2 + 1], 16)).toByte()
        }
        return out
    }

    /** Относительный URL сегмента → абсолютный, относительно плейлиста. */
    private fun resolveUrl(baseUrl: String, ref: String): String =
        runCatching { URI(baseUrl).resolve(ref).toString() }.getOrDefault(ref)

    // ------------------------------------------------------------------
    // Сеть
    // ------------------------------------------------------------------

    private suspend fun fetchText(client: HttpClient, url: String): String? = try {
        val response: HttpResponse = client.get(url)
        // Статус проверяем по числу: `isSuccess` в проекте не используется и на
        // нём уже падала сборка.
        if (response.status.value in 200..299) response.bodyAsText() else {
            android.util.Log.e(TAG, "HTTP ${response.status.value} для плейлиста")
            null
        }
    } catch (t: Throwable) {
        android.util.Log.e(TAG, "плейлист не получен: ${t.message}")
        null
    }

    private suspend fun fetchBytes(client: HttpClient, url: String): ByteArray? = try {
        val response: HttpResponse = client.get(url)
        if (response.status.value !in 200..299) null else {
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(64 * 1024)
            val acc = ByteArrayOutputStream()
            while (true) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read <= 0) break
                acc.write(buffer, 0, read)
            }
            acc.toByteArray()
        }
    } catch (t: Throwable) {
        null
    }

    private suspend fun fetchSegmentWithRetry(
        client: HttpClient,
        segment: Segment,
        keyCache: MutableMap<String, ByteArray>,
    ): ByteArray? {
        repeat(SEGMENT_RETRIES) { attempt ->
            val raw = fetchBytes(client, segment.url)
            if (raw != null && raw.isNotEmpty()) {
                val key = segment.key
                if (key == null) return raw
                val keyBytes = keyCache.getOrPut(key.uri) {
                    fetchBytes(client, key.uri) ?: ByteArray(0)
                }
                if (keyBytes.size == 16) {
                    val decrypted = runCatching { decryptAes128(raw, keyBytes, key.iv) }.getOrNull()
                    if (decrypted != null) return decrypted
                } else {
                    keyCache.remove(key.uri) // не тот ключ — пробуем ещё раз
                }
            }
            android.util.Log.w(TAG, "ретрай #${attempt + 1} сегмента ${segment.url}")
        }
        return null
    }

    /**
     * AES-128-CBC с PKCS5-паддингом — каждый сегмент расшифровывается отдельно,
     * ровно как в моде: паддинг стоит в конце каждого сегмента, а не потока.
     */
    private fun decryptAes128(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(data)
    }

    // ------------------------------------------------------------------
    // Ремукс MPEG-TS → MP3 (путь без FFmpeg)
    // ------------------------------------------------------------------

    enum class RemuxResult { Mp3, Aac, Failed }

    /**
     * Вынимает аудио-элементарный поток из MPEG-TS.
     *
     * ПОЧЕМУ это вообще возможно без FFmpeg: команда мода `-c:a copy` ничего не
     * перекодирует — она лишь снимает контейнер. Внутри TS у VK лежат кадры
     * MPEG-1 Layer III, а «mp3-файл» — это и есть просто поток таких кадров.
     * Поэтому корректный разбор TS + запись payload'ов = валидный mp3.
     *
     * Разбор идёт по стандарту: PAT (PID 0) → PMT → поток с типом 0x03/0x04
     * (MPEG-1/2 audio). Тип 0x0F/0x11 (AAC) сообщаем наверх отдельно: его в mp3
     * без реэнкода не превратить.
     */
    fun remuxTsToMp3(tsFile: File, mp3File: File): RemuxResult {
        val audio = findAudioStream(tsFile) ?: return RemuxResult.Failed
        // 0x0F = AAC ADTS, 0x11 = AAC LATM. mp3 из них без реэнкода не сделать —
        // решение принимает вызывающий, здесь только честный признак.
        if (audio.second == 0x0F || audio.second == 0x11) return RemuxResult.Aac
        if (audio.second != 0x03 && audio.second != 0x04) return RemuxResult.Failed

        val wrote = writeElementaryStream(tsFile, mp3File, audio.first)
        return if (wrote > 0L && mp3File.length() > 0L) RemuxResult.Mp3 else RemuxResult.Failed
    }

    /**
     * Вынимает AAC как ADTS-поток (.aac). Заголовки ADTS уже присутствуют в
     * элементарном потоке TS, поэтому достаточно того же копирования payload'ов,
     * что и для mp3, — отдельной упаковки не требуется.
     */
    fun extractAacStream(tsFile: File, aacFile: File): Boolean {
        val audio = findAudioStream(tsFile) ?: return false
        if (audio.second != 0x0F) return false // LATM без переупаковки не отдать
        return writeElementaryStream(tsFile, aacFile, audio.first) > 0L && aacFile.length() > 0L
    }

    /** Поиск (PID, stream_type) аудио-потока: PAT (PID 0) → PMT → поток. */
    private fun findAudioStream(tsFile: File): Pair<Int, Int>? {
        var pmtPid = -1
        var result: Pair<Int, Int>? = null
        tsFile.inputStream().buffered().use { input ->
            val packet = ByteArray(TS_PACKET_SIZE)
            scan@ while (true) {
                if (!readFully(input, packet)) break@scan
                if ((packet[0].toInt() and 0xFF) != TS_SYNC_BYTE) break@scan
                val pid = ((packet[1].toInt() and 0x1F) shl 8) or (packet[2].toInt() and 0xFF)
                val payload = payloadOf(packet) ?: continue
                // Секция начинается только в пакете с payload_unit_start_indicator.
                if ((packet[1].toInt() and 0x40) == 0) continue
                val start = sectionStart(packet, payload.first)
                if (start < 0) continue

                if (pid == 0 && pmtPid < 0) {
                    pmtPid = parsePatForPmt(packet, start)
                } else if (pid == pmtPid && pmtPid >= 0) {
                    val found = parsePmtForAudio(packet, start)
                    if (found != null) { result = found; break@scan }
                }
            }
        }
        return result
    }

    /** Копирует payload'ы нужного PID, снимая PES-заголовки. Возврат — байты. */
    private fun writeElementaryStream(tsFile: File, dest: File, audioPid: Int): Long {
        var wrote = 0L
        tsFile.inputStream().buffered().use { input ->
            dest.outputStream().buffered().use { out ->
                val packet = ByteArray(TS_PACKET_SIZE)
                while (readFully(input, packet)) {
                    if ((packet[0].toInt() and 0xFF) != TS_SYNC_BYTE) break
                    val pid = ((packet[1].toInt() and 0x1F) shl 8) or (packet[2].toInt() and 0xFF)
                    if (pid != audioPid) continue
                    val payload = payloadOf(packet) ?: continue
                    var offset = payload.first
                    var length = payload.second
                    if ((packet[1].toInt() and 0x40) != 0) {
                        // Начало PES-пакета: снимаем его заголовок, в файл должны
                        // попасть только аудио-кадры.
                        val skip = pesHeaderLength(packet, offset, length)
                        if (skip < 0) continue
                        offset += skip
                        length -= skip
                    }
                    if (length > 0 && offset >= 0 && offset + length <= TS_PACKET_SIZE) {
                        out.write(packet, offset, length); wrote += length
                    }
                }
                out.flush()
            }
        }
        return wrote
    }

    /**
     * Payload TS-пакета с учётом adaptation field: (offset, length) или null.
     *
     * pointer_field здесь СОЗНАТЕЛЬНО не снимается: он есть только у секций
     * (PAT/PMT) и отсутствует у PES. Кто именно перед нами, знает вызывающий —
     * он и снимает указатель через [sectionStart]. Ранее эта функция пыталась
     * угадать вид пакета эвристикой, и это был самый хрупкий код разбора.
     */
    private fun payloadOf(packet: ByteArray): Pair<Int, Int>? {
        val adaptationControl = (packet[3].toInt() shr 4) and 0x03
        if (adaptationControl == 0x00 || adaptationControl == 0x02) return null // payload'а нет
        var offset = 4
        if (adaptationControl == 0x03) {
            val adaptationLength = packet[4].toInt() and 0xFF
            offset = 5 + adaptationLength
            if (offset >= TS_PACKET_SIZE) return null
        }
        return offset to (TS_PACKET_SIZE - offset)
    }

    /**
     * Начало секции внутри payload: снимает pointer_field.
     * Вызывается только для PAT/PMT, где этот байт по стандарту присутствует
     * при выставленном payload_unit_start_indicator.
     */
    private fun sectionStart(packet: ByteArray, offset: Int): Int {
        val pointer = packet[offset].toInt() and 0xFF
        val start = offset + 1 + pointer
        return if (start >= TS_PACKET_SIZE) -1 else start
    }

    private fun parsePatForPmt(packet: ByteArray, offset: Int): Int {
        // table_id 0x00, затем section_length; программы идут по 4 байта.
        // Границы проверяем до каждого чтения: секция может быть обрезана концом
        // пакета, а выход за 188 байт — это краш на чужом файле.
        if (offset + 2 >= TS_PACKET_SIZE) return -1
        if ((packet[offset].toInt() and 0xFF) != 0x00) return -1
        val sectionLength = ((packet[offset + 1].toInt() and 0x0F) shl 8) or (packet[offset + 2].toInt() and 0xFF)
        var i = offset + 8 // пропускаем заголовок секции
        val end = minOf(offset + 3 + sectionLength - 4, TS_PACKET_SIZE)
        while (i + 3 < end) {
            val programNumber = ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
            val pid = ((packet[i + 2].toInt() and 0x1F) shl 8) or (packet[i + 3].toInt() and 0xFF)
            if (programNumber != 0) return pid // 0 — это NIT, не программа
            i += 4
        }
        return -1
    }

    /** (PID, stream_type) первого аудио-потока в PMT. */
    private fun parsePmtForAudio(packet: ByteArray, offset: Int): Pair<Int, Int>? {
        if (offset + 2 >= TS_PACKET_SIZE) return null
        if ((packet[offset].toInt() and 0xFF) != 0x02) return null
        val sectionLength = ((packet[offset + 1].toInt() and 0x0F) shl 8) or (packet[offset + 2].toInt() and 0xFF)
        if (offset + 11 >= TS_PACKET_SIZE) return null
        val programInfoLength = ((packet[offset + 10].toInt() and 0x0F) shl 8) or (packet[offset + 11].toInt() and 0xFF)
        var i = offset + 12 + programInfoLength
        val end = minOf(offset + 3 + sectionLength - 4, TS_PACKET_SIZE)
        while (i + 4 < end) {
            val streamType = packet[i].toInt() and 0xFF
            val pid = ((packet[i + 1].toInt() and 0x1F) shl 8) or (packet[i + 2].toInt() and 0xFF)
            val esInfoLength = ((packet[i + 3].toInt() and 0x0F) shl 8) or (packet[i + 4].toInt() and 0xFF)
            if (streamType == 0x03 || streamType == 0x04 || streamType == 0x0F || streamType == 0x11) {
                return pid to streamType
            }
            i += 5 + esInfoLength
        }
        return null
    }

    /** Длина PES-заголовка, которую надо пропустить, чтобы дойти до кадров. */
    private fun pesHeaderLength(packet: ByteArray, offset: Int, length: Int): Int {
        if (length < 9 || offset + 8 >= TS_PACKET_SIZE) return -1
        val startOk = (packet[offset].toInt() and 0xFF) == 0x00 &&
            (packet[offset + 1].toInt() and 0xFF) == 0x00 &&
            (packet[offset + 2].toInt() and 0xFF) == 0x01
        if (!startOk) return 0 // продолжение PES — payload целиком аудио
        val headerDataLength = packet[offset + 8].toInt() and 0xFF
        return 9 + headerDataLength
    }

    private fun readFully(input: java.io.InputStream, buffer: ByteArray): Boolean {
        var read = 0
        while (read < buffer.size) {
            val n = input.read(buffer, read, buffer.size - read)
            if (n < 0) return false
            read += n
        }
        return true
    }
}
