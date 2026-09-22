# LYRICS_RENDERER_IMPLEMENTATION_SPEC — clean-room спека рендерера лирики Apple Music (iOS 26, 23A341)

**Дата:** 2026-09-13. **Роль:** Final Closure (task: Lyrics Renderer Spec Author).
**Статус документа:** implementation-ready для переноса; все значения с провенансом, спорные вынесены отдельно.
**Ограничения:** research only; `/root/LMG-VK` и код приложения не изменялись.

Статусы: `[EXACT]` (сырые байты/псевдокод/дизасм), `[STRONG_INFERENCE]`, `[INFERRED]`, `[NOT FOUND]`, `DISPUTED` (значение есть в одном артефакте, но аудит его не подтвердил — не hardcode без перепроверки).

---

## 0. Источники и метод

| Артефакт | Значение |
|---|---|
| Primary binary | `appos/extracted/System/Library/ExtensionKit/Extensions/MusicEngagementExtension.appex/MusicEngagementExtension`, Mach-O arm64e, 14 629 280 B, sha256 `6f05b36cdf173a8a9ae0fefbc6f1f8f20b945571a93fcb2c3239d689532c3041` — модули `LyricsX` + `MusicCoreUI` (рендерер) |
| Secondary (кросс-проверка) | `appos/extracted/private/var/staged_system_apps/Music.app/Music` (20 278 608 B, sha256 `3f8162e7b5ec8ed42ae933accae336bcdc68001515eb70f70ccec8cd9701458b`, com.apple.Music 4025.110.6) — статические модули `LyricsX`/`MusicCoreUI` + app-level переходы |
| Декомпиляция | Ghidra 12.1.3 (MEE: рабочая выгрузка `/tmp/opencode/appos_work/decompiled_*.txt`; Music.app: `09_appos/musicapp_ghidra/pseudocode/`) |
| Аудит | `07_audit/RED_TEAM_AUDIT.md` (methodology); `09_appos/APPOS_RED_TEAM_AUDIT.md` §3.4 (строки 46–65), M2 (строка 230); `10_final_closure/STATE_RECONCILIATION.md` |
| Отчёты-основания | `09_appos/APPOS_LYRICSX.md`, `09_appos/APPOS_MUSICCOREUI.md`, `09_appos/APPOS_MUSIC_APP_DECOMP.md`, `03_lyrics/*` |

Правило проверки: числовые литералы перечитаны из псевдокода Ghidra (`fmov`/double-возвраты) и/или из `__TEXT`-пула; привязка «имя поля ↔ offset» — по `__swift5_fieldmd` (парсер APPOS) и, где доступно, по `vpfi`-инициализаторам полей (Music.app).

> **Ограничение точности:** Ghidra C не является исходником. Там, где Ghidra теряет selector/return-value, в спеке это явно отмечено (`[INFERRED]`). Ни одно `[INFERRED]` не выдаётся за `[EXACT]`.

---

## 1. Архитектура рендерера (что реализует clean-room порт)

| Класс | Родитель | Роль | Провенанс |
|---|---|---|---|
| `LyricsX.SyncedLyricsViewController` | UIViewController | корневой контроллер: `UIScrollView`, `lineViews`, `selectedLineViews`, `containerHeight`, `blurredLineViews`, аниматоры, инсеты | `[EXACT]` символы MEE + AX-контракт Music.app |
| `LyricsX.SyncedLyricsManager` (+ `Configuration`, `LineAction`) | — | тайминг-стейт: `elapsedTimeProvider`, `selectedLines`, `nextLine`, `moveToLine`/`selectLine` | `[EXACT]` MEE |
| `LyricsX.SyncedLyricsLineView` | UIControl | одна строка: `setSelected(_:animator:)`, blur, transform, content-view | `[EXACT]` `0x100452b64` (MEE), Music.app `0x100c76cc0` |
| `SBS_TextContentView.TextView` | UIView | основной текстовый путь (TextKit/CTLine, Word/Syllable/Glyph), karaoke-прогресс | `[EXACT]` символы + `0x100447974` |
| `LineProgressGradientView` | UIView, `layerClass = CAGradientLayer` | караоке-заливка строки/фона | `[EXACT]` `init` `0x10047f7c0` |
| `InstrumentalContentView` (+ `Specs`) | UIView | индикатор инструментального брейка (3 точки) | `[EXACT]` `0x10044a2bc`/`0x10044a628`/`0x10044aef0`/`0x10044bdcc` |
| `LyricsX.Lyrics` (+ `TextLine`, `Word`, `Syllable`, `InstrumentalLine`) | — | модель данных из `MSVLyricsSongInfo`; TTML-парсинг внешний (`MSVLyricsTTMLParser`, MediaPlayer) | `[EXACT]` `APPOS_LYRICSX.md` §2/§10 |

Внешние входы рендерера: `elapsedTime` (closure `elapsedTimeProvider`), `isPlayingSpatial` (+`spatialOffset`), `lyrics: Lyrics`, `specs: Specs`, `mode` (например 2 = режим, влияющий на seek). TTML парсится не здесь.

