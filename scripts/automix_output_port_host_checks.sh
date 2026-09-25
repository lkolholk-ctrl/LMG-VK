#!/usr/bin/env bash
# Real output/gate Kotlin and existing JNI ingress. Recording backend, no AudioTrack/DSP claim.
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
for tool in javac java kotlinc python3; do command -v "$tool" >/dev/null; done
CXX="${CXX:-c++}"
JAVA_HOME="${JAVA_HOME:-$(dirname -- "$(dirname -- "$(readlink -f "$(command -v javac)")")")}"
OUT="$(mktemp -d "${TMPDIR:-/tmp}/lmg-output-port.XXXXXXXX")"
trap 'rm -rf -- "$OUT"' EXIT
R=app/src/main/kotlin/com/lmg/vk/engine/automix/render
T=app/src/test/kotlin/com/lmg/vk/engine/automix/render
N=native/automix
kotlinc "$R/RenderBoundary.kt" "$R/PcmCueGate.kt" "$R/BoundaryForwarding.kt" \
 "$R/SameSinkOutputPort.kt" "$R/SameSinkPairOutput.kt" "$T/SameSinkOutputScenarios.kt" \
 -include-runtime -d "$OUT/output.jar"
java -jar "$OUT/output.jar"
"$CXX" -std=c++17 -Wall -Wextra -Wpedantic -Werror -pthread -shared -fPIC \
 -I "$N/include" -I "$JAVA_HOME/include" -I "$JAVA_HOME/include/linux" \
 "$N/src/pcm_owner_ingress.cpp" "$N/android/pcm_owner_ingress_jni.cpp" -o "$OUT/liblmg_automix_jni.so"
kotlinc "$R/RenderBoundary.kt" "$R/PcmCueGate.kt" "$R/BoundaryForwarding.kt" \
 "$R/SameSinkOutputPort.kt" "$R/SameSinkPairOutput.kt" "$R/NativeCueOwnerIngress.kt" \
 "$N/android/src/main/kotlin/com/lmg/vk/engine/automix/nativecore/NativePcmOwnerIngress.kt" \
 "$T/SameSinkPairScenarios.kt" -include-runtime -d "$OUT/pair.jar"
java -Djava.library.path="$OUT" -jar "$OUT/pair.jar"
python3 scripts/automix_output_port_guard_tests.py
printf '%s\n' 'OUTPUT_PORT_HOST_VERIFIED: 60 pure-Kotlin +18 real ingress-JNI scenarios. Recording output and test clock lease; no device or DSP playback claim.'
