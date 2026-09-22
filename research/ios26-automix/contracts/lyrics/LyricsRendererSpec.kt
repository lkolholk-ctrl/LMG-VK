// SPDX-License-Identifier: Clean-room research contract. No Apple source code copied.
package apple.music.contracts.lyrics

/*
 * LyricsRendererSpec.kt — implementation constants for the LyricsX renderer (iOS 26, MEE + Music.app).
 *
 * PROVENANCE (per constant):
 *  - deepseek_analysis/10_final_closure/LYRICS_RENDERER_IMPLEMENTATION_SPEC.md §2.1–§2.6, §3.1, §4
 *  - deepseek_analysis/09_appos/APPOS_ANIMATIONS.md §A.5 (cross-confirmation)
 *  - deepseek_analysis/10_final_closure/FINAL_RED_TEAM_AUDIT.md §6 (independent verification)
 *  - deepseek_analysis/10_final_closure/ANDROID_CLEANROOM_CONTRACT.md §1.9
 *
 * DISPUTED constants from LYR §4 are intentionally NOT part of this file; see README.md
 * forbidden list. Values listed there must never be hardcoded as renderer behavior.
 *
 * STATUS LEGEND: [EXACT] / [STRONG_INFERENCE] / [PARTIAL] / [UNKNOWN] / [PORT DESIGN] — see README.md.
 */

/** UIKit cubic animator parameters. */
data class CubicAnimatorSpec(val duration: Double, val cp1x: Double, val cp1y: Double, val cp2x: Double, val cp2y: Double)

/** Spring parameters (mass/stiffness/damping), m/k/c order note is STRONG_INFERENCE per LYR §2.2 #17. */
data class SpringSpec(val mass: Double, val stiffness: Double, val damping: Double)

object LyricsRendererSpec {

    // ---------------------------------------------------------------------------------------
    // Timing manager (LYR §2.1)
    // ---------------------------------------------------------------------------------------

    /** `maxSelectedLines` = 2. Specs offset +0xc0; condition in SyncedLyricsManager.update. [EXACT] */
    const val MAX_SELECTED_LINES = 2

    /** `maxEndTimeOffset` = 0.5 s (`0x3fe0000000000000`), offset +0xb8. [EXACT] */
    const val MAX_END_TIME_OFFSET_SECONDS = 0.5

    /**
     * `lineDelay` = 0.05 s (`0x3fa999999999999a`); Music.app vpfi `0x100c2b4e0`.
     * Used as the per-index cascade delay (`startAnimationAfterDelay`, factor `lineDelay * (index - 1)`),
     * NOT the TTML inter-line gap. [EXACT]
     */
    const val LINE_DELAY_SECONDS = 0.05

    /** `animationHeadstart` = 0.1 s (`0x3fb999999999999a`), offset +0x1c0. [EXACT] */
    const val ANIMATION_HEADSTART_SECONDS = 0.1

    /** `finishLineAnimationDuration` / `lineFinishProgressAnimationDuration` = 0.25 s (`0x3fd0000000000000`). [EXACT] */
    const val FINISH_LINE_ANIMATION_DURATION_SECONDS = 0.25

    /** Timing-provider hysteresis: repeat tap window 1.0 s; provider delta 0.5 s. [EXACT] (LYR §2.1 #6). */
    const val TIMING_HYSTERESIS_TAP_SECONDS = 1.0
    const val TIMING_HYSTERESIS_PROVIDER_DELTA_SECONDS = 0.5

    /** Cascaded line appearance: `lineDelay * (index - 1)` between neighboring lines. [STRONG_INFERENCE] Idem. */
    fun cascadeDelay(index: Int): Double = LINE_DELAY_SECONDS * (index - 1)

    // ---------------------------------------------------------------------------------------
    // Appearance animators (LYR §2.2)
    // ---------------------------------------------------------------------------------------

    /** `opacityAnimator()`: duration 0.12, control points (0.33,0) / (0.2,0.1). MEE `0x10045df1c`. [EXACT] */
    val OPACITY_ANIMATOR = CubicAnimatorSpec(0.12, 0.33, 0.0, 0.2, 0.1)

    /** line-change / scroll animator: duration 0.28, control points (0.17,0) / (0.83,1.0). MEE `0x1004739e0`. [EXACT] */
    val LINE_CHANGE_ANIMATOR = CubicAnimatorSpec(0.28, 0.17, 0.0, 0.83, 1.0)

    /** Blur uses the same cubic animator as (10). LYR §2.2 #19. [EXACT] */
    val BLUR_ANIMATOR = OPACITY_ANIMATOR

    /** `emphasizingScaleRange` = 1.0 .. 1.14 (`0x3ff23d70a3d70a3d`), lerp lower + f*(upper-lower). [EXACT] */
    const val EMPHASIS_SCALE_LOWER = 1.0
    const val EMPHASIS_SCALE_UPPER = 1.14

