# ANDROID_AUTOMIX — UnifiedTrackAnalysis, порт TransitionPlanner, scoring/ranking (audited)

Subagent #7. RESEARCH/SPEC ONLY. Источник истины — `01_automix/*` + `07_audit/*`; при расхождении
побеждает аудит. iOS-имена приведены как контракт, Android-имена — предлагаемые классы/интерфейсы.
Кода не создано.

Evidence-статусы: `[EXACT]` из аудита/отчётов, `[STRONG_INFERENCE]`, `[INFERRED]`,
`[UNVERIFIED]`, `NOT FOUND`.

---

## 0. Сводка audited-фактов, на которых стоит дизайн

| # | Факт | Статус | Источник |
|---|---|---|---|
| 1 | Entry point — `TransitionPlanner.transition(from:to:criterias:) -> Result<Transition, FailureReason>` | EXACT | 0x272267700 |
| 2 | 4 strategy-пары (CandidateGen + Styling): BeatMatchedFiltered / DeadAirRemoval / Fallback / SmartCrossFade | EXACT | witness slots [8], thunks |
| 3 | Score = `base·Π(factors) + tie·0.001`, base 10 (4/6 факторов) или 15 (expanded tempo) | EXACT | 0x27222d644; bases 0x4024…/0x402e… |
| 4 | Winner: max score, `score <= 0` отбрасывается, строгое `>`, ничьи — первый | EXACT | 0x27222e800 |
| 5 | Tempo tolerance 0.16 / 0.287 (expanded); `60/bpm` half/double; 0xfc = incompatible | EXACT | pools 0x272298f30/38; 0x272219ff0 |
| 6 | Vocal penalty 0.75 (leading incoming vocal significant) | EXACT | `fcsel` 0x272234538 |
| 7 | Minimum matching bars = 8 | EXACT | 0x272235fd8 |
| 8 | Complexity 0..3 (fallback/crossFade/crossFadeWithEffects/timeStretched…) — 4 случая | EXACT (audit) | `...ComplexityO...` символы |
| 9 | `beatMatchedFilteredCrossFade` — Algorithm(0)/strategy, НЕ complexity | REJECTED rank 4 | A1/D1 |
| 10 | Musicality thresholds 0.85 / 0.3 / 0.25, полярность per-function | числа EXACT, смысл corrected | A3/D16 |
| 11 | `FailureReason` = musicalCompatibility / timingAccuracy | EXACT | только 2 FWC-символа |
| 12 | Веса — константы кода; серверный — только каталог `TransitionStyles` (нет в корпусе) | EXACT/\[NOT FOUND\] | constants scan |

---

## 1. UnifiedTrackAnalysis — модель

### 1.1. Компонент: модель анализа

- iOS behavior: planner видит `TransitionPlanner.Song.MusicKitAnalysis { genres, duration,
  audioAnalysis: MusicKitInternal.AudioAnalysis?, flexAnalysis, spatialTimingInformation }` либо
  `.adaptiveMusic`; серверный слой — `MediaAPI.AudioAnalysisAttributes` + `FlexmlAnalysisAttributes`.
- source evidence: `MEDIAAPI_TO_TRANSITIONPLANNER.md` §1–§2 (Ma 0x27225d7cc, 0x272261280; поле
  `audioAnalysis` типизировано `MusicKitInternal.AudioAnalysis` — EXACT symbol); reflection
  `_SonicKit_MusicKit` 0x102ec8..0x103081 (EXACT); live JSON (sanitized) — EXACT.
- Android implementation: immutable `UnifiedTrackAnalysis` (Kotlin data class) с плоскими массивами
  (`LongArray`/`DoubleArray`) вместо вложенных списков; `AnalysisSource` = `MEDIA_API` / `LOCAL` /
  `ADAPTIVE`; `analysisVersion` для кэша. DTO-парсер `MediaApiAnalysisParser` (kotlinx.serialization)
  маппит JSON-ключи 1:1; производные величины считает `UnifiedAnalysisFactory` (средний BPM,
  beatDuration, beatsPerBar, downbeats из bars и т.п.) с пометкой `derived`.
- difficulty: medium.
- expected fidelity: 90% (схема EXACT; локальный fallback — эвристика).
- performance risks: 644 beats + 602 loudness-сэмпла + vocalActivity на трек; парсинг на IO,
  хранение в примитивных массивах.
- fallback: `LocalAnalysisBuilder` на `BPMDetector`/`KeyDetector`/`FeatureExtractor`/`EnergyAnalyzer`
  (существуют в `com.lmg.vk.automix`) либо `source=NONE` → переход только fallback-длительности.

### 1.2. Поля модели (маппинг server → planner → Android)

