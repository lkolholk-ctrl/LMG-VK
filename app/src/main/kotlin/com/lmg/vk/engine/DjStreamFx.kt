package com.lmg.vk.engine

/**
 * Состояние необязательных DJ-эффектов для конкретного вхождения трека в очередь.
 * Пишет [com.lmg.vk.automix.DJEffectsEngine.crossfadeWithEffects],
 * читает [DjFxAudioProcessor] в аудио-цепочке подтверждённого уходящего потока.
 */
object DjStreamFx {
    const val MODE_NONE = 0
    const val MODE_SWEEP = 4       // FILTER_SWEEP: LP-муффл уходящего
    const val MODE_ECHO = 5        // ECHO_OUT: делэй-повторы уходящего
    const val MODE_ECHO_TAIL = 6   // дозвучка хвоста: dry замьючен, звенят повторы

    internal data class Command(
        val mode: Int = MODE_NONE,
        val progress: Float = 0f,
        val outVolume: Float = 1f,
        val generation: Long = 0,
        val target: SinkAudioRouting.Selection? = null,
    ) {
        fun matches(sink: SinkAudioState?): Boolean =
            sink != null && !sink.mixedOutput && target?.matchesUnique(sink) == true
    }
    // Control-plane writers allocate one immutable command. Each PCM block reads
    // the reference once: no mixed generation/target/progress from separate atomics.
    @Volatile private var command = Command()
    internal fun snapshot(): Command = command
    val mode: Int get() = command.mode
    val progress: Float get() = command.progress
    val generation: Long get() = command.generation
    val outVolume: Float get() = command.outVolume
    fun isOutgoing(sink: SinkAudioState?): Boolean = command.matches(sink)

    @Synchronized fun begin(transitionType: Int) {
        command = Command(
            mode = when (transitionType) { 4 -> MODE_SWEEP; 5 -> MODE_ECHO; else -> MODE_NONE },
            generation = Math.addExact(command.generation, 1),
            target = AudioReactor.selectedSource(),
        )
    }
    @Synchronized fun update(p: Float, outVol: Float) {
        require(p.isFinite() && outVol.isFinite())
        command = command.copy(progress = p.coerceIn(0f, 1f), outVolume = outVol.coerceIn(0f, 1f))
    }
    @Synchronized fun beginTail() { command = command.copy(mode = MODE_ECHO_TAIL, outVolume = 1f) }
    @Synchronized fun stop() { command = Command(generation = command.generation) }

}
