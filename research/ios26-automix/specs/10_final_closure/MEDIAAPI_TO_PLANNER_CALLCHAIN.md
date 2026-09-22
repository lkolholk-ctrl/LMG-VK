# MEDIAAPI_TO_PLANNER_CALLCHAIN — финальная цепочка «поле MediaAPI JSON → потребитель планировщика»

**Дата:** 2026-09-13. **Роль:** Final Closure / MediaAPI→Planner Analyst (субагент 10).
**Ограничения:** research only; `/root/LMG-VK` и код приложения не изменялись; изменений вне
`deepseek_analysis/10_final_closure/` и файла `indexes/KEY_SYMBOLS_03_mediaapi.tsv` нет.
Секреты не приводятся (используется только sanitized live JSON).

**Статусы (по мандату):** `EXACT` — подтверждено raw-артефактом/адресом;
`STRONG_INFERENCE` — несколько независимых подтверждённых артефактов;
`PARTIAL` — часть звеньев подтверждена, часть нет; `UNKNOWN` — нет данных;
`UNUSED BY PLANNER (no evidence)` — xref/callee-доказательства потребления планировщиком нет
(наличие поля в API или «логичного» потребителя НЕ считается доказательством).

---

## 0. Метод и границы доказательности

1. **Три модели** (подтверждено в P1/P2 closure):
   `MediaAPI JSON` → `MusicKit.Cloud*` (Codable, MusicKit.framework, DSC subcache `.40`) →
   `MusicKitInternal.AudioAnalysis/FlexAnalysis` (нормализованная доменная модель) →
   `_SonicKit_MusicKit_Packages.TransitionPlanner.Song.MusicKitAnalysis` (планировщик).
2. **Call-level доказательства потребления планировщиком** получены в `08_closure/P2_MEDIADSP_CLOSURE.md`
   резолвом branch-pool островов (`__auth_stubs` всех 16 targets + пул `0x2743d…`). Полный DSC,
   из которого P2 резолвил острова (`/srv/research/tmp/dscfull/**`), на момент финального closure
   **удалён с диска**; повторный резолв невозможен. Поэтому:
   - **call-site острова → функция планировщика** — перепроверены локально по
     `automix/{disassembly,pseudocode}` (этот отчёт);
   - **остров → MKI-getter** — берётся из P2 §4.1 как документированный результат резолва
     (P2-провенанс), с явной пометкой в каждой строке.
3. **Правило мандата:** «MediaAPI-поле существует» ≠ «планировщик читает». Для каждого поля
   колонка «planner consumer» либо содержит конкретную функцию/адрес с xref/callee, либо
   `UNUSED BY PLANNER (no evidence)`.
4. **Проверенные локально негативные сканы** (новое в этом отчёте):
   - `rg` по `09_appos/musicapp_ghidra/function_index.tsv`, `symbols/`, `strings/` для
     `AudioAnalysis|CloudAudio|percentDeviation|loudness|Statistics|acousticness|beatsPerMinute|eventTimes|
     tonality|loudnessCurve|entryPoints|visualTempo|videoEvents|arousal|TransitionPlanner|SmartCrossFade` —
     **0 релевантных потребителей в Music.app** (см. §4, `blocked_13_15.md` §Searches).
   - Прямой скан `bl/b` на адреса MKI-getter'ов во всех `*/disassembly` corpus — см. §4.
   - `strings -a targets/_SonicKit_MusicKit_Packages` — нет `BeatsPerMinute`, `EventTimes`,
     `Statistics`, `energy`, `valence`, `arousal`, `visualTempo`, `entryPoints`, `exitPoints`,
     `fadeToBlack`, `LoudnessCurve` (см. §4).

---

## 1. Главная цепочка: поле → модель → конверсия → planner-facing → потребитель

Колонки: `MediaAPI JSON` → `decoded MusicKit (Cloud)` → `MusicKitInternal conversion` →
`planner-facing` → `planner consumer` → `status`.

