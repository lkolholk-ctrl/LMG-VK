# Accompanist lyrics integration

Imported from the user-supplied `Lyrics-Apache2-0.zip` on 2026-09-06.
Archive SHA-256: `d5dc8dff9acc81a19a4eea10fd8a2bc6e0da8fb2b513f65e90f0ed86af9f6da0`.

- `accompanist-lyrics-core-main.zip`: core sources and upstream parser/exporter tests (version 0.4.7 in upstream build).
- `accompanist-lyrics-ui-main.zip`: Compose lyrics renderer, common and Android sources.
- Copyright [2025] [6xingyv], as stated in both supplied LICENSE files.
- Both libraries are licensed under Apache License 2.0. Original LICENSE and README files are preserved next to each library.
- No separate upstream NOTICE files were present in the supplied archives.
- Copies of both licenses and the attribution are packaged under `assets/licenses/` in the APK.

The Android app compiles these source sets directly, with the app's existing Kotlin,
Compose and serialization dependencies. The upstream multiplatform publishing builds,
sample app, sample media, fonts and IDE files are not build inputs.

## Local modifications

Modified source files have a comment identifying LMG VK changes.

- `lyrics-ui/.../utils/String.kt` and `String.android.kt`: replace expect/actual declarations with Android source declarations.
- `lyrics-ui/.../LyricsLineItem.kt`: use Compose RoundedCornerShape instead of introducing Gaze Capsule; clear the blur effect when its radius returns to zero.
- `lyrics-ui/.../KaraokeLyricsView.kt`: keep position callbacks current, reset auto-follow on seeks, allow four seconds for manual reading after dragging, guard empty lists and short viewports, avoid applying the outer modifier twice, recalculate glyph measurements on density/font-scale changes, keep manually browsed lyrics clear.
- `lyrics-core/.../utils/TimeUtils.kt`: support TTML offset times (`s`, `ms`, `m`, `h`) without treating them as zero.
- `lyrics-core/.../KaraokeSyllable.kt`: finite progress for zero-duration syllables.
- `lyrics-ui/.../KaraokeLineText.kt`, `LyricsLayoutCalculator.kt` and new `LyricsMotion.kt`: a 30dp trailing fill edge, mirrored for RTL; -2dp spring lift (stiffness 25, damping ratio 0.93) staggered across characters and held until line end. Character emphasis uses delayed rise/return curves, baseline pivots, up to 1.14 scale, 5dp shadow and compensation for expanded character widths. Connected scripts, combining sequences and surrogate pairs retain whole-run shaping.
- `lyrics-ui/.../KaraokeBreathingDots.kt`, `KaraokeLyricsView.kt` and new `WaitingTimeline.kt`: merge all vocal coverage, including overlapping lines and nested background vocals, before creating waiting intervals. Replace independent visibility transitions with audio-sampled appearance, pulse, sequential fill and exit; use half-open intervals so dots cannot coexist with the next vocal at its start.
- `lyrics-ui/.../RowMotionData.kt` and `SpatialWave.kt`: cache glyph geometry, timing and spatial weights during layout; reuse frame buffers. Smooth only lift across word boundaries with a width-weighted Gaussian (radius 0.6 times text height). Emphasis uses the recovered per-character envelope directly, with no rotation or spatial smoothing of scale/shadow; expanded-width compensation is confined to each word.
- `KaraokeLineText.kt` and `KaraokeBreathingDots.kt`: observe a clamped animation interval with structural equality so future/settled content does not redraw on every audio frame. Keep the four-second spring settling tail active and resume immediately on backward seeks.
- `KaraokeLyricsView.kt` and `KaraokeBreathingDots.kt`: reserve a fixed instrumental slot; transition the follow anchor from dots to vocals once. The audio clock no longer changes item height every frame or repeatedly retargets placement springs.
- `SpringPlacementModifier.kt`: retain floating-point spring positions and place through layer translation instead of rounding to pixel coordinates. `KaraokeLyricsView` disables placement animation only for user dragging/manual-reading grace, not transient scroll-state changes. Glyph lift start follows its position within the syllable fill independently of the emphasis schedule; emphasis math is unchanged.
- `LyricsScreen` selects SrcOver composition and disables per-line blur, retaining the viewport fade and glyph emphasis shadow. `LyricsLineItem` uses Auto composition so opaque ordinary rows do not require a forced offscreen buffer; group opacity is retained for overlapping glyphs/shadows. `KaraokeLyricsView` caches the viewport gradient outside its per-frame draw callback and does not create blur animations when blur is disabled.
- `LyricsLineItem` and `LyricsActivityMotion.kt`: recover main-line activity timings from `C3463z.n0`: alpha rise 250ms; alpha fall 350ms after 250ms; scale rise 500ms after 150ms; scale fall 350ms after 50ms. Alpha uses cubic (0.39,0.575,0.565,1), scale uses (0.4,0.1,0,1). Compose retargets from current animated values; alpha/scale are read only in the graphics layer. Existing opacity endpoints, 0.98/1.0 line scale, glyph emphasis, lift and scroll timing are preserved.
- `LyricGlyphRaster.kt` and `KaraokeLineText.kt`: cache supersampled Alpha8 glyph coverage and a separate 5dp shadow mask instead of rasterizing transformed text every frame. Move and scale these masks with bilinear filtering to avoid axis-aligned text baseline quantization after removal of glyph rotation. An 8MiB LRU uses layout inputs to share repeated characters across rows; evicted images are not manually recycled while GPU commands may reference them. The existing lift, fill, emphasis envelope, centre-out spread and line activity math are unchanged. This is an Android-specific rendering adaptation, not recovered Apple code.

