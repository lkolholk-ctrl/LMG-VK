# ANDROID_LIQUID_GLASS — AGSL/RenderEffect-порт audited Liquid Glass

Subagent #7. RESEARCH/SPEC ONLY. Источник: `05_liquid_glass/*` + `07_audit/*`. Все формулы,
помеченные EXACT, повторно подтверждены аудитором (полином supercircle, meniscus, AA eps,
variable-blur LOD, пресеты). `plusL` — **`[INFERRED]`** (RETRACTED A2/D24): в корпусе доказаны
только имя фильтра, opacity и цвет; две конкурирующие формы формулы; в порт идёт за флагом.
Кода не создано.

Существующая база в приложении: `com.lmg.vk.ui.glass` (`LiquidGlassSurface.kt`, `GlassKit.kt`,
`PressScale.kt`, `AlbumArtImage.kt`) и `com.lmg.vk.ui.liquid` (`LiquidSlider`, `LiquidToggle`,
`DampedDragAnimation`, `InteractiveHighlight`) — новые шейдеры встраиваются сюда.

---

## 1. Компонент: continuous corners (supercircle)

- iOS behavior: контур `|uv| + 1 − 1/(1 − bez(t)) = 1`, где
  `bez(t) = P(t)·t²`, `P(t) = 0.268531 + 1.268030·t − 3.641220·t² + 3.156010·t³ − 0.926054·t⁴`;
  эффективный радиус = `cornerCurveExpansionFactor · radius`, clamp `min(W,H)/2`; константа
  расширения в шейдерах `1.5286649465560913`.
- source evidence: `supercircleImage` (QuartzCore, native string @file 0x37f3d2, EXACT);
  `glass_foreground_sdf_lpf.ll:464–551`, `glass_background_sdf_lpf.ll:372–374` (EXACT);
  `_MTDimensionsForContinuousCornerRadiusInBounds` 0x1be8b122c (EXACT).
- Android implementation: графика — `androidx.graphics.shapes.RoundedPolygon` +
  `CornerRounding(radiusPx, smoothing = ~0.6f)` для повседневного UI (быстро, без шейдера);
  точный путь — собственный `Shape` или AGSL SDF:
  `d = |p|; t = clamp(min(|px|,|py|)/max(|px|,|py|)); d += 1 − 1/(1 − P(t)t²)`.
  Расширение радиуса применять до рендера (`rEff = r * 1.5286649`; равенство expansion factor —
  STRONG_INFERENCE).
- difficulty: low (`RoundedPolygon`) / medium (точный AGSL).
- expected fidelity: 80% (RoundedPolygon) / 95% (точный полином).
- performance risks: SDF считать один раз на размер/радиус и кэшировать (аналог
  `CA::Render::SDFLayer::sdf_padding`, `is_sdf_cache_eligible`); не на каждый кадр.
- fallback: `RoundedCornerShape` при `glass.perfTier=low`.

---

## 2. Компонент: SDF-преломление (meniscus)

- iOS behavior: `x = saturate((−sdf − refraction_offset) · inv_refraction_height)`;
  `m = saturate(sqrt(x·(2−x)))`; смещение `disp = refraction_amount·(1−m)·normalize(disp_mat ·
  normalize(grad))`; `srcUV = uv + disp`.
- source evidence: `GLASS_SHADERS.md` §2.1–2.2 (EXACT instruction sequence, строки 28–74 IR).
- Android implementation: AGSL `RuntimeShader` (API 33+) с uniform `shader backdrop` (размытый фон),
  uniform-структурой по мотивам `GlassForegroundUniforms`/`GlassBackgroundUniforms` (точные имена
  полей EXACT: `displacement_mat`, `refraction_amount/inv_height/offset`, `inner_*`, `outer_*`,
  `refraction_threshold0/1`, `edge_*`); градиент — конечной разностью через `dFdx/dFdy` SDF-текстуры
  или двойную оценку. Подключение — `RenderEffect.createRuntimeShaderEffect(shader, "backdrop")` +
  `Modifier.graphicsLayer { renderEffect = ... }`.
- difficulty: medium.
- expected fidelity: 85% (формула EXACT; uniform-дефолты NOT FOUND → подбираются).
- performance risks: 1–2 full-screen выборки; `grad` через `dFdx/dFdy` дешевле второй оценки.
- fallback: без преломления — blur + tint; на API<33 — `RenderEffect.blur` + `BlendMode.DstIn`.

---

## 3. Компонент: chromatic aberration

