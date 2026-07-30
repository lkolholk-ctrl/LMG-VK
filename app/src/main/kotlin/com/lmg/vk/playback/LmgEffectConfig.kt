package com.lmg.vk.playback

/**
 * Конфигурация аудио-эффектов LMG VK.
 * Восстановлено из `defpackage.C6442e` + вложенные конфиги
 * (C6019e, C16801e/C1050e, C5267e/C14056e, C7190e, C16074e).
 *
 * Пресеты поставляются в assets: lmg_eq_presets.json / lmg_eq_prebuilt(_vk).json.
 */
data class LmgEffectConfig(
    val name: String = "custom",
    val bassBoost: BassBoostConfig = BassBoostConfig(),
    val reverb: ReverbConfig = ReverbConfig(),
    val equalizer: EqualizerConfig = EqualizerConfig(),
    val mbc: MbcConfig = MbcConfig(),
    val limiter: LimiterConfig = LimiterConfig(),
    val inputGain: InputGainConfig = InputGainConfig(),
    val extras: Map<String, String> = emptyMap(),
)

/** C6019e: { f12677e: enabled, f12676e: strength 0..100 } */
data class BassBoostConfig(
    val enabled: Boolean = false,
    val strength: Float = 0f,   // 0..100 -> setStrength(short) 0..1000 (×10)
)

/** C6019e (reverb-вариант) — EnvironmentalReverb, strength 0..100. */
data class ReverbConfig(
    val enabled: Boolean = false,
    val roomLevel: Float = 0f,
)

/** C16801e: { f32939e: List<C1050e> } — полосы эквалайзера. */
data class EqualizerConfig(
    val bands: List<EqBandConfig> = emptyList(),
)

/** C1050e: { f3537e: cutoffHz, f3536e: gainDb } */
data class EqBandConfig(
    val cutoffHz: Float,
    val gainDb: Float,
)

/**
 * C5267e: многополосный компрессор.
 *  - customBands == null -> 3-полосный режим 125/6000/20000 Гц, ratio 1.1
 *  - bassGain/trebleGain: 0..100 -> 0..8 дБ (coerceIn(0,100)/100*8)
 */
data class MbcConfig(
    val enabled: Boolean = false,
    val customBands: List<MbcBandConfig>? = null,
    val bassGain: Float = 0f,    // -> band 125 Hz
    val trebleGain: Float = 0f,  // -> band 20000 Hz
) {
    fun bassGainDb(): Float = (bassGain.coerceIn(0f, 100f) / 100f * 8f).coerceIn(0f, 8f)
    fun trebleGainDb(): Float = (trebleGain.coerceIn(0f, 100f) / 100f * 8f).coerceIn(0f, 8f)
}

/** C14056e: (cutoffHz, ratio, thresholdDb, gainDb) */
data class MbcBandConfig(
    val cutoffHz: Float,
    val ratio: Float,
    val thresholdDb: Float,
    val gainDb: Float,
)

/** C7190e — параметры DynamicsProcessing.Limiter. */
data class LimiterConfig(
    val enabled: Boolean = true,
    val linkGroup: Float = 0f,
    val attackMs: Float = 1f,
    val releaseMs: Float = 60f,
    val ratio: Float = 10f,
    val thresholdDb: Float = -1f,
    val postGainDb: Float = 0f,
)

/**
 * C16074e: входной гейн (баланс/усиление).
 *  - !enabled -> 0 дБ на все каналы
 *  - linked   -> вычисляемый linkedValue
 *  - иначе    -> по-канально L/R
 */
data class InputGainConfig(
    val enabled: Boolean = true,
    val linked: Boolean = false,
    val linkedValueDb: Float = 0f,
    val leftDb: Float = 0f,
    val rightDb: Float = 0f,
)
