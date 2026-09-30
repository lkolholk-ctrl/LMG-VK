#!/usr/bin/env bash
# Explicit-input native selector checks; not a full APK/JSON/JNI/device run.
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
CXX="${CXX:-c++}"
for tool in "$CXX" python3; do
  command -v "$tool" >/dev/null || { printf 'Missing tool: %s\n' "$tool" >&2; exit 1; }
done
OUT="$(mktemp -d "${TMPDIR:-/tmp}/lmg-automix-candidates.XXXXXXXX")"
trap 'if [[ "${AUTOMIX_CANDIDATE_KEEP:-0}" == 1 ]]; then printf "Artifacts: %s\n" "$OUT"; else rm -rf -- "$OUT"; fi' EXIT
FLAGS=(-std=c++17 -Wall -Wextra -Wpedantic -Werror -ffp-contract=off -I native/automix/include)
if [[ "${AUTOMIX_CANDIDATE_SANITIZERS:-0}" == 1 ]]; then
  FLAGS+=(-O1 -g -fsanitize=address,undefined -fno-omit-frame-pointer)
else
  FLAGS+=(-O2)
fi
CORE=(native/automix/src/planner_candidate_selector.cpp native/automix/src/planner_region_algebra.cpp
      native/automix/src/planner_scoring.cpp native/automix/src/planner_vocals.cpp native/automix/src/planner_loudness.cpp)
PREPARATION=(native/automix/src/planner_candidate_observation.cpp native/automix/src/planner_observation.cpp
      native/automix/src/planner_regions.cpp native/automix/src/planner_analysis.cpp
      native/automix/src/planner_beats.cpp native/automix/src/planner_flex.cpp)
echo 'Explicit-seed candidate selector: real C++ math and Stage 3a preparation; no PCM or automatic seed discovery.'
python3 native/automix/tools/planner_candidate_reference.py --check
"$CXX" "${FLAGS[@]}" "${CORE[@]}" native/automix/tests/planner_candidate_selector_test.cpp -o "$OUT/candidate-tests"
"$OUT/candidate-tests"
"$OUT/candidate-tests" --reference native/automix/tests/fixtures/planner_candidate_reference.tsv
"$CXX" "${FLAGS[@]}" "${CORE[@]}" "${PREPARATION[@]}" native/automix/tests/planner_candidate_prepared_test.cpp -o "$OUT/prepared-tests"
"$OUT/prepared-tests"
echo 'Candidate targeted checks PASSED. Full CTest, JSON/JNI, Gradle, packaging and device checks remain separate.'
