# NOW_PLAYING_ANIMATION_SPEC — Phase 9 Final Closure: UIKit sheet, Backdrop crossfade, Artwork morph, display-link coefficient

**Дата:** 2026-09-14. Subagent «Final Closure: Animations Analyst». RESEARCH ONLY; оригиналы не изменялись.
**Источники:** свежие DSC subcaches `/srv/research/apple-music-ios26/appos/sys_dsc/System/Library/Caches/com.apple.dyld/` (.03/.11/.33/.34/.70/.71/.75/.76), карвированный `/tmp/opencode/MediaCoreUI.macho` (subcache .21), извлечённый MusicUI `/srv/research/tmp/extracted_dylibs/MusicUI` (sha-не пересчитывался, Mach-O arm64e), Ghidra-экспорт Music.app `09_appos/musicapp_ghidra/`.
**Метод:** capstone 5.0.7 (skipdata), symbol-atlas `/tmp/opencode/dsc/symbols.bin` + `p3work/symlookup.py`, chained-fixup декодер `p10work/vm2.py`, Swift metadata/fieldmd парсинг, llvm-objdump-19 по извлечённому MusicUI. Окно `bl`-сканирования __TEXT UIKitCore 0x188973000+0x23a7740 дало полное покрытие 677 823 инструкций.

## 0. Сводка вердиктов

| # | Задача | Вердикт | Главные числа |
|---|---|---|---|
| A | UIKit sheet `_UISheetAnimationController`/`_UISheetPresentationMetrics` | **CLOSED**; гипотеза «0.5 c наследуется» — **ОПРОВЕРГНУТА** | duration **0.4 s** (`+[UITransitionView defaultDurationForTransition:8]`); curve = `UISpringTimingParameters(dampingRatio 1.0, response 0.3441442326)` → mass 1.0, k 333.33333328, c 36.51483716; high-speed ζ=0.8 → c 29.21186973 |
| B | `MediaCoreUI.Backdrop.CompositeRenderer.crossfadeDuration` | **CLOSED** | default **0.8f** (`0x3F4CCCCD`, пишется в `init` @0x1c4d062ac); consumer `textureTransitionMix += dt / 0.8` (clamp 1.0) |
| C | Artwork morph | **PARTIAL → advanced** | morph-state size (`+0x30/+0x38`), easeInOut(`UIView.inheritedAnimationDuration`); геометрия = constraints к `artworkLayoutGuide` (regular) / `view` (fullscreen); conditional factor **0.73/1.0**; AutoMix-часть 3.0/4.8/0.6/1.3 подтверждена |
| D | #36 display-link коэффициент | **CLOSED** | ключ `PPTContentOffsetScrollIncrement` (31 chars), default **10.0**; на диске 0.0 (runtime lazy); шаг `contentOffset.y += K` за кадр |

---

# A. UIKit sheet presentation (UIKitCore, DSC .03)

## A.1. Цепочка классов (EXACT)

```
Source file: appos/sys_dsc/.../dyld_shared_cache_arm64e.03 (UIKitCore __TEXT 0x188973000 size 0x23a7740)
Framework/Binary: UIKitCore
Function/Symbol: _UISheetAnimationController, _UISheetPresentationMetrics, UITransitionView, UISpringTimingParameters, _UISpringParameters
Address/Offset: см. ниже
Evidence type: disassembly (capstone) + symbol atlas (local symbols)
Status: EXACT
Reasoning: все адреса символов взяты из atlas /tmp/opencode/dsc/symbols.bin (UIKitCore, 281 831 симв.), дизасм — из свежего subcache .03.
```

Ключевые символы:

| Символ | Адрес |
|---|---|
| `-[_UISheetAnimationController init]` | 0x189712a70 |
| `-[_UISheetAnimationController transitionDuration:]` | 0x189712cd4 |
| `-[_UISheetAnimationController interruptibleAnimatorForTransition:]` | 0x189712cd8 |
| `-[_UISheetAnimationController animateTransition:]` | 0x18971357c |
| `-[_UISheetAnimationController isForward]` | 0x189712cb8 |
| `-[_UISheetAnimationController isReversed]` / `setIsReversed:` | 0x1897138cc / 0x1897138d4 |
| `-[_UISheetAnimationController addNoninteractiveAnimations:]` | 0x189712c10 |
| `-[_UISheetAnimationController addNoninteractiveCompletion:]` | 0x189712c64 |
| `-[_UISheetAnimationController runNoninteractiveAnimationsIfPossible]` | 0x189713738 |
| `__UISheetTransitionDuration` | 0x1894a2938 |
| `__UISheetTransitionSpringParametersHighSpeed` | 0x1894a2978 |
| `__UISheetTransitionTimingCurve` (+_block_invoke) | 0x1894a29cc / 0x1894a2a20 |
| `__UIFallbackSheetMetrics` | 0x188a34a28 |
| `-[_UISheetPresentationMetrics transitionDuration]` | 0x189cd6400 |
| `-[_UISheetPresentationMetrics transitionSpringParametersHighSpeed:]` (+_block_invoke) | 0x189cd6410 / 0x189cd6484 |
| `+[UITransitionView defaultDurationForTransition:]` | 0x18a251b70 |
| `-[UISpringTimingParameters initWithParameters:initialVelocity:]` | 0x1891a47c4 |
| `-[UISpringTimingParameters initWithDampingRatio:response:initialVelocity:]` | 0x1891a4694 |
| `+[UISpringTimingParameters _convertDampingRatio:response:toMass:stiffness:damping:]` | 0x1891a4cf4 |
| `+[_UISpringParameters parametersWithDampingRatio:response:]` | 0x189c3d34c |

