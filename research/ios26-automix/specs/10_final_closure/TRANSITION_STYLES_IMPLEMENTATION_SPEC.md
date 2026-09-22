# TRANSITION_STYLES_IMPLEMENTATION_SPEC.md — строгая спецификация каталога `TransitionStyles.json` (iOS 26.0)

**Subagent:** Final Closure: TransitionStyles Spec Author. **Дата:** 2026-09-13.
**Constraint compliance:** research only; `/root/LMG-VK` и код приложения не изменялись; запись только под `deepseek_analysis/10_final_closure/`.
**Источник:** `appos/extracted/System/Library/PrivateFrameworks/_SonicKit_MusicKit_Packages.framework/TransitionStyles.json`
**sha256:** `fe3d0a36625ccb043c519bcc5119c98190893a9911cfa58412ad60cbab04d120`; **size:** 55905 B. **Extraction date:** 2026-09-13.

Все числа в таблицах сгенерированы из raw JSON (machine extraction, скрипт-генератор в `/tmp/opencode`),
без «достраивания». Интерпретации вынесены в поля `semantics`/`status`. Machine-readable: `transition_styles_normalized.json` рядом с этим файлом.

---

## 0. Как каталог попадает в планировщик (provenance, EXACT)

| Что | Доказательство | Статус |
|---|---|---|
| Loader каталога | `FUN_27224e480`, вызван из `TransitionPlanner.init(configuration:)` (`0x2722673e4`, call site `0x272267594`); имя ресурса `TransitionStyles` (16 симв.) @`0x272291600`, ext `json`; `Bundle(for: _SonicKit_MusicKit_Packages_Locator)` | EXACT |
| Корень ресурса | JSON-массив `[TransitionStyle]`, 14 объектов | EXACT |
| ID → стиль | `FUN_27223e92c` — единственный caller `FUN_2722307ec` (call `0x27223119c`) = BeatMatched candidate driver | EXACT |
| Итерация по стилям | `FUN_27223e008`, stride `0x38`; вызывается из `FUN_2722307ec` (call `0x2722312f0`) через closure `0x27223738c → FUN_272234310 → FUN_272234380` | EXACT |
| Dispatch по `style.id` | `FUN_272234380` @`0x2722344d0-0x2722344e8`: `cmp x8,#0xc / #0x9 / #0x8`; иначе `b.ne 0x2722348a4` → `memset(candidate,0,0x100)` | EXACT |
| Отсутствие ресурса | `SmartTransitionsError` tag `0xd`; лог «Transition style catalog JSON resource missing…» | STRONG_INFERENCE |

Схема `TransitionStyle` по Swift-метаданным (P1 §3.2): `{id, startTime, maximumBarCount: Int?, outgoingSchedule, incomingSchedule}`;
raw-ключи ресурса отличаются (`id/name/offset/duration/instructions`); соответствие raw-ключ ↔ Swift-поле — STRONG_INFERENCE, значения — EXACT.

---

## 1. Единицы и общие правила

- **Время (relative).** `relative` — нормализованное время `0..1` внутри перехода. Дополнительный `offsetInSeconds` — секунды. Абсолютных времён в ресурсе нет.
- **Абсолютные/относительные значения.** `startValue`/`endValue` — абсолютные значения параметра (не дельты). `parameterName` (только `beat_length`, style 10) — runtime-резолв.
- **Placement.** `instructions.<side>[i].placement.{start,end}` — окно применения инструкции в тех же `{relative, offsetInSeconds}`. Окно рампы задаётся отдельно (`startTime`/`endTime`).
- **Interpolation.** Имя кривой → `curveByte` → формула из `FUN_272275f84` (см. §7).
- **Units** — по `parameterId`; таблица в §8. `EXACT` — семантика API/диапазон из бинаря; `STRONG_INFERENCE` — семантика публичных AU-заголовков.

---

## 2. Сводная таблица 14 стилей

| id | name | offset | duration | out inst | in inst | out autom | in autom | reachability | статус |
|---|---|---|---|---|---|---|---|---|---|
| 0 | Gapless | rel 0, +0 s | — | 1 | 1 | 1 | 1 | NO CONSUMER FOUND | PARTIAL |
| 1 | constant-power cross-fade | rel 0 | — | 1 | 1 | 1 | 1 | NO CONSUMER FOUND | PARTIAL |
| 2 | constant-power long fade-out short fade-in | rel 1, -0.3 s | — | 1 | 1 | 1 | 1 | NO CONSUMER FOUND | PARTIAL |
| 3 | overlap | — | — | 0 | 0 | 0 | 0 | NO CONSUMER FOUND | PARTIAL |
| 4 | Ease-in Ring-out | rel 0 | — | 0 | 1 | 0 | 1 | NO CONSUMER FOUND | PARTIAL |
| 6 | Beat-matched long fade-out short fade-in - Same Bar num | rel 0 | — | 2 | 2 | 2 | 2 | LEGACY / UNREACHABLE | STRONG_INFERENCE |
| 7 | Beat-matched long fade-out dynamic fade-in | rel 0 | — | 2 | 2 | 2 | 2 | LEGACY / UNREACHABLE | STRONG_INFERENCE |
| 8 | BM - Filter high to low | rel 0 | 8 | 3 | 4 | 3 | 4 | REACHABLE (BeatMatched) | EXACT |
| 9 | BM - Filter expansion | rel 0 | 8 | 4 | 4 | 4 | 4 | REACHABLE (BeatMatched) | EXACT |
| 10 | BM - Filter long out short in + delay | rel 0 | — | 4 | 3 | 7 | 3 | LEGACY / UNREACHABLE | STRONG_INFERENCE |
| 11 | BM - Filter long out short in + Reverb | rel 0 | — | 4 | 4 | 10 | 4 | LEGACY / UNREACHABLE | STRONG_INFERENCE |
| 12 | BM - Long filter high to low | rel 0 | 16 | 3 | 4 | 3 | 4 | REACHABLE (BeatMatched) | EXACT |
| 33 | overlap + Reverb | — | — | 1 | 0 | 6 | 0 | NO CONSUMER FOUND | PARTIAL |
| 44 | Ease-in Ring-out + Reverb | rel 0 | — | 1 | 1 | 6 | 1 | NO CONSUMER FOUND | PARTIAL |

