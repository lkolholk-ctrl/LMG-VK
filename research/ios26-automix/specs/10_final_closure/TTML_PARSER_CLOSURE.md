# TTML_PARSER_CLOSURE — MSVLyricsTTMLParser: реальный XML→model парсер Apple Music (iOS 26, 23A341)

**Дата:** 2026-09-14. **Роль:** Final Closure (TTML Parser Analyst).
**Статус документа:** closure для внешнего TTML-парсера; все утверждения с адресом/символом и статусом.
**Ограничения:** research only; `/root/LMG-VK` и код приложения не изменялись. Артефакты только в `10_final_closure/` + append в `indexes/KEY_SYMBOLS_04_lyrics.tsv`.

Статусы: `[EXACT]` — прямое подтверждение бинарём/дизасмом/псевдокодом; `[STRONG_INFERENCE]` — следует из нескольких независимых подтверждённых артефактов; `[INFERRED]`; `[NOT FOUND]`.

## Метод и провенанс (важно)

1. **MediaPlayer** карвлен напрямую из свежего subcache `.11` (VA `0x1a1e47000`, text size `0x4d2b00`): Mach-O header, 7 сегментов; в тексте/данных образа `MSVLyrics*` **нет** (поиск по всему карву). Использован только для проверки задания.
2. **MediaServices** в предоставленном наборе `appos/sys_dsc/...` отсутствует (его text-subcache — `.13`, а в наборе только `.03/.11/.33/.34/.70/.71/.75/.76`). Использована имеющаяся копия `.13` (`/tmp/opencode/msv/dyld_shared_cache_arm64e.13`, `dyld_v1`, mapping base `0x1a5374000`, MediaServices text `0x1abf6b000`); карв `MediaServices.macho` собран из неё + свежих data-subcache'ей набора (`.33.dylddata` и др.) — сегменты `__DATA_CONST/__AUTH_CONST/__AUTH/__DATA/__DATA_DIRTY` прочитаны без пропусков (проверено carve-скриптом). Для дальнейших задач стоит добавить `.13` в `sys_dsc`.
3. Символы (4474, из них 378 `MSVLyrics*`) — из локального символьного атласа DSC (`/tmp/opencode/dsc/symbols.bin`, base `0x1abf6b000`); имена/адреса методов, ivar-offset'ы (`_OBJC_IVAR_$_*`), `+[MSVLyricsSection _songPartForText:]` и т.п. — EXACT.
4. Поведение — из собственного дизасма `llvm-objdump-19` (arm64e) и Ghidra 12.1.3 pseudocode (`/tmp/opencode/msv/ghidra_out2`), с перепроверкой ключевых веток по дизасму; селекторы в карве MediaServices резолвятся однозначно (stubs `_objc_msgSend$...` + CFString-константы).
5. Сторона потребителя — MEE (14.6 MB, arm64e) и `MusicApplication.framework`/`Music.app`: точные import-ordinal'ы и selref'ы (разрешены декодированием chained-fixups и `__objc_selrefs`).

---

## 0. Главная поправка: парсер — не MediaPlayer, а MediaServices

**RETRACTED (в задании и в старых отчётах):** «`MSVLyricsTTMLParser` находится в `MediaPlayer.framework`».

**Новое evidence (EXACT):**
1. MEE (`MusicEngagementExtension`) импортирует классы `_OBJC_CLASS_$_MSVLyricsTTMLParser` (и Agent/Section/Transliteration) с **dylib ordinal 49**, который в списке `LC_LOAD_DYLIB` = `/System/Library/PrivateFrameworks/MediaServices.framework/MediaServices` (chained-fixups imports table MEE: entries 1725–1728).
2. `MusicApplication.framework/MusicApplication` и `Music.app/Music` импортируют те же классы с ordinal `0x1d` = `MediaServices.framework` (nlist N_UNDF + n_desc ordinal).
3. В карве `MediaPlayer.framework` (text VA `0x1a1e47000`, size `0x4d2b00`, subcache `.11`) **0 вхождений строк `MSVLyrics*`**; `MediaPlayer` не содержит ни одной из этих классификаций.
4. Строки/классы найдены в MediaServices (`dyld_shared_cache_arm64e.13`, text VA `0x1abf6b000`, size `0x844e0`): `MSVLyricsTTMLParser` class_ro `0x1f23a81d0`, имя `0x1abfd99cc` (локальная копия) и в shared `__OBJC_RO` `0x1fded80ac` (subcache `.34.dyldreadonly`).
5. Вся реализация методов — в MediaServices `__TEXT` (`0x1abf8xxxx`), 39 ObjC-методов (symbol atlas, вместе с property-аксессорами и `.cxx_destruct`; см. §2). Atлас локальных символов MediaServices (`symbols.bin` по base `0x1abf6b000`) даёт 4474 символа, 378 из них `MSVLyrics*`.

Замечание: `MSV` = MediaServices (не MediaPlayer). Задание опиралось на `MediaPlayer.framework`, но evidence однозначен.

```
Source file: appos/sys_dsc/.../dyld_shared_cache_arm64e.13 (копия: /tmp/opencode/msv/dyld_shared_cache_arm64e.13)
Framework/Binary: MediaServices.framework/MediaServices (DSC image; carved: /tmp/opencode/msv/MediaServices.macho)
Function/Symbol: MSVLyricsTTMLParser (class_ro 0x1f23a81d0)
Evidence type: chained-fixups imports (MEE ordinal 49), nlist ordinals (Music.app), symbol atlas, CFStrings, disassembly
Status: EXACT
```

---

## 1. Модель данных парсера (инвентарь, EXACT)

Все ivar-offset'ы получены из секции `_OBJC_IVAR_$_*` (linker symbols в `__DATA` MediaServices, 8-байтовые значения, offset = low 32 bits). Иерархия — из `class_t`/`class_ro_t` (superclass rebase-fixup резолвится по дереву).

