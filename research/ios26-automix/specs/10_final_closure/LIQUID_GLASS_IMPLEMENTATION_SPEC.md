# LIQUID_GLASS_IMPLEMENTATION_SPEC — clean-room Android-oriented спецификация (Phase 10)

**Дата:** 2026-09-13. **Owner:** Final Closure / Liquid Glass + vi-nnet Analyst (research-only).
**Вход:** `indexes/SHARED_BRIEFING.md`, `10_final_closure/STATE_RECONCILIATION.md`, `09_appos/APPOS_MASTER_SUMMARY.md §8`,
`09_appos/APPOS_GLASS_RECIPES.md`, `05_liquid_glass/*.md`, `08_closure/P1_GLASS_CLOSURE.md`, `P2_GLASS_CLOSURE.md`,
`P3_GLASS_CLOSURE.md`, `09_appos/APPOS_ML_MODELS.md`.
**Сырьё:** `decompiled_package/glass/air_ir/*` (native Metal AIR/LLVM IR), `decompiled_package/targets/{QuartzCore,CoreMaterial}`
(raw Mach-O arm64e), `appos/extracted/.../CoreMaterial.framework/*.materialrecipe`, `glass/presets/*.json`.
**Правило:** только проверенные данные; каждая позиция помечена `[EXACT] | [STRONG_INFERENCE] | [PARTIAL] | [UNKNOWN]`.
Ничего не заимствовано из lead-спеков без подтверждения. Приложения/`/root/LMG-VK` не изменялись.

---

## 0. Классификация: ALGORITHM CONSTANT vs DEVICE/RUNTIME TUNING

| Класс | Определение | Примеры |
|---|---|---|
| **ALGORITHM CONSTANT** | Математическая константа/формула/порядок, инвариантные между устройствами и кадрами; зашиты в IR или статические таблицы бинарника. Переносимы 1:1. | полином суперкруга, `1.5286649465560913`, менiscus, AA `1e-4`, LOD-формула, кап 7, порог Гаусса `0.002`, таблицы `downsample_blur_2/4`, порядок фильтров, `plusL/plusD` матрицы. |
| **DEVICE/RUNTIME TUNING** | Значение выбирается CPU/контекстом на устройстве: размеры tile, число mip-уровней на кадр, числовые веса для конкретного radius, формат render target, пороги/дефолты uniform-ключей (переопределяются рецептом), дефолты CASDF-эффектов. | `tileWidth/Height`, `levels`/`mipmapLevelCount`, `tile_simd_blur` weights, MTLPixelFormat слоя, значения `edge_*`/`shadow_*`/`sdr_*`, `CASDFGlass*Effect defaultValues`. |
| **PORT GUIDANCE** | Решение порта (не факт Apple). | AGSL manual-mip fallback, `BlendMode.Plus`, `graphics-shapes`. |

**Запрещено:** объявлять runtime tuning универсальной константой. Каждый пункт ниже несёт класс.

---

## 1. Геометрия (continuous corner / SDF)

### 1.1. Профиль continuous corner `[EXACT]` (ALGORITHM CONSTANT)

```
bez(t) = P(t) · t²
P(t)   = 0.268531 + 1.268030·t − 3.641220·t² + 3.156010·t³ − 0.926054·t⁴
контур: |uv| + 1 − 1/(1 − bez(t)),   t = min(|u|,|v|)/max(|u|,|v|)
```

- Источник: `glass/air_ir/glass_foreground_sdf_lpf.ll:464-551` (`supercircle_sdf`), double-константы
  `0x3FEDA23C…=0.926054`, `0x40093F822…=3.156010`, `0xC00D213800…=-3.641220`, `0x3FF449D9E…=1.268030`,
  `0x3FD12F9CA…=0.268531`; и embedded-исходник software-растеризатора в `targets/QuartzCore` (offset `0x37f3d2`).
- Metal-вариант добавляет `saturate(len)`: `dist = (len+1) − 1/(1 − t²·saturate(len)·P(t))`.
- **Не** степенной суперэллипс: фит `n` плавает ≈2.48…3.43; тезис `n≈4.4` REJECTED.

