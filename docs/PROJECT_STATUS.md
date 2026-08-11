# LMG VK — Документация по состоянию проекта

> **Проект:** полное восстановление музыкального клиента VK X v8.12.1 (ПО `ua.itaysonlab.vkx`) из бинарного APK + порт UI из LiquidMusicGlass → собственное приложение **LMG VK** (`com.lmg.vk`).
> **Репозиторий:** https://github.com/lkolholk-ctrl/LMG-VK
> **Дата актуального среза:** 2026-08-11

---

# ЧАСТЬ 0. ОБЯЗАТЕЛЬНЫЙ АКТУАЛЬНЫЙ HANDOFF

> Эта часть — источник истины для следующего агента. Разделы 1–3 ниже содержат
> полезную историю восстановления, но отдельные формулировки и TODO в них
> устарели. Не начинать работу по старому backlog без сверки с этой частью и
> текущим кодом.

## 0.1. Жёсткие правила работы владельца проекта

- **Запрещено запускать Gradle, сборку, компиляцию и другие тяжёлые команды в
  Termux.** Не запускать `./gradlew`, `gradle`, `assemble`, `compile`, `test`,
  `lint`, `ktlint` или аналогичные задачи без новой прямой команды владельца.
- Телефон заметно зависает от компиляции. Допустимы только лёгкие точечные
  проверки: `rg`, `sed` небольших диапазонов, `git diff --check`, подсчёт ссылок
  и парности скобок.
- Для поиска использовать `rg`/`rg --files`. **Не использовать `grep -R` или
  `grep -r`.** Это уже неоднократно перегружало Termux.
- Источник истины для серверного контракта — официальный клиент VK. Логику
  экранов и группировку настроек можно сверять с VK X, но визуальный стиль у
  LMG VK собственный.
- Без отдельной просьбы не менять собственную цепочку получения аудио,
  `audio_rip` и её fallback-логику.

## 0.2. Текущий технический срез

- В дереве **301 Kotlin-файл**. Приложение давно имеет ресурсы, `MainActivity`,
  рабочую авторизацию, навигацию и живой VK-бэкенд. Старые пункты ниже о том, что
  это отсутствует, являются историческими и уже не должны выполняться.
- Последний зафиксированный базовый коммит перед текущим срезом: `f2616f2`
  (`feat(ui): apply LMG glyph pack and rectangular heroes`). Текущий срез
  заменяет добавленные тогда системные Hero-блоки компактными шапками.
- Последняя известная сборка до текущего UI-рефакторинга была зелёной. **Текущие
  изменения намеренно не компилировались** из-за запрета выше; выполнены только
  статические проверки.
- `git diff --check` для текущего изменения чистый; ссылки на удалённый Hero и
  шесть его изображений отсутствуют.

## 0.3. Настройки после группировки в стиле VK X

Публичный `SettingsScreen` находится в
`app/src/main/kotlin/com/lmg/vk/ui/GroupedSettingsScreen.kt`.

Корневой экран больше не является длинной простынёй параметров. Он показывает
профиль и пять смысловых категорий:

1. **VK and profile** — профиль, трансляция трека в статус, онбординг
   рекомендаций.
2. **Playback** — исключение из оптимизации батареи, таймер сна, crossfade.
3. **Network** — прокси/обход блокировок и обновление адресов/сертификатов.
4. **Themes and interface** — Light/Dark/Auto, Increase Contrast, выбор
   launcher-иконки. Auto использует Dynamic Color из системной палитры на
   Android 12+, а на Android 10–11 следует системной светлой/тёмной теме.
5. **Diagnostics** — экран отладочного лога воспроизведения.

Каждая категория открывается как отдельная внутренняя страница. Кнопка шапки и
системный Back возвращают сначала в корень настроек; на корневом экране
сохраняется прежняя логика `showBack`. Пять быстрых тапов по заголовку Settings
по-прежнему переключают скрытый debug-флаг.

Переиспользуемые примитивы строк/карточек оставлены в старом
`ui/SettingsScreen.kt`, но прежняя монолитная реализация экрана из него удалена.

## 0.4. Системные Hero удалены

- Удалён `ui/components/SectionHero.kt`.
- Удалены шесть сгенерированных ресурсов:
  `hero_library.webp`, `hero_playlists.webp`, `hero_history.webp`,
  `hero_downloads.webp`, `hero_settings.webp`, `hero_music.webp`.
- Library, Playlists, Recent, My tracks, Downloads, History, Local Library и
  Settings используют компактный
  `app/src/main/kotlin/com/lmg/vk/ui/components/SectionTopBar.kt`.
