package com.lmg.vk.audio

import android.content.Context
import android.os.Build
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Слой доступа к FFmpeg — БЕЗ жёсткой зависимости на него в сборке.
 *
 * ПОЧЕМУ так, а не `implementation("com.arthenica:ffmpeg-kit-*")`:
 * артефакты ffmpeg-kit сняты с публикации. На 07.08.2026 в
 * `repo1.maven.org/maven2/com/arthenica/` лежат только `smart-exception-*`;
 * `ffmpeg-kit-full` / `-audio` / `-min` отдают 404, у `dl.google.com` их тоже
 * нет, и форка под другим groupId в Central не появилось. То есть вариант
 * «взять готовой зависимостью» физически недоступен, а не «дорог по размеру».
 *
 * ПОЧЕМУ не повторяем схему VK MP3 Mod «докачать .so» как основной путь:
 * мод качает ТОЛЬКО нативные библиотеки, а Java-обёртку `com.arthenica.ffmpegkit`
 * (33 класса) держит внутри своего APK, причём с ПРАВЛЕНЫМ `NativeLoader`, который
 * при `UnsatisfiedLinkError` делает `System.load(filesDir + "/lib/lib<name>.so")`.
 * Без этой обёртки скачанные `.so` бесполезны: вызывать их напрямую нечем, JNI-глюя
 * у нас нет. Саму обёртку взять из Maven уже нельзя (см. выше), а тащить её
 * jadx-декомпиляцией нельзя тем более: декомпиляция лоссовая, а JNI-подписи должны
 * совпадать с бинарником байт в байт.
 *
 * ПОЧЕМУ нельзя «скачать бинарник ffmpeg и запустить процессом»: с Android 10
 * (targetSdk у нас 36) SELinux запрещает exec файлов из каталога данных приложения
 * (`untrusted_app_29` не имеет `execute` на `app_data_file`). `System.load()` из
 * filesDir при этом работает — поэтому единственный рабочий вид докачки — это
 * именно `.so` + своя обёртка, см. выше.
 *
 * ЧТО В ИТОГЕ ДЕЛАЕТ ЭТОТ КЛАСС: подхватывает FFmpeg, если он в системе есть,
 * и честно говорит «нет», если его нет. Обёртка ищется РЕФЛЕКСИЕЙ, поэтому:
 *  - сегодня, когда обёртки в проекте нет, [isAvailable] вернёт false, HLS уедет
 *    на встроенный ремуксер ([HlsDownloader]) и всё продолжит работать;
 *  - если завтра положить `ffmpeg-kit`-AAR в `app/libs/` (или собрать свой
 *    `libffmpegkit.so` с той же обёрткой), FFmpeg включится сам, без правок кода
 *    и без пересборки логики загрузки.
 * Заглушек здесь нет: [remux] либо реально зовёт FFmpeg, либо возвращает
 * [Result.Unavailable] с причиной, которую видно в логе.
 */
object FfmpegProvider {

    private const val TAG = "lmg-ffmpeg"

    /** Каталог докачанных нативных библиотек — раскладка как у мода. */
    private const val NATIVE_DIR = "lib"

    /**
     * Порог «библиотеки на месте», подсмотренный в моде
     * (`FFMpeg.checksExists`): не меньше 17 файлов `.so` и суммарно не меньше
     * ~34.7 МБ. Точные SHA-256 мод не проверял вовсе — нам этого мало, поэтому
     * есть [verifySha256], но порог оставляем как первичный дешёвый фильтр:
     * он отсекает обрыв закачки и распакованный наполовину архив.
     */
    private const val MIN_SO_COUNT = 17
    private const val MIN_SO_TOTAL_BYTES = 34_685_036L

    /** Результат конвертации. Никаких «молча не получилось». */
    sealed class Result {
        object Success : Result()
        /** FFmpeg в системе отсутствует — вызывающий обязан уйти на фолбэк. */
        data class Unavailable(val reason: String) : Result()
        /** FFmpeg есть, но конкретная команда упала. */
        data class Failed(val code: Int, val log: String?) : Result()
    }

    @Volatile private var probed = false
    @Volatile private var probeResult: String? = null // null = доступен

    /**
     * Быстрая проверка «FFmpeg вообще есть». Результат кэшируется: рефлексия и
     * `-version` стоят заметно дороже, чем нам можно тратить на каждый трек
     * плейлиста.
     */
    fun isAvailable(context: Context): Boolean = probeReason(context) == null