| Класс | Наследник | instanceStart/size | ivar'ы (offset) |
|---|---|---|---|
| `MSVLyricsXMLElement` | NSObject (external root) | 8 / 32 | `_elementName`+0x08, `_identifier`+0x10, `_mutableText`+0x18 |
| `MSVLyricsElement` | MSVLyricsXMLElement | 32 / 80 | `_isBackgroundVocal`+0x20, `_type`+0x28, `_startTime`+0x30, `_endTime`+0x38, `_agent`+0x40, `_role`+0x48 |
| `MSVLyricsTextElement` | MSVLyricsElement | 80 / 96 | `_keepParentheses`+0x50, `_lyricsText`+0x58 |
| `MSVLyricsLine` | MSVLyricsTextElement | 96 / 168 | `_instrumentalBreak`+0x60, `_lineIndex`+0x68, `_originalLineIndex`+0x70, `_parentSection`+0x78, `_nextLine`+0x80, `_words`+0x88, `_translationKey`+0x90, `_backgroundVocals`+0x98, `_hasBackgroundVocal`+0x61, `_primaryVocalText`+0xa0 |
| `MSVLyricsWord` | MSVLyricsTextElement | 96 / 152 | `_parentLine`+0x60, `_nextWord`+0x68, `_parentWord`+0x70, `_subwords`+0x78, `_wordIndex`+0x80, `_characterRange`+0x88 |
| `MSVLyricsTranslationText` | MSVLyricsLine | 168 / 176 | `_lyricsLineKey`+0xa8 |
| `MSVLyricsTransliterationText` | MSVLyricsLine | 168 / 176 | `_lyricsLineKey`+0xa8 |
| `MSVLyricsSection` | MSVLyricsElement | 80 / 104 | `_songPart`+0x50, `_songPartText`+0x58, `_lines`+0x60 |
| `MSVLyricsSongInfo` | NSObject | 8 / 120 | `_type`+0x08, `_songDuration`+0x10, `_leadingSilence`+0x18, `_songwriters`+0x20, `_lyricGenId`+0x28, `_language`+0x30, `_availableTranslations`+0x38, `_translations`+0x40, `_transliterations`+0x48, `_lyricsLines`+0x50, `_agents`+0x58, `_audioAttributes`+0x60, `_lyricsSections`+0x68, `_translationsMap`+0x70 |
| `MSVLyricsSongWriter` | MSVLyricsXMLElement | 32 / 48 | `_name`+0x20, `_artistID`+0x28 |
| `MSVLyricsAgent` | MSVLyricsXMLElement | 32 / 56 | `_type`+0x20, `_name`+0x28, `_artistID`+0x30 |
| `MSVLyricsTranslation` | MSVLyricsXMLElement | 32 / 72 | `_automaticallyCreated`+0x20, `_type`+0x28, `_language`+0x30, `_typeText`+0x38, `_linesMap`+0x40 |
| `MSVLyricsTransliteration` | MSVLyricsXMLElement | 32 / 56 | `_automaticallyCreated`+0x20, `_language`+0x28, `_linesMap`+0x30 |
| `MSVLyricsAudioAttributes` | NSObject | 8 / 32 | `_spatialRole`+0x08, `_lyricsOffset`+0x10, `_role`+0x18 |

**Inline-структуры парсера `MSVLyricsTTMLParser`** (class_ro `0x1f23a81d0`, isize 0x70 = 112 B; 40 методов, 13 ivars):

| ivar | offset | смысл |
|---|---|---|
| `_linesAreSortedByStartTime` | +0x08 (BOOL) | флаг монотонности startTime |
| `_ttmlData` | +0x10 | NSData (в `initWithTTMLData:` не сохраняется) |
| `_inputStream` | +0x18 | NSInputStream |
| `_parseQueue` | +0x20 | serial queue |
| `_lyricsInfo` | +0x28 | MSVLyricsSongInfo |
| `_parserError` | +0x30 | NSError от NSXMLParser |
| `_elementStack` | +0x38 | NSMutableArray (cap 10) |
| `_lyricLines` | +0x40 | NSMutableArray (cap 100) |
| `_currentTextElement` | +0x48 | текущий TextElement |
| `_translations` | +0x50 | NSMutableArray |
| `_transliterations` | +0x58 | NSMutableArray |
| `_currentStartTime` | +0x60 (double) | последний startTime (для проверки порядка) |
| `_agents` | +0x68 | NSMutableArray (cap 3) |

**Enum'ы (EXACT):**
- `MSVLyricsSongInfo.type` (`+[MSVLyricsSongInfo _descriptionForLyricsInfoType:]` таб. `0x1e7b7a210`): `0 = Not Timed`, `1 = Timed Lines`, `2 = Timed Words`; парсер ставит его из `itunes:timing` на `<tt>` (см. §3): нет атрибута → 0; case-insensitive `"line"` → 1; case-insensitive `"word"` → 2; любое иное значение → 0.
- `MSVLyricsElement.type` (`_descriptionForElementType:` таб. `0x1e7b7a228`): `0=Section, 1=Line, 2=Word, 3=Translated Line, 4=Transliterated Line`. Задаётся в инициализаторах: `MSVLyricsSection init` @`0x1abf8a148` → 0; `MSVLyricsLine init` @`0x1abf89c54` → 1; `MSVLyricsWord init` @`0x1abf89efc` → 2; `MSVLyricsTranslationText init` @`0x1abf8a9cc` → 3; `MSVLyricsTransliterationText init` @`0x1abf8accc` → 4.
- `MSVLyricsSection.songPart` (`+[MSVLyricsSection _songPartForText:]` @`0x1abf8a1bc`, case-insensitive): verse→1, chorus→2, pre-chorus→3, bridge→4, intro→5, outro→6, refrain→7, instrumental→8, иначе 0. Парсер задаёт только `songPartText`; `-setSongPartText:` @`0x1abf89ffc` сам вызывает `_songPartForText:` и сохраняет enum в `+0x50` (`-songPart` @`0x1abf89fec` — plain ivar getter).
- `MSVLyricsTranslation._translationTypeForText:` @`0x1abf8a884`: nil → 0; `"subtitle"` → 1; `"replacement"` → 2 (оба case-insensitive), иначе 0.

---

## 2. Entry points и жизненный цикл (EXACT)

