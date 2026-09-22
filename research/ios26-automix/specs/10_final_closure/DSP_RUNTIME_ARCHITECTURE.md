# DSP_RUNTIME_ARCHITECTURE.md — runtime-контур SmartTransitions DSP (iOS 26.0)

**Subagent:** Final Closure: DSP Runtime Analyst. **Дата:** 2026-09-13.
**Constraint compliance:** research only; `/root/LMG-VK` и app-код не изменялись; запись только под
`deepseek_analysis/10_final_closure/` (+ append в `indexes/KEY_SYMBOLS_02_dsp.tsv`).
Все выводы — со ссылками на адрес/символ/строку. Runtime-only значения константами не объявляются.

**Закрываемые задачи:** A (`bypa` polarity), B (позиция time-stretch), C (`out_gain`), D (per-track DSP instances).

**Источники (raw evidence):**
- `appos/extracted/System/Library/PrivateFrameworks/_SonicKit_MusicKit_Packages.framework/{DSPGraph.dspg,TransitionStyles.json}`;
- `/srv/research/tmp/extracted_dylibs/{AudioToolbox,AudioToolboxCore,libAudioDSP.dylib,MediaPlaybackCore,_SonicKit_MusicKit,_SonicKit_MusicKit_Packages,SonicKit}`;
- corpus `decompiled_package/automix/{pseudocode,disassembly,symbols,types}`;
- DSC subcaches `.33/.34/.70/.71/.75/.76` (`/tmp/opencode/dscraw/...`): shared-selector region (`__OBJC_RO`),
  CFString-объекты AVFCore, `setSpeedRamp:`-селекторы (проверено чтением байтов; см. §2);
- llvm-objdump-19, llvm-nm-19.

Статусы: `[EXACT]`, `[STRONG_INFERENCE]`, `[PARTIAL]`, `[INFERRED]`, `[NOT FOUND]` — по правилам SHARED_BRIEFING.

---

## A. `bypa` polarity (property 21)

### A.1. Ресурс графа (EXACT)

```
Source file: appos/extracted/.../_SonicKit_MusicKit_Packages.framework/DSPGraph.dspg
param bypa 1 in
box BypassProperty property_cast 0 0
wireGraphParam bypa (BypassProperty 0 input)
wireProperty (BypassProperty ui32 output) (AUFilter 21 global)
... то же для AUHipass1/2, AULowpass1/2, AUDelay, AUReverb
```
Default `bypa = 1`; property ID 21 = `kAudioUnitProperty_BypassEffect` (UInt32, Global) `[EXACT]/[EXTERNAL]`.
Один `bypa` управляет всеми 7 эффект-AU; Gain-boxes не байпасятся. `[EXACT]`

### A.2. Полный механизм записи `bypa` → property 21 (EXACT, AudioToolboxCore)

1) `property_cast` — это `CalculationBox` с зарегистрированным оператором 8:
```
Source file: targets/AudioToolboxCore (raw disasm, llvm-objdump)
Function: NewBoxRegistry::NewBoxRegistry (0x18f3893c8), регистрация: adrp @0x18f38a69c, вызов @0x18f38a6bc
raw value: x1 = "property_cast" (VMA 0x18f6a97a4); w3=0 (OperatorDomain=parameter);
           w4=1 (OperatorCodomain=property); w5=8 (Operator=cast)
Status: EXACT
```
2) Оператор 8 = **прямая копия 16-байтового Value без инверсии** (`CalculationBox::calculate`, jump-table @0x18f3c1558, entry 8 → 0x18f3c1054):
```
18f3c1054: ldr x8,[x20,#0x338]   ; число входов
18f3c1058: cbz x8,<assert>
18f3c105c: add x8,x20,#0x318     ; массив входных Value
18f3c1060: ldr q0,[x8]
18f3c1064: str q0,[x19]          ; result = input[0]
Status: EXACT
```
3) При чтении property-endpoint `(BypassProperty ui32 output)` выполняется конверсия Value→u32:
```
Source file: targets/AudioToolboxCore
Function: CalculationBox::getValueAsProperty (0x18f4e2ea0), ветка "ui32" (0x18f4e2f4c)
double→u32: dispatcher Lm1 @0x18f4e3840:  ldr d0,[x1]; fcvtzu w0,d0; ret
(long long→u32): dispatcher Lm0 @0x18f4e384c: ldr w0,[x1]
Status: EXACT
```
Т.е. `bypa = 1.0` (Double) численно превращается в `UInt32 1`; `0.0` → `0`. Никакой инверсии в cast нет.