    /** `deselectedTransform` line scale 0.98 (`0x3fef5c28f5c28f5c`). [EXACT] */
    const val DESELECTED_SCALE = 0.98

    /** `backgroundVocalsDeselectedTransform` scale 0.9 (`0x3feccccccccccccd`). [EXACT] */
    const val BACKGROUND_VOCALS_DESELECTED_SCALE = 0.9

    /** Selected line transform = identity (`(1,0,0,1,0,0)`). [EXACT] (LYR §2.2 #15). */
    const val SELECTED_SCALE = 1.0

    /** Highlight curve on: m 1.0 / k 322 / c 24; fade duration 0.2, delay 0. [EXACT] */
    val HIGHLIGHT_ON = SpringSpec(1.0, 322.0, 24.0)
    const val HIGHLIGHT_ON_FADE_SECONDS = 0.2
    const val HIGHLIGHT_ON_DELAY_SECONDS = 0.0

    /** Highlight curve off: m 2.0 / k 300 / c 50; duration 0.3, delay 0.1. [EXACT] */
    val HIGHLIGHT_OFF = SpringSpec(2.0, 300.0, 50.0)
    const val HIGHLIGHT_OFF_FADE_SECONDS = 0.3
    const val HIGHLIGHT_OFF_DELAY_SECONDS = 0.1

    /** Other springs (numbers EXACT; m/k/c order STRONG_INFERENCE): lift 1/14/7; tap 2/260/50; bgVocals 1/30/9. */
    val LIFT_SPRING = SpringSpec(1.0, 14.0, 7.0)
    val TAP_SPRING = SpringSpec(2.0, 260.0, 50.0)
    val BACKGROUND_VOCALS_SPRING = SpringSpec(1.0, 30.0, 9.0)

    /** `backgroundVocalsSpring(showing: false)`: dampingRatio 1.0, response 0.2; `growSyllable` dampingRatio 1.0. [EXACT] */
    const val BACKGROUND_VOCALS_HIDDEN_DAMPING_RATIO = 1.0
    const val BACKGROUND_VOCALS_HIDDEN_RESPONSE = 0.2
    const val GROW_SYLLABLE_DAMPING_RATIO = 1.0

    /** `emphasizingScale` for a word: `lower + factor * (upper - lower)` = `1.0 + factor * 0.14`. [EXACT] */
    fun emphasisScale(factor: Double): Double =
        EMPHASIS_SCALE_LOWER + factor * (EMPHASIS_SCALE_UPPER - EMPHASIS_SCALE_LOWER)

    /**
     * `syllableBySyllableLineChangeSpringTimingParameters(gap:)` (MEE `0x100460188`, EXACT):
     * `t = clamp((min(gap, 0.75) - 0.2) / 0.55, 0..1)`;
     * `dampingRatio = (1 - t) * 0.12 + 0.78`; `response = t * 0.27 + 0.48`.
     */
    fun syllableBySyllableSpring(gap: Double): Pair<Double, Double> {
        val t = ((minOf(gap, 0.75) - 0.2) / 0.55).coerceIn(0.0, 1.0)
        val dampingRatio = (1.0 - t) * 0.12 + 0.78
        val response = t * 0.27 + 0.48
        return dampingRatio to response
    }

    /** Blur: radius = min(distance, 4.0) (`0x4010000000000000`). [EXACT] (distance is INFERRED as ordinal). */
    const val BLUR_RADIUS_CAP = 4.0

    /** Separate fixed blur branch: 3.0 (`0x4008000000000000`). [EXACT] */
    const val BLUR_RADIUS_FIXED = 3.0

    /** `lineBlurEnabled` flag at Specs +0x291; selected/boundary lines get setBlurRadius(0). [EXACT] */
    const val DEFAULT_LINE_BLUR_ENABLED = true

    /** CALayer filter keys used by the blur path. [EXACT] strings. */
    const val BLUR_FILTER_GAUSSIAN = "gaussianBlur.inputRadius"
    const val BLUR_FILTER_BRIGHTNESS = "colorBrightness.inputAmount"

    // ---------------------------------------------------------------------------------------
    // Scroll targeting (LYR §2.3)
    // ---------------------------------------------------------------------------------------

    /** Static content insets: top 22.0 (`0x4036000000000000`), bottom 30.0 (`0x403e000000000000`). [EXACT] */
    const val STATIC_TOP_CONTENT_INSET = 22.0
    const val STATIC_BOTTOM_CONTENT_INSET = 30.0

    /**
     * `contentOffset(for:)` branch WITHOUT payload (EXACT):
     * `offset.y = rect.minY - scrollView.contentInset.top`.
     */
    fun contentOffsetWithoutPayload(rectMinY: Double, contentInsetTop: Double): Double =
        rectMinY - contentInsetTop

