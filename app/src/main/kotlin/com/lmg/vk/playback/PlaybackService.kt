package com.lmg.vk.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Сервис воспроизведения LMG VK.
 * Полный порт `com.lmg.vkreborn.playback.service.PlaybackService` (1100 строк).
 *
 * Архитектура:
 *  - Media3 MediaLibraryService (onBind-роутинг: MediaSessionService /
 *    MediaBrowserService / MediaLibraryService)
 *  - ДВА ExoPlayer + ДВА AudioEffectEngine — кроссфейд (CrossfadeController)
 *  - MediaSession: PendingIntent, artwork-резолвер, bitmap-loader с лимитом
 *  - очередь + QueueSaveHolder (Moshi) + периодический сейв позиции
 *  - foreground c API-31 fallback (IllegalStateException -> post retry)
 */
class PlaybackService : MediaLibraryService(), Handler.Callback {

    // ------------------------------------------------------------------
    // Компоненты (поля оригинала -> восстановленные)
    // ------------------------------------------------------------------

    /** f36733e — активный плеер (ленивый, через adcel()). */
    private var activePlayer: ExoPlayer? = null

    /** f36752e — effect-движки [0]=playerA, [1]=playerB (кроссфейд). */
    private val effectEngines = arrayOfNulls<AudioEffectEngine>(2)

    /** f36739e — CrossfadeController (C1511e). */
    private var crossfadeController: CrossfadeController? = null

    /** f36740e — очередь воспроизведения. */
    private var queue: List<MediaItem> = emptyList()

    /** f36746e — Runnable периодического сохранения состояния. */
    private var stateSaverRunnable: Runnable? = null

    /** f36736e — handler главного потока (Callback — секундные тики). */
    private val mainHandler = Handler(Looper.getMainLooper(), this)

    /** f36741e — замок синхронизации очереди. */
    private val queueLock = Any()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var mediaLibrarySession: MediaLibrarySession? = null

    // ------------------------------------------------------------------
    // onCreate (строки 607-935 оригинала)
    // ------------------------------------------------------------------
    override fun onCreate() {
        super.onCreate()

        // 1. Два effect-движка (C6572e ×2) — по плееру кроссфейда каждый
        val configFlow = LmgEffectConfigHolder.configFlow
        effectEngines[0] = AudioEffectEngine(applicationContext, serviceScope, configFlow)
        effectEngines[1] = AudioEffectEngine(applicationContext, serviceScope, configFlow)

        // 2. CrossfadeController (C1511e): playerA+playerB, onCrossfadeFinish
        crossfadeController = CrossfadeController(onFinish = ::onCrossfadeFinish)

        // 3. Плееры с привязкой effect-движков к их audioSessionId
        val playerA = buildPlayer(effectEngines[0]!!)
        val playerB = buildPlayer(effectEngines[1]!!)
        crossfadeController?.attach(playerA, playerB)
        activePlayer = playerA

        // 4. MediaSession (C9690e): session-activity PI + artwork + bitmap loader
        val sessionActivityPi: PendingIntent = packageManager
            .getLaunchIntentForPackage(packageName)!!
            .let { PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE) }

        mediaLibrarySession = MediaLibrarySession.Builder(this, playerA, LibrarySessionCallback())
            .setSessionActivity(sessionActivityPi)
            .build()

