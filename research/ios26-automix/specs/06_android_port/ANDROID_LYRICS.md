# ANDROID_LYRICS — TTML-парсер, тайминг, Compose-рендерер (audited)

Subagent #7. RESEARCH/SPEC ONLY. Источник: `03_lyrics/*` + `07_audit/*`, sample TTML в
`/root/LMG-VK/*.ttml` (THIRD_PARTY канал доставки, структура EXACT), prior art —
`/root/LMG-VK/0001-Implement-Apple-Music-6.5.2-lyrics-behavior.patch` и
`APPLE-LYRICS-IMPLEMENTATION-PLAN.md` (источник — Apple Music **Android 6.5.2**, для iOS 26 →
`[UNVERIFIED]`). iOS-рендерер/парсер находится в отсутствующем `MusicCoreUI` (EXACT-отрицание).

В приложении уже есть база: `com.lmg.vk.engine.lyrics.apple` — `AppleTtmlParser`,
`AppleLyricsModels` (`AppleLyricsDocument/Section/Line/Piece`, `AppleTimingType`,
`ApplePieceRole`, `AppleLyricsAgent`), `AppleTtmlClient`, `AppleTtmlCache`, `AppleLyricsProjector`,
`AppleLyricsConfig`; UI — `com.lmg.vk.ui.lyrics.*`. Дизайн ниже наращивает эти типы, не ломая их.

---

## 0. Аудиторские ограничения

- `ttml` в iOS-коде corpus — 0 вхождений (D20/`RETRACTED` R5): нативного парсера нет; модель строится
  по sample-файлам, а не по декомпилу.
- `..._SYLLABLE.ttml` **байт-идентичен** PLAIN (md5 `bea7d8695c4a7e9411b4f2ab68a33ef7`, 36825 B;
  D21/R6) — валидных вариантов два: syllable и official-word.
- `itunes:timing="Word"` присутствует и в syllable, и в word sample — атрибут НЕ различает
  гранулярность; определять по структуре span.
- `x-bg` — это `ttm:role="x-bg"` на `<span>` с вложенными span, не отдельный тег; все 10 x-bg CHAI
  лежат внутри своей `<p>` (проверено программно).
- 38% viewport, 1.12 scale, 4 sec timeout, smoothstep, 4 pt glow — **в iOS corpus отсутствуют**
  (LYRICS_ANIMATIONS §4, LYRICS_SCROLLING §RETRACTED). В дизайне не используются как Apple-константы.
- Все prior-art числа (`wordOffset=-100ms`, anchor 28%, pause 4000 ms, 7000 ms interlude,
  scale 1.14, stagger, easing) — `[UNVERIFIED]` для iOS, только фича-флаги.

---

## 1. Компонент: TTML-парсер

- iOS behavior: нативный парсер отсутствует (в `MusicCoreUI`); контракт известен по sample.
- source evidence: `TTML_PARSER.md` §1–§10 (EXACT по 4 sample: `bea7d869…`, `376f3b12…`,
  `b01e221f…`; `<span` counts 644/364/468); `LYRICS_ARCHITECTURE.md` §5 (EXACT-отрицание).
- Android implementation: развить существующий `AppleTtmlParser` (DOM + `isNamespaceAware=true`):
  namespace-aware разбор `itunes:`/`ttm:`/default-ns `iTunesMetadata`; оба формата времени
  (`SS.mmm`, `M:SS.mmm`, поддержать `H:MM:SS.mmm`); `begin`/`end` обязательны у `div`/`p`/`span`;
  `dur` только у `body`; пробелы — отдельные piece `" "`; `ttm:agent` (v1) наследуется;
  `itunes:key` (L1..Ln) как стабильный id строки; `itunes:songPart` (Intro/Verse/Pre-Chorus/Chorus/
  Bridge/Outro); `songwriters`; `<translations/>` толерантно.
- difficulty: low (модель и парсер уже есть; доработка — гранулярность/x-bg/агенты).
- expected fidelity: 95%.
- performance risks: один большой XML (36 KB); парсить на `Dispatchers.IO`, кэшировать модель.
- fallback: legacy `LyricsParser`/LRC.

### 1.1. Компонент: определение гранулярности WORD vs SYLLABLE

