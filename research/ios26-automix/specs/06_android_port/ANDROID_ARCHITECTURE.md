# ANDROID_ARCHITECTURE — архитектура Android-порта audited Apple behavior (iOS 26)

Subagent #7 («Android Port Architect»). Статус: RESEARCH/SPEC ONLY.
Ничего в `/root/LMG-VK` не изменялось, реализация не создавалась. Всё, что ниже, — проектные
описания, имена классов/интерфейсов и потоки данных для будущего агента интеграции.

Авторитетный источник поведения — `07_audit/*` (RED_TEAM_AUDIT, DISPUTED_CLAIMS, RETRACTED_CLAIMS,
VERIFIED_CONSTANTS). Отчёты `01..05` использованы как evidence, но там, где аудит их откорректировал,
использована откорректированная версия. Реjected-claims в дизайн не переносятся.

Целевой стек (задан): Kotlin, Jetpack Compose, форк Media3 (`/root/LMG-VK/media3-m2`,
`com.liquidmusicglass.media3:*:1.11.0-lmg31`), собственный native C++ DSP, AGSL/Liquid Glass.
Media3 не переписывается — используются только его существующие seams (см. `MEDIA3_INTEGRATION.md`).

---

## 0. Инварианты дизайна (audit-driven)

1. **Complexity = 4 значения (0..3)**: `fallback`, `crossFade`, `crossFadeWithEffects`,
   `timeStretchedCrossFadeWithEffects`. `beatMatchedFilteredCrossFade` — это `Transition.Algorithm`
   (случай 0) / имя стратегии, а НЕ пятая complexity (RETRACTED A1, DISPUTED D1).
2. **Никаких cos/sin equal-power и Linkwitz-Riley/Q=0.707** в AutoMix-слоях Apple не найдено
   (RETRACTED R2/R3, D13). Android-кривые свода — собственные или fork-овские, это решение
   Android-стороны, а не реконструкция Apple.
3. **plusL — `[INFERRED]`** (RETRACTED A2, D24). В корпусе доказаны только имя фильтра и
   opacity/цвет. Формулу держать за фича-флагом и сверять по скриншотам, не выдавать за Apple.
4. **Полярность MediaAPI-предикатов** — per-function (A3): acousticness- и melodicness-предикаты
   возвращают TRUE для «незначимо», danceability — TRUE для «значимо». В порте реализуются
   семантические предикаты «significant ⟺ внутри полосы», а не инвертированные хелперы.
5. **Веса кандидатов — константы кода**, серверные веса не найдены (AUTOMIX_CONSTANTS §7).
   Внешний ресурс — только каталог стилей `TransitionStyles`, которого в корпусе нет.
6. **Time-stretch исполнитель**: диапазон `ts_rate` 0.03125..32 EXACT; место (граф vs AQ
   TimePitch) — NOT FOUND. Дизайн не фиксирует исполнителя, а даёт один контракт `TimeStretchSpec`.
7. **Lyrics renderer/parser у Apple — в отсутствующем `MusicCoreUI`**; в корпусе только AX-контракт
   классов и sample TTML. Всё, что не доказано, помечено `[UNVERIFIED]`.
8. **Squircle — полином `P(t)·t²`**, не `|x|^n+|y|^n`; `n≈4.4` REJECTED (D26, GLASS_GEOMETRY §1.4).

---

## 1. Целевой pipeline (сквозной контракт)

```
[1] Apple/local analysis
      MediaAPI audio-analysis / flexml-analysis JSON   (primary, EXACT schema)
      или локальный анализ (BPM/Key/Energy/ML)         (fallback, INFERRED)
            │  MediaApiAnalysisParser / LocalAnalysisBuilder
            ▼
[2] UnifiedTrackAnalysis            — immutable, versioned, кэшируемый (Kotlin data-модель)
            │  TransitionPlanner.plan(from, to, criteria)
            ▼
[3] TransitionPlan                  — immutable: Strategy, Complexity, Summary, Schedule,
            │                          DspGraphSpec, PlaybackState, TimeStretchSpec
            │  TransitionPlanCompiler
            ├──► CrossfadeSpec  ──► ExoPlayer.CrossfadeConfiguration (durationUs, curveType, entryOffsetUs)
            ├──► ParameterTimeline ──► DspGraphAudioProcessor (JNI) → native C++ DSP
            └──► TimeStretchSpec ──► SpeedProvider (SpeedChangingAudioProcessor)
            ▼
[4] native C++ DSP (float32, по буферам)   ← automation per-sample/step
            ▼
[5] существующий Media3 overlap/crossfade pipeline
      два MediaCodecAudioRenderer + secondaryAudioSink (DefaultRenderersFactory),
      PlayerAudioFadeControl (LMG-форк), AudioFadeControl.FadeEffectType
            ▼
[6] Media3 AudioSink (DefaultAudioSink) → AudioTrack
```

