#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
results=()
failed=0

run_suite() {
  local name="$1"
  shift

  printf '\n=== %s ===\n' "$name"
  if "$@"; then
    results+=("PASS  $name")
  else
    local exit_code=$?
    results+=("FAIL  $name (exit code $exit_code)")
    failed=1
  fi
}

run_suite "Backend tests" bash -c 'cd "$1/backend" && ./gradlew test' _ "$repo_root"
run_suite "Frontend tests" bash -c 'cd "$1/frontend" && npm run test:ci' _ "$repo_root"
run_suite "Headless browser tests" bash "$repo_root/scripts/e2e/run.sh" fullstack

printf '\n=== Test recap ===\n'
for result in "${results[@]}"; do
  printf '%s\n' "$result"
done

if (( failed )); then
  printf '\nSome test suites failed. See the suite output above for details.\n'
  exit 1
fi

printf '\nAll test suites passed.\n'
