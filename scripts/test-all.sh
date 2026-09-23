#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
report_dir="$(mktemp -d "${TMPDIR:-/tmp}/printcalc-tests.XXXXXX")"
trap 'rm -rf "$report_dir"' EXIT

results=()
failed=0
total_start=$SECONDS

run_suite() {
  local name="$1"
  local log_file="$2"
  shift 2
  local suite_start=$SECONDS
  local status=0

  printf '\n=== %s ===\n' "$name"
  if "$@" 2>&1 | tee "$log_file"; then
    status=0
  else
    status=$?
    failed=1
  fi

  results+=("$name|$status|$((SECONDS - suite_start))")
}

format_duration() {
  local seconds="$1"
  printf '%dm %02ds' "$((seconds / 60))" "$((seconds % 60))"
}

backend_log="$report_dir/backend.log"
frontend_log="$report_dir/frontend.log"
e2e_log="$report_dir/e2e.log"

run_suite "Backend" "$backend_log" bash -c 'cd "$1/backend" && ./gradlew test' _ "$repo_root"
run_suite "Frontend" "$frontend_log" bash -c 'cd "$1/frontend" && npm run test:ci' _ "$repo_root"
run_suite "Headless browser" "$e2e_log" bash "$repo_root/scripts/e2e/run.sh" fullstack

printf '\n=== Test recap ===\n'
for result in "${results[@]}"; do
  IFS='|' read -r name status duration <<< "$result"
  if [[ "$name" == "Backend" ]]; then
    counts="$(python3 - "$repo_root/backend/build/test-results/test" <<'PY'
import glob
import sys
import xml.etree.ElementTree as ET

files = glob.glob(sys.argv[1] + '/TEST-*.xml')
totals = {'tests': 0, 'failures': 0, 'errors': 0, 'skipped': 0}
for filename in files:
    root = ET.parse(filename).getroot()
    for key in totals:
        totals[key] += int(root.attrib.get(key, 0))
passed = totals['tests'] - totals['failures'] - totals['errors'] - totals['skipped']
print(f"{passed} passed, {totals['failures'] + totals['errors']} failed, {totals['skipped']} skipped, {totals['tests']} total")
PY
    )"
  elif [[ "$name" == "Frontend" ]]; then
    success_count="$(sed -nE 's/.*TOTAL: ([0-9]+) SUCCESS.*/\1/p' "$frontend_log" | tail -1)"
    failed_count="$(sed -nE 's/.*TOTAL:.* ([0-9]+) FAILED.*/\1/p' "$frontend_log" | tail -1)"
    skipped_count="$(sed -nE 's/.*TOTAL:.* ([0-9]+) SKIPPED.*/\1/p' "$frontend_log" | tail -1)"
    success_count="${success_count:-0}"
    failed_count="${failed_count:-0}"
    skipped_count="${skipped_count:-0}"
    counts="$success_count passed, $failed_count failed, $skipped_count skipped, $((success_count + failed_count + skipped_count)) total"
    if ! grep -q 'TOTAL:' "$frontend_log"; then
      counts="test counts unavailable (Karma did not print a TOTAL summary)"
    fi
  else
    counts="$(node - "$repo_root/frontend/test-results/results.json" <<'JS'
const fs = require('node:fs');
const filename = process.argv[2];
try {
  const report = JSON.parse(fs.readFileSync(filename, 'utf8'));
  const stats = report.stats ?? {};
  const passed = Number(stats.expected ?? 0);
  const failed = Number(stats.unexpected ?? 0);
  const skipped = Number(stats.skipped ?? 0);
  const flaky = Number(stats.flaky ?? 0);
  console.log(`${passed} passed, ${failed} failed, ${skipped} skipped, ${flaky} flaky, ${passed + failed + skipped + flaky} total`);
} catch {
  console.log('test counts unavailable (Playwright JSON report was not produced)');
}
JS
    )"
  fi

  if (( status == 0 )); then
    printf 'PASS  %-18s %s  (%s)\n' "$name" "$counts" "$(format_duration "$duration")"
  else
    printf 'FAIL  %-18s %s  (%s, exit code %s)\n' "$name" "$counts" "$(format_duration "$duration")" "$status"
  fi
done

printf 'Total elapsed: %s\n' "$(format_duration "$((SECONDS - total_start))")"
if (( failed )); then
  printf 'One or more test suites failed.\n'
  exit 1
fi
printf 'All test suites passed.\n'