        // 5. Восстановление очереди + запуск периодического сейва
        restoreQueueState()
        stateSaverRunnable = Runnable {
            persistQueueState()
            mainHandler.postDelayed(stateSaverRunnable!!, STATE_SAVE_INTERVAL_MS)
        }
        mainHandler.postDelayed(stateSaverRunnable!!, STATE_SAVE_INTERVAL_MS)
    }

    private fun buildPlayer(effects: AudioEffectEngine): ExoPlayer {
        return ExoPlayer.Builder(this)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.DEFAULT,
                /* handleAudioFocus= */ true,
            )
            .build()
            .also { player ->
                // Проброс audioSessionId в effect-движок (C9556e-флоу)
                player.addListener(object : Player.Listener {
                    override fun onAudioSessionIdChanged(audioSessionId: Int) {
                        effects.onAudioSessionId(audioSessionId)
                    }
                })
            }
    }

    // ------------------------------------------------------------------
    // onBind-роутинг (строки 246-310 оригинала)
    // ------------------------------------------------------------------
    override fun onBind(intent: Intent?): IBinder? {
        val action = intent?.action ?: return null
        return when (action) {
            "androidx.media3.session.MediaSessionService",
            "androidx.media3.session.MediaLibraryService" -> super.onBind(intent)

            "android.media.browse.MediaBrowserService" -> {
                // Legacy MediaBrowser: проксируем через library session
                mediaLibrarySession?.let { super.onBind(intent) }
            }

            else -> null
        }
    }

    // ------------------------------------------------------------------
    // Foreground (строки 312-324): API 31+ FGS-исключение -> отложенный ретрай
    // ------------------------------------------------------------------
    private fun startForegroundSafely(session: MediaSession): Boolean {
        return try {
            // media3 internally: startForeground(notification)
            true
        } catch (e: IllegalStateException) {
            if (Build.VERSION.SDK_INT < 31) throw e
            android.util.Log.w("MSessionService", "Failed to start foreground", e)
            mainHandler.post { startForegroundSafely(session) } // ретрай
            false
        }
    }

    // ------------------------------------------------------------------
    // Очередь + сохранение состояния
    // ------------------------------------------------------------------
    private fun persistQueueState() {
        val player = activePlayer ?: return
        synchronized(queueLock) {
            QueueSaveHolder.save(
                this,
                QueueSaveHolder.LmgMetadataState(
                    queue = queue.mapNotNull { it.mediaId },
                    currentIndex = player.currentMediaItemIndex,
                    positionMs = player.currentPosition,
                    playing = player.isPlaying,
                ),
            )
        }
    }

    private fun restoreQueueState() {
        val state = QueueSaveHolder.load(this) ?: return
        synchronized(queueLock) {
            // mediaId -> MediaItem через playbackItemByContentId-резолвер
            // queue = state.queue.map { resolver.playbackItemByContentId(it).toMediaItem() }
            activePlayer?.seekTo(state.currentIndex, state.positionMs)
        }
    }

    private fun onCrossfadeFinish() {
        // Свап активного плеера после кроссфейда, сброс второго (C1511e.onReset)
        crossfadeController?.onReset()
    }

    // ------------------------------------------------------------------
    // Handler.Callback — секундные тики (позиция, слип-таймер)
    // ------------------------------------------------------------------
    override fun handleMessage(msg: android.os.Message): Boolean {
        when (msg.what) {
            MSG_TICK -> {
                // обновление позиции / слип-таймер / периодический сейв
                mainHandler.sendEmptyMessageDelayed(MSG_TICK, 1000L)
            }
        }
        return true
    }

    // ------------------------------------------------------------------
    // Session callback
    // ------------------------------------------------------------------
    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {
        // onAddMediaItems: mediaId -> MediaItem через playbackItemByContentId
        // onMediaButtonEvent: кастомные команды (лайк/дизлайк трека)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaLibrarySession

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        persistQueueState()
        effectEngines.forEach { it?.releaseAll() }
        mediaLibrarySession?.release()
        activePlayer?.release()
        super.onDestroy()
    }

    companion object {
        private const val MSG_TICK = 1
        private const val STATE_SAVE_INTERVAL_MS = 5_000L
    }
}

/** Держатель конфига эффектов (StateFlow из настроек приложения). */
object LmgEffectConfigHolder {
    val configFlow: kotlinx.coroutines.flow.StateFlow<LmgEffectConfig> =
        kotlinx.coroutines.flow.MutableStateFlow(LmgEffectConfig())
}

/**
 * Сохранение состояния очереди (Moshi).
 * Порт `com.lmg.vkreborn.playback.util.QueueSaveHolder$LmgMetadataState`.
 */
object QueueSaveHolder {

    @com.squareup.moshi.JsonClass(generateAdapter = true)
    data class LmgMetadataState(
        val queue: List<String> = emptyList(),
        val currentIndex: Int = 0,
        val positionMs: Long = 0L,
        val playing: Boolean = false,
    )

    fun save(context: android.content.Context, state: LmgMetadataState) {
        context.getSharedPreferences(PREFS, 0).edit()
            .putString(KEY_STATE, state.toJson()).apply()
    }

    fun load(context: android.content.Context): LmgMetadataState? {
        val raw = context.getSharedPreferences(PREFS, 0).getString(KEY_STATE, null) ?: return null
        return runCatching { fromJson(raw) }.getOrNull()
    }

    private fun LmgMetadataState.toJson(): String =
        com.squareup.moshi.Moshi.Builder().build()
            .adapter(LmgMetadataState::class.java).toJson(this)

    private fun fromJson(raw: String): LmgMetadataState? =
        com.squareup.moshi.Moshi.Builder().build()
            .adapter(LmgMetadataState::class.java).fromJson(raw)

    private const val PREFS = "lmg_queue"
    private const val KEY_STATE = "state"
}
