# Передача состояния проекта LMG VK

> **2026-09-21 — работу над лагами отложили по просьбе владельца.** Не возобновлять оптимизацию, сбор диагностических логов или тестовые сборки для этой задачи без новой команды. Текущее состояние, неудачные изменения и откаты сохранены в [docs/LYRICS-PERFORMANCE-PAUSED.md](docs/LYRICS-PERFORMANCE-PAUSED.md). Эта договорённость имеет приоритет над дальнейшими старыми планами диагностики в этом файле.

> Дата среза: 2026-08-03. Этот файл — обязательная точка входа для следующего агента. Сведения ниже сверены с веткой `main`, историей Git, текущими Kotlin/C++ файлами и доступными архивами. Старый `docs/PROJECT_STATUS.md` полезен как исторический отчёт, но местами устарел; фактический код и Git имеют приоритет.

# Назначение проекта

Восстанавливается старое Android-приложение владельца **VK MP3 MOD / VK X** под текущим именем **LMG VK** (`com.lmg.vk`). Исходники были потеряны. Поддерживаемый Android-проект восстанавливается из сохранившихся APK, декомпилированного и деобфусцированного кода, XML/resources/assets/drawable/manifest/native-библиотек, сетевых наблюдений и оригинальных материалов.

Цель — постепенно вернуть фактическое поведение оригинального музыкального клиента: авторизацию VK, профиль, поиск, аудио, артистов, релизы, библиотеку, плейлисты, воспроизведение, скачивание, кеширование и настройки. Нельзя заменять восстановление домыслами или переписывать приложение целиком.

# Обязательные правила

Каждый следующий агент обязан:

1. Полностью прочитать `MEMORY.md` до любых изменений.
2. Сначала сверить записи с фактическим кодом, текущей веткой, `git status` и историей Git. При расхождении источником истины являются код и Git.
3. Не переделывать уже работающие функции.
4. Не трогать рабочую авторизацию и профиль без подтверждённой причины и конкретного воспроизводимого лога.
5. Работать небольшими логическими этапами. Текущее пожелание владельца для функциональной разработки: **пять небольших связанных батчей — один коммит**; при конфликте с новой явной командой владельца следовать новой команде.
6. Не делать крупный архитектурный рефакторинг.
7. Не удалять неизвестный или странный после декомпиляции код без анализа его использования и зависимостей.
8. Не хардкодить пользовательские access/refresh tokens, PAT, пароли, cookies и секреты; не выводить их в лог, Git или ответы.
9. Не делать force push, не переписывать историю и не удалять рабочие ветки.
10. Не использовать случайный код из интернета.
11. Не придумывать методы VK API, параметры или DTO. Сначала искать подтверждение в двух архивах, текущем коде либо официальной документации VK.
12. Отделять подтверждённые кодом или поведением факты от предположений.
13. Сохранять собственный Compose UI LMG VK. Из VK X/VK MP3 MOD брать прежде всего внутреннюю логику, данные и поведение; UI переносить только когда владелец отдельно это просит.
14. Навигация артистов, альбомов и плейлистов должна оставаться внутри приложения. Для страниц артистов и сообществ WebView/браузер не нужен.
15. Недоступные треки должны отображаться серыми и быть некликабельными.
16. Не начинать функциональную разработку, пока не понятны исходная реализация, затрагиваемые классы и ручная проверка.
17. Обновлять `MEMORY.md` после каждого законченного этапа: SHA, файлы, подтверждения, ограничения и следующий шаг.
18. **VK-only:** музыкальные данные, ссылки, разрешение потока, поиск, рекомендации и плейлисты не должны зависеть от ICM, `byicloud.online`, Apple Music, Tidal, Spotify, Яндекс Музыки или другого музыкального посредника. Из другого проекта разрешено переносить только UI, если владелец явно не разрешил перенос логики.

Отдельно: ограниченный GitHub PAT ранее передавался владельцем в чат только для пуша. Он не сохранён в репозитории; временный `git-askpass.sh` удалён. Никогда не переносить токен из переписки в файлы или команды с видимым выводом. Если авторизация для пуша недоступна, запросить у владельца безопасный способ, не хардкодить её.

# Разрешённые источники кода и ресурсов

UI, ресурсы и исходную логику необходимо **в первую очередь восстанавливать из перечисленных материалов**, а не придумывать заново.

1. **Текущий репозиторий восстановленного приложения**
   - Корень репозитория в этой сессии: `/workspace/scratch/0b99c7a15a83/LMG-VK`.
   - GitHub: `https://github.com/lkolholk-ctrl/LMG-VK.git`.
   - Главный модуль: `app/`; код: `app/src/main/kotlin/com/lmg/vk/`; native: `app/src/main/cpp/`; ресурсы: `app/src/main/res/` и `app/src/main/assets/`.

2. **Декомпилированный и деобфусцированный VK X**
   - Архив: `/workspace/scratch/0b99c7a15a83/upload/VKLMG_Recovery.zip`.
   - Внутри: `VKLMG_Recovery/src-deobf/` (деобфусцированный Java), `VKLMG_Recovery/VKX-ENDPOINTS.md`, `class-summaries.txt`, `members.txt`, mapping/name-map и инструменты восстановления.
   - Подтверждённые полезные внутренние пути включают `ua_itaysonlab_vkxnative_VKXNative.java`, `ua_itaysonlab_vkapi2_methods_audio_playlist_*`, `ua_itaysonlab_vkapi2_objects_music_*`, catalog blocks/adapters и downloader/cache/playback классы.

3. **Декомпилированный и деобфусцированный VK MP3 MOD**
   - Архив: `/workspace/scratch/0b99c7a15a83/upload/vk_mp3_mod_analysis.zip`.
   - Внутри: `vk_mp3_mod_analysis/jadx_out/`, `VK_MP3_MOD_RECOVERY.md`, `VkAudioDownloader.kt`, `Mp3TagWriter.kt`.
   - `jadx_out/resources/` содержит восстановленный `AndroidManifest.xml`, `res/`, `assets/`, сертификаты и native `.so` оригинальных материалов.

4. **Оригинальные APK как эталон поведения**
   - Оригинальный VK X в исторической документации идентифицирован как VK X v8.12.1 (`ua.itaysonlab.vkx`); VK MP3 MOD — источник `vk_mp3_mod_analysis.zip`.
   - Отдельные `.apk`-файлы в текущем scratch/root **не найдены**. Не придумывать путь. Если требуется запуск или точное сравнение оригинального APK, попросить владельца снова приложить APK.
   - Присланные эталонные скриншоты доступны в `/workspace/scratch/0b99c7a15a83/upload/`: `01-680903.jpg`, `02-680904.jpg`, `03-680905.jpg`, `04-680906.jpg` (артист), `01-680907.jpg` (альбом), `01-680909.jpg` и `01-680911.jpg` (библиотека/плейлисты), `01-680914.jpg` (информация и связанные страницы артиста). Они показывают желаемое поведение, но UI альбомов/плейлистов должен оставаться UI текущего приложения.

5. **XML, assets, drawable, manifest и native-библиотеки оригинальных материалов**
   - VK MP3 MOD: внутри `vk_mp3_mod_analysis.zip` по путям `vk_mp3_mod_analysis/jadx_out/resources/AndroidManifest.xml`, `assets/`, `res/`, `lib/<abi>/*.so`.
   - Текущие восстановленные аналоги: `app/src/main/AndroidManifest.xml`, `app/src/main/res/`, `app/src/main/assets/`, `app/src/main/cpp/lmg_native.cpp` и `CMakeLists.txt`.
   - Дополнительный сетевой материал: `/workspace/scratch/0b99c7a15a83/upload/VKX_Certs.zip` (`config_network_proxy.json`, `config_network_proxy_certs.json`, `vkx_remote_config_raw.json`, headers/request).

6. **Официальный VK API**
   - Использовать, когда надо проверить существование метода, точное имя параметра, формат ответа или актуальное ограничение. Для технического переноса приоритетны фактические вызовы и сериализаторы из оригинальных архивов; официальная документация служит проверкой, а не поводом придумывать неподтверждённый приватный метод.

Дополнительные материалы: `upload/Вставленный текст.txt` содержит правила и исходный статус владельца; `upload/Вставленный текст(1).txt` — Fishnet crash log истёкшей сессии, который привёл к исправлению refresh-логики.

# Что уже сделано

Ниже перечислены фактически видимые этапы текущего чата и непосредственно связанная история Git. Формулировка «проверено» используется только там, где владелец это явно сообщил.

## 1. Полный VK-поиск и недоступные треки

**Коммиты:** `b48d4a1` (`feat(music): restore complete VK search and availability`), `5ba6dd3`, `9ca7583`; связанные более ранние этапы: `ba85de2`, `6485a9d`, `e599777`.

- Реализовано объединение результатов VK для аудио, артистов и альбомов через реальные typed DTO и `MusicBackend.searchAll()`.
- Исправлено сохранение конкретных типов payload при слиянии ответов и нормализация DTO артистов, устранив ошибки Kotlin type inference/unresolved receiver в `MusicBackend.kt`.
- Поиск в UI работает как единый поиск VK; категории Apple/Video/All, отдельный переключатель VK и старые onboarding-категории удалены.
- `isAvailable` протянут через `AudioTrack`, backend-модели, `Track` и экраны. Недоступные песни оформляются серыми и не запускаются.
- Добавлены корректные cover fallback и VK placeholder в связанных коммитах `c55831e`, `5732617`; исправлялись фото артистов и метаданные в `7fa232e`, `8ad7e45`.
- Основные файлы: `MusicBackend.kt`, `BackendModels.kt`, `VkAudioApi.kt`, `AudioTrack.kt`, `Priority2MusicDtos.kt`, `Track.kt`, `SearchScreen.kt`, `SearchViewModel.kt`, `DetailScreenParts.kt`, `AlbumDetailScreen.kt`, `ArtistDetailScreen.kt`, `PlaylistDetailScreen.kt`, `LibraryScreen.kt`.
- Проверка владельцем: после двух присланных compile-логов ошибки типизации были исправлены; позднее владелец сообщил, что версия собралась. Полный набор поисковых результатов на всех аккаунтах отдельно не подтверждён.
- Ограничение: приватные каталожные ответы VK могут различаться по аккаунту/региону; следующий агент не должен объявлять все секции гарантированными без ручной проверки.

## 2. Сохранение и refresh VK-сессии

**Коммиты:** `b1381e9`, `4131a1f`; базовый auth-флоу был восстановлен раньше в `472ce5b`, `e2a0ce2`, `c6cd470`, `1b45f74`.

- `VkAuthSession` поддерживает access/exchange token и сроки; `EncryptedVkSessionStore` сохраняет JSON сессии в SharedPreferences в AES/GCM через Android Keystore.
- Сессии с отсутствующим/нулевым сроком не считаются автоматически истёкшими.
- При ошибке истёкшего access token refresh выполняется без повторной отправки stale bearer; после обновления запрос повторяется.
- `LibraryScreen` больше не превращает ожидаемую ошибку истёкшей сессии в необработанный crash.
- Основные файлы: `VkAuthSession.kt`, `EncryptedVkSessionStore.kt`, `VkApiClient.kt`, `VkAuthApi.kt`, `MusicBackend.kt` (`MusicAuth`), `AuthScreen.kt`, `EmailAuthSheet.kt`, `LibraryScreen.kt`, `ProfileScreen.kt`.
- Проверка владельцем: авторизация, имя и аватар были ранее проверены на тестовом аккаунте; после refresh-фикса владелец отдельно сообщил, что перезаходить не пришлось и данные загрузились.
- Ограничение: не менять этот слой без конкретного лога. Ошибки сборки/поведения исправлять точечно.

## 3. Нативные страницы артистов, альбомов и библиотеки

**Коммит:** `b8e5d88` (`feat(music): restore artist album and library catalog flows`).

- Расширены backend DTO и VK catalog parsing для артиста, релизов и пользовательской библиотеки.
- `ArtistDetailScreen` и `AlbumDetailScreen` получили реальные состояния загрузки/ошибки, данные VK, треки и внутренние переходы.
- `LibraryScreen` получил каталог библиотеки: аудио и плейлисты разделены, а не смешаны в одном списке треков.
- Основные файлы: `BackendModels.kt`, `MusicBackend.kt`, `Priority1MusicDtos.kt`, `LiquidNavHost.kt`, `ArtistDetailScreen.kt`, `AlbumDetailScreen.kt`, `LibraryScreen.kt`.
- Проверка владельцем: визуальные/поведенческие ожидания переданы скриншотами; позднее владелец сообщил, что текущий результат ему нравится. Это не равно проверке каждого каталожного блока.

## 4. Плейлисты отделены от «Моих аудио» и внутренняя навигация

**Коммит:** `2b69449`.

- В `LibraryScreen` плейлисты представлены отдельным разделом/сеткой; треки остаются в «Моих аудио».
- Были добавлены внутренние маршруты для подробностей и ссылок. Позже WebView был удалён для артистов/сообществ (см. следующий этап).
- Основные файлы: `LibraryScreen.kt`, `NavRoutes.kt`, `LiquidNavHost.kt`, на тот момент `InAppBrowserScreen.kt`.
- Ограничение: импорт из внешних сервисов остаётся stub/TODO в `MusicBackend.previewPlaylist()` и `importPlaylist()`; не выдавать его за готовый.

## 5. Полноценная страница плейлиста

**Коммит:** `c1b0109`.

- `PlaylistDetailScreen` получил loading/error/empty состояния, шапку, метаданные, воспроизведение и shuffle, действия над треками и недоступные строки.
- Добавлены/расширены общие детали и `TrackActionsSheet`.
- Основные файлы: `PlaylistDetailScreen.kt`, `TrackActionsSheet.kt`, `DetailScreenParts.kt`, `MusicBackend.kt`, `BackendModels.kt`.
- Проверка: эталон показан на скриншотах `01-680909.jpg` и `01-680911.jpg`. UI не копировался из VK X буквально; использовалась внутренняя логика в стиле LMG VK.

## 6. Артисты и сообщества открываются нативно, без WebView

**Коммит:** `d4806ba`.

- Удалён `InAppBrowserScreen.kt` и его маршруты.
- Переходы по артистам, связанным артистам, официальным профилям/сообществам остаются внутри Compose-навигации текущего приложения.
- В backend-модели добавлены признаки официальной страницы/сообщества, в `MusicBackend` расширено сопоставление данных.
- Основные файлы: `ArtistDetailScreen.kt`, `LibraryScreen.kt`, `LiquidNavHost.kt`, `NavRoutes.kt`, `MusicBackend.kt`, `BackendModels.kt`, `Priority1MusicDtos.kt`.
- Ограничение по явному требованию владельца: для сообществ достаточно внутреннего простого представления в существующем UI; углубляться в полноценную стену сообщества не надо.

## 7. Доработка альбомов в собственном UI

**Коммит:** `4fd7db0`.

- Доработаны native album interactions: play/shuffle, переход к артисту, действия над треком, метаданные и корректные состояния.
- Недоступные треки не участвуют в воспроизведении.
- Основные файлы: `AlbumDetailScreen.kt`, `DetailScreenParts.kt`.
- Проверка: визуальный ориентир — `01-680907.jpg`, но владелец прямо потребовал не переносить UI VK X, а оставить UI проекта.

## 8. Расширенная страница артиста и завершение album details

**Коммит:** `09ade82` (`feat(music): complete artist and album details`).

- `ArtistDetailScreen` показывает при наличии данных: top songs/все песни, последний релиз, albums, singles & EPs, compilations, live albums, playlists, similar artists, участие в релизах, bio/about, связанные артисты/links, concerts, merch/information, официальные профили, communities, music videos.
- Добавлены действия play, shuffle, artist mix, follow/unfollow и share; секции скрываются, когда данных нет.
- Добавлен личный блок истории: число прослушиваний артиста и наиболее слушаемый трек на основании локального `AppDatabase`.
- Information сделан компактнее; релизы категоризируются по типу. Похожие и связанные артисты дедуплицируются.
- `AlbumDetailScreen` дополнен метаданными релиза, действиями, строками треков и переходом к артисту.
- Основные файлы: `ArtistDetailScreen.kt`, `AlbumDetailScreen.kt`.
- Проверка владельцем: владелец сообщил «мне всё нравится» и затем, что версия собралась. Не считать подтверждёнными концерты/мерч/видео для каждого артиста: эти блоки зависят от реально возвращённых VK catalog данных.

## 9. Двусторонняя синхронизация плейлистов

**Коммит:** `91084cc` (`feat(playlists): sync local and VK libraries`).

- `PlaylistManager` хранит локальный ID, `remoteId`, локальное время изменения, remote timestamp и `lastSyncedAt`.
- Новый `PlaylistSyncManager` связывает локальные и VK-плейлисты, загружает новые VK-плейлисты локально, отправляет новые/изменённые локальные плейлисты в текущий аккаунт и разрешает конфликт по dirty/timestamp.
- `VkAudioApi` и `MusicBackend` получили create/edit/delete и получение содержимого плейлистов на основе восстановленных методов.
- Автосинхронизация подключена при изменениях, логине и восстановлении сети; UI библиотеки показывает состояние синхронизации.
- Основные файлы: `PlaylistManager.kt`, `PlaylistSyncManager.kt`, `MusicBackend.kt`, `VkAudioApi.kt`, `LibraryScreen.kt`, `LmgApplication.kt`.
- Ограничения: на сервер отправляются только ID формата VK `owner_id_audio_id`; треки других источников сохраняются как локальная часть. Конфликтная стратегия простая и требует полевой проверки. Последние playlist commits владельцем ещё не подтверждены сборкой/ручным тестом.

## 10. Управление синхронизируемыми плейлистами и офлайн-очередь

**Последний функциональный коммит:** `c3ebe71` (`feat(playlists): add synced playlist management`).

Это был один коммит из пяти небольших батчей:

1. Создание плейлиста из UI (`PlaylistNameDialog`) с последующей автосинхронизацией в VK.
2. Переименование локального/связанного плейлиста с отправкой изменения.
3. «Добавить в плейлист» из меню трека на поиске и странице альбома через `PlaylistPickerSheet`.
4. Удаление трека и изменение порядка треков на странице локального/синхронизированного плейлиста.
5. Persistent offline queue для удалений remote-плейлистов; обычные add/remove/rename остаются dirty и отправляются после логина/возврата сети.

- `TrackActionsSheet` получил add/remove/move up/move down callbacks.
- `PlaylistSyncManager.deleteEverywhere()` удаляет локально сразу, а неуспешное remote delete ставит в очередь; sync сначала повторяет tombstones и не подтягивает удалённый remote-плейлист обратно.
- `LmgApplication` debounce-ит серию локальных изменений и запускает sync после логина/смены сети.
- Основные файлы: `LmgApplication.kt`, `PlaylistManager.kt`, `PlaylistSyncManager.kt`, `PlaylistDialogs.kt`, `TrackActionsSheet.kt`, `AlbumDetailScreen.kt`, `LibraryScreen.kt`, `PlaylistDetailScreen.kt`, `SearchScreen.kt`.
- Проверка: выполнены только статические `git diff --check` и осмотр вызовов/сигнатур. Локальная Gradle-сборка не запускалась по правилу владельца; GitHub Actions не отслеживались. Владелец ещё не передавал лог сборки этого коммита.

## 11. VK-only очистка, удаление onboarding и пустые UI-оболочки Home/Wave

**Коммит:** смотреть последний коммит после применения patch (`git log -1 --oneline`); файл `MEMORY.md` не может надёжно содержать SHA собственного коммита.

Один коммит собран из пяти связанных батчей:

1. Удалены `byicloud.online` и ICM-resolver/fallback из runtime-кода. Реальные VK URL сохраняются только когда пришли от VK; неразрешённые онлайн-треки хранят `Uri.EMPTY` и разрешаются по VK ID.
2. Добавлен единый `VkAudioIdentity`: нормализация `owner_id_audio_id`, VK-only определение онлайн-трека и share URL `https://vk.ru/audio{owner_id}_{audio_id}`. Старые внешние URL не используются как playback URI.
3. Полностью удалён Wave onboarding: экран, глобальный gate, state/settings, DTO и API-адаптеры, search-категории и вызовы из `HomeViewModel`.
4. `WaveHomeScreen.kt` очищен до presentation-only оболочки будущего VK Mix: без репозитория, PlayerController, очереди, кеша, рекомендаций, фоновых запросов и старой Wave-логики.
5. `HomeScreen.kt` очищен до presentation-only оболочки будущей VK-главной. Удалены внешние playlist-import UI/DTO/stubs, старые provider badges и неиспользуемые service drawables.

Основные файлы: `VkAudioIdentity.kt`, `Track.kt`, `MusicBackend.kt`, `BackendModels.kt`, `VkAudioApi.kt`, `AppSettings.kt`, `AppRoot.kt`, `HomeViewModel.kt`, `HomeScreen.kt`, `WaveHomeScreen.kt`, `SearchScreen.kt`, `LibraryScreen.kt`, `TrackActionsSheet.kt`, `PlayerController.kt`, `AudioService.kt`, `MEMORY.md`.

Проверка выполнена только статически: `git diff --check`, поиск запрещённых доменов/символов и parser-проверка изменённых Kotlin-файлов через `kotlinc` без Android classpath. Gradle/CI не запускались.

Ограничение: Home/Wave намеренно не подключены к данным. Следующим этапом нельзя возвращать старую LMG/ICM Wave-логику; VK Mix и главная должны восстанавливаться отдельно по двум архивам и фактическим VK DTO/методам.

## 12. Восстановление экрана профиля VK

**Первый коммит этапа:** `bb53afd` (`feat(profile): restore VK account screen`).

Этап состоит из пяти связанных изменений:

1. `VkAccountProfile` расширен подтверждёнными полями оригинального `VKProfile`: `photo_base`, `name`, `is_followed`, `can_follow`; `bestPhotoUrl` использует `photo_base` последним fallback.
2. В `users.get` добавлены только подтверждённые поля профиля; исходник — `VKLMG_Recovery/src-deobf/ua_itaysonlab_vkapi2_objects_users_VKProfile.java` и его Moshi adapter.
3. `MusicAuth` публикует безопасные данные текущего аккаунта (`profileId`, `profileDomain`) и состояние обновления; `fetchUserData()` возвращает успешность и не сохраняет/не показывает токены.
4. `ProfileScreen` переписан как нативный VK account screen: аватар, имя, VK ID/domain, ручное обновление через `users.get`, статистика, настройки, вход и подтверждённый выход.
5. Переход из профиля в настройки исправлен: открывается overlay `SettingsScreen`, а не происходит неявное переключение таба.

- Из профиля удалены старые LMG region/subscription/followed-artists блоки: они зависели от неподтверждённых `TODO(vk-wire)` методов и не могли считаться восстановленной функциональностью. Ложный Premium-индикатор также не показывается.
- Основные файлы: `VkAccountProfile.kt`, `VkMethodsRegistry.kt`, `MusicBackend.kt` (`MusicAuth`), `ProfileScreen.kt`, `AppRoot.kt`.
- Проверка: `git diff --check` и поиск старых region/subscription вызовов в `ProfileScreen` пройдены. Локальная Gradle-сборка и GitHub Actions не запускались.
- Ограничение: доступные Recovery-материалы подтверждают только контракт `users.get` для account identity; не добавлять регион, платёжный статус, сторонние подписки или другие profile API без нового подтверждённого исходника.

### Расширение профиля без переноса UI/подписки VK X

**Коммит:** смотреть `git log -1 --oneline`; `MEMORY.md` входит в тот же коммит и не может надёжно содержать его собственный SHA.

Пять связанных изменений:

1. `MusicAuth` публикует только безопасный срок VK-сессии (`profileSessionExpiresAt`), без token material.
2. Профиль показывает состояние VK-сессии вместе с ID и domain.
3. Добавлен snapshot локальной библиотеки: избранное, скачивания, медиатека устройства, play events и длительность прослушивания.
4. Добавлена компактная LMG VK-карточка «Your library» с актуальным числом локальных плейлистов.
5. Выход из VK больше не вызывает старый `LocalAuthManager`: очищается только зашифрованная VK-сессия через `MusicAuth.logout()`.

- UI остаётся собственным Compose UI LMG VK; из VK X перенесён только подтверждённый контракт `users.get` и уже восстановленная модель VK-сессии. Premium/subscription UI и логика VK X не используются.
- Основные файлы: `MusicBackend.kt` (`MusicAuth`), `ProfileScreen.kt`, `MEMORY.md`.
- Проверка: `git diff --check`, статический поиск запрещённых profile region/subscription API. Локальная Gradle-сборка и GitHub Actions не запускались.
- Исправление по compile-логу владельца: в `ProfileCard` slot-type заменён с функции `Column` на `ColumnScope`; это устраняет `Unresolved reference 'Column'` на строках карточек профиля.

### Функции и реальные данные профиля

**Коммит:** смотреть `git log -1 --oneline`; `MEMORY.md` входит в тот же коммит.

Пять связанных изменений:

1. Реализована единая загрузка локальной сводки из существующих `AppDatabase`, `FavoriteTrackDatabase` и `PlaylistManager`.
2. В профиль добавлено реальное последнее событие прослушивания: время и источник из `playback_history`.
3. «Refresh profile» обновляет и `users.get`, и локальные показатели, а не только аватар/имя.
4. Добавлена функция копирования фактической ссылки `https://vk.com/<domain>` в clipboard — только если VK вернул `domain`.
5. Добавлен внутренний переход «My Library» к существующей вкладке библиотеки; он не открывает WebView/браузер.

- Реальные значения: VK ID/domain/session — из текущей восстановленной VK-сессии и `users.get`; библиотека/скачивания/прослушивания — исключительно из локальных БД LMG VK. Не подменять эти значения данными из неподтверждённого backend или VK X subscription.

## 13. Библиотека: восстановление первого полного набора функций

**Коммит:** смотреть `git log -1 --oneline`; `MEMORY.md` входит в тот же коммит.

Сверка с `VKLMG_Recovery/VKX-ENDPOINTS.md` подтвердила уже используемые слои: `audio.get`, `audio.getPlaylists`, `audio.getPlaylistById`, `audio.add`, `audio.delete`, `audio.restore`, `audio.reorderInPlaylist` и пагинацию плейлистов. UI LMG VK оставлен собственным.

Пять связанных изменений:

1. Третья вкладка нижней и боковой навигации переименована из ошибочного `Playlist` в `Library`.
2. Удалены ложные Premium/subscription карточка и блокировка пустого экрана скачиваний: они не относятся к восстановленному VK-only library flow.
3. На главной библиотеки добавлен общий refresh: он запускает offline-first sync треков и синхронизацию/получение VK-плейлистов.
4. Поиск теперь работает по всему локальному списку «Моих треков» и сохраняется при переходе в полный список; раньше фильтровалось только пять карточек preview.
5. Ошибки `LibraryRepository.syncWithCloud()` больше не теряются: ViewModel извлекает ошибку из `Result`, а UI показывает Snackbar.

- Основные файлы: `BottomBar.kt`, `SideBar.kt`, `LibraryScreen.kt`, `LibraryViewModel.kt`, `MEMORY.md`.
- Проверка: `git diff --check`, поиск Premium/subscription блоков и статический осмотр связей `audio.*`. Локальная Gradle-сборка и GitHub Actions не запускались.
- Следующая проверка владельцем: открыть Library → Refresh; проверить «My tracks» и поиск; открыть Downloads с пустой базой; затем открыть Playlists и убедиться, что sync/error статус виден.

## 14. Библиотека: поиск внутри текущего VK-профиля

**Коммит:** смотреть `git log -1 --oneline`; `MEMORY.md` входит в тот же коммит.

Сверка с `/storage/emulated/0/Download/VKLMG_Recovery/vkx-deobf.jar` подтвердила оригинальный `execute.SearchInProfile`: execute-код вызывает `audio.searchPlaylists` с `filters: "owned"` и `audio.search` с `search_own: 1`; Moshi-адаптер исходника разбирает ключи `playlists.items`, `playlists.profiles`, `playlists.groups` и `audios` как `AudioPlaylist`/`AudioTrack`.

Пять связанных изменений:

1. Добавлен типизированный DTO минимально нужной части ответа (`playlists.items`, `audios`); неиспользуемые `profiles/groups` Moshi пропускает.
2. `VkMethodsRegistry.searchInProfile()` перестал возвращать `Any` и использует этот DTO.
3. `MusicBackend.searchCurrentProfileLibrary()` выполняет подтверждённый execute-вызов, кеширует полученные VK-треки и отдаёт UI нормализованные треки/плейлисты.
4. `LibraryViewModel` получил отменяемый debounce-поиск, loading/error/result состояния; короткий или очищенный запрос не выполняет сеть.
5. Главный экран Library показывает реальные результаты собственного профиля при запросе от двух символов: плейлисты открываются внутри приложения, треки запускаются в очереди найденных VK-треков.

- Основные файлы: `ProfileLibrarySearchResponse.kt`, `VkMethodsRegistry.kt`, `MusicBackend.kt`, `BackendModels.kt`, `LibraryViewModel.kt`, `LibraryScreen.kt`, `MEMORY.md`.
- Проверка: `git diff --check`, статический осмотр signature/JSON-ключей и единственного вызова нового registry-метода. Локальная Gradle-сборка и GitHub Actions не запускались по правилу владельца.
- Следующая проверка владельцем: Library → ввести часть названия существующего личного трека и плейлиста; проверить оба раздела, открытие плейлиста и запуск трека. При ошибке передать конкретный compile/runtime лог.

## 15. New: восстановленная главная выдача VK CatalogKit

**Коммит:** смотреть `git log -1 --oneline`; `MEMORY.md` входит в тот же коммит.

Источник: `/storage/emulated/0/Download/VKLMG_Recovery/PRIORITY1-RECOVERY.md` подтверждает `catalog.getAudioAuto(need_blocks=1)` как главную музыкальную страницу VK X; `VkCatalogResponse` и `VkCatalogBlock` уже были восстановлены из адаптеров VK X с `audios_ids`, `playlists_ids`, `artists_ids`, `layout` и порядком `catalog.sections[].blocks`.

Пять связанных изменений:

1. Из New удалены перенесённые с Wave локальные mood-карточки, предпросмотр станций, recently played и история Room.
2. Из New удален отдельный синтетический блок charts: вкладка отображает только единый ответ главного VK-каталога.
3. `MusicBackend.loadHomeContent()` теперь сохраняет порядок и заголовки всех доступных catalog-блоков VK, а не сводит ответ к четырём искусственным категориям.
4. Для каждого блока реальные `audios_ids`/`playlists_ids`/`artists_ids` разрешаются против payload того же ответа VK; root-сущности используются только как fallback для вариантов API без ссылок blocks.
5. Новые карточки корректно открывают артиста/релиз внутри приложения, запускают VK-трек и блокируют недоступный трек; в UI добавлены loading/error состояния каталога.