- Фото/видео-шапки **артистов, альбомов, плейлистов и профиля не удалялись**:
  речь шла только о шести локально сгенерированных системных обложках.

## 0.5. Пакет VK-глифов

- First wave: **74 XML-глифа** + исторический `ui/icons/LmgGlyphs.kt` (path-порты
  `ImageVector` для совместимости со старыми call site).
- Second wave (2026-08-11): **+204 XML** из
  `LMG-VK_LMG-glyphs_8.185_second-wave.zip` → `res/drawable/lmg_*.xml`.
  Итого **278** `lmg_*.xml`. Пересечений имён с first wave нет.
- **XML — источник истины.** Second wave **не** дублируется path-данными в
  Kotlin. Доступ: `ui/icons/LmgDrawables.kt` (`R.drawable.lmg_*`) и
  `@Composable fun lmgVector(id)` → `ImageVector.vectorResource`.
- Семантические подстановки second wave (вместо Material / LiquidGlyphs.Star):
  - Bottom/Side **New** → `StarsOutline28`
  - Settings categories: Playback→`SoundWaveOutline28`, Network→`GlobeOutline28`,
    Appearance→`PaletteOutline28`, Diagnostics→`BugOutline28`
  - Verified → `CheckShieldOutline28`; Check/selected → `CheckDoubleOutline16` /
    `CheckCircleOn28`
  - Aura dislike/undo → `UnfavoriteOutline28` / `ArrowUturnLeftOutline28`
  - Sign out → `DoorArrowLeftOutline28`; queue drag → `Reorder24`
  - Fullscreen enter/exit → `ArrowUpDownCornersOutline24`
  - Move up/down → `ArrowUpOutline24` / `ChevronDown24`
  - Profile location → `PlaceOutline28`
- Пока без честной функции (не рисовать фиктивные действия): first-wave
  `ExternalLinkOutline24`, `MenuOutline28`, `QrCodeOutline28` и большая часть
  second-wave XML, ещё не подключённая к UI.
- `material-icons-extended` / `LiquidGlyphs` **ещё не удалять**: остаются call
  site вне этого батча (Library/Artist/Album detail, lyrics tools и т.п.).

## 0.6. Критичный Moshi-фикс от 2026-08-11

VK начал присылать `response.playlists[*].restriction.icons[*]` не только
строками, но и JSON-объектами. Старый `List<String>` ронял весь экран с ошибкой:

```text
JsonDataException: Expected a string but was BEGIN_OBJECT at path
$.response.playlists[16].restriction.icons[0]
```

В `network/dto/music/MusicMisc.kt` поле
`MusicDynamicRestriction.icons` изменено на `List<Any?>?`. UI его не читает, а
такой же динамический Moshi-тип уже применяется в других DTO проекта. Это
намеренная совместимость обоих wire-форматов; **не возвращать `List<String>`**.

## 0.7. Недавний функциональный baseline

- VK Mix/Aura реализованы серией коммитов `24cc980`…`cf610c6`: персональная
  сессия, настройки официального формата, dislike/undo, продолжение очереди.
- `77ff67a` устранил двойное добавление лайка в My Audio и сохранил корректную
  cloud identity для воспроизведения оставшейся записи.
- `f2616f2` импортировал shortlist глифов; текущий срез сохраняет пакет, но
  удаляет неудачные системные Hero.
- Не путать `SectionTopBar` системных экранов с полноразмерными шапками
  каталожных сущностей. У них разные роли и их нельзя снова сводить в один Hero.

## 0.8. Library и My Tracks после компактного UI-рефакторинга

- `LibraryScreen` сохраняет прежние ViewModel, VK/Room sync, поиск, маршруты и
  playback-команды, но главная Library теперь сразу показывает контент:
  компактные Search/Sync, сетку Playlists/Albums/Artists/Downloads, Recent и
  preview My tracks с переходами `See all`.
- Внутренний экран My Tracks сохраняет Sync tracks, Search, сортировку, Play
  all, Shuffle, меню трека и нижний inset `178.dp`; эти действия собраны в
  компактную панель перед плотным списком без карточки вокруг каждого трека.
- Текущий трек выделяется небольшим accent-контуром/цветом заголовка, скачанный
  — маленьким badge на обложке, unavailable остаётся приглушённым. Dynamic Color
  используется только для малых accent-state; крупные поверхности нейтральны.
- Для маленьких строк `AlbumArtImage` принимает опциональный размер LMG
  placeholder-глифа, чтобы 24dp-глиф не растягивался почти до размера обложки.

---

# ЧАСТЬ 1. ЧТО СДЕЛАНО