| Метод | VA | Поведение |
|---|---|---|
| `-[MSVLyricsTTMLParser initWithTTMLData:]` | `0x1abf8e2bc` | `stream = [[NSInputStream alloc] initWithData:data]` [STRONG_INFERENCE — класс из ref `_DAT_1e6b61f18`, селектор `initWithData:`]; `return [self initWithTTMLStream:stream]` (stub `objc_msgSend$initWithData:` + `$initWithTTMLStream:`; `objc_alloc`) |
| `-[MSVLyricsTTMLParser initWithTTMLStream:]` | `0x1abf8e1b8` | `[super init]`; `_inputStream = stream` (+0x18); `_parseQueue = dispatch_queue_create("com.apple.MediaServices.MSVLyricsTTMLParser", serial)` (+0x20); `_elementStack = [NSMutableArray arrayWithCapacity:10]`; `_lyricLines = arrayWithCapacity:100`; `_agents = arrayWithCapacity:3` |
| `-[MSVLyricsTTMLParser parseWithError:]` | `0x1abf8e004` | `_linesAreSortedByStartTime = YES`; `_currentStartTime = 0`; `[elementStack removeAllObjects]`; `[lyricLines removeAllObjects]`; `NSXMLParser *p = [[NSXMLParser alloc] initWithStream:_inputStream]`; `p.delegate = self`; `p.shouldProcessNamespaces = YES`; `[p parse]`; `if (_translations) _lyricsInfo.translations = _translations`; `if (_transliterations) _lyricsInfo.transliterations = _transliterations`; `if (error && _parserError) *error = _parserError`; `return _lyricsInfo` |
| `-[MSVLyricsTTMLParser parseWithCompletion:]` | `0x1abf8deac` | `dispatch_async(_parseQueue, ^{ NSError *e; MSVLyricsSongInfo *info = [self parseWithError:&e]; completion(info, e); })` (block `0x1abf8df60`) |
| `-[MSVLyricsTTMLParser parser:parseErrorOccurred:]` | `0x1abf8bb58` | логирует ошибку (os_log error) и делает `self.parserError = error` |
| `-[MSVLyricsTTMLParser parser:foundCharacters:]` | `0x1abf8bc28` | добавляет строку к `_currentTextElement.mutableText`; если элемент — Word (type 2), то также к `parentLine.mutableText` и `parentWord.mutableText`; если `_currentTextElement == nil` — к `elementStack.lastObject.mutableText` |
| `-[MSVLyricsTTMLParser parser:didStartElement:...]` | `0x1abf8cb14` | см. §3 |
| `-[MSVLyricsTTMLParser parser:didEndElement:...]` | `0x1abf8bd8c` | см. §3 |

Нет метода `-parse` у парсера (в отличие от NSXMLParser). В `parseWithError:` создаётся **новый** NSXMLParser на каждый вызов; namespace-префиксы обрабатываются как часть `elementName` (см. §3).

```
raw value: 0x8400102 (конфиг os_log), 0x8000202/0x8400202 — уровни логирования;
decoded: os_log subsystem "com.apple.amp.MediaServices", category "LyricsTTMLParser" (CF 0x1abfd22de/0x1abfd236f)
Status: EXACT
```

---

## 3. XML → модель: элементы и атрибуты (EXACT)

Общий хвост обработки `didStartElement` (LAB `0x1abf8d1f0`): если `_lyricsInfo == nil` → os_log error `"PARSE ERROR: Top-level element must be <tt> for TTML documents"` (продолжает разбор); если объект создан — устанавливает `startTime = msvl_timeValue(attrs["begin"])` и `endTime = msvl_timeValue(attrs["end"])`; **проверка порядка**: если `endTime != 0` и `startTime < _currentStartTime` → `_linesAreSortedByStartTime = NO`, затем `_currentStartTime = startTime`; если объект — `MSVLyricsTextElement`, ставит `_currentTextElement`; `ttm:agent` ищется в `_agents` блоком `0x1abf8de68` (`[agent.identifier isEqualToString:value]`), при ненахождении — os_log `"No agent exists for identifier \"%@\" in element: %@"`; `ttm:role` → `role`, `isBackgroundVocal = [role == "x-bg"]`; `itunes:parenthesis` (только TextElement) → `keepParentheses = [value == "keep"]`; затем `elementName = name`, `identifier = attrs["xml:id"]`, push в `_elementStack`.

| Элемент | Действие при старте (`didStartElement`) |
|---|---|
| `tt` | `_lyricsInfo = [MSVLyricsSongInfo new]`; `lyricGenId = attrs["itunes:lyricGenId"]`; `language = attrs["xml:lang"]`; NSAssert non-nil `songwriters` (line 0xd9) и `lyricsSections` (0xda); `type` из `itunes:timing`: нет → 0; `"line"` → 1; `"word"` → 2; иное → 0 (все сравнения case-insensitive; `0x1abf8cb14`, LAB `0x1abf8d1e0`) |
| `body` | `songDuration = msvl_timeValue(attrs["dur"])`; если `dur` нет — warning `"Warning: Document body element must specify song duration"` |
| `div` | `MSVLyricsSection new` + `songPartText = attrs["itunes:songPart"]`; `setSongPartText:` @`0x1abf89ffc` сам вычисляет `songPart` (= `_songPartForText:`) и кладёт enum в ivar `+0x50` |
| `p` | `MSVLyricsLine new` + `translationKey = attrs["itunes:key"]` |
| `span` | `MSVLyricsWord new`; родитель (по `_parentTextElement`): type 1/3/4 → `parentLine`; type 2 → `parentWord` + `parentLine = parentWord.parentLine`; иначе warning `"Warning: <span> must be a descendent of <p> or <span>."` |
| `metadata` | ничего (контейнер) |
| `iTunesMetadata` | `leadingSilence = msvl_timeValue(attrs["leadingSilence"])` (если атрибут есть) |
| `songwriters` | warning, если родитель не `iTunesMetadata` |
| `songwriter` | warning, если родитель не `songwriters`; `MSVLyricsSongWriter new`; `artistID = attrs["artistId"]`; append в `_lyricsInfo.songwriters` |
| `audio` | warning, если родитель не `iTunesMetadata`; `MSVLyricsAudioAttributes new`; `lyricsOffset = msvl_timeValue(attrs["lyricOffset"])`; `role = attrs["role"]`; `spatialRole = [role == "spatial"]`; `_lyricsInfo.audioAttributes = obj` |
| `translations` | `_translations = [NSMutableArray new]` (warning, если родитель не `iTunesMetadata`) |
| `translation` | warning, если родитель не `translations`; `xml:lang` обязателен (warning `"<translation> element must specify a language with <xml:lang> attribute"`); `MSVLyricsTranslation new`; `language`; `automaticallyCreated = [attrs["automaticallyCreated"] == "true"]`; `typeText = attrs["type"]`; `linesMap = [NSMutableDictionary new]`; append в `_translations` |
| `transliterations` | `_transliterations = [NSMutableArray new]` (warning про родителей) |
| `transliteration` | аналог `translation` (без `type`/`typeText`): `MSVLyricsTransliteration`, `language`, `automaticallyCreated`, `linesMap`, append |
| `text` | warning, если родитель не `translation`/`transliteration`; `MSVLyricsTranslationText` или `MSVLyricsTransliterationText`; `lyricsLineKey = attrs["for"]` |
| `agent` | warning, если родитель не `metadata`; `MSVLyricsAgent new`; `type = attrs["type"]`; `artistID = attrs["itunes:artistId"]`; append в `_agents` |
| `name` (`ttm:name`) | warning, если родитель не `agent`; `MSVLyricsXMLElement new` + `mutableText = [NSMutableString string]` |
| прочие/неизвестные элементы | игнорируются (в т.ч. любые элементы без обработки) |