- Основные файлы: `NewScreen.kt`, `MusicBackend.kt`, `BackendModels.kt`, `MEMORY.md`.
- Проверка: `git diff --check`, статический осмотр исходного `catalog.getAudioAuto` и полей блоков. Локальная Gradle-сборка и GitHub Actions не запускались по правилу владельца.
- Следующая проверка владельцем: открыть New на аккаунте с заполненной выдачей VK; убедиться, что видны несколько серверных секций, перейти в артиста/релиз и запустить обычный/недоступный трек. При логах сборки или сети передать их целиком.

# Что уже работало до этого этапа

Из исходного статуса владельца и его ручных сообщений известно:

- Авторизация VK восстановлена и была проверена на отдельном тестовом аккаунте.
- Профиль пользователя работает в проверенном сценарии.
- Имя и аватарка отображаются; исправление аватарки — commit `1b45f74`.
- Два ненужных поля под именем были удалены.
- Сохранение сессии/refresh было вручную подтверждено: после исправления перезаходить не пришлось.
- Поиск переводится на единственный источник VK.
- Лишние пользовательские категории/переключатели Apple, Video, All и отдельный VK должны отсутствовать.

Не утверждать без новой проверки, что вся авторизация, поиск или профиль работают на любом аккаунте/регионе/устройстве. Не возвращать удалённые категории поиска из-за старых комментариев или вспомогательных enum в коде.

# Текущая точка проекта

- Текущая ветка владельца: `main`.
- Базовый функциональный commit перед этим patch: `c3ebe71` плюс отдельный commit с первоначальным `MEMORY.md`.
- Последнее изменение: VK-only очистка, удаление onboarding и перевод `HomeScreen`/`WaveHomeScreen` в presentation-only состояние.
- После применения patch точный SHA смотреть через `git log -1 --oneline`.
- Требуется сборка владельцем и ручная проверка: запуск без onboarding, открытие Wave/Home без сетевых запросов старого слоя, VK Share, поиск, запуск треков из поиска/альбома/плейлиста/библиотеки, создание локального плейлиста.
- До подтверждения сборки не подключать VK Mix и не возвращать в Home/Wave старую бизнес-логику.

# Что делать дальше

Приоритет — небольшие этапы, продолжающие уже сделанное.

## 1. Проверить последний playlist management batch по логу владельца

- **Цель:** убедиться, что `c3ebe71` компилируется и базовые операции не падают.
- **Где искать исходную реализацию:** `VKLMG_Recovery.zip` → `src-deobf/ua_itaysonlab_vkapi2_methods_audio_playlist_*` и объекты `...objects_music_playlist_*`; текущая реализация — `PlaylistManager.kt`, `PlaylistSyncManager.kt`, `PlaylistDialogs.kt`.
- **Основные классы:** `LmgApplication`, `PlaylistManager`, `PlaylistSyncManager`, `MusicBackend`, `VkAudioApi`, `LibraryScreen`, `PlaylistDetailScreen`, `TrackActionsSheet`.
- **Ручная проверка владельца:** создать плейлист → дождаться появления в VK; переименовать с обеих сторон → запустить sync; добавить трек из Search/Album; переставить и удалить трек; удалить плейлист без сети и убедиться, что после сети он удаляется и не появляется снова.
- Не запускать/не ждать CI самостоятельно. Исправлять только конкретный присланный лог.

## 2. Проверить полноту и стабильность страницы артиста на нескольких типах артистов

- **Цель:** проверить обычного артиста, артиста без bio/видео и артиста с community/official pages; устранить только реальные пустые/дублированные секции.
- **Где искать:** VK X catalog blocks/adapters в `VKLMG_Recovery.zip`, эталоны `01-680903.jpg`—`04-680906.jpg` и `01-680914.jpg`.
- **Основные классы:** `ArtistDetailScreen`, `MusicBackend.getArtist`, `BackendModels.ArtistResponse`, `Priority1MusicDtos`, `VkCatalogApi`.
- **Ручная проверка:** поиск Басты/другого артиста → открыть внутри приложения → проверить top songs, все релизы, участие, похожих/связанных артистов, info и внутренние переходы; ни один переход не должен открывать VK app/WebView.

## 3. Проверить album details и недоступные треки

- **Цель:** подтвердить корректные metadata/play/shuffle/actions и серое некликабельное состояние unavailable.
- **Где искать:** VK X playlist/album DTO и `audio.getPlaylistById` в архиве; VK MP3 MOD — модели аудио и ресурсы; эталон `01-680907.jpg`.
- **Основные классы:** `AlbumDetailScreen`, `DetailScreenParts`, `MusicBackend.getAlbum`, `VkAudioApi`, `BackendModels.AlbumResponse/AlbumTrack`, `PlayerController`.
- **Ручная проверка:** открыть альбом из поиска и артиста; проверить имя/обложку/год/жанр/количество, переход к артисту, play/shuffle, add to playlist и невозможность запуска unavailable.

## 4. Проверить библиотеку «Мои аудио» и большие объёмы

- **Цель:** убедиться, что все аудио аккаунта загружаются, плейлисты не смешиваются с треками, отсутствуют дубли и корректно показаны unavailable.
- **Где искать:** `audio.get`, `audio.getPlaylists` и pagination в обоих архивах.
- **Основные классы:** `LibraryScreen`, `LibraryViewModel`, `LibraryRepository`, `MusicBackend`, `VkAudioApi`, `PlaylistSyncManager`.
- **Ручная проверка:** аккаунт с большой библиотекой; сравнить количество/первые и последние треки с VK, прокрутить, открыть плейлист, проверить refresh после изменения в VK.

## 5. Затем — скачивание и кеширование, без переписывания плеера

- **Цель:** сопоставить текущие `DownloaderService`/`TrackDownloader` и cache с оригиналом и закрыть один конкретный сценарий (например, один трек), не весь downloader сразу.
- **Где искать:** `vk_mp3_mod_analysis/VkAudioDownloader.kt`, `Mp3TagWriter.kt`, `VK_MP3_MOD_RECOVERY.md`; VK X downloader/cache классы в `VKLMG_Recovery.zip`.
- **Основные классы:** `DownloaderService`, `TrackDownloader`, `AudioDownloadManager`, `MediaCacheManager`, `CachedLibrary`, `DownloadedTrackEntity`.
- **Ручная проверка:** скачать один доступный трек, проверить прогресс, имя/теги/обложку, воспроизведение офлайн и повтор без дубликата.

Не предлагать переписывание всего проекта. Служебный backend/proxy/certs/native-антиhook исследовать только после основных пользовательских функций либо при конкретном блокере.

# Карта важных файлов

Указаны только реально найденные пути/классы.

## Авторизация

- `app/src/main/kotlin/com/lmg/vk/network/VkAuthSession.kt`
- `app/src/main/kotlin/com/lmg/vk/network/EncryptedVkSessionStore.kt`
- `app/src/main/kotlin/com/lmg/vk/network/VkAuthApi.kt`
- `app/src/main/kotlin/com/lmg/vk/network/VkApiClient.kt`
- `app/src/main/kotlin/com/lmg/vk/network/methods/AppsGetSilentAuth.kt`
- `app/src/main/kotlin/com/lmg/vk/network/dto/Priority4AuthDtos.kt`
- `app/src/main/kotlin/com/lmg/vk/ui/screens/AuthScreen.kt`
- `app/src/main/kotlin/com/lmg/vk/ui/screens/EmailAuthSheet.kt`
- `app/src/main/kotlin/com/lmg/vk/engine/backend/MusicBackend.kt` (`MusicAuth`, auth flow)

## Профиль

- `app/src/main/kotlin/com/lmg/vk/ui/screens/ProfileScreen.kt`
- `app/src/main/kotlin/com/lmg/vk/network/dto/VkAccountProfile.kt`
- `app/src/main/kotlin/com/lmg/vk/network/dto/gen/users/VKProfile.kt`
- `MusicAuth.fetchUserData()` в `MusicBackend.kt`

## Поиск

- `app/src/main/kotlin/com/lmg/vk/ui/screens/SearchScreen.kt`
- `app/src/main/kotlin/com/lmg/vk/ui/viewmodel/SearchViewModel.kt`
- `app/src/main/kotlin/com/lmg/vk/engine/backend/MusicBackend.kt`
- `app/src/main/kotlin/com/lmg/vk/engine/backend/BackendModels.kt`
- `app/src/main/kotlin/com/lmg/vk/network/methods/VkAudioApi.kt`
- `app/src/main/kotlin/com/lmg/vk/network/dto/music/Priority2MusicDtos.kt`

## Страница артиста и похожие/связанные сущности

- `app/src/main/kotlin/com/lmg/vk/ui/screens/ArtistDetailScreen.kt`
- `MusicBackend.getArtist()` в `MusicBackend.kt`
- `ArtistResponse`, `ArtistAlbum`, `SimilarArtist`, `ArtistPlaylist`, `ArtistOfficialPage`, `ArtistLink`, `ArtistVideo` в `BackendModels.kt`
- `app/src/main/kotlin/com/lmg/vk/network/methods/VkCatalogApi.kt`
- `app/src/main/kotlin/com/lmg/vk/network/dto/music/Priority1MusicDtos.kt`

## Релизы/альбомы

- `app/src/main/kotlin/com/lmg/vk/ui/screens/AlbumDetailScreen.kt`
- `AlbumResponse`, `Album`, `AlbumTrack` в `BackendModels.kt`
- `app/src/main/kotlin/com/lmg/vk/ui/components/DetailScreenParts.kt`
- `MusicBackend.getAlbum()` и соответствующие методы `VkAudioApi.kt`

## Плейлисты и библиотека

- `app/src/main/kotlin/com/lmg/vk/ui/screens/LibraryScreen.kt`
- `app/src/main/kotlin/com/lmg/vk/ui/screens/PlaylistDetailScreen.kt`
- `app/src/main/kotlin/com/lmg/vk/ui/components/PlaylistDialogs.kt`
- `app/src/main/kotlin/com/lmg/vk/engine/PlaylistManager.kt`
- `app/src/main/kotlin/com/lmg/vk/engine/PlaylistSyncManager.kt`
- `app/src/main/kotlin/com/lmg/vk/data/local/db/LibraryRepository.kt`
- `UserPlaylistsResponse`, `UserPlaylist`, `UserPlaylistTracksResponse`, `UserPlaylistTrack` в `BackendModels.kt`
- `MusicBackend` и `VkAudioApi`

## Треки

- `app/src/main/kotlin/com/lmg/vk/engine/Track.kt`
- `app/src/main/kotlin/com/lmg/vk/network/dto/music/AudioTrack.kt`
- `app/src/main/kotlin/com/lmg/vk/ui/components/TrackActionsSheet.kt`
- `app/src/main/kotlin/com/lmg/vk/data/local/db/FavoriteTrackEntity.kt`
- `app/src/main/kotlin/com/lmg/vk/data/local/db/DownloadedTrackEntity.kt`
- `app/src/main/kotlin/com/lmg/vk/data/local/db/LocalTrackEntity.kt`

## Навигация

- `app/src/main/kotlin/com/lmg/vk/ui/navigation/NavRoutes.kt`
- `app/src/main/kotlin/com/lmg/vk/ui/navigation/LiquidNavHost.kt`
- `app/src/main/kotlin/com/lmg/vk/ui/navigation/BottomBar.kt`
- `app/src/main/kotlin/com/lmg/vk/ui/navigation/LiquidBottomTab.kt`
- `app/src/main/kotlin/com/lmg/vk/ui/AppRoot.kt`
- Детали album/artist/playlist регистрируются в `musicDetailDestinations()` внутри каждого tab graph; это сохраняет внутренний back stack.

## API VK и модели

- `app/src/main/kotlin/com/lmg/vk/network/VkApiClient.kt`
- `app/src/main/kotlin/com/lmg/vk/network/VkMethod.kt`
- `app/src/main/kotlin/com/lmg/vk/network/VkResponseParser.kt`
- `app/src/main/kotlin/com/lmg/vk/network/VkApiLocator.kt`
- `app/src/main/kotlin/com/lmg/vk/network/methods/VkMethodsRegistry.kt`
- `app/src/main/kotlin/com/lmg/vk/network/methods/VkAudioApi.kt`
- `app/src/main/kotlin/com/lmg/vk/network/methods/VkCatalogApi.kt`
- `app/src/main/kotlin/com/lmg/vk/network/dto/music/`
- `app/src/main/kotlin/com/lmg/vk/engine/backend/BackendModels.kt`

## Загрузка изображений

- `app/src/main/kotlin/com/lmg/vk/ui/glass/AlbumArtImage.kt`
- `app/src/main/kotlin/com/lmg/vk/data/cache/ImageCache.kt`
- `app/src/main/kotlin/com/lmg/vk/ui/glass/AlbumColorExtractor.kt`
- На экранах также используется Coil `AsyncImage`/`ImageRequest`.

## Воспроизведение

- Основной текущий движок: `app/src/main/kotlin/com/lmg/vk/engine/PlayerController.kt`, `AudioService.kt`, `StreamingDataSource.kt`, `MediaCacheManager.kt`, `AudioDownloadManager.kt`.
- UI: `app/src/main/kotlin/com/lmg/vk/ui/player/FullPlayer.kt`, `MiniPlayer.kt`, `QueueSheet.kt`.
- Отдельный восстановленный reference stack: `app/src/main/kotlin/com/lmg/vk/playback/PlaybackService.kt`, `LmgAudioEffects.kt`, `CrossfadeController.kt`.
- Manifest регистрирует и `engine.AudioService`, и `playback.PlaybackService`; не удалять один из них без анализа реального использования.

## Native-часть

- `app/src/main/cpp/lmg_native.cpp`
- `app/src/main/cpp/CMakeLists.txt`
- `app/src/main/kotlin/com/lmg/vk/jni/LmgNative.kt`
- `app/src/main/kotlin/com/lmg/vk/security/NativeSecurity.kt`
- `app/src/main/kotlin/com/lmg/vk/engine/SecurityUtils.kt`
- `app/src/main/kotlin/com/lmg/vk/MainActivity.kt`

# Сеть и native-часть

## Подтверждено кодом/материалами

- Основные функции используют прямой VK API через `VkApiClient`, `VkAudioApi`, `VkCatalogApi` и DTO. Текущий код содержит API endpoint-логику VK и ссылки на официальные VK/userapi/vkuseraudio ресурсы.
- Crash log `Вставленный текст(1).txt` показывает прямое HTTPS-соединение к `sun9-67.userapi.com`, что согласуется с прямой загрузкой медиа/CDN VK.
- `VKX_Certs.zip` содержит proxy IP/domains, certificate pins, remote config и правила domain override. Это материал оригинального клиента; текущая основная логика приложения не должна слепо внедрять эти правила.
- Оригинальный JNI-класс в архиве: `ua.itaysonlab.vkxnative.VKXNative` с `x00()`, `x01()`, `x02(String)` и `BundleNativeClass`.
- Исторический анализ `docs/PROJECT_STATUS.md` утверждает, что оригинальная arm64 `libvkx.so` была stripped и защищена OLLVM/O-MVLL; восстановленная текущая реализация находится в `lmg_native.cpp` и собирается как собственная native library через CMake.
- Текущий `LmgNative.kt` exposes `getVkApiData()`, `getLmgEnvironment()`, `getSilentAuthorizationEnvironment(String)`; C++ регистрирует их через `RegisterNatives`.
- В текущем C++ сохранён anti-Xposed вызов `disableXposedHooks()` в `JNI_OnLoad`.
- Проверка подписи/целостности APK в текущем восстановленном C++ отключена/удалена (пустой слот environment); `MainActivity` прямо говорит, что восстановленные сборки подписываются владельцем.
- `NativeSecurity`/`SecurityUtils` содержат признаки проверки Frida/Xposed/debugger/emulator/root. Не отключать или расширять их без конкретной причины.

## Гипотезы и ограничения доказательств

- **Гипотеза:** `api.vkx.app` играл малую служебную роль (аналитика, remote config, feature flags, версия/подпись, proxy/cert config или служебные токены). Основание — PCAP-наблюдение владельца: около 11 КБ от этого backend против больших объёмов прямого VK traffic. Точное назначение в доступном текущем коде не доказано.
- Не строить поиск, профиль, библиотеку, аудио или плейлисты вокруг `api.vkx.app`: доступные факты указывают на прямую работу этих функций через VK API/CDN.
- В текущем `lmg_native.cpp` встречаются `ui.lmg.app`/`api.lmg.app`; `README.md` и `docs/PROJECT_STATUS.md` помечают их как placeholder/TODO. Не считать их доказанным рабочим backend и не подставлять новый домен без анализа.
- **Исторический отчёт, требующий осторожности:** `docs/PROJECT_STATUS.md` описывает восстановление `libvkx.so`, JNI и anti-tamper. Сам бинарник `libvkx.so` не найден внутри доступного `VKLMG_Recovery.zip`; поэтому новые выводы о бинарнике надо подтверждать оригинальным APK/отчётами, а не только пересказом.
- **Требует дополнительной проверки:** участвовала ли оригинальная подпись APK в расшифровке данных, отправлялся ли её отпечаток на `api.vkx.app`, и какие Java callers реально использовали каждый элемент результата `x00/x01/x02`.

# Правила проверки сборки

- Агентам запрещено тратить лимиты на ожидание GitHub Actions.
- Агент не должен постоянно проверять статус CI и не должен открывать Actions «посмотреть, собралось ли».
- Сборку и APK проверяет владелец проекта на GitHub/устройстве.
- Агент сообщает изменённые файлы, commit SHA и возможные риски.
- Нельзя утверждать, что сборка успешна, если она фактически не проверялась владельцем или конкретным завершившимся запуском.
- Ошибки сборки исправляются после того, как владелец передаст конкретный compile/runtime лог.
- Не запускать локальный Gradle/build: владелец прямо указал, что сборка только на GitHub.
- Допустимы короткие статические проверки (`git diff --check`, `rg` по сигнатурам/вызовам), но они не заменяют сборку.

# Формат передачи состояния

- **Текущая ветка:** `main`.
- **Последний функциональный commit до VK-only патча:** `c3ebe71`. SHA текущего патча смотреть через `git log -1 --oneline` после применения.
- **Последняя выполненная задача:** удаление ICM/byicloud из runtime-цепочек, перевод playback placeholder/share на VK и отключение неподтверждённого внешнего брокера.
- **Следующая рекомендуемая задача:** собрать на GitHub и вручную проверить Share и запуск VK-треков из Search/Album/Artist/Playlist/Library/History/Stats/Wave; при ошибке передать конкретный compile/runtime log.
- **Состояние проверки:** локальная Gradle-сборка и ожидание CI не выполнялись. Статические `rg`, осмотр resolve/share цепочки и `git diff --check` прошли; результат требует сборки владельцем.
- **Что обязательно прочитать/изучить следующему агенту:** этот `MEMORY.md`; `git log --oneline -15`; фактический `git status`; `docs/PROJECT_STATUS.md` только как исторический материал; два главных архива и их отчёты; затем конкретные текущие классы задачи. Для плейлистов в первую очередь: `PlaylistManager.kt`, `PlaylistSyncManager.kt`, `MusicBackend.kt`, `VkAudioApi.kt`, `LibraryScreen.kt`, `PlaylistDetailScreen.kt`, `PlaylistDialogs.kt`, `LmgApplication.kt`.

# VK-only cleanup: удаление ICM/byicloud

**Текущий логический этап:** подготовлен патч, который удаляет все обращения и placeholder-ссылки `byicloud.online` из проекта. SHA самого коммита смотреть через `git log -1 --oneline` после применения патча.

## Жёсткая политика источников

- Целевая и обязательная политика этого репозитория: музыкальные данные и аудио должны идти только через VK и локальный кеш ранее полученных данных VK.
- Из отдельного проекта ICM Music разрешено сохранять только явно запрошенный владельцем UI. Нельзя переносить его API, endpoints, resolver/fallback-логику, модели провайдеров, Apple Music, Tidal или другие музыкальные источники.
- Реальный URL аудио берётся из VK `AudioTrack.url`; при отсутствии URL трек хранит unresolved URI и непосредственно перед воспроизведением разрешается по полному VK ID `owner_id_audio_id` через существующий `audio.getById`.
- Для Share используется восстановленный из VK MP3 MOD формат `https://vk.ru/audio{owner_id}_{audio_id}`. Если ID не является полным VK audio ID, отправляются только название и исполнитель без подстановки сторонней ссылки.
- Не заменять удалённый брокер выдуманными VK-методами. Continuity, listening rooms, broker collaborative playlists и внешняя база credits не имеют подтверждённого аналога в двух архивах, поэтому их сеть отключена до отдельного решения владельца.

## Что изменено в этом этапе

- Добавлен единый `VkAudioIdentity`: нормализация VK full ID, unresolved playback URI, извлечение ID из внутреннего `liquid://` URI и официальный Share URL.
- В исходном срезе найдено 27 упоминаний `byicloud.online` в 18 файлах `app/src/main`; все runtime-ссылки и обращения удалены.
- Все `Track`, которые раньше получали `https://byicloud.online/track/<id>` как placeholder, теперь получают реальный URL VK при его наличии либо `Uri.EMPTY` до resolve через VK.
- `WaveRepository` больше не сохраняет удалённые signed/resolver URL в Room и игнорирует исторические внешние URL из уже существующей базы; сохраняются только локальные `file/content/...` URI.
- `Track.isOnlineTrack` больше не распознаёт онлайн-трек по чужому домену; используется `source == "vk"` или валидный VK full ID, а локальные `file/content/...` URI остаются локальными.
- `TrackActionsSheet` больше не публикует ICM-ссылку.
- Lyrics UI извлекает ID из внутреннего resolving URI, а не из пути стороннего сайта.
- Сетевой брокер в `LmgSyncApi` заменён compatibility-заглушкой без HTTP; его автоматический запуск из `AudioService` удалён.

## Проверка после применения

1. `rg -n -i 'byicloud|ICM Music' app/src/main` должен вернуть пустой результат.
2. Открыть Share у VK-трека и проверить ссылку вида `https://vk.ru/audio-123_456`.
3. Запустить трек из Search, Album, Artist, Playlist, Library, History, Stats и Wave; в логах не должно быть запросов к стороннему домену, resolve должен идти через VK.
4. Проверить локальный `file://`/`content://` трек: он не должен ошибочно уходить в VK resolver.
5. Broker-only continuity/rooms/shared playlists/credits сейчас не должны выполнять сетевые запросы; отдельный UI-cleanup этих пунктов можно сделать следующим маленьким батчем.

Локальная Gradle-сборка не запускалась согласно правилам проекта. Выполнены только статический поиск ссылок, осмотр цепочки resolve/share и `git diff --check`.

# VK audio cover fallback: цветные VK-варианты

**Текущий логический этап:** единая обложка для VK-треков без изображения или с недоступной CDN-обложкой. SHA данного коммита смотреть через `git log -1 --oneline` после применения.

- Владелец передал 9 скриншотов `Дефолт.zip`, подтвердивших актуальный мобильный вид: большая матовая нота с цветной фактурой; оттенок различается между треками. Маленький `https://vk.com/images/audio_row_placeholder.png` — legacy web placeholder и не является нужным полным вариантом.
- Владелец передал 10 готовых цветных PNG в `Default_covers.zip`; они добавлены как локальные `res/drawable-nodpi/default_track_cover_01..10.png` без UI-обрезки и сетевой зависимости.
- Удалён неиспользуемый синтетический `VkDefaultAudioCover` с выдуманными градиентами. `AlbumArtImage` распознаёт legacy URL как отсутствие cover и выбирает один из 10 новых ресурсов стабильным хешем ключа трека; плохой URL CDN проходит в тот же fallback.
- В ключ выбора передаются ID/название/исполнитель в плеере, очереди, контекстном меню, New, избранном, скачанном, recent и preview Library. Поэтому у одного трека оттенок сохраняется между этими экранами, а набор не сводится к одному варианту.
- Изменены: `ui/glass/AlbumArtImage.kt`, `ui/screens/NewScreen.kt`, `ui/screens/LibraryScreen.kt`, `ui/components/TrackActionsSheet.kt`, `ui/player/{FullPlayer,MiniPlayer,QueueSheet}.kt`, десять `default_track_cover_*.png`, `MEMORY.md`.
- Локальная Gradle-сборка и GitHub Actions не запускаются по прямому правилу владельца. Перед коммитом выполнить только `git diff --check`, статическую сверку R-ресурсов и поиск оставшегося `VkDefaultAudioCover`.

# Переключаемые иконки приложения

**Текущий логический этап:** 13 вариантов launcher-иконки из пользовательского `icons.zip`. SHA данного коммита смотреть через `git log -1 --oneline` после применения.

- В `res/drawable-nodpi/` добавлены варианты из архива владельца: `launcher_icon_{sunset,emerald,lagoon,amethyst,prism,neon,fuchsia,amber,ruby,graphite,rose,cobalt,pearl}.webp`.
- Названия вариантов: «Закат», «Изумруд», «Лагуна», «Аметист», «Призма», «Неон», «Фуксия», «Янтарь», «Рубин», «Графит», «Роза», «Кобальт», «Жемчуг». По умолчанию — «Закат».
- `AndroidManifest.xml` использует 13 `activity-alias`, из которых в fresh install включён только `LauncherIconSunset`; основной `MainActivity` больше не объявляет свой собственный LAUNCHER intent-filter, поэтому дубликата ярлыка нет.
- `ui/LauncherIconManager.kt` включает новый alias раньше отключения старого, сохраняет выбор в SharedPreferences и использует `DONT_KILL_APP` — смена не перезапускает activity.
- В Settings добавлена сетка превью со всеми 13 вариантами, выбранным состоянием и уведомлением. Некоторые системные лаунчеры могут обновить картинку на домашнем экране с короткой задержкой собственного кэша.
- `application.icon` и `roundIcon` указывают на «Закат», поэтому старая красная иконка с белой нотой удалена также из Android «Информация о приложении». Android не позволяет менять этот application-level icon на лету через activity-alias: только ярлык лаунчера следует выбранному пользователем варианту.
- Все 23 предоставленных cover/icon ресурса конвертированы из 1254×1254 PNG в WebP quality 92, сохранив имена Android resource и разрешение. `drawable-nodpi` уменьшился с 51 МБ до 7 МБ; ссылки Kotlin/manifest не менялись.
- После присланного владельцем compile-лога исправлен `AlbumArtImage`: у overload `Image(painter = …)` нет параметра `filterQuality`; параметр оставлен только у локального `ImageBitmap` overload.
- Локальная Gradle-сборка и GitHub Actions не запускались по правилу владельца; перед коммитом допустимы только статические проверки manifest/resources/diff.

# New: загрузка полных VK catalog blocks

**Текущий логический этап:** восстановление непустой вкладки New из VK CatalogKit. SHA данного коммита смотреть через `git log -1 --oneline` после применения.

- Причина пустого New: `catalog.getAudioAuto` у части ответов VK отдаёт только список section ID, тогда как реальные `blocks` и entity payload находятся в последующих `catalog.getSection` — это подтверждённый маршрут CatalogKit из VK X.
- `MusicBackend.loadHomeContent()` теперь загружает default section и все уникальные section ID, сохраняет серверный порядок блоков и кеширует аудио со всех полученных страниц.
- В DTO возвращены `catalog_banners` и `curators` из адаптера `Catalog2Response` VK X: New отображает серверные промо/редакторские карточки (в том числе «Сегодня в плеере» и «Собрано редакцией») без попытки передать их в аудиоплеер до восстановления их `click_action`.
- Также возвращены серверные links, radio stations, stream mixes и music owners, поэтому блоки со странами/чартами, редакционными витринами и радиостанциями не теряются на этапе преобразования CatalogKit в UI.
- Кеш New сохраняет типы CatalogKit (`isAlbum`, `isArtist`, `isCustom` и доступность), чтобы при мгновенном показе кеша редакционная карточка не превращалась в трек до прихода сети.
- Если `catalog.getAudioAuto` возвращает стартовую секцию напрямую в `section` (альтернативная подтверждённая форма `Catalog2Response`), её ID также загружается через `catalog.getSection`. Возвращены `audio_content_cards` и curator groups из адаптера VK X.
- Если сервер прислал сущности, но не связал их ID с block (встречается в частичном CatalogKit-ответе), New показывает эти же серверные сущности отдельными рядами вместо перехода к искусственному `popular` fallback.
- Корректный вход в витрины New подтверждён bytecode `C14914e.loadAd()` + `C18378e.ad()` VK X: `catalog.getAudioAuto` → header `Catalog2Block.actions[].section_id` → `catalog.getSection(section_id)`. `actions`/`Catalog2Button.section_id` возвращены в DTO и добавлены в обход.
- `Catalog2Button` в LMG намеренно содержит только `section_id`: в VK X `action` — обязательный полиморфный объект, а `owner_id` — `Long`. Их ложная строковая типизация превращала корректный ответ CatalogKit в `VkResult.Error(0)` и UI ошибочно показывал сообщение о сети.
- Если CatalogKit ответил без пригодных item IDs, используются только подтверждённые прямые VK `audio.getRecommendations` и `audio.getPopular`; локальные mood/recent/history карточки в New не возвращаются.
- New больше не отменяет незавершённую загрузку из-за смены таба и показывает кнопку повтора VK-запроса вместо пустого экрана при пустом ответе.
- Локальная Gradle-сборка и GitHub Actions не запускались по правилу владельца; перед коммитом выполнять только `git diff --check` и статическую проверку вызовов.

# New: восстановление структуры витрин CatalogKit

**Текущий логический этап:** сопоставление присланных владельцем экранов LMG VK и VK X из `/storage/emulated/0/Download/Обзор (New)`. SHA данного коммита смотреть через `git log -1 --oneline` после применения.

- Причина технических подписей `slider` и `triple_stacked_slider` установлена по `VKLMG_Recovery`: каждый заголовок CatalogKit — отдельный layout `header`/`header_compact`/`header_large`/`header_extended` с полем `title`, без entity IDs. Он относится к следующему блоку с контентом. `MusicBackend.toHomeBlocks()` теперь сохраняет этот заголовок, связывает его со следующим блоком и передаёт name раскладки как `HomeBlock.layoutName`.
- `NewScreen` использует layout из реального ответа VK: promo/banner выводится как широкая витрина, `triple_stacked_slider` и list-варианты — как горизонтальные колонки из трёх строк с обложкой, артистом и длительностью, остальные витрины остаются горизонтальными карточками. Для curator-блоков используется круглая карточка, для music chart — номер позиции.
- UI остаётся Compose UI LMG VK: не перенесены верхние вкладки, нижняя навигация, цвета или VK X subscription UI. Из VK X восстановлены только порядок, заголовки и типы витрин CatalogKit.
- Кеш New обновлён до schema v2 и сохраняет `layoutName`. Кеш старого формата разово отвергается, чтобы после обновления не продолжать показывать устаревшие технические названия.
- Изменены: `MusicBackend.kt`, `BackendModels.kt`, `HomeCacheManager.kt`, `NewScreen.kt`, `MEMORY.md`.
- Проверка: сопоставлены все присланные target/current скриншоты, просмотрены `Catalog2Layout` и `Catalog2Layout_HeaderJsonAdapter` в Recovery; `git diff --check` прошёл. Локальная Gradle-сборка и GitHub Actions не запускались по прямому правилу владельца.

