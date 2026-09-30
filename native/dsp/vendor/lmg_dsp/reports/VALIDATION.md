# Фактическая локальная проверка

Дата подготовки пакета: 27 сентября 2026 года.

Проверялся самостоятельный пакет LMG DSP, созданный для этой задачи.
Репозиторий LMG-VK не использовался. Сборка приложения, JNI, Android NDK,
Media3, AutoMix и воспроизведение на устройстве не выполнялись.

## Среда

Linux x86_64; GCC/G++ 14.2.0; CMake 3.31.6. Дополнительно использован
Clang 17.0.0 из установленного toolchain. Точные строки версий сохранены
в `environment.txt`. Это не эмуляция и не сборка Android ABI.

## Выполненные команды и результаты

| Проверка | Результат | Журнал |
|---|---|---|
| GCC Release, static, C++17 | Библиотека, пример и тестовые программы собраны; 22/22 C++ группы и C99 smoke прошли | `configure_release.log`, `build_release.log`, `ctest_release.log` |
| GCC RelWithDebInfo, AddressSanitizer + UndefinedBehaviorSanitizer | Обе CTest-программы прошли; sanitizer-диагностик в этом запуске нет | `configure_asan_ubsan.log`, `build_asan_ubsan.log`, `tests_asan_ubsan.log` |
| GCC Release, shared | Shared-библиотека и оба тестовых исполняемых файла собраны; проверки прошли | `configure_shared.log`, `build_shared.log`, `ctest_shared.log` |
| Clang Release, static, C++17 | 22/22 C++ группы и C99 smoke прошли | `configure_clang.log`, `build_clang.log`, `ctest_clang.log` |
| Установка CMake-пакета и внешний consumer | cmake --install, find_package(LmgDsp 1 CONFIG REQUIRED), связывание LMG::dsp и C99 smoke внешнего consumer прошли | `install_consumer.log` |
| Пример PCM без аудиоустройства | 48 000 входных и 48 000 полезных выходных кадров после удаления известного priming; задержка 240 кадров | `example_release.log` |

В каждом CTest-прогоне выполняются **два executable**, а не только две
проверки DSP: основной executable содержит 22 группы и тысячи внутренних
проверок, отдельный C99 executable проверяет C ABI. Подробный состав и
ограничения oracle приведены в `../docs/TESTING.md`.

ASAN_OPTIONS использовались `detect_leaks=1:halt_on_error=1`;
UBSAN_OPTIONS — `halt_on_error=1:print_stacktrace=1`.

Команды воспроизведения обычной сборки, sanitizers и shared находятся
в `../docs/TESTING.md`. Временные build/install/consumer-каталоги располагались
вне исходного дерева и **не включены в ZIP**. Архив содержит исходники,
документацию и журналы, не готовую Android .so и не ELF-файлы host-сборки.

## Явно не выполнено

ThreadSanitizer: команда подготовлена, не запускалась. Многопоточные stress-
тесты выполнены в обычных/ASan/Clang конфигурациях, но не заменяют TSan.

Android NDK/ARM32/ARM64, JNI, Media3, реальные потоки LMG-VK и AutoMix:
подготовлен контракт интеграции, проверки в проекте не запускались.

Windows/macOS, субъективное прослушивание, CPU/deadline/battery-профилирование
на телефоне, полный gapless закодированных файлов и аппаратный output:
не проверялись. Локальные успехи не объявляются доказательством абсолютной
корректности на всех параметрах/платформах или отсутствия слышимых артефактов.

## Связь с финальными исходниками

После последнего изменения исходников повторно выполнены GCC static,
ASan/UBSan, shared, Clang и внешний consumer. Корневой `MANIFEST.sha256`
фиксирует файлы поставки. Он создан после записи финальной документации;
сам manifest в собственную контрольную сумму не включён.
