package com.lmg.vk.engine.automix.observation

import com.lmg.vk.engine.automix.nativecore.NativeObservationBridge

/** Actual JNI is exercised; no native method is mocked by these scenarios. */
object NativeMetadataProbeScenarios {
    private const val HASH = "fe3d0a36625ccb043c519bcc5119c98190893a9911cfa58412ad60cbab04d120"
    private fun outgoing() = MetadataProbeTrack(180000.0, 180000, 120.0, 120.0, 90.0, 2, 2)
    private fun incoming() = MetadataProbeTrack(200000.0, 200000, 60.0, 180.0, 60.0, 2, 2)

    fun typedRoundTrip() {
        val result = MetadataProbe.calculate(outgoing(), incoming(), HASH)
        check(result.catalogSha256 == HASH && result.issues == setOf(MetadataProbeIssue.REGIONS_REQUIRED))
        check(result.outgoingDurationConfidence == 2 && result.incomingDurationConfidence == 2)
        check(result.mainNormalTempoTag == 2 && result.mainExpandedTempoTag == 2)
        check(result.edgeNormalTempoTag == 0 && result.edgeExpandedTempoTag == 0)
        check(result.selectedStyleId == null)
    }
    fun absentZeroAndFalseRemainDistinct() {
        val missing = MetadataProbe.calculate(outgoing().copy(endingBpm = null), incoming(), HASH)
        check(missing.edgeNormalTempoTag == null && MetadataProbeIssue.EDGE_TEMPO_MISSING in missing.issues)
        check(missing.mainNormalTempoTag == 2)
        val zero = MetadataProbe.calculate(outgoing().copy(endingBpm = 0.0), incoming(), HASH)
        check(zero.edgeNormalTempoTag == null && MetadataProbeIssue.INVALID_SCALAR in zero.issues)
        check(MetadataProbeIssue.EDGE_TEMPO_MISSING !in zero.issues)
        val disabled = MetadataProbe.calculate(outgoing().copy(supportState = 1), incoming().copy(audioState = 1), HASH)
        check(MetadataProbeIssue.OUT_SUPPORT_FALSE in disabled.issues)
        check(MetadataProbeIssue.IN_AUDIO_UNRESOLVED in disabled.issues && disabled.mainNormalTempoTag == null)
    }
    fun nativeBoundariesArePreserved() {
        val mismatch = MetadataProbe.calculate(outgoing().copy(timelineDurationMs = 182000), incoming(), HASH)
        check(mismatch.outgoingDurationConfidence == 0 && MetadataProbeIssue.OUT_DURATION_MISMATCH in mismatch.issues)
        val unknown = MetadataProbe.calculate(outgoing().copy(timelineDurationMs = null), incoming(), HASH)
        check(unknown.outgoingDurationConfidence == null && MetadataProbeIssue.OUT_DURATION_UNKNOWN in unknown.issues)
        val expanded = MetadataProbe.calculate(outgoing().copy(mainBpm = 100.0), incoming().copy(mainBpm = 121.0), HASH)
        check(expanded.mainNormalTempoTag == 252 && expanded.mainExpandedTempoTag == 129)
        val near = MetadataProbe.calculate(outgoing().copy(cloudDurationMs = 180000.5, timelineDurationMs = 182000), incoming(), HASH)
        check(near.outgoingDurationConfidence == 2)
    }
    fun invalidNativeAbiIsRejected() {
        val calls = listOf<() -> Any>(
            { NativeObservationBridge.probePair(DoubleArray(9), 0, 0) },
            { NativeObservationBridge.probePair(DoubleArray(11), 0, 0) },
            { NativeObservationBridge.probePair(DoubleArray(10), -1, 0) },
            { NativeObservationBridge.probePair(DoubleArray(10), 0, 256) },
            { NativeObservationBridge.probePair(DoubleArray(10), 0, 3) },
            { NativeObservationBridge.probePair(DoubleArray(10) { Double.NaN }, 0, 0) },
            { NativeObservationBridge.probePair(DoubleArray(10) { 1.0 }, 0, 0) },
        )
        for (call in calls) {
            var rejected = false
            try { call() } catch (_: IllegalArgumentException) { rejected = true }
            check(rejected)
        }
    }
    fun bridgeSnapshotsAreStrict() {
        val good = longArrayOf(1, 32768, 2, 2, 1, 1, 0, 0)
        MetadataProbe.decode(good, HASH)
        val invalid = listOf(
            good.copyOf(7), good.copyOf(9),
            good.copyOf().also { it[0] = 2 },
            good.copyOf().also { it[1] = 0 },
            good.copyOf().also { it[1] = 65536 },
            good.copyOf().also { it[2] = 1 },
            good.copyOf().also { it[4] = 3 },
        )
        for (snapshot in invalid) {
            var rejected = false
            try { MetadataProbe.decode(snapshot, HASH) } catch (error: ObservationFailure) {
                check(error.reason == ObservationReason.BRIDGE_CONTRACT_MISMATCH); rejected = true
            }
            check(rejected)
        }
    }

    @JvmStatic fun main(args: Array<String>) {
        val cases = listOf(
            "typedRoundTrip" to ::typedRoundTrip,
            "absentZeroAndFalseRemainDistinct" to ::absentZeroAndFalseRemainDistinct,
            "nativeBoundariesArePreserved" to ::nativeBoundariesArePreserved,
            "invalidNativeAbiIsRejected" to ::invalidNativeAbiIsRejected,
            "bridgeSnapshotsAreStrict" to ::bridgeSnapshotsAreStrict,
        )
        for ((name, run) in cases) { run(); println("PASS $name") }
        println("NativeMetadataProbe: ${cases.size}/${cases.size} real-JNI/contract scenario groups passed")
    }
}
