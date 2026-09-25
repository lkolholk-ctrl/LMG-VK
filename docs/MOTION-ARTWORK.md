# Живые обложки Apple Music

## Актуальная реализация — 2026-09-22, после видео пользователя

Первую сборку motion пользователь признал неудачной: обложка останавливалась примерно через пять секунд, фон показывал сетку/повторяющийся текст. Её успешная компиляция не означала исправную работу на HONOR. Референс пользователя: `/root/LMG-previews/motion-revision-2026-09-22/reference/SVID_20260922_104911_1.mp4`, 9,47 с. На записи Apple используется tall-композиция HEALTH — ANTIDOTE без треклиста слева; анимация продолжается при паузе аудио. Пользователь отдельно подтвердил tall, потребовал запуск с начала песни, непрерывность на паузе и сильный блюр, как у наших статичных обложек. Последнее уточнение фона принято как отсутствие отдельного вращения: размывается сам меняющийся кадр видео, отдельного таймера мерцания нет.

Endpoint: `https://lyrics.gsgit.org/v2/motion?title=…&artist=…`. Предпочитается валидный `tall`, при его отсутствии `square`, при отсутствии motion остаётся прежняя статичная обложка. Совпадение названия/исполнителей/feat/версии проверяет общий iTunes matcher. Длительности в ответе нет. HTTPS обязателен. Источник и палитра статичных обложек, теги скачиваемого аудио и VK-транспорт не изменены.

## Воспроизведение и загрузка

Один отдельный немой ExoPlayer, отключены audio/text tracks; RepeatMode.ONE. Теперь проигрывается HLS-плейлист, а не извлечённый из byte-range HLS целиком скачанный MP4. Для ANTIDOTE подтверждено: у старого сырого MP4 start_time=10,033333 с, container duration=29,5 с; HLS задаёт пять сегментов по3,9 с, итого19,5 с. Нельзя приписывать все остановки этому смещению без проверки на устройстве, но прежний путь обходил правильную HLS-временную шкалу и ждал скачивания всего файла.

Для видео задаются буфер начала250мс, восстановления500мс, минимум2с/максимум30с. Максимальный поддерживаемый bitrate, предпочтение H.264; у проверенного tall имеется1080×1440, peak bitrate10252325. Потоковые сегменты кешируются через SimpleCache/CacheDataSource в отдельном `motion_stream_v2`, LRU256MiB; singleton кеш создаётся на IO. HTTP видео — стандартный Media3 DefaultHttpDataSource. Metadata остаётся на прежнем OkHttp с installVpnBypass. Основной транспорт аудио/VK не менялся.

Корневой provider начинает подготовку текущей обложки при смене трека, не ждёт открытия full player. Следующий элемент очереди получает metadata, master/media playlist, initialization segment и первый media segment максимального H.264 варианта в кеш. Это запускается после первого кадра текущей motion-обложки либо после подтверждённого отсутствия motion; отменяется при смене цели/уходе ниже STARTED. Работа на IO, max12MiB на часть. Зашифрованные варианты не предзагружаются этим упрощённым путём. Нет второго скрытого видеоплеера и массового обхода очереди/медиатеки.

Пауза музыки НЕ останавливает motion. Продвижение зависит только от STARTED и присутствия выходных поверхностей. При смене трека прежний плеер/GL освобождаются. Настоящие музыкальные видеоклипы исключены. Холодный старт неизвестного трека всё ещё зависит от сети; обещать нулевую задержку нельзя. Аудио не задерживается ради загрузки обложки.

## Изображение

В портретном FullPlayer tall рисуется сверху на всю ширину, с сохранением пропорций в обычном высоком окне и переходом в сильно размытый фон снизу. Верхняя область ограничена66% высоты на коротких экранах. Старая квадратная карточка при этом скрыта. До первого видеокадра показывается tall.preview и нейтральный фон. На экране лирики/очереди используется только сильно размытая motion-подложка. Прочая логика лирики не изменяется.