4) `AUBox::setProperty` (форвард в AU) и `AUBox::isBypassed`:
```
Function: DSPGraph::AUBox::setProperty @0x18f37fab4:  b _AudioUnitSetProperty
Function: DSPGraph::AUBox::isBypassed   @0x18f5bbcd0:
    GetProperty(21,0,0) → value; return (status==0 && value!=0)
Function: DSPGraph::AUBox::bypass(bool) @0x18f37fa54: SetProperty(21,0,0,size=4,&bool)
Status: EXACT
```
`isBypassed == true` ⇔ property 21 != 0.

### A.3. Семантика AU-уровня (EXACT, libAudioDSP)

```
Source file: targets/libAudioDSP.dylib
Function: ausdk::AUEffectBase::SetProperty (0x1dce09428), ветка property 0x15 (=21):
  0x1dce09460: ldr w20,[x4]        ; value
  0x1dce09464: cmp w20,#0
  0x1dce09468: cset w9,ne         ; newBypass = (value != 0)
  ... SetBypassEffect(newBypass)  ; vtable+0x248
Function: GetProperty (0x1dce09530), property 0x15: возвращает [x0+0x228] (bool) как UInt32
Function: SetBypassEffect(bool) @0x1dc9c706c: strb w1,[x0,#0x228]
Function: ShouldBypassEffect @0x1dc9c7064: ldrb w0,[x0,#0x228]
Status: EXACT
```
Публичная конвенция `kAudioUnitProperty_BypassEffect` (1 = обход, 0 = обработка) `[EXTERNAL]`
совпадает с обеими независимыми реализациями (DSPGraph `AUBox::isBypassed` и `ausdk::AUEffectBase`).

### A.4. Дефолты и callsites

```
Source file: targets/_SonicKit_MusicKit_Packages
Function: effectBypassingState getter @0x27226e558 → lazy init FUN_27226e534:
  0x27226e53c: mov w9,#0x7962; movk w9,#0x6170,lsl16   ; little-endian "bypa"
  0x27226ed30: mov x11,#0x3ff0000000000000             ; defaultValue = 1.0 (Double)
  range q0 @0x27229e1a0 = {0.0,1.0}; styleParameterID = "bypa"
Status: EXACT
```
- `TransitionStyles.json`: `bypa` не автоматизируется ни одним стилем (0 вхождений) `[EXACT]` (APPOS_TRANSITION_STYLES).
- `DefaultDSPParameterProvider.smartTransitionParameterSchedule` (lazy init FUN_2721af178) засеивает
  словарь `[CMTime:[UInt32:Float]]` дефолтами всех `allCases`; id→UInt32 выполняется через
  `String.fourChars`-подобный конвертер (island 0x2743dbe40; проверка Optional-тега `tbnz x26,#0x20` @0x2721af3d0),
  поэтому `ts_rate`/`out_gain` (len≠4) пропускаются, а `bypa` попадает со значением 1.0 `[EXACT по поведению]`.
- Callsite, который писал бы `bypa=0` (или fourCC `0x62797061`): **не найден** ни в `_SonicKit_MusicKit`,
  ни в `_SonicKit_MusicKit_Packages`, ни в `MediaPlaybackCore` (raw-скан + disasm). В MediaPlaybackCore
  найдены только строки `bypassCache`/`bypassing` — к параметру не относятся. `[NOT FOUND]`
