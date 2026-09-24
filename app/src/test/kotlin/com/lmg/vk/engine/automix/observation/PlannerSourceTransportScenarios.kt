package com.lmg.vk.engine.automix.observation

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Reads actual native snapshots, never invents a positive native response in Kotlin. */
object PlannerSourceTransportScenarios {
    internal fun context() = MusicKitSourceContext.create(
        MusicKitOutgoingCriteria.LateInSong, MusicKitIncomingCriteria.InSong, 3,
        ResolvedPlannerEligibility.ALLOWED,
        MusicKitSourceKnowledge.ABSENT, MusicKitSourceKnowledge.ABSENT,
        MusicKitSourceKnowledge.ABSENT, MusicKitSourceKnowledge.ABSENT,
    )
    @JvmStatic fun main(args: Array<String>) {
        val input = ByteBuffer.wrap(File(args.single()).readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        val count = input.int
        check(count == 19)
        var rejected = 0
        repeat(count) { fixture ->
            val n = input.int; val m = input.int
            check(n == 24 && m in 12..140)
            val q = LongArray(n) { input.long }; val w = LongArray(m) { input.long }
            val ids = setOf(8L, 9L, 12L)
            val result = PlannerSourceContextWire.decode(w, q, ids)
            check(!result.canExecute && !result.explicitResolvedScope && result.scopeKind == "SOURCE_MUSICKIT_SUBSET")
            check(result.generation == q[3] && result.bindingRevision == q[4])
            check(result.sourceContextResolution?.ordinal?.toLong() == w[5])
            if (fixture == 0) {
                check(q.contentEquals(PlannerSourceContextWire.request(71, 1, context(), 120000, 120000)))
                check(result.selectedStyleId == 9 && result.candidate?.score == 15.104)
            }
            if (fixture == 1) check(result.selectedStyleId == 12 && result.candidate?.score == 10.064)
            if (fixture == 2) check(result.status == PlannerSelectionStatus.RESOURCE_LIMIT && result.candidate == null)
            var immutable = false
            try { (result.knownBeatMatchedStyleIds as MutableList<Int>)[0] = 0 } catch (_: UnsupportedOperationException) { immutable = true }
            check(immutable)
            fun bad(changed: LongArray, original: LongArray = q, catalog: Set<Long> = ids) {
                var caught = false
                try { PlannerSourceContextWire.decode(changed, original, catalog) } catch (_: ObservationFailure) { caught = true }
                check(caught) { "Malformed source snapshot accepted: fixture=$fixture" }; rejected++
            }
            for (length in listOf(0, 11, m - 1, m + 1, 141)) bad(w.copyOf(length))
            for ((slot, value) in listOf(0 to 0L, 1 to 2L, 2 to -1L, 3 to -1L, 4 to -1L, 5 to 13L,
                6 to -1L, 7 to -1L, 8 to 4L, 9 to 12L, 10 to 8L, 11 to 9L)) {
                val changed = w.copyOf(); changed[slot] = value; bad(changed)
            }
            if (w[5] == 0L) {
                val p = 12 + w[6].toInt()
                for ((slot, v) in listOf(12 to 0L, 12+1 to 2L, 12+3 to -1L, 12+4 to -1L,
                    12+5 to 0L, 12+6 to 0L, 12+7 to 1L, 12+8 to 1L, 12+9 to -1L,
                    12+10 to 2L, 12+11 to 2L, 12+12 to 15L, 12+13 to Double.NaN.toRawBits(),
                    12+14 to Double.POSITIVE_INFINITY.toRawBits(), 12+18 to 100000.0.toRawBits(), 12+19 to 17.0.toRawBits(), 12+20 to 17.0.toRawBits(),
                    12+22 to 1L, 12+23 to 1L, 12+24 to 9L, 12+28 to 2L,
                    12+29 to -1L, p+0 to 0L, p+1 to 1L, p+3 to -1L, p+4 to -1L,
                    p+9 to 1L, p+44 to 0L, p+45 to 9L, p+46 to 8L)) {
                    val changed = w.copyOf(); changed[slot] = v; bad(changed)
                }
                bad(w, catalog = setOf(8L, 9L))
                if (result.candidate != null) {
                    for ((slot, v) in listOf(28 to 44L, 31 to -1L, 36 to Double.NaN.toRawBits(),
                        39 to 99999.0.toRawBits(), 40 to 0L, 43 to 16_000_001L)) {
                        val changed = w.copyOf(); changed[p+slot] = v; bad(changed)
                    }
                }
            } else {
                val changed = w.copyOf(); changed[5] = 0L; bad(changed)
            }
            for (size in listOf(0, 23, 25)) bad(w, q.copyOf(size))
            for ((slot, v) in listOf(0 to 0L, 1 to 2L, 2 to 23L, 3 to -1L, 4 to 0L, 5 to 4L,
                9 to -1L, 10 to 3L, 11 to 3L, 15 to 3L, 17 to 3L, 20 to 4L, 21 to 1L,
                16 to Double.NaN.toRawBits())) {
                val changed = q.copyOf(); changed[slot] = v; bad(w, changed)
            }
        }
        check(!input.hasRemaining())
        var invalidCopy = false
        try { context().copy(maximumComplexity = 4) } catch (_: IllegalArgumentException) { invalidCopy = true }
        check(invalidCopy)
        println("Source transport: $count native snapshots accepted; $rejected malformed variants rejected; Kotlin source request matches C++")
    }
}