Итого: 14 стилей, 55 инструкций, 74 автоматики. Отсутствующие id (5, 13–32, 34–43) — разрежённый каталог, не потеря данных.

### 2.1. Reachability — вывод и доказательства

- **REACHABLE [EXACT]: id 8, 9, 12.** Все три — `BeatMatchedFilteredCrossFade`; единственные ветки `FUN_272234380`; lookup стилей (`FUN_27223e92c`) вызывается только из BeatMatched-драйвера `FUN_2722307ec`.
- **LEGACY/UNREACHABLE [STRONG_INFERENCE]: id 6, 7, 10, 11.** Носят имена Beat-matched/BM, но их id не сравнивается: в `FUN_272234380` они попадают в `b.ne 0x2722348a4` → zero-fill кандидата; других dispatch по style-id в корпусе не найдено.
- **NO CONSUMER FOUND [PARTIAL]: id 0, 1, 2, 3, 4, 33, 44.** Dispatch/lookup потребителя в планировщике iOS 26 не найден; corpus-negative (возможен legacy/другой конфигурации — не доказано).

---

## 3. Постилевые спецификации (machine-generated)

Обозначения: `rel` — `relative` (0..1), `±N s` — `offsetInSeconds`. Для автоматик время всегда в `{"default": {...}}`;
`map(beat_length)` — единственное mapped-значение каталога.

### Style 0 — «Gapless»

- **id:** 0; **name:** `Gapless` (EXACT); **reachability:** NO CONSUMER FOUND [PARTIAL]
- **offset:** relative=0, offsetInSeconds=+0 [EXACT]
- **duration:** отсутствует

**outgoing instructions** (1):

| # | instruction | placement start | placement end | parameterId | ramp startTime | ramp endTime | startValue | endValue | interpolation | units |
|---|---|---|---|---|---|---|---|---|---|---|
| 0.0 | GAIN | rel 1 -0.3 s | rel 1 | `out_gain` | rel 0 | rel 1 | 1 | 0 | ease-in-0.5 | linear volume (0..1) |

**incoming instructions** (1):

| # | instruction | placement start | placement end | parameterId | ramp startTime | ramp endTime | startValue | endValue | interpolation | units |
|---|---|---|---|---|---|---|---|---|---|---|
| 0.0 | GAIN | rel 0 | rel 0 +0.3 s | `out_gain` | rel 0 | rel 1 | 1 | 1 | ease-out-0.5 | linear volume (0..1) |

### Style 1 — «constant-power cross-fade»

- **id:** 1; **name:** `constant-power cross-fade` (EXACT); **reachability:** NO CONSUMER FOUND [PARTIAL]
- **offset:** relative=0 [EXACT]
- **duration:** отсутствует

**outgoing instructions** (1):

| # | instruction | placement start | placement end | parameterId | ramp startTime | ramp endTime | startValue | endValue | interpolation | units |
|---|---|---|---|---|---|---|---|---|---|---|
| 0.0 | GAIN | rel 0 | rel 1 | `out_gain` | rel 0 | rel 1 | 1 | 0 | ease-in-0.5 | linear volume (0..1) |

**incoming instructions** (1):

| # | instruction | placement start | placement end | parameterId | ramp startTime | ramp endTime | startValue | endValue | interpolation | units |
|---|---|---|---|---|---|---|---|---|---|---|
| 0.0 | GAIN | rel 0 | rel 1 | `out_gain` | rel 0 | rel 1 | 0 | 1 | ease-out-0.5 | linear volume (0..1) |

### Style 2 — «constant-power long fade-out short fade-in»

- **id:** 2; **name:** `constant-power long fade-out short fade-in` (EXACT); **reachability:** NO CONSUMER FOUND [PARTIAL]
- **offset:** relative=1, offsetInSeconds=-0.3 [EXACT]
- **duration:** отсутствует

**outgoing instructions** (1):

| # | instruction | placement start | placement end | parameterId | ramp startTime | ramp endTime | startValue | endValue | interpolation | units |
|---|---|---|---|---|---|---|---|---|---|---|
| 0.0 | GAIN | rel 0 | rel 1 | `out_gain` | rel 0 | rel 1 | 1 | 0 | ease-in-0.5 | linear volume (0..1) |

**incoming instructions** (1):

| # | instruction | placement start | placement end | parameterId | ramp startTime | ramp endTime | startValue | endValue | interpolation | units |
|---|---|---|---|---|---|---|---|---|---|---|
| 0.0 | GAIN | rel 0.75 | rel 1 | `out_gain` | rel 0 | rel 1 | 0 | 1 | ease-out-0.5 | linear volume (0..1) |

### Style 3 — «overlap»

- **id:** 3; **name:** `overlap` (EXACT); **reachability:** NO CONSUMER FOUND [PARTIAL]
- **offset:** отсутствует [EXACT]
- **duration:** отсутствует

**outgoing instructions** (0):

_пусто (нет инструкций)_

**incoming instructions** (0):

_пусто (нет инструкций)_

### Style 4 — «Ease-in Ring-out»

- **id:** 4; **name:** `Ease-in Ring-out` (EXACT); **reachability:** NO CONSUMER FOUND [PARTIAL]
- **offset:** relative=0 [EXACT]
- **duration:** отсутствует

**outgoing instructions** (0):

_пусто (нет инструкций)_

**incoming instructions** (1):

| # | instruction | placement start | placement end | parameterId | ramp startTime | ramp endTime | startValue | endValue | interpolation | units |
|---|---|---|---|---|---|---|---|---|---|---|
| 0.0 | GAIN | rel 0 | rel 0 +2 s | `out_gain` | rel 0 | rel 1 | 0 | 1 | ease-out-0.5 | linear volume (0..1) |

### Style 6 — «Beat-matched long fade-out short fade-in - Same Bar num»

- **id:** 6; **name:** `Beat-matched long fade-out short fade-in - Same Bar num` (EXACT); **reachability:** LEGACY / UNREACHABLE [STRONG_INFERENCE]
- **offset:** relative=0 [EXACT]
- **duration:** отсутствует

**outgoing instructions** (2):

