# Сборка и автономные проверки

Тесты не требуют аудиоустройства, Android, JNI, AutoMix, интернет-доступа или
DSP-фреймворка. Это исполняемые исходники, а не описание будущих заглушек.
Результаты конкретных запусков находятся в `../reports/VALIDATION.md` и `.log`.

## Обычная host-сборка

Требуются CMake >= 3.16, компилятор C++17, C99-компилятор для C smoke test
и стандартная поддержка потоков. Никакие зависимости не скачиваются.

```sh
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release \
  -DLMG_DSP_BUILD_TESTS=ON -DLMG_DSP_BUILD_EXAMPLES=ON
cmake --build build --parallel
ctest --test-dir build --output-on-failure -V
./build/lmg_dsp_pcm_example
```

CTest запускает два исполняемых файла. Первый содержит 22 отдельные группы,
второй действительно компилируется как C99, а не как C++ с C-заголовком.

## ASan + UBSan

Для поддерживающего sanitizers GCC/Clang на host:

```sh
cmake -S . -B build-asan -DCMAKE_BUILD_TYPE=RelWithDebInfo \
  -DLMG_DSP_BUILD_TESTS=ON -DLMG_DSP_BUILD_EXAMPLES=ON \
  -DCMAKE_C_FLAGS="-fsanitize=address,undefined -fno-omit-frame-pointer" \
  -DCMAKE_CXX_FLAGS="-fsanitize=address,undefined -fno-omit-frame-pointer"
cmake --build build-asan --parallel
ASAN_OPTIONS=detect_leaks=1:halt_on_error=1 \
UBSAN_OPTIONS=halt_on_error=1:print_stacktrace=1 \
ctest --test-dir build-asan --output-on-failure -V
```

Это проверяет выполненные пути на ошибки памяти, часть undefined behavior и
утечки. Положительный результат не доказывает отсутствие всех возможных
ошибок и не является performance measurement.

## ThreadSanitizer — отдельный запуск

Команда подготовлена, **ThreadSanitizer при подготовке этого пакета не
запускался**. Он не смешивается с AddressSanitizer:

```sh
cmake -S . -B build-tsan -DCMAKE_BUILD_TYPE=RelWithDebInfo \
  -DLMG_DSP_BUILD_TESTS=ON \
  -DCMAKE_C_FLAGS="-fsanitize=thread -fno-omit-frame-pointer" \
  -DCMAKE_CXX_FLAGS="-fsanitize=thread -fno-omit-frame-pointer"
cmake --build build-tsan --parallel
TSAN_OPTIONS=halt_on_error=1 \
ctest --test-dir build-tsan --output-on-failure -V
```

Два многопоточных stress-теста уже входят в обычную программу: очередь
передаёт 100 000 согласованных снимков, а Processor одновременно принимает
4 000 обновлений с audio processing/reset. Это полезные проверки, но stress
без TSan сам по себе не является доказательством отсутствия гонок.

## Состав 22 групп

