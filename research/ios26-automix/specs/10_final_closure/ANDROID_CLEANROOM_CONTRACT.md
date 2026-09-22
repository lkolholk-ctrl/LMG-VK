# ANDROID_CLEANROOM_CONTRACT.md — clean-room контракт Android-порта: данные и пайплайн (Phase 12)

**Дата:** 2026-09-13. **Роль:** Final Closure (Phase 12): Android Cleanroom Contract Author.
**Ограничения:** research/spec only. `/root/LMG-VK` не изменялся, Kotlin/C++ не создавались, git не использовался.
Запись — только под `deepseek_analysis/10_final_closure/`. Секреты не приводятся.
**Назначение:** контракт уровня данных/пайплайна для будущего агента Android-интеграции. НЕ реализация,
НЕ код; интерфейсы описаны как JSON-schema-like структуры + таблицы полей.

---

## 0. Как читать этот документ

### 0.1. Классы значений (колонка `Class` — всегда последняя в нормативных таблицах)

| Class | Значение |
|---|---|
| `EXACT` | значение/тип/поведение прямо подтверждены raw-артефактом Apple (адрес/байты/ресурс/символ). |
| `STRONG` | вытекает из нескольких независимых подтверждённых артефактов (STRONG_INFERENCE). |
| `PARTIAL` | часть звена подтверждена, часть нет; нельзя выдавать за полное доказательство. |
| `NOT FOUND` | данных нет / проверенный негатив. В реализацию не переносится как «факт». |
| `PORT` | **PORT DESIGN DECISION** — решение Android-порта (класс `PORT` в LIQUID_GLASS_SPEC), Apple-эквивалент отсутствует или недоказан. |

`EXTERNAL` (публичные SDK-заголовки Apple) отмечается в колонке evidence словом `[EXTERNAL]`; в подсчёте
(§5) такие строки учитываются как `EXACT`, т.к. значение из публичного контракта, не из корпуса.

### 0.2. Источники (сокращения; пути от `deepseek_analysis/`)

| Код | Файл |
|---|---|
| PLC | `10_final_closure/TRANSITION_PLANNER_CLEANROOM_SPEC.md` |
| TS | `10_final_closure/TRANSITION_STYLES_IMPLEMENTATION_SPEC.md` |
| DSPR | `10_final_closure/DSP_RUNTIME_ARCHITECTURE.md` |
| LYR | `10_final_closure/LYRICS_RENDERER_IMPLEMENTATION_SPEC.md` |
| LGC | `10_final_closure/LIQUID_GLASS_IMPLEMENTATION_SPEC.md` |
| MC | `10_final_closure/MUSIC_APP_AUTOMIX_CALLCHAIN.md` |
| M2P | `10_final_closure/MEDIAAPI_TO_PLANNER_CALLCHAIN.md` |
| BLK | `10_final_closure/blocked_13_15.md` |
| REC | `10_final_closure/STATE_RECONCILIATION.md` |
| DGR | `09_appos/APPOS_DSPGRAPH.md` |
| TSJ | `09_appos/APPOS_TRANSITION_STYLES.md` |
| MAA | `09_appos/APPOS_MASTER_SUMMARY.md` |
| APL | `09_appos/APPOS_LYRICSX.md` |
| A3 | `06_android_port/ANDROID_ARCHITECTURE.md` |
| AA | `06_android_port/ANDROID_AUTOMIX.md` |
| ADSP | `06_android_port/ANDROID_DSP_ENGINE.md` |
| ALY | `06_android_port/ANDROID_LYRICS.md` |
| M3 | `06_android_port/MEDIA3_INTEGRATION.md` |
| ALG | `06_android_port/ANDROID_LIQUID_GLASS.md` |
| LJV | `decompiled_package` / `appos/extracted` (raw, где указан путь) |

### 0.3. Правила контракта

1. **Apple-значение без source/status не попадает в контракт.** Если доказательства нет — `NOT FOUND`.
2. `REQ` = поле обязательно для данного пайплайна (иначе пайплайн деградирует по §4.3);
   `OPT` = допустимо отсутствие; `REQ(soft)` = желательно, деградация допустима.
3. Единицы указываются всегда; при отсутствии доказанных единиц пишется `NOT FOUND` (значение не масштабируется «на глаз»).
4. Композиция интерфейсов — решение порта; каждое поле-композиция помечено `PORT`.
5. `TransitionStyle` и `DSPParameter` — **данные** (переносятся как значения из ресурсов Apple);
   `SongAnalysis` — данные + runtime-производные (`PORT`).

---

## 1. Интерфейсы данных (JSON-schema-like)

Обозначения в схемах: `?` — OPT (может отсутствовать). В таблицах полей: `REQ`/`OPT`,
единицы, Apple-источник, `Class`.

### 1.1. SongAnalysis

Apple-аналоги: `TransitionPlanner.Song {id, duration, analysis, context}`,
`Song.Analysis = .musicKit(MusicKitAnalysis) | .adaptiveMusic(...)`,
`Song.MusicKitAnalysis {genres, duration, audioAnalysis, flexAnalysis, spatialTimingInformation, Options}`
(PLC §1.2, §1.3; Ma 0x272261280 / 0x27225d7cc; M2P §2.1).

```jsonc
// SongAnalysis (planner-facing view; Android: UnifiedTrackAnalysis)
{
  "id": "string",                       // Song.ID.rawValue
  "durationSeconds": 0.0,               // Double? seconds
  "source": "musicKit|adaptiveMusic|local",
  "analysisVersion": "string",          // cache key (PORT)
  "genres": [ { "id": "string", "name": "string?" } ],
  "loudnessCurve": { "samplingFrequency": 0.0, "value": [0.0] },   // dB-like, ~2 Hz (live)
  "vocalActivities": [ { "startSeconds": 0.0, "endSeconds": 0.0,
                         "kind": "singing|speech|rapping", "strength": 0.0 } ],
  "acousticness": { "main": 0.0, "beginning": 0.0, "ending": 0.0 },
  "danceability": { "main": 0.0, "beginning": 0.0, "ending": 0.0 },
  "melodicness": { "main": 0.0, "beginning": 0.0, "ending": 0.0 },
  "tonality": { "main|beginning|ending": { "tonic": "string", "mode": "string" } },
  "flex": { "events": [ { "time": 0.0, "timeScale": "short|medium|long|extraLong", "amplitude": 0.0 } ] },
  "spatialTimingInformation": { /* opaque */ },
  "context": { "previousPlaybackEndState": "PlaybackState?" },
  "supportsSmartTransitions": false,
  "beatEvents": [0.0], "downbeatEvents": [0.0], "bars": [0.0],  // sources UNKNOWN
  "beatStability": [0.0]
}
```

