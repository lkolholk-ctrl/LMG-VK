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
