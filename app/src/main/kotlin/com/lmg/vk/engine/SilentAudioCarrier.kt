package com.lmg.vk.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import com.lmg.vk.debug.DebugLog

/**
 * SilentAudioCarrier — легковесный статический AudioTrack тишины (MODE_STATIC).
 *
 * Назначение:
 * На Honor MagicOS / Huawei EMUI агрессивный фоновый менеджер питания (IAware / PowerGenie)
 * отслеживает события AudioTrack.pause() / stop(). Когда при завершении кроссфейда отработавший
 * sink останавливается, система взводит 35-секундный таймер и замораживает процесс через
 * cgroup.freeze = 1, несмотря на то, что второй AudioTrack продолжает играть.
 *
 * SilentAudioCarrier непрерывно крутит в аппаратном буфере 100 мс абсолютной тишины
 * в режиме PLAYSTATE_PLAYING, пока пользователь слушает музыку.
 * Для audioserver и PowerGenie приложение com.lmg.vk непрерывно активно.
 * Нагрузка на CPU / SoC — 0%.
 */
class SilentAudioCarrier {

    private var track: AudioTrack? = null
    @Volatile private var isRunning = false

    @Synchronized
    fun start() {
        if (isRunning) return
        try {
            val sampleRate = 48000
            val channelConfig = AudioFormat.CHANNEL_OUT_STEREO
            val encoding = AudioFormat.ENCODING_PCM_16BIT
            val frameCount = sampleRate / 10 // 100 ms = 4800 фреймов
            val bufferSizeBytes = frameCount * 4 // stereo 16-bit = 4 байта на фрейм (19200 байт)

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfig)
                .setEncoding(encoding)
                .build()

            val audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferSizeBytes)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    android.media.AudioManager.STREAM_MUSIC,
                    sampleRate,
                    channelConfig,
                    encoding,
                    bufferSizeBytes,
                    AudioTrack.MODE_STATIC
                )
            }

            val silence = ByteArray(bufferSizeBytes)
            audioTrack.write(silence, 0, silence.size)
            audioTrack.setLoopPoints(0, frameCount, -1)
            audioTrack.play()

            track = audioTrack
            isRunning = true
            DebugLog.add("CARRIER: Silent audio carrier started (session=${audioTrack.audioSessionId})")
        } catch (e: Throwable) {
            DebugLog.add("CARRIER: Failed to start silent carrier: ${e.message}")
        }
    }

    @Synchronized
    fun stop() {
        if (!isRunning) return
        isRunning = false
        val currentTrack = track
        track = null
        if (currentTrack != null) {
            try {
                if (currentTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    currentTrack.pause()
                }
                currentTrack.flush()
                currentTrack.release()
                DebugLog.add("CARRIER: Silent audio carrier stopped")
            } catch (e: Throwable) {
                DebugLog.add("CARRIER: Error stopping carrier: ${e.message}")
            }
        }
    }

    @Synchronized
    fun pause() {
        if (!isRunning) return
        try {
            track?.pause()
            DebugLog.add("CARRIER: Silent audio carrier paused")
        } catch (e: Throwable) {
            DebugLog.add("CARRIER: Error pausing carrier: ${e.message}")
        }
    }

    @Synchronized
    fun resume() {
        if (!isRunning) {
            start()
            return
        }
        try {
            if (track?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                track?.play()
                DebugLog.add("CARRIER: Silent audio carrier resumed")
            }
        } catch (e: Throwable) {
            DebugLog.add("CARRIER: Error resuming carrier: ${e.message}")
        }
    }
}
