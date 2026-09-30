# Технический реверс-инжиниринг и разбор архитектуры Cider Android (Beta 2.1)

> Версия приложения: `1.0.93` (билд 93)  
> Пакет: `com.cidercollective.cider`  
> Среда сборки: `compileSdkVersion 37 (Android 17)`, `targetSdkVersion 36 (Android 16)`, `minSdkVersion 33 (Android 13)`  
> Дата сборки: 27 сентября 2026 г.

---

## 1. Манифест и системные требования

### Почему строго Android 13+ (`minSdkVersion=33`)
1. **Android Graphics Shading Language (AGSL):** Класс `android.graphics.RuntimeShader` появился только в API 33. Весь UI рендерится через кастомные шейдеры.
2. **Android Spatializer API:** Класс `android.media.Spatializer` с функциями пространственного микширования и определением иммерсивности также появился в API 33.

### Ключевые разрешения и особенности
* `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK` — постоянный сервис воспроизведения.
* `android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION` и `REQUEST_INSTALL_PACKAGES` — встроенный механизм самообновления (OTA) в обход Google Play.
* **Поддержка VR/XR:** Задекларированы флаги `oculus.software.handtracking` и `com.oculus.feature.PASSTHROUGH` (приложение нативно оптимизировано под гарнитуры Meta Quest 3/Pro).

---

## 2. Нативный движок Cerium (Rust + C++)

В `lib/arm64-v8a/` находятся две библиотеки деобфусцированного медиа-конвейера:

### `libcerium_cdm.so` (761 КБ)
Кастомный Content Decryption Module (CDM). Реализует EME-клиент без использования системного Android MediaDrm.

**Экспортируемые JNI-символы:**
* `Java_com_cidercollective_cider_cerium_native_CeriumCdmNative_nativeInit` — инициализация контекста CDM.
* `Java_com_cidercollective_cider_cerium_native_CeriumCdmNative_nativeBuildChallenge` — генерация бинарного челенджа (DRM challenge) по полученному из манифеста PSSH / initData.
* `Java_com_cidercollective_cider_cerium_native_CeriumCdmNative_nativeExtractContentKeyHex` — извлечение 128-битного ключа AES-128 из ответа лицензионного сервера Apple Music.
* `Java_com_cidercollective_cider_cerium_native_CeriumCdmNative_nativeGetVersion` — версия протокола.
* `Java_com_cidercollective_cider_cerium_native_CeriumCdmNative_nativeReset` — сброс сессии.

### `libcerium_muxtool.so` (501 КБ)
Демультиплексор и потоковый дешифратор MP4 контейнеров.

**Экспортируемые JNI-символы:**
* `Java_com_cidercollective_cider_cerium_native_CeriumMuxtoolNative_nativeDecryptAndMuxToFd` — принимает зашифрованные CBCS-чанки (`sample-AES`), расшифровывает их на лету по ключу от `CeriumCdmNative` и пишет чистый поток в Linux FIFO pipe (`outputFd`).
* `Java_com_cidercollective_cider_cerium_native_CeriumMuxtoolNative_nativeDecryptAndMux` — вариант демультиплексирования в буфер в памяти.

### Связка с ExoPlayer
Приложение не использует стандартный `MediaDrmCallback` от Google. Вместо этого:
1. Сетевой загрузчик Cider забирает аудиочанки.
2. `libcerium_muxtool.so` расшифровывает их в `pipe()`.
3. Стандартный `ExoPlayer` читает дескриптор как локальный незашифрованный файл.

---

## 3. Архитектура воспроизведения и AutoMix (`c.iv0`, `c.sm0`)

### Dual-Deck плеер (две параллельные деки)
* В памяти постоянно живут два экземпляра плеера: `Deck A` и `Deck B`.
* При переходе следующий трек начинает декодироваться на неактивной деке заранее.

### Выявленные баги и костыли арбитра (`sm0.java`)
Архитектура страдает от классических проблем двух независимых плееров:
* `C1_CONCURRENT_TRANSITION` — попытка начать второй переход до завершения первого.
* `C2_ORPHAN_FADE_SILENCED_ACTIVE` — фоновый цикл фейда ошибочно глушит активный плеер до нулевой громкости.
* `C3_AUDIO_ON_INACTIVE_DECK` — состояние переключено на остановленную деку, плеер встает намертво.
* `C4_IDLE_GAIN_NOT_UNITY` — громкость не сброшена в 1.0 при отмене перехода.

---

## 4. Dolby Atmos и пространственный звук (`c.ns6`, `c.x`)

### Логика детекта аппаратуры
1. Сканирование декодеров `MediaCodecList`:
   * `audio/eac3` (Dolby Digital Plus);
   * `audio/eac3-joc` (Dolby Atmos JOC — Joint Object Coding).
2. Подключение к `android.media.Spatializer`:
   * Проверка флагов `isAvailable()`, `isEnabled()`, `getImmersiveAudioLevel()`.
   * Запрос `canBeSpatialized()` для 6-канального формата (5.1 surround bed, 48000 Hz).

### Режимы работы
* `Automatic`: вывод в Atmos, если подключены совместимые наушники или HDMI/eARC ресивер.
* `Always On`: принудительное декодирование E-AC-3 даже на встроенные стереодинамики.
* `Dolby Atmos on Any Headphones`: запрос специального бинаурального стерео-микса Apple с серверов для любых обычных наушников.

---

## 5. UI и AGSL-шейдеры (`RuntimeShader`)

### Жидкое стекло и 3D-блик на кнопках (`c.mj`)
* Аналитическое поле расстояний со скруглением (`sdRoundRect`).
* Расчет честных векторов нормалей поверхности (`sdNormal`):
  $$\vec{n} = \text{normalize}\left(\frac{\partial d}{\partial x}, \frac{\partial d}{\partial y}\right)$$
* Расчет освещения по Ламберту: световой блик естественно загибается по радиусу скругления кнопки.
* Дизеринг на основе шума (`fract(sin(dot(p, ...)))`) для устранения постеризации градиентов на OLED экранах.

### Псевдо-Motion Artwork (`c.m4`)
* Для треков без анимированной обложки создается трехслойный параллакс:
  $$I_{out} = 0.5 \cdot I_{base} + 0.3 \cdot I_{layer2} + 0.2 \cdot I_{layer3}$$
* Слои масштабируются и плавно смещаются на GPU без нагрузки на процессор.

### Караоке со слогами (`c.ez3`, `c.bz3`, `c.dz3`)
* Поддержка TTML и Musixmatch RichSync.
* Анимация активного слога: подскок на 10% + синусоидальное покачивание на 5% высоты шрифта:
  $$y_{offset} = -\sin\left(\text{clamp}\left(\frac{t - t_0}{1.4 \cdot \Delta t}, 0, 1\right) \cdot \pi\right) \cdot 0.05 \cdot h_{font}$$
* 16-ступенчатая динамическая маска ореола свечения вокруг поющегося слова.

---

## 6. Сетевая инфраструктура и API

* `https://exp-rise.cider.sh/api/v1/lyrics/mxm` — Musixmatch RichSync прокси.
* `https://exp-rise.cider.sh/api/v1/lyrics/translations` — построчные переводы текстов песен.
* `https://exp-rise.cider.sh/api/v1/lyrics/user/` — пользовательская краудсорсинговая база текстов.
* `https://taproom.cider.sh/api/v1/client/token-status` — проверка лицензии Taproom.
* `http://127.0.0.1:10767/api/v2/` — локальный RPC REST-API на ПК для удаленного контроля плеера.
