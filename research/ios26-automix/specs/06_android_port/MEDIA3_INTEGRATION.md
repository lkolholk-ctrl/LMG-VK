# MEDIA3_INTEGRATION — интеграция плана перехода и DSP в форк Media3 (read-only анализ)

Subagent #7. RESEARCH/SPEC ONLY. Форк читался только на чтение: sources-jar'ы распакованы во
временный каталог, ничего в `/root/LMG-VK/media3-m2` и `app/` не менялось.

Источники (реальные пути):
- `/root/LMG-VK/media3-m2/com/liquidmusicglass/media3/media3-exoplayer/1.11.0-lmg31/media3-exoplayer-1.11.0-lmg31-sources.jar`
- `.../media3-common/1.11.0-lmg31/media3-common-1.11.0-lmg31-sources.jar`
- `.../media3-decoder/1.11.0-lmg31/...`
- `/root/LMG-VK/settings.gradle.kts` — локальный maven `media3-m2`, группа
  `com.liquidmusicglass.media3`, версия `1.11.0-lmg31`.
- Приложение: `/root/LMG-VK/app/src/main/kotlin/com/lmg/vk/...` (read-only).

Ниже пути внутри sources-jar указаны от корня архива.

---

## 1. Существующие интеграционные швы форка (сводка, проверено по исходникам)

| Шов | Файл/строка (sources-jar) | Статус | Использование в порте |
|---|---|---|---|
| `DefaultRenderersFactory.buildAudioSink(context, enableFloatOutput, enableAudioOutputPlaybackParams)` | `media3-exoplayer/androidx/media3/exoplayer/DefaultRenderersFactory.java:966` (protected) | EXACT | переопределён в app (`PlayerAudioChain.kt:30`) — сюда вставляется DSP-цепочка |
| Второй аудио-рендерер + свой sink | `DefaultRenderersFactory.java:697–708` (secondary sink/renderer), `:976 createSecondaryRenderer` | EXACT (LMG-fork) | второй трек перехода; его sink тоже проходит `buildAudioSink` |
| `DefaultAudioSink.Builder.setAudioProcessorChain(...)` / `setAudioProcessors(...)` | `.../audio/DefaultAudioSink.java:352` / `:339`; `DefaultAudioProcessorChain` `:159` | EXACT | точка вставки `DspGraphAudioProcessor` |
| `setEnableFloatOutput(boolean)` | `DefaultAudioSink.java:368` | EXACT | обязательно `true` для float DSP |
| `AudioProcessor` / `BaseAudioProcessor` / `AudioProcessorChain` | `media3-common/.../audio/AudioProcessor.java:40` (`queueInput` :314, `isActive` :302, `flush` :349), `AudioProcessorChain.java` | EXACT | контракт DSP-процессора |
| `GainProcessor` + `GainProcessor.GainProvider` | `media3-common/.../audio/GainProcessor.java:33/:36`, `getGainFactorAtSamplePosition` :44 | EXACT | эквивалент input/output mixer |
| `DefaultGainProvider` + `FadeProvider` (LINEAR/EQUAL_POWER) | `media3-common/.../audio/DefaultGainProvider.java:44/:95/:126–158` | EXACT | кроссфейд-кривые (Android-выбор, не Apple) |
| `SpeedChangingAudioProcessor` + `SpeedProvider` | `media3-common/.../audio/SpeedChangingAudioProcessor.java:50`, `SpeedProvider.java` (`getSpeed(timeUs)`, `getNextSpeedChangeTimeUs`) | EXACT | ts_rate / rate-рампы |
| `SonicAudioProcessor.setSpeed/setPitch` | `media3-common/.../audio/SonicAudioProcessor.java:100/:115` | EXACT | time-domain time-stretch |
| `PlayerMessage` | `media3-exoplayer/androidx/media3/exoplayer/PlayerMessage.java:39` (`setType` :131, `setPayload` :150, `setPosition` :201, `setPosition(int,long)` :220, `send` :266); создание — `ExoPlayer.java:1976 createMessage(target)` | EXACT | дискретные события автоматизации |
| `Renderer.MSG_SET_VOLUME = 2`, `MSG_SET_AUDIO_ATTRIBUTES = 3` | `media3-exoplayer/.../Renderer.java:255/:273` | EXACT | программное управление громкостью рендерера |
| `ExoPlayer.CrossfadeConfiguration` | `media3-exoplayer/.../ExoPlayer.java:219` (durationUs/curveType/entryOffsetUs), `setCrossfadeConfiguration` :1736 | EXACT | план → длительность/кривая/entry offset |
| `CURVE_*` константы | `ExoPlayer.java`: `CURVE_DEFAULT=-1`, `CURVE_CONSTANT_POWER=0`, `CURVE_EXPONENTIAL=1`, `CURVE_LINEAR=2` | EXACT | выбор кривой Android-стороны |
| `PlayerAudioFadeControl` | `media3-exoplayer/.../PlayerAudioFadeControl.java:30` (класс package-private); `prepareForCrossFade` :444, `maybeStartCrossFading` :491, `doCrossFade` :534, `setCrossFadeDurationUs` :731, `setCrossFadeState` :753, `setFadeAudioEffect` :777 | EXACT (форк, порт Apple) | реальный движок свода |
| `AudioFadeControl` (интерфейс) | `media3-exoplayer/.../AudioFadeControl.java`: `FadeEffectType{LINEAR,CUBIC,EXPONENTIAL,LOGARITHMIC,CONSTANT_POWER,SIGMOID}`, `FadePhase`, `AudioFadeTransition`, хуки `canFadeBetweenPeriods/prepareForCrossFade/maybeStartCrossFading/doCrossFade` | EXACT (LMG-fork) | контракт фаз/кривых |
| `CrossfadeTrackRouting` | `media3-exoplayer/.../CrossfadeTrackRouting.java` (`moveSelection`, `moveStream`) | EXACT (LMG-fork, package-private) | перенос селекции/стрима при overlap |
| `ExoPlayerImplInternal` crossfade loop | `media3-exoplayer/.../ExoPlayerImplInternal.java:224, :388, :3109/:3123/:3142/:3148` | EXACT (LMG-fork) | автоматика «когда сводить» |
| `CrossfadeConfig.setDebugLogging` | `media3-exoplayer/.../CrossfadeConfig.java` | EXACT | диагностика |
| `ToFloatPcmAudioProcessor` / `ToInt16PcmAudioProcessor` | `media3-exoplayer/.../audio/ToFloatPcmAudioProcessor.java`, media3-common | EXACT | формат на входе DSP |