- Логов про bypass/AU property 21 в бинарях нет. `[NOT FOUND]`

### A.5. Вердикт по `bypa`

| bypa (param/schedule) | property 21 | фактическое состояние эффект-AU |
|---|---|---|
| **1.0** | 1 | **Bypassed** (эффект не обрабатывает) `[EXACT]` |
| **0.0** | 0 | **Active** (обработка включена) `[EXACT]` |

**Следствие:** при засеве расписания дефолтами (`bypa=1.0`) все 7 эффект-AU (AUFilter, AUHipass1/2,
AULowpass1/2, AUDelay, AUReverb) обходятся, и каталог `TransitionStyles` это состояние **не меняет**
(0 автоматик). Статически ни один callsite не пишет `bypa=0`. Т.е. фильтры/delay/reverb графа при
штатном каталоге не вносят вклада. Это не «полярность наоборот», а именно следствие дефолта 1.0.
Статус следствия: `[STRONG_INFERENCE]` (нужен runtime-dump значения property 21 / параметра `bypa`; см. NOT FOUND).
Ранее APPOS_DSPGRAPH §7 держал полярность как `[UNVERIFIED]` — снимается: полярность `[EXACT]`,
открытым остаётся только «пишет ли runtime 0».

---

## B. Позиция time-stretch (Task B)

### B.1. `ts_rate` не доставляется через DSPGraph (EXACT)

```
Source file: targets/_SonicKit_MusicKit
Function: FUN_2721b5788 (сборка parameterSchedule для эффекта), 0x2721b5xxx:
  - читает Automation.parameter (island 0x2743da990) и AutomationEffectParameter.id (island 0x2743dabb0)
  - конвертирует id → Optional<UInt32> (island 0x2743dbe40 = fourChars-класс: nil для строки длиной != 4)
  - при nil пропускает параметр (tbnz #0x20) — так выпадают `ts_rate` (7) и `out_gain` (8)
Status: EXACT (по коду и по типу словаря [CMTime:[UInt32:Float]])
```
`ts_rate` в `DSPGraph.dspg` отсутствует; в графе 27 параметров из 29 (APPOS_DSPGRAPH §6). `[EXACT]`

### B.2. Алгоритм и исполнитель time-pitch (EXACT)

```
Source file: targets/MediaPlaybackCore
Function: -[MPAVItem preferredAudioTimePitchAlgorithm] (P1: 0x1c4f80328)
CFString-объекты (проверено чтением DSC .33.dylddata):
  0x1f0d0c1a0: flags=0x7C8, cstr ptr=0x195e8276a, length=8  → "Spectral"
  0x1f0d0c180: flags=0x7C8, cstr ptr=0x195e8275f, length=10 → "TimeDomain"
Установка: configureQueueItem:playerItem:error: → AVPlayerItem.audioTimePitchAlgorithm := Spectral
Status: EXACT
```
Исполнитель — ME TimePitch внутри `MEMixerChannel` (AudioToolbox):

