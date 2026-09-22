# MUSIC_APP_AUTOMIX_CALLCHAIN — реальный вызывающий `TransitionPlanner.transition` и app-side цепочка интеграции (task #9)

**Дата:** 2026-09-13. **Роль:** Final Closure: Music.app AutoMix Analyst (task #9).
**Ограничения:** research only. `/root/LMG-VK` и код приложения не изменялись; артефакты только под
`deepseek_analysis/`; в `indexes/KEY_SYMBOLS_01_automix.tsv` — только append.
**Метод:** raw-скан всех секций всех доступных бинарей (Python-декодер ARM64 B/BL, ADRP+ADD/LDR),
чтение DSC-данных (`.70/.75/.33/.34/.71/.76.dyld*` из `/tmp/opencode/dscraw/`), дизасм/псевдокод
`decompiled_package/automix/**`, `llvm-nm/llvm-objdump` LLVM 19, `__objc_meta_data`, callgraph.
**Статусы:** EXACT | STRONG_INFERENCE | PARTIAL | UNKNOWN.

---

## 0. Вердикт (сводка)

1. **Потребитель/импортёр `TransitionPlanner.transition(from:to:criterias:)` — `_SonicKit_MusicKit`** [EXACT].
   В `_SonicKit_MusicKit.__TEXT,__auth_stubs` есть stub `0x2721d5ef0`, читающий слот `0x28247ad88`,
   в котором лежит auth-pointer `0x80140000f2267700` → `0x272267700`. Слот живой, привязан к planner.
2. **Весь блок импорта planner API** локализован: соседние слоты `0xb28…0xe40` страницы `0x28247a000`
   указывают на `TransitionPlanner.SchedulingPolicy.Ma`, `Configuration.init/Ma`, `FailureReason.Ma`,
   `TransitionPlanner.init(configuration:)`, `Song.Analysis.init(...)`, `Song.AdaptiveMusicAnalysis.Ma`,
   `Song.ID.Ma/init` [EXACT]. Это не «соседние случайные импорты», а именно planner-API.
3. **Реальный call-site `transition(...)` в предоставленных образах статически НЕ найден** [EXACT negative]:
   ни одного прямого `b/bl` на `0x272267700` / `0x2721d5ef0`, ни одной inline-загрузки слота
   (`adrp page 0x28247a000 + add #0xd88`), ни одной 8-байтной ссылки на адрес функции — во всех
   секциях всех 16 targets `decompiled_package/targets/*`, всех `extracted_dylibs/*`, `Music.app/Music`,
   app-`MusicApplication.framework`, `MusicEngagementExtension`. Из 645 stubs `_SonicKit_MusicKit`
   вызываются только #530/#531 (`0x2721d7b60/0x2721d7b70`, рантайм-хелпер `0x296dfa8d0`).
4. **Handoff-цепочка (Metal→player) реконструирована** [STRONG_INFERENCE/PARTIAL]: планирование перехода
   живёт в `_SonicKit_MusicKit` (путь `FUN_27219fc84` config → `FUN_2721a1ea4` renderer behavior →
   `FUN_2721b0970` сборка `SmartTransitionData` → `FUN_2721b15ac` расчёт+ошибки → результат
   `SmartTransitionData`), а на app-стороне результат кэшируется в
   `MediaPlaybackCore.SmartPlayerItemTransition.cachedSmartTransitionData` [EXACT поля] и применяется через
   ObjC-колбэки `smartTransitionWillBegin/DidEnd` [EXACT].
5. **#9 = PARTIAL (не закрыт)**: потребитель найден и доказан, литеральный call-site — нет. Нужен
   runtime-trace или переизвлечение `dyld_shared_cache_arm64e.66` (executable-сабкеш `_SonicKit_MusicKit`,
   в текущем `/tmp/opencode/dscraw` отсутствует; остались только `.70/.75/.33/.34/.71/.76`).

---

## 1. Цепочка вызовов (chain table)

| # | Hop | Symbol / адрес | Доказательство | Статус |
|---|-----|----------------|----------------|--------|
| 0 | MediaAPI (`audio-analysis`/`flexml-analysis`, `attributes.supportsSmartTransitions`) | live JSON + `MediaAPI.SongAttributes.supportsSmartTransitions` `0x272142ec0`; `MediaAPI.AudioAnalysisAttributes.from(decoder:)` `0x27215a614` | 01_automix/MEDIAAPI_*; `_SonicKit_MusicKit` | EXACT/STRONG |
| 1 | MusicKitInternal: нормализация `AudioAnalysis`/`FlexAnalysis` → `Song.supportsSmartTransitions` | `MusicKit.Song.supportsSmartTransitions` `0x1d40ccc6c`; `AudioAnalysis.init(cloudResource:)` `0x1d412061c` | P1_MEDIAAPI_CLOSURE §5; extracted `MusicKitInternal` | EXACT |
| 2 | `_SonicKit_MusicKit`: загрузка transition info (MediaAPI) | `A3NESC.loadTransitionInfoFor(outgoingTrackID:incomingTrackID:in:) async -> [SongTransitionInfo]` `0x2721a3c18` | nm `_SonicKit_MusicKit` | EXACT (symbol) |
| 3 | `_SonicKit_MusicKit`: асинхронный loader данных перехода | `A3NESC.loadTransitionData(outgoingTrackInfo:incomingTrackInfo:in:previousTransitionPlaybackEndState:) async -> SmartTransitionData?` `0x2721a1abc` | nm `_SonicKit_MusicKit` | EXACT (symbol) |
| 4 | `_SonicKit_MusicKit`: конфигурация планировщика | `FUN_27219fc84(double)` — лог `"[ALC] - Setting planner configuration with step duration: - %{public}f"` `0x2721fbbb0` | ref 0x2721a0304 → строка; pseudo 27219fc84 | EXACT (факт лога) / PARTIAL (связь) |
| 5 | `_SonicKit_MusicKit`: renderer behavior (SonicKit.EngineBehavior) | `FUN_2721a1ea4` — лог `"[ALC] - SupportsSmartTransitions overridden"` `0x2721fbc00`; вызывает `FUN_2721b0970` | callgraph `FUN_2721a1ea4 → FUN_2721b0970`; ref 0x2721a2034 | EXACT |
| 6 | `_SonicKit_MusicKit`: сборка результата + ALC-лог | `FUN_2721b0970` — `"[ALC] - Transition Generation Complete: %{public}s"` `0x2721fbc30`; `"TransitionPlanner failed to produce a transition"` `0x2721de280`; вызывает `FUN_2721b15ac`, затем `SmartTransitionData.debugDescription` `0x2721b0164` | pseudo/disasm 2721b0970 | EXACT |
| 7 | **ВЫЗОВ ПЛАНИРОВЩИКА** | `TransitionPlanner.transition(from:to:criterias:) -> Result<Transition, FailureReason>` `0x272267700` (`_SonicKit_MusicKit_Packages`) | import stub `0x2721d5ef0` → GOT `0x28247ad88` (см. §2.B); **call-site не найден** | **NOT FOUND** (import — EXACT) |
| 8 | `_SonicKit_MusicKit`: обработка `Result<Transition?, error>` | `FUN_2721b15ac` — `"TransitionPlanner failed to produce a transition with error: "` `0x2721de2d2`; тег результата в `x21`; `FUN_2721b3e60` — лог downgrade `0x2721fbc70/0x2721fbce0` | pseudo/disasm 2721b15ac/2721b3e60 | EXACT (логи) / STRONG (семантика) |
| 9 | `_SonicKit_MusicKit`: упаковка в `SmartTransitionData` | `SmartTransitionData.Ma` `0x2721b0028`; `FullSmartTransitionData` `0x2721afe70`; `fullTransition` `0x2721afab4`; `outgoingSongAudioMix`/`incomingSongAudioMix` `0x2721afb40/0x2721afb68`; `transitionProvided` `0x2721afa44`; `transitionStrategy` `0x2721afa50`; `transitionStartTime/EndTime` `0x2721afa1c/0x2721afa2c`; `transitionDuration` `0x2721afa18`; `transitionPlaybackEndState` `0x2721afa38` | nm `_SonicKit_MusicKit` | EXACT |
| 10 | Playback-хендофф (SonicKit engine) | `SonicKit.PlaybackEngine.music(configuration:)` (extension в `_SonicKit_MusicKit`) `0x2721d50b8`; `SmartTransitionRenderer: SonicKit.EngineBehavior` (`0x2721ed7b0`); `smartTransitionRenderer` getter `0x2721a0f80`; `SmartTransitionsDSPGraphDataProvider` `0x2721b721c` (graph `"smartTransitionsGraph"` `0x2721b6e68`) | nm; P3/DSP-отчёты | EXACT (symbol) / STRONG (роль) |
| 11 | MediaPlaybackCore: кэш и применение | класс `_TtC17MediaPlaybackCore25SmartPlayerItemTransition` (meta `0x1eed94b08`, ivars `0x1f479c2c0`): `cachedSmartTransitionData` (@0x1c52b01a0), `transitionProvider`, `transitionProvided`, `transitionStrategy`, `outgoingParameters`, `incomingParameters`, `expectedDurationAtStart`, `previousTransitionOffsetData`, `currentTransitionResultingOffsetData`, `hasReachedPivotPoint`, `setupFailureReason` | `MediaPlaybackCore` ObjC metadata; Swift typeref `_symbolic ...Sg 015_...SmartTransitionDataV` `0x1c52de956` | EXACT |
| 12 | MediaPlaybackCore: событийные колбэки | `-[_MPCPlaybackEnginePlayer smartTransitionWillBeginFrom:to:transitionTime:...:parameters:]` `0x1c51b09a8`; `smartTransitionDidEndFrom:to:transitionTime:timeStamp:` `0x1c51afdb0`; `-[MPCItemBookmarker itemSmartTransitionWillBeginFrom:...]` `0x1c51b9f8c`; `itemSmartTransitionDidEnd:time:` `0x1c51b9d48`; `-[MPCPlayerItemConfigurator configurePlayerItemForSmartTransitions:]` `0x1c4f809cc`; `+[MPCPlaybackEngine deviceSupportsSmartTransitions]` `0x1c4eb7cd4` | nm/selectors `MediaPlaybackCore` | EXACT |
| 13 | Music.app / MusicApplication: UI-тумблер и prefs | `MusicCore.Player.ToggleTransitionsCommand.isAvailable(in:)` `0x10030898c`, `.request(from:)` `0x1003089f0`; `ApplicationMainMenu.smartTransitionsToggleAction:` `0x10031c860`; `MPPlaybackUserDefaults.transitionStyleForCatalogPlayback` `0x10098fd48`; `Music.ArtworkSmartTransition.Renderer.drawInMTKView:` `0x10017b430`; `NowPlayingTransitionsButton` `0x100261168`; `SmartTransitionIndicatorView.showSmartTransitionIndicator` `0x100628864` | musicapp_ghidra function_index / nm | EXACT |

> Примечание: `A3NESC` — не «придуманное» имя, а буквальный тип из Swift-манглинга
> (`_$s015_SonicKit_MusicB00A3NESC...`, substitution-кодирование); `015_SonicKit_MusicB0` = модуль
> `_SonicKit_MusicKit`, `015_SonicKit_MusicB9_Packages` = модуль `_SonicKit_MusicKit_Packages`.
> Полный demangler в текущем окружении недоступен, поэтому имена приведены в манглированной форме.

**Линковка (dylibs-used, `llvm-objdump --macho --dylibs-used`)**:
`Music.app/Music` → `MediaPlaybackCore`, `MusicKitInternal`, `MusicUI`, `MediaPlaybackCore`-зависимые;
`MusicApplication.framework/MusicApplication` → `MediaPlaybackCore`, `MusicKitInternal`, `MusicUI`;
`MediaPlaybackCore` → `_SonicKit_MusicKit`, `SonicKit`, `SonicFoundation`;
`_SonicKit_MusicKit` → `_SonicKit_MusicKit_Packages`, `SonicKit`, `MusicKitInternal`, `SonicFoundation`;
`MusicEngagementExtension.appex` → `MediaPlaybackCore`, `MusicKitInternal` [EXACT].

**Вывод по цепочке (STRONG_INFERENCE):**
Music.app UI/preferences → MediaPlaybackCore (`SmartPlayerItemTransition`, колбэки) → SonicKit engine
(`PlaybackEngine.music`, `SmartTransitionRenderer` как `EngineBehavior`) → `_SonicKit_MusicKit`
(`loadTransitionInfoFor` → `loadTransitionData` → ALC-генерация) → `_SonicKit_MusicKit_Packages`
(`TransitionPlanner.transition`) → `Transition.DSPGraph`/`SmartTransitionData` (AudioMix+schedule) →
обратно в MediaPlaybackCore (`cachedSmartTransitionData`) и в плеер.

---

## 2. Блоки доказательств

### 2.A Определение `transition` (EXACT)

```
Source file: targets/_SonicKit_MusicKit_Packages (extracted; nm)
Framework/Binary: _SonicKit_MusicKit_Packages
Function/Symbol: _$s015_SonicKit_MusicB9_Packages17TransitionPlannerV10transition4from2to8criterias6ResultOyAA0E0VAC13FailureReasonOGAC4SongV_ApC8CriteriaVtKF
Address/Offset: 0x272267700 (T)
Evidence type: symbol
Status: EXACT
Reasoning: символ определён (T) в Packages; единственный «action»-метод TransitionPlanner в nm.
```

### 2.B Import stub `_SonicKit_MusicKit` → planner (EXACT)

stub #76 (`__auth_stubs` начинается `0x2721d5a30`, 16 B/stub; `0x2721d5ef0 = base + 76*16`):

```
00000002721d5ef0: adrp x17, 0x28247a000
00000002721d5ef4: add  x17, x17, #0xd88
00000002721d5ef8: ldr  x16, [x17]
00000002721d5efc: braa x16, x17
raw: 31 15 08 b0 | 31 22 36 91 | 30 02 40 f9 | 11 0a 1f d7
```

Значение слота в DSC (raw, `dyld_shared_cache_arm64e.70.dylddata`, VA `0x28247ad88`,
fileoff `0x8466d88`) — **перепроверено в этой задаче**:

```
raw value: 80 14 00 00 f2 26 77 00 (LE qword 0x80140000f2267700)
decoded:   auth-pointer; target = 0x180000000 + 0xf2267700 = 0x272267700
Source file: /tmp/opencode/dscraw/System/Library/Caches/com.apple.dyld/dyld_shared_cache_arm64e.70.dylddata
            (sha256 a6475490b8439387dc2ea7d7cb5f04d5b75596fd9c714bc16e36bfd5eab626c2)
Framework/Binary: _SonicKit_MusicKit (shared auth-got pool)
Evidence type: constant | xref
Status: EXACT
```

Соседние слоты того же блока (`0x28247ad80…0x28247adc8`) декодированы и сверены с nm Packages:

| slot | target | symbol |
|---|---|---|
| 0x28247ad80 | 0x272254a88 | `TransitionPlanner.SchedulingPolicy.Ma` |
| **0x28247ad88** | **0x272267700** | **`TransitionPlanner.transition(from:to:criterias:)`** |
| 0x28247ad90 | 0x2722548b4 | `Configuration.init(transitionSchedulingPolicy:)` |
| 0x28247ad98 | 0x2722549bc | `Configuration.Ma` |
| 0x28247ada0 | 0x27225b96c | `FailureReason.Ma` |
| 0x28247ada8 | 0x2722673e4 | `TransitionPlanner.init(configuration:)` |
| 0x28247adb0 | 0x27225d900 | `Song.Analysis.init(genres:duration:audioAnalysis:flexAnalysis:spatialTimingInformation:)` |
| 0x28247adb8 | 0x27225d7cc | `Song.AdaptiveMusicAnalysis.Ma` |
| 0x28247adc0 | 0x27226722c | `Song.ID.Ma` |
| 0x28247adc8 | 0x272267244 | `Song.ID.init(rawValue:)` |

### 2.C Импортное имя в LINKEDIT (EXACT)

Во фрагменте `linkedit.77` (VA region `0x2a1558000`, `/tmp/opencode/p2work/linkedit.77`,
sha256 `b0e2f16e01d1dab8efeb71e3657d82465013a6b5ced1b9c4ce08da37a193d305`) на fileoff `0x5657b6f`
находится **полное** имя импорта `_$s015_SonicKit_MusicB9_Packages17TransitionPlannerV10transition4from2to8…`;
в окне ±0x5000 — 185 символов `_SonicKit_Music*` и 0 символов `_$s8MusicKit`: это символьная таблица
`_SonicKit_MusicKit` (единственный клиент Packages), а не другого образа.

### 2.D Цепочка генерации в `_SonicKit_MusicKit` (EXACT логи/вызовы)

| функция | строки (VA) | связи |
|---|---|---|
| `FUN_27219fc84(double)` `0x27219fc84` | `"[ALC] - Setting planner configuration with step duration: - %{public}f"` `0x2721fbbb0` (xref `0x2721a0304`) | конфигурация планировщика (stepped policy), вызовы через metadata |
| `FUN_2721a1ea4` `0x2721a1ea4` | `"[ALC] - SupportsSmartTransitions overridden"` `0x2721fbc00` (xref `0x2721a2034`) | callgraph → `FUN_2721b0970` |
| `FUN_2721b0970` `0x2721b0970` | `"[ALC] - Transition Generation Complete: %{public}s"` `0x2721fbc30` (xref `0x2721b11b0`); `"TransitionPlanner failed to produce a transition"` `0x2721de280` (xref `0x2721b0d94`) | `bl 0x2721b15ac` в `0x2721b0c7c`; сборка/лог `SmartTransitionData.debugDescription` `0x2721b0164`; sret → `param_1` |
| `FUN_2721b15ac` `0x2721b15ac` | `"TransitionPlanner failed to produce a transition with error: "` `0x2721de2d2` (xref `0x2721b22e4`) | тег `unaff_x21` (успех/ошибка); вызов `FUN_2721b3e60` |
| `FUN_2721b3e60` `0x2721b3e60` | `"[ALC] - Transition complexity downgraded to .fallback due to both items not being subscription items"` `0x2721fbc70`; `"[ALC] - Non-subscription transition complexity downgrade overridden"` `0x2721fbce0` | downgrade complexity для не-subscription треков |

### 2.E App-side: `MediaPlaybackCore.SmartPlayerItemTransition` (EXACT)

```
Source: MediaPlaybackCore __objc_data class _TtC17MediaPlaybackCore25SmartPlayerItemTransition
        (meta 0x1eed94b08, instanceSize 216, 22 ivars, __IVARS__ 0x1f479c2c0)
ivars: identifier, condensedIdentifier, startQueueItem, startItem, endQueueItem, endItem,
       isStartItemSpatialized, isEndItemSpatialized, expectedDurationAtStart,
       previousTransitionOffsetData, currentTransitionResultingOffsetData,
       transitionDidBegin, hasReachedPivotPoint, hasCompletedSuccessfully, setupFailureReason,
       cachedSmartTransitionData, delegate, transitionProvider, transitionProvided,
       transitionStrategy, outgoingParameters, incomingParameters
Status: EXACT (objc-meta-data; имена строк @0x1c529f740…0x1c52b0220)
```

Плюс Swift-typeref на тип результата планировщика в MPC:
`_symbolic _____Sg 015_SonicKit_MusicB019SmartTransitionDataV` `0x1c52de956` [EXACT].

### 2.F Music.app UI (EXACT)

`musicapp_ghidra/function_index.tsv`: `ToggleTransitionsCommand` (0x10030898c/0x1003089f0),
`ApplicationMainMenu.smartTransitionsToggleAction:` (0x10031c860),
`MPPlaybackUserDefaults.transitionStyleForCatalogPlayback` (0x10098fd48),
`ArtworkSmartTransition.Renderer` (0x10017acf8/0x10017b430), `SmartTransitionIndicatorView` (0x100628864).
`Music.app/Music` и `MusicApplication.framework/MusicApplication` **не импортируют** SonicKit/
`SmartTransition*` напрямую (bind-таблица: 0 совпадений) — т.е. app-слой работает только через
MediaPlaybackCore/MusicKitInternal [EXACT].

---

## 3. NOT FOUND — негативные доказательства по call-site (главный результат #9)

Выполнены сплошные сканы (все секции всех перечисленных файлов; decoder — `/tmp/opencode/scan_calls.py`,
`/tmp/opencode/gotscan.py`, `/tmp/opencode/findxref.py`):

1. **Прямые ветви** на `0x272267700` и на stub `0x2721d5ef0`: 0 совпадений во всём множестве
   (16 targets corpus + 16 `extracted_dylibs` + `Music` + app-`MusicApplication` + `MusicEngagementExtension`).
2. **Inline-GOT**: `adrp` на страницу `0x28247a000` встречается только внутри `__auth_stubs`
   (`_SonicKit_MusicKit`, MusicUI, `_MusicKitInternal_MediaPlaybackCore`); `+add #0xd88` — только
   stub #76. В `__text` `_SonicKit_MusicKit` нет ни одной ссылки на страницу `0x28247a000`.
3. **Данные**: 8-байтная ссылка на `0x272267700` (в любой кодировке: `007726f2`, `00772672`,
   `00 77 26 72 02 00 00 00`, stub-адрес) — ровно **1** вхождение в DSC-данных
   (`0x28247ad88` в `.70.dylddata`); 0 в извлечённых Mach-O (их `__auth_got` занулены).
4. **Ghidra References**: `0x28247ad88`/соседние слоты читаются только файлами-стабами
   `2721d5ef0…2721d6060__FUN_*.c`; ни одна предметная функция `_SonicKit_MusicKit` их не читает.
5. **Звенья в Packages**: у `_SonicKit_MusicKit` **0 прямых `b/bl` в диапазон `_SonicKit_MusicKit_Packages`**
   (`[0x27220e000, 0x2722902dc)`), т.е. весь межмодульный обмен идёт через stubs, и все planner-stubs
   мертвы. Из 645 stubs образа вызываются только #530/#531 (`0x2721d7b60/0x2721d7b70`).
6. **Corpus callgraph**: у `0x272267700` нет входящих рёбер; у `FUN_2721a1ea4`/`FUN_27219fc84` вхождений
   нет (вызовы через witness/async-dispatch).

**Гипотезы (PARTIAL, не доказаны):**
- (a) DSC-builder/линкер оптимизировал вызов в inline-GOT или удалил код, оставив stub; противоречие —
  ни одного inline-слота planner не читается.
- (b) Вызов делается через асинхронную инфраструктуру (`loadTransitionData` — async-функция; её
  resume-функции вызываются через таблицы; статическая привязка аргумента не восстановлена), но
  указателя на функцию в данных нет — гипотеза не объясняет отсутствие ссылки.
- (c) Реальный вызов использует иную точку входа/wrapper, не отражённую в nm Packages (единственный
  «action»-метод — `transition`; других кандидатов нет).
Ни одну из гипотез нельзя поднять выше PARTIAL без runtime-наблюдения.

---

## 4. Что осталось UNKNOWN и как закрыть #9

1. Литеральный адрес инструкции вызова `transition(...)` (register-indirect/async?) — **UNKNOWN**.
   Закрытие: (a) переизвлечь `dyld_shared_cache_arm64e.66` (executable-сабкеш `_SonicKit_MusicKit`);
   в `/tmp/opencode/dscraw` его нет; повторить сканы п.3.1–3.3; (b) LLDB/breakpoint на
   `0x2721d5ef0+slide` или `0x272267700+slide` с backtrace; (c) trace `FUN_2721b15ac` (там
   формируется `Result`: тег `x21`, лог с ошибкой).
2. Точная привязка полей `Song.Analysis` к аргументам planner при вызове (call-level) — **PARTIAL**:
   известны типы входа (`Song.Analysis.init(genres:duration:audioAnalysis:flexAnalysis:spatialTimingInformation:)`,
   `Song.musicKit(options:context:)`) и конверсия в MusicKitInternal, но конкретный call-site не
   разрешён (см. п.1).
3. Где именно `MediaPlaybackCore` получает closure `transitionProvider` и как связывает его с
   `A3NESC.loadTransitionData` — **PARTIAL** (типы/поля EXACT, вызов не прослежен; MPC не линкует
   Packages, значит closure создаётся в `_SonicKit_MusicKit`/SonicKit и передаётся через engine).

---

## 5. Ready-to-paste строка для UNRESOLVED #9 (и CLOSURE_TRACKER)

```
9. **#9 — PARTIAL (потребитель доказан, literal call-site не найден).**
Потребитель/импортёр — `_SonicKit_MusicKit`: stub `0x2721d5ef0` (`__auth_stubs`, `_SonicKit_MusicKit`)
читает GOT-слот `0x28247ad88`, значение `0x80140000f2267700` → `0x272267700`
(`TransitionPlanner.transition(from:to:criterias:)`, `_SonicKit_MusicKit_Packages`); соседние слоты
`0x28247ad80..adc8` = planner-API (`Configuration`, `FailureReason`, `TransitionPlanner.init`,
`Song.Analysis.init`, `Song.ID`), импортное имя подтверждено в linkedit .77. Однако сплошной raw-скан
всех секций всех 16 targets + Music.app/MusicApplication/MusicEngagementExtension + извлечённых dylibs
даёт **0** прямых `b/bl` на `0x272267700`/stub, **0** inline-загрузок слота и ровно **1** ссылку в
DSC-данных (сам слот); у `_SonicKit_MusicKit` 0 прямых ветвлений в `_SonicKit_MusicKit_Packages`, из 645
stubs живут только #530/#531. Реконструированная цепочка: MediaAPI → MusicKitInternal
(`Song.supportsSmartTransitions`) → `_SonicKit_MusicKit` (`A3NESC.loadTransitionInfoFor 0x2721a3c18`,
`loadTransitionData 0x2721a1abc`, config `FUN_27219fc84`, renderer `FUN_2721a1ea4`, генерация
`FUN_2721b0970`, расчёт/ошибки `FUN_2721b15ac`, downgrade `FUN_2721b3e60`) → planner →
`SmartTransitionData` (`0x2721b0028`, audioMixes `0x2721afb40/0x2721afb68`) → MediaPlaybackCore
`SmartPlayerItemTransition.cachedSmartTransitionData` (meta `0x1eed94b08`) + ObjC-колбэки
`smartTransitionWillBegin/DidEnd` (`0x1c51b09a8/0x1c51afdb0`); app UI — `ToggleTransitionsCommand`
0x10030898c, `smartTransitionsToggleAction:` 0x10031c860. Как закрыть: runtime-backtrace по
`0x2721d5ef0`/`0x272267700` или повторный скан переизвлечённого `dyld_shared_cache_arm64e.66`.
Owner: Final Closure (task #9). Evidence: `10_final_closure/MUSIC_APP_AUTOMIX_CALLCHAIN.md`.
```

---

## 6. Приложение: воспроизводимость

- Скрипты (read-only, `/tmp/opencode/`): `scan_calls.py` (B/BL по всем секциям), `gotscan.py`
  (`adrp page 0x28247a000` + следующий instr), `findxref.py` (adrp+add/ldr → VA строк),
  `bases.py`, `strxref.py`, `gotloads.py`.
- DSC-данные: `/tmp/opencode/dscraw/System/Library/Caches/com.apple.dyld/dyld_shared_cache_arm64e.70.dylddata`
  (mapping: `0x2823a0000 + 0x65f0000`, fileoff `0x838c000`); `.77/.72` — карвинг LINKEDIT
  (`/tmp/opencode/p2work/linkedit.77|72`, sha256 в §2.C).
- Ключевые команды: `llvm-nm [-u|--demangle] <bin>`, `llvm-objdump --macho --dylibs-used|--objc-meta-data`,
  `rg` по `decompiled_package/automix/{pseudocode,disassembly,callgraphs}`.
- Секреты не использовались; live JSON не читался.

## RETRACTED / CORRECTED

1. P3 §1.2 называл соседние слоты «импортами из MusicKit/caulk/`_SonicKit_MusicKit_Packages`» —
   **уточнено**: слоты `0x28247ad80..adc8` — это ровно planner-API `_SonicKit_MusicKit_Packages`
   (`SchedulingPolicy.Ma`, `Configuration.init/Ma`, `FailureReason.Ma`, `TransitionPlanner.init`,
   `Song.Analysis.init`, `Song.AdaptiveMusicAnalysis.Ma`, `Song.ID.Ma/init`) [EXACT].
2. Старый вывод «caller вне предоставленных образов (Music.app/appex)» — **уточнено**: importer
   находится **внутри** `_SonicKit_MusicKit` (stub+GOT+импортное имя), а call-site отсутствует
   статически; app-бинари planner вообще не импортируют.