| MediaAPI JSON field | decoded MusicKit property (Cloud) | MusicKitInternal conversion (fn/addr) | planner-facing property (`Song.Analysis` / `MusicKitAnalysis`) | planner predicate/scoring consumer (fn/addr) | status |
|---|---|---|---|---|---|
| `beats.beatsInMilliseconds` `[Int]` | `CloudEventTimes.beatsInMilliseconds:[Int]?` (MusicKit desc 0x21617744c) | `EventTimes.beatOccurences:[Double]`; `FUN_1d41218c4` = `(double)x / 1000.0` на элемент (closure `0x1d412061c`, call @line 439); getter `0x1d43967e0` | у планировщика есть собственные `beatEvents/downbeatEvents/BeatStabilityMap`, но **getter `eventTimes` не входит** в 41 packages-импорт (P2 §1.2/§4.1), и острова для него нет | **UNUSED BY PLANNER (no evidence)**. Двигатель (не planner): `SmartTransitionSongData` читает `MediaAPI.AudioAnalysisAttributes` in-module (inline) | **UNUSED BY PLANNER (no xref)**; конверсия ms→s EXACT |
| `beats.barsInMilliseconds` `[Int]` | `CloudEventTimes.barsInMilliseconds:[Int]?` | `EventTimes.barOccurences:[Double]`; тот же `FUN_1d41218c4` | `bars` (planner-модель) — источник не установлен | **UNUSED BY PLANNER (no evidence)**; двигатель — см. выше | **UNUSED BY PLANNER (no xref)** |
| `bpm.{main,beginning,ending}` `Int` | `CloudCompositeAttribute<Double>` (key `bpm`, desc 0x2161773cc) | `BeatsPerMinute` `FUN_1d4121a98`: 3× `fcvtzs` (Double→Int) для main/beginning/ending (closure 0x1d412061c, call @line 467). Адрес getter: `0x1d4396964` | `MusicKitAnalysis.audioAnalysis` (getter 0x27225d788) | **UNUSED BY PLANNER (no evidence)**: getter `0x1d4396964` не в 41-импорте, нет `BeatsPerMinute`-строк в packages. Темповые предикаты существуют (`FUN_272236498`, `FUN_272219ff0`), но их входной путь не прослежен → consumer UNKNOWN | **UNUSED BY PLANNER (no xref)**; конверсия `[STRONG_INFERENCE]` (P2 §1.1) |
| `bpm.percentDeviation` `Int(live)/Double?` | `CloudCompositeAttribute<Double>.percentDeviation:Double?` (CodingKeys 0x21617771c) | `BeatsPerMinute.percentDeviation:Double?` getter `0x1d4399720`; `FUN_1d4121a98` значение **не масштабирует** (нет fmul/fdiv) | — | нет ни одного вызывающего во всех 16 targets (P2 §1.2) | **#13: BLOCKED** (потребителя нет; семантика UNVERIFIED) |
| `key.{beginning,ending,main}.{tonic,mode}` `String` | `CloudCompositeAttribute<CloudCompositeAttributeTonality>` (key `key`) | `CompositeAttribute<Tonality>` `FUN_1d41222b8` `[STRONG_INFERENCE]` (closure call @line 703) | `MusicKitAnalysis.audioAnalysis.tonality` (getter 0x1d4396898) | предикат `FUN_272231d04` («Tonality relationship…») ← `FUN_2722307ec`; тональные аксессоры в 41-импорте (P2 §4.1: `CompositeAttribute.main/beginning/ending+Ma × tonic/mode+Ma`), один из составных островов `FUN_2722200cc` | **PARTIAL**: потребитель EXACT, остров→поле не переразрешён (DSC нет) |
| `loudnessCurve.value` `[Double]` (~2 Гц) | `CloudLoudnessCurve.value:[Double]?` | `LoudnessCurve`; конвертер cloud→internal среди `FUN_1d4121c24`/`FUN_1d4121ef0` — **UNKNOWN** (P1 B3); getter `0x1d4396c24` | `MusicKitAnalysis.audioAnalysis.loudnessCurve` | `FUN_272223b6c` — единственный вызов острова `0x2743dd1c0` (`= AudioAnalysis.loudnessCurve`, P2 §3.2) @`0x272223d58`; внутри читает `samplingFrequency`/`value`; строит `LoudnessMap` → потребители `FUN_272232790` (avg loudness, ←`FUN_2722305c8`), `FUN_272235840` (trailing incoming loudness ratio, ←`FUN_272234380` → `FUN_27222d644` score) | **EXACT** (потребитель); конверсия `PARTIAL` |
| `vocalActivity[]` (start/end ms, strength, kind) | `[CloudVocalActivity]` | `FUN_1d4122dc0` → поэлементно `FUN_1d41253ac`: `(double)x/1000.0` для start/end; strength/kind через `FUN_1d4124d0c`/`FUN_1d4125094` (closure calls @813); getter `0x1d4396d18` | `MusicKitAnalysis.audioAnalysis.vocalActivities` | `FUN_27222343c`: `vocalActivities` @`0x2722236f8`, `strength` @`0x27222388c`, `kind` @`0x272223968` (острова `0x2743dd1d0/2e0/2c0`); модель `TransitionPlanner.VocalActivity{kind,strength}`; значимость — `FUN_272235198` (strength) ← `FUN_272234380`; kind используется для конверсии enum (switch 0/1/2) | **EXACT** |
| `acousticness.{beginning,ending,main}` | `CloudCompositeAttribute<Double>` | generic CompositeAttribute (точный конвертер per-field не локализован — `UNKNOWN`) | `MusicKitAnalysis.audioAnalysis.acousticness` (getter 0x1d4396c00); один из островов `0x2743dd270/170/180/160` в `FUN_2722200cc` (call-sites 0x272220480/0x272220550/0x27222061c/0x2722206ec) | `FUN_272243f80` (`v<0.85 || 1.0<v` = insignificant) ← `FUN_272242fd0` (лог «Outgoing/Incoming acousticness significant.») ← `FUN_27224241c` (BeatMatched styling) | потребитель **EXACT**; getter→предикат `STRONG_INFERENCE` (P2 41-импорт) |
| `danceability.*` | `CloudCompositeAttribute<Double>` | тот же generic путь | getter `0x1d4396b14`; один из указанных островов | `FUN_27224400c` (`0.3<=v<=1.0` = significant) ← `FUN_272242fd0` | потребитель **EXACT**; getter→предикат `STRONG_INFERENCE` |
| `melodicness.*` | `CloudCompositeAttribute<Double>` | тот же generic путь | getter `0x1d4396af0`; один из указанных островов | `FUN_2722341d4` (`v<0.25 || 1.0<v`) ← `FUN_272231d04` (tonality fallback) ← `FUN_2722307ec` | потребитель **EXACT**; getter→предикат `STRONG_INFERENCE` |
| `energy.*` (audio-analysis) | `CloudCompositeAttribute<Double>` | generic; конверсия не локализована | getter `0x1d43967bc` | нет: не в 41-импорте, нет `energy`-строк в packages, нет прямых `bl` на getter, нет потребителя в Music.app | **UNUSED BY PLANNER (no evidence)** |
| `valence.*` (audio-analysis) | `CloudCompositeAttribute<Double>` | generic | getter `0x1d4396a1c` | нет (то же) | **UNUSED BY PLANNER (no evidence)** |
| `fades.fadeIn/fadeOut` (ms) | `CloudFades` → `CloudTimeRange` | `Fades` getter `0x1d4396b38`; cloud→internal конвертер не локализован; обратные — `FUN_1d412469c`/`FUN_1d41256cc` (×1000) | `MusicKitAnalysis.audioAnalysis.fades` | нет: getter не в 41-импорте; в corpus есть только MKI-внутренний wrapper `thunk_FUN_1d4397bc8` без вызовов. Логи `fade rate/fading out trend` относятся к loudness-тренду | **UNUSED BY PLANNER (no evidence)** |
| `phrases` (`[TimeRange]`, в live отсутствует) | `[CloudTimeRange]` | `[TimeRange]` `FUN_1d4122c04` (÷1000, closure call @797); getter `0x1d4396cec` | `MusicKitAnalysis.audioAnalysis.phrases` | нет: getter не в 41-импорте; MKI wrapper `FUN_1d3d4d3f0` без corpus-callers | **UNUSED BY PLANNER (no evidence)** |
| `entryPoints[]` (`timeInSeconds`, `gainTimeInSeconds`, `gainValue`, `tags`) | `[CloudPivotPoints]` | `PivotPoints`; `FUN_1d40caae4` `[STRONG_INFERENCE]` (P1 §5.4); getter `0x1d3e81880`; `Gain.timeOffset/value` 0x1d3e85a34/38 | `MusicKitAnalysis.flexAnalysis.entryPoints` | planner: нет (нет `PivotPoints`/`entryPoints` строк, нет в 41-импорте). Двигатель: `SmartTransitionSongData.transitionPivotPoint` getter `0x2721afaa4` `[STRONG_INFERENCE]` (P1/P2) | **UNUSED BY PLANNER (no evidence)**; engine `STRONG_INFERENCE` |
| `exitPoints[]` (+`fadeToBlack`) | `[CloudPivotPoints]` (fadeToBlack: Double?) | тот же путь; `PivotPoints.fadeToBlackScore` getter `0x1d3e84fc8` | `MusicKitAnalysis.flexAnalysis.exitPoints` | planner: нет. Двигатель: `transitionPivotPoint`/fade-to-black `[STRONG_INFERENCE]` | **UNUSED BY PLANNER (no evidence)**; engine `STRONG_INFERENCE` |
| `visualTempo.value[0]` (`bpm/4`), `samplingFrequency=-1` | `CloudSampledValues` | `SampledValues`; getter `0x1d3e81ab0` | `MusicKitAnalysis.flexAnalysis.visualTempo` | нет: не в 41-импорте, нет `visualTempo`-строк в packages, нет прямых `bl` на getter | **UNUSED BY PLANNER (no evidence)** |
| `videoEvents.{timeInSeconds,score}` (642 точки) | `CloudEvents{timeInSeconds:[Double], score:[Int]}` | `FlexAnalysis.Event{time,timeScale,amplitude}`; `FUN_1d3e5ef4c` + `Event.init?(time:score:)` 0x1d3e81ad4: `score→TimeScale` (short[200,299]/medium[400,499]/long[600,699]/extraLong[800,899]; остальные отбрасываются), `amplitude=(score%100)/100.0` — EXACT | `MusicKitAnalysis.flexAnalysis.events` (getter `0x1d3e81a84`, поле flexAnalysis: metadata index 3, offset table +0x1c, `ldrsw` @0x2722232cc) | import подтверждён: P2 §4.1 (`FlexAnalysis.events/time/timeScale + Ma`). Builder `FUN_2722231b4` (caller `FUN_2722200cc`, читает `MusicKitAnalysis.flexAnalysis`; flex-кластер островов `0x2743dd150/140`); `FUN_272225ef4` (тот же caller) использует острова `0x2743dd100/110/120/130`; конкретное соответствие остров↔`Event.time/timeScale` не переразрешено; **scoring-предикат не локализован** | **PARTIAL** |
| `arousal.value[0]`, `valence.value[0]` (flexml) | `CloudSampledValues` | `SampledValues`; getters `0x1d3e8185c`/`0x1d3e819a8` | `MusicKitAnalysis.flexAnalysis.arousal/valence` | нет (P2 §4.1 не включает; строк нет) | **UNUSED BY PLANNER (no evidence)** |

