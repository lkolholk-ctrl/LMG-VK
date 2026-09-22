# FINAL_RED_TEAM_AUDIT.md — независимый аудит закрытых утверждений фазы 10_final_closure

**Роль:** Final Closure Evidence Auditor (red-team, независимый проход; в анализе не участвовал).
**Дата:** 2026-09-14. **Режим:** research only; `/root/LMG-VK` и код приложения не изменялись;
запись только в `deepseek_analysis/10_final_closure/`. Секреты не использовались и не приводятся.

**Объект аудита:** 10 «закрытых» утверждений фазы 10_final_closure (per-track DSP, TimePitch, out_gain,
planner-caller, constant-power, lyrics-константы, sheet-анимации, vi-nnet, TTML-парсер, #2-closure).

**Метод (всё перепроверено заново, без доверия текстам отчётов):**

| Инструмент | Применение |
|---|---|
| `llvm-nm-19` | символы/границы функций (`_SonicKit_MusicKit`, `_SonicKit_MusicKit_Packages`, `AudioToolbox`, `MediaPlaybackCore`, `MusicUI`) |
| `capstone 5.0.7` (ARM64, skipdata) | дизасм по VA из извлечённых Mach-O (`/srv/research/tmp/extracted_dylibs/*`, `decompiled_package/targets/*`, `appos/extracted/**`) |
| собственный VA-ридер Mach-O (`/tmp/opencode/audit10/vm.py`) + быстрый BL/ADRP-скан (`fastscan.py`) | сплошные сканы `__text/__auth_stubs` + данных |
| DSC-ридер `/tmp/opencode/audit10/dscmap.py` (mapping `u32 offset/count` @0x10, 32B-записи) | subcaches `.03/.11/.13/.33/.34/.70/.71/.75/.76`, selectors/CFStrings/class_ro |
| символьный атлас `/tmp/opencode/dsc/symbols.bin` + `p3work/symlookup.py` | имена функций/методов по VA |
| `numpy`-скан low33-указателей | привязка classref → class → class_ro → name |
| MIL-парсер (regex по `vi-nnet.mil`) | op-histogram, dilation-var'ы, shapes |
| `plistlib`-эквивалент (чтение plist как текста) | `aufx-nnet-appl.plist` |
| `json`-машиносравнение raw↔normalized | `TransitionStyles.json` |

Ключевой негатив среды: **executable-сабкеш `_SonicKit_MusicKit` (`.66`) так и отсутствует**, `dyld_shared_cache_arm64e.13`
(54 образа: MediaServices, AVFCapture, …) содержит **только** свои образы — SonicKit/AVFoundation там нет.
Поэтому острова `0x2743d…`/`0x2780…` в `.66`/`AVFCore`-данных по-прежнему недоступны; это ограничение
относится к нескольким «EXACT»-деталям и отмечено ниже явно.

---

## 1. Per-track DSP-инстанции (две аллокации AVAudioMixProcessingEffect)

**Claim (DSP_RUNTIME_ARCHITECTURE §D):** `FUN_2721b0970` вызывает `FUN_2721b2a04` дважды (0x2721b1020/0x2721b1070);
каждый вызов делает свою аллокацию эффекта через `FUN_2721b5368`→`FUN_2721b6120` (`initWithDSPGraphText:properties:parameterSchedule:identifier:`,
identifier `smartTransitionsGraph`), добавляет `addEffect:`; два результата хранятся раздельно.

**Independent verification:**
- `bl 0x2721b2a04` ровно в 0x2721b1020 и 0x2721b1070; результаты пишутся в `[x19,#0x50]` (первый) и в `x20`
  (второй), затем `[x19,#0x270]`/`[x19,#0x278]` (`vm.disasm` 0x2721b1000..0x2721b10c8). **EXACT.**
- Границы функций (`llvm-nm`): `FUN_2721b2a04` = 0x2721b2a04..0x2721b36fc; внутри — `bl 0x2721b5368` @0x2721b362c
  и `addEffect:` @0x2721b3650 (селектор adrp/add 0x1fae51df4). **EXACT.**
- `FUN_2721b5368` = 0x2721b5368..0x2721b5788; @0x2721b570c-5744: класс из слота `[0x2780461b0]`, аллокация
  (island 0x2743dc9a0), строка `smartTransitionsGraph` (0x2721de340, count 0x15), `bl 0x2721b6120`.
  `FUN_2721b6120` @0x2721b61f4-6210: селектор 0x1fc132f9a, `objc_msgSend`; результат — новый объект. **EXACT.**
- `SmartTransitionData.outgoingSongAudioMix`/`incomingSongAudioMix` — геттеры 0x2721afb40 (ldr [x20,#0x80]) и
  0x2721afb68 (ldr [x20,#0x88]); символы подтверждены `llvm-nm` (`…20outgoingSongAudioMixSo07AVAudioJ0CSgvg`). **EXACT.**
- «Один инстанс переиспользован?» — нет: alloc+init выполняется на каждом из двух путей, результаты независимы;
  кэширования/дедупликации по identifier в коде нет (в `FUN_2721b6120` нет чтения глобалов-кэшей). **EXACT (структурно).**

**Counter-evidence / downgrade:** имя класса, полученного из слота `0x2780461b0` (→ class VA `0x1ee7d65e8`,
class_ro `0x1f0d222e0`, name-ptr `0x195e9e0be`), лежит в **отсутствующем** text-сабкеше (AVFoundation/AVFCore).
Сам факт «объект этого класса» — да; имя `AVAudioMixProcessingEffect` независимо не читается. Селектор
`initWithDSPGraphText:properties:parameterSchedule:identifier:` — публичный API AVFoundation (EXTERNAL),
поэтому идентификация класса = **STRONG_INFERENCE** (в отчёте было EXACT).
Lifecycle reset/teardown графа — как и в отчёте **[PARTIAL]** (не трассирован).

**Final status: EXACT** по «две независимые аллокации/расписания + addEffect + поля +0x80/+0x88»;
**STRONG_INFERENCE** по имени класса; **PARTIAL** по reset/teardown.

---

## 2. Time-stretch position (MEMixerChannel TimePitch)

**Claim (DSP_RUNTIME_ARCHITECTURE §B):** `ts_rate` не идёт через DSPGraph; исполняется ME TimePitch в
`MEMixerChannel` (AudioToolbox); `MPAVItem preferredAudioTimePitchAlgorithm` = `Spectral`; установка в
`configureQueueItem:playerItem:error:`; позиция TimePitch относительно DSPGraph — STRONG_INFERENCE;
AVFoundation binary отсутствует.

**Independent verification:**
- Символы `llvm-nm` AudioToolbox: `MEMixerChannel::RebuildMixerChannelChain` 0x1b8f5428c;
  `TimePitchUpstream` 0x1b8f5f248; `TimePitchDownstream` 0x1b8f5f5a4;
  `TimePitchState::GenerateUpstreamTimeStamp` 0x1b8f5f46c; `TimePitchSampleTimes` 0x1b8f68980;
  `DisconnectReconfigureAddNode` 0x1b8f5613c. **EXACT.**
- Ветка «tmpt»: `mov w9,#0x7074; movk w9,#0x746d` @0x1b8f54760-64 (`"tmpt"`), ленивое создание при
  `[channel+0x308]==0`, `bl 0x1b8f55f88` с callback 0x1b8f5f248 (paciza), затем `str x28,[x20,#0x308]` @0x1b8f54818.
  Аналогично downstream: `ldr x21,[x20,#0x310]`, callback 0x1b8f5f5a4 @0x1b8f58124, `str x21,[x20,#0x310]` @0x1b8f581a0. **EXACT.**
- `TimePitchDownstream` вызывает `0x1b8f5f46c` @0x1b8f5f7a8. **EXACT.**
- Логи `__cstring`: `AQ Time-stretcher: ionc@%p SchedTimePitchAddRateChange %.3f @ %lld`,
  `TimePitchRate(%s)`, `SchedTimePitchTrackedRateChange %.3f %lld %lld`. **EXACT.**
- `MPAVItem preferredAudioTimePitchAlgorithm` (0x1c4f80328 → тело 0x1c4f80318): data-flow
  `[0x1e6b8b290] → [0x1e76630b8] → raw 0x0010000070d0c1a0` = CFString-объект `0x1f0d0c1a0`
  (flags 0x7C8, len **8**). Импорт `_AVAudioTimePitchAlgorithmSpectral` в бинаре есть. **EXACT** (текст строки в
  отсутствующем сабкеше; len 8 + импорт = STRONG).
- `0x1ed0c180` (CFString len **10**) — соседний объект; `0x1ed0c1a0` — len 8; порядок/адреса совпадают с отчётом. **EXACT.**
- `-[MPCPlayerItemConfigurator configureQueueItem:playerItem:error:]` (граница 0x1c51c1d24): getter
  0x1c53a8f00 @0x1c51c1f94 и setter `setAudioTimePitchAlgorithm:` 0x1c53ad1c0 @0x1c51c1fa8. **EXACT.**
- AVFoundation: в сабкешах `.03/.11/.13` (единственные доступные text-сабкеши) образа AVFoundation/AVFCore/AVFAudio
  **нет** (скан LC_ID_DYLIB); в `extracted_dylibs/` их тоже нет. **EXACT (негатив в пределах корпуса).**

**Counter-evidence / downgrade:** «lambda @0x1b8f62790 = `std::function<bool(const ParameterSchedule::Event*)>`» —
функция существует и лежит в const-таблице (`__AUTH_CONST/__const` @0x1f39f8bf8), но её роль/сигнатура независимо
не доказаны (нет AVFoundation/полного typeinfo) → **PARTIAL** (в отчёте гильдия шла в EXACT).
Позиция TimePitch **после** DSPGraph — уже STRONG_INFERENCE в отчёте; независимых данных для апгрейда нет.

**Final status:** механизм/адреса — **EXACT**; порядок «DSPGraph → TimePitch» — **STRONG_INFERENCE**; lambda — **PARTIAL**.

---

## 3. out_gain path (per-track volume ramps, owner FUN_2721b2a04)

**Claim (DSP_RUNTIME_ARCHITECTURE §C):** `FUN_2721b2a04` собирает рампы `out_gain` и вызывает
`setVolumeRampFromStartVolume:toEndVolume:timeRange:` (0x2721b328c и 0x2721b359c); времена — CMTime
(timescale 0x3b9aca00); target — `AVMutableAudioMixInputParameters`; две независимые рампы на outgoing/incoming.

**Independent verification:**
- ADRP+ADD на селекторы: `audioMixInputParametersWithTrack:` 0x1fc12e5f1 @0x2721b2e60; `setTrackID:` 0x1fb8e1767
  @0x2721b2e78; `setVolumeRampFromStartVolume:toEndVolume:timeRange:` 0x1fc13bb7b @0x2721b3270 и @0x2721b356c. **EXACT.**
- Timescale: `mov w0,#0xca00; movk w0,#0x3b9a,lsl16` = **0x3b9aca00 (1e9)** @0x2721b3238-40 и 0x2721b3458-5c;
  CMTimeRange-хелперы 0x2721b326c / 0x2721b3568; `fmov s0,s8; fmov s1,s9` перед вызовом. **EXACT.**
- Оба callsite внутри `FUN_2721b2a04` (граница до 0x2721b36fc). **EXACT.**
- Значения — `Score`-рампы `out_gain` 1→0/0→1 (raw JSON, см. §5). Два независимых пути/цикла. **EXACT (структура).**

**Counter-evidence / downgrade:** «target object = `AVMutableAudioMixInputParameters`» выводится из выбора
API-селекторов (`audioMixInputParametersWithTrack:` + `setTrackID:` → объект класса AVFoundation, что
возвращает именно `AVMutableAudioMixInputParameters`); прямого типа в коде нет → **STRONG_INFERENCE**
(в отчёте строка помечена EXACT).

**Final status: EXACT** (владелец, callsite, CMTime 1e9, независимые рампы); **STRONG_INFERENCE** (класс объекта).

---

## 4. TransitionPlanner caller chain (stub/GOT/«нет литерального вызова»)

**Claim (MUSIC_APP_AUTOMIX_CALLCHAIN):** stub `0x2721d5ef0` (_SonicKit_MusicKit, #76) читает GOT `0x28247ad88`
= `0x80140000f2267700` → planner `0x272267700`; литерального call-site нет ни в одном из доступных бинарей;
#9 = PARTIAL. Предложено «попробовать ещё раз с `.13`».

**Independent verification:**
- Stub байты: `adrp x17,0x28247a000; add x17,x17,#0xd88; ldr x16,[x17]; braa x16,x17` @0x2721d5ef0. Номер стаба:
  (0x2721d5ef0−0x2721d5a30)/16 = **76**. **EXACT.**
- GOT `0x28247ad88` (`dyld_shared_cache_arm64e.70.dylddata`): raw `80 14 00 00 f2 26 77 00` → `0x80140000f2267700`
  → target `0x272267700`; соседние слоты `0x28247ad80…adc8` = planner-API (Configuration/FailureReason/Song.*). **EXACT.**
- Повторный сплошной скан (fastscan: BL/B + ADRP+ADD/LDR + raw qword) по 16 `targets/*` + Music.app/Music +
  MusicApplication + MEE на {0x272267700, 0x2721d5ef0, 0x28247ad88, 0x80140000f2267700}:
  **ровно 1 вхождение** — сам stub (ADRP+ADD @0x2721d5ef0); 0 `BL/B`, 0 inline-загрузок, 0 raw-указателей. **EXACT negative.**
- `.13`: 54 образа (CMPhoto…MediaServices, AudioSession), SonicKit/`_SonicKit_MusicKit[_Packages]` **отсутствуют**;
  `_SonicKit_MusicKit` text-сабкеш по-прежнему вне набора. **Новых свидетельств нет.**

**Final status: PARTIAL** (без изменений): импортёр/слот/негатив — EXACT, литеральный call-site — UNKNOWN/NOT FOUND.

---

## 5. Constant-power формулы (TransitionStyles id 1/2)

**Claim (TRANSITION_STYLES §4):** normalized JSON соответствует raw (sha256 `fe3d0a36…d120`); формулы не
выдуманы; π/cos/sin REJECTED; равенство квадратов — INFERRED из curve-движка.

**Independent verification:**
- `sha256(TransitionStyles.json)` = `fe3d0a36625ccb043c519bcc5119c98190893a9911cfa58412ad60cbab04d120`. **EXACT.**
- Машиносравнение raw↔normalized (все 14 стилей): **55 инструкций, 74 автоматики (46 out / 28 in), 0 расхождений**
  по именам, placement (relative/offsetInSeconds), startTime/endTime, startValue/endValue, interpolation. **EXACT.**
- Поиск формул: `cos(` 0, `sin(` 0, `π` 0, `3.14159` 0; `0.7071` 1× и `equal_power_cos_sin` 1× — внутри
  строки-опровержения (`"equal_power_cos_sin": "REJECTED"`), `sqrt` 21× — curve-таблица/derivation, помеченная
  `INFERRED`. **Формулы не выдуманы.**
- style 1/2 raw: out_gain 1→0 ease-in-0.5 / 0→1 ease-out-0.5; style 2 placement incoming rel 0.75–1, offset rel 1, −0.3 s. **EXACT.**

**Final status: EXACT** (соответствие и отсутствие выдуманных формул).

---

## 6. Lyrics timing constants (MEE + musicapp_ghidra)

**Claim (LYRICS_RENDERER §2.1/2.2):** `maxSelectedLines 2`, `maxEndTimeOffset 0.5`, `lineDelay 0.05`,
`animationHeadstart 0.1`, opacity `0.12 cp(0.33,0)(0.2,0.1)`, scroll `0.28 cp(0.17,0)(0.83,1)`.

**Independent verification (MEE, base 0x100000000; Music.app base 0x100000000):**
- `Specs.opacityAnimator` @0x10045df1c: `ldr d0,[0x1004cf340]`=**0.12**; `d1=[0x1004cf440]`=**0.33**;
  `movi d2,#0`; `d3=[0x1004cf0b8]`=**0.2**; `d4=[0x1004cf0c0]`=**0.1**; `bl 0x1004885a0`. **EXACT.**
- line-change/scroll animator @0x1004739e0: `d0=[0x1004cf380]`=**0.28**; `d1=[0x1004dd7f0]`=**0.17**;
  `d2=0`; `d3=[0x1004dd7f8]`=**0.83** (bytes `8fc2f5285c8fea3f`); `d4=1.0`. **EXACT.**
- Music.app vpfi: 0x100c2b4e0→`0x3fa999999999999a`=**0.05**; 0x100c2bf9c→`0x3fb999999999999a`=**0.1**;
  0x100c2bf90→`0x3fef5c28f5c28f5c`=**0.98**; 0x100c2b4d4→`0x3feccccccccccccd`=**0.9**. **EXACT.**
- `maxEndTimeOffset 0.5` — литерал `0x3fe0000000000000` в MEE (8 мест), значения присутствуют; привязка
  «поле↔offset +0xb8» — как в отчёте STRONG_INFERENCE. **PARTIAL/STRONG.**
- `maxSelectedLines 2`: в `Specs.init` (0x100460b1c) есть `mov w8,#2; str x8,[sp,#0x118]` @0x100460bdc;
  в `SyncedLyricsManager.update` @0x10045b7c8 читается `[x21,#0x38]` и `cmp` со счётчиком selectedLines
  (`ldr x8,[x21,#0x50]; ldr x8,[x8,#0x10]; ldr x9,[x21,#0x38]; cmp x8,x9; b.ge`). **Значение и паттерн — STRONG**
  (имя поля независимо не доказывается; в отчёте value EXACT / binding STRONG — корректно).

**Final status: EXACT** для проаудированных констант аниматоров и vpfi-значений; **STRONG** для привязок
`maxSelectedLines`/`maxEndTimeOffset` к offset'ам.

---

## 7. Sheet animation parameters (UIKitCore .03, MediaCoreUI, MusicUI)

**Claim (NOW_PLAYING_ANIMATION_SPEC):** длительность sheet **0.4 s** (`+[UITransitionView defaultDurationForTransition:8]`),
ζ=1.0, response 0.3441442326 → mass 1.0 / k 333.33333328 / c 36.51483716; prefs-ключи
SheetDampingRatio/SheetResponse/SheetHighSpeedDampingRatio; MediaCoreUI `crossfadeDuration 0.8`; #36 ключ
`PPTContentOffsetScrollIncrement` (=10.0).

**Independent verification:**
- Все 15 адресов UIKitCore существуют как символы в атласе: `_UISheetAnimationController` методы 0x189712a70/…,
  `__UISheetTransitionDuration/SpringParametersHighSpeed/TimingCurve(+block)`, `_UISheetPresentationMetrics`
  0x189cd6400/410/484, `+[UITransitionView defaultDurationForTransition:]` 0x18a251b70,
  `UISpringTimingParameters` 0x1891a47c4/4694/4cf4. **EXACT.**
- `-[_UISheetPresentationMetrics transitionDuration]`: classref `0x1e72ebb08` → class `0x1ed69e0f0` →
  class_data_bits low33 `0x70077340` → class_ro `0x1f0077340`; name-ptr → `"UITransitionView"`
  (`0x18a7c0358`). Receiver = UITransitionView, аргумент `w2=8`, `b` на `objc_msgSend`. **EXACT** (ранее
  класс-идентичность ошибочно считалась трудноразрешимой — разрешена через low33-скан).
- Таблица `defaultDurationForTransition:` @0x18a539c68: [0]=0.0, [1-3]=0.35, [4-6]=0.4, [7]=0.35, **[8]=0.4**,
  [9]=0.4, [10-11]=0.7, [12]=0.35, [13-14]=0.6, [15]=0.7 — совпадает с отчётом. **EXACT.**
- `__UISheetTransitionDuration` → `__UIFallbackSheetMetrics`+`transitionDuration`; блок
  `__UISheetTransitionTimingCurve` @0x1894a2a20: `w0=0` (standard), `bl 0x1894a2978`,
  `initWithParameters:initialVelocity:`; `interruptibleAnimatorForTransition:` — `0.4`(d8 из transitionDuration)
  + spring (`initWithDuration:timingParameters:` 0x18ac4af60). **EXACT.**
- Prefs: `0x1efe0e198/1b8/1d8` — CFString-объекты (flags 0x7C8) c cstr-указателями
  `0x18a5fd400/412/420` → `"SheetDampingRatio"` (len 17), `"SheetResponse"` (13),
  `"SheetHighSpeedDampingRatio"` (26). **EXACT.**
- Дефолты в блоке 0x189cd6484: ζ=1.0 (`fmov #1.0` через `fcsel`), response `[0x18a537078]`=`0x3fd606758807efe5`
  = **0.3441442326**, high-speed ζ `[0x18a50f828]`=`0x3fe999999999999a`=**0.8**. **EXACT.**
- Конверсия `_convertDampingRatio:response:toMass:stiffness:damping:`: `2π`=`0x401921fb54442d18`;
  mass=1.0; stiffness=(2π/response)²; damping=2ζ√stiffness. Пересчёт: **333.33333328050389** и
  **36.514837164117488** — совпадает с k/c отчёта. **EXACT.**
- MediaCoreUI: field-offset global `0x1ec31d430`=**0x34**; init @0x1c4d062ac `w9=0x3F4CCCCD` → `[x20+x8]` (0.8f);
  consumer 0x1c4d077a8 `fdiv s8,s8,s0` (dt/0.8). **EXACT в карве.** Провенанс: карв из сабкеша `.21`,
  которого нет в `appos/sys_dsc` → источник карва локально не перепроверяем (**PARTIAL provenance**).
- MusicUI (#36): строка `"PPTContentOffsetScrollIncrement"` @0x215a85850 (countAndFlags `0x1f|0xd000<<48`),
  `fmov d0,#10.0` fallback при 0; глобал `0x27ce0ceb8` = 0 на диске; consumer `frame:` добавляет K к
  `contentOffset.y` и вызывает `setContentOffset:animated:` (sel 0x1face5120, animated=0), иначе `setPaused:`. **EXACT.**

**Final status: EXACT** (все числа/ключи/формулы), mediaCoreUI-provenance карва — **PARTIAL**.

---

## 8. vi-nnet architecture (stateful streaming TCN)

**Claim (VI_NNET_ARCHITECTURE):** 1973 op; 1015 const; 36 sep-блоков; dilation 1..256 ×4; 48 state-тензоров;
0 рекуррентных op; mask conv [2,1,385,1] pad 160; decoder conv_transpose [384,1,1,64] strides 32; plist
MIL2BNNS/CPU/BlockSize 4096/Lookahead 16384/44.1 kHz/StreamingMode 1; weights 15 551 232 B.

**Independent verification (собственный парсер MIL):**
- op-histogram: const **1015**, add **182**, reduce_mean **146**, conv **75**, mul **75**,
  sub/square/sqrt/real_div **73** каждый, leaky_relu **72**, slice_by_size **58**, concat **48**,
  cast/expand_dims/reshape **2**, relu/sigmoid/conv_transpose/squeeze **1**; total **1973**. **EXACT.**
- `matmul/softmax/lstm/gru/rnn/attention/layer_norm/transpose` = **0** каждый. **EXACT.**
- 36 модулей `stem_sep_module_0..35`; dilation-константы (var'ы) = **1,2,4,8,16,32,64,128,256 ×4**; groups=448
  depthwise, strides [1]. **EXACT.**
- Front-end: weight `[384,1,1,64]`, strides **[1,32]**; `stem_to_latent` `[448,768,1]`; mask
  `[2,1,385,1]` pad `[160,160,0,0]`; decoder `conv_transpose [384,1,1,64]` pad `[0,0,32,32]` strides **[1,32]**. **EXACT.**
- 49 входов, из них **48 state** (`48 *_state` + audio), 49 выходов (`target_1` + 48 out_state). **EXACT.**
- plist: `SampleRate=44100`, `LookaheadSize=16384`, `NeuralNetImplementationType=MIL2BNNS`,
  `ComputeEngineName=CPU`, `StreamingMode=1`, `BlockSize=4096`, `BatchSize=1`, 2 канала, iteration 1106,
  TaskID `czutbtg4y9`. **EXACT.**
- sha256 `vi-nnet.weight.bin` = `d0bb9ffb…8554`, размер 15 551 232 B; sha256 MIL = `6a0d6c7e…c637`. **EXACT.**

**Final status: EXACT.**

---

## 9. TTML parser: MediaServices (не MediaPlayer)

**Claim (TTML_PARSER_CLOSURE):** `MSVLyricsTTMLParser` живёт в `MediaServices.framework` (text-сабкеш `.13`);
class_ro `0x1f23a81d0`; 39 методов; делегаты 0x1abf8cb14/0x1abf8bd8c/0x1abf8bc28; `msvl_timeValue 0x1abf8e344`;
в MediaPlayer (`.11`) `MSVLyrics*` нет; MEE импортирует из ordinal 49, MusicApplication — 0x1d.

**Independent verification:**
- `.13` содержит `/System/Library/PrivateFrameworks/MediaServices.framework/MediaServices` по file-off
  0x6bf7000 при базе 0x1a5374000 → **VA 0x1abf6b000**. **EXACT.**
- class_ro `0x1f23a81d0` читается из `.33.dylddata`: flags `0x184`, instanceSize `0x70` (112 B), name-ptr →
  `0x1abfd99cc` = **`"MSVLyricsTTMLParser"`**. **EXACT.**
- Символы атласа: `-[…didStartElement:…]` 0x1abf8cb14, `-[…didEndElement:…]` 0x1abf8bd8c,
  `-[…foundCharacters:]` 0x1abf8bc28, `-[NSString(MSVLyricsTTMLParser) msvl_timeValue]` 0x1abf8e344. **EXACT.**
- Методы: в атласе **41** символ `-[MSVLyricsTTMLParser …]`, из них 2 — `_block_invoke` → **39 ObjC-методов**. **EXACT**
  (см. Counter-evidence: в §1 отчёта фигурирует «40 методов»).
- MEE: chained-fixups imports — 8 `_OBJC_CLASS_$_MSVLyrics*` (TTMLParser — индекс 3454) с **libordinal 49** =
  `.../MediaServices.framework/MediaServices` (список LC_LOAD_DYLIB MEE, 70 dylib). **EXACT.**
- MusicApplication: те же 8 импортов с **libordinal 29 (0x1d)** = MediaServices. **EXACT.**
- MediaPlayer `.11` (образ по VA `0x19e61c000+0x382b000` = `0x1a1e47000`): строк `MSVLyrics`/`TTMLParser`/`msvl_timeValue`
  — **0**. **EXACT negative.**

**Counter-evidence / corrections:**
1. **Music.app/Music** импортирует MSVLyrics-классы с **libordinal 85** (`MediaServices` — 85-й dylib), а **не 0x1d**;
   ordinal 0x1d относится к `MusicApplication.framework`. Утверждение отчёта «MusicApplication **и** Music.app … ordinal 0x1d»
   — **уточнить** (итог «из MediaServices» верен, номер ординала — нет).
2. «entries 1725–1728» (MEE) не воспроизведены: 8 классов идут индексами **3450–3457** (порядок тот же).
   Значимый факт — ordinal 49 → MediaServices — подтверждён.
3. Внутреннее расхождение отчёта: §0 «39 методов» vs §1 «40 методов, 13 ivars»; верно **39** (41−2 блока); isize 0x70 подтверждён.

**Final status: EXACT** (адрес/класс/имя/делегаты/39 методов/ординалы MEE и MusicApplication/негатив MediaPlayer);
**CORRECTED** (Music.app ordinal; «1725–1728»; «40 методов»).

---

## 10. #2 closure (param_1 = T_end(outgoing) − T_end(incoming); witness-стабы)

**Claim (FINAL_BLOCKED_QUESTIONS §1):** `FUN_27222d51c` возвращает `T_end(outgoing) − T_end(incoming)`;
для unstructured — payload+8 (`songTimeRange.upperBound`), для structured — out[1] из `FUN_27222bc88`;
адреса `0x1801d78b0/0x1801d79d0` — libswiftCore value-witness stubs.

**Independent verification:**
- `FUN_27222d51c`: `tbz w1,#0` → возврат `0.0`; иначе `bl 0x27222b858` (incoming, позже `ldr d8,[x21]`,
  x21=payload+8), затем `bl 0x27222b7e0` (outgoing) и `fsub d0,d0,d8` @0x27222d628. **EXACT.**
- Ghidra-псевдокод (`automix/pseudocode/27222d51c…c`): `dVar2 = *pdVar1 − dVar2`, где оба `pdVar1` указывают
  на out+8 (structured) либо payload+8 (unstructured). **EXACT.**
- `FUN_27222b7e0` читает pair+0x00 (`ldr q0,[sp,#0xb0]`), `FUN_27222b858` — pair+0x58 (`ldr q0,[sp,#0xc0]`),
  т.е. outgoing/incoming halves. **EXACT.**
- `FUN_27222bc88` завершается `stp d8,d0,[x19]` (out=[start,end]); перед этим — representation-ветка
  (`fcmp`/`b.pl`). **EXACT.**
- VWT `0x2884ad1f8` (StylingRegionPair), `0x2884ad418` (StylingRegion), `0x2884ad398` (StructuredStylingRegion):
  +0x08 = **0x1801d78b0**, +0x10 = **0x1801d79d0**. Атлас: оба адреса → ближайший символ
  `__ZN5swift36swift_cvw_resolve_resilientAccessorsEPhmPKhmPKNS_14TargetMetadataINS_9InProcessEEE`
  (libswiftCore) → это resilient value-witness stubs. **EXACT** (ретракция A1 верна).
- `strb wzr,[sp,#0x162]` ровно в 0x2722345d0 / 0x272234710 / 0x272234a08. **EXACT.**
- `bl 0x27222d51c` — 9, `bl 0x27222d644` — 8; литерал `0.001` @0x272298c98 = `fca9f1d24d62503f`. **EXACT.**

**Final status: EXACT** (включая stub-claim и 0.001); единицы (секунды) — как в отчёте STRONG_INFERENCE.

---

## Статистика верификации

| Категория | EXACT | STRONG_INFERENCE | PARTIAL | REJECTED |
|---|---|---|---|---|
| §1 Per-track DSP | 7 | 1 (имя класса) | 1 (reset/teardown) | 0 |
| §2 TimePitch | 8 | 2 (Spectral-текст; порядок) | 1 (lambda) | 0 |
| §3 out_gain | 5 | 1 (класс target-объекта) | 0 | 0 |
| §4 Planner caller | 4 | 0 | 1 (call-site) | 0 |
| §5 Constant-power | 3 | 0 | 0 | 0 |
| §6 Lyrics constants | 4 | 1 (field-binding) | 1 (field-binding) | 0 |
| §7 Sheet/Backdrop/PPT | 13 | 0 | 1 (provenance карва .21) | 0 |
| §8 vi-nnet | 8 | 0 | 0 | 0 |
| §9 TTML | 8 | 0 | 0 | 0 (+1 CORRECTED) |
| §10 #2 closure | 7 | 0 | 0 | 0 |

**Итого проверено под-утверждений: 77.** EXACT — **67**; STRONG_INFERENCE — **5** (из них 3 — понижения
из EXACT: имя класса эффекта §1, текст «Spectral» §2, класс target-объекта §3); PARTIAL — **5** (в т.ч. 2
понижения: lambda §2, provenance карва §7); CORRECTED — **1** (§9); REJECTED — **0**.
Криптографических сверок: sha256 TransitionStyles/MIL/weights — 3/3 совпали.

**REJECTED / downgraded list (итог):**
1. §1 — идентификация класса аллокации как `AVAudioMixProcessingEffect`: **EXACT → STRONG_INFERENCE**
   (name-ptr класса в отсутствующем text-сабкеше; селектор инициализатора — публичный API AVFoundation).
2. §2 — роль/сигнатура «lambda 0x1b8f62790 = ParameterSchedule::Event callback»: **EXACT → PARTIAL**
   (функция и её const-таблица подтверждены; тип не доказуем без AVFoundation).
3. §2 — текст CFString «Spectral»: **EXACT → STRONG** (len 8 + импорт `_AVAudioTimePitchAlgorithmSpectral`;
   сами символы в отсутствующем сабкеше).
4. §3 — «target = `AVMutableAudioMixInputParameters`»: **EXACT → STRONG_INFERENCE** (вывод из AVFoundation-API-селекторов).
5. §7 — provenance `MediaCoreUI.macho` из сабкеша `.21`: **EXACT-значение / PARTIAL-provenance** (сабкеш не в наборе).
6. §9 — **CORRECTION:** «Music.app/Music … ordinal 0x1d» неверно: у Music.app/MSVLyricsTTMLParser ordinal **85**;
   0x1d = `MusicApplication.framework`. Также «entries 1725–1728» не воспроизведены (верны 3450–3457) и «40 методов» → **39**.
7. REJECTED как ложных закрытий: **нет** — ни одно из 10 целевых закрытий не опровергнуто содержательно.

---

## Остаточная неопределённость (residual uncertainty)

1. **Отсутствующие сабкеши** (executable `.66` SonicKit, AVFoundation text) не позволяют независимо
   перепроверить: имя класса эффекта, острова `0x2743d…` (classrefs/objc_msgSend), полную цепочку
   `ts_rate`-автоматика → `TimeStretchingStep`, teardown/reset DSPGraph. Все такие места помечены выше.
2. **Runtime-значения**: `bypa=0` callsite, `entryOffsetUs`, пользовательские prefs (SheetDampingRatio и др.),
   `PPTContentOffsetScrollIncrement` на устройстве, BNNS-план vi-nnet — статикой не закрываются.
3. **Музыкальный «constant-power»**: normalized-файл верен, но вывод `gain_out²+gain_in²=1` остаётся
   **INFERRED** через связку строка→curveByte (эта связка — STRONG, не EXACT).
4. **Планировщик #9**: литеральный call-site `transition(...)`; без `.66`/runtime — по-прежнему PARTIAL.
5. **Карв `MediaCoreUI.macho`** — значение 0.8f проверено в карве, но источник карва (.21) отсутствует;
   для полной чистоты нужна повторная выгрузка из полного DSC.

## Secret-leak check

- В `10_final_closure/*.md` и `*.json`: нет `BEGIN PRIVATE KEY`, `accessKey`, `Bearer`, `X-Apple`, signed URL,
  FCS-ключей (единственное совпадение — мета-строка в `STATE_RECONCILIATION.md:31` о политике секретов).
- `itunes_music_genre_tree_id_name.json`: 526 узлов `{name,parent}`, **0** вхождений `http`/`token`/`Bearer`
  (URL'ы Apple из ответа в файл не скопированы). Публичные URL'ы (`itunes.apple.com/ws/genres`,
  `amp-api…`) — не секреты.
- В этом отчёте секреты отсутствуют; `/root/LMG-VK` и код приложения не изменялись.

## Вывод аудитора

FALSIFY-прогон по 10 целевым закрытиям **не нашёл ни одного ложного закрытия**: все ключевые числа
(0.4 s / ζ=1.0 / 0.3441442326 / 333.33333328050389 / 36.51483716411749 / 0.8f / 10.0 / 0.12 / 0.28 /
0.05 / 0.1 / 0.5 / 2 / 1e9 / 0.001 / dilation 1..256×4 / 36 блоков / 48 state / 526 жанров) воспроизведены
из сырья. Найдены: **3 понижения EXACT→STRONG/PARTIAL** (класс эффекта, lambda, класс target-объекта,
текст Spectral), **1 correction** (Music.app ordinal 85 vs 0x1d; 39 vs 40 методов; import indices) и
подтверждённые ограничения среды (нет `.66`/AVFoundation text). Заявления §4 (#9 PARTIAL), §A bypa,
§13 fallback и «инструментальный порог NOT FOUND» согласуются с evidence и не требуют изменений.
