// SPDX-License-Identifier: Clean-room research contract. No Apple source code copied.
package apple.music.contracts.glass

/*
 * GlassSpecs.kt — Liquid Glass algorithm constants (iOS 26) + CoreMaterial recipes.
 *
 * PROVENANCE:
 *  - deepseek_analysis/10_final_closure/LIQUID_GLASS_IMPLEMENTATION_SPEC.md (LGC; canonical spec)
 *  - deepseek_analysis/09_appos/APPOS_GLASS_RECIPES.md (materialrecipe values)
 *  - deepseek_analysis/09_appos/APPOS_MASTER_SUMMARY.md §8 (summary)
 *  - deepseek_analysis/10_final_closure/ANDROID_CLEANROOM_CONTRACT.md §1.9 (shared glass rows)
 *
 * CLASSIFICATION (LGC §0): every entry below is an ALGORITHM CONSTANT (portable 1:1) unless marked
 * otherwise. DEVICE/RUNTIME TUNING (tile sizes chosen by Metal, per-frame mip counts, runtime
 * weights, uniform values overridden by recipes, CASDF effect defaults as used at runtime) is
 * EXCLUDED from active constants — see README "do not hardcode" list.
 *
 * STATUS LEGEND: [EXACT] / [STRONG_INFERENCE] / [PARTIAL] / [UNKNOWN] / [PORT DESIGN] — see README.md.
 */

object GlassSpecs {

    // -----------------------------------------------------------------------------------------
    // 1. Continuous corner / SDF (LGC §1.1–§1.3)
    // -----------------------------------------------------------------------------------------

    /**
     * Continuous-corner polynomial coefficients. Source: `glass/air_ir/glass_foreground_sdf_lpf.ll:464-551`
     * (`supercircle_sdf`), double constants (raw hex in LGC §1.1). [EXACT] ALGORITHM CONSTANT.
     * ```
     * bez(t) = P(t) * t^2
     * P(t)   = c0 + c1*t + c2*t^2 + c3*t^3 + c4*t^4
     * contour: |uv| + 1 - 1/(1 - bez(t)),  t = min(|u|,|v|) / max(|u|,|v|)
     * ```
     * Not a power-law superellipse (n fit floats ~2.48..3.43; `n≈4.4` REJECTED).
     */
    const val CORNER_POLY_C0 = 0.268531
    const val CORNER_POLY_C1 = 1.268030
    const val CORNER_POLY_C2 = -3.641220
    const val CORNER_POLY_C3 = 3.156010
    const val CORNER_POLY_C4 = -0.926054

    /** `P(t)` from the continuous-corner fit. [EXACT] */
    fun cornerPolynomial(t: Double): Double =
        CORNER_POLY_C0 + CORNER_POLY_C1 * t + CORNER_POLY_C2 * t * t +
            CORNER_POLY_C3 * t * t * t + CORNER_POLY_C4 * t * t * t * t

    /**
     * Corner contour value `|uv| + 1 - 1/(1 - bez(t))` with
     * `t = min(|u|,|v|)/max(|u|,|v|)` (guard: max==0 -> t=0). [EXACT] structure.
     */
    fun cornerContour(u: Double, v: Double): Double {
        val au = kotlin.math.abs(u)
        val av = kotlin.math.abs(v)
        val maxAbs = maxOf(au, av)
        val t = if (maxAbs == 0.0) 0.0 else minOf(au, av) / maxAbs
        val bez = cornerPolynomial(t) * t * t
        return au + av + 1.0 - 1.0 / (1.0 - bez)
    }

    /**
     * `+[CALayer cornerCurveExpansionFactor:]` @`0x183ceb2d8`: `"continuous"` -> double
     * `0x3FF875696E58A32F` = 1.528665; otherwise 1.0. In float32 = `0x3FC3AB4B`, bit-identical to the
     * shader constant `1.5286649465560913` (`0x3FF8756960000000` in IR). [EXACT] ALGORITHM CONSTANT.
     * Effective radius is multiplied by this factor, then ceil (`frintp`) and clamped to
     * `<= 0.5 * min(W,H)` (CoreMaterial `_MTDimensionsForContinuousCornerRadiusInBounds`).
     */
    const val CORNER_CURVE_EXPANSION_FACTOR = 1.528665

    /** Anti-aliasing: `alpha = saturate(0.5 - d / max(fwidth(d), 1e-4))`. [EXACT] (§1.3). */
    const val AA_FWIDTH_EPSILON = 1e-4

    // -----------------------------------------------------------------------------------------
    // 2. Meniscus refraction (LGC §1.4)
    // -----------------------------------------------------------------------------------------

