#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

fail=0

check_forbidden() {
  local pattern="$1"
  local label="$2"
  if rg -n -i "$pattern" app/src/main; then
    echo "ERROR: found forbidden $label" >&2
    fail=1
  fi
}

check_forbidden 'byicloud|byicm' 'ICM/byicloud reference'
check_forbidden 'WaveOnboarding|needsOnboarding|onboarding_(completed|genres|artists)' 'removed onboarding reference'
check_forbidden 'source\s*=\s*"(apple|yandex|tidal|spotify)"|startsWith\("ym_' 'non-VK music source selector'
check_forbidden 'ic_service_(apple_music|spotify|yandex)' 'removed service drawable reference'

for screen in \
  app/src/main/kotlin/com/lmg/vk/ui/screens/HomeScreen.kt \
  app/src/main/kotlin/com/lmg/vk/ui/screens/WaveHomeScreen.kt; do
  if rg -n 'PlayerController|MusicBackend|WaveRepository|collectAsState|LaunchedEffect' "$screen"; then
    echo "ERROR: $screen is no longer presentation-only" >&2
    fail=1
  fi
done

if (( fail != 0 )); then
  exit 1
fi

echo "VK-only source guard passed"
