# TRANSITION_PLANNER_CLEANROOM_SPEC — детерминированная спецификация планировщика переходов Apple Music (iOS 26)

**Дата:** 2026-09-13. **Роль:** Final Closure: TransitionPlanner Cleanroom Spec Author.
**Ограничения:** research only. `/root/LMG-VK` и код приложения не изменялись; запись только под
`deepseek_analysis/10_final_closure/`. Спецификация — clean-room: ни одна строка не является копией
исходного текста Apple; всё изложено псевдокодом и таблицами по восстановленным артефактам.
**Build identity:** AppOS `23A341__iPhone16,1`; `_SonicKit_MusicKit_Packages.framework` (CFBundleVersion
25110.25.31.601, DTSDK iphoneos26.0.internal); `TransitionStyles.json` sha256
`fe3d0a36625ccb043c519bcc5119c98190893a9911cfa58412ad60cbab04d120` (TS §0).

## 0. Как читать этот документ

1. **Статусы** (маркер в конце каждой нормативной строки):
   - `EXACT` — прямо подтверждено адресом/байтами/символом/raw-ресурсом;
   - `STRONG_INFERENCE` — следует из нескольких независимых подтверждённых артефактов;
   - `PARTIAL` — часть звеньев подтверждена, часть нет;
   - `UNKNOWN` — данных нет.
   Легенда сознательно не содержит квадратных скобок, чтобы автоматический подсчёт считал только
   нормативные строки разделов 1–14 (метод — §16).
2. **Адреса** — VM-адреса `targets/_SonicKit_MusicKit_Packages`, если не указано иное.
   Строки этого бинаря имеют сдвиг −0x20 (RT §3); oslog-строки читаются без сдвига.
3. **Сокращения источников** (пути относительно `deepseek_analysis/`):
   - `P1` = `08_closure/P1_AUTOMIX_CLOSURE.md`; `P2` = `08_closure/P2_AUTOMIX_CLOSURE.md`;
     `P3` = `08_closure/P3_AUTOMIX_CLOSURE.md`;
   - `RT` = `07_audit/RED_TEAM_AUDIT.md`; `RC` = `07_audit/RETRACTED_CLAIMS.md`;
     `VC` = `07_audit/VERIFIED_CONSTANTS.md`;
   - `Arch` = `01_automix/AUTOMIX_ARCHITECTURE.md`; `CG` = `01_automix/AUTOMIX_CANDIDATE_GENERATION.md`;
     `SC` = `01_automix/AUTOMIX_SCORING.md`; `CN` = `01_automix/AUTOMIX_CONSTANTS.md`;
     `SM` = `01_automix/AUTOMIX_STATE_MACHINE.md`; `M2T` = `01_automix/MEDIAAPI_TO_TRANSITIONPLANNER.md`;
     `FUM` = `01_automix/MEDIAAPI_FIELD_USAGE_MATRIX.md`;
   - `MC` = `10_final_closure/MUSIC_APP_AUTOMIX_CALLCHAIN.md`;
     `MAP` = `10_final_closure/MEDIAAPI_TO_PLANNER_CALLCHAIN.md`;
     `B1315` = `10_final_closure/blocked_13_15.md`; `REC` = `10_final_closure/STATE_RECONCILIATION.md`;
   - `TS` = `09_appos/APPOS_TRANSITION_STYLES.md`; `P2DSP` = `08_closure/P2_MEDIADSP_CLOSURE.md`.
4. **Принцип:** отсутствие данных не восполняется правдоподобными значениями. Где значение не доказано,
   строка помечена `[PARTIAL]`/`[UNKNOWN]`, а не «вероятным» числом. Противоречия решаются в пользу
   red-team/raw evidence (REC §0).

---

## 1. Входные данные анализа (что получает планировщик)

### 1.1. Точка входа

```
TransitionPlanner.transition(from: Song, to: Song, criterias: Criteria)
    -> Result<Transition, TransitionPlanner.FailureReason>          [EXACT]
```
`_$s015_SonicKit_MusicB9_Packages17TransitionPlannerV10transition4from2to8criterias6ResultOyAA0E0VAC13FailureReasonOGAC4SongV_ApC8CriteriaVtKF`,
символ `T` @`0x272267700`; единственный «action»-метод планировщика (P1; MC §2.A; Arch §1) **[EXACT]**.

Реальный литеральный call-site `transition(...)` в корпусе не найден: у `_SonicKit_MusicKit` есть живой
GOT-слот `0x28247ad88` → `0x272267700` и stub `0x2721d5ef0`, но ни один из 16 targets и весь доступный DSC
не содержит перехода на stub/функцию (MC §3) **[EXACT]** (негатив); привязка вызова к потоку данных —
`[UNKNOWN]`.

### 1.2. Модель входа (type metadata)

| Тип/поле | Значение | Источник | Статус |
|---|---|---|---|
| `TransitionPlanner.Song` | `{id: ID, duration: Double?, analysis: Analysis?, context: Context?}` | `Ma 0x272261280`; M2T §2 | [EXACT] |
| `Song.Analysis` (enum) | `.musicKit(MusicKitAnalysis)` / `.adaptiveMusic(AdaptiveMusicAnalysis)` | nom. desc `0x27225c004`; M2T §2 | [EXACT] |
| `Song.Context` | `previousPlaybackEndState: Transition.PlaybackState?` | getter `0x2722666d0`, init `0x2722666e4` (символы EXACT; атрибуция типа — по смыслу/SM §2) | [STRONG_INFERENCE] |
| `Song.ID` | `init(rawValue:)` @`0x272267244`, `Ma` @`0x27226722c` | MC §2.B (слоты 0x28247adc0/c8) | [EXACT] |
| `Song.MusicKitAnalysis` | `genres:[Genre]`, `duration:Double?`, `audioAnalysis:MusicKitInternal.AudioAnalysis?`, `flexAnalysis:MusicKitInternal.FlexAnalysis?`, `spatialTimingInformation:…?`, `Options` | `Ma 0x27225d7cc`, init `0x27225d900`; M2T §2 | [EXACT] |
| `MusicKitAnalysis.Options` | `default` / `applySpatialTimingInformation` (rawValue Int) | symbols `0x2722607fc/0x27226080c/0x272260818` | [EXACT] |
| Смещения полей `MusicKitAnalysis`: `audioAnalysis`@+0x18, `flexAnalysis`@+0x1c, `spatialTimingInformation`@+0x20 | код читает `ldrsw` после `Ma` | MAP §2.3; disasm `2722200cc/27222343c/272223b6c/2722231b4/272220920` | [STRONG_INFERENCE] (offset EXACT, имя↔индекс — по порядку полей) |
| `Criteria` | `outgoingPlacement`@+0x00 (0x10 B), `incomingPlacement`@+0x10 (0x11 B), `maximumTransitionComplexity`@+0x21; `allowedGenreIDs`/`deniedGenreIDs` (Arch §6) | P2 §3; init `0x27225abb8`; getter max @`0x27225abac` | [EXACT] (layout/genre-поля — PARTIAL) |
| `TransitionPlanner.Configuration` | `{transitionSchedulingPolicy}`; `.default` = raw `{0,1}` | SM §1; `0x272254890/0x2722549cc` | [EXACT] raw; case `[PARTIAL]` |
| `TransitionPlanner.SchedulingPolicy` | `.continuous(Transition.ContinuousSchedule)` / `.stepped(Double)` | symbols `…SchedulingPolicyO7stepped…/…continuous…` | [EXACT] |

### 1.3. Поля, реально потребляемые планировщиком (новый callchain-отчёт)

| Поле MediaAPI | Путь к потребителю планировщика | Статус |
|---|---|---|
| `loudnessCurve.value` | `MusicKitAnalysis.audioAnalysis.loudnessCurve` → island `0x2743dd1c0` @`0x272223d58` (`FUN_272223b6c` строит `LoudnessMap`) → `FUN_272232790`, `FUN_272235840` | потребитель [EXACT]; конверсия cloud→internal [PARTIAL] (MAP §1, §2.5) |
| `vocalActivity[]` (`start/end/strength/kind`) | `FUN_27222343c`: `vocalActivities` @`0x2722236f8`, `strength` @`0x27222388c`, `kind` @`0x272223968`; модель `VocalActivity{kind,strength}` | [EXACT] (MAP §1; P2DSP §4.1) |
| `acousticness.*` | `FUN_272243f80` ← `FUN_272242fd0` (BeatMatched styling) | потребитель [EXACT]; island↔поле [STRONG_INFERENCE] |
| `danceability.*` | `FUN_27224400c` ← `FUN_272242fd0` | потребитель [EXACT]; island↔поле [STRONG_INFERENCE] |
| `melodicness.*` | `FUN_2722341d4` ← `FUN_272231d04` (tonality fallback) | потребитель [EXACT]; island↔поле [STRONG_INFERENCE] |
| `key.{beginning,ending,main}.{tonic,mode}` | тональные аксессоры в 41-импорте; предикат `FUN_272231d04` | потребитель [EXACT]; остров→поле [PARTIAL] (DSC-пул удалён) |
| `videoEvents` (Flex `events/time/timeScale`) | import подтверждён; builder `FUN_2722231b4` (caller `FUN_2722200cc`), `FUN_272225ef4`; scoring-предикат не локализован | [PARTIAL] |
| `durationInMillis` | `Song.duration`/`MusicKitAnalysis.duration`; confidence/лимиты длительности (`FUN_272224eac`, `FUN_27222171c`) | [STRONG_INFERENCE] |
| `genres` | `GenreTree/GenreFilter`; `FUN_27223f618`/`FUN_27223edec`/`FUN_272240e68` | types [EXACT]; сравнение [STRONG_INFERENCE] |
| `spatialTimingInformation` | читается `FUN_272220920` (@+0x20), spatial-наборы complexity | offset [EXACT]; роль [STRONG_INFERENCE] |
| `supportsSmartTransitions` | gate уровнем выше планировщика (SonicKit/MPC), не `transition()` | [STRONG_INFERENCE] (MC §1) |
| `EventTimes` | внутренние `[Double]` = секунды (конверсия ms→s в MusicKitInternal `FUN_1d41218c4` ÷1000.0) | [EXACT] (B1315 §14) |

