#!/usr/bin/env bash
# Actual new native admission + JNI + production gate. No DSP/device substitutes are linked.
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
for tool in javac java kotlinc python3; do command -v "$tool" >/dev/null; done
CXX="${CXX:-c++}"
JAVA_HOME="${JAVA_HOME:-$(dirname -- "$(dirname -- "$(readlink -f "$(command -v javac)")")")}"
OUT="$(mktemp -d "${TMPDIR:-/tmp}/lmg-owner-ingress.XXXXXXXX")"
trap 'rm -rf -- "$OUT"' EXIT
FLAGS=(-std=c++17 -Wall -Wextra -Wpedantic -Werror -ffp-contract=off -pthread -I native/automix/include)
"$CXX" "${FLAGS[@]}" native/automix/src/pcm_owner_ingress.cpp native/automix/tests/pcm_owner_ingress_test.cpp -o "$OUT/native-test"
"$OUT/native-test"
"$OUT/native-test" --random
"$CXX" "${FLAGS[@]}" -shared -fPIC -I "$JAVA_HOME/include" -I "$JAVA_HOME/include/linux" \
 native/automix/src/pcm_owner_ingress.cpp native/automix/android/pcm_owner_ingress_jni.cpp -o "$OUT/liblmg_automix_jni.so"
PYTHONPATH=scripts python3 - "$OUT/liblmg_automix_jni.so" <<'CHECK_EXPORTS'
from pathlib import Path
import sys
from automix_verify_owner_ingress import verify_owner_exports
from automix_verify_jni_export import OWNER_SYMBOLS
raw=Path(sys.argv[1]).read_bytes()
verify_owner_exports(raw,2,62) # actual host x86-64 component, not Android ABI proof
for symbol in OWNER_SYMBOLS:
    corrupt=raw.replace(symbol,b'X'+symbol[1:])
    try: verify_owner_exports(corrupt,2,62)
    except ValueError: pass
    else: raise AssertionError('Missing owner export accepted')
print('PASS actual host JNI ELF: 9 exports; 9 removed-symbol variants rejected')
CHECK_EXPORTS
MAIN=app/src/main/kotlin/com/lmg/vk/engine/automix/render
TEST=app/src/test/kotlin/com/lmg/vk/engine/automix/render
kotlinc "$MAIN/RenderBoundary.kt" "$MAIN/BoundaryForwarding.kt" "$MAIN/PcmCueGate.kt" "$MAIN/NativeCueOwnerIngress.kt" \
 native/automix/android/src/main/kotlin/com/lmg/vk/engine/automix/nativecore/NativePcmOwnerIngress.kt \
 "$TEST/PcmOwnerTransactionScenarios.kt" "$TEST/PcmCueGateScenarios.kt" "$TEST/RenderBoundaryScenarios.kt" \
 -include-runtime -d "$OUT/owner.jar"
for suite in PcmOwnerTransactionScenarios PcmCueGateScenarios RenderBoundaryScenarios; do
 java -Djava.library.path="$OUT" -cp "$OUT/owner.jar" "com.lmg.vk.engine.automix.render.$suite"
done
python3 scripts/automix_owner_ingress_guard_tests.py
printf '%s\n' 'HOST_OWNER_INGRESS_VERIFIED. Output lease is a test double; no hardware/DSP/Android-build claim.'
