package com.lmg.vk.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import java.io.IOException

/** Detect the actual audio format; never label an HLS response or AAC bytes as MP3. */
internal object AudioContainer {
    fun normalize(file: File): File {
        if (file.length() < 16) throw IOException("Empty audio")
        val head = file.inputStream().use { input -> ByteArray(16).also { input.read(it) } }
        // Validate standard containers without depending on device codec support (notably ALAC).
        val magic = String(head, 0, 4, Charsets.US_ASCII)
        val extension = when {
            String(head, 4, 4, Charsets.US_ASCII) == "ftyp" -> "m4a"
            magic == "fLaC" -> "flac"
            magic == "OggS" -> "ogg"
            magic.startsWith("ID3") || (head[0].toInt() and 255 == 255 &&
                head[1].toInt() and 224 == 224 && head[1].toInt() and 6 != 0) -> "mp3"
            else -> null
        }
        if (extension != null) {
            val header = org.jaudiotagger.audio.AudioFileIO.readAs(file, extension).audioHeader
            // This tag reader rounds MP4 duration to whole seconds; a short clip may report zero.
            // MP3 reader leaves audioDataLength at zero, whereas MP4 supplies that field.
            val hasSamples = header.preciseTrackLength > 0 || (header.noOfSamples ?: 0L) > 0L ||
                (header.audioDataLength ?: 0L) > 0L
            if (header.sampleRateAsNumber <= 0 || !hasSamples ||
                header.audioDataEndPosition?.let { it > file.length() } == true)
                throw IOException("Invalid $extension audio container")
            val target = File(file.parentFile, file.nameWithoutExtension + ".$extension")
            if (file != target && !file.renameTo(target)) throw IOException("Cannot commit downloaded audio")
            return target
        }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IOException("Response contains no audio")
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME)
            val mp4 = String(head, 4, 4, Charsets.US_ASCII) == "ftyp"
            if (mime == "audio/mp4a-latm" && !mp4) {
                val target = File.createTempFile("remux-", ".m4a", file.parentFile)
                extractor.selectTrack(track)
                val muxer = MediaMuxer(target.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                var success = false
                try {
                    val targetTrack = muxer.addTrack(format); muxer.start()
                    val buffer = ByteBuffer.allocate(1024 * 1024)
                    val info = MediaCodec.BufferInfo()
                    var samples = 0
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw InterruptedException()
                        buffer.clear()
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) break
                        info.set(0, size, extractor.sampleTime, extractor.sampleFlags)
                        muxer.writeSampleData(targetTrack, buffer, info); samples++
                        extractor.advance()
                    }
                    if (samples == 0) throw IOException("Audio has no samples")
                    muxer.stop(); success = true
                } finally { muxer.release(); if (!success) target.delete() }
                val destination = File(file.parentFile, file.nameWithoutExtension + ".m4a")
                if (!target.renameTo(destination)) { target.delete(); throw IOException("Cannot commit remuxed audio") }
                if (file != destination) file.delete()
                return destination
            }
            val ext = when {
                mp4 -> ".m4a"
                mime == "audio/mpeg" -> ".mp3"
                mime == "audio/flac" -> ".flac"
                mime == "audio/vorbis" || mime == "audio/opus" -> ".ogg"
                else -> throw IOException("Unsupported downloaded audio: $mime")
            }
            val target = File(file.parentFile, file.nameWithoutExtension + ext)
            if (target != file && !file.renameTo(target)) throw IOException("Cannot commit downloaded audio")
            return target
        } finally { extractor.release() }
    }
}
