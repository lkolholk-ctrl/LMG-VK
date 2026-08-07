package com.lmg.vk.audio

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/**
 * Запись ID3v2.3-тегов и встраивание обложек в скачанные mp3.
 *
 * Перенос `AudioDownloader.setTags` из VK MP3 Mod (VK_MP3_MOD_RECOVERY.md §4).
 * Мод пользовался jaudiotagger; новых зависимостей в проект добавлять нельзя,
 * поэтому байты тега собираются вручную в [Id3Utils] по спеке ID3v2.3.
 *
 * Пишутся те же поля, что и у мода: TIT2, TPE1, TALB, TYER, TRCK, TCON,
 * USLT (текст песни), COMM (комментарий), APIC (обложка).
 */
object Mp3TagWriter {

    /**
     * Верхняя граница размера обложки для APIC.
     *
     * VK отдаёт обложки разных размеров (photo_300 ≈ 30–60 КБ, photo_1200 ≈ 200–500 КБ),
     * так что 2 МБ с запасом покрывают любой честный вариант. Ограничение нужно потому,
     * что APIC целиком лежит в начале файла: раздутая картинка заставляет плеер вычитывать
     * её до первого аудиокадра, а размер фрейма ID3v2.3 вообще ограничен 4 байтами.
     * Если по ссылке пришло больше — считаем это не обложкой и отдаём null.
     */
    private const val MAX_COVER_BYTES = 2 * 1024 * 1024

    /**
     * Запас нулей в конце тега. По спеке padding входит в размер тега и легален.
     * Он нужен, чтобы последующая правка тегов (наша повторная или сторонним редактором)
     * могла дописать поле, не перекладывая многомегабайтный mp3 целиком.
     */
    private const val PADDING_BYTES = 2048

    data class Meta(
        val title: String?, val artist: String?, val album: String?,
        val year: String?, val trackNumber: String?, val genre: String?,
        val lyrics: String?, val comment: String?, val coverBytes: ByteArray?,
    ) {
        // ByteArray в data-классе сравнивается по ссылке, из-за чего два одинаковых Meta
        // считались бы разными. Переопределяем, чтобы кэши/дедупликация вызывающего кода
        // не ломались на обложке.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Meta) return false
            return title == other.title && artist == other.artist && album == other.album &&
                year == other.year && trackNumber == other.trackNumber && genre == other.genre &&
                lyrics == other.lyrics && comment == other.comment &&
                (coverBytes?.contentEquals(other.coverBytes) ?: (other.coverBytes == null))
        }