    /**
     * Meniscus displacement (instruction sequence confirmed in both background and foreground IR):
     * ```
     * x    = saturate((-sdf - offset) * inv_height)
     * m    = saturate(sqrt(x * (2 - x)))
     * disp = amount * (1 - m) * normalize(disp_mat * normalize(grad))
     * ```
     * [EXACT] ALGORITHM CONSTANT.
     */
    fun meniscusX(sdf: Double, offset: Double, invHeight: Double): Double =
        ((-sdf - offset) * invHeight).coerceIn(0.0, 1.0)

    fun meniscusM(x: Double): Double =
        kotlin.math.sqrt(x.coerceIn(0.0, 1.0) * (2.0 - x.coerceIn(0.0, 1.0))).coerceIn(0.0, 1.0)

    /**
     * Foreground chromatic aberration: 7 samples total — R: 3 taps at `+f*ab` (f = 1, 2/3, 1/3),
     * B: 4 taps at `-f*ab` (f = 0, 1/3, 2/3, 1), G mixed; RGB normalized by (0.5, 1/3, 0.5),
     * then divided by 7. [EXACT] tap structure.
     */
    val CHROMATIC_ABERRATION_R_TAP_FACTORS = doubleArrayOf(1.0, 2.0 / 3.0, 1.0 / 3.0)
    val CHROMATIC_ABERRATION_B_TAP_FACTORS = doubleArrayOf(0.0, 1.0 / 3.0, 2.0 / 3.0, 1.0)
    val CHROMATIC_ABERRATION_RGB_NORMALIZATION = doubleArrayOf(0.5, 1.0 / 3.0, 0.5)
    const val CHROMATIC_ABERRATION_TOTAL_TAPS = 7

    // -----------------------------------------------------------------------------------------
    // 3. Variable blur (LGC §2)
    // -----------------------------------------------------------------------------------------

    /** `VariableBlurUniforms` fields (32 B, 8 floats). [EXACT]. */
    val VARIABLE_BLUR_UNIFORM_FIELDS = listOf(
        "max_blur", "noise", "divide", "offset", "dx", "dy", "fade_mul", "fade_add"
    )

    /**
     * `r = max_blur * saturate(mask.a)`;
     * `lod = max(0, log2(if (r < 2) 0.5 * r + 1 else r))`.
     * [EXACT] formula; numeric `max_blur` etc. are runtime tuning.
     */
    fun variableBlurRadius(maxBlur: Double, maskAlpha: Double): Double =
        maxBlur * maskAlpha.coerceIn(0.0, 1.0)

    fun variableBlurLod(r: Double): Double {
        val value = if (r < 2.0) 0.5 * r + 1.0 else r
        if (value <= 0.0) return 0.0
        return maxOf(0.0, kotlin.math.log2(value))
    }

    /**
     * CORRECTION (LGC §2): exactly 4 source taps at `texcoord0 + (±dx*lod, ±dy*lod)`
     * (diagonal, no center sample), `avg = 0.25 * sum`. The former "5-tap cross" formulation
     * is rejected.
     */
    const val VARIABLE_BLUR_SOURCE_TAPS = 4

    /**
     * Unpremultiply guard: `avg.rgb /= max(avg.a, 1e-3)` (raw `0x3F50624DE0000000`; the IR comment
     * names FLT_EPSILON but the immediate decodes to 1e-3). [EXACT] value.
     */
    const val VARIABLE_BLUR_UNPREMULTIPLY_EPSILON = 1e-3

    /** Dither: `avg.rgb += noise_scale * avg.a * (noise_texture(pos / 32).rgb - 0.5)` when `noise != 0`. [EXACT] */
    const val VARIABLE_BLUR_NOISE_DIVISOR = 32.0

    /** Output: `saturate(fade_mul * r + fade_add) * avg` (4 components, premultiplied). [EXACT] */
    fun variableBlurFade(fadeMul: Double, fadeAdd: Double, r: Double): Double =
        (fadeMul * r + fadeAdd).coerceIn(0.0, 1.0)

    // -----------------------------------------------------------------------------------------
    // 4. Blur chain: mip, weights, Gaussian kernel (LGC §3)
    // -----------------------------------------------------------------------------------------

    /**
     * `chain = floor(log2(max(src_w, src_h))) + 1`;
     * `levels = min(chain - 1, floor(log2(1.6 * r)))`;
     * LOD scale = `2^min(chain, 7)` — hard cap 7 (source constant 1.6f @`0x183db8fbc`). [EXACT] formula;
     * per-frame level count is runtime tuning.
     */
    const val VARIABLE_BLUR_CHAIN_CAP = 7
    const val VARIABLE_BLUR_LOG_FACTOR = 1.6