    /**
     * `contentOffset(for:)` branch WITH `selectedLinePosition` payload (structure EXACT,
     * enum semantics INFERRED -> STRONG_INFERENCE):
     * `offset.y = rect.maxY - (containerHeight - rect.height) * 0.5 - payloadValue`,
     * where for one payload bit the substitute is `scrollView.frame.height`.
     */
    fun contentOffsetWithPayload(
        rectMaxY: Double,
        rectHeight: Double,
        containerHeight: Double,
        payloadValue: Double,
    ): Double = rectMaxY - (containerHeight - rectHeight) * 0.5 - payloadValue

    // ---------------------------------------------------------------------------------------
    // Karaoke / word progress (LYR §2.4)
    // ---------------------------------------------------------------------------------------

    /** Line progress: `elapsedTime - spatialOffset` when `isPlayingSpatial`. [EXACT] */
    fun lineProgress(elapsedTime: Double, spatialOffset: Double, isPlayingSpatial: Boolean): Double =
        if (isPlayingSpatial) elapsedTime - spatialOffset else elapsedTime

    /**
     * Jitter guard in `SBS_TextContentView.setProgress` (`0x100447974`):
     * skip updates when the new progress is smaller than the old one by less than 0.5 s;
     * larger rewinds are applied; forward updates always apply. [EXACT]
     */
    fun shouldApplyProgress(oldProgress: Double, newProgress: Double): Boolean =
        newProgress >= oldProgress || (oldProgress - newProgress) >= 0.5

    /** `LineProgressGradientView.updateColors`: colors = [color, color.withAlpha(0)] — 2 stops. [EXACT] */
    const val GRADIENT_STOP_COUNT = 2

    /** `lineProgressionGradientFeather` = 30.0 pt (`0x403e000000000000`), Specs +0x1f8. [EXACT] */
    const val GRADIENT_FEATHER_POINTS = 30.0

    /**
     * Feather strip geometry (EXACT branch logic; direction enum INFERRED):
     * direction bit 0 == 0 -> L2R, `startX 0` / `endX 1`; bit 1 -> R2L, `startX 1` / `endX 0`.
     * feather band `w = featherWidth`; `x = width - featherWidth` for L2R, `x = 0` for R2L.
     */
    fun featherBandX(width: Double, featherWidth: Double, rightToLeft: Boolean): Double =
        if (rightToLeft) 0.0 else width - featherWidth

    /** Glow: radius 5.0; `glowRange` 0.0..0.4; spring `dampingRatio 1.0`, `response = min(duration, 3.0)`. [EXACT] */
    const val GLOW_RADIUS = 5.0
    const val GLOW_RANGE_LOWER = 0.0
    const val GLOW_RANGE_UPPER = 0.4
    const val GLOW_RESPONSE_CAP_SECONDS = 3.0

    /** Glow scale factor: `lower + (upper - lower) * factor`. [EXACT] */
    fun glowFactor(factor: Double): Double = GLOW_RANGE_LOWER + (GLOW_RANGE_UPPER - GLOW_RANGE_LOWER) * factor

    /**
     * Glow stagger: `step = min(duration / count * 0.4, 0.4)` s; return phase `duration / (count / 2)`.
     * Formula structure STRONG_INFERENCE (LYR §2.4 #38). Division by zero is guarded.
     */
    fun glowStaggerSeconds(duration: Double, count: Int): Double =
        if (count <= 0) 0.0 else minOf(duration / count * 0.4, 0.4)

    /** `syllableLift` = 2.0 pt (`0x4000000000000000`), Specs +0x270. Value [EXACT]; application point INFERRED. */
    const val SYLLABLE_LIFT_POINTS = 2.0

    // ---------------------------------------------------------------------------------------
    // Seek (LYR §2.6)
    // ---------------------------------------------------------------------------------------

    /** Fast seek path (mode == 2): animator duration 0.25 s, curve 3 (`0x3fd0000000000000`). [EXACT] */
    const val SEEK_FAST_DURATION_SECONDS = 0.25
    const val SEEK_FAST_CURVE = 3

    // ---------------------------------------------------------------------------------------
    // Instrumental indicator (LYR §2.5)
    // ---------------------------------------------------------------------------------------

    object Instrumental {
        /** Number of dots = 3 (Specs +0x250; `createDots` `0x10044bdcc`). [EXACT] */
        const val DOT_COUNT = 3

        /** Dot length (diameter) = 12.0 pt (`0x4028000000000000`); cornerRadius = dotLength / 2. [EXACT] */
        const val DOT_LENGTH = 12.0

        /** Margin between dots = 8.0 pt (`0x4020000000000000`). [EXACT] */
        const val DOT_MARGIN = 8.0