```
Source file: targets/AudioToolbox
Function: MEMixerChannel::RebuildMixerChannelChain(MEProcessorID) @0x1b8f5428c
  четыреCC-диспетчер процессоров; в ветке "tmpt" (TimePitch) @0x1b8f54760-0x1b8f547b4:
    alloc XProcessingInsertBase (0x1b8f55f88) с callback
    TimePitchUpstream (0x1b8f5f248), контекст = MEMixerChannel
    → сохраняется в [channel+0x308]
Function: MEMixerChannel::DisconnectReconfigureAddNode(...) @0x1b8f5613c
  @0x1b8f58128: XProcessingInsertBase с callback TimePitchDownstream (0x1b8f5f5a4)
    → сохраняется в [channel+0x310]
Function: MEMixerChannel::TimePitchState::GenerateUpstreamTimeStamp @0x1b8f5f46c
  вызовы: из TimePitchUpstream @0x1b8f5f2ac и TimePitchDownstream @0x1b8f5f7a8
Function: MEMixerChannel::TimePitchSampleTimes @0x1b8f68980
Function: TimePitchDownstream использует ParameterSchedule::Event:
  lambda @0x1b8f62790 (std::function<bool(const ParameterSchedule::Event*)>)
Log strings (targets/AudioToolbox, __cstring):
  "%25s:%-5d AQ Time-stretcher: ionc@%p SchedTimePitchAddRateChange %.3f @ %lld"
  "TimePitchRate(%s)", "SchedTimePitchTrackedRateChange %.3f %lld %lld"
Status: EXACT (регистрация callback'ов, timestamps, rate-события)
```
Слот vtable+0x250 в `AUEffectBase::ProcessBufferLists`, ветка пропуска обработки при bypass
(0x1dce09348 `tbnz w0,#0`), согласуется с `ShouldBypassEffect()` `[STRONG_INFERENCE]` (идентичность слота
не доказана номинативно).

### B.3. Как доставляется `ts_rate` (speedRamp) (EXACT по структурам и логам)

```
Source file: targets/_SonicKit_MusicKit_Packages
Transition.SteppedSchedule.TimeStretchingSchedule:
  outgoingSongSteps: [TimeStretchingStep]  @0x272281544
  incomingSongSteps: [TimeStretchingStep]  @0x2722817bc
TimeStretchingStep { playbackRate: Double @0x272282538; timeRange: PlaybackTimeRange @0x27228255c }
Transition.TimeStretchingState { songTime, stretchedSongTime } @0x27226fde0/dec
StretchedSongTime extensions (stretchedSongTimes/Range) @0x272269b08/0x272269c44
Status: EXACT (поля и типы)

Source file: targets/_SonicKit_MusicKit
SmartTransitionSongData.speedRampMappings: [CMTimeMapping]?  @0x2721afa80
Status: EXACT (тип [CMTimeMapping]? — CoreMedia CMTimeMapping)

Source file: targets/MediaPlaybackCore
FUN_1c4fec0f0: "Setting speedRamp for incoming item - %s with %ld time mappings" (строка ~241)
               "Setting speedRamp for outgoing item - %s with %ld time mappings" (строка ~625)
FUN_1c4f91004: "Setting speedRamps to nil on: %s due to no existing transition"
               "… due to clearTransitionOffsetDataOnCurrentItem with an uninitialized transition"
Строки/селекторы в shared cache (.34.dyldreadonly, проверено байтами):
  0x1fc13b59c  "setSpeedRamp:"
  0x1fc13b6ce  "setSupportsSpeedRamps:"   (вызов из MPC @0x1c5021548 с w2=1)
  0x1fc123098  "canPlaySpeedRamp" / "_AVPlayerCanPlaySpeedRampDidChangeNotification" (MPC, __cstring)
  0x1fc13c23e  "speedRampAdjustabilityMargin"
  0x1fc12c74e  "_updateSpeedRampDataOnFigPlaybackItem"; 0x1fc12ab3d "_speedRampDataWasSet"
Overlapped playback: -[AVQueuePlayer(OverlappedPlayback) mpc_setSupportsAdvanceTimeForOverlappedPlayback:]
  @0x1c51eee1c → селектор 0x1fc13b662 "setSupportsAdvanceTimeForOverlappedPlayback:"
Status: EXACT (логи/селекторы/типы); точная инструкция вызова setSpeedRamp: — NOT FOUND (§6)
```
Цепочка: `ts_rate`-автоматика стиля → `TimeStretchingStep{playbackRate,timeRange}` (per side) →
`SmartTransitionSongData.speedRampMappings:[CMTimeMapping]?` → `SmartTransitionParameters` (MPC) →
`AVPlayerItem.speedRamp` (per item) → AVFCore `_updateSpeedRampDataOnFigPlaybackItem` → FigPlayer →
ME TimePitch (Spectral) c `SchedTimePitchAddRateChange`. `[EXACT по артефактам; точная функция планировщика, режущая автомат в TimeStretchingStep — NOT FOUND]`

