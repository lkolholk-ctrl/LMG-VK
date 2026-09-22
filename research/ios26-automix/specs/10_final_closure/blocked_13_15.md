# blocked_13_15.md — финальные статусы #13 (`bpm.percentDeviation`) и #15 (`loudness.peak`)

**Дата:** 2026-09-13. **Роль:** Final Closure / MediaAPI→Planner Analyst (субагент 10).
**Ограничения:** research only; `/root/LMG-VK` и код приложения не изменялись; секреты не приводятся
(используется только `evidence/MEDIAAPI_live_1776914757_sanitized.json`).

Итоговые вердикты (по мандату: «если потребителя и доказательства шкалы нет → BLOCKED допустимо»):

| # | Вопрос | Потребитель | Семантика/шкала | Итоговый статус |
|---|---|---|---|---|
| 13 | `bpm.percentDeviation` | **NOT FOUND** (EXACT-негатив в 16 targets + Music.app + MusicApplication) | **UNVERIFIED** (масштабирования нет; live=1; одиночный трек) | **BLOCKED** (consumer-negative EXACT) |
| 15 | `loudness.peak` | **NOT FOUND** (EXACT-негатив) | **UNVERIFIED** (линейная vs dB неразрешима; live-значения несовместимы с одной шкалой) | **BLOCKED** (consumer-negative EXACT) |

---

## 1. #13 — `bpm.percentDeviation`

### 1.1. Подтверждённые звенья (EXACT)

1. **Cloud**: `MusicKit.CloudAudioAnalysis.CloudCompositeAttribute<Double>` с полем
   `percentDeviation: Double?` (CodingKeys `percentDeviation`; MusicKit, DSC subcache `.40`;
   P1_MEDIAAPI_CLOSURE §2.3).
2. **Internal**: `MusicKitInternal.AudioAnalysis.BeatsPerMinute.percentDeviation: Double?`,
   getter `0x1d4399720` (`lyrics_functions.tsv`).
3. **Конвертер** `FUN_1d4121a98` (Cloud BPM → `BeatsPerMinute`, closure `0x1d412061c` @line 467):
   только 3× `fcvtzs` для `main/beginning/ending`; в теле нет `fmul`/`fdiv`, `percentDeviation`
   переносится как Double без масштабирования (P2 §1.1; перепроверено: pseudocode
   `lyrics/pseudocode/1d4121a98__FUN_1d4121a98.c`, overflow-checked Double→Int).
4. **Live JSON** (`evidence/MEDIAAPI_live_1776914757_sanitized.json`):
   `bpm = {beginning:125, ending:129, main:129, percentDeviation:1}`.

### 1.2. Поиски потребителя (все выполнены; результат — негативный)

| Поиск | Команда/метод | Результат |
|---|---|---|
| P2: все 16 targets | резолв `__auth_stubs` + branch-pool островов `_SonicKit_MusicKit`/`_SonicKit_MusicKit_Packages` (P2 §1.2) | getter `0x1d4399720` **не вызывается**; нет в 41 packages-импорте и 15 engine-импортах |
| Прямые ветви в corpus | `rg -n 'bl 0x1d4399720\|b 0x1d4399720' lyrics/disassembly animations/disassembly automix/disassembly` | **0** совпадений |
| Строки планировщика | `strings -a targets/_SonicKit_MusicKit_Packages \| grep -i 'deviation\|percent'` | **0** |
| Music.app (Ghidra 09_appos) | `rg -i 'percentDeviation\|deviation' musicapp_ghidra/function_index.tsv symbols/ strings/` | **0** |
| MusicApplication | `rg` по `indexes/animations_functions.tsv` (946 функций MusicApplication) | **0** |
| Cross-framework xrefs | `rg -i 'deviation' decompiled_package/cross_framework_xrefs/xrefs.tsv` | **0** |
| Live JSON | поиск других песен: `grep -rl 'percentDeviation' /srv/research --include='*.json'` | только один трек (`1776914757`) в 3 копиях (raw/full/sanitized); других песен нет; см. §1.3 |

### 1.3. Попытка корреляции на live JSON (correlation ≠ proof; данных мало)

Дано ровно **одно** живое наблюдение (трек `1776914757`); массивы в sanitized JSON усечены до head/tail по 4 значения,
поэтому корреляция по всем 644 битам невозможна. Что можно вычислить:
- `beatsInMilliseconds` head: 302/767/1231/1696 мс → интервалы 465/464/465 мс → 129.03 BPM;
  tail: …/300249/300659/301069/301479 → интервалы 410/410/410 мс → 146.34 BPM (темп внутри
  трека меняется).