- iOS behavior: второй meniscus от `(−sdf − aberration_offset)·inv_aberration_height` даёт
  `ab = aberration_amount·(1−meniscus)`; направления из `(angle_x, −angle_y)` и `(angle_y, angle_x)`
  через `displacement_mat`; R — 3 тапа `+f·ab` (f=1, 2/3, 1/3), B — 4 тапа `−f·ab` (f=0, 1/3, 2/3, 1),
  G — смесь; нормировки `(0.5, 1/3, 0.5)`, общий `/7`; AA-множитель.
- source evidence: `GLASS_SHADERS.md` §2.4 (EXACT; `glass_foreground_sdf_lpf.ll:106–153`).
- Android implementation: тот же AGSL-проход, 7 выборок backdrop; при `refine=0` — 3 выборки
  (по одной на канал), включать полный набор только на high-tier. Условие: uniforms управления
  (angle/height/offset) существуют, значения — NOT FOUND.
- difficulty: medium.
- expected fidelity: 80% (математика EXACT, значения — подбор).
- performance risks: 7 tap'ов full-screen — основная стоимость foreground; на mid-tier 3 тапа.
- fallback: без aberration (равный сдвиг по всем каналам).

---

## 4. Компонент: edge ramp + AA

- iOS behavior: `t = saturate((sdf − edge_start)/(edge_end − edge_start))`;
  `rgb *= 1 − mix(edge_opacity_start, edge_opacity_end, t)`;
  `aa = saturate(0.5 − sdf/max(fwidth(sdf), 1e-4))` (ε = `0x3F1A36E2E0000000`).
- source evidence: `GLASS_SHADERS.md` §2.6–2.7, `GLASS_GEOMETRY.md` §1.5 (EXACT).
- Android implementation: inline в SDF-шейдере; `fwidth` доступен в AGSL;
  `edge_start/end/opacity_*` — uniforms (значения NOT FOUND → дефолты из дизайна).
- difficulty: low.
- expected fidelity: 90% (математика), 60% (значения).
- performance risks: нет.
- fallback: без edge-ramp.

---

## 5. Компонент: variable blur (по маске)

- iOS behavior: `r = max_blur · saturate(mask.a)`;
  `lod = max(0, log2(r < 2 ? 0.5r + 1 : r))`; сэмпл на LOD; 5 tap'ов «крест»
  `center + (±dx,±dy)`, `dx,dy = (u.dx,u.dy)·lod`; `alpha *= saturate(fade_mul·r + fade_add)`;
  системный пресет: `inputRadius 15`, `inputNormalizeEdges true`, `inputDither false`.
- source evidence: `variable_blur_frag_lpf.ll:17–34` (EXACT), `GLASS_RADII_PRESETS.md` §2 (EXACT).
- Android implementation: в AGSL нет `textureLod`/mip-цепи — строить пирамиду вручную:
  offscreen-копии backdrop 1/2, 1/4, 1/8, 1/16 (`Bitmap`/`ImageDecoder`/`Canvas` downscale или
  несколько `RenderEffect.blur` проходов), шейдер выбирает уровень по `lod` (mix двух соседних
  уровней по дробной части) и делает 5-tap cross; dither — только если задан noise-текстур.
  Маска — `ImageBitmap` альфы (`toolbar_blur_mask` — Apple-ресурс, у Android своя маска).
- difficulty: high.
- expected fidelity: 70% (формула EXACT; пирамида/LOD — аппроксимация).
- performance risks: пирамида — самая дорогая часть; кэшировать уровни между кадрами (backdrop
  меняется реже скролла); пересобирать только при resize/смене контента.
- fallback: `RenderEffect`-цепочка `blur(8) → blur(24)` + альфа-маска (`BlendMode.DstIn`);
  API<31 — статичный полупрозрачный фон.

---

## 6. Компонент: tint / plusL (**INFERRED**)

- iOS behavior (доказано): `compositingFilter: "plusL"`/`"plusD"`, opacity `0.1/0.2/1.0`,
  backgroundColor white/white 0.35/white 0.9 alpha 0.3 (EXACT, пресеты);
  формула — **не доказана**. Две формы из отчётов:
  (a) `out.rgb = saturate(in.rgb + tint.rgb·tint.a)` (со ссылкой на `_CAColorMatrixMakePlusL`
  0x183c47de0 в LIQUID_GLASS_ARCHITECTURE §2.1);
  (b) `out.rgb = saturate(in.rgb + tint.rgb·tint.a·opacity)`, `out.a = in.a + tint.a·opacity`
  (GLASS_ANDROID_AGSL_PORT §5). Аудит: формула `[INFERRED]`, не Apple-verified.