---

## 2. Реализационная таблица (implementation-ready)

### 2.1. Selected line / тайминг-менеджер

| # | Что | Значение | Источник (binary → symbol @VA) | Статус |
|---|---|---|---|---|
| 1 | `maxSelectedLines` | `2` | MEE `SyncedLyricsManager.Configuration`-поле; `Specs.init` specialization `0x100460b1c` (uStack_288 = 2), field offset `+0xc0`; условие `selectedLines.count < config+0x38` в `SyncedLyricsManager.update` `0x10045b18c` | EXACT (значение) / STRONG_INFERENCE (VC→Configuration) |
| 2 | `maxEndTimeOffset` | `0.5` s | `Specs.init` `0x100460b1c` (uStack_290 = `0x3fe0000000000000`), offset `+0xb8` | EXACT |
| 3 | `lineDelay` | `0.05` s | `Specs.init` `0x100460b1c` (uStack_298 = `0x3fa999999999999a`), offset `+0xb0`; Music.app `vpfi` `0x100c2b4e0` (return `0x3fa999999999999a`) | EXACT |
| 4 | `animationHeadstart` | `0.1` s | `Specs.init` `0x100460b1c` (uStack_188 = `0x3fb999999999999a`), offset `+0x1c0`; Music.app `vpfi` `0x100c2bf9c` | EXACT |
| 5 | `finishLineAnimationDuration` / `lineFinishProgressAnimationDuration` | `0.25` s | `Specs.init` (uStack_c0 = `0x3fd0000000000000`), offset `+0x288`; `Line.finishGradient` `0x10043f5b4` → `UIView.animate(duration: specs+0x288, options: 0x30001)` | EXACT |
| 6 | hysteresis смены timing provider | `1.0` s (повторный tap), `0.5` s (|Δ| провайдера) | `SyncedLyricsViewController.timingProvider.didset` `0x100463a64`; debug-строки `[SyncedLyricsDebug] Timing provider update: less than 1 second since last tap, ignoring.` / `difference is smaller than half a second, ignoring.` | EXACT |
| 7 | `LineAction` из `calculateNextLineAction(elapsedTime:)` | raw `0/1/2` (имена кейсов не восстановлены) | MEE `0x10045b98c` (вложена в `update` `0x10045b18c`): `2` — next-line + `count < maxSelectedLines` + каст `InstrumentalLine` не прошёл + условия по start/elapsed; `1` — уход из окна; `0` — none | EXACT (значения) / INFERRED (семантика имён) |
| 8 | порядок работы `update()` | читает `elapsedTimeProvider` → `elapsed - spatialOffset` (если `isPlayingSpatial`) → `updateNextLineIfNeeded()` → каст selectedLines.last в `InstrumentalLine` → `calculateNextLineAction` → `moveToLine`/`selectLine`/смена `timingProvider` | `0x10045b18c`, `0x10045c0f0`, `0x10045bd6c`, `0x10045bebc` | EXACT (структура) |
| 9 | стаггер появления строк | `lineDelay * (index - 1)` между `startAnimationAfterDelay` соседних строк | MEE `animate` `0x100473200` (f_animate: `specs+0xb0 * (i-1)` → `startAnimationAfterDelay:`) | STRONG_INFERENCE |

Провенанс (пример, обязательный формат):
```
raw value: 0x3fa999999999999a
decoded value: 0.05
instruction/function context: return-константа property-initializer'а `Specs.lineDelay` @0x100c2b4e0 (Music.app) / store в `Specs.init` @0x100460b1c (MEE)
how used: задержка каскада анимации строк (`startAnimationAfterDelay`, множитель индекса), НЕ межстрочный gap TTML
Status: EXACT
```

### 2.2. Appearance: аниматоры, transform'ы, blur

