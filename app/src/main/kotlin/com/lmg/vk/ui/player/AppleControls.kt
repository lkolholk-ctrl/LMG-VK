package com.lmg.vk.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lmg.vk.R
import com.lmg.vk.ui.icons.LmgGlyphs
import com.lmg.vk.ui.theme.VkSansDisplay

private val AppleInk = Color.White
private val AppleTrack = Color(0x2EFFFFFF)
private val AppleFill = Color(0x54FFFFFF)
private val AppleFillActive = Color(0xF0FFFFFF)
private val AppleDim = Color.White.copy(alpha = 0.55f)

@Composable
fun AppleControlsBar(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    autoplayEnabled: Boolean,
    onSeek: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleAutoplay: () -> Unit,
    onQueueTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        AppleSlider(
            positionMs = positionMs,
            durationMs = durationMs,
            onSeek = onSeek
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatAppleTime(positionMs),
                color = AppleDim,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = VkSansDisplay
            )
            Text(
                text = if (durationMs > 0) "-" + formatAppleTime(durationMs - positionMs) else "-0:00",
                color = AppleDim,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = VkSansDisplay
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppleControlButton(onClick = onSkipPrevious) {
                Icon(
                    imageVector = LmgGlyphs.SkipPrevious36,
                    contentDescription = null,
                    tint = AppleInk,
                    modifier = Modifier.size(46.dp)
                )
            }
            AppleControlButton(onClick = onTogglePlay) {
                Icon(
                    imageVector = if (isPlaying) LmgGlyphs.Pause36 else LmgGlyphs.Play36,
                    contentDescription = null,
                    tint = AppleInk,
                    modifier = Modifier.size(62.dp)
                )
            }
            AppleControlButton(onClick = onSkipNext) {
                Icon(
                    imageVector = LmgGlyphs.SkipNext36,
                    contentDescription = null,
                    tint = AppleInk,
                    modifier = Modifier.size(46.dp)
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppleTabIcon(
                active = shuffleEnabled,
                onClick = onToggleShuffle
            ) {
                Icon(
                    imageVector = LmgGlyphs.ShuffleOutline28,
                    contentDescription = stringResource(if (shuffleEnabled) R.string.queue_shuffle_on else R.string.queue_shuffle_off),
                    tint = if (shuffleEnabled) AppleFillActive else AppleInk.copy(alpha = 0.75f),
                    modifier = Modifier.size(26.dp)
                )
            }
            AppleTabIcon(
                active = repeatMode != 0,
                onClick = onCycleRepeat
            ) {
                Icon(
                    imageVector = if (repeatMode == 2) LmgGlyphs.RepeatOneOutline28 else LmgGlyphs.RepeatOutline28,
                    contentDescription = stringResource(R.string.queue_repeat),
                    tint = if (repeatMode != 0) AppleFillActive else AppleInk.copy(alpha = 0.75f),
                    modifier = Modifier.size(26.dp)
                )
            }
            AppleTabIcon(
                active = autoplayEnabled,
                onClick = onToggleAutoplay
            ) {
                Icon(
                    imageVector = QueueInfinityIcon,
                    contentDescription = stringResource(R.string.queue_autoplay),
                    tint = if (autoplayEnabled) AppleFillActive else AppleInk.copy(alpha = 0.75f),
                    modifier = Modifier.size(26.dp)
                )
            }
            AppleTabIcon(
                active = true,
                onClick = onQueueTab
            ) {
                Icon(
                    imageVector = LmgGlyphs.ListPlayOutline28,
                    contentDescription = stringResource(R.string.queue_title),
                    tint = AppleFillActive,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
private fun AppleTabIcon(
    active: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = if (active) 0.20f else 0f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun AppleControlButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(74.dp)
            .clip(RoundedCornerShape(50))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun AppleSlider(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
) {
    var widthPx by remember { mutableFloatStateOf(0f) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = dragFraction
        ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val fillColor = if (dragFraction != null) AppleFillActive else AppleFill

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .onSizeChanged { widthPx = it.width.toFloat() }
                .pointerInput(durationMs) {
                    detectTapGestures(
                        onPress = { offset: Offset ->
                            val f = (offset.x / size.width).coerceIn(0f, 1f)
                            dragFraction = f
                            tryAwaitRelease()
                            if (durationMs > 0) onSeek((f * durationMs).toLong())
                            dragFraction = null
                        }
                    )
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragFraction = ((dragFraction ?: 0f) + dragAmount / size.width)
                                .coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            dragFraction?.let { if (durationMs > 0) onSeek((it * durationMs).toLong()) }
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (dragFraction == null) 6.dp else 10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(AppleTrack)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(if (dragFraction == null) 6.dp else 10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(fillColor)
            )
        }
    }
}

private fun formatAppleTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:%02d".format(seconds)
}