Ключевое правило: **planner и analysis не знают о Media3**; DSP-слой не знает о планировщике —
он получает только `ParameterTimeline` и `TimeStretchSpec`. Media3-слой не знает ни о том, ни о
другом — он получает `CrossfadeSpec` и уже существующие сообщения/провайдеры.

---

## 2. Логическая карта модулей (пакеты/кандидаты в Gradle-модули)

Приложение сейчас — один модуль `:app` (`com.lmg.vk`, namespace `com.lmg.vk`,
`minSdk=29`, `targetSdk=36`, Compose, CMake `src/main/cpp/CMakeLists.txt`). Ниже — предлагаемое
разделение **по пакетам** (Gradle-модули — опция, не обязательство; форк Media3 остаётся внешним
артефактом из `media3-m2`).

| Логический модуль | Пакет (предлагаемый) | Что содержит | Источник вдохновения (iOS) |
|---|---|---|---|
| Analysis | `com.lmg.vk.analysis` | `UnifiedTrackAnalysis`, `MediaApiAnalysisParser`, `LocalAnalysisBuilder`, `AnalysisRepository`, карты (`BeatGrid`, `BarGrid`, `LoudnessMap`, `VocalActivityMap`, `TonalityMap`, `SongStructure`, `GenreTree`) | MediaAPI + MusicKitInternal.AudioAnalysis (03, MEDIAAPI_ANALYSIS_SCHEMA) |
| AutoMix planner | `com.lmg.vk.automix.planner` | `TransitionPlanner`, `TransitionCriteria`, стратегии, `TransitionPlan`, `TransitionSummary`, `FailureReason`, schedulers | `_SonicKit_MusicKit_Packages.TransitionPlanner` |
| AutoMix runtime | `com.lmg.vk.automix.runtime` | `TransitionService`, `TransitionPlanCache`, `TransitionSession` (state), мост в `AudioService` | SonicKit SmartTransitions orchestration |
| DSP transport | `com.lmg.vk.dsp` | `DspGraphAudioProcessor : AudioProcessor`, `DspParameterProvider`, `ParameterTimeline`, `DspNativeBridge` (JNI) | `AVAudioMixProcessingEffect` + `DefaultDSPParameterProvider` |
| DSP native | `app/src/main/cpp/dsp/` (новые файлы, позже) | C++ node graph по 29 параметрам, automation player, time-stretch | `.dspg`/CADSPGraph (reconstruct by params) |
| Lyrics core | `com.lmg.vk.engine.lyrics.apple` (существующий) + `...apple.timeline` | уже есть `AppleTtmlParser`, `AppleLyricsModels`, `AppleTtmlClient`, `AppleTtmlCache`; добавить `LyricsTimeline`, `LyricsEvent`, `LyricsState` | MusicCoreUI (absent) → own port |
| Lyrics UI | `com.lmg.vk.ui.lyrics.apple` | Compose-рендерер строк/piece, x-bg, караоке-заливка, скролл, emphasis | `SyncedLyricsLineView` (AX contract) |
| Glass | `com.lmg.vk.ui.glass.shader` | AGSL-шейдеры, `RuntimeShader`, RenderEffect-цепочки, пресеты | QuartzCore Metal `.air` |
| Legacy (остаётся) | `com.lmg.vk.automix` (`AutoMixController`, `SmartTransitionFinder`, `CrossfadeEngine`, `DJEffectsEngine`, `FeatureExtractor`, `MLTransitionPredictor`, `BPMDetector`, `KeyDetector`, `EnergyAnalyzer`) | текущий AutoMix VK/ML | — |
| Legacy engine | `com.lmg.vk.engine.automix` (`AutoMixCoordinator`, `JuceHandoffController`, `AutoMixNativeEngine`) | текущий JUCE/Oboe путь; в новой схеме — только fallback/эксперимент | — |