| # | instruction | placement start | placement end | parameterId | ramp startTime | ramp endTime | startValue | endValue | interpolation | units |
|---|---|---|---|---|---|---|---|---|---|---|
| 0.0 | TimeStretching | rel 0 | rel 1 | `ts_rate` | rel 0 | rel 1 | 1 | 1 | linear | playback rate ratio (1.0 = original) |
| 1.0 | GAIN | rel 0 | rel 1 | `out_gain` | rel 0 | rel 1 | 1 | 0 | ease-in-0.5 | linear volume (0..1) |

**incoming instructions** (2):

| # | instruction | placement start | placement end | parameterId | ramp startTime | ramp endTime | startValue | endValue | interpolation | units |
|---|---|---|---|---|---|---|---|---|---|---|
| 0.0 | TimeStretching | rel 0 | rel 1 | `ts_rate` | rel 0 | rel 1 | 1 | 1 | linear | playback rate ratio (1.0 = original) |
| 1.0 | GAIN | rel 0.75 | rel 1 | `out_gain` | rel 0 | rel 1 | 0 | 1 | ease-out-0.5 | linear volume (0..1) |

### Style 7 — «Beat-matched long fade-out dynamic fade-in»

- **id:** 7; **name:** `Beat-matched long fade-out dynamic fade-in` (EXACT); **reachability:** LEGACY / UNREACHABLE [STRONG_INFERENCE]
- **offset:** relative=0 [EXACT]
- **duration:** отсутствует

**outgoing instructions** (2):

| # | instruction | placement start | placement end | parameterId | ramp startTime | ramp endTime | startValue | endValue | interpolation | units |
|---|---|---|---|---|---|---|---|---|---|---|
| 0.0 | TimeStretching | rel 0 | rel 1 | `ts_rate` | rel 0 | rel 1 | 1 | 2 | linear | playback rate ratio (1.0 = original) |
| 1.0 | GAIN | rel 0 | rel 1 | `out_gain` | rel 0 | rel 1 | 1 | 0 | ease-in-0.5 | linear volume (0..1) |

**incoming instructions** (2):

| # | instruction | placement start | placement end | parameterId | ramp startTime | ramp endTime | startValue | endValue | interpolation | units |
|---|---|---|---|---|---|---|---|---|---|---|
| 0.0 | TimeStretching | rel 0 | rel 1 | `ts_rate` | rel 0 | rel 1 | 2 | 1 | linear | playback rate ratio (1.0 = original) |
| 1.0 | GAIN | rel 0 | rel 1 | `out_gain` | rel 0 | rel 1 | 0 | 1 | ease-out-0.5 | linear volume (0..1) |

### Style 8 — «BM - Filter high to low»

- **id:** 8; **name:** `BM - Filter high to low` (EXACT); **reachability:** REACHABLE (BeatMatched) [EXACT]
- **offset:** relative=0 [EXACT]
- **duration:** 8 (maximumBarCount: `STRONG_INFERENCE`; unit NOT FOUND)

**outgoing instructions** (3):

| # | instruction | placement start | placement end | parameterId | ramp startTime | ramp endTime | startValue | endValue | interpolation | units |
|---|---|---|---|---|---|---|---|---|---|---|
| 0.0 | TimeStretching | rel 0 | rel 1 | `ts_rate` | rel 0 | rel 1 | 1 | 2 | linear | playback rate ratio (1.0 = original) |
| 1.0 | AULowpass | rel 0 | rel 1 | `lp_cutoff_freq` | rel 0 | rel 1 | 20000 | 200 | ease-out-2 | Hz |
| 2.0 | GAIN | rel 1 -0.3 s | rel 1 | `out_gain` | rel 0 | rel 1 | 1 | 0 | linear | linear volume (0..1) |

**incoming instructions** (4):

| # | instruction | placement start | placement end | parameterId | ramp startTime | ramp endTime | startValue | endValue | interpolation | units |
|---|---|---|---|---|---|---|---|---|---|---|
| 0.0 | TimeStretching | rel 0 | rel 1 | `ts_rate` | rel 0 | rel 1 | 2 | 1 | linear | playback rate ratio (1.0 = original) |
| 1.0 | AUHipass | rel 0 | rel 1 | `hp_cutoff_freq` | rel 0 | rel 1 | 20000 | 10 | ease-out-2 | Hz |
| 2.0 | AULowpass | rel 0 | rel 1 | `lp_cutoff_freq` | rel 0 | rel 1 | 1200 | 20000 | ease-in-4 | Hz |
| 3.0 | GAIN | rel 0 | rel 0 +0.3 s | `out_gain` | rel 0 | rel 1 | 0 | 1 | linear | linear volume (0..1) |

### Style 9 — «BM - Filter expansion»

- **id:** 9; **name:** `BM - Filter expansion` (EXACT); **reachability:** REACHABLE (BeatMatched) [EXACT]
- **offset:** relative=0 [EXACT]
- **duration:** 8 (maximumBarCount: `STRONG_INFERENCE`; unit NOT FOUND)

**outgoing instructions** (4):

| # | instruction | placement start | placement end | parameterId | ramp startTime | ramp endTime | startValue | endValue | interpolation | units |
|---|---|---|---|---|---|---|---|---|---|---|
| 0.0 | TimeStretching | rel 0 | rel 1 | `ts_rate` | rel 0 | rel 1 | 1 | 2 | linear | playback rate ratio (1.0 = original) |
| 1.0 | AULowpass | rel 0 | rel 1 | `lp_cutoff_freq` | rel 0.5 | rel 1 | 20000 | 20 | ease-out-2 | Hz |
| 2.0 | AUHipass | rel 0 | rel 1 | `hp_cutoff_freq` | rel 0.5 | rel 0.75 | 20 | 200 | ease-out-0.5 | Hz |
| 3.0 | GAIN | rel 1 -0.3 s | rel 1 | `out_gain` | rel 0 | rel 1 | 1 | 0 | linear | linear volume (0..1) |

**incoming instructions** (4):