## A.2. Длительность: 0.4 s, НЕ 0.5 s (EXACT) — опровержение наследования 0.5 c

`-[_UISheetAnimationController transitionDuration:]` @0x189712cd4 — это 4-байтовый tail-branch:

```
189712cd4:	b #0x1894a2938        ; __UISheetTransitionDuration()
```

`__UISheetTransitionDuration` @0x1894a2938:
```
1894a294c: bl #0x188a34a28        ; __UIFallbackSheetMetrics()
1894a2950: bl #0x18ce00c60        ; retain
1894a2954: mov x19,x0
1894a2958: bl #0x18ad05da0        ; objc_msgSend$transitionDuration (atlas)
1894a295c: fmov d8,d0
1894a2960: bl #0x18ce00e40        ; release
1894a2964: fmov d0,d8             ; return d0
```

`__UIFallbackSheetMetrics` @0x188a34a28 = `[[_UIPresentationControllerDefaultVisualStyleProvider sharedInstance] defaultSheetMetrics]`
(классref 0x1e72eccf0 → класс `_UIPresentationControllerDefaultVisualStyleProvider`; селекторы `sharedInstance`@0x18ace7820, `defaultSheetMetrics`@0x18ac1da00 — имена из atlas).

`-[_UISheetPresentationMetrics transitionDuration]` @0x189cd6400:
```
189cd6400: adrp x8,0x1e72eb000
189cd6404: ldr  x0,[x8,#0xb08]     ; classref 0x1e72ebb08 -> UITransitionView
189cd6408: mov  w2,#8
189cd640c: b    #0x18ac1c060       ; objc_msgSend$defaultDurationForTransition:
```

`+[UITransitionView defaultDurationForTransition:]` @0x18a251b70 — таблица из 16 double в __TEXT 0x18a539c68 (index = w2):

| idx | 0 | 1–3 | 4–6 | 7 | 8 | 9 | 10–11 | 12 | 13–14 | 15 | >15 (fallback 0x18a50a6a8) |
|---|---|---|---|---|---|---|---|---|---|---|---|
| double | 0.0 | 0.35 | 0.4 | 0.35 | **0.4** | 0.4 | 0.7 | 0.35 | 0.6 | 0.7 | 0.4 |

`s = 8` → **duration = 0.4000000000000000222** (raw `0x3fd999999999999a`) — 0.4 c.

**Вердикт:** базовый UIKit sheet-переход (present и dismiss) идёт с **0.4 c**, а не 0.5 c. Подкласс Music.app `PalettePresentationAnimationController` **не переопределяет** `transitionDuration:` (его ObjC-методы: `init` 0x100544988, `interruptibleAnimatorForTransition:` 0x100544a7c, `animationEnded:` 0x100544c14, `.cxx_destruct`), а его `interruptibleAnimatorForTransition:` вызывает `super` и возвращает аниматор базового класса:

```c
/* 0x100544a7c PalettePresentationAnimationController */
FUN_100544a58();                       /* prepare: noninteractive animations */
IVar5 = _objc_msgSendSuper2(&super, "interruptibleAnimatorForTransition:", ctx);
return IVar5;                          /* <- базовый animator, duration 0.4 */
```

0.5 c (`UIViewPropertyAnimator(duration:0.5, UICubicTimingParameters())`) и `MPCubicSpringAnimator(m3 k500 c1000, cp(0.1878,0.0023)/(0.5399,0.9629))` из `Music.PalettePresentationAnimationController` — это **noninteractive** аниматоры (передаются в `setNoninteractiveAnimations:`/`setNoninteractiveCompletion:`), которые выполняются рядом с базовым интерактивным аниматором. Это CORRECTION к трактовке «Palette → 0.5 c — длительность sheet-перехода» (см. §RETRACTED).

## A.3. Кривая: UISpringTimingParameters(ζ=1.0, response=0.3441442326) (EXACT)

