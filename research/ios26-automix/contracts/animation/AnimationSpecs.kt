// SPDX-License-Identifier: Clean-room research contract. No Apple source code copied.
package apple.music.contracts.animation

/*
 * AnimationSpecs.kt — Now Playing / sheet / marquee / backdrop / artwork-morph animation constants
 * (iOS 26), clean-room.
 *
 * PROVENANCE:
 *  - deepseek_analysis/10_final_closure/NOW_PLAYING_ANIMATION_SPEC.md (canonical: UIKit sheet, backdrop, morph, PPT)
 *  - deepseek_analysis/09_appos/APPOS_ANIMATIONS.md §A.2/§A.3/§A.5 (marquee, app cross-checks, pill)
 *  - deepseek_analysis/09_appos/APPOS_MASTER_SUMMARY.md §12.4 (constants summary)
 *  - deepseek_analysis/10_final_closure/FINAL_RED_TEAM_AUDIT.md §7 (independent verification)
 *
 * STATUS LEGEND: [EXACT] / [STRONG_INFERENCE] / [PARTIAL] / [UNKNOWN] / [PORT DESIGN] — see README.md.
 */

/** Cubic timing function control points. */
data class AnimationCubicBezier(val cp1x: Double, val cp1y: Double, val cp2x: Double, val cp2y: Double)

object AnimationSpecs {

    // -----------------------------------------------------------------------------------------
    // A. UIKit sheet presentation (UIKitCore .03)
    // -----------------------------------------------------------------------------------------

    object Sheet {
        /**
         * `+[UITransitionView defaultDurationForTransition:8]` @`0x18a251b70`; table entry index 8
         * is 0.4 (raw `0x3fd999999999999a`). [EXACT]
         * (REFUTES the earlier "0.5 s inherited" hypothesis; 0.5 belongs to app noninteractive animators.)
         */
        const val DURATION_SECONDS = 0.4

        /**
         * `UISpringTimingParameters(dampingRatio: 1.0, response: 0.3441442326)`.
         * Preference default `SheetResponse` raw `0x3fd606758807efe5` @`0x18a537078`. [EXACT]
         */
        const val DAMPING_RATIO = 1.0
        const val RESPONSE = 0.3441442326

        /**
         * Derived via `_convertDampingRatio:response:toMass:stiffness:damping:`
         * (`stiffness = (2π / response)^2`, `damping = 2 * ζ * sqrt(stiffness)`, mass 1.0):
         * stiffness = 333.33333328050389, damping = 36.51483716411749. [EXACT]
         */
        const val MASS = 1.0
        const val STIFFNESS = 333.3333332805039
        const val DAMPING = 36.51483716411749

        /** High-speed spring: ζ = 0.8 (`0x3fe999999999999a`), damping 29.21186973129399. [EXACT] */
        const val HIGH_SPEED_DAMPING_RATIO = 0.8
        const val HIGH_SPEED_DAMPING = 29.21186973129399

        /** `__UIInternalPreference` keys (CFString objects at `0x1efe0e198/1b8/1d8`). [EXACT] */
        const val PREF_SHEET_DAMPING_RATIO = "SheetDampingRatio"
        const val PREF_SHEET_RESPONSE = "SheetResponse"
        const val PREF_SHEET_HIGH_SPEED_DAMPING_RATIO = "SheetHighSpeedDampingRatio"

        /** Present and dismiss share the same duration/curve; only the direction flag differs. [EXACT] */
        fun springStiffness(response: Double): Double {
            val twoPi = 2.0 * kotlin.math.PI
            return (twoPi / response) * (twoPi / response)
        }

        fun springDamping(dampingRatio: Double, stiffness: Double): Double =
            2.0 * dampingRatio * kotlin.math.sqrt(stiffness)

        /** `defaultDurationForTransition:` table indices 0..16 (raw doubles, UIKitCore). [EXACT] */
        val DEFAULT_DURATION_TABLE = doubleArrayOf(
            0.0, 0.35, 0.35, 0.35, 0.4, 0.4, 0.4, 0.35, 0.4, 0.4,
            0.7, 0.7, 0.35, 0.6, 0.6, 0.7, 0.4
        )
    }

    // -----------------------------------------------------------------------------------------
    // B. MediaCoreUI Backdrop crossfade
    // -----------------------------------------------------------------------------------------