| # | instruction | placement start | placement end | parameterId | ramp startTime | ramp endTime | startValue | endValue | interpolation | units |
|---|---|---|---|---|---|---|---|---|---|---|
| 0.0 | TimeStretching | rel 0 | rel 1 | `ts_rate` | rel 0 | rel 1 | 2 | 1 | linear | playback rate ratio (1.0 = original) |
| 1.0 | AULowpass | rel 0 | rel 1 | `lp_cutoff_freq` | rel 0 | rel 0.75 | 1000 | 20000 | ease-out-0.5 | Hz |
| 2.0 | AUHipass | rel 0 | rel 1 | `hp_cutoff_freq` | rel 0.5 | rel 0.75 | 1000 | 20 | ease-out-2 | Hz |
| 3.0 | GAIN | rel 0 | rel 0 +0.3 s | `out_gain` | rel 0 | rel 1 | 0 | 1 | linear | linear volume (0..1) |

### Style 10 — «BM - Filter long out short in + delay»

- **id:** 10; **name:** `BM - Filter long out short in + delay` (EXACT); **reachability:** LEGACY / UNREACHABLE [STRONG_INFERENCE]
- **offset:** relative=0 [EXACT]
- **duration:** отсутствует

**outgoing instructions** (4):

| # | instruction | placement start | placement end | parameterId | ramp startTime | ramp endTime | startValue | endValue | interpolation | units |
|---|---|---|---|---|---|---|---|---|---|---|
| 0.0 | TimeStretching | rel 0.75 | rel 1 | `ts_rate` | rel 0 | rel 1 | 1 | 2 | linear | playback rate ratio (1.0 = original) |
| 1.0 | AULowpass | rel 0 | rel 1 | `lp_cutoff_freq` | rel 0 | rel 1 | 20000 | 200 | ease-out-2 | Hz |
| 2.0 | AUDelay | rel 0.75 | rel 1 | `fx_delay_dry_wet` | rel 0 | rel 0 +1 s | 0 | 100 | ease-out-2 | percent (0..100) |
| 2.1 | AUDelay | rel 0.75 | rel 1 | `fx_delay_delay_time` | rel 0 | rel 0 | map(beat_length) | map(beat_length) | ease-out-2 | seconds |
| 2.2 | AUDelay | rel 0.75 | rel 1 | `send_mixer_gain` | rel 0 | rel 1 | 0.5 | 0 | linear | linear gain (0..1) |
| 2.3 | AUDelay | rel 0.75 | rel 1 | `fx_mixer_wet` | rel 0 | rel 0 | 0 | 1 | linear | linear gain (0..1) |
| 3.0 | GAIN | rel 1 -0.3 s | rel 1 | `out_gain` | rel 0 | rel 1 | 1 | 0 | linear | linear volume (0..1) |

**incoming instructions** (3):

| # | instruction | placement start | placement end | parameterId | ramp startTime | ramp endTime | startValue | endValue | interpolation | units |
|---|---|---|---|---|---|---|---|---|---|---|
| 0.0 | TimeStretching | rel 0.75 | rel 1 | `ts_rate` | rel 0 | rel 1 | 2 | 1 | linear | playback rate ratio (1.0 = original) |
| 1.0 | AUHipass | rel 0.75 | rel 1 | `hp_cutoff_freq` | rel 0 | rel 1 | 20000 | 200 | ease-out-2 | Hz |
| 2.0 | GAIN | rel 0.75 | rel 0.75 +0.3 s | `out_gain` | rel 0 | rel 1 | 0 | 1 | linear | linear volume (0..1) |

### Style 11 — «BM - Filter long out short in + Reverb»

- **id:** 11; **name:** `BM - Filter long out short in + Reverb` (EXACT); **reachability:** LEGACY / UNREACHABLE [STRONG_INFERENCE]
- **offset:** relative=0 [EXACT]
- **duration:** отсутствует

**outgoing instructions** (4):

| # | instruction | placement start | placement end | parameterId | ramp startTime | ramp endTime | startValue | endValue | interpolation | units |
|---|---|---|---|---|---|---|---|---|---|---|
| 0.0 | TimeStretching | rel 0.5 | rel 1 | `ts_rate` | rel 0 | rel 1 | 1 | 2 | linear | playback rate ratio (1.0 = original) |
| 1.0 | AULowpass | rel 0 | rel 1 | `lp_cutoff_freq` | rel 0 | rel 1 | 20000 | 80 | ease-out-2 | Hz |
| 2.0 | AUHipass | rel 0 | rel 1 | `hp_cutoff_freq` | rel 0 | rel 1 | 10 | 200 | ease-in-4 | Hz |
| 3.0 | AUReverb2 | rel 0 | rel 1 | `fx_reverb_high_frequency_decay_time` | rel 0 | rel 0 | 0.001 | 4 | linear | seconds |
| 3.1 | AUReverb2 | rel 0 | rel 1 | `fx_reverb_low_frequency_decay_time` | rel 0 | rel 0 | 0.001 | 4 | linear | seconds |
| 3.2 | AUReverb2 | rel 0 | rel 1 | `fx_reverb_min_delay_time` | rel 0 | rel 0 | 0.008 | 0.052 | linear | seconds |
| 3.3 | AUReverb2 | rel 0 | rel 1 | `fx_reverb_max_delay_time` | rel 0 | rel 0 | 0.106 | 0.106 | linear | seconds |
| 3.4 | AUReverb2 | rel 0 | rel 1 | `fx_reverb_randomize_reflections` | rel 0 | rel 0 | 333 | 333 | linear | unitless integer-like (1..1000) |
| 3.5 | AUReverb2 | rel 0 | rel 1 | `fx_reverb_dry_wet` | rel 0 | rel 0 | 0 | 100 | linear | percent (0..100) |
| 3.6 | AUReverb2 | rel 0 | rel 1 | `send_mixer_gain` | rel 0 | rel 0.25 | 0 | 1 | linear | linear gain (0..1) |

**incoming instructions** (4):

| # | instruction | placement start | placement end | parameterId | ramp startTime | ramp endTime | startValue | endValue | interpolation | units |
|---|---|---|---|---|---|---|---|---|---|---|
| 0.0 | TimeStretching | rel 0.5 | rel 1 | `ts_rate` | rel 0 | rel 1 | 2 | 1 | linear | playback rate ratio (1.0 = original) |
| 1.0 | AULowpass | rel 0.5 | rel 1 | `lp_cutoff_freq` | rel 0 | rel 1 | 60 | 21000 | ease-in-4 | Hz |
| 2.0 | AUHipass | rel 0.5 | rel 1 | `hp_cutoff_freq` | rel 0 | rel 1 | 3000 | 10 | ease-out-4 | Hz |
| 3.0 | GAIN | rel 0 | rel 1 | `out_gain` | rel 0.5 | rel 0.5 | 0 | 1 | linear | linear volume (0..1) |