Существующие реальные точки (read-only, подтверждены): `com.lmg.vk.engine.PlayerAudioChain`
(`PlayerAudioChain.kt:28` — анонимный `DefaultRenderersFactory.buildAudioSink` с цепочкой
`BassAudioProcessor → DjFxAudioProcessor → VolumeNormalizationProcessor`), `com.lmg.vk.engine.AudioService`
(`AudioService.kt:730` и `:1121` — `setCrossfadeConfiguration`), `com.lmg.vk.engine.AudioFxController`,
`com.lmg.vk.ui.glass.*`, `com.lmg.vk.ui.liquid.*`.

---

## 3. Data flow — компоненты по шаблону

### 3.1. Analysis ingestion (server schema → UnifiedTrackAnalysis)

- iOS behavior: прямая Codable-схема `MediaAPI.AudioAnalysisAttributes` / `FlexmlAnalysisAttributes`
  (`_SonicKit_MusicKit` reflection 0x102ec8..0x103081) + нормализованная `MusicKitInternal.AudioAnalysis`,
  которую видит planner.
- source evidence: `MEDIAAPI_ANALYSIS_SCHEMA.md` §3–§4 (EXACT schema), reflection table
  `_SonicKit_MusicKit` 0x102ec8..0x103081 (EXACT); `from(decoder:)` 0x27215a614 / 0x272156474 (EXACT).
- Android implementation (описание): `MediaApiAnalysisParser` (kotlinx.serialization, DTO с
  `ignoreUnknownKeys`) → `UnifiedTrackAnalysis` (immutable). Поля, которые planner реально использует
  (подтверждено семантически/численно), обязательны; остальные — `@Transient`/optional. Локальный
  fallback `LocalAnalysisBuilder` использует существующие `BPMDetector`, `KeyDetector`,
  `FeatureExtractor`, `EnergyAnalyzer`, `MLTransitionPredictor`; все производные поля помечаются
  `derived=true`, `confidence` ниже серверных.
- difficulty: medium.
- expected fidelity: 90% (схема EXACT; локальный fallback — эвристика).
- performance risks: `beatsInMilliseconds`/`barsInMilliseconds` — большие массивы (644/161 в live),
  парсинг на IO; не аллоцировать в аудиопотоке.
- fallback: нет `audio-analysis` → `.local` источник; нет и локального → `supportsTransition=false`
  и штатный plain crossfade.

### 3.2. UnifiedTrackAnalysis store/cache

- iOS behavior: `TransitionPlanner.Song { id, duration, analysis, context }`, `Analysis = .musicKit |
  .adaptiveMusic` (`AUTOMIX_ARCHITECTURE` §6, EXACT metadata).
- source evidence: `MEDIAAPI_TO_TRANSITIONPLANNER.md` §2 (`Song` Ma 0x272261280, `MusicKitAnalysis`
  Ma 0x27225d7cc — EXACT symbols).
- Android implementation: `UnifiedTrackAnalysis(version, trackId, durationMs, source, analysis?)` +
  `AnalysisRepository` (диск-кэш) + `TransitionPlanCache` по ключу
  `(fromId, fromAnalysisVersion, toId, toAnalysisVersion, criteriaHash)`.
- difficulty: low.
- expected fidelity: n/a (инфраструктура).
- performance risks: cache invalidation при смене анализа; размер JSON-анализа в памяти.
- fallback: in-memory LRU.

### 3.3. TransitionPlanner

- iOS behavior: единственная точка входа `TransitionPlanner.transition(from:to:criterias:) ->
  Result<Transition, FailureReason>` (0x272267700, EXACT); внутри guard → timing accuracy →
  StylingContext → strategy.style → Summary.
- source evidence: `AUTOMIX_ARCHITECTURE.md` §1, `AUTOMIX_STATE_MACHINE.md` §6 (EXACT).
- Android implementation: `TransitionPlanner.plan(from, to, criteria): Result<TransitionPlan,
  FailureReason>` — чистая функция, без I/O; детали в `ANDROID_AUTOMIX.md`.
- difficulty: high (крупный порт).
- expected fidelity: 70–80% (структура и формулы EXACT; селекторы 8/9/12 и часть длительностей NOT FOUND).
- performance risks: candidate generation аллоцирует; держать planner на `Dispatchers.Default`,
  результаты immutable.
- fallback: `FallbackCrossFadeStrategy` → `CrossfadeSpec` только с длительностью.

### 3.4. TransitionPlanCompiler

- iOS behavior: `Transition` (0x80 B) + `Transition.Summary` (0x50 B) + `Schedule` (continuous/stepped)
  → `[CMTime:[UInt32:Float]]` для `AVAudioMixProcessingEffect`.
