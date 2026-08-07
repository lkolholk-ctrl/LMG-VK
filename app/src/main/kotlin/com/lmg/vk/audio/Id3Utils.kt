package com.lmg.vk.audio

import java.io.File
import java.io.RandomAccessFile

/**
 * Низкоуровневая сборка байтов ID3v2.3 и разбор уже существующих тегов.
 *
 * Вынесено из [Mp3TagWriter] отдельно, потому что публичный контракт врайтера жёстко
 * зафиксирован (write/fetchCover/Meta) и в него нельзя добавлять служебные функции:
 * на него уже пишет другой код проекта.
 *
 * Основа — `AudioDownloader.setTags` из VK MP3 Mod (jaudiotagger), см.
 * VK_MP3_MOD_RECOVERY.md §4. Мод складывал теги библиотекой, у нас зависимостей
 * добавлять нельзя, поэтому байты собираются руками по спеке ID3v2.3.
 */
internal object Id3Utils {

    /** Кодировка 0 по спеке ID3v2.3 — ISO-8859-1, ей терминатор в один нулевой байт. */
    const val ENC_ISO = 0.toByte()

    /** Кодировка 1 по спеке ID3v2.3 — UTF-16 с BOM, терминатор — два нулевых байта. */
    const val ENC_UTF16 = 1.toByte()

    /**
     * Формальное поле языка для USLT/COMM. Плееры его практически не смотрят,
     * а мод через jaudiotagger писал ровно тот же дефолт, поэтому не выдумываем
     * определение языка по тексту — это давало бы врущее значение.
     */
    private val LANG_ENG = byteArrayOf('e'.code.toByte(), 'n'.code.toByte(), 'g'.code.toByte())

    // ------------------------------------------------------------------ кодировка текста

    /**
     * ID3v2.3 разрешает только ISO-8859-1 и UTF-16.
     * Кириллица (как и любой символ выше U+00FF) в ISO-8859-1 физически не помещается —
     * именно так и появляются «кракозябры» в чужих тегах. Поэтому:
     *  - чистый латинский текст пишем в ISO-8859-1 (компактнее, читают даже древние плееры);
     *  - всё остальное — в UTF-16 с BOM.
     */
    fun encodingFor(text: String): Byte =
        if (text.all { it.code <= 0xFF }) ENC_ISO else ENC_UTF16

    /**
     * Текст в байты в выбранной кодировке.
     * Для UTF-16 обязателен BOM: без него плеер не знает порядок байт и читает мусор.
     * Kotlin-овая Charsets.UTF_16 сама добавляет BOM (big-endian, FE FF) — на это и опираемся,
     * лишний BOM руками не приписываем, иначе он попал бы в текст как символ U+FEFF.
     */
    fun encodeText(text: String, encoding: Byte): ByteArray =
        if (encoding == ENC_UTF16) text.toByteArray(Charsets.UTF_16) else text.toByteArray(Charsets.ISO_8859_1)

    /** Длина терминатора строки: в UTF-16 нулевым байтом обрывается только половина code unit. */
    private fun terminatorSize(encoding: Byte): Int = if (encoding == ENC_UTF16) 2 else 1

    // ------------------------------------------------------------------ фреймы

    /** Обычный текстовый фрейм (TIT2/TPE1/TALB/TYER/TRCK/TCON): байт кодировки + текст. */
    fun textFrame(id: String, value: String): ByteArray {
        val enc = encodingFor(value)
        val text = encodeText(value, enc)
        val payload = ByteArray(1 + text.size)
        payload[0] = enc
        text.copyInto(payload, 1)
        return frame(id, payload)
    }

    /**
     * USLT — несинхронизированный текст песни.
     * Структура иная, чем у T***: кодировка, 3 байта языка, descriptor с терминатором,
     * и только потом сам текст. Если писать USLT как простой текстовый фрейм
     * (как в черновике из архива), плееры покажут пустую лирику или обрежут первые буквы.
     */
    fun lyricsFrame(text: String): ByteArray = descriptorFrame("USLT", text)

