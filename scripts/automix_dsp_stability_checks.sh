#!/usr/bin/env bash
# Full existing native library required. No synthetic DSP or Android framework stubs.
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD="${1:?Usage: automix_dsp_stability_checks.sh native-build-dir}"
BUILD="$(cd -- "$BUILD" && pwd)"
cd "$ROOT"
test -s "$BUILD/android/liblmg_automix_jni.so"
OUT="$(mktemp -d)";trap 'rm -rf -- "$OUT"' EXIT
E=app/src/main/kotlin/com/lmg/vk/engine
R=$E/automix/render
N=native/automix/android/src/main/kotlin/com/lmg/vk/engine/automix/nativecore
T=app/src/test/kotlin/com/lmg/vk/engine/automix/render
kotlinc "$E/SinkAudioRouting.kt" "$E/AudioReactor.kt" "$E/DjStreamFx.kt" "$E/PcmBandMeter.kt" "$E/PcmNormalizationKernel.kt" \
 "$R/RenderBoundary.kt" "$R/PcmCueGate.kt" "$R/BoundaryForwarding.kt" "$R/NativeCueOwnerIngress.kt" \
 "$R/SameSinkOutputPort.kt" "$R/SameSinkPairOutput.kt" "$R/LivePcmPump.kt" "$R/PreparedResourceTask.kt" \
 "$N/NativeLivePcmExecutor.kt" "$N/NativePcmOwnerIngress.kt" "$T/LivePcmPumpScenarios.kt" "$T/DspStabilityScenarios.kt" \
 -include-runtime -d "$OUT/stability.jar"
java -Djava.library.path="$BUILD/android" -cp "$OUT/stability.jar" com.lmg.vk.engine.automix.render.DspStabilityScenarios
java -Djava.library.path="$BUILD/android" -cp "$OUT/stability.jar" com.lmg.vk.engine.automix.render.DspStabilityScenarios --allocations