AirSheet и альбомный FullPlayer используют тот же поток в своих существующих размерах, с центральным crop. Их отдельный square-видеоплеер не создаётся. Фон FullPlayer берётся из того же кадра.

Последующее уточнение пользователя: внешний вид FullPlayer одобрен. На «Моей волне» нужны обычная статичная обложка и прежний AuraBackground из её палитры, без motion-слоёв и без остановки Aura после готовности видео. Для AirSheet пользователь отдельно попросил сохранить живую миниатюру. Удалены только два MotionArtworkSurface в WaveHomeScreen и условие !motionArtworkReady() у Aura. FullPlayer, AirSheet, общий видеоплеер и загрузка не изменяются этой правкой.

Вместо25 разнесённых выборок, создававших сетку, кадр уменьшается до32×32 и проходит три пары горизонтального/вертикального Gaussian blur на двух FBO. Финальная подложка использует усиление насыщенности и dark/light overlay, затем дополнительное затемнение под видео-референс; вращения и синтетического мерцания нет. На чёрно-белой ANTIDOTE получается мягкий серый фон, меняющийся только вместе с роликом. CPU-копирования видеокадров нет.

Один decoder SurfaceTexture/OES и GL-thread. На каждую TextureView допускается один ещё не потреблённый кадр: после swap ждём onSurfaceTextureUpdated перед следующим выводом именно в эту поверхность. Скрытый/неотрисовываемый компонент не заполняет очередь буферов и не тормозит остальные поверхности. Ошибка отдельного EGL window удаляет только его; decoder/GL-global ошибка переводит в static fallback. Добавлены редкие события first_frame/loop/error вместо покадрового логирования.

## Проверки и границы

assembleDebug и39 JVM tests прошли:31 существующих artwork/downloadtags/catalog +8 motion. Motion-тесты обновлены под tall-first, square fallback, HLS-приоритет, HTTPS и строгий matcher. Сборка подписана прежним ключом.

Настоящие JSON/HLS и tall MP4 проверены read-only. Tall-видеокадр совпал с композицией референса. Оба GLSL fragment + vertex скомпилированы/слинкованы в Mesa EGL1.5/GLES3.2. Дополнительно отрисован весь blur/portrait pipeline с реальным кадром в обычной2D texture вместо Android external OES: glGetError=0, визуально исчезли сетка и повторяющийся текст. Это проверка математики и GLSL, не тест Android TextureView/MediaCodec.

Устройство и эмулятор не использовались. Точная причина прежней остановки через5с, реальная бесшовность повторов и работа HONOR compositor этой проверкой не подтверждены. Лаги лирики по-прежнему отложены.

Артефакты: `/root/LMG-previews/motion-revision-2026-09-22/`: before, hashes/patch, пользовательский референс, tall-variant/master/JSON, shader/render-preview scripts, build/tests, APK/signature/Drive metadata. Предыдущие артефакты: `/root/LMG-previews/motion-artwork-2026-09-22/`.

