package com.lmg.vk.playback

import android.content.Context
import android.media.audiofx.AudioEffect
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Менеджер цепочки эффектов одного плеера.
 * Восстановлено из `defpackage.C6572e` (+ хост `C9556e`).
 *
 * В PlaybackService создаётся ДВА таких менеджера (f36752e[0], [1]) —
 * по одному на каждый ExoPlayer-инстанс кроссфейда. При смене audioSessionId
 * эффекты пересоздаются; конфиг применяется реактивно (StateFlow).
 */
class AudioEffectEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val configFlow: StateFlow<LmgEffectConfig>,
) {
    /** Доступные на устройстве типы эффектов (в оригинале: AbstractC3383e.ad()). */
    private val availableEffectTypes: List<java.util.UUID>
        get() = AudioEffect.queryEffects().map { it.type }

    private val wrappers: List<AudioEffectWrapper> = buildList {
        if (availableEffectTypes.contains(AudioEffect.EFFECT_TYPE_BASS_BOOST)) {
            add(BassBoostEffect())
        }
        if (availableEffectTypes.contains(AudioEffect.EFFECT_TYPE_ENV_REVERB)) {
            add(EnvironmentalReverbEffect())
        }
        if (Build.VERSION.SDK_INT >= 28 &&
            availableEffectTypes.contains(java.util.UUID.fromString(DynamicsProcessingEffect.DYNAMICS_PROCESSING_UUID))
        ) {
            add(DynamicsProcessingEffect())
        }
    }

    private var audioSessionId: Int = -1

    /** Вызывается при появлении новой аудио-сессии плеера (C9556e-флоу). */
    fun onAudioSessionId(sessionId: Int) {
        if (sessionId == audioSessionId) return
        releaseAll()
        audioSessionId = sessionId
        val config = configFlow.value
        wrappers.forEach { it.attach(sessionId, config) }
    }

    /** Реактивное применение нового конфига (пресет сменился). */
    fun applyConfig(config: LmgEffectConfig) {
        wrappers.forEach { it.apply(config) }
    }

    fun releaseAll() {
        wrappers.forEach { it.release() }
        audioSessionId = -1
    }
}
