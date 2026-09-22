# FINAL_BLOCKED_QUESTIONS.md — финальное закрытие #2, #11, #13, #15

**Дата:** 2026-09-13. **Роль:** Final Closure / Blocked Questions Analyst (Phase 6).
**Ограничения:** research only; `/root/LMG-VK` и код приложения не изменялись; секреты не приводились.
**Метод:** disasm ARM64 (`automix/disassembly/**`), Ghidra pseudocode, raw Mach-O (`/srv/research/tmp/extracted_dylibs/_SonicKit_MusicKit_Packages`),
Swift-метаданные (`__swift5_fieldmd`/`__swift5_reflstr`/`__swift5_types`, layout string symbols), VWT/metadata records в `__AUTH_CONST`,
публичный сетевой endpoint Apple (без credentials), сверка с `08_closure/P1_AUTOMIX_CLOSURE.md`, `P2_AUTOMIX_CLOSURE.md`,
`P3_AUTOMIX_CLOSURE.md`, `10_final_closure/blocked_13_15.md`.

**Артефакты этой фазы (только `10_final_closure/`):**
- `FINAL_BLOCKED_QUESTIONS.md` (этот файл);
- `itunes_music_genre_tree_id_name.json` — `{id: {name, parent}}`, 526 узлов дерева Music (id=34) из публичного
  Apple-эндпоинта; получен этой фазой, см. §2.3.

---

## 0. Сводка вердиктов

| # | Вопрос | Прежний статус | **Итог Phase 6** | Ответ в одну строку |
|---|--------|----------------|------------------|---------------------|
| 2 | `param_1` (tie-breaker ×0.001) на `FUN_27222d644` | PARTIAL (имя поля BLOCKED) | **CLOSED** (единицы — STRONG_INFERENCE) | `param_1 = T_end(outgoing StylingRegion) − T_end(incoming StylingRegion)`; `T_end` = `UnstructuredStylingRegion.songTimeRange.upperBound.rawValue` либо вычисленный конец beat/bar-диапазона `StructuredStylingRegion.representation` (`FUN_27222bc88`, out[1]) |
| 11 | Genre ID→name | BLOCKED | **PARTIAL** (локальной таблицы нет; канон. маппинг добыт из публичного Apple API) | В бинарях/DSC/resources таблицы нет; имена приходят только с сервера/из БД; полное дерево Music (ID+name, 526 узлов) получено по публичному `itunes.apple.com/.../ws/genres?id=34`; `amp-api.music.apple.com/v1/catalog/us/genres` = 401 (нужен токен) |
| 13 | `bpm.percentDeviation` semantics | BLOCKED | **BLOCKED** (без изменений; consumer-negative EXACT) | getter `0x1d4399720` не вызывается ни из одного corpus-target; конвертер `FUN_1d4121a98` не масштабирует; live=1; см. §3 |
| 15 | `loudness.peak` scale | BLOCKED | **BLOCKED** (без изменений; consumer-negative EXACT) | `Statistics.{value,range,peak}` (`0x1d439a0a8/b0/b8`) не вызываются; планировщик читает только `loudnessCurve`; согласованной шкалы нет; см. §4 |

---

## 1. #2 — `param_1` tie-breaker: точная семантика поля

### 1.1. Идентификация типа 0x52 байта: `TransitionPlanner.StylingRegion` (EXACT)

В `automix/disassembly` символ `27222d248` — это `_get_enum_tag_for_layout_string_015_SonicKit_MusicB9_Packages17TransitionPlannerV13StylingRegionO`:

```
27222d248: ldrb w8,[x0, #0x51]      ; tag-байт
27222d24c: and w0,w8,#0x1
27222d250: ret
27222d254: mov w2,#0x52; b 0x272290d8c   ; ___swift_memcpy82_8 (size 0x52)
```

`0x27222d25c`/`0x27222d298` — single-payload tag get/store того же enum (tag @+0x51, spare @+0x52).
VWT и metadata в `__AUTH_CONST` (`/srv/research/tmp/extracted_dylibs/_SonicKit_MusicKit_Packages`, fileoff 0xAC000 → VA 0x2884a8a50):