## 1.1. Взлом нативного слоя (libvkx.so) — ✅ ЗАВЕРШЕНО

Оригинальная `lib/arm64-v8a/libvkx.so` (1.46 МБ, NDK r21e, stripped) защищена **Obfuscator-LLVM / O-MVLL**:
- Control-Flow Flattening (все функции «расплющены» в switch-автоматы)
- Двухслойное шифрование строк (21 экспортируемая `.datadiv_decode*`-функция + runtime key-layer, зависящий от самопроверки целостности)
- Анти-Xposed и анти-тamper логика в `JNI_OnLoad`

### Метод взлома
Построен эмулятор на **Unicorn Engine** (`/root/vkx_tools/vkx_emu_onload.py`):
- фейковые JavaVM/JNIEnv-витрины через BRK-сентинели (каждый JNI-вызов перехватывается и логируется)
- полные релокации ELF (RELATIVE/ABS64/GLOB_DAT), PLT-шимы libc
- виртуальная ФС с реальным APK (для прохождения самопроверки)
- выполнение реальных `DT_INIT_ARRAY` инициализаторов → расшифровка строк в правильном порядке

Это обошло OLLVM-флаттенинг **без ручного дефлаттенинга**: код просто исполняется.

### Извлечённые результаты

**Карта JNI** (из `RegisterNatives` в `JNI_OnLoad @ 0xB8F64`, таблица на стеке):

| Kotlin (obf) | Адрес | Восстановленное имя |
|---|---|---|
| `x00()` | `0xB79A0` | `getVkApiData()` |
| `x01()` | `0xB7C1C` | `getLmgEnvironment()` |
| `x02(String)` | `0xB8170` | `getSilentAuthorizationEnvironment()` |

**Алгоритм x02** (подтверждён дифференциальным анализом на 3 входах, побайтно):
```
result = base64_std( input[i] XOR "THETRUTHLIES"[i % 12] )
```
Ключ в `.rodata @ 0x131535`. Применение: код из `apps.get(app_id=51931326)` → обёртка → параметр `"code"` silent-авторизации VK.

**Извлечённые секреты и конфиги:**
- Эндпоинты: `https://api.vk.ru/`, `https://oauth.vk.ru/` + прокси `vk-api-proxy.xtrafrancyz.net`, `vk-oauth-proxy.xtrafrancyz.net`
- Креды официальных клиентов VK (base64): Android `2274003` / `hHbZxrka2uZ6jB1inYsH`; iOS `3140623` / `VeWdmVclDCtn6ihuP1nt`
- UA-шаблоны `VKAndroidApp/{8.70, 7.1, 8.165.1}` (суффикс собирается из `android/os/Build`)
- Бэкенд VK X: `ui.lmg.app`/`api.lmg.app` (бывш. vkx.app), токен, JWT-заготовка `eyJhbGciOiAibm9uZSJ9...`, лицензионный blob, **ECDSA P-256 публичный ключ** (X.509 SPKI hex)

**Защитные механизмы (задокументированы):**
- Anti-Xposed: рефлексией `XposedBridge.disableHooks = TRUE`
- Самопроверка подписи и целостности APK удалена: восстановленные сборки подписываются ключом владельца проекта.

### Артефакты
- `app/src/main/cpp/lmg_native.cpp` — чистый C++17 (компилируется и линкуется NDK r29 → `liblmg.so`, проверено toolchain `aarch64-linux-android-clang++`)
- `app/src/main/cpp/CMakeLists.txt` — NDK-модуль `lmg`
- `app/src/main/kotlin/com/lmg/vk/jni/LmgNative.kt` — JNI-мост
- Отчёт: `/sdcard/Download/VKX_Fresh/VKX_NATIVE_RECOVERY_v8.12.1.md`

> ⚠️ **Фикс попутно:** в C++ `RegisterNatives` регистрирует **реальные** имена методов (`getVkApiData` и т.д.), консистентно с Kotlin — обфускация `x00/x01/x02` не нужна в своём коде.

---

## 1.2. Сетевое ядро (vkapi2) — ✅ ЗАВЕРШЕНО

Пакет `com.lmg.vk.network` — деобфускация из `defpackage.C*` (словарь ad-sdk) по Kotlin Metadata + Moshi-ключам.

### Карта деобфускации (ядро)

