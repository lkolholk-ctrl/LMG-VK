package com.lmg.vk.playback

import android.media.AudioTrack
import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.EnvironmentalReverb
import android.os.Build

/**
 * Обёртка над одним AudioEffect.
 * Восстановлено из `defpackage.InterfaceC5387e`.
 */
interface AudioEffectWrapper {
    /** Создать эффект на аудио-сессии и применить конфиг. */
    fun attach(audioSessionId: Int, config: LmgEffectConfig)

    /** Обновить конфиг на живом эффекте. Возвращает успех. */
    fun apply(config: LmgEffectConfig): Boolean

    fun release()
}

/**
 * BassBoost + EnvironmentalReverb (варианты по индексу).
 * Восстановлено из `defpackage.C10882e` (priority 100).
 */
class BassBoostEffect : AudioEffectWrapper {
    private var effect: BassBoost? = null

    override fun attach(audioSessionId: Int, config: LmgEffectConfig) {
        effect = BassBoost(100, audioSessionId)
        apply(config)
    }

    override fun apply(config: LmgEffectConfig): Boolean {
        val fx = effect ?: return false
        val cfg = config.bassBoost
        fx.enabled = cfg.enabled
        // strength: 0..100 -> 0..1000 (×10), coerce 0..1000
        fx.setStrength((cfg.strength * 10f).toInt().coerceIn(0, 1000).toShort())
        return true
    }

    override fun release() {
        effect?.release(); effect = null
    }
}

class EnvironmentalReverbEffect : AudioEffectWrapper {
    private var effect: EnvironmentalReverb? = null

    override fun attach(audioSessionId: Int, config: LmgEffectConfig) {
        effect = EnvironmentalReverb(100, audioSessionId)
        apply(config)
    }

    override fun apply(config: LmgEffectConfig): Boolean {
        val fx = effect ?: return false
        fx.enabled = config.reverb.enabled
        return true
    }

    override fun release() {
        effect?.release(); effect = null
    }
}

/**
 * Полный DSP-конвейер (EQ + MBC + Limiter + InputGain) — «эквалайзер» LMG VK.
 * Восстановлено из `defpackage.C17203e` (API 28+, EFFECT_TYPE_DYNAMICS_PROCESSING).
 *
 * Config: stereo, preEq=postEq=одни и те же полосы, MBC (кастом или 3-полосный
 * 125/6000/20000), Limiter; preferredFrameDuration = 4096000/sampleRate.
 */
class DynamicsProcessingEffect : AudioEffectWrapper {
    private var effect: DynamicsProcessing? = null

    override fun attach(audioSessionId: Int, config: LmgEffectConfig) {
        if (Build.VERSION.SDK_INT < 28) return

        val eq = buildEq(config)
        val mbc = buildMbc(config)
        val limiter = buildLimiter(config)

        val dpConfig = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, // 0
            2,                                // stereo
            true, eq.bandCount,
            true, if (config.mbc.customBands != null) config.mbc.customBands!!.size else 3,
            true, eq.bandCount,
            true,
        )
            .setPreferredFrameDuration(
                4096000f / AudioTrack.getNativeOutputSampleRate(android.media.AudioManager.STREAM_MUSIC)
            )
            .setPreEqAllChannelsTo(eq)
            .setPostEqAllChannelsTo(eq)
            .setMbcAllChannelsTo(mbc)
            .setLimiterAllChannelsTo(limiter)
            .apply { applyInputGain(config) }
            .build()

        effect = DynamicsProcessing(100, audioSessionId, dpConfig).apply { enabled = true }
    }

    private fun DynamicsProcessing.Config.Builder.applyInputGain(config: LmgEffectConfig) {
        val gain = config.inputGain
        when {
            !gain.enabled -> setInputGainAllChannelsTo(0f)
            gain.linked -> setInputGainAllChannelsTo(gain.linkedValueDb)
            else -> {
                setInputGainByChannelIndex(0, gain.leftDb)
                setInputGainByChannelIndex(1, gain.rightDb)
            }
        }
    }

    private fun buildEq(config: LmgEffectConfig): DynamicsProcessing.Eq {
        val bands = config.equalizer.bands
        val eq = DynamicsProcessing.Eq(true, true, bands.size)
        bands.forEachIndexed { i, band ->
            eq.setBand(i, DynamicsProcessing.Eq.Band(true, band.cutoffHz, band.gainDb))
        }
        return eq
    }

    private fun buildMbc(config: LmgEffectConfig): DynamicsProcessing.Mbc {
        val mbc = config.mbc
        val custom = mbc.customBands
        return if (custom != null) {
            DynamicsProcessing.Mbc(true, mbc.enabled, custom.size).apply {
                custom.forEachIndexed { i, band ->
                    setBand(i, DynamicsProcessing.Mbc.Band(
                        true, band.cutoffHz, band.ratio, band.thresholdDb, band.gainDb, band.gainDb))
                }
            }
        } else {
            // 3-полосный режим: 125/6000/20000 Гц, ratio 1.1, гейны из bass/treble
            DynamicsProcessing.Mbc(true, mbc.enabled, 3).apply {
                setBand(0, DynamicsProcessing.Mbc.Band(true, 125f, 1.1f, 0f, mbc.bassGainDb(), mbc.bassGainDb()))
                setBand(1, DynamicsProcessing.Mbc.Band(true, 6000f, 1.1f, 0f, 0f, 0f))
                setBand(2, DynamicsProcessing.Mbc.Band(true, 20000f, 1.1f, 0f, mbc.trebleGainDb(), mbc.trebleGainDb()))
            }
        }
    }

    private fun buildLimiter(config: LmgEffectConfig): DynamicsProcessing.Limiter {
        val l = config.limiter
        return DynamicsProcessing.Limiter(
            l.enabled, true, l.linkGroup.toInt(),
            l.attackMs, l.releaseMs, l.ratio, l.thresholdDb, l.postGainDb,
        )
    }

    override fun apply(config: LmgEffectConfig): Boolean {
        // В оригинале: сверка bandCount у живого MBC и пересоздание конфига
        val fx = effect ?: return false
        release()
        // пересоздание происходит через attach с той же сессией на стороне менеджера
        return fx != null
    }

    override fun release() {
        effect?.release(); effect = null
    }

    companion object {
        /** EFFECT_TYPE_DYNAMICS_PROCESSING UUID (из оригинала). */
        const val DYNAMICS_PROCESSING_UUID = "7261676f-6d75-7369-6364-28e2fd3ac39e"
    }
}
