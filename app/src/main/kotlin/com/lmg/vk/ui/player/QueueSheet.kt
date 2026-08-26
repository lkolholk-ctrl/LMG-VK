package com.lmg.vk.ui.player

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lmg.vk.engine.PlayerController
import com.lmg.vk.engine.Track
import com.lmg.vk.ui.glass.AlbumArtImage
import com.lmg.vk.ui.glass.AlbumColors
import com.lmg.vk.ui.glass.pressScale
import com.lmg.vk.ui.icons.LmgGlyphs
import com.lmg.vk.ui.theme.VkSansDisplay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

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
    val playerPlaying by PlayerController.isPlaying.collectAsState()
    val playerPosition by PlayerController.currentPositionMs.collectAsState()
    val playerDuration by PlayerController.durationMs.collectAsState()
    val libraryRepo = remember { com.lmg.vk.data.local.db.LibraryRepository.getInstance(context) }
    val favoriteFlow = remember(currentTrack?.id) {
        currentTrack?.id?.let { libraryRepo.isFavoriteFlow(it) } ?: flowOf(false)
    }
    val isFavorite by favoriteFlow.collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    val contentAlpha = ((progress - 0.45f) / 0.55f).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = progress },
    ) {
        val density = LocalDensity.current
        val expandedArtScale = with(density) {
            ((maxWidth - 48.dp).toPx() / 54.dp.toPx()).coerceAtLeast(1f)
        }
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
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 30.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp),
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

            currentTrack?.let { track ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .graphicsLayer {
                            translationY = (1f - progress) * 26.dp.toPx()
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AlbumArtImage(
                        uri = track.albumArtUri ?: albumArtUri,
                        coverUrl = track.coverUrl ?: coverUrl,
                        audioFileUri = track.uri.takeIf { it != Uri.EMPTY } ?: audioFileUri,
                        albumId = track.albumId.takeIf { it >= 0 } ?: albumId,
                        modifier = Modifier
                            .size(54.dp)
                            .shadow(6.dp, RoundedCornerShape(7.dp))
                            .clip(RoundedCornerShape(7.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(7.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onDismiss,
                            )
                            .graphicsLayer {
                                val scale = 1f + (1f - progress) * (expandedArtScale - 1f)
                                scaleX = scale
                                scaleY = scale
                                translationX = (progress - 1f) * 6.dp.toPx()
                                transformOrigin = TransformOrigin(0f, 0f)
                            },
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(12.dp))
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer { alpha = contentAlpha },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = VkSansDisplay,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = track.artist,
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 14.sp,
                                fontFamily = VkSansDisplay,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        QueueHeaderButton(
                            onClick = { scope.launch { libraryRepo.toggleFavorite(track, "player") } },
                        ) {
                            Icon(
                                imageVector = if (isFavorite) LmgGlyphs.Favorite28 else LmgGlyphs.FavoriteOutline28,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(21.dp),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        QueueHeaderButton(onMoreClick) {
                            Icon(
                                imageVector = LmgGlyphs.MoreHorizontal28,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        alpha = contentAlpha
                        translationY = (1f - progress) * 26.dp.toPx()
                    },
            ) {
                InlineQueue(
                    queue = queue.mapIndexed { index, track -> track to (index >= sections.autoStart) },
                    currentIndex = PlayerController.getCurrentIndex(),
                    autoplayEnabled = autoplayEnabled,
                    onJumpTo = { PlayerController.playTrack(context, it) },
                    onRemove = PlayerController::removeQueueItem,
                    onMove = PlayerController::moveQueueItem,
                    onClear = PlayerController::clearQueueAfterCurrent,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            AppleControlsBar(
                positionMs = playerPosition,
                durationMs = playerDuration,
                isPlaying = playerPlaying,
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
                modifier = Modifier,
            )
        }
    }
}

@Composable
private fun QueueHeaderButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.14f))
            .pressScale(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
