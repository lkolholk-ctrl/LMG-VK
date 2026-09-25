// Modified for LMG VK: clock/scroll guards and audio-driven waiting over merged vocal gaps.
package com.mocharealm.accompanist.lyrics.ui.composable.lyrics

import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastRoundToInt
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.mocharealm.accompanist.lyrics.ui.utils.isRtl
import com.mocharealm.accompanist.lyrics.ui.utils.modifier.springPlacement
import kotlin.math.absoluteValue

internal data class FocusState(
    val firstIndex: Int,
    val allIndices: List<Int>,
    val waiting: WaitingInterval?
)

/**
 * A comprehensive lyrics view that supports Karaoke and Synced lyrics with advanced rendering.
 *
 * This composable handles:
 * - Scrolling and auto-scrolling to the current line
 * - Rendering karaoke lines with syllable-level timing and animations
 * - Rendering synced lines
 * - Displaying breathing dots during instrumental interludes
 * - Determining active and accompaniment lines
 *
 * @param listState The scroll state for the lazy list.
 * @param lyrics The lyrics data to display.
 * @param currentPosition A lambda returning the current playback position in milliseconds.
 * @param onLineClicked Callback when a line is clicked (seek to position).
 * @param onLinePressed Callback when a line is long-pressed (share/menu).
 * @param modifier The modifier to apply to the layout.
 * @param normalLineTextStyle The style for normal text lines.
 * @param accompanimentLineTextStyle The style for accompaniment/background vocals lines.
 * @param textColor The primary text color.
 * @param breathingDotsDefaults Styling defaults for the breathing dots.
 * @param blendMode The blend mode used for rendering text (e.g., [BlendMode.Plus] for glowing effects).
 * @param useBlurEffect Whether to apply blur effect to non-active lines.
 * @param offset The vertical padding/offset at the start and end of the list.
 * @param showDebugRectangles Debug flag to draw bounding boxes around glyphs.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun KaraokeLyricsView(
    listState: LazyListState,
    lyrics: SyncedLyrics,
    currentPosition: () -> Int,
    onLineClicked: (ISyncedLine) -> Unit,
    onLinePressed: (ISyncedLine) -> Unit,
    modifier: Modifier = Modifier,
    normalLineTextStyle: TextStyle = LocalTextStyle.current.copy(
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        textMotion = TextMotion.Animated,
    ),
    accompanimentLineTextStyle: TextStyle = LocalTextStyle.current.copy(
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        textMotion = TextMotion.Animated,
    ),
    textColor: Color = Color.White,
    breathingDotsDefaults: KaraokeBreathingDotsDefaults = KaraokeBreathingDotsDefaults(),
    phoneticTextStyle: TextStyle = normalLineTextStyle.copy(
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
    ),
//    TODO: expose it
//    verticalFadeBrush: Brush = Brush.verticalGradient(
//        0f to Color.White.copy(0f),
//        0.05f to Color.White,
//        0.6f to Color.White,
//        1f to Color.White.copy(0f)
//    ),
    blendMode: BlendMode = BlendMode.Plus,
    useBlurEffect: Boolean = true,
    showTranslation: Boolean = true,
    showPhonetic: Boolean = true,
    offset: Dp = 32.dp,
    keepAliveZone: Dp = 100.dp,
    blurDelta: Float = 3f,
    showDebugRectangles: Boolean = false,
    scrollResetKey: Any = Unit,
    bottomContent: (@Composable () -> Unit)? = null,
) {
    val density = LocalDensity.current
    val stableNormalTextStyle = remember(normalLineTextStyle) { normalLineTextStyle }
    val stableAccompanimentTextStyle =
        remember(accompanimentLineTextStyle) { accompanimentLineTextStyle }
    val stablePhoneticTextStyle = remember(phoneticTextStyle) { phoneticTextStyle }
    val stableOffset = remember(offset) { offset }
    val stableOffsetPx =
        remember(stableOffset, density) { with(density) { stableOffset.toPx().fastRoundToInt() } }
    val keepAliveZonePx = with(density) { keepAliveZone.toPx() }
    val stableBlendMode = remember(blendMode) { blendMode }

    val fontFamilyResolver = LocalFontFamilyResolver.current
    val layoutDirection = LocalLayoutDirection.current
    val locales = LocaleList.current
    val layoutKey = remember(
        lyrics, stableNormalTextStyle, stableAccompanimentTextStyle, stablePhoneticTextStyle,
        density, layoutDirection, fontFamilyResolver, locales,
    ) {
        LyricsLayoutKey(
            lyrics, stableNormalTextStyle, stableAccompanimentTextStyle, stablePhoneticTextStyle,
            density.density, density.fontScale, layoutDirection, fontFamilyResolver, locales,
        )
    }
    var preparedLayouts by remember(layoutKey) {
        mutableStateOf(
            if (lyrics.lines.none { it is KaraokeLine }) emptyMap()
            else com.lmg.vk.debug.PlayerOpeningTrace.measure("layout_cache_lookup") {
                PreparedLyricsLayouts.peek(layoutKey)
            }
        )
    }
    LaunchedEffect(layoutKey) {
        com.lmg.vk.debug.PlayerStartupTrace.mark(
            "lyricsLayouts=" + if (preparedLayouts == null) "cacheMiss" else "cacheHit"
        )
        if (preparedLayouts == null) {
            val trace = com.lmg.vk.debug.PlayerStartupTrace.begin("lyrics_prelayout")
            try {
                preparedLayouts = PreparedLyricsLayouts.prepare(layoutKey)
            } finally {
                trace?.end()
            }
        }
    }
    val layoutCache = preparedLayouts
    if (layoutCache == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = textColor.copy(alpha = 0.6f),
                strokeWidth = 2.dp,
            )
        }
        return
    }

    androidx.compose.runtime.SideEffect {
        com.lmg.vk.debug.PlayerOpeningTrace.markOnce("prepared_body_committed")
    }
    val latestPosition by rememberUpdatedState(currentPosition)
    val timeProvider: () -> Int = remember { { latestPosition() } }
    val currentTimeMs = timeProvider

    val accompanimentToMainMap = remember(lyrics.lines) {
        val map = mutableMapOf<Int, Int>()
        val mainLinesIndices = lyrics.lines.indices.filter { index ->
            val line = lyrics.lines[index]
            line !is KaraokeLine || line !is KaraokeLine.AccompanimentKaraokeLine
        }
        if (mainLinesIndices.isNotEmpty()) {
            lyrics.lines.forEachIndexed { index, line ->
                if (line is KaraokeLine && line is KaraokeLine.AccompanimentKaraokeLine) {
                    // Find the main line that is closest in time (either the one just before or just after)
                    val beforeIdx = mainLinesIndices.findLast { it <= index }
                    val afterIdx = mainLinesIndices.find { it >= index }

                    val anchorIndex = when {
                        beforeIdx != null && afterIdx != null -> {
                            val distBefore =
                                (line.start - lyrics.lines[beforeIdx].start).absoluteValue
                            val distAfter =
                                (lyrics.lines[afterIdx].start - line.start).absoluteValue
                            if (distBefore <= distAfter) beforeIdx else afterIdx
                        }

                        beforeIdx != null -> beforeIdx
                        afterIdx != null -> afterIdx
                        else -> mainLinesIndices.first()
                    }
                    map[index] = anchorIndex
                }
            }
        }
        map
    }
    val effectiveEndTimes = remember(lyrics.lines) {
        IntArray(lyrics.lines.size) { index ->
            val line = lyrics.lines[index]
            var maxEnd = line.end

            if (line is KaraokeLine.MainKaraokeLine) {
                line.accompanimentLines?.forEach { acc ->
                    if (acc.end > maxEnd) maxEnd = acc.end
                }
            }
            maxEnd
        }
    }

    val waitingWindows = remember(lyrics.lines) { waitingIntervals(lyrics.lines) }
    val waitingByLine = remember(waitingWindows) { waitingWindows.groupBy { it.nextLineIndex } }
    val waitingSlotPx = with(density) { (breathingDotsDefaults.size + 40.dp).roundToPx() }

    val lyricsFocusState by remember(lyrics, effectiveEndTimes, accompanimentToMainMap, waitingWindows) {
        derivedStateOf {
            val time = currentTimeMs()
            val activeIndex = lyrics.lines.indices.find { idx ->
                time >= lyrics.lines[idx].start && time < effectiveEndTimes[idx]
            }

            val first = if (activeIndex != null) {
                activeIndex
            } else {
                val nextIdx = lyrics.lines.indexOfFirst { it.start > time }
                if (nextIdx != -1) nextIdx else lyrics.lines.lastIndex
            }

            val base = lyrics.lines.indices.filter { index ->
                time >= lyrics.lines[index].start && time < effectiveEndTimes[index]
            }
            val result = base.toMutableSet()
            base.forEach { index ->
                val line = lyrics.lines.getOrNull(index)
                if (line is KaraokeLine && line is KaraokeLine.AccompanimentKaraokeLine) {
                    accompanimentToMainMap[index]?.let { result.add(it) }
                }
            }

            val waiting = waitingWindows.firstOrNull { it.contains(time) }
            FocusState(waiting?.nextLineIndex ?: first, result.toList().sorted(), waiting)
        }
    }

    val scrollInCode = remember { mutableStateOf(false) }

    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    var pauseAutoScroll by remember(lyrics) { mutableStateOf(false) }
    LaunchedEffect(isDragged) {
        if (isDragged) {
            pauseAutoScroll = true
        } else if (pauseAutoScroll) {
            delay(4000)
            while (listState.isScrollInProgress) delay(100)
            pauseAutoScroll = false
        }
    }
    LaunchedEffect(scrollResetKey) { pauseAutoScroll = false }
    LaunchedEffect(lyrics, listState, stableOffsetPx, keepAliveZonePx, scrollResetKey, waitingByLine, waitingSlotPx) {
        snapshotFlow {
            val firstIndex = lyricsFocusState.firstIndex
            val windows = waitingByLine[firstIndex].orEmpty()
            val waiting = lyricsFocusState.waiting
            val leadingSlots = if (waiting == null) windows.size else windows.indexOf(waiting).coerceAtLeast(0)
            Triple(firstIndex to leadingSlots * waitingSlotPx, pauseAutoScroll, listState.layoutInfo.totalItemsCount)
        }.collectLatest { (anchor, paused, count) ->
            val (firstIndex, contentOffsetPx) = anchor
            if (paused || isDragged || firstIndex !in lyrics.lines.indices || count == 0) return@collectLatest
            try {
                scrollInCode.value = true
                val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == firstIndex }
                if (target != null) {
                    listState.scrollBy(
                        target.offset + contentOffsetPx - (listState.layoutInfo.viewportStartOffset + stableOffsetPx + keepAliveZonePx)
                    )
                } else {
                    listState.scrollToItem(firstIndex, contentOffsetPx)
                }
            } finally {
                scrollInCode.value = false
            }
        }
    }
    LookaheadScope {
        Crossfade(lyrics to layoutCache) { (lyrics, layoutCache) ->
            Box(modifier = modifier.clipToBounds()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawWithCache {
                            val topFade = (20.dp.toPx() / size.height.coerceAtLeast(1f)).coerceAtMost(0.25f)
                            val bottomFade = (100.dp.toPx() / size.height.coerceAtLeast(1f)).coerceAtMost(0.45f)
                            val fadeBrush = Brush.verticalGradient(
                                0f to Color.Transparent,
                                topFade to Color.Black,
                                1f - bottomFade to Color.Black,
                                1f to Color.Transparent
                            )
                            onDrawWithContent {
                                drawContent()
                                drawRect(
                                    brush = fadeBrush,
                                    blendMode = BlendMode.DstIn
                                )
                            }
                        }
                        .layout { measurable, constraints ->
                            val extraHeightPx = (keepAliveZone * 2).roundToPx()

                            val placeable = measurable.measure(
                                constraints.copy(
                                    maxHeight = constraints.maxHeight + extraHeightPx
                                )
                            )

                            layout(constraints.maxWidth, constraints.maxHeight) {
                                placeable.place(0, -(keepAliveZone.roundToPx()))
                            }
                        },
                    contentPadding = PaddingValues(vertical = stableOffset + keepAliveZone)
                ) {
                    itemsIndexed(
                        items = lyrics.lines,
                        key = { index, line -> "${line.start}-${line.end}-$index" }
                    ) { index, line ->
                        val isCurrentFocusLine = index in lyricsFocusState.allIndices
                        val isLineRtl =
                            when (line) {
                                is KaraokeLine -> {
                                    remember(line.syllables) { line.syllables.any { it.content.isRtl() } }
                                }

                                else -> false
                            }
                        val isLineRightAligned = when (line) {
                            is KaraokeLine -> {
                                remember { line.alignment == KaraokeAlignment.End }
                            }

                            else -> false
                        }
                        val isVisualRightAligned = remember(isLineRightAligned, isLineRtl) {
                            if (isLineRightAligned) !isLineRtl
                            else isLineRtl
                        }

                        val distanceWeightState = remember(useBlurEffect, lyricsFocusState) {
                            derivedStateOf {
                                val start = lyricsFocusState.allIndices.firstOrNull() ?: lyricsFocusState.firstIndex
                                val end = lyricsFocusState.allIndices.lastOrNull() ?: lyricsFocusState.firstIndex
                                maxOf(0, start - index, index - end)
                            }
                        }

                        val dynamicStiffness by remember(distanceWeightState.value) {
                            derivedStateOf {
                                (120f - (distanceWeightState.value * 20f)).coerceAtLeast(20f)
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .springPlacement(
                                    this@LookaheadScope,
                                    "${line.start}-${line.end}-$index",
                                    isDragged || pauseAutoScroll,
                                    stiffness = dynamicStiffness
                                ),
                            horizontalAlignment = if (isVisualRightAligned) Alignment.End else Alignment.Start
                        ) {
                            // Instrumental space stays in the layout; only the follow anchor changes at vocal entry.
                            waitingByLine[index].orEmpty().forEach { waiting ->
                                KaraokeBreathingDots(
                                    alignment = if (isVisualRightAligned) KaraokeAlignment.End else KaraokeAlignment.Start,
                                    startTimeMs = waiting.startMs,
                                    endTimeMs = waiting.endMs,
                                    currentTimeProvider = timeProvider,
                                    defaults = breathingDotsDefaults,
                                    isRtl = isLineRtl
                                )
                            }

                            val blurRadiusState = if (useBlurEffect) animateFloatAsState(
                                targetValue = (
                                        if (!pauseAutoScroll && distanceWeightState.value > 0 && (!listState.isScrollInProgress || scrollInCode.value)) {
                                            distanceWeightState.value * blurDelta
                                        } else 0f),
                                animationSpec = tween(300),
                            ) else remember { mutableStateOf(0f) }

                            when (line) {
                                is KaraokeLine -> {
                                    if (line is KaraokeLine.MainKaraokeLine) {
                                        LyricsLineItem(
                                            isFocused = isCurrentFocusLine,
                                            isRightAligned = isVisualRightAligned,
                                            onLineClicked = { onLineClicked(line) },
                                            onLinePressed = { onLinePressed(line) },
                                            blurRadius = { blurRadiusState.value },
                                            blendMode = stableBlendMode,
                                        ) {
                                            KaraokeLineText(
                                                line = line,
                                                currentTimeProvider = timeProvider,
                                                normalLineTextStyle = stableNormalTextStyle,
                                                accompanimentLineTextStyle = stableAccompanimentTextStyle,
                                                phoneticTextStyle = stablePhoneticTextStyle,
                                                activeColor = textColor,
                                                blendMode = stableBlendMode,
                                                showDebugRectangles = showDebugRectangles,
                                                showTranslation = showTranslation,
                                                showPhonetic = showPhonetic,
                                                precalculatedLayouts = layoutCache.getValue(line),
                                                preparedLineLayouts = layoutCache
                                            )
                                        }
                                    }
                                }

                                is SyncedLine -> {
                                    val isLineRtl = remember(line.content) { line.content.isRtl() }
                                    LyricsLineItem(
                                        isFocused = isCurrentFocusLine,
                                        isRightAligned = isLineRtl,
                                        onLineClicked = { onLineClicked(line) },
                                        onLinePressed = { onLinePressed(line) },
                                        blurRadius = { blurRadiusState.value },
                                        blendMode = stableBlendMode,
                                    ) {
                                        SyncedLineText(
                                            line = line,
                                            isLineRtl = isLineRtl,
                                            textStyle = stableNormalTextStyle.copy(lineHeight = 1.2.em),
                                            textColor = textColor,
                                            showTranslation = showTranslation,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (bottomContent != null) {
                        item("LyricsFooter") {
                            val lastFocusedIndex = lyricsFocusState.allIndices.lastOrNull()
                                ?: lyricsFocusState.firstIndex
                            val distance = (lyrics.lines.size - lastFocusedIndex).coerceAtLeast(0)
                            Box(Modifier.fillMaxWidth().springPlacement(
                                this@LookaheadScope,
                                "LyricsFooter",
                                isDragged || pauseAutoScroll,
                                stiffness = (120f - distance * 20f).coerceAtLeast(20f),
                            )) {
                                bottomContent()
                            }
                        }
                    }
                    item("BottomSpacing") {
                        Spacer(
                            modifier = Modifier.fillMaxWidth().height(2000.dp)
                        )
                    }
                }
            }
        }
    }
}