| # | Что | Значение | Источник (symbol @VA) | Статус |
|---|---|---|---|---|
| 10 | `Specs.opacityAnimator()` | `UIViewPropertyAnimator(duration 0.12, cp1 (0.33, 0.0), cp2 (0.2, 0.1))` | MEE `0x10045df1c`; Music.app `0x100c81a70` (raw: `0x3fbeb851eb851eb8`, `0x3fd51eb851eb851f`, 0, `0x3fc999999999999a`, `0x3fb999999999999a`) | EXACT |
| 11 | scroll/line-change аниматор | `UIViewPropertyAnimator(duration 0.28, cp1 (0.17, 0.0), cp2 (0.83, 1.0))` | MEE `animate` `0x100473200` (создание ~`0x1004739e0`; dVar42=0.17, dVar45=0.83, duration `0x3fd1eb851eb851ec`); audit row 51 | EXACT |
| 12 | `emphasizingScaleRange` | `1.0 ... 1.14` (lerp: `lower + factor*(upper-lower)`) | `Specs.init` `0x100460b1c` (uStack_250/uStack_248 = `0x3ff0000000000000`/`0x3ff23d70a3d70a3d`), offset `+0xf8..+0x100`; применение: `Word.animateGlow` (`0xf8`/`0x100` в `0x100444500`) | EXACT |
| 13 | `deselectedTransform` (линия) | `CGAffineTransformMakeScale(0.98, 0.98)` | `Specs.init` `0x100460b1c` (scale `0x3fef5c28f5c28f5c`), offset `+0x190`; Music.app `vpfi` `0x100c2bf90` | EXACT |
| 14 | `backgroundVocalsDeselectedTransform` | `CGAffineTransformMakeScale(0.9, 0.9)` | `Specs.init` `0x100460b1c` (scale `0x3feccccccccccccd`), offset `+0x80`; Music.app `vpfi` `0x100c2b4d4` | EXACT |
| 15 | selected line transform | identity `(1,0,0,1,0,0)` | `SyncedLyricsLineView.setSelected(_:animator:)` `0x100452b64` (при `animator == nil` — немедленно; при animator — в `addAnimations`) | EXACT |
| 16 | highlight-кривые | on: spring `m 1.0 / k 322 / c 24`, fade duration `0.2`, delay `0`; off: `m 2.0 / k 300 / c 50`, duration `0.3`, delay `0.1` | MEE `Specs.animateWithHighlightAnimationCurves` `0x10045df7c`; Music.app/MusicApplication `0x100c81ad0`/`0x93fd78` (кросс-подтверждение) | EXACT |
| 17 | прочие springs | `liftSpringTimingParameters` m1/k14/c7; `tapSpringTimingParameters` m2/k260/c50; `backgroundVocalsSpring(showing:true)` m1/k30/c9; `(showing:false)` dampingRatio 1.0 response 0.2; `growSyllableTimingParameters(response:)` dampingRatio 1.0 | MEE `0x10045ddf0`, `0x1004602ac`, `0x10045de8c`, `0x10045de3c`; Music.app §3.4 | EXACT (числа) / STRONG_INFERENCE (порядок mass/stiffness/damping для m/k/c) |
| 18 | `syllableBySyllableLineChangeSpringTimingParameters(gap:)` | `t = clamp((min(gap,0.75) - 0.2)/0.55, 0..1)`, `gap < 0.2 → t = 0`; `dampingRatio = (1-t)*0.12 + 0.78`; `response = t*0.27 + 0.48` | MEE `0x100460188`; `lineChangeSpringParameters` `0x100471838` (формула при `lyrics.type == .static` и `gap != nil`) | EXACT |
| 19 | blur строк | радиус `min(distance, 4.0)`; выделенная/граничные → `setBlurRadius(0)`; отдельная ветка фиксированный `3.0`; флаг `specs.lineBlurEnabled` (`+0x291`); аниматор 0.12 cubic как (10) | MEE `updateBlur` `0x100471a80` (`4.0` = `0x4010000000000000`, `min`), `setBlurRadius` `0x100453010` (animator 0.12 + `CABasicAnimation`), `applyBlurRadius` `0x100472cb0`; `3.0` = `0x4008000000000000` | EXACT (значения); distance здесь = порядковое удаление от выбранной строки (индекс), `[INFERRED]` |
| 20 | blur-фильтры CALayer | `filters.gaussianBlur.inputRadius`, `filters.colorBrightness.inputAmount` | строки MEE (`strings_all` 0x49fde0/0x4a0b80) + `setBlurRadius` `0x100453010` | EXACT |

### 2.3. Scroll targeting (только доказанное)