### Style 12 — «BM - Long filter high to low»

- **id:** 12; **name:** `BM - Long filter high to low` (EXACT); **reachability:** REACHABLE (BeatMatched) [EXACT]
- **offset:** relative=0 [EXACT]
- **duration:** 16 (maximumBarCount: `STRONG_INFERENCE`; unit NOT FOUND)

**outgoing instructions** (3):

| # | instruction | placement start | placement end | parameterId | ramp startTime | ramp endTime | startValue | endValue | interpolation | units |
|---|---|---|---|---|---|---|---|---|---|---|
| 0.0 | TimeStretching | rel 0 | rel 1 | `ts_rate` | rel 0 | rel 1 | 1 | 2 | linear | playback rate ratio (1.0 = original) |
| 1.0 | AULowpass | rel 0.25 | rel 0.75 | `lp_cutoff_freq` | rel 0 | rel 1 | 20000 | 200 | ease-out-2 | Hz |
| 2.0 | GAIN | rel 0.75 -0.3 s | rel 0.75 | `out_gain` | rel 0 | rel 1 | 1 | 0 | linear | linear volume (0..1) |

**incoming instructions** (4):

| # | instruction | placement start | placement end | parameterId | ramp startTime | ramp endTime | startValue | endValue | interpolation | units |
|---|---|---|---|---|---|---|---|---|---|---|
| 0.0 | TimeStretching | rel 0 | rel 1 | `ts_rate` | rel 0 | rel 1 | 2 | 1 | linear | playback rate ratio (1.0 = original) |
| 1.0 | AUHipass | rel 0.25 | rel 0.75 | `hp_cutoff_freq` | rel 0 | rel 1 | 20000 | 10 | ease-out-2 | Hz |
| 2.0 | AULowpass | rel 0.25 | rel 0.75 | `lp_cutoff_freq` | rel 0 | rel 1 | 1200 | 20000 | ease-in-4 | Hz |
| 3.0 | GAIN | rel 0.25 | rel 0.25 +0.3 s | `out_gain` | rel 0 | rel 1 | 0 | 1 | linear | linear volume (0..1) |

### Style 33 — «overlap + Reverb»

- **id:** 33; **name:** `overlap + Reverb` (EXACT); **reachability:** NO CONSUMER FOUND [PARTIAL]
- **offset:** отсутствует [EXACT]
- **duration:** отсутствует

**outgoing instructions** (1):

| # | instruction | placement start | placement end | parameterId | ramp startTime | ramp endTime | startValue | endValue | interpolation | units |
|---|---|---|---|---|---|---|---|---|---|---|
| 0.0 | AUReverb2 | rel 0 | rel 1 | `fx_reverb_high_frequency_decay_time` | rel 0 | rel 0 | 0.001 | 6 | linear | seconds |
| 0.1 | AUReverb2 | rel 0 | rel 1 | `fx_reverb_low_frequency_decay_time` | rel 0 | rel 0 | 0.001 | 5 | linear | seconds |
| 0.2 | AUReverb2 | rel 0 | rel 1 | `fx_reverb_min_delay_time` | rel 0 | rel 0 | 0.008 | 0.9 | linear | seconds |
| 0.3 | AUReverb2 | rel 0 | rel 1 | `fx_reverb_dry_wet` | rel 0 | rel 0 | 0 | 100 | linear | percent (0..100) |
| 0.4 | AUReverb2 | rel 0 | rel 1 | `send_mixer_gain` | rel 0 | rel 0 +2 s | 0 | 0.75 | linear | linear gain (0..1) |
| 0.5 | AUReverb2 | rel 0 | rel 1 | `fx_mixer_wet` | rel 0 | rel 0 | 0 | 1 | linear | linear gain (0..1) |

**incoming instructions** (0):

_пусто (нет инструкций)_

### Style 44 — «Ease-in Ring-out + Reverb»

- **id:** 44; **name:** `Ease-in Ring-out + Reverb` (EXACT); **reachability:** NO CONSUMER FOUND [PARTIAL]
- **offset:** relative=0 [EXACT]
- **duration:** отсутствует

**outgoing instructions** (1):

| # | instruction | placement start | placement end | parameterId | ramp startTime | ramp endTime | startValue | endValue | interpolation | units |
|---|---|---|---|---|---|---|---|---|---|---|
| 0.0 | AUReverb2 | rel 0 | rel 1 | `fx_reverb_high_frequency_decay_time` | rel 0 | rel 0 | 0.001 | 6 | linear | seconds |
| 0.1 | AUReverb2 | rel 0 | rel 1 | `fx_reverb_low_frequency_decay_time` | rel 0 | rel 0 | 0.001 | 5 | linear | seconds |
| 0.2 | AUReverb2 | rel 0 | rel 1 | `fx_reverb_min_delay_time` | rel 0 | rel 0 | 0.008 | 0.9 | linear | seconds |
| 0.3 | AUReverb2 | rel 0 | rel 1 | `fx_reverb_dry_wet` | rel 0 | rel 0 | 0 | 100 | linear | percent (0..100) |
| 0.4 | AUReverb2 | rel 0 | rel 1 | `send_mixer_gain` | rel 0 | rel 0 +2 s | 0 | 0.75 | linear | linear gain (0..1) |
| 0.5 | AUReverb2 | rel 0 | rel 1 | `fx_mixer_wet` | rel 0 | rel 0 | 0 | 1 | linear | linear gain (0..1) |

**incoming instructions** (1):

| # | instruction | placement start | placement end | parameterId | ramp startTime | ramp endTime | startValue | endValue | interpolation | units |
|---|---|---|---|---|---|---|---|---|---|---|
| 0.0 | GAIN | rel 0 | rel 0 +2 s | `out_gain` | rel 0 | rel 1 | 0 | 1 | ease-out-0.5 | linear volume (0..1) |