### B.4. Итоговый flow (позиция time-stretch)

```text
[decode / audio source item]
   │
   ▼
AVPlayerItem audio processing (item/song time):
   ├─ AVMutableAudioMixInputParameters.volume = out_gain ramps   [EXACT: §C]
   └─ AVAudioMixProcessingEffect = SmartTransitions DSPGraph    [EXACT: §D]
   │
   ▼
ME TimePitch (AVAudioTimePitchAlgorithmSpectral)                  [EXACT: executor]
   ▲ rate changes: AVPlayerItem.speedRamp = [CMTimeMapping]       [EXACT: types+logs]
   │
   ▼
ME mixer bus → output
```
Позиция TimePitch **после** DSPGraph/mix-эффекта: `[STRONG_INFERENCE]`.
Аргументы: (1) и volume-рампы, и DSP-расписание заданы в CMTime/item-time (см. §C/B.5) и
потребляются в media-time (иначе времена не совпадут с медиа при rate≠1); (2) `GenerateUpstreamTimeStamp`
в TimePitchUpstream вычисляет upstream (media) время для downstream-запроса — т.е. источник, включая
mix-эффект, находится выше (upstream) time-pitch. Прямого дизассемблера AVFoundation в корпусе нет →
EXACT-статус для порядка не заявляется.

### B.5. Независимость сторон, синхронизация, time base

| Вопрос | Ответ | Статус |
|---|---|---|
| outgoing/incoming независимые rates | да: `TimeStretchingSchedule.outgoingSongSteps`/`incomingSongSteps` — разные массивы; speedRamp ставится отдельно каждому item'у | EXACT |
| одновременное воспроизведение двух item'ов | да: `AVQueuePlayer(OverlappedPlayback)` + `setSupportsAdvanceTimeForOverlappedPlayback:` | EXACT |
| media/transition позиция | поток: song time → stretched song time (`StretchedSongTime`), далее CMTime | STRONG_INFERENCE |
| синхронизация | тип `Transition.SteppedSchedule.SynchronizedPlaybackTimeRange`; точки `AlignmentTime/transitionStartTime` | PARTIAL (точная формула не трассирована) |
| time base графиков | CMTime со timescale **1e9** (`CMTimeMakeWithSeconds(..., 0x3b9aca00)` @0x2721b3234-40) | EXACT |
| time base TimePitch | `AudioTimeStamp` sampleTime (`SchedTimePitchAddRateChange … @ %lld`; `TimePitchSampleTimes`) | EXACT |

---

## C. `out_gain` (Task C)

### C.1. Параметр (EXACT)

```
Targets: _SonicKit_MusicKit_Packages singleton 0x280c9a0a0, init FUN_27226e49c (P2)
id="out_gain", range {0.0,1.0} (q0 @0x27229e1a0), defaultValue = 0.0 (xzr)
catalog: 21 автоматика во всех стилях кроме 3 и 33 (APPOS_TRANSITION_STYLES §6/#23)
Status: EXACT
```

### C.2. Владелец рампы и её применение (EXACT)