        override fun hashCode(): Int {
            var result = title?.hashCode() ?: 0
            result = 31 * result + (artist?.hashCode() ?: 0)
            result = 31 * result + (album?.hashCode() ?: 0)
            result = 31 * result + (year?.hashCode() ?: 0)
            result = 31 * result + (trackNumber?.hashCode() ?: 0)
            result = 31 * result + (genre?.hashCode() ?: 0)
            result = 31 * result + (lyrics?.hashCode() ?: 0)
            result = 31 * result + (comment?.hashCode() ?: 0)
            result = 31 * result + (coverBytes?.contentHashCode() ?: 0)
            return result
        }
    }

    /** Дописывает ID3v2.3-тег в начало файла. true — успех. */
    fun write(file: File, meta: Meta): Boolean {
        if (!file.isFile || file.length() <= 0L) return false

        val frames = buildFrames(meta)
        // Писать нечего — честно говорим, что тег не записан, вместо пустого заголовка.
        if (frames.isEmpty()) return false

        val tag = try {
            Id3Utils.buildTag(frames, PADDING_BYTES)
        } catch (t: Throwable) {
            return false
        }

        // Старый ID3v2 в начале выбрасываем (иначе получилось бы два тега, а плеер читает
        // первый), ID3v1 в конце тоже (он однобайтовый и на кириллице всегда мусор).
        val skipHead = Id3Utils.id3v2Length(file)
        val cutTail = Id3Utils.id3v1Length(file)
        val audioBytes = file.length() - skipHead - cutTail
        if (audioBytes <= 0L) return false // на аудиоданные ничего не осталось — файл битый, не портим его

        // ID3v2.3 обязан лежать в начале файла, то есть mp3 надо переписать целиком.
        // Делаем это через временный файл рядом и только потом подменяем исходный:
        // при обрыве (нет места, kill процесса) исходный mp3 остаётся целым.
        // Требование пользователя: лучше mp3 без тегов, чем битый mp3.
        val tmp = File(file.parentFile, file.name + ".id3tmp")
        try {
            if (tmp.exists() && !tmp.delete()) return false

            val written = try {
                copyWithTag(file, tmp, tag, skipHead, audioBytes)
            } catch (t: Throwable) {
                false
            }
            // Сверяем длину: если места на диске не хватило, поток мог оборваться молча.
            if (!written || tmp.length() != tag.size + audioBytes) {
                tmp.delete()
                return false
            }

            return replaceOriginal(file, tmp)
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    /** Скачивает обложку по URL для APIC. null — если не удалось. */
    suspend fun fetchCover(client: HttpClient, url: String): ByteArray? {
        if (url.isBlank()) return null
        // Ни одна ошибка здесь не должна ронять скачивание трека: без обложки
        // трек всё равно сохраняется с текстовыми тегами.
        return try {
            withContext(Dispatchers.IO) {
                val response = client.get(url)
                // Проверка статуса тем же способом, что и в остальном проекте
                // (VkApiClient, LastFmScrobbler): `status.value in 200..299`.
                // `HttpStatusCode.isSuccess()` в кодовой базе не используется
                // нигде, и ставить сборку на непроверенный импорт незачем.
                if (response.status.value !in 200..299) return@withContext null

                // Если сервер сам сказал, что тело больше лимита, тело даже не читаем.
                val declared = response.headers["Content-Length"]?.toLongOrNull()
                if (declared != null && declared > MAX_COVER_BYTES) return@withContext null

                val channel = response.bodyAsChannel()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                // Content-Length берём лишь как подсказку для начального размера буфера:
                // некоторые серверы присылают -1, и такой размер уронил бы конструктор.
                val hint = declared?.coerceIn(0L, MAX_COVER_BYTES.toLong())?.toInt() ?: DEFAULT_BUFFER_SIZE
                val acc = java.io.ByteArrayOutputStream(hint)
                try {
                    var emptyReads = 0
                    while (true) {
                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read < 0) break // конец потока
                        if (read == 0) {
                            // Ноль байт при непустом буфере — нештатная ситуация; страхуемся
                            // от бесконечного цикла, но не считаем это концом данных сразу,
                            // иначе можно молча обрезать картинку.
                            if (++emptyReads > 32) return@withContext null
                            continue
                        }
                        emptyReads = 0
                        // Обрезать картинку нельзя: получился бы битый JPEG в APIC.
                        // Поэтому при превышении лимита отказываемся от обложки целиком.
                        if (acc.size() + read > MAX_COVER_BYTES) return@withContext null
                        acc.write(buffer, 0, read)
                    }
                } finally {
                    channel.cancel(null)
                }

                val bytes = acc.toByteArray()
                // Проверяем, что это действительно поддерживаемая картинка: VK на ошибке
                // или редиректе легко отдаёт HTML/JSON со статусом 200, и такой «обложкой»
                // мы бы испортили тег.
                if (bytes.isEmpty() || Id3Utils.sniffImageMime(bytes) == null) null else bytes
            }
        } catch (t: Throwable) {
            null
        }
    }

    // ------------------------------------------------------------------ внутреннее

    private fun buildFrames(meta: Meta): List<ByteArray> {
        val frames = ArrayList<ByteArray>(9)
        // Порядок как у мода: сначала текст, обложка последней. Так плееру, который читает
        // фреймы по порядку, не приходится сначала пролистывать сотни килобайт APIC.
        meta.title?.trim()?.takeIf { it.isNotEmpty() }?.let { frames += Id3Utils.textFrame("TIT2", it) }
        meta.artist?.trim()?.takeIf { it.isNotEmpty() }?.let { frames += Id3Utils.textFrame("TPE1", it) }
        meta.album?.trim()?.takeIf { it.isNotEmpty() }?.let { frames += Id3Utils.textFrame("TALB", it) }
        // TYER по спеке — ровно 4 цифры года; мод брал его из AudioGetYearAndOrder (§4.2),
        // где при неизвестном альбоме приходит 0. Мусор в TYER ломает сортировку по годам.
        meta.year?.trim()?.takeIf { it.length == 4 && it.all(Char::isDigit) && it != "0000" }
            ?.let { frames += Id3Utils.textFrame("TYER", it) }
        // TRCK — «номер» или «номер/всего»; у мода это позиция в плейлисте, и 0 означает
        // «не нашли трек в альбоме», такой номер писать незачем.
        meta.trackNumber?.trim()?.takeIf { it.isNotEmpty() && it.trimStart('0').isNotEmpty() }
            ?.let { frames += Id3Utils.textFrame("TRCK", it) }
        meta.genre?.trim()?.takeIf { it.isNotEmpty() }?.let { frames += Id3Utils.textFrame("TCON", it) }
        meta.comment?.trim()?.takeIf { it.isNotEmpty() }?.let { frames += Id3Utils.commentFrame(it) }
        // USLT — у мода текст из audio.getLyrics (§4.3). Переводы строк оставляем как есть:
        // построчный текст — это и есть формат USLT.
        meta.lyrics?.takeIf { it.isNotBlank() }?.let { frames += Id3Utils.lyricsFrame(it) }
        meta.coverBytes?.takeIf { it.isNotEmpty() && it.size <= MAX_COVER_BYTES }?.let { cover ->
            // MIME только по сигнатуре байтов; не угадали формат — обложку пропускаем,
            // но текстовые теги всё равно записываем.
            Id3Utils.sniffImageMime(cover)?.let { mime -> frames += Id3Utils.apicFrame(mime, cover) }
        }
        return frames
    }

    /**
     * Пишет в [dst] новый тег и следом аудиоданные из [src], пропустив [skipHead] байт
     * старого тега и взяв ровно [audioBytes] байт. Стримим чанками, а не readBytes():
     * трек на 100 МБ не должен оказаться в heap приложения целиком.
     */
    private fun copyWithTag(src: File, dst: File, tag: ByteArray, skipHead: Long, audioBytes: Long): Boolean {
        RandomAccessFile(src, "r").use { input ->
            input.seek(skipHead)
            dst.outputStream().buffered().use { out ->
                out.write(tag)
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var remaining = audioBytes
                while (remaining > 0) {
                    val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                    val read = input.read(buffer, 0, toRead)
                    if (read <= 0) return false // файл короче, чем обещала длина — не подменяем исходный
                    out.write(buffer, 0, read)
                    remaining -= read
                }
                out.flush()
            }
        }
        return true
    }

    /**
     * Подменяет исходный файл готовым временным.
     *
     * Сначала пробуем обычный rename — на одной ФС он атомарен, и исходный файл
     * в любой момент либо старый, либо новый. Если ФС так не умеет (бывает на
     * SAF/эмулированном хранилище Android), отходим на схему с бэкапом: исходник
     * отводим в сторону и возвращаем на место, если подмена не удалась.
     */
    private fun replaceOriginal(original: File, tmp: File): Boolean {
        if (tmp.renameTo(original)) return true

        val backup = File(original.parentFile, original.name + ".id3bak")
        if (backup.exists() && !backup.delete()) return false
        if (!original.renameTo(backup)) return false
        if (tmp.renameTo(original)) {
            backup.delete()
            return true
        }
        // Подмена не удалась — обязаны вернуть пользователю исходный mp3.
        backup.renameTo(original)
        return false
    }
}