- source evidence: `AUTOMIX_STATE_MACHINE.md` §8, `DSP_AUTOMATION.md` §5 (EXACT type/структура,
  данные NOT FOUND).
- Android implementation: `TransitionPlanCompiler` превращает `TransitionPlan` в `CrossfadeSpec`,
  `ParameterTimeline` (`ParameterEvent(timeUs, param, value)`), `TimeStretchSpec` и `TransitionUiState`.
  Никакого доступа к Media3 из planner.
- difficulty: medium.
- expected fidelity: 85% (транспорт).
- performance risks: расписание на длинный переход (сотни событий) — держать в плоских массивах.
- fallback: пустой timeline → DSP bypass (`bypa=0`).

### 3.5. Native C++ DSP

- iOS behavior: `AVAudioMixProcessingEffect initWithDSPGraphText:properties:parameterSchedule:identifier:`
  + `CADSPGraph*` из `libAudioDSP` (EXACT symbols); граф `.dspg` в корпусе отсутствует.
- source evidence: `DSP_GRAPH_ARCHITECTURE.md` §2/§4 (EXACT symbols), 29 параметров — `DSP_NODES.md` (EXACT).
- Android implementation: `DspGraphAudioProcessor implements AudioProcessor` (поверх
  `BaseAudioProcessor`), JNI в `liblmg_dsp.so`; описание — `ANDROID_DSP_ENGINE.md` и
  `MEDIA3_INTEGRATION.md`. Граф восстанавливается по инвентарю параметров, а не портируется из `.dspg`.
- difficulty: high.
- expected fidelity: 60–70% (ручки и диапазоны EXACT; топология/типы фильтров/кривые — INFERRED).
- performance risks: full-graph float32 на два трека; aux bus между рендерерами; запрет offload.
- fallback: `bypa=1` + Media3 `CrossfadeConfiguration` без DSP.

### 3.6. Media3 crossfade pipeline

- iOS behavior: MediaPlaybackCore `smartTransitionWillBeginFrom:to:...` + AVAudioMix.
- source evidence: `DSP_GRAPH_ARCHITECTURE.md` §6 (EXACT symbols); форк Media3 — `MEDIA3_INTEGRATION.md`.
- Android implementation: уже существующий в форке overlap двух рендереров
  (`DefaultRenderersFactory` secondary sink, `PlayerAudioFadeControl`, `CrossfadeTrackRouting`) +
  `ExoPlayer.CrossfadeConfiguration`. Compiler кладёт в него `durationUs`/`curveType`/`entryOffsetUs`.
- difficulty: low (интеграция), т.к. форк уже содержит crossfade.
- expected fidelity: 75% (тайминги переходов ≈ Apple; кривые — Android-выбор).
- performance risks: два AudioTrack одновременно; underrun при тяжёлом DSP.
- fallback: `CrossfadeConfiguration.DEFAULT` (свод выключен).

### 3.7. Lyrics (parser → timeline → Compose renderer)

- iOS behavior: parser/renderer в `MusicCoreUI` (отсутствует; EXACT-отрицание). Есть только
  AX-контракт классов и sample TTML.
- source evidence: `LYRICS_ARCHITECTURE.md` §2/§4 (EXACT строки и AX validation), TTML samples
  (md5 EXACT, THIRD_PARTY канал).
- Android implementation: существующий `AppleTtmlParser`/`AppleLyricsModels` используется как основа;
  добавить `LyricsTimeline` (event-driven, аналитический `evaluateAt(positionMs)`) и Compose-рендерер
  (одинаковый контракт с prior-art `AppleTtmlParser`, но без копирования недоказанных констант).
  Детали — `ANDROID_LYRICS.md`.
- difficulty: medium (парсер уже есть), high (пиксель-точный вид невозможен — нет эталона).
- expected fidelity: парсер/модель 95%; анимации/скролл 40–60% (NOT FOUND).
- performance risks: per-frame аллокации, измерение текста, LazyColumn recomposition.
- fallback: line-level режим (без слово-караоке), LRC.

### 3.8. Liquid Glass

- iOS behavior: Metal `.air` (SDF/meniscus/aberration/variable blur) + CoreMaterial-рецепты.
- source evidence: `GLASS_SHADERS.md` (EXACT), `GLASS_PIPELINE.md`, presets JSON (EXACT).
- Android implementation: Compose + `RuntimeShader` (AGSL) + `RenderEffect` + `graphicsLayer`;
  описание — `ANDROID_LIQUID_GLASS.md`.
