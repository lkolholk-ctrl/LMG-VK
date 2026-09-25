package com.lmg.vk.engine.automix.render

import java.nio.ByteBuffer

internal inline fun RenderBoundaryEndpoint.diagnostic(block: () -> Unit) {
    try { block() } catch (_: Exception) { disable() }
}

/** Never turns a partial delegate result into ownership of PCM. */
internal inline fun RenderBoundaryEndpoint.forwardUnchanged(
    buffer: ByteBuffer, presentationTimeUs: Long, accessUnits: Int,
    write: () -> Boolean,
): Boolean {
    diagnostic { before(buffer,presentationTimeUs,accessUnits) }
    val result: Boolean
    try { result=write() }
    catch (failure: Throwable) {
        diagnostic { fault() }
        throw failure // Original delegate exception, never a diagnostic exception.
    }
    diagnostic { after(buffer,result) }
    return result
}

/** The gate is dormant unless explicitly requested for the current plan token. */
internal inline fun RenderBoundaryEndpoint.forwardWithCueGate(
    buffer: ByteBuffer, presentationTimeUs: Long, accessUnits: Int,
    crossinline write: () -> Boolean,
): Boolean {
    // Native acknowledgements are NOT bytes accepted by the original sink ledger.
    // Preserve the old before/after behavior for dormant, held and rolled-back buffers.
    if (cuePort.ownsInput()) return cuePort.forward(buffer,presentationTimeUs,accessUnits) { write() }
    return forwardUnchanged(buffer, presentationTimeUs, accessUnits) {
        cuePort.forward(buffer, presentationTimeUs, accessUnits) { write() }
    }
}