`__UISheetTransitionTimingCurve` @0x1894a29cc (cached global 0x1ed6b2710; once-токен 0x1ed6b2718) инициализируется блоком 0x1894a2a20:

```
1894a2a34: ldr x0,[0x1e72eb568]   ; classref -> UISpringTimingParameters
1894a2a3c: bl  #0x18ce00bd0       ; alloc
1894a2a44: mov w0,#0
1894a2a48: bl  #0x1894a2978       ; __UISheetTransitionSpringParametersHighSpeed(0)
1894a2a5c: movi d0,#0 ; movi d1,#0
1894a2a64: bl  #0x18ac4eac0       ; initWithParameters:initialVelocity:
```

`__UISheetTransitionSpringParametersHighSpeed(arg)` = `[__UIFallbackSheetMetrics() transitionSpringParametersHighSpeed:arg]`.
`-[_UISheetPresentationMetrics transitionSpringParametersHighSpeed:]` @0x189cd6410 возвращает один из двух закэшированных `_UISpringParameters` (слоты +0x10/+0x18; csel: `arg==0` → +0x10, `arg!=0` → +0x18). Блок 0x189cd6484 строит оба из `__UIInternalPreference`:

| preference (CFString VA) | имя | default (double) |
|---|---|---|
| 0x1efe0e198 | `SheetDampingRatio` | **1.0** |
| 0x1efe0e1b8 | `SheetResponse` | **0.3441442326** (`0x3fd606758807efe5`, literal 0x18a537078) |
| 0x1efe0e1d8 | `SheetHighSpeedDampingRatio` | **0.8** (`0x3fe999999999999a`, literal 0x18a50f828) |

- стандартный объект (arg=0): ζ=1.0, response=0.3441442326;
- high-speed объект (arg≠0): ζ=0.8, response=0.3441442326.

Цепочка конверсии (EXACT):

```
-[UISpringTimingParameters initWithParameters:initialVelocity:]  @0x1891a47c4
   -> init(dampingRatio:response:initialVelocity:)              @0x1891a4694 (tail 0x18ac4a1e0)
      -> [+UISpringTimingParameters _convertDampingRatio:response:toMass:stiffness:damping:] @0x1891a4cf4
         d2 = 6.283185307179586 (2π, literal 0x18a50cd98)
         mass      = 1.0
         stiffness = (2π / response)^2                                  ; fmul d1,d1,d1
         damping   = 2 * zeta * sqrt(stiffness)                         ; fsqrt+fadd+fmul
      -> -[UISpringTimingParameters initWithMass:stiffness:damping:initialVelocity:] @0x18ac4de40
```

Итоговые числа:
- standard: mass **1.0**, stiffness **333.3333332805039**, damping **36.51483716411749** (ζ=1.0, critical);
- high-speed: mass 1.0, stiffness 333.3333332805039, damping **29.21186973129399** (ζ=0.8).

Базовый аниматор `interruptibleAnimatorForTransition:` @0x189712cd8 создаётся так (EXACT):

```
189712d4c: bl 0x18ad05dc0        ; [self transitionDuration:ctx]      -> d8 = 0.4
189712d54: bl 0x1894a29cc        ; __UISheetTransitionTimingCurve()   -> spring UISP
189712d6c: bl 0x18ac4af60        ; initWithDuration:(0.4)timingParameters:(spring)
189712d7c: bl 0x18accee20        ; [self setPropertyAnimator:]
189712d90: bl 0x18acdf3c0        ; [self setTransitionContext:]
```

## A.4. Interruptibility и interactive path (EXACT)

- Возвращается **UIViewPropertyAnimator**, закэшированный в ivar `_propertyAnimator` (0x189712d20 getter; повторный вызов возвращает существующий инстанс).
- `-[_UISheetAnimationController animateTransition:]` @0x18971357c = `[self interruptibleAnimatorForTransition:ctx]` + `startAnimation` (селекторы `interruptibleAnimatorForTransition:`@0x18ac581a0, `startAnimation`@0x18acf1b40).
- Направление: `isForward` @0x189712cb8 = `![self isReversed]` (`objc_msgSend$isReversed` + `eor w0,w0,#1`); `isReversed` — байтовый ivar +0x8 (`_OBJC_IVAR_$__UISheetAnimationController._isReversed` = 0x1eab325dc). `UISheetPresentationController` выставляет его в `_setOcclusionEnabled:`-блоке: `setIsReversed:(w21^1)` @0x189fcefac (селектор `setIsReversed:`@0x18acbf480).
- Interactive drag: `_UISheetInteraction` держит `_animator`, `_dragSource`, detent-интерполяцию; `UISheetPresentationController _completeInteractiveTransition:duration:timingCurve:` при завершении (не dragging) анимирует через `_animateWithParameters:animations:` (0x18ab4b420) с `__UISheetTransitionDuration()` и `__UISheetTransitionSpringParametersHighSpeed(0)` (arg=0 → стандартная ζ=1.0) — 0x189fcf8fc..0x189fcf914.
- High-speed spring (ζ=0.8) запрашивается в `_UISheetInteraction` drag-блоках: `___86-[_UISheetInteraction draggingChangedInSource:withTranslation:velocity:animateChange:]_block_invoke` (0x18918c790/98) и `dragingEndedInSource`-блок (0x18918cc20, флаг = сравнение скоростей `cset w23,ge`).
- Scroll/detent-анимации `_UISheetInteraction _scrollView:adjustedUnconstrainedOffset...` используют обе функции (0x18918c02c/0x18918c034).