| Поле | Тип / единицы | REQ | Apple-источник (evidence/status) | Class |
|---|---|---|---|---|
| `id` | String | REQ | PLC §1.2 `Song.ID.rawValue` (Ma 0x27226722c/init 0x272267244) | EXACT |
| `durationSeconds` | Double, s | REQ(soft) | PLC §1.2 `Song.duration`/`MusicKitAnalysis.duration`; источник `durationInMillis` (M2P §1) | STRONG |
| `source` | enum: `musicKit`/`adaptiveMusic` | REQ | PLC §1.2 (`Song.Analysis` enum; reflstr 0x27225c004) | EXACT |
| `source=local` | enum case | OPT | Android fallback (`LocalAnalysisBuilder`, A3 §3.1); Apple-кейса нет | PORT |
| `analysisVersion` | String | REQ | ключ кэша плана/анализа (A3 §3.2); Apple-эквивалента нет | PORT |
| `genres[].id` | String | REQ(soft) | PLC §1.2; `Genre.ID.rawValue: String` (PLC §4.8) | EXACT |
| `genres[].name` | String? | OPT | таблицы ID→имя нет в бинарях/DSC (MAA §11 #11 BLOCKED; PLC §4.8) | NOT FOUND |
| `loudnessCurve.samplingFrequency` | Double, Hz | REQ(soft) | M2P §1 island `0x2743dd1c0` @0x272223d58 (`LoudnessCurve.samplingFrequency`, P2 §4.1); live ≈2 Hz | EXACT |
| `loudnessCurve.value[]` | Double[], dB-like (отриц.) | REQ(soft) | M2P §1; PLC §8.1 (`L < 0` — условие); cloud→internal конвертер не локализован | STRONG |
| `vocalActivities[].start/end` | Double, s | REQ(soft) | M2P §1: JSON ms → internal s, `FUN_1d41253ac` (`/1000.0`) | EXACT |
| `vocalActivities[].kind` | enum 0/1/2 → singing/speech/rapping | REQ(soft) | PLC §7.1; M2P §1 (switch 0/1/2) | EXACT |
| `vocalActivities[].strength` | Double (enum-tags не восстановлены) | REQ(soft) | PLC §7.2; порог «significant» — PARTIAL | PARTIAL |
| `acousticness.*` | Double 0..1 | OPT | PLC §4.7 (0.85/1.0 EXACT); остров→поле STRONG (M2P §2.2/§2.4) | STRONG |
| `danceability.*` | Double 0..1 | OPT | PLC §4.7 (0.3/1.0 EXACT); остров→поле STRONG | STRONG |
| `melodicness.*` | Double 0..1 | OPT | PLC §4.7 (0.25/1.0 EXACT); остров→поле STRONG | STRONG |
| `tonality.*.{tonic,mode}` | String | OPT | PLC §6; M2P §1 (импорт EXACT, остров→sub-поле не переразрешён) | PARTIAL |
| `flex.events[].{time,timeScale,amplitude}` | s / enum / 0..1 | OPT | M2P §1 (`score→TimeScale` 200…899, `amplitude=(score%100)/100.0` EXACT); scoring-предикат не найден | PARTIAL |
| `flex.entryPoints/exitPoints` | — | OPT | M2P §3: потребитель — engine (`SmartTransitionSongData.transitionPivotPoint`), planner — нет | NOT FOUND |
| `flex.visualTempo/arousal/valence` | — | OPT | M2P §1/§3: не в planner-импорте, потребителя нет | NOT FOUND |
| `spatialTimingInformation` | opaque | OPT | PLC §1.2/M2P §2.3 (offset +0x20 EXACT, `FUN_272220920`); роль STRONG | STRONG |
| `context.previousPlaybackEndState` | `Transition.PlaybackState?` | OPT | PLC §1.2 (getter 0x2722666d0/init 0x2722666e4); атрибуция типа STRONG | STRONG |
| `supportsSmartTransitions` | Bool | REQ | MC §1: gate уровнем выше planner (SonicKit/MPC) | STRONG |
| `beatEvents/downbeatEvents/bars` | Double[], s | REQ(soft) | PLC §1.4/§5.4: getters `0x1d4396964/67e0` не вызываются; источник полей UNKNOWN | NOT FOUND |
| `beatStability` | Double[]/map | REQ(soft) | PLC §4.6; допуск 0.031 s — MAA §12.1; связь с `percentDeviation` опровергнута (M2P §5.4) | PARTIAL |
| `bpm.{main,beginning,ending}` | Int/Double | OPT | PLC §1.4: consumer-negative EXACT; темновые предикаты есть, вход не прослежен | NOT FOUND |
| `bpm.percentDeviation` | Double? | OPT | BLK §1: consumer-negative EXACT, конвертер не масштабирует, семантика UNVERIFIED | NOT FOUND |
| `loudness.{value,range,peak}` | Double | OPT | BLK §2: `Statistics.value/range/peak` не вызываются; шкала UNVERIFIED | NOT FOUND |
| `fades` / `phrases` | — | OPT | M2P §1: getters не в 41-импорте, потребителя нет | NOT FOUND |
| `energy` / `valence` | Double | OPT | M2P §1: 0 потребителей, 0 строк в packages | NOT FOUND |

**Запрещено:** использовать `bpm.*`, `fades`, `phrases`, `energy`, `valence`, `loudness.value/range/peak`,
`percentDeviation` в предикатах/скоринге, пока не доказан потребитель (M2P §0.3).

### 1.2. TransitionCandidate

Apple-аналог: запись кандидата `0x100 B` (score @`+0xF8`, Double) / fallback `0x120 B` (score @`+0x118`)
(PLC §2.4; SC §3; P1 §4). Композиция — `PORT`.

```jsonc
// TransitionCandidate
{
  "styleId": 8,                    // Apple: TransitionStyle.id (dispatch 8/9/12)
  "algorithm": 0,                  // 0 beatMatchedFilteredCrossFade .. 3 fallbackCrossFade
  "complexity": 3,                 // 0 fallback .. 3 timeStretchedCrossFadeWithEffects
  "regionPair": {
    "outgoing": { /* 0x51 B */ },  // scaled/doubled/halved/stable/truncated/shifted variants
    "incoming": { /* 0x51 B */ },
    "tag": 0
  },
  "factors": [1.0],                // tempo / vocal / barCount / loudness / tonality / relationship
  "base": 10.0,                    // 10.0 | 15.0 | 2.0 | 1.0 | 3.0
  "score": 0.0,                    // base·Πfactors + tieBreaker·0.001 (if product > 0)
  "tieBreaker": 0.0,               // param_1 semantics UNKNOWN
  "recordBytes": 256               // PORT-level detail
}
```

| Поле | Тип / единицы | REQ | Apple-источник (evidence/status) | Class |
|---|---|---|---|---|
| `styleId` | Int (8/9/12) | REQ | PLC §2.3/§10.3 (FUN_272234380 `cmp #0x8/#0x9/#0xc`) | EXACT |
| `algorithm` | 0..3 | REQ | PLC §9.2 (`Algorithm.description` @0x27228b36c) | EXACT |
| `complexity` | 0..3 | REQ | PLC §9.1 (`Transition.Complexity`, ровно 4 кейса) | EXACT |
| `algorithm→complexity` | mapping | REQ | PLC §9.3 (Fallback 0 / DeadAir 1 / Smart 2 / BeatMatched 3) | EXACT |
| `regionPair.outgoing` | 0x51 B | REQ | PLC §12.2 (FUN_272234b0c: outgoing @+0x00) | EXACT |
| `regionPair.incoming` | 0x51 B | REQ | PLC §12.2 (incoming @+0x58, tag @+0xa9) | EXACT |
| region-преобразования | scaled/doubled/halved/stable/truncated/shifted | REQ | PLC §2.3 (функции/вызовы EXACT; семантика шагов — STRONG по логам) | STRONG |
| `factors.tempo` | 1.0/0.0 | REQ | PLC §5.1/§5.2 (FUN_272236498; tol 0.16/0.287) | EXACT |
| `factors.leadingVocal` | 0.75/1.0 | REQ | PLC §7.2 (fcsel @0x272234538) | EXACT |
| `factors.barCountRatio` | Double | REQ | PLC §8.2 (FUN_27223550c); внутренняя арифметика не полностью трассирована | PARTIAL |
| `factors.minBarCount` | Bool (count>7) | REQ | PLC §4.6 (FUN_272235fd8) | EXACT |
| `factors.trailingLoudness` | Double (ratio) | REQ | PLC §8.1 (`L/T`, без clamp, `fdiv @0x272235bbc`) | EXACT |
| `factors.tonality` | 1.0/0.0 | REQ | PLC §6 (tag 3 EXACT; формула tonality relationship UNKNOWN) | PARTIAL |
| `factors.vocalRelationship` | 1.0/0.0 | REQ | PLC §7.3 (фактор STRONG; условие совместимости PARTIAL) | PARTIAL |
| `factors.expandedApplicable` | 1.0/0.0 | REQ | PLC §10.3 (ветка 0xc, 2× FUN_272236498 w3=0/1) | PARTIAL |
| `base` | 10.0/15.0/2.0/1.0/3.0 | REQ | PLC §10.2 (адреса fmov) | EXACT |
| `score` | Double | REQ | PLC §10.1 (`base·Πfactors`, отбраковка `<=0`, `+param_1·0.001` @0x27222d680) | EXACT |
| `score` storage | @+0xF8 (0x100B record) | REQ | PLC §2.4 (FUN_27222e0ec) | EXACT |
| `tieBreaker` (=`param_1`) | Double; единицы/имя поля UNKNOWN | REQ | PLC §12.1–12.2 (арифметика `fsub` @0x27222d628 EXACT; имя поля UNKNOWN) | PARTIAL |
| `tieBreaker` default в порте | 0.0 + фича-флаг | REQ | AA §5.1: значение на call-site не найдено; 0.0 — безопасный дефолт | PORT |
| winner-семантика | строгое `>`; ничьи — ранее добавленный | REQ | PLC §2.5 (FUN_27222e800) | EXACT |
| `recordBytes` | 0x100 / 0x120 fallback | OPT | PLC §2.4; §13.1 | EXACT |

### 1.3. TransitionPlan

Apple-аналоги: `Transition` (0x80 B, PLC §14.1), `Transition.Summary` (0x50 B, PLC §14.2),
`Transition.Schedule` (`ContinuousSchedule`/`SteppedSchedule`, ADSP §4.2),
`SmartTransitionData` (MC §1 hop 9). Композиция верхнего уровня — `PORT`.

```jsonc
// TransitionPlan
{
  "strategy": { /* 16 B tag+payload */ },
  "algorithm": 0,
  "complexity": 3,
  "styleId": 8,
  "summary": {
    "strategy": { },                       // 2 qword
    "outgoingSongTimeRange": { "lower": 0.0, "upper": 0.0 },   // Range<SongTime>, s
    "incomingSongTimeRange": { "lower": 0.0, "upper": 0.0 },
    "musicalCompatibility": { "outgoingSongIssues": 0, "incomingSongIssues": 0, "songIssues": 0 },
    "timingAccuracy": { "rawValue": 0 }    // OptionSet: 1 stereo, 2 spatial
  },
  "schedule": {
    "policy": "continuous|stepped",
    "stepSeconds": 0.2,                    // stepped only; valid 0.0001..1.0
    "automations": [ /* AutomationRamp + parameter */ ],
    "timeStretching": { "outgoingSongSteps": [], "incomingSongSteps": [] },
    "playbackAlignment": { }
  },
  "dspGraph": { "identifier": "smartTransitionsGraph", "resourceName": "DSPGraph",
                "graphName": "SmartTransitions" },
  "audioMixes": { "outgoing": "AudioMix", "incoming": "AudioMix" },   // out_gain ramps
  "speedRampMappings": [ /* CMTimeMapping */ ],
  "noFollowUp": false,
  "failureReason": "musicalCompatibility|timingAccuracy"
}
```

| Поле | Тип / единицы | REQ | Apple-источник (evidence/status) | Class |
|---|---|---|---|---|
| `strategy` | 16 B (tag+payload) | REQ | PLC §14.1; полевой layout полный не перепроверен | PARTIAL |
| `algorithm` | 0..3 | REQ | PLC §9.2 | EXACT |
| `complexity` | 0..3 | REQ | PLC §9.1 | EXACT |
| `styleId` | Int | REQ | PLC §11.3 | EXACT |
| `summary.strategy` | 2 qword | REQ | PLC §14.2 (запись @+0x00/+0x08; getters 0x27228c3c8…) | STRONG |
| `summary.outgoingSongTimeRange` | `Range<SongTime>`, s | REQ | PLC §14.2 (@+0x10/+0x18; манглинг `SNyAA0hI0VG`) | EXACT |
| `summary.incomingSongTimeRange` | `Range<SongTime>`, s | REQ | PLC §14.2 (@+0x20/+0x28) | EXACT |
| `summary.musicalCompatibility` | 2 qword + SongIssues | REQ | PLC §14.3 (getters EXACT); биты SongIssues UNKNOWN | PARTIAL |
| `summary.timingAccuracy` | OptionSet Int: 1/2 | REQ | PLC §14.4 (`stereoTimeInaccurate`/`spatialTimeInaccurate`) | EXACT |
| `schedule.policy` | `continuous`/`stepped` | REQ | PLC §1.2; ADSP §10 | EXACT |
| `schedule.stepSeconds` | Double, s (0.2; 0.0001..1.0) | OPT | ADSP §4.2 (`0x3fc999999999999a` @0x2722a1010) | EXACT |
| `schedule.automations` | `Automation {parameter, points, ramps}` | REQ | ADSP §4.2/§4.3 (типы EXACT) | EXACT |
| `schedule.timeStretching.songSteps` | `[TimeStretchingStep]` per side | REQ(soft) | DSPR §B.3 (getters 0x272281544/0x2722817bc) | EXACT |
| `schedule.playbackAlignment` | `SynchronizedPlaybackTimeRange` | OPT | DSPR §B.5 (тип найден; формула — PARTIAL) | PARTIAL |
| `dspGraph.identifier` | String `smartTransitionsGraph` | REQ | DSPR §D.1 (data 0x2721de340, len 0x15) | EXACT |
| `dspGraph.resourceName` | `DSPGraph` (`.dspg`) | REQ | DGR §0 (loader `FUN_27226b7cc`, name="DSPGraph") | EXACT |
| `dspGraph.graphName` | `SmartTransitions` | REQ | DGR §1 (graphName) | EXACT |
| `audioMixes.outgoing/incoming` | 2 × `AVAudioMix` | REQ | DSPR §D.1 (SmartTransitionData +0x80/+0x88) | EXACT |
| `speedRampMappings` | `[CMTimeMapping]?` | REQ(soft) | DSPR §B.3 (getter 0x2721afa80); automations→steps функция NOT FOUND | PARTIAL |
| `noFollowUp` | Bool | OPT | PLC §14.2 (`*(result+0x1d8)=0`, FUN_272269220) | STRONG |
| `failureReason` | 2 кейса | REQ | PLC §14.5 (0x27229c7fc/c800) | EXACT |
| `transitionStartTime/EndTime/Duration` | CMTime / s (1e9) | REQ | MC §1 hop 9 (0x2721afa1c/afa2c/afa18) | EXACT |
| Timing/normalized→absolute mapping | s | REQ | DSPR §C (CMTime 1e9 EXACT); `entryOffsetUs` runtime — PARTIAL (MAA #49) | PARTIAL |

### 1.4. TransitionStyle (каталог)

Apple-аналог: `TransitionStyles.json` (14 объектов; sha256 `fe3d0a36…d120`, 55905 B; TSJ §0;
TS §0–1). Схема — raw-ключи **EXACT**, привязка ключ↔Swift-поле — STRONG (TS §1.1).

```jsonc
// TransitionStyle (resource item; 14 шт.; IDs 0,1,2,3,4,6,7,8,9,10,11,12,33,44)
{
  "id": 8, "name": "BM - Filter high to low",
  "offset": { "relative": 0.0, "offsetInSeconds": -0.3 },   // optional
  "duration": 8,                                             // optional; только 8/9/12
  "instructions": {
    "outgoing": [ { "name": "TimeStretching|AULowpass|AUHipass|AUDelay|AUReverb2|GAIN",
                    "placement": { "start": {"relative":0.0,"offsetInSeconds":0.0},
                                   "end":   {"relative":1.0} },
                    "automations": [ /* AutomationRamp */ ] } ],
    "incoming": [ ]
  }
}
```

| Поле | Тип / единицы | REQ | Apple-источник (evidence/status) | Class |
|---|---|---|---|---|
| `id` | Int | REQ | TSJ §1/§2; TS §2 | EXACT |
| `name` | String | REQ | TSJ §2 | EXACT |
| `offset.relative` | Double 0..1 | OPT | TSJ §1 (raw); bind↔`startTime` — STRONG | EXACT |
| `offset.offsetInSeconds` | Double, s | OPT | TSJ §1 (только style 0 `0`, style 2 `-0.3`) | EXACT |
| `duration` | Int (`8/8/16`) | OPT | TSJ §1/§2; семантика `maximumBarCount` — STRONG, единицы (бары vs s) NOT FOUND (TS §5.2) | STRONG |
| `instructions.outgoing/incoming` | Array | REQ | TSJ §1; TS §3 | EXACT |
| `instruction.name` | enum (6 значений) | REQ | TSJ §1 | EXACT |
| `instruction.placement.{start,end}` | `{relative, offsetInSeconds?}` | REQ | TS §1 (окно применения) | EXACT |
| `automation.parameterId` | String (= styleParameterID) | REQ | TSJ §1.1 (`parameterId`) | EXACT |
| `automation.startTime/endTime.default` | `{relative, offsetInSeconds?}` | REQ | TSJ §1 | EXACT |
| `automation.startValue/endValue.default` | Double | REQ | TS §3 (все значения) | EXACT |
| `automation.startValue/endValue.parameterName` | `"beat_length"` (2 вхождения, style 10) | OPT | TSJ §1 (резолвер NOT FOUND, TSJ §7.2) | PARTIAL |
| `automation.interpolation` | enum строк (6) | REQ | TSJ §4; формулы кривых — EXACT (ADSP §4.1) | EXACT |
| style ID→ветка планировщика | 8/9/12 | REQ | TS §2.1 (REACHABLE EXACT) | EXACT |
| reachability 6/7/10/11 | legacy/unreachable | OPT | TS §2.1 (zero-fill кандидата) | STRONG |
| reachability 0–4/33/44 | NO CONSUMER FOUND | OPT | TS §2.1 (corpus-negative) | NOT FOUND |
| loader каталога | `FUN_27224e480`, name/ext/json | REQ | TS §0; PLC §11.2 | EXACT |
| missing-resource error | `SmartTransitionsError` tag 0xd | OPT | TS §0; PLC §11.2 | STRONG |

### 1.5. AutomationRamp

Apple: `AutomationRamp {startValue@0, endValue@8, t0@0x10, t1@0x18, curveByte@0x20}`
(ADSP §4, DSP_CONSTANTS §4); `ContinuousSchedule.AutomationRamp {startValue, endValue, songTimeRange, curve}`
(ADSP §4.3). Evaluator — EXACT (ADSP §4).

```jsonc
// AutomationRamp
{
  "parameter": "lp_cutoff_freq",          // DSPParameter.styleParameterID
  "startValue": 20000.0, "endValue": 200.0,
  "t0": { "relative": 0.0, "offsetInSeconds": 0.0 },   // или songTime (s)
  "t1": { "relative": 1.0 },
  "curveByte": 65,                        // 0x41 ease-out-2
  "units": "Hz"                           // из DSPParameter/units таблицы
}
```

| Поле | Тип / единицы | REQ | Apple-источник (evidence/status) | Class |
|---|---|---|---|---|
| `parameter` | String | REQ | TS §1/§8; DGR §6 (14 используемых в каталоге) | EXACT |
| `startValue`/`endValue` | Double (единицы параметра) | REQ | TS §3; ADSP §4.1 | EXACT |
| `t0`/`t1` | normalized 0..1 (+offset s) или songTime | REQ | TSJ §1; ADSP §4.2 | EXACT |
| `curveByte` | UInt8 | REQ | ADSP §4.1 (таблица 0x00…0xff) | EXACT |
| evaluator: `clamp p=(t−t0)/(t1−t0)` | formula | REQ | ADSP §4 (`t1<=t→end`, `t0>=t→start`) | EXACT |
| evaluator: `value=start+y(p)·(end−start)` | formula | REQ | ADSP §4; TS §7 | EXACT |
| кривые linear/0.5/2 | `p`, `1−√(1−p)`, `√p`, `1−(1−p)²` | REQ | ADSP §4.1 (0x80/0x00/0x40/0x41/0x01) | EXACT |
| ease-in-4 / ease-out-4 | `p⁴` / `1−(1−p)⁴`; диапазоны байт 0x02–0x3f / 0x42–0x7f | REQ | ADSP §4.1 (формулы EXACT; точный байт внутри диапазона — STRONG) | STRONG |
| logarithmic (0x81) | `f=log2`, `g=exp2` (геометрическая интерполяция) | OPT | MAA §11 #22 (P2 CLOSED); в каталоге 0 вхождений (TSJ §4) | EXACT |
| замена строки кривой на байт | enum→byte | REQ | TS §7 (классы EXACT; точный байт для 4-х кривых STRONG) | STRONG |

### 1.6. DSPParameter

Apple: `Transition.AutomationEffectParameter` — 29 записей ×0x38 B:
`{id: String, valueRange: Range<Double>, defaultValue: Double, styleParameterID: String}`
(DGR §6; ADSP §2). Карта AU-индексов — `[EXTERNAL]` публичные заголовки (DGR §5.1).

Полная таблица значений (единицы для AU — по публичным заголовкам; gains — линейные 0..1):

| # | swift case | id | styleParameterID | range | default | target box[index] | Class |
|---|---|---|---|---|---|---|---|
| 1 | inputMixerVolume | Ga1g | player_gain | 0.0…1.0 | 1.0 | Gain1[0] | EXACT |
| 2 | multibandFilterCenterBandwidth | Fbw1 | aufilter_center_bandwidth | 0.05…3.0 | 2.0 | AUFilter[5] | EXACT |
| 3 | multibandFilterCenterGain | Fcg1 | aufilter_center_gain | −18.0…18.0 | 0.0 | AUFilter[4] | EXACT |
| 4 | multibandFilterCenterFrequency | Fcf1 | aufilter_center_freq | 10.0…21829.5 | 2500.0 | AUFilter[3] | EXACT |
| 5 | highPassFilterCutoffFrequency | HP1f | hp_cutoff_freq | 10.0…22050.0 | 10.0 | AUHipass1[0] | EXACT |
| 6 | highPassFilterResonance | HP1r | hp_reso | −20.0…40.01 | 0.0 | AUHipass1[1] | EXACT |
| 7 | lowPassFilterCutoffFrequency | LP1f | lp_cutoff_freq | 10.0…21829.5 | 22000.0 | AULowpass1[0] | EXACT |
| 8 | lowPassFilterResonance | LP1r | lp_reso | −20.0…40.01 | 0.0 | AULowpass1[1] | EXACT |
| 9 | auxEffectsBusSendMixerVolume | Ga2g | send_mixer_gain | 0.0…1.0 | 1.0 | Gain2[0] | EXACT |
| 10 | delayDelayTime | DLdt | fx_delay_delay_time | 0.0001…2.01 | 1.0 | AUDelay[1] | EXACT |
| 11 | delayLowPassFilterCutoffFrequency | DLlf | fx_delay_lp_cutoff_frequency | 10.0…22050.0 | 2500.0 | AUDelay[3] | EXACT |
| 12 | delayDryWetBalance | DLdw | fx_delay_dry_wet | 0.0…100.0 | 0.0 | AUDelay[0] | EXACT |
| 13 | delayFeedback | DLfb | fx_delay_feedback | −99.9…99.9 | 50.0 | AUDelay[2] | EXACT |
| 14 | reverbGain | RVga | fx_reverb_gain | −20.0…20.01 | 1.0 | AUReverb[1] | EXACT |
| 15 | reverbDryWetBalance | RVdw | fx_reverb_dry_wet | 0.0…100.0 | 0.0 | AUReverb[0] | EXACT |
| 16 | reverbMinimumDelayTime | RVmi | fx_reverb_min_delay_time | 0.0001…1.0 | 0.008 | AUReverb[2] | EXACT |
| 17 | reverbMaximumDelayTime | RVma | fx_reverb_max_delay_time | 0.0001…1.0 | 0.05 | AUReverb[3] | EXACT |
| 18 | reverbLowFrequencyDecayTime | RVlf | fx_reverb_low_frequency_decay_time | 0.001…20.0 | 1.0 | AUReverb[4] | EXACT |
| 19 | reverbHighFrequencyDecayTime | RVhf | fx_reverb_high_frequency_decay_time | 0.001…20.0 | 0.5 | AUReverb[5] | EXACT |
| 20 | reverbReflectionsRandomization | RVrr | fx_reverb_randomize_reflections | 1.0…1000.0 | 1.0 | AUReverb[6] | EXACT |
| 21 | auxEffectsBusHighPassFilterCutoffFrequency | HP2f | fx_hp_cutoff_freq | 10.0…22050.0 | 10.0 | AUHipass2[0] | EXACT |
| 22 | auxEffectsBusHighPassFilterResonance | HP2r | fx_hp_reso | −20.0…40.0 | 0.0 | AUHipass2[1] | EXACT |
| 23 | auxEffectsBusLowPassFilterCutoffFrequency | LP2f | fx_lp_cutoff_freq | 10.0…21829.5 | 22000.0 | AULowpass2[0] | EXACT |
| 24 | auxEffectsBusLowPassFilterResonance | LP2r | fx_lp_reso | −20.0…40.0 | 0.0 | AULowpass2[1] | EXACT |
| 25 | auxEffectsBusReturnMixerDryVolume | Ga3g | fx_mixer_dry | 0.0…1.0 | 1.0 | Gain3[0] | EXACT |
| 26 | auxEffectsBusReturnMixerWetVolume | Ga4g | fx_mixer_wet | 0.0…1.0 | 0.0 | Gain4[0] | EXACT |
| 27 | timeStretchingRate | ts_rate | ts_rate | 0.03125…32.0 | 1.0 | НЕТ в графе (AVFoundation) | EXACT |
| 28 | outputMixerVolume | out_gain | out_gain | 0.0…1.0 | 0.0 | НЕТ в графе (AVFoundation) | EXACT |
| 29 | effectBypassingState | bypa | bypa | 0.0…1.0 | 1.0 | BypassProperty[0] | EXACT |

| Доп. поле | Значение | REQ | Apple-источник (evidence/status) | Class |
|---|---|---|---|---|
| `units` AU-параметров (Hz/dB/s/percent) | по публичным заголовкам (DGR §5.1) | REQ | DGR §5.1 `[EXTERNAL]`; совпадение valueRange с AU — STRONG | EXACT |
| `units` gains (linear 0..1) | linear | REQ | DSPR §C.2 (`AVMutableAudioMixInputParameters.volume`); ADSP §2 | EXACT |
| `catalogUses` | 14 из 29 используются | OPT | DGR §6; TSJ §2 | EXACT |
| id→UInt32 (fourCC) | `[UInt32:Float]` ключи расписания | REQ | DSPR §A.4/A.5; APPOS_DSPGRAPH §6 (имена = fourCC, EXACT; big-endian — STRONG) | STRONG |
| `styleParameterID` = human contract | для JSON/логов | OPT | ADSP §6 (`ts_rate` — 7 символов, не fourCC) | EXACT |
| `out_gain` default 0.0 | «граф стартует закрытым» | REQ | DGR §6; менять только автоматикой (21 рампа) | EXACT |

### 1.7. DSPGraphInstance

Apple: `DSPGraph.dspg` (`graphName SmartTransitions`), инстанцируется **per track** как
`AVAudioMixProcessingEffect` (DSPR §D.1; DGR §1–§3). Реализация узлов на Android — `PORT`.

```jsonc
// DSPGraphInstance (per track)
{
  "identifier": "smartTransitionsGraph",
  "graphName": "SmartTransitions",
  "format": { "sampleRate": 0, "numIns": 1 },
  "nodes": ["Gain1","Gain2","Gain3","Gain4","AUFilter","AUHipass1","AUHipass2",
            "AULowpass1","AULowpass2","AUDelay","AUReverb","Mixer","BypassProperty"],
  "edges": [ ["Input","Gain1"], ["Gain1","AUFilter"], ["AUFilter","AUHipass1"],
             ["AUHipass1","AULowpass1"], ["AULowpass1","Gain2"], ["AULowpass1","Gain3"],
             ["Gain2","AUDelay"], ["AUDelay","AUReverb"], ["AUReverb","AUHipass2"],
             ["AUHipass2","AULowpass2"], ["AULowpass2","Gain4"],
             ["Gain3","Mixer.0"], ["Gain4","Mixer.1"], ["Mixer","Output"] ],
  "parameters": { /* 27 графических DSPParameter */ },
  "bypass": { "property": 21, "scope": "global", "default": 1.0, "affects": "7 AU (не Gain)" },
  "instancesPerSide": 1            // 2 на переход (out/in)
}
```

| Поле/факт | Значение | REQ | Apple-источник (evidence/status) | Class |
|---|---|---|---|---|
| `graphName` | `SmartTransitions` | REQ | DGR §1 | EXACT |
| `identifier` | `smartTransitionsGraph` | REQ | DSPR §D.1 | EXACT |
| `format` | `audioFormat([sampleRate][numIns])` | REQ | DGR §1/§2 (sampleRate/numIns — runtime, в ресурсе не заданы) | EXACT |
| nodes | 12 боксов (11 узлов + property_cast) | REQ | DGR §1/§2 | EXACT |
| edges | 14 wires (см. схему DGR §3) | REQ | DGR §1/§3 | EXACT |
| topology | Gain1→Filter→HP1→LP1→{dry Gain3, send Gain2→Delay→Reverb→HP2→LP2→wet Gain4}→Mixer | REQ | DGR §3 (dry/wet два входа Mixer) | EXACT |
| `parameters` | 27 из 29 (нет `ts_rate`,`out_gain`) | REQ | DGR §6 | EXACT |
| bypass mechanism | `property_cast` op8 → property 21 (`kAudioUnitProperty_BypassEffect`) | REQ | DSPR §A.2 (op8 = copy, без инверсии) | EXACT |
| `bypass.default` | 1.0 (=bypassed) | REQ | DSPR §A.4 (`0x3ff0000000000000` @0x27226ed30) | EXACT |
| `bypass.polarity` | 1 = эффект обойдён, 0 = активен | REQ | DSPR §A.5 (обе реализации AUBox/AUEffectBase) | EXACT |
| runtime-запись `bypa=0` | callsite не найден | REQ | DSPR §A.4 `[NOT FOUND]`; каталог `bypa` не автоматизирует (DGR §6) | NOT FOUND |
| per-track экземпляры | 2 аллокации, 2 schedule | REQ | DSPR §D.1 (FUN_2721b0970 → 2×FUN_2721b2a04) | EXACT |
| aux state | не общий (per instance) | REQ | DSPR §D.2/§D.3 (RETRACTED «shared aux») | EXACT |
| lifecycle reset/destroy | явный reset графа не трассирован | OPT | DSPR §D.2 | PARTIAL |
| дедупликация по identifier в AVFoundation | не проверяема | OPT | DSPR `NOT FOUND` §6 | NOT FOUND |
| Android node designs (biquad/Q=10^(dB/20), reverb) | расчётные формулы | REQ | ADSP §3.1 (`INFERRED`, не Apple) | PORT |
| Android `DspParamId` 0..28 | собственные ID | REQ | ADSP §6 (fourCC не изобретать) | PORT |

### 1.8. LyricsModel

Apple: типы `LyricsX.Lyrics (+TextLine, Word, Syllable, InstrumentalLine)` из `MSVLyricsSongInfo`;
TTML-парсер внешний (`MSVLyricsTTMLParser`, MediaPlayer) — LYR §1; APL. В корпусе TTML-парсер
отсутствует (MAA #24/§13.13). Модель Android — `AppleTtmlParser` (ALY §2); структура — EXACT по samples.

```jsonc
// LyricsModel
{
  "durationMs": 182000, "lang": "en", "timingType": "WORD|SYLLABLE|LINE|NONE",
  "agents": [ { "id": "v1", "type": "person|group", "name": "string?" } ],
  "songwriters": ["string"],
  "sections": [ { "beginMs": 0, "endMs": 0, "songPart": "Intro|Verse|Chorus|...",
                  "agentId": "v1", "lines": [ Line ] } ]
}
// Line = { key:"L1", beginMs, endMs, agentId,
//          main:[Piece], background:[BackgroundGroup(outer:Piece, children:[Piece])],
//          translations:[], pronunciations:[] }
// Piece = { text, beginMs, endMs, role, isWhitespace, children:[Piece] }
// Word/Syllable = { startTime, endTime, progress }   // karaoke, from LyricsX
```

| Поле/факт | Тип / единицы | REQ | Apple-источник (evidence/status) | Class |
|---|---|---|---|---|
| Типы `Lyrics/TextLine/Word/Syllable/InstrumentalLine` | — | REQ | LYR §1 (символы MEE/Music.app); APL | EXACT |
| `durationMs` | Int, ms (`body@dur`) | REQ | ALY §2; sample TTML (182000 vs 170380) | EXACT |
| `lang` | BCP-47 (`xml:lang`) | OPT | ALY §2 | EXACT |
| `timingType` | WORD/SYLLABLE/LINE/NONE | REQ | ALY §1.1 (классификатор по метрикам; `itunes:timing` не различает) | PORT |
| `agents` | person/group, атрибуция body/div/p | OPT | MAA #28 (P3 CLOSED-структура); `ttm:agent` | STRONG |
| `sections`.songPart | Intro/Verse/Pre-Chorus/Chorus/Bridge/Outro | OPT | ALY §1 (`itunes:songPart`) | EXACT |
| `line.key` | String (`itunes:key` L1..Ln) | REQ | ALY §1/§2 | EXACT |
| `line.beginMs/endMs` | Int, ms; из `p`, не пересчитывать | REQ | ALY §2 (инвариант) | EXACT |
| `piece.text/beginMs/endMs` | String/Int ms | REQ | ALY §1 (оба формата времени; `SS.mmm`, `M:SS.mmm`, `H:MM:SS.mmm`) | EXACT |
| `piece.role`, `isWhitespace`, `children` | enum/Bool/Array | REQ | ALY §2; пробелы — отдельные piece | EXACT |
| `background` (x-bg) | outer `role=BACKGROUND`, inner — children | OPT | ALY §5 (`ttm:role="x-bg"`, 10 outer в CHAI) | EXACT |
| `translations/pronunciations` | Array | OPT | MAA #27 (head-translations; x-translation/x-roman не найдены) | PARTIAL |
| `Word/Syllable.startTime/endTime` | s | OPT | LYR §2.4 (`Line.currentSyllable` 0x100440de0; контракт) | STRONG |
| karaoke progress | `(t−begin)/(end−begin)` clamp | REQ | LYR §2.4; ALY §3 | EXACT |
| `timingType` серверный выбор варианта | — | OPT | MAA #33: вне бинаря | NOT FOUND |
| `InstrumentalLine` (маркер) | тип | OPT | LYR §2.5 (класс + AX-ключ `instrumental.break`) | EXACT |
| порог показа брейка | ms | OPT | LYR §2.5/§5: 7000 ms в iOS не найден; интерлюд приходит из модели | NOT FOUND |
| TTML-парсер Apple | — | REQ | MAA §13.13 (внешний MSV, в DSC; в корпусе нет); Android — свой `AppleTtmlParser` | NOT FOUND |
| granularity classifier | правила | REQ | ALY §1.1 (median span / end==next.begin) | PORT |

### 1.9. RendererConfig

Apple: `LyricsX.SyncedLyricsViewController.Specs` (63 поля, struct 0x2B0) + аниматоры;
значения — LYR §2.1–2.6. Android-композиция (tier-флаги) — `PORT`. Общие поля UI/glass — в конце.

```jsonc
// RendererConfig (lyrics renderer)
{
  "maxSelectedLines": 2, "maxEndTimeOffset": 0.5, "lineDelay": 0.05,
  "animationHeadstart": 0.1, "finishLineAnimationDuration": 0.25,
  "opacityAnimator": { "duration": 0.12, "cp1": [0.33, 0.0], "cp2": [0.2, 0.1] },
  "lineChangeAnimator": { "duration": 0.28, "cp1": [0.17, 0.0], "cp2": [0.83, 1.0] },
  "emphasizingScaleRange": [1.0, 1.14], "deselectedScale": 0.98, "bgVocalsDeselectedScale": 0.9,
  "highlight": { "on": {"m":1.0,"k":322,"c":24,"fade":0.2}, "off": {"m":2.0,"k":300,"c":50,"fade":0.3,"delay":0.1} },
  "springs": { "lift": [1,14,7], "tap": [2,260,50], "bgVocals": [1,30,9],
               "grow": {"dampingRatio":1.0} },
  "syllableSpring": { "gapClamp": 0.75, "t": "clamp((min(gap,0.75)-0.2)/0.55,0..1)",
                      "dampingRatio": "0.88-0.12t", "response": "0.48+0.27t" },
  "blur": { "cap": 4.0, "fixed": 3.0, "enabled": true },
  "insets": { "top": 22.0, "bottom": 30.0 }, "gradientFeather": 30.0,
  "progressGuard": 0.5, "seekFastPath": { "duration": 0.25, "curve": 3 },
  "timingHysteresis": { "tap": 1.0, "providerDelta": 0.5 },
  "glow": { "radius": 5.0, "range": [0.0, 0.4], "response": "min(duration,3.0)", "dampingRatio": 1.0 },
  "syllableLift": 2.0,
  "instrumental": { "dots": 3, "dotLength": 12.0, "margin": 8.0, "height": 40.0,
                    "initialAlpha": 0.1, "fadeIn": {"duration": 0.8, "stagger": 0.06} }
}
```

| Поле | Значение / единицы | REQ | Apple-источник (evidence/status) | Class |
|---|---|---|---|---|
| `maxSelectedLines` | 2 | REQ | LYR §2.1 #1 (Specs offset +0xc0) | EXACT |
| `maxEndTimeOffset` | 0.5 s | REQ | LYR §2.1 #2 (`0x3fe0000000000000`) | EXACT |
| `lineDelay` | 0.05 s | REQ | LYR §2.1 #3 (Music.app vpfi 0x100c2b4e0) | EXACT |
| `animationHeadstart` | 0.1 s | REQ | LYR §2.1 #4 | EXACT |
| `finishLineAnimationDuration` | 0.25 s | REQ | LYR §2.1 #5 (`0x3fd0000000000000`) | EXACT |
| `opacityAnimator` | 0.12; (0.33,0)/(0.2,0.1) | REQ | LYR §2.2 #10 (MEE+Music.app) | EXACT |
| `lineChangeAnimator` | 0.28; (0.17,0)/(0.83,1.0) | REQ | LYR §2.2 #11 | EXACT |
| `emphasizingScaleRange` | 1.0…1.14 | REQ | LYR §2.2 #12 (`0x3ff23d70a3d70a3d`) | EXACT |
| `deselectedScale` | 0.98 | REQ | LYR §2.2 #13 | EXACT |
| `bgVocalsDeselectedScale` | 0.9 | REQ | LYR §2.2 #14 | EXACT |
| selected transform | identity | REQ | LYR §2.2 #15 | EXACT |
| `highlight.on/off` | m1/k322/c24, fade 0.2; m2/k300/c50, 0.3/0.1 | REQ | LYR §2.2 #16 | EXACT |
| `springs.lift/tap/bgVocals/grow` | 1/14/7; 2/260/50; 1/30/9; ζ1.0 r0.2 | REQ | LYR §2.2 #17 (порядок m/k/c — STRONG) | EXACT |
| `syllableSpring` | 0.2/0.55/0.75/0.12/0.78/0.27/0.48 | REQ | LYR §2.2 #18 | EXACT |
| `blur.cap/fixed/enabled` | 4.0 / 3.0 / flag | REQ | LYR §2.2 #19 | EXACT |
| blur filters | `gaussianBlur.inputRadius`, `colorBrightness.inputAmount` | REQ | LYR §2.2 #20 | EXACT |
| `insets` | 22.0 / 30.0 pt | REQ | LYR §2.3 #23 | EXACT |
| `contentOffset` формула | `maxY−(H−h)·0.5−payload` / `minY−inset.top` | REQ | LYR §2.3 #21–22 (структура EXACT; кейсы INFERRED) | STRONG |
| `containerHeight` | runtime | OPT | LYR §2.3 #24 (fallback `view.frame.height`) | PORT |
| viewport anchors 28%/38% | — | OPT | LYR §2.3 #25/§4: в iOS отсутствуют | NOT FOUND |
| `USER_SCROLL_PAUSE_MS` 4000 | — | OPT | LYR §2.3 #26/§4: Android-patch | NOT FOUND |
| karaoke gradient colors | `[color, alpha0]` | REQ | LYR §2.4 #32 | EXACT |
| `gradientFeather` | 30.0 pt | REQ | LYR §2.4 #33 | EXACT |
| L2R/R2L геометрия | startX/endX, полоса feather | REQ | LYR §2.4 #34 (ветвления EXACT; enum→направление INFERRED) | STRONG |
| `progressGuard` | backward `<0.5 s` игнор | REQ | LYR §2.4 #31 (`0x3fe0000000000000`) | EXACT |
| `glow` | radius 5.0; range 0.0…0.4; ζ1.0; `min(duration,3.0)` | REQ | LYR §2.4 #36 | EXACT |
| emphasis scale слов | `1.0 + factor·0.14` | REQ | LYR §2.4 #37 | EXACT |
| `syllableLift` | 2.0 pt | REQ | LYR §2.4 #39 | EXACT |
| `seekFastPath` | 0.25 s, curve 3 | REQ | LYR §2.6 #57 | EXACT |
| `timingHysteresis` | 1.0 s / 0.5 s | REQ | LYR §2.1 #6 | EXACT |
| instrumental (dots/geometry/transforms/reset/fade) | 3/12/8/40/0.1; 0.9/1.2/1.2/0.2; −1.8; 0.8/0.06 | REQ | LYR §2.5 #43–54 | EXACT |
| instrumental anchorPoint ±1.3 | значения EXACT; ось/семантика INFERRED | OPT | LYR §2.5 #54/§4 | PARTIAL |
| `lineChangeSpring stiffness 100.0` | — | OPT | LYR §4: double в MEE не найден (только float) | PARTIAL |
| `touchDownTransform 0.95` | — | OPT | LYR §4: double 0.95 в MEE не найден | PARTIAL |
| `paragraphSpacing 39.0` | — | OPT | LYR §4: double 39.0 аудитом не найден | PARTIAL |
| `1.12` scale / smoothstep / 10dp-6dp / 750-250 ms | — | OPT | LYR §4: только Android prior art | NOT FOUND |
| open/close переход лирики | в LyricsX отсутствует; app-side `LyricsSharingAnimationController` m1/k396/c32 | OPT | LYR §2.7 #63–65 (роль INFERRED) | STRONG |
| `mode` VC (2 = seek-путь) | runtime | OPT | LYR §2.6 #57 | PORT |
| `perfTier`/`flags` (tier, feature flags) | auto/high/mid/low | REQ | A3 §5 (Android-флаги); Apple-аналога нет | PORT |
| Glass shared: `cornerCurveExpansionFactor` | 1.5286649465560913 (double `0x3FF875696E58A32F`) | REQ | LGC §1.2 | EXACT |
| Glass shared: AA epsilon | `1e-4` (`0x3F1A36E2E0000000`) | REQ | LGC §1.3 | EXACT |
| Glass shared: variable-blur LOD | `max(0, log2(r<2 ? 0.5r+1 : r))`, 4 tap | REQ | LGC §2 (CORRECTION: 4 тапа, не 5) | EXACT |
| Glass shared: mip cap | `2^min(chain,7)` | REQ | LGC §3.1 | EXACT |
| Glass shared: `plusL` formula | 2 конкурирующие формы | OPT | LGC §9 (матрицы EXACT); ALG §6 (per-pixel — INFERRED) | PARTIAL |
| Glass shared: tile 32×32 / imageblock 16×32 | кодовые значения; фактические tileW/H — runtime | OPT | LGC §3.4 | STRONG |

---

## 2. Pipeline: Track A / Track B (block/edge spec)

### 2.1. Схема (контракт)

```text
                 ┌───────────────────────────── Track A (outgoing) ─────────────────────────────┐
[MediaItem A] → A0 decode → A1 optional time-stretch → A2 DSP instance A → A3 external gain ramp ─┤
                 └──────────────────────────────────────────────────────────────────────────────────┤
                 ┌───────────────────────────── Track B (incoming) ─────────────────────────────┐  │
[MediaItem B] → B0 decode → B1 optional time-stretch → B2 DSP instance B → B3 external gain ramp ─┤
                 └──────────────────────────────────────────────────────────────────────────────────┤
                                                                                                    ▼
                                                             A4/B4 existing Media3 mixing/output
                                                             (2 renderers + fade control + sink)
```

Apple-контур для сравнения (DSPR §B.4, §D.1; DGR §1–§3):
`item decode → item-level AVAudioMixProcessingEffect (per track) + AVMutableAudioMixInputParameters volume ramp
→ ME TimePitch (Spectral; downstream) → ME mixer bus → output`.

### 2.2. Блоки

| ID | Блок | Apple-поведение (evidence/status) | Android-привязка | Class |
|---|---|---|---|---|
| A0/B0 | decode источника | `AVPlayerItem`/MPC item; реализация декодера не декомпилирована (MC §1) | Media3 `MediaCodecAudioRenderer`/decoder | EXACT |
| A1/B1 | optional time-stretch | `TimeStretchingSchedule.{outgoing,incoming}SongSteps` EXACT (DSPR §B.3); исполнитель ME TimePitch, `Spectral` EXACT (DSPR §B.2); `AVPlayerItem.speedRamp` типы/логи EXACT, точная инструкция `setSpeedRamp:` NOT FOUND (DSPR §B.3) | `SpeedChangingAudioProcessor` + `SpeedProvider` (M3 §6); spectral-качество — C++-узел (OPEN) | EXACT |
| A1/B1 (optionality) | когда включается | `ts_rate` присутствует только в стилях 6–12 (14 автоматик, DGR §6); reachable BM-стили 8/9/12 (TS §2.1) | rule: нет `ts_rate`-автоматик → узел passthrough | EXACT |
| A2/B2 | DSP instance A/B | per-track `AVAudioMixProcessingEffect` (2 аллокации, 2 schedule; DSPR §D.1) EXACT; граф `SmartTransitions` dspg EXACT (DGR) | 2 × `DspGraphAudioProcessor` (M3 §2) | EXACT |
| A2/B2 (bypass) | состояние эффектов | default `bypa=1.0` → property 21=1 → 7 AU обойдены; статус следствия STRONG (DSPR §A.5) | Android: явный runtime-контракт bypass; НЕ писать `bypa=0` вслепую | STRONG |
| A3/B3 | external gain ramp | `AVMutableAudioMixInputParameters` volume ramps из `out_gain` (21 автоматика; DSPR §C.2; TSJ §6) EXACT; две независимые аудио-миксы (+0x80/+0x88) EXACT; CMTime timescale 1e9 EXACT | `GainProcessor`/`GainProvider` или C++ gain-node (M3 §7); не дублировать с `PlayerAudioFadeControl` | EXACT |
| A4/B4 | mixing/output | Apple: ME mixer bus → output (DSPR §B.4) | существующий форк: 2 `MediaCodecAudioRenderer` + secondary sink + `PlayerAudioFadeControl` + `DefaultAudioSink`→`AudioTrack` (M3 §1/§5) | PARTIAL |

### 2.3. Рёбра (edges)

| ID | Ребро | Apple-поведение (evidence/status) | Class |
|---|---|---|---|
| E1 | decode → time-stretch | Apple item-обработка: TimePitch **downstream** DSP/mix-effect (STRONG_INFERENCE, DSPR §B.4); контрактная позиция «stretch до DSP» — решение порта | PORT |
| E2 | time-stretch → DSP | расписания DSP и volume заданы в CMTime/item-time и потребляются в media-time; порядок блоков после смены rate не доказан EXACT (DSPR §B.4/B.5) | STRONG |
| E3 | DSP → external gain | gain задаётся на per-track `AVAudioMixInputParameters` (pre-mixer); относительный порядок gain↔effect на item-уровне прямо не заявлен (DSPR §C.2; §B.4) | PARTIAL |
| E4 | gain → mix/output | mixer получает уже обработанный трек; `setVolumeRampFromStartVolume:toEndVolume:timeRange:` (DSPR §C.2) | EXACT |
| E5 | planner → DSP automation | `parameterSchedule: [CMTime:[UInt32:Float]]` → `AVAudioMixProcessingEffect` EXACT (DSPR §D.1; M3 §4); UInt32=fourCC — STRONG (DGR §6) | STRONG |
| E6 | planner → time-stretch | automations `ts_rate` → `TimeStretchingStep{playbackRate,timeRange}` → `speedRampMappings:[CMTimeMapping]?` (DSPR §B.3); функция automations→steps NOT FOUND | PARTIAL |
| E7 | planner → out_gain | `AutomationRamp` (out_gain) → CMTimeRange → volume ramp (DSPR §C.2) EXACT; normalized→absolute mapping PARTIAL | EXACT |
| E8 | crossfade orchestration | Apple MPC `smartTransitionWillBeginFrom:to:…` / `smartTransitionDidEnd…` EXACT selectors (MC hop 12); внутренняя машина — PARTIAL | PARTIAL |
| E9 | lyrics renderer position | Apple `elapsedTimeProvider` closure (LYR §1); Android: player position per frame (ALY §3.1), к аудио-цепочке не подключён | PORT |

**Критично:** контрактный порядок `A1→A2` (stretch before DSP) — `PORT`; Apple-доказанный порядок —
TimePitch **после** DSP-эффекта (`STRONG`). Интерфейсы блоков от порядка не зависят; при получении
runtime-подтверждения блоки меняются местами без смены контрактов.

---

## 3. Do-not-hardcode list (runtime/device tuning и недоказанное)

| # | Что | Почему нельзя хардкодить | Class | Источник |
|---|---|---|---|---|
| 1 | `tileWidth/tileHeight` blur/threadgroup | выбирает Metal/runtime; в бинаре не зашито | PORT | LGC §0/§3.4 |
| 2 | число mip-уровней на кадр (`levels`, `mipmapLevelCount`) | зависит от размера кадра и radius | PORT | LGC §3.1 |
| 3 | числовые веса `tile_simd_blur`/`narrow_blur` для конкретного radius | только runtime (Metal-trace) | PORT | LGC §3.3/§11 |
| 4 | `MTLPixelFormat` слоя Apple Music | runtime; в коде только generic-константы | PORT | LGC §3.4 |
| 5 | эффективные uniform-значения `edge_*`/`shadow_*`/`sdr_*`/`holding_tone_opacity`, CASDF `defaultValues` | дефолты CPU-стороны переопределяются рецептом/интерполяцией; per-pixel шейдеров нет | PORT | LGC §5/§8/§12 |
| 6 | tiles/blur числа Android-пирамиды (download, weights) | производные от собственного рендера | PORT | ALG §5 |
| 7 | `bypa=0` runtime-запись | callsite `bypa=0` не найден; каталог `bypa` не автоматизирует; следствие «эффекты обойдены по умолчанию» — STRONG, не EXACT; нужен runtime-dump property 21 | NOT FOUND | DSPR §A.4/§A.5; DGR §7 |
| 8 | tie-breaker `param_1`: имя поля, единицы, «time-delta»-семантика | арифметика EXACT, семантика UNKNOWN; использовать 0.0 + флаг | NOT FOUND | PLC §12.2; AA §5.1 |
| 9 | stylistic/lyric disputed: `100.0` (lineChange stiffness), `0.95` (touchDown), `39.0` (paragraphSpacing) | аудит не подтвердил double в MEE; в Music.app vpfi есть, но не перепроверено | PARTIAL | LYR §4; REC |
| 10 | `1.12` scale, `smoothstep`, `10dp/6dp`, `750/250 ms` dots | Android prior art; в iOS corpus отсутствуют | NOT FOUND | LYR §4 |
| 11 | viewport anchors `28%`/`38%` | в iOS отсутствуют (0.28 — duration аниматора; 28% — Android patch) | NOT FOUND | LYR §2.3/§4 |
| 12 | `USER_SCROLL_PAUSE_MS=4000`, instrumental `7000 ms` | в iOS не найдены; интерлюд из модели | NOT FOUND | LYR §4; REC |
| 13 | genre IDs / ID→name маппинг | таблицы нет в бинарях/DSC; Android-жанры не равны Apple ID | NOT FOUND | MAA #11; PLC §4.8 |
| 14 | `bpm.percentDeviation` (семантика/шкала) | consumer-negative EXACT; конвертер не масштабирует; live=1 не подтверждает 1% | NOT FOUND | BLK §1 |
| 15 | `loudness.peak` (единицы), `loudness.value/range` | consumer-negative EXACT; линейная vs dB неразрешима | NOT FOUND | BLK §2 |
| 16 | `cos/sin(π/2·t)` и π-константы equal-power в AutoMix | в бинарях отсутствуют; REJECTED | NOT FOUND | TS §4; A3 §0.2 |
| 17 | `plusL` per-pixel формула как «Apple» | две конкурирующие формы; матрицы/имена EXACT, per-pixel INFERRED → фича-флаг | PARTIAL | LGC §9; ALG §6 |
| 18 | `duration` стилей (8/8/16) как секунды | семантика `maximumBarCount` STRONG, единицы NOT FOUND | STRONG | TS §10.1; TSJ §7.4 |
| 19 | `entryOffsetUs` / normalized→absolute времена | runtime-маппинг PARTIAL | PARTIAL | MAA #49; DSPR §C |
| 20 | fallback → style 4 binding; резолвер `beat_length`; привязка не-BM стилей к алгоритмам | не доказаны | NOT FOUND | TS §6/§10; TSJ §5/§7.2 |
| 21 | Android prior-art lyrics: `wordOffset=-100ms`, anchor 28%, pause 4000 ms | `[UNVERIFIED]` для iOS | NOT FOUND | ALY §0; A3 §5 |
| 22 | `cornerCurveExpansionFactor` для CoreMaterial-внутри | в CoreMaterial не декодирован; шейдерная константа — да | PARTIAL | LGC §1.2/§12 |
| 23 | reflection 12 сегментов: геометрия cap | конфиг/дефолты EXACT, построение NOT FOUND | NOT FOUND | LGC §6/§11 #42 |
| 24 | ошибки/дефолты `out_gain=0.0` графа как «поведение» | дефолт EXACT, но семантика «граф стартует закрытым» — INFERRED; значения открываются 21 автоматикой | STRONG | ADSP §2; DSPR §C |

---

## 4. Границы интеграции с Media3 (без кода)

### 4.1. Где что живёт (слои)

| Слой | Позиция | Вход | Выход | Границы |
|---|---|---|---|---|
| Analysis | вне плеера (IO/Default) | MediaAPI JSON / локальный анализ | `SongAnalysis` (immutable) | не знает Media3; кэш по `analysisVersion` (A3 §3.1–3.2) |
| Planner | выделенный single-thread executor | 2 × `SongAnalysis` + criteria | `TransitionPlan` | чистая функция без I/O; не знает Media3 (A3 §3.3) |
| Compiler | JVM | `TransitionPlan` | `CrossfadeSpec`, `ParameterTimeline`, `TimeStretchSpec` | единственная точка разделения на Media3-контракты (A3 §3.4) |
| Native DSP | audio thread, 2 инстанса | `ParameterTimeline` + PCM float | PCM float | `AudioProcessor` в `buildAudioSink`; JNI; offload off (M3 §2/§8) |
| Automation | provider/ring buffer | timeline events | per-frame параметры | sample-accurate provider предпочтителен; `PlayerMessage` — fallback (M3 §4) |
| Lyrics renderer | UI/Compose | `LyricsModel` + позиция игрока | UI state | к аудио-цепочке не подключён; один frame clock (ALY §3.1) |
| Glass | UI/Compose | `RuntimeShader`/`RenderEffect` | визуал | вне audio/planner (ALG §9) |

### 4.2. Media3-швы (только существующие; fork-патчи вне scope)

| Потребность | Шов | Доступ | Источник |
|---|---|---|---|
| Вставить DSP в оба рендерера | `DefaultRenderersFactory.buildAudioSink` + второй аудио-рендерер/sink | protected | M3 §1/§3 |
| Цепочка процессоров | `DefaultAudioSink.Builder.setAudioProcessorChain`/`setAudioProcessors`, float output | public | M3 §2/§8 |
| Gain input/output | `GainProcessor` + `GainProvider` | public | M3 §7 |
| Time-stretch | `SpeedChangingAudioProcessor` + `SpeedProvider` | public | M3 §6 |
| Автоматизация | `PlayerMessage` либо собственный provider по образцу `SpeedProvider` | public | M3 §4 |
| Длительность/кривая/entry offset свода | `ExoPlayer.CrossfadeConfiguration` (`durationUs`, `curveType`, `entryOffsetUs`) | public | M3 §5 |
| Реальный crossfade-движок | `PlayerAudioFadeControl`, `CrossfadeTrackRouting` (форк) | package-private | M3 §1/§11 (патч — решение владельца) |
| Позиция для лирики | `Player.currentPosition` + `withFrameNanos` | public | ALY §3.1 |

### 4.3. Fallback-матрица

| Условие отказа | Fallback | Источник |
|---|---|---|
| нет `audio-analysis` и нет локального анализа | `supportsTransition=false` → plain crossfade фиксированной длительности | A3 §3.1 |
| planner failure / timeout / exception | `FallbackCrossFadeStrategy` (Apple 2.0 s EXACT) или настройка Android | PLC §13; AA §9 |
| нет DSP-плана / `automix.dsp=off` | `isActive()==false` — процессор пропускается | M3 §2 |
| `bypa=1` (штатный Apple-дефолт) | эффект-AU обойдены, dry-путь | DSPR §A.5 |
| TimeStretchSpec пуст | `SpeedProvider.DEFAULT` (rate=1.0) | M3 §6 |
| aux-bus v1 при двух рендерерах | `perTrack` (v2 `shared` — решение порта; Apple-эталон RETRACTED как shared) | ADSP §1; DSPR §D.3 |
| lyrics: нет TTML/тайминга | line-level режим / LRC | ALY §3 |
| glass: API<33 / tier=low | `RenderEffect.blur`+tint; API<31 — статичный фон | ALG §10 |
| crossfade недоступен | `CrossfadeConfiguration.DEFAULT` (свод выключен) | M3 §5 |

### 4.4. Testability checkpoints

| # | Checkpoint | Что проверяет | Источник |
|---|---|---|---|
| 1 | Planner golden на live-JSON фикстурах | score = base·Πfactors + tie·0.001; отбраковка `<=0`; ничьи — первый | A3 §6.1 |
| 2 | Threshold-тесты | tempo 0.16/0.287, min bars 8, `60/bpm` half/double, полосы musicality с правильной полярностью | A3 §6.2 |
| 3 | DSP evaluator sweep 0x00…0xff | соответствие `curveByte` формулам; границы 0/1 для 0x81 | ADSP §10.1 |
| 4 | DSP null-test | `bypa`/passthrough = bit-exact; clamp 29 диапазонов | ADSP §10.2–10.3 |
| 5 | Media3 overlap instrumentation | 2 рендерера без glitch; `entryOffsetUs` применяется; fallback при отсутствии анализа | A3 §6.4 |
| 6 | Energy/RMS null-test перехода | отсутствие провалов/пиков относительно legacy | M3 §12.2 |
| 7 | Provider/ring-buffer под нагрузкой | без дропов/гонок (TSan); sample-accurate сетка | ADSP §10.8 |
| 8 | Lyrics parser golden | 56/56 `p`, span 644/364, `2:40.160=160160`, границы End<Start | ALY §10 |
| 9 | Lyrics timing/UI | `evaluateAt` без мигания на границах; seek без прыжка; scroll/пауза по флагам | ALY §10; LYR §2 |
| 10 | Glass деградация | tier=low/minimal на API 29/31/33/36 без крашей; frame budget 60/120 Гц | ALG §12 |
| 11 | JNI safety | отсутствие крашей при `loadLibrary` failure (стиль `runCatching`) | ADSP §9; M3 §12.7 |
| 12 | Aux bus perTrack vs shared | корреляция фазы и CPU | M3 §12.6 |

---

## 5. Spec completeness по подсистемам (counting basis)

### 5.1. База подсчёта

Считаются только **нормативные строки таблиц** разделов §1 и §2 (строки, начинающиеся с `|` и
заканчивающиеся ячейкой Class ровно одного из `EXACT|STRONG|PARTIAL|NOT FOUND|PORT`).
Проза, заголовки и §3/§4 не считаются. Команда воспроизведения — §5.4.

- `N = EXACT + STRONG + PARTIAL + NOT FOUND` — Apple-derived items (без `PORT`).
- **Completeness** = `(EXACT + STRONG) / N` — доля пунктов, переносимых как доказанное поведение/данные.
- `PORT` показывается отдельно (решения порта, не входят в Apple-полноту).

### 5.2. Сводная таблица

| Подсистема | Раздел(ы) | N | EXACT | STRONG | PARTIAL | NOT FOUND | PORT | Completeness |
|---|---|---|---|---|---|---|---|---|
| SongAnalysis (модель анализа) | 1.1 | 27 | 6 | 8 | 4 | 9 | 2 | 51.9% |
| TransitionCandidate | 1.2 | 21 | 15 | 1 | 5 | 0 | 1 | 76.2% |
| TransitionPlan | 1.3 | 23 | 16 | 2 | 5 | 0 | 0 | 78.3% |
| TransitionStyle | 1.4 | 18 | 13 | 3 | 1 | 1 | 0 | 88.9% |
| AutomationRamp / curves | 1.5 | 10 | 8 | 2 | 0 | 0 | 0 | 100.0% |
| DSPParameter (29 + служебные) | 1.6 | 35 | 34 | 1 | 0 | 0 | 0 | 100.0% |
| DSPGraphInstance | 1.7 | 15 | 12 | 0 | 1 | 2 | 2 | 80.0% |
| LyricsModel | 1.8 | 17 | 11 | 2 | 1 | 3 | 2 | 76.5% |
| RendererConfig (lyrics+shared) | 1.9 | 42 | 30 | 4 | 5 | 3 | 3 | 81.0% |
| Pipeline (Track A/B, блоки+рёбра) | 2.2–2.3 | 14 | 7 | 3 | 4 | 0 | 2 | 71.4% |
| **Итого** | §1–§2 | **222** | **152** | **26** | **26** | **18** | **12** | **80.2%** |

### 5.3. Комментарий к полноте

- Наивысшая полнота: данные ресурсов Apple — `AutomationRamp` (100%), `DSPParameter` (100%),
  `TransitionStyle` (89%): значения/диапазоны/кривые переносятся 1:1.
- Средняя: `RendererConfig` (81%), `DSPGraphInstance` (80%), `TransitionPlan` (78%),
  `TransitionCandidate` (76%), `LyricsModel` (77%).
- Ниже: `SongAnalysis` (52%) — из-за большого числа полей MediaAPI с проверенным негативом
  (consumer-negative) и planning-источников, не найденных в корпусе; `Pipeline` (71%) —
  из-за позиции time-stretch и относительного порядка gain↔effect.
- `NOT FOUND` сконцентрированы в: `bypa=0` callsite, per-instance lifecycle, отсутствующие
  planner-источники `beatEvents/bars`, `percentDeviation/peak`, Apple TTML-парсер.

### 5.4. Команда подсчёта (воспроизводимость)

```bash
python3 - <<'EOF'
import re
p='/srv/research/apple-music-ios26/deepseek_analysis/10_final_closure/ANDROID_CLEANROOM_CONTRACT.md'
sec=None; c={}
for ln in open(p):
    m=re.match(r'^### (1\.\d+)',ln)
    if m: sec='1.'+m.group(1).split('.')[1]
    if re.match(r'^## 2\.',ln): sec='2'
    if re.match(r'^## 3\.',ln): sec=None
    if sec and ln.startswith('|'):
        m2=re.search(r'\|\s*(EXACT|STRONG|PARTIAL|NOT FOUND|PORT)\s*\|\s*$',ln)
        if m2: c.setdefault(sec,{}); k=m2.group(1); c[sec][k]=c[sec].get(k,0)+1
for s in sorted(c): print(s,c[s])
EOF
```

---

## 6. Сводка открытых неизвестных (не заполнять догадками)

1. `bypa=0` runtime-запись и фактическое значение property 21 в рантайме (DSPR NOT FOUND #1).
2. Литеральный call-site `TransitionPlanner.transition(...)` (MC §3; #9 PARTIAL).
3. Функция automations→`TimeStretchingStep`; точная инструкция `setSpeedRamp:` (DSPR NOT FOUND #2–3).
4. Формула синхронизации alignment/pivot в stretched time (DSPR #4).
5. Teardown/reset DSPGraph-инстанции (DSPR #5); дедупликация по identifier (DSPR #6).
6. Имя/единицы tie-breaker `param_1` (PLC §12.2; #2 BLOCKED).
7. Поля `beatEvents/downbeatEvents/bars`, `beatStabilityMap`: источник (PLC §1.4/§5.4, #18 UNKNOWN).
8. Genre ID→name таблица (#11 BLOCKED).
9. `percentDeviation`, `loudness.peak` (#13/#15 BLOCKED).
10. Apple TTML-парсер (внешний MSV) и серверный endpoint (#25 PARTIAL; §13.13 BLOCKED).
11. Reflection cap геометрии; runtime mip/tile/weights glass (#42/#47 PARTIAL).
12. Диапазоны/байты `ease-in-4`/`ease-out-4` внутри 0x02–0x3f / 0x42–0x7f (TS §10.5; STRONG).
13. `entryOffsetUs` маппинг normalized→absolute (MAA #49 PARTIAL).

**Правило наследования:** при появлении любого из этих значений — обновлять соответствующий
контракт-файл с `Class=EXACT` и новым evidence; текущие `PARTIAL/NOT FOUND` строки заменяются,
а не «достраиваются».
