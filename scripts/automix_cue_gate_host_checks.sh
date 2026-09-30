#!/usr/bin/env bash
# Real pure-Kotlin gate/ledger. Does not compile the Android sink or run JNI/device audio.
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
for cmd in java kotlinc python3; do command -v "$cmd" >/dev/null; done
OUT="$(mktemp -d "${TMPDIR:-/tmp}/lmg-cue-gate.XXXXXXXX")"
trap 'rm -rf -- "$OUT"' EXIT
MAIN=app/src/main/kotlin/com/lmg/vk/engine/automix/render
TEST=app/src/test/kotlin/com/lmg/vk/engine/automix/render
kotlinc "$MAIN/RenderBoundary.kt" "$MAIN/BoundaryForwarding.kt" "$MAIN/PcmCueGate.kt" \
  "$TEST/PcmCueGateScenarios.kt" "$TEST/RenderBoundaryScenarios.kt" \
  -include-runtime -d "$OUT/gate.jar"
java -cp "$OUT/gate.jar" com.lmg.vk.engine.automix.render.PcmCueGateScenarios
java -cp "$OUT/gate.jar" com.lmg.vk.engine.automix.render.RenderBoundaryScenarios
python3 scripts/automix_cue_gate_report_tests.py
printf '%s\n' 'PASS: 70 cue gate + 62 original ledger scenarios. Android/JNI/device remain separate gates.'
