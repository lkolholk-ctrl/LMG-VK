package com.lmg.vk.debug

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ProfilingManager
import android.os.ProfilingResult
import android.os.SystemClock
import android.os.Trace
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.core.os.BufferFillPolicy
import androidx.core.os.SystemTraceRequestBuilder
import com.lmg.vk.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

internal object AppPerformanceCapture {
    data class State(
        val initialized: Boolean = false,
        val armed: Boolean = false,
        val pending: Boolean = false,
        val exporting: Boolean = false,
        val capture: File? = null,
        val message: String = "Проверяем сохранённую запись…",
    )

    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()
    private val initialized = AtomicBoolean()
    private val worker by lazy {
        Executors.newSingleThreadExecutor { task ->
            Thread({
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
                task.run()
            }, "PerformanceCapture")
        }
    }
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private var pendingTag: String? = null
    private var requestDetails = ""
    @Volatile private var traceUntilMs = 0L
    private val traceSequence = AtomicInteger()
    private const val PREFS = "performance_capture"
    private const val DURATION_MS = 60_000

    fun initialize(context: Context) {
        if (!BuildConfig.DEBUG || !initialized.compareAndSet(false, true)) return
        val app = context.applicationContext
        worker.execute {
            try {
                val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val saved = files(app).latest()
                mutableState.value = State(initialized = true, capture = saved,
                    message = if (saved == null) "Запишите первую минуту после запуска приложения."
                    else "Предыдущая запись сохранена и готова к отправке.")
                if (Build.VERSION.SDK_INT < 35) {
                    mutableState.value = mutableState.value.copy(message = "Системная запись доступна с Android 15.")
                    return@execute
                }
                val armed = prefs.getBoolean("armed", false)
                val pending = prefs.getString("pending", null)
                if (!armed && pending == null) return@execute
                if (armed) check(prefs.edit().putBoolean("armed", false).commit())
                pendingTag = pending
                if (pending != null) {
                    requestDetails = prefs.getString("details", "").orEmpty()
                    mutableState.value = mutableState.value.copy(pending = true,
                        message = "Ожидаем результат предыдущей записи от Android…")
                }
                Platform.register(app)
                if (armed && pending == null) {
                    start(app)
                } else if (pending != null) {
                    scheduleTimeout(app, pending)
                }
            } catch (error: Exception) {
                fail(app, "Не удалось подготовить запись: ${error.javaClass.simpleName}: ${error.message}")
            }
        }
    }

    fun setArmed(context: Context, armed: Boolean) {
        if (!BuildConfig.DEBUG || Build.VERSION.SDK_INT < 35) return
        val app = context.applicationContext
        worker.execute {
            if (!mutableState.value.initialized || pendingTag != null) return@execute
            try {
                if (armed) Platform.requireService(app)
                check(app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("armed", armed).commit())
                mutableState.value = mutableState.value.copy(armed = armed, message = if (armed)
                    "Готово. Полностью остановите LMG VK в настройках Android и откройте заново. Запись начнётся один раз."
                    else "Запись при следующем запуске отменена.")
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(message =
                    "Не удалось настроить запись: ${error.javaClass.simpleName}: ${error.message}")
            }
        }
    }

    @RequiresApi(35)
    private fun start(app: Context) {
        val tag = "lmg-start-${System.currentTimeMillis()}"
        pendingTag = tag
        requestDetails = """
            LMG VK ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})
            device=${Build.MANUFACTURER} ${Build.MODEL} sdk=${Build.VERSION.SDK_INT}
            tag=$tag
            requestWallMs=${System.currentTimeMillis()}
            requestElapsedMs=${SystemClock.elapsedRealtime()}
            requestedDurationMs=$DURATION_MS bufferKb=32768 fillPolicy=DISCARD
            Capture requested after Application.onCreate; platform start is asynchronous.
            The trace is filtered by Android to this application's process.
        """.trimIndent()
        check(app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("pending", tag).putString("details", requestDetails).commit())
        mutableState.value = mutableState.value.copy(pending = true, armed = false,
            message = "Запись запрошена на 60 секунд. Пользуйтесь вкладками, музыкой и текстом. Затем дождитесь готового файла здесь.")
        DebugLog.add("PERFORMANCE_CAPTURE request tag=$tag durationMs=$DURATION_MS")
        traceUntilMs = SystemClock.elapsedRealtime() + DURATION_MS + 5_000L
        Platform.request(app, tag)
        scheduleTimeout(app, tag)
    }

    private fun scheduleTimeout(app: Context, tag: String) {
        main.postDelayed({
            worker.execute {
                if (pendingTag == tag) fail(app,
                    "Android не вернул запись за 5 минут. Если результат придёт позже, он будет сохранён.")
            }
        }, 300_000)
    }

