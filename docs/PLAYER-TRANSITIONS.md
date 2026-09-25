# Player transitions and retained lyrics — 2026-09-23

## Scope

User request: compare Apple Music Android Song ↔ Lyrics/Queue transitions and track changes; prevent stale previous-track layers and repeated multi-second lyrics loading. The motion pipeline and backdrop were already approved and must be preserved.

## Verified Apple reference

Local reference `/root/applemusic_6.5.2_jadx/`:

- `sources/com/apple/android/music/player/fragment/t0.java`, `D1` at line 1106: fragment fade 500 ms, curve `E.f42328i`; shared-element transition configured separately. `F1` guards overlapping fragment transactions.
- `sources/com/apple/android/music/utils/E.java:93`: `(0.25, 0.1, 0.25, 1)`.
- `resources/res/layout/fragment_player_main.xml:36`: motion ViewSwitcher with two containers and platform fade animations, below `player_fragments_host`.
- `PlayerSongViewFragment.h2`, ready callback and `fragment/S0.java`: preview/ready coordination, one-shot switch completion, cancellation of existing animator, separate card/motion fades.
- `PlayerLyricsViewFragment.onCreate` (2049–2063): Activity-scoped PlayerLyricsViewModel. Current lyrics Adam ID checks (1368, 2024) avoid loading the same item's lyrics again.
- `PlayerLyricsViewModel`: parsed SongInfo held in MutableLiveData, coroutine owned by ViewModel, cleared in onCleared.

These are Android findings, not inferred iOS internals. LMG's Compose implementation adapts the behavior; it does not copy Apple's fragment/shared-element implementation byte-for-byte.

## Changes

- FullPlayer owns one interruptible Song/Lyrics/Queue transition for overlay opacity. Lyrics, Queue, static backdrop and controls use 500 ms with the verified curve. Removed the scaled lyrics-sheet entrance and the unrelated full-sized Queue cover morph in the shared FullPlayer host. Standalone Queue retains its geometry animation.
- Queue header prioritizes the already resolved current artwork passed by FullPlayer.
- Outgoing motion retains its own presentation mode throughout fade, including when the next lookup is temporarily empty or a non-motion/square track replaces tall motion. The current static cover remains visible until the incoming motion submits its first output frame.
- A hidden static background completes the existing renderer's one-second artwork blend before sleeping; a new hidden cover is warmed without needing a sheet to open. This prevents an unfinished previous-track blend from resuming when Lyrics/Queue reopens. The renderer's colors, blur, geometry and blend math are unchanged.
- `LoadedLyricsStore` retains parsed lyrics in a bounded process-session cache (8 entries, 200k text weight). Keys include track, title/artist, duration, embedded text, provider selection and locale. First opening reads cache synchronously; warm opening skips providers and TTML conversion.
- Both loading and measured-layout preparation use `RetainedLyricsCache`: shared in-flight work survives a sheet closing; reopening joins it. Layout cache retains its existing 2-entry/16000-weight budget and stale-font validation. At most two jobs per cache execute concurrently; pending jobs are bounded by entry capacity, with a 60-second work timeout. No polling or permanent hidden lyrics UI was introduced.
- Provider selection is snapshotted through LyricsRepository and LyricsParser, so a late job cannot be stored under a different provider selection. Invalidated/cancelled results are rejected, failures/empty provider responses are retryable. Existing provider disk caches remain.
- `lyricsData=cacheHit/cacheMiss` and existing `lyricsLayouts=cacheHit/cacheMiss` diagnostics distinguish the two stages.

## Preserved

MotionArtworkPlayback, MotionArtworkRenderer, MotionBackdropLayout, MotionArtworkFrameDelivery, MotionArtworkLifetime and ArtworkBackgroundRenderer match the approved baseline by SHA256. No UI frame acknowledgement gate is reintroduced. Opening Lyrics, Queue or AirSheet and pausing audio does not change motion play intent. Ordinary artwork/triple blur remains on Lyrics/Queue; AirSheet motion thumbnail remains.

## Validation and limits

Regression tests cover closing/reopening during a load, synchronous warm access, late previous-track result, error/empty retry, explicit invalidation, LRU/weight limits, font invalidation, cache identity, and hidden background settlement after quick track/sheet changes. Existing artwork/player/lyrics suites are included in final Gradle validation.

No emulator or phone testing was performed. JVM/build checks do not prove visual GPU behavior on HONOR. Cold first load still depends on the provider/network; cache entries can be evicted by memory limits or invalidated by changed song metadata/settings. Repeated opening of an already loaded current track uses retained data/layout, while the screen itself keeps its intentional fade.