```
Source file: targets/_SonicKit_MusicKit
Function: FUN_2721b2a04 (SmartTransitionData builder для одной стороны; вызывается 2× из FUN_2721b0970)
  1) цикл по Automations: parameter == AutomationEffectParameter.outputMixerVolume
     (islands 0x2743da990 / 0x2743dab90 / 0x2743daba0) → собирает AutomationRamp-массив
  2) для каждого ramp:
       0x2721b3238-0x2721b3240: island 0x2743dc320(d0=seconds, w0=0x3b9aca00=1_000_000_000)
           → возвращает CMTime (x0,x1,x2); константа timescale — EXACT, имя функции — STRONG_INFERENCE
       0x2721b326c: CMTimeRange(s)  (хелпер)
       0x2721b3270-0x2721b328c: objc_msgSend(sel=0x1fc13bb7b "setVolumeRampFromStartVolume:toEndVolume:timeRange:")
           s0=s8 (start volume, float), s1=s9 (end volume, float)
  второй вызов того же селектора: 0x2721b356c-0x2721b359c (второй путь/цикл)
Target object: AVMutableAudioMixInputParameters (создан через "audioMixInputParametersWithTrack:"
  0x1fc12e5f1 и "setTrackID:" 0x1fb8e1767; P2)
Status: EXACT
```
- **Владелец рампы:** `_SonicKit_MusicKit` (SmartTransitionData builder) — не граф и не MPC.
- **Единицы:** линейный множитель громкости `Float` 0.0…1.0 (`AVMutableAudioMixInputParameters.volume`),
  значения берутся из `out_gain`-автоматик стилей (1→0 / 0→1) как Double→Float. `[EXACT]`
- **Времена:** `Automation.startTime/endTime` планировщика → CMTime (секунды, timescale 1e9) → CMTimeRange.
  Отображение normalized/offset из TransitionStyles в абсолютные секунды делает планировщик (частично
  трассировано APPOS_TRANSITION_STYLES; `entryOffsetUs` runtime — PARTIAL). `[EXACT для CMTime/вызова; PARTIAL для маппинга стиля]`
- **Независимые incoming/outgoing рампы:** да — две отдельные `AVAudioMix` (SmartTransitionData
  `outgoingSongAudioMix` @+0x80, `incomingSongAudioMix` @+0x88), каждая строится своим вызовом
  FUN_2721b2a04 со своим schedule. `[EXACT]`
- **До/после микса:** до микса — volume задаётся на per-track `AVAudioMixInputParameters`
  (`setTrackID:`/`audioMixInputParametersWithTrack:`); микшер получает уже обработанный трек. `[EXACT по API-семантике]`
- Третий callsite `setVolumeRampFromStartVolume:toEndVolume:timeRange:` в MediaPlaybackCore
  (@0x1c4fdfa20, селектор 0x1fc13bb7b) — путь MPC (не SmartTransitions-builder); отмечается, не смешивается. `[EXACT]`

---

## D. DSP instance topology (Task D)

### D.1. Две отдельные инстанции (per side / per track) (EXACT)

```
Source file: targets/_SonicKit_MusicKit
FUN_2721b0970 (SmartTransitionData builder) вызывает FUN_2721b2a04 дважды:
  call sites 0x2721b1020 и 0x2721b1070 (декодирование BL по __text)
Каждый вызов FUN_2721b2a04:
   └─ FUN_2721b5368 (call site 0x2721b362c): строит свой parameterSchedule и вызывает
        FUN_2721b6120 @0x2721b5744:
          island 0x2743dc9a0([class @0x2780461b0])  → alloc нового объекта эффекта (x20)
          island 0x2743dbda0 → bridge(dspGraphText)
          island 0x2743dbc90 → bridge(schedule)
          island 0x2743dc040 → bridge(properties)
          x4/x5 = Swift-строка "smartTransitionsGraph" (header 0x2721de320, data 0x2721de340, len 0x15)
          0x2721b6210: objc_msgSend(0x1fc132f9a
             "initWithDSPGraphText:properties:parameterSchedule:identifier:")
   └─ возвращённый эффект добавляется: "addEffect:" (0x1fae51df4) в FUN_2721b2a04 @0x2721b3650
Status: EXACT: два разных вызова → две разные аллокации AVAudioMixProcessingEffect,
        у каждой свой schedule; identifier у обеих одинаковый ("smartTransitionsGraph")
```
`SmartTransitionData` хранит **две** миксы (поля +0x80/+0x88, геттеры 0x2721afb40/0x2721afb68), и MPC
применяет их к outgoing/incoming items (`[ALC] Setting speedRamp … incoming/outgoing item`,
`setAudioMix:`; teardown: `Tearing down transition`).

