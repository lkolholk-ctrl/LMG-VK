package com.lmg.vk.ui.lyrics.apple

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.DragInteraction
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.lmg.vk.engine.lyrics.apple.AppleAgentType
import com.lmg.vk.engine.lyrics.apple.AppleLyricsDocument
import com.lmg.vk.engine.lyrics.apple.AppleLyricsLine
import com.lmg.vk.engine.lyrics.apple.AppleTimingType
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val USER_SCROLL_PAUSE_MS = 4000L
private const val ACTIVE_LINE_BIAS = 0.28f

@Composable
fun AppleKaraokeList(
    document: AppleLyricsDocument,
    uiState: AppleKaraokeUiState,
    currentPositionMs: Long,
    isPlaying: Boolean,
    showTranslations: Boolean,
    showPronunciations: Boolean,
    primaryTextColor: Color,
    unsungTextColor: Color,
    glowColor: Color,
    onSeek: (Long) -> Unit,
    onShareLine: (AppleLyricsLine) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val config = LocalConfiguration.current
    val lines = document.allLines
    val isStatic = document.timing == AppleTimingType.NONE
    val activeIndex = uiState.activeLineIndex
    val scrollTargetIndex = uiState.scrollTargetLineIndex
    val itemTranslations = remember(document) { mutableStateMapOf<Int, Float>() }
    var lastUserInteractionTime by remember { mutableLongStateOf(0L) }
    var isUserScrolling by remember { mutableStateOf(false) }
    var positionedOnce by remember(document) { mutableStateOf(false) }
    var lastEpoch by remember(document) { mutableLongStateOf(uiState.playbackEpoch) }

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

    // Apple relocation: capture old coordinates, snap the list to its new layout, then
    // animate inverse per-row translations back to zero. No smooth list scrolling.
    LaunchedEffect(
        scrollTargetIndex,
        uiState.playbackEpoch,
        isUserScrolling,
        lastUserInteractionTime,
    ) {
        if (scrollTargetIndex !in lines.indices) return@LaunchedEffect
        if (isUserScrolling) return@LaunchedEffect
        val elapsedSinceTouch = System.currentTimeMillis() - lastUserInteractionTime
        if (elapsedSinceTouch < USER_SCROLL_PAUSE_MS) {
            delay(USER_SCROLL_PAUSE_MS - elapsedSinceTouch)
        }
        while (listState.isScrollInProgress) delay(100L)

        val isDiscontinuity = uiState.playbackEpoch != lastEpoch
        lastEpoch = uiState.playbackEpoch
        if (!positionedOnce || isDiscontinuity) {
            listState.scrollToItem(scrollTargetIndex, 0)
            itemTranslations.clear()
            positionedOnce = true
            return@LaunchedEffect
        }

        // Preserve the actual on-screen position when a previous relocation is
        // interrupted by a fast next line (common in rap). LazyList offsets do not
        // include graphicsLayer translation, so fold it in before snapping layout.
        val oldOffsets = listState.layoutInfo.visibleItemsInfo
            .associate { info ->
                info.index to (info.offset.toFloat() + (itemTranslations[info.index] ?: 0f))
            }
        itemTranslations.clear()
        listState.scrollToItem(scrollTargetIndex, 0)
        withFrameNanos { }
        val newOffsets = listState.layoutInfo.visibleItemsInfo
            .associate { it.index to it.offset.toFloat() }
        val duration = if (scrollTargetIndex > 0) {
            appleLineMoveDuration(
                currentEnd = lines[scrollTargetIndex - 1].endMs,
                nextBegin = lines[scrollTargetIndex].beginMs,
            )
        } else {
            480
        }

        coroutineScope {
            newOffsets.forEach { (index, newOffset) ->
                val oldOffset = oldOffsets[index] ?: return@forEach
                val inverse = oldOffset - newOffset
                itemTranslations[index] = inverse
                launch {
                    if (index > scrollTargetIndex) {
                        delay(AppleEmphasisEngine.calculateCascadeDelayMs(index - scrollTargetIndex))
                    }
                    val animation = Animatable(inverse)
                    animation.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(duration, easing = AppleEmphasisEngine.ScrollEasing),
                    ) {
                        itemTranslations[index] = value
                    }
                    itemTranslations[index] = 0f
                }
            }
        }
    }

    val alignments = remember(lines, document.agents) {
        resolveAppleLineAlignments(lines, document)
    }
    val isDuet = remember(document.agents) { document.agents.size > 1 }
    val wordOffsetMs = if (document.timing == AppleTimingType.WORD) -100L else 0L

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 32.dp,
                end = 32.dp,
                top = (config.screenHeightDp * ACTIVE_LINE_BIAS).dp,
                bottom = (config.screenHeightDp * 0.40f).dp,
            ),
        ) {
            itemsIndexed(
                items = lines,
                key = { index, line -> "$index:${line.id}" },
            ) { index, line ->
                AppleKaraokeLine(
                    line = line,
                    isActive = isStatic || index == activeIndex,
                    alignment = alignments[index],
                    currentPositionMs = currentPositionMs,
                    isPlaying = isPlaying,
                    karaokeEnabled = document.timing == AppleTimingType.WORD,
                    playbackEpoch = uiState.playbackEpoch,
                    wordOffsetMs = wordOffsetMs,
                    isDuet = isDuet,
                    showTranslations = showTranslations,
                    showPronunciations = showPronunciations,
                    translationLanguage = document.translation,
                    pronunciationLanguage = document.pronunciation,
                    primaryTextColor = primaryTextColor,
                    unsungTextColor = unsungTextColor,
                    glowColor = glowColor,
                    onLineClick = {
                        if (!isStatic) onSeek(line.beginMs)
                    },
                    onLineLongClick = { onShareLine(line) },
                    modifier = Modifier.graphicsLayer {
                        translationY = itemTranslations[index] ?: 0f
                        clip = false
                    },
                )
                if (index < lines.lastIndex) Spacer(Modifier.height(32.dp))
            }
        }

        if (uiState.isInterlude) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = 32.dp,
                        top = (config.screenHeightDp * ACTIVE_LINE_BIAS).dp,
                    ),
                contentAlignment = Alignment.TopStart,
            ) {
                AppleInterlude(
                    eventKey = uiState.nextEventAtMs ?: uiState.playbackEpoch,
                    color = primaryTextColor.copy(alpha = 0.85f),
                )
            }
        }
    }
}