    object Backdrop {
        /**
         * `Backdrop.CompositeRenderer.crossfadeDuration` default Float 0.8 (`0x3F4CCCCD`),
         * written in init @`0x1c4d062ac`; field offset 0x34 via resilient field-offset global. [EXACT]
         * Note: carved from subcache .21 which is not in the current set -> provenance PARTIAL (red-team §7).
         */
        const val CROSSFADE_DURATION_SECONDS = 0.8

        /** Progress consumer: `textureTransitionMix += dt / crossfadeDuration`, clamped to 1.0. [EXACT] */
        fun advanceMix(currentMix: Double, dt: Double, crossfadeDuration: Double = CROSSFADE_DURATION_SECONDS): Double =
            (currentMix + dt / crossfadeDuration).coerceAtMost(1.0)

        /** `crossfadeTimingFunction = CAMediaTimingFunction(0, 0, 0.3, 1)` from the same init. [EXACT] */
        val CROSSFADE_TIMING_FUNCTION = AnimationCubicBezier(0.0, 0.0, 0.3, 1.0)

        /** `modeTimingFunction` / `warpTimingFunction = CAMediaTimingFunction(0.42, 0, 0.58, 1)`. [EXACT] */
        val WARP_TIMING_FUNCTION = AnimationCubicBezier(0.42, 0.0, 0.58, 1.0)

        /** `warpTimingSpeed = 3.5` (double `0x400c000000000000`). [EXACT] */
        const val WARP_TIMING_SPEED = 3.5

        /** Sibling pixel-format defaults from the same init (BGRA8Unorm=80, RGBA16Float=115). [EXACT] */
        const val DEFAULT_FRAMEBUFFER_PIXEL_FORMAT = 80
        const val DEFAULT_COLOR_PIXEL_FORMAT = 115
    }

    // -----------------------------------------------------------------------------------------
    // C. Artwork morph (Music.app)
    // -----------------------------------------------------------------------------------------

    object ArtworkMorph {
        /**
         * `MorphingMotionArtworkContainer.layoutSubviews` @`0x1005e2610`:
         * `SwiftUI.Animation.easeInOut(duration: UIView.inheritedAnimationDuration)` + `withAnimation`.
         * Easing [EXACT]; duration is inherited from the outer UIKit animation (e.g. sheet 0.4 s) —
         * there is NO own numeric duration constant. [EXACT]
         */
        val EASING = AnimationCubicBezier(0.42, 0.0, 0.58, 1.0)

        /**
         * Conditional multiplier in `MorphingMotionArtwork`: double 0.73 vs 1.0 (`fcsel` @`0x1005e1cfc`).
         * Value [EXACT]; the modifier (probably scale) and trigger are STRONG_INFERENCE.
         */
        const val CONDITIONAL_FACTOR = 0.73
        const val CONDITIONAL_FACTOR_DEFAULT = 1.0

        /**
         * AutoMix/SmartTransition artwork transition (engine `FUN_10017acfc`):
         * `p = clamp((now - startTime) / 3.0, 0, 1)`;
         * `sharpness = min(p*4.8, (1-p)*4.8, 0.6f)`; `intensity = p*1.3`; progress eased with
         * `CAMediaTimingFunction(0.62, 0, 0.8, 1.0)`. [EXACT]
         */
        const val TRANSITION_DURATION_SECONDS = 3.0
        const val SHARPNESS_FACTOR = 4.8
        const val SHARPNESS_CAP = 0.6
        const val INTENSITY_FACTOR = 1.3
        val PROGRESS_EASING = AnimationCubicBezier(0.62, 0.0, 0.8, 1.0)

        /** `NowPlayingTransitionsButton` corner radius 7.0, continuous curve. [EXACT] */
        const val TRANSITIONS_BUTTON_CORNER_RADIUS = 7.0

        /** `SmartTransitionIndicatorView` opacity 1.0f; position offset `-(width + 48.0 - 0.875)`. [EXACT] */
        const val INDICATOR_OPACITY = 1.0
        const val INDICATOR_POSITION_INSET = 48.0
        const val INDICATOR_POSITION_SUBTRACT = 0.875
    }

    // -----------------------------------------------------------------------------------------
    // D. Marquee (MusicMarqueeView)
    // -----------------------------------------------------------------------------------------

