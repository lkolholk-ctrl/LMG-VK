package com.lmg.vk.ui.lyrics.apple

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.lmg.vk.engine.lyrics.apple.AppleLyricsLine
import com.lmg.vk.engine.lyrics.apple.AppleLyricPiece
import com.lmg.vk.ui.theme.VkSansDisplay

enum class AppleLineAlignment { START, END, CENTER }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppleKaraokeLine(
    line: AppleLyricsLine,
    isActive: Boolean,
    alignment: AppleLineAlignment,
    currentPositionMs: Long,
    isPlaying: Boolean,
    karaokeEnabled: Boolean,
    playbackEpoch: Long,
    wordOffsetMs: Long,
    isDuet: Boolean,
    showTranslations: Boolean,
    showPronunciations: Boolean,
    translationLanguage: String?,
    pronunciationLanguage: String?,
    primaryTextColor: Color,
    unsungTextColor: Color,
    glowColor: Color,
    onLineClick: () -> Unit,
    onLineLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lineScale by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.98f,
        animationSpec = tween(350, easing = AppleEmphasisEngine.StandardEasing),
        label = "AppleLineScale_${line.id}",
    )
    val lineAlpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.55f,
        animationSpec = tween(350),
        label = "AppleLineAlpha_${line.id}",
    )

    val lineText = remember(line.main) { line.main.joinToString("") { it.text } }
    val widthFraction = if (isDuet) 0.85f else 1f
    val translation = remember(line.translation, translationLanguage) {
        line.translation.preferredLanguageText(translationLanguage)
    }
    val selectedPronunciationPieces = remember(line.pronunciation, pronunciationLanguage) {
        line.pronunciation.preferredLanguagePieces(pronunciationLanguage)
    }
    val pronunciation = remember(selectedPronunciationPieces) {
        selectedPronunciationPieces.joinToString("") { it.text }.trim()
    }
    val bgTranslation = remember(line.translationBackground, translationLanguage) {
        line.translationBackground.preferredLanguageText(translationLanguage)
    }
    val selectedBgPronunciationPieces = remember(
        line.pronunciationBackground,
        pronunciationLanguage,
    ) {
        line.pronunciationBackground.preferredLanguagePieces(pronunciationLanguage)
    }
    val bgPronunciation = remember(selectedBgPronunciationPieces) {
        selectedBgPronunciationPieces.joinToString("") { it.text }.trim()
    }
    val usePronunciationAsMain = showPronunciations && pronunciation.isNotBlank() &&
        !pronunciation.equals(lineText, ignoreCase = true)
    val displayedMainPieces = if (usePronunciationAsMain) selectedPronunciationPieces else line.main
    val displayedMainText = if (usePronunciationAsMain) pronunciation else lineText
    val mainGroups = remember(displayedMainPieces) { buildAppleRenderGroups(displayedMainPieces) }
    val mainDirection = if (AppleLyricsScriptRules.isRtl(displayedMainText)) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }
    // Apple w0() returns semantic VIEW_START/VIEW_END. Convert that semantic
    // alignment to a physical Compose column edge using the line direction.
    val horizontalAlignment = when (alignment) {
        AppleLineAlignment.START -> if (mainDirection == LayoutDirection.Rtl) {
            Alignment.End
        } else {
            Alignment.Start
        }
        AppleLineAlignment.END -> if (mainDirection == LayoutDirection.Rtl) {
            Alignment.Start
        } else {
            Alignment.End
        }
        AppleLineAlignment.CENTER -> Alignment.CenterHorizontally
    }
    val transformOrigin = when (horizontalAlignment) {
        Alignment.Start -> TransformOrigin(0f, 0.5f)
        Alignment.End -> TransformOrigin(1f, 0.5f)
        else -> TransformOrigin.Center
    }
    val backgroundText = remember(line.background) { line.background.joinToString("") { it.text }.trim() }
    val useBgPronunciation = showPronunciations && bgPronunciation.isNotBlank() &&
        !bgPronunciation.equals(backgroundText, ignoreCase = true)
    val displayedBackgroundPieces = if (useBgPronunciation) {
        selectedBgPronunciationPieces
    } else {
        line.background
    }
    val backgroundGroups = remember(displayedBackgroundPieces) {
        buildAppleRenderGroups(displayedBackgroundPieces)
    }
    val displayedBackgroundText = remember(displayedBackgroundPieces) {
        displayedBackgroundPieces.joinToString("") { it.text }
    }
    val backgroundDirection = if (AppleLyricsScriptRules.isRtl(displayedBackgroundText)) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }

    Column(
        horizontalAlignment = horizontalAlignment,
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(line.id) {
                detectTapGestures(
                    onTap = { onLineClick() },
                    onLongPress = { onLineLongClick() },
                )
            }
            // Blur is present from first composition; it does not wait for a line event.
            .blur(
                radius = if (isActive) 0.dp else 2.dp,
                edgeTreatment = BlurredEdgeTreatment.Unbounded,
            )
            .graphicsLayer {
                scaleX = lineScale
                scaleY = lineScale
                alpha = lineAlpha
                this.transformOrigin = transformOrigin
                clip = false
            },
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides mainDirection) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(widthFraction),
            ) {
                mainGroups.forEach { group ->
                    AppleKaraokeGroup(
                        group = group,
                        currentPositionMs = currentPositionMs,
                        isPlaying = isPlaying,
                        karaokeEnabled = karaokeEnabled,
                        playbackEpoch = playbackEpoch,
                        wordOffsetMs = wordOffsetMs,
                        isBackground = false,
                        fontSize = 34.sp,
                        sungColor = primaryTextColor,
                        unsungAlpha = (unsungTextColor.alpha / primaryTextColor.alpha)
                            .coerceIn(0f, 1f),
                        glowColor = glowColor,
                    )
                }
            }
        }

        if (usePronunciationAsMain) {
            AppleSecondaryLine(lineText, widthFraction)
        }
        if (showTranslations && translation.isNotBlank() &&
            !translation.equals(displayedMainText, ignoreCase = true) &&
            !translation.equals(lineText, ignoreCase = true)
        ) {
            AppleSecondaryLine(translation, widthFraction)
        }

        if (backgroundGroups.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            CompositionLocalProvider(LocalLayoutDirection provides backgroundDirection) {
                FlowRow(modifier = Modifier.fillMaxWidth(widthFraction)) {
                    backgroundGroups.forEach { group ->
                        AppleKaraokeGroup(
                            group = group,
                            currentPositionMs = currentPositionMs,
                            isPlaying = isPlaying,
                            karaokeEnabled = karaokeEnabled,
                            playbackEpoch = playbackEpoch,
                            wordOffsetMs = wordOffsetMs,
                            isBackground = true,
                            fontSize = 22.sp,
                            sungColor = primaryTextColor.copy(alpha = 0.35f),
                            // DST_IN multiplies the source alpha. Use the ratio so
                            // background vocals land at absolute white alpha 0.18.
                            unsungAlpha = 0.18f / 0.35f,
                            glowColor = glowColor,
                        )
                    }
                }
            }
            if (useBgPronunciation && backgroundText.isNotBlank()) {
                AppleSecondaryLine(backgroundText, widthFraction)
            }
            if (showTranslations && bgTranslation.isNotBlank() &&
                !bgTranslation.equals(bgPronunciation, ignoreCase = true)
            ) {
                AppleSecondaryLine(bgTranslation, widthFraction)
            }
        }
    }
}

@Composable
private fun AppleSecondaryLine(text: String, widthFraction: Float) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.82f),
        fontFamily = VkSansDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        modifier = Modifier.fillMaxWidth(widthFraction),
    )
}

private fun List<AppleLyricPiece>.preferredLanguageText(language: String?): String {
    if (isEmpty()) return ""
    val exact = language?.let { requested ->
        filter { it.language.equals(requested, ignoreCase = true) }
            .takeIf { it.isNotEmpty() }
    }
    return (exact ?: this).joinToString("") { it.text }.trim()
}

private fun List<AppleLyricPiece>.preferredLanguagePieces(language: String?): List<AppleLyricPiece> {
    if (isEmpty() || language.isNullOrBlank()) return this
    return filter { it.language.equals(language, ignoreCase = true) }
        .takeIf { it.isNotEmpty() }
        ?: this
}