- source evidence: `GLASS_RADII_PRESETS.md` §2 (EXACT значения), `RETRACTED_CLAIMS` A2 / `DISPUTED` D24.
- Android implementation: offscreen-слой платтера → `Paint(ColorFilter = BlendModeColorFilter(
  tint.premultiplied, BlendMode.Plus))` или `PorterDuff.Mode.ADD`; opacity — свойство слоя.
  Формула с `opacity` — дефолт (`glass.plusL=inferred`); `plainTint` — вариант (b) без opacity.
  `BlendMode.Plus` в Skia клампит каналы.
- difficulty: low.
- expected fidelity: 40% (имя/цвет/прозрачность 95%, математика не доказана).
- performance risks: дополнительный offscreen-слой.
- fallback: обычный полупрозрачный белый overlay (как `Default`-пресет без эффектов).

---

## 7. Компонент: reflection (SFCapsuleCollectionView)

- iOS behavior: JSON `capsule_effects.json`: `cornerRadius 12`, `maskRadius 0.5`, `maskInset 0.5`,
  `thickness 2.3333` (=7/3), `sourceThickness 5.6667` (=17/3), `sourceOutset 3`, `blurRadius 2`,
  `sliceCountPerCorner 12`, `isReversed false`.
- source evidence: `GLASS_RADII_PRESETS.md` §3 (EXACT), `GLASS_GEOMETRY.md` §2.2.
- Android implementation: `Path`/`Shape` с 12 сегментами на угол (заменяет 9-slice);
  `BlurMaskFilter(blurRadius)`, inset/mask — clip-путь; `isReversed` — направление профиля.
- difficulty: high (геометрия сегментов не документирована).
- expected fidelity: 50%.
- performance risks: path перестраивается при resize — кэшировать.
- fallback: простой белый бордер/градиент.

---

## 8. Компонент: specular / highlight / shadow / color matrices

- iOS behavior: фрагменты присутствуют как uniforms (`shadow_*`, `face_cm0..2`, `bleed_cm0..2`,
  `shadow_cm0..2`, `sdr_*`, `holding_tone_opacity`, `clamp_limit`, `preserve_hue`,
  `sdr_white_value`), но значения и отдельный specular-шейдер — NOT FOUND; есть
  `CASDFGlassHighlightEffect defaultValues` (числа не декодированы).
- source evidence: `GLASS_SHADERS.md` §3 + NOT FOUND, `GLASS_GEOMETRY.md` NOT FOUND.
- Android implementation: `edge highlight` — radial/edge ramp с независимыми uniform-рами
  (подбор); `shadow` — `RenderEffect.createBlurEffect` + смещение + цветовая матрица;
  color matrices — `ColorMatrix` 3×4 (порядок применения как в IR: face → bleed → shadow).
- difficulty: medium.
- expected fidelity: 30–40% (структура), значения — нет.
- performance risks: shadow-blur full-screen; ограничить областью платтера.
- fallback: без shadow/bleed (только tint+blur).

---

## 9. Компонент: pipeline и caching

- iOS behavior: проходы: SDF field/gradients → blur pyramid (downsample/LOD) → variable blur →
  narrow/SIMD blur → background glass (blur+refraction+bleed+shadow+matrices) → foreground glass
  (lens+aberration) → resolve blur weight → composite (plusL/plusD/opacity/bgcolor).
- source evidence: `GLASS_PIPELINE.md` §1/§4 (ядра EXACT, порядок STRONG_INFERENCE).
- Android implementation: цепочка `RenderEffect`-ов на offscreen-слое:
  `blur(s) → RuntimeShader(sdf_refraction) → RuntimeShader(aberration) → Composite(plusL)`.
  Backdrop — снапшот контента под платтером (`View.draw` в `Picture`/`Bitmap` или
  `RenderNode`+`ImageReader`), обновление с троттлингом (≥1 кадр между снапшотами).
  SDF-текстура — кэш по ключу `(w, h, radius, curve)`.
- difficulty: high.
- expected fidelity: 70% (порядок/идея), 100% недостижимо без `.metallib`-эталона.
- performance risks: несколько full-screen pass'ов; на 120 Гц — бюджет ~8 мс; троттлить backdrop.
- fallback: blur-only платтер (без refr/aberr).

---

## 10. Performance tiers и API-матрица

