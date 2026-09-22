# ANDROID_DSP_ENGINE — native C++ node graph, автоматизация, параметры (audited)

Subagent #7. RESEARCH/SPEC ONLY. Источник: `02_dsp/*` + `07_audit/*`. Все ручки и диапазоны —
из `DSP_NODES.md`/`DSP_CONSTANTS.md` (EXACT, повторно подтверждены аудитором: 29 записей,
rodata-пары 0x27229e0c0..0x27229e1b0, `allCases` 0x1d×0x38). Топология графа и типы фильтров —
`[STRONG_INFERENCE]`/`NOT FOUND`. Кода не создано.

Терминология: «ручка» = `styleParameterID` (имя параметра в JSON/графе), «id» — короткий
fourCC-подобный код Apple. Android использует собственные стабильные `DspParamId` (см. §6).

---

## 1. Целевая схема (реконструкция, статус STRONG_INFERENCE)

```
Track chain A (и B — симметрично):
  inputMixer(player_gain, out=1.0) → multibandFilter(center freq/bw/gain)
      → highPassFilter(hp_cutoff_freq/hp_reso) → lowPassFilter(lp_cutoff_freq/lp_reso)
      → outputMixer(out_gain, def 0.0)
Aux bus:
  sendMixer(send_mixer_gain) → auxHP(fx_hp_cutoff/reso) → auxLP(fx_lp_cutoff/reso)
      → delay(fx_delay_delay_time / fx_delay_lp_cutoff_frequency / fx_delay_dry_wet / fx_delay_feedback)
      → reverb(fx_reverb_gain / dry_wet / min_delay / max_delay / lf_decay / hf_decay / randomize)
      → auxReturnMixer(fx_mixer_dry / fx_mixer_wet)
Time stretch: timeStretchingRate (ts_rate)  — место НЕ подтверждено
Bypass: effectBypassingState (bypa, 1.0 = эффекты активны)
```

Ручки из AU-семейств: multiband `aufilter_center_*` — Apple AUFilter-подобная секция
`[INFERRED]`; delay — ручки AU Delay (`kDelayParam_*`) `[STRONG_INFERENCE]`; reverb — ручки
AU Reverb2 (`DryWetMix/RandomizeReflections/Gain/MinDelayTime/MaxDelayTime/DecayTimeAt0Hz/
DecayTimeAtNyquist`) `[STRONG_INFERENCE]`. Прямых символов AU в AutoMix-бинарниках нет.

---

## 2. Компонент: ParameterTable (29 ручек)

- iOS behavior: `Transition.AutomationEffectParameter` — 29 (0x1d) записей по 0x38 B:
  `{id: String, valueRange: Range<Double>, defaultValue: Double, styleParameterID: String}`.
- source evidence: `DSP_NODES.md` (init-функции 0x27226d284..0x27226e558), аудит: `allCases`
  0x27226c8b0 (`puVar2[2]=0x1d`, capacity 0x3a), rodata-пары (EXACT).
- Android implementation: `enum class DspParamId` + статическая `ParameterTable` (Kotlin),
  зеркалируемая в C++ (`constexpr std::array<ParamDef, 29>`); единый источник — таблица ниже.
- difficulty: low.
- expected fidelity: 100% (значения), 100% по составу.
- performance risks: нет (compile-time).
- fallback: n/a.

### Полная таблица 29 параметров (EXACT: id / styleParameterID / range / default)