> Примечание: `durationInMillis` и `supportsSmartTransitions` — вне этого мандата; их использование
> (confidence/ограничения длительности; gate) не перепроверялось в этом отчёте.

---

## 2. Ключевые доказательства (provenance)

### 2.1. P2-резолв импортов планировщика (основание для «imported/not imported»)

```
Source file: 08_closure/P2_MEDIADSP_CLOSURE.md §1.2, §3.1, §4.1
Framework/Binary: _SonicKit_MusicKit_Packages (41 import stub 0x27229033c..0x272290608),
                  _SonicKit_MusicKit (15 import stub)
Function/Symbol: резолв branch-pool островов + __auth_stubs
Evidence type: xref | disassembly
Status: EXACT (на момент P2; DSC с пулом удалён)
Reasoning: среди 41/15 целей нет getters 0x1d4399720, 0x1d4396964, 0x1d43967e0;
           есть FlexAnalysis.events/time/timeScale+Ma, VocalActivity start/end/strength/kind+Ma,
           CompositeAttribute.main/beginning/ending+Ma (×tonality tonic/mode+Ma),
           acousticness/danceability/melodicness/loudnessCurve/vocalActivities,
           LoudnessCurve.samplingFrequency/.value, SpatialTimingInformation.*.
```

### 2.2. Локально перепроверенные call-sites островов (этот отчёт)

