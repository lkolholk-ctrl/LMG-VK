#!/usr/bin/env bash
# Pure Kotlin production ledger/publication + existing real coroutine lifecycle scenarios.
# This does NOT compile the Android sink decorator or MediaCodec renderer changes.
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"; cd "$ROOT"
for tool in java kotlinc python3; do command -v "$tool" >/dev/null; done
KOTLIN_HOME="${KOTLIN_HOME:-$(dirname -- "$(dirname -- "$(readlink -f "$(command -v kotlinc)")")")}"
COROUTINES_JAR="${COROUTINES_JAR:-$KOTLIN_HOME/lib/kotlinx-coroutines-core-jvm.jar}"
[[ -f "$COROUTINES_JAR" ]]
OUT="$(mktemp -d "${TMPDIR:-/tmp}/lmg-render-boundary.XXXXXXXX")"
trap 'rm -rf -- "$OUT"' EXIT
MAIN=app/src/main/kotlin/com/lmg/vk/engine/automix/observation
RENDER=app/src/main/kotlin/com/lmg/vk/engine/automix/render
TEST=app/src/test/kotlin/com/lmg/vk/engine/automix/observation
RTEST=app/src/test/kotlin/com/lmg/vk/engine/automix/render
BRIDGE=native/automix/android/src/main/kotlin/com/lmg/vk/engine/automix/nativecore/NativeObservationBridge.kt
kotlinc "$MAIN/ObservationPipeline.kt" "$MAIN/ResolvedPlannerScope.kt" "$MAIN/PlannerSelectionReport.kt" \
 "$MAIN/PlannerSourceContextWire.kt" "$MAIN/MetadataProbe.kt" "$MAIN/PlannerPreparation.kt" \
 "$MAIN/RenderBoundaryPublication.kt" "$MAIN/LiveScheduleEncoding.kt" "$BRIDGE" "$RENDER/RenderBoundary.kt" \
 "$RENDER/BoundaryForwarding.kt" "$RENDER/PcmCueGate.kt" "$RTEST/RenderBoundaryScenarios.kt" \
 "$TEST/ObservationBindingScenarios.kt" "$TEST/RenderBoundaryPublicationScenarios.kt" \
 "$TEST/ObservationPipelineScenarios.kt" "$TEST/ObservationSourceContextScenarios.kt" \
 "$TEST/PlannerSourceTransportScenarios.kt" -cp "$COROUTINES_JAR" -include-runtime -d "$OUT/publication.jar"
java -cp "$OUT/publication.jar:$COROUTINES_JAR" com.lmg.vk.engine.automix.render.RenderBoundaryScenarios
for suite in RenderBoundaryPublicationScenarios ObservationPipelineScenarios ObservationBindingScenarios ObservationSourceContextScenarios; do
 java -cp "$OUT/publication.jar:$COROUTINES_JAR" "com.lmg.vk.engine.automix.observation.$suite"
done
printf '%s\n' 'PASS: 62 ledger + 8 publication + 44 prior lifecycle. Android/fork/AAR/device remain separate.'