---

## 4. Constant-power стили (id 1, 2) — что доказуемо из ресурса

**Сырые данные (EXACT):**

- **Style 1 «constant-power cross-fade»:** `offset={relative:0}`; outgoing GAIN: `out_gain` `1→0`, `ease-in-0.5`, время `rel 0→1`; incoming GAIN: `out_gain` `0→1`, `ease-out-0.5`, время `rel 0→1`.
- **Style 2 «constant-power long fade-out short fade-in»:** `offset={relative:1, offsetInSeconds:-0.3}`; outgoing — как в style 1; incoming GAIN placement `rel 0.75→1`, рампа `out_gain` `0→1`, `ease-out-0.5`, time `rel 0→1`.

**Что доказуемо:**

1. Ресурс кодирует только `parameterId`, значения рамп, времена, `interpolation` и `placement`. Явной формулы equal-power/constant-power (cos/sin, сумма квадратов) в ресурсе **нет** — только строковое имя стиля.
2. В AutoMix-бинарях нет постоянных `π`, `π/2`, `√0.5` (0.7071/0.707/1/√2) ни как float64, ни как float32 (независимый byte-scan `_SonicKit_MusicKit_Packages`, `_SonicKit_MusicKit`, `SonicKit`; согласуется с RED_TEAM_AUDIT §4.2).
3. **Derivation (INFERRED/NOT FOUND as explicit formula):** если evaluator применяет `ease-in-0.5` как `y=1−sqrt(1−p)` и `ease-out-0.5` как `y=sqrt(p)` (curveByte `0x00`/`0x40`, EXACT-формулы `FUN_272275f84`; связка «строка→byte» — STRONG_INFERENCE), то
   `gain_out(t)=1−(1−sqrt(1−t))=sqrt(1−t)`, `gain_in(t)=0+sqrt(t)`, и `gain_out²+gain_in²=1` — то есть constant-power в смысле суммы квадратов амплитуд,
   **а не** `cos(π/2·t)`/`sin(π/2·t)`. Формула с π — NOT FOUND и REJECTED.
4. Для style 2 окно incoming-рампы задано `placement rel 0.75→1` (короткий fade-in), но `startTime/endTime` рампы остаются `rel 0→1` — resource-relative времена не равны placement-временам.

**Статусы:** raw values EXACT; curve formulas EXACT (evaluator); строка→byte STRONG_INFERENCE; вывод «constant-power = sqrt-пары» INFERRED; π-формула NOT FOUND/REJECTED.

---

## 5. Стили 8/9/12 — детально (планировщик) и сверка с P1

### 5.1. Полные рампы (из ресурса)

**Style 8 «BM - Filter high to low»** — `duration=8`, `offset=relative=0`.

**outgoing instructions** (3):

| # | instruction | placement start | placement end | parameterId | ramp startTime | ramp endTime | startValue | endValue | interpolation | units |
|---|---|---|---|---|---|---|---|---|---|---|
| 0.0 | TimeStretching | rel 0 | rel 1 | `ts_rate` | rel 0 | rel 1 | 1 | 2 | linear | playback rate ratio (1.0 = original) |
| 1.0 | AULowpass | rel 0 | rel 1 | `lp_cutoff_freq` | rel 0 | rel 1 | 20000 | 200 | ease-out-2 | Hz |
| 2.0 | GAIN | rel 1 -0.3 s | rel 1 | `out_gain` | rel 0 | rel 1 | 1 | 0 | linear | linear volume (0..1) |

**incoming instructions** (4):

| # | instruction | placement start | placement end | parameterId | ramp startTime | ramp endTime | startValue | endValue | interpolation | units |
|---|---|---|---|---|---|---|---|---|---|---|
| 0.0 | TimeStretching | rel 0 | rel 1 | `ts_rate` | rel 0 | rel 1 | 2 | 1 | linear | playback rate ratio (1.0 = original) |
| 1.0 | AUHipass | rel 0 | rel 1 | `hp_cutoff_freq` | rel 0 | rel 1 | 20000 | 10 | ease-out-2 | Hz |
| 2.0 | AULowpass | rel 0 | rel 1 | `lp_cutoff_freq` | rel 0 | rel 1 | 1200 | 20000 | ease-in-4 | Hz |
| 3.0 | GAIN | rel 0 | rel 0 +0.3 s | `out_gain` | rel 0 | rel 1 | 0 | 1 | linear | linear volume (0..1) |

**Style 9 «BM - Filter expansion»** — `duration=8`, `offset=relative=0`.

**outgoing instructions** (4):

| # | instruction | placement start | placement end | parameterId | ramp startTime | ramp endTime | startValue | endValue | interpolation | units |
|---|---|---|---|---|---|---|---|---|---|---|
| 0.0 | TimeStretching | rel 0 | rel 1 | `ts_rate` | rel 0 | rel 1 | 1 | 2 | linear | playback rate ratio (1.0 = original) |
| 1.0 | AULowpass | rel 0 | rel 1 | `lp_cutoff_freq` | rel 0.5 | rel 1 | 20000 | 20 | ease-out-2 | Hz |
| 2.0 | AUHipass | rel 0 | rel 1 | `hp_cutoff_freq` | rel 0.5 | rel 0.75 | 20 | 200 | ease-out-0.5 | Hz |
| 3.0 | GAIN | rel 1 -0.3 s | rel 1 | `out_gain` | rel 0 | rel 1 | 1 | 0 | linear | linear volume (0..1) |

**incoming instructions** (4):

| # | instruction | placement start | placement end | parameterId | ramp startTime | ramp endTime | startValue | endValue | interpolation | units |
|---|---|---|---|---|---|---|---|---|---|---|
| 0.0 | TimeStretching | rel 0 | rel 1 | `ts_rate` | rel 0 | rel 1 | 2 | 1 | linear | playback rate ratio (1.0 = original) |
| 1.0 | AULowpass | rel 0 | rel 1 | `lp_cutoff_freq` | rel 0 | rel 0.75 | 1000 | 20000 | ease-out-0.5 | Hz |
| 2.0 | AUHipass | rel 0 | rel 1 | `hp_cutoff_freq` | rel 0.5 | rel 0.75 | 1000 | 20 | ease-out-2 | Hz |
| 3.0 | GAIN | rel 0 | rel 0 +0.3 s | `out_gain` | rel 0 | rel 1 | 0 | 1 | linear | linear volume (0..1) |

