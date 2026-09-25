package com.lmg.vk.ui.player

import android.view.TextureView
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lmg.vk.artwork.ArtworkQuery
import com.lmg.vk.artwork.MotionArtworkPlayback
import com.lmg.vk.artwork.MotionArtworkRepository
import com.lmg.vk.engine.PlayerController
import com.lmg.vk.engine.Track
import com.lmg.vk.artwork.MotionArtworkSource
import com.lmg.vk.artwork.MotionBackdropLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity

private val LocalMotionArtwork = compositionLocalOf<MotionArtworkPlayback?> { null }

@Composable
internal fun ProvideMotionArtwork(track: Track?, content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val videoClip by PlayerController.isVideoClip.collectAsState()
    val queue by PlayerController.queueFlow.collectAsState()
    val query = track?.let { ArtworkQuery(it.title, it.artist, it.durationMs, it.albumName) }
    val lookup = key(track?.id, videoClip) {
        val result by produceState<Pair<Boolean, MotionArtworkSource?>>(false to null, query) {
            value = true to if (query != null && !videoClip) MotionArtworkRepository.load(context, query, track?.id) else null
        }
        result
    }
    val source = lookup.second
    val playback = remember(track?.id, videoClip, source?.artwork?.playbackUrl) {
        source?.let { MotionArtworkPlayback(context, it) }
    }
    var started by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            started = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(playback) { onDispose { playback?.retire() } }
    val next = queue.getOrNull(PlayerController.getCurrentIndex() + 1)?.takeUnless { it.id == track?.id }
    val nextQuery = next?.let { ArtworkQuery(it.title, it.artist, it.durationMs, it.albumName) }
    LaunchedEffect(nextQuery, query, lookup, playback?.ready, started, videoClip) {
        if (lookup.first && started && !videoClip && query != null && nextQuery != null && (source == null || playback?.ready == true)) {
            MotionArtworkRepository.prefetch(context, nextQuery)
        }
    }
    CompositionLocalProvider(LocalMotionArtwork provides playback, content = content)
}

@Composable
internal fun motionArtworkReady(): Boolean = LocalMotionArtwork.current?.let { it.ready && !it.failed } == true

@Composable
internal fun motionArtworkTall(): Boolean = LocalMotionArtwork.current?.let { it.artwork.tall && !it.failed } == true

@Composable
internal fun MotionArtworkSurface(
    modifier: Modifier = Modifier,
    blurred: Boolean = false,
    enabled: Boolean = true,
    presentation: Boolean = false,
    portraitTransform: (androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit)? = null,
) {
    val playback = LocalMotionArtwork.current
    val target = playback?.takeIf { enabled && !it.failed }
    Crossfade(
        targetState = target,
        animationSpec = tween(450),
        modifier = modifier,
        label = "motionTrack",
    ) { displayed ->
        if (displayed != null) {
            // The outgoing track must retain its own presentation until its fade ends.
            // A new lookup (temporarily null) or a square cover must not collapse the
            // old tall frame into ambient mode during that same fade.
            var retainedPresentation by remember(displayed) { mutableStateOf(presentation) }
            if (displayed === target) retainedPresentation = presentation
            val presentationFraction by animateFloatAsState(
                targetValue = if (retainedPresentation) 1f else 0f,
                animationSpec = tween(420),
                label = "motionPresentation",
            )
            // Move the live output layer, not a captured screenshot or a second player.
            // Outgoing tall tracks keep the same transform until their track fade finishes.
            Box(Modifier.fillMaxSize().graphicsLayer {
                if (blurred && retainedPresentation) portraitTransform?.invoke(this)
            }) {
                MotionArtworkContent(displayed, blurred, presentationFraction)
            }
        }
    }
}

@Composable
private fun MotionArtworkContent(playback: MotionArtworkPlayback, blurred: Boolean, presentation: Float) {
    val frameTick = remember(playback, blurred) { mutableIntStateOf(0) }
    var frameAvailable by remember(playback, blurred) { mutableStateOf(false) }
    val previewAlpha by animateFloatAsState(
        targetValue = if (frameAvailable) 0f else 1f,
        animationSpec = tween(if (frameAvailable) 300 else 0),
        label = "motionPreview",
    )
    Box(Modifier.fillMaxSize()) {
        key(playback, blurred) {
            AndroidView(
                factory = { context ->
                    TextureView(context).also { view ->
                        playback.bind(view, blurred, presentation) { available ->
                            frameAvailable = available
                            if (available) frameTick.intValue++
                        }
                    }
                },
                update = { playback.setPresentation(it, presentation) },
                onRelease = { playback.unbind(it) },
                modifier = Modifier.fillMaxSize().drawWithContent {
                    frameTick.intValue
                    drawContent()
                },
            )
        }
        if (previewAlpha > 0.001f) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = previewAlpha }) {
                if (blurred) {
                    val color = Color(playback.backgroundColor.toInt())
                    Box(Modifier.fillMaxSize().background(color))
                    if (playback.artwork.tall) {
                        val context = LocalContext.current
                        val ambientPreview = remember(playback, context) {
                            ImageRequest.Builder(context).data(playback.artwork.preview).size(32).build()
                        }
                        AsyncImage(model = ambientPreview, contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                                .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(80.dp, BlurredEdgeTreatment.Unbounded) else Modifier)
                                .graphicsLayer { alpha = 0.70f })
                        BoxWithConstraints(Modifier.fillMaxSize().graphicsLayer { alpha = presentation }) {
                            val layout = MotionBackdropLayout.create(constraints.maxWidth, constraints.maxHeight, LocalDensity.current.density)
                            val fraction = layout.videoFraction
                            val height = maxHeight * fraction
                            Box(Modifier.fillMaxWidth().height(height)
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                .drawWithCache {
                                    val mask = Brush.verticalGradient(
                                        0f to Color.White,
                                        (1f - layout.overlapFraction * 0.5f / fraction).coerceIn(0f, 0.99f) to Color.White,
                                        1f to Color.Transparent)
                                    onDrawWithContent {
                                        drawContent()
                                        drawRect(mask, blendMode = BlendMode.DstIn)
                                    }
                                }) {
                                AsyncImage(model = playback.artwork.preview, contentDescription = null,
                                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                } else {
                    AsyncImage(model = playback.artwork.preview, contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}
