#!/usr/bin/env bash
# Real native schedule composition and Kotlin transport, not a full Android/JNI run.
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)";cd "$ROOT"
for tool in java javac kotlinc python3 nm; do command -v "$tool" >/dev/null;done
CXX="${CXX:-c++}"; command -v "$CXX" >/dev/null
JAVA_HOME="${JAVA_HOME:-$(dirname -- "$(dirname -- "$(readlink -f "$(command -v javac)")")")}"
KOTLIN_HOME="${KOTLIN_HOME:-$(dirname -- "$(dirname -- "$(readlink -f "$(command -v kotlinc)")")")}"
COROUTINES_JAR="${COROUTINES_JAR:-$KOTLIN_HOME/lib/kotlinx-coroutines-core-jvm.jar}"
[[ -f "$COROUTINES_JAR" && -f "$JAVA_HOME/include/jni.h" && -f "$JAVA_HOME/include/linux/jni_md.h" ]]
OUT="$(mktemp -d "${TMPDIR:-/tmp}/lmg-stage4a.XXXXXXXX")"
trap 'if [[ "${AUTOMIX_SCHEDULE_KEEP:-0}" == 1 ]]; then echo "Files retained: $OUT"; else rm -rf -- "$OUT";fi' EXIT
FLAGS=(-std=c++17 -O1 -Wall -Wextra -Wpedantic -Werror -ffp-contract=off -I native/automix/include)
if [[ "${AUTOMIX_SCHEDULE_SANITIZE:-0}" == 1 ]];then
 FLAGS+=(-g -fsanitize=address,undefined -fno-omit-frame-pointer -fno-sanitize-recover=all)
 export ASAN_OPTIONS="${ASAN_OPTIONS:-detect_leaks=1:halt_on_error=1}"
 export UBSAN_OPTIONS="${UBSAN_OPTIONS:-halt_on_error=1:print_stacktrace=1}"
fi
CORE=(planner_scoring planner_loudness planner_vocals planner_flex planner_beats planner_analysis
 planner_regions planner_observation planner_region_algebra planner_candidate_selector planner_candidate_observation
 planner_input_producers planner_produced_observation planner_selection_binding planner_source_bindings planner_source_context
 planner_transition_schedule planner_schedule_observation automation continuous_schedule playback_time parameter_catalog
 style_schedule style_timing style_curves)
OBJECTS=()
for module in "${CORE[@]}";do
 "$CXX" "${FLAGS[@]}" -c "native/automix/src/$module.cpp" -o "$OUT/$module.o";OBJECTS+=("$OUT/$module.o")
done
for test in planner_transition_schedule planner_schedule_observation;do
 "$CXX" "${FLAGS[@]}" "native/automix/tests/${test}_test.cpp" "${OBJECTS[@]}" -o "$OUT/$test"
done
"$OUT/planner_transition_schedule"
python3 scripts/automix_schedule_reference.py --check native/automix/tests/fixtures/planner_schedule_reference.tsv
"$OUT/planner_transition_schedule" --reference native/automix/tests/fixtures/planner_schedule_reference.tsv
"$OUT/planner_schedule_observation" "$OUT/schedules.bin"
if [[ "${AUTOMIX_SCHEDULE_NATIVE_ONLY:-0}" == 1 ]];then
 printf '%s\n' 'Stage 4a native-only checks PASSED (Kotlin/JNI compilation/report checks not requested).'
 exit 0
fi
# These compile checks do not execute a replacement parser or claim positive JSON/JNI execution.
"$CXX" "${FLAGS[@]}" -c native/automix/src/planner_schedule_observation_json.cpp -o "$OUT/json.o"
"$CXX" "${FLAGS[@]}" -c native/automix/tests/planner_schedule_json_test.cpp -o "$OUT/json-test.o"
"$CXX" "${FLAGS[@]}" -I "$JAVA_HOME/include" -I "$JAVA_HOME/include/linux" \
 -c native/automix/android/planner_schedule_jni.cpp -o "$OUT/jni.o"
nm "$OUT/jni.o" | grep 'Java_com_lmg_vk_engine_automix_nativecore_NativeObservationBridge_compileMusicKitScheduleV1' >/dev/null
MAIN=app/src/main/kotlin/com/lmg/vk/engine/automix/observation
TEST=app/src/test/kotlin/com/lmg/vk/engine/automix/observation
BRIDGE=native/automix/android/src/main/kotlin/com/lmg/vk/engine/automix/nativecore/NativeObservationBridge.kt
kotlinc "$MAIN/ObservationPipeline.kt" "$MAIN/ResolvedPlannerScope.kt" "$MAIN/PlannerSelectionReport.kt" \
 "$MAIN/PlannerSourceContextWire.kt" "$MAIN/PlannerScheduleWire.kt" "$BRIDGE" \
 "$TEST/PlannerSourceTransportScenarios.kt" "$TEST/PlannerScheduleTransportScenarios.kt" \
 -cp "$COROUTINES_JAR" -include-runtime -d "$OUT/wire.jar"
java -cp "$OUT/wire.jar:$COROUTINES_JAR" com.lmg.vk.engine.automix.observation.PlannerScheduleTransportScenarios "$OUT/schedules.bin"
python3 scripts/automix_verify_schedule_reports_test.py
printf '%s\n' 'Stage 4a targeted checks PASSED. Canonical raw JSON/real JNI/JUnit, complete CTest, APK and device checks are separate server gates.'