**Style 12 «BM - Long filter high to low»** — `duration=16`, `offset=relative=0`.

**outgoing instructions** (3):

| # | instruction | placement start | placement end | parameterId | ramp startTime | ramp endTime | startValue | endValue | interpolation | units |
|---|---|---|---|---|---|---|---|---|---|---|
| 0.0 | TimeStretching | rel 0 | rel 1 | `ts_rate` | rel 0 | rel 1 | 1 | 2 | linear | playback rate ratio (1.0 = original) |
| 1.0 | AULowpass | rel 0.25 | rel 0.75 | `lp_cutoff_freq` | rel 0 | rel 1 | 20000 | 200 | ease-out-2 | Hz |
| 2.0 | GAIN | rel 0.75 -0.3 s | rel 0.75 | `out_gain` | rel 0 | rel 1 | 1 | 0 | linear | linear volume (0..1) |

**incoming instructions** (4):

| # | instruction | placement start | placement end | parameterId | ramp startTime | ramp endTime | startValue | endValue | interpolation | units |
|---|---|---|---|---|---|---|---|---|---|---|
| 0.0 | TimeStretching | rel 0 | rel 1 | `ts_rate` | rel 0 | rel 1 | 2 | 1 | linear | playback rate ratio (1.0 = original) |
| 1.0 | AUHipass | rel 0.25 | rel 0.75 | `hp_cutoff_freq` | rel 0 | rel 1 | 20000 | 10 | ease-out-2 | Hz |
| 2.0 | AULowpass | rel 0.25 | rel 0.75 | `lp_cutoff_freq` | rel 0 | rel 1 | 1200 | 20000 | ease-in-4 | Hz |
| 3.0 | GAIN | rel 0.25 | rel 0.25 +0.3 s | `out_gain` | rel 0 | rel 1 | 0 | 1 | linear | linear volume (0..1) |

### 5.2. Перекрёстная сверка с P1 selector evidence

| Утверждение P1 | Ресурс | Согласованность |
|---|---|---|
| id 8 = BeatMatched, 4 фактора, база score 10.0 (`0x2722345ec`) | style 8: `duration=8`; TimeStretching + AULowpass (out) / AUHipass + AULowpass (in); GAIN линейные | CONFIRMED (набор DSP согласуется; маппинг фактор↔автоматика NOT FOUND) |
| id 9 = BeatMatched, 6 факторов, база 15.0 (`0x27223472c`) | style 9: `duration=8`; расширение: 4+4 инструкции, несимметричные окна `lp/hp` (0.5→1 / 0→0.75, 0.5→0.75) | CONFIRMED (структурно богаче style 8) |
| id 12 = expanded tempo/time-stretch, база 10.0 (`0x272234a24`), shifted region pair, лог `0x2722aa200` | style 12: `duration=16`; окна фильтров `0.25→0.75`, GAIN-рампа `rel 1→0` в узком окне `0.75−0.3s→0.75` | CONFIRMED (окна смещены внутрь, длительность вдвое больше) |
| `duration` = `maximumBarCount` (Int?) | `duration` есть только у 8/9/12: 8/8/16 | STRONG_INFERENCE; unit (бары vs секунды) NOT FOUND |
| styles 6/7/10/11 — BM-имена, но не выбираются | id нет в ветках `FUN_272234380` | CONFIRMED (zero-fill) |

**Итог 8/9/12:** полные рампы, `placement` и `duration` — EXACT из ресурса; привязка id→ветка планировщика — EXACT; семантика `duration` — STRONG_INFERENCE; runtime-конверсия normalized→секунды — PARTIAL (планировщик, `DSP_RUNTIME_ARCHITECTURE` §C).

---

## 6. Fallback-поведение

| Ситуация | Поведение | Статус |
|---|---|---|
| Стиля с именем `Fallback` в каталоге нет | — | EXACT |
| Длительность Fallback Cross-Fade | **2.0 s жёстко в коде** (`FUN_272239248`, `0x2722394ec` `fmov d0,#0x4000000000000000`; порог и лог) | EXACT |
| Привязка fallback → style 4 «Ease-in Ring-out» | не доказана; в каталоге style 4 — incoming GAIN `0→1` c placement `rel 0→0+2s` | INFERRED, **не hardcode** |
| Неизвестный style.id в dispatch (`FUN_272234380`) | zero-fill кандидата (0x100 B) → кандидат не выбирается | EXACT |
| `beat_length` не резолвится | берётся default параметра; ошибка «ID and/or default value missing.» | STRONG_INFERENCE |
| Ресурс `TransitionStyles.json` отсутствует | `SmartTransitionsError` tag `0xd`, лог; transition не строится | STRONG_INFERENCE |

---

## 7. Кривые: имена, байты, формулы

| interpolation (ресурс) | curveByte | формула y(p) | статус |
|---|---|---|---|
| `linear` | `0x80` | `p` | EXACT |
| `ease-out-2` | `0x41` | `1 - (1 - p)^2` | EXACT |
| `ease-out-0.5` | `0x40` | `sqrt(p)` | EXACT |
| `ease-in-0.5` | `0x00` | `1 - sqrt(1 - p)` | EXACT |
| `ease-in-4` | `0x02..0x3f` | `pow(p, 4.0)` | STRONG_INFERENCE |
| `ease-out-4` | `0x42..0x7f` | `1 - pow(1 - p, 4.0)` | STRONG_INFERENCE |

`value = startValue + y(p)·(endValue − startValue)` (EXACT, `FUN_272275f84`). `logarithmic` в каталоге не используется (0 вхождений).

---

## 8. Units по параметрам, используемым каталогом