- difficulty: high (AGSL без textureLod; backdrop capture).
- expected fidelity: 60–85% по компонентам (см. таблицу в файле).
- performance risks: full-screen SDF/blur выборки; деградация на mid/low.
- fallback: API<33 — `Modifier.blur` + ColorMatrix/clip; API<31 — статичный полупрозрачный фон.

---

## 4. Threading model

| Стадия | Поток | Правило |
|---|---|---|
| Fetch/parse analysis | `Dispatchers.IO` | никакой работы на main |
| Локальный анализ (BPM/ML) | `Dispatchers.Default`, bounded | отменяемо, приоритет ниже playback |
| Planner | выделенный single-thread executor (`PlannerExecutor`) | детерминизм, без общего состояния |
| Plan → player | main-thread `PlayerMessage.send()` (или `player.createMessage`) | доставка по позиции на playback-поток |
| DSP-параметры | lock-free SPSC ring buffer + `AtomicReference` снапшот плана | ноль локов/аллокаций на audio-потоке |
| Native DSP | audio callback (`queueInput`) | только `process()`, параметры применяются по фрейму |
| Lyrics timeline | immutable, читается UI-потоком; время — media position | один frame clock (`withFrameNanos`), без собственного накопления |
| Glass | RenderThread (RenderEffect/AGSL) | кэш SDF/backdrop, пересборка только при resize |

Правила безопасности: `TransitionPlan` — immutable (deep freeze), передаётся в native как
read-only; JNI-вызовы только из audio-потока (create/configure — до старта); любые ошибки JNI не
должны ронять приложение (как уже сделано в `AutoMixNativeEngine` через `runCatching` — сохранить
этот стиль).

---

## 5. Feature flags (предлагаемые ключи `AppSettings`/`PlayerSettings`)

| Флаг | Значения | Смысл |
|---|---|---|
| `automix.planner` | `off` / `legacy` / `apple` | источник плана перехода |
| `automix.dsp` | `off` / `passthrough` / `apple` | native DSP включён/выключен |
| `automix.auxBus` | `off` / `perTrack` / `shared` | шина delay/reverb (shared требует общего инстанса) |
| `automix.timeStretch` | `off` / `scheduled` | использовать `SpeedProvider` из плана |
| `automix.overlapSource` | `media3` / `fork` | разрешить форк-перекрытие или plain config |
| `lyrics.mode` | `legacy` / `ttmlLine` / `ttmlWord` | рендерер и гранулярность |
| `lyrics.wordLeadMs` | int, default 0 | prior art −100 мс — по умолчанию ВЫКЛ (`[UNVERIFIED]`) |
| `lyrics.scrollAnchorPct` | float, default 0.28 | prior art 28% (`[UNVERIFIED]`) |
| `lyrics.userScrollPauseMs` | long, default 4000 | prior art (`[UNVERIFIED]`) |
| `glass.agsl` | `auto` / `on` / `off` | RuntimeShader-путь |
| `glass.plusL` | `inferred` / `plainTint` | выбор формулы (`[INFERRED]`) |
| `glass.perfTier` | `auto` / `high` / `mid` / `low` | деградация эффектов |

Всё, что помечено `[UNVERIFIED]`, включается осознанно и не считается Apple-поведением.

---

## 6. Testing strategy

1. **Planner unit tests (JVM, golden):** зафиксированные `UnifiedTrackAnalysis` фикстуры из
   live JSON (`live_validation_raw_1776914757.json`, sanitized) → ожидаемый `TransitionPlan`
   (strategy, complexity, score, duration). Проверки: `score = base·Πfactors + tie·0.001`;
   отбраковка `score <= 0`; ничьи — первый; каскад fallback.
2. **Threshold tests:** tempo 0.16/0.287, min bars 8, `60/bpm` half/double, 0xfc-несовместимость,
   melodicness [0.25..1.0], acousticness [0.85..1.0], danceability [0.3..1.0].
3. **DSP native tests (host, gtest):** per-node impulse/sine отклик; automation evaluator по всем
   curveByte 0x00..0xff (значения из `DSP_AUTOMATION.md` §3.2); null-test (bypass = bit-exact);
   проверка границ параметров (clamp по 29 диапазонам).
4. **Media3 integration tests (instrumentation):** два рендерера стартуют/останавливаются без
   glitch; `entryOffsetUs` применяется; отсутствие underrun при `automix.dsp=apple`; fallback при
   отсутствии `audio-analysis`.