# New: полная выдача CatalogKit без повторов

**Текущий логический этап:** объединение всех VK-секций и страниц блоков для богатого экрана New. SHA данного коммита смотреть через `git log -1 --oneline` после применения.

- `MusicBackend.loadHomeContent()` теперь пагинирует `catalog.getSection` и `catalog.getBlockItems` (с защитой от повторного `start_from`), затем объединяет payload всех ответов до построения UI. Это устраняет ситуацию, когда IDs блока приходили отдельно от его сущностей и почти весь New отбрасывался.
- Страницы одного CatalogKit-блока объединяются по ID и сливают все entity IDs; порядок серверных блоков и заголовков сохраняется.
- Добавлена дедупликация внутри всей выдачи по типизированному VK ID: аудио, альбом, редакционный плейлист и промо-карточка больше не повторяются в разных секциях. Локальная медиатека, история и mood-карточки в New не подмешиваются.
- `AudioPlaylist` больше не помечается альбомом без проверки: релизы остаются альбомами, остальные VK-плейлисты открываются через существующий экран плейлиста. Для плейлистов и альбомов выбирается лучшая `photo/thumbs` обложка (`photo_1200/600/...`), а не только слабое поле `src`.
- `HomeItem.isPlaylist` сохранён в schema v3 кэша; старый кэш инвалидируется, чтобы устаревшие повторы и неверные типы не возвращались при входе в New.
- Изменены: `MusicBackend.kt`, `BackendModels.kt`, `HomeCacheManager.kt`, `NewScreen.kt`, `LiquidNavHost.kt`, `MEMORY.md`.
- Локальная Gradle-сборка и GitHub Actions не запускались по правилу владельца. Выполнены `git diff --check`, статический просмотр изменённых Kotlin-блоков и проверка ссылок на новый `isPlaylist`.

# New: кликабельные стрелки секций

**Текущий логический этап:** открытие полного содержимого VK-блока по стрелке заголовка New. SHA данного коммита смотреть через `git log -1 --oneline` после применения.

- Стрелка в `NewSectionHeader` теперь является реальной Compose-кнопкой.
- По нажатию открывается `ModalBottomSheet` с полным набором элементов выбранного VK-блока; локальные аудио, история и персональные карточки туда не добавляются.
- Элементы в листе используют те же VK-обложки и обработчики альбомов, плейлистов, артистов и проигрывания, что и основная витрина.
- Изменён `ui/screens/NewScreen.kt`; локальная Gradle-сборка и GitHub Actions не запускались по правилу владельца, выполнен `git diff --check`.

# New: максимальная полировка каталога и редакционные типы

**Текущий логический этап:** доведение вкладки New до полноценной VK-витрины с сохранением UI LMG VK. SHA данного коммита смотреть через `git log -1 --oneline` после применения.

- Заголовок New теперь показывает число реально загруженных VK-разделов, время последнего ответа и безопасную кнопку обновления; при refresh текущая выдача не исчезает, а отображается тонкий индикатор загрузки.
- Ошибка при обновлении поверх уже загруженного каталога показывается компактной плашкой с повтором, а ошибка на пустом экране — отдельной карточкой с понятным действием.
- Стрелки секций получили увеличенную область нажатия и иконку LMG; для баннерных секций тоже доступен полный список. Шторка показывает количество элементов, тип раскладки и кнопку «Слушать все» для доступных VK-треков.
- Восстановлены wire-поля CatalogKit, которые раньше терялись: клипы/видео, подкасты и их эпизоды, лонгриды, аудиокниги, авторы аудиокниг и обновления подписок. Они проходят типизированное объединение и глобальную дедупликацию, а не превращаются в локальные рекомендации.
- Для opaque-обложек редакционных сущностей добавлен безопасный поиск реального `url/src/uri` в payload; отсутствие изображения остаётся на штатном VK fallback `AlbumArtImage`, без сторонних источников.
- Текстовые подсказки CatalogKit не выводятся как фальшивые аудиокарточки: в New остаются только блоки, которые можно корректно представить витриной с VK-сущностью/обложкой.
- Изменены: `ui/screens/NewScreen.kt`, `engine/backend/MusicBackend.kt`, `network/dto/music/Priority1MusicDtos.kt`, `MEMORY.md`.
- Локальная Gradle-сборка и GitHub Actions не запускались по правилу владельца; выполнен `git diff --check` и статический просмотр новых DTO/мапперов.

# VK Mix: исправление загрузки настроек Aura

**Текущий логический этап:** устранение сообщения «Не найдено» после нажатия
настройки Mix на главном экране. SHA смотреть через `git log -1 --oneline` после
отдельной команды владельца на commit.

- `resolvePersonalMixSession()` теперь проходит связанные секции CatalogKit и
  их `next_from`, а не проверяет только корень и первую страницу. Цепочка остаётся
  подтверждённой VK: `catalog.getAudioAuto` → `catalog.getSection`.
- При нескольких Mix fallback предпочитает серверный `is_tunable=true`; найденный
  `common` по-прежнему имеет высший приоритет.
- Ошибки `catalog.getSection` больше не теряются через `getOrNull`: если Mix не
  найден, UI получает исходный код VK, а не искусственный локальный 404.
- `AudioGetStreamMixSettingsResponseDto.settings` приведён к официальному VK
  8.185: поле nullable. При `settings: null` Moshi больше не падает.
- Если `audio.getStreamMixSettings` отвечает 404, но официальный CatalogKit уже
  передал `AudioStreamMix.settings`, используется этот серверный snapshot.
- Кнопка настройки не запускает второй запрос во время уже активной загрузки.
  Ошибки VK Mix записываются в DebugLog с операцией и кодом; UI различает
  отсутствие персонального Mix и отсутствие настроек текущего Mix.
- Изменены `MusicBackend.kt`, `Priority1MusicDtos.kt`, `WaveHomeScreen.kt`,
  `HomeViewModel.kt`, `VkMixSettingsTest.kt`, `docs/vkx-port/01-music.md`.
- Проверка: только `git diff --check` и статическая сверка вызовов/nullable-типа.
  Локальный Gradle/build запрещён и не запускался.

# Публичные профили пользователей VK

**Текущий логический этап:** усиление профильной части без переписывания уже
работающего экрана собственного аккаунта. SHA смотреть после отдельной команды
владельца на commit.

- Основной источник — оригинальный VK 8.185 из
  `/root/VK_8.185_55039_analysis`: `UsersFieldsDto.java`,
  `UsersUserFullProfileDto.java`, `FriendsFriendStatusStatusDto.java` и
  `dto/user/UserProfile.java`. Они подтверждают поля `users.get`, статусы дружбы,
  поведение скрытого online и состав публичного профиля.
- `VkAccountProfile` расширен только используемыми подтверждёнными полями:
  `about`, `activities`, `interests`, `music`, `occupation`, `site`,
  `home_town`, `common_count`, `is_friend`, `friend_status`, `can_see_audio`,
  а также базовыми признаками закрытого/деактивированного профиля.
- Добавлены `UserProfileViewModel` и нативный `UserProfileScreen` в UI LMG VK:
  крупное фото, имя, verified, status/presence, факты профиля, публичные детали,
  VK/site links, Share и переход к существующему экрану музыки владельца.
- Друзья текущего аккаунта и участники сообщества открывают публичный профиль;
  сообщества из профиля открывают полный `GroupScreen`, а не только их аудио.
- `library/user/{id}` зарегистрирован в графе Library. Ссылки на пользователя и
  сообщество ведут в профиль, `/audios...` сохраняет прямой вход в музыку.
- `openOwnerAudioById()` параллельно получает метаданные владельца через
  `users.get`/`groups.getById`, поэтому прямой аудиоэкран заменяет `id123` или
  `club123` реальным именем и крупным изображением.
- `online_info.visible=false` теперь блокирует показ online/last seen у текущего
  профиля, публичного профиля и друзей, как в `UserProfile.P()` оригинального VK.

Изменены: `VkAccountProfile.kt`, `VkSocialDtos.kt`, `VkMethodsRegistry.kt`,
`UserProfileViewModel.kt`, `UserProfileScreen.kt`, `ProfileScreen.kt`,
`GroupScreen.kt`, `VkProfileRepository.kt`, `VkLinkResolver.kt`, `NavRoutes.kt`,
`LiquidNavHost.kt`, `AppRoot.kt`, `docs/PLAN.md`, `MEMORY.md`.

Проверка: `git diff --check`, отдельный whitespace-check двух новых Kotlin-файлов,
точечная сверка route/callback/API symbols и статический review изменённых файлов.
Gradle, сборка, компиляция и тестовые задачи не запускались по правилу владельца.
Ручная проверка: Profile -> Friends -> пользователь -> Music -> Back; Profile ->
Communities -> сообщество; Group -> участник; открыть `vk.com/id.../` и
`vk.com/club.../`; проверить закрытый профиль и пользователя со скрытым online.

# Профиль VK: публичные страницы пользователей

**Текущий логический этап:** усиление профильной части без замены существующего
экрана текущего аккаунта и без переноса UI официального VK. SHA смотреть после
отдельной команды владельца на commit.

Подтверждённые источники официального VK 8.185:

- `/root/VK_8.185_55039_analysis/jadx/sources/com/p056vk/api/generated/users/dto/UsersFieldsDto.java`
  подтверждает имена запрашиваемых полей `users.get`;
- `/root/VK_8.185_55039_analysis/jadx_parts/part16/sources/com/vk/api/generated/users/dto/UsersUserFullProfileDto.java`
  подтверждает wire-типы публичного профиля;
- `/root/VK_8.185_55039_analysis/jadx/sources/com/p056vk/api/generated/friends/dto/FriendsFriendStatusStatusDto.java`
  подтверждает значения friend status 0/1/2/3;
- `/root/VK_8.185_55039_analysis/jadx_parts/part11/sources/com/vk/dto/user/UserProfile.java`
  подтверждает разбор `photo_base`, `crop_photo`, статуса, online visibility,
  friend state, followers и public/private/deactivated состояний.

Что сделано:

1. `VkAccountProfile` расширен только подтверждёнными публичными полями: about,
   activities, interests, music, occupation, site, hometown, common friends,
   friend state и доступность аудио.
2. `VkMethodsRegistry.usersGetProfile()` использует отдельный
   `PUBLIC_PROFILE_FIELDS`; служебные profile buttons, сообщения и стена не
   запрашиваются.
3. Добавлены `UserProfileViewModel` и нативный `UserProfileScreen` в UI LMG VK:
   фото, имя, verified, статус, присутствие, реальные факты и details, share,
   внешний VK URL и отдельное действие Music через существующий OwnerAudio.
4. Добавлен маршрут `library/user/{id}`. Друзья текущего аккаунта и участники
   сообщества открывают публичный профиль; сообщества из Profile открывают
   существующий полноценный `GroupScreen`.
5. Ссылки на пользователя/сообщество отличены от `/audios...`: профильная ссылка
   ведёт на профиль, аудиоссылка — сразу к трекам. Завершающий `/` принимается.
6. `openOwnerAudioById()` параллельно получает metadata владельца; для сообщества
   использует `groups.getById`, поэтому direct audio screen больше не обязан
   оставаться с `club123` и пустой обложкой.
7. `online_info.visible=false` учитывается у текущего аккаунта, друзей и публичной
   страницы: скрытое присутствие и last seen не раскрываются.

Основные файлы: `VkAccountProfile.kt`, `VkSocialDtos.kt`,
`VkMethodsRegistry.kt`, `UserProfileViewModel.kt`, `UserProfileScreen.kt`,
`ProfileScreen.kt`, `GroupScreen.kt`, `VkProfileRepository.kt`,
`VkLinkResolver.kt`, `NavRoutes.kt`, `LiquidNavHost.kt`, `AppRoot.kt`,
`docs/PLAN.md`, `MEMORY.md`.

Проверка: `git diff --check`, отдельная whitespace-проверка двух новых Kotlin-
файлов и статическая сверка route/callback/API-field цепочек. Gradle, компиляция,
тестовые задачи и тяжёлые команды не запускались по прямому правилу владельца.

Ручная проверка владельцем: Profile -> Friends -> пользователь; открыть Music и
вернуться; Profile -> Communities -> сообщество; в Group нажать участника; затем
проверить приватный/удалённый профиль и пользователя со скрытым online status.

# Расширенный профиль VK: 10 функций оригинала

**Текущий логический этап:** все десять согласованных направлений реализованы в
коде поверх публичного профиля. Runtime-проверка на живом аккаунте обязательна;
SHA смотреть только после отдельной команды владельца на commit.

Подтверждённые источники VK 8.185:

- generated `friends.add`, `friends.delete`, `friends.getMutual`,
  `users.getFollowers`, `users.getSubscriptions` и их response DTO;
- `UsersUserFullProfileDto`, `UsersFieldsDto`, `UsersCareerDto`,
  `UsersSchoolDto`, `UsersUniversityDto`, `UsersRelativeDto`,
  `UsersProfileButtonDto`/`ActionDto`;
- `xsna/bjq.java`: точный `users.getFullProfile` с `user_fields`,
  `current_user`, friends/recommendations flags;
- `xsna/gs.java` и `xsna/k4m.java`: подтверждённые записи
  `account.saveProfileInfo(about)` и `status.set(text)`;
- `upload/impl/tasks/u.java`, `t.java`, `xsna/lha0.java`: owner image flow,
  multipart-поле `photo`, raw upload response и save endpoints.

Реализовано:

1. Friend state 0/1/2/3: отправка, принятие, отмена заявки и удаление с
   подтверждением результата VK.
2. Mutual friends через `friends.getMutual` с последующим typed `users.get`.
3. Пагинируемый `users.getFollowers`.
4. Пагинируемый extended `users.getSubscriptions` со смешанными пользователями
   и сообществами и внутренней навигацией.
5. Первые треки и плейлисты владельца прямо в профиле; play идёт через
   `MusicBackend.adoptTracks` и штатный `PlayerController`.
6. `status_audio`/`extended_status.audio` с обложкой и воспроизведением.
7. `cover`, `animated_avatar`, `image_status`; cover становится фоном шапки,
   avatar остаётся отдельным кругом.
8. Career, universities, schools, relation/partner, relatives, personal,
   contacts и descriptions из `users.getFullProfile`.
9. Server `profile_buttons` показываются только при безопасном URL action
   (`http`, `https`, `vk`); неизвестные action без URL не симулируются.
10. Свой профиль: status/about edit и owner photo/cover upload. Upload повторяет
    get-server -> signed multipart field `photo` -> save; cover проверяется на
    минимум 960x384, максимум 7000x7000, близкий к VK ratio 2.5:1, GIF запрещён.

Добавлены `UserConnectionsScreen.kt`, `UserConnectionsViewModel.kt`,
`VkProfileMediaUploader.kt`; расширены `UserProfileScreen.kt`,
`UserProfileViewModel.kt`, `VkAccountProfile.kt`, `VkSocialDtos.kt`,
`VkMethodsRegistry.kt`, `NavRoutes.kt`, `LiquidNavHost.kt`, `ProfileScreen.kt`,
`MusicBackend.kt`, `Priority2MusicDtos.kt`, `docs/PLAN.md`.

Проверка: `git diff --check` и отдельный whitespace-check каждого нового файла.
Gradle, сборка, компиляция и тестовые задачи не запускались. Обязательный manual:
friend request/accept/delete; три social list; status track; preview playback;
full details/buttons; status/about save; avatar upload; заранее подготовленный
cover 2.5:1; ошибки private profile и закрытого audio.

# VK ID multi-account

**Текущий логический этап:** реализован только multi-account из возможностей VK
ID. Остальные VK ID settings не переносились. SHA смотреть после отдельной
команды владельца на commit.

Архитектура:

- `EncryptedVkSessionStore` теперь реализует `VkMultiSessionStore`: весь список
  `VkAuthSession` и active user id лежат в одном AES/GCM payload под прежним
  Android Keystore key. Старый одиночный `VkAuthSession` читается как legacy и
  мигрирует при первой записи без потери текущего логина.
- `VkApiClient` по-прежнему видит только `session`, поэтому методы, refresh token
  и подпись запросов не получили параллельных token paths. `activate/remove`
  меняют active session атомарно внутри store lock.
- `MusicAuth.accounts` отдаёт UI только user id/name/domain/avatar/expiry и active
  flag; access/exchange/trusted tokens наружу не выходят.
- Profile -> VK accounts открывает picker: switch, remove и Add VK account.
  Добавление использует существующий OAuth/OTP/captcha flow, не выкидывая текущую
  сессию при ошибке. Sign Out удаляет только active session и выбирает следующую.
- Поздние ответы `auth.refreshTokens`, `users.get` и ProfileRepository не могут
  снова активировать/показать старый аккаунт: перед записью сверяется user id;
  profile refresh нового аккаунта ставится в очередь за старым.
- Смена аккаунта отклоняется, пока идёт cloud library/playlist operation. Это
  предотвращает запрос старым token с записью результата в новый account scope.

Изоляция данных:

- `favorite_tracks` обновлён до schema v8: добавлен `accountId`, уникальность
  стала `(accountId, trackId)` и `(accountId, cloudTrackId)`. Legacy rows с id=0
  присваиваются первому активному аккаунту. Heart flows перечитываются при switch.
- downloaded_tracks намеренно не менялся: скачанные файлы общие для устройства.
- локальный Playlist получил `remoteOwnerId`; merge/push/pull/delete и очередь
  удалений используют только active owner. Legacy remote links закрепляются за
  исходным аккаунтом до добавления второго.
- Home cache маркируется VK ID; Home/New/Library/Profile/Group/social screens
  перезагружаются по изменению `MusicAuth.profileId`.
- Mini-app token cache (year stats), broadcast status и home widget реагируют на
  active account и не переиспользуют account-bound состояние предыдущего.

Основные файлы: `EncryptedVkSessionStore.kt`, `VkApiClient.kt`, `VkAuthApi.kt`,
`MusicBackend.kt`, `AuthScreen.kt`, `ProfileScreen.kt`, `AppRoot.kt`,
`FavoriteTrackDatabase.kt`, `FavoriteTrackEntity.kt`, `LibraryRepository.kt`,
`PlaylistManager.kt`, `PlaylistSyncManager.kt`, `HomeCacheManager.kt`,
`HomeViewModel.kt`, account-sensitive screens, `VkBroadcastManager.kt`,
`VkMiniAppTokenProvider.kt`, `LmgApplication.kt`.

Проверка: `git diff --check` и точечная сверка всех изменённых сигнатур. Gradle,
сборка и компиляция не запускались. Manual: обновление поверх v1 single session;
Add второго аккаунта; неверный пароль не сбрасывает первый; switch A/B меняет
профиль, каталог, favorites и cloud playlists; remove inactive/active/last;
перезапуск сохраняет active account; switch во время sync показывает ожидание.

# Compact public profile + multi-account CI fix

- Собственный `ProfileScreen` по прямому указанию владельца визуально НЕ менялся;
  multi-account picker в нём сохранён.
- Компактным сделан только `UserProfileScreen` другого пользователя: hero 320/400
  dp заменён низкой cover-полосой, avatar 76/84 dp, одной строкой имени/status и
  двумя action-кнопками высотой 40 dp.
- Music preview ограничен 3 tracks и 2 playlists. Full profile details закрыты
  строкой `More information` и разворачиваются по запросу; дубли followers,
  mutual и friendship убраны из PROFILE facts, потому что они уже есть в SOCIAL.
- Ошибка CI run `31709706523` была не в multi-account логике: Kotlin 2.2 вывел
  intersection type для трёх SQLite `arrayOf(Long, String, String)`, а warnings в
  release считаются errors. Все три bind arrays явно объявлены `arrayOf<Any?>`.
- Проверка: только `git diff --check` и статическая сверка. Локальная сборка и
  Gradle не запускались по правилу владельца.

# Public profile structure from VK screenshot

Референс владельца: два JPEG из
`/storage/emulated/0/Download/Screenshot_20260814_161930_com_vkontakte_android_FragmentWrapperActivity.zip`.
От VK взята только структура/API, визуальные компоненты остаются LMG VK.

- Основной публичный профиль: cover, центрированный avatar с online dot, имя,
  status/domain/presence, строка More information, actions Music/Friend и
  компактная friends card. Стена, messages, calls, posts и VK tabs не переносились.
- `users.getFullProfile` теперь запрашивает `need_friends_block=1`. Wire shape
  подтверждён `UsersUserFullProfileFriendsBlockDto`: top-level `friends` object,
  внутри `offset` и `friends: List<UsersUserFullDto>`.
- Добавлен `VkProfileFriendsBlock`; если full-profile block отсутствует, preview
  честно догружается `friends.get(user_id, extended=1, count=3)`.
- Friends card показывает реальный total, mutual count и до трёх avatars; тап
  открывает пагинируемый новый kind `UserConnectionsKind.FRIENDS`.
- More information ведёт на отдельный route `library/user/{id}/details`, как на
  втором скриншоте. Там находятся status/domain, birthday/location/occupation,
  friends/mutual/followers/subscriptions, career/education/relation/relatives,
  contacts, languages, worldview, life/people priorities, smoking/alcohol,
  server URL actions и links.
- Собственный `ProfileScreen` визуально не менялся.
- Проверка: `git diff --check` и статическая сверка route/API/exhaustive branches;
  Gradle и локальная сборка не запускались.

# VK ID auth 8.14.1: полная повторная сверка

- По свежему декомпиляту `/root/decompiled_vkx_8.14.1` прослежена цепочка от
  `get_anonym_token` через `auth.validateAccount`, SmartCaptcha, `ecosystem.sendOtp*`,
  `ecosystem.checkOtp` и `oauth/token` до сохранения успешной сессии.
- Причина полевого симптома «после Я не робот сразу пароль вместо SMS» находилась
  в `MusicAuth.startAuthAttempt`: клиент заменял выбранный сервером OTP-метод на
  пароль, если пароль присутствовал среди альтернатив. Теперь используется только
  `next_step.verification_method`, как в исходной ветке.
- `auth.validateAccount` больше не отправляет отсутствующий в исходном билдере
  `accounts_trusted_hashes`.
- Дискриминатор `oauth/token` повторяет шесть исходных веток по значению `error`;
  неподтверждённая ветка `processing` удалена. Легаси-`need_validation`
  повторяет `oauth/token` с `validation_sid` и введённым `code`.
- Успешный access token сохраняется до попытки получить exchange token. Ошибка
  необязательного обмена больше не отменяет уже успешный вход; при успехе common
  token дописывается только в ту же активную сессию.
- Транспортная проверка уточнена: локальный UA метода в исходнике равен null, но
  общий Ktor UserAgent plugin добавляет native bundle slot 13. Поэтому сохранение
  `VkUserAgents.auth` для auth-запросов подтверждено, а прежняя документация
  «UA отсутствует» исправлена. Формат UA исправлен на `<manufacturer> <model>` и
  `<width>x<height>` без лишнего разделителя.
- Проверка: статическая сверка декомпилята и изменённых сигнатур, поиск оставшихся
  ссылок и `git diff --check`. Gradle, сборка и тестовые задачи не запускались по
  правилу владельца. Нужна ручная проверка: номер → Я не робот → SMS → код → пароль.

# Официальная Android-идентичность во всех VK-запросах

- Аудит подтвердил, что `VkApiClient.rawCall` уже всегда передаёт Android
  `api_id`, `device_id`, Android UA и служебные Android-заголовки; auth-методы
  отдельно задают подтверждённый auth UA и `client_id=2274003`.
- Добавлен `VkRequestIdentity`: общий OkHttp-клиент добавляет Android UA только
  VK-хостам и их CDN. Явный auth UA не перезаписывается, а при редиректе
  за пределы VK Android UA удаляется.
- Тот же host-aware UA подключён к прямым загрузкам audio/clip, playlist cover,
  обложек, Mix Lottie и signed profile upload URL. Last.fm, LRCLIB, update/config
  и другие сторонние сервисы его не получают.
- Домены сверены с локальным декомпилятом 8.14.1: `vk.com`, `vk.ru`,
  `userapi.com`, `vk-cdn.net`, `vkuser.net`, `vkuseraudio.com/.net`,
  `vkuserlive.com/.net`, `vkuservideo.com/.net` и их поддомены.
- Проверка: `git diff --check`, статический аудит всех Ktor, OkHttp,
  Media3 и `HttpURLConnection`-путей. Gradle и локальная сборка не запускались.
- CI compile-fix: в `MusicBackend.kt` добавлен пропущенный импорт
  `AuthFlowName`, используемого при разборе `NEED_REGISTRATION`.
- По полевой ошибке `3615 Error while sending code` повторно
  прослежены builders и `C8341l.mopub`: `validateAccount` и `ecosystem.*`
  не кладут `access_token` в form-body. Анонимный токен передаётся
  только в Bearer через отдельное поле `authorizationToken`.
- Builder `oauth/token` приведён к исходному для пустых значений:
  `sid`, `anonymous_token` и `code` кладутся в форму всегда. В UI auth-ошибки
  теперь сохраняют код VK в виде `[code] message`.
- Полный native-аудит 8.14.1 восстановил единый User-Agent slot 13:
  `VKAndroidApp/8.183-54468 (Android <release>; SDK <sdk>; ru; <abi>; <manufacturer> <model>; <width>x<height>)`.
  API/auth переведены на этот один формат; прежние версии и позиция Locale удалены.

# Безопасная трассировка VK ID

- `VkApiClient` пишет `VK AUTH WIRE` только для `get_anonym_token`,
  `auth.validateAccount`, `ecosystem.sendOtp*`, `ecosystem.checkOtp` и
  `api/oauth/token`.
- В журнал попадают endpoint, HTTP-метод, host, точный User-Agent, служебные
  заголовки, порядок form-параметров, HTTP-статус и разобранный код ошибки VK.
- Логин, пароль, SMS-код, captcha key, success token и client secret заменяются
  на `present`/`empty`. Bearer, sid, anonymous/access token, captcha sid и
  device id представлены только длиной и первыми 12 hex SHA-256; исходные
  значения в журнал не попадают.
- Трассировка охватывает повтор того же запроса после SmartCaptcha, поэтому по
  хэшам можно проверить сохранение anonymous token и sid между шагами.
- Проверка: `git diff --check` и статическая сверка областей логирования.
  Gradle, сборка и тестовые задачи не запускались по правилу владельца.

# VK ID auth: физический размер дисплея в User-Agent

- Полевой лог подтвердил успешный повтор `auth.validateAccount` после
  SmartCaptcha и ошибку VK `3615` непосредственно на `ecosystem.sendOtpSms`.
- Заголовки, Bearer anonymous token, порядок form-параметров, `sid`, `flow_type`
  и `sak_version` совпадают с восстановленным transport 8.14.1.
- Устранено подтверждённое различие native identity: размер экрана для Android
  User-Agent теперь получается через `WindowManager` и `Display.getRealSize`,
  а не из масштабированных системных display metrics.
- `VkUserAgents` получает application context в начале `LmgApplication.onCreate`;
  при недоступном display сохраняется прежний безопасный fallback.
- Проверка: `git diff --check` и статическая сверка ранней инициализации.
  Gradle, сборка и тестовые задачи не запускались по правилу владельца.

# VK ID auth: password после ecosystem OTP

- Ручная проверка подтвердила успешный вход без пароля по цепочке телефон,
  SmartCaptcha, SMS-код. Встроенный VK proxy при включённом обходе блокировок
  вызывал `3615`; при отключении обхода SMS отправляется сразу.
- На аккаунте с паролем после успешного SMS-кода `oauth/token` возвращал
  `[8] Invalid request`.
- Повторная сверка `C14467l.m4694l` подтвердила: в новом ecosystem OTP-flow
  параметр `code` в `oauth/token` отсутствует. Он передаётся только отдельной
  legacy-веткой 2FA. LMG ошибочно отправлял `code=` во всех запросах.
- `AuthAttempt.oauthCode` и аргумент `VkMethodsRegistry.oauthToken` сделаны
  nullable; новый SMS/password-flow теперь не кладёт `code` в form-body,
  legacy-ветка продолжает передавать введённый код.
- Проверка: `git diff --check` и статическая сверка единственного call site.
  Gradle, сборка и тестовые задачи не запускались по правилу владельца.

# Изоляция VK auth от обхода блокировок

- Ручная проверка владельцем доказала причину `ecosystem.sendOtpSms` error 3615:
  при включённом встроенном VK proxy SMS не отправляется, при его отключении
  тот же вход сразу продолжает работу.
- Все методы `auth.*`, `ecosystem.*`, `get_anonym_token` и `token` теперь
  помечаются внутренним direct-флагом независимо от пользовательского тумблера.
- `VkProxyInterceptor` удаляет внутренний заголовок до сетевой отправки и не
  применяет IP/domain override к помеченному запросу. VK этот заголовок не видит.
- Музыка, каталог, профили, изображения и остальные VK-запросы сохраняют
  прежнее поведение обхода блокировок.
- В безопасную трассировку входа добавлено `Route=direct`.
- Проверка: `git diff --check` и статическая сверка interceptor chain.
  Gradle, сборка и тестовые задачи не запускались по правилу владельца.

# Откат direct auth и безопасное значение proxy по умолчанию

- Ручная повторная проверка владельцем показала возврат `3615` после выделения
  auth-запросов в отдельный direct-route. Изоляция из предыдущего этапа полностью
  убрана; транспорт снова одинаков для auth и остальных VK API-запросов.
- Проксированное соединение теперь принудительно выключается один раз после
  обновления, включая установки с ранее сохранённым `enabled=true`. После этой
  миграции ручной выбор пользователя снова сохраняется.
- В Network пункт переименован в `Проксированное соединение`, действие — в
  `Обновить проксированное соединение`. Количество адресов, доменов, сведения о
  сертификатах и другие технические подробности больше не показываются.
- Проверка: `git diff --check`, статическая сверка полного удаления direct-флага
  и неиспользуемого proxy state из UI. Gradle и сборка не запускались.

# Стабильный VK-маршрут при активном системном VPN

- Системный VPN остаётся включённым, но при активном обходе VK-трафик приложения
  привязывается к валидной физической сети. Выбор больше не зависит от случайного
  порядка `ConnectivityManager.allNetworks`: приоритет имеют validated Wi-Fi,
  Ethernet и затем мобильная сеть.
- Без активного VPN процесс не закрепляется за интерфейсом. Повторный опрос не
  выполняет `bindProcessToNetwork`, если выбранный маршрут не изменился.
