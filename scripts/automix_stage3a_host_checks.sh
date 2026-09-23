#!/usr/bin/env bash
# Preparation/maps/region inventory and native->Kotlin transport. Does NOT
# replace full CTest, raw-JSON/JNI JUnit, Android packaging or device testing.
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
[[ -f "$JAVA_HOME/include/jni.h" && -f "$JAVA_HOME/include/linux/jni_md.h" && -f "$COROUTINES_JAR" ]] || {
  echo "Set JAVA_HOME/KOTLIN_HOME/COROUTINES_JAR for a Linux JDK and Kotlin" >&2; exit 1;
}
OUT="$(mktemp -d "${TMPDIR:-/tmp}/lmg-automix-stage3a.XXXXXXXX")"
trap 'if [[ "${AUTOMIX_STAGE3A_KEEP:-0}" == 1 ]]; then echo "Host files retained: $OUT"; else rm -rf -- "$OUT"; fi' EXIT
FLAGS=(-std=c++17 -Wall -Wextra -Wpedantic -Werror -ffp-contract=off -I native/automix/include)
CORE=(native/automix/src/planner_regions.cpp native/automix/src/planner_observation.cpp
      native/automix/src/planner_analysis.cpp native/automix/src/planner_flex.cpp
      native/automix/src/planner_beats.cpp native/automix/src/planner_loudness.cpp
      native/automix/src/planner_vocals.cpp)
"$CXX" "${FLAGS[@]}" "${CORE[@]}" native/automix/tests/planner_preparation_test.cpp -o "$OUT/preparation-tests"
"$OUT/preparation-tests" "$OUT/native-snapshots.bin"
# Compile the production raw entry point and JNI against real headers. This is
# object/syntax verification, NOT a claim that the raw decoder was linked/run.
"$CXX" "${FLAGS[@]}" -c native/automix/src/planner_observation_json.cpp -o "$OUT/raw.o"
"$CXX" "${FLAGS[@]}" -I "$JAVA_HOME/include" -I "$JAVA_HOME/include/linux" \
  -c native/automix/android/planner_observation_jni.cpp -o "$OUT/jni.o"
nm "$OUT/jni.o" | grep 'Java_com_lmg_vk_engine_automix_nativecore_NativeObservationBridge_preparePair' >/dev/null
MAIN=app/src/main/kotlin/com/lmg/vk/engine/automix/observation
TEST=app/src/test/kotlin/com/lmg/vk/engine/automix/observation
BRIDGE=native/automix/android/src/main/kotlin/com/lmg/vk/engine/automix/nativecore/NativeObservationBridge.kt
kotlinc "$MAIN/ObservationPipeline.kt" "$MAIN/PlannerPreparation.kt" "$BRIDGE" \
  "$TEST/PlannerPreparationTransportScenarios.kt" -cp "$COROUTINES_JAR" -include-runtime -d "$OUT/transport.jar"
java -cp "$OUT/transport.jar:$COROUTINES_JAR" \
  com.lmg.vk.engine.automix.observation.PlannerPreparationTransportScenarios "$OUT/native-snapshots.bin"
python3 scripts/automix_verify_stage3a_reports_test.py
python3 scripts/automix_collect_stage3_region_evidence_test.py
printf '%s\n' 'Stage 3a targeted checks PASSED. Full CTest/JNI/Android verification still required.'
