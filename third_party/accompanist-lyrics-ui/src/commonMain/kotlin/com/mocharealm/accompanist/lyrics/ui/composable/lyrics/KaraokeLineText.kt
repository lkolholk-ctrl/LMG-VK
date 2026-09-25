// LMG VK modification: cached spatial wave, baseline emphasis and bounded redraw intervals.
package com.mocharealm.accompanist.lyrics.ui.composable.lyrics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.ui.utils.LayerPaint
import com.mocharealm.accompanist.lyrics.ui.utils.isRtl
import kotlin.math.roundToInt

/**
 * Creates a horizontal gradient brush that represents the karaoke progress.
 * The gradient moves from inactive color to active color based on the current time.
 *
 * @param lineLayout The layout information for syllables in the line.
 * @param currentTimeMs The current playback time in milliseconds.
 * @param isRtl Whether the layout direction is Right-to-Left.
 */
private fun DrawScope.createLineGradientBrush(
    rowData: RowRenderData,
    currentTimeMs: Int,
    isRtl: Boolean
): Brush {
    val activeColor = Color.White
    val inactiveColor = Color.White.copy(alpha = 0.2f)

    val lineLayout = rowData.rowLayouts
    val totalMinX = rowData.totalMinX
    val totalMaxX = rowData.totalMaxX
    val totalWidth = rowData.totalWidth

    if (totalWidth <= 0f) {
        val isFinished = currentTimeMs >= lineLayout.last().syllable.end
        val color = if (isFinished) activeColor else inactiveColor
        return SolidColor(color)
    }

    val firstSyllableStart = rowData.firstSyllableStart
    val lastSyllableEnd = rowData.lastSyllableEnd

    val lineProgress = run {
        if (currentTimeMs <= firstSyllableStart) return Brush.horizontalGradient(
            listOf(inactiveColor, inactiveColor)
        )
        if (currentTimeMs >= lastSyllableEnd) return Brush.horizontalGradient(
            listOf(activeColor, activeColor)
        )

        val activeSyllableLayout = lineLayout.find {
            currentTimeMs in it.syllable.start until it.syllable.end
        }

        val currentPixelPosition = when {
            activeSyllableLayout != null -> {
                val syllableProgress = activeSyllableLayout.syllable.progress(currentTimeMs)
                if (isRtl) {
                    activeSyllableLayout.position.x + activeSyllableLayout.width * (1f - syllableProgress)
                } else {
                    activeSyllableLayout.position.x + activeSyllableLayout.width * syllableProgress
                }
            }

            else -> {
                val lastFinished = lineLayout.lastOrNull { currentTimeMs >= it.syllable.end }
                if (isRtl) {
                    lastFinished?.position?.x ?: totalMaxX
                } else {
                    lastFinished?.let { it.position.x + it.width } ?: totalMinX
                }
            }
        }
        ((currentPixelPosition - totalMinX) / totalWidth).coerceIn(0f, 1f)
    }

    val (fadeStart, fadeEnd) = fillFeatherStops(lineProgress, totalWidth, density, isRtl)

    val colorStops = if (isRtl) {
        arrayOf(
            0.0f to inactiveColor,
            fadeStart.coerceIn(0f, 1f) to inactiveColor,
            fadeEnd.coerceIn(0f, 1f) to activeColor,
            1.0f to activeColor
        )
    } else {
        arrayOf(
            0.0f to activeColor,
            fadeStart.coerceIn(0f, 1f) to activeColor,
            fadeEnd.coerceIn(0f, 1f) to inactiveColor,
            1.0f to inactiveColor
        )
    }

    return Brush.horizontalGradient(
        colorStops = colorStops,
        startX = totalMinX,
        endX = totalMaxX
    )
}

/**
 * Draws a multi-row lyrics line into the canvas.
 * Handles row wrapping, padding, and applying the karaoke progress gradient.
 *
 * @param lineLayouts The pre-calculated layout of syllables, organized by rows.
 * @param currentTimeMs The current playback time in milliseconds.
 * @param color The base text color.
 * @param blendMode The blend mode to use for drawing.
 * @param isRtl Whether the layout direction is Right-to-Left.
 * @param showDebugRectangles Whether to draw debug outlines around glyphs.
 */