## A.5. Present vs dismiss (EXACT/STRONG_INFERENCE)

- Отдельной «dismiss-длительности/кривой» нет: и present, и dismiss используют один и тот же `transitionDuration:` → 0.4 c и ту же spring-кривую (0x189712cd4/0x189712d54 не зависят от направления).
- Различие — направление: `isForward = !isReversed`; по нему выбираются `forwardView` (`viewForKey:` From/To, 0x1e73050c0/0x1e73050b8) и `forwardViewFullFrame` (`finalFrameForViewController:`/`initialFrameForViewController:`, 0x18ac33800/0x18ac53660).
- `layoutTransitionViews` @0x1897135bc читает `forwardView`/`forwardViewFullFrame`/`interactiveFrame` (`objc_msgSend$interactiveFrame`@0x18ac578a0), `sourceView`/`sourceFrame` (0x18acf0500/0x18acf01a0), `presentedView`, `frameOfPresentedViewInContainerView`, `convertRect:toView:`; при `transitionWasCancelled` (0x18ad064c0) идёт отдельная ветка завершения.
- Блок без анимации: `[UIView performWithoutAnimation:^{ [self layoutTransitionViews]; [[self forwardView] layoutIfNeeded]; }]` (0x189712fb8-0x18971300c, invoke 0x1897132dc).
- Доп. хуки: `[metrics addAlongsideAnimations:forSheetTransition:context:]` (0x18abedb00, вызов @0x18971304c); `addAnimations:`/`addCompletion:` (0x18abedc00/0x18abee100).

## A.6. Что осталось в A

- Точное значение enum-аргумента `8` у `defaultDurationForTransition:` (семантика типа перехода) — таблица подтверждена, имя enum — NOT FOUND.
- Семантика `0x18ce00ff0` (первый вызов в `interruptibleAnimatorForTransition:`) — NOT FOUND (стаб отсутствующего subcache; x19 однозначно используется как transitionContext позже — `setTransitionContext:`/`viewForKey:`).
- Пользовательские значения preference-ключей `SheetDampingRatio`/`SheetResponse`/`SheetHighSpeedDampingRatio` на устройстве — runtime.

---

# B. MediaCoreUI `Backdrop.CompositeRenderer.crossfadeDuration`

## B.1. Класс и поле (EXACT)

```
Source file: /tmp/opencode/MediaCoreUI.macho (карвирован из DSC subcache .21; sha первоисточника не перепроверялся)
Framework/Binary: MediaCoreUI (private framework)
Function/Symbol: _TtCO11MediaCoreUI8Backdrop17CompositeRenderer (ObjC-имя класса), поле хранимого типа
Address/Offset: class_ro_t @0x1eed88268 (name -> 0x1c4e22430); field offset global 0x1ec31d430 = 0x34
Evidence type: Swift metadata (fieldmd/field-offset vector/ivar list) + disassembly
Status: EXACT
Reasoning: class_ro_t в __AUTH __objc_data; field descriptor 0x1c4e5e3e8 (20 полей, поле[5] name='crossfadeDuration', type='Sf'=Float);
           metadata field-offset vector (metadata 0x1edc56538, +0x58.. ) дает 0x34; ObjC ivar list 0x1f475eea0 подтверждает
           имя->смещение и 4-байтовый размер (flags=0x2).
```

MediaCoreUI собран с library evolution (resilient): все обращения к полям идут через глобальные слоты field-offset (напр. `adrp x8,0x1ec31d000; ldr x8,[x8,#0x430]` → self+0x34), прямых `str s?,[x0,#0x34]` в __TEXT нет — это объясняет прежний NOT FOUND.

## B.2. Значение по умолчанию: 0.8f (EXACT)

Тело designated init @0x1c4d06210 (обёртка alloc+init — 0x1c4d061cc):

```
1c4d062a4: adrp x8,0x1ec31d000
1c4d062a8: ldr  x8,[x8,#0x430]      ; field offset crossfadeDuration (0x34)
1c4d062ac: mov  w9,#0xcccd
1c4d062b0: movk w9,#0x3f4c,lsl #16  ; w9 = 0x3F4CCCCD
1c4d062b4: str  w9,[x20,x8]         ; self.crossfadeDuration = 0.8f
```