5. **TTML tests:** на реальных файлах — PLAIN/OFFICIAL дают одинаковые `p` (56/56) и разные span
   (644/364); x-bg CHAI: 10 outer внутри своей `p`; парсинг времени `2:40.160 == 160160`;
   `evaluateAt` не мигает на границах.
6. **Glass tests:** рендер-тесты шейдеров (Paparazzi/Robolectric где возможно), золотые скриншоты
   на устройстве для meniscus/edge/aberration; отдельный тест на отсутствие AGSL-краша на API<33.
7. **Regression:** сравнение энергии/громкости до/после перехода (энергетический null-test),
   визуальная A/B с legacy-crossfade.

---

## 7. Fidelity summary и главные риски

| Подсистема | Ожидаемая точность | Главный риск |
|---|---|---|
| UnifiedTrackAnalysis (схема) | 90% | поля, не подтверждённые планировщиком (energy/valence/arousal) |
| Planner (структура/формулы/пороги) | 70–80% | селекторы 8/9/12, длительности стилей, каталог `TransitionStyles` |
| Scoring | 90% (формула) | `param_1` tie-breaker NOT FOUND |
| DSP инвентарь 29 параметров | 95% (ручки) | топология/типы фильтров/кривая log |
| DSP автоматизация | 80% | семантика time-шкал, шаг 0.2 s |
| Media3 overlap | 90% (интеграция) | aux bus на два рендерера |
| Lyrics парсер/модель | 95% | — |
| Lyrics анимации/скролл | 40–60% | эталонные константы отсутствуют |
| Glass геометрия/SDF | 85% | specular NOT FOUND |
| Glass plusL | INFERRED | формула не доказана |

Топ-риски проекта:
1. **Aux bus (delay/reverb) при двух независимых рендерерах** — в Apple один граф видит оба трека;
   без shared-инстанса получим фазовые различия.
2. **Time-stretch**: место (граф vs `SpeedChangingAudioProcessor`) не подтверждено; pitch-preserving
   качество Android `SonicAudioProcessor` (time-domain) ниже возможного spectral-варианта.
3. **NOT FOUND каталог стилей** — конкретные start/end значения автоматизации (свипы HP/LP) не
   известны; порт даёт механизм, но не контент Apple.
4. **Lyrics pixel-fidelity** — нет iOS-эталона, только AX-контракт; любые «Apple-константы»
   из prior art запрещены.
5. **Glass specular/highlight** — нет ни шейдера, ни значений; визуальный разрыв.

---

## 8. Аудиторские корректировки, изменившие дизайн

| Корректировка | Что было в 01..05 | Что в дизайне |
|---|---|---|
| A1 (Complexity) | 5 уровней с `beatMatched=4` | `TransitionComplexity` 0..3; `beatMatchedFilteredCrossFade` — `Algorithm`/стратегия |
| A2 (plusL) | формула как факт | `glass.plusL=inferred`, две конкурирующие формы, флаг и сверка |
| A3 (predicates) | все «significant ⟺ band» | предикаты с явной полярностью; outgoing/incoming = STRONG_INFERENCE |
| R2/R3 (DSP) | cos/sin, LR/Q=0.707, свипы 20→250 | запрет на trig/LR; resonance в dB; диапазоны 10..22050 / 10..21829.5 |
| R4 (squircle) | `n≈4.4` | полином `P(t)·t²` |
| R5/R6 (lyrics) | TTML-парсер в корпусе; SYLLABLE отдельный | парсер — свой порт; SYLLABLE ≡ PLAIN (один файл) |
| D27 (old spec) | серверные веса | веса — константы кода |

## NOT FOUND / OPEN

- Формат `.dspg` и UInt32-ID параметров Apple — нет в корпусе (собственные ID, фиксированная
  таблица 29 параметров).
- Каталог `TransitionStyles` (JSON) — сам ресурс отсутствует; следовательно, нет реальных кривых
  и длительностей стилей.
- Значение `param_1` (tie-breaker `×0.001`) на call-site.
- Селекторы ветвлений `0x8/0x9/0xc` в сборке кандидата — природа не восстановлена.
- Кто вызывает `TransitionPlanner.transition(...)` в iOS (callers пусты).
- Точная псевдографика `logarithmic`-кривой (ветка curveByte 0x81).
- Полный набор `IncomingPlacement`/`OutgoingPlacement` случаев и их payload-структур.
- iOS-эталон lyrics-анимаций/скролла и glass-specular.
- Точное место time-stretch исполнения (граф vs AQ TimePitch vs AVFoundation unit).