    /** Причина недоступности или null, если FFmpeg готов. Для честных сообщений. */
    fun probeReason(context: Context): String? {
        if (probed) return probeResult
        synchronized(this) {
            if (probed) return probeResult
            probeResult = doProbe(context)
            probed = true
            return probeResult
        }
    }

    /** Сбросить кэш пробы — после успешной докачки библиотек. */
    fun invalidate() {
        synchronized(this) { probed = false; probeResult = null }
    }

    private fun doProbe(context: Context): String? {
        // 1. Обёртка. Есть она — есть шанс; нет — дальше идти незачем.
        val kitClass = try {
            Class.forName("com.arthenica.ffmpegkit.FFmpegKit")
        } catch (t: Throwable) {
            return "обёртка com.arthenica.ffmpegkit не подключена (артефакт снят с публикации)"
        }
        // 2. ABI: проект собирается под arm64-v8a / armeabi-v7a / x86, но
        // нативные библиотеки могут быть положены не под ту архитектуру. Ошибку
        // хотим внятную, а не UnsatisfiedLinkError из глубины JNI.
        val abi = primaryAbi()
        if (abi == null) return "не удалось определить ABI устройства"
        // 3. Натив: либо уже в APK (jniLibs), либо докачан в filesDir/lib.
        //    Проверяем самым дешёвым реальным способом — просим версию.
        return try {
            when (val r = executeReflective(kitClass, "-hide_banner -version")) {
                is Result.Success -> null
                is Result.Failed -> "FFmpeg найден, но не запускается (rc=${r.code})"
                is Result.Unavailable -> r.reason
            }
        } catch (t: Throwable) {
            "FFmpeg не грузится на $abi: ${t.message}"
        }
    }

    /**
     * Ремукс контейнера в mp3 БЕЗ реэнкода — ровно та команда, которую мы
     * расшифровали из VK MP3 Mod (`DES.e(..., "FFMpeg")`):
     * `-hide_banner -y -i "<in>" -c:a copy -f mp3 "<out>"`.
     *
     * `-c:a copy` тут принципиально: мод не перекодирует, а перекладывает уже
     * готовый MP3-поток из MPEG-TS в mp3-контейнер. Это же означает, что задача
     * решаема и без FFmpeg (см. [HlsDownloader.remuxTsToMp3]) — FFmpeg остаётся
     * как более всеядный путь для нестандартных потоков.
     */
    fun remux(context: Context, input: File, output: File): Result {
        val reason = probeReason(context)
        if (reason != null) return Result.Unavailable(reason)
        val kitClass = try {
            Class.forName("com.arthenica.ffmpegkit.FFmpegKit")
        } catch (t: Throwable) {
            return Result.Unavailable("обёртка исчезла между пробой и вызовом")
        }
        // Пути в кавычках: имена файлов у нас пользовательские
        // («Артист - Трек.mp3»), пробелы в них — норма.
        val cmd = "-hide_banner -y -i \"${input.absolutePath}\" -c:a copy -f mp3 \"${output.absolutePath}\""
        return try {
            executeReflective(kitClass, cmd)
        } catch (t: Throwable) {
            Result.Failed(-1, t.message)
        }
    }

    /**
     * Вызов FFmpegKit через рефлексию.
     *
     * ПОЧЕМУ рефлексия: без неё файл не скомпилируется, пока обёртки нет в
     * classpath, и тогда весь проект нельзя собрать из-за фичи, которая пока
     * необязательна. Рефлексия делает интеграцию опциональной по-настоящему.
     */
    private fun executeReflective(kitClass: Class<*>, cmd: String): Result {
        val session = kitClass.getMethod("execute", String::class.java).invoke(null, cmd)
            ?: return Result.Unavailable("FFmpegKit.execute вернул null")
        val rc = session.javaClass.getMethod("getReturnCode").invoke(session)
        val rcClass = Class.forName("com.arthenica.ffmpegkit.ReturnCode")
        val isSuccess = rcClass.getMethod("isSuccess", rcClass).invoke(null, rc) as? Boolean ?: false
        if (isSuccess) return Result.Success
        val code = runCatching {
            rc?.javaClass?.getMethod("getValue")?.invoke(rc) as? Int
        }.getOrNull() ?: -1
        val log = runCatching {
            session.javaClass.getMethod("getOutput").invoke(session) as? String
        }.getOrNull()
        return Result.Failed(code, log)
    }

    /** Основной ABI устройства среди поддерживаемых проектом. */
    private fun primaryAbi(): String? {
        val supported = Build.SUPPORTED_ABIS ?: return null
        val ours = setOf("arm64-v8a", "armeabi-v7a", "x86")
        return supported.firstOrNull { it in ours } ?: supported.firstOrNull()
    }