- API и обложки используют общий OkHttp connection pool. При смене маршрута пул
  очищается, URL-кэши аудио сбрасываются, а текущий играющий онлайн-трек получает
  новую подписанную ссылку с сохранением позиции.
- В Network показывается фактическое применение обхода, а не только положение
  тумблера. Gradle и сборка не запускались; выполнены `git diff --check` и
  статическая сверка call sites.

# Сквозная синхронизация локальной и VK-библиотеки

**Коммит:** `e15b081` (`Fix end-to-end VK library sync`).

- Исправлен сетевой контракт добавления: одиночный `audio.add` использует
  `audio_id`, `owner_id` и необязательный `access_key`; прежний неподтверждённый
  параметр `audio_ids` для этого метода удалён.
- Массовая досинхронизация использует подтверждённый VKScript `execute`: пачки
  по 25 `API.audio.add`, случайная пауза 1500–2500 мс между пачками.
- Перед отправкой полностью загружается библиотека текущего аккаунта. Уже
  существующие облачные копии связываются с локальными строками по cloud ID и
  метаданным; повторный `audio.add` для них не выполняется.
- После `execute` библиотека повторно опрашивается с задержками 1.5/3/6 секунд.
  Только подтверждённый реальный owner/audio ID сохраняется как cloudTrackId.
  Частичные ошибки остаются pending и безопасно повторяются после нового pull.
- Все синхронизации сериализованы одним mutex. Локальные like/unlike проходят
  через одну persistent SQLite-очередь, а повторные события объединяются.
- Старые строки, помеченные synced, но отсутствующие в VK, переводятся в pending
  и автоматически досылаются. Это покрывает накопившееся расхождение счётчиков.
- Изменены `LibraryRepository.kt`, `MusicBackend.kt`, `VkAudioApi.kt`.
- Проверка: `git diff --check`, статическая сверка всех call sites и отсутствие
  `audio_ids` в ветке `audio.add`. Gradle, сборка и тесты не запускались по
  правилу владельца. Следующий шаг — установить сборку и сверить итоговый счётчик
  с официальным клиентом; журнал `LIBRARY SYNC` показывает cloud/local/pending/
  submitted/failed без токенов и пользовательских идентификаторов.

# Изоляция плейлистов между VK-аккаунтами

**Коммит:** `b1ad075` (`Scope playlists to active VK account`).

- Устранён общий SharedPreferences-ключ `data`, из-за которого интерфейс после
  смены аккаунта продолжал показывать плейлисты предыдущего пользователя.
- `PlaylistManager` хранит и загружает только `data_account_<userId>` активного
  аккаунта. Переключение выполняется синхронно до публикации нового `profileId`,
  поэтому старый список не успевает попасть в UI или синхронизацию нового токена.
- Старый общий JSON мигрирует один раз: связанные облачные плейлисты раскладываются
  по `remoteOwnerId`, а локальные и legacy-связи закрепляются за текущим аккаунтом.
  Уже существующие account-scoped записи имеют приоритет при совпадении local ID.
- Очередь удалений остаётся общей на диске, но её элементы уже содержат owner ID
  и выбираются только для активного аккаунта.
- `PlaylistSyncManager` сбрасывает отчёт, ошибку и время прежней синхронизации при
  смене пользователя. Новая синхронизация видит только список активного user ID.
- Изменены `PlaylistManager.kt`, `PlaylistSyncManager.kt`, `MusicBackend.kt`.
- Проверка: `git diff --check`, поиск старых call sites и общего runtime-ключа,
  статическая сверка порядка account switch. Gradle, сборка и тестовые задачи не
  запускались по правилу владельца. Ручная проверка: создать разные локальные
  плейлисты в аккаунтах A/B, несколько раз переключиться и перезапустить приложение;
  каждый аккаунт должен видеть и синхронизировать только собственный список.

# Полная изоляция контента и единая синхронизация VK-аккаунта

**Коммит:** `496c8fe` (`Scope all VK content to active account`).

- `MusicAuth.applySession` стал единой точкой переключения account-bound
  состояния до публикации нового `profileId`. Синхронно переключаются избранное,
  плейлисты, Room, Wave, плеер и общий менеджер синхронизации; backend/profile
  cache очищается только при реальной смене user ID.
- Room обновлён до schema v5. `cached_tracks`, `listening_history`,
  `playback_history`, `track_stats` и `listen_history` получили `accountId`,
  составные ключи/индексы и безопасное присвоение legacy-строк первому активному
  аккаунту. История, статистика и персонализация больше не смешиваются.
- По аккаунтам разделены Home cache, search history/cache, dismissed banners,
  legacy history/favorites и сохранённая онлайн-очередь. При switch очищаются
  результаты экранов, пагинация, рекомендации, профильные каталоги и поздние
  ответы старых запросов.
- Онлайн-очередь предыдущего аккаунта останавливается; локальная MediaStore
  музыка может продолжить играть. Событие завершения старого трека записывается
  по владельцу очереди, поэтому быстрый switch не загрязняет новую статистику.
- Новый `AccountSyncManager` последовательно запускает playlist sync и полную
  двустороннюю library sync. Он используется после входа/switch, восстановления
  сети и ручного refresh. Существующая library sync сначала делает полный pull,
  связывает копии по cloud ID/метаданным и отправляет только pending строки,
  поэтому повторное добавление уже существующих треков не выполняется.
- Скачанные физические файлы, локальный MediaStore и настройки приложения
  намеренно остаются общими для устройства; облачный и персонализированный
  контент разделён.
- `HomeCacheManager.init` добавлен в startup. В затронутом account/sync-коде не
  добавлялись комментарии; встретившиеся служебные комментарии удалялись.
- Проверка: `git diff --check`, поиск старых DAO-сигнатур, дубликатов импортов,
  account-sensitive call sites и новых комментариев. Gradle, сборка и тестовые
  задачи не запускались по правилу владельца.
- Ручная проверка: аккаунты A/B должны иметь разные Home, поиск, избранное,
  плейлисты, историю, статистику, рекомендации и online queue; после добавления
  трека дождаться sync и сверить библиотеку в официальном клиенте; повторный sync
  не должен менять облачный счётчик.
- CI compile-fix: в `VpnBypassManager.kt` добавлен пропущенный импорт
  `NetworkVitality`, используемый после смены привязанного сетевого маршрута.
- Полевой лог парольного аккаунта выявил UI/backend routing bug: после успешного
  ecosystem OTP пароль отправлялся повторным `ecosystem.checkOtp`, потому что
  введённый SMS-код оставался в `AuthScreen` и имел приоритет над password-step.
  Код теперь очищается при `NeedPassword`, а `AuthAttempt.awaitingPassword`
  делает password continuation приоритетным и направляет его в `oauth/token` с
  `grant_type=phone_confirmation_sid`, новым sid и без ecosystem `code`.
- После первой полной library sync официальный VK показал все композиции, но
  локальный My Audio остался больше из-за нескольких pending SQLite-строк,
  совпадающих с одной облачной копией. После pull синхронизация теперь удаляет
  только дополнительные pending-строки, когда уже существует отдельная synced
  строка с тем же реальным `cloudTrackId` и совпадают title/artist/duration.
  Разные дубли, реально присутствующие в облачной библиотеке VK, сохраняются.
- Полевой password OAuth успешно израсходовал одноразовый sid, но
  `finishSignIn` затем отбросил готовый access token из-за параллельной library
  sync и показал `Wait for library synchronization to finish`; повтор того же
  password-step закономерно получил `sid is invalid`. Готовая сессия теперь
  сразу сохраняется в encrypted multi-account store как неактивная, ожидание
  sync происходит без потери токена, затем аккаунт активируется и получает
  exchange token. Ручные switch/remove по-прежнему блокируются во время sync.
- Причиной облачных дублей оказалась повторная отправка `audio.add` после
  неоднозначного результата пачки: VK мог принять часть запросов, а клиент при
  ошибке ответа оставлял всю пачку pending. Теперь каждая отправленная строка
  обязательно подтверждается новым полным pull, локальные копии уже найденного
  облачного трека схлопываются до отправки, автоматические повторы убраны, один
  sync ограничен пятью мутациями.
- Экран входа теперь полностью изолирует auth от library/playlist sync с момента
  открытия до закрытия. Уже начатая синхронизация прекращает новые мутации, а
  фоновые sync-запросы во время входа не ставятся в очередь. Очистка существующих
  точных облачных дублей запускается только ручным обновлением библиотеки и
  удаляет не более пяти новых копий за проход, сохраняя самую старую.
- Постоянный полный account sync отключён: он больше не запускается при старте,
  смене аккаунта, восстановлении сети или простом открытии библиотеки/плейлистов.
  Полный двусторонний pull/push остался только на ручной кнопке обновления.
  Обычный like/unlike отправляет только затронутый трек, без захвата старого
  pending-хвоста; последовательные ручные действия разводятся паузой. Изменения
  плейлистов по-прежнему синхронизируются по событию пользователя.
- В `VK and profile` добавлен отдельный ручной экран `Сканирование дублей`.
  Сканирование делает только paginated `audio.get`, группирует облачные записи
  по нормализованным title/artist, точной длительности и explicit-флагу, затем
  показывает группы до любых изменений. Удаление требует подтверждения, за раз
  обрабатывает не более пяти новых копий с паузами и сохраняет самую старую.
  Удалённая облачная копия также удаляется из account-scoped SQLite, поэтому
  последующий ручной sync не добавит её обратно. Обычный library refresh дубли
  больше не удаляет: очистка доступна только на экране сканера.

# Единый Back и интерактивный predictive back

- Полноэкранные Search, Settings, Profile и Auth переведены с независимых
  флагов на единый стек. Возврат закрывает только верхний слой, поэтому сценарии
  Settings → Profile и Profile → Settings возвращают на реальный предыдущий
  экран, а не на главную.
- Переходы из Settings, Profile и Search в маршруты NavHost временно скрывают,
  но не уничтожают исходный слой. После pop восстанавливается тот же экран с его
  внутренним состоянием. Аналогично диалоги аккаунтов снова открываются после
  отмены повторной авторизации или удаления.
- Для полноэкранных слоёв добавлен PredictiveBackHandler: движение напрямую
  следует progress системного edge-жеста, при остановке пальца замирает, при
  отмене пружинно возвращается, при завершении уходит вправо со scale, alpha и
  динамическим скруглением. Стрелка вызывает тот же завершающий callback.
- Pop-переходы NavHost используют stationary previous destination и уход
  текущего экрана вправо со scale/fade. Queue и Lyrics внутри FullPlayer не
  менялись и не оборачивались новой анимацией.
- Внутренние разделы Library, Profile, Settings, Auth и редактора LRCLIB получили
  единые функции возврата для стрелки и системного Back. В частности, Back на
  OTP/password/captcha возвращает к номеру, а Back из подраздела Library — на
  главный экран библиотеки.
- Проверка: `git diff --check`, статическая сверка всех изменённых обработчиков,
  состояний оверлеев и API activity-compose 1.9.3. Gradle, сборка и тестовые
  задачи не запускались по правилу владельца.
- Полевое исправление после `5f9901b`: нижняя вкладка Settings использовала
  отдельный `SettingsScreen` внутри NavHost и обходила predictive-слой, а
  обработчик оверлея регистрировался до локальных BackHandler. Settings теперь
  всегда открывается единым root overlay поверх текущего экрана; predictive
  callback композится после содержимого и включается только на корне. Profile,
  Auth и LRCLIB сообщают наличие внутреннего подэкрана, чтобы на нём приоритет
  оставался у локального Back, а на корне — у интерактивного жеста.

# Подтверждённая лирика: сохранять резервную копию (20 сентября 2026)

- Пользователь проверил APK `LMG-VK-debug-lyrics-motion-restored-2026-09-20.apk` и подтвердил, что растяжение слов исправлено. Просит беречь резервную копию как полностью рабочий вариант.
- Исходный архив: `/root/backups/lmg-vk-lyrics-backup-20260909_103634.tar.gz`; рядом сохранён SHA-256. Не удалять и не перезаписывать.
- Дополнительная проверенная копия исходного архива, снимок текущих исходников, подтверждённый APK, инструкция восстановления и SHA256SUMS: `/root/LMG-safe-backups/lyrics-confirmed-2026-09-20/`. Файлы доступны только для чтения.
- Перед будущими экспериментами с декомпилом iOS сохранять новые версии отдельно. Эталонную копию не менять. Восстанавливать в отдельный каталог с проверкой SHA256SUMS.
- Рабочая формула: подъём -2dp; задержка букв min(0.4 * duration / count, 400ms); возврат длится min(duration, 3000ms), easing (0.25, 0.1, 0.25, 1). Замена возврата на 300ms вызывала заметное резкое сжатие отдельных букв.

## 2026-09-20: проба четырёх изменений лирики по iOS

Пользователь разрешил четыре изменения после аудита и особо потребовал сохранить исправление дрожания букв. Пробная сборка не является новым подтверждённым baseline до отзыва пользователя.

Изменения: подъём m1/k14/c7, критическая пружина растяжения с response=min(duration,3s) и stagger*(index+1), общее свечение слова до0.4, пружина перехода строк в зависимости от паузы для timedWords. Raster/SpatialWave/дробное размещение букв сохранены. Никаких дополнительных визуальных изменений и комментариев в коде.

Доказательства и границы адаптации: /root/LMG-previews/ios-lyrics-trial/IMPLEMENTATION.md. В старом отчёте неверно названа ветка static: enum подтверждает timedWords=2. Формирование gap в Android адаптировано по соседним основным строкам; не заявлять весь UIKit renderer как точную копию.

69 тестов прошли, BUILD SUCCESSFUL 5m42s, подпись совпадает с рабочей. APK: app/build/outputs/apk/ios-lyrics-trial/LMG-VK-debug-ios-lyrics-trial-2026-09-20.apk. MD5 5b6f71b486aac382eafbdaaca6defa9d совпал с Google Drive. Ссылка: https://drive.google.com/file/d/1QJpqQAtOqnz8sleolQA0c1Bl1We2CbPW/view.

Защищённый baseline /root/LMG-safe-backups/lyrics-confirmed-2026-09-20 не изменён; все SHA256 проверены. Эмулятор не запускался. Плавность на HONOR Android16 ещё проверяет пользователь.

Параллельно пользователь обсуждает лицензирование AMLL с юристом; изменений в веб-проекте не просил. Проверена история upstream: commit7a22f365041275b32ec36918c4b05b1830cb4c63 от19.09.2024 заменяет корневой LICENSE с GPLv3 на AGPLv3; текущий main/core package.json содержит AGPL-3.0-only. Юридических выводов о его конкретной интеграции не делали.

## 2026-09-20: обратная связь и Apple Music Web

Пользователь: новые растяжения более чем устраивают, дальнейшее восстановление UIKit отменено. Подёргивания в начале автопрокрутки пока только предположение: он посмотрит внимательнее; до его уточнения ничего не менять. После ios-lyrics-trial исходники анимаций не изменялись.

По просьбе пользователя исследован Apple Music Web через отдельный Chromium и Chrome DevTools Protocol. Данные: /root/LMG-previews/apple-music-web-2026-09-20/FINDINGS.md. Найденный MusicKit Web путь растягивает DOM-буквы1→1.05→1,500мс на рост и500мс на возврат, задержка duration/count*index. Скролл350мс easeInOutQuad через requestAnimationFrame. Это не те же параметры, что iOS. Реальные computedStyle проверены на собственной синтетической строке; аккаунт Apple и воспроизведение подписочного трека не использовались. В LMG ничего не переносилось.


## 2026-09-21: подпевки и отклик на нажатие строки

Пользователь разрешил только пункты1и2. По декомпилу MEE добавлено появление подпевок от scale0.9 (m1/k30/c9), исчезновение critical response0.2s; убран дополнительный slide. Нажатие строки scale0.95 m1/k322/c24; отпускание m2/k300/c50 после100ms, через существующий combinedClickable. Формулы растяжения, raster/SpatialWave, активность строк и автопрокрутка не менялись: проверено хешами; в KaraokeLineText изменён только блок подпевок. Это адаптация параметров в Compose, не полный перенос UIKit.

Доказательства, before-копии, хеши, тесты и инструкция: /root/LMG-previews/lyrics-backing-and-press-20260920-213403/IMPLEMENTATION.md. Защищённая резервная копия прошла SHA256-проверку и не менялась.74теста прошли, BUILD SUCCESSFUL5m54s; подпись прежняя. Эмулятор не запускался, визуально на телефоне ещё не проверено.

APK: app/build/outputs/apk/lyrics-backing-press/LMG-VK-debug-lyrics-backing-press-2026-09-21.apk. MD5:864d162f904ce32348c5b71daf8f027b совпадает с GoogleDrive. Ссылка: https://drive.google.com/file/d/133rVxnthMe2Wfy6u33nGFpEDNmZJnt4V/view.

Пользователь поправил дату: у него уже21сентября, хотя на сервере ещё20сентября UTC. Для названия доставленного APK использовано21сентября; исторические UTC-каталоги не переименовывались.


## 2026-09-21: НЕУДАЧНАЯ проба подпевок/нажатия — полный откат

Пользователь сообщил о сильных лагах в lyrics-backing-press и сломанной плавности автоскролла.74 unit-теста не проверяли frame time или взаимодействие layout-анимаций со скроллом; не считать эту сборку рабочей и не предлагать её повторно. Конкретная причина не профилирована. Не утверждать, что неизменные исходники автоскролла исключают регрессию от других эффектов.

Откат выполнен строго из before-копий: KaraokeLineText.kt, LyricsLineItem.kt; удалены только новые LyricsSecondaryMotion.kt и LyricsSecondaryMotionTest.kt, предварительно сохранены в /root/LMG-previews/lyrics-backing-press-rollback-2026-09-21/rejected-source/. Проверены все before-хеши и19записей trial-source-hashes.json: полное совпадение с предыдущей ios-lyrics-trial. Растяжения этой версии, которые пользователь одобрял, сохранены. Другие файлы приложения и защищённые бэкапы не менялись.

Для доставки взят оригинальный APK ios-lyrics-trial без пересборки, только имя копии изменено на LMG-VK-debug-lyrics-rollback-2026-09-21.apk. SHA256 e0dce5b0a12b915c59b92af7d4d4f6ab8663cfc27d9458bacced7dc1d793d5ca; MD5 5b6f71b486aac382eafbdaaca6defa9d. Новые тесты не запускались: это побайтово прежний бинарник; восстановление исходников проверено хешами. Подробности в /root/LMG-previews/lyrics-backing-press-rollback-2026-09-21/rollback.json. Не возвращать два отклонённых эффекта без нового согласования и проверки производительности.

Google Drive отката (MD5 проверен): https://drive.google.com/file/d/10wgZW0WtUm6Iav-PTTwgjVXvavkRODO2/view


## 2026-09-21: пользователь подтвердил плавность после отката

После установки LMG-VK-debug-lyrics-rollback-2026-09-21.apk пользователь сообщил: «вот щас лучше, даже лагов нет», приложив экран Apple TTML для No Love (Eminem feat. Lil Wayne). Считать эту версию подтверждённой пользователем текущей рабочей точкой лирики. Это исходный ios-lyrics-trial, SHA256 e0dce5b0a12b915c59b92af7d4d4f6ab8663cfc27d9458bacced7dc1d793d5ca. Сохранить её растяжения и автопрокрутку. Не возвращать отклонённые изменения подпевок/нажатия. Скриншот сам по себе не измеряет плавность; отсутствие лагов подтверждено словами пользователя. Старые защищённые бэкапы остаются нетронутыми. Код в этом ходе не менялся.


## 2026-09-21: пользователь попросил адаптировать эффекты под Android, не отказываться от них

Уточнение пользователя: «неудачные эффекты надо адаптировать под андройд самому специально, с первого раза нечего не будет». Это разрешение на новую реализацию; предыдущий запрет возвращать отклонённый код не означает отказ от самой функции. Решено проверять эффекты по одному: первая новая проба содержит только отклик строки. Подпевки пока сохранены как в подтверждённом rollback; их адаптация — следующий отдельный этап после проверки первой пробы.

Новая реализация: LyricsPressIndication — IndicationNodeFactory/DrawModifierNode; combinedClickable получает interactionSource=null, узел/источник создаётся лениво. Нет добавленных composable state/LaunchedEffect на каждой строке. Scale читается только в draw, размеры/скролл не меняет. При press0.95 с m1/k322/c24, release m2/k300/c50 без искусственной задержки100ms; Cancel сразу snap1; detach сбрасывает визуальное состояние. В существующем LyricsLineItem только2добавленные строки; прочие хеши renderer совпадают с рабочим rollback. Растяжения и backing transition не изменены.

Отчёт и before-копии: /root/LMG-previews/lyrics-android-press-2026-09-21/IMPLEMENTATION.md. Точную причину прошлых лагов не установили: изменение высоты подпевок пружиной с Lookahead/скроллом и дополнительное состояние строк — подозреваемые по коду, без trace не выдавать их за доказанный root cause.

BUILD SUCCESSFUL6m,69unit-тестов прошли; новый LyricsPressLayoutTest с проверкой отсутствия remeasure/recompose на press/release/cancel скомпилирован, НЕ выполнен: adb devices пуст, эмулятор не запускался. НЕ объявлять пробу плавной или подтверждённой до отзыва пользователя. APK LMG-VK-debug-lyrics-android-press-2026-09-21.apk, MD5 dfd4277d14482a4617387a7f95a1e59d, SHA256 c196256362b5cbecc47c47ded8b3f528865dfbe1eea6746ba3b0fa777427e2e9; подпись прежняя. Рабочий rollback и защищённые бэкапы сохранены.

Google Drive новой отдельной пробы нажатия (MD5 проверен): https://drive.google.com/file/d/1jvcs7IhisbJ4Bmz0nc7TCLGQmEUzE8tR/view


## 2026-09-21: авторы в конце лирики и сравнение TTML-парсеров

По просьбе пользователя добавлен блок «Авторы: …» после последней строки, скрытый при отсутствии метаданных. AccompanistLyricsAdapter сохраняет имена для RawTtml/Rich/Legacy; AppleTtmlParser.readSongwriters читает head через существующий защищённый DOM, поддерживает префиксы и XML-сущности, удаляет пустые записи и дубли. KaraokeLyricsView получил optional bottomContent; LyricsScreen выводит приглушённый переносимый текст. Авторов не добавляли в вокальные строки/таймлайн. Отдельного автоперехода на титры нет, скролл следует последней вокальной строке.

Растяжения, подсветка слов, backing-вокал, focus/autoscroll алгоритм и пружины не менялись. У footer используется существующий springPlacement. Перед изменением сохранено79исходников в /root/LMG-previews/lyrics-songwriters-2026-09-21/before;74из79совпадают после функционального изменения. Защищённый backup проверен по SHA256SUMS. Текущая ветка main, исходный HEAD3b3e9d2; новый коммит не создавался, другие несохранённые изменения сохранены.

Сравнены установленные AMLL lyric1.1.0/ttml1.0.1 и Kotlin Accompanist на12одинаковых искусственных TTML. Подробно: docs/TTML-PARSER-COMPARISON-2026-09-21.md. Mini App сильнее в ruby, вложенных словах, XML-сущностях, фонетике по таймингам, plain x-bg; но теряет строки без itunes:key, ms-тайминги и дополнительные x-bg. Богатый parser и упрощённая renderer-модель различаются: Mini App выбирает первый перевод, Accompanist затирает последним. Следующими кандидатами рекомендованы XML mixed content/entities, затем plain x-bg/timing fallback и языковые слои; они пока НЕ внедрены. AMLL-код в APK не переносился.

79выбранных unit-тестов прошли; assembleDebug и compileDebugAndroidTestKotlin успешны,7m16s. Instrumentation только скомпилирована, на телефоне не запускалась; эмулятор не использовался. Плавность и внешний вид на HONOR ещё не подтверждены. Реальный embedded TTML из1495267448.mp3(Godzilla) дал5авторов, совпадающих со скриншотом. Повторная проба подтвердила неизменность вокальных результатов на12fixtures.

APK: /root/LMG-VK/app/build/outputs/apk/lyrics-songwriters/LMG-VK-debug-lyrics-songwriters-2026-09-21.apk. SHA256 4079251006dbfbdd58fdca42efb76a0dff83b67d8f90399bf9cac8fe62e18c6f; MD5 12baff4d15b5dad5aa12530ddd2699e2. Подпись совпадает с предыдущим APK. Google Drive (remote MD5 проверен): https://drive.google.com/file/d/1ZT1MJyu3KcVBQEUQgbXpedHrmFg2NUy7/view


## 2026-09-21: исправлены пропавшие авторы, первый шаг XML и возврат пружинного скролла

Пользователь показал отсутствие авторов в прошлом APK. Причина подтверждена по Android libcore и воспроизведена на прежнем compiled classes.jar: DocumentBuilderFactoryImpl не поддерживает использованные setFeature, readSongwriters проглатывал ParserConfigurationException. JVM-проверка прошлой сборки этот случай пропустила. Убраны несовместимые настройки; DOCTYPE отклоняется до DOM, внешние сущности блокируются EntityResolver. Исправление также действует на rich/untimed fallback. Кэш содержит исходный TTML и не требует очистки.

Разрешённый первый пункт сравнения с Mini App: порядок смешанного/вложенного текста, однократное декодирование XML-сущностей включая numeric/Unicode, CDATA, quoted > в атрибутах. Тайминги не изменялись. Метаданные переводов/романизации отделены от вокала. Не переносили следующие пункты (plain BG, inferred timings, ruby, выбор языков).

Дополнительная просьба пользователя: вернуть прежний пружинный автоскролл. В KaraokeLyricsView восстановлен сохранённый вариант до ios-lyrics-trial: stiffness=max(120-distance*20,20), damping0.95, с отставанием более далёких строк. Gap-dependent spring больше не применяется в view. Footer следует той же политике. Растяжение, fractional placement, focus, waiting, ручная прокрутка и seek не изменялись.

В Godzilla из реального API все5авторов: A. Villasana, D. Doman, Jarad Higgins, Luis Resto, Marshall Mathers. С ограничениями Android factory до исправления список пустой, после полный. Для Godzilla API и embedded TTML по1067записей строк/слов: весь текст и тайминги совпали до/после.133 JVM-теста прошли; assembleDebug и compileDebugAndroidTestKotlin успешны. Добавлен Android UI-тест авторов, только скомпилирован, не запускался. Эмулятор не использовался; плавность на телефоне ещё не подтверждена.

Отчёт/снимки/сравнения: /root/LMG-previews/lyrics-xml-text-2026-09-21/IMPLEMENTATION.md. Защищённый backup прошёл SHA256SUMS и не менялся. APK: app/build/outputs/apk/lyrics-credits-spring/LMG-VK-debug-lyrics-credits-spring-2026-09-21.apk. SHA256 d48c7066ec7f93f5aae5d351ef365bfc502b1b28464ea97e30a8170bd0c315f3, MD5 332c2472da3dd41a2f2426af5272d0b3; размер/MD5 совпали на Google Drive. Ссылка: https://drive.google.com/file/d/1hlwrdGaE4bQK4sdt0seGtz6TeOTMa7SX/view. Коммита не было; чужие изменения сохранены.


## 2026-09-21: отзыв о lyrics-credits-spring на телефоне

Пользователь подтвердил: авторов теперь видно. В первые 3–6 секунд наблюдал небольшие подтормаживания автоскролла, затем движение стабилизировалось и стало нормальным. Это подтверждение показа авторов и плавности после начального периода, а не отсутствия лагов вообще. Причина стартовых подтормаживаний не установлена; не выдавать предположение о прогреве/кэшировании за диагноз. В ответ на этот отзыв код и параметры анимаций не менялись.


## 2026-09-21: TTML, разрешённый второй пункт

После обсуждения начальных подтормаживаний пользователь решил не рисковать: кэширование/прогрев и анимации пока не менять. Повторное открытие после смены трека было плавным; это согласуется с прогревом, но точный ресурс не диагностирован. Затем пользователь разрешил второй пункт сравнения TTML.

Изменён только production TTMLParser.kt: x-bg без word-span сохраняется одной фразой с собственными границами или fallback по словам/родителю; недостающие p begin/end вычисляются по временам вокальных дочерних элементов перед сортировкой и agent alignment. Явные границы (включая0) сохраняются. Перевод/фонетика не становятся вокалом, полностью untimed текст не получает нулевые строки. Основной нетаймированный текст вместе с BG теперь сохраняется одной фразой. Несколько подпевок остаются отдельными.

Добавлены12core-тестов и1adapter-тест.146JVM-тестов прошли, assembleDebug/compileDebugAndroidTestKotlin успешны. Android-тесты не запускались, эмулятор не использовался. Среди12фикстур изменились только plain_background и child_timing, оба теперь совпадают с Mini App. Godzilla API и embedded TTML: по1067записей, весь текст и время до/после совпали.77из78сохранённых исходников неизменны; растяжения, пружинный скролл, авторы и кэш не тронуты. Защищённый backup проверен поSHA256SUMS.

Материалы: /root/LMG-previews/lyrics-background-timing-2026-09-21/IMPLEMENTATION.md. APK: app/build/outputs/apk/lyrics-background-timing/LMG-VK-debug-lyrics-background-timing-2026-09-21.apk. SHA256 fa9ff4d75302b1344c1eba2eff2e2d9ae39408ea974b94d7bd2fdd5227e7dab7, MD5 a31d098ef6b98f1102ab55441df2fd01. Размер и MD5 на Google Drive совпали. Ссылка: https://drive.google.com/file/d/1jQCA3xRPV5JQd3x_AqJodfAFmXaL8dTJ/view. Подпись прежняя. Пользователь ещё не подтвердил эту сборку на телефоне. Коммит не создавался, остальные изменения сохранены.


## 2026-09-21: расследование холодного входа FullPlayer — ожидается лог телефона

После TTML шага2 пользователь вновь заметил первые6–8СЕКУНД лагов (минуты были опечаткой), затем плавно. По его уточнению начинается уже с MiniPlayer→FullPlayer. Просит выяснить причину. Ранее просил пока не рисковать прогревом/кэшированием; сейчас исследуем, формулы анимаций не менять. Нельзя называть кэш подтверждённой причиной: повторный вход плавный, но конкретный ресурс не измерен.

Факты кода: лирика не монтируется до showLyrics; общий анимированный фон стартует при входеFullPlayer. Загрузкаартворка, программныйblur/генерацияbitmap выполняютсявworker; при открытиилирикидобавляютсяprelayoutвсехстрок, измерениявидимыхстрок, растрыбукв/glowпромахикэша. Возможныепричины, не доказанныйrootcause. ADBdevices пуст; эмуляторнеиспользовать.