- iOS behavior: `itunes:timing` не помогает (все `Word`).
- source evidence: `WORD_TIMING.md` §1, `TTML_PARSER.md` §10: PLAIN (syllable) median span 0.190 s,
  644 span; OFFICIAL (word) median 0.348 s, 364 span.
- Android implementation: `TimingTypeClassifier.classify(pieces): WORD | LINE | NONE` по метрикам:
  доля соседних span с `end == next.begin`, медианная длительность, средняя длина текста.
  Результат хранить в `AppleLyricsDocument.timingType` (не переопределять из атрибута).
- difficulty: low.
- expected fidelity: 90%.
- performance risks: нет.
- fallback: LINE (пословная заливка отключается).

---

## 2. Компонент: модель данных

- iOS behavior: неизвестна (корпус отрицательный).
- source evidence: sample TTML (EXACT); prior-art модель `TtmlDocument` (patch, `[UNVERIFIED]`
  как Apple-эталон).
- Android implementation (существующая + расширение):
  `AppleLyricsDocument(durationMs = body@dur, lang, timingType, agents, songwriters, sections)`;
  `AppleLyricsSection(beginMs, endMs, songPart, agentId, lines)`;
  `AppleLyricsLine(key, beginMs, endMs, agentId, main, background, translations, pronunciations)`;
  `AppleLyricPiece(text, beginMs, endMs, role, isWhitespace, children)`.
  Инварианты: `p.begin/end` — из атрибутов `p`, не пересчитывать по span (удержания/паузы);
  `div.begin/end` — из `div`; x-bg outer → `role=BACKGROUND`, inner → `children`.
- difficulty: low.
- expected fidelity: 100% (структурно).
- performance risks: immutable-модель держит все piece — нормально (тысячи).
- fallback: line-level модель без children.

---

## 3. Компонент: Timeline / timing engine

- iOS behavior: тайминг-модель и маппинг playback time → строка/слово — NOT FOUND (в corpus нет кода).
  Известно: `setSelected(_:animator:)` вызывается при смене активной строки (AX override),
  `SyncedLyricsViewController.scrollView`, `lineViews`, `containerHeight` (AX validation).
- source evidence: `WORD_TIMING.md` §1/§4 (правила выведены из sample), `LYRICS_ARCHITECTURE.md`
  §4.2 (EXACT поля), `LYRICS_ANIMATIONS.md` §2.1 (EXACT метод).
- Android implementation: `LyricsTimeline` (immutable, предвычисленные события
  `LineStart/LineEnd/PieceStart/PieceEnd/BgPiece*`), `evaluateAt(positionMs): LyricsUiState`
  без аллокаций на кадр; активная строка `p.begin <= t < p.end`; при разрывах — interlude;
  активный piece `begin <= t < end`; `progress = (t-begin)/(end-begin)` clamp 0..1,
  `coerceAtLeast(1ms)`; seek → аналитический пересчёт + epoch++ (без «догоняющих» анимаций).
  Tie-break на границах: End-события обрабатывать раньше Start (из patch, `[UNVERIFIED]`).
- difficulty: medium.
- expected fidelity: 90% (правила; точный алгоритм Apple — нет).
- performance risks: per-frame состояние; держать вне Compose-рекомпозиции (derivedStateOf/Flow).
- fallback: line-level `evaluateAt`.

### 3.1. Компонент: playback clock

- iOS behavior: источник времени (AVPlayer time vs hostTime), lead-in, округления, seek — NOT FOUND.
- source evidence: `WORD_TIMING.md` §2 (STRONG_INFERENCE: отдельный сетевой канал, не Now Playing).
- Android implementation: единственный источник — `ExoPlayer.currentPosition`/media clock;
  кадровый тик `withFrameNanos`; без собственного накопления времени; опциональный
  `wordLeadMs` (default 0; prior art −100 ms за флагом).
- difficulty: low.
- expected fidelity: 90% (архитектура), точные смещения — 0%.
- performance risks: частые вызовы `currentPosition`; кэшировать на кадр.
- fallback: `currentPosition` раз в 1–2 кадра.

---

## 4. Компонент: Compose-рендерер строки

- iOS behavior: `SyncedLyricsLineView: UIControl` с `isSelected`, `containerView`, контент:
  `MusicTextContentView(label: UILabel)`, `MusicSBS_TextContentView(text/attributedText)`,
  `MusicInstrumentalContentView` (AX-контракт EXACT). Karaoke-механика — NOT FOUND.
