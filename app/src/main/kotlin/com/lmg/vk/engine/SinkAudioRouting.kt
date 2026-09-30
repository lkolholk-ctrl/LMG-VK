package com.lmg.vk.engine

/** One group per service player, never per renderer index. Registration/selection are
 * control-plane operations; meter publication uses preallocated, independently owned slots. */
class SinkAudioRouting {
    class Selection internal constructor(private val group: SinkAudioRouting, val windowUid: Any, val mediaId: String) {
        internal fun matchesUnique(state: SinkAudioState): Boolean = group.matchesUnique(this,state)
    }
    private val slots = arrayOfNulls<SinkAudioState>(2)
    @Volatile private var count = 0
    @Volatile var selection: Selection? = null; private set

    @Synchronized fun newSink(): SinkAudioState {
        check(count < slots.size) { "Only the two internal audio sinks are supported" }
        val state = SinkAudioState()
        slots[count] = state
        count += 1
        return state
    }
    @Synchronized fun select(windowUid: Any?, mediaId: String?) {
        if (windowUid == null || mediaId.isNullOrEmpty()) { selection = null; return }
        val old = selection
        if (old?.windowUid != windowUid || old.mediaId != mediaId)
            selection = Selection(this, windowUid, mediaId)
    }
    /** UI reads one atomically published three-band packet, never interleaved writes
     * from two processors. A real native mixer output takes precedence over raw B. */
    fun levels(): Long {
        val selected = selection ?: return 0L
        val n = count
        var i = 0
        var result = -1L
        while (i < n) {
            val packet = slots[i++]!!.mixedLevels()
            if (packet >= 0) { if (result >= 0) return 0L; result = packet }
        }
        if (result >= 0) return result
        i = 0
        while (i < n) {
            val packet = slots[i++]!!.matchingLevels(selected)
            if (packet >= 0) { if (result >= 0) return 0L; result = packet }
        }
        return if (result < 0) 0L else result
    }
    private fun matchesUnique(selected: Selection, expected: SinkAudioState): Boolean {
        val n=count
        var found: SinkAudioState?=null
        var i=0
        while(i<n) {
            val state=slots[i++]!!
            if(state.matches(selected)) { if(found!=null)return false;found=state }
        }
        return found === expected
    }

}

/** Written by ONE playback owner. UI observes only volatile immutable references,
 * packed levels and the even version. No callback registration or per-buffer objects. */
class SinkAudioState internal constructor() {
    @Volatile private var version = 0L
    private var token: Any? = null
    private var windowUid: Any? = null
    private var mediaId: String? = null
    @Volatile var levels = 0L; private set
    @Volatile var mixedOutput = false
    val streamVersion: Long get() = version

    fun bind(outputToken: Any?, uid: Any?, id: String?) {
        if (token === outputToken && windowUid == uid && mediaId == id) return
        version += 1 // odd: readers reject the transition rather than spinning
        token = outputToken; windowUid = uid; mediaId = id
        levels = 0; mixedOutput = false
        version += 1
    }
    fun matches(selected: SinkAudioRouting.Selection?): Boolean {
        if (selected == null) return false
        val before = version
        if (before and 1L != 0L) return false
        val match = windowUid == selected.windowUid && mediaId == selected.mediaId && token != null
        return match && before == version
    }
    internal fun matchingLevels(selected: SinkAudioRouting.Selection): Long {
        val before = version
        val packet = levels
        val matched = token != null && windowUid == selected.windowUid && mediaId == selected.mediaId
        return if (before and 1L == 0L && matched && version == before) packet else -1L
    }
    internal fun mixedLevels(): Long {
        val before = version
        val mixed = mixedOutput
        val packet = levels
        return if (before and 1L == 0L && mixed && version == before) packet else -1L
    }
    fun resetLevels() { levels = 0L }
    fun publish(low: Float, mid: Float, high: Float) {
        levels = pack(low) or (pack(mid) shl 21) or (pack(high) shl 42)
    }
    companion object {
        private const val SCALE = (1 shl 21) - 1
        private fun pack(value: Float): Long =
            if (!value.isFinite()) 0 else (value.coerceIn(0f, 1f) * SCALE).toLong()
        fun band(packet: Long, index: Int): Float =
            ((packet ushr (index * 21)) and SCALE.toLong()).toFloat() / SCALE
    }
}
