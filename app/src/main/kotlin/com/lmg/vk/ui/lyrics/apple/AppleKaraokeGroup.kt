package com.lmg.vk.ui.lyrics.apple

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit

@Composable
fun AppleKaraokeGroup(
    group: AppleRenderGroup,
    currentPositionMs: Long,
    isPlaying: Boolean,
    karaokeEnabled: Boolean,
    playbackEpoch: Long,
    wordOffsetMs: Long,
    isBackground: Boolean,
    fontSize: TextUnit,
    sungColor: Color,
    unsungAlpha: Float,
    glowColor: Color,
    modifier: Modifier = Modifier,
) {
    val startMs = (group.beginMs + wordOffsetMs).coerceAtLeast(0L)
    val endMs = group.endMs.coerceAtLeast(startMs + 1L)
    val timedCount = group.timedPieces.size.coerceAtLeast(1)
    val coreEligible = karaokeEnabled && AppleEmphasisEngine.isCoreEligible(
        isBackground = isBackground,
        durationMs = group.durationMs,
        charCount = group.text.length,
    ) && AppleLyricsScriptRules.allowsCoreStretch(group.text)
    val targetScale = if (coreEligible) {
        AppleEmphasisEngine.calculateTargetScale(group.durationMs)
    } else {
        1f
    }
    val staggerMs = AppleEmphasisEngine.calculateStaggerMs(group.durationMs, timedCount)
    val animationDurationMs = AppleEmphasisEngine.calculateAnimationDurationMs(group.durationMs)
    val returnBaseMs = AppleEmphasisEngine.calculateReturnBaseDelayMs(group.durationMs, timedCount)
    val animationEndMs = if (coreEligible) {
        maxOf(
            endMs,
            startMs + returnBaseMs + (timedCount - 1) * staggerMs + animationDurationMs,
        )
    } else {
        endMs
    }
    val animationRunId = if (currentPositionMs >= startMs) startMs else Long.MIN_VALUE
    val clock = remember(group.id) { Animatable(currentPositionMs.toFloat()) }

    LaunchedEffect(group.id, playbackEpoch, isPlaying, animationRunId, karaokeEnabled) {
        clock.snapTo(currentPositionMs.toFloat())
        if (
            karaokeEnabled && isPlaying && currentPositionMs >= startMs &&
            currentPositionMs < animationEndMs
        ) {
            clock.animateTo(
                targetValue = animationEndMs.toFloat(),
                animationSpec = tween(
                    durationMillis = (animationEndMs - currentPositionMs)
                        .coerceAtLeast(1L)
                        .coerceAtMost(Int.MAX_VALUE.toLong())
                        .toInt(),
                    easing = LinearEasing,
                ),
            )
        }
    }

    val widths = remember(group.id) { mutableStateMapOf<String, Float>() }
    val isRtl = remember(group.text) { AppleLyricsScriptRules.isRtl(group.text) }

    val scales = FloatArray(group.pieces.size) { pieceIndex ->
        val piece = group.pieces[pieceIndex]
        if (!coreEligible || piece.isWhitespace) return@FloatArray 1f
        val timedIndex = group.timedPieces.indexOfFirst { it.id == piece.id }.coerceAtLeast(0)
        val attackStart = startMs + timedIndex * staggerMs
        val attackFraction = ((clock.value - attackStart.toFloat()) / animationDurationMs.toFloat())
            .coerceIn(0f, 1f)
        val returnStart = startMs + returnBaseMs + timedIndex * staggerMs
        val returnFraction = ((clock.value - returnStart.toFloat()) / animationDurationMs.toFloat())
            .coerceIn(0f, 1f)
        val attack = AppleEmphasisEngine.StandardEasing.transform(attackFraction)
        val release = AppleEmphasisEngine.ReturnEasing.transform(returnFraction)
        1f + (targetScale - 1f) * attack * (1f - release)
    }

    val expansions = FloatArray(group.pieces.size) { index ->
        AppleEmphasisEngine.calculateExpansionPx(
            scale = scales[index],
            baseWidthPx = widths[group.pieces[index].id] ?: 0f,
        )
    }
    val translations = AppleEmphasisEngine.calculateAppleNeighborTranslations(
        expansionPx = expansions,
        isRtl = isRtl,
    )
    val pieceProgress = FloatArray(group.pieces.size) { index ->
        val piece = group.pieces[index]
        val pieceStart = (piece.beginMs + wordOffsetMs).coerceAtLeast(0L)
        val pieceEnd = piece.endMs.coerceAtLeast(pieceStart + 1L)
        when {
            !karaokeEnabled -> 1f
            piece.isWhitespace -> if (clock.value >= endMs.toFloat()) 1f else 0f
            clock.value <= pieceStart.toFloat() -> 0f
            clock.value >= pieceEnd.toFloat() -> 1f
            else -> ((clock.value - pieceStart.toFloat()) / (pieceEnd - pieceStart).toFloat())
                .coerceIn(0f, 1f)
        }
    }
    val totalWidth = group.pieces.sumOf { (widths[it.id] ?: 0f).toDouble() }.toFloat()
    val maskProgress = if (totalWidth > 0f) {
        group.pieces.indices.sumOf { index ->
            ((widths[group.pieces[index].id] ?: 0f) * pieceProgress[index]).toDouble()
        }.toFloat() / totalWidth
    } else if (!karaokeEnabled || clock.value >= endMs.toFloat()) {
        1f
    } else {
        0f
    }

    Row(
        modifier = modifier.appleKaraokeGradient(
            sungFraction = maskProgress,
            unsungAlpha = unsungAlpha,
            isRtl = isRtl,
        ),
        verticalAlignment = Alignment.Bottom,
    ) {
        group.pieces.forEachIndexed { index, piece ->
            val pieceStart = (piece.beginMs + wordOffsetMs).coerceAtLeast(0L)
            val pieceEnd = piece.endMs.coerceAtLeast(pieceStart + 1L)
            AppleKaraokePiece(
                piece = piece,
                scale = scales[index],
                displacementX = translations[index],
                liftActive = karaokeEnabled && !piece.isWhitespace &&
                    clock.value >= pieceStart.toFloat() && clock.value < pieceEnd.toFloat(),
                playbackEpoch = playbackEpoch,
                fontSize = fontSize,
                sungColor = sungColor,
                glowColor = glowColor,
                onMeasuredWidth = { width -> widths[piece.id] = width },
            )
        }
    }
}