### 1.2. `cornerCurveExpansionFactor` (#44) `[EXACT]` (ALGORITHM CONSTANT)

- `+[CALayer cornerCurveExpansionFactor:]` `0x183ceb2d8` (QuartzCore): `"continuous"` → double
  `0x3FF875696E58A32F` = **1.528665**, иначе `1.0`.
- В float32 = `0x3FC3AB4B` — бит-в-бит совпадает с шейдерной `1.5286649465560913`
  (`0x3FF8756960000000` в IR). Вывод: это один и тот же float-множитель.
- Применение (CoreMaterial `_MTDimensionsForContinuousCornerRadiusInBounds` `0x1be8b122c`):
  `factor · r`, затем `frintp` (ceil) и clamp `≤ 0.5·min(W,H)`; в raw-дизасме присутствует дополнительная
  пара `fmul/fdiv` (квантование к сетке пикселей, P2 §2.3) — деталь не влияет на сам множитель.
- Android: эффективный радиус перед построением контура домножать на `1.5286649` для continuous-кривой.

### 1.3. AA края `[EXACT]` (ALGORITHM CONSTANT)

```
alpha = saturate(0.5 − d / max(fwidth(d), 1e-4))
```

- IR: `glass_background_sdf_lpf.ll:27-33`, `glass_foreground_sdf_lpf.ll:30-34`; `1e-4 = 0x3F1A36E2E0000000`.
- Ранее заявленное `ε=10⁻⁶` — REJECTED (spec §1.1). `ε=1e-3` (`0x3F50624DE0000000`) встречается как порог
  clamp/floordiv, не в fwidth-clamp.
- Android/AGSL: правило один-в-один (`fwidth()` доступен); при отсутствии derivatives — 2–4 eval SDF.

### 1.4. Meniscus `[EXACT]` (ALGORITHM CONSTANT)

```
x  = saturate((−sdf − offset) · inv_height)
m  = saturate(sqrt(x·(2−x)))
disp = amount · (1 − m) · normalize(disp_mat · normalize(grad))
```

- Instruction sequence подтверждён и в background (`glass_background_sdf_lpf.ll:56-74`), и в foreground
  (`glass_foreground_sdf_lpf.ll:56-74`): `(2−x)`, `*x`, `sqrt`, `saturate`.
- Foreground-ядро: дополнительно **хроматическая аберрация 7 сэмплов**: R — 3 тапа `+f·ab` (f=1, 2/3, 1/3),
  B — 4 тапа `−f·ab` (f=0, 1/3, 2/3, 1), G — смесь; нормализация `rgb *= (0.5, 1/3, 0.5)`, общий `/7`.
- Edge ramp: `rgb *= 1 − mix(edge_opacity_start, edge_opacity_end, saturate((sdf−edge_start)/(edge_end−edge_start)))`.

---

## 2. Variable blur `[EXACT]` (ALGORITHM CONSTANT + runtime weights)

Файл `glass/air_ir/variable_blur_frag_lpf.ll` (и `variable_blur_frag_lph` — идентичная структура в half).
Arg-метаданные: `texcoord0`(source), `texcoord1`(mask), `source_texture`, `mask_texture`, `noise_scale`, `noise_texture`,
`VariableBlurUniforms u` (32 B, 8 float), `edr_scale`.

Uniform-поля `VariableBlurUniforms` `[EXACT]`: `max_blur, noise, divide, offset, dx, dy, fade_mul, fade_add`.

```
r   = max_blur · saturate(mask.a)
lod = max(0, log2( r < 2 ? 0.5·r + 1 : r ))
taps: 4 выборки source_texture в texcoord0 + (±dx·lod, ±dy·lod)   // диагональные, не «крест»
avg = Σtaps · 0.25
if divide == 1: avg.rgb /= max(avg.a, FLT_EPSILON)               // unpremultiply, 0x3F50624DE0000000
avg.rgb *= edr_scale
if noise != 0: avg.rgb += noise_scale·avg.a·(noise_texture(pos/32).rgb − 0.5)
out = saturate(fade_mul·r + fade_add) · avg                      // ×4 компоненты (premultiplied)
```