Опорные API: [Media3 HLS](https://developer.android.com/media/media3/exoplayer/hls), [Media3 network/cache](https://developer.android.com/media/media3/exoplayer/network-stacks). Реальные API сверены с локальным media3-lmg1.11.

APK: [LMG-VK-debug-motion-tall-loop-2026-09-22.apk](https://drive.google.com/file/d/1eBhYs8MYsgLS1vZUMwd2a5KURLwOER5t/view). SHA256 `acdf9fda50ef60a822feca058fe7135e4fe2905e147d8d3cefe4e858a7e1ae88`. Размер/MD5 Drive подтверждены.

Последняя сборка с обычной обложкой/фоном Wave и сохранённой живой миниатюрой AirSheet: [LMG-VK-debug-static-wave-2026-09-22.apk](https://drive.google.com/file/d/1QewPezbAfPhJkJS-yh4oM1nJI6T6LLrt/view). assembleDebug успешен; проверка хешей подтверждает изменение только WaveHomeScreen.kt. Подпись и Drive MD5/размер проверены. SHA256 `8a114753591dbb44b44e47f484bb443b41547efa0287f38578348b6a973d41a2`.


## 2026-09-22 — очистка запросов и устаревшие отрицательные результаты

По сообщению пользователя сервер /v2/motion уже очищает VK-теги, ищет альбомный motion для сингла и возвращает ORDINARY LOSS has_motion=true. Клиент ранее отправлял сырые поля и удерживал отрицательные ответы12часов. Исправления включены в новую сборку; полная проверка и ссылка приведены ниже.

Общий ItunesArtworkMatcher очищает скобочные vk.com/vk.ru/vkontakte.ru ссылки и bitrate-теги, а известный жанровый хвост вроде Electronic удаляет только после такого служебного тега. Реальные названия Electronic/Pop и версии Live/Remix/Sped Up/Radio Edit сохраняются. Очистка применяется и при формировании запроса, и при сравнении с ответом. canonical.album теперь сохраняет очищенный нормализованный альбом; shared ArtworkSelection по-прежнему сравнивает идентичность песни без album, чтобы не разделить обложку игрока и остальных экранов.

Motion запрашивает canonical.title/artist и непустой canonical.album. Album передаётся от текущего/следующего Track и учитывается в ключе metadata. iTunes использует ту же очистку, включая предварительный поиск; совпадение по длительности±3с и версиям не ослаблено.

Добавлен lookupVersion=1 для записей обоих metadata-кешей. Старые отрицательные записи без версии игнорируются при чтении даже до expires; новый запрос перезаписывает их. Валидные старые положительные записи принимаются там, где ключ совпадает. Новые промахи —15мин вместо12ч; ошибки загрузки картинки/iTunes сохраняют короткий5минTTL. Пустые списки iTunes candidates также живут15мин. Видеосегменты/Coil-кеш не очищаются. RAM-кеши после установки новой сборки создаются заново. Изменение кеша начинает действовать после установки новой сборки и следующего обращения к записи.

Проверки:37 JVM artwork-тестов прошли напрямую через локальные Kotlin2.3.10/JUnit4.13.2 (включая реальныеформыVkзагрязнения, URL/queryalbum, совпадениесчистымmotion, старыйnegativecache, TTL, сохранениеверсий). Изменённые Android-исходники, включая оба repository и Compose provider, отдельно скомпилированы локальным Kotlin+Compose plugin против cachedappclasses/Android36/зависимостей. git diff --check чистый. Это НЕ полный Gradle build и НЕ Android runtime test.

Ограничение среды этой сессии: Gradle не запускается — FileLockContentionHandler: Could not determine a usable wildcard IP for this machine; исходящий curl также не разрешает lyrics.gsgit.org. Сетевые операции ограничены, повышение прав недоступно. При первой попытке assemble/upload были заблокированы. Затем ограничения сняты пользователем; результат полной сборки указан ниже. Артефакты: /root/LMG-previews/artwork-query-cleanup-2026-09-22/ (before/hash/patch, build.log, local-check.py/logs, compile-android.py/logs).


## Полная сборка и доставка после снятия ограничений — 2026-09-22

Пользователь разрешил запуск после включения сетевого доступа. assembleDebug + testDebugUnitTest успешны:46 tests,0failures/errors/skipped (37artwork+5downloadtags+4catalog). Изменённые исходники совпали с проверенными SHA256. Live запрос canonical title=ordinary loss, artist=health, album=conflict dlc подтвердил has_motion=true, tall HLS, track1832593366/album1832593364. Это серверная проверка; на телефоне не запускалось.

APK: [LMG-VK-debug-artwork-query-cleanup-2026-09-22.apk](https://drive.google.com/file/d/1eo2plxxbukCKINsSgBR1UVBUbWc1aT-E/view); локально /root/LMG-VK/app/build/outputs/apk/artwork-query-cleanup/LMG-VK-debug-artwork-query-cleanup-2026-09-22.apk. Размер190887048, SHA256 `cf270c62b3a56c1eabd60aabc38d8fa25752211aff262d47ab9a4e6b8f2fbbe4`, MD5 `e0f61902d801665fa1ae8c35b0b97422`. Подпись совпала с предыдущей сборкой, новые cache/query классы проверены вDEX, размер/MD5 Drive совпали. После установки старые отрицательные записи будут игнорироваться при следующем чтении, нового поиска не придётся ждать12часов.

Артефакты полной проверки: full-build.log, full-tests.json, ordinary-loss.json, apk.json, signature.txt, drive.json, drive-link.txt в /root/LMG-previews/artwork-query-cleanup-2026-09-22/.


## 2026-09-23 — переходы FullPlayer, LyricsScreen и QueueSheet

Пользователь сообщил о кратком появлении остатка прошлой песни при входе/выходе из лирики и очереди, а также о резкой смене полноэкранного motion. В коде обнаружены два связанных механизма: presentation входил в Compose key TextureView, поэтому переключение экранов уничтожало и создавало поверхность; под ней SharedArtworkBackground продолжал показывать последнюю отрисованную картинку даже при остановленном renderer. При смене трека provider сразу закрывал прежний ExoPlayer и EGL.

Режимы full portrait и blurred background теперь переключаются uniform presentation в одном и том же output. За420мс чёткая часть кадра растворяется в существующем блюре и возвращается. TextureView, размер output и preview не пересоздаются из-за открытия/закрытия LyricsScreen/QueueSheet. Изменение uniform запоминается даже при pending buffer; после ACK renderer выводит последнее состояние. Dirty flag исключает бесконечную перерисовку неподвижного outgoing кадра после ACK.

MotionArtworkSurface делает450мс Crossfade по конкретному MotionArtworkPlayback, включая переходы к/от null. Прежний playback после смены трека перестаёт продвигать видео, но сохраняет ресурсы до выхода всех его поверхностей из перехода. Последний unbind освобождает player/GL. Уничтожение отдельного экрана для текущей песни приостанавливает motion без потери возможности снова открыть его. На первой реально обновлённой TextureView кадр проявляется за300мс, вместо прежнего скачка alpha0.001→1. Unbind отменяет ViewPropertyAnimator и запрещает запоздалому callback снова показать поверхность. Несколько кратковременно сосуществующих renderer используют общий счётчик владения EGL display, чтобы завершение старого не завершало display нового.

ArtworkBackgroundGeneration сбрасывает старую статичную подложку, когда источник менялся во время приостановки renderer. При обычном переключении статичных треков renderer сохраняется и прежний blend продолжает работать. Открытие лирики/очереди на одном треке не меняет background generation. Квадратная карточка теперь исчезает/возвращается плавно при переключении tall/static. AirSheet сохраняет живую миниатюру.

Визуальная проверка шейдера на Mesa: фракция1 пиксельно совпала с одобренной прежней portrait-композицией; фракция0 совпала с прежним blurred output;0.25/0.5/0.75 отличаются от ожидаемой линейной смеси не больше чем на1 из255. Настоящие OES/blur шейдеры скомпилированы и слинкованы; для offscreen-пикселей использована2D-подстановка реального tall-кадра. Это не проверка Android TextureView/MediaCodec/HONOR.

Добавлены5 тестов lifetime (два выхода, повторное открытие, быстрые смены треков, повторный detach/поздний callback, незапущенный трек) и4 теста invalidation статичного фона. Итоговая сборка и доставка указаны ниже.

Артефакты: `/root/LMG-previews/motion-transitions-2026-09-23/`. Устройство/эмулятор не запускались. Лаги самого renderer лирики остаются отдельной отложенной задачей; duration±7с и использование static_artwork при найденном motion в эту правку не входят.

Контракт SurfaceTextureListener сверялся с [Android API](https://developer.android.com/reference/android/view/TextureView.SurfaceTextureListener): показ после onSurfaceTextureUpdated, освобождение SurfaceTexture самим владельцем при возврате false из onSurfaceTextureDestroyed.


### Проверка и APK переходов — 2026-09-23

assembleDebug + testDebugUnitTest для artwork/ui.player завершились успешно:57 тестов,0 failures/errors/skipped. Включены9 новых regression tests. После последней защиты onSurfaceTextureAvailable/SizeChanged выполнена повторная сборка; guards подтверждены в compiled bytecode. Новые классы/метки переходов присутствуют в APK DEX. SHA256 исходников совпали с переданными на сборку. Подпись совпадает с предыдущим APK.

APK: [LMG-VK-debug-motion-transitions-2026-09-23.apk](https://drive.google.com/open?id=1PceNqC4Wqesxs7a7xY6A1srYZdZutvI4). Размер 187620507 байт; SHA256 `ee8f8f2e2d19a887beb18c1f95b38817b3b1b7695d0a2d01e97cfbf47f0e81a1`; MD5 `6aef5d6dc00b55640969e048e3059cc0`. Google Drive размер/MD5 подтверждены. Локальный путь: `/root/LMG-VK/app/build/outputs/apk/motion-transitions/LMG-VK-debug-motion-transitions-2026-09-23.apk`. Артефакты: `/root/LMG-previews/motion-transitions-2026-09-23/` (before, implementation.patch, source-hashes, build logs, tests, shader/render checks,5 промежуточных кадров, surface-bytecode, подпись и Drive metadata).

На телефоне/эмуляторе не проверялось. Требуется пользовательская проверка быстрых переходов LyricsScreen↔FullPlayer↔QueueSheet и смен motion→motion/motion→static на HONOR. Не выдавать JVM/GL проверки за подтверждение отсутствия всех артефактов на устройстве.


## 2026-09-23 — статичная подложка лирики и очереди после проверки на HONOR

Пользователь проверил APK motion-transitions и прислал938878/938877: в QueueSheet/LyricsScreen оставалась чёткая полноэкранная обложка The Weeknd под контентом. Это реальный отрицательный результат предыдущей правки; успешные57 JVM тестов и Mesa-проверка не подтвердили Android-композицию. Точную runtime-причину сохранения presentation по двум скриншотам не установили.

По разрешённому пользователем варианту выбран обычный статичный источник обложки для двух экранов. В FullPlayer showLyrics||showQueue теперь включает существующий SharedArtworkBackground независимо от motionReady/tallMotion и выключает оба MotionArtworkSurface (полноэкранный фон и видео в квадратной карточке, в том числе split). После450мс штатного Crossfade motion-output снимается; поверх лирики/очереди остаётся только обычный фон, без зависимости от смены uniform presentation. SharedArtworkBackground использует прежний ArtworkBackgroundRenderer/ArtworkBlur:3 пары горизонтального/вертикального box blur, без нового алгоритма. На закрытии экранов motion возвращается через существующее плавное появление. Presentation в FullPlayer теперь зависит только от tallMotion. AirSheet оставлен с живой миниатюрой, как ранее отдельно просил пользователь.

Производственный код изменён только в FullPlayer.kt. Общий статичный источник пока прежний: при отсутствии Apple-обложки может остаться VK thumb. Улучшение matching/static_artwork и duration±7с — отдельные ожидающие задачи.

Артефакты: `/root/LMG-previews/static-lyrics-queue-2026-09-23/`. Итог сборки/доставки указан ниже; устройство и эмулятор для этой правки не запускались.


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


## 2026-09-23 — переходы и повторное открытие лирики

См. [PLAYER-TRANSITIONS.md](PLAYER-TRANSITIONS.md): общий переход Lyrics/Queue, сохранение геометрии уходящего motion, показ текущей статичной обложки до первого motion frame, завершение скрытого фонового перехода и кэш загруженной/измеренной лирики. Одобренные шейдеры фона и работающий playback/frame delivery не изменены.


### Уточнение: морфинг motion, а не только fade

Предыдущий переход пользователь отклонил. Проверена цепочка t0.u1 → u0/v0: живой контейнер motion сжимается в thumbnail с одновременным fade/скруглением за300ms (кривая .2,.06,0,1), назад разворачивается. Реализована адаптация без пересоздания surface; подробности и ограничения в [PLAYER-TRANSITIONS.md](PLAYER-TRANSITIONS.md), раздел исправления после отклонённого перехода.