### D.2. Lifecycle

| Фаза | Механизм | Статус |
|---|---|---|
| create | `_SonicKit_MusicKit` SmartTransitionRenderer/Data builder (FUN_2721a2234 → FUN_2721b0970 → 2×FUN_2721b2a04 → FUN_2721b5368→FUN_2721b6120) | EXACT |
| attach | MPC: `setAudioMix:`/`audioMixInputParametersWithTrack:`; аудио-миксы в SmartTransitionData | EXACT |
| schedules | per side: отдельные словари `[CMTime:[UInt32:Float]]` (два вызова FUN_2721b5368) | EXACT |
| aux state | Per-instance: две отдельные DSPGraph-инстанции, следовательно Delay/Reverb-состояние не общее | EXACT (структурно) |
| reset/destroy | жизненный цикл объекта — AVFoundation (объект в AVAudioMix элемента); при teardown transition/смене item аудио-миксы снимаются | PARTIAL (`_speedRampDataWasSet`/reset-логи; явный reset графа не трассирован) |

### D.3. Гипотеза «shared aux bus между треками» — RETRACTED

**Старое:** aux bus (delay/reverb state) может быть общим между outgoing/incoming (гипотеза порта).
**Новое:** на каждый трек строится **отдельный** `AVAudioMixProcessingEffect` (две аллокации, два
parameterSchedule, два `addEffect:`); это исключает общий aux-стейт на уровне приложения.
Identifier-строка общая (`"smartTransitionsGraph"`), но идентификатор — лишь тег, объекты и расписания разные.
Статус ретракции: `[EXACT]` (две инстанции), остаточная неопределённость — внутренняя дедупликация
компиляции графа в AVFoundation (см. NOT FOUND).

---

## RETRACTED / CORRECTED

| # | Где было | Было | Стало |
|---|---|---|---|
| R1 | STATE_RECONCILIATION / P1_PORT_CLOSURE | «aux bus shared между треками» — гипотеза порта | **RETRACTED**: две отдельные AVAudioMixProcessingEffect-инстанции (per outgoing/incoming), §D |
| R2 | APPOS_DSPGRAPH §7 / P1 #19 | `bypa` полярность `[UNVERIFIED]`, default 1 противоречив | Полярность **EXACT**: 1=bypassed, 0=active (§A); следствие «по умолчанию эффекты обходятся» — STRONG_INFERENCE |
| R3 | P1 #21 «роль ts_rate внутри .dspg — BLOCKED» | ts_rate может быть узлом графа | **RESOLVED**: ts_rate в графе отсутствует; доставляется как `AVPlayerItem.speedRamp` (TimeStretchingSchedule → CMTimeMapping) и исполняется ME TimePitch (Spectral), §B |
| R4 | P2 #23 «конкретные значения/времена out_gain — BLOCKED» | времена рамп были BLOCKED | Механизм и конверсия времён EXACT (CMTime 1e9, setVolumeRamp…); маппинг normalized→секунды — PARTIAL (планировщик), §C |
| R5 | P1 #20 «UInt32-ключи расписания = BE fourCC — STRONG_INFERENCE» | не трассировано | Подтверждено поведением: не-4-символьные id отбрасываются fourChars-конвертером до вставки в `[UInt32:Float]`; полярность/значения не зависят от этого; оставлено STRONG_INFERENCE (сам big-endian порядок не дизассемблирован) |

## NOT FOUND / UNVERIFIED

1. **Callsite, устанавливающий `bypa=0`** (или fourCC `0x62797061`) — отсутствует в 16 targets. Как следствие:
   штатный каталог оставляет эффект-AU в bypass. Проверка: LLDB на устройстве (значение property 21 / параметра `bypa`
   в `AVAudioMixProcessingEffect`).
2. **Точная инструкция вызова `setSpeedRamp:`** в MediaPlaybackCore — селектор в shared cache есть
   (0x1fc13b59c), вызов идёт косвенно (не найден adrp+add/селектор-реф); поведение доказано логами/типами.