Вывод: **переписывать Media3 не нужно** — все нужные точки уже есть; часть доступна приложению
(protected/public), часть — package-private и требует маленького патча форка (см. §11).

---

## 2. Компонент: DspGraphAudioProcessor (новый `AudioProcessor`)

- iOS behavior: `AVAudioMixProcessingEffect` с `dspGraphText:properties:parameterSchedule:identifier:`
  (EXACT selector @file 0x117ea3 `_SonicKit_MusicKit`, 0x4e89d5 `MediaPlaybackCore`); граф —
  `CADSPGraph*` (libAudioDSP).
- source evidence: `DSP_GRAPH_ARCHITECTURE.md` §2/§4 (EXACT), `ANDROID_DSP_ENGINE.md`.
- Android implementation (описание):
  `com.lmg.vk.dsp.DspGraphAudioProcessor : BaseAudioProcessor()` (файл, который создаст агент
  интеграции, НЕ этот сабагент). Обязанности: `onConfigure(inputFormat)` → выбрать float формат
  (`AudioFormat(sampleRate, channelCount, C.ENCODING_PCM_FLOAT)`), создать/сконфигурировать
  native-граф (вне audio thread), принять `TransitionPlan`/`ParameterTimeline`;
  `queueInput(buffer)` → вызвать `nativeProcess` по буферу (in-place), продвинуть frame index и
  применить параметры; `isActive()` → `dspEnabled && bypa != 0 && plan != null`;
  `onReset/flush` → `nativeReset()` (мгновенные значения по умолчанию).
- difficulty: high.
- expected fidelity: 65%.
- performance risks: JNI-копия на буфер (использовать direct buffer), два инстанса (по одному на
  рендерер), RT-safety (без аллокаций/локов/mutex).
- fallback: `isActive()==false` → цепочка пропускает процессор без затрат; сработает при
  `automix.dsp=off` или отсутствии плана.

---

## 3. Компонент: вставка в цепочку рендерера