Собрана диагностическая версия без изменения поведения/кэшей/пружин/растяжений. FrameMetrics наwindow+длительностиbackground_load/init/frame, lyrics_load/parse/prelayout/measure/wrap, glyph_face/glow_miss. Начинает16-секунднуюсессиюприFullPlayer, отмечаетLyricsвходвтойжесессии; еслиперваяистекла,Lyricsсоздаётновую. После16сек/закрытияlistenerснимается, handlerthreadостанавливается. Метрикипокадровонекопируютсявлог: итоговыесекундныеагрегатыPLAYER_STARTUPпишутсявсуществующийDebugLog. Автоматическойотправкинет,ADBненужен. Суффикс.uiвload/prelayoutозначаетпотокНАЧАЛАсинтерваломожидания, неCPUблокировку. Длительностиперекрываются;не складывать. FIRST_DRAWотдельно,дропыметриквидны,GCдельтывконце.

150JVMтестовпрошли,assembleDebugиcompileDebugAndroidTestKotlinуспешны. Намобильномещёнепроверено,причинанедоказана. Нуженлогпользователя: установить, воспроизвестиобычныйMiniPlayer→FullPlayer→лирикапуть,подождать20сек;закрытьFullPlayerиповторитьбезперезапускаещё20сек;Настройки→Диагностика→Отладочныйлог→Поделиться. Логвоперативнойпамяти,экспортироватьдоперезапуска. Пользовательспросил«логзапишетсябезадб?»—ответилдаивыдалэтотпуть.

Материалы:/root/LMG-previews/player-startup-diagnostics-2026-09-21/FINDINGS.md, before/92файла,хеши,patch,tests,build.log. Диагностическиеhooksпослезавершенияубрать/сделатьopt-in,безоткатадальнейшихправок. Productionновыеdebug/PlayerStartupTrace.kt,StartupTraceStats.kt;6файловтольконаблюдение/таймеры. Парсер,clock,пружины,LyricsMotion,KaraokeLineTextнеизменны. APK:app/build/outputs/apk/player-startup-diagnostics/LMG-VK-debug-player-startup-diagnostics-2026-09-21.apk. SHA256 fba7e20450637f833cb65a3416080052105ca8722a50d354f1d9445693bb577b;MD5 75af23e812196339e7a8827f00a5dbef. Размер/MD5наДискесовпали,подписьпрежняя. Ссылка:https://drive.google.com/file/d/1xhXTZLYISrhBBxxBVqF7F0GfSafGwb3X/view. Коммитанебыло.


## Первый лог PLAYER_STARTUP с телефона получен

HONOR BVL-N49 Android16, 2полные16-секундныесессии. Разбор:/root/LMG-previews/player-startup-diagnostics-2026-09-21/DEVICE-LOG-ANALYSIS.md; цифрыdevice-log-summary.json. ADBнепонадобился. ВАЖНО:междусессиямипользовательсменилтрек/плейлист, поэтомуэтоНЕконтролируемыйsame-trackcold/warmтест.

Сессия1:дооткрытияLyrics(at1588ms)вFullPlayerпервыйкадрmax335.63ms,draw64.54ms,vsyncdelay200.08ms. ПослеLyricsmax377.34ms. UIизмерениетекста12вызовов/113.37ms/max25.24ms. Worker77вызовов/822.87ms. Prelayout1501.44ms—elapsedвключаяожидания/worker,НЕЛЬЗЯназватьблокировкойUI1.5сек. Растрыface25промахов/7.43ms/max0.79ms.83/817кадровoverBudget.

Сессия2:другойтрек,Lyricsat759ms,кадрmax248.24ms. UImeasure13/80.27/max17.55ms;worker27/157.13ms;prelayout413.86ms.10/932кадровoverBudget,после2сек2/836. Face35/9.29ms. GC4разаобесессии263/211ms,blockinggc0;неприписыватьвсюGCдлительностьUI.

Подтверждёнкодовыйпуть:видимыестрокимогутсинхронноизмерятьсявUIдофоновойподготовкиэтихжестрок,апозжеполучатьготовыйрезультатиповторноwrap. Этообоснованнаяцельточечнойоптимизации,ноНЕполноеобъяснениеFullPlayerспайкаи6–8сек. Глифкэшодинневиноватпомеркам. JIT/прогревдругихресурсовнеизмерены.

ИсправленанеточностьпредыдущегоFINDINGS:внешнийTextMeasurerprelayoutНЕпередаётсяKaraokeLineText;устроксвойrememberTextMeasurer,передаваемыйтолькоподпевкам. ДоказательствобщегоUI/workermeasurer/raceНЕТ.

Вответналогproductionкод/анимации/кэширующаяполитика/APKнеменялись. Полныйrootcauseещёнеустановлен;следующаяобоснованнаямера—приоритетвидимыхстрок,убратьUIfallback/дублированиеподготовки,контролироватьпубликациюразметки,сохранивформулырастяжения/скролла.


## 2026-09-21: диагностика первой минуты приложения

Пользователь уточнил: фризы начинаются с открытия приложения, продолжаются 30–40 секунд, затем всё плавно. Ранее PLAYER_STARTUP начинался слишком поздно. В сравнении одной песни первый/повторный FullPlayer: max кадр первой секунды 289.22→26.13 мс, backgroundArtwork cacheMiss→cacheHit, загрузка фона elapsed 279.79→0.12 мс. Лирика всё равно даёт 438.64→221.30 мс; UI измерения 148.54→113.69 мс, worker 27 строк оба раза; prelayout elapsed 339.54→509.75 мс, не непрерывная блокировка UI. 44/856→6/943 кадров overBudget. Полный root cause не установлен.

Добавлена только диагностическая APP_STARTUP с Application.onCreate на первые 60 секунд один раз за процесс. FrameMetrics всех Activity, wall+threadCPU синхронных init/JSON decode/adapter/catalog mapping/Aura shader constructor; elapsed сетевых и фоновых задач отдельно; каждые 5 секунд processCPU/heap/GC/allocation; foreground async heartbeat с ограниченными main-stack samples (>=120мс ожидания, раз в5сек, максимум12, пропускает при задержке самого наблюдателя>400мс). Сбор автоматически снимает callbacks/listeners/heartbeat и выгружает в DebugLog пакет с APP_STARTUP end=timeout seconds=60. Старый PLAYER_STARTUP сохранён и его фазы зеркалируются в app trace. До Application.onCreate, RenderThread/JIT scheduler полностью не охвачены. Длинный elapsed запроса НЕ означает UI блокировку. Метрики пересекаются, GC time не равен паузам.

Исходники сохранены до правок в /root/LMG-previews/app-startup-diagnostics-2026-09-21/before/, 431 Kotlin-файл. Изменены8 существующих: LmgApplication,MainActivity,AppRoot,PlayerStartupTrace,VkApiClient,VkResponseParser,MusicBackend,AuraBackground; 423 сохранены неизменными. Новые: AppStartupTrace.kt,StartupStallGate.kt,StartupStallGateTest.kt. Параметры эффектов, TTML/авторы/растяжения/пружины/кэши/dispatchers не менялись. Tolerant VK decode выделен в синхронную функцию только ради тайминга после bodyText. Защищённый backup SHA256SUMS проверен успешно. 168JVM тестов прошли; assembleDebug и compileDebugAndroidTestKotlin успешны2м54с. Эмулятор/телефонные тесты не запускались; на устройстве новая диагностика ещё не подтверждена.

APK app/build/outputs/apk/app-startup-diagnostics/LMG-VK-debug-app-startup-diagnostics-2026-09-21.apk; SHA256 1b3be0f364f276d425c9bfd608b09f1d353b1e5fe89ab45de2b48986e11c6a44; MD5 966b1737223ac942ebca54415e2d37f1; размер190871244. Подпись прежняя. Google Drive размер+MD5 сверены: https://drive.google.com/file/d/1lQE-H3A5QroqTJBi960GWqFEfHruIc6u/view. Только Диск, не вложение в чат. Инструкция: установить, через настройкиAndroid остановитьLMGVK, открыть заново,обычно пользоваться>=70сек,не очищать лог и не перезапускать до экспорта Настройки→Диагностика→Отладочныйлог→Поделиться. БезADB.

Параллельно Gemini переносит Timeweb→личный сервер пользователя; по скриншоту выполнен перенос backup, ещё идут развёртывание сервисов и конфигурации. Пользователь уточнил: загрузкиВК остаются локальными, серверзатрагиваетCDN; музыка может оборваться. Это не считать автоматически причинойUIфризов. Gemini МОЖЕТ менятьIP/адреса непосредственно в LMG-VK: сохранятьегоизменения,неоткатывать,переддоставкойAPKсверятьсоставсборки. При текущей сборке/проверке новых адресов или иных concurrent source edits не обнаружено. Не подключаться к серверам,неиспользоватьпоказанныевскриншотеучётныеданные;переносомзанимаетсяGemini. Коммит не создавался.


## Первый полный APP_STARTUP лог с телефона — музыка не прерывалась

Получена полная первая минута HONOR BVL-N49 Android16; пользователь особо подтвердил, что музыка не заглохла. Анализ: /root/LMG-previews/app-startup-diagnostics-2026-09-21/DEVICE-LOG-ANALYSIS.md, числовая сводка DEVICE-LOG-SUMMARY.json. Диагностика работает безADB. В этот ответ production-код и APK не менялись.

Главный вывод: подтверждены несколько main-thread задержек при инициализации и входах в экраны; одной непрерывной задачиВК на30–40сек лог не доказывает. Application741.93мс; local_playlists_init284.14wall/0.80cpu (ожидание, не284мсJSONCPU); Ktor init243.50wall/226.29cpu наUI. Первый кадр1268.08мс включаетпланирование, не складыватьсapplication. Начальные profile/domain/ping/update завершаются примернона4сек. Session maintenance<1мс.

Библиотека на5.320сек: кадр369.56мс; VK decodeUI на7сек54.75wall/54.19cpu. FullPlayer13.009сек: кадр263.48мс. Lyrics14.990сек:420.65мс, UImeasure10вызовов139.60мс;43workerстроки. ПовторLyrics19.885сек:291.74мс, UImeasure99.80мс и43workerстроки снова,prelayout681.29elapsed (неблокировкацеликом). Mainstackна15.726вisRtl/isArabic/HashSet.contains — это снимок, НЕ доказательство200мсinsidecontains. Arabicsetsужеlazy,направлениелинииremember; не «оптимизировать» HashSetнаугад.

Новое36.040сек: кадр276.02мс; stackMainPoster/MainPlayButtoncomposition, ДОответакаталога. На38–39секUIdecode50.68/51.11мс (CPU47.54/50.23), catalogMap22.96мсCPU22.48. Это подтверждённая цельпервогоисправления:декодированиеиmappingвworkerссохранениемcancellation/accountguards. Необъясняетвсеentryspikes.24–28секоколо60кадровбезoverBudget;после40секестьединичныеспайки(51сек132мс,54сек183мс). НулевыекадрынастатичномэкранеприбыстромheartbeatНЕфриз. GC11/486мс concurrent,blocking0;неприписыватьвесьGCtimeUI. ProcessCPU37665мсза60секнесколькоthreads;необъявлятьCPU/GPU/JITединственнойпричиной.

ОтдельноПОСЛЕзахватапримерно82сек:AppleTTMLDancingInTheFlames16747байтHTTP200,ноExternalLyricsRepositoryparseTtmlдаёт0строкизкэшаисети,выбрасываеткэшиищетfallback(словауспешно). ТамвсёещёDocumentBuilderFactory.setFeatureсранееunsupportedнаAndroidфлагами;runCatchingскрываетошибку. Подозреваемаяотдельнаяошибкаproviderparser,непричинапервых40сек. Не смешиватьсAccompanistTTML/анимациями. ОшибкидругихпровайдеровTLS/429/530/402такжепозже. GeminiпереноситCDN/можетменятьIP;никакихегоизмененийнеоткатывать.


## 2026-09-21: разрешён и выполнен перенос разбора ответов VK в фон

Пользователь: «давай разбор вк отправим в фон». Сделан узкий перенос всего вызова VkResponseParser.parse через internal parseInBackground = withContext(Dispatchers.Default) { parse(raw).also { ensureActive() } }. VkApiClient.execute вызывает этот helper; отдельный путь VkMiniAppTokenProvider (редиректы, сознательно не execute) тоже переведён на него. Вложенные parser/mapping операции теперь worker, продолжение/ошибки/captcha/retry возвращаются в исходныйконтекст. После синхронного разбора проверяется cancellation; результат отменённого запроса не доставляется.

Не перенесены этим шагом: eager-конструированиеMoshiадаптеров, последующееMusicBackend.toHomeBlocks/catalog_map, инициализацияприложения и lyricprelayout. Не утверждать,что все причиныфризовустраненыиливесьVKработаетвфоне. ПарсерTTML, лирика, растяжения, пружины, кэши и серверныеадреса не менялись. ДиагностическиеAPP_STARTUP/PLAYER_STARTUPсохраненыдлясравнения; vk_decode теперьдолженмаркироватьсяworker. Повторныйтестнаустройствеещёожидается.

Триproductionизменения: network/VkApiClient.kt, network/VkResponseParser.kt, network/methods/VkMiniAppTokenProvider.kt. Новый VkBackgroundParsingTest проверяет реальныйфоновыйпотокдляпарсингаивложенногомаппинга,возвратвcallerexecutor,сохранениеerrorenvelope/исключения,отменувовремясинхронногоразбора.171JVMтестпрошёл;assembleDebug/compileDebugAndroidTestKotlinуспешны1м38с. Наэмуляторе/устройствездесьнезапускалось.

Артефакты:/root/LMG-previews/vk-background-decode-2026-09-21/;before433исходника,patch,хеши,тесты,build.log,IMPLEMENTATION.md. Только3существующихфайлаизменены,430сохранены;concurrentизмененийадресов/исходниковдоокончаниясборкиипослезагрузкинебыло. Защищённыйbackupнеизменялся. НеоткатыватьвозможныебудущиеправкиGeminiсIPприпереносеCDN.

APK app/build/outputs/apk/vk-background-decode/LMG-VK-debug-vk-background-decode-2026-09-21.apk;SHA256053876916068d69839389d760b94cf7f8af428a3eedd272c2e2ed8a1bbf333e4;MD5ca63c55b6b53296548e729a590561b73;размер190873316. Подписьпрежняя. GoogleDriveразмер+MD5проверены:https://drive.google.com/file/d/1y1o_B8LhDmQ32EN9d2TJrBZAHHIEkSjN/view. Коммита нет. Проверкапользователем:установить,полностьюостановитьчерезAndroid,открытьипользоваться70сек,экспортлогабезочистки/перезапуска.


## 2026-09-21 — проба кеша измерений лирики
После лога Bruised Sky пользователь разрешил попробовать устранение повторных UI-измерений. KaraokeLyricsView готовит весь документ с вложенными подпевками в Default и показывает строки после готовности; до этого небольшой индикатор. PreparedLyricsLayouts/LyricsPreparationCache сохраняют до 2 документов, суммарно до 16000 text layouts; ключ учитывает lyrics/styles/density/fontScale/layoutDirection/font resolver/locales, устаревшие font results отклоняются. Отмена/ошибка не публикуют частичный результат. Crossfade хранит карту вместе с соответствующим документом. Формулы растяжения/пружины и измерения не менялись. В лог добавлен lyricsLayouts=cacheHit/cacheMiss.
177 JVM-тестов прошли; assembleDebug/compileDebugAndroidTestKotlin успешны, Android-тест геометрии только скомпилирован, эмулятор не запускался. На устройстве пока не проверено. Защищённый backup проверен SHA256SUMS, адреса/настройки Gemini не менялись. Артефакты /root/LMG-previews/lyrics-layout-cache-2026-09-21/. APK SHA256 7d8379de8e5abb62b3af7f7a17c0255808f94c824de99114572bac636648d7a3, Drive https://drive.google.com/file/d/1fWMhD0GxNyDk-96jFRgkwOq5plRl0J5N/view; размер/MD5 после загрузки совпали. Сравнить холодный и повторный вход на Bruised Sky: основной lyrics_measure должен быть worker, повторный вход cacheHit. Кеш живёт только в процессе; после force-stop холодный расчёт ожидаем. Прочие стартовые задержки FullPlayer/приложения этим не исправляются.


## 2026-09-21 — диагностика короткого фриза открытия лирики
Новый пользовательский лог подтвердил lyricsLayouts=cacheHit при повторном входе; lyrics_measure.ui отсутствует, при повторном входе нет prelayout/measure. Первый расчёт 27 строк worker~210мс, prelayout237мс. Остаются149мс на повторном входе,246мс первый показлирики,268мс FullPlayer и отдельные поздние просадки. Это другойтрек/сценарий, сравнение с BruisedSky не является контролируемым.
По явному разрешению добавлен PlayerOpeningTrace: DEBUG-only, отдельные5с на каждый вход с нажатия кнопки, fallbackattach из ObservePlayerStartup. Heartbeat16мс, stackприwait>=40мс,нечаще32мс,макс3наpendingheartbeat/18наоткрытие,loopgap>64мсигнорируется;стек32кадра+phase/state/sampleCostUs. Буфер64события,выводLYRICS_OPENINGнаworker,полноеотключениетаймеров/heartbeat/threadпоtimeout/exit. Синхронные wall/CPUзамерыcachelookup,screen/bodymeasure/place/draw,маркерыcommit/entrance/data/prepared. Draw—CPUrecording,неGPU;вложенныефазынельзяскладывать. frames=0вLYRICS_OPENINGзначитчтоэтотблоксобираеттолькостадии,кадрысмотретьвPLAYER_STARTUP.
181JVMтестпрошёл,assembleDebug/compileDebugAndroidTestKotlinуспешны1м34с. Эмулятор/Androidтестынезапускались. Прежнийкеш/растяжения/пружина/TTML/адресаGeminiсохранены,protectedbackupSHAпроверен. APKпровереннапрежнююподпись,наличиемаркераDEX,размер/MD5послезагрузки. SHA256 80746a575259f816a4671b8e1d42b5b14d520948133c055814e212fb9231c5ce; https://drive.google.com/file/d/1iVMP_M1ALGkL5A0BxKU0vhk-k23ZIFwK/view. Артефакты /root/LMG-previews/lyrics-opening-diagnostics-2026-09-21/. Это диагностическая сборка, не заявление об устранении оставшихся фризов. Попросить первый/повторныйвходтойжепеснипо15с иэкспортлога.


## 2026-09-21 — одна ширина на контейнер лирики
Пользователь разрешил пробу после LYRICS_OPENING: повторный вход cacheHit, lookup1.41мс, body.measure90.62wall/89.18cpu, place13.57, draw8.86; стеки BoxWithConstraints/SubcomposeLayout и attach/lookahead. Это не доказательство исключительной стоимости BoxWithConstraints.
Убран BoxWithConstraints из KaraokeLineText, один добавлен в KaraokeLyricsView вокруг списка. containerWidthPx передаётся строкам; основная вычитает два отдельно округлённых отступа16dp, подпевки получают оставшуюся ширину с отступом0. Сохранён px→dp→px для wrapping, width-dependent remember. Формулы пружины/растяжения/RowMotionData, Lookahead и кеш не менялись.
184 JVM теста прошли, assembleDebug и compileDebugAndroidTestKotlin успешны34с. Новые3 JVM теста (включая fractional density) выполнены;2 Compose instrumentation теста геометрии/resize/RTL/подпевок/фонетики только скомпилированы, не запущены. Эмулятор не запускался. Телефонное улучшение пока не подтверждено. Изменены только2 существующихKotlinфайла, новыеhelper+2testфайла; хеши после сборки/загрузки стабильны. Protected backup SHA256SUMS проверен; адресаGemini не менялись.
Артефакты /root/LMG-previews/lyrics-shared-width-2026-09-21/: before/after-hashes, исходные2файла, implementation.patch, build.log, test-results, IMPLEMENTATION.md, apk.json/drive.json. APK /root/LMG-VK/app/build/outputs/apk/lyrics-shared-width/LMG-VK-debug-lyrics-shared-width-2026-09-21.apk; SHA256 2a5d28c7c557ac65503e4388e5525c44bf6627fe6cccfbb01a488843690b52ea; MD5 5c3c8c56ee322dd15425ea022c500312; размер190873316. Подпись прежняя, новыйhelper подтверждёнвDEX; размер+MD5 наДиске совпали: https://drive.google.com/file/d/1gkMbDudqSXqLpUdfVu5YyNTtZkJN3uMJ/view. Коммит не создавался.
Проверка: послеустановки force-stop, одинтрек, первый/повторныйвходлирикипо15с, лог после70с от запуска. Сравнить body.measure/main_queue_wait/кадры открытия; проверить переносы/подпевки/пружину. Поздние фризы7–9сек первого входа остаются отдельной невыясненной проблемой.


## 2026-09-21 — общая ширина лирики дала регрессию, откат
Пользователь проверил shared-width APK и сообщил: стало хуже, сильные лаги автоскролла; предыдущая версия была плавнее. НЕ считать пробу успешной по unit-тестам или уменьшению числа BoxWithConstraints. Причина регрессии не установлена, новый лог не предоставлен. Последняя правка откатывается полностью: KaraokeLineText.kt и KaraokeLyricsView.kt восстановлены точно из pre-trial snapshots; новый helper KaraokeLineWidth.kt и два тестовых файла удалены после проверки их SHA256 на отсутствие concurrent edits. Кеш измерений, VK parsing worker и диагностические probes сохранены. Исходники совпадают с before-hashes trial; дальнейшие изменения ширины/структуры строк без новой обоснованной проверки не повторять. Артефакты /root/LMG-previews/lyrics-width-rollback-2026-09-21/, неудачный вариант сохранён там в trial/.

Откат собран:181JVMтест,assembleDebug,compileDebugAndroidTestKotlinуспешны11с;Androidтестынезапускались. Прежняяподписьпроверена. APK SHA256 6392069331ba1fad1915f31f677d860a334083c172c885de5d9a5613d7809982, MD5 909ad404c3c20db823fdefbeec7662a1, размер190873316; Drive размер/MD5 совпали: https://drive.google.com/file/d/16bsfQuHnGQEZKsIRsSHzyBZcP1K0jkIl/view. После загрузки исходники по manifest всё ещё совпадают с версией до shared-width. Пользователю предложить проверить возврат прежней плавности.


## 2026-09-21 — уточнение: после первой песни плавность сохраняется
Пользователь заметил исчезновение общей заторможенности примерно на40–45сек песни. На уточнение, возвращаются ли лаги после переключения на другую, ответил: «нет плавность остается даже после переключения». Значит повторяемая работа каждой песни слабее как гипотеза; отличать разовую подготовку процесса от первого использования плеера/лирики. Не объявлять JIT или фоновые загрузки доказанной причиной.
Read-only проверка: AudioService buffer30–60сек и preload30сек; эти значения задают объём буфера, НЕ таймер отключения работы через40сек. scheduleAudioPreCache на смене: delay8сек, awaitCurrentBuffered опросmainраз2сек до60сек, затем next2треков IO. HLS preCacheTrack возвращаетfalse немедленно, caller зря повторяет3раза, но это не доказательство40секнагрузки. НативныйJUCE40секпорог относитсякконцутрека, текущиелогиEXO. AudioTelemetry первыйотчёт90сек, не40. APP_STARTUP диагностика60сек, lyricsopening5сек, player16сек; нельзя сопоставлять по одной позициипесни. ПоставляемыйAPKdebuggable=true, возможнуюрольdebug/прогрева нужноизмерить, не утверждать. Productionкод в этом ответе не менялся, rollbackсохранён. Следующий простойконтроль: force-stop→открытьбезвоспроизведения60сек→запуститьпервыйтрек/лирику; выяснить, проходитлиподготовкаотожиданияилинужнопервоеиспользование.


## 2026-09-21 — ожидание без действий не устраняет фризы
Пользователь выполнил минутное ожидание без музыки: проблема осталась; уточнил, что пока не переключать вкладки, их данные не грузятся. Это направляет поиск на первое использование экранов, но не доказывает единую причину/JIT. Read-only проверка подтверждает NewCatalogScreen LaunchedEffect(viewModel,activeAccountId)→loadHomeContent; HomeViewModel имеет TTL5мин для повторных загрузок. LibraryScreen LaunchedEffect по account/login инициирует импортированные плейлисты (limit1000); FavoriteTrackDatabase создаётся remember при появлении экрана. NavHost создаёт контент по заходу на маршрут. Бездействие не инициирует эти пути. Старые логи содержат задержки созданияCompose/Text/LayoutNode, а decode уже worker. Следующий фокус: стоимость первых посещений вкладок и создание UI; отрисовкулирическихстрок послеотката не менять наугад. Productionкод не менялся.


## 2026-09-21 — диагностика без ПК: запись из приложения
Пользователь сообщил, что компьютера вообще нет; в показанном меню разработчика HONOR System Tracing отсутствует. Добавлена вручную включаемая одноразовая запись при следующем старте процесса через Android 35+ ProfilingManager, доступная в Debug Log на Android16. Это диагностическая сборка, не исправление лагов. Полный откат shared-width сохранён; структура строк, пружины/растяжения и воспроизведение не изменялись.
Изменены LmgApplication.kt (initialize), DebugLogScreen.kt (карточка), AppStartupTrace.kt (условные sync/async Trace-секции и маркеры). Новые AppPerformanceCapture.kt, PerformanceCaptureFiles.kt, PerformanceCaptureCard.kt, PerformanceCaptureFilesTest.kt. Запрос 60сек/32768КБ/DISCARD; arm флаг расходуется до обращения к системе. Диск/настройки/экспорт на worker; Handler lazy, чтобы выключенный путь не инициализировал Android Looper в JVM. Глобальный listener только для armed/pending. Результат копируется с проверкой canonical path и staging rename; храним2записи; ZIP содержит trace, metadata и DebugLog, отправляется только явным Android chooser через существующий FileProvider cache/logs. Автоматических отправок нет.
Ограничения: поддержка сервиса HONOR ещё не проверена. Android может отказать/ограничить частоту; ошибки видны в карточке. Старт асинхронный, самые ранние кадры могут отсутствовать, объём буфера может ограничить длительность. Trace фильтруется к процессу приложения, это не полный системный дамп и не method CPU profile. Через5мин без ответа показываем timeout; поздний ответ в текущем процессе ещё сохраняется. Старые debug probes активны, учитывать их overhead.
Финальная проверка:187JVMтестов без failures/errors/skips; assembleDebug и compileDebugAndroidTestKotlin прошли59сек. Android instrumentation только скомпилирован; эмулятор/устройство не запускались. После добавления trace hooks тест выявил eager Handler init; исправлено lazy и весь выбранный набор повторён успешно, см build-verified.log. 487 исходников совпадают с after-hashes; защищённый backup проверен, чужие изменения не затронуты. Коммита нет.
Артефакты /root/LMG-previews/in-app-performance-capture-2026-09-21/. APK app/build/outputs/apk/in-app-performance-capture/LMG-VK-debug-in-app-performance-capture-2026-09-21.apk; размер190882724; SHA25613ca291bac4ed2928c65d569e8734fbec99bfd84d90257b5ec203b0d2d8c7561; MD543738fb019e1d4fedbddae85b3e13970. Прежняя подпись проверена; Drive размер/MD5 совпали: https://drive.google.com/file/d/1khsYQsE51cHTcCaAGpjHXs01mN0cU-Iq/view.
Инструкция: установить; Debug Log → Записать при следующем запуске → дождаться Готово; принудительно остановить через настройки Android и снова открыть; сразу воспроизвести обычные действия/табы/музыку/лирику первую минуту; примерно через90сек вернуться в Debug Log и дождаться Запись готова → Отправить запись → прислать ZIP. При ошибке прислать её текст. Файл не предлагается автоматическим окном: нужно вернуться к кнопке.


## 2026-09-21 — No Love: устранено глубокое хеширование кэша в анимации текста
Пользователь прислал полный309строчный лог после ERROR_FAILED_POST_PROCESSING=5 с пустым пояснением; больше логов не запрашивать без конкретной необходимости. В LYRICS_OPENING +2582/+2616/+2654мс три последовательных snapshot на main показывают Pair.hashCode→AbstractMap.hashCode→SyllableLayout.hashCode→TextLayoutResult.hashCode, вызываемые Crossfade через MutableScatterMap и movable groups. Код подтвердил Crossfade(lyrics to layoutCache). Это регрессия стоимости ключа от нашей оптимизации prepared-layout cache. Не приписывать ей все40сек или непрерывные117мс: snapshots не дают точного распределения времени.
Узкое исправление: remember(lyrics,layoutCache) создаёт обычный LyricsCrossfadeContent с identity hash/equals и теми же payload. Crossfade получает этот объект; исходящий/входящий контент сохраняет собственные lyrics/layouts. Изменён только KaraokeLyricsView.kt, добавлены LyricsCrossfadeContent.kt и LyricsCrossfadeContentTest.kt. Lookahead, BoxWithConstraints строк, пружины/растяжения, скролл и звук не менялись. Shared-width откат сохранён. Пользователь возразил, что кроссфейд только передконцомпесни; объяснено: речь исключительно о Compose-анимации появления текста, НЕ об аудиокроссфейде. В дальнейшем явно различать названия, пользователю говорить «анимация текста».
Тест использует настоящую androidx MutableScatterMap с payload-счётчиками hash: старый Pair обходит документ и map; новый объект при120циклахinsert/get/hash не вызывает payload.hashCode. Ещё2теста проверяют раздельные outgoing/incoming payload и новую identity при заменеlayouts. Все190выбранныхJVMтестов прошли; assembleDebug/compileDebugAndroidTestKotlin успешны34сек. Устройство/эмулятор не запускались, общую плавность не объявлять исправленной. Protected backup проверен,488sourcehashesсовпали, только1существующийsourceизменён. Коммита нет.
Дополнительные факты: подготовка176строк ~2сек worker при APPt22/23безoverBudget; затем UIbody.measure103мс. Поздниеt33–36лаги не объяснены текущими снимками. На46сек первыйnew/homeкадр154мс; универсальный40секпорог не подтверждён. Systemcaptureвэтомпрогонемогдобавитьнагрузку. Остались отдельныеknownUI HTTPinit~301мсCPU/catalogmap~24–26мсCPU.
Артефакты /root/LMG-previews/lyrics-crossfade-key-2026-09-21/: snapshots/hashes/patch, FINDINGS.md, localComposebytecode, build/test/APK/Drive/signature. APK /root/LMG-VK/app/build/outputs/apk/lyrics-crossfade-key/LMG-VK-debug-lyrics-crossfade-key-2026-09-21.apk; SHA256 22790800159d5f2ba2d82f7e88fa02c288c2c24416d6c74bde49fd6ba594bb2c; MD5 bbf8c08436ff2a5588776d832dab3bde; размер190882724. Прежняяподпись, DEXновогокласса проверены. Drive размер/MD5совпали: https://drive.google.com/file/d/1n2DmqRZNwzKGNGsNHW6ZA2d9YnvwuE6G/view. Системную запись для этой сборки не включать, дополнительных действий по логам не просим.