3. **Функция планировщика, превращающая `ts_rate`-автоматику в `TimeStretchingStep`** — не локализована
   (структуры и поток EXACT, переход automations→steps — NOT FOUND).
4. **Точная формула синхронизации** (alignment/pivot в stretched time) — `SynchronizedPlaybackTimeRange`,
   `Transition.AlignmentTime` найдены как типы; математика не трассирована (PARTIAL).
5. **Teardown/reset DSPGraph-инстанции** внутри AVFoundation — явных вызовов reset графа в corpus нет (PARTIAL).
6. **Внутренняя дедупликация графа по identifier в AVFoundation** — не проверяема статически (нет AVFCore binary в corpus); структурно исключена на уровне объектов.
7. **Логи** про property 21/bypass — отсутствуют (0 совпадений).

---

## Provenance summary (ключевые адреса)

| Артефакт | Binary | Address |
|---|---|---|
| `property_cast` регистрация (op=8) | AudioToolboxCore | 0x18f38a69c (name @0x18f6a97a4) |
| CalculationBox::calculate op8 (copy) | AudioToolboxCore | 0x18f3c1054 |
| Value→ui32 (fcvtzu) | AudioToolboxCore | 0x18f4e3840 |
| AUBox::setProperty / bypass / isBypassed | AudioToolboxCore | 0x18f37fab4 / 0x18f37fa54 / 0x18f5bbcd0 |
| AUEffectBase::SetProperty/GetProperty/ShouldBypass | libAudioDSP | 0x1dce09428 / 0x1dce09530 / 0x1dc9c7064 |
| effectBypassingState default=1.0 | _SonicKit_MusicKit_Packages | 0x27226e534 (store @0x27226ed30) |
| Schedule seeds defaults (allCases) | _SonicKit_MusicKit | FUN_2721af178 (0x2721af3b4-0x2721af3d0) |
| ME TimePitch chain ("tmpt") | AudioToolbox | 0x1b8f54760-0x1b8f547b4 / callback 0x1b8f5f248 / 0x1b8f5f5a4 |
| TimePitchState::GenerateUpstreamTimeStamp | AudioToolbox | 0x1b8f5f46c |
| SchedTimePitchAddRateChange log | AudioToolbox | __cstring |
| Spectral/TimeDomain CFStrings | AVFCore (.33.dylddata) | 0x1f0d0c1a0 (len 8) / 0x1f0d0c180 (len 10) |
| TimeStretchingSchedule steps getters | _SonicKit_MusicKit_Packages | 0x272281544 / 0x2722817bc |
| TimeStretchingStep.playbackRate / timeRange | _SonicKit_MusicKit_Packages | 0x272282538 / 0x27228255c |
| speedRampMappings getter | _SonicKit_MusicKit | 0x2721afa80 |
| MPC speedRamp set (incoming/outgoing) | MediaPlaybackCore | FUN_1c4fec0f0 (logs @lines 241/625) |
| setSpeedRamp: / setSupportsSpeedRamps: selectors | shared cache (.34) | 0x1fc13b59c / 0x1fc13b6ce (call @0x1c5021548) |
| per-side builder (out_gain + effect) | _SonicKit_MusicKit | FUN_2721b2a04 (calls 0x2721b1020/0x2721b1070) |
| effect builder / init call | _SonicKit_MusicKit | FUN_2721b5368 (0x2721b362c) / FUN_2721b6120 (0x2721b6210) |
| identifier "smartTransitionsGraph" | _SonicKit_MusicKit | data 0x2721de340, header 0x2721de320, len 0x15 |
| setVolumeRamp … call sites | _SonicKit_MusicKit | 0x2721b328c / 0x2721b359c (MPC: 0x1c4fdfa20) |
| SmartTransitionData mixes | _SonicKit_MusicKit | +0x80 outgoing / +0x88 incoming (getters 0x2721afb40/b68) |