fun DrawScope.drawLyricsLine(
    rowRenderData: List<RowRenderData>,
    currentTimeMs: Int,
    color: Color,
    blendMode: BlendMode,
    isRtl: Boolean,
    showDebugRectangles: Boolean = false,
    showPhonetic: Boolean = true,
    lineEndMs: Int = rowRenderData.maxOfOrNull { it.lastSyllableEnd } ?: 0
) {
    rowRenderData.forEach { rowData ->
        val lastSyllableEnd = rowData.lastSyllableEnd

        if (currentTimeMs >= lastSyllableEnd) {
            drawRowText(
                rowData,
                color,
                showDebugRectangles,
                currentTimeMs,
                showPhonetic,
                lineEndMs
            )
            return@forEach
        }

        drawIntoCanvas { canvas ->
            val layerBounds = rowData.layerBounds
            canvas.saveLayer(layerBounds, LayerPaint)

            drawRowText(
                rowData,
                color,
                showDebugRectangles,
                currentTimeMs,
                showPhonetic,
                lineEndMs
            )

            val progressBrush = createLineGradientBrush(rowData, currentTimeMs, isRtl)
            drawRect(
                brush = progressBrush,
                topLeft = layerBounds.topLeft,
                size = layerBounds.size,
                blendMode = BlendMode.DstIn
            )
            canvas.restore()
        }
    }
}

/**
 * Draws text for a single row, handling word and character animations.
 *
 * @param rowLayouts The layouts for syllables in this row.
 * @param drawColor The color to draw the text with.
 * @param blendMode The blend mode to use.
 * @param showDebugRectangles Whether to show debug bounds.
 * @param currentTimeMs Current playback time.
 * @param showPhonetic Whether to show syllable-level phonetics.
 */
private fun DrawScope.drawRowText(
    rowData: RowRenderData,
    drawColor: Color,
    showDebugRectangles: Boolean,
    currentTimeMs: Int,
    showPhonetic: Boolean,
    lineEndMs: Int
) {
    val time = currentTimeMs.toLong()
    val release = lineEndMs.toLong()
    val motion = rowData.motion
    motion.update(time, release)
    val glyphTint = ColorFilter.tint(drawColor.copy(alpha = 1f))
    for (index in motion.glyphs.indices) {
        val glyph = motion.glyphs[index]
        if (!glyph.visible) continue
        val emphasis = motion.emphasis[index]
        val scale = 1f + 0.14f * emphasis
        val liftPx = motion.lifts[index] * density
        withTransform({
            translate(left = motion.offsets[index], top = liftPx)
            scale(scale, scale, glyph.pivot)
        }) {
            drawStableLyricGlyph(glyph.text, glyph.origin, glyphTint, drawColor.alpha,
                motion.shadowAlphas[index])
            if (showDebugRectangles) drawRect(
                Color.Red, glyph.origin, Size(glyph.text.size.width.toFloat(), glyph.text.size.height.toFloat()),
                style = Stroke(1f)
            )
        }
    }

    rowData.rowLayouts.forEachIndexed { index, layout ->
        if (layout.charLayouts != null && (!showPhonetic || layout.phoneticLayoutResult == null)) return@forEachIndexed
        val lift = wordLiftDp(time, motion.runStarts[index], release).dp.toPx()
        if (layout.charLayouts == null) {
            // Keep connected scripts and combining sequences shaped as complete text runs.
            val position = layout.position.copy(y = layout.position.y + lift)
            drawText(layout.textLayoutResult, color = drawColor, topLeft = position)
            if (showDebugRectangles) drawRect(
                Color.Green, position,
                Size(layout.textLayoutResult.size.width.toFloat(), layout.textLayoutResult.size.height.toFloat()),
                style = Stroke(2f)
            )
        }
        if (showPhonetic) layout.phoneticLayoutResult?.let { phonetic ->
            drawText(
                phonetic, color = drawColor.copy(alpha = 0.4f),
                topLeft = Offset(layout.position.x, layout.position.y + lift - phonetic.size.height + 4.dp.toPx())
            )
        }
    }
}

/**
 * Renders a single karaoke line, capable of handling multi-row wrapping.
 *
 * This composable pre-calculates the text layout and then
 * renders the frames using an efficient Canvas drawing strategy. It handles:
 * - Text measurement and line breaking
 * - Syllable and character-level animations (bounce, rise, swell)
 * - Karaoke fill gradient application
 *
 * @param line The karaoke line data.
 * @param currentTimeProvider Provider for the current playback time.
 * @param modifier Modifier for the layout.
 * @param normalLineTextStyle Style for normal lines.
 * @param accompanimentLineTextStyle Style for accompaniment lines.
 * @param phoneticTextStyle Style for phonetics.
 * @param activeColor Color for the active (sung) portion of text.
 * @param blendMode Blend mode for drawing.
 * @param showDebugRectangles Debug flag for layout bounds.
 * @param showTranslation Whether to show line-level translations.
 * @param showPhonetic Whether to show phonetics (both syllable and line level).
 * @param precalculatedLayouts Optional pre-calculated layouts (optimization).
 * @param isDuoView Whether this line is part of a duet view.
 * @param textMeasurer Text measurer for layout (default provided).
 */
