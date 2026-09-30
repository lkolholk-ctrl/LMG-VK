package com.lmg.vk.ui.liquid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Full Player's 6dp rail, 40x24 capsule and spring response, painted directly.
 * No backdrop capture, blur, lens, shadow or offscreen graphics layer. */
@Composable
internal fun FlatPlayerSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String,
    displayValue: String,
    modifier: Modifier = Modifier,
    activeColor: Color,
    trackColor: Color,
    zeroFraction: Float? = null,
) {
    val latestChange by rememberUpdatedState(onValueChange)
    val latestValue by rememberUpdatedState(value)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    val motion = remember(scope) {
        DampedDragAnimation(scope, value.coerceIn(0f, 1f), 0f..1f, 0.001f, 1f, 1.5f,
            onDragStarted = {}, onDragStopped = {}, onDrag = { _, _ -> })
    }
    LaunchedEffect(value, dragging) {
        if (!dragging) motion.updateValue(value.coerceIn(0f, 1f))
    }
    fun publish(fraction: Float) {
        val next = fraction.coerceIn(0f, 1f)
        motion.updateValue(next)
        latestChange(next)
    }
    Canvas(modifier.height(48.dp)
        .semantics {
            contentDescription = label
            stateDescription = displayValue
            progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(0f, 1f), 0f..1f)
            setProgress { requested ->
                if (!requested.isFinite()) false else {
                    publish(requested)
                    true
                }
            }
        }
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) false else {
                val next = when (event.key) {
                    Key.DirectionRight -> latestValue + if (rtl) -0.01f else 0.01f
                    Key.DirectionLeft -> latestValue + if (rtl) 0.01f else -0.01f
                    Key.DirectionUp -> latestValue + 0.01f
                    Key.DirectionDown -> latestValue - 0.01f
                    Key.MoveHome -> 0f
                    Key.MoveEnd -> 1f
                    else -> return@onKeyEvent false
                }
                publish(next); true
            }
        }
        .focusable()
        .pointerInput(motion, rtl) {
            val inset = 20.dp.toPx()
            fun fraction(x: Float): Float {
                val physical = ((x - inset) / (size.width - inset * 2).coerceAtLeast(1f)).coerceIn(0f, 1f)
                return if (rtl) 1f - physical else physical
            }
            detectTapGestures { position ->
                val next = fraction(position.x)
                motion.animateToValue(next)
                latestChange(next)
            }
        }
        .pointerInput(motion, rtl) {
            val inset = 20.dp.toPx()
            var target = 0f
            fun finish() { dragging = false; motion.release() }
            detectHorizontalDragGestures(
                onDragStart = { point ->
                    dragging = true
                    val physical = ((point.x - inset) / (size.width - inset * 2).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    target = if (rtl) 1f - physical else physical
                    motion.press()
                    motion.updateValue(target)
                    latestChange(target)
                },
                onDragEnd = ::finish,
                onDragCancel = ::finish,
                onHorizontalDrag = { change, delta ->
                    change.consume()
                    target = (target + delta / (size.width - inset * 2).coerceAtLeast(1f) * if (rtl) -1f else 1f).coerceIn(0f, 1f)
                    motion.updateValue(target)
                    latestChange(target)
                },
            )
        }
    ) {
        val inset = 20.dp.toPx().coerceAtMost(size.width / 2)
        val width = (size.width - inset * 2).coerceAtLeast(0f)
        val y = size.height / 2
        val progress = motion.progress.coerceIn(0f, 1f)
        val physical = if (rtl) 1f - progress else progress
        val center = inset + width * physical
        val rail = 6.dp.toPx()
        drawRoundRect(trackColor, Offset(inset, y - rail / 2), Size(width, rail), CornerRadius(rail / 2))
        val fillStart = if (rtl) center else inset
        drawRoundRect(activeColor, Offset(fillStart, y - rail / 2), Size(width * progress, rail), CornerRadius(rail / 2))
        zeroFraction?.let {
            val x = inset + width * if (rtl) 1f - it else it
            drawLine(activeColor.copy(alpha = 0.45f), Offset(x, y - 6.dp.toPx()), Offset(x, y + 6.dp.toPx()), 1.dp.toPx())
        }
        val velocity = motion.velocity / 10f
        val thumbWidth = 40.dp.toPx() * motion.scaleX / (1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f))
        val thumbHeight = 24.dp.toPx() * motion.scaleY * (1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f))
        val thumbLeft = (center - thumbWidth / 2).coerceIn(0f, (size.width - thumbWidth).coerceAtLeast(0f))
        drawRoundRect(activeColor, Offset(thumbLeft, y - thumbHeight / 2),
            Size(thumbWidth, thumbHeight), CornerRadius(thumbHeight / 2))
    }
}