## Animation research adaptation

The fill and lift constants above come from the user's Apple Music 6.5.2 research
in `LMG-VK-Native-Test/APPLE-MUSIC-LYRICS-IMPLEMENTATION-AUDIT-RU.md`, sections
8.4 and 8.5, cross-referenced there to `C3463z.j0` and the gradient layout.
Further evidence: `C3463z.a0` and listeners `f/g/h` specify character delays
`min(0.4 * wordDuration / count, 400ms)`, return delay `2 * wordDuration / count`,
rise/return duration capped at 3000ms, and cubic `(0.25, 0.1, 0.25, 1)`.
Rechecked directly against `classes2.dex` methods `player/z.a0`, `player/g.onTimeUpdate`
and `player/h.invoke` using baksmali. Delays round to milliseconds. Shadow follows
the animation fraction independently of the duration-dependent scale target, with
maximum alpha 128/255 and radius 5dp. Horizontal spread follows the original
centre-out recurrence using half-growth and half the inner neighbour's offset,
instead of full cumulative expansion. This renderer uses cached glyph advance
widths for spread; Apple's callback measures ink bounds with Paint.getTextBounds.
`LYRICS-KARAOKE-N0-RECONSTRUCTED.md` sections 3.3/4 locate lift reset at line
highlight/unhighlight, correcting the earlier local release-at-word-end approximation.
The previous word-wide lift and Swell/Bounce effects are replaced with a character wave.

`LYRICS-C3463Z-REMAINING-METHODS.md` section 1 and `C3463z.c0` describe sequential
dot fill, cubic `(0, 0.25, 1, 0.58)`, and duration-fitted 4000ms breathing cycles.
`LYRICS-UX-RENDERING.md` section 2.5 supplies the 97 numeric S8/a pulse samples.
The audit's section 11 gives 10dp dots, 6dp spacing, 750ms staggered appearance
(50ms between dots), and 750ms expansion followed by a 250ms fade/shrink.

These are new Compose implementations using recovered parameters, not a compiled
decompiled Java engine. Local adaptations: ordinary character starts follow supplied
syllable timings; release uses the supplied line end; instrumental exit fits inside
the known vocal gap; gap detection retains Accompanist's threshold of more than 5s.
Glyph and waiting phases are pure functions of audio position so reopening, pause
and seek produce the same state. Whole-line activity transitions use the display
frame clock and retarget on focus changes. Apple's activity curves and delays are
adapted to group opacity, retaining LMG's existing opacity endpoints rather than
replacing per-segment colors. Apple's full merge-group construction and background
vocal/scroll transitions are not reproduced by these changes.
Spatial smoothing of lift is a local refinement requested after device testing,
not a recovered Apple constant. Glyph rotation and spatial smoothing of emphasis
are removed following device feedback; emphasis no longer affects unrelated words.
Instrumental gaps retain blank
spacing when inactive to keep the list geometry stable during automatic scrolling.