@Composable
fun KaraokeLineText(
    line: KaraokeLine,
    currentTimeProvider: () -> Int,
    modifier: Modifier = Modifier,
    normalLineTextStyle: TextStyle = LocalTextStyle.current,
    accompanimentLineTextStyle: TextStyle = LocalTextStyle.current,
    phoneticTextStyle: TextStyle = LocalTextStyle.current,
    activeColor: Color = Color.White,
    blendMode: BlendMode = BlendMode.SrcOver,
    showDebugRectangles: Boolean = false,
    showTranslation: Boolean = true,
    showPhonetic: Boolean = true,
    precalculatedLayouts: List<SyllableLayout>? = null,
    isDuoView: Boolean = false,
    textMeasurer: TextMeasurer = rememberTextMeasurer(),
    preparedLineLayouts: Map<KaraokeLine, List<SyllableLayout>>? = null,
) {
    val isLineRtl = remember(line.syllables) { line.syllables.any { it.content.isRtl() } }

    val isRightAligned = remember(line.alignment, isLineRtl) {
        when (line.alignment) {
            KaraokeAlignment.Start, KaraokeAlignment.Unspecified -> isLineRtl
            KaraokeAlignment.End -> !isLineRtl
        }
    }

    val translationTextAlign = remember(isRightAligned) {
        if (isRightAligned) TextAlign.End else TextAlign.Start
    }

    val columnHorizontalAlignment = remember(isRightAligned) {
        if (isRightAligned) Alignment.End else Alignment.Start
    }

    val mainLine = line as? KaraokeLine.MainKaraokeLine
    val accompanimentLinesBeforeMain =
        mainLine?.accompanimentLines?.filter { it.start < line.start }.orEmpty()
    val accompanimentLinesAfterMain =
        mainLine?.accompanimentLines?.filter { it.start >= line.start }.orEmpty()

    @Composable
    fun AccompanimentLines(isBefore: Boolean, accompanimentLines: List<KaraokeLine>) {
        accompanimentLines.forEach { bgLine ->
            val isAccompanimentVisible by remember(bgLine) {
                derivedStateOf {
                    val currentTime = currentTimeProvider()
                    // Use a simplified visibility range for nested lines, or pass ranges down
                    currentTime >= (bgLine.start - 600) && currentTime <= (bgLine.end + 600)
                }
            }

            AnimatedVisibility(
                visible = isAccompanimentVisible,
                enter = scaleIn(
                    tween(600), transformOrigin = TransformOrigin(
                        if (isRightAligned) 1f else 0f, if (isBefore) 1f else 0f
                    )
                ) + fadeIn(tween(600)) + slideInVertically(
                    tween(
                        600
                    )
                ) + expandVertically(tween(600), if (isBefore) Alignment.Top else Alignment.Bottom),
                exit = scaleOut(
                    tween(600), transformOrigin = TransformOrigin(
                        if (isRightAligned) 1f else 0f, if (isBefore) 1f else 0f
                    )
                ) + fadeOut(tween(600)) + slideOutVertically(
                    tween(
                        600
                    )
                ) + shrinkVertically(tween(600), if (isBefore) Alignment.Top else Alignment.Bottom),
            ) {
                LyricsLineItem(
                    isFocused = true,
                    isRightAligned = isRightAligned,
                    onLineClicked = { },
                    onLinePressed = { },
                    blurRadius = { 0f },
                    blendMode = blendMode,
                    activeAlpha = 0.6f,
                    inactiveAlpha = 0.2f
                ) {
                    KaraokeLineText(
                        line = bgLine,
                        currentTimeProvider = currentTimeProvider,
                        normalLineTextStyle = normalLineTextStyle,
                        accompanimentLineTextStyle = accompanimentLineTextStyle,
                        phoneticTextStyle = phoneticTextStyle,
                        activeColor = activeColor,
                        blendMode = blendMode,
                        showDebugRectangles = showDebugRectangles,
                        showTranslation = showTranslation,
                        showPhonetic = showPhonetic,
                        textMeasurer = textMeasurer,
                        precalculatedLayouts = preparedLineLayouts?.getValue(bgLine),
                        preparedLineLayouts = preparedLineLayouts,
                    )
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(
            vertical = 8.dp, horizontal = if (line is KaraokeLine.AccompanimentKaraokeLine) 0.dp
            else 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = columnHorizontalAlignment
    ) {
        AccompanimentLines(true, accompanimentLinesBeforeMain)

        BoxWithConstraints {
            val density = LocalDensity.current
            val availableWidthPx = with(density) { maxWidth.toPx() }

            val textStyle = remember(
                line is KaraokeLine.AccompanimentKaraokeLine, normalLineTextStyle, accompanimentLineTextStyle
            ) {
                val baseStyle =
                    if (line is KaraokeLine.AccompanimentKaraokeLine) accompanimentLineTextStyle
                    else normalLineTextStyle
                baseStyle.copy(textDirection = TextDirection.Content)
            }

            val spaceWidth = remember(textMeasurer, textStyle) {
                textMeasurer.measure(" ", textStyle).size.width.toFloat()
            }

            val processedSyllables = remember(line.syllables, line.alignment) {
                if (line.alignment == KaraokeAlignment.End) {
                    line.syllables.dropLastWhile { it.content.isBlank() }
                } else {
                    line.syllables
                }
            }

            val initialLayouts by remember(
                precalculatedLayouts, processedSyllables, textMeasurer, textStyle, phoneticTextStyle, spaceWidth
            ) {
                derivedStateOf {
                    precalculatedLayouts ?: measureSyllablesAndDetermineAnimation(
                        syllables = processedSyllables,
                        textMeasurer = textMeasurer,
                        style = textStyle,
                        phoneticStyle = phoneticTextStyle,
                        isAccompanimentLine = line is KaraokeLine.AccompanimentKaraokeLine,
                        spaceWidth = spaceWidth
                    )
                }
            }

            val wrappedLines by remember(initialLayouts, availableWidthPx, textMeasurer, textStyle) {
                derivedStateOf {
                    calculateBalancedLines(
                        syllableLayouts = initialLayouts,
                        availableWidthPx = availableWidthPx,
                        textMeasurer = textMeasurer,
                        style = textStyle
                    )
                }
            }

            val lineHeight = remember(textMeasurer, textStyle) {
                textMeasurer.measure("M", textStyle).size.height.toFloat()
            }

            val phoneticHeight = remember(textMeasurer, phoneticTextStyle) {
                textMeasurer.measure("M", phoneticTextStyle).size.height.toFloat()
            }

            val finalLineLayouts = remember(
                wrappedLines, availableWidthPx, lineHeight, isLineRtl, isRightAligned, showPhonetic
            ) {
                calculateStaticLineLayout(
                    wrappedLines = wrappedLines,
                    isLineRightAligned = isRightAligned,
                    canvasWidth = availableWidthPx,
                    lineHeight = lineHeight,
                    phoneticHeight = if (showPhonetic) phoneticHeight else 0f,
                    isRtl = isLineRtl
                )
            }

            val rowRenderData = remember(finalLineLayouts, showPhonetic, density) {
                calculateRowRenderData(
                    lineLayouts = finalLineLayouts,
                    showPhonetic = showPhonetic,
                    density = density.density
                )
            }

            val hasPhonetics = remember(initialLayouts, showPhonetic) {
                showPhonetic && initialLayouts.any { it.phoneticLayoutResult != null }
            }

            val totalHeight = remember(wrappedLines, lineHeight, hasPhonetics, phoneticHeight) {
                var height = lineHeight * wrappedLines.size
                if (hasPhonetics) {
                    height += phoneticHeight * wrappedLines.size
                }
                height
            }

            val latestPosition = rememberUpdatedState(currentTimeProvider)
            val drawPosition = remember(line, latestPosition) {
                derivedStateOf(structuralEqualityPolicy()) {
                    lyricAnimationPositionMs(latestPosition.value(), line.start, line.end)
                }
            }
            Canvas(modifier = Modifier.size(maxWidth, (totalHeight.roundToInt() + 8).toDp())) {
                val time = drawPosition.value
                drawLyricsLine(
                    rowRenderData = rowRenderData,
                    currentTimeMs = time,
                    color = activeColor,
                    blendMode = blendMode,
                    isRtl = isLineRtl,
                    showDebugRectangles = showDebugRectangles,
                    showPhonetic = showPhonetic,
                    lineEndMs = line.end
                )
            }
        }

        if (showTranslation) {
            line.translation?.let { translation ->
                Text(
                    text = translation,
                    color = activeColor.copy(0.4f),
                    modifier = Modifier.graphicsLayer {
                        this.blendMode = blendMode
                    },
                    textAlign = translationTextAlign
                )
            }
        }

        if (showPhonetic) {
            line.phonetic?.let { phonetic ->
                Text(
                    text = phonetic,
                    style = phoneticTextStyle,
                    color = activeColor.copy(alpha = 0.6f),
                    modifier = Modifier.graphicsLayer {
                        this.blendMode = blendMode
                    },
                    textAlign = translationTextAlign
                )
            }
        }
        AccompanimentLines(false, accompanimentLinesAfterMain)
    }
}

@Composable
private fun Int.toDp(): Dp = with(LocalDensity.current) { this@toDp.toDp() }
