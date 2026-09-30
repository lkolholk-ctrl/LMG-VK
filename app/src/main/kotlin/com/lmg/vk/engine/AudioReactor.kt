package com.lmg.vk.engine

/**
 * Публикует уровни звука по трём полосам (0..1) для аудио-реактивных
 * эффектов UI (пульсация ауры на «Моей волне» и в плеере).
 *
 * Обновляется из аудио-потока ([BassAudioProcessor]); читается из UI.
 */
object AudioReactor {
    private val routing = java.util.concurrent.atomic.AtomicReference<SinkAudioRouting?>(null)
    internal fun attach(value: SinkAudioRouting) { routing.set(value) }
    internal fun detach(value: SinkAudioRouting) { routing.compareAndSet(value, null) }
    internal fun selectedSource(): SinkAudioRouting.Selection? = routing.get()?.selection
    private fun routed(index: Int, fallback: Float): Float {
        val current = routing.get() ?: return fallback
        return SinkAudioState.band(current.levels(), index)
    }
    @Volatile private var _low = 0f
    @Volatile private var _mid = 0f
    @Volatile private var _high = 0f

    /**
     * Когда уровни читали в последний раз.
     *
     * По нему [hasListeners] решает, считать ли полосы вообще. Счётчик
     * подписчиков был бы точнее, но рассинхрон счётчика убивает пульсацию
     * намертво и молча; отметка времени сама себя чинит — как только кто-то
     * снова прочитает уровень, разбор возобновится со следующего же буфера.
     */
    @Volatile private var lastReadAt = Long.MIN_VALUE

    /** Низкие частоты (бас), 0..1. */
    var low: Float
        get() { lastReadAt = (System.nanoTime() / 1_000_000L); return routed(0, _low) }
        set(value) { _low = value }

    /** Средние частоты, 0..1. */
    var mid: Float
        get() { lastReadAt = (System.nanoTime() / 1_000_000L); return routed(1, _mid) }
        set(value) { _mid = value }

    /** Высокие частоты, 0..1. */
    var high: Float
        get() { lastReadAt = (System.nanoTime() / 1_000_000L); return routed(2, _high) }
        set(value) { _high = value }

    /** Алиас баса для обратной совместимости. */
    val level: Float get() = low

    /**
     * Нужен ли кому-то разбор по полосам прямо сейчас.
     *
     * Раньше посэмпльный цикл с двумя фильтрами крутился всегда — и с закрытым
     * приложением тоже, хотя результат никто не читал.
     */
    val hasListeners: Boolean
        get() {
            val read = lastReadAt
            if (read == Long.MIN_VALUE) return false
            val age = (System.nanoTime() / 1_000_000L) - read
            return age >= 0 && age < IDLE_TIMEOUT_MS
        }

    private const val IDLE_TIMEOUT_MS = 2_000L

    // ── Мост для JUCE-локалки ────────────────────────────────────────────
    // Стриминг кормит уровни из BassAudioProcessor (цепочка ExoPlayer); у
    // локального JUCE-пути этой цепочки нет — дым/пульс обложки были МЕРТВЫ
    // на локальной музыке. Мост нормализует нативную бас-огибающую
    // (automix LP ~110 Гц, уже считается для хаптики) в тот же 0..1
    // адаптивным пиком: быстрый захват вверх, медленный спад (~30с на тиках
    // ~100мс). Кормится из тикера JuceLocalPlayer.
    @Volatile private var jucePeak = 0.05f

    fun feedJuceBass(env: Float) {
        val e = if (env.isFinite() && env > 0f) env else 0f
        jucePeak = if (e > jucePeak) e else (jucePeak * 0.998f).coerceAtLeast(0.02f)
        low = (e / jucePeak).coerceIn(0f, 1f)
    }
}