    /** Static downsample weights (`downsample_blur_2`, 4 f32, sum = 1.000000). [EXACT] ALGORITHM CONSTANT. */
    val DOWNSAMPLE_BLUR_2_WEIGHTS = doubleArrayOf(0.25342491, 0.20945647, 0.11824646, 0.04558462)

    /**
     * Static downsample weights (`downsample_blur_4`, 8 f32, sum = 1.000003). [EXACT] ALGORITHM CONSTANT.
     */
    val DOWNSAMPLE_BLUR_4_WEIGHTS = doubleArrayOf(
        0.18899369, 0.16900714, 0.12085755, 0.06910989,
        0.03159967, 0.01155237, 0.00337652, 0.0
    )

    /**
     * Gaussian weight drop threshold `0.002` (`tile_simd_blur` / `narrow_blur`); weights are computed
     * as `w(i) = exp(-i^2 * 0.5 / r)` for i = 0..13, `w(0) = 1.0`, normalized
     * `1 / (1 + 2 * sum(w))`, then tail weights `< 0.002` are dropped and mirrored to `2N+1` taps.
     * Algorithm [EXACT]; numeric weights for a concrete radius are runtime tuning.
     */
    const val GAUSSIAN_WEIGHT_THRESHOLD = 0.002

    /**
     * Kernel selection by radius count (radius count -> kernel):
     * >12 -> 27; 11-12 -> 23; 9-10 -> 19; 7-8 -> 15; 5-6 -> 11; 4 -> 7; 1-3/default -> 5. [EXACT]
     */
    fun gaussianKernelSize(radiusCount: Int): Int = when {
        radiusCount > 12 -> 27
        radiusCount >= 11 -> 23
        radiusCount >= 9 -> 19
        radiusCount >= 7 -> 15
        radiusCount >= 5 -> 11
        radiusCount == 4 -> 7
        else -> 5
    }

    /** Compute-encoding sizes (code values EXACT; actual tile sizes are Metal/runtime). */
    const val TILE_SIMD_BLUR_THREADS = 32
    const val IMAGEBLOCK_WIDTH = 16
    const val IMAGEBLOCK_HEIGHT = 32

    /** Blur optimization: `29.5 < r < 35.0 -> 29.5`; dynamic quality 10 (low devices) / 100. [EXACT] values. */
    const val BLUR_OPTIMIZATION_LOWER = 29.5
    const val BLUR_OPTIMIZATION_UPPER = 35.0
    const val BLUR_OPTIMIZATION_CLAMP = 29.5
    const val DYNAMIC_QUALITY_LOW = 10
    const val DYNAMIC_QUALITY_HIGH = 100

    // -----------------------------------------------------------------------------------------
    // 5. Filter order (LGC §4)
    // -----------------------------------------------------------------------------------------

    /** `_mt_orderedFilterTypes` (9 CFString, CoreMaterial block `0x1be8ae488`). [EXACT] ALGORITHM CONSTANT. */
    val ORDERED_FILTER_TYPES = listOf(
        "averageColor", "luminanceMap", "luminanceCurveMap", "curves", "gaussianBlur",
        "variableBlur", "colorMatrix", "colorSaturate", "colorBrightness"
    )

    /**
     * `_mt_orderedFilterTypesBlurAtEnd` (block `0x1be8ae3e4`): blur filters moved to the end,
     * color filters to the middle. [EXACT]
     */
    val ORDERED_FILTER_TYPES_BLUR_AT_END = listOf(
        "averageColor", "luminanceMap", "luminanceCurveMap", "curves", "colorMatrix",
        "colorSaturate", "colorBrightness", "gaussianBlur", "variableBlur"
    )

    // -----------------------------------------------------------------------------------------
    // 6. CASDF highlight / displacement defaults (LGC §5)
    // -----------------------------------------------------------------------------------------

    /**
     * `CASDFGlassHighlightEffect` default values (`0x183b52da0`): height 20, curvature 1.0,
     * angle π/2, spread π, amount 0.5, white color. Values [EXACT]; per-pixel shader [UNKNOWN];
     * effective values may be overridden at runtime -> treat as defaults only.
     * `CASDFGlassDisplacementEffect` (`0x183b59a80`): angle 0.0, curvature 1.0, height 20.
     */
    object CasdfDefaults {
        const val HIGHLIGHT_HEIGHT = 20
        const val HIGHLIGHT_CURVATURE = 1.0
        const val HIGHLIGHT_ANGLE = 1.5707963267948966
        const val HIGHLIGHT_SPREAD = 3.141592653589793
        const val HIGHLIGHT_AMOUNT = 0.5
        const val DISPLACEMENT_ANGLE = 0.0
        const val DISPLACEMENT_CURVATURE = 1.0
        const val DISPLACEMENT_HEIGHT = 20

