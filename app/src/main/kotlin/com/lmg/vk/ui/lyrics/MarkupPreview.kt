package com.lmg.vk.ui.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lmg.vk.R
import com.lmg.vk.engine.LyricsParser
import com.lmg.vk.engine.PlayerController

/** Preview the edited timings with the same Accompanist renderer as the lyrics screen. */
@Composable
fun MarkupPreviewView(
    lyrics: LyricsParser.Lyrics,
    startLineIndex: Int,
    accent: Color,
    onBackToEdit: () -> Unit
) {
    val context = LocalContext.current
    val isPlaying by PlayerController.isPlaying.collectAsState()
    val coarse by PlayerController.currentPositionMs.collectAsState()
    val duration by PlayerController.durationMs.collectAsState()
    val position = rememberLyricsPosition(lyrics, coarse, isPlaying, durationMs = duration)
    var data by remember(lyrics) { mutableStateOf<AccompanistLyrics?>(null) }
    LaunchedEffect(lyrics, duration) {
        data = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            AccompanistLyricsAdapter.fromLegacy(lyrics, duration)
        }
    }
    val isWordLevel = lyrics.isWordLevel
    val speed by PlayerController.playbackSpeed.collectAsState()
    var seekRevision by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        PlayerController.positionDiscontinuity.collect { seekRevision++ }
    }

    // Старт с выбранной строки.
    LaunchedEffect(startLineIndex, lyrics) {
        val t = lyrics.lines.getOrNull(startLineIndex)?.timeMs?.coerceAtLeast(0L) ?: 0L
        runCatching { PlayerController.seekTo(t) }
        if (!PlayerController.isPlaying.value) runCatching { PlayerController.togglePlayPause(context) }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0A0A0C))) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

            // ── Топ-бар ──
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(accent)
                        .clickable(remember { MutableInteractionSource() }, null, onClick = onBackToEdit)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(com.lmg.vk.ui.icons.LmgGlyphs.ArrowLeftOutline28, null, tint = Color.White,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.back_to_sync), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.test_label), color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                data?.let { loaded ->
                    AccompanistLyricsBody(
                        data = loaded, position = position, seekRevision = seekRevision,
                        onSeek = { PlayerController.seekTo(it.start.toLong()) },
                        onShare = {},
                    )
                }
            }

            // ── Транспорт ──
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircleBtn(if (isPlaying) com.lmg.vk.ui.icons.LmgGlyphs.Pause28 else com.lmg.vk.ui.icons.LmgGlyphs.Play28, accent) {
                    PlayerController.togglePlayPause(context)
                }
                CircleBtn(com.lmg.vk.ui.icons.LmgGlyphs.RefreshOutline28, accent) {
                    val t = lyrics.lines.getOrNull(startLineIndex)?.timeMs?.coerceAtLeast(0L) ?: 0L
                    PlayerController.seekTo(t)
                }
                Text(
                    if (isWordLevel) stringResource(R.string.preview_words_hint)
                    else stringResource(R.string.preview_lines_hint),
                    color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp
                )
            }

            // ── Скорость (можно проверять и в замедлении) ──
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.speed_label), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                for (i in 10 downTo 1) {
                    val v = i / 10f
                    val sel = kotlin.math.abs(speed - v) < 0.01f
                    Text(
                        "$v×", color = Color.White, fontSize = 13.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .background(if (sel) accent else Color.White.copy(alpha = 0.12f))
                            .clickable(remember { MutableInteractionSource() }, null) {
                                PlayerController.setPlaybackSpeed(v)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun CircleBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(accent)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
}