    // ---------------------------------------------------------------------
    // Докачка нативных библиотек (схема VK MP3 Mod). Оставлена рабочей, но
    // выключенной по умолчанию: без Java-обёртки она бессмысленна, а адрес
    // источника не должен быть захардкожен — см. ниже.
    // ---------------------------------------------------------------------

    /**
     * Куда мод кладёт библиотеки: `filesDir/lib`. Тот же путь ищет его патченый
     * `NativeLoader`, поэтому раскладку сохраняем — если обёртку когда-нибудь
     * подключат, она найдёт файлы там, где ожидает.
     */
    fun nativeDir(context: Context): File = File(context.filesDir, NATIVE_DIR)

    /**
     * Что нашлось в реверсе про источник библиотек (для протокола, не для
     * автозагрузки): мод тянет их как ДОКУМЕНТЫ ВК из группы `-135250105`
     * (`FFMpeg.getLibsDownloadUrl`), выбирая документ по ABI:
     *   - armeabi-v7a / armeabi → `doc-135250105_665731516`
     *   - x86 / x86_64         → `doc-135250105_665731526`
     *   - arm64-v8a            → не качается вовсе: в APK мода уже лежат
     *     `libavcodec/libavformat/libavfilter/libavutil/libswresample/libswscale/
     *      libffmpegkit(+_abidetect).so` под arm64 (~18 МБ), см. `lib/arm64-v8a`.
     * URL собирается как `<api base_url>doc-135250105_<did>?api=1`, файл
     * сохраняется как `libs.jar` и распаковывается в `filesDir/lib`.
     *
     * ПОЧЕМУ мы это не включаем по умолчанию: источник — документ в чужой
     * группе, он требует авторизации в VK, может быть удалён в любой момент и
     * не имеет ни версии, ни подписи. Ставить в зависимость от него скачивание
     * музыки нельзя. Поэтому адрес приходит ИЗВНЕ (настройка/remote config), а
     * целостность проверяется [verifySha256].
     */
    suspend fun installNativesFrom(
        context: Context,
        archiveUrl: String,
        expectedSha256: String?,
        fetch: suspend (String, File) -> Boolean,
    ): Boolean {
        val dir = nativeDir(context)
        if (!dir.exists() && !dir.mkdirs()) return false
        val archive = File(dir, "libs.jar")
        if (archive.exists()) archive.delete()
        if (!fetch(archiveUrl, archive)) return false
        // Целостность ДО распаковки: битый zip иначе оставит половину .so и
        // проба решит, что FFmpeg «почти есть».
        if (expectedSha256 != null && !verifySha256(archive, expectedSha256)) {
            archive.delete()
            return false
        }
        // Старые .so удаляем — иначе смесь версий даёт неотлаживаемые падения
        // в JNI (мод делает то же самое перед распаковкой).
        dir.listFiles()?.forEach { if (it.isFile && it.name.endsWith(".so")) it.delete() }
        val ok = unpackZip(archive, dir) && nativesLookComplete(dir)
        archive.delete()
        if (ok) invalidate()
        return ok
    }

    /** Первичный дешёвый фильтр целостности (порог из мода). */
    fun nativesLookComplete(dir: File): Boolean {
        if (!dir.isDirectory) return false
        var count = 0
        var total = 0L
        dir.listFiles()?.forEach {
            if (it.isFile && it.name.endsWith(".so")) { count++; total += it.length() }
        }
        return count >= MIN_SO_COUNT && total >= MIN_SO_TOTAL_BYTES
    }

    fun verifySha256(file: File, expectedHex: String): Boolean {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        val actual = md.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(expectedHex.trim(), ignoreCase = true)
    }

    /**
     * Распаковка только файлов, только в целевой каталог.
     * Проверка на `..` в имени обязательна: архив внешний, а zip path traversal
     * позволил бы им писать за пределы filesDir.
     */
    private fun unpackZip(archive: File, destDir: File): Boolean {
        return try {
            ZipInputStream(archive.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name.substringAfterLast('/')
                    if (entry.isDirectory || name.isBlank() || !name.endsWith(".so")) {
                        zip.closeEntry(); continue
                    }
                    val out = File(destDir, name)
                    if (!out.canonicalPath.startsWith(destDir.canonicalPath)) {
                        zip.closeEntry(); continue
                    }
                    out.outputStream().buffered().use { os -> zip.copyTo(os) }
                    zip.closeEntry()
                }
            }
            true
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "распаковка libs.jar сорвалась: ${t.message}")
            false
        }
    }
}