- `offset` (uniform[3]) в этом ядре **не читается** — задаётся CPU для смещения.
- **CORRECTION:** формулировка «5 tap cross: center + (±dx,±dy)» (`05_liquid_glass/GLASS_SHADERS.md §4.1`,
  `GLASS_PIPELINE.md §3`) не подтверждается IR: source-сэмплов ровно **4** (строки 40/45/52/57), центр не
  сэмплируется; деление на 4, а не на 5. Затронутые отчёты: GLASS_SHADERS, GLASS_PIPELINE, GLASS_ANDROID_AGSL_PORT.
- **DEVICE/RUNTIME TUNING:** численные `dx,dy,noise_scale,fade_mul,fade_add,max_blur` задаются CPU/маской;
  в IR только формула.

---

## 3. Blur-цепочка: mip, кап 7, веса, Гаусс

### 3.1. Mip-пирамида и chain cap 7 `[EXACT-формула] / [PARTIAL-значения]`

```
chain  = floor(log2(max(src_w, src_h))) + 1          // полная цепочка до 1×1
levels = min(chain − 1, floor(log2(1.6·r)))          // r = blur radius
LOD scale = 2^min(chain, 7)                          // ЖЁСТКИЙ КАП 7 (2^7=128)
```

- Источник: `CA::OGL::compute_variable_blur_parameters` `0x183c010a8` (P3 §2.1), константа `1.6f` `@0x183db8fbc`.
- **Класс:** формула и кап 7 — ALGORITHM CONSTANT; `levels`/`mipmapLevelCount` для конкретного кадра — DEVICE/RUNTIME
  TUNING (зависит от размера и radius). **Не объявлять число уровней универсальным.**
- `Context::variable_blur_surface` `0x183b658ac` передаёт `(0.0, BlurParams[0])` → `levels=0` (compute-цикл пропускается),
  MTL-текстура создаётся с `mipmapLevelCount = chain`.

### 3.2. `downsample_blur_2/4` — статические веса `[EXACT]` (ALGORITHM CONSTANT)

`downsample_blur_2_weight` `@0x183dba034` (4 f32): `0.25342491, 0.20945647, 0.11824646, 0.04558462`
(`Σ = 1.000000`); offsets `@0x183dba018` (7 f32, используются 3 vec2).

`downsample_blur_4_weight` `@0x183db9ff8` (8 f32): `0.18899369, 0.16900714, 0.12085755, 0.06910989,
0.03159967, 0.01155237, 0.00337652, 0` (`Σ = 1.000003`); offsets `@0x183db9fc4` (13 f32).
Тапы: 2x — центр + 3 пары (7 тапов), 4x — центр + 6 пар (13 тапов). Offset-векторы статические, но масштабируются
runtime-скаляром (`[[x20+0x10]+0x60]`).

### 3.3. Гаусс `tile_simd_blur` / `narrow_blur` `[EXACT-алгоритм] / [PARTIAL-числа]`

```
normalized_half_normal_distribution<27>(r):
    w(i) = exp(−i² · 0.5 / r), i = 0..13            // 14 double
    w(0) = 1.0;  нормировка 1/(1 + 2Σw(i));  threshold = 0.002 @0x183db73c8
    отбрасываются хвостовые w < 0.002; count = число сохранённых
    HalfFloat::convert_float_array → fp16; зеркалирование → 2N+1 тапов (симметрично)
```

- `simdBlurParameters` `0x183b5b998`, `normalized_half_normal_distribution<27>` `0x183b314dc`; тот же порог 0.002
  использует `narrowBlurParameters` `0x183b31280` — общий для blur-семейства.
- Таблица выбора ядра `@0x183dbd28c` (radius count → kernel): >12 → `tile_simd_blur_27`; 11–12 → `23`; 9–10 → `19`;
  7–8 → `15`; 5–6 → `11`; 4 → `7`; 1–3/default → `5`. `[EXACT]`