| # | Swift case | id | styleParameterID | range | default |
|---|---|---|---|---|---|
| 1 | inputMixerVolume | Ga1g | player_gain | 0.0…1.0 | 1.0 |
| 2 | multibandFilterCenterBandwidth | Fbw1 | aufilter_center_bandwidth | 0.05…3.0 | 2.0 |
| 3 | multibandFilterCenterGain | Fcg1 | aufilter_center_gain | -18.0…18.0 | 0.0 |
| 4 | multibandFilterCenterFrequency | Fcf1 | aufilter_center_freq | 10.0…21829.5 | 2500.0 |
| 5 | highPassFilterCutoffFrequency | HP1f | hp_cutoff_freq | 10.0…22050.0 | 10.0 |
| 6 | highPassFilterResonance | HP1r | hp_reso | -20.0…40.01 | 0.0 |
| 7 | lowPassFilterCutoffFrequency | LP1f | lp_cutoff_freq | 10.0…21829.5 | 22000.0 |
| 8 | lowPassFilterResonance | LP1r | lp_reso | -20.0…40.01 | 0.0 |
| 9 | auxEffectsBusSendMixerVolume | Ga2g | send_mixer_gain | 0.0…1.0 | 1.0 |
| 10 | delayDelayTime | DLdt | fx_delay_delay_time | 0.0001…2.01 | 1.0 |
| 11 | delayLowPassFilterCutoffFrequency | DLlf | fx_delay_lp_cutoff_frequency | 10.0…22050.0 | 2500.0 |
| 12 | delayDryWetBalance | DLdw | fx_delay_dry_wet | 0.0…100.0 | 0.0 |
| 13 | delayFeedback | DLfb | fx_delay_feedback | -99.9…99.9 | 50.0 |
| 14 | reverbGain | RVga | fx_reverb_gain | -20.0…20.01 | 1.0 |
| 15 | reverbDryWetBalance | RVdw | fx_reverb_dry_wet | 0.0…100.0 | 0.0 |
| 16 | reverbMinimumDelayTime | RVmi | fx_reverb_min_delay_time | 0.0001…1.0 | 0.008 |
| 17 | reverbMaximumDelayTime | RVma | fx_reverb_max_delay_time | 0.0001…1.0 | 0.05 |
| 18 | reverbLowFrequencyDecayTime | RVlf | fx_reverb_low_frequency_decay_time | 0.001…20.0 | 1.0 |
| 19 | reverbHighFrequencyDecayTime | RVhf | fx_reverb_high_frequency_decay_time | 0.001…20.0 | 0.5 |
| 20 | reverbReflectionsRandomization | RVrr | fx_reverb_randomize_reflections | 1.0…1000.0 | 1.0 |
| 21 | auxEffectsBusHighPassFilterCutoffFrequency | HP2f | fx_hp_cutoff_freq | 10.0…22050.0 | 10.0 |
| 22 | auxEffectsBusHighPassFilterResonance | HP2r | fx_hp_reso | -20.0…40.0 | 0.0 |
| 23 | auxEffectsBusLowPassFilterCutoffFrequency | LP2f | fx_lp_cutoff_freq | 10.0…21829.5 | 22000.0 |
| 24 | auxEffectsBusLowPassFilterResonance | LP2r | fx_lp_reso | -20.0…40.0 | 0.0 |
| 25 | auxEffectsBusReturnMixerDryVolume | Ga3g | fx_mixer_dry | 0.0…1.0 | 1.0 |
| 26 | auxEffectsBusReturnMixerWetVolume | Ga4g | fx_mixer_wet | 0.0…1.0 | 0.0 |
| 27 | timeStretchingRate | ts_rate | ts_rate | 0.03125…32.0 | 1.0 |
| 28 | outputMixerVolume | out_gain | out_gain | 0.0…1.0 | 0.0 |
| 29 | effectBypassingState | bypa | bypa | 0.0…1.0 | 1.0 |

Примечания (важные для порта):
- `out_gain` default **0.0** — вероятно, граф стартует «закрытым» и открывается автоматизацией;
  Android-граф при отсутствии автоматизации должен явно ставить 1.0 (иначе тишина) — это
  осознанное отклонение от default, помеченное `[INFERRED]`.