| # | Что | Значение / формула | Источник | Статус |
|---|---|---|---|---|
| 21 | `contentOffset(for rect:)` — ветвь с payload `selectedLinePosition` | `offset.y = rect.maxY - (containerHeight - rect.height)*0.5 - payloadValue`; для bit0 payload — подстановка `scrollView.frame.height`; второй вычет — `minY` payload-rect (или 0) | MEE `0x1004711ec` (псевдокод: два `CGRectGetHeight`, `CGRectGetMinY`, `(param_1 - (dVar9 - dVar8)*0.5) - dVar11`) | STRONG_INFERENCE (структура EXACT, семантика enum-кейсов INFERRED) |
| 22 | `contentOffset(for:)` — ветвь без payload | `offset.y = rect.minY - scrollView.contentInset.top` | MEE `0x1004711ec` (иначе: `CGRectGetMinY` − `contentInset`) | EXACT |
| 23 | insets | `staticTopContentInset = 22.0`, `staticBottomContentInset = 30.0` | `Specs.init` `0x100460b1c` (`0x4036000000000000`/`0x403e000000000000`), offsets `+0x38`/`+0x40`; `updateInsets()` `0x10046ad9c` | EXACT |
| 24 | `containerHeight` | runtime-поле VC (fallback `view.frame.height`), используется в contentOffset/blur | `0x10046628`-кластер (vg/vs/vM); AX-контракт Music.app (`containerHeight`) | EXACT (поле), значение — runtime |
| 25 | viewport anchors (28% / 38%) | **NOT FOUND** — в iOS-коде отсутствуют; 0.28 — это duration аниматора (см. #11), 38% не встречается | `03_lyrics/LYRICS_SCROLLING.md` §4/§NOT FOUND; audit | NOT FOUND |
| 26 | `USER_SCROLL_PAUSE_MS` (пауза autoscroll после drag) | **NOT FOUND в iOS** (`4000` — пользовательский patch Android) | `03_lyrics/LYRICS_SCROLLING.md` | NOT FOUND |
| 27 | видимость строк / выборка для blur | строки-`UIControl` одного `UIScrollView`; `lineViews(in:startingAt:)` `0x100470da0`, `visibleLineViews` `0x100470b50`, `frame(for:selected:previousViewFrame:)` `0x10046bdac`, `layoutLines()` `0x10046b37c` | MEE | EXACT (символы/структура) |
| 28 | seek-ветвь (mode == 2, строка ещё не выбрана) | `UIViewPropertyAnimator(duration: 0.25, curve: 3, animations:)` + опциональный `select(...)` внутри блока | `syncedLyrics(_:jumpTo:select:elapsedTime:)` `0x1004785d0` (`0x3fd0000000000000`, curve `3`) | EXACT |
| 29 | seek-ветвь (иначе) | пересчёт `contentOffset(for:)`, `CGRectIntersectsRect`, `frame(for:selected:)`, каскадный `layout` видимых строк, `animate(line:to:...)` со `adjustedContentOffset` | `0x1004785d0` → `0x10047a2a8` (специализация `animateTo`), `0x10046db34` | EXACT (структура) |

**Правило для порта:** autoscroll = «целевой `contentOffset` из `contentOffset(for frame:)` + аниматор 0.28 cubic»; доля экрана/якорь (28%/38%) не доказаны и в спеку не входят.

### 2.4. Word progress + karaoke gradient + слоги

| # | Что | Значение / формула | Источник | Статус |
|---|---|---|---|---|
| 30 | прогресс строки | `progress = elapsedTime - spatialOffset` (если `isPlayingSpatial`), затем `TextView.setProgress(_:animated:)`; для instrumental-line → `InstrumentalContentView.update(elapsedTime:)` | `updateProgress(for:animated:)` `0x100469f00`; `SyncedLyricsManager.spatialOffset` `0x10045a9c8` | EXACT |
| 31 | защита от отката прогресса (jitter) | обновление пропускается, если новый прогресс меньше старого и разница `< 0.5` (при откате ≥ 0.5 — применяется; вперёд — всегда) | `SBS_TextContentView.setProgress` `0x100447974` (`0.5` = `0x3fe0000000000000`) | EXACT |
| 32 | `LineProgressGradientView` colors | `colors = [color, color.withAlphaComponent(0)]` (2 стопа), `fillView.backgroundColor = color` | `updateColors` `0x10047fe94` | EXACT |
| 33 | feather | `specs.lineProgressionGradientFeather = 30.0` pt | `Specs.init` `0x100460b1c` (uStack_150 = `0x403e000000000000`), offset `+0x1f8`; `init(color:featherWidth:direction:frame:)` `0x10047f7c0` | EXACT |
| 34 | геометрия заливки (L2R/R2L) | направление: bit0==0 → L2R (`startX 0`, `endX 1`), bit0==1 → R2L (`startX 1`, `endX 0`); полоса feather `w = featherWidth`, `x = width - featherWidth` (bit0==0) / `x = 0` (bit0==1); `fillView.width` клампится снизу в 0; `horizontalPaddingView` — по `maxX` при direction==1 и `minX` при direction==0 | `updateDirection` `0x10047fb60`, `updateSubviewsFrames` `0x10047fc6c` | EXACT (ветвления/литералы), `[INFERRED]` — направление enum (0/1 ↔ L2R/R2L) и точный клэмп fill |
| 35 | распределение прогресса по словам/слогам | `Line.animate(progress:specs:)` идёт по `words`; для каждого — по `syllables`: state «до/после» по `word/syllable.startTime`; per-syllable `UIViewPropertyAnimator` c `liftSpringTimingParameters` (m1 k14 c7); завершение — `Word.animateGlow` | `Line.animate` `0x10043fab0`; `Syllable.animate` (внутри), `Word.animateGlow` `0x100444500` | EXACT (числа/вызовы) / INFERRED (полная диаграмма состояний) |
| 36 | glow слова | `factor` из `glowRange` (`0.0...0.4`): `f = lower + (upper-lower)*factor`; `CASpringAnimation keyPath shadowOpacity`, `from 0`, `to f`; spring `UISpringTimingParameters(dampingRatio: 1.0, response: min(duration, 3.0))` | `Word.WordView.glow` `0x10044626c`; `glowRadius = 5.0` (`+0x1d0`), `glowRange 0.0...0.4` (`+0x1d8`) в `Specs.init` | EXACT |
| 37 | emphasis scale (слова) | `scale = emphasizingScaleRange.lower + factor*(upper-lower)` = `1.0 + factor*0.14` | `Word.animateGlow` `0x100444500` (`0xf8`/`0x100`); поле `emphasizingScaleRange` (#12) | EXACT |
| 38 | стаггер glow | `step = min(duration/count * 0.4, 0.4)` s; фаза возврата — `duration / (count/2)` | `Word.animateGlow` `0x100444500` (0.4-кап, `dVar30/(count/2)`) | STRONG_INFERENCE (структура формулы в псевдокоде) |
| 39 | `syllableLift` | `2.0` pt | `Specs.init` `0x100460b1c` (uStack_d8 = `0x4000000000000000`), offset `+0x270` | EXACT (значение) / INFERRED (точка применения — lift при слоге) |
| 40 | `currentSyllable(for:)` / `currentWord(for:)` | возвращают пару `(current, next)` по времени `t ∈ [start, end)` | `Line.currentSyllable` `0x100440de0` (псевдокод подтверждён); `Line.currentWord` `0x100440be8` — символ EXACT, тело не выгружалось | EXACT (символы) / INFERRED (контракт) |
| 41 | финализация строки | `finishGradient(specs:)` проходит по словам строки, `UIView.animate(duration 0.25, delay 0, options 0x30001)`; после — завершение слогов | `syncedLyrics(_:finish:)` `0x100479164` + `Line.finishGradient` `0x10043f5b4` | EXACT (константы) / INFERRED (что именно анимируется в блоке) |
| 42 | слово/слог визуальные представления | создаются из `CTLine` (`wordVisualRepresentations`, `transliterationMetadata`), типы `Word/WordContainerView/WordView`, `Syllable/SyllableContainerView/SyllableView`, `Glyph/...` | `APPOS_LYRICSX.md` §9; символы MEE | EXACT (типы) |

### 2.5. Instrumental indicator

Все значения — `InstrumentalContentView.Specs` (значения) и функции `InstrumentalContentView` (формулы).

| # | Что | Значение / формула | Источник (symbol @VA) | Статус |
|---|---|---|---|---|
| 43 | количество точек | `3` | `Specs.init` (uStack_f8 = 3), offset `+0x250`; `createDots` `0x10044bdcc` читает `specs+0x250` | EXACT |
| 44 | длина точки (диаметр) | `12.0` pt, `cornerRadius = dotLength/2 = 6.0` | `Specs.init` (uStack_e8 = `0x4028000000000000`), offset `+0x260`; `createDots` `0x10044bdcc` (`specs+0x260 * 0.5`) | EXACT |
| 45 | margin между точками | `8.0` pt | `Specs.init` (uStack_e0 = `0x4020000000000000`), offset `+0x268`; `layoutSubviews` `0x10044a004` | EXACT |
| 46 | высота view | `40.0` pt | `Specs.init` (uStack_f0 = `0x4044000000000000`), offset `+0x258` | EXACT |
| 47 | начальная alpha точки | `0.1` | `Specs.dotInitialAlpha_WZ` `0x100449dc0` (загрузка `0.1` в `update` `0x10044aef0` при reset) | EXACT |
| 48 | transforms дыхания | `breathOut = scale(0.9)`, `breathIn = scale(1.2)`, `fadeOutZoomIn = scale(1.2)`, `fadeOutZoomOut = scale(0.2)` | `0x100449de8`, `0x100449e04`, `0x100449e24`, `0x100449e44` | EXACT |
| 49 | `reset()` формулы | `end = line.endTime - 1.8`; `dur = end - line.startTime`; `breathDuration = (dur / trunc(dur*0.25)) * 0.5`; `dotFadeInDuration = (end - (line.startTime + 1.0)) / dotCount`; counters=0; alpha=0; transform=identity | `reset` `0x10044a2bc` (`-1.8`, `0.25`, `0.5`, `1.0`, `scvtf dotCount`) | EXACT |
| 50 | layout точек | `total = dotLength*count + margin*(count-1)`; `alignment == 1` (центр) → `x = (width-total)*0.5`; `alignment == 2` (справа) → `x = width - total`; шаг `dotLength + margin`; `y = height*0.5 - dotLength*0.5` | `layoutSubviews` `0x10044a004` | EXACT |
| 51 | появление точек | `UIViewPropertyAnimator(duration 0.8)` на каждую точку, `startAnimation(afterDelay: index * 0.06)`; мгновенно «уже должны быть видны»: `k = min((long)((elapsed-(start+1.0))/dotFadeInDuration)+1, 3)` | `fadeIn` `0x10044a628` (`0.8` = `0x3fe999999999999a`, `0.06` = `0x3fae147ae147ae14`) | EXACT |
| 52 | дыхание | `UIView.animate(duration: breathDuration - 0.4, delay: 0.2, options: 0x20000, animations:)` | `fadeIn` `0x10044a628` (`-0.4`, `0.2` = `0x3fc999999999999a`) | EXACT (константы) / INFERRED (какой transform в блоке на конкретном шаге) |
| 53 | `update(elapsedTime:)` | `k = min((long)((elapsed-(start+1.0))/dotFadeInDuration)+1, dotCount)`; если `k < totalDotsCompleted` → `reset()` + полный re-fade; fade-out cue при `start+1.0 < elapsed && totalDotsFadedIn == dotCount && elapsed < end-1.8` | `update` `0x10044aef0`; `fadeOut` `0x10044b728` (transforms zoom in/out) | EXACT (формулы/условия) |
| 54 | anchor точек | первый/последний dot получают `anchorPoint` со смещением `+1.3` / `-1.3`; ось не видна в псевдокоде | `createDots` `0x10044bdcc` (`1.3` = `0x3ff4cccccccccccd`, `-1.3`) | EXACT (значения) / INFERRED (ось/семантика) |
| 55 | порог показа брейка | **NOT FOUND** — механика приходит из `Lyrics.InstrumentalLine` (`MSVLyricsSongInfo`), gap-порог 7000 ms в iOS не найден | `APPOS_LYRICSX.md` §7/§12; `STATE_RECONCILIATION.md` | NOT FOUND |

### 2.6. Seek / reset

| # | Что | Значение / формула | Источник | Статус |
|---|---|---|---|---|
| 56 | API seek | `syncedLyrics(_:jumpTo:select:elapsedTime:)`; `syncedLyrics(_:finish:)`; `syncedLyricsDesectAllLines` | `0x1004785d0`, `0x100479164`, `0x100479160` | EXACT |
| 57 | быстрый seek (mode==2) | аниматор 0.25 s curve 3; в блоке — опциональный `select(line, animator, deselectAll: true, fadeInInstrumental: true)` | `0x1004785d0` | EXACT |
| 58 | общий seek | пересчёт target offset → intersect check → layout строк → `animate(line:to:elapsedTime:tapMode:gap:adjustedContentOffset:)`; для instrumental — `fadeIn(elapsed)` или `update(elapsed)` | `0x1004785d0`, `0x100473200` | EXACT (структура) |
| 59 | reset менеджера | при откате `update` пересобирает `nextLine`/`selectedLines`; `calculateNextLineAction` даёт ветку пере—выбора; hysteresis провайдера (#6) | `0x10045b18c`/`0x10045b98c` | EXACT (структура) / INFERRED (детали) |
| 60 | reset instrumental | даун-пересчёт `k < totalDotsCompleted` → полный `reset()` (см. #53) | `0x10044aef0` | EXACT |
| 61 | защита прогресса при seek | микро-откат `< 0.5` s игнорируется на уровне `setProgress` (см. #31); крупный откат применяется | `0x100447974` | EXACT |
| 62 | пауза/возобновление | `displayLinkShouldPause` `0x100470a18`, `displayLinkResumeIfNeeded` `0x100c8e804` (Music.app), `displayLinkFired` `0x10047c760` | MEE/Music.app | EXACT (символы), логика — PARTIAL |

### 2.7. Open/close transition (Now Playing ↔ lyrics)

| # | Что | Значение | Источник | Статус |
|---|---|---|---|---|
| 63 | переход открытия/закрытия в LyricsX/MEE | **NOT FOUND** — в MEE нет transitioning delegate/аниматора для презентации лирики | `APPOS_LYRICSX.md` §12 | NOT FOUND |
| 64 | app-side: `Music.NowPlayingLyricsViewController` как transitioning delegate | `animationControllerForPresentedController:presentingController:sourceController:` @`0x1002b1170` создаёт `Music.LyricsSharingAnimationController` | Music.app (`APPOS_MUSIC_APP.md` §2.1, `APPOS_MUSIC_APP_DECOMP.md` §3.2) | EXACT (app-side) |
| 65 | app-side spring | `UISpringTimingParameters(mass 1.0, stiffness 396.0, damping 32.0)` (`stampAnimator = UIViewPropertyAnimator(duration: 0, timingParameters:)`) | Music.app `0x10033b6f0`, `0x1002b1790` | EXACT (app-side) |
| 66 | app-side: переход Palette → Now Playing (не лирика) | `UIViewPropertyAnimator(duration 0.5, UICubicTimingParameters())` + `MPCubicSpringAnimator(duration 0, MPCubicSpringTimingParameters(m 3.0, k 500.0, c 1000.0), cp (0.1878,0.0023)-(0.5399,0.9629))` | Music.app `0x100544c68` | EXACT (app-side, другой переход) |

**Вывод:** собственная open/close анимация лирики как UIKit-transition в доступных бинарях не найдена; есть только app-side класс `LyricsSharingAnimationController` (spring m1/k396/c32), связанный с `NowPlayingLyricsViewController`. Его точная роль (sharing-sheet vs открытие лирики) статически не доказана — `[INFERRED]`. Для порта: делать переход app-side, параметры (65) использовать только как ориентир с пометкой.

---

## 3. Реализационная сводка (что писать в код)

### 3.1. Safe to hardcode (проверено ≥1 сырым артефактом, аудит не оспаривает)

| Константа | Значение | Комментарий |
|---|---|---|
| `maxSelectedLines` | 2 | значение EXACT; имя поля читается менеджером |
| `maxEndTimeOffset` | 0.5 s | EXACT |
| `lineDelay` | 0.05 s | EXACT; каскадная задержка строк, не TTML-gap |
| `animationHeadstart` | 0.1 s | EXACT |
| `opacityAnimator` | 0.12; cp (0.33,0)/(0.2,0.1) | EXACT (MEE + Music.app) |
| line/scroll animator | 0.28; cp (0.17,0)/(0.83,1.0) | EXACT |
| `emphasizingScaleRange` | 1.0…1.14 | EXACT (emphasis слов) |
| `deselectedTransform` | scale 0.98 | EXACT |
| `backgroundVocalsDeselectedTransform` | scale 0.9 | EXACT |
| highlight on/off | m1/k322/c24 (fade 0.2/0); m2/k300/c50 (0.3/0.1) | EXACT |
| lift/tap/bgVocals/grow springs | 1/14/7; 2/260/50; 1/30/9 и ζ1.0 r0.2; ζ1.0 | EXACT (числа) |
| syllableBySyllable formula | 0.2 / 0.55 / 0.75 / 0.12 / 0.78 / 0.27 / 0.48 | EXACT |
| blur cap / fixed / flag | 4.0 / 3.0 / `lineBlurEnabled` | EXACT |
| insets | 22.0 / 30.0 | EXACT |
| contentOffset ветви | `rect.maxY - (containerHeight - rect.height)*0.5 - payload` / `rect.minY - contentInset.top` | STRONG_INFERENCE |
| gradient colors | `[color, color.alpha0]` | EXACT |
| feather | 30.0 pt | EXACT |
| progress guard | backward `< 0.5 s` игнор | EXACT |
| `lineFinishProgressAnimationDuration` | 0.25 s | EXACT |
| instrumental dots | 3 / 12 / 8 / 40 / alpha 0.1 | EXACT |
| instrumental transforms | 0.9 / 1.2 / 1.2 / 0.2 | EXACT |
| instrumental reset formulas | `-1.8`; `(end-(start+1))/count`; `(dur/trunc(dur*0.25))*0.5` | EXACT |
| dot fade-in | animator 0.8 s; stagger 0.06; breath `duration-0.4`, delay 0.2 | EXACT (числа) |
| seek fast path | 0.25 s curve 3 | EXACT |
| hysteresis | 1.0 s / 0.5 s | EXACT |
| glow | radius 5.0; range 0.0…0.4; response `min(duration,3.0)`, dampingRatio 1.0 | EXACT |
| `syllableLift` | 2.0 | EXACT (значение) |

### 3.2. Runtime-dependent (нельзя зашивать как константы)

| Величина | Почему |
|---|---|
| `containerHeight`, `cardHeight`, `scrollView.frame` | layout runtime; fallback только при `<= 0` |
| `contentInset.top` (dynamic) | вычисляется в `updateInsets()` из `staticTop/staticBottom`, `containerHeight`, `bottomTapAreaHeight` |
| `elapsedTime` / `spatialOffset` / `isPlayingSpatial` | playback-state |
| `selectedLinePosition` payload | 33-байтовый enum; кейсы не восстановлены |
| `mode` VC (2 = особый путь seek) | app-level состояние |
| расстояния точек/строк до выделенной | зависят от layout/видимости |
| `line.startTime/endTime`, `word.startTime`, `syllable.startTime` | данные трека (TTML/MSV) |
| источник `elapsedTimeProvider` и момент вызова `update()` | задаётся app-слоем |
| переходы открытия/закрытия лирики | app-side (UIKit), в LyricsX отсутствуют |
| instrumental line как таковая | приходит извне (`MSVLyricsSongInfo`), не вычисляется по gap |

---

## 4. Спорные / не подтверждённые значения (НЕ hardcode)

| Значение | Где встречается | Почему спорно | Аудит-референс |
|---|---|---|---|
| `100.0` (lineChangeSpring stiffness) | `Specs.init` `0x100460b1c` создаёт значения хелпером из целых `1`, `100`, `18`; Music.app `vpfi` `0x100c2c38c` | прямой double `100.0` в MEE не найден (только float), порядок mass/stiffness/damping не подтверждён | `09_appos/APPOS_RED_TEAM_AUDIT.md` строка 58 (ранг 58) → **PARTIAL/UNVERIFIABLE**; `STATE_RECONCILIATION.md` (100.0) |
| `0.95` (`touchDownTransform`) | `Specs.init` `0x100460b1c` (scale `0x3fee666666666666`); Music.app `vpfi` `0x100c2c2c8` | аудит MEE: double `0.95` не найден; в Music.app vpfi литерал есть, но значение не перепроверено аудитом | `09_appos/APPOS_RED_TEAM_AUDIT.md` строка 60; M2 (строка 230); `STATE_RECONCILIATION.md` (0.95) |
| `39.0` (`paragraphSpacing`) | `Specs.init` `0x100460b1c` (uStack_300 = `0x4043800000000000`); Music.app `vpfi` `0x100c2b4b4` | в MEE double 39.0 аудитом не найден (только float) | `09_appos/APPOS_RED_TEAM_AUDIT.md` строка 59 → **UNVERIFIED** |
| `1.12` (scale) | старый Android prior art | в iOS corpus отсутствует; реальная верхняя граница эмфазы 1.14 | `03_lyrics/LYRICS_ANIMATIONS.md` §4 |
| `38%` / `28%` viewport anchor | старые спеки/Android patch | в iOS отсутствуют; 0.28 — duration аниматора; 28% — `ACTIVE_LINE_BIAS` пользовательского patch'а | `03_lyrics/LYRICS_SCROLLING.md` §RETRACTED |
| `4000 ms` user-scroll pause | Android patch `USER_SCROLL_PAUSE_MS` | в iOS не найдено | `03_lyrics/LYRICS_SCROLLING.md` |
| `7000 ms` instrumental threshold | Android plan §48 | в iOS не найдено (interlude приходит из модели) | `STATE_RECONCILIATION.md`; `INSTRUMENTAL_BREAK.md` |
| `10dp/6dp`, 750/250 ms dots | Android prior art | iOS: 12/8 pt, 0.8 s/+0.06/breath | `INSTRUMENTAL_BREAK.md` §RETRACTED |
| `smoothstep` | third-party GLSL | в Apple-коде 0 вхождений | `03_lyrics/WORD_TIMING.md` §RETRACTED |
| ось `anchorPoint ±1.3` точки | MEE `createDots` | значения EXACT, ось в псевдокоде не видна | `APPOS_LYRICSX.md` §7 |
| семантика `SelectedLinePosition` и кейсов `LineAction` | MEE | раскладка/значения EXACT, имена кейсов не восстановлены | `APPOS_LYRICSX.md` §8/§12 |

---

## 5. NOT FOUND (честные пробелы спеки)

1. `currentWord` implementation (символ `0x100440be8` есть, тело не выгружалось).
2. Полная диаграмма `Word.animateGlow` (async-замыкания; числовые базы — 0.4-кап стаггера, `count/2` — из псевдокода, но end-to-end не проверены).
3. TTML-парсер (внешний `MSVLyricsTTMLParser`; в бинарях отсутствует).
4. Порог включения инструментального брейка и правила пауз.
5. Anchor/bias активной строки, user-scroll pause, seek-snap-политика (только `0.5` jitter-guard и 0.25-s fast path).
6. Открытие/закрытие лирики как transition в LyricsX/MEE (только app-side ориентиры).
7. Значения `Word.CrossfadeAnimationParameters.crossfadeDuration/crossfadeTimingFunction` (`09_appos/APPOS_MUSIC_APP_DECOMP.md` §4).
8. Haptics/звук переключения строк; RTL-специфика рендера (кроме вывода направления из текста).

---

## 6. RETRACTED / CORRECTED (для аудитора)

1. **«anchor 28% / 38% viewport»** → `NOT FOUND`; `0.28` — duration аниматора (`0x100473200`), 28% — `ACTIVE_LINE_BIAS` Android-patch'а. (`03_lyrics/LYRICS_SCROLLING.md` §RETRACTED повторно подтверждено.)
2. **«instrumental 10dp/6dp, 750/250 ms»** → iOS: dots 12/8 pt, alpha 0.1, transforms 0.9/1.2/1.2/0.2, animator 0.8 s, stagger 0.06, breath `duration-0.4`. (`APPOS_LYRICSX.md` §13.4.)
3. **«1.12 scale / emphasis — Android»** → iOS `emphasizingScaleRange = 1.0...1.14` в `Specs.init` `0x100460b1c`, применяется в `Word.animateGlow`. (`APPOS_LYRICSX.md` §13.2.)
4. **«setSelected:animator: без типа animator»** → `UIViewPropertyAnimator?` (`0x100452b64`). (`APPOS_LYRICSX.md` §13.5.)
5. **«lineDelay — межстрочный интервал»** → используется как множитель каскада `startAnimationAfterDelay`; межстрочная геометрия — `lineSpacing 25.0`/`paragraphSpacing`.

---

## 7. Рекомендуемый порядок реализации (clean-room)

1. Модель строк/слов/слогов из `MSVLyricsSongInfo` (внешний парсер TTML).
2. Менеджер: `elapsed → update → calculateNextLineAction` с `maxSelectedLines=2`, `maxEndTimeOffset=0.5`, `lineDelay=0.05`, `animationHeadstart=0.1`.
3. Строка: `setSelected(_:animator:)`, transform 0.98/identity, opacity-blur 0.12 cubic.
4. Karaoke: `[color, alpha0]`, feather 30, L2R/R2L, progress guard 0.5, lift spring m1/k14/c7, emphasis 1.0…1.14.
5. Scroll: `contentOffset(for:)` + insets 22/30 + аниматор 0.28 cubic.
6. Instrumental: reset/fadeIn/update/geometry (раздел 2.5).
7. Seek: fast path 0.25 curve 3, иначе пересчёт offset; reset instrumental при откате.
8. Переход open/close — отдельный app-level слой (в LyricsX отсутствует).
