package com.lmg.vk.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lmg.vk.R
import com.lmg.vk.ui.icons.LmgGlyphs
import com.lmg.vk.ui.theme.VkSansDisplay
import kotlinx.coroutines.launch

@Composable
fun AppleControlsBar(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    isBuffering: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
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
    var scrubValue by remember { mutableFloatStateOf(Float.NaN) }
    val shown = if (scrubValue.isNaN()) {
        if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    } else {
        scrubValue
    }
    val context = LocalContext.current
    val systemVolume by rememberSystemVolume()
    val volume = remember { Animatable(systemVolume) }
    var volumeDragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(systemVolume) {
        if (!volumeDragging) {
            volume.animateTo(systemVolume, tween(220, easing = FastOutSlowInEasing))
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        BitChordThinSlider(
            value = shown,
            onValueChange = { scrubValue = it },
            onValueChangeFinished = {
                if (!scrubValue.isNaN() && durationMs > 0) onSeek((scrubValue * durationMs).toLong())
                scrubValue = Float.NaN
            },
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-9).dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                QueueTime((shown * durationMs).toLong())
                QueueTime(durationMs - (shown * durationMs).toLong(), remaining = true)
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransportGlyph(
                icon = Icons.Rounded.FastRewind,
                size = 46.dp,
                enabled = hasPrevious || positionMs > 3_000L,
                onClick = onSkipPrevious,
            )
            if (isBuffering) {
                Box(Modifier.size(74.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(38.dp),
                    )
                }
            } else {
                TransportGlyph(
                    icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    size = 62.dp,
                    onClick = onTogglePlay,
                )
            }
            TransportGlyph(
                icon = Icons.Rounded.FastForward,
                size = 46.dp,
                enabled = hasNext,
                onClick = onSkipNext,
            )
        }

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.VolumeDown,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            BitChordThinSlider(
                value = volume.value,
                onValueChange = {
                    volumeDragging = true
                    scope.launch { volume.snapTo(it) }
                    setSystemVolume(context, it)
                },
                onValueChangeFinished = { volumeDragging = false },
                idleHeight = 6.dp,
                activeHeight = 10.dp,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomGlyph(
                icon = LmgGlyphs.ShuffleOutline28,
                highlighted = shuffleEnabled,
                onClick = onToggleShuffle,
            )
            BottomGlyph(
                icon = if (repeatMode == 2) LmgGlyphs.RepeatOneOutline28 else LmgGlyphs.RepeatOutline28,
                highlighted = repeatMode != 0,
                onClick = onCycleRepeat,
            )
            BottomGlyph(
                icon = QueueInfinityIcon,
                highlighted = autoplayEnabled,
                onClick = onToggleAutoplay,
            )
            BottomGlyph(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                highlighted = true,
                onClick = onQueueTab,
            )
        }

        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun QueueTime(ms: Long, remaining: Boolean = false) {
    Text(
        text = (if (remaining) "-" else "") + formatAppleTime(ms),
        color = Color.White.copy(alpha = 0.55f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = VkSansDisplay,
    )
}

@Composable
private fun TransportGlyph(
    icon: ImageVector,
    size: Dp,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .size(size + 12.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.3f),
            modifier = Modifier.size(size),
        )
    }
}

@Composable
private fun BottomGlyph(
    icon: ImageVector,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (highlighted) Color.White.copy(alpha = 0.20f) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(R.string.queue_title),
            tint = Color.White.copy(alpha = if (highlighted) 1f else 0.75f),
            modifier = Modifier.size(26.dp),
        )
    }
}

private fun formatAppleTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    return "${totalSeconds / 60}:%02d".format(totalSeconds % 60)
}