| Planner-имя (iOS strings) | MediaAPI JSON | Android-поле | Planner использует | Статус |
|---|---|---|---|---|
| `beatsPerMinute`, `averageTempo` | `bpm.main/beginning/ending/percentDeviation` | `tempo.bpmMain/bpmBegin/bpmEnd/percentDeviation` | темп (0.16/0.287) | STRONG_INFERENCE (потребитель), JSON EXACT |
| `beatEvents`, `beatDuration` | `beats.beatsInMilliseconds` | `beatsMs` | bar match, stable, region algebra | STRONG_INFERENCE |
| `bars`, `beatsPerBar` | `beats.barsInMilliseconds` | `barsMs` | bar range, start downbeat | STRONG_INFERENCE |
| `timeSignature` | — (в JSON нет) | `timeSignature` (локально) | branch 9 (0x272243340) | число NOT FOUND |
| `tonality` (`tonic`, `mode`) | `key.*.tonic/mode` | `tonality` | `0x272231d04` | STRONG_INFERENCE |
| `loudnessMap` | `loudness.*.value` + `loudnessCurve.value` | `loudnessDb: FloatArray` (~2 Гц) | silence/fade/ratio | STRONG_INFERENCE |
| `vocalActivityMap` | `vocalActivity[].{start,end,strength,kind}` | `vocalActivity: List<VocalRegion>` | 0.75-фактор, relationship | STRONG_INFERENCE |
| `acousticness` | `acousticness.main` | `acousticnessMain` | 0.85-предикат | EXACT (числа) |
| `danceability` | `danceability.main` | `danceabilityMain` | 0.3-предикат | EXACT (числа) |
| `melodicness` | `melodicness.main` | `melodicnessMain` | 0.25-предикат + fallback тональности | EXACT (числа) |
| `structure`, `segments`, `sections`, `regions` | `phrases` (в live отсутствует) + bar/beat grid | `structure: SongStructure` | BeatMatched region pair map | STRONG_INFERENCE |
| `beatStabilityMap` | `bpm.percentDeviation` (предположение) | `beatStability` | stable suffix | NOT ESTABLISHED |
| `duration` | `durationInMillis` | `durationMs` | guard/лимиты 30/60 c | STRONG_INFERENCE |
| `minOutgoingStartSongTime`, `songTimeRange` | `fades`, `entry/exitPoints` | `regions` | placement | STRONG_INFERENCE |
| `energy`, `valence` | `energy.*`, `valence.*` | `energyMain`, `valenceMain` | НЕ используется | NOT ESTABLISHED |
| FlexML (`entryPoints`, `exitPoints`, `fadeToBlack`, `visualTempo`, `videoEvents`, `arousal`) | flexml-analysis | `flex: FlexAnalysis?` | planner — нет; engine — вероятно | engine STRONG_INFERENCE |

Правило: поля со статусом NOT ESTABLISHED парсить и хранить, но НЕ использовать в предикатах —
иначе появится недоказанная логика.

### 1.3. Компонент: AdaptiveMusic-вариант

- iOS behavior: `Song.Analysis = .musicKit(...) | .adaptiveMusic(AdaptiveMusicAnalysis)`
  (`AUTOMIX_ARCHITECTURE` §6, EXACT reflstr).
- source evidence: reflstr-имена `musicKit`, `adaptiveMusic` (`_SonicKit_MusicKit_Packages` 0x97800–).
- Android implementation: sealed interface `TrackAnalysisSource { MusicKit, Adaptive, Local }`;
  planner работает через интерфейс `AnalysisView` (getters), а не через конкретный класс.
- difficulty: low.
- expected fidelity: структура — 100%, поведение AdaptiveMusic — `[UNVERIFIED]` (в корпусе нет тел).
- performance risks: нет.
- fallback: `Local`.

---

## 2. План и Summary

### 2.1. Компонент: TransitionPlan / Summary / Schedule

- iOS behavior: `Transition { schedule, audioGraph, summary, musicalCompatibility, timingAccuracy,
  strategy, styleID, ... }` (0x80 B); `Transition.Summary { strategy, outgoingSongTimeRange,
  incomingSongTimeRange, musicalCompatibility, timingAccuracy }` (0x50 B).
- source evidence: `_type_layout_string` Transition @0x2722a2710 (0x80), Summary @0x2722a2570 (0x50)
  — аудиторские адреса (A4); reflstr из `AUTOMIX_STATE_MACHINE.md` §8 (EXACT).
- Android implementation:
  `TransitionPlan(algorithm, complexity, styleId, summary, schedule, dspGraph, playbackState,
  timeStretch, noFollowUp)`;
  `TransitionSummary(strategy, outgoingRange, incomingRange, musicalCompatibility, timingAccuracy)`;
  `TransitionSchedule.ContinuousContinuous / Stepped(stepSeconds)` (шаг по умолчанию 0.2 s —
  `SteppedSchedule.defaultStepDuration`, EXACT).
- difficulty: low.
- expected fidelity: 95% (транспортные структуры).
- performance risks: immutable-копии; не сериализовать на каждый кадр.
- fallback: `TransitionPlan.stub(fallbackDurationMs)`.

---

## 3. TransitionPlanner — порт

### 3.1. Компонент: entry point

- iOS behavior: статический `transition(from:to:criterias:)`; пустой результат стратегии →
  "Transition Planner: No transition." и `Result.failure`; иначе лог summary.
- source evidence: 0x272267700 (EXACT), логи EXACT.
- Android implementation: `class TransitionPlanner(schedulers, strategyCatalog, clock)` с
  `fun plan(from: UnifiedTrackAnalysis, to: UnifiedTrackAnalysis, criteria: TransitionCriteria):
  Result<TransitionPlan, TransitionFailureReason>`; внутри — чистые функции без I/O; инъекция
  `Random`/`Clock` запрещена (детерминизм).
- difficulty: high.
- expected fidelity: 75%.
- performance risks: генерация десятков кандидатов × региональная алгебра; ограничить бюджет и
  закэшировать.