### 1.4. Поля, НЕ потребляемые планировщиком (проверенный негатив)

| Поле/getter | Проверка | Статус |
|---|---|---|
| `bpm.main` (getter `0x1d4396964`), `beats/barsInMilliseconds` (getter `0x1d43967e0`), `bpm.beginning/ending` | не входят в 41 packages-импорт и 15 engine-импортов; прямых `bl` нет | [EXACT] (P2 §1.2; MAP §4; DSC-пул удалён — повторный резолв невозможен) |
| `bpm.percentDeviation` (`0x1d4399720`) | 0 вызовов во всех targets/Music.app; конвертер `FUN_1d4121a98` не масштабирует; live=1, семантика 1 %/доля не подтверждена | consumer-negative [EXACT]; semantics [UNKNOWN] (B1315 §1) |
| `loudness.{value,range,peak}` / `Statistics` (`0x1d439a0a8/b0/b8`) | не импортируются; планировщик читает только `loudnessCurve` | consumer-negative [EXACT]; units [UNKNOWN] (B1315 §2) |
| `energy`, `valence` (audio-analysis) | нет в 41-импорте, 0 строк/порогов в packages | [EXACT] (MAP §4) |
| `fades`, `phrases` | getters `0x1d4396b38`/`0x1d4396cec` вне импортов; только MKI-обёртки без вызовов | [EXACT] (MAP §4) |
| Flex `arousal`, `valence`, `visualTempo` | нет в 41-импорте, 0 прямых `bl` | [EXACT] (MAP §4) |
| `entryPoints`/`exitPoints`/`fadeToBlack`/`gain*` | потребитель — движок SmartTransitions (`SmartTransitionSongData.transitionPivotPoint` @`0x2721afaa4`), не planner | planner [EXACT]-негатив; engine [STRONG_INFERENCE] (MAP §3) |
| Как планировщик получает `beatEvents`/`downbeatEvents`/`bars` | в planner-модели имена есть, но источника (getter/поле) нет | [UNKNOWN] (MAP §4.2) |

---

## 2. Генерация кандидатов

### 2.1. Реестр стратегий

Каждая из 4 стратегий реализует `TransitionPlannerCandidateGenerationStrategy` и
`TransitionPlannerStylingStrategy`; типы реальны (witness-реализации без Ghidra-символов) (Arch §2–3; RT §4.1)
**[EXACT]**.