| Tier | Условие | Что включено |
|---|---|---|
| high | API 33+, GPU ≥ midrange, `glass.perfTier=high` | SDF+meniscus, 7-tap aberration, variable blur пирамида, reflection, shadow |
| mid | API 33+ | SDF+meniscus, 3-tap aberration, blur 15→10, без bleed/shadow |
| low | API 31–32 | `RenderEffect.blur` + ColorMatrix tint + clip; refraction нет |
| minimal | API <31 / `agsl=off` | статичный полупрозрачный фон, `RoundedCornerShape` |

- iOS behavior: `_MTDynamicBlurRadiusGraphicsQuality` = 100 / 10 (low-quality devices) — EXACT;
  порог оптимизации `29.5 < radius < 35.0 → 29.5` — EXACT.
- source evidence: `LIQUID_GLASS_ARCHITECTURE.md` §2.2, `GLASS_RADII_PRESETS.md` §4.
- Android implementation: `GlassPerfTier` выбирается по `Build.VERSION`, `ActivityManager.isLowRamDevice`,
  GPU-эвристике (`GLES20.glGetString(GL_RENDERER)`), затем по фактическому frame time (адаптив).
  Apple-числа (100/10, 29.5/35) — использовать как ориентир деградации, не как обязательные.
- difficulty: medium.
- expected fidelity: 80% (деградация), 100% визуал недостижим.
- performance risks: перегрев/джиттер на низких tier — адаптивно отключать pass'ы.
- fallback: minimal.

---

## 11. Пресеты геометрии (EXACT, использовать как дефолты)

| Параметр | Variable Blur (Liquid Glass) | Floating | Docked 8/0pt |
|---|---|---|---|
| Platter corner radius | 32 | 22 | 32 |
| Platter inset | 0 | 34 | 8 / 0 |
| Tab side margin | 20 | 20 | 10 |
| Padding above tabs | 8 | 20 | 10 |
| Padding between tabs | 8 | 8 | 18 |
| Padding below tabs | 3 | 3 | 3 |
| Toolbar padding above/below | 0 | 0 | 0 |
| Dodges safe area | true | true | false |
| Hide Capsule Shadows | false | false | true |

- source: `GLASS_RADII_PRESETS.md` §1 (EXACT; derivative of Safari presets, Variable Blur
  побитово валидирован).
- Android implementation: `GlassPreset` data class + маппинг pt≈dp (телефон); паддинги интегрировать
  в `WindowInsets` безопасной зоны.
- difficulty: low.
- expected fidelity: 95% (числа), визуал — зависит от шейдеров.
- performance risks: нет.
- fallback: значения по умолчанию для Android Material.

---

## 12. Тест-план glass

1. Полином: численная сверка контура `P(t)t²` с коэффициентами (значения ≠ 4.4-суперэллипс);
   тест на монотонность/границы t∈[0,1].
2. Meniscus: `x→sqrt(x(2−x))` — значения 0/0.5/1.
3. AA: при `sdf=0` alpha≈0.5; eps не меньше 1e-4.
4. plusL: unit-тест двух форм формулы; визуальная сверка с системным скриншотом (флаг).
5. Variable blur: LOD-формула (r<2 → 0.5r+1), 5-tap; `inputRadius=15` дефолт.
6. Пресеты: парсинг 3 наборов (VB/Floating/Docked) и применение к Compose-макету.
7. Деградация: принудительно tier=low/minimal — отсутствие крашей на API 29/31/33/36.
8. Frame budget: 60/120 Гц — профилирование всех pass'ов; отключение shadow первым.

## NOT FOUND / OPEN

- Значения uniform-дефолтов `edge_start/end`, `shadow_*`, `sdr_*`, `face_cm/bleed_cm/shadow_cm`,
  `holding_tone_opacity`, `clamp_limit`, `preserve_hue`, `sdr_white_value`.
- Нативный Metal-код specular/highlight (в наборе `.air` отсутствует); значения
  `CASDFGlass*Effect defaultValues` не декодированы.
- plusL-формула (две конкурирующие формы; аудит — INFERRED).
- Число mip-уровней, размеры тайлов/threadgroup, формат render targets, CPU-веса SIMD-blur.
- Точный порядок 9 фильтров `_mt_orderedFilterTypes`.
- Геометрия 12 сегментов reflection (как строится cap).
- Рецепты `platformContentGlass*` (blur/colorMatrix/tint значения).
- Значение `cornerCurveExpansionFactor` внутри CoreMaterial (только шейдерная константа).
- Есть ли отдельный specular-pass вообще (STRONG_INFERENCE, что нет).
