#!/usr/bin/env bash
# Real native models/composition and Kotlin epoch/codec checks. No full JSON/JNI/APK claim.
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
for tool in java javac kotlinc python3 nm; do command -v "$tool" >/dev/null; done
CXX="${CXX:-c++}"; command -v "$CXX" >/dev/null
JAVA_HOME="${JAVA_HOME:-$(dirname -- "$(dirname -- "$(readlink -f "$(command -v javac)")")")}"
KOTLIN_HOME="${KOTLIN_HOME:-$(dirname -- "$(dirname -- "$(readlink -f "$(command -v kotlinc)")")")}"
COROUTINES_JAR="${COROUTINES_JAR:-$KOTLIN_HOME/lib/kotlinx-coroutines-core-jvm.jar}"
[[ -f "$JAVA_HOME/include/jni.h" && -f "$JAVA_HOME/include/linux/jni_md.h" && -f "$COROUTINES_JAR" ]]
OUT="$(mktemp -d "${TMPDIR:-/tmp}/lmg-selection-binding.XXXXXXXX")"
trap 'if [[ "${AUTOMIX_BINDING_KEEP:-0}" == 1 ]]; then echo "Checks retained: $OUT"; else rm -rf -- "$OUT"; fi' EXIT
FLAGS=(-std=c++17 -O1 -Wall -Wextra -Wpedantic -Werror -ffp-contract=off -I native/automix/include)
if [[ "${AUTOMIX_BINDING_SANITIZE:-0}" == 1 ]]; then
 FLAGS+=(-g -fsanitize=address,undefined -fno-omit-frame-pointer -fno-sanitize-recover=all)
 export ASAN_OPTIONS="${ASAN_OPTIONS:-detect_leaks=1:halt_on_error=1}"
 export UBSAN_OPTIONS="${UBSAN_OPTIONS:-halt_on_error=1:print_stacktrace=1}"
fi
CORE=(planner_scoring planner_loudness planner_vocals planner_flex planner_beats planner_analysis
 planner_regions planner_observation planner_region_algebra planner_candidate_selector
 planner_candidate_observation planner_input_producers planner_produced_observation planner_selection_binding)
OBJECTS=()
for module in "${CORE[@]}"; do
 "$CXX" "${FLAGS[@]}" -c "native/automix/src/$module.cpp" -o "$OUT/$module.o"
 OBJECTS+=("$OUT/$module.o")
done
"$CXX" "${FLAGS[@]}" native/automix/tests/planner_selection_binding_test.cpp "${OBJECTS[@]}" -o "$OUT/native-tests"
"$OUT/native-tests" "$OUT/snapshots.bin"
# Actual source/signatures and exported JNI entry; not a substitute for linking/running raw decoder.
"$CXX" "${FLAGS[@]}" -c native/automix/src/planner_selection_binding_json.cpp -o "$OUT/raw.o"
"$CXX" "${FLAGS[@]}" -c native/automix/tests/planner_selection_binding_json_test.cpp -o "$OUT/raw-test.o"
"$CXX" "${FLAGS[@]}" -I "$JAVA_HOME/include" -I "$JAVA_HOME/include/linux" \
 -c native/automix/android/planner_selection_binding_jni.cpp -o "$OUT/jni.o"
nm "$OUT/jni.o" | grep 'Java_com_lmg_vk_engine_automix_nativecore_NativeObservationBridge_selectResolvedPair' >/dev/null
MAIN=app/src/main/kotlin/com/lmg/vk/engine/automix/observation
TEST=app/src/test/kotlin/com/lmg/vk/engine/automix/observation
BRIDGE=native/automix/android/src/main/kotlin/com/lmg/vk/engine/automix/nativecore/NativeObservationBridge.kt
kotlinc "$MAIN/ObservationPipeline.kt" "$MAIN/ResolvedPlannerScope.kt" "$MAIN/PlannerSelectionReport.kt" "$BRIDGE" \
 "$TEST/ObservationBindingScenarios.kt" "$TEST/PlannerSelectionTransportScenarios.kt" \
 -cp "$COROUTINES_JAR" -include-runtime -d "$OUT/kotlin-tests.jar"
java -cp "$OUT/kotlin-tests.jar:$COROUTINES_JAR" \
 com.lmg.vk.engine.automix.observation.PlannerSelectionTransportScenarios "$OUT/snapshots.bin"
java -cp "$OUT/kotlin-tests.jar:$COROUTINES_JAR" \
 com.lmg.vk.engine.automix.observation.ObservationBindingScenarios
python3 scripts/automix_verify_selection_binding_reports_test.py
python3 scripts/automix_verify_jni_export_test.py
printf '%s\n' 'Selection binding host checks PASSED; raw JSON+JNI/JUnit, full CTest, Android and device checks remain separate.'