| Тип (fieldmd) | metadata VA | kind | VWT VA | VWT size/stride | layout string VA | descriptor |
|---|---|---|---|---|---|---|
| `TransitionPlanner.StylingRegion` (enum, 2 case) | 0x2884ad498 | 0x201 Enum | 0x2884ad418 | **0x52 / 0x58** | 0x272298b28 | 0x2722a3bc4 |
| `TransitionPlanner.StylingRegionPair` (enum) | 0x2884ad278 | 0x201 | 0x2884ad1f8 | **0xab / 0xb0** | 0x2722987d0 | 0x2722a3b4c |
| `StructuredStylingRegion` (struct) | 0x2884ad400 | 0x200 Struct | 0x2884ad398 | 0x51 / 0x58 | 0x272298a40 | 0x2722a3ba8 |
| `StructuredStylingRegionPair` (struct) | 0x2884ad1d8 | 0x200 | 0x2884ad170 | **0xaa / 0xb0** | 0x272298690 | 0x2722a3b30 |
| `UnstructuredStylingRegion` (struct, 2×Double) | 0x2884ad510 | 0x200 | 0x2884ad4a8 | 0x10 / 0x10 | 0x272298c30 | 0x2722a3be0 |
| `UnstructuredStylingRegionPair` (struct, 0x20) | 0x2884ad2f0 | 0x200 | 0x2884ad288 | 0x20 / 0x20 | 0x272298940 | 0x2722a3b68 |
| `StylingRegion.Representation` (enum beat/bar) | 0x2884ad388 | 0x201 | 0x2884ad308 | 0x51 / 0x58 | 0x2722989b0 | 0x2722a3b8c |

Оба `Unstructured*` — тривиальные struct'ы из `Double` (layout string: refBytes=0; trailing skip=0x10/0x20). VWT header:
`metadata-8` = VWT, `metadata-0x10` = layout string (Swift `TargetTypeMetadataHeader`).