| parameterId | swift case | dspg id | range | default | units | unit status | target | delivery |
|---|---|---|---|---|---|---|---|---|
| `fx_delay_delay_time` | delayDelayTime | DLdt | 0.0001..2.01 | 1.0 | seconds | STRONG_INFERENCE | AUDelay[1] | DSPGraph AUDelay |
| `fx_delay_dry_wet` | delayDryWetBalance | DLdw | 0.0..100.0 | 0.0 | percent (0..100) | STRONG_INFERENCE | AUDelay[0] | DSPGraph AUDelay |
| `fx_mixer_wet` | auxEffectsBusReturnMixerWetVolume | Ga4g | 0.0..1.0 | 0.0 | linear gain (0..1) | EXACT | Gain4[0] | DSPGraph gain box (wet return) |
| `fx_reverb_dry_wet` | reverbDryWetBalance | RVdw | 0.0..100.0 | 0.0 | percent (0..100) | STRONG_INFERENCE | AUReverb[0] | DSPGraph AUReverb2 |
| `fx_reverb_high_frequency_decay_time` | reverbHighFrequencyDecayTime | RVhf | 0.001..20.0 | 0.5 | seconds | STRONG_INFERENCE | AUReverb[5] | DSPGraph AUReverb2 |
| `fx_reverb_low_frequency_decay_time` | reverbLowFrequencyDecayTime | RVlf | 0.001..20.0 | 1.0 | seconds | STRONG_INFERENCE | AUReverb[4] | DSPGraph AUReverb2 |
| `fx_reverb_max_delay_time` | reverbMaximumDelayTime | RVma | 0.0001..1.0 | 0.05 | seconds | STRONG_INFERENCE | AUReverb[3] | DSPGraph AUReverb2 |
| `fx_reverb_min_delay_time` | reverbMinimumDelayTime | RVmi | 0.0001..1.0 | 0.008 | seconds | STRONG_INFERENCE | AUReverb[2] | DSPGraph AUReverb2 |
| `fx_reverb_randomize_reflections` | reverbReflectionsRandomization | RVrr | 1.0..1000.0 | 1.0 | unitless integer-like (1..1000) | STRONG_INFERENCE | AUReverb[6] | DSPGraph AUReverb2 |
| `hp_cutoff_freq` | highPassFilterCutoffFrequency | HP1f | 10.0..22050.0 | 10.0 | Hz | STRONG_INFERENCE | AUHipass1[0] | DSPGraph AUHipass |
| `lp_cutoff_freq` | lowPassFilterCutoffFrequency | LP1f | 10.0..21829.5 | 22000.0 | Hz | STRONG_INFERENCE | AULowpass1[0] | DSPGraph AULowpass |
| `out_gain` | outputMixerVolume | — | 0.0..1.0 | 0.0 | linear volume (0..1) | EXACT | NOT IN DSPGraph | AVFoundation: AVMutableAudioMixInputParameters volume ramp |
| `send_mixer_gain` | auxEffectsBusSendMixerVolume | Ga2g | 0.0..1.0 | 1.0 | linear gain (0..1) | EXACT | Gain2[0] | DSPGraph gain box (aux send) |
| `ts_rate` | timeStretchingRate | — | 0.03125..32.0 | 1.0 | playback rate ratio (1.0 = original) | EXACT | NOT IN DSPGraph | AVFoundation: AVPlayerItem.speedRamp / ME TimePitch (Spectral) |

Параметры вне графа: `ts_rate` (AVPlayerItem.speedRamp, ME TimePitch Spectral) и `out_gain` (AVMutableAudioMixInputParameters volume ramp) — EXACT (`DSP_RUNTIME_ARCHITECTURE` §B/§C).

---

## 9. Safe to implement vs Do not hardcode

### Можно реализовывать как данные (EXACT/STRONG)

- Значения и времена GAIN-рамп `out_gain` (21 автоматика), включая style 0 `1→1` и style 2 `0.75→1` placement.
- Ремпы фильтров `lp_cutoff_freq`/`hp_cutoff_freq` (значения, окна, `interpolation`) — EXACT из ресурса; units Hz — STRONG_INFERENCE.
- `fx_delay_*`, `fx_reverb_*`, `send_mixer_gain`, `fx_mixer_wet` — значения/времена EXACT; units — STRONG_INFERENCE (AU-заголовки).
- `duration` 8/8/16 у 8/9/12 как максимум длительности (семантика `maximumBarCount`).
- Порядок инструкций и `placement` окон — EXACT.
- Dispatch 8/9/12 в BeatMatched-пути; zero-fill для прочих id — EXACT.

### Нельзя хардкодить (NOT FOUND / INFERRED)

- Формулы `cos/sin(π/2·t)` и любые π-константы — REJECTED, в бинарях отсутствуют.
- Вывод «constant-power = sqrt-пара» — INFERRED; реализовывать как следствие curve-движка, а не как отдельную формулу.
- Точный `curveByte` для `ease-in-4`/`ease-out-4` (диапазоны 0x02–0x3f / 0x42–0x7f).
- Резолвер `beat_length` (источник значения/BPM) — NOT FOUND; при отсутствии — default параметра.
- Привязку алгоритмов Fallback/DeadAir/Smart к стилям 0–4/33/44 — NOT FOUND.
- Привязку fallback → style 4 — только INFERRED.
- Единицы `duration` (бары vs секунды) и runtime-конверсию normalized→секунды (`entryOffsetUs`) — PARTIAL/NOT FOUND.
- Семантику `bypa`/активацию эффектов графа — по умолчанию эффекты в bypass, каталог `bypa` не меняет (STRONG_INFERENCE по следствию).

---

## 10. Оставшиеся неизвестные (сводно)

1. `duration` (8/8/16): бар-каунт vs секунды; точная конверсия в планировщике — NOT FOUND.
2. `offset` ↔ `TransitionStyle.startTime`: raw-ключ подтверждён, bind к Swift-полю — STRONG_INFERENCE.
3. Runtime-резолв `beat_length` и поведение при отсутствии значения — STRONG_INFERENCE (default).
4. Потребитель стилей 0–4/33/44 в iOS 26-планировщике — NOT FOUND (corpus-negative).
5. Точный байт кривых `ease-in-4`/`ease-out-4` — STRONG_INFERENCE.
6. Точная функция планировщика, превращающая `ts_rate`-автоматику в `TimeStretchingStep` — NOT FOUND (`DSP_RUNTIME_ARCHITECTURE` §B).
7. `bypa=0` callsite — NOT FOUND; следствие «эффекты по умолчанию обойдены» — STRONG_INFERENCE.

