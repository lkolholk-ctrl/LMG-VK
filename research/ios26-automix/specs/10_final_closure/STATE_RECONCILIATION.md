# STATE_RECONCILIATION.md — синхронизация состояния после нахождения полного Music.app

**Дата:** 2026-09-13. Приоритет: red-team findings и raw evidence выше старого текста.
Все старые утверждения о недоступности Music.app считаются RETRACTED.

| Old claim | New evidence | Corrected status | Reports requiring update |
|---|---|---|---|
| «Music.app full binary отсутствует в IPSW; нужен AppVolume/устройство» | `090-89642-625.dmg` (OS volume) содержит `private/var/staged_system_apps/Music.app/Music` (20,278,608 B; sha256 `3f8162e7…458b`; com.apple.Music 4025.110.6) и `Music.app/Frameworks/MusicApplication.framework/MusicApplication` (17,728,512 B; sha256 `3249ce9a…0e62`); файл извлечён | **RETRACTED → FOUND/CLOSED** | APPOS_CLOSURE_MATRIX (audit append), APPOS_MUSIC_APP, APPOS_MUSIC_APP_DECOMP, APPOS_MASTER_SUMMARY |
| «`Ap,SystemVolumeCanonicalMetadata` mtree — полный канонический листинг OS volume» | mtree не содержит `private/var/staged_system_apps`; полный листинг — `7z l` (232,692 файла, 221,478 каталогов) | **RETRACTED (partial listing)**; инвентарь вести по 7z + mtree | APPOS_MANIFEST, APPOS_TARGET_INVENTORY, APPOS_PROVENANCE |
| «AppOS = 851 файл / 150 каталогов» | 7z summary: 1099 файлов / 150 каталогов, ~11.88 MB данных | Corrected (1099/150) | APPOS_PROVENANCE, APPOS_OVERVIEW |
| «#34 app transitions: BLOCKED (нет app binary)» | Music.app/MusicApplication доступны; PalettePresentationAnimationController и marquee декомпилированы (0.5s cubic + MPCubicSpringAnimator m3 k500 c1000; marquee 3.0 s / 30 pt/s) | **#34 CLOSED (app), #38 CLOSED** | P1_BLOCKED_CLOSURE, APPOS_ANIMATIONS, CLOSURE_TRACKER, UNRESOLVED_QUESTIONS |
| «#24 MusicCoreUI/LyricsX отсутствуют (нужен App Cryptex)» | Модули в `MusicEngagementExtension.appex` (183 функции Ghidra); `MusicApplication.framework` статически линкует MusicCoreUI (1733 симв.) и LyricsX (736) | **CLOSED** | P1_BLOCKED_CLOSURE, 03_lyrics/*, APPOS_LYRICSX/MUSICCOREUI |
| «aux bus shared между треками» (гипотеза порта) | Не подтверждено; проверяется в Phase 3 (per-track instances) | Pending (Phase 3 DSP_RUNTIME_ARCHITECTURE) | P1_PORT_CLOSURE, 06_android_port |
| «equal-power = cos/sin(π/2·t)» | REJECTED ранее; TransitionStyles содержит constant-power стили и easing-кривые; формулы подтверждаются в Phase 4 | REJECTED (повторно проверяется Phase 4) | 02_dsp/DSP_*, AUTOMIX_* |
| «plusL — luminance-add» | P1 closure: `out.rgb=in.rgb+(r,g,b)*a`, `out.a=in.a+a` (QuartzCore disasm) | EXACT (уже исправлено) | GLASS_*, 07_audit |
| «instrumental threshold 7000 ms» | нет подтверждения; измеренные разрывы 2.24–2.68 s; LyricsX механика декомпилирована | NOT FOUND / rejected | 03_lyrics/INSTRUMENTAL_BREAK |
| «константы 100.0 / 0.95 как EXACT» | аудит: paragraphSpacing 39.0 (float), lineChange 100.0 (float), touchDown 0.95 не подтверждён как double | **не hardcode без пометки** | Phase 8 LYRICS_RENDERER spec |
| «42,334 Swift-символа в extension» | фактически 42,202 (Swift 6.0.3 demangler) | Corrected | APPOS_LYRICSX |
| «mtree = 226,646 записей (полнота)» | парсер дал 226,646 структурированных записей (вариант), текст 436,723 строк; mtree частичный | Corrected/уточнено | APPOS_PROVENANCE |
| «#49 CLOSED vs PARTIAL конфликт» | TransitionStyles §6 = CLOSED (durations/offsets), трекер = PARTIAL | **PARTIAL (runtime entryOffsetUs не доказаны)** | APPOS_TRANSITION_STYLES §6, UNRESOLVED_QUESTIONS |
| «musicapp_ghidra пуст» (промежуточная заметка auditor) | фактически 689 MB: 65,030 pseudocode, 65,034 disasm, function_index 65,035 | Corrected | APPOS_MASTER_SUMMARY §14 |
| «MusicUIService/AdaptiveMusicApp извлечены корректно» | первые копии были усечены (90,736/278,560); переизвлечено (117,520/300,160) | Fixed | APPOS_RED_TEAM_AUDIT (R4) |

## Действующие правила фазы 10_final_closure

1. Источник истины: извлечённые ресурсы (`appos/extracted/**`), `musicapp_ghidra/**`, raw JSON/plist, DSC binaries.
2. Каждое утверждение — с путём/адресом/символом; статусы EXACT/STRONG_INFERENCE/PARTIAL/UNKNOWN.
3. Не «достраивать» inference там, где нет xref.
4. Runtime-only значения не объявлять константами.
5. Противоречия решаются в пользу red-team/raw evidence.
6. Секреты (FCS-ключи, accessKey, signed URL) в отчёты не попадают.
