package com.lmg.vk.debug

import android.util.Log
import java.util.concurrent.ArrayBlockingQueue

/**
 * Легковесный логгер (замена ui/debug-инфраструктуры LMG).
 * Кольцевой буфер последних событий + вывод в logcat.
 */
object DebugLog {
    private const val TAG = "LmgVk"
    private const val CAPACITY = 500

    private val buffer = ArrayBlockingQueue<String>(CAPACITY)

    @JvmStatic
    fun add(message: String) {
        val line = "${System.currentTimeMillis() % 100000} $message"
        if (!buffer.offer(line)) {
            buffer.poll(); buffer.offer(line)
        }
        Log.d(TAG, message)
    }

    fun caller(): String {
        val st = Throwable().stackTrace
        return st.getOrNull(3)?.let { "${it.methodName}:${it.lineNumber}" } ?: "?"
    }

    fun snapshot(): List<String> = buffer.toList()
    fun clear() = buffer.clear()
}