- **DEVICE/RUNTIME TUNING:** числа весов для конкретного `r = f(radius, scale)` — только runtime (Metal-trace).

### 3.4. Tile/форматы `[EXACT-код] / [PARTIAL-значения]` (#47)

- `tile_simd_blur_surface` `0x183b5be54`: `dispatchThreadsPerTile {32,32,1}` → threadgroup/tile 32×32;
  `setThreadgroupSizeMatchesTileSize: NO`; halo `extend_surface(16|32)` (бит 0 `[BlurState+0x78]`).
  **Фактический `tileWidth/Height` выбирает Metal** (в бинаре не зашит) → DEVICE/RUNTIME TUNING.
- `tile_downsample_surface` `0x183b5af74`: threads = `(tileW/div, tileH/div)`, div=2 (типы 1/2/4) или 4 (`_8`);
  `tileW/H` runtime.
- Compute-пирамида: imageblock **16×32**, threadsPerThreadgroup `{16,16}` или `{16,32}` (ctx-бит 0x40) `[EXACT]`.
- Render target: attachment 0 = MTLPixelFormat destination-текстуры (`Surface+0xc0`); attachment 1 при бите 3
  `[ctx+0xed8]` = **115 (RGBA16Float, bpp=8)**; флаги `0x10→115`, `0x100000→80`, `0x20000→80`, default = drawable.
  Формат конкретного слоя Apple Music — runtime → `[PARTIAL]`.

---

## 4. Порядок фильтров `[EXACT]` (ALGORITHM CONSTANT)

`_mt_orderedFilterTypes` (CoreMaterial block `0x1be8ae488`, 9 CFString из QuartzCore):

```
1 averageColor  2 luminanceMap  3 luminanceCurveMap  4 curves  5 gaussianBlur
6 variableBlur  7 colorMatrix   8 colorSaturate      9 colorBrightness
```

`_mt_orderedFilterTypesBlurAtEnd` (`0x1be8ae3e4`): удаляются `gaussianBlur`/`variableBlur` из середины и
добавляются в конец; `colorMatrix/colorSaturate/colorBrightness` — в середину:

```
averageColor, luminanceMap, luminanceCurveMap, curves, colorMatrix, colorSaturate, colorBrightness, gaussianBlur, variableBlur
```

Выбор ветки — свойство `isBlurAtEnd` (`MTTintingFilteringMaterialSettings_isBlurAtEnd` `0x1be8ab6c0`;
`MTMaterialSettingsInterpolator_isBlurAtEnd` `0x1be8ac07c`). Дополнительно: optimization `29.5 < r < 35.0 → 29.5`;
dynamic quality `10` (low devices) / `100`.

---

## 5. Highlight (kind 6) и Displacement (kind 7) `[EXACT-defaults / UNKNOWN-shader]`

- `+[CASDFGlassHighlightEffect name]` = **"Glass Highlight"**; `+[CASDFGlassDisplacementEffect name]` =
  **"Glass Displacement"**; kind-байт +0x88 = **6** и **7** соответственно
  (`configureLayer:transaction:` `0x183d56478` / `0x183d56280`). Строк `specular` в QuartzCore нет (0 вхождений).
- `defaultValues` **DEVICE/RUNTIME TUNING** (значения объекта; Apple Music может их переопределять):
  - Displacement (`0x183b59a80`): `angle=0.0`, `curvature=1.0`, `height=20` (int). Uniforms: `height, curvature,
    angle, maskOffset`.
  - Highlight (`0x183b52da0`): `height=20`, `curvature=1.0`, `angle=π/2 (1.5707963267948966)`, `spread=π
    (3.141592653589793)`, `amount=0.5`, `color=CGColorGetConstantColor(kCGColorWhite)`; uniform struct:
    `height, curvature, angle, spread, amount, color(RGBA), global(bool)`.