    private fun fail(app: Context, message: String) {
        pendingTag = null
        traceUntilMs = 0L
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("pending").apply()
        mutableState.value = mutableState.value.copy(initialized = true, pending = false, message = message)
        DebugLog.add("PERFORMANCE_CAPTURE error $message")
    }

    @RequiresApi(35)
    private fun onResult(app: Context, result: ProfilingResult) {
        val tag = result.tag ?: return
        if (!tag.matches(Regex("lmg-start-[0-9]{13}"))) return
        if (pendingTag != null && pendingTag != tag) return
        if (result.errorCode != ProfilingResult.ERROR_NONE) {
            val reason = when (result.errorCode) {
                ProfilingResult.ERROR_FAILED_RATE_LIMIT_PROCESS,
                ProfilingResult.ERROR_FAILED_RATE_LIMIT_SYSTEM -> "Android временно ограничил частоту записей. Повторите позже."
                ProfilingResult.ERROR_FAILED_PROFILING_IN_PROGRESS -> "В Android уже выполняется другая запись."
                ProfilingResult.ERROR_FAILED_NO_DISK_SPACE -> "Недостаточно свободного места для записи."
                else -> "Android не смог записать трассировку."
            }
            fail(app, "$reason Код ${result.errorCode}: ${result.errorMessage.orEmpty()}")
            return
        }
        try {
            val source = File(checkNotNull(result.resultFilePath))
            val details = requestDetails + "\nresultTag=$tag\nresultWallMs=${System.currentTimeMillis()}\n"
            val capture = files(app).save(tag, source, details, DebugLog.snapshot().joinToString("\n"))
            pendingTag = null
            traceUntilMs = 0L
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("pending").apply()
            mutableState.value = mutableState.value.copy(pending = false, capture = capture,
                message = "Запись готова. Нажмите «Отправить запись» и выберите, куда отправить файл.")
            DebugLog.add("PERFORMANCE_CAPTURE ready tag=$tag bytes=${source.length()}")
        } catch (error: Exception) {
            fail(app, "Не удалось сохранить запись: ${error.javaClass.simpleName}: ${error.message}")
        }
    }

    fun share(context: Context) {
        val app = context.applicationContext
        worker.execute {
            val capture = mutableState.value.capture ?: return@execute
            if (mutableState.value.exporting) return@execute
            mutableState.value = mutableState.value.copy(exporting = true)
            try {
                val archive = files(app).export(capture)
                val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", archive)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "LMG VK — запись производительности")
                    clipData = ClipData.newRawUri("LMG performance", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                main.post {
                    try {
                        app.startActivity(Intent.createChooser(intent, "Отправить запись")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (error: Exception) {
                        Toast.makeText(app, "Не удалось открыть отправку: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(message = "Не удалось подготовить файл: ${error.message}")
            } finally {
                mutableState.value = mutableState.value.copy(exporting = false)
            }
        }
    }

    private fun files(app: Context) = PerformanceCaptureFiles(app.filesDir, app.cacheDir)

    private fun tracing() = traceUntilMs != 0L && SystemClock.elapsedRealtime() < traceUntilMs && Trace.isEnabled()

    fun beginAsync(name: String): AutoCloseable? {
        if (!tracing()) return null
        val label = "LMG/$name".take(120)
        val cookie = traceSequence.incrementAndGet()
        Trace.beginAsyncSection(label, cookie)
        return AutoCloseable { Trace.endAsyncSection(label, cookie) }
    }

    fun mark(name: String) {
        if (!tracing()) return
        Trace.beginSection("LMG/$name".take(120))
        Trace.endSection()
    }

    fun <T> measure(name: String, block: () -> T): T {
        if (!tracing()) return block()
        Trace.beginSection("LMG/$name".take(120))
        try {
            return block()
        } finally {
            Trace.endSection()
        }
    }

    @RequiresApi(35)
    private object Platform {
        private var registered = false

        fun requireService(app: Context): ProfilingManager =
            checkNotNull(app.getSystemService(ProfilingManager::class.java)) { "Служба записи недоступна в этой прошивке" }

        fun register(app: Context) {
            if (registered) return
            requireService(app).registerForAllProfilingResults(worker, Consumer { onResult(app, it) })
            registered = true
        }

        fun request(app: Context, tag: String) {
            val request = SystemTraceRequestBuilder()
                .setTag(tag)
                .setDurationMs(DURATION_MS)
                .setBufferSizeKb(32768)
                .setBufferFillPolicy(BufferFillPolicy.DISCARD)
                .build()
            requireService(app).requestProfiling(request.profilingType, request.params, request.tag,
                request.cancellationSignal, null, null)
        }
    }
}
