package com.lmg.vk.artwork

import android.content.Context
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
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
        (maxOf(32L, (((artwork.background shr 16) and 255L) * 0.70f).toLong()) shl 16) or
        (maxOf(32L, (((artwork.background shr 8) and 255L) * 0.70f).toLong()) shl 8) or
        maxOf(32L, ((artwork.background and 255L) * 0.70f).toLong())
    var aspect by mutableStateOf(if (artwork.tall) 3f / 4f else 1f)
        private set
    var ready by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    private val lifetime = MotionArtworkLifetime<TextureView>()
    private var released = false
    private val main = Handler(Looper.getMainLooper())
    private var traceTicks = 0
    private var presentedFrames = 0L
    private val trace = object : Runnable {
        override fun run() {
            if (released || !lifetime.canPlay) return
            DebugLog.add("MOTION pipeline state=${player.playbackState} playing=${player.isPlaying} " +
                "wanted=${player.playWhenReady} positionMs=${player.currentPosition} " +
                "bufferedMs=${player.totalBufferedDuration} uiFrames=$presentedFrames ${renderer.diagnostic()}")
            traceTicks++
            main.postDelayed(this, if (traceTicks < 6) 5_000L else 30_000L)
        }
    }
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
        density = context.resources.displayMetrics.density,
        onSurface = {
            if (!released) {
                player.setVideoSurface(it)
                updatePlaying()
                DebugLog.add("MOTION prepare source=${if (artwork.hls != null) "hls" else "mp4"} wanted=${player.playWhenReady}")
                player.prepare()
            }
        },
        onFailure = { fail("render ${it.javaClass.simpleName}: ${it.message}") },
    )

    init {
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) = fail("player ${error.errorCodeName}")
            override fun onPlaybackStateChanged(playbackState: Int) {
                DebugLog.add("MOTION player state=$playbackState playWhenReady=${player.playWhenReady}")
                if (playbackState == Player.STATE_READY) updatePlaying()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                DebugLog.add("MOTION player isPlaying=$isPlaying")
            }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.height > 0) aspect = videoSize.width.toFloat() * videoSize.pixelWidthHeightRatio / videoSize.height
                renderer.videoSize(videoSize.width, videoSize.height)
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) DebugLog.add("MOTION loop")
            }
            override fun onRenderedFirstFrame() {
                DebugLog.add("MOTION player first_frame_to_renderer")
            }
        })
        updatePlaying()
        main.postDelayed(trace, 5_000L)
    }

    private fun updatePlaying() {
        if (!released) player.playWhenReady = lifetime.canPlay && !failed
    }

    private fun fail(reason: String) {
        if (released || failed) return
        DebugLog.add("MOTION $reason")
        failed = true
        ready = false
        player.stop()
    }

    fun bind(view: TextureView, blurred: Boolean, presentation: Float, onFrame: (Boolean) -> Unit) {
        if (!lifetime.attach(view)) return
        view.isOpaque = false
        view.alpha = 1f
        updatePlaying()
        var attachedSurface: SurfaceTexture? = null
        var announced = false
        var presented = false
        val listener = object : TextureView.SurfaceTextureListener {
            private fun size(surface: SurfaceTexture, width: Int, height: Int): Pair<Int, Int> {
                val scale = if (blurred && !artwork.tall) minOf(1f, 256f / maxOf(width, height)) else 1f
                val w = (width * scale).toInt().coerceAtLeast(1)
                val h = (height * scale).toInt().coerceAtLeast(1)
                surface.setDefaultBufferSize(w, h)
                return w to h
            }
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                if (!lifetime.contains(view)) return
                attachedSurface = surface
                val (w, h) = size(surface, width, height)
                renderer.attach(surface, w, h, if (blurred && artwork.tall) 2 else if (blurred) 1 else 0,
                    view.tag as? Float ?: presentation) {
                    if (lifetime.contains(view) && attachedSurface === surface && view.surfaceTexture === surface && !failed) {
                        onFrame(true)
                        view.postInvalidateOnAnimation()
                        if (!announced) {
                            announced = true
                            DebugLog.add("MOTION output first_queued blurred=$blurred size=${w}x$h")
                            ready = true
                        }
                    }
                }
            }
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                if (!lifetime.contains(view)) return
                val (w, h) = size(surface, width, height)
                renderer.resize(surface, w, h)
            }
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                attachedSurface = null
                announced = false
                presented = false
                if (lifetime.contains(view)) onFrame(false)
                renderer.detach(surface, releaseTexture = true)
                return false
            }
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                presentedFrames++
                if (!presented && lifetime.contains(view) && attachedSurface === surface && !failed) {
                    presented = true
                    DebugLog.add("MOTION output first_presented blurred=$blurred")
                }
            }
        }
        view.surfaceTextureListener = listener
        if (view.isAvailable) view.surfaceTexture?.let {
            listener.onSurfaceTextureAvailable(it, view.width, view.height)
        }
    }

    fun setPresentation(view: TextureView, fraction: Float) {
        view.tag = fraction
        if (lifetime.contains(view)) view.surfaceTexture?.let { renderer.presentation(it, fraction) }
    }

    fun retire() {
        main.removeCallbacks(trace)
        lifetime.retire()
        updatePlaying()
        if (lifetime.shouldRelease) close()
    }

    fun unbind(view: TextureView) {
        view.animate().cancel()
        view.alpha = 0f
        lifetime.detach(view)
        view.surfaceTexture?.let { renderer.detach(it, releaseTexture = false) }
        updatePlaying()
        if (lifetime.shouldRelease) close()
    }

    fun close() {
        if (released) return
        released = true
        main.removeCallbacks(trace)
        lifetime.close()
        player.release()
        renderer.close()
    }
}