```
raw value: 0x3F4CCCCD
decoded value: 0.800000011920929 (Float 0.8)
instruction/function context: 0x1c4d062ac/b0/b4, init(context:configuration:) (0x1c4d06210)
how it is used: значение по умолчанию; далее configuration поле НЕ перезаписывает 0x430 (проверено весь init 0x1c4d06210-0x1c4d065a8)
Status: EXACT
```

Тот же init задаёт соседние дефолты: `framebufferPixelFormat=0x50` (80, BGRA8Unorm), `colorPixelFormat=0x73` (115, RGBA16Float), `isPaused=false`, `imageStorage=nil`, `placeholderColor=systemGrayColor`, `warpTimingSpeed=3.5` (double `0x400c000000000000`), `crossfadeTimingFunction=CAMediaTimingFunction(0,0,0.3,1)` (`fmov s2,#0x3E99999A` → 0.3), `modeTimingFunction`/`warpTimingFunction=CAMediaTimingFunction(0.42,0,0.58,1)`, `aspectRatio=(1.0,1.0)`.

## B.3. Accessors и consumer (EXACT)

| Что | Адрес | Код |
|---|---|---|
| getter (vtable) | 0x1c4d05298 | `ldr x8,[0x1ec31d430]; beginAccess; ldr s0,[self+x8]; ret` |
| setter (vtable) | 0x1c4d052e0 | `str s8,[self+x8]; endAccess; retab` |
| `_modify` accessor | 0x1c4d05334/0x1c4d05340 | coroutine, падает на 0x1c4bd8b4 |
| **consumer** (progress) | 0x1c4d077a8 | `d = dt; s0=dt/xfadeDuration; mix += s0; clamp 1.0; store textureTransitionMix` |

Consumer полностью (0x1c4d077c8-0x1c4d07808):
```
ldr x8,[0x1ec31d430]; add x19,x20,x8     ; &crossfadeDuration
bl  swift_beginAccess
ldr s0,[x19]                              ; crossfadeDuration
fdiv s8,s8,s0                             ; dt / crossfadeDuration
ldr x8,[0x1ec31d448]; ldr s0,[x20,x8]     ; textureTransitionMix (offset 0xa0)
fadd s0,s8,s0
fcsel s0,s1(=1.0),s0,gt                    ; clamp <= 1.0
str s0,[x20,x8]
```
(B этом же методе далее: `pinchMix` 0xa4 → `fsub`, и т.п.)

## B.4. Callsites (EXACT)

MediaCoreUI (carve):
- SwiftUI `Backdrop` view accessor-пара: getter @0x1c4bc134c, setter @0x1c4bc1390 (оба через field-offset 0x430).
- `Backdrop` body: `spectrumAnalysis` mix 0.2/1.0 — 0x1c4bc12ac (`0x3E4CCCCD`=0.2 vs 1.0 по флагу).

Music.app (`09_appos/musicapp_ghidra`):
- getter-вызов: `0x100428748: bl 0x100da74b0` (в `FUN_1004282fc`, `Music.MusicLyricsBackgroundView`, source `Music/LyricsBackgroundView.swift`; сразу после `CompositeRenderer(context:configuration:)` — 0x100da7500 — значение читается и кладётся в `MusicLyricsBackgroundView::defaultDuration`);
- setter-вызовы: `0x100427de8: bl 0x100da74c0` (`FUN_100427d10`) и `0x1004282bc: bl 0x100da74c0` (`FUN_1004281c8`: `renderer.crossfadeDuration = view.defaultDuration`, только если изменилось и не reduced-motion-ветка).
- init-обёртки: `MediaCoreUI.Backdrop.CompositeRenderer` getter/setter/init/setImage — thunks 0x100da74b0/0x100da74c0/0x100da7500/0x100da7540 (`_$s11MediaCoreUI8BackdropO17CompositeRendererC...`).

`ShaderFallbackKit.Backdrop.CompositeRenderer.init` @0x100bd9890 — `__swift_stdlib_reportUnimplementedInitializer` (заглушка, не источник значения).

**Вердикт B:** реальное значение — **Float 0.8 s**; прогресс backdrop-кроссфейда = `dt/0.8` с клампом 1.0; Music.app читает дефолт сразу после init и синхронизирует его с `MusicLyricsBackgroundView.defaultDuration`.

---

# C. Artwork morph (advance PARTIAL)

## C.1. `MorphingMotionArtworkState` / `MorphingMotionArtworkContainer` (EXACT)