private fun resolveAppleLineAlignments(
    lines: List<AppleLyricsLine>,
    document: AppleLyricsDocument,
): List<AppleLineAlignment> {
    if (document.agents.size <= 1) {
        return List(lines.size) { AppleLineAlignment.START }
    }

    val resolved = ArrayList<AppleLineAlignment>(lines.size)

    // Direct Compose translation of Apple 6.5.2 C3463z.w0(). It deliberately
    // follows previous-line alignment, agent changes and text direction instead
    // of assigning fixed left/right sides to v1/v2.
    fun resolve(agentId: String, index: Int): AppleLineAlignment {
        val type = document.agents[agentId]?.type
        if (index <= 0) {
            return when (type) {
                AppleAgentType.PERSON -> AppleLineAlignment.END
                AppleAgentType.OTHER -> AppleLineAlignment.CENTER
                else -> AppleLineAlignment.START
            }
        }
        if (type == AppleAgentType.GROUP) return AppleLineAlignment.START

        val previousIndex = index - 1
        val previousAlignment = resolved.getOrNull(previousIndex) ?: AppleLineAlignment.START
        if (previousAlignment == AppleLineAlignment.START) {
            return resolve(agentId, previousIndex)
        }

        val previous = lines[previousIndex]
        if (agentId == previous.agentId) return previousAlignment

        val currentRtl = AppleLyricsScriptRules.isRtl(lines[index].mainText())
        val previousRtl = AppleLyricsScriptRules.isRtl(previous.mainText())
        val useEnd = if (previousAlignment != AppleLineAlignment.END) {
            previousRtl == currentRtl
        } else {
            previousRtl != currentRtl
        }
        return if (useEnd) AppleLineAlignment.END else AppleLineAlignment.CENTER
    }

    lines.forEachIndexed { index, line ->
        val agentId = line.agentId
        resolved += if (agentId.isNullOrBlank()) {
            AppleLineAlignment.START
        } else {
            resolve(agentId, index)
        }
    }
    return resolved
}

private fun AppleLyricsLine.mainText(): String = main.joinToString("") { it.text }