```
272220480: bl 0x2743dd270   \ 4 составных CompositeAttribute-геттера в FUN_2722200cc
272220550: bl 0x2743dd170   |  (acousticness/danceability/melodicness/tonality —
27222061c: bl 0x2743dd180   |  соответствие остров↔поле не переразрешено; pseudocode
2722206ec: bl 0x2743dd160   /  2722200cc lines 195/223/251/280)
2722236f8: bl 0x2743dd1d0   ; AudioAnalysis.vocalActivities        (FUN_27222343c)
27222388c: bl 0x2743dd2e0   ; VocalActivity.strength               (FUN_27222343c)
272223968: bl 0x2743dd2c0   ; VocalActivity.kind                   (FUN_27222343c)
272223d58: bl 0x2743dd1c0   ; AudioAnalysis.loudnessCurve          (FUN_272223b6c)
272223250: bl 0x2743dd150   ; flex-кластер (Ma/аксессор)           (FUN_2722231b4)
27222335c: bl 0x2743dd140   ; flex-кластер (аксессор)              (FUN_2722231b4)
272225f24: bl 0x2743dd110   ; flex-кластер (роль не переразрешена) (FUN_272225ef4)
272225fa4: bl 0x2743dd130   ; flex-кластер (роль не переразрешена) (FUN_272225ef4)
27222607c: bl 0x2743dd100   ; flex-кластер (роль не переразрешена) (FUN_272225ef4)
272226088: bl 0x2743dd120   ; flex-кластер (роль не переразрешена) (FUN_272225ef4)
27222452c: bl 0x2743dd1e0   ; CompositeAttribute.main/beginning/ending (FUN_272224414,
2722245d8: bl 0x2743dd200   ;  острова 0x2743dd1e0/1f0/200 — соответствие конкретному
27222466c: bl 0x2743dd1f0   ;  sub-полю не переразрешено)
```
Острова `0x2743dd1d0/2e0/2c0/1c0` соответствуют MKI-аксессорам по P2 §4.1/§3.2 (P2-провенанс).
Назначение островов `0x2743dd160/170/180/270` конкретным полям (acousticness vs danceability vs
melodicness vs tonality) **в этом отчёте не переразрешено** — DSC пула отсутствует.