| Алгоритм (complexity) | CandidateGeneration witness [8] → impl | Styling witness (req#4) → impl |
|---|---|---|
| BeatMatchedFilteredCrossFade (3) | `0x27222ea38` → `FUN_27222e528` | `0x272243f7c` → `FUN_27224241c` [EXACT] |
| DeadAirRemoval (1) | `0x27222ea54` → `FUN_27222e69c` (closure `0x27222ea58`) | `0x2722450e0` → `FUN_272244aa4` [EXACT] |
| FallbackCrossFade (0) | `0x27222ea98` → `FUN_27222e69c` (closure `0x27222ea9c`, `x3=0x272239248`) | `0x272245750` → `FUN_27224522c` [EXACT] |
| SmartCrossFade (2) | `0x27222eadc` → `FUN_27222e69c` (closure `0x27222eae0`, `x3=0x27223a4a4`) | `0x2722460a8` → `FUN_272245764` [EXACT] |
| Общий диспетчер не-BM | `FUN_27222e69c` + нормализация `FUN_272246888` | SM §7 | [STRONG_INFERENCE] |

Порядок вызова в `transition()`: полный проход каталога `DefaultStylingStrategyCatalog` (req#2 impl
`0x2722469d4` → `FUN_2722469f0`) обходит стратегии и вызывает у каждой её req#4 (P3 §2.3) **[EXACT]**.

### 2.2. Структурный путь BeatMatched

```
FUN_27222e528 (candidates)
  ├─ FUN_27222f54c  region pair map (требует SongStructure outgoing+incoming; иначе "Region pair map: None")
  ├─ FUN_27221f524 + closure FUN_2722377dc  перебор
  ├─ FUN_27222fa50  "Candidate outgoing end downbeats" / "First phrase start downbeat not identified"
  ├─ FUN_27222fd74  пары регионов (phrase/segment)
  ├─ FUN_272230274 / FUN_272230380 / FUN_2722304d8 / FUN_2722305c8  фильтры пар
  │    FUN_272230380 → FUN_27222a728 (complexity gate)
  └─ FUN_27222e800  выбор победителя
```
Рёбра вызовов и функции — по callgraph/pseudocode (CG §2) **[EXACT]**; построчная семантика шагов
(«scaled/doubled/…») установлена по лог-строкам (CG §3) **[STRONG_INFERENCE]**.

### 2.3. Региональная алгебра (водитель `FUN_2722307ec`)

Последовательность преобразований региона-пары (функция → назначение по её логам) (CG §3; Arch §4)
**[STRONG_INFERENCE]** (существование функций/вызовов — EXACT):

`FUN_2722315f8` scaled bar range; `FUN_272233100` doubled incoming; `FUN_2722335b4` halved incoming;
`FUN_27223218c` stable bar range (suffix; «Stable suffix duration zero»); `FUN_2722324d0` beats-stable log;
`FUN_272231b68` truncated pair; `FUN_272233804/272233888` truncation incoming; `FUN_272233adc` shifted pair;
`FUN_272233c88` shifted region; `FUN_272232790` non-silent outgoing region (avg loudness/fade);
`FUN_2722305c8` non-silent outgoing regions (fallback: все); `FUN_272234b0c` candidate style region pair;
`FUN_272234e14` style outgoing region; `FUN_272236920` start downbeat; `FUN_27223c3ac` truncated styling pair;
`FUN_27223c184` incoming styling region (max duration); `FUN_27223d324/27223d6cc` incoming start time;
`FUN_27223cd58` stable duration before fade out.

### 2.4. Сборка кандидата и запись

- Кандидат-запись: **0x100 байт**, score @**+0xF8** (Double) (SC §3; RT §4.1) **[EXACT]**.
- Per-style сборка: `FUN_27223e008` (stride 0x38 по стилям) → closure `0x27223738c` →
  `FUN_272234310` (копия 0x38 B style) → `FUN_272234380` (P1 §1.2) **[EXACT]**.
- ID→style: closure `0x27223736c` → `FUN_27223e92c` (маппинг 8-байтных `TransitionStyle.ID` в стили)
  (P1 §1.2) **[EXACT]**.
- `FUN_272234380` берёт region pair (`FUN_272234b0c`), пишет веса и вызывает score-агрегатор
  `FUN_27222d644` (Arch §4; P1 §2) **[EXACT]** (структура), семантика веток — §10.

### 2.5. Выбор победителя

```
winner = none; 
for c in candidates (stride 0x100):
    s = c.score(@+0xF8)
    if s <= 0.0: skip                     // отбраковка
    if winner == none or s > winner.score:  // строгое >
        winner = c
// ничьи => остаётся ранее добавленный
```
`FUN_27222e800`; копия победителя `FUN_272290d8c(...,0x100)` (SC §3; Arch §4) **[EXACT]**.

---

## 3. Генерация размещений (IncomingPlacement / OutgoingPlacement)

### 3.1. Case-таблицы (P2 §3; fieldmd + encode/from-символы)

| Тип | Cases (raw tag → payload) | CodingKeys (raw-ключи) | encode/from |
|---|---|---|---|
| `Criteria.IncomingPlacement` | 0 `early(EarlyPlacementConstraint)` | `early` | `0x272254d98`/`0x272254fc8` [EXACT] |
| `…IncomingPlacement.EarlyPlacementConstraint` | 0 `after(SongTime)`; 1 `within(Range<SongTime>)`; 2 `inSong` | `inSong`,`after`,`within` | `0x2722560a8`/`0x272256520` [EXACT] |
| `Criteria.OutgoingPlacement` | 0 `early(OutgoingPlacement.EarlyPlacementConstraint)`; 1 `late(OutgoingPlacement.LatePlacementConstraint)` | `early`,`late` | `0x2722578c4`/`0x272257c18` [EXACT] |
| `…OutgoingPlacement.EarlyPlacementConstraint` | 0 `after(SongTime)` | `after` | `0x272258d24`/`0x272258f44` [EXACT] |
| `…OutgoingPlacement.LatePlacementConstraint` | 0 `after(SongTime)`; 1 `inSong` | `inSong`,`after` | `0x272259b9c`/`0x272259eb8` [EXACT] |
| `SongTime` | `{rawValue: Double}` | — | P2 §3 #154 [EXACT] |
| `StylingPlacementPair` | `earlyToEarly`, `lateToEarly` (2 case, без payload) | — | fieldmd #12 [EXACT] (тип/имена); выбор региона по паре — [PARTIAL] |

Примечание: ранняя таблица `SM §2` («InSong/After/Early/Within» как случаи самих placement) уточнена
работой P2: случаи `InSong/After` принадлежат вложенным constraint-типам; raw JSON-ключи = имена кейсов
**[STRONG_INFERENCE]** (synthesized Codable; кастомных rawValue нет) (P2 §3).

### 3.2. Валидация (per-candidate)

```
// incoming criteria (FUN_272214200)
region.start ∈ [requiredMin, requiredMax]                 // log "Required incoming region start time range"
// outgoing criteria (FUN_272213d4c)
if placementCase == 3: region.start >= minimum            // log "Minimum outgoing region start time"
else:                  region.start ∈ [requiredMin, requiredMax]
// incoming constraints (FUN_272229758)
region.end <= maximumIncomingRegionEndTime
// outgoing constraints (FUN_27222919c)
region.start >= minimumStart и/или region.end >= minimumEnd
// агрегатор (FUN_2722290a0): "Placement constraints: Not satisfied."
```
Источник: SM §2; pseudocode `272214200/272213d4c/272229758/27222919c/2722290a0`
**[EXACT]** (код/предикаты). Семантика «placementCase == 3 = Late/End-ориентированный» —
**[PARTIAL]**; границы диапазонов вычисляются из региона и `Song.Context.previousPlaybackEndState`,
абсолютных значений в коде нет (SM §RETRACTED) **[PARTIAL]**.
Per-candidate вызов: `FUN_27222e9bc` ← `FUN_27222eb20` → `FUN_2722290a0` + `FUN_272213c20` (SM §2) **[EXACT]**.

### 3.3. Диапазоны

- `within(Range<SongTime>)` — payload вида `Range<SongTime>` (lower/upper), `SongTime.rawValue: Double`
  (P2 §3) **[EXACT]**.
- Требуемые диапазоны старта региона — производные (вычисляются на лету из региона и состояния
  предыдущего воспроизведения); числовые значения в бинаре не зафиксированы (SM §2, §RETRACTED)
  **[PARTIAL]**.

---

## 4. Требования и гейты

### 4.1. Complexity gate

```
pass1 = (maximumComplexity >= algorithmComplexity)   // context+0x78, байт
pass2 = membership(algorithmComplexity, reducedSet(outgoingSong))
     && membership(algorithmComplexity, reducedSet(incomingSong))
allowed = pass1 && pass2
```
`FUN_27222a728`: `ldrb w19,[x0]` (algorithm), `ldrb w8,[x20,#0x78]` (maximum), `cmp/b.cs`; затем
per-song membership (лог `0x2722a9ee0` / `0x2722a9c60` / `0x2722a9d40`) (P2 §2.3) **[EXACT]**.

### 4.2. Reduced-complexity наборы (не битмаски)

Пять статических массивов `[Transition.Complexity]` (1-байтовые элементы) (P2 §2.1, raw-дампы):

| Адрес | Элементы | Лог/смысл | Статус |
|---|---|---|---|
| `0x2884aa4d0` | `{0,1,2,3}` | «All transitions allowed» | [EXACT] |
| `0x2884aa4f8` | `{0,1,2}` | индекс «low» duration-confidence | [EXACT] bytes; достижимость [STRONG_INFERENCE] |
| `0x2884aa520` | `{0}` | только fallback | [EXACT] |
| `0x2884aa548` | `{0,3}` | beat-matched или fallback | [EXACT] |
| `0x2884aa570` | `{0}` | только fallback (spatial-ветка) | [EXACT] |

### 4.3. Duration confidence → набор

Таблица `PTR @0x27a974888` = `[none→0x2884aa520, low→0x2884aa4f8, high→0x2884aa4d0]` (индекс —
`TransitionComplexityConfidence`: `none=0, low=1, high=2`) (P2 §2.2) **[EXACT]**.
`FUN_272224eac(expected, actual, flag)`: при `flag&1==0`: `|expected−actual| < 2.0` → `high(2)`, иначе
`none(0)`; при `flag&1==1` → `high(2)` (P2 §2.2; константа `0x4000000000000000` @`0x2722250f4`)
**[EXACT]**; индекс `low` этим кодом не производится **[STRONG_INFERENCE]**.

### 4.4. Spatial confidence + drift 0.04

```
close = FUN_2722255d0(flags)   // 2=close match, 1=low/not close, 0=none (incomplete)
drift = FUN_272225810(drift, have) // есть данные && drift <= 0.04 -> 2; иначе 1; нет данных -> 0
if close == 0:                          set = {0}
elif close == 1 and drift == 2:         set = {0,3}
elif close == 2 and drift == 2:         set = {0,1,2,3}
else:                                   set = {0}
```
`FUN_272225204` (P2 §2.2); порог `0.04` raw `0x3fa47ae147ae147b` (P2 §2.2; `FUN_272225810`) **[EXACT]**.
Итог `FUN_272221994` = пересечение duration-набора и spatial-набора; пустое пересечение → лог
«Duration-based and spatial transition complexities do not overlap.» + пустой массив (P2 §2.4)
**[STRONG_INFERENCE]** (инференс по структуре хеш-таблицы).

### 4.5. Timing accuracy (SongIssues)

- `FUN_272221f08` возвращает `Transition.TimingAccuracy.SongIssues` (OptionSet, rawValue Int):
  `0` — нет проблем, `1` — `stereoTimeInaccurate`, `2` — `spatialTimeInaccurate` (P2 §2.4; symbols
  `0x272289d20/d28/d30`) **[EXACT]**.
- Логи: «Stereo timing inaccurate. Transition complexities reduced.» `0x2722a88d0`; «Spatial timing
  inaccurate…» `0x2722a89b0`; «None. …not subject to limitations.» `0x2722a8940` (P2 §2.4) **[EXACT]**.
- Пред-проверка `FUN_272267f4c`: 4 исхода («Songs not suitable…», «Outgoing/Incoming/Both have timing
  accuracy issues.») (SM §3) — логи **[EXACT]**; влияние на результат — **[PARTIAL]**.

### 4.6. Структурные гейты кандидата

- **Минимум совпадающих тактов = 8**: `FUN_272235fd8`: `if (7 < matchingBarCount) return 1` (SC §4.2;
  CN §1) **[EXACT]**.
- Bar-count ratio `FUN_27223550c`, matching bar count `FUN_272235d08` (при отсутствии карт — `None`)
  (SC §4.2) **[STRONG_INFERENCE]**.
- Stable suffix / beats stable: `FUN_27223218c` (лог «Stable suffix duration zero», «Beats not stable»)
  (SC §4.2) — механизм **[PARTIAL]** (числовых констант стабильности нет).
- `FUN_27222f54c` требует `SongStructure` обеих песен, иначе кандидатов нет (CG §2) **[EXACT]** (лог/ветка).

### 4.7. Музыкальность (числовые полосы; полярность по RT A3)

| Атрибут | Предикат | Правило (return TRUE) | Порог | Статус |
|---|---|---|---|---|
| acousticness | `FUN_272243f80` | `v < 0.85 \|\| 1.0 < v` | 0.85 / 1.0 | [EXACT] |
| danceability | `FUN_27224400c` | `0.3 <= v <= 1.0` | 0.3 / 1.0 | [EXACT] |
| melodicness | `FUN_2722341d4` | `v < 0.25 \|\| 1.0 < v` | 0.25 / 1.0 | [EXACT] |

Композиция — `FUN_272242fd0` («Musicality attribute compatibility: Compatible.»; «Incompatible.
Outgoing/Incoming acousticness significant / danceability insignificant») (M2T §3) — логи **[EXACT]**;
привязка outgoing/incoming — **[STRONG_INFERENCE]** (RT §4.3).

### 4.8. Жанровый гейт

`BeatMatchedFilteredCrossFadeStylingStrategy.genreFilters: [GenreFilter]`; `Genre.ID.rawValue: String`;
`GenreTree{rootGenre,mainGenreID,ancestor/descendant/significantGenreIDs}`; сравнение `FUN_27223f618`
(main/ancestors/descendants/significant vs allowed/denied) (P2 §5.1) — типы **[EXACT]**, алгоритм
сравнения **[STRONG_INFERENCE]** (по логам); таблицы ID→имя в бинарях отсутствуют **[EXACT]**-негатив.

### 4.9. Time signature

`FUN_272243340`: «Time signature compatibility…», Compatible при недоступности первой подписи
(CG §7) — существование/логи **[EXACT]**; место вызова в цепочке styling — **[PARTIAL]**.
Важно: в `FUN_272234380` вызова `FUN_272243340` нет (P1 §1.5 C1) **[EXACT]**-негатив.

### 4.10. Лимиты длительности перехода

Константы `60.0` (`0x404e000000000000`), `30.0`, `2.0`, `0.5`, `−30.0` в `FUN_2722210e0`/`FUN_27222171c`
(CN §4) — значения **[EXACT]**; точная семантика («Maximum … transition duration for long/short song»)
— **[PARTIAL]**.

### 4.11. Ранний guard

`FUN_272267e70` — ранняя проверка длительностей/темпов; при нарушении `SmartTransitionsError` tag `6`
(CN §6; Arch §1) — код/тег **[EXACT]**; состав условия **[PARTIAL]**.

---

## 5. Темповое сопоставление

### 5.1. Сравнение отношения

```
FUN_272219ff0(tol, tempoA, hasA, tempoB, hasB):
    if not hasA or not hasB: compatible (проверка пропускается)
    a = f(tempoA); b = g(tempoB)               // 60.0/bpm <-> bpm по флагам представления
    return compatible iff ABS(a - b) <= tol
```
`60.0` = `0x404e000000000000`; результат — packed enum: low byte `< 0xfc` = compatible, `0xfc` =
incompatible (CG §5; SC §4.1; CN §6) **[EXACT]**.
Особые случаи (логи): «First outgoing/incoming tempo not available» → Compatible; «Multiple outgoing
tempos» → Compatible (SC §4.1) **[EXACT]**.

### 5.2. Допуски

- Обычный режим: **0.16** (raw `0x3fc47ae147ae147b`, literal pool `0x272298f30`; загрузка `0x2722365c0`)
  [EXACT].
- Expanded-режим: **0.287** (raw `0x3fd25e353f7ced91`, pool `0x272298f38`; загрузка `0x2722365c8`;
  выбор `fcsel` `0x2722365cc`) [EXACT].
- Дублирующий гейт `FUN_272243850`: загрузки `0x272243ab8/0x272243ac0`, `fcsel` `0x272243ac4` [EXACT]
  (SC §4.1; CN §1).

### 5.3. TempoBinaryScaleFactor и TempoBinaryRatio

| Тип | Кейсы (tag → множитель/смысл) | Статус |
|---|---|---|
| `TransitionPlanner.TempoBinaryScaleFactor` | 0 `half` → 0.5 (`0x3fe0000000000000`); 1 `one` → identity; 2 `two` → 2.0 (`0x4000000000000000`) | [EXACT] (константы `FUN_27221a300`; типизация `allCases` @`0x2884aa438` = `00 01 02` — [STRONG_INFERENCE]) |
| `TransitionPlanner.TempoBinaryRatio` | 0 `oneToTwo`; 1 `oneToOne`; 2 `twoToOne` | [EXACT] (fieldmd #41) |

`FUN_27221a300` перебирает ровно 3 кейса, строит множитель и применяет `mul`/`div` (`param_4` —
«инвертировать операцию»); выбирает кейс с минимальным `|tempo_candidate − tempo_ref|`, возвращает tag;
при пустом списке — `1` (P2 §4.1) **[EXACT]** (арифметика). Потребители — `FUN_272219ff0`,
`FUN_2722315f8` (P2 §4.1) **[EXACT]**.

### 5.4. Источник темпа для планировщика

Предикаты темпа существуют (`FUN_272236498`, `FUN_272219ff0`, `FUN_272243850`), но getter `bpm.main`
`0x1d4396964` не вызывается планировщиком (§1.4); путь «JSON/модель → `tempoRatio`/`averageTempo`»
не установлен (MAP §4.2) **[UNKNOWN]**.

---

## 6. Тональность (и мелодичность как fallback)

```
FUN_272231d04: tonality relationship
    if relationship == incompatible (tag 3):
        if melodicness(outgoing) insignificant && melodicness(incoming) insignificant:
            compatible   // log "Tonalities are compatible: Tonality relationship incompatible but
                         // checking for insignificant melodicness."
        else: incompatible
    else: compatible
```
Правило и лог (SC §4.3; M2T §3) — tag/константные проверки **[EXACT]**; комбинация условия
(«обе insignificant») **[STRONG_INFERENCE]** (по логу). Tag `3` = incompatible (CN §6) **[EXACT]**.
`FUN_272231d04` вызывается из `FUN_2722307ec`; флаг тональности для ветки ID 9 передаётся через closure
context `ctx+0x38 = [x19+0x48]&1` (P1 §1.4, C1) **[EXACT]**.
Тональные аксессоры (`CompositeAttribute.main/beginning/ending` × `tonic/mode`) в 41-импорте (MAP §2.1)
**[EXACT]**; соответствие «остров → конкретное sub-поле» — **[PARTIAL]** (DSC-пул удалён).
`TonalityMap`, `Tonality` — planner-типы (M2T §3) **[EXACT]** (типы); формула tonality relationship
(совместимость тоник/ладов) — **[UNKNOWN]** (строки/ветки не восстановлены до числа).

---

## 7. Вокальные правила

### 7.1. Модель и потребление

`VocalActivity {kind, strength}`; `AudioAnalysis.vocalActivities` читается `FUN_27222343c`:
`vocalActivities` island `0x2743dd1d0` @`0x2722236f8`, `strength` @`0x27222388c`, `kind` @`0x272223968`
(MAP §1, §2.5; P2DSP §4.1) **[EXACT]**.
`kind` используется: switch 0/1/2 → enum-конверсия; кейсы `VocalKind`: `singing`, `speech`, `rapping`
(MAP §1; P2DSP §4.1; M2T §3) **[EXACT]** (строки/switch). Неизвестные значения kind обрабатываются
через enum-конвертер (порядок кейсов) **[PARTIAL]**.

### 7.2. Leading incoming vocal activity → штраф 0.75

- `FUN_272235198` — «Leading incoming vocal activity is significant: %{bool}. Leading vocal activity
  strength = %s.» (M2T §3; P2DSP §4.1) **[EXACT]** (лог/вызов).
- Значимость по `strength`; штраф: significant → **0.75** (`fmov d0,0x3fe8000000000000` @`0x272234534`),
  иначе 1.0; выбор `fcsel d11,d0,d8,ne` @`0x272234538` (и дубли `0x272234688`, `0x272234984`) (RT A5;
  CN §1; P2 §1.1) **[EXACT]**.
- Точные enum-теги `VocalActivityStrength` (наблюдались сравнения `< 3` и `!= 5`) не восстановлены
  (SC §NOT FOUND) **[PARTIAL]**.

### 7.3. Прочие вокальные факторы

- `FUN_272237058` — leading vocal time range («Last leading beat not identified») (CG §6)
  **[STRONG_INFERENCE]** (роль по логам).
- `FUN_272236188` — vocal activity relationship, бинарный фактор (лог «Vocal activity relationship:
  Incompatible. Incoming/Outgoing vocal activity map not available.») (CG §6; SC §2) — фактор
  **[STRONG_INFERENCE]**; условие совместимости **[PARTIAL]**.
- `FUN_272235840` использует и vocal map (presence guard, §8.1) **[EXACT]**.

---

## 8. Сравнение громкости

### 8.1. Формула trailing incoming loudness ratio (`FUN_272235840`)

```
region = [r0, r1]                                  // FUN_27222bc88 (out через x8/x19), проверка r0<=r1
L = mean(loudnessMap.samples ∩ region)             // FUN_272216414 + FUN_272215af4
if L nil or L >= 0: return nil
D = r1 - r0                                        // FUN_27222c9dc
T = mean(loudnessMap.samples ∩ [r1, r1+D])         // окно той же длины
if T nil or T >= 0: return nil
return L / T                                       // без clamp
```
Полная реконструкция по дизасму (`272235840:13-15,30,48,133-230`; `fdiv d0,d9,d0` @`0x272235bbc`)
(P2 §1.2) **[EXACT]**. Хелперы: `FUN_272216414` (сэмплы в окне), `FUN_272215af4` (среднее массива
double; count==0 → nil), `FUN_27222bc88` (`[start,end]` региона), `FUN_27222c9dc` (длина) (P2 §1.3)
**[EXACT]**. Clamp/числовых констант в теле нет; «ограничение» — только требование `L<0` и `T<0`
(P2 §1.4) **[EXACT]**. Оба средних — знаковые dB-подобные (предполагаются отрицательными), поэтому
`ratio > 0` (P2 §1.4) **[EXACT]**.

### 8.2. Использование ratio в кандидате

- `d8` инициализируется `1.0` (`0x272234510`); при `tag bit0 = 1` (nil) вес остаётся 1.0
  (`fcsel d8,d8,d0,ne` @`0x272234570`) (P2 §1.1) **[EXACT]**.
- Порядок записи 4 весов: `stp d10,d11,[x0,#0x20]; stp d9,d8,[x0,#0x30]` =
  **tempo, leading-vocal, bar-count ratio, trailing-loudness** (P2 §1.1) **[EXACT]** (порядок/смещения).
- Логи nil-ветвей: `0x2722aa420` (vocal map unavailable), `0x2722aa480` (loudness map unavailable),
  `0x2722aa4d0`, `0x2722aa520`, `0x2722aa580`, `0x2722aa5d0` (P2 §1.2) **[EXACT]**.

### 8.3. Прочие громкостные константы

| Константа | Где | Роль | Статус |
|---|---|---|---|
| −30.0 | `FUN_272232790` @`0x272232884`, `FUN_272238f30` @`0x272238f60` | порог тишины (`value <= -30`) | [EXACT] (значение); роль [STRONG_INFERENCE] |
| −10.0 | `FUN_272238be8` @`0x272238c0c` | окно затухания `[x-10, x]` | [EXACT] (значение); роль [PARTIAL] |
| 10.0 | `FUN_272238e38` @`0x272238e60` | `<= min(x, 10.0)` | [EXACT] (значение); роль [PARTIAL] |
| 1.0 | `FUN_272232790` @`0x2722327e8` | множитель average loudness (не-silent) | [EXACT] (значение); роль [STRONG_INFERENCE] |
| 2.0 | `FUN_272238a20` @`0x272238a4c` | `\|Δt\| >= 2.0 s` — significant | [EXACT] |

`LoudnessMap` строится из `loudnessCurve.value`/`samplingFrequency` (`FUN_272223b6c`) (MAP §1, §2.5)
**[EXACT]** (потребитель); точное окно усреднения/интерполяция `LoudnessMap` — **[PARTIAL]**.

---

## 9. Сложность (Transition.Complexity) и алгоритм

### 9.1. Complexity — ровно 4 кейса 0..3

| Rank | Case | Источник | Статус |
|---|---|---|---|
| 0 | `fallback` | field-witness symbols `0x272a29f0/f4/f8/fc`; `encode` @`0x27228ecbc` switch 0..3 | [EXACT] |
| 1 | `crossFade` | там же | [EXACT] |
| 2 | `crossFadeWithEffects` | там же | [EXACT] |
| 3 | `timeStretchedCrossFadeWithEffects` | там же | [EXACT] |
| — | rank 4 (unmatched string в `FUN_27228e824`) | sentinel, НЕ кейс; `beatMatchedFilteredCrossFade` — имя `Algorithm`/стратегии | [EXACT] (RT A1; RC A1) |

### 9.2. Algorithm — 4 кейса

`Transition.Algorithm.description` @`0x27228b36c` (RT §3 о −0x20 bias): 0 `beatMatchedFilteredCrossFade`
(«Beat-Matched Filtered Cross-Fade»), 1 `smartCrossFade` («Smart Cross-Fade»), 2 `deadAirRemoval`
(«Dead-Air Removal»), 3 `fallbackCrossFade` («Fallback Cross-Fade») (CG §1) **[EXACT]**.

### 9.3. Соответствие «алгоритм → complexity» (свойство алгоритма, не styleID)

| Алгоритм | Complexity | Байт в гейт | Адрес установки | Статус |
|---|---|---|---|---|
| FallbackCrossFade | 0 `fallback` | 0 | `0x272245264: strb wzr` | [EXACT] |
| DeadAirRemoval | 1 `crossFade` | 1 | `0x272244adc` (из `[context+0x50]==1`) | [EXACT] |
| SmartCrossFade | 2 `crossFadeWithEffects` | 2 | `0x27224579c: mov w8,#0x2` | [EXACT] |
| BeatMatchedFilteredCrossFade | 3 `timeStretchedCrossFadeWithEffects` | 3 | `0x272242458: mov w8,#0x3` | [EXACT] |

(P2 §2.3; корректировка C5: style ID 8/9/12 — не complexity-ранги.)

---

## 10. Скоринг кандидатов

### 10.1. Формула

```
score = base
for w in weights: score *= w
if score <= 0.0: return score          // кандидат отбраковывается
return param_1 * 0.001 + score         // tie-breaker
```
`FUN_27222d644`: `fmov d8,d1` (base), цикл `fmul d8,d8,d0`, `fcmp d8,#0.0`/`fcsel` (если product<=0 —
вернуть product), `ldr d0,0x3f50624dd2f1a9fc` = 0.001 @`0x27222d680`, `fmul d0,d9,d0`, `fadd`
(P1 §2.1; SC §2) **[EXACT]**.

### 10.2. Базы (param_2)

| Ветка/место | База | Адрес | Статус |
|---|---|---|---|
| `FUN_272234380` ветка ID `0x8` (4 фактора) | 10.0 | `0x2722345ec` | [EXACT] |
| `FUN_272234380` ветка ID `0x9` (6 факторов) | 15.0 | `0x27223472c` | [EXACT] (P1 C2) |
| `FUN_272234380` ветка ID `0xc` (4 фактора, expanded) | 10.0 | `0x272234a24` | [EXACT] (P1 C2) |
| DeadAir (`FUN_272237b14`) call-site `0x272238868` | 2.0 | `0x272238868` | [EXACT] |
| Fallback (`FUN_272239248`) call-site `0x272239b4c` | 1.0 | `0x272239b48` | [EXACT] |
| Scheduling `FUN_27223c6cc` call-sites `0x27223c888/9a8/a74/cb4c` | 3.0 | там же | [EXACT] |

### 10.3. Факторы по ветках `FUN_272234380` (селектор = `TransitionStyle.id`)

| ID | Факторы | Кол-во | База | Особенности |
|---|---|---|---|---|
| `0x8` | tempo; leading-vocal (0.75); bar-count ratio (`FUN_27223550c`); trailing-loudness (`FUN_272235840`) | 4 | 10.0 | region pair `FUN_272234b0c` до switch; лог «Candidate style region pair not identified.» | [EXACT] (состав/адреса); имена факторов [STRONG_INFERENCE] |
| `0x9` | tempo; тональность (`FUN_272231d04` через ctx+0x38); min-bar-count (`FUN_272235d08`+`FUN_272235fd8`); vocal relationship (`FUN_272236188`); leading-vocal (0.75); trailing-loudness | 6 | 15.0 | тот же region pair | [EXACT] (состав/адреса); имена [STRONG_INFERENCE] |
| `0xc` | `(normal ? expanded : 0)` через 2× `FUN_272236498` (w3=0/1); min-bar-count; leading-vocal; trailing-loudness | 4 | 10.0 | shifted/expanded region `FUN_272233adc(...,0x10)`; лог «Expanded tempo range …» | [EXACT] (состав/адреса); имена [STRONG_INFERENCE] |

Селектор — первое поле копии `TransitionStyle` (1-й стековый аргумент x19), а не `param_10[0]`
(P1 §1.1–1.3, C3) **[EXACT]**. Ветвление — цепочка `cmp/b.eq`, не jump table; неизвестный ID →
обнуление 0x100-байтового кандидата (`mov w0,#0x100; bl 0x2743ddaf0`) (P1 §1.1) **[EXACT]**.

### 10.4. Ограничения/отсутствие иных весов

- Все веса — константы кода; сканирование numeric_constants в диапазоне планировщика не нашло других
  весов/порогов (CN §7) **[EXACT]**-негатив.
- `product <= 0` отбраковывает кандидата; максимальный score ≈ base (10/15) (SC §2) **[EXACT]**.
- `StylingScore` = `struct {rawValue: Double}`; `Comparable` + `RawRepresentable<Double>`; шкала —
  знаковый Double, `<= 0` отбрасывается, «больше = лучше»; фиксированных границ 0..10/0..15 в бинаре
  НЕТ (P3 §2.6; RC A-ретракция) — тип **[EXACT]**, границы **[UNKNOWN]**.
- Выбор лучшего StylingResult: `FUN_27223e4f0` — `fcmp #0.0; b.le` (отбросить), затем «max wins»
  (P3 §2.6) **[STRONG_INFERENCE]**.

---

## 11. Выбор стиля (каталог TransitionStyles)

### 11.1. Каталог

- Ресурс: `appos/extracted/System/Library/PrivateFrameworks/_SonicKit_MusicKit_Packages.framework/TransitionStyles.json`,
  55905 B, sha256 `fe3d0a36…d120`; корень — **массив 14 объектов** (TS §0–1) **[EXACT]**.
- IDs: `0,1,2,3,4,6,7,8,9,10,11,12,33,44`; 74 автоматики (out 46/in 28) (TS §2) **[EXACT]**.
- IDs 8 → name «BM - Filter high to low» (duration 8), 9 → «BM - Filter expansion» (8),
  12 → «BM - Long filter high to low» (16) (TS §2) **[EXACT]**.

### 11.2. Loader

`TransitionPlanner.init(configuration:)` @`0x2722673e4` → `FUN_27224e480` с name `"TransitionStyles"`
(`0x272291600`, 16 символов), ext `"json"` (small string `0x736a/0x6e6f`), класс-локатор
`…SonicKit_MusicKit_Packages_Locator`; при отсутствии — лог `0x2722ad310` и `SmartTransitionsError`
(P1 §3.1) — name/ext/лог **[EXACT]**; `Bundle(for:)`-паттерн **[STRONG_INFERENCE]**.

### 11.3. ID → кандидат

- `FUN_272234380` обслуживает только ID `8/9/0xc`; остальные ID → zero-fill кандидата (P1 §1.1, §1.4;
  TS §5) **[EXACT]**.
- Все три ID принадлежат BeatMatched-стратегии (цепочка
  `FUN_27222e528→0x27223e174→…→FUN_2722307ec→FUN_272234380`) (P1 §1.3) **[EXACT]**.
- Привязка не-BM стилей (0..4,33,44) к алгоритмам Fallback/DeadAir/Smart в коде не найдена
  (TS §5, §7) **[UNKNOWN]**.
- Стили 6/7/10/11 носят BM-имена, но их ID нигде не сравниваются → в iOS 26 BM-путём не выбираются
  (TS §5) **[STRONG_INFERENCE]**.

### 11.4. Схема ресурса и соответствия

- Ключи: `id`, `name`, `offset{relative,offsetInSeconds?}`, `duration?`, `instructions{outgoing,incoming}`,
  `placement{start,end}`, `automations[{parameterId,startTime{default},endTime{default},startValue,
  endValue,interpolation}]`; `parameterName="beat_length"` встречается ровно 2 раза (style 10) (TS §1)
  **[EXACT]**.
- P1-гипотеза «raw-ключ = case-имя» скорректирована: фактические ключи `parameterId`, `interpolation`,
  `relative`, `offsetInSeconds`, `default`, `parameterName`, `offset`, `duration`, `outgoing/incoming`,
  `placement`, `automations` (TS §1.1) — raw-ключи **[EXACT]**; поле↔ключ **[STRONG_INFERENCE]**.
- `duration` 8/8/16 у ID 8/9/12 вероятно = `maximumBarCount` (TS §5) **[STRONG_INFERENCE]**.
- Кривые: `linear`(44), `ease-out-2`(11), `ease-out-0.5`(9), `ease-in-0.5`(5), `ease-in-4`(4),
  `ease-out-4`(1); `logarithmic` не используется (TS §4) — строки **[EXACT]**, отображение в
  curveByte-классы **[STRONG_INFERENCE]**.

---

## 12. Tie-breaking

### 12.1. Формула (EXACT)

- На всех 8 call-site `FUN_27222d644` значение `param_1` (d0) = возврат `FUN_27222d51c`; `param_2` (d1) —
  база (10.0/15.0/2.0/1.0/3.0) (P1 §2.2) **[EXACT]**.
- `FUN_27222d51c`: `flag==0` → вернуть `0.0`; иначе `convert(region-half B)` →
  `A = double@+8`, `convert(region-half A)` → `B = double@+8`, вернуть `B − A` (арифметика
  `fsub d0,d0,d8` @`0x27222d628`) (P1 §2.3) **[EXACT]**.
- Инкремент: `score = base × Π(weights); if product > 0: score += param_1 × 0.001` (P1 §2.1)
  **[EXACT]**; 0.001 raw `0x3f50624dd2f1a9fc`.

### 12.2. Что известно/неизвестно после задачи #2

| Вопрос | Ответ | Статус |
|---|---|---|
| Это индекс кандидата / timestamp? | Нет: это разность Double-поля (+8) двух половин конвертированного 0x52-байтного StylingRegion пары | [EXACT] (отрицание) |
| Точное имя поля | не восстановлено: witness-вызовы `0x27222d554`→таблица `0x2884ad418` (слоты `0x1801d78b0/0x1801d79d0` вне корпуса) и `FUN_2722113c8`→таблица `0x2884ad1f8` | [UNKNOWN] |
| Семантика | малое слагаемое-tie-breaker: при равном product выигрывает кандидат с большим `param_1`; «time-delta»-единицы не установлены | [PARTIAL] |
| Половины региона | `FUN_272234b0c`: outgoing @+0x00 (0x51 B), incoming @+0x58 (0x51 B), tag @+0xa9 | [EXACT] |

Ничьи при равном `product` и равном `param_1` разрешаются в пользу ранее добавленного кандидата
(`FUN_27222e800`, строгое `>`) (SC §3) **[EXACT]**.

---

## 13. Fallback

### 13.1. Кандидат FallbackCrossFade

- `FUN_272239248` (Fallback candidate generation; вызов через closure `0x27222ea9c`) **[EXACT]**.
- База score **1.0** (`0x3ff0000000000000` @`0x272239b48`): `score = 1.0 + param_1*0.001`; запись
  кандидата 0x120 B, score @+0x118 (`0x272239bdc`) (P1 §4) **[EXACT]**.
- Планировочная длительность **2.0 s** (`0x4000000000000000`): порог `outgoingDuration >= 2.0` и
  `incomingDuration >= 2.0` (иначе кандидата нет; логи «…less than Fallback Cross-Fade duration»);
  регион строится на фиксированном 2-секундном окне; значение 2.0 печатается в логе (`0x2722396a8`)
  (P1 §4) **[EXACT]**.
- `−2.0` (`0x2722395e0`) — сдвиг `outgoing − 2.0` при построении окна (P1 §4) — константа/инструкция
  **[EXACT]**, полная роль **[PARTIAL]**.

### 13.2. Каталог и fallback-перебор

- Стиля с именем «Fallback» в каталоге нет; 2.0 s в каталоге встречается только как placement-offset
  стилей 4/44/33 (TS §6) **[EXACT]**-негатив; связь «fallback → style 4» кодом не доказана
  **[INFERRED]** (не использовать как факт).
- `DefaultStylingStrategyCatalog` (`FUN_2722469f0`) перебирает алгоритмы; при неуспехе всех —
  «All algorithms: Styling result not identified.» → пустой результат → в `transition()` лог
  «Transition Planner: No transition.» → `Result.failure` (Arch §1; SM §7) — логи/вызовы **[EXACT]**;
  порядок перебора **[STRONG_INFERENCE]**.
- Нормализация не-BM стратегий: `FUN_272246888` ← `FUN_27222e69c` (SM §7) **[STRONG_INFERENCE]**.

### 13.3. Внешний downgrade

`FUN_2721b3e60` (`_SonicKit_MusicKit`): «[ALC] - Transition complexity downgraded to .fallback due to
both items not being subscription items» / «…downgrade overridden» (MC §2.D) — логи **[EXACT]**,
семантика **[STRONG_INFERENCE]**.

---

## 14. Финальная структура Transition

### 14.1. Transition

- `Transition` layout — **0x80 B** (`_type_layout_string` @`0x2722a2710`) (RT A4/VC) **[EXACT]**.
- Поля: `strategy`, `summary`, `schedule`, `dspGraph`, … (Arch §6) — набор полей **[PARTIAL]**
  (полный layout пополево не перепроверен).

### 14.2. Transition.Summary — layout 0x50 B и сборка

- Размер **0x50 B** (`_type_layout_string` @`0x2722a2570`) (RT A6/VC) **[EXACT]**.
- Сборка `FUN_27226838c` (вызывается из `transition()` @`0x272267700`) пишет по смещениям
  (pseudocode `27226838c`):
  `+0x00/+0x08` — `strategy` (16 B: tag + payload), `+0x10/+0x18` — outgoing `Range<SongTime>`,
  `+0x20/+0x28` — incoming `Range<SongTime>`, `+0x30/+0x38` — `musicalCompatibility` (2 qword),
  `+0x40/+0x48` — `timingAccuracy` (2 qword) **[EXACT]** (смещения/запись).
  Соответствие «смещение ↔ имя» — по getter-символам (ниже) **[STRONG_INFERENCE]**.
- Getter-символы: `strategy` @`0x27228c3c8`, `outgoingSongTimeRange` @`0x27228c3dc`,
  `incomingSongTimeRange` @`0x27228c3e4`, `musicalCompatibility` @`0x27228c3ec`,
  `timingAccuracy` @`0x27228c3f4` (extracted symbols) **[EXACT]**.
- Диапазоны имеют тип `Range<SongTime>` (манглинг `SNyAA0hI0VG`), `SongTime {rawValue: Double}`
  (P2 §3 #154) **[EXACT]**.
- **Caveat:** в одной ветке `FUN_27226838c` вызывает `FUN_272290d8c(...,0x51)` — копия 0x51 при layout
  0x50 (RT A6) **[EXACT]** (расхождение не объяснено).
- Лог `transition()`: «Transition Planner: Transition summary = %s.» (Arch §1; `Summary.description`
  @`0x27228d034`) **[EXACT]**.
- `FUN_2722685b4` — упаковка Summary + песен; `FUN_272269220` — установка `*(result+0x1d8)=0`
  («no follow-up») (Arch §1) **[STRONG_INFERENCE]**.

### 14.3. MusicalCompatibility

`Transition.MusicalCompatibility` — getters `outgoingSongIssues` @`0x272288494`, `incomingSongIssues`
@`0x27228849c`, `songIssues` @`0x2722884a4`; `SongIssues` — OptionSet (`rawValue: Int`)
@`0x272288d8c/d94` **[EXACT]** (тип/поля). Конкретные биты `MusicalCompatibility.SongIssues` не
перечислены **[UNKNOWN]**.

### 14.4. TimingAccuracy

`Transition.TimingAccuracy.SongIssues` — OptionSet (`rawValue: Int`): `stereoTimeInaccurate` @`0x272289d20`,
`spatialTimeInaccurate` @`0x272289d28`, `rawValue` @`0x272289d18/d30`; raw-значения `1` (stereo),
`2` (spatial) (P2 §2.4) **[EXACT]**; `description` @`0x272289ec4` **[EXACT]**.

### 14.5. FailureReason

Ровно 2 кейса: `musicalCompatibility(Transition.MusicalCompatibility)` и
`timingAccuracy(Transition.TimingAccuracy)`; конструкторы `0x27229c7fc/0x27229c800`, metadata
`0x27225b96c`; `Error` (Mc `0x272229c7ac`) (SM §5; RT §4.1) **[EXACT]**.
Пустой результат стратегии → «Transition Planner: No transition.» → failure-запись размером
0x1d9 B (`FUN_272290d8c`) (SM §5) **[EXACT]** (лог/вызов).

---

## 15. Проверяемость (для EXACT-пунктов — одна воспроизводимая проверка)

**Рабочие каталоги:** пути `automix/…`, `targets/…` — от
`/srv/research/apple-music-ios26/decompiled_package/`; пути `07_audit/…`, `08_closure/…`,
`10_final_closure/…` — от `/srv/research/apple-music-ios26/deepseek_analysis/`; `../appos/…` — от
`decompiled_package/`. `rg` = ripgrep 14; в этом окружении LLVM 19 доступен как `llvm-nm-19` /
`llvm-objdump-19` (короткие имена `llvm-nm`/`llvm-objdump` в PATH отсутствуют).
Команды с `rdvm.py` — read-only дамп VM→raw Mach-O (скрипт: `/tmp/opencode/p1_closer/rdvm.py`).
Все команды проверены на текущем корпусе; номера строк — наблюдаемые на момент проверки.

| # | EXACT-пункт | Проверка |
|---|---|---|
| 1 | Entry point `0x272267700` | `llvm-nm-19 targets/_SonicKit_MusicKit_Packages \| rg 'transition4from2to8criterias'` → `0000000272267700 T …transition…KF` (в `automix/symbols/…symbols.txt` — строка 3107) |
| 2 | Строки «No transition.» / «Transition summary» | `rg -n 'Transition Planner: (No transition\|Transition summary)' automix/pseudocode/272267700__*.c` → строки 234/270 |
| 3 | Style-селектор `cmp x8,#0x8/9/0xc` | `rg -n 'cmp x8,#0x(8\|9\|c)' automix/disassembly/272234380__FUN_272234380.asm` → строки 90/92/94 |
| 4 | Селектор — x19 (1-й стековый аргумент) | `rg -n 'ldp x19,x24' automix/disassembly/272234380__FUN_272234380.asm` → строка 30 |
| 5 | Копия 0x38 B style | `rg -n 'ldp q0,q1\|ldr q0' automix/disassembly/272234310__FUN_272234310.asm` → строки 13/15 |
| 6 | База 10.0 (ID8 и 0xc) | `rg -n 'fmov d1,0x4024000000000000' automix/disassembly/272234380__FUN_272234380.asm` → строки 160, 430 |
| 7 | База 15.0 (ID9) | `rg -n 'fmov d1,0x402e000000000000' automix/disassembly/272234380__FUN_272234380.asm` → строка 240 |
| 8 | Штраф 0.75 + fcsel | `rg -n '272234534\|272234538' automix/disassembly/272234380__FUN_272234380.asm` → строки 114/115 |
| 9 | 0.001 tie-breaker | `rg -n 'ldr d0,\[x8, #0xc98\]' automix/disassembly/27222d644__FUN_27222d644.asm` → строка 20; `python3 /tmp/opencode/p1_closer/rdvm.py 0x272298c98 8` → `fca9f1d24d62503f` = 0.001 |
| 10 | Tolerances 0.16/0.287 | `rg -n 'ldr d0,\[x9, #0xf30\]\|ldr d1,\[x9, #0xf38\]\|fcsel d0,d1,d0,ne' automix/disassembly/272236498__FUN_272236498.asm` → строки 78/80/82; `python3 /tmp/opencode/p1_closer/rdvm.py 0x272298f30 16` → `7b14ae47e17ac43f91ed7c3f355ed23f` |
| 11 | Выбор допуска `fcsel` | `rg -n 'fcsel d0,d1,d0,ne' automix/disassembly/272236498__FUN_272236498.asm` → строка 82 |
| 12 | 60/bpm конверсия | `rg -n '0x404e000000000000' automix/constants/numeric_constants.tsv` → строки 5277/5571/5705/… |
| 13 | Tempo 0xfc несовместимость | `rg -n 'mov w19,#0xfc' automix/disassembly/272219ff0__FUN_272219ff0.asm` → строка 48 |
| 14 | TempoBinaryScaleFactor множители | `rg -n 'fmov d0,0x4000000000000000\|fmov d0,0x3fe0000000000000' automix/disassembly/27221a300__FUN_27221a300.asm` → строки 38/44 |
| 15 | Минимум 8 тактов | `rg -n '7 <' automix/pseudocode/272235fd8__*.c` → строка 27 |
| 16 | Drift 0.04 | `rg -n 'ldr d0,\[x8, #0x4e8\]' automix/disassembly/272225810__FUN_272225810.asm` → строка 92; `python3 /tmp/opencode/p1_closer/rdvm.py 0x2722984e8 8` → `7b14ae47e17aa43f` = 0.04 |
| 17 | Reduced-set массивы | `python3 /tmp/opencode/p1_closer/rdvm.py 0x2884aa4d0 48` → count=4 (0x…e0), элементы `00 01 02 03` @0x…f0; аналогично `0x2884aa4f8` → `00 01 02`, `0x2884aa548` → `00 03` |
| 18 | PTR-таблица confidence | `python3 /tmp/opencode/p1_closer/rdvm.py 0x27a974888 24` → `20a54a88… f8a44a88… d0a44a88…` = `[0x2884aa520, 0x2884aa4f8, 0x2884aa4d0]` |
| 19 | Complexity ровно 4 кейса | `rg -n 'cmp x16,#0x3' automix/disassembly/27228ecbc__*.asm` → строка 185 (encode Complexity) |
| 20 | Algorithm description | `rg -n 'Beat-Matched Filtered Cross-Fade\|Smart Cross-Fade\|Dead-Air Removal\|Fallback Cross-Fade' automix/pseudocode/27228b36c__*.c` → 3 совпадения |
| 21 | Байты complexity алгоритмов | `rg -n 'strb wzr,\[sp, #0x50\]' automix/disassembly/27224522c__*.asm; rg -n 'mov w8,#0x2' automix/disassembly/272245764__*.asm; rg -n 'mov w8,#0x3' automix/disassembly/27224241c__*.asm` |
| 22 | Fallback 2.0 s | `rg -n 'fmov d0,0x4000000000000000' automix/disassembly/272239248__FUN_272239248.asm` → строка 174 |
| 23 | Fallback база 1.0 | `rg -n 'fmov d1,0x3ff0000000000000' automix/disassembly/272239248__FUN_272239248.asm` → строка 581 |
| 24 | Loudness ratio fdiv | `rg -n 'fdiv d0,d9,d0' automix/disassembly/272235840__FUN_272235840.asm` |
| 25 | Nil→вес 1.0 | `rg -n 'fcsel d8,d8,d0,ne' automix/disassembly/272234380__FUN_272234380.asm` → строка 129 |
| 26 | Acousticness 0.85 | `rg -n '0.85' automix/pseudocode/272243f80__*.c` → строка 32 |
| 27 | Danceability 0.3 | `rg -n '0.3' automix/pseudocode/27224400c__*.c` → строка 32 |
| 28 | Melodicness 0.25 | `rg -n '0x3fd0000000000000' automix/disassembly/2722341d4__*.asm` → строка 65 |
| 29 | Winner max>0 | `rg -n 'local_60 <= 0.0\|local_160 <= local_60' automix/pseudocode/27222e800__*.c` → строки 38/74 |
| 30 | Candidate score @+0xF8 | `rg -n '0xf8' automix/pseudocode/27222e0ec__*.c` → строка 65 |
| 31 | Импорт planner-API (GOT) | `rg -n 'f2267700' 10_final_closure/MUSIC_APP_AUTOMIX_CALLCHAIN.md` (raw `80 14 00 00 f2 26 77 00`) |
| 32 | TransitionStyles.json + sha256 | `sha256sum ../appos/extracted/System/Library/PrivateFrameworks/_SonicKit_MusicKit_Packages.framework/TransitionStyles.json` → `fe3d0a36…d120` |
| 33 | 14 стилей / IDs | `python3 -c "import json;d=json.load(open('../appos/extracted/System/Library/PrivateFrameworks/_SonicKit_MusicKit_Packages.framework/TransitionStyles.json'));print(len(d),[x['id'] for x in d])"` → `14 [0,1,2,3,4,6,7,8,9,10,11,12,33,44]` |
| 34 | duration 8/8/16 | `rg -n '\"duration\"' ../appos/extracted/System/Library/PrivateFrameworks/_SonicKit_MusicKit_Packages.framework/TransitionStyles.json` |
| 35 | Summary 0x50 layout | `rg -n 'Summary' 07_audit/VERIFIED_CONSTANTS.md` (layout @`0x2722a2570`) |
| 36 | Summary getters/offsets | `rg -n 'TransitionV7SummaryV' automix/symbols/_SonicKit_MusicKit_Packages_extracted_symbols.tsv` → 10 совпадений |
| 37 | TimingAccuracy raw 1/2 | `rg -n 'stereoF10Inaccurate\|spatialF10Inaccurate' automix/symbols/_SonicKit_MusicKit_Packages_extracted_symbols.tsv` → 2 совпадения |
| 38 | FailureReason 2 кейса | `rg -n 'FailureReasonO(20musicalCompatibility\|14timingAccuracy)' automix/symbols/_SonicKit_MusicKit_Packages_symbols.txt` → строки 5758/5759 |
| 39 | Placement encode/from | `rg -n 'IncomingPlacementO6encode\|IncomingPlacementO05EarlyI10ConstraintO6encode' automix/symbols/_SonicKit_MusicKit_Packages_symbols.txt` → строки 2312/2379 |
| 40 | Vocal kind/strength islands | `rg -n '272223968\|27222388c' 10_final_closure/MEDIAAPI_TO_PLANNER_CALLCHAIN.md` → 3 совпадения |
| 41 | Поля-негативы (bpm/beats) | `strings -a targets/_SonicKit_MusicKit_Packages \| rg -c --include-zero 'BeatsPerMinute\|EventTimes\|Statistics'` → `0` |
| 42 | Неизвестный style-ID ветка | `rg -n 'bl 0x2743ddaf0' automix/disassembly/272234380__FUN_272234380.asm` → строка 338 (zero-fill кандидата) |

---

## 16. Сводный счётчик статусов (разделы 1–14)

Подсчёт — только по нормативным строкам разделов 1–14 (секции 0, 15–18 исключены):

```bash
awk '/^## 1\. /{f=1} /^## 15\./{f=0} f{print}' 10_final_closure/TRANSITION_PLANNER_CLEANROOM_SPEC.md > /tmp/s14.md
grep -cE '\[EXACT\]' /tmp/s14.md            # 163
grep -cE '\[STRONG_INFERENCE\]' /tmp/s14.md # 40
grep -cE '\[PARTIAL\]' /tmp/s14.md          # 23
grep -cE '\[UNKNOWN\]' /tmp/s14.md          # 10
grep -cE '\[(EXACT|STRONG_INFERENCE|PARTIAL|UNKNOWN)\]' /tmp/s14.md # 202
```

| Статус | Нормативных строк |
|---|---|
| `EXACT` | 163 (164 маркера: одна строка содержит 2) |
| `STRONG_INFERENCE` | 40 |
| `PARTIAL` | 23 |
| `UNKNOWN` | 10 |
| Строк с хотя бы одним маркером | **202** (33 строки несут `EXACT` + второй маркер) |

Соответствие ключевым требованиям мандата: complexity — 4 кейса 0..3 (`EXACT`); drift 0.04 (`EXACT`);
min bars 8 (`EXACT`); tempo tolerance 0.16/0.287 (`EXACT`); vocal penalty 0.75 (`EXACT`); loudness ratio
(`EXACT`); базы 10/15 (`EXACT`); fallback 2.0 s и база 1.0 (`EXACT`); style IDs 8/9/12 (`EXACT`).

---

## 17. Сводка RETRACTED / CORRECTED, учтённых в спеке

| # | Что было | Как в спеке | Источник |
|---|---|---|---|
| R1 | Complexity — лестница 0..4 с `beatMatched=4` | ровно 4 кейса 0..3; rank 4 — sentinel | RT A1 / RC A1 |
| R2 | `8/9/12` ↔ complexity rank 1/2/3 | complexity — свойство алгоритма; 8/9/12 — три стиля одной BM-стратегии | P2 C5 |
| R3 | 15.0 — ветка «expanded tempo» | 15.0 — ветка ID 9; expanded (0xc) = 10.0 | P1 C2 |
| R4 | Ветка 9: «time signature (`param_8&1`)» | флаг = тональность (`FUN_272231d04`), ctx+0x38 | P1 C1 |
| R5 | Селектор `param_10[0]` | 1-й стековый аргумент x19 = копия `TransitionStyle`, поле `id` | P1 C3 |
| R6 | fallback duration — параметр стиля | 2.0 s жёстко в коде | P1 C4 |
| R7 | reduced-complexity — битсеты | массивы `[Transition.Complexity]`; биты — у `TimingAccuracy.SongIssues` | P2 C6 |
| R8 | `TempoBinaryScaleFactor{oneToTwo/oneToOne/twoToOne/half}` | `TempoBinaryRatio` = oneToTwo/…; `ScaleFactor` = half/one/two | P2 C7 |
| R9 | `StylingScore` — шкала 0..10/0..15 | `{rawValue: Double}`, границ в бинаре нет | P3 §2.6 |
| R10 | witness +0x18 = `StylingStrategy.style` | +0x18 = req#2 каталога; пер-стратегийный style = req#4 @+0x28 | P3 §2.3 |
| R11 | caller `transition()` в DSC | DSC-вызовов нет; живой импорт только в `_SonicKit_MusicKit`, call-site не найден | MC §3 |
| R12 | Predicates «все significant = внутри полосы» | полярность разная: acousticness/melodicness TRUE вне полосы; danceability TRUE внутри | RT A3 |
| R13 | `plusL`-формула как факт | не относится к планировщику; в этой спеке не используется | RC A2 |
| R14 | «MediaAPI-поле есть ⇒ планировщик читает» | введён статус consumer-negative; не потребляемые поля перечислены в §1.4 | MAP §5 |

---

## 18. Оставшиеся неизвестные (не заполнять догадками)

1. Литеральный call-site `TransitionPlanner.transition(...)` и связывание `Song.Analysis` полей на
   вызове — [UNKNOWN] (§1.1; MC §4).
2. Точное имя Double-поля (+8) StylingRegion для tie-breaker (`FUN_27222d51c`) — [UNKNOWN] (§12.2).
3. Источник planner-полей `beatEvents`/`downbeatEvents`/`bars`/`beatStabilityMap` (bpm/beats getters
   не вызываются) — [UNKNOWN] (§1.4, §5.4).
4. Формула tonality relationship (совместимость тоник/ладов) — [UNKNOWN] (§6).
5. Точные enum-теги `VocalActivityStrength` и порог «significant» — [PARTIAL] (§7.2).
6. Битовая семантика `MusicalCompatibility.SongIssues` — [UNKNOWN] (§14.3).
7. Привязка не-BM стилей каталога (0..4,33,44) к алгоритмам Fallback/DeadAir/Smart — [UNKNOWN] (§11.3).
8. Алгоритм построения `LoudnessMap` из `loudnessCurve` (окно/интерполяция) — [PARTIAL] (§8.3).
9. Семантика `bpm.percentDeviation` и единицы `loudness.peak` — [UNKNOWN]/BLOCKED (B1315 §1–2).
10. Полный пополевой layout `Transition` (кроме Summary) и `Transition.Strategy` — [PARTIAL] (§14.1).