```
Source file: appos/extracted/private/var/staged_system_apps/Music.app/Music (ghidra export 09_appos/musicapp_ghidra)
Framework/Binary: Music (Swift, приватные классы P33_E38AC92F7F664DD94CDA81A33FE0509F)
Function/Symbol: _TtC5MusicP33_...26MorphingMotionArtworkState / ...30MorphingMotionArtworkContainer
Address/Offset: state-слот контейнера: глобальный field-offset 0x10114b8a0; методы: 0x1005e08ec (мутатор состояния), 0x1005e2610 (реализация layoutSubviews), ObjC-тхук 0x1005e28b8
Evidence type: disassembly + pseudocode
Status: EXACT
```

Поля state (Swift @Observable):
- `+0x30` width (Double), `+0x38` height (Double), `+0x40` animated-флаг (Bool).
- Мутатор `FUN_1005e08ec(w,h,animated)`: если `animated==0` и значения не изменились — выход; иначе `Observation.withMutation(keyPath:)` — изменение наблюдаемое и анимируемое SwiftUI.

`MorphingMotionArtworkContainer.layoutSubviews` (реализация 0x1005e2610; ObjC 0x1005e28b8 вызывает её):
1. `super.layoutSubviews`;
2. FeatureFlags (`MusicUtilities.Feature...isEnabled`);
3. берёт `self.bounds` (size → d9/d8);
4. читает `self.state` (field-offset 0x10114b8a0); если nil — выход;
5. если `state.size != bounds.size` (или флаг animated установлен) и **не** в UIView-блоке анимации (`[UIView _isInAnimationBlockWithAnimationsEnabled] == 0`) → `FUN_1005e08ec(w,h,0)` (без анимации);
6. **иначе**: `duration = [UIView inheritedAnimationDuration]; SwiftUI.Animation.easeInOut(duration: duration); SwiftUI.withAnimation(...)` → `FUN_1005e08ec(w,h,1)`.

Итог по анимации морфа:
- **easing = SwiftUI `.easeInOut`** (в терминах CAMediaTimingFunction = `(0.42, 0, 0.58, 1)`);
- **duration = `UIView.inheritedAnimationDuration`** — наследуется от внешней UIKit-анимации: для Now Playing sheet это базовая длительность sheet-перехода **0.4 c** (Task A), для app-level Palette-обёртки — её noninteractive аниматоры (в т.ч. 0.5 c). Собственной числовой константы длительности у морфа нет.

## C.2. Геометрия source/destination (EXACT)

`NowPlayingViewController` (ObjC-имена полей из Ghidra) держит: `morphingMotionArtworkContainer`, `morphingMotionArtwork`, `morphingMotionArtworkState`, `morphingMotionLayoutGuide` (UILayoutGuide), `fullScreenMorphingMotionConstraints`, `regularMorphingMotionConstraints`, `morphingMotionDynamicConstraints`, `morphingMotionFullScreenConstraints`. Инициализация — `FUN_1005e4f80` (все nil/пустые), построение constraints — `FUN_1005cfc00` (viewIsAppearing, feature-gated):

- **regular** (`morphingMotionDynamicConstraints`): `morphingMotionLayoutGuide.{centerX,centerY,width,height} == MusicNowPlayingControlsViewController.artworkLayoutGuide.{...}` (0x1005cfc00: 0x189...→ селекторы `centerXAnchor`/`constraintEqualToAnchor:`; поля: 4 NSLayoutConstraint в Swift-массиве);
- **full screen** (`morphingMotionFullScreenConstraints`): те же 4 anchors, но к `self.view`.

Т.е. источник/назначение морфа — геометрия `artworkLayoutGuide` внутри NowPlaying controls (regular, мини/регулярный режим) ↔ полный экран `self.view`; контейнер морфа меняет bounds, state.size следует за ним (C.1), а artwork-вью анимирует свой размер. Числовые константы смещений в этих constraints не найдены (anchors equal, без multipliers) — источник/приёмник задаётся раскладкой.

## C.3. Scale/corner (PARTIAL)

SwiftUI-тело `MorphingMotionArtwork` — `FUN_1005e0cd4` (499 строк, 401 ref): использует `MusicCoreUI.MCUINamespace.motionCollection(policy:)`, затем `MusicCoreUI.View.corner(Corner)` (0x... символ `__s7SwiftUI4ViewP09MusicCoreB0E6corneryQrAD6CornerOF`) и `MusicCoreUI.View.border(_:corner:)`; читает `state.size.width` (+0x30) при `state.animated == 0` — размер участвует в построении corner/border (радиус — из `MusicCoreUI.Corner`, вычисляется по геометрии, не константа).

Найден условный множитель: double **0.73** (pool 0x100e8a088; `ldr d0,[x8,#0x88]; fmov d1,#1.0; fcsel d10,d0,d1,ne` @0x1005e1cfc), выбираемый по Bool-состоянию (`SwiftUI.State.wrappedValue` @0x100dad520) и применяемый рядом с `UnitPoint.center` (@0x100dadb60). Точный модификатор (scaleEffect/opacity/…) из псевдокода не восстановлен — **STRONG_INFERENCE (вероятно scale)**, конкретное число 0.73 — EXACT для константы.