### 2.3. Привязка построителей к полям `MusicKitAnalysis` (field-offset metadata, этот отчёт)

Дизассемблер (raw `ldrsw` после `Ma` — `bl 0x27225d7cc`):

| Функция | Инструкции | Offset-таблица `Ma` | Поле (по порядку полей из P1 types) |
|---|---|---|---|
| `FUN_2722200cc` | 0x272220408 `bl Ma`; 0x27222040c `ldrsw x28,[x0,#0x18]` | +0x18 (index 2) | `audioAnalysis` |
| `FUN_27222343c` | 0x272223684 `bl Ma`; 0x272223688 `ldrsw x8,[x0,#0x18]` | +0x18 | `audioAnalysis` |
| `FUN_272223b6c` | 0x272223cd8 `bl Ma`; 0x272223cdc `ldrsw x8,[x0,#0x18]` | +0x18 | `audioAnalysis` |
| `FUN_2722231b4` | 0x2722232c8 `bl Ma`; 0x2722232cc `ldrsw x8,[x0,#0x1c]` | +0x1c (index 3) | `flexAnalysis` |
| `FUN_272220920` | 0x272220a20 `bl Ma`; 0x272220a24 `ldrsw x8,[x0,#0x20]` | +0x20 (index 4) | `spatialTimingInformation` |

```
Source file: automix/disassembly/2722200cc__FUN_2722200cc.asm (212-214);
             27222343c (151-153); 272223b6c (96-98); 2722231b4 (74-76); 272220920 (69-71)
Evidence type: disassembly + type_metadata (Ma 0x27225d7cc = MusicKitAnalysis.Ma)
Status: EXACT (считываемые смещения); index↔имя поля — STRONG_INFERENCE (порядок полей:
        genres, duration, audioAnalysis, flexAnalysis, spatialTimingInformation, Options — P1 types)
```

### 2.4. Предикаты musicality/tonality (перепроверено, EXACT)

| Функция | Свидетель-офсет | Правило (диассемблер) | Вызывающий | Лог |
|---|---|---|---|---|
| `FUN_272243f80` | +0x78 | `v<0.85 \|\| 1.0<v` → TRUE (insignificant) | `FUN_272242fd0` | «Outgoing/Incoming acousticness significant» |
| `FUN_27224400c` | +0x80 | `0.3<=v<=1.0` → TRUE (significant) | `FUN_272242fd0` | «Outgoing/Incoming danceability insignificant» |
| `FUN_2722341d4` | +0x88 | `v<0.25 \|\| 1.0<v` → TRUE | `FUN_272231d04` | tonality fallback «insignificant melodicness» |
| `FUN_272231d04` | — | tonality relationship | `FUN_2722307ec` | «Tonalities are compatible…» |