| Группа | Проверка |
|---|---|
| measured_frequency_responses_44k_48k_96k | 84 измерения по обработанному PCM: шесть типов на трёх частотах и десять центров Graphic при каждом Fs |
| boundary_grid_poles_and_independent_reference | 2 160 комбинаций rate/f0/Q/gain/S/type, Jury, комплексные полюса и 6 480 сравнений АЧХ/фазы с независимым прототипом |
| boost_cut_cancellation | Взаимная компенсация +12/−12 dB peaking при одинаковых f/Q |
| bit_exact_bypass_and_exclusive_eq_modes | Полный bypass, нейтральные режимы и отсутствие скрытого второго EQ |
| channel_independence | Нулевой правый канал не получает сигнал левого; mono совпадает с левым stereo |
| latency_eos_short_empty_streams | 44.1/48/96 kHz, mono/stereo, задержки 0/0.01/5/20 ms, пустые/короче L/длинные источники |
| errors_validation_queue_backpressure | Неизвестные режимы, NaN, Nyquist, slope, attack/ceiling, переполнение очереди, неверные lifecycle/размеры |
| seek_reset_adopts_latest_and_clears_state | Reset очищает старую историю и принимает последнее опубликованное состояние |
| fixed_parameter_block_independence | Полное побитовое равенство результата при блоках 1/37/1024, включая хвост и limiter |
| scheduled_automation_block_independence | События на одинаковых кадрах; блоки 1/53/1024, смена режима, gain, limiter и bypass |
| smooth_preamp_and_bypass | Нет жёсткого скачка при обновлении, ограничены шаги огибающей, достигнут точный endpoint |
| limiter_threshold_ceiling_stereo_link | Большие случайные пики, рабочий threshold, ceiling, общий gain stereo, прозрачность ниже порога |
| limiter_attack_release_timing | Отдельная численная проверка finite attack и exp release |
| limiter_enable_and_dynamic_ceiling | Плавное включение и соблюдение текущего плавного ceiling при полностью активном limiter |
| tail_fade_and_no_silence_trimming | Tail равен явным нулевым входам, fade имеет заданную форму, нулевая амплитуда не меняет длину |
| nan_inf_denormal_float_overflow | Санация NaN/Inf/subnormal, сохранение normal dry PCM, отчёт float overflow |
| extreme_valid_cascades | 72 крайних 8-полосных конфигурации с обработкой шума/нулей без восстановления после числовой аварии |
| q_slope_preamp_headroom_presets | Shelf не использует Q одновременно с S; preamp/headroom применяются один раз; пресеты явные |
| drain_memory_bounds | Записывается только возвращённый префикс, точное число хвостовых кадров |
| no_cpp_allocations_in_realtime | Перехват C++17 normal/aligned/array/sized/nothrow new/delete во время process/reset/end/drain |
| spsc_snapshot_concurrency | Нет разорванных полей/потери порядка при одном producer и consumer |
| processor_control_audio_concurrency | Конкурентные submit и process/reset без нечислового выхода/ошибок |

Граничная сетка содержит Fs=192 kHz дополнительно к трём основным частотам.
Это не заявка на исчерпывающий перебор всех вещественных значений параметров.
Для каждого нового рабочего снимка сама библиотека проверяет устойчивость
созданных коэффициентов.

### Независимость проверки АЧХ

PCM измеряется после одной секунды settling, на следующей секунде, через
синусную/косинусную проекцию. Проверка не вызывает production-фильтр как
свой expected-result oracle. Эталон отдельно вычисляет аналоговые прототипы
с bilinear prewarping `r=tan(pi*f/Fs)/tan(pi*f0/Fs)` в complex arithmetic.
Глубина notch на центре должна быть ниже −100 dB; для остальных измерений
допуск 0.01 dB. Измеряются также все десять заданных графических центров.

Побитовая независимость от блоков означает одну платформу и одни compile
settings. Между различными компиляторами/CPU/libm возможно несколько ULP;
межплатформенная побитовая идентичность не обещается. Для конкурентных
обновлений точка принятия по отношению к кадрам должна совпадать.

Перехват new/delete не перехватывает произвольный C malloc. В исходниках
realtime-пути отдельно проверяется отсутствие allocation, mutex, I/O,
логирования, Java/JNI и вызовов проектирования коэффициентов. ОС и обёртка
могут иметь собственные задержки/выделения; их нужно измерять отдельно.

## Static/shared и установка

```sh
cmake -S . -B build-shared -DCMAKE_BUILD_TYPE=Release \
  -DBUILD_SHARED_LIBS=ON -DLMG_DSP_BUILD_TESTS=ON
cmake --build build-shared --parallel
ctest --test-dir build-shared --output-on-failure
cmake --install build --prefix install
```

Установленный пакет экспортирует `LMG::dsp` через
`find_package(LmgDsp CONFIG REQUIRED)`. Для Android предпочтительно добавить
исходники через CMake существующего приложения, используя его реальные
NDK/ABI/minSdk/STL. Десктопные test executables не добавлять в APK.

## Что остаётся проверить в проекте

Android NDK ABI, JNI, конкретная версия Media3, AutoMix, реальные декодеры,
encoder delay/end padding, audio offload/float path, timestamps и gapless
приложения — **подготовлен контракт, на проекте не запускалось**.
Субъективное прослушивание, CPU/battery/deadline профилирование на телефоне,
TSan и проверки на Windows/macOS также не выдаются за выполненные.
Инструкция интегратору — `INTEGRATION.md`.