## C.4. AutoMix/SmartTransition artwork-кроссфейд (re-check, EXACT)

Подтверждено по Ghidra-экспорту (см. также 09_appos/APPOS_MUSIC_APP_DECOMP.md §3.5):

- `_TtCO5Music22ArtworkSmartTransition8Renderer.drawInMTKView:` @0x10017b430 → helper `FUN_10017acfc`:
  `p = clamp((CACurrentMediaTime() - startTime)/3.0, 0, 1)`; перед sharpness/intensity к p применяется `CAMediaTimingFunction(0.62, 0, 0.8, 1.0)` (`initWithControlPoints::::` @0x1007c9c14);
  `sharpness = min(p*4.8, (1-p)*4.8, 0.6f)`, `intensity = p*1.3`, `speed = p + seed`, uniforms 24 B (`AlchemyUniforms`), завершение при `p>=1.0`; строки направления `fadeIn`/`fadeOut`.
- `ArtworkSmartTransition.Renderer.init()` @0x10017b498 — `__swift_stdlib_reportUnimplementedInitializer` (designated init — вне export; значение констант 3.0/4.8/0.6/1.3 перепроверено).
- `NowPlayingTransitionsButton` @0x100260e34: cornerRadius 7.0 continuous; `SmartTransitionIndicatorView` @0x100627b6c: position −(width+48.0−0.875), opacity 1.0f.

## C.5. Связь с Backdrop (Task B, EXACT)

Backdrop-кроссфейд и морф синхронизированы через длительность: `CompositeRenderer.crossfadeDuration = 0.8` по умолчанию; `MusicLyricsBackgroundView` (Music.app) забирает значение из renderer и хранит как `defaultDuration`, затем (FUN_1004281c8) возвращает его renderer'у при сбросе — т.е. backdrop-эффект для artwork-фона имеет собственную кроссфейд-константу 0.8 s, а не 0.4/0.5 s sheet-перехода. `setImage(_:animated:)`/`setPlaceholderColor(_:animated:)` — thunk'и 0x100da7540/0x100da74d0.

## C.6. NOT FOUND в C

- Точные значения `MusicCoreUI.Corner` для morphing artwork (радиус выводится из геометрии; в Music.app числовой константы нет).
- Модификатор для условного множителя 0.73 (scale/opacity) и его триггер-условие.
- Длительность/easing перехода «обычного» (не motion) artwork Mini Player↔Now Playing сверх того, что даёт базовый sheet (0.4 s): отдельной artwork-анимации в Music.app не найдено, геометрию делает UIKit sheet (Task A) + constraints C.2.

---

# D. #36 — display-link коэффициент content-offset (CLOSED)

## D.1. Ключ NSUserDefaults: `PPTContentOffsetScrollIncrement` (EXACT)

```
Source file: /srv/research/tmp/extracted_dylibs/MusicUI (Mach-O arm64e; __cstring 0x215a75620+0x15836)
Framework/Binary: MusicUI
Function/Symbol: FUN_21587b5e0 (lazy helper), FUN_21587b5c0 (continuation/once-block), -[_TtC7MusicUI...ContentOffsetScrollDisplayLinkTarget frame:] (FUN_21587b6ac)
Address/Offset: строка-литерал: заголовок 0x215a85830, текст 0x215a85850; countAndFlags 0xD00000000000001F (count=31, immortal)
Evidence type: disassembly + raw bytes (__cstring) + DSC selectors
Status: EXACT
```

Код 0x21587b618-0x21587b648:
```
adrp x8,0x215a85000; add x8,x8,#0x850; sub x8,x8,#0x20   ; 0x215a85830 (Swift string header)
orr  x1,x8,#0x8000000000000000                            ; tagged pointer -> 0x8000000215a85830
mov  x0,#0x1f ; movk x0,#0xd000,lsl #48                   ; countAndFlags 0xD0..1F (count=31)
bl   String-конструктор (0x21b8ff000)
; x20 = Swift String "PPTContentOffsetScrollIncrement"
adrp x8,0x1fb56e000; add x1,x8,#0x3a0                   ; selector "doubleForKey:" (fresh DSC .34)
mov  x0,x19; mov x2,x20
bl   objc_msgSend                                        ; [defaults doubleForKey:@"PPTContentOffsetScrollIncrement"]
fmov d8,d0
fmov d0,#10.0
fcmp d8,#0.0
b.eq  <return 10.0>                                      ; 0 -> fallback 10.0
```
`x19` = результат `[[NSUserDefaults standardUserDefaults] ...]` (селектор `standardUserDefaults` @0x1fae5acce, свежий DSC .34; чтение defaults — 0x21587b5f4-0x608).

