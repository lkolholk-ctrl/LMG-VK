#!/usr/bin/env bash
# Exact new source module, then real existing composition/wire/lifecycle checks.
# Does not replace the complete CTest, JSON+JNI Gradle or Android/device pass.
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
CXX="${CXX:-c++}"
OUT="$(mktemp -d "${TMPDIR:-/tmp}/lmg-source-bindings.XXXXXXXX")"
trap 'rm -rf -- "$OUT"' EXIT
FLAGS=(-std=c++17 -O1 -Wall -Wextra -Wpedantic -Werror -ffp-contract=off -I native/automix/include)
if [[ "${AUTOMIX_BINDING_SANITIZE:-0}" == 1 ]]; then
 FLAGS+=(-g -fsanitize=address,undefined -fno-omit-frame-pointer -fno-sanitize-recover=all)
 export ASAN_OPTIONS="${ASAN_OPTIONS:-detect_leaks=1:halt_on_error=1}"
 export UBSAN_OPTIONS="${UBSAN_OPTIONS:-halt_on_error=1:print_stacktrace=1}"
fi
"$CXX" "${FLAGS[@]}" native/automix/src/planner_source_bindings.cpp \
 native/automix/tests/planner_source_bindings_test.cpp -o "$OUT/source-tests"
"$OUT/source-tests" profile
"$OUT/source-tests" criteria
bash scripts/automix_selection_binding_host_checks.sh
