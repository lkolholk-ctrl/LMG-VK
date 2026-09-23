package com.lmg.vk.engine.automix.observation

import java.io.DataInputStream
import java.io.File

/** Reads snapshots produced by the REAL native preparation/encoder host test.
 * This is a cross-language ABI test, NOT a replacement for real-JNI JUnit tests.
 */
object PlannerPreparationTransportScenarios {
    private val sha = "a".repeat(64)
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 1)
        val cases = linkedMapOf<String, LongArray>()
        DataInputStream(File(args[0]).inputStream().buffered()).use { input ->
            val magic = ByteArray(8); input.readFully(magic); check(magic.decodeToString() == "AMX3A001")
            val n = input.readInt(); check(n in 1..64)
            repeat(n) {
                val size = input.readInt(); check(size in 1..64)
                val name = ByteArray(size); input.readFully(name)
                val count = input.readInt(); check(count in 72..PlannerPreparation.MAX_WORDS)
                check(cases.put(name.decodeToString(), LongArray(count) { input.readLong() }) == null)
            }
            check(input.read() == -1)
        }
        check(cases.keys == setOf("uniform", "missing", "empty", "limit", "preview", "negative_zero", "unknown_duration"))
        val reports = cases.mapValues { PlannerPreparation.decode(it.value, sha, sha) }
        val full = reports.getValue("uniform")
        check(!full.canExecute && full.selectedStyleId == null && full.selectionBlock == "REGION_PAIR_RULES_UNVERIFIED")
        check(full.outgoing.status == PlannerPreparationStatus.PREPARED)
        check(full.outgoing.structure?.events == 33 && full.outgoing.structure.bars == 8)
        check(full.outgoing.structure?.stablePreview?.single()?.averageTempoBpm == 120.0)
        check(full.outgoing.tonality?.main == PreparedTonality(4, 0) && full.outgoing.tonality.ending == null)
        check(full.outgoing.loudnessPoints == 4 && full.outgoing.vocalIntervals == 1)
        check(reports.getValue("missing").outgoing.flexEvents == null)
        check(reports.getValue("empty").outgoing.flexEvents == 0 && reports.getValue("empty").outgoing.vocalIntervals == 0)
        check(reports.getValue("empty").outgoing.loudnessPoints == null)
        check(reports.getValue("limit").outgoing.status == PlannerPreparationStatus.RESOURCE_LIMIT)
        check(reports.getValue("limit").incoming.status == PlannerPreparationStatus.PREPARED)
        val preview = requireNotNull(reports.getValue("preview").outgoing.structure)
        check(preview.stableRegions == 20 && preview.stablePreview.size == 16 && preview.previewTruncated)
        check(preview.stablePreview.last().sourceMapIndex == 15)
        check(reports.getValue("negative_zero").outgoing.structure!!.stablePreview[0].startSeconds.toRawBits() == Long.MIN_VALUE)
        check(reports.getValue("unknown_duration").outgoing.resolvedDurationMs == null)
        check(reports.getValue("unknown_duration").outgoing.structure!!.stablePreview[0].insideResolvedTrack == null)
        var rejected = 0
        fun reject(raw: LongArray) {
            try { PlannerPreparation.decode(raw, sha, sha); error("Bad snapshot accepted") }
            catch (e: ObservationFailure) { check(e.reason == ObservationReason.BRIDGE_CONTRACT_MISMATCH); ++rejected }
        }
        val baseline = cases.getValue("uniform")
        fun mutate(index: Int, value: Long) = reject(baseline.copyOf().also { it[index] = value })
        mutate(0, 2); mutate(1, 0); mutate(2, 1); mutate(3, 31); mutate(4, 7); mutate(5, 99)
        mutate(6, 1); mutate(7, 8); mutate(8, 99); mutate(9, 1L shl 20); mutate(10, 3)
        mutate(13, 1L shl 9); mutate(14, -1); mutate(18, 4097); mutate(19, 34)
        mutate(20, 0); mutate(23, 2); mutate(24, 0); mutate(25, 2); mutate(30, 12)
        mutate(36, 0); mutate(37, 1); mutate(38, 1); mutate(39, 1)
        mutate(40, 1); mutate(41, -1); mutate(42, 99)
        mutate(43, Double.NaN.toRawBits()); mutate(44, (-1.0).toRawBits())
        mutate(45, 0); mutate(46, Double.POSITIVE_INFINITY.toRawBits()); mutate(47, 0)
        reject(baseline.copyOf(baseline.size - 1)); reject(baseline + longArrayOf(0))
        var immutable = false
        try { (full.outgoing.structure!!.stablePreview as MutableList<StableRegionPreview>).clear() }
        catch (_: UnsupportedOperationException) { immutable = true }
        check(immutable)
        println("Planner preparation transport: ${cases.size} native snapshots, $rejected malformed snapshots rejected; immutable preview passed")
    }
}