- Per-pixel формула эффекта: фрагментного шейдера kind 6/7 в наборе `.air` **нет** → `[UNKNOWN]`.
- Android: воспроизводимо как edge/radial highlight с этими 6 параметрами, но без гарантии совпадения формулы.

---

## 6. Reflection (9-slice / 12 сегментов) `[PARTIAL]` (#42)

Реализация — **MobileSafari Swift** (`SFCapsuleCollectionView.Reflection`), не CoreMaterial/QuartzCore:
`MobileSafari.ReflectionView` (desc `0x18ba9c040`), `RoundedRectangleReflectionMaskView` (`0x18ba9c188`),
struct `RoundedRectangleReflection` (ровно 9 полей, desc `0x18ba98954`/`0x18baae8fc`).

| Поле | Значение (live JSON `capsule_effects.json`) | Происхождение |
|---|---|---|
| `cornerRadius` | 12 | EXACT |
| `maskRadius` / `maskInset` | 0.5 / 0.5 | EXACT |
| `thickness` | 2.3333333333333335 (= 1/scale + 2) | EXACT |
| `sourceThickness` | 5.666666666666667 (= 6 − 1/scale) | EXACT |
| `sourceOutset` | 3 | EXACT |
| `blurRadius` | 2 | EXACT |
| `sliceCountPerCorner` | **12** | EXACT |
| `isReversed` | false | EXACT |

- Построение пути/cap 9-slice, координаты 12 сегментов — **не декомпилировано** (Swift-vtable MobileSafari без имён)
  → `[PARTIAL]`; закрытие требует парсинга local symbols DSC `.symbols` или рендер-эксперимента.
- Маска собирается в `RoundedRectangleReflectionMaskView` из `fillView` + `shadowView` (`[INFERRED]`).
- **#42 final:** PARTIAL (конфиг/дефолты EXACT; геометрия сегментов NOT FOUND).

---

## 7. Рецепты материала `[EXACT]` (#43 закрыт)

`platformContentGlass.materialrecipe` (CoreMaterial.framework; XML plist, 1849 B, sha256 `589fc5c5…7858`):

```
materialSettingsVersion = 2
baseMaterial.materialFiltering.blurRadius = 45
colorMatrix (5x4):
  m11 0.921  m12 -0.265  m13 -0.027  m14 0  m15 0.235
  m21 -0.079 m22  0.735  m23 -0.027  m24 0  m25 0.235
  m31 -0.079 m32 -0.265  m33  0.973  m34 0  m35 0.235
  m41 0      m42 0       m43 0       m44 1  m45 0
```

- Варианты `platformContentGlass{Darker,Lighter,UltraDarker}.materialrecipe` **байт-идентичны** (тот же sha256) —
  вариантность вне рецепта (наблюдение EXACT; причина INFERRED).
- Loader: `com.apple.CoreMaterial` + `URLForResource:<name> withExtension:@"materialrecipe"`
  (`0x1be8b9e64`), override-бандл поддержан (`coreMaterialOverrideRecipeBundleURL`).
- **ALGORITHM vs RUNTIME:** `blurRadius=45` и matrix — статические данные рецепта (константы для данного build);
  итоговый uniform-буфер формируется CPU: `blurRadius` может быть заменён (optimization/quality/интерполяция).
- Совместимость с CA-стороной: рецепт переопределяет статический дефолт `inputBlurRadius = 30.0` (P1 §2.1).

---

## 8. Статические uniform-дефолты CPU-стороны `[EXACT-значения / PARTIAL-эффективность]` (#41)

Полные таблицы — `08_closure/P1_GLASS_CLOSURE.md §2` (46 float-ключей background + 11 foreground). Ключевое:

| Группа | Значения |
|---|---|
| Foreground | `refraction amount −150, height 100, offset −10`; `aberration −15/20, offset 0, angle 0`; `edge_start −4.5, edge_end −3.0, opacity 0→1` |
| Background refraction | inner `−150/60`, outer `100/50`, opacity `0.75`, dist `−11/−3` |
| Blur | `blur_radius 30`, opacity `1/0.1/0.1/0.4`, dist `−450/−3/−0.0/0` |
| Shadow | amount `200`, height `250`, radius `25`, opacity `1.0`, distance_offset `−50`, SDR shadow opacity `0` |
| Bleed | amount `400`, height `500`, dist `−400/−42`, opacity `0.2`, darken blend int 0 |
| Face/ColorMatrix | `face_opacity 1.0`; White/Black/Saturation = `1/0/1`; Fill `(0,0,0,0)` |
| SDR | holding tone enabled `0`, white `0.97`, gradient `−2.5/−1.5`; `clamp 0.0`, preserve_hue `0` |

- **Класс:** это статические дефолты CA-стороны (ALU-инструкции загрузки констант — EXACT), но **эффективные**
  значения зависят от рецепта/интерполяции/веса → `[PARTIAL]`; `holding_tone_opacity`, `sdr_shadow_dist0/1`,
  `clamp_limit`, `preserve_hue`, `sdr_white_value` — вычисляемые (fsub/fmla), не константы.
- **Не превращать в универсальные константы Android-порта.**

---

## 9. Композитинг `plusL`/`plusD` `[EXACT]` (ALGORITHM CONSTANT)

```
PlusL: out.rgb = in.rgb + tint.rgb·a ;  out.a = in.a + a        (матрица аффинная, без clamp)
PlusD: out.rgb = in.rgb + a·(1 − tint.rgb) ; out.a = in.a + a
```

- `_CAColorMatrixMakePlusL` `0x183c47de0`, `_CAColorMatrixMakePlusD` `0x183c47e3c` (raw disasm).
- Clamp — свойство рендера: SW `uqadd` (saturate), fallback `adc` (`0x183a9647c`), env `CA_DISABLE_PLUSL_CLAMP`
  (потребитель не локализован). IgnoreAlpha — отдельные фильтры (atom `0x230/0x232`, blend-коды `adi`/`pdi`).
- CoreMaterial-дубль `0x1be8be2c0`: `saturate(p1 + c·p2)`, значения light `0.185/0.85`, `0.48/0.70`, dark
  PlusD `0.90`, stroke dark `0.2/0.5/0.7`.
- Android: `BlendMode.Plus`/`PorterDuff.ADD` (насыщающее сложение) — совпадает с дефолтным clamp.

---

## 10. Android-маппинг (PORT GUIDANCE, не факты Apple)

| Компонент | Android-примитив | Класс | Статус Apple-данных |
|---|---|---|---|
| Continuous corner | собственный `Shape`/AGSL по `P(t)·t²`, `r_eff = 1.5286649·r` | PORT | EXACT |
| SDF refraction/meniscus | AGSL `RuntimeShader` + 1 выборка фона | PORT | EXACT (формула) |
| Chromatic aberration | AGSL 7 taps (или 3 при `amount≈0`) | PORT | EXACT |
| Variable blur | offscreen mip-пирамида + manual LOD + 4 tap diagonal + dither | PORT | EXACT (формула), weights runtime |
| Gaussian blur (backdrop) | `RenderEffect.blur` (двухпроходный) | PORT | algorithm EXACT у Apple |
| Tint/plusL | `BlendMode.Plus` на offscreen | PORT | EXACT |
| Reflection | Path + маска; `sliceCountPerCorner=12` — цель | PORT | PARTIAL |
| Highlight | радиальный/edge highlight по 6 параметрам | PORT | defaults EXACT, шейдер UNKNOWN |
| Uniform-дефолты | свои значения (дефолты Apple — recipe/runtime) | PORT | PARTIAL |

---

## 11. #42 / #47 — final verdicts

- **#42 (reflection) = PARTIAL.** Конфиг и дефолты 9 полей + `sliceCountPerCorner=12` — EXACT
  (MobileSafari Swift). Само построение 9-slice/cap и координаты 12 сегментов — NOT FOUND. `MobileSafari`
  Swift-слой, не CoreMaterial. Закрытие: local symbols DSC `.symbols`/Swift demangle или рендер-эксперимент.
