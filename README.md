# LMG VK — проект (com.lmg.vk)

Музыкальный клиент на собственном стеке: ядро восстановлено из бинарных
артефактов (см. `../VKX_NATIVE_RECOVERY_v8.12.1.md` — исторический отчёт),
бренд/пакеты/UI — собственные (LMG).

## Структура

```
recovered_project/  (rootProject = "lmg-recovered")
├── settings.gradle.kts          # :app + media3-m2 (форк media3-lmg)
├── build.gradle.kts             # AGP 8.7.3 + Kotlin 2.1.0
├── gradle.properties
└── app/
    ├── build.gradle.kts         # namespace/applicationId = com.lmg.vk, v1.0.0
    │                            # Ktor + Moshi + Realm + media3-lmg fork (1.5.1-lmg29)
    └── src/main/
        ├── AndroidManifest.xml  # .LmgApplication, label "LMG VK"
        ├── cpp/
        │   ├── CMakeLists.txt   # target: lmg (liblmg.so)
        │   └── lmg_native.cpp   # JNI: com/lmg/vk/jni/*, реальные имена методов
        └── kotlin/com/lmg/vk/
            ├── LmgApplication.kt            # точка входа, синглтоны
            ├── jni/LmgNative.kt             # JNI-мост (loadLibrary("lmg"))
            ├── network/                     # VkApiClient (Ktor), VkMethod,
            │   ├── dto/ (+ gen/ 35 DTO)     #   Moshi-модели
            │   └── methods/                 #   VkAudioApi + реестр 50+ методов
            ├── playback/                    # PlaybackService (media3), кроссфейд,
            │                                #   LmgEffectConfig/DSP-эффекты
            └── downloader/                  # загрузка + Realm-кэш
```

## Сборка

```bash
./gradlew :app:assembleRelease
# NDK-модуль: app/src/main/cpp (abiFilters: arm64-v8a, armeabi-v7a, x86)
# Требуется media3-m2/ с форком media3-lmg (как в LMG: zip релиза)
```

## Примечания ребренда

- Пакет везде `com.lmg.vk` (+ `com.lmg.vk.jni` для нативного моста)
- JNI регистрирует реальные имена (`getVkApiData` и т.д.) — не обфусцированные
- **TODO(backend)**: хосты `ui.lmg.app`/`api.lmg.app` — плейсхолдеры, указать реальные
- Прокси VK API (`xtrafrancyz.net`) оставлены как есть (рабочие эндпоинты)
- `VkApiClient` и др. с `Vk`-префиксом — фактическое именование VK API, оставлено
