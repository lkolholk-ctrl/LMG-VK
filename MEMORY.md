# Передача состояния проекта LMG VK

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
