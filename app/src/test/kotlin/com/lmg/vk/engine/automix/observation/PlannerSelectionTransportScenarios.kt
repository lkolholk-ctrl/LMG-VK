package com.lmg.vk.engine.automix.observation

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object PlannerSelectionTransportScenarios {
    @JvmStatic fun main(args: Array<String>) {
        val input = ByteBuffer.wrap(File(args.single()).readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        val count = input.int
        check(count == 12)
        var rejected = 0
        repeat(count) { index ->
            val n = input.int; check(n in 24..80)
            val q = LongArray(n) { input.long }; val w = LongArray(48) { input.long }
            val r = PlannerSelectionWire.decode(w, q)
            check(!r.canExecute && r.generation == 71L)
            check(r.knownBeatMatchedStyleIds == listOf(8, 9, 12))
            if (index == 0) check(r.missingSourceBindingCodes == listOf("CATALOG_FIELD_MAPPING_UNVERIFIED", "CRITERIA_BINDING_UNVERIFIED"))
            var immutable = false
            try { (r.knownBeatMatchedStyleIds as MutableList<Int>)[0] = 99 } catch (_: UnsupportedOperationException) { immutable = true }
            check(immutable)
            if (index == 1) {
                val fromKotlin = PlannerSelectionWire.request(71, 1, ObservationBindingScenarios.scope(), 120000, 120000)
                check(q.contentEquals(fromKotlin))
                check(r.selectedStyleId == 9 && r.candidate?.score == 15.104)
            }
            if (index == 2) check(r.selectedStyleId == 12)
            fun bad(changed: LongArray) {
                var caught = false
                try { PlannerSelectionWire.decode(changed, q) } catch (_: ObservationFailure) { caught = true }
                check(caught) { "Malformed snapshot was accepted: fixture=$index" }; rejected++
            }
            for (size in listOf(0, 47, 49)) bad(w.copyOf(size))
            for ((slot, value) in listOf(0 to 0L, 1 to 1L, 2 to 47L, 3 to 99L, 4 to 99L,
                5 to (1L - w[5]), 6 to 9L, 8 to 3L, 9 to 1L, 10 to 2L, 44 to 1L, 45 to 9L, 46 to 8L, 47 to 0L)) {
                val changed = w.copyOf(); changed[slot] = value; bad(changed)
            }
            if (r.candidate != null) {
                for ((slot, value) in listOf(11 to 65L, 20 to 999L, 26 to 8192L, 28 to 44L,
                    29 to 64L, 30 to 14L, 31 to -1L, 32 to w[31], 34 to 4096L, 35 to 3L,
                    36 to Double.NaN.toRawBits(), 37 to Double.POSITIVE_INFINITY.toRawBits(),
                    39 to 121.0.toRawBits(), 40 to 0L, 41 to 42L, 43 to 16_000_001L)) {
                    val changed = w.copyOf(); changed[slot] = value; bad(changed)
                }
            } else {
                val changed = w.copyOf(); changed[28] = 9; bad(changed)
            }
        }
        check(!input.hasRemaining())
        println("Selection transport: $count C++ snapshots accepted; $rejected malformed snapshots rejected; Kotlin request matches C++")
    }
}