- iOS behavior: рендерер строится SonicKit/MediaPlaybackCore; DSP-эффект — часть AVAudioMix.
- source evidence: app `PlayerAudioChain.kt:28–66` (существующая цепочка
  `BassAudioProcessor → DjFxAudioProcessor → VolumeNormalizationProcessor`), форк
  `DefaultRenderersFactory.java:966`.
- Android implementation: в `PlayerAudioChain.renderersFactory` (или его преемнике) собрать
  `DefaultAudioSink.Builder(context).setEnableFloatOutput(true).setAudioProcessors(arrayOf(
  GainProcessor(inputGainProvider), DspGraphAudioProcessor(plan), SpeedChangingAudioProcessor(speedProvider),
  GainProcessor(outputGainProvider), BassAudioProcessor, DjFxAudioProcessor,
  VolumeNormalizationProcessor))`. Порядок фиксируется и комментируется; DSP видит float;
  legacy-процессоры остаются для совместимости и могут быть отключены флагом.
- difficulty: medium.
- expected fidelity: 90% (интеграция).
- performance risks: порядок процессоров влияет на анализ/нормализацию; пересборка sink на лету
  недопустима (пересоздание AudioTrack) — менять только через `setAudioProcessorChain` при flush.
- fallback: текущая цепочка без DSP.

---

## 4. Компонент: transport автоматизации (план → native)

- iOS behavior: `DefaultDSPParameterProvider.smartTransitionParameterSchedule` =
  `[CMTime:[UInt32:Float]]` (EXACT тип, getter 0x2721af5fc); передаётся в AVAudioMixProcessingEffect.
- source evidence: `DSP_AUTOMATION.md` §5 (EXACT), `ANDROID_DSP_ENGINE.md` §7.
- Android implementation — два уровня (приоритет (b)):
  (a) **PlayerMessage-путь**: `player.createMessage(MessageTarget)` (`ExoPlayer.java:1976`),
  `setType(DSP_PARAM_EVENT)`, `setPosition(positionMs)` (`PlayerMessage.java:201`),
  `setPayload` (:150), `send` (:266); целевой `target` — сам `DspGraphAudioProcessor` (реализует
  `PlayerMessage.Target`), `handleMessage` вызывает `nativeQueueParameterFrame`.
  (b) **Provider-путь (предпочтительно, sample-accurate)**: интерфейс `DspParameterProvider` по
  образцу `SpeedProvider` (media3-common): `getNextParameterChangeTimeUs(timeUs)` +
  `getParameterFrame(timeUs): ParameterFrame`; `DspGraphAudioProcessor.queueInput` читает кадр и
  применяет значения по границам фреймов. Никаких блокировок.
- difficulty: medium.
- expected fidelity: 85%.
- performance risks: PlayerMessage идёт через playback-поток (может джиттерить на сетке 0.2 s);
  provider-путь точнее; переполнение ring buffer — дропать и считать счётчик.
- fallback: PlayerMessage + `stepSeconds=0.2` (Apple-дефолт).

---

## 5. Компонент: планирование overlap/кроссфейда

- iOS behavior: `-[_MPCPlaybackEnginePlayer smartTransitionWillBeginFrom:to:transitionTime:
  outgoingItemAveragePrePivotTransitionRate:timeStamp:parameters:]` (0x1c51b09a8, EXACT) +
  `smartTransitionDidEndFrom:to:...`.
- source evidence: `DSP_GRAPH_ARCHITECTURE.md` §6, `DSP_TIMESTRETCH.md` §2 (EXACT symbols).
- Android implementation: форк уже делает перекрытие по периоду. План влияет так:
  1. `TransitionPlanCompiler` → `CrossfadeSpec(durationUs, curveType, entryOffsetUs)` →
     `player.setCrossfadeConfiguration(CrossfadeConfiguration(...))` (app уже это делает:
     `AudioService.kt:730` и `:1121`), фактически заменяя текущие `PlayerSettings.crossfadeMs` на
     длительность из плана (`TransitionSummary.outgoing/incomingSongTimeRange` → durationUs).
  2. Готовность входящего периода — `setPreloadConfiguration` (app: `AudioService.kt:~739`)
     остаётся; план должен учитывать, что свод стартует только при готовом next.
  3. Внутри `ExoPlayerImplInternal` уже вызываются
     `audioFadeControl.maybeStartCrossFading(...)` / `prepareForCrossFade(...)` /
     `doCrossFade(...)` (`ExoPlayerImplInternal.java:3109/:3123/:3142/:3148`); менять это не нужно.