Полярность сохраняется как в `07_audit/RED_TEAM_AUDIT.md` §4.3: acousticness/melodicness возвращают
TRUE для «insignificant», danceability — TRUE для «significant». Числа перепроверены:
`0.85` (fmov в 272243f80), `0.3`/`1.0` (27224400c), `0.25` = `0x3fd0000000000000` (2722341d4).

### 2.5. Loudness/vocal потребители (этот отчёт, EXACT)

| Функция | Роль | Caller |
|---|---|---|
| `FUN_272223b6c` | строит `LoudnessMap` из `loudnessCurve` | `FUN_2722200cc` |
| `FUN_272232790` | «Average loudness is insignificant…» | `FUN_2722305c8` |
| `FUN_272235840` | «Trailing incoming loudness ratio…», «Region loudness not negative» | `FUN_272234380` |
| `FUN_272235198` | «Leading incoming vocal activity … strength» | `FUN_272234380` |
| `FUN_27222d644` | score (base × factors, tie-breaker) | `FUN_272234380` и др. |

---

## 3. Потребители вне планировщика (двигатель SmartTransitions, не `TransitionPlanner`)

| Поле | Потребитель | Доказательство | Статус |
|---|---|---|---|
| `entryPoints`/`exitPoints`/`fadeToBlack`/`gainTimeInSeconds`/`gainValue` | `SmartTransitionSongData.transitionPivotPoint` getter `0x2721afaa4`; `FullSmartTransitionData`, `SmartTransitionRequestSongData` | P1/P2: строк нет в `_SonicKit_MusicKit_Packages`, есть в `_SonicKit_MusicKit`; call-level цепочка не локализована | `STRONG_INFERENCE` (engine) |
| `beats/bars/bpm` | `MediaAPI.AudioAnalysisAttributes` (прямая Codable-схема в `_SonicKit_MusicKit`) → `SmartTransitionSongData`; same-module доступы инлайнятся, branch-pool их не содержит | P2 §«Что осталось BLOCKED», строка 13/16 | `UNKNOWN/PARTIAL` (engine inline) |
| `videoEvents` (Flex events) | planner импортирует `FlexAnalysis.events/time/timeScale` (P2 §4.1) | см. §1 | planner `PARTIAL`; engine-потребление не проверялось |
| `videoEvents.score` → Level/TimeScale | `FUN_1d3e5ef4c` / `Event.init?(time:score:)` | P1 §5.4, перепроверено | `EXACT` |

---

## 4. NOT FOUND (по мандату — то, что не найдено, и что именно искали)

1. **Прямая call-level цепочка «JSON-ключ → getter → builder → предикат»** для
   `acousticness/danceability/melodicness/tonality`: острова `0x2743dd160/170/180/270` не
   переразрешены (полный DSC/branch pool удалён: `/srv/research/tmp/dscfull/**` отсутствует;
   `/tmp/opencode/p2work/reader.py` без него не работает). Статус звена — `STRONG_INFERENCE`.
2. **Потребителя `beatsInMilliseconds`/`barsInMilliseconds`/`bpm.main` в планировщике** —
   нет: getters `0x1d43967e0`/`0x1d4396964` не вызываются ни из одного из 16 targets (P2),
   нет строк `EventTimes`/`BeatsPerMinute` в `_SonicKit_MusicKit_Packages`.
   Как планировщик получает данные для `beatEvents/bars` — **UNKNOWN** (возможный кандидат:
   FlexAnalysis events — `videoEvents`, но прямого xref «events → BeatEvent» нет).
3. **Потребителя `energy`, `valence` (audio-analysis), `arousal`, `valence`, `visualTempo` (flexml)** —
   нет: не в 41-импорте; `rg` по Music.app — 0; прямых `bl` на getters — 0.
4. **Потребителя `fades`/`phrases`** — нет: getters `0x1d4396b38`/`0x1d4396cec` не в 41-импорте;
   существуют только MKI-внутренние wrapper'ы `thunk_FUN_1d4397bc8` и `FUN_1d3d4d3f0` без вызовов
   в corpus (и без символов).
5. **Scoring-потребитель `videoEvents`** — не локализован (import и построитель есть, предиката нет).
6. **Конвертера cloud→internal для `fades`** и **персональной привязки `FUN_1d4121c24`/`FUN_1d4121ef0`**
   (loudness/energy/valence/…) — нет (P1 B3).
