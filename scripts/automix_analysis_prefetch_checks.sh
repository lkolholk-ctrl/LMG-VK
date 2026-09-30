#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KOTLINC="${KOTLINC:-$(command -v kotlinc)}"
KLIB="${KOTLIN_LIB:-$(cd "$(dirname "$(readlink -f "$KOTLINC")")/../lib" && pwd)}"
COROUTINES="${COROUTINES_JAR:-$KLIB/kotlinx-coroutines-core-jvm.jar}"
[[ -f "$COROUTINES" ]] || { echo "COROUTINES_JAR not found" >&2; exit 1; }
BUILD="$(mktemp -d /tmp/automix-analysis-prefetch.XXXXXXXX)"
trap 'rm -rf "$BUILD"' EXIT
S="$ROOT/app/src/main/kotlin/com/lmg/vk/engine/automix/analysis"
T="$ROOT/app/src/test/kotlin/com/lmg/vk/engine/automix/analysis"
"$KOTLINC" "$S/RuAutoMixAnalysisClient.kt" "$S/AnalysisPrefetchStore.kt" \
  "$S/AnalysisPrefetchCoordinator.kt" "$T/AnalysisPrefetchScenarios.kt" \
  -cp "$COROUTINES" -include-runtime -d "$BUILD/prefetch.jar"
java -cp "$BUILD/prefetch.jar:$COROUTINES" \
  com.lmg.vk.engine.automix.analysis.AnalysisPrefetchScenariosKt
