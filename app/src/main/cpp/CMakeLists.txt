cmake_minimum_required(VERSION 3.22.1)
project(lmg_native LANGUAGES CXX)

# Восстановленный нативный модуль LMG VK (liblmg.so)
# Оригинал: NDK r21e, arm64-v8a / armeabi-v7a / x86, minSdk 23

add_library(lmg SHARED
    lmg_native.cpp
)

target_compile_features(lmg PRIVATE cxx_std_17)

set_target_properties(lmg PROPERTIES
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON
)

target_compile_options(lmg PRIVATE
    -O2
    -fstack-protector-strong
    -ffunction-sections
    -fdata-sections
)

target_link_options(lmg PRIVATE
    -Wl,--gc-sections
    -Wl,--build-id=sha1
)

target_link_libraries(lmg
    log
    android
)

# Separate observation/AutoMix JNI library. Do not link it into legacy liblmg
# and do not instantiate it from the current PCM chain in this phase.
set(LMG_AUTOMIX_TESTS OFF CACHE BOOL "Host tests are built by the native CI job" FORCE)
set(LMG_AUTOMIX_JNI ON CACHE BOOL "Package the standalone AutoMix JNI bridge" FORCE)
add_subdirectory("${CMAKE_CURRENT_LIST_DIR}/../../../../native/automix"
                 "${CMAKE_CURRENT_BINARY_DIR}/automix")