- difficulty: low (апп-сторона).
- expected fidelity: 85% (тайминги/перекрытие), кривые — Android-выбор (Apple cos/sin запрещён).
- performance risks: при коротком `entryOffsetUs` и большом DSP — underrun; проверять на устройстве.
- fallback: `CrossfadeConfiguration.DEFAULT` (свод выключен) или фиксированная длительность.

---

## 6. Компонент: time-stretch через `SpeedProvider`

- iOS behavior: `ts_rate` 0.03125…32 (EXACT); `SmartTransitionSongData.speedRampMappings
  [CMTimeMapping]?` + `averagePlaybackRateAsDominantTrack` (EXACT symbols); исполнитель — NOT FOUND.
- source evidence: `DSP_TIMESTRETCH.md` §1–§3, `DSP_GRAPH_TO_MEDIA3.md` §1.
- Android implementation: `TransitionSpeedProvider : SpeedProvider` из
  `TimeStretchSpec(rateRamps)`; `getSpeed(timeUs)` — кусочно-постоянно, `getNextSpeedChangeTimeUs`
  — следующая граница рампы; клампить [0.03125, 32.0]. Внутри — уже существующий
  `SpeedChangingAudioProcessor` (SonicAudioProcessor, time-domain). Если план требует
  spectral-качество — отдельный C++-узел (OPEN).
- difficulty: medium.
- expected fidelity: 60% (контракт точный, качество/исполнитель — нет).
- performance risks: смена rate пересоздаёт playback parameters; избегать частых переключений
  (сетка 0.2 s усугубляет zipper) — интерполировать внутри процессора.
- fallback: `SpeedProvider.DEFAULT` (rate=1.0).

---

## 7. Компонент: gain (input/output mixer)

- iOS behavior: `player_gain` (вход, def 1.0), `out_gain` (выход, def 0.0), `send_mixer_gain`,
  `fx_mixer_dry/wet` (EXACT).
- source evidence: `DSP_NODES.md` #1/#9/#25/#26/#28.
- Android implementation: `player_gain`/`out_gain` — либо узлы C++-графа, либо `GainProcessor` +
  `GainProvider` (`GainProcessor.java:36/:44`) на входе/выходе цепочки. Не дублировать с
  кроссфейд-громкостью (`PlayerAudioFadeControl` управляет рендерерами через `MSG_SET_VOLUME`,
  `Renderer.java:255`) — во избежание двойного усиления.
- difficulty: low.
- expected fidelity: 90%.
- performance risks: дублирование gain при неправильной раскладке — тест energy-null.
- fallback: только C++-узлы.

---

## 8. Компонент: PCM-формат и sink

- iOS behavior: AVAudioMix работает с float-буферами.
- source evidence: форк `DefaultAudioSink.java:96`, `setEnableFloatOutput` :368,
  `ToFloatPcmAudioProcessor`; `DSP_GRAPH_TO_MEDIA3.md` §4.
- Android implementation: `setEnableFloatOutput(true)`; DSP-граф строго float32; отключить
  offload/passthrough на время активного DSP; PCM16-декодер конвертируется `ToFloatPcmAudioProcessor`
  до графа. Финальный resampler (`AudioSink`) — после rate-процессора, иначе AudioTrack пересоздастся.
- difficulty: low.
- expected fidelity: 95%.
- performance risks: лишние конверсии PCM16↔float; не включать tunneling.
- fallback: DSP выключен, sink в штатном режиме.

---

## 9. Компонент: два рендерера и aux bus

- iOS behavior: один AVAudioMix видит оба трека; aux (delay/reverb) общий.
- source evidence: `DSP_GRAPH_ARCHITECTURE.md` §1 (STRONG_INFERENCE), форк — второй рендерер
  `DefaultRenderersFactory.java:697–708`.
- Android implementation: два независимых `DspGraphAudioProcessor` (self-contained track graph).
  `automix.auxBus=perTrack` — эффекты дублируются на трек (просто, но фаза/энергия отличаются от
  Apple); `shared` — один общий native-инстанс `AuxBusGraph`, в который оба процессора пишут
  через lock-free вход (требует совместного жизненного цикла и синхронного frame-index).
