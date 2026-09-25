#!/usr/bin/env bash
# Uses the COMPLETE freshly built project .so, never a substitute decoder/DSP.
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD="${1:?Usage: automix_live_executor_host_checks.sh /absolute/native-build}"
BUILD="$(cd -- "$BUILD" && pwd)"
cd "$ROOT"
test -s "$BUILD/android/liblmg_automix_jni.so"
ctest --test-dir "$BUILD" -R '^automix_live_pcm_executor$' --output-on-failure
OUT="$(mktemp -d "${TMPDIR:-/tmp}/automix-live-jni.XXXXXXXX")"
trap 'rm -rf -- "$OUT"' EXIT
R=app/src/main/kotlin/com/lmg/vk/engine/automix/render
N=native/automix/android/src/main/kotlin/com/lmg/vk/engine/automix/nativecore
kotlinc "$R/RenderBoundary.kt" "$R/PcmCueGate.kt" "$R/BoundaryForwarding.kt" \
 "$R/SameSinkOutputPort.kt" "$R/SameSinkPairOutput.kt" "$R/NativeCueOwnerIngress.kt" \
 "$N/NativePcmOwnerIngress.kt" "$N/NativeLivePcmExecutor.kt" "$R/LivePcmPump.kt" \
 app/src/test/kotlin/com/lmg/vk/engine/automix/render/LivePcmPumpScenarios.kt \
 -include-runtime -d "$OUT/live.jar"
java -Djava.library.path="$BUILD/android" -jar "$OUT/live.jar"
printf '%s\n' 'LIVE_EXECUTOR_HOST_VERIFIED: real native DSP +13 JNI cases; output backend is recording, not hardware.'
