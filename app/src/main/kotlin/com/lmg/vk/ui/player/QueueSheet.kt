package com.lmg.vk.ui.player

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lmg.vk.engine.PlayerController
import com.lmg.vk.engine.Track
import com.lmg.vk.ui.theme.VkSansDisplay
import com.lmg.vk.ui.glass.AlbumArtImage
import com.lmg.vk.ui.glass.AlbumColors
import com.lmg.vk.ui.glass.pressScale
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Queue overlay — открывается как LyricsScreen: fadeIn/fadeOut поверх FullPlayer.
 * НЕ использует slideIn/slideOut — только прозрачность.
 *
 * Фон: динамический из обложки (как LyricsBackground).
 * Кнопки управления: те же что в FullPlayer (Shuffle, Prev, Play/Pause, Next, Repeat).
 * Без ползунка громкости.
 */
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
    onRequestControls: () -> Unit = {},
    modifier: Modifier = Modifier,
    // Split-режим (альбом, правая половина): свой фон не рисуем — общий фон
    // плеера уже под нами (иначе вертикальный шов-«полоса» и другой оттенок).
    splitMode: Boolean = false
) {
    val context = LocalContext.current
    var controlsVisible by remember { mutableStateOf(false) }
    val playerPlaying by PlayerController.isPlaying.collectAsState()
    val playerPosition by PlayerController.currentPositionMs.collectAsState()
    val playerDuration by PlayerController.durationMs.collectAsState()
    LaunchedEffect(controlsVisible, playerPlaying) {
        if (controlsVisible && playerPlaying) {
            kotlinx.coroutines.delay(3000)
            controlsVisible = false
        }
    }
    val libraryRepo = remember { com.lmg.vk.data.local.db.LibraryRepository.getInstance(context) }
    // Flow нужно строить в remember: без него isFavoriteFlow() создавал новый
    // экземпляр на каждой рекомпозиции, а collectAsState кеширует подписку по
    // инстансу — то есть Room переподписывался буквально каждый кадр.
    val favoriteFlow = remember(currentTrack?.id) {
        currentTrack?.id?.let { libraryRepo.isFavoriteFlow(it) } ?: flowOf(false)
    }
    val isFavorite by favoriteFlow.collectAsState(initial = false)
    val scope = rememberCoroutineScope()

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)),
        exit = fadeOut(tween(350)),
        modifier = modifier
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!splitMode) {
                if (albumColors != null) {
                    AnimatedPlayerBackground(
                        albumColors = albumColors
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1C1C2E))
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.18f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onDismiss
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = com.lmg.vk.ui.icons.LmgGlyphs.CancelOutline28,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.90f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Now Playing section (Pinned at the top, does not scroll!)
                if (currentTrack != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .border(0.5.dp, Color(0x1A000000), RoundedCornerShape(5.dp))
                            ) {
                                AlbumArtImage(
                                    uri = currentTrack.albumArtUri,
                                    coverUrl = currentTrack.coverUrl,
                                    audioFileUri = currentTrack.uri,
                                    albumId = currentTrack.albumId,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = currentTrack.title,
                                    color = Color.White.copy(alpha = 0.94f),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = VkSansDisplay,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = currentTrack.artist,
                                    color = Color.White.copy(alpha = 0.45f),
                                    fontSize = 13.sp,
                                    fontFamily = VkSansDisplay,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.18f))
                                    .pressScale {
                                        scope.launch {
                                            libraryRepo.toggleFavorite(currentTrack, "player")
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) com.lmg.vk.ui.icons.LmgGlyphs.Favorite28
                                    else com.lmg.vk.ui.icons.LmgGlyphs.FavoriteOutline28,
                                    contentDescription = null,
                                    tint = if (isFavorite) Color.White.copy(alpha = 0.94f)
                                    else Color.White.copy(alpha = 0.90f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onRequestControls
                        )
                ) {
                    val queue by PlayerController.queueFlow.collectAsState()
                    val sections by PlayerController.queueSections.collectAsState()
                    InlineQueue(
                        queue = queue.mapIndexed { i, t -> t to (i >= sections.autoStart) },
                        currentIndex = PlayerController.getCurrentIndex(),
                        autoplayEnabled = false,
                        onJumpTo = { PlayerController.playTrack(context, it) },
                        onRemove = { PlayerController.removeQueueItem(it) },
                        onMove = { from, to -> PlayerController.moveQueueItem(from, to) },
                        onClear = { PlayerController.clearManualSection() },
                        modifier = Modifier.fillMaxSize()
                    )
                }

            }

            if (!splitMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.425f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { if (!controlsVisible) controlsVisible = true }
                )
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(tween(500)),
                    exit = fadeOut(tween(500)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    AppleControlsBar(
                        positionMs = playerPosition,
                        durationMs = playerDuration,
                        isPlaying = playerPlaying,
                        activeTab = AppleControlsTab.QUEUE,
                        onSeek = { PlayerController.seekTo(it) },
                        onTogglePlay = { PlayerController.togglePlayPause(context) },
                        onSkipNext = { PlayerController.skipNext(context) },
                        onSkipPrevious = { PlayerController.skipPrevious(context) },
                        onLyricsTab = onDismiss,
                        onQueueTab = { controlsVisible = false },
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                    )
                }
            }
        }
    }
}