Проверка имени элемента — `-[NSString(MSVLyricsTTMLParser) msvl_isElementType:]` @`0x1abf8e324` = `caseInsensitiveCompare: == NSOrderedSame` (без namespace-логики; `shouldProcessNamespaces=1` включён, но сравнение идёт по полному `elementName`).

**Атрибуты (полный список, EXACT):** `begin`, `end`, `dur`, `itunes:songPart`, `itunes:key`, `itunes:timing`, `itunes:lyricGenId`, `itunes:artistId`, `itunes:parenthesis`, `xml:id`, `xml:lang`, `ttm:agent`, `ttm:role`, `type` (agent и translation), `artistId` (songwriter), `leadingSilence`, `lyricOffset`, `role` (audio), `automaticallyCreated`, `for` (text).

**Обработка `didEndElement`** (`0x1abf8bd8c`): сверяет `elementName` верхушки стека с пришедшим (иначе os_log error `"Mismatched element names! start:%@, end:%@"`), pop; далее:
- `</body>`: `uVar5 = [self _translatedLyrics:_lyricLines forLanguage:[[NSLocale preferredLanguages] firstObject]]`; если `_linesAreSortedByStartTime == NO` → os_log `"Lyrics lines are out of order: they should be ordered by start time"` и `lyricsInfo.lyricsLines = uVar5` (без сортировки); иначе `lyricsInfo.lyricsLines = uVar5` через свойство `lyricsLinesSortedByStartTime` (сортирует и переиндексирует); `lyricsInfo.agents = [_agents copy]`.
- `</tt>`: ничего.
- `</songwriter>`: `name` из накопленного `mutableText` (см. §3.1).
- `</ttm:name>`: NSAssert, что родитель — последний добавленный agent; `agent.name = mutableText`; `mutableText = nil`.
- `</p>`/`</text>` (type 1/3/4): `lineIndex = _lyricLines.count`; `originalLineIndex = lineIndex`; `prevLine.nextLine = line`; для type 3: если родитель — `translation` и является `MSVLyricsTranslation`, то `translation.linesMap[lyricsLineKey] = line` (иначе os_log `"Invalid translation text element at line %ld: %@"`); type 4 — аналогично для transliteration; type 1 — `[_lyricLines addObject:line]`; затем background-vocal-хвост (см. §6); если родитель — `MSVLyricsSection`, то `section.lines += line; line.parentSection = section`.
- `</span>` (type 2): `_updateWords:withWord:parentText:` (см. §5); background-vocal-хвост; в конце, если элемент был `_currentTextElement`, финализируется `lyricsText = [[NSString alloc] initWithString:mutableText]`, `mutableText = nil`, `_currentTextElement = parentTextElement`.
- `</div>` (type 0): append в `lyricsInfo.lyricsSections` (NSAssert `-[MSVLyricsSection lines] should never be nil`).

### 3.1. `songwriter` name
`</songwriter>` (ветка `else` в `didEndElement`, `0x1abf8bf9c`): берётся сам element `MSVLyricsSongWriter` (созданный на `<songwriter>` и лежащий на стеке; `objc_retain` x0 → x24), `writer.name = [writer mutableText]`, затем `writer.mutableText = nil`. То есть `<songwriter artistId="...">Имя</songwriter>` → `MSVLyricsSongWriter{artistID, name}`.

---

## 4. Тайминг (EXACT)

**`-[NSString(MSVLyricsTTMLParser) msvl_timeValue]` @`0x1abf8e344`:**
1. `components = [self componentsSeparatedByString:@":"]`
2. `seconds = [components.lastObject doubleValue]`
3. если `components.count >= 2`: `seconds += [components[count-2] integerValue] * 60.0` (`0x404e000000000000` = 60.0, `fmadd`)
4. если `components.count >= 3`: os_log error `"Warning: time format should specify [minutes:]seconds only; other components are ignored: %@"` — часы **не** учитываются (только warning, значение не меняется)
5. возвращает double секунд.

**Форматы:** `SS.mmm`, `MM:SS.mmm` (оба в одном файле); `HH:MM:SS.mmm` формально не падает, но часы игнорируются с warning. Привязка: `MSVLyricsSongInfo.songDuration` = `msvl_timeValue(body.dur)`; `leadingSilence` = `msvl_timeValue(iTunesMetadata.leadingSilence)`; `audioAttributes.lyricsOffset` = `msvl_timeValue(audio.lyricOffset)`; `begin`/`end` — на каждом созданном XMLElement/Element (кроме translation/transliteration — они выходят до общего хвоста).