| Obf | Восстановлено | Роль |
|---|---|---|
| `C8221e` | `VkApiClient` | ядро: execute/rawCall, токены, ретраи |
| `C5577e` | `VkMethod` | обёртка метода (name, v="5.272", params) |
| `C18479e` | `VkAuthSession` | точный `VkAccount`: id, access/exchange token, сроки, профиль |
| `C18301e` | `VkAuthApi.refreshTokens()` | протокол auth.refreshTokens |
| `C9022e`/`C7220e` | `VkResult` | sealed Success/Error |
| `InterfaceC11962e` | `VkResponseParser` | парсер конверта (Moshi) |
| `C5577e`+`C4271e`... | `methods/` | обёртки эндпоинтов |
| `AbstractC4533e` | `VkLocales` | uk→ua, kk→kz, whitelist, else en |
| `AbstractC1831e` | `VkApiLocator` | синглтон-доступ |

### Ключевые факты о стеке
- **Стек — Ktor Client** (не OkHttp): `POST https://api.<domain>/method|oauth/<name>`
- Body: `params + v=5.272 + https=1 + api_id=2274003 + lang + device_id`
- Headers: `X-VK-Android-Client: new`, `X-Screen: nowhere`, `Authorization: Bearer`
- Резолв токена: params → `auth.getExchangeToken` (exchangeToken) → обычный метод (`getValidToken()` под `Mutex` + double-check)
- **Ретраи ошибок VK**: `14` captcha → handler → retry; `17` validation → redirect → retry; `1117` token expired → `auth.refreshTokens` → один retry
- **Refresh-протокол**: `client_secret` найден прямым текстом в Java (кросс-подтверждение native-слоя побайтно ✓)
- **Silent auth**: `apps.get(app_id=51931326)` → код → `LmgNative.getSilentAuthorizationEnvironment` → `"code"`

### Методы (реестр 50+ эндпоинтов, `methods/VkMethodsRegistry.kt`)
`audio.*` (get, search, getPlaylists, getPlaylistById, add/delete/restore, addDislike/removeDislike, getAudioIdsBySource, getAudioPreviewUrl, getRelatedArtists, getStreamMixAudios/getStreamMixSettings, reorderInPlaylist, followRadioStation/unfollow, searchArtists, searchMain), `audioBooks.*`, `podcasts.subscribe/unfollow`, `users.get`, `utils.resolveScreenName`, `storage.get/set`, `stats.trackEvents`, `musicStatResults.*`, `studio.getArtistYearRecapData`, полный **auth-флоу** (`validateAccount`, `processAuthCode(Multi)`, `ecosystem.*` OTP, `get_anonym_token`, OAuth `token` grant_type=password).

Priority 2 добавил точный ответ `audio.searchMain` с 7 секциями
(`albums/audios/artists/playlists/own_*`) и полный 43-key контракт
`AudioPlaylistDto` из C9885e/C1471e. Число 41 в отчёте P2 было неточным:
дескриптор C1471e явно объявляет 43 ключа. Базовый StreamMix из VK X совпал с
DTO Priority 1, но официальный VK 8.185 добавил nullable `multi_select`,
`selected` и `icon_badge`; актуальная wire-модель учитывает эту разницу. Старое
`ua.lmg...AudioPlaylist` сохранено отдельным классом.

Priority 3 добавил подтверждённые R8-merged фрагменты:
- выбор API-хоста по `api.vk.com/ping.txt` → `api.vk.ru/ping.txt` с исходным
  enum `VK_COM_WORKS/VK_RU_WORKS/NOTHING_WORKS`;
- точные запросы `auth.getExchangeToken` (v5.180), `auth.validatePhone`,
  `auth.validateAccount`, `get_anonym_token`, `ecosystem.sendOtp*` и два
  варианта `users.get(fields=photo_100)`;
- wire DTO `AnonymTokenResponse(token, expired_at)`,
  `EcosystemSendOtpResponse(status, sid, code_length, info)`,
  `BaseResult(result="0"|"1")`; в `ValidatePhoneResponse` возвращён десятый
  ключ `phone_mask`;
- новый типизированный контракт `audio.restore -> AudioAudioDto` оставлен
  рядом со старым Unit-контрактом, не заменяя семейство `ua.lmg.vkapi2`.

В отчёте P3 встречаются ошибочные смысловые подписи из-за горизонтального
слияния R8. Источником истины при переносе считаются сериализатор и JSON
descriptor: например, C7862e — `AnonymTokenResponseDto(token, expired_at)`,
а C18422e — 39-польный `AudioAudioDto`.

Priority 4 восстановил типизированный auth/OTP-слой:
- `AuthGetExchangeTokenResponse` со списком пользовательских common/tier
  exchange-токенов оставлен рядом с отдельным ответом `C7862e`;
