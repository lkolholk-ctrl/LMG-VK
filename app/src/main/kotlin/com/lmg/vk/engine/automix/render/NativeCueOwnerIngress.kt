package com.lmg.vk.engine.automix.render

import com.lmg.vk.engine.automix.nativecore.NativePcmOwnerIngress
import java.nio.ByteBuffer

/** Concrete JNI-backed admission adapter. It owns INPUT copies only after commit.
 * No output lease can be manufactured from this class: it has no sink, gain or clock.
 */
internal class NativeCueOwnerIngress(val native: NativePcmOwnerIngress) : CueOwnerAdmission {
    override fun stage(generation: Long, revision: Long, format: RenderPcmFormat,
        outgoing: ByteBuffer, outInfo: CueBufferInfo, incoming: ByteBuffer, inInfo: CueBufferInfo): Any {
        require(format.supported && format.channels==native.channels && format.bytesPerSample==native.bytesPerSample)
        return native.stage(generation,revision,
            NativePcmOwnerIngress.Input(outgoing,outInfo.firstSourceFrame,outInfo.cueSourceFrame),
            NativePcmOwnerIngress.Input(incoming,inInfo.firstSourceFrame,inInfo.cueSourceFrame))
    }
    override fun commit(ticket: Any): Boolean = native.commit(ticket as NativePcmOwnerIngress.Ticket)
    override fun abort(ticket: Any) { native.abort(ticket as NativePcmOwnerIngress.Ticket) }
    override fun poll(side: Int, destination: ByteBuffer, maxFrames: Int, info: LongArray): Int =
        native.read(side,destination,maxFrames,info)
    override fun offer(side: Int, bytes: ByteBuffer, firstFrame: Long): Int = native.push(side,bytes,firstFrame)
}