- Заявленные BPM: beginning 125, main 129, ending 129.
- Гипотезы для `percentDeviation = 1`:
  - `(main−beginning)/main·100 = 3.101 %`; `(main−beginning)/beginning·100 = 3.200 %` — не 1;
  - `(main−ending) = 0` — тоже не 1;
  - 1 % не соответствует и разбросу бит-интервалов (видимые интервалы, по крайней мере, 464–465 мс
    и 410 мс в разных участках), что скорее исключает «процент отклонения локального темпа».
- Вывод: **ни одна из проверяемых гипотез (проценты/доли/confidence) не подтверждена**;
  единственная выборка не позволяет отличить 1 % от целочисленного округления произвольной метрики.
  **Корреляция не является доказательством; семантика = UNVERIFIED.**

### 1.4. Что осталось BLOCKED и как закрыть

- Runtime-точка на getter `0x1d4399720` (LLDB) или трассировка `MusicKit.framework`
  `CloudAudioAnalysis` декодера на устройстве; либо дамп `AudioAnalysis` из Apple Music app.
- Иного локального пути нет: конвертер значение не трогает, потребителей в corpus нет.

---

## 2. #15 — `loudness.peak` (и `loudness.value/range`)

### 2.1. Подтверждённые звенья (EXACT)

1. **Cloud**: `CloudCompositeAttribute<CloudStatistics>` с полями `value/range/peak: Double?`
   (`CloudStatistics`, MusicKit DSC `.40`; P1_MEDIAAPI_CLOSURE §2.3).
2. **Internal**: `MusicKitInternal.AudioAnalysis.Statistics { value, range, peak: Double }`;
   getters `0x1d439a0a8` / `0x1d439a0b0` / `0x1d439a0b8`.
3. **Конвертеры** loudness-ветки (`FUN_1d4121c24`, `FUN_1d4121ef0`, `FUN_1d41240a4`, `FUN_1d41245f8`):
   **0 инструкций `fmul`/`fdiv`** — значения проходят как Double без масштабирования (P2 §3.2).
4. **Live JSON**: `main {value:-7.912872, range:5.786646, peak:0.4707109}`,
   `beginning {value:-10.99596, range:4.970956, peak:0.14886901}`,
   `ending {value:-6.661943, range:4.43449, peak:0.25221932}`;
   live `loudnessCurve.value` = 602 сэмпла dB-подобных (−48.8…−3.3) ≈ 2 Гц.

### 2.2. Поиски потребителя (все выполнены; результат — негативный)

| Поиск | Команда/метод | Результат |
|---|---|---|
| P2: все 16 targets | резолв `__auth_stubs` + островов | `Statistics.value/range/peak` и `AudioAnalysis.loudness` (`0x1d43966f0`) **не импортируются/не вызываются** (P2 §3.1) |
| Прямые ветви в corpus | `rg -n 'bl 0x1d439a0b8\|0x1d439a0a8\|0x1d439a0b0\|0x1d43966f0' lyrics/disassembly animations/disassembly automix/disassembly` | для peak/value/range — **0**; для `loudness` — только MKI-внутренний wrapper `FUN_1d3d4d4d8` (без corpus-callers) |
| Строки планировщика | `strings -a targets/_SonicKit_MusicKit_Packages \| grep -iE 'Statistics\|peak\|loudness[A-Z]'` | `Statistics/peak` — **0**; есть только planner-native `LoudnessMap/LoudnessSample/LoudnessSampleRange/LoudnessValue` + `loudnessCurve`-потребители |
| Music.app (Ghidra 09_appos) | `rg -i 'loudness\|\\bpeak\\b\|Statistics' musicapp_ghidra/function_index.tsv symbols/ strings/` | **0** релевантных (только JetUI `keepStatisticsOnComponents`) |
| MusicApplication | `rg -i 'loudness\|peak' indexes/animations_functions.tsv` | **0** |
| Cross-framework xrefs | `rg -i 'loudness\|peak' cross_framework_xrefs/xrefs.tsv` | только `MPC *_loudnessInfo*` (нормализация громкости воспроизведения, к MediaAPI-анализу не относится) |
| Альтернативный потребитель | `strings -a targets/{_SonicKit_MusicKit,_SonicKit_MusicKit_Packages,MusicKitInternal} \| grep -icE 'VolumeNormalization\|ReplayGain'` | 0/0/0 |

Что **реально** читает планировщик из loudness-семейства (EXACT, P2 §3.2 + перепроверено):
`AudioAnalysis.loudnessCurve` (остров `0x2743dd1c0`, call @`0x272223d58` в `FUN_272223b6c`),
затем `LoudnessCurve.samplingFrequency`/`.value` — т.е. `loudness.{value,range,peak}` **независимо
не используются**.

### 2.3. Попытка доказать шкалу `peak` без потребителя