        /** Effect names (kind bytes 6 / 7). [EXACT] */
        const val HIGHLIGHT_NAME = "Glass Highlight"
        const val DISPLACEMENT_NAME = "Glass Displacement"

        /**
         * STATUS: UNKNOWN — needs runtime/device: per-pixel fragment shader for kinds 6/7 is absent
         * from the `.air` set (LGC §5/§12).
         */
        fun evaluateHighlight(): Double =
            TODO("STATUS: UNKNOWN — needs runtime/device: CASDF highlight/displacement shader formula absent")
    }

    // -----------------------------------------------------------------------------------------
    // 7. Material recipes (LGC §7, APPOS_GLASS_RECIPES.md)
    // -----------------------------------------------------------------------------------------

    /** One CoreMaterial filtering recipe. */
    data class MaterialRecipe(
        val name: String,
        val materialSettingsVersion: Int,
        val blurRadius: Double,
        /** 4 rows x 5 columns color matrix (m11..m45). */
        val colorMatrix: List<List<Double>>,
        val sha256: String,
    )

    /**
     * `platformContentGlass.materialrecipe` (CoreMaterial.framework, XML plist, 1849 B,
     * sha256 `589fc5c5795c149a78a8339a93738e13a88d6919f124d9b790f920e9dd897858`).
     * All values [EXACT] resource data (ALGORITHM/RECIPE constant for this build).
     * The Darker/Lighter/UltraDarker variants are byte-identical in this build (EXACT observation).
     */
    val PLATFORM_CONTENT_GLASS = MaterialRecipe(
        name = "platformContentGlass",
        materialSettingsVersion = 2,
        blurRadius = 45.0,
        colorMatrix = listOf(
            listOf(0.921, -0.265, -0.027, 0.0, 0.235),
            listOf(-0.079, 0.735, -0.027, 0.0, 0.235),
            listOf(-0.079, -0.265, 0.973, 0.0, 0.235),
            listOf(0.0, 0.0, 0.0, 1.0, 0.0),
        ),
        sha256 = "589fc5c5795c149a78a8339a93738e13a88d6919f124d9b790f920e9dd897858",
    )

    /**
     * Loader: `com.apple.CoreMaterial` + `URLForResource:<name> withExtension:@"materialrecipe"`
     * (`0x1be8b9e64`); override bundle supported via `coreMaterialOverrideRecipeBundleURL`. [EXACT]
     * Recipe static default `inputBlurRadius = 30.0` is overridden by blurRadius=45 above (LGC §7).
     */
    const val CORE_MATERIAL_BUNDLE_NAME = "com.apple.CoreMaterial"
    const val MATERIAL_RECIPE_EXTENSION = "materialrecipe"

    // -----------------------------------------------------------------------------------------
    // 8. Compositing plusL / plusD (LGC §9)
    // -----------------------------------------------------------------------------------------

    /**
     * PlusL: `out.rgb = in.rgb + tint.rgb * a; out.a = in.a + a`.
     * `_CAColorMatrixMakePlusL` `0x183c47de0`. [EXACT] matrix.
     * Clamp is a render property (SW `uqadd`, fallback `adc`); Android `BlendMode.Plus` matches the
     * default clamped behavior [PORT DESIGN].
     */
    fun plusL(inRgb: DoubleArray, tintRgb: DoubleArray, inA: Double, a: Double): DoubleArray {
        val out = DoubleArray(4)
        out[0] = inRgb[0] + tintRgb[0] * a
        out[1] = inRgb[1] + tintRgb[1] * a
        out[2] = inRgb[2] + tintRgb[2] * a
        out[3] = inA + a
        return out
    }

    /**
     * PlusD: `out.rgb = in.rgb + a * (1 - tint.rgb); out.a = in.a + a`.
     * `_CAColorMatrixMakePlusD` `0x183c47e3c`. [EXACT] matrix.
     */
    fun plusD(inRgb: DoubleArray, tintRgb: DoubleArray, inA: Double, a: Double): DoubleArray {
        val out = DoubleArray(4)
        out[0] = inRgb[0] + a * (1.0 - tintRgb[0])
        out[1] = inRgb[1] + a * (1.0 - tintRgb[1])
        out[2] = inRgb[2] + a * (1.0 - tintRgb[2])
        out[3] = inA + a
        return out
    }
}