## 2026-09-21 — identity target ухудшил лирику: полный откат и веб-проверка
Пользователь: «нихуя не лучше стало, а только хуже… смотри интернет…50секундпрошлолагилирикиостались,доэтогоисправлениятакогонебыло». Последняяпроба признана НЕУСПЕШНОЙ; не спорить сdeviceрезультатом190unitтестами. KaraokeLyricsViewвосстановленточноизpre-trialsnapshot; LyricsCrossfadeContentи3егоunitтестаудаленыпослепроверкиSHAнаconcurrentedits. Все486beforehashesсовпали. Лирика,звук,серверыосталиськакдопоследнейпробы. НовыхоптимизацийвAPKнет.
Интернетпроверен: Google Composeperformance рекомендуетrelease+R8, debugнакладныерасходыизвестны, ноэтоНЕобъясняетразницудвухdebugAPK. Strongskippingменяетсравнениеstable/unstableпараметров; влияниеidentitytargetнаполнуюанимациюнепроверено. РеальновразрешённыхзависимостяхFoundation/UI1.10.2(не1.7.6изBOM). ВофициальныхreleasenotesFoundation1.13.0-alpha01от12авг2026естьlazy-prefetch-idlebugприопоздавшихкадрах(b/458024295), вызывающийscrolljank; alpha02исправляетkeep-aroundduringlookahead. Этонаправлениеисследования,НЕдоказанныйкореньпроблемы,alphaобновлениенеделалось. Полныессылки/выводы:/root/LMG-previews/lyrics-crossfade-key-rollback-2026-09-21/RESEARCH.md;dependencyInsightcompose-version.txt.
Откат:187JVMтестов,assembleDebug/compileDebugAndroidTestKotlinпрошли8сек;устройство/эмуляторнеиспользовались. Подписьпрежняя,DriveразмерMD5совпали. APK /root/LMG-VK/app/build/outputs/apk/lyrics-crossfade-key-rollback/LMG-VK-debug-lyrics-crossfade-key-rollback-2026-09-21.apk;SHA256 e4c03189192b98500d4143b1548ab0845135a355705f250b47fdbc293426f140;MD5 f507cbc1d3595ee63aa32d1adc06977b;размер190882724. Ссылка:https://drive.google.com/file/d/15PBFX5o3ob97psFWAFkIaNMXon_k5FqO/view. Новыелогинезапрашивать,системнуюзаписьневключать. Необъявлятьобщуюпричинунайденной. Коммита нет.

## 2026-09-21 — iTunes как первый источник обложек песен

Владелец уточнил и разрешил порядок для всех песен: совпавшая обложка iTunes → обложка ВК → существующий сгенерированный VK thumb. Это явное исключение из старого VK-only для изображений. Аудиопотоки, серверы/CDN владельца и авторизация не меняются. Задача лагов остаётся на паузе; рендерер лирики, растяжения/пружины и аудиокроссфейд не тронуты.

Добавлены `artwork/ItunesArtworkMatcher.kt`, `ItunesArtworkRepository.kt`, `ItunesSessionBitmapLoader.kt`, `ui/glass/RememberTrackCover.kt`, тесты matcher. Подключены UI списки песен, каталог/поиск/очередь, плеер и его палитра, локальные/скачанные песни, MediaSession через обёртку bitmap loader. Исходные VK URL не перезаписываются; старый `album.thumb → track.thumb` сохранён. Альбомы/плейлисты как сущности и фото артистов не заменяются; теги аудиофайлов не меняются. Полное описание, ограничения и источники: [docs/ITUNES-ARTWORK.md](docs/ITUNES-ARTWORK.md).

Строгое совпадение названия/набора артистов с нормализацией feat и разницей длительности до 3 секунд; неизвестная длительность — без поиска. Другие версии не удаляются из названия ради совпадения. При наличии названия альбома предпочитается оно. Витрина US, максимум 25 результатов. URL 600×600 проверяется загрузкой, затем исходный artworkUrl100. Поиск вне main, не чаще раза в 3,2 секунды; кеш положительных/отрицательных/временных результатов, отмена после ухода потребителей. Не обещать мгновенной замены всех обложек большой библиотеки: только видимые/играющие песни, с очередью и кешем.

Живой iTunes Search API проверен на Lady Gaga / The Dead Dance: результаты MAYHEM и сингл, изображение 600×600 успешно загружено. Условия promotional artwork ранее объяснены пользователю; реализация не является подтверждением лицензии на распространение и не решает юридический вопрос. Устройство/эмулятор не запускались, фактический вид на HONOR ещё не подтверждён. Коммита нет. Артефакты: `/root/LMG-previews/itunes-artwork-2026-09-21/`.

Финальная проверка: `assembleDebug` и 10 целевых JVM-тестов (6 iTunes matcher + 4 CatalogArtwork) прошли; `git diff --check` без ошибок. APK: `/root/LMG-VK/app/build/outputs/apk/itunes-artwork/LMG-VK-debug-itunes-artwork-2026-09-21.apk`; SHA256 `6608a0366f36dac4420d3637eec2c254c4dcd22e254979d404cdf0d7040313ec`, MD5 `ce9a3121c8dc6722fb76e1acc8646d10`, размер 190887160. Прежняя подпись проверена, новый repository найден в DEX; финальные исходники совпали с сохранёнными хешами.
Google Drive: https://drive.google.com/file/d/1KKMUBE56VllvmTvBgULBJAjCGmy0iFgs/view — размер и MD5 удалённого файла совпали.


## 2026-09-21 — единая обложка текущей песни: Wave / FullPlayer / фон

Пользователь показал Godzilla: на Wave красная обложка, в FullPlayer и AirPlay — Side B Deluxe; фон отличался. Причина в коде: Wave искал без albumName, Root/FullPlayer и палитра — с albumName. Полный ArtworkQuery был ключом кеша/запроса, matcher предпочитал альбом: один трек мог получить разные издания. По скриншоту нельзя доказать, что Side B получена именно из ВК, поскольку она также бывает в Apple.

Исправлено: lookupQuery нормализует title/artist/feat и исключает альбом для единого ключа и запроса. ProvidePlayingArtwork в AppRoot один раз выбирает текущую обложку и отдаёт через CompositionLocal всем совпадающим потребителям. Wave-карточка прямо получает waveTrackCover, тот же URL использует её палитра. FullPlayer/фон/AirPlay уже используют preferredTrackCover. Repository общий и для MediaSession. Кеш сменён на itunes_artwork_v2: старые конфликтующие результаты не читаются, пользователю не нужно очищать данные приложения. Порядок iTunes → VK cover → существующий VK thumb сохранён.

Проверка: assembleDebug и 15 целевых JVM-тестов (6 matcher, 5 selection, 4 CatalogArtwork) прошли. Изменены только 5 существующих Kotlin-файлов, добавлены ArtworkSelection и тест; сравнение всех исходных Kotlin-хешей подтвердило отсутствие посторонних изменений. Документация docs/ITUNES-ARTWORK.md обновлена. Подпись APK прежняя, новый класс проверен в DEX. На HONOR/эмуляторе не запускалось; синхронность UI на устройстве ещё не подтверждена. Лаги остаются на паузе, код лирики/звука не менялся. Коммита нет.

Артефакты: /root/LMG-previews/itunes-artwork-sync-2026-09-21/. APK: /root/LMG-VK/app/build/outputs/apk/itunes-artwork-sync/LMG-VK-debug-itunes-artwork-sync-2026-09-21.apk; размер 190886968; SHA256 0830690710b2a5d3b2fa65771b304b69397406ffc9d46215a878582fca6a817c; MD5 7db22784f3ff35649968eac507392eb6. Drive размер и MD5 совпали: https://drive.google.com/file/d/1skntWmIA3phkh_16jfo1-2adXjPv3bSY/view


## 2026-09-22 — окончательная обложка сразу, исходное качество и теги загрузок

Пользователь: убрать видимую замену VK → iTunes в поиске, распространить на AirSheet/FullPlayer/главный экран/«Мою музыку»; при скачивании вшивать обложку, брать максимальное качество. Реализовано без изменений лирики/звука/транспорта/серверов. Лаги остаются на паузе. Подробности: docs/ITUNES-ARTWORK.md.

ArtworkLoadState разделяет pending и готовое отсутствие совпадения: memory cache сразу используется в первом кадре, холодный запрос показывает нейтральное место загрузки до окончательного iTunes/VK. Root/Wave/landscape не обходят pending через старый displayArtUri; общий выбранный URL текущей песни наследуют фон, FullPlayer/AirPlay. Palette memory cache читается синхронно. Убран лишний повторный preload на каждом UI-потребителе и crossfade Coil для строк с artworkQuery. Поиск iTunes идёт вместе с VK, пул ответов128/12часов используется через строгий matcher; отсутствующие длительности обогащаются до публикации с пределом1200мс, ожидание общего поиска≤150мс. Нельзя обещать мгновенную загрузку незнакомой песни: сеть и очередь iTunes3,2сек сохраняются; большого фонового обхода медиатеки нет.

Качество: исходный asset a5.mzstatic.com/us/r1000/0, затем10000rendition, затемURLAPI. Проверено на Godzilla: исходник3600×3600/5283384байта, rendition10000 тоже3600×3600/4246607байт. UI decode по размеру, disk cache хранит исходные байты. Старые v2 600px соответствия обновляются по asset без Search; новые quality=1. Обложка при новом скачивании выбирается общим repository, исходные JPEG/PNG байты из Coil disk cache сохраняются в .covers и вшиваются в MP3 APIC или M4A covr перед MediaStore export. Single+playlist пути, включая фактическийM4AизsingleHLS, подключены. Лимиткартинки32MiB вместо2MiB. Уже скачанные файлы автоматически не переписываются; при недоступнойкартинке сохраняется аудио с текстовымитегами.

Проверка: assembleDebug и31JVMтест прошли (6matcher+5selection+8loading+3quality+5downloadtags+4catalog). На синтетических0,2секаудиофайлах подтверждены байтовое равенство встроеннойкартинки, PNG>2MiB, сохранениеMP3payload/M4Aаудиосэмплов поstco/co64/stsz, заменаартбездвойныхкартинок, rollbackбитогоконтейнера. Дополнительно ffmpeg скопировал AAC до/послеM4Aтегов: SHA256потоковсовпал. Изначально тест ошибочно сравнивал весьmdat: jaudiotaggerперестраиваетконтейнер и добавляетpadding, поэтому тестуточнён до самихаудиосэмплов. Не выдавать это за тестнанастоящемHONOR. Устройство/эмулятор не запускались.

Изменены12существующихKotlinфайлов; baselineпроверен,удаленийнет, финальныехешисовпадают; gitdiffcheckчистый. Коммита нет. Артефакты /root/LMG-previews/artwork-first-frame-2026-09-22/ (before,patch,hashes,build/tests,реальнаяAppleпроверка,APK/Drive/signature). Подпись прежняя, новыеclassesиURLвDEXпроверены.

APK: /root/LMG-VK/app/build/outputs/apk/artwork-first-frame/LMG-VK-debug-artwork-first-frame-2026-09-22.apk; размер 190887048; SHA256 c7da6fb865fde51ad9da73a9110648f45451e3ee4ac32b5a755788caf5023d50; MD5 82a5c8156dd3675523240db3f0957888.
Google Drive (размер/MD5 совпали): https://drive.google.com/file/d/1Sku_uSmPFG7Pxwp01DhXYOD_Hm40-46c/view


## 2026-09-22 — живые обложки через готовый backend /v2/motion

Пользователь подготовил backend и прислал два скриншота контракта. Правильный домен lyrics.gsgit.org (в раннем сообщении .com, на готовомконтракте.org). Явный пример HEALTH — ANTIDOTE / CONFLICT DLC. Сервис проверен read-only: ANTIDOTEhas_motion=true; Godzilla (Eminem feat.JuiceWRLD) false. Endpointtitle/artist, JSONsquare.m3u8/mp4/preview,tall,static_artwork,colors. Backend/serverнеизменялись.

Добавлены MotionArtwork.kt (ответ/metadata-match/HLSvariant), MotionArtworkRepository.kt (HTTP+диск), MotionArtworkRenderer.kt (EGL/OESодинtexture), MotionArtworkPlayback.kt (отдельныйнемойExo), ui/player/MotionArtworkSurface.kt (rootprovider/lifecycle/TextureViews), MotionArtworkTest.kt и4fixtures. Только4существующихKotlinизменены: AppRoot, FullPlayer, WaveHomeScreen, AirPlaySheet. Oldbaselinesнапроверке совпалиостальные,удаленийнет; gitdiffcheckчистый. Лирика/звук/транспортVK/теги/серверы/CDN/авторизациянетронуты. Коммита нет.

Rootвладеетоднойvideoplaybackнаиграющийтрек,передаётчерезCompositionLocal. Загрузкаproviderизолированаkeyвнутрисостояния,НЕпересоздаётAppRootContentприskip. ГлавнаякарточкаWave,FullPlayerиAirSheetthumbnailрисуютоднуexternaltexture;фонWave/FullPlayerтотжекадрсblur/dim. НетдвухнезависимыхплееровилиCPUbitmapкаждыйкадр. Backgroundбуферmax256pxпо большейстороне,25-tapshader;foregroundпоразмеруUI. RepeatONEлокальногоMP4,volume0,audio/texttracksdisabled;следуетпауземузыки/lifecycleSTARTED/наличиюoutputs. Настоящиеvideoclipsисключены. TextureViewsневидимыпрактически(alpha0.001)доonSurfaceTextureUpdated,затемalpha1;абсолютныйalpha0нельзяиспользоватькакбарьервпервыйкадр. Статичнаявыбраннаяобложка/старыйфоностаютсяfallback;CPUфоностанавливается,когдаmotionready.

Highquality: backendmp4ANTIDOTE768×768/30fps/29.5сек/5304302байта, HLS содержит1080×1080/30fps/29.5сек/24716992байта. ВыборнаибольшегоH264fullvideo,равныеразмеры→maxBANDWIDTH;исключеныtrickplay. ДляVODбезшифрованиясEXT-X-MAP+byterangeодногоMP4загружаетсяполныйфайл,приошибкеfallbackbackendmp4. max64MiB/file,256MiB/LRUвидео,metadata7днейpositive/12часовnone/max256records. Роликдокачиваетсялокальнопередпоказом,поэтомуcoldнеinstant,статичнаяобложкавэто времяостаётся. Tallнеиспользуетсядляexistingквадратноголейаута;фонберёткадрэтогожеsquare. Backenddurationотсутствует,поэтомупроверяютсяtitle+artist/featс сохранениемверсий,совпадение3секдляmotionневозможно.

Тесты: assembleDebug+39JVMпрошли (31предыдущийartwork/tag/catalog+8motion). РеальныеJSON/m3u8fixturesпроверяютnone,wrongartist/version,feat,HTTPS,выбор1080fullvideo,единыйbyterangeMP4,rejectencrypted/live/multifile. Реальные768и1080mp4провереныffprobe. GLSLvertex/fragmentOES/blurотдельноскомпилированы/слинкованынаEGL1.5/GLES3.2Mesa. ЭтоНЕпроверканателефоне;плавностьпетли/рендерTextureViewвglassиHONORещёнетестировались. Эмулятор/устройство незапускались. Необъявлятьlagисправленным.

Docs: docs/MOTION-ARTWORK.md. Артефакты /root/LMG-previews/motion-artwork-2026-09-22/ (before,hashes,diff,backendJSON,mp4/ffprobe,GLSLcheck,build/tests,APK/Drive/signature). Новыеклассы/endpointвDEXпроверены,подписьпрежняя,финальныеKotlinхешисовпадают.

APK: /root/LMG-VK/app/build/outputs/apk/motion-artwork/LMG-VK-debug-motion-artwork-2026-09-22.apk; размер 190887724; SHA256 54f7d9bfc83ef882692f2c47fffdc096d9d3f55eae8724681baf352cf246f7e6; MD5 95f6d192998973afa888c5d251281be0.
Google Drive (размер/MD5 совпали): https://drive.google.com/file/d/1nBzUMNGNBqwurTSO8hiDj8HCY1dKAWHD/view


## 2026-09-22 — исправление motion по видео Apple

Пользователь: первая motion-сборка зависает около5сек, фон с сеткой; прислал9,47сек видео Apple. Подтвердил tall вместоsquare, непрерывность даже на паузе, сильный блюр как у обычных обложек. Фон без отдельного вращения/таймерамерцания, меняется от видеокадра. Полный актуальный контракт/ограничения в docs/MOTION-ARTWORK.md.

Изменены только MotionArtwork.kt, MotionArtworkRepository.kt, MotionArtworkPlayback.kt, MotionArtworkRenderer.kt, ui/player/MotionArtworkSurface.kt, FullPlayer.kt и MotionArtworkTest.kt; никаких аудио/VK/лирикаправок. Tallpreferred/squarefallback; HLSвместоизвлечённогоMP4. УсырогостарогоMP4start10,033с/container29,5с, HLSпетля19,5с; связь с конкретным5сзависанием не доказана. H264highestdevice, tallANTIDOTE1080×1440, bufferedstart250ms. Отдельный256MiB LRUcache, metadata7д/12ч, nexttrackprefetchmetadata+init+firstsegmentпослестартаcurrent. HTTPвидеоDefaultHttpDataSource, metadataпрежнийOkHttpbypass, VKнеизменён. ПаузааудиобольшеНЕпередаётсяmotion; STARTED+outputs определяютplayWhenReady.

FullPlayerportrait: tallнаверхувовсюширину(до66%высоты), мягкийпереходвфон, скрытаквадратнаякарточка; previewдоfirstframe. Wave/AirSheet/landscape центральныйcropэтогожетall. GLblur32×32+6separablepassesвместо25sparseghosttaps; saturation/darkenфона, безвращения. Windowoutputs неотправляютвторойкадрдоackTextureView; потеряодногоEGLwindowнеостанавливаетвесьплеер.

Проверено assembleDebug+39tests (0failed), GLSLcompile/link иoffscreenrenderс2Dподстановкойвидеокадра(glGetError0). Неиспользовантелефон/эмулятор; невыдаватьэтичекизауспешнуюбесшовнуюпетлюнаHONOR. Холодныйнеизвестныйтрекзависитотсети, абсолютныйinstantнеобещать. Лагилирикиостаютсяотложены. Артефакты /root/LMG-previews/motion-revision-2026-09-22/.

APK: /root/LMG-VK/app/build/outputs/apk/motion-tall-loop/LMG-VK-debug-motion-tall-loop-2026-09-22.apk; размер190887816; SHA256 acdf9fda50ef60a822feca058fe7135e4fe2905e147d8d3cefe4e858a7e1ae88; MD5 e43bfe8e3a9c8c035c406729a9075eda.
Google Drive (размер/MD5подтверждены,подписьпрежняя,новыйкодвDEXпроверен): https://drive.google.com/file/d/1eBhYs8MYsgLS1vZUMwd2a5KURLwOER5t/view


## 2026-09-22 — статичная обложка и фон только на «Моей волне»

Пользователь одобрил FullPlayer по скриншоту938369. Попросил на Wave вернуть обычную статичную обложку и фон от неё. Затем явно уточнил: живую миниатюру AirSheet оставить, ему нравится. Итог: изменён только WaveHomeScreen.kt — убраны два MotionArtworkSurface (большая карточка и фон), AuraBackground.animate снова animationsActive, без зависимости от готовности motion. FullPlayer/AirPlaySheet байтово совпадают с предыдущей принятой сборкой. Подготовка/кеш/плеерmotion не менялись.

Проверено assembleDebug SUCCESS, git diff --check, SHA256 всехKotlin: толькоWave изменён. Новые тесты для удаленияUIслоёв не добавлялись, прежниеunit не перезапускались. Подпись прежняя, Drive размер/MD5 проверены. Телефон/эмулятор не запускались. Артефакты /root/LMG-previews/motion-full-player-only-2026-09-22/ (имяпапкираннее; AirSheetсохранёнпоисправлениюпользователя).

APK: /root/LMG-VK/app/build/outputs/apk/static-wave/LMG-VK-debug-static-wave-2026-09-22.apk; size 190887048; SHA256 8a114753591dbb44b44e47f484bb443b41547efa0287f38578348b6a973d41a2; MD5 78aa64bb125fa1005d28ed361f59f8dd. Drive: https://drive.google.com/file/d/1QewPezbAfPhJkJS-yh4oM1nJI6T6LLrt/view


## 2026-09-22 — очистка запросов и устаревшие отрицательные результаты

По сообщению пользователя сервер /v2/motion уже очищает VK-теги, ищет альбомный motion для сингла и возвращает ORDINARY LOSS has_motion=true. Клиент ранее отправлял сырые поля и удерживал отрицательные ответы12часов. Исправления подготовлены в исходниках; новый APK ещё НЕ собран/не доставлен.

Общий ItunesArtworkMatcher очищает скобочные vk.com/vk.ru/vkontakte.ru ссылки и bitrate-теги, а известный жанровый хвост вроде Electronic удаляет только после такого служебного тега. Реальные названия Electronic/Pop и версии Live/Remix/Sped Up/Radio Edit сохраняются. Очистка применяется и при формировании запроса, и при сравнении с ответом. canonical.album теперь сохраняет очищенный нормализованный альбом; shared ArtworkSelection по-прежнему сравнивает идентичность песни без album, чтобы не разделить обложку игрока и остальных экранов.

Motion запрашивает canonical.title/artist и непустой canonical.album. Album передаётся от текущего/следующего Track и учитывается в ключе metadata. iTunes использует ту же очистку, включая предварительный поиск; совпадение по длительности±3с и версиям не ослаблено.

Добавлен lookupVersion=1 для записей обоих metadata-кешей. Старые отрицательные записи без версии игнорируются при чтении даже до expires; новый запрос перезаписывает их. Валидные старые положительные записи принимаются там, где ключ совпадает. Новые промахи —15мин вместо12ч; ошибки загрузки картинки/iTunes сохраняют короткий5минTTL. Пустые списки iTunes candidates также живут15мин. Видеосегменты/Coil-кеш не очищаются. RAM-кеши после установки новой сборки создаются заново. На телефоне сейчас ничего не сброшено: требуется новая сборка.

Проверки:37 JVM artwork-тестов прошли напрямую через локальные Kotlin2.3.10/JUnit4.13.2 (включая реальныеформыVkзагрязнения, URL/queryalbum, совпадениесчистымmotion, старыйnegativecache, TTL, сохранениеверсий). Изменённые Android-исходники, включая оба repository и Compose provider, отдельно скомпилированы локальным Kotlin+Compose plugin против cachedappclasses/Android36/зависимостей. git diff --check чистый. Это НЕ полный Gradle build и НЕ Android runtime test.

Ограничение среды этой сессии: Gradle не запускается — FileLockContentionHandler: Could not determine a usable wildcard IP for this machine; исходящий curl также не разрешает lyrics.gsgit.org. Сетевые операции ограничены, повышение прав недоступно. Полный APK assemble/upload не выполнен; прежний APK нельзя выдавать за содержащий эту правку. Артефакты: /root/LMG-previews/artwork-query-cleanup-2026-09-22/ (before/hash/patch, build.log, local-check.py/logs, compile-android.py/logs).


## Полная сборка и доставка после снятия ограничений — 2026-09-22

Пользователь разрешил запуск после включения сетевого доступа. assembleDebug + testDebugUnitTest успешны:46 tests,0failures/errors/skipped (37artwork+5downloadtags+4catalog). Изменённые исходники совпали с проверенными SHA256. Live запрос canonical title=ordinary loss, artist=health, album=conflict dlc подтвердил has_motion=true, tall HLS, track1832593366/album1832593364. Это серверная проверка; на телефоне не запускалось.