- `bypa` default 1.0 = эффекты активны, 0.0 = байпас `[STRONG_INFERENCE]`.
- `fx_reverb_min_delay_time` default 0.008, `max` 0.05 — подтверждены (`DSP_CONSTANTS` §1 #16/#17).
- Резонанс — в dB (не Q); Q=0.707 в AutoMix-бинарниках отсутствует (RETRACTED R3/D13).

---

## 3. Компонент: Node graph (C++)

- iOS behavior: `AVAudioMixProcessingEffect initWithDSPGraphText:properties:parameterSchedule:
  identifier:` + `CADSPGraphCreateWithModel/LoadStrip/SetParameter/ProcessPCMData` (libAudioDSP);
  текст `.dspg` в корпусе отсутствует.
- source evidence: `DSP_GRAPH_ARCHITECTURE.md` §2/§4 (EXACT symbols), §1 topology STRONG_INFERENCE.
- Android implementation (описание классов, без кода):
  `DspGraph` (владеет узлами и порядком обработки), `GraphNode` (интерфейс `prepare(fs, block)`,
  `process(const float* const* in, float* const* out, int frames)`, `setParam(id, value)`),
  `GainNode`, `BiquadNode` (`HighPass`, `LowPass`), `MultibandNode` (cascade из 2 biquad:
  peak-секция по center/bandwidth/gain; см. §4), `DelayNode` (линия + внутренний LP + feedback +
  dry/wet), `ReverbNode` (FDN/Шрёдер-подобный с настройками Reverb2-типа), `MixerNode`,
  `BypassNode` (проверка `bypa`), `TimeStretchNode` (опционально; см. §5).
  Граф: `TrackGraph` ×2 (outgoing/incoming) + `AuxBusGraph` (shared или per-track — флаг
  `automix.auxBus`). Все — float32, non-interleaved или interleaved (выбрать одно; интерфейс
  Media3 `AudioProcessor` даёт interleaved PCM16/float — см. `MEDIA3_INTEGRATION.md`).
- difficulty: high.
- expected fidelity: 60–70% (ручки EXACT, топология INFERRED, типы фильтров NOT FOUND).
- performance risks: два трековых графа + aux; при `shared` — межпоточная синхронизация двух
  рендереров (см. риски); запрет offload.
- fallback: `bypa`/passthrough (`AudioProcessor.isActive()==false` при отключённом DSP).

### 3.1. Filter designs (NOT FOUND — фиксировать как решение, не как Apple)

- iOS behavior: неизвестно (нет `.dspg`; строк `cutoff_frequency|_resonance|center_frequency`
  в бинарниках нет).
- source evidence: `DSP_FILTERS.md` §2 (NOT FOUND), D13.
- Android implementation: bilinear-transform biquad (RBJ-подобный) с маппингом resonance dB → Q:
  `Q = 10^(resonanceDb/20)` (INFERRED, экспериментально подбираемо), clamp Q; НЕ заявлять как
  Apple-формулу. Multiband `bandwidth` 0.05..3.0 трактовать как октавы (INFERRED).
  Коэффициенты пересчитывать на block-boundary, не на кадр.
- difficulty: medium.
- expected fidelity: 50% (звук похож, но не идентичен).
- performance risks: стабильность при Q→max и cutoff→Nyquist (clamp 0.45·fs).
- fallback: одно-полосный shelf/peak.

---

## 4. Компонент: Automation evaluator

- iOS behavior: кусочно-линейная интерполяция по `AutomationRamp` c `curveByte` (offset 0x20):
  сегмент `{startValue@0, endValue@8, t0@0x10, t1@0x18, curveByte@0x20}`;
  `if t1 <= t → endValue; if t0 >= t → startValue; p=(t−t0)/(t1−t0)` clamp; `value = start +
  y·(end−start)`.
- source evidence: `DSP_AUTOMATION.md` §3.2 / `DSP_CONSTANTS.md` §4 (EXACT instruction sequence).
- Android implementation: `AutomationRamp(t0, t1, startValue, endValue, curve)` +
  `AutomationEvaluator.evaluate(ramp, t)`; C++-зеркало `eval_ramp(ramp, t)`.
- difficulty: low.
- expected fidelity: 90%.
- performance risks: вычисления на аудио-потоке — только по активному сегменту, без поиска/аллокаций.
- fallback: linear.

### 4.1. Таблица кривых (EXACT формулы)

| curveByte | id-строка | формула y(p) | статус формулы |
|---|---|---|---|
| 0x00 | "ease-in-0.5" | `1 − sqrt(1 − p)` | EXACT (инструкции) |
| 0x01 | "ease-in-2" | `p·p` | EXACT |
| 0x02–0x3f | "ease-in-4" | `pow(p, 4.0)` | EXACT |
| 0x40 | "ease-out-0.5" | `sqrt(p)` | EXACT |
| 0x41 | "ease-out-2" | `1 − (1−p)²` | EXACT |
| 0x42–0x7f | "ease-out-4" | `1 − pow(1−p, 4.0)` | EXACT |
| 0x80 | "linear" | `p` | EXACT |
| 0x81 | "logarithmic" | `f(start)+p·(f(end)−f(start))` → `g(·)` (f/g не идентифицированы) | структура EXACT, формулы f/g NOT FOUND |
| 0x82–0xff | "logarithmic" (тот же id) | наблюдается `p` (спец-веток нет) | EXACT по наблюдаемому поведению |

Важно: `AutomationCurveEasingStyle.id` = строки "0.5"/"2"/"4" (EXACT); **наблюдение**
(`DSP_AUTOMATION` §3.2): id «ease-in-*» соответствует complementary-формуле, «ease-out-*» —
прямой; номенклатура может быть инвертирована `[INFERRED]`. В коде — таблица по `curveByte`,
не по строке.

### 4.2. Компонент: расписания (SteppedSchedule)

- iOS behavior: `SteppedSchedule`: `defaultStepDuration = 0.2 s` (0x3fc999999999999a @0x2722a1010),
  `validStepDurationRange = 0.0001…1.0` (0x3f1a36e2eb1c432d / fmov 1.0); поля
  `automationSchedule`, `timeStretchingSchedule`, `playbackAlignmentSchedule`,
  `Automation {parameter, points, ramps}`; `TimeStretching{incomingSongSteps,outgoingSongSteps}`,
  `TimeStretchingStep{playbackRate,timeRange}`; `PlaybackTime{songTime,stretchedSongTime,
  transitionTime}`.
- source evidence: `DSP_AUTOMATION.md` §4 (EXACT).
- Android implementation: `SteppedAutomation` компилирует plan в сетку шага `stepSeconds`
  (кламп в 0.0001..1.0), на каждом шаге — целевой `ParameterFrame` (плоский массив `[paramId] →
  value`); между шагами — линейная интерполяция evaluator-ом (если требуется). Диапазон 0.2 s —
  Apple-дефолт; Android-компилятор может использовать меньший шаг, но контракт шага сохраняется.
- difficulty: medium.
- expected fidelity: 80%.
- performance risks: память сетки; лишние `setParam` — фильтровать изменения (dirty-маска).
- fallback: `stepSeconds=0.2`.

### 4.3. Компонент: ContinuousSchedule

- iOS behavior: `ContinuousSchedule.Automation {parameter, points, ramps, startValue, endValue,
  songTimeRange?}`, `AutomationPoint {value, songTime, curve}`, `AutomationRamp {startValue,
  endValue, songTimeRange, curve}`; `SongSchedule {songTimeRange, transitionTimeRange,
  referenceSongTime, referencePlaybackTime}`, `playbackRate(at:)` 0x272278c90, `songTime(at:)`
  0x272278fe4.
- source evidence: `DSP_AUTOMATION.md` §3 (EXACT symbols/поля).
- Android implementation: `ContinuousAutomation` — список `AutomationRamp` на шкале `songTime`
  (µs); `ContinuousEvaluator.value(parameter, songTimeUs)`.
- difficulty: medium.
- expected fidelity: 85%.
- performance risks: бинарный поиск сегмента — O(log n), кэш последнего индекса.
- fallback: stepped.

---

## 5. Компонент: Time-stretch

- iOS behavior: параметр `ts_rate` 0.03125…32.0 default 1.0 (EXACT). Rate-расписания:
  `ContinuousSchedule.SongSchedule.playbackRate(at:)/playbackRates(at:)`,
  `SteppedSchedule.TimeStretchingStep.playbackRate/timeRange`; iOS-слой передаёт
  `SmartTransitionSongData.speedRampMappings [CMTimeMapping]?` и
  `averagePlaybackRateAsDominantTrack` в `_MPCPlaybackEnginePlayer.smartTransitionWillBegin…`.
  Исполнитель растяжения (граф vs AQ TimePitch vs AVFoundation unit) — NOT FOUND; phase vocoder —
  UNVERIFIED (не REJECTED).
- source evidence: `DSP_TIMESTRETCH.md` §1–§3 (EXACT имена; исполнитель NOT FOUND).
- Android implementation: `TimeStretchSpec(rateRamps: List<RateRamp>, averagePrePivotRate)`.
  Приоритетная реализация — Media3 `SpeedChangingAudioProcessor` + собственный `SpeedProvider`
  (уже есть в форке, кусочно-постоянный, `getNextSpeedChangeTimeUs`); spectral-качество — отдельный
  C++-узел позже. Клампить в [0.03125, 32.0] (Apple-диапазон EXACT).
- difficulty: high.
- expected fidelity: 60% (контракт/диапазон EXACT; качество и место — нет).
- performance risks: time-domain `Sonic` артефакты на больших rate; ресемплинг до финального SRC;
  AudioTrack может пересоздаться при смене playback parameters (проверять на устройстве).
- fallback: rate=1.0, переход без растяжения (complexity ≤ crossFadeWithEffects).

---

## 6. Компонент: Parameter ID mapping (caveat)

- iOS behavior: `parameterSchedule: [CMTime: [UInt32: Float]]`; id параметров имеют вид 4-символьных
  кодов ("Ga1g", "HP1f", "ts_rate" — 7 символов!), но прямой конвертации `id → UInt32` в
  AutoMix-функциях НЕ найдено; UInt32-ключи графа — внешние данные (`_DAT_2780e3c50`), NOT FOUND.
- source evidence: `DSP_AUTOMATION.md` §5 (EXACT тип, NOT FOUND данные); `DSP_NODES.md`.
- Android implementation: **не изобретать четырехсимвольный UInt32-маппинг**. Собственный
  стабильный `DspParamId` (0..28 в порядке таблицы §2, зафиксирован и версионирован), JNI передаёт
  `int id + float value`. `styleParameterID`-строки — человекочитаемый контракт для JSON/логов
  (`TransitionStyles`-совместимость), но не ключ графа.
- difficulty: low.
- expected fidelity: n/a (Apple-ключи невосстановимы).
- performance risks: нет.
- fallback: n/a.

---

## 7. Компонент: Scheduling и передача событий в нативный поток

- iOS behavior: дискретное расписание `[CMTime:[UInt32:Float]]` → `AVAudioMixProcessingEffect`
  parameterSchedule; крупная сетка 0.2 s из `SteppedSchedule`.
- source evidence: `DSP_AUTOMATION.md` §5 (EXACT структура).
- Android implementation: два уровня точности (см. `MEDIA3_INTEGRATION.md` §4):
  (a) `PlayerMessage` для дискретных событий по сетке; (b) предпочтительно `DspParameterProvider`
  по образцу `SpeedProvider` — возвращает `ParameterFrame(timeUs, flatPairs)` sample-accurate.
  В JNI — lock-free SPSC ring buffer (`ParameterCommand{frameIndex, paramId, value}`);
  `process()` применяет команды в начале кадра, по достижении frame index.
- difficulty: medium.
- expected fidelity: 90% (механика), данные — частично.
- performance risks: ring overflow → дроп команды (без блокировки); не аллоцировать в process.
- fallback: PlayerMessage.

---

## 8. Buffer / sample-rate considerations

1. **Sample rate aware:** верхние границы — 22050 Hz (HP) и 21829.5 Hz (LP) = 0.99 × 22050.
   22050 = Nyquist при 44.1 кГц. Для fs ≠ 44.1 кГц клампить cutoff в `min(value, 0.99·fs/2)`
   (LP) и `min(value, fs/2)` (HP); при 48 кГц реальный предел — 24000, при 32 кГц — 16000.
   Соотношение с 0.99 — EXACT для 44.1; масштабирование `[INFERRED]`.
2. **Формат:** весь DSP — float32 (Media3 `ToFloatPcmAudioProcessor`); PCM16 конвертируется до
   графа и обратно после, если sink не float. `setEnableFloatOutput(true)` обязателен.
3. **Offload/passthrough:** отключать; кастомная обработка несовместима с offload.
4. **Block size:** проектировать от 1024 кадров (реальный audio-callback может быть 128–4096);
   без зависимости от размера блока (reverb/delay — фиксированные буферы).
5. **Delay/reverb буферы:** delay time до 2.01 s, reverb max delay до 1.0 s → предвыделять по
   максимальным значениям; менять время delay без кликов (crossfade двух читающих указателей —
   INFERRED-решение).
6. **Denormals:** включить FTZ/DAZ (flush-to-zero) в C++ — иначе reverb tail убьёт CPU.
7. **Двойной рендеринг:** два AudioTrack (outgoing/incoming) суммируются в аналоге; при shared aux
   нужен общий инстанс графа, иначе Energy/фаза могут отличаться от Apple (риск №1).

---

## 9. Компонент: JNI-контракт (описание)

- iOS behavior: `CADSPGraphSetParameter/ProcessPCMData` (EXACT symbols).
- source evidence: `DSP_GRAPH_ARCHITECTURE.md` §4.
- Android implementation (имена функций, без кода): `nativeCreate(sampleRate, maxBlock)`,
  `nativeDestroy()`, `nativeConfigure(graphSpecJson)` (до старта, не на audio thread),
  `nativeQueueParameter(paramId, value, frameIndex)`,
  `nativeQueueParameterFrame(frameIndex, intArray ids, floatArray values)`,
  `nativeProcess(input: FloatArray, output: FloatArray, frames)` (или direct ByteBuffer для zero-copy),
  `nativeReset()`, `nativeBypass(enabled)`. Все вызовы, кроме configure/create, безопасны на audio
  thread; ошибки возвращаются кодами и логируются на Java-стороне (стиль `AutoMixNativeEngine`:
  любой сбой не должен ронять приложение).
- difficulty: medium.
- expected fidelity: 90%.
- performance risks: JNI-граница на каждый буфер (копирование массивов) — использовать
  `ByteBuffer.allocateDirect`/`FloatBuffer` и обрабатывать in-place.
- fallback: `automix.dsp=off`.

---

## 10. Тест-план DSP

1. Eval-таблица: по всем `curveByte` 0x00..0xff сверить значения evaluator с формулами §4.1
   (для 0x81 — только границы 0/1).
2. Диапазоны: любое присвоение clamp-ится в пары §2; `ts_rate` clamp [0.03125, 32.0].
3. Null-test: `bypa=0` ⇒ выход бит-в-бит равен входу (без денормальных хвостов).
4. Biquad: АЧХ плана (sine sweep) — отсутствие резонансного взрыва на границах cutoff/reso.
5. Delay/Reverb: стабильность на max feedback/decay (20 s) без NaN/Inf; FTZ включён.
6. Automation: клик-тест на 0.2 s сетке — отсутствие zipper noise (линейная интерполяция между
   шагами для гейнов).
7. Sample rate: прогон на 44.1/48 кГц — корректный кламп Nyquist.
8. Многопоточность: параметр-команды под нагрузкой (10k/с) — без дропов/гонок (TSan).

## NOT FOUND / OPEN

- Тип/порядок фильтров и структура каскадов (нет `.dspg`).
- AU subtype для delay/reverb/multiband (только совпадение ручек).
- Топология: per-track vs single mixer, точные edges графа.
- UInt32-ключи параметров графа и сам `parameterSchedule` по умолчанию.
- Формула `logarithmic` (curveByte 0x81) — f/g не идентифицированы.
- Место time-stretch: узел графа vs AQ TimePitch vs AVFoundation time-pitch unit.
- Реальные значения автоматизации (свипы HP/LP/гейнов) — каталог `TransitionStyles` отсутствует.
- Семантика `out_gain` default 0.0 (когда и кем открывается).
- Фактический алгоритм `preferredAudioTimePitchAlgorithm` для AutoMix.
- Числовые веса `tile_simd_blur`/`downsample_blur` (в этом файле не нужны, но всплывают в glass).
