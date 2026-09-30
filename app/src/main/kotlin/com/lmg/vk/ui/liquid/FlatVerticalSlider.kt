package com.lmg.vk.ui.liquid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.min

/** Flat LMG fader: a rounded rail and capsule thumb, without backdrop effects. */
@Composable
internal fun FlatVerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String,
    displayValue: String,
    activeColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    zeroFraction: Float? = null,
) {
    val latestChange by rememberUpdatedState(onValueChange)
    val latestValue by rememberUpdatedState(value)
    var dragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val motion = remember(scope) {
        DampedDragAnimation(scope, value.coerceIn(0f, 1f), 0f..1f, 0.001f, 1f, 1.08f,
            onDragStarted = {}, onDragStopped = {}, onDrag = { _, _ -> })
    }
    LaunchedEffect(value, dragging) {
        if (!dragging) motion.updateValue(value.coerceIn(0f, 1f))
    }
    fun publish(next: Float) {
        val bounded = next.coerceIn(0f, 1f)
        motion.updateValue(bounded)
        latestChange(bounded)
    }
    Canvas(modifier.height(184.dp)
        .semantics {
            contentDescription = label
            stateDescription = displayValue
            progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(0f, 1f), 0f..1f)
            setProgress { requested ->
                if (!requested.isFinite()) false else { publish(requested); true }
            }
        }
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) false else {
                val next = when (event.key) {
                    Key.DirectionUp, Key.DirectionRight -> latestValue + 0.01f
                    Key.DirectionDown, Key.DirectionLeft -> latestValue - 0.01f
                    Key.MoveHome -> 0f
                    Key.MoveEnd -> 1f
                    else -> return@onKeyEvent false
                }
                publish(next); true
            }
        }
        .focusable()
        .pointerInput(motion) {
            val inset = 16.dp.toPx()
            detectTapGestures { position ->
                publish(1f - (position.y - inset) / (size.height - inset * 2).coerceAtLeast(1f))
            }
        }
        .pointerInput(motion) {
            val inset = 16.dp.toPx()
            fun move(y: Float) = publish(1f - (y - inset) / (size.height - inset * 2).coerceAtLeast(1f))
            fun finish() { dragging = false; motion.release() }
            // Child consumes vertical drags; horizontal swipes remain available to the band row.
            detectVerticalDragGestures(
                onDragStart = { position -> dragging = true; motion.press(); move(position.y) },
                onDragEnd = ::finish,
                onDragCancel = ::finish,
                onVerticalDrag = { change, _ -> change.consume(); move(change.position.y) },
            )
        }
    ) {
        val inset = 16.dp.toPx()
        val railHeight = (size.height - inset * 2).coerceAtLeast(0f)
        val centerX = size.width / 2
        val railWidth = 7.dp.toPx()
        val thumbY = inset + railHeight * (1f - motion.progress.coerceIn(0f, 1f))
        val originY = inset + railHeight * (1f - (zeroFraction ?: 0f).coerceIn(0f, 1f))
        drawRoundRect(trackColor, Offset(centerX - railWidth / 2, inset),
            Size(railWidth, railHeight), CornerRadius(railWidth / 2))
        if (zeroFraction != null) {
            drawLine(trackColor, Offset(centerX - 11.dp.toPx(), originY),
                Offset(centerX + 11.dp.toPx(), originY), 1.dp.toPx())
        }
        drawRoundRect(activeColor.copy(alpha = 0.65f), Offset(centerX - railWidth / 2, min(thumbY, originY)),
            Size(railWidth, abs(thumbY - originY)), CornerRadius(railWidth / 2))
        val thumbWidth = min(30.dp.toPx() * motion.scaleX, size.width - 2.dp.toPx()).coerceAtLeast(0f)
        val thumbHeight = 20.dp.toPx() * motion.scaleY
        drawRoundRect(activeColor, Offset(centerX - thumbWidth / 2, thumbY - thumbHeight / 2),
            Size(thumbWidth, thumbHeight), CornerRadius(thumbHeight / 2))
    }
}
