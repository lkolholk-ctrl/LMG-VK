Total questions: 61 (49 legacy #1..#49 + 12 новых AppOS/final-closure N1..N12)
CLOSED: 43 (35 legacy + 8 новых)
PARTIAL: 15 (12 legacy + 3 новых)
BLOCKED: 3 (2 legacy: #13, #15 + 1 новый: N11)

Newly closed in this pass: 23 (15 legacy — #2,#4,#18,#19,#24,#29,#30,#31,#32,#33,#34,#36,#38,#39,#43; 8 новых — N1,N2,N3,N4,N5,N7,N9,N10; детали в Appendix A)
Still blocked: 3 — #13 (percentDeviation semantics), #15 (loudness.peak units), N11 (Panache numerics); отдельно runtime-only пробелы (не статусы таблицы): bypa=0 callsite, literal call-site `TransitionPlanner.transition` (#9 = PARTIAL), entryOffsetUs mapping
Retracted claims: 51 (дедуплицированный регистр STATE_RECONCILIATION + 07_audit + 10_final_closure; Appendix B)

AutoMix completeness: 83.3% (60/72 — ANDROID_CLEANROOM_CONTRACT §1.2–§1.5: TransitionCandidate 76.2%, TransitionPlan 78.3%, TransitionStyle 88.9%, AutomationRamp 100%); legacy-вопросы #1–#10: 8/10 CLOSED (PARTIAL: #9, #10)
DSP completeness: 94.0% (47/50 — contract §1.6 DSPParameter 100% 35/35 + §1.7 DSPGraphInstance 80% 12/15); runtime-часть pipeline (позиция TimePitch, out_gain normalized→absolute) — 71.4% (10/14), статусы #21/#23 PARTIAL
MediaAPI completeness: 51.9% (14/27 — contract §1.1 SongAnalysis; consumer-negative поля не засчитаны: #13/#15 BLOCKED, bpm/beats/fades/energy/valence/flex-* — NOT FOUND)
Lyrics timing completeness: 76.5% (13/17 — contract §1.8 LyricsModel; instrumental-порог и серверный выбор word/syllable — NOT FOUND)
Lyrics rendering completeness: 81.0% (34/42 — contract §1.9 RendererConfig; PARTIAL: 100.0/0.95/39.0, anchorPoint ±1.3, open/close переход)
Animations completeness: 75.0% (9/12 — 6 legacy #34–#39 CLOSED + sheet/backdrop/AutoMix-artwork CLOSED; PARTIAL: artwork morph, Volume.ca consumer; BLOCKED: Panache numerics; asset-level инвентарь .ca/.caar — EXACT и в счёт не входит)
Liquid Glass completeness: 75.0% (6/8 — #40–#47; PARTIAL: #42 reflection geometry, #47 runtime tile/mip/weights; дополнительно NOT FOUND: per-pixel шейдеры kind 6/7, эффективные uniform-значения)
Android clean-room spec completeness: 80.2% (178/222 — contract §5.2: EXACT 152 + STRONG 26; PARTIAL 26, NOT FOUND 18, PORT 12 не входят в Apple-полноту)

---

# Appendix A. Definitive-таблица статусов (#1..#49 + N1..N12)

Сокращения источников (пути от `deepseek_analysis/`, если не указано иное):
`P1A/P1D/P1G/P1M/P1P/P1B` = `08_closure/P1_{AUTOMIX,DSP,GLASS,MEDIAAPI,PORT,BLOCKED}_CLOSURE.md`;
`P2A/P2MD/P2G/P2L` = `08_closure/P2_{AUTOMIX,MEDIADSP,GLASS,LYRICS_PORT}_CLOSURE.md`;
`P3A/P3AN/P3G/P3L` = `08_closure/P3_{AUTOMIX,ANIMATIONS,GLASS,LYRICS_PORT}_CLOSURE.md`;
`MASTER` = `09_appos/APPOS_MASTER_SUMMARY.md`; `MATRIX` = `09_appos/APPOS_CLOSURE_MATRIX.md`;
`AT-RTA` = `09_appos/APPOS_RED_TEAM_AUDIT.md`;
`A-TS/A-DG/A-LX/A-MC/A-AN/A-MA/A-GR/A-MLA` = `09_appos/APPOS_{TRANSITION_STYLES,DSPGRAPH,LYRICSX,MUSICCOREUI,ANIMATIONS,MUSIC_APP,GLASS_RECIPES,ML_MODELS}.md`;
`PLC/TS/DSPR/LYR/LGC/MC/M2P/FBQ/B1315/NPAS/TTML/VIN/ACC` =
`10_final_closure/{TRANSITION_PLANNER_CLEANROOM_SPEC,TRANSITION_STYLES_IMPLEMENTATION_SPEC,DSP_RUNTIME_ARCHITECTURE,LYRICS_RENDERER_IMPLEMENTATION_SPEC,LIQUID_GLASS_IMPLEMENTATION_SPEC,MUSIC_APP_AUTOMIX_CALLCHAIN,MEDIAAPI_TO_PLANNER_CALLCHAIN,FINAL_BLOCKED_QUESTIONS,blocked_13_15,NOW_PLAYING_ANIMATION_SPEC,TTML_PARSER_CLOSURE,VI_NNET_ARCHITECTURE,ANDROID_CLEANROOM_CONTRACT}.md`;
`FRA` = `10_final_closure/FINAL_RED_TEAM_AUDIT.md`; `REC` = `10_final_closure/STATE_RECONCILIATION.md`;
`RT/RC/DC/VC` = `07_audit/{RED_TEAM_AUDIT,RETRACTED_CLAIMS,DISPUTED_CLAIMS,VERIFIED_CONSTANTS}.md`.
`new` в колонке источника = пункт закрыт в финальном проходе (09_appos + 10_final_closure) относительно baseline P1–P3.

## A.1. Legacy #1..#49

| # | вопрос | статус | источник |
|---|---|---|---|
| 1 | Селекторы 0x8/0x9/0xc: алгоритм/сложность/ID | CLOSED | P1A §1; P2A C5; RT §4.1 |
| 2 | `param_1` tie-breaker ×0.001: значение/поле | CLOSED | FBQ §1; FRA §10 (`new`) |
| 3 | Формула `FUN_272235840` (trailing loudness ratio) | CLOSED | P2A §1 |
| 4 | Каталог TransitionStyles: schema/кривые/длительности | CLOSED | A-TS; TS §2; `new` (AppOS) |
| 5 | Длительность Fallback Cross-Fade | CLOSED | P1A §4; FBQ §6 |
| 6 | Reduced-complexity наборы (не битмаски) | CLOSED | P2A §2 |
| 7 | Placement cases/payload/CodingKeys | CLOSED | P2A §3; PLC §3.1 |
| 8 | TempoBinaryScaleFactor raw-значения | CLOSED | P2A §4; PLC §5.3 |
| 9 | Кто вызывает `transition(from:to:criterias:)` | PARTIAL | MC §3 (literal call-site NOT FOUND); FRA §4 |
| 10 | Requirement-имена, шкала `StylingScore` | PARTIAL | P3A §2; PLC §10.4 |
| 11 | Genre ID→name | PARTIAL (был BLOCKED) | FBQ §2; `itunes_music_genre_tree_id_name.json` (526 узлов) |
| 12 | `CloudAudioAnalysis` → `MusicKitInternal.AudioAnalysis` | CLOSED | P1M |
| 13 | `bpm.percentDeviation` semantics | BLOCKED | B1315 §1; FBQ §3 (consumer-negative EXACT) |
| 14 | Единицы `EventTimes` (ms→s) | CLOSED | P2MD §2; M2P §1 |
| 15 | Шкала `loudness.{value,range,peak}` | BLOCKED | B1315 §2; FBQ §4 (consumer-negative EXACT) |
| 16 | `phrases` / `vocalActivity.kind` | CLOSED (kind — да; phrases NOT FOUND) | P2MD §4; M2P §1 |
| 17 | Прямая call-level цепочка JSON→предикат | PARTIAL | P2MD §1.2/§4.1; M2P §2 (конвертеры/острова) |
| 18 | `.dspg` текст/топология | CLOSED | A-DG §1–§3; `new` (AppOS) |
| 19 | Типы/порядок фильтров, AU subtype | CLOSED | A-DG §5.1; `new` |
| 20 | UInt32 ID параметров + default schedule | CLOSED | A-DG §6; DSPR §A; `new` |
| 21 | Где выполняется time-stretch + алгоритм | PARTIAL (механизм EXACT; порядок STRONG; lambda PARTIAL) | DSPR §B; FRA §2 |
| 22 | Формулы кривой 0x81 (log) | CLOSED | P2MD §5; PLC §7 (log) |
| 23 | `out_gain` semantics (кто открывает выход) | PARTIAL (значения CLOSED) | DSPR §C; TS §8 |
| 24 | Код MusicCoreUI/LyricsX (parser/renderer) | CLOSED | A-LX; A-MC; `new` (MEE/MusicApplication) |
| 25 | Сетевой endpoint/формат TTML | PARTIAL | P1B §25; TTML §12 |
| 26 | `itunes:timing` Line/None, `ttp:*`, `xml:id` | PARTIAL | P2L §26; TTML §3 (None/ttp не встречаются) |
| 27 | translations/transliterations, x-roman | CLOSED (структура; x-roman/ttm:name NOT FOUND) | P2L §27; TTML §7 |
| 28 | Duet/multi-agent layout, RTL | CLOSED (структура) | P3L §28; TTML §3/§7 |
| 29 | Алгоритм активной строки | CLOSED | A-LX §3; LYR §2.1; `new` |
| 30 | Autoscroll: anchor/скорости/pause | CLOSED | A-LX §8; LYR §2.3; `new` |
| 31 | Instrumental break: геометрия/анимация/порог | CLOSED (порог model-driven, константы нет) | A-LX §7; LYR §2.5; `new` |
| 32 | Karaoke-заливка и тип animator | CLOSED | A-LX §5–6; LYR §2.4; `new` |
| 33 | `userPreferenceSyllable` | CLOSED (частично; серверный выбор вне бинаря) | A-LX §9; ACC §1.8; `new` |
| 34 | Тайминги app-переходов (Mini Player↔Now Playing и др.) | CLOSED (app-часть) | A-MA §3.1; NPAS §A; `new` |
| 35 | `UICubicTimingParameters` curve | CLOSED | P3AN §35 |
| 36 | Display-link интерполяция content offset | CLOSED | NPAS §D (ключ `PPTContentOffsetScrollIncrement`, default 10.0); `new` |
| 37 | Pill-bounce from/to | CLOSED | P3AN §37; VC #63–65 |
| 38 | Marquee Mini Player | CLOSED | A-MA §3.2; NPAS §C.4; `new` (AppOS) |
| 39 | `WaveformPlayIndicator` | CLOSED | A-AN §2.1 (BouncyBars.caar); `new` (AppOS) |
| 40 | Порядок 9 фильтров / BlurAtEnd | CLOSED | P2G §1; LGC §4 |
| 41 | Uniform-дефолты и color matrices | CLOSED | P1G §2; LGC §8 |
| 42 | Reflection 12 сегментов (геометрия cap) | PARTIAL | P2G §4; LGC §6/§11 |
| 43 | Рецепты `platformContentGlass*` | CLOSED | A-GR; LGC §7; `new` (AppOS) |
| 44 | `cornerCurveExpansionFactor` | CLOSED | P2G §2; LGC §1.2 |
| 45 | Семантика `plusL`/`plusD` | CLOSED (матрицы; per-pixel INFERRED) | P1G §1; LGC §9 |
| 46 | Specular/highlight pass, CASDF defaults | CLOSED (defaults; шейдер UNKNOWN) | P2G §3; LGC §5 |
| 47 | Render targets/mips/tile/CPU-веса | PARTIAL | LGC §3.4/§11; P3G |
| 48 | Shared aux graph для двух рендереров | PARTIAL (Apple-эталон отсутствует; дизайн порта CLOSED) | P1P §48; DSPR §D.3 |
| 49 | `entryOffsetUs`/normalized→absolute | PARTIAL (данные стилей получены; маппинг в Media3 не завершён) | MATRIX; MASTER §11 (конфликт с A-TS §6 решён в пользу PARTIAL) |

## A.2. Новые элементы AppOS / final-closure

| # | вопрос | статус | источник |
|---|---|---|---|
| N1 | Наличие полного `Music.app/Music` + `MusicApplication.framework` | CLOSED (найдены в OS volume) | REC; A-MA §1; MATRIX (аудит, `new`) |
| N2 | `MSVLyricsTTMLParser`: framework, модель, правила парсинга | CLOSED (MediaServices, 39 методов) | TTML §0–§11; `new` |
| N3 | `vi-nnet` (Apple Music Sing / vocal attenuation): архитектура | CLOSED (stateful streaming TCN, 36 блоков, 48 state) | VIN; FRA §8; `new` |
| N4 | UIKit sheet presentation: длительность/кривая | CLOSED (0.4 s; spring ζ=1.0, response 0.3441442326) | NPAS §A; FRA §7; `new` |
| N5 | MediaCoreUI `Backdrop.CompositeRenderer.crossfadeDuration` | CLOSED (0.8f) | NPAS §B; FRA §7; `new` |
| N6 | Artwork morph: геометрия/модификатор | PARTIAL (механизм EXACT; множитель 0.73 — роль не установлена) | NPAS §C; A-MA §3.6 |
| N7 | AutoMix artwork UI (Alchemy 3.0/4.8/0.6/1.3) | CLOSED (app-часть) | A-MA §3.5; NPAS §C.4; `new` (AppOS) |
| N8 | `bypa` полярность + runtime-запись | PARTIAL (полярность EXACT; callsite `bypa=0` NOT FOUND) | DSPR §A; FRA §1 |
| N9 | Per-track DSP-инстанции / shared aux bus | CLOSED (две независимые аллокации; shared — RETRACTED) | DSPR §D; FRA §1; `new` |
| N10 | Целостность `MusicUIService`/`AdaptiveMusicApp` (усечённые копии) | CLOSED (переизвлечено 117 520/300 160) | REC; AT-RTA R4; `new` |
| N11 | Panache numerics (metallib) | BLOCKED (только имена/entry) | A-MA-ASSETS §1 |
| N12 | Volume.ca consumer (44 анимации) | PARTIAL (ресурс EXACT; потребитель не найден) | A-MA-ASSETS §2; A-AN §1.3 |

**Итог:** 61 пункт = 43 CLOSED + 15 PARTIAL + 3 BLOCKED. Legacy-подсчёт согласован с MASTER §11
(33 CLOSED / 12 PARTIAL / 4 BLOCKED) и обновлён тремя финальными изменениями:
#2 BLOCKED→CLOSED (FBQ; FRA §10), #11 BLOCKED→PARTIAL (FBQ §2), #36 PARTIAL→CLOSED (NPAS §D).

---

# Appendix B. Retracted / corrected claims (deduplicated, 51)

Одна строка = одно утверждение (или группа дублей), снятое/исправленное red-team и closure-фазами.
Источники: `REC`, `RT/RC/DC/VC`, `AT-RTA`, `FRA`, а также RETRACTED-секции отчётов 10_final_closure.

1. «Music.app/Music отсутствует в IPSW; нужен AppVolume/устройство» → найден в OS volume (`090-89642-625.dmg`), RETRACTED (REC R1; AT-RTA R1).
2. «mtree — полный канонический листинг OS volume / 226 646 записей» → листинг частичный (нет `staged_system_apps`); инвентарь по `7z l` (REC R2/R10).
3. «AppOS = 851 файл / 150 каталогов» → фактически 1099 файлов / 150 каталогов (REC R3; AT-RTA).
4. «#34 BLOCKED — нет app binary» → app-часть CLOSED (Music.app/MusicApplication) (REC R4).
5. «#24 MusicCoreUI/LyricsX отсутствуют (нужен App Cryptex)» → найдены (MEE + статическая линковка в MusicApplication) (REC R5).
6. «aux bus shared между треками» (гипотеза порта) → RETRACTED: две независимые per-track инстанции (DSPR §D.3; REC R6).
7. «equal-power = `cos/sin(π/2·t)`» → REJECTED: π/π2/0.7071 в бинарях AutoMix отсутствуют (RT R2; DC D13).
8. «`plusL` — luminance-add» и «`plusL` formula = saturate(in + tint·alpha)» как факт → матрицы `plusL/plusD` EXACT; per-pixel формула — INFERRED (REC R7; RC A2; DC D24; LGC §9).
9. «instrumental threshold 7000 ms» → NOT FOUND для iOS; интерлюд model-driven (REC R8; LYR §2.5/§5).
10. «100.0 / 0.95 (и 39.0) — EXACT double-константы» → не подтверждены аудитом (float/отсутствуют), не hardcode (REC R9; LYR §4; MASTER §13).
11. «42 334 Swift-символа в extension» → 42 202 (Swift 6.0.3 demangler) (REC R11).
12. «#49 CLOSED vs PARTIAL конфликт» → принят PARTIAL (`entryOffsetUs` не доказаны) (REC R12; MASTER §11).
13. «`musicapp_ghidra/` пуст» → фактически 65 030 pseudocode/65 034 disasm, 689 MB (REC R14).
14. «MusicUIService/AdaptiveMusicApp извлечены корректно» → первые копии усечены, переизвлечены (REC R15; AT-RTA R4).
15. «Strategy class names fabricated (0 rg hits)» → имена реальны (witness-таблицы) (RT R1; DC D2).
16. «Linkwitz-Riley 2nd order / Q=0.707; HP sweep 20→250, LP 20000→250» → REJECTED: resonance в dB, диапазоны 10..22050 / 10..21829.5 (RT R3; DC D13/D27).
17. «Squircle exponent n≈4.4» → REJECTED: полином `P(t)·t²` (RT R4; DC D26).
18. «Манифест: TTML parser / x-bg — контент corpus» → REJECTED: парсер внешний (RT R5; DC D20).
19. «`..._SYLLABLE.ttml` — отдельный вариант» → REJECTED: byte-identical PLAIN (RT R6; DC D21).
20. «`Transition.Complexity` — лестница 0..4 c `beatMatched=4`» → REJECTED: ровно 4 кейса 0..3; rank 4 — sentinel (RC A1; DC D1; FRA §10 не затронут).
21. «MediaAPI: все три предиката `significant ⟺ band`» → полярность разная (acousticness/melodicness TRUE вне полосы); pairing outgoing/incoming — STRONG (RC A3; DC D16).
22. «KEY_SYMBOLS_01 rows 66–68: Address = layout» → адреса — `__constg_swift`; истинные `_type_layout_string` 0x272298d10/0x2722a2710/0x2722a2570 (RC A4).
23. «0.75 fcsel @0x272234534» → `fmov` @0x272234534, `fcsel` @0x272234538 (RC A5; DC D6).
24. «Summary 0x50 B, копия 0x51 B» → 1-байтовое расхождение не объяснено (caveat; RC A6).
25. «`bypa` полярность UNVERIFIED / default 1 противоречив» → EXACT: 1=bypass, 0=active; runtime-запись `bypa=0` NOT FOUND (DSPR R2; FRA §1).
26. «`ts_rate` — узел `.dspg`» → RESOLVED: в графе отсутствует; доставляется `AVPlayerItem.speedRamp` → ME TimePitch (Spectral) (DSPR R3; FRA §2).
27. «Времена `out_gain` — BLOCKED» → механизм/конверсия EXACT (CMTime 1e9, volume ramps); normalized→absolute — PARTIAL (DSPR R4).
28. «Sheet-переход Music.app наследует 0.5 s» → CORRECTED: базовая длительность 0.4 s; 0.5 s — noninteractive аниматор Palette (NPAS RETRACTED 1).
29. «`Backdrop.CompositeRenderer.crossfadeDuration` — NOT FOUND» → CORRECTED: 0.8f (NPAS RETRACTED 2).
30. «#36 ключ UNRESOLVED» → CORRECTED: `PPTContentOffsetScrollIncrement` (default 10.0) (NPAS RETRACTED 3).
31. «tagged-константа 0xD00000000000001F — отдельный ключ» → это count+flags Swift-строки (NPAS RETRACTED 4).
32. «`MSVLyricsTTMLParser` в MediaPlayer.framework» → MediaServices.framework (TTML §0; FRA §9).
33. «x-bg/duet/emphasis на стороне MSV не видны» → правила восстановлены (TTML §3/§6/§7).
34. «Instrumental/interlude приходит из `MSVLyricsSongInfo`» → TTML-парсер instrumental-строк не создаёт; `instrumentalBreak` не заполняется/не читается (TTML §9/§13.3).
35. «TTML-парсер отсутствует в iOS corpus (0 вхождений)» → найден в MediaServices DSC (TTML §13.5).
36. «Music.app ordinal 0x1d / 40 методов / entries 1725–1728» → ordinal 85; 39 методов; индексы 3450–3457 (FRA §9 CORRECTION).
37. «variable blur — 5-tap cross (center + ±dx/±dy), Σ/5» → CORRECTED: 4 тапа (±dx·lod, ±dy·lod), Σ·0.25, центра нет (LGC RETRACTED 1).
38. «Uniform-дефолты — „значения системы“» → это статические дефолты CA-стороны; эффективные — recipe/runtime (LGC §8/RETRACTED 3).
39. «Соседние GOT-слоты planner-stub — импорты MusicKit/caulk/Packages» → это ровно planner-API `_SonicKit_MusicKit_Packages` (MC RETRACTED 1).
40. «Caller `transition()` вне предоставленных образов (Music.app/appex)» → importer внутри `_SonicKit_MusicKit`; literal call-site не найден; app-бинари planner не импортируют (MC RETRACTED 2).
41. «Witness-адреса 0x1801d78b0/0x1801d79d0 — вне корпуса, блокер» → libswiftCore resilient value-witness stubs, не блокер (FBQ A1).
42. «`param_1` = произвольное Double-поле +8» → `T_end(outgoing) − T_end(incoming)` (FBQ A2; FRA §10).
43. «tag @+0xa9 = doubled/halved/normal» → поле `tempoRatio: TempoBinaryRatio` (FBQ A3).
44. «FIELD_USAGE_MATRIX: bpm.main / beats / key / loudness.value used by planner» → consumer-negative (M2P §5.1; P2 §1.2).
45. «videoEvents/visualTempo „NOT ESTABLISHED“» → `FlexAnalysis.events` импортируется (PARTIAL); visualTempo/arousal/valence — без потребителя (M2P §5.2).
46. «`percentDeviation` → `beatStabilityMap`» → опровергнуто: stability = допуск 0.031 s (M2P §5.4).
47. «Класс эффекта = `AVAudioMixProcessingEffect` (EXACT)» → EXACT→STRONG_INFERENCE (имя класса в отсутствующем text-сабкеше) (FRA §1).
48. «lambda @0x1b8f62790 = `ParameterSchedule::Event` callback (EXACT)» → EXACT→PARTIAL (роль/сигнатура не доказаны) (FRA §2).
49. «Текст CFString „Spectral“ (EXACT)» → EXACT→STRONG (len 8 + импорт) (FRA §2).
50. «Target volume-рампы = `AVMutableAudioMixInputParameters` (EXACT)» → EXACT→STRONG_INFERENCE (вывод из API-селекторов) (FRA §3).
51. «Карв `MediaCoreUI.macho` provenance (EXACT)» → значение EXACT / provenance PARTIAL (сабкеш `.21` не в наборе) (FRA §7).

---

# 1. Что теперь доказано

**AutoMix / TransitionPlanner.** Точка входа `TransitionPlanner.transition(from:to:criterias:)`
(`0x272267700`, `_SonicKit_MusicKit_Packages`) — EXACT; 4 алгоритма и ровно 4 `Complexity` 0..3 с
маппингом Fallback 0 / DeadAir 1 / Smart 2 / BeatMatched 3 (PLC §9). Скоринг: `base × Π(факторы)`,
отбраковка `≤0`, `+ param_1×0.001`; базы 10.0/15.0/2.0/1.0/3.0; winner — строгое `>`; score @+0xF8
записи 0x100 B (PLC §10/§12; `07_audit/VERIFIED_CONSTANTS.md` #4–6, #13–14). Пороги: tempo 0.16/0.287,
`60/bpm`, тег `0xfc`, min bars 8, музыкальность 0.85/0.3/0.25, штраф 0.75, drift 0.04, beat-stability
0.031 s, Fallback 2.0 s (PLC §4–§8, §13; P2A/P2MD). Tie-breaker #2 закрыт: `param_1 =
T_end(outgoing) − T_end(incoming)` (FBQ §1; FRA §10 — EXACT). Каталог `TransitionStyles.json`
(14 стилей, 74 автоматики, sha256 `fe3d0a36…d120`) получен и нормализован; из стилей iOS 26 реально
достижимы только id 8/9/12 (BeatMatched), остальные — zero-fill/legacy (A-TS; TS §2.1; PLC §11).
Отчёты: `10_final_closure/TRANSITION_PLANNER_CLEANROOM_SPEC.md`,
`10_final_closure/TRANSITION_STYLES_IMPLEMENTATION_SPEC.md` + `transition_styles_normalized.json`,
`09_appos/APPOS_TRANSITION_STYLES.md`, `08_closure/P{1,2,3}_AUTOMIX_CLOSURE.md`.

**DSP / граф.** Полный текст `DSPGraph.dspg` (graphName `SmartTransitions`), узлы, 14 wire-рёбер,
dry/wet-ветки; AU subtypes `filt/hpas/lpas/dely/rvb2`; 29 `AutomationEffectParameter` (27 в графе;
`ts_rate`/`out_gain` — вне графа) с диапазонами/дефолтами; полярность `bypa` (1 = эффект обойдён,
0 = активен); time-stretch исполняется ME TimePitch (`tmpt`) при `AVAudioTimePitchAlgorithmSpectral`,
`ts_rate` доставляется через `TimeStretchingStep → speedRampMappings → AVPlayerItem.speedRamp`;
per-track — две независимые инстанции `AVAudioMixProcessingEffect` (shared aux RETRACTED);
`out_gain` применяется volume-рампами `AVMutableAudioMixInputParameters` (CMTime timescale 1e9).
Отчёты: `09_appos/APPOS_DSPGRAPH.md`, `10_final_closure/DSP_RUNTIME_ARCHITECTURE.md`,
`TRANSITION_STYLES_IMPLEMENTATION_SPEC.md` §7–§8, `08_closure/P1_DSP_CLOSURE.md`, `P2_MEDIADSP_CLOSURE.md`.

**MediaAPI.** Трёхзвенная модель `MediaAPI JSON → MusicKit.Cloud* → MusicKitInternal →
TransitionPlanner.Song.MusicKitAnalysis`; конверсии ms→s (`FUN_1d41218c4` ÷1000) и Flex
`score→TimeScale/amplitude` EXACT; карта потребителей: `loudnessCurve` и `vocalActivities`
(strength/kind) — EXACT, acousticness/danceability/melodicness — EXACT-предикаты (остров↔поле STRONG),
`FlexAnalysis.events` — PARTIAL; bpm/beats/key/fades/phrases/energy/valence/entryPoints — без
доказанного потребителя планировщика (consumer-negative). #13/#15 — consumer-negative EXACT.
Отчёты: `10_final_closure/MEDIAAPI_TO_PLANNER_CALLCHAIN.md`, `blocked_13_15.md`,
`08_closure/P1_MEDIAAPI_CLOSURE.md`, `P2_MEDIADSP_CLOSURE.md`.

**Lyrics / TTML.** Парсер `MSVLyricsTTMLParser` найден в `MediaServices.framework`: 39 ObjC-методов,
полная модель `MSVLyrics*` (14 классов), все элементы/атрибуты, тайминг (`msvl_timeValue`), порядок
строк, переводы/транслитерации, x-bg/duet, primaryVocalText, malformed-поведение; цепочка
`TTML → NSXMLParser → MSVLyricsSongInfo → LyricsX.Lyrics` (TTML §1–§11). Рендерер `LyricsX`:
`Specs` (63 поля), тайминг-менеджер (`maxSelectedLines=2`, `maxEndTimeOffset=0.5`, `lineDelay=0.05`,
`animationHeadstart=0.1`), аниматоры 0.12/0.28 cubic, blur cap 4.0, karaoke gradient [color, α0] +
feather 30, прогресс-guard 0.5, instrumental (3 точки 12/8/40, α 0.1, формулы reset/fade), seek fast
path 0.25/curve 3, insets 22/30, contentOffset-формулы, hysteresis 1.0/0.5. Отчёты:
`10_final_closure/TTML_PARSER_CLOSURE.md`, `LYRICS_RENDERER_IMPLEMENTATION_SPEC.md`,
`09_appos/APPOS_LYRICSX.md`, `APPOS_MUSICCOREUI.md`, `08_closure/P{2,3}_LYRICS_PORT_CLOSURE.md`.

**Animations.** Закрыты: app-переходы Palette (`0.5 s` + MPCubicSpring m3 k500 c1000) и базовая
sheet-анимация UIKitCore (**0.4 s**, spring ζ=1.0, response 0.3441442326 → k 333.33333328,
c 36.51483716); marquee (3.0 s, 30 pt/s, keyTimes, frameInterval 0.016); waveform (`BouncyBars.caar`,
3 варианта × 5 баров, 12 keyTimes, values 24…93.7); pill-bounce (0/−2/+2);
highlight-кривые; `#36` (ключ `PPTContentOffsetScrollIncrement`, default 10.0);
`Backdrop.crossfadeDuration 0.8f`; AutoMix artwork 3.0/4.8/0.6/1.3; Volume/ForwardBackward/PlayPauseStop
.ca-параметры. Отчёты: `10_final_closure/NOW_PLAYING_ANIMATION_SPEC.md`,
`09_appos/APPOS_ANIMATIONS.md`, `APPOS_MUSIC_APP.md`, `APPOS_MUSIC_APP_ASSETS.md`,
`08_closure/P3_ANIMATIONS_CLOSURE.md`.

**Liquid Glass.** Профиль continuous corner `P(t)·t²` (5 double-коэффициентов), expansion factor
1.5286649465560913 (= double `0x3FF875696E58A32F`, float32 совпадает с шейдерной), AA `1e-4`, meniscus
+ 7-tap chromatic aberration, variable blur (4 тапа, LOD `max(0,log2(r<2?0.5r+1:r))`), mip-cap
`2^min(chain,7)`, статические веса `downsample_blur_2/4`, порядок 9 фильтров и BlurAtEnd,
`platformContentGlass*` (blurRadius 45 + colorMatrix), статические uniform-дефолты, матрицы
`plusL/plusD` (`out.rgb=in.rgb+rgb·a`, `out.a=in.a+a` и PlusD-вариант), CASDF Highlight/Displacement
defaults. Отчёты: `10_final_closure/LIQUID_GLASS_IMPLEMENTATION_SPEC.md`,
`09_appos/APPOS_GLASS_RECIPES.md`, `08_closure/P1_GLASS_CLOSURE.md`, `P2_GLASS_CLOSURE.md`,
`decompiled_package/glass/air_ir/*.ll`.

**vi-nnet (Apple Music Sing).** Архитектура сети EXACT: вход `[1,2,4096]`, encoder k64/s32 → 384
канала, latent 448, 36 TCN-блоков с dilations 1..256 ×4, 48 state-тензоров (concat/slice, без
RNN-операторов), mask head `[2,1,385,1]` pad 160 → sigmoid, decoder conv_transpose k64/s32; plist:
MIL2BNNS/CPU/BlockSize 4096/Lookahead 16384/44.1 kHz/StreamingMode 1; веса 15 551 232 B
(sha256 `d0bb9ffb…8554`). Отчёт: `10_final_closure/VI_NNET_ARCHITECTURE.md`; аудит — FRA §8.

**Clean-room контракт.** Интерфейсы данных (`SongAnalysis`, `TransitionCandidate`, `TransitionPlan`,
`TransitionStyle`, `AutomationRamp`, `DSPParameter`, `DSPGraphInstance`, `LyricsModel`,
`RendererConfig`), pipeline Track A/B, fallback-матрица, testability checkpoints и границы Media3
зафиксированы в `10_final_closure/ANDROID_CLEANROOM_CONTRACT.md` (полнота 80.2%). Черновые Kotlin-
контракты существуют: `kotlin_contracts/analysis/SongAnalysis.kt`,
`kotlin_contracts/automix/TransitionModels.kt` (research-only, вне `deepseek_analysis/`).

**Provenance.** Аудит AppOS: 19/19 криптосверок, дерево 130/132 (2 повреждённые копии переизвлечены),
аудированные 10 закрытий — без ложных (FRA). Итог: 43/61 пунктов CLOSED.

# 2. Что остаётся неизвестным

- **Планировщик:** literal call-site `transition(...)` (#9); имена requirement-методов стратегий (#10);
  точный источник planner-полей `beatEvents/downbeatEvents/bars/beatStabilityMap` (PLC §1.4/§5.4);
  формула tonality relationship (PLC §6); биты `MusicalCompatibility.SongIssues` (PLC §14.3);
  семантика `LineAction`/`SelectedLinePosition` (LYR §2.1/§4); единицы `duration` 8/8/16
  (бары vs секунды, TS §10.1); точный байт `ease-in-4`/`ease-out-4` внутри диапазонов 0x02–0x3f /
  0x42–0x7f (TS §10.5).
- **DSP:** позиция TimePitch относительно DSPGraph (STRONG_INFERENCE), роль/сигнатура lambda
  `ParameterSchedule::Event` (PARTIAL), teardown/reset графа (PARTIAL), внутренняя дедупликация по
  identifier (NOT FOUND), формула alignment/pivot в stretched time (PARTIAL).
- **MediaAPI:** семантика `percentDeviation` (#13) и шкала `loudness.{value,range,peak}` (#15);
  конвертер cloud→internal для `loudnessCurve` (PARTIAL) и `fades`; остров↔поле для
  acousticness/danceability/melodicness/tonality (STRONG/PARTIAL); scoring-потребитель `videoEvents`
  (PARTIAL); tag-и `VocalActivityStrength`; окно/интерполяция `LoudnessMap`.
- **Lyrics:** endpoint доставки song-TTML (#25); образцы `itunes:timing="Line"/"None"` и `ttp:*`
  (#26); порог инструментального брейка (в iOS отсутствует как константа); `currentWord`-реализация;
  haptics/RTL-специфика рендера; значения `Word.CrossfadeAnimationParameters`.
- **Animations:** модификатор/условие множителя 0.73 в artwork morph (N6); Panache numerics (N11);
  потребитель `Volume.ca` (N12); enum-значение аргумента `8` в `+[UITransitionView
  defaultDurationForTransition:]`; роль `LyricsSharingAnimationController` (sharing vs открытие лирики).
- **Glass:** геометрия cap 12 сегментов reflection (#42); числовые веса `tile_simd_blur`/`narrow_blur`
  для конкретного radius (#47); per-pixel формулы Highlight (kind 6) / Displacement (kind 7);
  эффективные uniform-значения (после recipe/интерполяции); потребитель `CA_DISABLE_PLUSL_CLAMP`.
- **Прочее:** Genre ID↔name и равенство `MusicKit.Genre.ID.rawValue` web-ID (#11); статус
  `availableTranslations`/`instrumentalBreak` (кто заполняет); vi-nnet BNNS op-mapping; назначение
  `1.00390625` в front-norm; 2 повреждённые копии ресурсов (переизвлечены — контрольная проверка
  не повторялась).

# 3. Что требует runtime capture / device

1. `bypa`/property 21: дамп значения на устройстве (LLDB) — подтвердить «эффекты по умолчанию
   обойдены» и найти callsite `bypa=0` (DSPR §A.4, NOT FOUND #1).
2. Backtrace по `0x2721d5ef0+slide` / `0x272267700+slide` — literal call-site planner (#9).
3. `entryOffsetUs` и normalized→absolute времена — runtime-лог `Transition.Summary`/CMTime (#49; DSPR §C).
4. Getter'ы `0x1d4399720` (`percentDeviation`) и `0x1d439a0b8` (`loudness.peak`) — LLDB/trace
   (#13/#15).
5. Пользовательские prefs: `SheetDampingRatio`/`SheetResponse`/`SheetHighSpeedDampingRatio`,
   `PPTContentOffsetScrollIncrement` (NPAS §A/NOT FOUND).
6. Metal/GPU-trace glass: tileW/H, число mip-уровней на кадр, веса blurs, MTLPixelFormat слоя (#47).
7. Network-trace (mitm) Apple Music: endpoint TTML (#25), формат доставки word/syllable-варианта (#33).
8. Genre catalog: `GET /v1/catalog/{sf}/genres` (developer token) либо дамп БД медиатеки (#11).
9. vi-nnet: реальная задержка/упаковка BNNS-буферов и op→BNNS mapping (VIN §8/§10).
10. Биты `MusicalCompatibility.SongIssues`, теги `VocalActivityStrength`, окно `LoudnessMap` — только
    runtime/полный DSC.

# 4. Что невозможно получить из текущего корпуса

- **Отсутствующие text-сабкеши:** executable `.66` `_SonicKit_MusicKit` (DSC) — блокирует независимую
  проверку имени класса эффекта, острова `0x2743d…`, полную цепочку speedRamp→TimePitch и literal
  call-site; text-сабкеши AVFoundation/AVFCore/AVFAudio (нет ни в `appos/sys_dsc`, ни в
  `extracted_dylibs`) — порядок TimePitch/DSPGraph и internal teardown графа.
- **Удалённый branch-pool** `/srv/research/tmp/dscfull/**` (P2-провенанс островов): повторный резолв
  «остров→getter» невозможен; звенья acousticness/danceability/melodicness/tonality остаются
  STRONG/PARTIAL.
- **`.13` MediaServices** отсутствует в `appos/sys_dsc` (использована копия
  `/tmp/opencode/msv/dyld_shared_cache_arm64e.13`); для воспроизводимости TTML-задач `.13` нужно
  добавить в набор.
- **Карв `MediaCoreUI.macho` из `.21`**: значение 0.8f проверено, provenance — PARTIAL (FRA §7).
- **Swift-слой MobileSafari/UIKitCore локальные символы:** геометрия reflection 12 сегментов; enum
  `defaultDurationForTransition:`; formula-детали SwiftUI-модификаторов.
- **libswiftCore witness-таргеты** (`0x1801d78b0/0x1801d79d0`) и resilient-аксессоры — имя поля
  `StylingRegion` не читается статикой (FBQ §1.5).
- **Серверная часть Apple:** song-TTML endpoint, серверный выбор word/syllable, заполнение
  `instrumentalBreak`/`availableTranslations`, developer-token API — вне бинарей.
- **AudioUnit/DSP-плагины:** внутренности `aufx-nnet-appl` (BNNS-план), per-pixel шейдеры kind 6/7,
  runtime tuning glass — нужен GPU/audio trace.
- **User/device state:** prefs, БД медиатеки, storefront/catalog — только устройство.

# 5. Safe list для Astra (отдавать в реализацию)

Разрешено переносить как есть (EXACT/STRONG; значения с провенансом; аудит не оспаривает):

1. **Данные стилей:** `TransitionStyles.json` (14 стилей, 74 автоматики, placement/времена/значения/
   interpolation) + `transition_styles_normalized.json`; ids 8/9/12 reachable, прочие — zero-fill;
   `duration` 8/8/16 как максимум длительности (семантика единиц — см. §6).
2. **DSP-данные:** `DSPGraph.dspg` (текст/узлы/wires), AU subtypes и порядок,
   29 `DSPParameter` (id/fourCC/range/default/target), 27 в графе; bypass-контракт (property 21;
   1 = bypass) — как политика конфигурации.
3. **Evaluator/кривые:** `linear`, `ease-out-2`, `ease-out-0.5`, `ease-in-0.5`, log-кривая 0x81,
   `value = start + y(p)·(end − start)`; диапазоны 0.16/0.287 и т.д.
4. **Планировщик:** Complexity/Algorithm (4+4), маппинг алгоритм→complexity, формула score
   (`base × Πfactors + tie×0.001`, отбраковка ≤0), базы 10/15/2/1/3, пороги (0.75, 8, 0.04,
   0.031 s), winner-семантика, Fallback 2.0 s; формула #2 (`T_end diff`) с tie=0.0 по умолчанию.
5. **MediaAPI-потребители (доказанные):** `loudnessCurve` (dB-like, ~2 Гц), `vocalActivities`
   (start/end s, kind singing/speech/rapping), acousticness/danceability/melodicness-предикаты,
   Flex `events → TimeScale/amplitude`; конверсии ms→s.
6. **DSP runtime-контракт:** две per-track инстанции; `out_gain` — volume ramps CMTime 1e9;
   `ts_rate` — speedRamp/ME TimePitch (Spectral); независимые outgoing/incoming.
7. **Lyrics model/parser:** правила `MSVLyricsTTMLParser` (элементы/атрибуты/время/порядок,
   translations/transliterations, x-bg, primaryVocalText, malformed-поведение) как clean-room
   спецификация; `LyricsModel`-схема контракта.
8. **Lyrics renderer константы:** `maxSelectedLines 2`, `maxEndTimeOffset 0.5`, `lineDelay 0.05`,
   `animationHeadstart 0.1`, opacity 0.12 cubic (0.33,0)/(0.2,0.1), line-change 0.28 (0.17,0)/(0.83,1),
   emphasis 1.0…1.14, deselected 0.98/0.9, highlight springs, syllable-formula, blur 4.0/3.0,
   прозрачность/guard 0.5, gradient/feather 30, instrumental-геометрия и формулы, insets 22/30,
   contentOffset-формулы, seek 0.25/curve 3, hysteresis 1.0/0.5.
9. **Animations:** sheet 0.4 s + spring (ζ=1.0, response 0.3441442326; high-speed ζ=0.8), backdrop
   0.8, marquee 3.0/30/0.016, pill 0/−2/+2, highlight-кривые, waveform keyframes,
   AutoMix artwork 3.0/4.8/0.6/1.3, `#36` ключ (default 10.0) — с пометкой «testing hook».
10. **Glass:** профиль `P(t)·t²` + 5 коэффициентов, factor 1.5286649465560913, AA `1e-4`,
    meniscus/aberration формулы, variable blur формула (веса runtime), mip-cap 7, статические веса
    downsample, порядок фильтров, recipes (blur 45 + colorMatrix), матрицы plusL/plusD,
    CASDF defaults — как defaults.
11. **vi-nnet:** топология/plist/weights как данные для воспроизведения модели (BNNS-план — runtime).
12. **Android-контракт:** схемы данных, pipeline Track A/B, fallback-матрица, testability checkpoints;
    черновые Kotlin-контракты `kotlin_contracts/**` (research-only).

# 6. Do-not-hardcode list (Astra — НЕ хардкодить)

1. `tileWidth/tileHeight`, число mip-уровней на кадр, числовые веса `tile_simd_blur`/`narrow_blur`,
   `MTLPixelFormat` слоя (runtime; ACC §3 #1–4).
2. Эффективные uniform-значения (`edge_*`/`shadow_*`/`sdr_*`/`holding_tone_opacity`), CASDF
   `defaultValues` как «истина приложения», per-pixel шейдеры kind 6/7 (ACC §3 #5; LGC §5/§8).
3. Геометрия reflection 12 сегментов — только конфиг/дефолты (ACC §3 #23).
4. `bypa=0` и «граф стартует закрытым» как факт (полярность EXACT, runtime-запись NOT FOUND;
   ACC §3 #7).
5. Имя/единицы tie-breaker `param_1` — использовать 0.0 + фича-флаг (ACC §3 #8; PLC §12.2).
6. Genre ID/ID→name и любые Android/VKID-маппинги (ACC §3 #13).
7. `bpm.percentDeviation`, `loudness.peak/value/range`, `bpm.main`, `beats/bars` как входы
   предикатов (consumer-negative; ACC §3 #14–15).
8. `cos/sin(π/2·t)` и π-константы equal-power; `sqrt`-пара как отдельная формула (реализовать через
   curve-движок; TS §9/§10; ACC §3 #16).
9. `duration` 8/8/16 как секунды; `beat_length`-резолвер; fallback→style 4; привязка не-BM стилей
   (0–4/33/44) к алгоритмам (TS §10; ACC §3 #18/#20).
10. `1.12` scale, `smoothstep`, `10dp/6dp`, `750/250 ms` (Android prior art); viewport anchors
    28%/38%; `USER_SCROLL_PAUSE_MS=4000`; `instrumental 7000 ms`; `wordOffset=-100ms`
    (LYR §4; ACC §3 #10–12/#21).
11. `100.0` (lineChange stiffness), `0.95` (touchDown), `39.0` (paragraphSpacing) — PARTIAL/не
    подтверждены (LYR §4; ACC §3 #9).
12. Семантика `LineAction`/`SelectedLinePosition`, `anchorPoint ±1.3` (имена/ось не восстановлены;
    LYR §4/§5).
13. Позиция TimePitch относительно DSPGraph как факт — интерфейсы блоков сохранять
    взаимозаменяемыми (Apple-порядок STRONG; ACC E1/E2: `PORT`).
14. Per-pixel формула `plusL` как Apple-verified — фича-флаг (INFERRED; ACC §3 #17).
15. vi-nnet op→BNNS mapping и runtime-задержка (VIN §10).
16. `entryOffsetUs`/normalized→absolute времена (PARTIAL; ACC §3 #19).
17. Открытие/закрытие лирики как UIKit-transition в LyricsX (в бинаре отсутствует; app-side роль
    `LyricsSharingAnimationController` — INFERRED; LYR §2.7).
18. Panache numerics/Volume.ca-поведение (N11/N12) — данных нет.

# Artifact pointer index

- **`09_appos/`** — 16 отчётов: `APPOS_MASTER_SUMMARY.md` (консолидация, §11 таблица #1..#49,
  §13 остаточные неизвестности, §14 указатель), `APPOS_CLOSURE_MATRIX.md`, `APPOS_OVERVIEW.md`,
  `APPOS_MANIFEST.md`, `APPOS_TARGET_INVENTORY.md`, `APPOS_PROVENANCE.md`,
  `APPOS_TRANSITION_STYLES.md`, `APPOS_DSPGRAPH.md`, `APPOS_LYRICSX.md`, `APPOS_MUSICCOREUI.md`,
  `APPOS_NEW_SHADERS.md`, `APPOS_ANIMATIONS.md`, `APPOS_MUSIC_APP.md`, `APPOS_MUSIC_APP_ASSETS.md`,
  `APPOS_MUSIC_APP_DECOMP.md`, `APPOS_GLASS_RECIPES.md`, `APPOS_ML_MODELS.md`,
  `APPOS_RED_TEAM_AUDIT.md`; `transitions_styles_extract/` (normalized JSON + TSV);
  **`musicapp_ghidra/`** — `function_index.tsv` (65 035), `pseudocode/` (65 030), `disassembly/`
  (65 034), `symbols/`, `strings/`, `constants/`, `callgraph/`, `selective/`, `provenance/`.
- **`10_final_closure/`** — этот файл + `FINAL_RED_TEAM_AUDIT.md`, `FINAL_BLOCKED_QUESTIONS.md`,
  `blocked_13_15.md`, `STATE_RECONCILIATION.md`, `TRANSITION_PLANNER_CLEANROOM_SPEC.md`,
  `TRANSITION_STYLES_IMPLEMENTATION_SPEC.md`, `transition_styles_normalized.json`,
  `itunes_music_genre_tree_id_name.json` (526 узлов), `DSP_RUNTIME_ARCHITECTURE.md`,
  `MUSIC_APP_AUTOMIX_CALLCHAIN.md`, `MEDIAAPI_TO_PLANNER_CALLCHAIN.md`,
  `LYRICS_RENDERER_IMPLEMENTATION_SPEC.md`, `NOW_PLAYING_ANIMATION_SPEC.md`,
  `TTML_PARSER_CLOSURE.md`, `LIQUID_GLASS_IMPLEMENTATION_SPEC.md`, `VI_NNET_ARCHITECTURE.md`,
  `ANDROID_CLEANROOM_CONTRACT.md`.
- **`08_closure/`** — P1/P2/P3 closure-отчёты (6+4+5) + `P3_39_WAVEFORM_ADDENDUM.md` + копии
  `APPOS_*` (использовать версии из `09_appos/`).
- **`07_audit/`** — `RED_TEAM_AUDIT.md`, `DISPUTED_CLAIMS.md`, `RETRACTED_CLAIMS.md`,
  `VERIFIED_CONSTANTS.md`.
- **`06_android_port/`** — `ANDROID_ARCHITECTURE.md`, `ANDROID_AUTOMIX.md`, `ANDROID_DSP_ENGINE.md`,
  `ANDROID_LYRICS.md`, `ANDROID_LIQUID_GLASS.md`, `MEDIA3_INTEGRATION.md`.
- **`indexes/`** — `KEY_SYMBOLS.tsv` (1024), `KEY_SYMBOLS_0{1..7}_*.tsv`, `SHARED_BRIEFING.md`,
  `{automix,animations,glass,lyrics}_functions.tsv`, `binary_function_counts.tsv`.
- **`evidence/`** — `MEDIAAPI_live_1776914757_sanitized.json` (1 трек, массивы усечены).
- **`deliverables/`** — `deepseek_analysis_full.zip` (3.6 MB).
- **Сырьё (`/srv/research/apple-music-ios26/`)** — `appos/extracted/**` (Music.app, CoreMaterial,
  MediaCoreUI, MEE, `_SonicKit_MusicKit_Packages.framework/{DSPGraph.dspg,TransitionStyles.json}`),
  `appos/sys_dsc/**` (DSC subcaches `.03/.11/.33/.34/.70/.71/.75/.76`; `.13` и `.21` отсутствуют),
  `appos/fcs-keys.json` (приватный ключ, chmod 600 — не публиковать), `decompiled_package/**`
  (`targets/`, `automix/`, `lyrics/`, `animations/`, `glass/air_ir/`, `presets/`).
- **`kotlin_contracts/` (существует на момент закрытия)** — `analysis/SongAnalysis.kt`,
  `automix/TransitionModels.kt` (research-only clean-room контракты; остальные каталоги пусты).
- **Эфемерное (не полагаться на долговременное хранение):** `/srv/research/tmp/extracted_dylibs/**`,
  `/srv/research/tmp/dscfull/**` (удалён), `/tmp/opencode/**` (скрипты/карвы/Ghidra).

*Правило наследования: любое из указанных неизвестных при появлении нового raw-evidence заменяет
соответствующую строку Appendix A (PARTIAL/NOT FOUND → CLOSED) с указанием источника; значения не
«достраиваются». Секреты (FCS-ключи, accessKey, signed URL) в этом отчёте отсутствуют.*