**Порядок строк:** флаг `_linesAreSortedByStartTime` устанавливается в `YES` в начале `parseWithError:` и сбрасывается в `NO`, если у элемента с ненулевым `endTime` его `startTime` меньше `_currentStartTime`. При `</body>`:
- если строки уже отсортированы: `setLyricsLinesSortedByStartTime:` @`0x1abf88a0c` → `_sortLyricsLinesByStartTime:` @`0x1abf888a4` (`sortedArrayUsingComparator:` по `startTime` возрастанию, блок `0x1abf888b4`), записывает результат в `_lyricsLines` (+0x50) и пробегает массив: `line.lineIndex = i`, `line.nextLine = lines[i+1]` (nil для последней) @`0x1abf88940..0x1abf889f8`;
- если порядок был нарушен: строки сохраняются как есть (без сортировки), с os_log.

`_currentStartTime = 0` в начале, поэтому первая строка с `startTime < 0` нереальна; сравнение строго `<`.

**Lookup API модели** (для плееров, не для LyricsX):
- `-[MSVLyricsSongInfo lyricsLineStartingBeforeTimeOffset:]` @`0x1abf88c74` — бинарный поиск по `_lyricsLines` (только при `type != 0`): находит line с `startTime <= t <= next.startTime`.
- `-[MSVLyricsSongInfo lyricsWordsAtTimeOffset:errorMargin:]` @`0x1abf88dc4`, `lyricsLinesAtTimeOffset:errorMargin:` @`0x1abf88f6c` — через `+[MSVLyricsSongInfo _elementsInArray:atTimeOffset:errorMargin:]` @`0x1abf89220`.
- `-[MSVLyricsLine containsTimeOffset:withErrorMargin:]` @`0x1abf89b60` — попадание `t` в `[startTime-margin, endTime+margin]`.

---

## 5. Слова и слоги (EXACT)

- **Слог = вложенный `<span>`**: `_updateWords:withWord:parentText:` @`0x1abf8b4b8` вызывается для каждого span: `words = parent.words + word` (или `[NSArray arrayWithObject:]`); `word.wordIndex = words.count-1`; если `wordIndex > 0` → `words[wordIndex-1].nextWord = word`; `word.characterRange = NSMakeRange(parentText.length - word.mutableText.length, word.mutableText.length)` — диапазон в *родительском* тексте (`parentLine.mutableText` для верхнего span, `parentWord.mutableText` для вложенного слога).
- Верхний span (`parentLine == _parentTextElement`) → `line.words = updateWords(line.words, word, line.mutableText)`; вложенный (`parentWord != nil`) → `parentWord.subwords = updateWords(parentWord.subwords, word, parentWord.mutableText)`; если `parentWord.isBackgroundVocal`, то `word.isBackgroundVocal = YES`.
- `foundCharacters:` для Word дублирует символы в `parentLine.mutableText` и `parentWord.mutableText` (обе строки аккумулируются одновременно).
- `primaryVocalText` (для строки с bg-вокалом): копия `line.mutableText`, из неё удаляется `backgroundVocals.characterRange`, затем `replaceOccurrencesOfString:@"()" withString:@""`, trim по `whitespaceCharacterSet`; результат — `NSString`, кладётся в `line.primaryVocalText` @`0x1abf8bd8c` (ветка 288–317).

**`-[MSVLyricsTTMLParser _parentTextElement]` @`0x1abf8ba18`** — первый `MSVLyricsTextElement` при обходе `_elementStack` в обратном порядке (ближайший объемлющий line/word/translation-text).

---

## 6. Background vocals (`ttm:role="x-bg"`) (EXACT)

- `isBackgroundVocal = [attrs["ttm:role"] isEqualToString:@"x-bg"]` (общий хвост).
- `keepParentheses = [attrs["itunes:parenthesis"] isEqualToString:@"keep"]` (только TextElement).
- При `</span>` (`_stripParenthesesFromBackgroundVocalWord:backgroundVocalText:` @`0x1abf8af0c`):
  - NSAssert `"MSVLyricsWord must be background vocal"` (line 0x2d9);
  - копия текста строки, `msvl_trimTrailingWhitespace` @`0x1abf8e48c`; если оканчивается `")"` — удаляются первый и последний символы, `word.lyricsText` = строка без скобок, `word.characterRange.location += (range в исходном тексте)`, первый subword теряет ведущий `"("`, последний — хвостовой `")"`, subwords пересобираются; NSCAssert `"relativeRange.location != NSNotFound"` (line 0x2e9).
- При добавлении bg-слова к строке: если `line.hasBackgroundVocal` уже `YES` → os_log `"Warning: Ignoring additional background vocals for lyrics line \"%@\""`; иначе `line.hasBackgroundVocal = YES`, `line.backgroundVocals = word` (только для слов с subwords или при parent type == 3).
- Текст bg берётся из самого XML как есть (скобки — литеральные символы в Apple TTML); при отсутствии `itunes:parenthesis="keep"` скобки снимаются.

---

## 7. Переводы и транслитерации (EXACT)

- `<translation xml:lang=... automaticallyCreated="true" type="subtitle|replacement">` → `MSVLyricsTranslation{language, automaticallyCreated, typeText, linesMap}`; `<text for="Lx">translated text</text>` → `MSVLyricsTranslationText{lyricsLineKey}` с накопленным `lyricsText`; при `</text>` объект кладётся в `translation.linesMap[for]`. Аналогично transliteration/TransliterationText.
- `-[MSVLyricsSongInfo setTranslations:]` @`0x1abf88fe0`: сохраняет массив и строит `translationsMap` = `{language: linesMap}` **только для translation.type == 0** (т.е. `subtitle`/`replacement`-переводы в map не попадают). `-setTransliterations:` @`0x1abf8881c` — обычный synthesized setter (отдельного map нет).
- `-[MSVLyricsSongInfo translatedTextForLyricsLine:language:]` @`0x1abf88b54`: `translationsMap[language][line.translationKey].lyricsText` (nil-safe).
- `_translatedLyrics:forLanguage:` @`0x1abf8b60c` (вызывается при `</body>` c `preferredLanguages.firstObject`): **работает только для языков с префиксом `zh-Hant` или `zh-Hans`** (`hasPrefix:` CF `0x1f23a0918`/`0x1f23a0938`); выбирает translation с `type == 0` и языком с тем же префиксом; для каждой исходной строки берёт `linesMap[translationKey]`, копирует в неё `startTime/endTime/agent/translationKey` и возвращает массив с переводами вместо оригиналов; строки без перевода остаются как есть. Для остальных языков возвращает `nil`, и `</body>` сохраняет оригинальные строки.

