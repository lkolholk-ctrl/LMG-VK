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
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
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
    sharedBackground: Boolean = false,
    transitionProgress: State<Float>? = null,
    onArtworkPositioned: ((LayoutCoordinates) -> Unit)? = null,
) {
    val context = LocalContext.current
    val ownProgress = animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(500, easing = com.lmg.vk.ui.theme.AppleEasings.Standard),
        label = "queueProgress",
    )
    val progress = transitionProgress ?: ownProgress
    if (!visible && progress.value <= 0.001f) return

    val queue by PlayerController.queueFlow.collectAsState()
    val sections by PlayerController.queueSections.collectAsState()
    val queueRows = remember(queue, sections.autoStart) {
        queue.mapIndexed { index, track -> track to (index >= sections.autoStart) }
    }
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
            .graphicsLayer { alpha = progress.value },
    ) {
        if (!splitMode && !sharedBackground) {
            SharedArtworkBackground(albumArtUri, coverUrl, audioFileUri, albumId)
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
                    currentTrack?.let { track ->
                        Box(
                            modifier = Modifier
                                .size(fullArt)
                                .graphicsLayer {
                                    // Shared FullPlayer already owns the large cover. Do not
                                    // animate a second, unrelated square cover over tall video.
                                    val p = if (sharedBackground) 1f else progress.value
                                    val scale = lerp(fullArt, QueueThumbSize, p) / fullArt
                                    transformOrigin = TransformOrigin(0f, 0f)
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = (lerp((maxWidth - fullArt) / 2, 0.dp, p)).toPx()
                                    translationY = lerp(groupTop, 0.dp, p).toPx()
                                    shape = RoundedCornerShape(lerp(10.dp, 7.dp, p) / scale)
                                    shadowElevation = com.lmg.vk.ui.theme.LiquidMetrics.castShadow(lerp(14.dp, 6.dp, p).toPx() / scale)
                                    clip = true
                                }
                                .onGloballyPositioned { onArtworkPositioned?.invoke(it) }
                                .background(Color.Black.copy(alpha = 0.18f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { if (progress.value > 0.5f) onDismiss() },
                                ),
                        ) {
                            AlbumArtImage(
                                uri = track.albumArtUri.takeIf { track.albumId >= 0 } ?: albumArtUri,
                                coverUrl = coverUrl ?: track.coverUrl,
                                artworkQuery = com.lmg.vk.artwork.ArtworkQuery(track.title, track.artist, track.durationMs),
                                audioFileUri = track.uri.takeIf { it != Uri.EMPTY } ?: audioFileUri,
                                albumId = track.albumId.takeIf { it >= 0 } ?: albumId,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    val p = if (sharedBackground) 1f else progress.value
                                    translationX = -(QueueThumbSize + 12.dp).toPx() * (1f - p)
                                    translationY = (groupTop + fullArt + QueueArtTitleGap).toPx() * (1f - p)
                                }
                                .padding(start = QueueThumbSize + 12.dp)
                                .height(QueueHeaderHeight),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val titleSize = 16.sp
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    color = Color.White,
                                    fontSize = titleSize,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = VkSansDisplay,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = track.artist,
                                    color = Color.White.copy(alpha = 0.55f),
                                    fontSize = titleSize,
                                    fontWeight = FontWeight.SemiBold,
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

                    run {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = QueueHeaderHeight + 10.dp)
                                .graphicsLayer {
                                    alpha = if (sharedBackground) 1f else
                                        ((progress.value - 0.45f) / 0.55f).coerceIn(0f, 1f)
                                    translationY = if (sharedBackground) 0f else
                                        (1f - progress.value) * 26.dp.toPx()
                                },
                        ) {
                            InlineQueue(
                                queue = queueRows,
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
