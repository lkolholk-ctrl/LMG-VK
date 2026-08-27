package com.lmg.vk.ui.lyrics.apple

import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lmg.vk.engine.lyrics.apple.AppleLyricsDocument
import com.lmg.vk.engine.lyrics.apple.AppleLyricsLine
import kotlinx.coroutines.delay

private const val USER_SCROLL_PAUSE_MS = 4000L
private const val ACTIVE_LINE_BIAS = 0.28f

@Composable
fun AppleKaraokeList(
    document: AppleLyricsDocument,
    uiState: AppleKaraokeUiState,
    currentPositionMs: Long,
    primaryTextColor: Color,
    unsungTextColor: Color,
    glowColor: Color,
    onSeek: (Long) -> Unit,
    onShareLine: (AppleLyricsLine) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }

    var lastUserInteractionTime by remember { mutableLongStateOf(0L) }
    var isUserScrolling by remember { mutableStateOf(false) }

    // Detect user dragging to pause autoscroll
    LaunchedEffect(listState.interactionSource) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    isUserScrolling = true
                    lastUserInteractionTime = System.currentTimeMillis()
                }
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    isUserScrolling = false
                    lastUserInteractionTime = System.currentTimeMillis()
                }
            }
        }
    }

    val lines = document.allLines
    val activeIndex = uiState.activeLineIndex

    // Check if document has multiple distinct agents (Duet)
    val isDuet = remember(document.agents) { document.agents.size > 1 }
    val primaryAgentId = remember(document.agents) { document.agents.keys.firstOrNull() }

    // Smooth autoscroll to active line
    LaunchedEffect(activeIndex, uiState.playbackEpoch) {
        if (activeIndex in lines.indices) {
            val now = System.currentTimeMillis()
            if (!isUserScrolling && now - lastUserInteractionTime >= USER_SCROLL_PAUSE_MS) {
                val currentLine = lines[activeIndex]
                val nextLine = lines.getOrNull(activeIndex + 1)
                val moveDuration = if (nextLine != null) {
                    appleLineMoveDuration(currentLine.endMs, nextLine.beginMs)
                } else {
                    550
                }

                // Scroll item near ACTIVE_LINE_BIAS (approx 28% from top)
                val targetOffset = -(screenHeightPx * ACTIVE_LINE_BIAS).toInt()
                listState.animateScrollToItem(
                    index = activeIndex,
                    scrollOffset = targetOffset
                )
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 32.dp,
                end = 32.dp,
                top = (config.screenHeightDp * ACTIVE_LINE_BIAS).dp,
                bottom = (config.screenHeightDp * 0.40f).dp
            )
        ) {
            itemsIndexed(
                items = lines,
                key = { _, line -> line.id }
            ) { index, line ->
                val isActive = index == activeIndex
                val isSecondaryAgent = isDuet && line.agentId != null && line.agentId != primaryAgentId

                AppleKaraokeLine(
                    line = line,
                    isActive = isActive,
                    activeMainPieceIds = if (isActive) uiState.activeMainPieceIds else emptySet(),
                    activeBgPieceIds = if (isActive) uiState.activeBgPieceIds else emptySet(),
                    currentPositionMs = currentPositionMs,
                    isDuet = isDuet,
                    isSecondaryDuetAgent = isSecondaryAgent,
                    primaryTextColor = primaryTextColor,
                    unsungTextColor = unsungTextColor,
                    glowColor = glowColor,
                    onLineClick = { onSeek(line.beginMs) },
                    onLineLongClick = { onShareLine(line) }
                )

                // Inter-line spacing (32dp)
                if (index < lines.size - 1) {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            // Interlude indicator if between songs or long instrumental
            if (uiState.isInterlude) {
                item(key = "apple_interlude") {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        AppleInterlude(color = primaryTextColor.copy(alpha = 0.85f))
                    }
                }
            }
        }
    }
}