    /**
     * COMM — комментарий, структура совпадает с USLT.
     * В моде здесь лежал его водяной знак («Downloaded by VK mp3 mod {owner_id=..._id=...}»,
     * VK_MP3_MOD_RECOVERY.md §5.4): поле заполнялось одной строкой, descriptor оставался пустым.
     * Свой водяной знак мы не подставляем — что писать в COMM, решает вызывающий код.
     */
    fun commentFrame(text: String): ByteArray = descriptorFrame("COMM", text)

    private fun descriptorFrame(id: String, text: String): ByteArray {
        val enc = encodingFor(text)
        val body = encodeText(text, enc)
        val term = terminatorSize(enc)
        // 1 байт кодировки + 3 байта языка + пустой descriptor (только терминатор) + текст
        val payload = ByteArray(1 + 3 + term + body.size)
        var p = 0
        payload[p++] = enc
        LANG_ENG.copyInto(payload, p); p += LANG_ENG.size
        p += term // терминатор пустого descriptor'а: массив уже заполнен нулями
        body.copyInto(payload, p)
        return frame(id, payload)
    }

    /**
     * APIC — встроенная обложка.
     * Описание оставляем пустым и в ISO-8859-1: описание никто не показывает,
     * а UTF-16 здесь только добавил бы BOM и повод для несовместимости.
     * MIME по спеке всегда латиница с одним нулевым байтом-терминатором.
     */
    fun apicFrame(mime: String, image: ByteArray): ByteArray {
        val mimeBytes = mime.toByteArray(Charsets.ISO_8859_1)
        val payload = ByteArray(1 + mimeBytes.size + 1 + 1 + 1 + image.size)
        var p = 0
        payload[p++] = ENC_ISO
        mimeBytes.copyInto(payload, p); p += mimeBytes.size
        payload[p++] = 0 // терминатор MIME
        payload[p++] = 3 // picture type = cover (front), как и у мода
        payload[p++] = 0 // пустое описание
        image.copyInto(payload, p)
        return frame("APIC", payload)
    }

    /**
     * Заголовок фрейма: 4 байта ID, 4 байта размера, 2 байта флагов.
     * Важно: в ID3v2.3 размер фрейма — обычный big-endian int, НЕ syncsafe
     * (syncsafe появился только в v2.4). Перепутать = тег не прочитается вообще.
     */
    private fun frame(id: String, payload: ByteArray): ByteArray {
        val idBytes = id.toByteArray(Charsets.ISO_8859_1)
        require(idBytes.size == 4) { "ID фрейма должен быть из 4 символов: $id" }
        val out = ByteArray(10 + payload.size)
        idBytes.copyInto(out, 0)
        out[4] = (payload.size ushr 24).toByte()
        out[5] = (payload.size ushr 16).toByte()
        out[6] = (payload.size ushr 8).toByte()
        out[7] = payload.size.toByte()
        // out[8], out[9] — флаги фрейма, нули
        payload.copyInto(out, 10)
        return out
    }

    // ------------------------------------------------------------------ заголовок тега

    /**
     * Собирает полный тег: 10 байт заголовка + фреймы + padding.
     * Padding по спеке разрешён и входит в размер тега; он нужен, чтобы сторонний
     * редактор (или наш повторный вызов) мог дописать поля, не перекладывая весь mp3.
     */
    fun buildTag(frames: List<ByteArray>, paddingSize: Int): ByteArray {
        val framesSize = frames.sumOf { it.size }
        val bodySize = framesSize + paddingSize
        // Размер в заголовке — syncsafe 28 бит, больше 256 МБ выразить нельзя.
        require(bodySize in 0 until 0x1000_0000) { "Тег ID3 не влезает в 28 бит: $bodySize" }

        val out = ByteArray(10 + bodySize)
        out[0] = 'I'.code.toByte()
        out[1] = 'D'.code.toByte()
        out[2] = '3'.code.toByte()
        out[3] = 3 // major version -> ID3v2.3
        out[4] = 0 // revision
        out[5] = 0 // флаги: без unsynchronisation, без extended header
        writeSyncsafe(bodySize, out, 6)
        var p = 10
        for (f in frames) {
            f.copyInto(out, p)
            p += f.size
        }
        // хвост массива уже нулевой — это и есть padding
        return out
    }

