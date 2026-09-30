#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD="${1:?Usage: dsp_host_checks.sh build-directory}"
cmake -S "$ROOT/native/dsp" -B "$BUILD" -DLMG_PLAYER_DSP_TESTS=ON -DLMG_DSP_BUILD_TESTS=ON -DCMAKE_BUILD_TYPE=Release
cmake --build "$BUILD" -j2
ctest --test-dir "$BUILD" --output-on-failure
kotlinc "$ROOT/app/src/main/kotlin/com/lmg/vk/engine/dsp/NativeDsp.kt" \
    "$ROOT/app/src/test/kotlin/com/lmg/vk/engine/dsp/DspJniScenarios.kt" \
    -include-runtime -d "$BUILD/jni-checks.jar"
java -Djava.library.path="$BUILD" -cp "$BUILD/jni-checks.jar" com.lmg.vk.engine.dsp.DspJniScenarios

# Optional real Media3/JNI lifecycle check. Classpath must contain media3-common,
# Guava, Android SDK android.jar, androidx annotations, JUnit4 and Hamcrest.
if [[ -n "${LMG_DSP_MEDIA3_TEST_CLASSPATH:-}" ]]; then
    kotlinc "$ROOT/app/src/main/kotlin/com/lmg/vk/engine/dsp/NativeDsp.kt" \
        "$ROOT/app/src/main/kotlin/com/lmg/vk/engine/dsp/DspAudioProcessor.kt" \
        "$ROOT/app/src/test/kotlin/com/lmg/vk/engine/dsp/DspAudioProcessorTest.kt" \
        -cp "$LMG_DSP_MEDIA3_TEST_CLASSPATH" -include-runtime -d "$BUILD/media3-checks.jar"
    java -Djava.library.path="$BUILD" -cp "$BUILD/media3-checks.jar:$LMG_DSP_MEDIA3_TEST_CLASSPATH" \
        org.junit.runner.JUnitCore com.lmg.vk.engine.dsp.DspAudioProcessorTest
fi