APK: [LMG-VK-debug-artwork-query-cleanup-2026-09-22.apk](https://drive.google.com/file/d/1eo2plxxbukCKINsSgBR1UVBUbWc1aT-E/view); локально /root/LMG-VK/app/build/outputs/apk/artwork-query-cleanup/LMG-VK-debug-artwork-query-cleanup-2026-09-22.apk. Размер190887048, SHA256 `cf270c62b3a56c1eabd60aabc38d8fa25752211aff262d47ab9a4e6b8f2fbbe4`, MD5 `e0f61902d801665fa1ae8c35b0b97422`. Подпись совпала с предыдущей сборкой, новые cache/query классы проверены вDEX, размер/MD5 Drive совпали. После установки старые отрицательные записи будут игнорироваться при следующем чтении, нового поиска не придётся ждать12часов.

Артефакты полной проверки: full-build.log, full-tests.json, ordinary-loss.json, apk.json, signature.txt, drive.json, drive-link.txt в /root/LMG-previews/artwork-query-cleanup-2026-09-22/.


## 2026-09-23 — плавные переходы motion и отсутствие старого фона

Запрос пользователя: остаток прошлой песни при входе/выходе из LyricsScreen и QueueSheet; плавное исчезновение/появление полноэкранных моушенов при смене треков и экранов. Изменены MotionArtworkSurface/Playback/Renderer и FullPlayer; добавлены MotionArtworkLifetime и ArtworkBackgroundGeneration с9 regression tests. Presentation теперь420мс uniform transition в том же TextureView, без смены key/пересоздания. Track transition450мс Crossfade удерживает конкретный outgoing playback до последнего unbind; старое видео ставится на паузу, текущая motion по-прежнему не зависит от паузы аудио. Первое появление TextureView300мс. Late callbacks после unbind не показывают и не привязывают снятую поверхность. Общий reference count EGL display защищает перекрывающиеся renderer. Dirty/ACK схема позволяет довести presentation до конечного значения без непрерывной перерисовки замороженного кадра. Статичный renderer пересоздаётся при смене источника во время suspension; обычный static→static blend сохранён. Wave и живой AirSheet не меняли.

Проверки:57 целевых JVM тестов прошли, assembleDebug успешен. Шейдеры скомпилированы/слинкованы Mesa; portrait1 пиксельно совпал с прежним, portrait0 с блюром, промежуточные фракции дают ожидаемую смесь±1/255. Это offscreen2D подстановка реального кадра, не Android/OES runtime. Устройство/эмулятор не запускали. Docs: docs/MOTION-ARTWORK.md.

Отдельно остаются: лаги renderer лирики (отложены); duration±7сек для motion/artwork, чтобы обрезок São Paulo1:30 не получал оформление полной5:00; static_artwork из ответа motion, когда iTunes оставил VK thumb. Они не реализованы в текущей правке переходов.


### Проверка и APK переходов — 2026-09-23

assembleDebug + testDebugUnitTest для artwork/ui.player завершились успешно:57 тестов,0 failures/errors/skipped. Включены9 новых regression tests. После последней защиты onSurfaceTextureAvailable/SizeChanged выполнена повторная сборка; guards подтверждены в compiled bytecode. Новые классы/метки переходов присутствуют в APK DEX. SHA256 исходников совпали с переданными на сборку. Подпись совпадает с предыдущим APK.

APK: [LMG-VK-debug-motion-transitions-2026-09-23.apk](https://drive.google.com/open?id=1PceNqC4Wqesxs7a7xY6A1srYZdZutvI4). Размер 187620507 байт; SHA256 `ee8f8f2e2d19a887beb18c1f95b38817b3b1b7695d0a2d01e97cfbf47f0e81a1`; MD5 `6aef5d6dc00b55640969e048e3059cc0`. Google Drive размер/MD5 подтверждены. Локальный путь: `/root/LMG-VK/app/build/outputs/apk/motion-transitions/LMG-VK-debug-motion-transitions-2026-09-23.apk`. Артефакты: `/root/LMG-previews/motion-transitions-2026-09-23/` (before, implementation.patch, source-hashes, build logs, tests, shader/render checks,5 промежуточных кадров, surface-bytecode, подпись и Drive metadata).

На телефоне/эмуляторе не проверялось. Требуется пользовательская проверка быстрых переходов LyricsScreen↔FullPlayer↔QueueSheet и смен motion→motion/motion→static на HONOR. Не выдавать JVM/GL проверки за подтверждение отсутствия всех артефактов на устройстве.


## 2026-09-23 — обратная связь: чёткий motion остался под лирикой/очередью

Пользователь прислал938878/938877 после APK motion-transitions: на обоих экранах остаётся чёткое tall-изображение The Weeknd, текст плохо читается. Предыдущую проверку JVM/Mesa НЕ считать подтверждением исправления на HONOR. По разрешённому пользователем варианту для LyricsScreen и QueueSheet выбран обычный static artwork через прежний тройной ArtworkBlur. Больше не полагаемся на смену presentation uniform для скрытия чёткой обложки: обе motion-поверхности FullPlayer выключаются через existing Crossfade при открытии любого из двух экранов. Статичный renderer явно включён независимо от готовности motion. На возврате motion плавно появляется; в AirSheet остаётся живая миниатюра. Matching и duration-фильтр не менялись.


### APK с обычным фоном LyricsScreen/QueueSheet — 2026-09-23

assembleDebug +57 JVM tests artwork/ui.player прошли (0 failures/errors/skipped). Изменение производственного кода только в FullPlayer.kt: при showLyrics||showQueue включается SharedArtworkBackground, обе motion-поверхности уходят через450мс Crossfade и удаляются. FullPlayer и AirSheet сохраняют motion. Подпись прежняя, хеш исходника после сборки совпал; Drive размер/MD5 совпали. Android runtime не проверялся.

APK: [LMG-VK-debug-static-lyrics-queue-2026-09-23.apk](https://drive.google.com/open?id=1_I8VnOn_8GTD6YSEIUf28xDmnGJ3DIeI); размер 187209413 байт, SHA256 `832eb119466a620c34ef117086c13086b4e8c1f5c8ad4543caa1134c6f696bab`, MD5 `5c8cc09be5c089c0b42d173ca379cf64`. Локально: `/root/LMG-VK/app/build/outputs/apk/static-lyrics-queue/LMG-VK-debug-static-lyrics-queue-2026-09-23.apk`. Артефакты `/root/LMG-previews/static-lyrics-queue-2026-09-23/`.


## 2026-09-23 — progressive blur по образцу Apple Music

Пользователь сравнил Poppy — Bruised Sky в Apple Music и LMG (938879–938881): прежний короткий тёмный переход не совпадал по цвету и воспринимался полосой. MotionArtworkRenderer теперь использует три GPU-уровня размытия 256/96/32 с шестью Gaussian passes на каждом. В нижних 40% portrait-изображения плавно увеличивается радиус (sharp → gentle → medium → strong) одновременно с переходом к подложке. Верх остаётся чётким. После непосредственного сравнения preview с Apple начало смещено с 50% на 60% высоты картинки, чтобы дольше сохранять нижние детали. Это приближение по предоставленным кадрам, не утверждение об идентичности алгоритму Apple.

Цвет portrait-подложки вычисляется из шести проб нижней части сильно размытого текущего кадра, без прежнего усиления насыщенности. Коэффициент яркости .70, минимальная яркость .12. На Poppy получен тёплый серый RGB152/143/143 вместо прежнего100/86/87; на HEALTH сохранён нейтральный серый. Placeholder-палитра согласована по яркости, переход preview начинается на60%. Пирамида всех трёх уровней строится для portrait outputs; square background продолжает использовать coarse blur. CPU readback видео не добавлен.

Статичная обычная обложка с прежним тройным блюром для LyricsScreen/QueueSheet сохранена. AirSheet сохраняет motion-миниатюру. Переходы и lifetime из предыдущей правки сохранены. Лаги лирики и matching/duration остаются отдельными задачами.

Проверено: assembleDebug и57 JVM tests artwork/ui.player успешны; реальные OES/blur shaders компилируются/линкуются в Mesa, offscreen2D рендеры Poppy/HEALTH без GL errors. Проверено сохранение чёткого верха, отсутствие скачка на краю, нейтральный HEALTH и падение пространственной детализации checkerboard после компенсации прозрачности. Подбор ширины выполнен сравнением с пользовательским скриншотом Apple. Это НЕ тест Android TextureView/MediaCodec и НЕ проверка на HONOR.

APK: `LMG-VK-debug-motion-progressive-blur-2026-09-23.apk`; размер 187622399 байт; SHA256 `21fc07ef58c990bdcaa4fdccaeee8f5cc799ecc320ec2f388f580318b4459f30`; MD5 `a4b92f0aded4813386d99ada8bb65213`. Артефакты: `/root/LMG-previews/motion-progressive-blur-2026-09-23/`.

Доставка: [APK progressive blur](https://drive.google.com/open?id=1tN4nxrGoXdx8iyTz28atEdGP8SgfMd0o). Google Drive размер/MD5 совпали с локальным APK; подпись прежняя.


## 2026-09-23 — переход motion опущен и разделён на blur/fade

Отрицательная обратная связь после APK progressive-blur:938884 показывает, что LMG слишком рано теряет детали по сравнению с Apple Music и переход не выглядит бархатным. Предыдущее визуальное сравнение на Mesa не подтвердило соответствие на HONOR.

В MotionArtworkRenderer разделены нарастание радиуса и исчезновение картинки. Раньше radius вычислялся из visibility, и сильный блюр появлялся уже на почти исчезнувшем изображении. Теперь глубина от верха artwork управляет ими независимо: blur smoothstep(.65,1.18)*3; fade smoothstep(.68,1.12). Цвет нижнего края, уже размытого на GPU, продолжается ещё на12% высоты artwork за физическую границу кадра, вместо завершения перехода строго на этой границе. Геометрия самого видео не растягивается; используется clamp координаты текстуры. Начало сохранения/исчезновения деталей ниже, чем в предыдущем APK. Три blur-levels и вычисление цвета подложки сохранены.

Preview до первого video frame тоже начинает затухать ниже (68%) и использует несколько остановок smoothstep вместо линейного fade; он по-прежнему заканчивается внутри высоты preview и не воспроизводит расширение video shader. Это стартовый fallback, не проверка первого кадра на устройстве.

Рендеры Poppy/HEALTH сравнены со скриншотами Apple и предыдущей реализацией. EGL/Mesa OES shaders compile/link; проверены сохранение верхней области, плавность нижнего края, отсутствие цветного оттенка на HEALTH. На синтетическом checkerboard детали на600px сохраняются сильнее прошлого APK; усреднённая по полосам пространственная детализация падает к низу:37.71→21.21→2.21→0.12. Усреднение по полосам исключает зависимость теста от попадания строки точно на границу клетки. Square backdrop пиксельно совпал с прежним, промежуточные presentation fractions соответствуют непрерывной смеси±1.1/255. Проверки — offscreen2D подстановка, не Android/OES playback.

assembleDebug и57 JVM tests artwork/ui.player успешны. Устройство/эмулятор не запускались. FullPlayer сохраняет motion, Lyrics/Queue обычный static backdrop с тройным блюром, AirSheet — motion-миниатюру. Артефакты: `/root/LMG-previews/motion-soft-transition-2026-09-23/`.

Доставка: [APK motion-soft-transition](https://drive.google.com/open?id=14Ux4qujrOGg_6GAcWX2HbL5EGoZ3Vt7V). Размер 187622431; SHA256 `72af7141430fae50d184f25423ad6ff1ae3ebebb7e830ab30235bf395f4b2e15`; MD5 `736f0c8e0b94ed58a582622519c22f01`. Google Drive размер/MD5 совпали; подпись прежняя.


## 2026-09-23 — живой ambient, переменный Gaussian и линейный RGB

Пользователь передал разбор от Gemini: два слоя, variable blur, linear-light, нормализация краёв и более низкая геометрия. В тексте пропали численные значения и не было подтверждающего декомпила. Использовано как направление реализации, НЕ как доказанный алгоритм Apple Music/iOS. Публичная документация описывает [Gaussian convolution](https://developer.apple.com/documentation/metalperformanceshaders/mpsimagegaussianblur) и [sRGB/linear conversions в графическом pipeline](https://wikis.khronos.org/opengl/Image_Format), но не подтверждает приведённые утверждения о внутреннем устройстве Apple Music.

Изменены MotionArtworkRenderer и MotionArtworkSurface. Удалены portraitBackdrop с шестью усреднёнными цветами и смешивание sharp/gentle/medium/strong. Теперь ambient — весь видеокадр, растянутый на экран и глубоко размытый в двух32×32 ping-pong FBO (шесть Gaussian passes). Это пространственно неоднородная динамическая картинка. Яркость ambient .46 в linear RGB, нижний порог luminance .012; усиления насыщенности нет.

Foreground сохраняет пропорции с center crop: высота fit-width ограничена70–86% экрана, вместо прежнего cap66%. Верх рисуется непосредственно из OES, ниже54% экрана используется отдельный результат variable blur. На кадр готовится foreground scene шириной384 и высотой192–1024 по аспекту output, затем горизонтальная и вертикальная33-tap Gaussian convolution. Sigma непрерывно растёт по quintic smootherstep от54% до84% экрана, максимум .065 ширины. Нормируются реально допустимые tap weights, координаты за пределами0..1 отбрасываются. Это двухпроходное приближение пространственно-переменного Gaussian, а не точная2D свёртка с одинаковой sigma для всех горизонтальных строк внутри вертикального ядра. Нельзя называть алгоритм1-в-1 CAFilter.variableBlur.

Feather переднего слоя58–84% экрана, независимо от радиуса. Для читаемости foreground exposure постепенно переходит к .46 между54–80% высоты. Оба слоя композитятся в linear RGB, результат переводится в sRGB; малый статичный dithering±0.5/255 в размытой области снижает ступеньки8-bit output. Это настроенные параметры LMG, не добытые из Apple численные константы.

При доступных OES_texture_half_float/linear и EXT_color_buffer_half_float промежуточные FBO используют FP16 linear. Есть проверка framebuffer completeness и fallback на RGBA8 с хранением sRGB; в fallback каждый bilinear sample вручную декодирует четыре соседних texel в linear до интерполяции. Веса blur и alpha compositing работают в linear в обоих путях. OES decoder frame трактуется как SDR/sRGB; отдельный HDR/BT.2020 color-management в эту правку не добавлен. Исходное масштабирование decoder texture выполняет sampler, поэтому это не обещание идеальной фотометрической точности всего входного video pipeline.

Рабочие большие FBO выделяются только при открытии portrait output. Ambient/portrait подготовка кешируется по версии видеокадра и аспекту: анимация presentation для того же кадра не пересчитывает свёртку. CPU readback, новый decoder и связь с audio pause не добавлены. Preview геометрия согласована с70–86%, под ним размытая32px статичная preview-картинка; foreground preview растворяется через alpha mask. Стартовый Compose preview остаётся приближённым fallback, не точной копией video shader. Lifecycle/Crossfade и отдельный static background Lyrics/Queue сохранены; AirSheet остаётся с живой миниатюрой.

Проверка EGL/Mesa: три реальных GLES/OES fragment shaders compile/link; offscreen2D подстановка Poppy и HEALTH в FP16 и RGBA8. Однотонные цвета5/40/128/245 и40,150,220 сохраняются после blur включая границы±2/255. Проверка поймала и устранила потемнение крайней строки из-за сравнения с half-texel координатой: валидность теперь0..1, с нормализацией весов. Black/white midpoint187 вместо ожидаемых188 (linear light); ширина размытой границы увеличивается1→12→42→75→92px. Ambient неоднороден и меняется при изменении входного кадра; presentation fractions соответствуют linear-light смеси. FP16/fallback на Poppy средняя разница0.085/255,99-й перцентиль1/255. HEALTH остаётся монохромным. Это проверка графических вычислений на Mesa, НЕ Android TextureView/MediaCodec/HONOR и НЕ замер GPU времени на телефоне. Полное сравнение с Apple и плавность нужно оценить на устройстве.

assembleDebug +57 JVM tests artwork/ui.player успешны. Исходники и DEX проверены; прежняя подпись APK. Артефакты: `/root/LMG-previews/motion-linear-variable-2026-09-23/` (before, source-hashes, implementation.patch, render-check.py/log, Poppy/HEALTH/Fallback renders, build.log, tests, APK/Drive metadata).

Доставка: [APK motion-linear-variable](https://drive.google.com/open?id=1Sk4AMCqrbABVS0JSlKQTMFokCHLhzet8). Размер 187626691; SHA256 `fbf0a79447be89e2e0f157e54251b5bbb62b4fe17ff3e02ea00b27a0c0ac6055`; MD5 `b24261ebb58bcf05298dee4110eebfbd`. Google Drive размер/MD5 совпали; подпись прежняя.


## 2026-09-23 — motion не ставится на паузу при Lyrics/Queue

Пользователь требует непрерывный цикл motion для текущего трека: вход/выход LyricsScreen и Queue не должен останавливать видео. Причина в MotionArtworkLifetime.canPlay: требовался хотя бы один attached output. После450мс Crossfade в static Lyrics/Queue последний output удалялся, unbind вызывал updatePlaying и playWhenReady=false.

canPlay теперь зависит только от того, что playback не закрыт и не retired. Удалены active/setPlayback и SideEffect, связывавшие текущий motion с STARTED Activity: по запросу пользователя UI-переходы и скрытие Activity больше не устанавливают программную паузу. STARTED оставлен только для prefetch следующего трека. Текущий беззвучный ExoPlayer запускается после подключения decoder Surface, ещё до первого видимого output; REPEAT_MODE_ONE сохранён. Пауза аудио по-прежнему не влияет на motion.

Renderer уже постоянно держит decoder SurfaceTexture и EGL anchor: когда видимых outputs нет, onFrameAvailable продолжает updateTexImage и принимает текущие кадры; draw не запускает blur/composition при пустом списке outputs. Возврат FullPlayer привязывает новый TextureView к тому же playback и последнему текущему кадру. Static triple blur на Lyrics/Queue и motion thumbnail AirSheet сохранены. На смене трека прежний playback становится retired, останавливается и освобождается после завершения outgoing surfaces; ошибки player/renderer и уничтожение владельца всё ещё могут остановить его. Не обещать продолжение декодирования после принудительного завершения приложения ОС.

Обновлён regression test закрытия FullPlayer: ожидает canPlay=true при нуле outputs. Добавлены сценарии старта до первого экрана, повторных Lyrics/Queue/AirSheet выходов и возвратов, запрета оживления закрытого playback. JVM тесты проверяют policy/lifetime, не реальный MediaCodec. Устройство/эмулятор не запускались. Артефакты: `/root/LMG-previews/motion-continuous-2026-09-23/`.

Проверено: assembleDebug +60 JVM tests artwork/ui.player успешны (0 failures/errors/skipped). В compiled getCanPlay отсутствует проверка outputs; остаются только closed/retired. Исходники после сборки совпали с зафиксированными SHA256. Проверки на HONOR ещё нет.

Доставка: [APK motion-continuous](https://drive.google.com/open?id=1jw3xeBoWAPTRiME1e-iJAwR4c3S6ehpx). Размер 187626515; SHA256 `d475a7ad01b053623521b78c91875b6bf22ad926bb081db80198c68bea5c1384`; MD5 `d67db5597a20699fe05ef1590daef5c2`. Google Drive размер/MD5 совпали; подпись прежняя.


## 2026-09-23 — доставка первого кадра и постоянные поверхности под окнами

После APK motion-continuous пользователь сообщил: открытие AirSheet по-прежнему убирает анимацию; после загрузки по сети видео не стартует визуально до открытия/закрытия окна. Успех 60 JVM tests lifetime НЕ подтвердил исправление TextureView на HONOR. Причина зависания на устройстве не установлена трассировкой; следующая правка устраняет найденную зависимость доставки кадра от UI redraw.

MotionArtworkRenderer теперь явно сообщает UI об успешном eglSwapBuffers каждого output. MotionArtworkFrameGate допускает один неподтверждённый буфер на поверхность, запоминает новый dirty frame и повторно запрашивает presentation при задержке подтверждения. Outputs независимы: ожидание FullPlayer не блокирует миниатюру AirSheet. Уведомления главного потока объединяются через AtomicBoolean. onSurfaceTextureUpdated подтверждает потребление, но больше не является единственным способом проявить почти прозрачный TextureView.

MotionArtworkPlayback держит привязанный TextureView с alpha=1. MotionArtworkSurface рисует preview поверх него и плавно убирает preview после первого поставленного в очередь кадра. Каждый callback явно инвалидирует TextureView и drawWithContent через Compose state, чтобы обновлялось также содержимое layerBackdrop, используемое AirSheet. Если TextureView уже доступен при bind, существующая SurfaceTexture подключается сразу. Поздние callbacks старой поверхности проверяют актуальную привязку. Логи decoder first_frame / output first_queued / output first_presented / isPlaying позволяют отличить остановку decoder от отсутствия UI presentation, без логирования каждого кадра.

FullPlayer больше не выключает motion surfaces при showLyrics/showQueue. Обычный SharedArtworkBackground с прежним тройным блюром расположен поверх постоянного motion и плавно проявляется за420мс; при возвращении исчезает. Motion thumbnail AirSheet сохраняется. Пауза аудио не управляет motion. Шейдеры/геометрия/цвета motion в этой правке не изменены.

Пять новых JVM tests проверяют первый redraw без acknowledgment, задержанный acknowledgment без заполнения очереди,60 доставленных кадров без стороннего UI события, независимость AirSheet от задержки FullPlayer, отсутствие redraw loop после подтверждения неизменённого кадра. Это проверки управления доставкой, НЕ аппаратного TextureView/MediaCodec. Устройство/эмулятор не запускались.

Артефакты: `/root/LMG-previews/motion-frame-delivery-2026-09-23/`.

### Проверенные материалы Apple Music Android

Указанные пользователем локальные файлы доступны: `/root/applemusic_6.5.2_jadx/resources/res/layout/fragment_player_main.xml`, `view_motion_container.xml`, `/root/applemusic_6.5.2_jadx/sources/com/apple/android/music/player/f1.java`, `G6/d.java`, `S6/s.java`. Это декомпилированные файлы, а не опубликованный Apple исходный код; происхождение архива отдельно не удостоверялось.

Разметка подтверждает motion_switcher с двумя контейнерами под player_fragments_host. TextureView в motion container имеет H,3:4; есть отдельный preview поверх видео. G6.d при уничтожении поверхности останавливает/сбрасывает controller; метод SurfaceTextureUpdated пуст. Все перечисленные Gemini привязки pause к onStop/SCREEN_OFF/мини-плееру пока независимо не проверены.

f1 подтверждает нижнюю1/8 полоску уменьшенного кадра (width/8 с округлением вверх до10, height/16), saturation1.4, Toolkit blur25, black35% и white4% scrims. BitmapShader: X clamp, Y mirror, ширина1.25 с центральным сдвигом, радиальная маска; view scaleY=-1. Barrier margin=-150dp. Из2×2 сэмпла получается средний RGB. Четыре alpha stops0/204/242/255 на позициях0/.7/.9/1; конец градиента —40% высоты НИЖНЕГО overlay view, а не40% всего дисплея.

Этот код противоречит прежнему утверждению Gemini, что Apple никогда не использует средний цвет. В текущую правку доставки кадров новый нижний фон не включён: renderer всё ещё использует предыдущий live ambient/variable Gaussian. Для дальнейшего соответствия Apple следует опираться на проверенную геометрию и нижнюю полоску, а не на неподтверждённое описание iOS CAFilter.


## 2026-09-23 — применён фон из Apple Music Android, общий APK

Пользователь уточнил, применён ли присланный разбор в коде. Предыдущий раздел фиксировал только изменения surfaces/frame delivery. Теперь нижняя подложка также заменена; общий APK включает ОБЕ части. Промежуточный APK motion-frame-delivery не является последней сборкой.

В MotionArtworkRenderer удалена большая384px рабочая текстура с двухпроходным variable Gaussian. Tall layout теперь3:4 по ширине, ограниченный высотой доступного output; overlap150dp (в пикселях через density), вместо условных54–84% дисплея. Геометрия вынесена в MotionBackdropLayout; preview получил ту же высоту картинки и привязанное к overlap начало исчезновения. Сам Compose preview до первого видео остаётся упрощённым: обычный размытый preview/палитра, а не второй GPU pipeline.

Нижняя1/8 полоска уже уменьшенного изображения захватывается напрямую в маленький sRGB/RGBA8 FBO. Размер соответствует f1: ceil(width/80)*10 на max(round(videoHeight/16)/8,2). Saturation1.4 с коэффициентами Android ColorMatrix .213/.715/.072. Два51-tap Gaussian passes, radius25, sigma10.6; kernel radius→sigma сверён с [Android RenderScript Toolkit Blur.cpp](https://github.com/android/renderscript-intrinsics-replacement-toolkit/blob/main/renderscript-toolkit/src/main/cpp/Blur.cpp): sigma=.4*radius+.6, clamp-to-edge и нормированные веса. Это GPU-адаптация: bilinear sampling и промежуточное округление RGBA8 отличаются от реализации Toolkit/Canvas, поэтому побитовая идентичность Apple не заявляется.

Под видео — зеркальное продолжение полоски, горизонтальный масштаб1.25/сдвиг-.125 ширины, радиальная alpha mask с геометрией f1.b и учётом scaleY=-1. Цвет полоски после чёрного89/255 и белого10/255 scrims. Среднее четырёх сэмплов2×2 даёт цвет градиента с alpha0/204/242/255, stops0/.7/.9/1, конец на40% НИЖНЕЙ подложки. Фон пересчитывается с новыми decoded frames, включая паузу аудио. Дополнительно проверен t0.g: legibility color#b3171717, mask alpha .2→0→1 со stops0/.2/1; применён до нижних overlays. Это заменяет прежний полный ambient в portrait presentation; старый32px ambient сохранён для square/nonportrait режима и presentation перехода. CPU getBitmap/readback кадров не добавлен, новый decoder не добавлен.

Сохранены исправления текущего сеанса: постоянные TextureView FullPlayer под статичным triple-blurred фоном Lyrics/Queue, независимый output AirSheet, явные draw notifications после eglSwapBuffers, startup preview поверх alpha=1 TextureView. Audio pause не управляет motion. Обработка track retirement/Crossfade сохранена.

Проверено: assembleDebug +69 JVM tests artwork/ui.player (0 failures/errors/skipped). Четыре новых layout tests проверяют3:4,150dp при разных density, отсутствие растяжения на более высоком дисплее, конечные параметры малых/коротких surfaces. Реальные GLES/OES shaders compile/link в Mesa; offscreen2D рендеры Poppy/HEALTH. Проверено сохранение постоянных цветов при convolution, saturation/scrims, влияние только нижней полоски на фон, обновление фона от следующего кадра, независимый численный расчёт radial mask, отсутствие скачка на video edge, непрерывность presentation и RGBA8 fallback. Poppy bottom mean RGB150/144/143, HEALTH46/46/46. Сравнение с Apple screenshot сохранено в poppy-comparison.jpg. Это НЕ Android TextureView/MediaCodec/HONOR тест и НЕ доказательство полного визуального совпадения при проигрывании. Устройство/эмулятор не запускались.

Артефакты: `/root/LMG-previews/motion-apple-backdrop-2026-09-23/`; предыдущая часть frame delivery: `/root/LMG-previews/motion-frame-delivery-2026-09-23/`.

Доставка общего APK: [motion-apple-backdrop](https://drive.google.com/open?id=1M4MuOacp8N0jKrT3sFmxQXzJ_Q-OKSxh). Размер 187636239; SHA256 `095c21652fc751cdd47d214e7ee79a3b4cb088f5bee1d52e29d16ba852968368`; MD5 `6438c371ca0e19bd6c3020b447867c78`. Google Drive размер/MD5 совпали, прежняя подпись подтверждена.


## 2026-09-23 — повторные зависания motion: цепочка Apple и удаление UI-барьера

Пользователь подтвердил: фон в motion-apple-backdrop подходит, БОЛЬШЕ НЕ МЕНЯТЬ. Но холодный старт и открытие AirSheet по-прежнему дают остановку/исчезновение анимации. Нельзя считать предыдущие69 JVM tests и shader checks подтверждением исправления воспроизведения на HONOR.

Проверена цепочка Apple Android6.5.2 в локальном декомпиле:
- f1.d/e получают EditorialVideo.video из metadata Song, выбирают tall/square по flavor и аспекту.
- f1.g не пересоздаёт controller при том же URL. f1.h назначает G6.d listener и сразу подключает уже имеющуюся SurfaceTexture; иначе Available подключит позже.
- G6.d создаёт/получает SimpleMediaPlayerControllerImpl, выключает audio, включает video и repeat-all, регистрируется в G6.b. d(surface) задаёт video output и prepare(uri,false).
- G6.d.b сохраняет play intent до READY (f4918h), либо сразу вызывает play у подготовленного player. G6.c на READY применяет intent и вызывает callback готовности независимо от перерисовки UI.
- SimpleMediaPlayerControllerImpl → ExoSimplePlayer → HlsMediaSource.Factory с CacheDataSourceFactory (при наличии asset cache). В декомпиле5s min/max buffer,100ms startup/rebuffer, forceHighestSupportedBitrate=true, setMaxVideoSize по размеру view.
- onSurfaceTextureUpdated в G6.d пустой. Нет прикладного разрешения каждого следующего кадра через этот callback. SurfaceDestroyed останавливает/reset controller, поэтому сохранение View под оверлеями по-прежнему важно. PlayerSongViewFragment держит два motion containers и preview до ready. Полный список lifecycle pause triggers отдельно не заявляется доказанным.

У нас выявлена оставшаяся зависимость: MotionArtworkFrameGate после одного eglSwapBuffers выставлял pending=true; снять его мог только onSurfaceTextureUpdated→frameConsumed. Добавленные в прошлой сборке invalidate/frameTick не устраняли этот барьер. Новый decoded frame лишь становился dirty и мог бесконечно ждать UI acknowledgement. Это подтверждённый дефект управления потоком в коде; без трассы телефона нельзя объявлять его доказанной единственной причиной всех видимых зависаний.

Изменения: MotionArtworkFrameGate заменён на MotionArtworkFrameDelivery с dirty flag без pending/consumed. Renderer получает новые кадры от decoder SurfaceTexture и сразу выдаёт их в outputs; onSurfaceTextureUpdated теперь только считает presentation для диагностики. EGL swap interval0 проверяется, minimumSwapInterval пишется в лог. В Android [Surface.cpp](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/main/libs/gui/Surface.cpp) interval0 включает async BufferQueue; [BufferQueueProducer.cpp](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/main/libs/gui/BufferQueueProducer.cpp) заменяет ещё не взятый droppable frame свежим. Прикладная очередь не ждёт Compose. Это не гарантия отсутствия любых GPU/fence stalls на конкретном драйвере.

Входящие decoder callbacks объединяются в одну pending GL task. Уведомления UI по-прежнему объединяются через AtomicBoolean; preview и явная Compose draw invalidation сохранены. Изменять цвета, шейдеры, blur geometry, FullPlayer layering или AirSheet внешний вид запрещено — они остались прежними.

Запуск: play intent выставляется до prepare и повторно применяется на READY с учётом retired/failed, без ожидания открытия окна. ProvideMotionArtwork сохраняет текущий lookup на время обновления метаданных того же track.id и переиспользует playback по track.id + playbackUrl, как URL identity guard Apple. Уточнение durationMs в PlayerController.updatePosition раньше меняло ArtworkQuery и могло пересоздавать motion с тем же URL. При смене самого трека, URL, videoClip либо подтверждённом отсутствии motion прежний playback всё ещё retired через Crossfade.

Диагностика без URL/ключей авторизации: lookup cache source/time, prepare intent, decoder first frame, first EGL queued/presented, pipeline state/position/bufferedMs/decoded/submitted/uiFrames/inputAge/stage/swapMs. Heartbeat раз в5s первые30s, далее раз в30s, удаляется при retire/close. Main читает volatile GL counters, поэтому stuck swap виден даже если GL Handler больше не выполняет задачи. EGL output failures теперь включают mode/error code/message. Следующая проверка телефона должна использовать эти MOTION строки, а не ещё одну догадку.

Пять regression tests FrameDelivery заменяют прежние тесты, которые фактически закрепляли ожидание UI ack:300 decoded frames при отсутствующем UI callback, первый кадр после сети, многократный AirSheet open/close, coalescing dirty updates, новая поверхность с уже декодированным кадром. JVM не проверяет реальный Android BufferQueue.

Артефакты: `/root/LMG-previews/motion-pipeline-2026-09-23/`. Фон заморожен через hash файлов/визуальной части MotionArtworkSurface и точное сравнение блока shader constants. Устройство/эмулятор не запускаются; подтверждение на HONOR остаётся необходимым.

Проверено: финальные assembleDebug +69 JVM tests artwork/ui.player успешны (0 failures/errors/skipped). В DEX присутствует FrameDelivery, отсутствуют FrameGate и frameConsumed. Замороженные shaders/layout/UI фона совпали с одобренной версией. Доставка: [APK motion-pipeline](https://drive.google.com/open?id=1PjZgZRRvkhlEhBVXr9wCeot7Pt1R-Bf-); размер 187674463, SHA256 `50ad0f7e3e11f68f7a2f4546cb60fa421f0e01a2922c4d0ebd5861d62dd0fd7f`, MD5 `2532e4bd9a8099070958dadfd7ff9db3`. Подпись прежняя; Google Drive размер/MD5 совпали. Проверки на HONOR нет.


## 2026-09-23 — пользователь подтвердил motion-pipeline

После APK motion-pipeline пользователь сообщил: «Вот теперь вообще отлично отлично» и предложил считать декомпил Apple Music эталоном. Это положительное подтверждение поведения последней сборки пользователем; подробная матрица сценариев/измерения на устройстве не предоставлены.

Сохранять текущую выдачу кадров без ожидания onSurfaceTextureUpdated и переиспользование playback для того же track.id/URL. Одобренный фон не менять (прямое требование пользователя). При дальнейших изменениях поведения motion/player сначала изучать проверяемую цепочку в `/root/applemusic_6.5.2_jadx/sources/` и связанных resources; отличать найденные в коде факты от предположений. Декомпил служит эталоном поведения, а ограничения и UI LMG определяются требованиями пользователя.


## 2026-09-23 — переходы Lyrics/Queue и сохранение лирики

Пользователь попросил изучить реальные переходы Apple Music Android LyricsScreen/QueueSheet ↔ FullPlayer и смену песни, убрать артефакты и повторную загрузку лирики по 5 секунд. Проверены локальные t0.D1/F1, E.f42328i, PlayerSongViewFragment.h2/ready callback, S0 и Activity-scoped PlayerLyricsViewModel (подробности в docs/PLAYER-TRANSITIONS.md и артефакте apple-review.md). Apple: fade500ms с (0.25,0.1,0.25,1), два motion containers под host, preview/ready координация; parsed SongInfo хранится во ViewModel, проверки текущего AdamID предотвращают повторный load.

LMG: общий interruptible transition для Lyrics/Queue, согласованные500ms fade, удалён масштаб всей лирики и ложный морф второй большой квадратной обложки Queue в shared-host. Уходящий motion сохраняет собственную геометрию; текущая обычная обложка остаётся до первого queued motion frame. Скрытый статичный фон завершает существующий секундный crossfade перед засыпанием и прогревает новый cover, чтобы при возврате не показывать незаконченный прошлый кадр. Математика, цвета и геометрия фона не менялись. Playback/renderer/frame delivery/lifetime совпали по SHA256 с одобренной версией; визуальное тело MotionArtworkContent также совпало. AirSheet и непрерывный motion сохранены.

LoadedLyricsStore хранит parsed lyrics8entries/200k text weight; ключ включает track/title/artist/duration/embedded/providers/locale. Кэш раскладки сохраняет2entries/16000weight и stale-font validation. RetainedLyricsCache делает shared in-flight работу независимой от жизни экрана; закрытие не отменяет load/prelayout, reopening получает cached result или присоединяется к нему. Max2 parallel jobs/cache, bounded pending,60s timeout. Invalidated/stale completions не публикуются; ошибки и пустые ответы не кешируются как успешные. Sources snapshot передаётся до LyricsParser. Diagnostics lyricsData/cacheHit и lyricsLayouts/cacheHit. Cold first load всё ещё зависит от сети, после eviction/смены параметров потребуется повторная подготовка.

assembleDebug и170JVMtests passed,0 failures/errors/skipped. Без устройства/эмулятора; визуальная проверка HONOR остаётся у пользователя. Артефакты `/root/LMG-previews/player-transitions-2026-09-23/`; финальный лог build-verified.log. Предыдущие build.log/build-final.log — промежуточные неуспешные проходы, не финальная проверка.

[APK player-transitions](https://drive.google.com/open?id=17FpWSLsZapinbDCcqvWN7noZj8BpzJqg); размер 192824943; SHA256 `f45a2a4b7c9276478ca0f9320b49ab3c6c9df9735d2634543477526d640e3bd6`; MD5 `81667bfb6f39144c95b3de8c1cb4a2a6`. Google Drive размер/MD5 совпали; debug signature прежняя.


## 2026-09-23 — исправление после отклонённого перехода

Пользователь отклонил APK player-transitions: fade окон не воспроизводит поведение motion при Song ↔ Lyrics/Queue. Предыдущий разбор был неполным: fade500ms относится к содержимому фрагмента, а геометрией motion управляет отдельный shared-element transition.

Проверенная цепочка: RunnableC3408r0/RunnableC3410s0 → t0.u1 (строка1026) → u0.onAnimationUpdate/v0. t0.u1 берёт текущие x/y/width/height/alpha motion_switcher. При переходе в Lyrics/Queue цель — координаты thumbnail guidelines, квадрат player_thumbnail_height60dp и alpha0; назад — origin0,0, полноэкранные размеры и alpha1 для tall motion. u0 интерполирует одновременно положение, размеры, прозрачность и скругление. p032b9.f задаёт300ms и E.f42327h = (.2,.06,0,1). Отдельный fade фрагмента остаётся500ms.

Реализация LMG: motion действительно сжимается к реальной миниатюре Lyrics/Queue (onGloballyPositioned + localBoundingBoxOf), скругляется и затухает; назад разворачивается. Используются реальные48/54dp миниатюры приложения вместо изменения layout под Apple60dp. Слой статичного фона находится под движущимся контейнером, чтобы его fade не скрывал движение. На GPU применяется единый масштаб по ширине с изменением области clipping: видео сохраняет пропорции, а TextureView/EGL buffers не перевыделяются каждый кадр. Входящий native output имеет минимальную альфу0.001, чтобы первая отрисовка не была подавлена во время загрузки под открытым окном. Это адаптация геометрии Apple, не обещание побитового соответствия её ViewGroup relayout.

MotionPlayback, renderer/shaders, approved backdrop geometry, frame delivery, lifetime и кэш лирики не менялись (проверка SHA256). Не создаётся второй decoder или screenshot для анимации. Полноэкранное состояние имеет исходные размеры, scale1, без скругления и с alpha1. Экранные static artwork/triple blur и AirSheet остаются прежними по назначению.

Артефакты: `/root/LMG-previews/motion-sheet-morph-2026-09-23/` — точные ссылки на декомпил в apple-reference.md, focused patch, hashes, тесты/сборка и проверка доставки. Без устройства/эмулятора; визуальное поведение на HONOR ещё не подтверждено.


Проверка motion-sheet-morph: assembleDebug +177JVMtests успешны,0 failures/errors/skipped. SHA256 замороженных playback/renderer/background/cache файлов совпали; тело MotionArtworkContent совпало побайтно. В DEX есть MotionSheetGeometryKt/MotionSheetClip, прежний FrameDelivery, отсутствует FrameGate. Подпись debug прежняя.

[APK motion-sheet-morph](https://drive.google.com/open?id=17wt50O1JwKUMk8WzSRd39YWkhsASoCsB); 192824943 bytes; SHA256 `01042f004e0cd2b55bef8f9655569b7e94e542a3861f22b1514efbd08924ae5b`; MD5 `b990f486125fdc4a17b8046322a898c4`. Размер/MD5 Google Drive проверены. От пользователя ещё требуется визуальная проверка HONOR; её результаты здесь не предполагаются.


## 2026-09-24 — карточки каталога и встроенный TTML

Все карточки New/Main/Overview получают обработчик: native VK route через существующий VkLinkResolver, catalog section/curator или web fallback. HTTP(S)/относительные/query адреса больше не отбрасываются узким фильтром; catalogSectionId сохраняется в HomeCache schema9. Нажимается вся круглая карточка с подписью, mix и recommendation; play/settings кнопки сохраняют отдельные действия. Если VK не передал destination, диалог с обновлением вместо молчаливого no-op; назначения не угадываются по названиям.

Скачивание сохраняет оригинальный валидированный TTML в MP3 USLT / M4A lyrics. При отсутствии текста скачивание успешно завершается; повторная явная загрузка повторяет enrichment. Ready3 означает обработанные lyrics, старые загрузки проходят existing repair/staging route. EmbeddedLyrics определяет контейнер по bytes, читает file/content URI; LyricsRepository проверяет встроенные lyrics до сети даже с отключёнными провайдерами; legacy parser понимает TTML. Retag сохраняет существующие lyrics/cover. Shared tag artwork dimensions читаются из PNG/JPEG header без bitmap decode. Пользователь просил не тратить отдельные усилия на FLAC, поскольку в VK его нет; фокус MP3/M4A.

74 JVM tests,0 failures/errors/skips; assembleDebug успешен. Проверены точный TTML, cover, неизменность encoded MP3/M4A, extensionless reading и routing. Device/offline UI check не выполнен. Предыдущая debug подпись сохранена.

APK: https://drive.google.com/file/d/18CuAKs3tXY9MRdXiDOjNmpaP-KqjN2le/view?usp=drivesdk ;193783703bytes;SHA256 5f478c09dbbba73c30ebaa620b62f61c9b4cabe664feff9cc248b0b563226d55;MD5 6a4dbdddfa5926c19bc820c2e6f0d9b3. Размер/MD5 Drive совпали. Connector upload отказал только по 100MB limit; успешно доставлен через существующий gdrive_personal rclone в ту же APK папку. Артефакты /root/LMG-previews/cards-ttml-2026-09-24/. Параллельно Gemini собирал Stage4c в media3-lmg-review; эти изменения не объявляются здесь проверенными.


## 2026-09-24 — нативные карточки вместо запуска VK

Пользователь сообщил, что карточки New запускают официальный VK, и потребовал полностью нативное открытие без WebView. Причина: добавленный ранее VkLinkResolver.handle имел fallback reportAndOpenOutside/Android ACTION_VIEW. Карточки теперь используют отдельный handleInApp без внешнего запуска, включая ошибки API; native metadata artist/album/playlist имеет приоритет. Секции/кураторы остаются нативными; music key_url и /audio?catalog= идут через catalog.getAudio(url), его default section, существующие native blocks/section sheet, pagination по фактически возвращённому section ID. Retry сохраняет URL. general/explore и другие root tab names не принимаются за opaque section_id; z= имеет приоритет над внешней вкладкой. Быстрые повторные тапы отменяют предыдущий resolve. Внешние статьи и неизвестные destinations пока показывают объяснение внутри клиента; полноценный article reader не реализован. Точная проблемная карточка пользователем пока не названа.

В момент проверки в основном /root/LMG-VK уже присутствует Stage4c Media3BoundaryAudioSink.kt (mtime14:53 UTC), импортирующий LmgPcmBoundaryListener. Обычная компиляция с default1.5.1-lmg30 упала на этой зависимости; локальный1.5.1-lmg30-boundary1 содержит интерфейс. Финальные compile + selected catalog/layout JVM tests успешны с -PautomixExoplayerVersion=1.5.1-lmg30-boundary1. Default dependency/version не менялись. APK не собирался и не загружался по прямому указанию пользователя; текущий APK на Диске не содержит это исправление и исправление подписи встроенной лирики. Артефакты /root/LMG-previews/catalog-native-2026-09-24/.

Ранее пользователь подтвердил офлайн-работу встроенного TTML Bruised Sky на телефоне; фон с blur также одобрил. Общий API-ключ APK пока оставляем, per-user auth/лимиты запланированы позже; сейчас auth не менять.


2026-09-24: пользователь запросил APK и тем самым разрешил сборку. assembleDebug с -PautomixExoplayerVersion=1.5.1-lmg30-boundary1 успешен, source hashes навигации совпадают с протестированными. APK native-cards включает handleInApp/loadCatalogUrl, EmbeddedLyrics/DownloadLyrics и LmgPcmBoundaryListener; подпись прежняя. Ссылка: https://drive.google.com/file/d/1x69QeYZcBUkCqP9_X5MUFEQ8fVAFGfF5/view?usp=drivesdk . Размер199798215;SHA256 81f65f6c99ff40faa2bef4310f0c61b2e26f5acfbb25a59575e1ca5bee2b1911;MD5 81d77bba4d9e314921a78d10311d250c. Drive size/MD5 проверены. Это не подтверждение прохождения отдельной полной проверки Stage4c или теста навигации на телефоне.

### 2026-09-26 — доставка Android-изменений
Пользователь прямо указал: после изменений клиента сразу собирать APK и загружать на Google Диск, не ждать отдельной просьбы о сборке. Давать проверенную ссылку на загруженный файл. Это относится и к LMG Lyrics Plus + фоновой предзагрузке лирики.


## 2026-09-27 — сборки клиента только ARM64

Пользователь прямо указал: впредь собирать только arm64-v8a. В app/build.gradle.kts фильтр ARM64 установлен и для NDK packaging, и для externalNativeBuild. Не возвращать armeabi-v7a/x86 в обычные APK без нового указания пользователя. Финальная сборка с DSP тоже готовится только для ARM64.


## 2026-09-27 — собственный DSP-эквалайзер подключён к LMG VK

По запросу пользователя интегрирован архив LMG_DSP_Cpp17_v1.0.0.zip (SHA256 44b89c06bb78e0ea8cdca56a509814bcb6b7522e5db84dcc88771ebc4f64e7cd). Оригинал без изменений в native/dsp/vendor/lmg_dsp; отдельные PCM adapter + JNI liblmg_player_dsp. Новый DSP не зависит от Android AudioFX, JUCE или Oboe. Старые неиспользуемые классы не удалялись.

В Настройки → Воспроизведение → Эквалайзер DSP добавлены 10 графических полос/пресеты, 8 параметрических полос с 6 типами фильтра, preamp/headroom, limiter threshold/ceiling/release, общий bypass и сохранение настроек. По умолчанию DSP выключен. DspController — один producer, последнее изменение повторяется при QueueFull; каждый sink имеет собственную native history. DspAudioProcessor устанавливается после метра/DJ FX/normalization; native mixed PCM входит в эту же цепочку. Seek/flush сбрасывает историю, обычный drained same-format boundary сохраняет её. Configure откладывает смену формата до flush.

Ограничения текущей интеграции: float32 внутри DSP, PCM16 на выходе (Media3 float shortcut обходит custom processors и выключен); mono/stereo 44.1–192kHz, остальные форматы без DSP. Lookahead и attack = 0 для сохранения количества кадров/таймингов. Sample-peak limiter, не true peak; новый gapless scheduler не добавлялся. Все три Android DSP target оптимизируются -O2 даже в debug. Итоговый APK ТОЛЬКО ARM64 согласно новому указанию пользователя.

Проверки: 3 native CTest suites прошли в Release и под ASan/UBSan; реальный JNI проверен на bounds/QueueFull/retry/concurrent lifecycle; 4 lifecycle-теста реального Media3+JNI на JVM; финальные assembleDebug и 8 DSP JVM-тестов успешны. ABI/DEX/native library, совпадение подписи с предыдущим APK и размер/MD5 на Drive проверены. Тест на физическом телефоне и прослушивание ещё нужны; не считать их выполненными.

APK: https://drive.google.com/file/d/1ohhsEU4libfI9ObE2km1k7lwbLQWVMQY/view?usp=drivesdk . Размер 132055612; SHA256 4a12ac300b1abdccaacadce960bced4aa5fe10b648b55cfd1ef2ade44ba8dd3e; MD5 b3aba78c4f72e91cd12a78b5b9e5ccb3. Артефакты/логи/backup/scoped diff/source hashes: /root/build-lmg-dsp-integration/. Временный swap сборки отключён и удалён, постоянные настройки сервера не менялись. Пользовательские изменения в dirty tree сохранены; коммит/публикация исходников не выполнялись.


## 2026-09-27 — компактный DSP UI и отключение падающих теней

Пользователь подтвердил на телефоне, что DSP меняет звук, и нашёл собственную настройку баса. Новые требования: компактный собственный UI LMG, ползунок по образцу аудио-ползунка Full Player, в DSP без стекла; отключить надоевшие тени блоков, обложек и кнопок.

DspSettingsContent использует плоские карточки/переключатели/капсулы и новый FlatPlayerSlider: дорожка 6dp, бегунок 40×24dp, существующая пружинная модель FullPlayer без backdrop/blur/lens/тени. Строки 48dp, частота/значение по краям, при fontScale>1.3 подписи над ползунком. Сохранены параметры DSP, диапазоны и player_dsp_v1; добавлены русские названия пресетов. Ползунок поддерживает внешние изменения параметров, RTL, клавиатуру и accessibility progress; горизонтальный drag отделён от вертикальной прокрутки.

LiquidMetrics.CastShadowsEnabled=false — общий выключатель падающих теней: Card/Cover/Button/SecondaryButton/QueueDrag elevations. Четыре отдельные тени вторичных кнопок привязаны к токену; тени крупных обложек FullPlayer/QueueSheet — к castShadow(); внешний Shadow в LiquidSlider/LiquidToggle/AirPlaySheet не создаётся при false. Внутренняя оптика стекла, blur/lens/highlights сохранены. Не включать тени обратно без запроса пользователя.

Финальный assembleDebug успешен (10m03s), git diff --check чистый. APK только arm64-v8a, подпись прежняя; liblmg_player_dsp.so побайтно совпадает с предыдущим доставленным APK. Проверены 13 изменённых и 49 защищённых файлов по SHA256. Новые native/JVM тесты не запускались для UI-правки; визуальное поведение новых контролов на телефоне ещё не подтверждено. Коммита/пуша нет, прежний dirty tree сохранён.

APK: https://drive.google.com/file/d/1iMm5oEGpjhcVgL8brfhAbgOeFWKBSKpg/view?usp=drivesdk . Размер 137465278; SHA256 bd673867b713539f5267ed9a575606a7d9586520481cee5c4429e697024fcf8e; MD5 98d9e9469f19fe54f9ebf8f2dbb827f9. Размер/MD5 Drive проверены. Артефакты, backup, scoped patch, логи и метаданные: /root/build-lmg-dsp-ui/.


## 2026-09-27 — вертикальный DSP и справка по каждой полосе

Пользователь попросил укрупнить предыдущий компактный UI, сделать 1–8 овальными в собственном стиле LMG, перевести полосы в вертикальные. Дополнительно: маленький кружок «!» у каждой полосы с объяснением её действия.

DspSettingsContent: шрифты/переключатели крупнее, собственные монохромные капсулы вместо стандартного вида контролов; полосы 1–8 в сетке 4×2, при fontScale>1.3 — 2×4. Графический EQ — десять вертикальных фейдеров: значения сверху, частоты и «!» снизу. Минимальная ширина 36dp масштабируется по fontScale, на узком экране ряд прокручивается. Частота/усиление/Q/крутизна параметрического EQ также расположены вертикально рядом. Уровень и лимитер сохранили горизонтальные регуляторы с увеличенными подписями.

Новый FlatVerticalSlider: плоская дорожка 7dp, овальный бегунок 30×20dp, DampedDragAnimation из Full Player; увеличение вверх, заполнение усилений от нулевой отметки, без blur/lens/shadow. Обновляемые callback/value, drag cancellation, keyboard/accessibility progress, логарифмическая шкала частоты/Q сохранены. Модель DSP и настройки не менялись.

Новый values/dsp_help.xml: отдельные русские объяснения всех 10 частот (31–16k), что даст усиление/ослабление; оговорено влияние на область частот, а не изолированный инструмент. На каждой овальной кнопке 1–8 свой «!», справка отражает текущий фильтр и частоту этой полосы (номера не закреплены за диапазонами). Отдельные подсказки для частоты/усиления/Q/крутизны. Используется существующий GlassDialog с плоской поверхностью, прокручиваемым текстом и кнопкой «Понятно». Справка не изменяет настройки.

Проверки: assembleDebug успешен (10m08s), XML обработан, git diff --check чистый; 3 изменённых и 61 защищённый файл совпали с SHA256 manifest. APK только arm64-v8a, прежняя подпись, DSP native binary побайтно совпадает с проверенной базовой интеграцией. Нативные/JVM тесты заново не запускались для UI. На физическом телефоне новые жесты/внешний вид ещё не проверены. Full Player, отключённые тени и пользовательский dirty tree сохранены; коммита/пуша нет.

APK: https://drive.google.com/file/d/1fMVHjj1Kp63bYqRN9n2vSItWSTLk4O2i/view?usp=drivesdk . Размер 137508730; SHA256 e8fe54d4c59f3b93f32bdf4216414bb1d115dcfcd11fa21e1d598332a4868d8f; MD5 42eb03f617fe5c395b9f6a93c59fc573. Размер/MD5 Drive подтверждены после повтора из-за project API rate limit. Артефакты/backup/scoped patch/manifests/logs: /root/build-lmg-dsp-vertical/.


## 2026-09-30 — Approved detail UI port and US server cleanup

User explicitly approved porting the final HTML prototype (lmg-music-pages.html, preview v9) into Android, first deleting unnecessary US-server files. Removed ~4.2 GB: eight obsolete generated APKs, npm/APT cache, Gradle 8.4 distribution/cache/build-cache, obsolete Robolectric i4 jars. Latest DSP vertical-help APK, protected lyric backups, sources, chat histories, custom Media3, models and active audio/training downloads preserved. Background downloads continue consuming disk; freed total is not current free capacity.

Native UI: artist full-width portrait with cached progressive blur layers/fade matching page background, no sheet seam/grabber; genre above larger title; flat outlined play/shuffle capsules below hero. Album centered artwork and artist subtitle navigation; playlist wide artwork. Native releases grid with All/Albums/Singles & EP filters and genuine year metadata; artist album/single previews two columns with full-discography access. Existing catalogue/paging/actions/data preserved. Context menus opaque #303030 / #505050 outline, 22dp radius, 280dp width, 440ms opening and 190ms closing to the actual tapped ellipsis. Full-window transparent popup prevents transform clipping; bounds clamp, outside/back dismissal, scrollable long menus, keyboard/TalkBack action semantics. Track actions execute once after closing; download progress/cancel/retry remains visible. Full Player/DSP/native engine/global shadow policy untouched.

Changed: ui/components/{DetailScreenParts,TrackActionsSheet,DetailPopover,ProgressiveArtistArtwork}.kt; ui/screens/{ArtistDetailScreen,AlbumDetailScreen,PlaylistDetailScreen}.kt; two strings; DetailMenuPositionTest. Scoped before-files/diff/logs/manifests/report: /root/LMG-previews/detail-ui-2026-09-30/. Existing dirty tree preserved, no commit/push. First incremental Kotlin compile had stale top-level declaration errors; full -Pkotlin.incremental=false succeeded. Final assembleDebug + 3 geometry JVM tests passed (8m52s), diff --check clean. APK only arm64-v8a, same signer, DSP .so byte-identical to previous delivered APK. 59 non-target protected files unchanged. No physical-device/emulator visual or touch test; user should check HONOR.

APK local: /root/LMG-previews/detail-ui-2026-09-30/LMG-VK-detail-ui-arm64-2026-09-30.apk ; 137545662 bytes; SHA256 4c6b7682d0bc50933aef5f4610a723a3c4b4d830bffedec17a8aa7722abbec75 ; MD5 824021e30be78da10c3685ce60383b80 ; versionCode326/versionName1.1.0.
Delivery is ZIP containing that unchanged signed APK: https://drive.google.com/file/d/1Ijjq3przmfu9iP4CtAxR5979kjJgU8Vm/view?usp=drivesdk . ZIP45437536bytes; SHA256 17494542ebfc2ba7337070a92041514ffc494fa371395fefa28faac10255d2b1 ; MD5 779264250f27f55f687e5c4dcdfea071. Existing APK folder14XX6Ve1Ond6JSBXRbi_LpQQn1yVNocYA. User extracts APK and installs over current version. rclone gdrive_personal now fails invalid_grant (expired/revoked token); do not repeatedly retry unchanged credentials. Direct connector APK upload rejects >100MiB; ZIP upload succeeds. Drive metadata/readback/raw fetch confirmed size/name/parent. Remote hash was not exposed by connector and signed download URL returned403 from server, so remote MD5 verification is not claimed; local archive CRC and inner APK SHA256 verified.

## 2026-09-30 — Detail UI fidelity correction after user rejected first APK
User explicitly rejected the first native port: blur over face, mixed button styles, old tiles/count strip/information grid unlike the approved web prototype. Corrected Artist/Album/Playlist and shared detail components. DetailStyle scopes reference red #fa354b independently of dynamic system theme; outlined 48dp play/shuffle capsules; 44dp outlined top controls and centered title; flat secondary actions. Artist duplicate information grid/count card removed from main flow, information/personal stats retained in a separate view. Latest release is a flat 94dp artwork row; popular songs one full-width list; album grids have 9dp cover corners and lighter labels. Album queue/download/add inline; top menus use shared existing callbacks. No DSP/FullPlayer/lyrics changes.
Portrait remains sharp through upper70%; cached small CPU blur bitmaps have baked alpha starting70%/84%, page fade remains seamless. No offscreen destination-in masking. Portrait crossfade disabled locally; a dark first screenshot was an unfinished Coil loading transition in the test renderer, not proof of a device rendering fault. Main image now appears directly like the web reference. Do not restore the old strong middle-of-face blur.
Actually rendered production Compose components with fixture data via temporary Robolectric4.16/native graphics/API36 harness and compared PNGs against Playwright screenshots of the approved HTML. Artist390/360dp, album, playlist, popup action/close:5 tests; geometry3 tests; all8 passed. Native screenshot capture used decorView.draw because captureToImage timed out. No physical-phone test. Harness/deps are external to app source; final APK built without init script and verified to exclude test activity. Evidence, PNGs, tests, manifests/logs/backups: /root/LMG-previews/detail-ui-fidelity-2026-09-30/.
Final assembleDebug --offline --max-workers=2 -Pkotlin.incremental=true succeeded1m56s. APK137520518bytes, SHA2569be52bc7cddc3c02a478147a5f04f861dd0a2e1bd033b94cb5c4c45895828f1b; onlyarm64-v8a; same signer; DSP binary identical;59 protected files unchanged. ZIP45664103bytes SHA256d3f24ebabedf543b28508311f334de62a3f60ca49d4233d1b7ead5a5233bed56.
Delivery: https://drive.google.com/file/d/1xNsOhiRKsX16WAx1PrIiaXKTQ8Haq0Pz/view?usp=drivesdk ; file LMG-VK-detail-fidelity-arm64-2026-09-30.zip, folderAPK14XX6Ve1Ond6JSBXRbi_LpQQn1yVNocYA. Extract signed APK and install over existing. Old build preserved. User complained strongly about taking an hour: acknowledged excessive time spent on test environment. Future fixes should reuse this working harness, avoid repeated environment experiments/full builds, and deliver promptly after relevant verification.

2026-09-30 post-build cleanup: user explicitly demanded immediate RAM/cache cleanup after only ~500MiB remained available. Stopped confirmed IDLE Gradle9.1 daemon; its Kotlin compiler exited too (combined RSS~5.2GiB). Available RAM recovered to~5.6GiB. Cleared Gradle build-cache (~219MiB), stale temporary Robolectric8/i4 jar (~90MiB), generated unit-test APK (~8.6MiB). Preserve compiler/dependency caches needed for fast builds, browser sessions, running services and delivered APKs. Standing workflow: after delivery, stop idle Gradle/Kotlin daemons promptly and verify available RAM; do not leave multi-GB compiler processes resident. Disk still tight due ongoing data downloads; not a RAM issue.


2026-09-30 artist placement/quality fix: user reported popup shifting secondary row, whitespace, pixelated hero and visible seam. Root cause: DetailMenuButton emitted Popup as a separate sibling in SpaceBetween Row; popup now nested inside trigger Box. ArtistActionsStrip uses compact fixed8dp gaps, spacer before right menu; no top5dp gap. Anchor conversion accounts for activity/popup screen origins, popup gap scales with density. Hero no longer calls toThumb, uses separate full-display-resolution Coil cache key. VkArtistDto.heroPhoto selects largest advertised as/cs variant and MusicBackend picks largest matching artist across catalog pages. Progressive backdrop extends below390dp portrait through playback controls with clamped lower edge and smoothstep page-color fade. CPU cached blur retained; sharp portrait crop unchanged.
Evidence/backup/scoped.patch/native fixture screenshots/tests: /root/LMG-previews/detail-ui-placement-2026-09-30/. 12 tests passed:6 native Compose fixtures (includes exact equality of all3 secondary-action bounds before/after opening),4 popup geometry/inset,2 hero URL tests. No physical-phone test/live Poppy source inspected. CAUTION reused visual harness run unexpectedly39m28s total, UI tests1874s; do not blindly promise fast harness reruns. Final assembleDebug without test init passed4m16s after first client process SIGTERM143; final APK excludes test activity. Source hashes verified,59 protected files unchanged, DSP binary byte-identical, same signer,onlyarm64-v8a. No commit/push.
Delivered ZIP https://drive.google.com/file/d/15JrgE2-CpqFiN-EnnDRf4baht_rkzJ9w/view?usp=drivesdk . Local LMG-VK-artist-fixes-arm64-2026-09-30.apk132321956bytes SHA256e87fc4e9f6cf1dd963028c3ff7d156163c701a9fff11e7a55847b1ee590b8109. ZIP45656135bytes SHA2562017ae55775f0d93acfb7a7623dbf8e7ba6b2ee0a2f4c9cc3b5b19726c8209a2. Extract APK and install over existing. Daemons stopped immediately afterwards; availableRAM5.9GiB. Keep user requirement to stop compiler daemons after builds, do not indiscriminately delete incremental caches causing slow rebuilds.


2026-09-30 follow-up artist cover/menu: user explicitly requested removal of duplicate inline Mix/Favorite. Removed ArtistActionsStrip and top favorite star; ArtistMenuButton now occupies top-right circular ellipsis, retains mix/follow/share callbacks. No empty secondary row. ArtistHeroArtworkResolver compares actual decoded original and up to12 existing release covers, improves only when candidate has larger pixel area and32x32 RGB match(meanerror<=12,contrast>=40). Bounded8s background work/1.5s per candidate, original kept on no match/network failure; skips original>=900px. Does not substitute unrelated album merely byartistname. This addresses album-cover artist thumbnails; live Poppy URLs not available, exact phone result unverified. Existing sharp decode/progressive gradient unchanged. Newunit tests compressed sameart accepted,different/blank rejected; total8 JVM tests pass, assembleDebug passed3m11s, no emulator/full native fixture rerun. Previous external native harness uses removed ArtistActionsStrip and must be adapted before reuse.
Artifacts /root/LMG-previews/artist-cover-menu-2026-09-30/. APK137790262bytes SHA2562aea4584f59b8465ca80eac5ae2eb862c505137c23f77e33ff772dbe3d3b1e70,arm64only,same signature,DSP byte-identical,59 protected files unchanged;source hashes checked. ZIP45671247bytes SHA25626d27b9f12731f9a3b7ad211cc1a8f7ef0f2899fc88ef28c676f7e0f965b2db9. Delivered https://drive.google.com/file/d/1iaC9N55t35ZeNRPKWCW_UkHc0RIz-den/view?usp=drivesdk . Extract APK/install over existing. Daemons stopped;5.8GiB available. User explained prior half-hour outage was provider-wide and affected VPN/bots/chat, likely explains extended previous run; avoid attributing all delay to native harness.

2026-09-30: User cancelled requested star+ellipsis oval after seeing latest delivered APK; explicitly approves existing single upper-right ellipsis. Oval build stopped, three changed source files restored from /root/LMG-previews/artist-oval-2026-09-30/before and ALL approved artist-cover-menu source hashes verified. Latest approved delivery remains 1iaC9N55t35ZeNRPKWCW_UkHc0RIz-den (artist-cover-menu). No new APK required. Do not restore star/oval unless user asks again.


2026-09-30 artist centered title pill: user requested GPT-like oval in CENTER of collapsed artist top bar, containing small artist cover + name. Kept separate back and upper-right single ellipsis approved previously. DetailTopBar now optional titleContent slot; ArtistTitlePill44dpmin,30dp rounded cover,15sp name, opaque303030 and1dp505050 border. Appears when existing showTopBarTitle scroll threshold is reached. User explicitly corrected: long names must expand pill, NO ellipsis. Implemented intrinsic width up to safe space between side buttons, softWrap with NO maxLines/ellipsis, adaptive min44dp header/pill height allows full names to wrap. Initial single-line build was cancelled; only final full-name version delivered. No new tests for this low-impact UI layout; successful assembleDebug7m48s, diffcheck, source hash verification, no real-device visual check claimed.
Artifacts /root/LMG-previews/artist-title-pill-2026-09-30/. APK137795054bytes SHA256cb0efcb1c3d90c289c361dc33a1e768c7542b1c1616de56f96ed727741352b5c. ZIP45674729bytes SHA256c67debf5943df1a71d0c1b9e01218af451ee1fe925a4345b4022b654ad4c60d1. Onlyarm64,same signingcert,DSPbyteidentical,59protectedfilesunchanged,ArtistTitlePill DEXpresent,no test activity. Delivery https://drive.google.com/file/d/1v5FLtBPs4FMCF1xEWlIP_axqKWi2TSbe/view?usp=drivesdk . ExtractAPK/installovercurrent. Daemons stopped;5.6GiB available. Do not revive cancelled star+ellipsis oval; this is a different CENTER title oval.


2026-09-30 all-artist-tracks retry + header + release filter labels: user reported persistent Retry at200tracks, requested center artist cover/name pill with count below name on SAME all-tracks dialog, updated back button, and centered labels Все/Альбомы/Синглы и EP. Found actual schema evidence in /root/vkmusic_8.37_jadx/sources/com/vk/music/model/artist/c.java calling audio.getAudiosByArtist with o1(7), which decodes AudioGetResponseDto(count,items); our VkAudioApi parsed bare List only. Added ArtistAudioPageParser supporting count/items AND legacy arrays while preserving actual VK errors/malformed failures. Added getAudiosByArtistPage, existing list wrapper maps items. MusicBackend.getArtistTracksPage now uses total count via nextArtistTrackOffset; exact-full finalpage ends without extra request; legacy unknowncount only ends onempty. UI discards cursor when complete/repeated, guards post-completion requests, retryfooter onlywhenhasMore. No fixed200 cap or suppressionofgenuineerrors.
ArtistTracksDialog uses DetailTopBar+ArtistTitlePill with30dpcover,wrappedfullname,and localized track_count subtitle. Removed misleading200+ songs threshold. ArtistTitlePill optional subtitle preserves approved main header. ReleasefilterText uses TextAlign.Center in equal-width pills. UI no physicalphone test claimed. Six parser/paging tests pass (object response,count,legacyempty,error,malformed,fullfinalpage,unknowntotal). Initialcombinedtest/build3m32s, finalcenteredlabelsassemble46s. Artifacts /root/LMG-previews/artist-tracks-paging-2026-09-30/.
APK137799098bytes SHA256036c116915cfe93fddec6c1d1ff4814eec54ad24fd26b2ef155b8f50adbd359f;ZIP45675757bytes SHA25672c0bfa9870ad8739b414ae1cce9930c94aec266dbee0795872a7fbf654822ae. Onlyarm64,same signature,DSPbyte-identical,59protectedfilesunchanged,sourcesealchecked,notestactivity. Delivery https://drive.google.com/file/d/1Pnzv6FElIXG5fd8rMLYMH17v2eVmEVCF/view?usp=drivesdk ;extractAPK/installoverexisting. Daemonsstopped,5.5GiB available. User explicitly approved centered main artist pill on phone screenshot ASTEROID47 before this task.
