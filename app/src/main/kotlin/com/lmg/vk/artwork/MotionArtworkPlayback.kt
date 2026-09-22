package com.lmg.vk.artwork

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.lmg.vk.debug.DebugLog

internal class MotionArtworkPlayback(context: Context, source: MotionArtworkSource) {
    val artwork = source.artwork
    val backgroundColor = 0xFF000000L or
        (maxOf(48L, (artwork.background shr 16) and 255L) shl 16) or
        (maxOf(48L, (artwork.background shr 8) and 255L) shl 8) or
        maxOf(48L, artwork.background and 255L)
    var aspect by mutableStateOf(if (artwork.tall) 3f / 4f else 1f)
        private set
    var ready by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    private var active = false
    private var outputs = 0
    private var released = false
    private val player = ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(source.dataSourceFactory))
        .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(2_000, 30_000, 250, 500).build())
        .build().apply {
        volume = 0f
        repeatMode = Player.REPEAT_MODE_ONE
        setHandleAudioBecomingNoisy(false)
        trackSelectionParameters = trackSelectionParameters.buildUpon()
            .setForceHighestSupportedBitrate(true)
            .setPreferredVideoMimeTypes("video/avc")
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        setMediaItem(MediaItem.Builder().setUri(artwork.playbackUrl)
            .setMimeType(if (artwork.hls != null) androidx.media3.common.MimeTypes.APPLICATION_M3U8
                else androidx.media3.common.MimeTypes.VIDEO_MP4).build())
    }
    private val renderer = MotionArtworkRenderer(
        onSurface = { if (!released) { player.setVideoSurface(it); player.prepare(); updatePlaying() } },
        onFailure = { fail("render ${it.javaClass.simpleName}: ${it.message}") },
    )

    init {
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) = fail("player ${error.errorCodeName}")
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.height > 0) aspect = videoSize.width.toFloat() * videoSize.pixelWidthHeightRatio / videoSize.height
                renderer.videoSize(videoSize.width, videoSize.height)
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) DebugLog.add("MOTION loop")
            }
        })
    }

    fun setPlayback(foreground: Boolean) {
        active = foreground
        updatePlaying()
    }

    private fun updatePlaying() {
        if (!released) player.playWhenReady = active && outputs > 0 && !failed
    }

    private fun fail(reason: String) {
        if (released || failed) return
        DebugLog.add("MOTION $reason")
        failed = true
        ready = false
        player.stop()
    }

    fun bind(view: TextureView, blurred: Boolean, presentation: Boolean) {
        view.isOpaque = false
        view.alpha = 0.001f
        outputs++
        updatePlaying()
        view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            private var shown = false
            private fun size(surface: SurfaceTexture, width: Int, height: Int): Pair<Int, Int> {
                val scale = if (blurred && !presentation) minOf(1f, 256f / maxOf(width, height)) else 1f
                val w = (width * scale).toInt().coerceAtLeast(1)
                val h = (height * scale).toInt().coerceAtLeast(1)
                surface.setDefaultBufferSize(w, h)
                return w to h
            }
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                val (w, h) = size(surface, width, height)
                renderer.attach(surface, w, h, if (presentation) 2 else if (blurred) 1 else 0)
            }
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                val (w, h) = size(surface, width, height)
                renderer.resize(surface, w, h)
            }
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                shown = false
                view.alpha = 0.001f
                renderer.detach(surface, releaseTexture = true)
                return false
            }
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                renderer.frameConsumed(surface)
                if (!shown && !released && !failed) {
                    shown = true
                    view.alpha = 1f
                    if (!ready) DebugLog.add("MOTION first_frame tall=${artwork.tall} aspect=$aspect")
                    ready = true
                }
            }
        }
    }

    fun unbind(view: TextureView) {
        outputs = (outputs - 1).coerceAtLeast(0)
        view.surfaceTexture?.let { renderer.detach(it, releaseTexture = false) }
        updatePlaying()
    }

    fun close() {
        if (released) return
        released = true
        player.release()
        renderer.close()
    }
}