## App integration

`AccompanistLyricsAdapter` converts existing provider data off the UI thread. Raw TTML
is parsed by the supplied core; untimed TTML uses the existing plain-text projection. Rich and legacy data retain their word timings,
spacing, translations, pronunciation and background vocals. Untimed text remains
manually scrollable. A lifecycle-aware frame clock samples the active session
player's current position through PlayerController, including seeks, speed changes,
pause/resume, buffering and the saved per-track offset. It does not extrapolate
wall time or reject backwards position corrections.

`LyricsScreen`, the compatibility `LyricsSheet` entry point and the timing editor's
`MarkupPreviewView` use the same native renderer. The former AMLL WebView, generated
HTML bundle and web build sources have been removed.

## Shared player background

`FullPlayer` owns one `SharedArtworkBackground`; embedded `LyricsScreen` and
`QueueSheet` are transparent over it, including split layout. Standalone lyrics
use the same renderer. Artwork loads through Coil's cache or one bounded local
embedded-art fallback on IO, with a three-entry background cache. Animation phase
survives switching sheets. Rendering stops outside STARTED lifecycle or when the
player is collapsed, without advancing the hidden animation phase.

`ArtworkBackgroundRenderer` adapts `LyricsBackgroundLayerView.onDraw` from the
user's Apple 6.5.2 research: three transformed artwork copies (originally 120/90/70
seconds, now 150/112.5/87.5 seconds for 20% slower movement),
saturation 2.5, 1.3x overscan, black 30% then white 10% scrims, one-second artwork
crossfade with cubic (0,0,0.3,1), and a minimum 50ms frame interval. It uses reusable
tiny worker-owned surfaces and three sliding box filters approximating the radius-25
Gaussian in linear time. A dedicated background-priority thread renders at no more
than one-third CPU duty when frames take longer; missed frames are never queued.
Published bitmaps are immutable and read by a separate Compose graphics layer with
bilinear filtering, keeping draw invalidations local within the backdrop capture.
An unchanged neutral background reuses its published frame without invalidating draw.
Working dimensions follow density divisors 24/16, capped proportionally at 180px.
This is an independent adaptation: the optional mesh presets, separate tinted
ImageView overlays, RenderScript's exact numeric rounding, and reduced-effects
branch are not reproduced. Background rendering does not drive the lyrics clock.

## Verification

Run `./gradlew :app:testDebugUnitTest :app:assembleDebug`.
Upstream core tests are included in the app's unit-test source set, alongside adapter
regressions in `AccompanistLyricsAdapterTest`.
`LyricsPlaybackTest` checks audio-driven frames through pauses, buffering, seeks
and track changes. `LyricsApiTimingTest` compares every line and word timestamp
in the ANTIDOTE / What Is Love API snapshots; their text is replaced with placeholders.
`LyricsMotionTest` compares the spring to numerical physical integration, checks
release continuity, character staggering, emphasis joins, centre-out character spread,
pauses/seeks, invalid durations, density and mirrored RTL feather stops.
`WaitingTimelineTest` checks overlapping/nested vocals, intro and gap boundaries,
sequential fill, continuous pulse/exit, short intervals and deterministic pause/seek.
`SpatialWaveTest` checks bounded adjacent height/scale changes, flat settled text,
density invariance and reused buffers after seeks. `LyricsRenderSchedulingTest`
uses Compose snapshot observation to verify zero inactive-frame invalidations and
immediate reactivation when seeking back into a line.

UI checks for a device: open lyrics mid-track; pause/resume; seek forward and
backward; tap a line with positive and negative SYNC offsets; scroll manually and
wait for auto-follow; switch tracks; toggle translations/pronunciation; rotate
between portrait and landscape; open untimed lyrics and an empty result.
Instrumentation checks are in `AccompanistLyricsScreenTest` and can be run with
`:app:connectedDebugAndroidTest` when a device is available.
`LyricGlyphRasterTest` checks quarter-pixel coverage movement, visible Alpha8 masks,
separate shadow coverage and cache reuse after movement. It requires a device;
compiling the instrumentation APK alone does not verify visual smoothness.