Swift-поля (P1-closer fieldmd, `/tmp/opencode/p1_closer/all_types_fields.txt` #66–#72):

```
StylingRegion (enum)          = structured(StructuredStylingRegion) | unstructured(UnstructuredStylingRegion)
StructuredStylingRegion       = { representation: Representation }
Representation (enum)         = beatRange(SongBeatRange) | barRange(SongBarRange)
UnstructuredStylingRegion     = { songTimeRange: Range<SongTime> }        // SongTime = { rawValue: Double }
StructuredStylingRegionPair   = { outgoingRegion @+0x00 (0x51), incomingRegion @+0x58 (0x51), tempoRatio @+0xa9 (1 B) }
```

Физические offsets `StructuredStylingRegionPair` подтверждены билдером `FUN_272234b0c`:
`out+0 ← outgoing` (272234ca8-cc4), `out+0x58 ← incoming` (272234cb8-cc8), `strb w24,[x19,#0xa9]` = `tempoRatio` (272234cc8);
`tempoRatio` из входа (`ldrb w24,[x23,#0xa9]`, 272234b88) масштабирует счётчик региона ×2/×1/÷2 (272234b8c-ba0) — это `TempoBinaryScaleFactor`.

### 1.2. Что делают `FUN_27222d51c`, `FUN_27222b7e0`, `FUN_27222b858` (EXACT)

`FUN_27222d51c(param_1, flag)` — функция, копирующая `StylingRegionPair` (0xab) и извлекающая из копии два значения (incoming/outgoing):

```
27222d53c: tbz w1,#0x0,0x27222d56c     ; flag.bit0==0 -> destroy и return 0.0
27222d548: bl 0x27222b858              ; (1) out1 = <incoming> StylingRegion (0x118)
27222d554: bl 0x2722132b8              ; copy out1 по VWT 0x2884ad418 (StylingRegion)
27222d558: ldrb w8,[sp,#0x111]         ; tag = copy+0x51
27222d55c: tbz w8,#0x0,0x27222d57c     ; tag==0 -> structured-ветка
27222d560: add x21,<copy>,#0x8         ; tag!=0 (unstructured): x21 = payload+8
...
27222d57c: (structured) memmove 0x51; x8=sp; bl 0x27222bc88   ; [start,end]
...
27222d5b0: ldr d8,[x21]                ; d8 = <incoming> значение (end)
27222d5b4: add x8,sp,#0xc0
27222d5bc: bl 0x27222b7e0              ; (2) out2 = <outgoing> StylingRegion
27222d5c8: (аналогично; tag @sp+0xa9)
27222d624: ldr d0,[x21]                ; d0 = <outgoing> значение (end)
27222d628: fsub d0,d0,d8               ; return outgoing − incoming
```

`FUN_27222b858` / `FUN_27222b7e0`: копируют весь pair (через `FUN_2722113c8` = VWT-копия `StylingRegionPair`,
table `[0x2884ad270]→0x2884ad1f8`) во временный буфер, затем извлекают одно поле:

```
b858 (incoming): tag = temp[0xaa];
  tag!=0 (unstructured): dest[0..15] = temp[0x10..0x1f]           ; pair.incoming (0x10)
  tag==0 (structured):   memmove(sp,temp,0xaa); FUN_272213440(sp+0x58,dest)  ; VWT-копия StructuredStylingRegion
  dest[0x51] = tag                                                 ; tag StylingRegion
b7e0 (outgoing): tag = temp[0xaa];
  tag!=0: dest[0..15] = temp[0..0xf]                               ; pair.outgoing (0x00)
  tag==0: FUN_272213440(temp+0x00,dest)
  dest[0x51] = tag
```

`FUN_272213440` — VWT-копия `StructuredStylingRegion` (table `[0x2884ad3f8]→0x2884ad398`, size 0x51).
Вывод: **tag pair 0 = structured**, **tag pair 1 = unstructured**; b7e0 = `outgoingRegion`, b858 = `incomingRegion`
(порядок полей fieldmd #66/#68 + builder FUN_272234b0c).

### 1.3. Семантика значения (EXACT по конструкции; единицы STRONG_INFERENCE)

- **unstructured** (`tag=1`): `dest+8` = второй Double полезной нагрузки 16 B = `Range<SongTime>.upperBound`
  (`SongTime = { rawValue: Double }`), это **конец** диапазона.
  Подтверждение конструкции в DeadAir `FUN_272237b14`:
  ```
  2722387d4: stp d10,d8,[sp,#0xd0]    ; outgoing range = (d10=start, d8=end)
  2722387d8: stp d11,d9,[sp,#0xe0]    ; incoming range = (d11=start, d9=end)
  2722387e0: strb w8,[sp,#0x17a]      ; pair tag = 1 (unstructured)
  ...       bl 0x2722113c8; bl 0x27222d51c
  ```
  (end = start+duration: `fadd d8,d10,d9` + `fcmp d8,d10` @2722384b0-b4.)
- **structured** (`tag=0`): `FUN_27222bc88` (0x27222bc88) считает диапазон события:
  ```
  27222bc90..: bl _OUTLINED_FUNCTION_11; bl 0x27222ca84   ; representation (meta 0x2884ad388)
  27222bcac: ldrb w8,[sp,#0xa8]; tbz w8,#0 -> 0x27222bcec
  bit0==1 (27222bcb4..): FUN_27222c364 / FUN_27222c458 -> d8,d0; fcmp d0,d8; b.pl (start<=end)
  bit0==0 (27222bcec..): FUN_27222c5dc / FUN_27220ff40 -> d8,d0
  27222bd08: stp d8,d0,[x19]          ; out = [start, end]
  ```
  `FUN_27222d51c` читает `out[1]` (x21 = out+8) ⇒ **end** (time события beat/bar-диапазона).
- Все 3 call-site в `FUN_272234380` (ветки ID 8/9/0xc) принудительно ставят pair tag = 0:
  `strb wzr,[sp,#0x162]` (2722345d0, 272234710, 272234a08), т.е. для BeatMatched это **structured end-time дельта**.
  DeadAir/Fallback строят pair сами и ставят tag=1 (unstructured): `strb w8=1,[sp,#0x17a]` в `FUN_272237b14`
  (2722387e0) и `FUN_272239248` (272239ac4); Scheduling-сайты `FUN_27223c6cc` копируют pair с уже готовым тегом.
- Скоррер `FUN_27222d644` (raw bytes подтверждены):
  ```
  27222d680: ldr d0,[x8,#0xc98]   ; raw fca9f1d24d62503f = 0.001
  27222d684: fmul d0,d9,d0        ; param_1 * 0.001
  27222d688: fadd d0,d0,d8        ; + product
  27222d68c: fcmp d8,#0.0; fcsel  ; product<=0 -> product
  ```

### 1.4. RETRACTED / CORRECTED (Phase 6)

| # | Где было | Было | Стало | Evidence |
|---|---|---|---|---|
| A1 | `P1_AUTOMIX_CLOSURE.md` §2.3, APPOS §11 #2 | «точное имя поля упирается в witness-вызов вне корпуса (`0x1801d78b0/0x1801d79d0`)» | Эти адреса — **libswiftCore stubs resilient value-witness** (`swift_cvw_resolve_resilientAccessors`-регион), используемые как `initializeWithCopy`/`destroy` для локальных layout-string типов; поля и offsets известны компилятору и зашиты в `FUN_27222b7e0/b858`. Блокера нет | VWT 0x2884ad1f8/0x2884ad418/0x2884ad398 (+0x10 = 0x1801d79d0, +0x08 = 0x1801d78b0, size/stride), metadata header (layout string @ −0x10), `__swift5_fieldmd` |
| A2 | P1 §2.2 | «param_1 = разность Double-поля (+8)» | Уточнение: +8 — это **конец** диапазона (`upperBound` / out[1]), а не произвольное поле | §1.3; DeadAir pair (start,end); `FUN_27222bc88: stp d8,d0,[x19]` + `ldr d0,[x21]` где `x21=out+8` |
| A3 | P1 §2.3 | «tag @+0xa9 = doubled/halved/normal» | +0xa9 — это поле **`tempoRatio: TempoBinaryRatio`** (fieldmd #66), оно масштабирует incoming-регион (×2/×1/÷2) | `FUN_272234b0c` 272234b88-ba0, 272234cc8; `strb wzr,[sp,#0x162]` = enum tag пары @+0xaa |

### 1.5. Вердикт, остаточный gap и как закрыть

**#2 = CLOSED.** `param_1` (d0) на всех 8 call-site `FUN_27222d644` = `T_end(outgoing StylingRegion) − T_end(incoming StylingRegion)`,
где для `unstructured`-региона это `songTimeRange.upperBound.rawValue`, для `structured` — вычисленный конец
beat/bar-диапазона (`FUN_27222bc88`, out[1]); в score добавляется `param_1 × 0.001`.

Остаточный gap (не блокирующий): **единицы измерения** Double. Все наблюдаемые величины — времена планера (SongTime);
STRONG_INFERENCE = секунды (на основании P2 #14: MediaAPI ms→s конверсии; `beatStability 0.031 s`). Прямого литерала
«секунды» в этом поле нет, т.к. это тип `SongTime`.
Как закрыть до EXACT-единиц: runtime-лог пар регионов/score на устройстве (LLDB breakpoint `0x27222d51c+slide`,
снять `d0..d9` и `[x20+0x18]`) либо dump `SongTime.rawValue` из `AudioAnalysis`-структуры.

---

## 2. #11 — Genre ID→name: локальная таблица vs Apple catalog

### 2.1. Локальный поиск (EXACT-негатив; таблицы нет)

| Поиск | Команда/метод | Результат |
|---|---|---|
| Все 16 extracted dylibs | `strings -a targets/* \| grep -xE 'Pop|Rock|Jazz|Hip-Hop/Rap|...'` | только `AudioToolboxCore` (17 совпадений) и `QuartzCore` (1) |
| `AudioToolboxCore` | контекст строк | это **ID3v1 genre list 0..147** из `ID3Parser.cpp` (`Blues, Classic Rock, …, Anime, JPop, Synthpop`) — не Apple Music catalog |
| `MusicKitInternal` | `strings`/`llvm-nm` | только API-типы и SQL-схема: `StorePlatformGenre`, `genreName/genreNames`, `all_genres TEXT`, `LEFT OUTER JOIN genre USING (genre_id)`, `MPModelGenre` — **без данных** |
| Music.app (Ghidra) | `musicapp_ghidra/function_index.tsv`, `strings/` | только legacy-код `MCDGenres*`/`MCDRadioGenres*` (DataSource/VC), таблицы ID→имя нет; `rg -i genre` по `animations_functions.tsv` = 0 |
| Ресурсы | `find /srv/research -iname '*genre*'` (кроме `deepseek_analysis`) | только .md/.asm/.c; `GenreTree.json`/`genres.plist` отсутствуют; `*.db/*.sqlite` в `appos/extracted/**` нет |
| MediaAPI/DSC | `10_final_closure/MEDIAAPI_TO_PLANNER_CALLCHAIN.md` §1; P2 §5 | приходят только **имена**: `SongAttributes.genreNames: [String]?`, `GenreAttributes.name: String?` |
| Планировщик | fieldmd #15–#17 (P2 §5) | `Genre { id: Genre.ID; subgenres: [Genre] }`, `Genre.ID { rawValue: String }`, `GenreTree/GenreFilter` — только структуры и сравнение `FUN_27223f618` |

### 2.2. Runtime-путь данных (EXACT)

- `_SonicKit_MusicKit.SongTransitionInfo.genres: [MusicKit.Genre]` — getter `0x27210c898` (KEY_SYMBOLS_03 #163);
  далее `Song.Analysis.init(genres:duration:audioAnalysis:…)` = `0x27225d900` (`MUSIC_APP_AUTOMIX_CALLCHAIN.md` §129/222;
  `TRANSITION_PLANNER_CLEANROOM_SPEC.md` §65). Т.е. в planner жанры попадают как **MusicKit.Genre** (каталожные ресурсы, id строковый) — имена есть в
  `MusicKit.Genre` (MusicKit.framework) и/или MediaAPI `GenreAttributes`.
- Локальный fallback на устройстве: БД медиатеки (`genre` ↔ `genre_id` INTEGER, `all_genres`) — **другое ID-пространство**
  (store genre id в БД библиотеки), смешивать с `Genre.ID.rawValue(String)` нельзя без проверки.

### 2.3. Сеть (эта фаза; без credentials, секреты не печатались)

```
$ curl -sS -o /dev/null -w '%{http_code}' https://amp-api.music.apple.com/v1/catalog/us/genres
401                              # Apple Music API требует developer token; не использовали
$ curl -sS 'https://itunes.apple.com/WebObjects/MZStoreServices.woa/ws/genres?id=34'          # 200, 239815 B
$ curl -sS 'https://itunes.apple.com/WebObjects/MZStoreServices.woa/ws/genres'                # 200, 1771153 B
```
- Публичное дерево Music (`id=34`) содержит 526 узлов `{id, name, url, subgenres}`; `url` каждого узла —
  `https://music.apple.com/us/genre/.../id<ID>` (например `music-pop/id14`, `music-hip-hop-rap/id18`),
  т.е. это **Apple Music catalog web genre ID** (прямой curl этих URL в этом окружении отдаёт 301→`/us/search`,
  но страница-ответ имеет заголовок «Music Genres and Categories on Apple Music»; URL-форма `.../id<ID>` взята
  из поля `url` самого ответа Apple).
- Полный маппинг сохранён: `10_final_closure/itunes_music_genre_tree_id_name.json`
  (`{id: {name, parent}}`; top-level 51: 14 Pop, 18 Hip-Hop/Rap, 21 Rock, 15 R&B/Soul, 17 Dance, 7 Electronic,
  12 Latin, 6 Country, 5 Classical, 11 Jazz, 24 Reggae, 16 Soundtrack, 19 Worldwide, 23 Vocal, 2 Blues, 3 Comedy,
  4 Children's Music, 8 Holiday, 13 New Age, 10 Singer/Songwriter, 22 Christian, 27 J-Pop, 29 Anime, 20 Alternative и др.).
- Live-JSON MediaAPI (`evidence/MEDIAAPI_live_1776914757_sanitized.json`): `song_attributes.genreNames = ["R&B/Soul","Music"]` — имена
  согласуются с деревом (15 и 34), ID в JSON нет.

### 2.4. Вердикт, остаточный gap и как закрыть

**#11 = PARTIAL.** Статической локальной таблицы `Genre.ID → name` нет (EXACT-негатив по бинарям/DSC/resources).
Канонический маппинг **добывается** (и уже сохранён) из публичного Apple-эндпоинта; имена в runtime доступны через
MediaAPI/MusicKit.Genre. 

Остаточный gap: доказать, что `MusicKit.Genre.ID.rawValue` (String) **численно совпадает** с ID из дерева `ws/genres`
(STRONG_INFERENCE: web-URL `id14/18/…` — Apple Music catalog URL; но не EXACT без ответа каталога).
Закрытие: (a) device/runtime capture `GET /v1/catalog/{storefront}/genres` (с developer token) или дамп
`MusicKit.Genre`/`GenreTree` на устройстве; (b) дамп БД медиатеки (`genre`,`all_genres`) — для library-жанров;
(c) локализация: `?l=ru`/storefront-specific catalog. **Не** переиспользовать Android/VKID и не считать
`MPMediaItemPropertyStoreGenreID` (Int) за `Genre.ID` без сверки.

---

## 3. #13 — `bpm.percentDeviation` (BLOCKED; из `blocked_13_15.md`, без изменений)

**Вопрос:** семантика/шкала `percentDeviation: Double?` (Cloud → `BeatsPerMinute.percentDeviation`).
**Статус: BLOCKED (consumer-negative EXACT; semantics UNVERIFIED).**

- Cloud: `MusicKit.CloudAudioAnalysis.CloudCompositeAttribute<Double>.percentDeviation` (CodingKeys `percentDeviation`,
  MusicKit DSC `.40`; P1_MEDIAAPI_CLOSURE §2.3). Internal getter: `0x1d4399720`.
- Конвертер `FUN_1d4121a98` — без `fmul/fdiv`: только 3× `fcvtzs` для `main/beginning/ending`; `percentDeviation` переносится как Double
  (`lyrics/pseudocode/1d4121a98__FUN_1d4121a98.c`).
- Потребитель NOT FOUND: getter `0x1d4399720` не вызывается ни из одного из 16 corpus-targets; 0 прямых `bl` в
  `lyrics/animations/automix/disassembly`; 0 в Music.app/MusicApplication; 0 `deviation` в strings планировщика и xrefs.
- Live JSON: `bpm={beginning:125, ending:129, main:129, percentDeviation:1}`; гипотезы `(main−beginning)/main=3.10 %`,
  `(main−beginning)/beginning=3.20 %` не дают 1; интервалы битов по краям 129.03/146.34 BPM — шкала не подтверждена.
- **Gap:** нет потребителя и нет калибровки шкалы; одна выборка (1 трек; массивы усечены) корреляцию не доказывает.
- **Как закрыть:** runtime-точка на getter `0x1d4399720` (LLDB) / трассировка декодера `CloudAudioAnalysis` на устройстве;
  либо дамп `AudioAnalysis` из Apple Music app.

---

## 4. #15 — `loudness.peak` (BLOCKED; из `blocked_13_15.md`, без изменений)

**Вопрос:** шкала `loudness.{value,range,peak}`.
**Статус: BLOCKED (consumer-negative EXACT; units UNVERIFIED).**

- Cloud: `CloudCompositeAttribute<CloudStatistics>{value/range/peak: Double?}`; Internal: `Statistics` getters
  `0x1d439a0a8/0x1d439a0b0/0x1d439a0b8`; конвертеры `FUN_1d4121c24/1d4121ef0/1d41240a4/1d41245f8` без `fmul/fdiv`.
- Потребитель NOT FOUND (тот же скан, что #13): `Statistics.*` и `AudioAnalysis.loudness` (`0x1d43966f0`) не импортируются/не вызываются;
  планировщик читает только `AudioAnalysis.loudnessCurve` (island `0x2743dd1c0` @ call `0x272223d58` в `FUN_272223b6c`),
  далее `LoudnessCurve.samplingFrequency/value`.
- Live: `main {value:-7.912872, range:5.786646, peak:0.4707109}`, `beginning {value:-10.99596, range:4.970956, peak:0.14886901}`,
  `ending {value:-6.661943, range:4.43449, peak:0.25221932}`; `20·log10(peak)` vs `value`: main +1.37 dB, beginning −5.55 dB,
  ending −5.30 dB — одной согласованной шкалы нет.
- **Gap:** нет кода-потребителя и калибровочных констант (линейная амплитуда 0…1 vs dB-подобная).
- **Как закрыть:** runtime-лог `loudness.*` на устройстве с одновременным `value/range/peak` и сравнение с `loudnessCurve`;
  либо поиск потребителя в app/appex вне corpus.

---

## 5. Строки для `UNRESOLVED_QUESTIONS.md` / `CLOSURE_TRACKER.md`

```
2.  param_1 tie-breaker ×0.001 — CLOSED. param_1 = T_end(outgoing StylingRegion) − T_end(incoming StylingRegion);
    T_end = UnstructuredStylingRegion.songTimeRange.upperBound.rawValue (payload+8) либо out[1] из FUN_27222bc88
    (end beat/bar-диапазона StructuredStylingRegion.representation). Тип: TransitionPlanner.StylingRegion (enum 0x52 B,
    VWT 0x2884ad418, tag @+0x51); pair — StylingRegionPair 0xab (VWT 0x2884ad1f8). «Witness вне корпуса» (0x1801d78b0/
    0x1801d79d0) = libswiftCore value-witness stubs, не блокер. Единицы (секунды) — STRONG_INFERENCE.
11. Genre ID→name — PARTIAL. Локальной таблицы нет (EXACT-негатив: binaries/DSC/resources). Имена — MediaAPI
    genreNames/GenreAttributes.name; канонический ID↔name добыт из публичного itunes ws/genres?id=34 (526 узлов,
    itunes_music_genre_tree_id_name.json). amp-api /v1/catalog/us/genres = 401 (token). Gap: доказать равенство
    MusicKit.Genre.ID.rawValue и этих ID (runtime capture / device DB); не смешивать с Android/VK и library genre_id.
13. percentDeviation — BLOCKED (consumer-negative EXACT; semantics UNVERIFIED; getter 0x1d4399720; unscaled; live=1).
15. loudness.peak — BLOCKED (consumer-negative EXACT; units UNVERIFIED; Statistics 0x1d439a0a8/b0/b8; читается только loudnessCurve).
```

---

## 6. Воспроизводимость (ключевые команды/файлы)

```bash
# Тип StylingRegion и его VWT/metadata (raw Mach-O)
llvm-nm-19 /srv/research/tmp/extracted_dylibs/_SonicKit_MusicKit_Packages | grep -i StylingRegion
llvm-otool-19 -l /srv/research/tmp/extracted_dylibs/_SonicKit_MusicKit_Packages   # __AUTH_CONST va=0x2884a8a50 fileoff=0xAC000
python3  # чтение qword по VA: foff = 0xAC000 + (va-0x2884a8a50); VWT @0x2884ad418 size=+0x40
# disasm/call-sites
rg -n 'bl 0x27222d51c' decompiled_package/automix/disassembly/     # 9 (3×272234380, 1×272237b14, 1×272239248, 4×27223c6cc)
rg -n 'bl 0x27222d644' decompiled_package/automix/disassembly/     # 8
# #2 значение
sed -n '1,80p' decompiled_package/automix/disassembly/27222d51c__FUN_27222d51c.asm
sed -n '1,45p' decompiled_package/automix/disassembly/27222b7e0__FUN_27222b7e0.asm
sed -n '1,36p' decompiled_package/automix/disassembly/27222b858__FUN_27222b858.asm
sed -n '1,44p' decompiled_package/automix/disassembly/27222bc88__FUN_27222bc88.asm
# #11 сеть
curl -sS -o /dev/null -w '%{http_code}\n' https://amp-api.music.apple.com/v1/catalog/us/genres   # 401
curl -sS 'https://itunes.apple.com/WebObjects/MZStoreServices.woa/ws/genres?id=34' > /tmp/genres34.json
# -> 10_final_closure/itunes_music_genre_tree_id_name.json
```

**Не изменялось:** `/root/LMG-VK`, код приложения, оригинальные артефакты под `/srv/research/apple-music-ios26/**`
(кроме `deepseek_analysis/10_final_closure/**`).
