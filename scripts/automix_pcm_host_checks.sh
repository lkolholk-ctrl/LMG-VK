#!/usr/bin/env bash
# HOST ONLY. No Player/device/Android action, network request, repo edit or audio playback.
# Pass a NEW or existing compatible CMake build dir; compilation uses actual repo DSP.
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD="${1:-$ROOT/build_native_stage4b}"
OUT="${2:-$(mktemp -d "${TMPDIR:-/tmp}/lmg-pcm-captures.XXXXXXXX")}"
mkdir -p "$OUT"
python3 - "$ROOT/research/ios26-automix/TransitionStyles.json" <<'PYHASH'
import hashlib, pathlib, sys
p = pathlib.Path(sys.argv[1])
expected = "fe3d0a36625ccb043c519bcc5119c98190893a9911cfa58412ad60cbab04d120"
if hashlib.sha256(p.read_bytes()).hexdigest() != expected:
    raise SystemExit("Canonical catalog SHA-256 mismatch")
PYHASH
cmake -S "$ROOT/native/automix" -B "$BUILD" \
  -DCMAKE_BUILD_TYPE=Debug -DLMG_AUTOMIX_TESTS=ON -DLMG_AUTOMIX_PCM_BENCH=ON
cmake --build "$BUILD" --parallel 2 --target \
  lmg_automix_pcm_bench lmg_automix_pcm_tests lmg_automix_pcm_support_tests
ctest --test-dir "$BUILD" -L offline_pcm --output-on-failure
for scenario in unity stretch; do
  "$BUILD/offline/lmg_automix_pcm_bench" \
    --catalog "$ROOT/research/ios26-automix/TransitionStyles.json" \
    --scenario "$scenario" --sample-rate 48000 --channels 2 \
    --output-dir "$OUT/$scenario"
  # Verification is read-only. Its output is not an Apple-parity certificate.
  python3 "$ROOT/scripts/automix_verify_pcm_report.py" "$OUT/$scenario"
done
printf '\nStage4b host acceptance PASSED. Captures: %s\n' "$OUT"
printf 'No Android build, device playback or full-source parity was asserted.\n'
