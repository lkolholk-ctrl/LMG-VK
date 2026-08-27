package com.lmg.vk.ui.lyrics.apple

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lmg.vk.engine.lyrics.apple.AppleLyricsLine
import com.lmg.vk.engine.lyrics.apple.AppleLyricPiece

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppleKaraokeLine(
    line: AppleLyricsLine,
    isActive: Boolean,
    activeMainPieceIds: Set<String>,
    activeBgPieceIds: Set<String>,
    currentPositionMs: Long,
    isDuet: Boolean,
    isSecondaryDuetAgent: Boolean,
    primaryTextColor: Color,
    unsungTextColor: Color,
    glowColor: Color,
    onLineClick: () -> Unit,
    onLineLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Confirmed Apple Line Scale: active = 1.0, inactive = 0.98
    val lineScale by animateFloatAsState(
        targetValue = if (isActive) 1.0f else 0.98f,
        animationSpec = tween(durationMillis = 350, easing = AppleEmphasisEngine.StandardEasing),
        label = "LineScale_${line.id}"
    )

    // Inactive lines are slightly dimmed
    val lineAlpha by animateFloatAsState(
        targetValue = if (isActive) 1.0f else 0.40f,
        animationSpec = tween(durationMillis = 350),
        label = "LineAlpha_${line.id}"
    )

    val alignment = if (isSecondaryDuetAgent) Alignment.CenterEnd else Alignment.CenterStart
    val widthFraction = if (isDuet) 0.85f else 1.0f

    val mainPieces = remember(line.main) { line.main }
    val bgPieces = remember(line.background) { line.background }

    // Precalculate emphasis groups for main line
    val nonWsMain = remember(mainPieces) { mainPieces.filter { !it.isWhitespace } }
    val groupDurationMs = remember(nonWsMain) {
        if (nonWsMain.isEmpty()) 0L else (nonWsMain.maxOf { it.endMs } - nonWsMain.minOf { it.beginMs }).coerceAtLeast(0L)
    }
    val isEligible = remember(groupDurationMs, nonWsMain) {
        val chars = nonWsMain.sumOf { it.text.length }
        AppleEmphasisEngine.isCoreEligible(isBackground = false, durationMs = groupDurationMs, charCount = chars)
    }
    val targetScale = remember(isEligible, groupDurationMs) {
        if (isEligible) AppleEmphasisEngine.calculateTargetScale(groupDurationMs) else 1.0f
    }
    val staggerMs = remember(isEligible, groupDurationMs, nonWsMain) {
        if (isEligible) AppleEmphasisEngine.calculateStaggerMs(groupDurationMs, nonWsMain.size) else 0L
    }
    val returnDelayMs = remember(isEligible, groupDurationMs, nonWsMain) {
        if (isEligible) AppleEmphasisEngine.calculateReturnBaseDelayMs(groupDurationMs, nonWsMain.size) else 0L
    }
    val animDurationMs = remember(groupDurationMs) {
        AppleEmphasisEngine.calculateAnimationDurationMs(groupDurationMs)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(line.id) {
                detectTapGestures(
                    onTap = { onLineClick() },
                    onLongPress = { onLineLongClick() }
                )
            }
            .graphicsLayer {
                scaleX = lineScale
                scaleY = lineScale
                alpha = lineAlpha
                transformOrigin = if (isSecondaryDuetAgent) TransformOrigin(1.0f, 0.5f) else TransformOrigin(0.0f, 0.5f)
                clip = false
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(widthFraction)
                .align(alignment)
        ) {
            // Main Vocal Line (34sp)
            FlowRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                mainPieces.forEachIndexed { index, piece ->
                    val isPieceActive = isActive && piece.id in activeMainPieceIds
                    val sungFraction = when {
                        !isActive -> if (currentPositionMs >= line.endMs) 1.0f else 0.0f
                        currentPositionMs >= piece.endMs -> 1.0f
                        currentPositionMs <= piece.beginMs -> 0.0f
                        else -> ((currentPositionMs - piece.beginMs).toFloat() / piece.durationMs.toFloat()).coerceIn(0f, 1f)
                    }

                    AppleKaraokePiece(
                        piece = piece,
                        isActive = isPieceActive,
                        sungFraction = sungFraction,
                        targetScale = targetScale,
                        staggerMs = index * staggerMs,
                        returnDelayMs = returnDelayMs,
                        animationDurationMs = animDurationMs,
                        displacementX = 0f,
                        fontSize = 34.sp,
                        baseColor = unsungTextColor,
                        sungColor = primaryTextColor,
                        glowColor = glowColor
                    )
                }
            }

            // Background Vocal Channel (22sp, 22dp top margin)
            if (bgPieces.isNotEmpty()) {
                Spacer(modifier = Modifier.height(22.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    bgPieces.forEachIndexed { index, piece ->
                        val isPieceActive = isActive && piece.id in activeBgPieceIds
                        val sungFraction = when {
                            !isActive -> if (currentPositionMs >= line.endMs) 1.0f else 0.0f
                            currentPositionMs >= piece.endMs -> 1.0f
                            currentPositionMs <= piece.beginMs -> 0.0f
                            else -> ((currentPositionMs - piece.beginMs).toFloat() / piece.durationMs.toFloat()).coerceIn(0f, 1f)
                        }

                        AppleKaraokePiece(
                            piece = piece,
                            isActive = isPieceActive,
                            sungFraction = sungFraction,
                            targetScale = 1.0f, // Background vocals excluded from core long-note emphasis
                            staggerMs = 0L,
                            returnDelayMs = 0L,
                            animationDurationMs = 300L,
                            displacementX = 0f,
                            fontSize = 22.sp,
                            baseColor = unsungTextColor.copy(alpha = 0.70f),
                            sungColor = primaryTextColor.copy(alpha = 0.90f),
                            glowColor = glowColor
                        )
                    }
                }
            }
        }
    }
}
