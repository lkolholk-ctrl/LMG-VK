# Первичные источники

Проверены через официальные/первичные веб-источники при подготовке пакета
27 сентября 2026 года. Они описывают математику/внешние контракты, а не
доказывают корректность конкретного кода; результаты кода находятся в reports.
Исходники приложения, сторонние DSP-фреймворки и их код не использовались.

**[EQ1] Audio EQ Cookbook**, W3C Working Group Note, 8 June 2021,
редактор Raymond Toy, формулы Robert Bristow-Johnson. Использован как
математическая спецификация коэффициентов и трактовок Q/S. Реализация
написана отдельно; текст источника не включён в пакет.

```text
https://www.w3.org/TR/audio-eq-cookbook/
```

**[C1] C++ working draft, [atomics.order].** Первичная спецификация
release/acquire и синхронизации неатомарных данных через атомарные индексы.
В библиотеке используются только возможности C++17; просмотренная онлайн-
редакция черновика может содержать более новые разделы стандарта.

```text
https://eel.is/c++draft/atomics.order
```

**[M1] Android Developers, Media3 AudioProcessor.** Контракты configure,
flush, queueEndOfStream, getOutput, владения ByteBuffer. На просмотренной
странице указано обновление 1 июля 2026 года. Закреплённую версию Media3
LMG-VK интегратор обязан сверить отдельно; новые названия методов не
предполагаются автоматически доступными в приложении.

```text
https://developer.android.com/reference/androidx/media3/common/audio/AudioProcessor
```

**[M2] Android Developers, DefaultAudioSink.Builder.** Ограничения
AudioProcessorChain для PCM, offload/passthrough, предупреждение о float-
output в setEnableFloatOutput. На просмотренной странице указано обновление
6 августа 2026 года. Не является утверждением о настройках LMG-VK.

```text
https://developer.android.com/reference/androidx/media3/exoplayer/audio/DefaultAudioSink.Builder
```

Все прочие алгоритмы и контракты — явно выбранные решения этого пакета:
двухбанковый переход, SPSC layout, finite-slew limiter, ceiling guard,
фиксированная задержка при bypass и frame-counted drain. Они описаны в
DSP.md/API.md и проверяются включёнными автономными тестами.