- `AuthValidateAccountResponse`, `AuthProcessAuthCodeResponse`,
  `AuthGetAuthCodeStatusResponse`, `EcosystemCheckOtpResponse` и
  `EcosystemGetVerificationMethodsResponse` подключены к реальным методам;
- OAuth `token` разбирается в шесть подтверждённых sealed-веток
  `RequestTokenResponse`: success, client error, 2FA, nested VK error,
  captcha и unknown error;
- параметры OAuth-флоу дополнены точными `flow_type=tg_flow`,
  `sak_version=1.142` и `supported_ways=push,email`.

### Раздельные старые интеграции
- **VK Android OAuth**: client `2274003` и его secret используются только в
  `token`, `get_anonym_token` и `auth.refreshTokens`.
- **UMA service auth**: восстановлен `auth.getCredentialsForService` с отдельными
  package/app_id/app_secret/digest_hash и точным `SilentCredentials` (11 полей).
- **Last.fm**: восстановлены `track.updateNowPlaying` и `track.scrobble`, сортировка
  параметров, MD5-подпись и form-urlencoded POST. Last.fm session key остаётся
  отдельным пользовательским токеном и не попадает в VK-сессию.

### DTO-модель (~50 классов)
- Ручные: `AudioTrack` (29 полей с `fullId="owner_audio"`), `AudioPlaylist`, `AudioAlbum`, `AlbumThumb`, `MainArtist`, `AudioLyrics(+timestamps)`, `Genre`, `RadioStation`, `MusicDynamicRestriction`, `PodcastInfo`, `VKError/VKResponse/VKRequestParameter`
- Автогенератором из Moshi-адаптеров: 35 DTO (`dto/gen/`): AudioBook, NewsfeedItem(16), VKProfile, VKVideo, Podcast, Conversation-семейство, Catalog...

---

## 1.3. Плеер и эквалайзер (vkxreborn/playback) — ✅ ЗАВЕРШЕНО (восстановление)

Пакет `com.lmg.vk.playback`.

### Эквалайзер — это DynamicsProcessing (API 28+)
- UUID `7261676f-6d75-7369-6364-28e2fd3ac39e`, priority 100
- **preEq = postEq** из полос `EqBandConfig(cutoffHz, gainDb)` (пресеты из `vkx_eq_presets.json`)
- **MBC** многополосный компрессор: кастом или 3-полосный дефолт `125/6000/20000 Гц`, ratio 1.1, `bass/trebleGain: 0..100 → 0..8 дБ`
- **Limiter**, **InputGain** (linked/per-channel L/R)
- Плюс `BassBoost` и `EnvironmentalReverb` (по `queryEffects()`)
- Конфиг: `LmgEffectConfig` (+ вложенные), движок `AudioEffectEngine`

### Архитектура воспроизведения
- **Media3 `MediaLibraryService`** (`PlaybackService`) с onBind-роутингом (MediaSession/MediaBrowser/MediaLibrary)
- **ДВА ExoPlayer + две effect-цепочки** → **кроссфейд** (`CrossfadeController`, колбэк `onCrossfadeFinish`)
- MediaSession: PendingIntent, artwork-резолвер, bitmap-loader
- Очередь + `QueueSaveHolder` (Moshi) + периодический сейв позиции (5 сек)
- FGS с API-31 fallback (`IllegalStateException` → post retry)

---

## 1.4. Загрузчик (vkx/downloader) — ✅ ЗАВЕРШЕНО (восстановление)

Пакет `com.lmg.vk.downloader`.

Конвейер `downloadTrack` (восстановлен из 2055-строчного `DownloaderService`):
1. uid трека → Realm `CachedTrack` (`uid == $0`): валидный streamUrl из кэша (не протухает)
2. Имя файла по шаблону `"(album) artist - title (subtitle).mp3"` + опции `[playlist]`/папка артиста (санитизация `\\/:*?"<>|`)
3. Skip существующих
4. `TrackDownloader.download(url, file, progress, cancellation)` — стриминг чанками
5. Запись в Realm (+ embedded thumb)

Сопутствующее: `DownloadNaming`, `DownloadPathResolver`, `CachedLibrary` (Realm: CachedTrack/Lyrics/EmbeddedThumb).

---

## 1.5. Порт UI из LiquidMusicGlass — 🔶 ВЫПОЛНЕНО ЧАСТИЧНО (структурно)

Портировано **149 файлов** из LMG (Compose UI + engine + data) в `com.lmg.vk`, с полной отвязкой от ICM (Apple Music API).

### Что перенесено

