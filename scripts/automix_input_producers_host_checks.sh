#!/usr/bin/env bash
# Targeted real native producer/preparation/selector checks. No full CTest/JNI/Android claim.
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
CXX="${CXX:-c++}"
command -v "$CXX" >/dev/null
command -v python3 >/dev/null
OUT="$(mktemp -d "${TMPDIR:-/tmp}/lmg-input-producers.XXXXXXXX")"
trap 'if [[ "${AUTOMIX_PRODUCER_KEEP:-0}" == 1 ]]; then echo "Targeted native files retained: $OUT"; else rm -rf -- "$OUT"; fi' EXIT
FLAGS=(-std=c++17 -O2 -Wall -Wextra -Wpedantic -Werror -ffp-contract=off -I native/automix/include)
if [[ "${AUTOMIX_PRODUCER_SANITIZE:-0}" == 1 ]]; then
  FLAGS+=(-O1 -g -fsanitize=address,undefined -fno-omit-frame-pointer -fno-sanitize-recover=all)
  export ASAN_OPTIONS="${ASAN_OPTIONS:-detect_leaks=1:halt_on_error=1}"
  export UBSAN_OPTIONS="${UBSAN_OPTIONS:-halt_on_error=1:print_stacktrace=1}"
fi
CORE=(planner_scoring planner_loudness planner_vocals planner_flex planner_beats planner_analysis
      planner_regions planner_observation planner_region_algebra planner_candidate_selector
      planner_candidate_observation planner_input_producers planner_produced_observation)
OBJECTS=()
for module in "${CORE[@]}"; do
  "$CXX" "${FLAGS[@]}" -c "native/automix/src/$module.cpp" -o "$OUT/$module.o"
  OBJECTS+=("$OUT/$module.o")
done
for test in planner_input_producers planner_produced_observation planner_producer_reference; do
  "$CXX" "${FLAGS[@]}" "native/automix/tests/${test}_test.cpp" "${OBJECTS[@]}" -o "$OUT/$test"
done
python3 scripts/automix_producer_reference.py --check native/automix/tests/fixtures/planner_producer_reference.tsv
"$OUT/planner_input_producers"
"$OUT/planner_produced_observation"
"$OUT/planner_producer_reference" native/automix/tests/fixtures/planner_producer_reference.tsv
echo 'Input producer host checks PASSED. Full repository CTest, raw JSON/JNI, Android and device checks are separate.'
