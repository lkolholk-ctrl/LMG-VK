#!/usr/bin/env bash
# Targeted Linux host checks. Does NOT replace full CTest/JNI/Android verification.
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
for tool in javac java kotlinc python3 nm; do
  command -v "$tool" >/dev/null || { echo "Required command missing: $tool" >&2; exit 1; }
done
CXX="${CXX:-c++}"
command -v "$CXX" >/dev/null || { echo "C++ compiler missing: $CXX" >&2; exit 1; }
JAVA_HOME="${JAVA_HOME:-$(dirname -- "$(dirname -- "$(readlink -f "$(command -v javac)")")")}"
KOTLIN_HOME="${KOTLIN_HOME:-$(dirname -- "$(dirname -- "$(readlink -f "$(command -v kotlinc)")")")}"
COROUTINES_JAR="${COROUTINES_JAR:-$KOTLIN_HOME/lib/kotlinx-coroutines-core-jvm.jar}"
[[ -f "$JAVA_HOME/include/jni.h" && -f "$JAVA_HOME/include/linux/jni_md.h" ]] || {
  echo "Set JAVA_HOME to a Linux JDK with JNI headers" >&2; exit 1;
}
[[ -f "$COROUTINES_JAR" ]] || { echo "Set COROUTINES_JAR to kotlinx-coroutines-core-jvm.jar" >&2; exit 1; }
OUT="$(mktemp -d "${TMPDIR:-/tmp}/lmg-automix-stage2.XXXXXXXX")"
trap 'if [[ "${AUTOMIX_STAGE2_KEEP:-0}" == 1 ]]; then echo "Targeted test files retained: $OUT"; else rm -rf -- "$OUT"; fi' EXIT
mkdir -p "$OUT/native"
echo 'Stage 2 targeted host checks: real scoring/probe JNI; no Android/full-core build.'
FLAGS=(-std=c++17 -Wall -Wextra -Wpedantic -Werror -ffp-contract=off -I native/automix/include)
CORE=(native/automix/src/planner_scoring.cpp native/automix/src/metadata_probe.cpp)
"$CXX" "${FLAGS[@]}" "${CORE[@]}" native/automix/tests/metadata_probe_test.cpp -o "$OUT/native/metadata_probe_tests"
"$OUT/native/metadata_probe_tests"
"$CXX" "${FLAGS[@]}" -shared -fPIC -I "$JAVA_HOME/include" -I "$JAVA_HOME/include/linux" \
  "${CORE[@]}" native/automix/android/metadata_probe_jni.cpp -o "$OUT/native/liblmg_automix_jni.so"
nm -D "$OUT/native/liblmg_automix_jni.so" | grep 'Java_com_lmg_vk_engine_automix_nativecore_NativeObservationBridge_probePair' >/dev/null
MAIN=app/src/main/kotlin/com/lmg/vk/engine/automix/observation
TEST=app/src/test/kotlin/com/lmg/vk/engine/automix/observation
BRIDGE=native/automix/android/src/main/kotlin/com/lmg/vk/engine/automix/nativecore/NativeObservationBridge.kt
kotlinc "$MAIN/ObservationPipeline.kt" "$TEST/ObservationPipelineScenarios.kt" \
  -cp "$COROUTINES_JAR" -include-runtime -d "$OUT/pipeline-tests.jar"
java -cp "$OUT/pipeline-tests.jar:$COROUTINES_JAR" com.lmg.vk.engine.automix.observation.ObservationPipelineScenarios
kotlinc "$MAIN/ObservationPipeline.kt" "$MAIN/MetadataProbe.kt" "$MAIN/PlannerPreparation.kt" "$BRIDGE" "$TEST/NativeMetadataProbeScenarios.kt" \
  -cp "$COROUTINES_JAR" -include-runtime -d "$OUT/probe-jni-tests.jar"
java -Djava.library.path="$OUT/native" -cp "$OUT/probe-jni-tests.jar:$COROUTINES_JAR" \
  com.lmg.vk.engine.automix.observation.NativeMetadataProbeScenarios
python3 scripts/automix_verify_stage2_reports_test.py
echo 'Targeted host checks PASSED. Run complete CMake, Gradle, APK and device checks separately.'