| Слой | Файлы | Содержимое |
|---|---|---|
| `ui/theme/` | 7 | AppleEasings, Color, LiquidMetrics, LiquidMotion, Type, Theme |
| `ui/glass/` | 5 | GlassKit, LiquidGlassSurface, AlbumArtImage, AlbumColorExtractor, PressScale, AnimatedListItem, GlassDialog |
| `ui/liquid/` | 5 | DampedDragAnimation, InteractiveHighlight, LiquidSlider, LiquidToggle, DragGestureInspector |
| `ui/player/` | 10 | FullPlayer, MiniPlayer, AnimatedPlayerBackground, AuraBackground, QueueSheet, LyricsSheet, CreditsSheet, LandscapeBottomBar, VolumeObserver, WaveformVisualizer, SystemRoutePicker |
| `ui/screens/` | 17 | Home, Search, Library, LocalLibrary, Album/Artist/Playlist Detail, History, New, Profile, Settings, Stats, TagEdit, AudioFx, Wave(Home), LandscapeHome |
| `ui/lyrics/` | 6 | LyricsScreen, LyricsBackground, LyricsTimeProcessor, LyricShareCard, MarkupPreview, WaitingDots |
| `ui/navigation/` | 4 | NavRoutes, LiquidNavHost, BottomBar, LiquidBottomTab, SideBar |
| `ui/components/` | 4 | DetailScreenParts, LikeBurstHeart, TrackActionsSheet, WrapRow |
| `ui/icons/` `ui/viewmodel/` `ui/` | — | LiquidGlyphs, HomeViewModel/LibraryViewModel/SearchViewModel, AppRoot, DeviceTier, WindowInfo, PerfMonitor, PowerSaveMonitor, EffectsLifecycle |
| `engine/` | ~30 | Track, PlayerController, AudioService, AudioFxController, EndlessPlaybackEngine, LyricsParser, MediaCacheManager, PlaylistDownloadService, automix/, vad/, sync/... |
| `data/local/` `data/cache/` `data/wave/` | ~20 | Room (favorites/history/downloads/library), ImageCache, WaveSessionState/Candidate/Filter... |

### Отвязка от ICM — как сделано
Создан нейтральный фасад **`engine/backend/`**:
- `MusicBackend` — все вызовы бэкенда (стрим/поиск/home/charts/лайки/плейлисты/волна/тексты)
- `MusicAuth` — авторизация/подписка (isPremium, maxQuality, profile...)
- `WaveSignalQueue` — очередь сигналов прослушивания
- `BackendModels.kt` + `wave/WaveModels.kt` — DTO бэкенда без Icm-префиксов (1354+61 строки портировано)
- `BackendException`, `backendUserMessage()`

Все `IcmRepository.*`/`IcmAuthRepository.*`/`WaveSignalQueue.*` заменены седами на фасад. Типы переименованы (`IcmHomeBlock → HomeBlock` и т.д., regex `\bIcm([A-Z]\w*) → \1`).

### Что выпилено при порте
- ❌ Yandex-экраны и секции (YandexMusicScreen, YandexWebLoginDialog, YandexBottomBar, YandexSection/SideStrip, data/yandex/)
- ❌ Playlist-import (data/playlistimport/, ImportServicesSheet, PlaylistImportIndicator)
- ❌ camp (CampSelectorScreen, FeatureAccessManager)
- ❌ ui/crash, ui/debug (заменены лёгким `engine/debug/DebugLog.kt`)
- ❌ UpdateDialog, CoverSigningInterceptor, IcmPasswordSheet, EmailAuthSheet, AuthScreen (LMG)
- ❌ LrcPublishScreen

### Финальная числовая сводка порта
- приложены type-rename правила к 19 файлам
- зачищены AppRoot, LiquidNavHost, NavRoutes, LibraryScreen (Yandex tile, import dialog, imported sections), ProfileScreen (PasswordSheet → аккаунт-блок)
- ProfileScreen хвост переписан вручную после sed-повреждения
---

# ЧАСТЬ 2. ИСТОРИЧЕСКИЙ BACKLOG (ЧАСТИЧНО УСТАРЕЛ)

> Не выполнять пункты этой части автоматически. В частности, ресурсы,
> `MainActivity`, авторизация и основные экраны уже существуют. Актуальные
> ограничения и ближайший handoff находятся в Части 0.

## 2.1. КРИТИЧНО — блокеры сборки (без этого `./gradlew assembleDebug` упадёт)