7. **`grep` результатов**: прямые `bl 0x1d439a0b8` (peak), `0x1d439a0a8` (value), `0x1d439a0b0` (range),
   `0x1d4399720` (percentDeviation), `0x1d43967bc` (energy), `0x1d4396a1c` (valence),
   `0x1d3e81ab0` (visualTempo) — **0 совпадений** во всех `*/disassembly` corpus.

---

## 5. RETRACTED / CORRECTED

1. `01_automix/MEDIAAPI_FIELD_USAGE_MATRIX.md` §1–2: статусы `STRONG_INFERENCE` («used by planner»)
   для `bpm.main`, `beats.beatsInMilliseconds`, `beats.barsInMilliseconds`, `key.*`, `loudness.value`,
   `loudnessCurve.value`, `vocalActivity.*` **уточняются**:
   - `vocalActivity.*` и `loudnessCurve.value` — подтверждены call-level (EXACT);
   - `bpm.main`, `beats.*`, `key.*`, `loudness.value` (audio-analysis) — **не подтверждены xref**:
     getters `0x1d4396964`/`0x1d43967e0` не вызываются планировщиком (P2 §1.2), `loudness` (0x1d43966f0)
     и `Statistics` не в 41-импорте. Корректный статус звена — `UNUSED BY PLANNER (no evidence)`/
     `UNKNOWN`. Семантические догадки от лог-строк (`Matching bar count`, `Tempo relationship`)
     остаются, но без xref они **не являются** доказательством потребления полей.
2. `MEDIAAPI_FIELD_USAGE_MATRIX.md` §2: `videoEvents`/`visualTempo`/`entryPoints`/`exitPoints`
   «NOT ESTABLISHED» — уточнение: `FlexAnalysis.events` (+`time`/`timeScale`) **импортируется
   планировщиком** (P2 §4.1), обрабатывается `FUN_2722231b4`→`FUN_272225ef4` — статус `PARTIAL`
   (не NOT ESTABLISHED и не EXACT); `visualTempo`/`arousal`/`valence` остаются без потребителя.
3. `01_automix/MEDIAAPI_ANALYSIS_SCHEMA.md` §7 «Назначение `loudness.peak`…» — без изменений:
   единицы не доказаны; см. `blocked_13_15.md`.
4. `MEDIAAPI_FIELD_USAGE_MATRIX.md` §1 `bpm.percentDeviation` «возможный вход beatStabilityMap» —
   **опровергнуто** (P2 §1.3: `beatStabilityMap` — поле типа `TransitionPlanner.SongStructure`,
   стабильность считается допуском 0.031 s, не из percentDeviation).

---

## Приложение. Источники

- `indexes/SHARED_BRIEFING.md`; `10_final_closure/STATE_RECONCILIATION.md`;
  `09_appos/APPOS_MASTER_SUMMARY.md` §5; `01_automix/MEDIAAPI_*.md`;
  `08_closure/P1_MEDIAAPI_CLOSURE.md`; `08_closure/P2_MEDIADSP_CLOSURE.md`;
  `07_audit/RED_TEAM_AUDIT.md`.
- Live JSON: `evidence/MEDIAAPI_live_1776914757_sanitized.json` (1 трек, массивы усечены head/tail).
- MusicKitInternal getters: `indexes/lyrics_functions.tsv`, `lyrics/pseudocode/1d4396*.c`.
- Конверсии: `lyrics/pseudocode/1d412061c...c` (closure), `1d41218c4`, `1d4121a98`, `1d4122c04`,
  `1d4122dc0`, `1d41253ac`, `1d41222b8`, `1d3e5ef4c`.
- Планировщик: `automix/pseudocode/2722200cc`, `27222343c`, `272223b6c`, `2722231b4`, `272225ef4`,
  `272224414`, `27224241c`, `272242fd0`, `272243f80`, `27224400c`, `272231d04`, `2722341d4`,
  `272235198`, `272235840`, `272232790`, `27222d644`, `272234380`, `2722307ec`, `2722305c8`;
  `automix/disassembly/*` (call-sites островов).
- Негативные сканы: `09_appos/musicapp_ghidra/{function_index.tsv,symbols/,strings/}`;
  `indexes/animations_functions.tsv` (MusicApplication); `strings -a targets/_SonicKit_MusicKit_Packages`.