Единственная зацепка — арифметика live-значений (корреляция, не доказательство):
- `main`: `20·log10(0.4707109) = −6.545 dB` при `value = −7.913 dB` → разница **+1.368 dB**;
- `beginning`: `20·log10(0.14886901) = −16.544 dB` при `value = −10.996 dB` → разница **−5.548 dB**;
- `ending`: `20·log10(0.25221932) = −11.964 dB` при `value = −6.662 dB` → разница **−5.302 dB**.

Одна и та же шкала (линейная амплитуда `0…1` для `peak` и dB для `value`) не даёт согласованного
соотношения: для `main` peak выше «среднего» на 1.37 dB, а для `beginning`/`ending` — **ниже** на
~5.3–5.5 dB. Это может объясняться тем, что `value` — не RMS, либо `peak` нормирован иначе/по
другому окну. Без кода-потребителя и калибровочных констант единицы (`0…1` линейная амплитуда vs
dB-подобная/иная шкала) **доказать нельзя**; преобразования `FUN_1d4121c24/1ef0/40a4/45f8`
не содержат ни `fmul`, ни `fdiv`.

**Вердикт #15: BLOCKED** (потребитель NOT FOUND; шкала UNVERIFIED) — соответствует допустимому
исходу мандата. Закрытие: runtime-лог `loudness.*` на устройстве и сравнение `peak` с `value/range`,
либо поиск потребителя в Apple Music app на устройстве (вне corpus).

---

## 3. Сводка для UNRESOLVED_QUESTIONS / CLOSURE_TRACKER

```
13. percentDeviation — BLOCKED (consumer-negative EXACT; semantics UNVERIFIED).
    getter 0x1d4399720 не вызывается ни из одного из 16 corpus-targets (P2), 0 прямых bl
    в lyrics/animations/automix disasm, 0 в Music.app/MusicApplication, 0 в strings packages.
    FUN_1d4121a98 не масштабирует. Live=1; единственный трек, гипотезы 1%/доли не подтверждены
    ((main-beg)/main=3.10%). Закрытие — runtime на устройстве.
15. loudness.peak — BLOCKED (consumer-negative EXACT; units UNVERIFIED).
    Statistics.value/range/peak (0x1d439a0a8/b0/b8) не импортируются/не вызываются;
    планировщик читает только loudnessCurve (island 0x2743dd1c0 @0x272223d58).
    Live: 20log10(peak) vs value: main +1.37 dB, beginning -5.55 dB, ending -5.30 dB —
    согласованной шкалы нет. Закрытие — runtime на устройстве.
```

---

## 4. Точный перечень выполненных поисков (reproducibility)

```bash
# 1) P2-скан импортов/островов (исторический, DSC тогда был на диске)
#    см. 08_closure/P2_MEDIADSP_CLOSURE.md §1.2, §3.1, §4.1

# 2) локальные прямые ветви к getter'ам (corpus: 16 targets)
for a in 1d439a0b8 1d439a0a8 1d439a0b0 1d4399720 1d43966f0 1d43967bc 1d4396a1c \
         1d4396cec 1d3e81ab0 1d4396b38 1d43967e0 1d4396964; do
  rg -l "bl 0x$a|b 0x$a" \
    /srv/research/apple-music-ios26/decompiled_package/{lyrics,animations,automix}/disassembly
done
# -> все 0, кроме 1d43966f0/1d4396cec/1d4396b38/1d43967e0/1d4396964, которые вызываются только
#    из MKI-внутренних wrapper'ов без символьных имён:
#    1d3d4d4d8 (loudness), 1d3d4d3f0 (phrases), 1d4397bc8 (fades), 1d43977e0 (eventTimes),
#    1d3d4d548 (beatsPerMinute) — callers в corpus отсутствуют

# 3) Music.app
rg -i 'percentDeviation|deviation|loudness|\bpeak\b|Statistics|AudioAnalysis|beatsPerMinute|eventTimes' \
  /srv/research/apple-music-ios26/deepseek_analysis/09_appos/musicapp_ghidra/{function_index.tsv,symbols,strings}

# 4) MusicApplication (index)
rg -i 'acousticness|beatsPerMinute|eventTimes|loudness|vocalActivit|deviation' \
  /srv/research/apple-music-ios26/deepseek_analysis/indexes/animations_functions.tsv

# 5) xrefs
rg -i 'deviation|loudness|peak' \
  /srv/research/apple-music-ios26/decompiled_package/cross_framework_xrefs/xrefs.tsv

# 6) strings planner
strings -a /srv/research/apple-music-ios26/decompiled_package/targets/_SonicKit_MusicKit_Packages \
  | grep -iE 'Statistics|peak|deviation|energy|valence|visualTempo|BeatsPerMinute|EventTimes'

# 7) live JSON (sanitized; 1 трек, массивы усечены)
python3 - <<'EOF'
# см. расчёты §1.3/§2.3: интервалы битов, bpm, 10^(dB/20), 20log10(peak)
EOF
```