- source evidence: `LYRICS_ARCHITECTURE.md` §4.2, `LYRICS_ANIMATIONS.md` §2.4 (EXACT).
- Android implementation: `LyricsLine(pieces, state)` — `Row` из `PieceText`; активный piece рисуется
  двумя слоями (`drawText` базовым цветом + акцентным по `clipRect(progress * width)`) через два
  `TextLayoutResult`; измерение — `TextMeasurer`; не менять layout width на кадр (scale — transform).
  `MusicInstrumentalContentView` → `InstrumentalLine` (см. §7).
- difficulty: medium.
- expected fidelity: 70% (структура контента EXACT, визуал — нет эталона).
- performance risks: две раскладки на piece — для длинных строк оптимизировать (общий базовый
  `TextLayoutResult`, поверх — clip).
- fallback: одна раскладка + градиентная заливка кистью.

---

## 5. Компонент: x-bg (background vocals)

- iOS behavior: unknown; in corpus только семантика sample.
- source evidence: `TTML_PARSER.md` §7 (EXACT): outer `ttm:role="x-bg"` с begin/end, вложенные span;
  текст в круглых скобках; 10 outer в CHAI, все внутри своей `p`; outer max 8.040 s, inner 0.146…1.745 s.
- Android implementation: `LyricsLine.background: List<BackgroundGroup(outer: AppleLyricPiece,
  children: List<AppleLyricPiece>)>`; рендер второй строкой (меньший размер/прозрачность —
  конкретные значения NOT FOUND, брать из дизайна, не выдавать за Apple); своя караоке-заливка по
  children; клэмпить к границам `p` (sample-инвариант, но не полагаться).
- difficulty: medium.
- expected fidelity: 75%.
- performance risks: +1 текстовая раскладка на строку с x-bg.
- fallback: показывать x-bg как обычные piece без суб-тайминга.

---

## 6. Компонент: autoscroll

- iOS behavior: `SyncedLyricsViewController.scrollView: UIScrollView`, `lineViews:
  [SyncedLyricsLineView]` (AX validation EXACT); строки — плоские subview в одном scrollView.
  Anchor/паузы/кривые — NOT FOUND.
- source evidence: `LYRICS_SCROLLING.md` §2 (EXACT), §4 (prior art `[UNVERIFIED]`).
- Android implementation: `LazyColumn` + `listState`; `animateScrollToItem(index, offset =
  -anchorPx)`; anchor `lyrics.scrollAnchorPct` (default 0.28 — `[UNVERIFIED]`, prior art),
  top/bottom padding 0.28/0.40 (флаги); pause autoscroll на `userScrollPauseMs` (default 4000,
  флаг) после drag; seek → snap без анимации; длительность перехода — производная от
  `prevEnd → next.begin`, но конкретные значения NOT FOUND.
- difficulty: medium.
- expected fidelity: 50% (структура EXACT, параметры — эвристика).
- performance risks: `animateScrollToItem` и частые recomposition; не скроллить при каждом кадре.
- fallback: мгновенный `scrollToItem` + anchor 28% (флаг OFF).

---

## 7. Компонент: instrumental break

- iOS behavior: класс `MusicInstrumentalContentView` + AX-ключ `instrumental.break` (EXACT);
  визуальная механика — NOT FOUND.
- source evidence: `INSTRUMENTAL_BREAK.md` §2 (EXACT), §3 (prior art `[UNVERIFIED]`).
- Android implementation: `isInterlude`-строка (или пустой piece) с `InstrumentalLine`
  (3 точки, размер/анимация — Android-эвристика: 10dp/6dp, expand 750 ms / collapse 250 ms,
  за флагом); показ при разрыве ≥ `interludeThresholdMs` (default 7000 — `[UNVERIFIED]`);
  accessibility label из ресурса `instrumental.break`.
- difficulty: low.
- expected fidelity: 40% (маркер EXACT, вид — нет).
- performance risks: infinite transition на видимой строке — гасить вне окна.
- fallback: пустая строка.

---

## 8. Компонент: selection / emphasis / reveal-анимации

