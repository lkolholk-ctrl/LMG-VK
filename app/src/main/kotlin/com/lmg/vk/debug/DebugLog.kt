package com.lmg.vk.debug

import android.util.Log
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * Легковесный логгер (замена ui/debug-инфраструктуры LMG).
 * Кольцевой буфер последних событий + вывод в logcat.
 */
object DebugLog {
    private const val TAG = "LmgVk"
    // Ёмкость поднята с 500: одна попытка воспроизведения (разрешение URL →
    // сеть → кэш → движок) даёт сотни строк, и при 500 её НАЧАЛО вытеснялось
    // концом — то есть терялось ровно то место, где всё встаёт.
    private const val CAPACITY = 2000

    private val buffer = ArrayBlockingQueue<String>(CAPACITY)

    /**
     * Счётчик изменений буфера. Нужен экрану лога: перечитывать snapshot() по
     * таймеру дёшево только если можно СНАЧАЛА понять, что ничего не менялось
     * (иначе на каждом тике копируется список из 2000 строк). Atomic — потому
     * что add() зовут из разных потоков (сеть, движок, main).
     */
    private val revisionCounter = AtomicLong(0L)

    @JvmStatic
    fun add(message: String) {
        val line = "${System.currentTimeMillis() % 100000} $message"
        if (!buffer.offer(line)) {
            buffer.poll(); buffer.offer(line)
        }
        revisionCounter.incrementAndGet()
        Log.d(TAG, message)
    }

    fun caller(): String {
        val st = Throwable().stackTrace
        return st.getOrNull(3)?.let { "${it.methodName}:${it.lineNumber}" } ?: "?"
    }

    fun snapshot(): List<String> = buffer.toList()

    fun clear() {
        buffer.clear()
        // Инкремент и на очистке: иначе экран не заметил бы, что список опустел.
        revisionCounter.incrementAndGet()
    }

    /** Версия содержимого буфера — меняется на каждом add()/clear(). */
    fun revision(): Long = revisionCounter.get()
}