- difficulty: medium (perTrack) / high (shared).
- expected fidelity: perTrack 60%, shared 75%.
- performance risks: shared — межпоточная синхронизация двух audio-callback'ов; задержка одного
  трека должна компенсироваться.
- fallback: `perTrack`; при проблемах — `off` (dry crossfade).

---

## 10. Порядок и потоки

1. Планирование — вне playback (см. `ANDROID_ARCHITECTURE.md` §4); к моменту
   `maybeStartCrossFading` (`ExoPlayerImplInternal.java:3109`) план уже скомпилирован, оба
   `DspGraphAudioProcessor` сконфигурированы (sink создан заранее).
2. `setCrossfadeConfiguration` вызывается на main (`AudioService.applyCrossfade`, `:1121`);
   внутри форка это обновляет `audioFadeControl.setCrossFadeDurationUs` при `:2944`.
3. Параметры — provider/JNI на audio thread; никаких `PlayerMessage` для sample-accurate задач.
4. Seek/stop — `audioFadeControl.reset()` + `restoreFullGain(...)` (`ExoPlayerImplInternal.java:1788–1789,
   2209–2213`); DSP получает `nativeReset()` через `AudioProcessor.flush()`.

---

## 11. Что доступно приложению, а что требует правки форка

| Возможность | Доступ | Комментарий |
|---|---|---|
| Длительность/кривая/entry offset свода | public (`ExoPlayer.CrossfadeConfiguration`) | app уже использует |
| Свои AudioProcessor'ы в обоих рендерерах | protected (`buildAudioSink`) | app уже переопределяет |
| Sample-accurate параметры DSP | своими средствами (Provider) | форк не требуется |
| Точечная установка per-direction `FadeEffectType`/`AudioFadeTransition` | НЕТ (пакет-приватный `PlayerAudioFadeControl.setFadeAudioEffect`) | нужен маленький патч форка: публичный API/прокси (например, метод на `ExoPlayer` или listener) |
| Управление `crossFadeState` (0=AUTO/1=MANUAL/2=OFF) | НЕТ извне | по умолчанию MANUAL включается при `durationUs>0` (`ExoPlayerImplInternal.java:394–399`) |
| Доступ к `getCrossFadePhase`/`isCrossFadeInProgress` для UI | НЕТ напрямую | можно эмулировать по позиции/длительности, либо патч форка |

Рекомендация: **не менять** существующую crossfade-машину; для первого этапа использовать только
public/protected швы (длительность+entry offset+свой DSP). Патч форка (публичный прокси к
`AudioFadeControl`) — отдельное решение владельца, делать его будет агент интеграции, не этот
сабагент (hard constraint: форк не трогаем).

---

## 12. Тест-план интеграции

1. `automix.dsp=off` vs `on`: отсутствие/наличие обработки, `bypa=0` = bit-exact.
2. Энерго-тест перехода: суммарная RMS по окну не имеет провалов/пиков относительно legacy.
3. Float-формат: `DefaultAudioSink` получает float; нет лишних ресемплов (лог конфигурации).
4. Overlap: два рендерера активны одновременно; `entryOffsetUs` применяется; seek посреди свода
   не оставляет «немую деку» (форк это уже чинит, `ExoPlayerImplInternal.java:1789`).
5. SpeedProvider: rate-рампы без zipper; rate clamp.
6. Aux bus: `perTrack` vs `shared` — сравнение фазы (корреляция) и потребления CPU.
7. JNI: TSan/ASan на host-стенде; отсутствие крашей при `System.loadLibrary` failure (по образцу
   `AutoMixNativeEngine.ensureLibrary`).

## NOT FOUND / OPEN

- UInt32-идентификаторы параметров Apple-графа и таблица `parameterSchedule` по умолчанию.
- Точные `entryOffsetUs`/длительности переходов Apple (стили отсутствуют).
- Кривые Apple для свода (в AutoMix нет cos/sin; конкретные кривые — в каталоге стилей).
- Место time-stretch (граф vs AQ vs AVFoundation unit).
- Семантика `out_gain` default 0.0 в графе Apple.
- Полный список вызовов `PlayerAudioFadeControl` из `ExoPlayerImplInternal` (LMG-форк уже
  расширен; при будущем обновлении форка — перепроверить).
- Наличие/отсутствие поддержки полноценного «shared graph» для двух рендереров (решение Android).