- iOS behavior:
  - `SyncedLyricsLineView.setSelected(_:animator:)` существует (EXACT selector/signature `v`),
    AX-категория лишь публикует announcement и не меняет анимацию (дизасм);
  - `MusicUI` generic reveal: `AnimatedTextListItemView` = SwiftUI
    `TimelineView<ExplicitTimelineSchedule<AnimationSchedule>>` + `TextRevealEffectRenderer:
    Animatable` (структура EXACT, параметры NOT FOUND); связь с lyrics — НЕ доказана;
  - числовых констант lyrics-анимаций в corpus нет.
- source evidence: `LYRICS_ANIMATIONS.md` §2.1–§2.5, `LYRICS_TRANSITIONS.md` §2 (EXACT/структура).
- Android implementation: `LyricsAnimationEngine` с per-frame значением (аналог TimelineView):
  `withFrameNanos` → `Animatable`-подобный параметр; отдельный канал `selection` на строку
  (`setSelected`-аналог) для accessibility announcement. Опциональные эффекты (scale 1.00→1.14,
  stagger, lift spring) — только за флагами и с пометкой `[UNVERIFIED]` (prior art, не Apple).
- difficulty: medium (каркас), high (фиделити).
- expected fidelity: 40–50%.
- performance risks: покадровая анимация текста — держать в `graphicsLayer`/draw-фазе, не в
  композиции; ограничить число анимируемых строк.
- fallback: без scale/emphasis — только karaoke-fill и selection-color.

---

## 9. Компонент: translations / pronunciations / duet / RTL

- iOS behavior: reporting-флаги `displayTranslationEnabled`, `displayTransliterationEnabled`,
  `userPreferenceSyllable` (EXACT accessors из `MPCReportingLyricsViewEvent`); содержимое в sample
  пусто; значения ролей `x-translation`/`x-roman` — `[UNVERIFIED]` (AMLL-документация).
- source evidence: `LYRICS_ARCHITECTURE.md` §3.4 (EXACT accessors), `TTML_PARSER.md` §7 NOT FOUND.
- Android implementation: модель уже содержит `translations`/`pronunciations`; UI показывает
  sub-line, только если непусто; `duet` — по числу агентов (в sample 1); RTL — из `xml:lang`.
- difficulty: low.
- expected fidelity: структура 100%, поведение `[UNVERIFIED]`.
- performance risks: нет.
- fallback: скрывать пустые каналы; выравнивание по умолчанию.

---

## 10. Тест-план lyrics

1. Parser golden: PLAIN и OFFICIAL → 56/56 `p` с идентичными begin/end/key/agent; span 644 vs 364;
   `body.dur` 182000 vs 170380 ms.
2. x-bg CHAI: 10 outer, каждое внутри своей `p`; outer 0.8…8.04 s; inner 0.146…1.745 s.
3. Время: `2:40.160 → 160160`, `1:00.980 → 60980`, `0.000 → 0`; оба формата в одном файле.
4. Пробелы: между span ровно один пробел; нет ведущих/хвостовых text-node.
5. `evaluateAt`: на границах begin/end state не мигает (End раньше Start); seek назад/вперёд
   не вызывает визуального прыжка.
6. Гранулярность: классификатор даёт SYLLABLE для PLAIN (median 0.190) и WORD для OFFICIAL (0.348).
7. Zero/negative duration: `coerceAtLeast(1ms)`.
8. UI: прокрутка при смене строки, пауза на drag, возврат к активной строке (флаги).

## NOT FOUND / OPEN

- Реализация `MusicCoreUI.SyncedLyricsViewController`/`SyncedLyricsLineView` и всего
  parser/timing/renderer кода (EXACT-отрицание).
- Тип `animator` в `setSelected(_:animator:)`, связь selection и scroll.
- Karaoke-заливка (gradient/clip/mask) — ни символов, ни шейдеров.
- Точные lead-in/offset/lookahead, алгоритм активной строки, hysteresis/debounce.
- Autoscroll: anchor ratio, insets, скорости, кривые, паузы, seek-поведение.
- Instrumental break: геометрия/анимация/порог (только класс-маркер + ключ).
- `translations`/`transliterations` содержимое и роли `x-translation`/`x-roman` для Apple-originals.
- Duet/multi-agent layout, RTL-правила, `ttm:name`.
- Сетевой endpoint Apple для TTML (доступный канал — THIRD_PARTY proxy).
- `itunes:timing="Line"/"None"` образцы, `ttp:*`, `xml:id` на `p`/`span`.
- Числовые константы любых lyrics-анимаций в iOS corpus.