    private fun writeSyncsafe(value: Int, dst: ByteArray, offset: Int) {
        dst[offset] = ((value ushr 21) and 0x7F).toByte()
        dst[offset + 1] = ((value ushr 14) and 0x7F).toByte()
        dst[offset + 2] = ((value ushr 7) and 0x7F).toByte()
        dst[offset + 3] = (value and 0x7F).toByte()
    }

    private fun readSyncsafe(src: ByteArray, offset: Int): Int =
        ((src[offset].toInt() and 0x7F) shl 21) or
            ((src[offset + 1].toInt() and 0x7F) shl 14) or
            ((src[offset + 2].toInt() and 0x7F) shl 7) or
            (src[offset + 3].toInt() and 0x7F)

    // ------------------------------------------------------------------ разбор существующего

    /**
     * Длина уже имеющегося ID3v2-тега в начале файла, 0 — если тега нет.
     * Нужна, чтобы при перетеговании выбросить старый тег, а не наслоить второй:
     * плееры читают первый и показывали бы старые данные.
     * Формула та же, что мод использовал в своём ID3Parser (§4.4): syncsafe из байт 6..9 + 10.
     */
    fun id3v2Length(file: File): Long {
        if (file.length() < 10) return 0
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val head = ByteArray(10)
                raf.readFully(head)
                if (head[0] != 'I'.code.toByte() || head[1] != 'D'.code.toByte() || head[2] != '3'.code.toByte()) {
                    return 0
                }
                val flags = head[5].toInt() and 0xFF
                val footer = if ((flags and 0x10) != 0) 10 else 0 // footer бывает только в v2.4
                val len = 10L + readSyncsafe(head, 6) + footer
                if (len in 0..file.length()) len else 0
            }
        } catch (t: Throwable) {
            0
        }
    }

    /**
     * Есть ли в конце файла ID3v1 (128 байт, начинаются с "TAG").
     * Его надо отрезать: ID3v1 умеет только однобайтовую кодировку, кириллица там
     * всегда мусор, и часть плееров предпочитает именно v1 — наш аккуратный v2.3
     * оказался бы перекрыт кракозябрами.
     */
    fun id3v1Length(file: File): Long {
        if (file.length() < 128) return 0
        return try {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(file.length() - 128)
                val tag = ByteArray(3)
                raf.readFully(tag)
                if (tag[0] == 'T'.code.toByte() && tag[1] == 'A'.code.toByte() && tag[2] == 'G'.code.toByte()) 128L else 0L
            }
        } catch (t: Throwable) {
            0
        }
    }

    // ------------------------------------------------------------------ картинки

    /**
     * MIME определяем по сигнатуре байтов, а не по расширению в URL:
     * VK отдаёт обложки по ссылкам вида .../photo_300 вообще без расширения,
     * а иногда за «.jpg» лежит png. Неверный MIME в APIC = обложка не покажется.
     * Неизвестный формат (webp/gif/heic) возвращает null: их поддержка в APIC
     * у плееров случайная, лучше сохранить трек вовсе без обложки.
     */
    fun sniffImageMime(bytes: ByteArray): String? {
        if (bytes.size < 12) return null
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        val b2 = bytes[2].toInt() and 0xFF
        val b3 = bytes[3].toInt() and 0xFF
        if (b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF) return "image/jpeg"
        if (b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47) return "image/png"
        return null
    }
}