Кэш коэффициента: глобал **0x27ce0ceb8** (DSC .70.dylddata, файловый off 0x2df8eb8; на диске double 0.0), инициализируется lazy-block'ом 0x21587b5c0 (`str d0,[0x27ce0ceb8]`) через once-токен 0x27cdfa540; `FUN_21587b5e0` — вычислитель (defaults/10.0). Ключ — тестовый (`MusicUI/PPT.swift`, "PPT" = testing hook), строка соседствует в __cstring с `scrollView`, `scrollFinished`, именем файла `MusicUI/PPT.swift`.

## D.2. Потребитель (EXACT)

`-ContentOffsetScrollDisplayLinkTarget.frame:` (thunk 0x21587b828 → тело 0x21587b6ac):
- достаёт слабую цель (0x21587b6cc: `add x0,x20,#0x10` + msgSend), затем у цели:
  - `contentOffset` (sel @0x1fac49c20),
  - `contentSize` (sel @0x1fb40aaa0),
  - `bounds` (sel @0x1fb9101a0);
- `fsub d0,d9,d0; fcmp d8,d0; b.pl …` — если `contentOffset.y <= contentSize.height - bounds.height` (т.е. не достигнут низ):
  - `d1 = contentOffset.y + K` (K = `[0x27ce0ceb8]`, default 10.0), `d0 = contentOffset.x`,
  - `setContentOffset:animated:` (sel @0x1face5120, animated=0) @0x21587b76c;
- иначе — `setPaused:` (sel @0x1fb9cdc20, `ldr x20,[x20,#0x18]`; 0x21587b7c4-0x7d8) — остановка display-link.

**Вердикт D:** ключ = `PPTContentOffsetScrollIncrement`, эффективный коэффициент = **10.0 пунктов за кадр** (fallback при 0), на диске 0.0 (runtime-lazy), потребитель — frame-метод display-link таргета, шаг `contentOffset.y += 10.0` (значения runtime; при заданном дефолте — пользовательские).

---

# RETRACTED / CORRECTED

1. **«Sheet-переход Music.app наследует 0.5 c»** — CORRECTED: 0.5 c относится только к noninteractive `UIViewPropertyAnimator` приложения; базовая длительность `_UISheetAnimationController.transitionDuration:` = **0.4 c** (`+[UITransitionView defaultDurationForTransition:8]`), `PalettePresentationAnimationController` не переопределяет `transitionDuration:` (полный список его методов: init/ interruptibleAnimatorForTransition:/animationEnded:/.cxx_destruct).
2. **«`Backdrop.CompositeRenderer.crossfadeDuration` NOT FOUND»** — CORRECTED: значение = **0.8f** (`0x3F4CCCCD`) в `init` @0x1c4d062ac; поле resilient (доступ через field-offset 0x1ec31d430=0x34), поэтому прямых `#0x34`-обращений в __TEXT нет.
3. **«#36 точный ключ UNRESOLVED»** — CORRECTED: ключ **`PPTContentOffsetScrollIncrement`** (Swift-литерал 0x215a85850; countAndFlags 0xD00000000000001F).
4. `08_closure/P3_ANIMATIONS_CLOSURE.md` §36 «tagged-константа 0xD00000000000001F не декодирована» — устранено (это count+флаги Swift-строки, а не отдельный ключ).

# NOT FOUND (итог)

- Enum-значение аргумента `8` `+[UITransitionView defaultDurationForTransition:]`.
- Модификатор условного множителя 0.73 в `MorphingMotionArtwork` (вероятно scale/opacity) и условие его выбора.
- Числовые значения `MusicCoreUI.Corner` для morphing artwork (выводятся из геометрии).
- Пользовательские рантайм-значения preference ключей `SheetDampingRatio`/`SheetResponse`/`SheetHighSpeedDampingRatio` и `PPTContentOffsetScrollIncrement`.

# Артефакты и методика

- Task A: `bl`-скан __TEXT UIKitCore (677 823 инстр.), symbol atlas (0x1894a2938/978/9cc, 0x189cd6400/410, 0x18a251b70, 0x1891a4cf4 и др.), чтение double-таблицы 0x18a539c68.
- Task B: `/tmp/opencode/MediaCoreUI.macho` — Swift metadata (fieldmd 0x1c4e5e3e8, metadata 0x1edc56538, ivar list 0x1f475eea0), disasm init 0x1c4d06210 и consumer 0x1c4d077a8; Music.app — `FUN_1004282fc/FUN_1004281c8/FUN_100427d10`, thunks 0x100da74b0/c0.
- Task C: `musicapp_ghidra` — 0x1005e2610/0x1005e08ec/0x1005cfc00/0x1005e0cd4/0x10017acfc.
- Task D: llvm-objdump-19 по извлечённому MusicUI (0x21587b5c0/5e0/6ac, __cstring 0x215a85850), свежий DSC .34 для селекторов.
