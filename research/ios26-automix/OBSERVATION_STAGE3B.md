# Stage 3b / region-algebra checkpoint

This patch implements recovered native region operations, not the full automatic
candidate selector. Production Observation Mode still cannot execute a transition.

## Scope

New `native/automix/include/lmg/automix/planner_region_algebra.h` and its C++
implementation provide unit-qualified owner-bound references, outgoing bar suffixes,
incoming beat suffixes/halving, inverse-scaled count budgets, composition of supplied
seed pairs, time-pair truncation, incoming start resolution from two supplied values,
and checks of already-resolved placement bounds.

The functions require native seed regions / resolved inputs. They do NOT enumerate
all stable-region pairs as a substitute for recovered source generation. They do
NOT take Kotlin-supplied musical predicates or create a fake successful plan.

Only the native CMake file changes among existing files. New tests and research
tools are added. `AudioService`, the observation state machine, JNI ABI, Kotlin,
Gradle, Media3 pins, DSP kernels and scoring remain unchanged. No Oboe/JUCE/TFLite.
The user's latest collector change (scan limit 1000000 in commit c47d129) is retained.

## Example from a synthetic native structure fixture

At four beats/bar and 0.5 seconds/beat, a supplied outgoing region [16,32] seconds
and incoming region [0,16] seconds with a requested four-bar suffix produce:

| Supplied incoming scale | Outgoing result | Incoming result |
|---|---|---|
| one | [24,32] | [8,16] |
| half | [24,32] | [0,16] |
| two | [24,32] | [12,16] |

These are transformations of a supplied seed pair, not proof that Apple's
candidate generator would choose these seeds or any particular style.

## Validation after applying

```bash
set -euo pipefail
git diff --check
cmake -S native/automix -B build_native_stage3b \
  -DCMAKE_BUILD_TYPE=Debug \
  -DLMG_AUTOMIX_JNI=ON -DLMG_AUTOMIX_TESTS=ON \
  -DLMG_AUTOMIX_SANITIZERS=OFF
cmake --build build_native_stage3b --parallel 2
ctest --test-dir build_native_stage3b --output-on-failure
bash scripts/automix_stage2_host_checks.sh
bash scripts/automix_stage3a_host_checks.sh
bash scripts/automix_stage3b_host_checks.sh
./gradlew :app:testDebugUnitTest :app:assembleDebug --rerun-tasks --stacktrace \
  -PautomixHostLibraryPath="$PWD/build_native_stage3b/android"
python3 scripts/automix_verify_observation_apk.py
```

Expected total: 45 CTest suites (previous 43 plus `automix_region_algebra` and
`automix_region_algebra_reference`). Previous 17 real JNI tests and 20 lifecycle
tests remain required; no new JNI tests are claimed for a new JNI entry point,
because this patch does not add one. Current catalog SHA and three ABI checks remain.
These are server acceptance criteria, not locally claimed full-run results.

Targeted sanitizer run (separate from the normal host/JNI build):

```bash
CXX=clang++ AUTOMIX_STAGE3B_SANITIZERS=1 \
ASAN_OPTIONS=detect_leaks=1 UBSAN_OPTIONS=halt_on_error=1 \
  bash scripts/automix_stage3b_host_checks.sh
```

## Collect the next source dependency corpus

The prior explicit allowlist collected the requested callers, but not their callees.
The new tool leaves that original script untouched. It reads only address-named
text dumps and selected reports, follows direct calls/named callbacks to a bounded
depth, and creates a new ZIP without executing evidence or sending data anywhere.

```bash
set -euo pipefail
RESEARCH_ROOT="${RESEARCH_ROOT:-/srv/research/apple-music-ios26}"
OUT_DIR="$(mktemp -d /tmp/automix-stage3b-dependencies.XXXXXXXX)"
python3 scripts/automix_collect_stage3b_dependencies.py \
  --root "$RESEARCH_ROOT" \
  --output "$OUT_DIR/automix-stage3b-dependencies.zip"
python3 - "$OUT_DIR/automix-stage3b-dependencies.zip" <<'PY'
import json, sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as z:
    m = json.loads(z.read('manifest.json'))
print('Status:', m['status'])
print('Files:', m['fileCount'], 'Bytes:', m['uncompressedBytes'])
print('Missing required:', m['missingRequiredDisassembly'])
print('Unvisited at depth limit:', m['unvisitedAtDepthLimit'])
print('Files for review:')
for entry in m['files']:
    print(entry['path'], entry['bytes'], entry['sha256'])
PY
sha256sum "$OUT_DIR/automix-stage3b-dependencies.zip"
printf '\nEvidence package: %s\n' "$OUT_DIR/automix-stage3b-dependencies.zip"
```

Limits: 1024 text files, 2 MiB/file, 32 MiB total, 2000000 scanned filenames,
maximum call depth 4 by default (CLI accepts 0..8). Reports of incomplete bodies
are explicit; a nonempty ZIP never implies full evidence. On a size/scan failure,
no package is published. Narrow the root to decompiled_package/automix when needed;
additional NON-overlapping research roots can be passed with repeated `--root`.
Review the manifest before sharing; unknown protocol-witness bodies may still
require a separate source extraction even when all named dumps are present.

Implementation/fidelity details: `native/automix/evidence/PLANNER_REGION_ALGEBRA_IMPLEMENTATION.md`.
