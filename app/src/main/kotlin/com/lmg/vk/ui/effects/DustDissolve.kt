package com.lmg.vk.ui.effects

import android.animation.ValueAnimator
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@Stable
internal class DustDissolveState {
    internal val progress = Animatable(0f)
}

@Composable
internal fun rememberDustDissolveState(): DustDissolveState = remember { DustDissolveState() }

private class DustFrame(val mesh: DustParticleMesh, val paint: Paint)

@Composable
internal fun DustDissolve(
    dissolving: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    state: DustDissolveState = rememberDustDissolveState(),
    durationMillis: Int = 850,
    maxParticles: Int = 6000,
    content: @Composable BoxScope.() -> Unit,
) {
    val layer = rememberGraphicsLayer()
    val density = LocalDensity.current.density
    val finish by rememberUpdatedState(onFinished)
    var recorded by remember { mutableStateOf(false) }
    var frame by remember { mutableStateOf<DustFrame?>(null) }
    LaunchedEffect(dissolving) {
        if (!dissolving) {
            frame = null
            recorded = false
            state.progress.snapTo(0f)
            return@LaunchedEffect
        }
        if (!ValueAnimator.areAnimatorsEnabled()) {
            state.progress.snapTo(1f)
            finish()
            return@LaunchedEffect
        }
        val snapshot = try {
            withTimeoutOrNull(400) {
                snapshotFlow { recorded }.first { it }
                layer.toImageBitmap()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        if (snapshot != null) {
            val mesh = withContext(Dispatchers.Default) {
                DustParticleMesh(snapshot.width, snapshot.height, density, maxParticles)
            }
            frame = DustFrame(mesh, Paint(Paint.FILTER_BITMAP_FLAG).apply {
                shader = BitmapShader(snapshot.asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            })
            state.progress.animateTo(1f, tween(durationMillis, easing = LinearEasing))
        } else {
            state.progress.snapTo(1f)
        }
        finish()
        frame = null
    }
    DisposableEffect(Unit) {
        onDispose { frame = null }
    }
    Box(
        modifier = modifier
            .then(if (dissolving) Modifier.clearAndSetSemantics {} else Modifier)
            .pointerInput(dissolving) {
                if (dissolving) awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
            .drawWithContent {
                if (!dissolving) {
                    drawContent()
                } else if (state.progress.value < 1f) {
                    val current = frame
                    if (current == null) {
                        if (!recorded && size.width > 0f && size.height > 0f) {
                            layer.record { this@drawWithContent.drawContent() }
                            recorded = true
                        }
                        drawContent()
                    } else {
                        current.mesh.update(state.progress.value)
                        drawContext.canvas.nativeCanvas.drawVertices(
                            Canvas.VertexMode.TRIANGLES, current.mesh.vertices.size,
                            current.mesh.vertices, 0, current.mesh.textureCoordinates, 0,
                            current.mesh.colors, 0, null, 0, 0, current.paint,
                        )
                    }
                }
            },
        content = content,
    )
}