- fallback: при исключении/таймауте — `FallbackCrossFadeStrategy`.

### 3.2. Компонент: guard + timing accuracy

- iOS behavior: ранний guard `FUN_272267e70` (SmartTransitionsError tag 6); timing accuracy
  `FUN_272267f4c` + witness `+0x38`, 4 лога ("Songs not suitable…", "Outgoing/Incoming/Both …
  timing accuracy issues"); снижение complexity при issues (`FUN_272221f08`), наборы из
  `FUN_272224c30` ("All transitions allowed…" / "Only beat-matched or fallback…" / "Only fallback…").
- source evidence: `AUTOMIX_STATE_MACHINE.md` §3 (EXACT).
- Android implementation: `PlannerGuard.check()` → `Eligibility`; `TimingAccuracyValidator` возвращает
  `TimingAccuracy(songIssues: Set<SongIssue>)` (`STEREO`, `SPATIAL` — из строк "Stereo and spatial
  timing inaccurate", EXACT); `ComplexityReducer` сужает множество разрешённых complexity
  (`ALL`, `BEAT_MATCHED_OR_FALLBACK`, `FALLBACK_ONLY`, `NONE`).
- difficulty: medium.
- expected fidelity: 80% (строки и роли EXACT; побитовая раскладка наборов — NOT FOUND).
- performance risks: нет.
- fallback: `FALLBACK_ONLY`.

### 3.3. Компонент: StylingContext

- iOS behavior: `FUN_272229b1c` строит StylingContext (логи "Styling context: Outgoing/Incoming
  song ID = %s."), внутри `FUN_27222a4b4` (switch musicKit/adaptive) и `FUN_2722200cc` (~20 helpers).
- source evidence: `AUTOMIX_ARCHITECTURE.md` §1 (EXACT).
- Android implementation: `StylingContext(outgoing: StyledSong, incoming: StyledSong,
  criteria: TransitionCriteria, reducedComplexities, placement: PlacementContext)`; `StyledSong`
  — view на `UnifiedTrackAnalysis` с предвычисленными region-кандидатами.
- difficulty: medium.
- expected fidelity: 70%.
- performance risks: предвычисления на трек — кэшировать.
- fallback: прямые ссылки на `UnifiedTrackAnalysis` без предвычислений.

### 3.4. Компонент: strategy dispatch и каталог

- iOS behavior: witness-вызов `(**(wt+0x18))(out, stylingContext, …)` = `style`; при пустом
  результате — `DefaultStylingStrategyCatalog` (`FUN_2722469f0`) перебирает все алгоритмы
  ("All algorithms: Styling result not identified.").
- source evidence: `AUTOMIX_ARCHITECTURE.md` §1/§3 (EXACT witness tables: 0x2884ad878/938/9f8/aa8,
  catalog 0x2884adaf8).
- Android implementation:
  интерфейсы `CandidateGenerationStrategy` (`fun candidates(context): List<TransitionCandidate>`)
  и `StylingStrategy` (`fun style(context): TransitionPlan?`); реализации:
  `BeatMatchedFilteredCrossFadeStrategy`, `SmartCrossFadeStrategy`, `DeadAirRemovalStrategy`,
  `FallbackCrossFadeStrategy`; `DefaultStylingStrategyCatalog` пробует по порядку
  `[beatMatchedFiltered, smart, deadAir, fallback]` (порядок — из описания и логики каталога,
  `[INFERRED]`).
- difficulty: medium.
- expected fidelity: 85% (имена/связки EXACT; порядок каталога — INFERRED).
- performance risks: перебор умножает стоимость; кэш отрицательных результатов по паре.
- fallback: fallback-стратегия всегда последняя.

### 3.5. Компонент: построение Summary

- iOS behavior: `FUN_27226838c` собирает `Transition.Summary` (0x50; в одном пути копируется 0x51 —
  необъяснимая дельта, A6); затем `FUN_2722685b4`, флаг no-follow-up (`*(result+0x1d8)=0`,
  `FUN_272269220`).
- source evidence: `AUTOMIX_ARCHITECTURE.md` §1 (EXACT).
- Android implementation: `TransitionSummaryBuilder.build(plan)` + `TransitionPlan.noFollowUp`
  (в Android — флаг «не запускать авто-переход к следующему треку»).
- difficulty: low.
- expected fidelity: 95%.
- performance risks: нет.
- fallback: summary без диапазонов.

---

## 4. Candidate generation

### 4.1. Компонент: structural path (BeatMatched)

- iOS behavior: `FUN_27222e528 → FUN_27222f54c` (region pair map; требует SongStructure обеих песен,
  иначе "Outgoing/Incoming song structure not available"); `FUN_27222fa50` candidate outgoing end
  downbeats; `FUN_27222fd74` phrase/segment пары; фильтры/сортировки `FUN_272230274/…/2722305c8`.
- source evidence: `AUTOMIX_CANDIDATE_GENERATION.md` §2 (EXACT функции/логи).
- Android implementation: `StructuralCandidateGenerator`:
  `buildRegionPairMap(outgoing, incoming): RegionPairMap?` → `candidateOutgoingEndDownbeats()` →
  `buildPhraseSegmentPairs()` → фильтры `complexityGate`, `nonSilentRegions`; только эта стратегия
  вызывает region pair map (первый—единственный, DISTINCT path, EXACT по xref).
- difficulty: high.
- expected fidelity: 65% (структура EXACT, детали фильтров/сортировок — частично).
- performance risks: O(beats×bars) на пару; предвычислять downbeat-индексы.
- fallback: unstructured path.

### 4.2. Компонент: region algebra

- iOS behavior: последовательный перебор `FUN_2722307ec`:
  scaled (`FUN_2722315f8`), doubled incoming (`FUN_272233100`), halved incoming (`FUN_2722335b4`),
  stable bar range (`FUN_27223218c`, "Stable suffix duration zero"/"Beats not stable"),
  truncated (`FUN_272231b68` + `272233804/272233888`), shifted (`FUN_272233adc/272233c88`),
  non-silent outgoing (`FUN_272232790`, `FUN_2722305c8`), style region pair (`FUN_272234b0c`),
  start downbeat (`FUN_272236920`), truncated styling (`FUN_27223c3ac`), incoming styling region
  (`FUN_27223c184`), incoming start time (`FUN_27223d324/27223d6cc`), stable duration before fade
  (`FUN_27223cd58`).
- source evidence: `AUTOMIX_CANDIDATE_GENERATION.md` §3 (EXACT таблица).
- Android implementation: `RegionAlgebra` — чистые функции над `Region`/`BarRange`/`BeatRange`:
  `scaledRegionPair`, `doubledIncoming`, `halvedIncoming`, `stableBarRange`, `truncatedRegionPair`,
  `shiftedRegionPair`, `nonSilentOutgoing`, `styleRegionPair`, `startDownbeat`, `incomingStartTime`,
  `stableDurationBeforeFadeOut`. Стабильность битов в числах — NOT FOUND → конфигурируемый предикат
  `BeatStability.isStable()` с дефолтом по `beatStabilityMap`, если он есть.
- difficulty: high.
- expected fidelity: 60–70%.
- performance risks: много промежуточных регионов; переиспользовать буферы.
- fallback: `shiftedRegionPair`/fallback-регион.

### 4.3. Компонент: сборка кандидата (FUN_272234380)

- iOS behavior: три ветки по селектору `param_10[0] == 0xc / 0x9 / 0x8`; ветки собирают 4/6
  факторов и стартовое произведение 10.0/15.0:
  - `0xc` (expanded tempo): shifted region + tempo(обычный/expanded) + bar count + leading vocal +
    trailing loudness; 6 факторов; base 15.0;
  - `0x9`: tempo + time signature (`param_8&1`) + bar count + vocal relationship + leading vocal +
    trailing loudness; 6 факторов; base 10.0;
  - `0x8`: tempo + leading vocal + bar count ratio + trailing loudness; 4 фактора; base 10.0.
- source evidence: `AUTOMIX_CANDIDATE_GENERATION.md` §4 (структура EXACT, селекторы UNVERIFIED).
- Android implementation: `CandidateAssembler.assemble(styleId, selector, regionPair, checks)`
  возвращает `TransitionCandidate(styleId, selector, regionPair, factors: DoubleArray, score)`.
  Селекторные значения 8/9/12 не типизировать как публичный enum — внутренние константы с
  комментарием `[UNVERIFIED]`.
- difficulty: high.
- expected fidelity: 70% (ветки/факторы EXACT, семантика selector — нет).
- performance risks: аллокация `DoubleArray` на кандидата — пул/переиспользование.
- fallback: ветка `0x8` (минимальный набор).

### 4.4. Компонент: unstructured path (DeadAir/Fallback/Smart)

- iOS behavior: `FUN_27222e69c` общий для трёх стратегий (фиксированные длительности);
  алгоритм-специфичные пути: Smart `FUN_27223a4a4`, DeadAir `FUN_272237b14`, Fallback `FUN_272239248`
  (гарантия региона не короче Fallback-длительности, "Song duration less than Fallback Cross-Fade
  duration"); привязка «функция→алгоритм» — STRONG_INFERENCE.
- source evidence: `AUTOMIX_ARCHITECTURE.md` §3, `AUTOMIX_CANDIDATE_GENERATION.md` §8.
- Android implementation: `UnstructuredCandidateGenerator(durationProvider)`; реальная длительность
  fallback — параметр `TransitionStyle` (значение NOT FOUND), задаётся через
  `FallbackCrossFadeStyle(durationMs)` с дефолтом из настроек (не выдавать за Apple).
- difficulty: medium.
- expected fidelity: 70%.
- performance risks: нет.
- fallback: константа из UI-настроек.

---

## 5. Scoring и ranking (audited)

### 5.1. Компонент: score кандидата (FUN_27222d644)

- iOS behavior (псевдокод):
  `product = base; for w in weights: product *= w; if product <= 0 return product;
  return tieBreaker*0.001 + product;` — score хранится по смещению `+0xF8` записи кандидата 0x100 B.
- source evidence: 0x27222d644 (EXACT); bases `0x4024000000000000`=10.0 и
  `0x402e000000000000`=15.0 (EXACT); tie-scale `0x3f50624dd2f1a9fc`=0.001 (EXACT);
  score offset `+0xF8` (`FUN_27222e0ec`, EXACT).
- Android implementation: `CandidateScorer.score(tieBreaker: Double, base: Double,
  factors: DoubleArray): Double` — дословная формула; накопление без промежуточных боксов;
  `tieBreaker` провайдер `TieBreakerProvider` (значение на call-site NOT FOUND → дефолт 0.0 и
  фича-флаг для экспериментов).
- difficulty: low.
- expected fidelity: 95% (сама формула), реальный tie-breaker — 0%.
- performance risks: нет.
- fallback: `tieBreaker = 0.0`.

### 5.2. Компонент: факторы (weights)

| # | Фактор | Значение | iOS-функция | Android |
|---|---|---|---|---|
| 1 | Tempo relationship | 1.0 / 0.0 | `FUN_272236498` | `Factors.tempoRelationship` |
| 2 | Matching bar count compatible | 1.0 / 0.0 | `FUN_272235fd8` | `Factors.barCountCompatible` |
| 3 | Leading incoming vocal significant | 0.75 / 1.0 | `FUN_272235198` | `Factors.leadingVocalPenalty` |
| 4 | Trailing incoming loudness ratio | value / 1.0 | `FUN_272235840` | `Factors.trailingLoudnessRatio` |
| 5 | Time signature compatible | 1.0 / 0.0 | `FUN_272243340` | `Factors.timeSignatureCompatible` |
| 6 | Vocal activity relationship | 1.0 / 0.0 | `FUN_272236188` | `Factors.vocalRelationship` |
| 7 | Expanded/shifted region applicable | 1.0 / 0.0 | `FUN_272236498(flag=1)` | `Factors.expandedRegionApplicable` |

- iOS behavior: «весов важности» кроме 0.75 и base 10/15 нет; любой нулевой фактор обнуляет score.
- source evidence: `AUTOMIX_SCORING.md` §2 (EXACT значения и функции).
- Android implementation: enum `TransitionFactor` + `FactorEvaluator.evaluate(context): DoubleArray`;
  каждая проверка возвращает `Double` строго из {0.0, 1.0, 0.75, ratio}.
- difficulty: medium.
- expected fidelity: 85% (значения EXACT; формула ratio `FUN_272235840` NOT FOUND → 1.0-safe).
- performance risks: нет.
- fallback: `trailingLoudnessRatio = 1.0`.

### 5.3. Компонент: winner selection (FUN_27222e800)

- iOS behavior: первый проход отбрасывает `score <= 0.0`; второй — строгое сравнение `>` (ничьи в
  пользу ранее добавленного); победившая запись копируется 0x100 байт.
- source evidence: 0x27222e800 (EXACT, компактный цикл stride 0x100).
- Android implementation: `CandidateRanker.pickWinner(candidates): TransitionCandidate?`
  — `filter { it.score > 0.0 }.maxByOrNull` с сохранением порядка (Kotlin `maxByOrNull` может вернуть
  последний при равенстве — реализовать явный цикл `if (best == null || c.score > best.score)`).
- difficulty: low.
- expected fidelity: 100%.
- performance risks: явный цикл, без сортировок.
- fallback: `null` → каталог → failure.

---

## 6. Пороги и предикаты (audited)

### 6.1. Tempo

- iOS behavior: `TempoRelationship.between(tol, ...)`: `d = 60.0/bpm` (beat duration) по флагу
  half/double; `|a-b| <= tol`; возврат тега `< 0xfc` = compatible, `0xfc` = incompatible; при
  отсутствии/множестве темпов — Compatible (проверка пропускается).
  Толеранс 0.16 (0x3fc47ae147ae147b) / 0.287 (0x3fd25e353f7ced91) — выбор `fcsel`
  (0x2722365cc / 0x272243ac4).
- source evidence: 0x272219ff0, pool 0x272298f30/38 (EXACT).
- Android implementation: `TempoRelationship.between(outTempo, inTempo, expanded: Boolean):
  TempoRelationship` (enum `ONE_TO_TWO`, `ONE_TO_ONE`, `TWO_TO_ONE`, `INCOMPATIBLE`); константы
  `TempoTolerance.NORMAL = 0.16`, `EXPANDED = 0.287`; half/double — `TempoBinaryScaleFactor`
  (`ONE_TO_ONE`, `ONE_TO_TWO`, `TWO_TO_ONE`, `HALF` — имена EXACT, raw-значения NOT FOUND →
  работаем только через три ветки `60/bpm`, `bpm`, `60/(2·bpm)`).
- difficulty: medium.
- expected fidelity: 90%.
- performance risks: нет.
- fallback: compatible при отсутствии темпов (как в оригинале).

### 6.2. Bar count / bars

- iOS behavior: `matchingBarCount > 7` (т.е. ≥8) → compatible (`FUN_272235fd8`);
  `barCountRatio` (`FUN_27223550c`), `matchingBarCount` (`FUN_272235d08`); stable suffix
  (`FUN_27223218c`, числа NOT FOUND).
- source evidence: `AUTOMIX_SCORING.md` §4.2 (EXACT).
- Android implementation: `BarCompatibility.matchingBarCountCompatible(count): Boolean = count > 7`;
  `BarGrid.ratio(outgoing, scaledIncoming)`.
- difficulty: low.
- expected fidelity: 95%.
- performance risks: нет.
- fallback: `false` (нулевой фактор → кандидат отбрасывается).

### 6.3. Musicality — **с исправленной полярностью** (A3/D16)

- iOS behavior (как реально в коде):
  - `FUN_272243f80` (acousticness): TRUE **вне** полосы `[0.85, 1.0]` ⇒ функция вычисляет
    «insignificant»;
  - `FUN_27224400c` (danceability): TRUE **внутри** полосы `[0.3, 1.0]` ⇒ «significant»;
  - `FUN_2722341d4` (melodicness): TRUE **вне** полосы `[0.25, 1.0]` ⇒ «insignificant».
- source evidence: `MEDIAAPI_TO_TRANSITIONPLANNER.md` §3 + поправка `RED_TEAM_AUDIT` §4.3 /
  `RETRACTED_CLAIMS` A3 / `DISPUTED_CLAIMS` D16 (числа EXACT, полярность corrected).
- Semantic composition (`FUN_272242fd0`, STRONG_INFERENCE — атрибуция outgoing/incoming только по
  логам): incompatible, если у любой из песен acousticness significant, ИЛИ у любой danceability
  insignificant; иначе Compatible.
- Android implementation: `Musicality.acousticnessSignificant(v) = v in 0.85..1.0`;
  `danceabilitySignificant(v) = v in 0.3..1.0`; `melodicnessSignificant(v) = v in 0.25..1.0`;
  `Musicality.compatibility(out, in): MusicalityCompatibility` — возвращает причину
  (`AcousticnessSignificant(Side)`, `DanceabilityInsignificant(Side)`, `Compatible`) для логов.
  НЕ переносить инвертированные хелперы Apple в публичный API.
- difficulty: low.
- expected fidelity: 90% (числа EXACT; композиция/стороны — STRONG_INFERENCE).
- performance risks: нет.
- fallback: `Compatible` при отсутствующем атрибуте.

### 6.4. Tonality / melodicness fallback

- iOS behavior: `FUN_272231d04`: если `TonalityRelationship` tag == 3 (incompatible), но
  melodicness обеих песен insignificant → тональности совместимы (лог "Tonalities are compatible:
  Tonality relationship incompatible but checking for insignificant melodicness."); лог при
  недоступной тональности.
- source evidence: `AUTOMIX_SCORING.md` §4.3 (EXACT).
- Android implementation: `TonalityCompatibility.check(out, in, melodicnessOut, melodicnessIn):
  TonalityVerdict`; при отсутствии тональностей — `Compatible` (лог-факт) c флагом `Reason`.
- difficulty: medium.
- expected fidelity: 80%.
- performance risks: нет.
- fallback: Compatible.

### 6.5. Loudness / fade

- iOS behavior: порог тишины `value <= -30 dB` (0xc03e…), окно `[x-10, x]` (0xc024…), верхний фильтр
  `min(x, 10.0)`, «duration delta significant» при `|Δt| >= 2.0 s`, средняя громкость не-silent
  региона (порог −30, множитель 1.0).
- source evidence: `AUTOMIX_CONSTANTS.md` §2, `AUTOMIX_SCORING.md` §4.4 (EXACT).
- Android implementation: `LoudnessMap.minDb`, `silenceWindow(thresholdDb = -30.0)`,
  `fadeWindow(deltaDb = 10.0)`, `durationDeltaSignificant(deltaMs) = abs(delta) >= 2000.0`.
- difficulty: low.
- expected fidelity: 85% (формула trailing ratio NOT FOUND).
- performance risks: сканирование 602 сэмплов на кандидата — предвычислять региональные агрегаты.
- fallback: нейтральные значения.

---

## 7. Complexity gating (corrected)

- iOS behavior: `FUN_27222a728`: `if (*(byte*)(context+0x78) < *criteria.maxComplexity) OK` (иначе лог
  "Transition complexity … incompatible with maximum complexity"); далее сверка с reduced
  complexities песен ("No transitions allowed" при issues и несовпадении).
- source evidence: `AUTOMIX_STATE_MACHINE.md` §4 (EXACT код), поправка `A1/D1` — лестница 0..3.
- Android implementation:
  `enum class TransitionComplexity { FALLBACK, CROSS_FADE, CROSS_FADE_WITH_EFFECTS,
  TIME_STRETCHED_CROSS_FADE_WITH_EFFECTS }` (ordinal = rank 0..3, сравнение ординалов);
  `ComplexityGate.check(candidateComplexity, criteriaMax, reduced): Boolean`.
  **Запрещено** добавлять 5-й элемент `BEAT_MATCHED` — `beatMatchedFilteredCrossFade` живёт только
  как `TransitionAlgorithm`.
- difficulty: low.
- expected fidelity: 100% (структура), значения reduced-наборов — NOT FOUND.
- performance risks: нет.
- fallback: FALLBACK.

---

## 8. Placement / Criteria

- iOS behavior: `Criteria { outgoingPlacement, incomingPlacement, maximumTransitionComplexity,
  allowedGenreIDs, deniedGenreIDs }` (layout, прочитано 0x22 байта); placement-случаи:
  `IncomingPlacement { InSong, After, Early(EarlyPlacementConstraint) }`;
  `OutgoingPlacement { InSong, After, Early, Late(LatePlacementConstraint) }`; constraints
  `InSong/After/Within` (вложенные CodingKeys). Проверки:
  incoming `region.start ∈ [reqMin, reqMax]` (`FUN_272214200`);
  outgoing case==3 (Late) → `region.start >= min`, иначе диапазон (`FUN_272213d4c`);
  incoming constraint `region.end <= maxIncomingRegionEnd` (`FUN_272229758`);
  outgoing constraint `start >= minStart` и/или `end >= minEnd` (`FUN_27222919c`); агрегатор
  `FUN_2722290a0`.
- source evidence: `AUTOMIX_STATE_MACHINE.md` §2 (EXACT).
- Android implementation: sealed `Placement` с `IncomingPlacement`/`OutgoingPlacement` и
  `PlacementConstraint`; `PlacementValidator.validate(candidateRegion, criteria, context):
  PlacementResult`; `Criteria` — data class с genre-фильтрами.
- difficulty: medium.
- expected fidelity: 70% (кейсы восстановлены, payload-структуры — частично).
- performance risks: per-candidate проверки — дешёвые.
- fallback: без ограничений (только complexity gate).

### 8.1. Genre compatibility

- iOS behavior: `FUN_27223edec` (genre-tree), `FUN_27223f618` (ancestor/descendant),
  `FUN_272240e68` (allowed/denied IDs); логи "Genre compatibility: … significant/denied/allowed".
- source evidence: `AUTOMIX_CANDIDATE_GENERATION.md` §7; `MEDIAAPI_TO_TRANSITIONPLANNER.md` §3.
- Android implementation: `GenreTree`, `GenreCompatibility.check(outGenres, inGenres, allowed,
  denied)`.
- difficulty: medium (нужно дерево жанров; у Android — жанры VK/локальные, маппинг на Apple Genre IDs
  `[UNVERIFIED]`).
- expected fidelity: 50–60%.
- performance risks: нет.
- fallback: без genre-фильтра (пустые allowed/denied).

### 8.2. Time signature

- iOS behavior: `FUN_272243340`: Compatible при недоступности первой подписи; иначе relationship.
- source evidence: `AUTOMIX_CANDIDATE_GENERATION.md` §7 (`AUTOMIX_SCORING.md` §4).
- Android implementation: `TimeSignatureCompatibility.check(out, in)`; отсутствие подписи в
  MediaAPI-схеме — считать локально или `Compatible`.
- difficulty: low.
- expected fidelity: 80%.
- performance risks: нет.
- fallback: Compatible.

---

## 9. FailureReason и fallback

- iOS behavior: ровно 2 случая: `FailureReason.musicalCompatibility(MusicalCompatibility)` и
  `FailureReason.timingAccuracy(TimingAccuracy)` (0x27229c7fc/c800, EXACT).
- source evidence: `AUTOMIX_STATE_MACHINE.md` §5 (EXACT).
- Android implementation:
  `sealed interface TransitionFailureReason { MusicalCompatibility(reason); TimingAccuracy(issues) }`.
  `TimingAccuracy` — битовая маска `SongIssue`.
- difficulty: low.
- expected fidelity: 100%.
- performance risks: нет.
- fallback: при failure — plain crossfade длительностью из `TransitionCriteria.fallbackDurationMs`
  (значение Apple NOT FOUND; Android-дефолт из настроек).

---

## 10. Scheduling: continuous / stepped

- iOS behavior: `TransitionSchedulingPolicy = .continuous(ContinuousSchedule) |
  .stepped(Double)`; `.default` = raw bytes `{0x0, 0x1}` (случай — `[INFERRED]`);
  `DesignTimeTransitionScheduler`, `DesignTimeContinuousTransitionScheduler`,
  `DesignTimeSteppedTransitionScheduler`; калькуляторы `StructuredScheduleCalculator`,
  `UnstructuredScheduleCalculator`; `SteppedSchedule` шаг 0.2 s, valid range 0.0001..1.0;
  `PlaybackTime { songTime, stretchedSongTime, transitionTime }`, `SynchronizedPlaybackTime`.
- source evidence: `AUTOMIX_STATE_MACHINE.md` §1, `DSP_AUTOMATION.md` §4 (EXACT).
- Android implementation: sealed `SchedulingPolicy.Continuous / Stepped(stepSeconds = 0.2)`;
  интерфейс `TransitionScheduler.schedule(plan, context): TransitionSchedule`;
  `SteppedScheduleCompiler` генерирует сетку 0.2 s (конфигурируемо, кламп 0.0001..1.0);
  `TransitionTimeMap` хранит три шкалы; `TransitionClock` пересчитывает `songTime ↔
  stretchedSongTime ↔ transitionTime`.
- difficulty: medium.
- expected fidelity: 75% (шаг EXACT; алгоритмы калькуляторов — частично).
- performance risks: размер сетки для длинного перехода (300 с / 0.2 = 1500 точек × параметры) —
  компилировать в компактные массивы.
- fallback: `Stepped(0.2)`.

### 10.1. Runtime state machine (Android)

Состояния `TransitionSession`:
`IDLE → ARMED(plan ready, preload) → STARTING(fade-out begun) → CROSSFADING(overlap) →
COMPLETED → IDLE`; ошибки → `FAILED` → fallback. Переходы инициируются фактами Media3
(готовность следующего периода, позиция старта) — не таймером. Apple-фазовой машины с именами
`*Phase` не существует (RETRACTED в `AUTOMIX_STATE_MACHINE.md` §RETRACTED) — это наша
операционная модель, не реконструкция.

---

## 11. Маппинг Apple → Android (сводная таблица имён)

| Apple (binary) | Android (предлагаемое имя) |
|---|---|
| `TransitionPlanner` | `com.lmg.vk.automix.planner.TransitionPlanner` |
| `TransitionPlanner.transition(from:to:criterias:)` | `TransitionPlanner.plan(from, to, criteria)` |
| `TransitionPlanner.Song` / `Song.Analysis` | `UnifiedTrackAnalysis` / `TrackAnalysisSource` |
| `TransitionPlanner.Criteria` | `TransitionCriteria` |
| `Transition` | `TransitionPlan` |
| `Transition.Summary` | `TransitionSummary` |
| `TransitionPlanner.FailureReason` | `TransitionFailureReason` |
| `Transition.Algorithm` | `TransitionAlgorithm` (0..3) |
| `Transition.Complexity` | `TransitionComplexity` (0..3) |
| `TransitionPlanner.StylingScore` | `@JvmInline value class StylingScore(val value: Double)` |
| `...CandidateGenerationStrategyP` | `CandidateGenerationStrategy` |
| `...StylingStrategyP` | `StylingStrategy` |
| `...StylingStrategyCatalogP` / `DefaultStylingStrategyCatalog` | `StylingStrategyCatalog` / `DefaultStylingStrategyCatalog` |
| `BeatMatchedFilteredCrossFadeCandidateGenerationStrategy` | `BeatMatchedFilteredCrossFadeStrategy` |
| `DeadAirRemovalCandidateGenerationStrategy` | `DeadAirRemovalStrategy` |
| `FallbackCrossFadeCandidateGenerationStrategy` | `FallbackCrossFadeStrategy` |
| `SmartCrossFadeCandidateGenerationStrategy` | `SmartCrossFadeStrategy` |
| `TransitionPlanner.TransitionSchedulingPolicy` | `SchedulingPolicy.Continuous / Stepped` |
| `Transition.ContinuousSchedule` / `SteppedSchedule` | `ContinuousSchedule` / `SteppedSchedule` |
| `Transition.Summary.musicalCompatibility/timingAccuracy` | `MusicalCompatibility` / `TimingAccuracy` |
| `Transition.TimingAccuracy.SongIssues` | `SongIssue` (enum set) |
| `Transition.DSPGraph` / `graphIdentifier "smartTransitionsGraph"` | `DspGraphSpec(identifier, resourceName="DSPGraph")` |
| `Transition.AutomationEffectParameter` (29) | `DspParameter` enum + таблица (см. `ANDROID_DSP_ENGINE.md`) |
| `TransitionPlacementP` + `Criteria.Incoming/OutgoingPlacement` | `Placement` sealed + `PlacementValidator` |
| `DefaultDSPParameterProvider.smartTransitionParameterSchedule` | `DspParameterProvider` / `ParameterTimeline` |
| `Acousticness/Danceability/Melodicness` + maps | `Musicality` / `MusicalityMap` |
| `LoudnessMap`, `VocalActivityMap`, `BeatStabilityMap`, `TonalityMap`, `SongStructure` | одноимённые Kotlin-типы в `com.lmg.vk.analysis` |

---

## 12. Тест-план AutoMix

1. Tempo: пары BPM {125,129,130,250,62.5} × режимы normal/expanded → ожидаемые
   `TempoRelationship` (60/bpm; tol 0.16/0.287; 0xfc-случай).
2. Score: `base ∈ {10,15}` × factors с нулём/0.75 → проверка `product <= 0` и `+tie·0.001`.
3. Winner: равные score → первый; отрицательные отброшены.
4. Complexity gate: `max=1` отсекает `CROSS_FADE_WITH_EFFECTS`; reduced-set `FALLBACK_ONLY`.
5. Bar count: 7 → false, 8 → true.
6. Musicality polarity: acousticness 0.9 → significant (несовместимо), 0.5 → insignificant;
   danceability 0.2 → insignificant (несовместимо), 0.5 → significant; melodicness 0.1 → insignificant.
7. Placement: incoming start вне диапазона → reject; outgoing Late → min-only.
8. Golden: live JSON пары треков → фиксированный `TransitionPlan` или `FailureReason`.
9. Fallback: убрать поля анализа по одному → стратегия должна деградировать, не падать.

## NOT FOUND / OPEN

- Селекторы `0x8/0x9/0xc` в `FUN_272234380` — природа не восстановлена.
- `param_1` tie-breaker `×0.001` — значение на call-site.
- Точная формула `FUN_272235840` (trailing incoming loudness ratio).
- Числовые пороги `BeatStabilityMap`/`VocalActivityStrength` (теги 3..5).
- Реальная длительность Fallback Cross-Fade (параметр стиля).
- JSON-каталог `TransitionStyles` (ID → алгоритм/регион) — отсутствует в корпусе.
- Точная таблица «complexity → allowed algorithms» (побитовые наборы `FUN_272224c30`).
- Полные payload-структуры placement constraints.
- Значения `TempoBinaryScaleFactor` (raw).
- Raw-значения enum `TransitionSchedulingPolicy.default` (только байты `{0,1}`).
- Кто вызывает planner в iOS (callers пусты).
- Использование planner-ом `energy`/`valence`/FlexML-полей — NOT ESTABLISHED.
- Побитовый формат `bytes[0]` дат в Apple-подписях (не относится к AutoMix, но всплывает в анализе).
