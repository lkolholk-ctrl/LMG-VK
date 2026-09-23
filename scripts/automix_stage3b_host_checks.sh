#!/usr/bin/env bash
# Targeted region-algebra checks. Not a full CTest/JNI/Android verification.
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
CXX="${CXX:-c++}"
for tool in "$CXX" python3; do
  command -v "$tool" >/dev/null || { printf 'Required tool missing: %s\n' "$tool" >&2; exit 1; }
done
OUT="$(mktemp -d "${TMPDIR:-/tmp}/lmg-automix-stage3b.XXXXXXXX")"
trap 'if [[ "${AUTOMIX_STAGE3B_KEEP:-0}" == 1 ]]; then printf "Targeted artifacts: %s\n" "$OUT"; else rm -rf -- "$OUT"; fi' EXIT
FLAGS=(-std=c++17 -Wall -Wextra -Wpedantic -Werror -ffp-contract=off -I native/automix/include)
if [[ "${AUTOMIX_STAGE3B_SANITIZERS:-0}" == 1 ]]; then
  FLAGS+=(-O1 -g -fsanitize=address,undefined -fno-omit-frame-pointer)
else
  FLAGS+=(-O2)
fi
SOURCES=(native/automix/src/planner_region_algebra.cpp native/automix/src/planner_beats.cpp
         native/automix/src/planner_flex.cpp native/automix/tests/planner_region_algebra_test.cpp)
echo 'Stage 3b targeted checks: native region algebra and existing Flex/structure code. No PCM or style selector.'
python3 native/automix/tools/planner_region_reference.py --check
"$CXX" "${FLAGS[@]}" "${SOURCES[@]}" -o "$OUT/region-tests"
"$OUT/region-tests"
"$OUT/region-tests" --reference native/automix/tests/fixtures/planner_region_reference.tsv
python3 scripts/automix_collect_stage3b_dependencies_test.py
echo 'Stage 3b targeted checks PASSED. Full CTest, prior JNI/lifecycle tests and Android build are separate.'