### A. Ресурсы не перенесены
UI ссылается на ресурсы LMG, которых нет в проекте:
- **`res/values/`** — только `strings.xml` (app_name). Нет: colors, themes, dimens, стилей
- **`res/drawable*/`** — иконки (`ic_service_*`, плейсхолдеры обложек, глифы)
- **`res/font/`** — кастомные шрифты (в UI используется `AppFontFamily` — ожидается шрифт LMG)
- **`res/mipmap*/`** — иконка приложения (ic_launcher)
- **assets/** — `vkx_eq_presets.json` (для эквалайзера), другие json

**Действие:** скопировать `app/src/main/res/` и `app/src/main/assets/` из `/root/LiquidMusicGlass`, переименовать брендовые строки.

### B. MainActivity отсутствует
Нет точки входа `Activity`. В LMG это `MainActivity.kt` (запускает `ui/AppRoot`, инициализирует engine).
**Действие:** портировать `"MainActivity"` из LMG + зарегистрировать в манифесте с `LAUNCHER`.

### C. Символ-резолверы UI
Часть символов, на которые ссылается UI, могла остаться в нерепортированных/выпиленных файлах LMG:
- `AppFontFamily` (theme/Type.kt — должен быть портирован)
- `onOpenStats`, `onOpenAuth` и т.п. колбэки — проверить сигнатуры экранов
- `LocalArtistDetailScreen` упоминается в NavHost — проверить, что файл на месте
- `musicDetailDestinations(...)` в NavHost — extension, должен быть в navigation/

**Действие:** прогнать `./gradlew assembleDebug` и итеративно добить unresolved references (обычно 10-30 символов).

### D. KSP/Room
`ksp` плагин добавлен, Room подключён. Проверить, что `@Database`/`@Entity` из `data/local/db` компилируются (AppDatabase на месте).

---

## 2.2. Реализация бэкенда (оживление приложения)

### A. `MusicBackend` — Priority 1 подключён к VK API

Готово по подтверждённым контрактам из APK:
- `getTrackInfo/getStreamUrl/getTrackMeta/getBatchTrackMeta` ← `audio.getById` + 8-минутный кэш URL
- `searchTracks` ← `audio.search`; `searchAll` ← `audio.searchMain` (7 секций Priority 2)
- `getLibraryLikes/likeTrack/unlikeTrack` ← `audio.get/add/delete`
- `getUserPlaylists/getUserPlaylistTracks/deleteUserPlaylist` ← методы плейлистов VK
- `loadHomeContent/loadCharts` ← `catalog.getAudioAuto` + `audio.getPopular`
- `getAlbum/getArtist/getArtistTopTracks` ← `catalog.getAudioArtist`, `audio.getAudiosByArtist`, `audio.getRelatedArtistsById`
- `getLyricsResult` ← `audio.getLyrics` с синхронными `begin/end` и plain-text fallback
- Aura запускает персональный VK Mix `common` ← `audio.getStreamMixAudios`
  (порции по 5, `append=false` → `append=true`, полный
  `PlaybackContext.VkMix(VkMixSession)`)
- настройки StreamMix ← `audio.getStreamMixSettings`; официальный JSON
  `options` имеет вид `{ "id": "…", "category_id": ["option_id"] }`, пустые
  категории не отправляются, а один `mixOptionsId` сохраняется на всю очередь
- настройки открываются внутри полноэкранной Aura без карточек на главном;
  поддержаны hidden-категории, `multi_select`, Reset/Apply и состояния
  loading/empty/error/session-expired
- отрицательный отзыв в VK Mix ← `audio.addDislike`; Undo ←
  `audio.removeDislike`, без вымышленных more/less genre действий
- Wave onboarding удалён из текущего приложения; старые методы не подключать обратно без новой явной команды владельца.
- follow/unfollow радиостанций добавлены в `VkAudioApi`
- приложение инициализирует Ktor, `VkApiClient`, `MusicBackend` и AES/GCM-хранилище сессии
- все сетевые `TODO()` в `com.lmg.vk.network` устранены; Moshi codegen подключён через KSP

Осталось: профиль/подписка, полный auth UI, сигналы `stats.trackEvents`, пользовательские
настройки/регион и старые серверные функции импорта/Apple-клипов (у них нет аналога
среди восстановленных VK-ручек).
**Ключевой маппер:** `network.dto.music.AudioTrack → engine.Track`
(id=`fullId`, title, artist, albumName=album?.title, uri=Uri.parse(url), durationMs=duration*1000, coverUrl=album thumb, artists=main_artists, isExplicit, source="vk")

### B. Авторизация VK
`MusicAuth` сейчас всегда `isPremium=true`, `isLoggedIn=false`. Нужно:
- Новый `VkAuthScreen` (замена выпиленной LMG AuthScreen): логин по телефону/коду через `VkMethodsRegistry.validateAccount` → `processAuthCode` → oauth `token`
- Сохранение `VkAuthSession` в `VkSessionStore`
- `MusicAuth.isPremium` ← статус VK X+ / музыкальной подписки VK

### C. Silent auth
Связка уже готова: `apps.get` → `LmgNative.getSilentAuthorizationEnvironment` → `"code"`.

---

## 2.3. Нативный модуль — финализация

- ✅ `lmg_native.cpp` компилируется, JNI_OnLoad экспортирован
- ⚠️ **TODO(backend)**: хосты `ui.lmg.app`/`api.lmg.app` — плейсхолдеры, указать реальные хосты своего бэкенда (см. `lmg_native.cpp:66`)
- ✅ Проверка подписи/целостности APK и старый хекс подписи удалены; переподписанная восстановленная сборка не блокируется.
- ⚠️ Анти-Xposed блок оставлен активным (можно выпилить)
- `x00[7..8]`, `x01[9..11]` — слоты под runtime key-layer в эмуляции не декодированы (нужен прогон на устройстве/Frida)

---

## 2.4. Плеер — состыковка двух реализаций

В проекте **ДВА** плеерных стека:
1. Восстановленный `playback/PlaybackService` + `CrossfadeController` (из VK X)
2. Перенесённый из LMG `engine/AudioService` + `PlayerController` + `PlayerAudioChain` (с JUCE/automix)

**Решение (рекомендация):** использовать **LMG-стек как основной** (он полнее и родной для UI), а из восстановленного взять только **DSP-конфиг** (`LmgEffectConfig` как формат пресетов). Восстановленный `playback/` можно пометить deprecated или удалить после переноса EQ-пресетов.

Задача: решить и оставить один плеерный стек, убрать дублирование.

---

## 2.5. Тестирование и CI

- ❌ Нет unit/UI-тестов в LMG VK (в LMG были: AuthTest, LibraryTest, PlayerFullTest и т.д. — не портились)
- ❌ Нет GitHub Actions CI (сборка APK, ktlint/detekt)
- ❌ Нет проверки на устройстве

---

# ЧАСТЬ 3. КАРТА РЕПОЗИТОРИЯ

```
LMG-VK/  (rootProject "lmg-recovered")
├── settings.gradle.kts          # :app + media3-m2 (форк media3-lmg, 1.5.1-lmg30)
├── build.gradle.kts             # AGP 8.7.3 + Kotlin 2.1.0 + KSP
├── gradle.properties
├── .gitignore                   # + media3-m2 исключён
├── README.md
├── docs/PROJECT_STATUS.md       # ← этот файл
└── app/
    ├── build.gradle.kts         # com.lmg.vk, v1.0.0(1), Compose BOM,
    │                            #   Ktor+Moshi+Realm+Room+media3-lmg, NDK
    └── src/main/
        ├── AndroidManifest.xml  # .LmgApplication, "@string/app_name"=LMG VK
        ├── cpp/                 # lmg_native.cpp + CMakeLists (liblmg.so)
        ├── res/values/strings.xml
        └── kotlin/com/lmg/vk/
            ├── LmgApplication.kt
            ├── jni/LmgNative.kt
            ├── network/         # VkApiClient + methods + dto (+gen)
            ├── playback/        # (восстановленный стек VK X — кандидат на merge)
            ├── downloader/      # загрузки + Realm
            ├── engine/          # LMG: PlayerController, AudioService, backend/
            ├── data/            # local(Room), cache, wave
            ├── debug/DebugLog.kt
            └── ui/              # 149 портированных файлов (Compose)
```

---

# ЧАСТЬ 4. БЕЗОПАСНЫЙ СТАРТ СЛЕДУЮЩЕГО СЕАНСА

Сначала прочитать Часть 0 и проверить только лёгкое состояние репозитория:

```bash
git status --short
git log --oneline -10
rg -n "SectionHero|hero_(library|playlists|history|downloads|settings|music)" app/src/main
rg -n "Icons\\." app/src/main/kotlin/com/lmg/vk/ui --glob '*.kt'
git diff --check
```

**Не запускать Gradle/сборку.** Следующая ожидаемая UI-задача — получить от
владельца дополнительный shortlist VK-глифов и заменить оставшиеся Material/
LiquidGlyphs без механически неверных сопоставлений.

---

> **Примечание по бренду:** все упоминания `itaysonlab`/`vkx` удалены (0 совпадений по дереву), пакеты `com.lmg.vk`, нативный модуль `liblmg.so`, приложение «LMG VK». Прокси `xtrafrancyz.net` оставлены как рабочие эндпоинты VK API.