- **#47 = PARTIAL (закрыто по формулам, открыто по runtime-значениям).** EXACT: выражение для `levels`/`chain`,
  кап 7 и `2^min(chain,7)`, `tile_simd_blur` `{32,32,1}`, `setThreadgroupSizeMatchesTileSize: NO`,
  imageblock 16×32, threads `{16,16}/{16,32}`, статические таблицы весов `downsample_blur_2/4`, attachment 1 = 115.
  Runtime (DEVICE TUNING): `tileWidth/Height`, число mip-уровней на кадр, числовые веса `tile_simd_blur`/`narrow_blur`,
  MTLPixelFormat конкретного слоя Apple Music. **Эти значения не универсальны и не переносятся как константы.**

---

## 12. NOT FOUND / UNKNOWN

- Формула per-pixel highlight (kind 6) и displacement (kind 7) — шейдерных ядер в `.air` нет.
- Полная цепочка uniform-значений, доходящих до GPU для Apple Music glass (только статические дефолты CPU-стороны).
- Геометрия reflection 12 сегментов (см. #42).
- Потребитель env-опции `CA_DISABLE_PLUSL_CLAMP` (generic parser).
- Числовые `tile_simd_blur`/`narrow_blur` веса для конкретного radius.

## 13. Provenance (ключевое)

```
Source file: decompiled_package/glass/air_ir/{variable_blur_frag_lpf,glass_background_sdf_lpf,glass_foreground_sdf_lpf}.ll
Framework: QuartzCore (Air); Evidence: shader_ir; Status: EXACT
Source file: decompiled_package/targets/QuartzCore
  _CAColorMatrixMakePlusL 0x183c47de0 / PlusD 0x183c47e3c;
  +[CALayer cornerCurveExpansionFactor:] 0x183ceb2d8;
  compute_variable_blur_parameters 0x183c010a8; tile_simd_blur_surface 0x183b5be54;
  tile_downsample_surface 0x183b5af74; simdBlurParameters 0x183b5b998;
  normalized_half_normal_distribution<27> 0x183b314dc; CASDFGlass{Highlight,Displacement}Effect defaultValues
  0x183b52da0 / 0x183b59a80; configureLayer 0x183d56478 / 0x183d56280
Source file: decompiled_package/targets/CoreMaterial
  _mt_orderedFilterTypes 0x1be8ac0b8 / block 0x1be8ae488; …BlurAtEnd block 0x1be8ae3e4;
  _MTDimensionsForContinuousCornerRadiusInBounds 0x1be8b122c; _MTDynamicBlurRadiusGraphicsQuality 0x1be8ab76c
Source file: appos/extracted/.../CoreMaterial.framework/platformContentGlass*.materialrecipe
Evidence: constant (plist); Status: EXACT
Source file: decompiled_package/glass/presets/capsule_effects.json (variable_blur/Effects_dec)
Evidence: live_json; Status: EXACT
```

## RETRACTED / CORRECTED (к предыдущим отчётам)

1. `05_liquid_glass/GLASS_SHADERS.md §4.1` / `GLASS_PIPELINE.md §3` / `GLASS_ANDROID_AGSL_PORT.md`:
   «variable blur — 5 tap cross, center + (±dx,±dy), сумма /4» → **4 тапа** `(±dx·lod, ±dy·lod)`, `Σ·0.25`,
   центральный сэмпл отсутствует (IR строки 40/45/52/57). Формулы LOD/dither/fade подтверждены.
2. `05_liquid_glass/GLASS_PIPELINE.md`: «число mip-уровней, tile/форматы задаются CPU» — теперь
   CPU-значения декодированы (P3), но остаются runtime; здесь фиксирована граница константа/tuning.
3. `05_liquid_glass/LIQUID_GLASS_ARCHITECTURE.md §1` uniform-дефолты поданы как известные «значения системы»:
   это статические дефолты CA-стороны, эффективные значения — recipe/runtime (см. §8).
