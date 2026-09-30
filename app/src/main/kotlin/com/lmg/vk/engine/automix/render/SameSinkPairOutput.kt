package com.lmg.vk.engine.automix.render

import java.nio.ByteBuffer

/** Concrete reservation of the two EXISTING delegate write domains. Only outgoing writes;
 * incoming must have no queued data and becomes a suppressed companion. This is deliberately
 * NOT CuePlaybackLease: it does not reserve ExoPlayer's source-period/renderer clock or install
 * the DSP pump. Adapting it using a constant-true clock proof is invalid.
 */
internal class SameSinkPairOutput private constructor(
    private val controller: RenderBoundaryController,
    private val primary: SameSinkOutputPort,
    private val secondary: SameSinkOutputPort,
    private val a: SameSinkOutputPort.Ticket,
    private val b: SameSinkOutputPort.Ticket,
) {
    val canExecute: Boolean get() = false
    val liveDspInstalled: Boolean get() = false
    val rendererClockReserved: Boolean get() = false
    private var admitted: CueOwnerTicket? = null
    fun bindCommittedInputs(receipt: CueOwnerTicket): Boolean {
        if (admitted != null || !controller.hasCommittedCueOwner(receipt)) return false
        if (!secondary.activate(b) || !primary.activate(a)) { revoke(); return false }
        if (!controller.hasCommittedCueOwner(receipt)) { revoke(); return false }
        admitted = receipt
        return true
    }
    fun write(packetId: Long, firstFrame: Long, pcm: ByteBuffer): OutputWriteReceipt {
        val input = admitted ?: throw IllegalStateException("No committed PCM input transaction")
        if (!controller.hasCommittedCueOwner(input)) { revoke(); throw OutputPortResetRequired() }
        return primary.write(a, packetId, firstFrame, pcm)
    }
    fun writeInto(packetId: Long, firstFrame: Long, pcm: ByteBuffer, into: OutputWriteState) {
        val input = admitted ?: throw IllegalStateException("No committed PCM input transaction")
        if (!controller.hasCommittedCueOwner(input)) { revoke(); throw OutputPortResetRequired() }
        primary.writeInto(a, packetId, firstFrame, pcm, into)
    }
    fun completeFrameCount(): Long = primary.completeFrameCount()
    fun sampleSinkClockValue(): Long = primary.sampleSinkClockValue(a)
    fun prefixPreservesLegacyLevel(): Boolean = primary.livePrefixIsUnity()
    val basePtsUs: Long get() = a.basePtsUs
    fun drain(): Boolean = primary.drain(a)
    fun sampleSinkClock(): Long? = primary.sampleSinkClock(a)
    fun snapshot(): OutputPortSnapshot = primary.snapshot()
    fun revoke() { a.revoke(); b.revoke() }
    fun rollbackBeforeInputClaim(): Boolean {
        // Releasing output reservations never authorizes replay of already claimed source frames.
        if (admitted != null || controller.cueOwnerSnapshot()?.phase in setOf(
                CueOwnerPhase.CLAIMED, CueOwnerPhase.INPUT_OWNED, CueOwnerPhase.RESET_REQUIRED)) {
            revoke(); return false
        }
        // Each side independently preserves uncertainty. No replay after a primary write attempt.
        val primaryRestored = primary.rollback(a)
        val secondaryRestored = secondary.rollback(b)
        return primaryRestored && secondaryRestored
    }
    companion object {
        fun reserve(controller: RenderBoundaryController, receipt: RenderBoundaryReservation): SameSinkPairOutput? {
            if (!controller.owner() || !controller.isCurrent(receipt)) return null
            // Real held buffers must be in place. Merely seeing two output identities is insufficient.
            if (controller.cueProbeSnapshot().phase != CueProbePhase.PAIR_HELD) return null
            val e = receipt.epoch as? RenderBoundaryController.Epoch ?: return null
            val anchor = controller.heldCueOutputAnchors() ?: return null
            if (anchor.generation != e.generation || anchor.revision != e.revision || anchor.outgoingPtsUs < 0) return null
            val firstOutputPtsUs = anchor.outgoingPtsUs
            val out = receipt.a as? RenderBoundaryEndpoint ?: return null
            val inc = receipt.b as? RenderBoundaryEndpoint ?: return null
            val f = out.format ?: return null
            if (inc.format != f || out === inc) return null
            val master = out.sameSinkOutputPort as? SameSinkOutputPort ?: return null
            val companion = inc.sameSinkOutputPort as? SameSinkOutputPort ?: return null
            if (master === companion) return null
            fun current(): Boolean = controller.epoch() === e && !e.closed &&
                out.version == receipt.aVersion && inc.version == receipt.bVersion &&
                out.seenEpoch === e && inc.seenEpoch === e && out.problem == null && inc.problem == null &&
                out.sameSinkOutputPort === master && inc.sameSinkOutputPort === companion
            val bt = companion.reserve(e.generation, e.revision, f, firstOutputPtsUs, ::current, false) ?: return null
            try {
                val at = master.reserve(e.generation, e.revision, f, firstOutputPtsUs, ::current, true)
                if (at == null) { companion.rollback(bt); return null }
                return SameSinkPairOutput(controller, master, companion, at, bt)
            } catch (failure: Throwable) {
                try { companion.rollback(bt) } catch (_: Throwable) { }
                throw failure
            }
        }
    }
}