    object Marquee {
        /** `marqueeDelay = 3.0 s` (`0x4008000000000000`, `initWithFrame:` @`0x1000e5698`). [EXACT] */
        const val DELAY_SECONDS = 3.0

        /** `marqueeScrollRate = 30.0 pt/s` (`0x403e000000000000`). [EXACT] */
        const val SCROLL_RATE_POINTS_PER_SECOND = 30.0

        /** `setFrameInterval: 0.016` (`0x3f90624dd2f1a9fc` @`0x100e71558`). [EXACT] */
        const val FRAME_INTERVAL_SECONDS = 0.016

        /**
         * `_duration = delay + (contentSize.width + contentGap) / marqueeScrollRate`
         * (APPOS_MUSIC_APP §3.2). [EXACT] formula; per-instance setters are never called
         * (defaults are used).
         */
        fun duration(delay: Double, contentWidth: Double, contentGap: Double, rate: Double): Double =
            delay + (contentWidth + contentGap) / rate

        /** keyTimes = [0, delay/duration, duration/duration, 1]; translate.x [0, 0, travel, travel]; easeInEaseOut. [EXACT] */
        fun keyTimes(delay: Double, duration: Double): DoubleArray =
            doubleArrayOf(0.0, delay / duration, 1.0, 1.0)

        /** Fade mask gradient: colors white/clear/clear/white; `compositingFilter = kCAFilterDestOut`. [EXACT] strings. */
        val FADE_COLORS = listOf("white", "clear", "clear", "white")
        const val FADE_COMPOSITING_FILTER = "destOut"
    }

    // -----------------------------------------------------------------------------------------
    // E. MaterialKit highlight + pill bounce
    // -----------------------------------------------------------------------------------------

    object HighlightAndPill {
        /** `+[MTMaterialView newDefaultHighlightAnimator]`: UIViewPropertyAnimator 0.2 s, cubic (0.25,0.1,0.25,1.0). [EXACT] */
        const val HIGHLIGHT_DURATION_SECONDS = 0.2
        val HIGHLIGHT_TIMING = AnimationCubicBezier(0.25, 0.1, 0.25, 1.0)

        /**
         * `-[MTLumaDodgePillView _bounce]`: CASpringAnimation on position.y, additive;
         * mass 1.0 / stiffness 300.0 / damping 13.0; beginTime 0.2; fromValue 0.0; toValue ∓2.0
         * (up -2.0 / down +2.0). Values [EXACT]; animation key frame timing beyond these values PARTIAL.
         */
        const val PILL_SPRING_MASS = 1.0
        const val PILL_SPRING_STIFFNESS = 300.0
        const val PILL_SPRING_DAMPING = 13.0
        const val PILL_BEGIN_TIME = 0.2
        const val PILL_FROM_VALUE = 0.0
        const val PILL_UP_TO_VALUE = -2.0
        const val PILL_DOWN_TO_VALUE = 2.0
    }

    // -----------------------------------------------------------------------------------------
    // F. Display-link content offset (MusicUI, testing hook)
    // -----------------------------------------------------------------------------------------

    object DisplayLinkContentOffset {
        /**
         * `NSUserDefaults` key `PPTContentOffsetScrollIncrement` (31 chars), default **10.0**
         * (`fmov d0,#10.0` fallback when the stored value is 0). [EXACT]
         * Consumer: `contentOffset.y += K` per frame, `setContentOffset:animated:NO`;
         * at the bottom it calls `setPaused:`.
         */
        const val DEFAULTS_KEY = "PPTContentOffsetScrollIncrement"
        const val FALLBACK_INCREMENT = 10.0

        /** On-disk cached global is 0.0 (runtime lazy init) — runtime value wins. [EXACT] */
        const val ON_DISK_CACHED_VALUE = 0.0

        /** Effective step: `stored != 0 ? stored : 10.0`. [EXACT] behavior. */
        fun increment(storedValue: Double): Double =
            if (storedValue == 0.0) FALLBACK_INCREMENT else storedValue

        /** True while `contentOffset.y <= contentSize.height - bounds.height`. [EXACT] */
        fun canScrollFurther(offsetY: Double, contentHeight: Double, boundsHeight: Double): Boolean =
            offsetY <= contentHeight - boundsHeight
    }
}
