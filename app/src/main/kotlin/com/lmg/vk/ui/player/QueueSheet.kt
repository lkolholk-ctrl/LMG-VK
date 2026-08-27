package com.lmg.vk.ui.player

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.lmg.vk.engine.PlayerController
import com.lmg.vk.engine.Track
import com.lmg.vk.ui.glass.AlbumArtImage
import com.lmg.vk.ui.glass.AlbumColors
import com.lmg.vk.ui.theme.VkSansDisplay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private val QueueThumbSize = 54.dp
private val QueueHeaderHeight = 60.dp
private val QueueArtTitleGap = 20.dp
private val QueueDismissStripHeight = 44.dp
private val QueueArtTopPadding = 14.dp
private val QueueGutter = 30.dp
private val QueueMaxWidth = 560.dp

@Composable
fun QueueSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    albumArtUri: Uri? = null,
    coverUrl: String? = null,
    audioFileUri: Uri? = null,
    albumId: Long = -1L,
    albumColors: AlbumColors? = null,
    currentTrack: Track? = null,
    onMoreClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    splitMode: Boolean = false,
) {
    val context = LocalContext.current
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(420),
        label = "queueProgress",
    )
    if (progress <= 0.001f) return

    val queue by PlayerController.queueFlow.collectAsState()
    val sections by PlayerController.queueSections.collectAsState()
    val autoplayEnabled by PlayerController.autoplayEnabled.collectAsState()
    val shuffleEnabled by PlayerController.shuffleEnabled.collectAsState()
    val repeatMode by PlayerController.repeatMode.collectAsState()
    val isPlaying by PlayerController.isPlaying.collectAsState()
    val isBuffering by PlayerController.isBuffering.collectAsState()
    val positionMs by PlayerController.currentPositionMs.collectAsState()
    val durationMs by PlayerController.durationMs.collectAsState()
    val currentIndex = PlayerController.getCurrentIndex()
    val library = remember { com.lmg.vk.data.local.db.LibraryRepository.getInstance(context) }
    val favoriteFlow = remember(currentTrack?.id) {
        currentTrack?.id?.let(library::isFavoriteFlow) ?: flowOf(false)
    }
    val isFavorite by favoriteFlow.collectAsState(initial = false)
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = progress },
    ) {
        if (!splitMode) {
            if (albumColors != null) {
                AnimatedPlayerBackground(albumColors = albumColors)
            } else {
                Box(Modifier.fillMaxSize().background(Color(0xFF1C1C2E)))
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(QueueDismissStripHeight),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(38.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.32f)),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = QueueGutter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = QueueMaxWidth)
                        .fillMaxWidth()
                        .padding(top = QueueArtTopPadding, bottom = 18.dp),
                ) {
                    val fullArt = minOf(maxWidth, maxHeight - QueueArtTitleGap - QueueHeaderHeight)
                        .coerceAtLeast(QueueThumbSize)
                    val groupTop = ((maxHeight - fullArt - QueueArtTitleGap - QueueHeaderHeight) / 2)
                        .coerceAtLeast(0.dp)
                    val artSize = lerp(fullArt, QueueThumbSize, progress)
                    val artTop = lerp(groupTop, 0.dp, progress)
                    val artStart = lerp((maxWidth - fullArt) / 2, 0.dp, progress)
                    val titleTop = lerp(groupTop + fullArt + QueueArtTitleGap, 0.dp, progress)
                    val titleStart = lerp(0.dp, QueueThumbSize + 12.dp, progress)
                    val corner = lerp(10.dp, 7.dp, progress)

                    currentTrack?.let { track ->
                        Box(
                            modifier = Modifier
                                .offset(x = artStart, y = artTop)
                                .size(artSize)
                                .shadow(lerp(14.dp, 6.dp, progress), RoundedCornerShape(corner))
                                .clip(RoundedCornerShape(corner))
                                .background(Color.Black.copy(alpha = 0.18f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    enabled = progress > 0.5f,
                                    onClick = onDismiss,
                                ),
                        ) {
                            AlbumArtImage(
                                uri = track.albumArtUri.takeIf { track.albumId >= 0 } ?: albumArtUri,
                                coverUrl = track.coverUrl ?: coverUrl,
                                audioFileUri = track.uri.takeIf { it != Uri.EMPTY } ?: audioFileUri,
                                albumId = track.albumId.takeIf { it >= 0 } ?: albumId,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = titleTop)
                                .padding(start = titleStart)
                                .height(QueueHeaderHeight),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val titleSize = (20f - 4f * progress).sp
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    color = Color.White,
                                    fontSize = titleSize,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = VkSansDisplay,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = track.artist,
                                    color = Color.White.copy(alpha = 0.55f),
                                    fontSize = titleSize,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = VkSansDisplay,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            QueueCircleGlyph(
                                icon = if (isFavorite) QueueHeartFilledIcon else QueueHeartIcon,
                                active = isFavorite,
                                onClick = { scope.launch { library.toggleFavorite(track, "player") } },
                            )
                            Spacer(Modifier.width(8.dp))
                            QueueCircleGlyph(
                                icon = Icons.Rounded.MoreHoriz,
                                onClick = onMoreClick,
                            )
                        }
                    }

                    if (progress > 0.01f) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = QueueHeaderHeight + 10.dp)
                                .graphicsLayer {
                                    alpha = ((progress - 0.45f) / 0.55f).coerceIn(0f, 1f)
                                    translationY = (1f - progress) * 26.dp.toPx()
                                },
                        ) {
                            InlineQueue(
                                queue = queue.mapIndexed { index, track -> track to (index >= sections.autoStart) },
                                currentIndex = currentIndex,
                                autoplayEnabled = autoplayEnabled,
                                onJumpTo = { PlayerController.playTrack(context, it) },
                                onRemove = PlayerController::removeQueueItem,
                                onMove = PlayerController::moveQueueItem,
                                onClear = PlayerController::clearQueueAfterCurrent,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                AppleControlsBar(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    hasPrevious = currentIndex > 0,
                    hasNext = currentIndex in 0 until queue.lastIndex,
                    shuffleEnabled = shuffleEnabled,
                    repeatMode = repeatMode,
                    autoplayEnabled = autoplayEnabled,
                    onSeek = PlayerController::seekTo,
                    onTogglePlay = { PlayerController.togglePlayPause(context) },
                    onSkipNext = { PlayerController.skipNext(context) },
                    onSkipPrevious = { PlayerController.skipPrevious(context) },
                    onToggleShuffle = PlayerController::toggleShuffle,
                    onCycleRepeat = PlayerController::cycleRepeatMode,
                    onToggleAutoplay = PlayerController::toggleAutoplay,
                    onQueueTab = onDismiss,
                    modifier = Modifier.widthIn(max = QueueMaxWidth),
                )
            }
        }
    }
}

@Composable
private fun QueueCircleGlyph(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (active) 0.34f else 0.18f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(19.dp),
        )
    }
}