---

## 8. Malformed TTML / ошибки (EXACT)

| Ситуация | Поведение | Где |
|---|---|---|
| XML parse error (NSXMLParser) | `parserError = error`; `parseWithError:` возвращает **частично** собранный `lyricsInfo` (без исключения); `*error` заполняется, если задан out-param | `0x1abf8bb58`, `0x1abf8e004` |
| Верхнеуровневый элемент не `<tt>` | os_log error `"PARSE ERROR: Top-level element must be <tt> for TTML documents"`, разбор продолжается (модель может содержать generic XMLElement'ы) | `didStartElement` LAB `0x1abf8d1f0` |
| `<songwriters>`/`<lyricsSections>` nil на `<tt>` | `NSAssert` через `+[NSAssertionHandler currentHandler] handleFailureInMethod:...` (line 0xd9/0xda; strings `"songwriters array must not be nil"`, `"lyricsSections array must not be nil"`) — **NSAssert-путь присутствует в бинаре**, т.е. собран без `NS_BLOCK_ASSERTIONS` | `didStartElement` |
| `<translation>` без `_translations` / `<transliteration>` без `_transliterations` | NSAssert: `"<translation> end element expects translations to be set by start of <translations> element"` (0x118) / `"<transliteration> ..."` (0x12d) | `didEndElement` |
| `</text>` не того типа на верхушке | NSAssert `"At end of <text> element, an MSVLyricsTranslationText object should be top of stack"` (0x1e3) / Transliteration-вариант (0x1f1) | `didEndElement` |
| `-[MSVLyricsSection lines]` nil | NSAssert `"-[MSVLyricsSection lines] should never be nil"` (0x20e) | `didEndElement` |
| `</ttm:name>` не в последнем agent | NSAssert `"Unexpected agent element"` (0x246) | `didEndElement` |
| bg-word не background vocal | NSAssert `"MSVLyricsWord must be background vocal"` (0x2d9) | `_stripParentheses...` |
| misplaced известные элементы (`<audio>` вне `<iTunesMetadata>`, `<songwriter>` вне `<songwriters>`, `<agent>` вне `<metadata>`, `<translation>` вне `<translations>`, `<name>` вне `<agent>`, `<span>` вне `<p>/<span>`, отсутствие `xml:lang`, отсутствие `dur`) | только os_log warning, разбор продолжается | `didStartElement`/`didEndElement` |
| Неизвестные элементы/атрибуты | игнорируются | все три delegate-метода |

`MSVLyricsTTMLParserErrorDomain` (`0x1f23a03b8`) объявлен, но **не используется** ни в одном декомпилированном методе MediaServices [NOT FOUND usage].

---

## 9. Instrumental markers — в TTML-парсере ОТСУТСТВУЮТ (EXACT)

- `MSVLyricsLine.isInstrumentalBreak` @`0x1abf89a5c` — тривиальный getter ivar `+0x60`; setter @`0x1abf89a4c` — synthesized. **Ни парсер, ни MEE, ни MusicApplication/Music не вызывают** `setInstrumentalBreak:`/`isInstrumentalBreak` (проверено: 0 вхождений в строках MEE/MusicApplication; в MediaServices pseudocode — только сам getter/setter). В TTML нет элемента/атрибута для интерлюдии.
- `LyricsX.Lyrics.InstrumentalLine` (Swift-структура с `lineIndex/startTime/endTime`) существует в MEE, но создаётся не из `MSVLyricsLine` (в LyricsX-конверсии не читается ни один instrumental-селектор).
- Порог 7000 ms — по-прежнему `[NOT FOUND]` для iOS (подтверждено повторно).

---

## 10. Цепочка TTML XML → MSV model → LyricsX (evidence per hop)

**Объектная цепочка (поэтапно):**

```
XML <tt itunes:timing> <body dur> <iTunesMetadata leadingSilence>
    └─ <div itunes:songPart ttm:agent>            -> MSVLyricsSection{songPartText, songPart(=derived), lines[]}   (didStart 0x1abf8cb14)
       └─ <p begin end itunes:key ttm:agent>      -> MSVLyricsLine{translationKey, startTime, endTime, agent,
                                                       lineIndex, originalLineIndex, nextLine}            (didStart+общий хвост; didEnd 0x1abf8bd8c)
          ├─ <span begin end>                     -> MSVLyricsWord{startTime, endTime, wordIndex, nextWord,
          │                                            characterRange, parentLine, parentWord}            (didStart; didEnd -> _updateWords 0x1abf8b4b8)
          │  └─ <span begin end> (вложенный)      -> MSVLyricsWord.subwords[] (слог)                     (didEnd -> _updateWords)
          ├─ <span ttm:role="x-bg" itunes:parenthesis> -> word.isBackgroundVocal/keepParentheses;
          │                                            line.hasBackgroundVocal/backgroundVocals;
          │                                            line.primaryVocalText                              (0x1abf8af0c, 0x1abf8bd8c)
          └─ (опционально) <translations>/<translation>/<text for="Lx">
                                                  -> MSVLyricsTranslation.linesMap[Lx] = TranslationText
    └─ <metadata> <ttm:agent xml:id type itunes:artistId> <ttm:name> -> MSVLyricsAgent{type,name,artistID} (0x1abf8cb14/0x1abf8bd8c)
    └─ <songwriters> <songwriter artistId>Текст</songwriter>          -> MSVLyricsSongWriter{artistID,name} (0x1abf8cb14/0x1abf8bf9c)
    └─ <audio lyricOffset role>                                      -> MSVLyricsAudioAttributes{role,spatialRole,lyricsOffset}
```

Таймстемпы каждого уровня: `startTime/endTime` — `msvl_timeValue` (`0x1abf8e344`) из `begin`/`end` (абсолютные секунды); `characterRange` слова — позиция в тексте родителя (`0x1abf8b4b8`); `lineIndex/nextLine` — порядок/цепочка (`0x1abf88940`); `songDuration` — `body.dur`; `leadingSilence` — `iTunesMetadata.leadingSilence`. Все поля попадают в `MSVLyricsSongInfo` (модель §1), который возвращается `parseWithError:`.

| # | Hop | Evidence | Status |
|---|---|---|---|
| 1 | TTML bytes → `NSInputStream` | `initWithTTMLData:` @`0x1abf8e2bc` → `initWithData:` + `initWithTTMLStream:`; `initWithTTMLStream:` @`0x1abf8e1b8` | EXACT |
| 2 | stream → NSXMLParser | `parseWithError:` @`0x1abf8e004`: `initWithStream:`, `setDelegate:self`, `setShouldProcessNamespaces:1`, `parse` | EXACT |
| 3 | XML → model | delegate-методы @`0x1abf8cb14`/`0x1abf8bd8c`/`0x1abf8bc28`; правила §3–§7 | EXACT |
| 4 | model root | `MSVLyricsSongInfo` (поля §1); возврат из `parseWithError:` | EXACT |
| 5 | parser → consumer callback | блок `parseWithCompletion:` @`0x1abf8df60` → `completion(lyricsInfo, error)`; MEE-замыкание с типом `(MSVLyricsSongInfo?, Error?)` @`0x1002b6e0c` (`…U_ySo09MSVLyricsJ4InfoCSg_sAK_pSgtYbcfU_`) | EXACT |
| 6 | `MSVLyricsSongInfo` → `LyricsX.Lyrics` | `Lyrics.init(identifier:songInfo:)` @`0x100408fac`; читает селекторы: `leadingSilence`, `lyricsLines`, `lyricsSections`, `audioAttributes`, `isSpatialRole`, `lyricsOffset`, `songwriters`, `translations`, `transliterations`, `language`, `lines`, `linesMap`, `isAutomaticallyCreated` (selrefs `0x1005b43d0`, `0x1005b4408`, `0x1005b4418`, `0x1005b42f0`, `0x1005b43b8`, `0x1005b4410`, `0x1005b4540`, `0x1005b45b0`, `0x1005b45b8`, `0x1005b43c8`, `0x1005b43f8`, `0x1005b4400`) | EXACT (selector refs; call-site mapping ещё и в APPOS_LYRICSX §10) |
| 7 | `[MSVLyricsLine]` → `[TextLine]` | `processLines` @`0x100418b6c`; читает `agent`, `translationKey`, `words`, `language`, `backgroundVocals`, `lineIndex`, `startTime`, `endTime` | EXACT |
| 8 | слова/слоги | `Lyrics.msvWordsToLyricsWords(msvWords:text:lineText:lineDuration:shouldProcessEmphasis:usesSpacesAsWordDelimiter:)` @`0x100417644` (+ closure `0x10040f364`: `lyricsText`, `startTime`, `endTime`, substringWithRange) → `Lyrics.words(for:language:)` @`0x100418330` (`words`, `primaryVocalText`, `lyricsText`, `startTime`, `endTime`) | EXACT |
| 9 | bg-вокал | `LyricsX.TextLine.BackgroundVocals.init(backgroundVocals:language:)` @`0x1004186d4` (`subwords`, `startTime`, `endTime`, `lyricsText`); `LineTranslationMetadata.init` @`0x10040e7c4` (`hasBackgroundVocal`, `backgroundVocals`, `lyricsText`) | EXACT |
| 10 | vendor-подтверждение | `MusicApplication.framework` и `Music.app/Music` импортируют те же MSVLyrics-классы из `MediaServices.framework` (nlist ordinal 0x1d); у MEE — chained-fixups imports 1725–1728 (ordinal 49) | EXACT |

Итог: TTML XML → (NSXMLParser delegate) → `MSVLyricsSongInfo{lyricsSections/lyricsLines/agents/translations/transliterations/leadingSilence/songDuration/type/audioAttributes}` → (parseWithCompletion) → LyricsX `Lyrics.init(identifier:songInfo:)` + `processLines` → `LyricsX.Lyrics/TextLine/Word`. Статус всей цепочки: **EXACT на уровне классов/полей/селекторов**, точный порядок чтения внутри Swift-функций не декомпилировался (см. §12).

---

## 11. Ключевые адреса/символы (provenance)

| Symbol | VA | Source |
|---|---|---|
| `-[MSVLyricsTTMLParser initWithTTMLData:]` | `0x1abf8e2bc` | MediaServices `.13` |
| `-[MSVLyricsTTMLParser initWithTTMLStream:]` | `0x1abf8e1b8` | " |
| `-[MSVLyricsTTMLParser parseWithError:]` | `0x1abf8e004` | " |
| `-[MSVLyricsTTMLParser parseWithCompletion:]` | `0x1abf8deac` (+block `0x1abf8df60`) | " |
| `-[MSVLyricsTTMLParser parser:didStartElement:...]` | `0x1abf8cb14` | " |
| `-[MSVLyricsTTMLParser parser:didEndElement:...]` | `0x1abf8bd8c` | " |
| `-[MSVLyricsTTMLParser parser:foundCharacters:]` | `0x1abf8bc28` | " |
| `-[MSVLyricsTTMLParser parser:parseErrorOccurred:]` | `0x1abf8bb58` | " |
| `-[MSVLyricsTTMLParser _updateWords:withWord:parentText:]` | `0x1abf8b4b8` | " |
| `-[MSVLyricsTTMLParser _stripParenthesesFromBackgroundVocalWord:backgroundVocalText:]` | `0x1abf8af0c` | " |
| `-[MSVLyricsTTMLParser _translatedLyrics:forLanguage:]` | `0x1abf8b60c` | " |
| `-[MSVLyricsTTMLParser _parentTextElement]` | `0x1abf8ba18` | " |
| `-[NSString(MSVLyricsTTMLParser) msvl_timeValue]` | `0x1abf8e344` | " |
| `-[NSString(MSVLyricsTTMLParser) msvl_isElementType:]` | `0x1abf8e324` | " |
| `-[NSMutableString(MSVLyricsTTMLParser) msvl_trimTrailingWhitespace]` | `0x1abf8e48c` | " |
| `+[MSVLyricsSection _songPartForText:]` | `0x1abf8a1bc` | " |
| `+[MSVLyricsTranslation _translationTypeForText:]` | `0x1abf8a884` | " |
| `-[MSVLyricsSongInfo setTranslations:]` | `0x1abf88fe0` | " |
| `-[MSVLyricsSongInfo setLyricsLinesSortedByStartTime:]` | `0x1abf88a0c` | " |
| `-[MSVLyricsSongInfo _sortLyricsLinesByStartTime:]` | `0x1abf888a4` | " |
| `-[MSVLyricsSongInfo translatedTextForLyricsLine:language:]` | `0x1abf88b54` | " |
| `-[MSVLyricsSongInfo lyricsLineStartingBeforeTimeOffset:]` | `0x1abf88c74` | " |
| `-[MSVLyricsLine isInstrumentalBreak]` | `0x1abf89a5c` | " |
| `-[MSVLyricsLine primaryVocalText]` | `0x1abf89a6c` | " |
| class_ro `MSVLyricsTTMLParser` | `0x1f23a81d0` | `__AUTH_CONST` |
| `_OBJC_CLASS_$_MSVLyricsTTMLParser` import (MEE) | index 1727, ordinal 49 → MediaServices | chained fixups MEE |
| MEE `Lyrics.init(identifier:songInfo:)` | `0x100408fac` | MEE (llvm-nm) |
| MEE `processLines` (Tf4nnnnnnnnd_n) | `0x100418b6c` | MEE |
| MEE `msvWordsToLyricsWords` | `0x100417644` | MEE |
| MEE `Lyrics.words(for:language:)` | `0x100418330` | MEE |
| MEE `TextLine.BackgroundVocals.init` | `0x1004186d4` | MEE |
| MEE completion `(MSVLyricsSongInfo?, Error?)` closure | `0x1002b6e0c` | MEE |

**Метод-инвентарь `MSVLyricsTTMLParser` (39 символов):** property-аксессоры (get/set для `inputStream`, `parseQueue`, `lyricsInfo`, `parserError`, `elementStack`, `lyricLines`, `currentTextElement`, `translations`, `transliterations`, `currentStartTime`, `linesAreSortedByStartTime`, `ttmlData`, `agents`) + содержательные методы из таблиц §2–§7. Полный список — в `mediaservices_syms.tsv` (378 `MSVLyrics*` символов), ключевые вынесены в `KEY_SYMBOLS_04_lyrics.tsv`.

---

## 12. NOT FOUND / открытые вопросы

1. **Точное место вызова `initWithTTMLData:`/`parseWithCompletion:` в MEE**: класс-импорт и completion-тип `(MSVLyricsSongInfo?, Error?)` подтверждены; сам call-site в статике не локализован (в MEE нет selref на эти селекторы — вероятно, selector приходит из другого модуля/линковки; полный MEE-decompile не выполнялся из бюджета).
2. **`ttmlData`** ivar: `initWithTTMLData:` не сохраняет `NSData` (создаёт stream) — свойство `ttmlData` фактически не используется парсером.
3. **`MSVLyricsSongInfo.availableTranslations`**: setter'а нет; getter @`0x1abf88838` — synthesized (кто заполняет, не найдено; вероятно, из другого источника, не парсер).
4. **`MSVLyricsLine.instrumentalBreak`**: поле не заполняется парсером и не читается LyricsX/Music.app (см. §9). Где оно заполняется (server payload?) — вне доступных бинарей.
5. **`MSVLyricsTTMLParserErrorDomain`**: объявлен, использований нет.
6. **`x-translation`/`x-roman`/`ttm:role` на `p`/`div`** — парсер поддерживает только `x-bg` на span; иных ролей нет.
7. Точная семантика `type` в `<translation type="...">` при рендере (subtitle/replacement) — вне парсера (используется LyricsX/UI).
8. Порог инструментального брейка в iOS — не найден повторно (нет ни в парсере, ни в потребителях).

---

## 13. RETRACTED / CORRECTED

1. **Было (задание):** «`MSVLyricsTTMLParser` — MediaPlayer.framework». **Стало:** MediaServices.framework (import ordinals MEE 49 / Music.app 0x1d; 0 вхождений `MSVLyrics*` в карве MediaPlayer). См. §0.
2. **Было (`APPOS_LYRICSX` §10, §12):** «точные правила x-bg/duet/emphasis на стороне MSV не видны». **Стало:** правила восстановлены (§3, §6): x-bg = `ttm:role="x-bg"`, `itunes:parenthesis="keep"`, снятие скобок, bg-слова и `primaryVocalText`; duet = несколько `ttm:agent` + `ttm:agent="vN"` (lookup по `xml:id`); emphasis в TTML-парсере не обрабатывается (есть только у рендерера).
3. **Было (`LYRICS_RENDERER_IMPLEMENTATION_SPEC` §5.55 / STATE_RECONCILIATION):** «interlude/instrumental приходит из `MSVLyricsSongInfo`/`InstrumentalLine`». **Уточнение:** TTML-парсер не создаёт и не помечает instrumental-строки; `instrumentalBreak` не заполняется и не читается (см. §9). Формулировку «приходит из MSVLyricsSongInfo» следует читать как «модель имеет поле, но в доступных бинарях оно не используется».
4. **Было (`TASK` premise):** «MediaPlayer image ... MSVLyricsTTMLParser inventory» — инвентарь построен по MediaServices (VA-базы MediaPlayer оставлены только как проверка отсутствия).
5. **Было (`TTML_PARSER.md`):** «TTML-парсер в iOS corpus отсутствует (0 вхождений ttml в коде)». **Уточнение:** в старом corpus его не было; в DSC MediaServices он найден и декомпилирован здесь (класс `MSVLyricsTTMLParser`, 39 методов).

---

*Конец отчёта. Все сырые артефакты: `/srv/research/apple-music-ios26/appos/sys_dsc/System/Library/Caches/com.apple.dyld/dyld_shared_cache_arm64e.{11,33.dylddata,34.dyldreadonly,…}` (read-only), карв `/tmp/opencode/msv/MediaServices.macho` (sha отдельных сегментов совпадает с DSC), символьный атлас `/tmp/opencode/dsc/symbols.bin`.*