Artifacts: `/root/LMG-previews/player-transitions-2026-09-23/` (baseline copies, focused patch, hashes, Apple review, build/test output, APK signature and Drive verification). Final results and APK link are appended after verification.


## Verified delivery

`assembleDebug` and 170 targeted JVM tests passed (0 failures/errors/skipped); log `build-verified.log`. Frozen files and MotionArtworkContent visual body match the approved baseline. Final DEX contains LoadedLyricsStore, RetainedLyricsCache, ArtworkBackgroundFrameSchedule and MotionArtworkFrameDelivery, with no MotionArtworkFrameGate. Debug signer SHA256 remains `41c93323375b003e53fc016b5c438ef03dc960af96269eb89d0b2ce03628dad0`.

[APK player-transitions](https://drive.google.com/open?id=17FpWSLsZapinbDCcqvWN7noZj8BpzJqg) — 192824943 bytes. SHA256 `f45a2a4b7c9276478ca0f9320b49ab3c6c9df9735d2634543477526d640e3bd6`, MD5 `81667bfb6f39144c95b3de8c1cb4a2a6`. Google Drive size/MD5 verified against local file. Device visual confirmation remains outstanding.


## 2026-09-23 — исправление после отклонённого перехода

Пользователь отклонил APK player-transitions: fade окон не воспроизводит поведение motion при Song ↔ Lyrics/Queue. Предыдущий разбор был неполным: fade500ms относится к содержимому фрагмента, а геометрией motion управляет отдельный shared-element transition.

Проверенная цепочка: RunnableC3408r0/RunnableC3410s0 → t0.u1 (строка1026) → u0.onAnimationUpdate/v0. t0.u1 берёт текущие x/y/width/height/alpha motion_switcher. При переходе в Lyrics/Queue цель — координаты thumbnail guidelines, квадрат player_thumbnail_height60dp и alpha0; назад — origin0,0, полноэкранные размеры и alpha1 для tall motion. u0 интерполирует одновременно положение, размеры, прозрачность и скругление. p032b9.f задаёт300ms и E.f42327h = (.2,.06,0,1). Отдельный fade фрагмента остаётся500ms.

Реализация LMG: motion действительно сжимается к реальной миниатюре Lyrics/Queue (onGloballyPositioned + localBoundingBoxOf), скругляется и затухает; назад разворачивается. Используются реальные48/54dp миниатюры приложения вместо изменения layout под Apple60dp. Слой статичного фона находится под движущимся контейнером, чтобы его fade не скрывал движение. На GPU применяется единый масштаб по ширине с изменением области clipping: видео сохраняет пропорции, а TextureView/EGL buffers не перевыделяются каждый кадр. Входящий native output имеет минимальную альфу0.001, чтобы первая отрисовка не была подавлена во время загрузки под открытым окном. Это адаптация геометрии Apple, не обещание побитового соответствия её ViewGroup relayout.

MotionPlayback, renderer/shaders, approved backdrop geometry, frame delivery, lifetime и кэш лирики не менялись (проверка SHA256). Не создаётся второй decoder или screenshot для анимации. Полноэкранное состояние имеет исходные размеры, scale1, без скругления и с alpha1. Экранные static artwork/triple blur и AirSheet остаются прежними по назначению.

Артефакты: `/root/LMG-previews/motion-sheet-morph-2026-09-23/` — точные ссылки на декомпил в apple-reference.md, focused patch, hashes, тесты/сборка и проверка доставки. Без устройства/эмулятора; визуальное поведение на HONOR ещё не подтверждено.


Проверка motion-sheet-morph: assembleDebug +177JVMtests успешны,0 failures/errors/skipped. SHA256 замороженных playback/renderer/background/cache файлов совпали; тело MotionArtworkContent совпало побайтно. В DEX есть MotionSheetGeometryKt/MotionSheetClip, прежний FrameDelivery, отсутствует FrameGate. Подпись debug прежняя.

[APK motion-sheet-morph](https://drive.google.com/open?id=17wt50O1JwKUMk8WzSRd39YWkhsASoCsB); 192824943 bytes; SHA256 `01042f004e0cd2b55bef8f9655569b7e94e542a3861f22b1514efbd08924ae5b`; MD5 `b990f486125fdc4a17b8046322a898c4`. Размер/MD5 Google Drive проверены. От пользователя ещё требуется визуальная проверка HONOR; её результаты здесь не предполагаются.
