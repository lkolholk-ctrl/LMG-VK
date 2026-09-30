# AutoMix Stage 4c — DSP stability patch

Основа: **приложенный LMG-VK-project.zip**, а не состояние удалённой ветки.
Архитектура не меняется: один сервисный ExoPlayer, два внутренних renderer/sink,
существующий AAR `1.5.1-lmg30-boundary3`, native/automix. Новый Player, AudioTrack,
аудиобэкенд или модель не добавлены. Ограничения текущего live-сценария не сняты.

## Исправления с воспроизводимыми проверками

1. **Дробный input-hop TimePitch.** Раньше int64 cursor округлялся на каждом шаге,
   теряя остаток. При rate=1.0005 накопленная ошибка достигала 511.232 кадров примерно
   за миллион входных кадров. Host transport теперь суммирует точную позицию и
   квантует абсолютный cursor один раз. Остаток <1 кадра; recovered standalone
   timePitchCommitHop, phase/spectral kernels и правила transient не переписаны.
2. **Холодный STFT на cue.** Первый DC sample 0.1 становился 0.066666678, затем
   догонял уровень за первые ~1500 кадров. Primed live-режим подаёт реальные N/2
   кадров исходящего источника ДО cue в тот же graph/TimePitch. Отрицательные
   output-frame результаты не выводятся повторно. В 12 DC/tone/mono/stereo тестах
   8/44.1/48 kHz максимальная ошибка 2.23517e-8. Нет gain boost/limiter/подмены нулями.
   Если точной истории нет, отказ происходит до CLAIMED. История ограничена 8192
   кадрами/двумя каналами и очищается при реальной смене output/формата/flush.
3. **Рабочий PCM-цикл.** Один вызов заблокированного sink на тик вместо spin-retry;
   исходные данные продвигаются только на реально принятую величину. Квитанция
   output переиспользуется, примитивные clocks не упаковываются в Long на каждом
   тике. Поступление input само по себе не отменяет timeout застрявшего выхода.
4. **JNI ownership.** Steady-state обращение к live/ingress handle использует
   thread-local strong ownership cache, без захвата registry/entry mutex. Первый
   доступ/создание/уничтожение остаются control operations. Kotlin wrappers
   используют CAS-привязку одного владельца вместо @Synchronized на каждый блок.
   Чужой поток не может уничтожить и удалить активный native handle из registry.
   Native readonly/range/alignment checks сохранены.
5. **Подготовка вне playback-потока.** Native graphs, FFT, scratch/direct buffers
   создаются на уже существующем Dispatchers.Default до запроса lease. Не вызываются
   методы, привязывающие owner, пока объект не принят playback-потоком. Отмена до
   take освобождает worker resource, после take его освобождает только owner.
   Ошибка/неготовность подготовки не заменяется синхронным выделением графов у cue.
6. **Громкость.** Типизированный master-volume не сбрасывает manual transition
   factor в 1. Во время owned output отделяется только fade, master/ducking остаются.
   Уже сведённый native PCM не получает остаточную Sound Check ramp второй раз.
7. **Два sink.** У каждого свои meter/DJ/normalization histories. Роль связывается
   с actual OUTPUT token/window UID, а не со счётчиком создания процессоров.
   Сброс B не обнуляет A; native mixed-output становится единственным meter source.
   DJ-команда — один immutable control snapshot с occurrence target. Неоднозначное
   совпадение выключает legacy DJ, а не выбирает «последний созданный» процессор.
8. **PCM.** Fixed output storage подготавливается при configure; большие входы
   обрабатываются частями. Нет asShortBuffer/asReadOnlyBuffer на native hot-path.
   Mono/stereo metering использует оба канала и реальные sample-rate coefficients;
   RMS и normalization окна фиксированы в кадрах, а не в размере callback.
   В Sound Check исправлен подсчёт времени stereo (20 секунд кадров, не 10).
   Отключённый processor byte-transparent; float headroom не ограничивается им.
   Все 65536 PCM16 значений round-trip через float проверены. Обратное преобразование
   произвольного float в PCM16 НЕ является lossless: существующая явная saturation
   и её счётчик сохранены. Dither/скрытая нормализация не добавлены.

## Почему новый символ обязателен

`NativeLivePcmExecutor.nativeCreatePrimed` — новый JNI entry с двумя metadata словами
(число history frames и первый source-frame). Старый nativeCreate оставлен для
совместимых offline/host вызовов. Production подготовка явно выбирает primed mode.
Старый APK/.so больше не проходит live-export guard. Не смешивать новые Kotlin
классы со старой native .so; пересобрать host JNI и все APK ABI. Форк/AAR не меняется.

## Точный смысл allocation-проверок

* Native тест переопределяет new/new[]/aligned new и считает вызовы только после
  конструирования. Реальные kernel push/pull/encode и fractional-map циклы: 0.
* HotSpot ThreadMXBean: 0 выделенных bytes в измеренных прогретых span:
  20 000 JNI encode/stats/clock, 20 000 same-sink writes, 4000 meter/normalization,
  1500 полных gate+JNI DSP pump тиков. Sink — recording backend, lease — тестовый.
* Это **не** доказательство allocation-free работы Android ART, framework AudioSink,
  codec/AudioTrack, сторонних processors, всех ветвей recovery и первого claim.
  Снимки диагностики, token/lease/request объекты и короткая синхронизация первого
  claim остаются control-plane. Новый shared worker handoff также выделяет control
  objects. Упаковка active/steady-state переменных не объявляется lock-free всей системы.
* Histories и mutable buffers имеют одного подтверждённого playback-owner.
  Управление с Main публикует immutable/CAS state. Нельзя вызывать DSP параллельно
  из двух renderer callbacks на разных потоках; нарушение отвергается.

## Проверки и текущие ограничения

31 новый JVM scenario; три CTest (fractional cursor, realtime PCM, warm handoff).
7 изолированных семантических мутаций. Запуски используют реальную собранную из
архива библиотеку, не fake DSP. Никакое полное соответствие Apple не заявлено.

В исходном архиве отсутствуют бинарные reference fixtures: исходный CTest
45/71 pass, 26 failures. После патча 48/74 pass, тот же набор 26 failures,
новых failures нет. Не скипать и не ослаблять эти тесты: в полном checkout требуется
74/74. Под ASan/UBSan с реально инструментированным ядром прошли все 6 выбранных
тестов, включая три новых, live executor, TimePitch stream и ProcessedTrack.

В архиве нет app/build.gradle.kts/gradle и Android SDK. Поэтому полный Android
build, новые adapter source проверки на Android classpath и APK/device-прогон
здесь не выполнены. Сохранён существующий DEBUG/single-pair live-домен, а также
запреты spatial/previous-state/неподтверждённых соответствий. A→B→C не реализован
этим patch и не включается выключением guards. Статус «релизная стабильность»
требует полноценного server + device acceptance, не только host tests.

Нужно проверить: действительное output-context переключение кодека, pause/seek
во время удержания/partial-write/после claim, повтор mediaId, перестановку очереди,
смену устройства, master/ducking, нормализацию при переключении настройки и
стереосигналы. Системный scheduler/декодер может вызвать underrun независимо от DSP;
абсолютного отсутствия щелчков на всех Android-устройствах эти тесты не доказывают.
EOF/drain policy и прежнее handback ограничение остаются прежними.