        /** View height = 40.0 pt (`0x4044000000000000`). [EXACT] */
        const val VIEW_HEIGHT = 40.0

        /** Initial dot alpha = 0.1 (`dotInitialAlpha_WZ`). [EXACT] */
        const val DOT_INITIAL_ALPHA = 0.1

        /** Breathing transforms: breathOut 0.9, breathIn 1.2, fadeOutZoomIn 1.2, fadeOutZoomOut 0.2. [EXACT] */
        const val TRANSFORM_BREATH_OUT = 0.9
        const val TRANSFORM_BREATH_IN = 1.2
        const val TRANSFORM_FADE_OUT_ZOOM_IN = 1.2
        const val TRANSFORM_FADE_OUT_ZOOM_OUT = 0.2

        /** Dot layout: total = dotLength*count + margin*(count-1); x by alignment (1 center / 2 right). [EXACT] */
        fun totalWidth(count: Int): Double =
            DOT_LENGTH * count + DOT_MARGIN * (count - 1).coerceAtLeast(0)

        fun startX(width: Double, count: Int, alignment: Int): Double = when (alignment) {
            1 -> (width - totalWidth(count)) * 0.5
            2 -> width - totalWidth(count)
            else -> 0.0
        }

        fun dotY(height: Double): Double = height * 0.5 - DOT_LENGTH * 0.5

        /**
         * `reset()` formulas (EXACT):
         * `end = line.endTime - 1.8`;
         * `dur = end - line.startTime`;
         * `breathDuration = (dur / trunc(dur * 0.25)) * 0.5`;
         * `dotFadeInDuration = (end - (line.startTime + 1.0)) / dotCount`.
         */
        fun resetEnd(lineEndTime: Double): Double = lineEndTime - 1.8

        fun breathDuration(lineStartTime: Double, lineEndTime: Double): Double {
            val end = resetEnd(lineEndTime)
            val dur = end - lineStartTime
            val divisor = kotlin.math.truncate(dur * 0.25)
            return if (divisor == 0.0) 0.0 else (dur / divisor) * 0.5
        }

        fun dotFadeInDuration(lineStartTime: Double, lineEndTime: Double, dotCount: Int): Double {
            if (dotCount <= 0) return 0.0
            val end = resetEnd(lineEndTime)
            return (end - (lineStartTime + 1.0)) / dotCount
        }

        /** Fade-in animator duration 0.8 s (`0x3fe999999999999a`), stagger 0.06 s (`0x3fae147ae147ae14`). [EXACT] */
        const val FADE_IN_DURATION = 0.8
        const val FADE_IN_STAGGER = 0.06

        /** Breath UIView animation: `duration = breathDuration - 0.4`, `delay = 0.2`. [EXACT constants] */
        const val BREATH_DURATION_OFFSET = -0.4
        const val BREATH_DELAY = 0.2

        /**
         * `fadeIn` "already visible" count: `k = min((long)((elapsed - (start + 1.0)) / dotFadeInDuration) + 1, 3)`.
         * `update(elapsed)` caps the same formula at `dotCount`. [EXACT]
         */
        fun visibleDotCount(
            elapsedTime: Double,
            lineStartTime: Double,
            dotFadeInDuration: Double,
            cap: Int,
        ): Int {
            if (dotFadeInDuration <= 0.0) return 0
            val raw = ((elapsedTime - (lineStartTime + 1.0)) / dotFadeInDuration).toLong() + 1
            return minOf(raw, cap.toLong()).coerceAtLeast(0L).toInt()
        }

        /** Fade-out cue condition: `start + 1.0 < elapsed && all faded in && elapsed < end - 1.8`. [EXACT] */
        fun shouldFadeOut(
            elapsedTime: Double,
            lineStartTime: Double,
            lineEndTime: Double,
            dotsFadedIn: Int,
            dotCount: Int,
        ): Boolean =
            lineStartTime + 1.0 < elapsedTime &&
                dotsFadedIn == dotCount &&
                elapsedTime < resetEnd(lineEndTime)

        /** AnchorPoint offsets on first/last dot: `+1.3` / `-1.3` (`0x3ff4cccccccccccd`). Values [EXACT]; axis INFERRED. */
        const val ANCHOR_OFFSET_FIRST = 1.3
        const val ANCHOR_OFFSET_LAST = -1.3
    }

    // ---------------------------------------------------------------------------------------
    // Open/close transition (LYR §2.7) — app-side reference only
    // ---------------------------------------------------------------------------------------

    /**
     * App-side `LyricsSharingAnimationController` spring (m 1.0 / k 396.0 / c 32.0).
     * Role (sharing sheet vs lyrics open) is INFERRED — use only as an orientation value. [STRONG_INFERENCE]
     */
    val LYRICS_SHARING_SPRING = SpringSpec(1.0, 396.0, 32.0)
}
