#!/usr/bin/env bash
set -eu
backend_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$backend_root"
main_class=com.printcalculator.tools.QuoteTimeCalibration
case "${1:-}" in
  --matrix) main_class=com.printcalculator.tools.QuoteTimeCalibrationMatrix; shift ;;
  --fit) main_class=com.printcalculator.tools.QuoteTimeCalibrationFit; shift ;;
  --report) main_class=com.printcalculator.tools.QuoteTimeCalibrationReport; shift ;;
esac
./gradlew bootJar
workspace="$(mktemp -d "${TMPDIR:-/tmp}/quote_time_calibration.XXXXXX")"
trap 'rm -rf "$workspace"' EXIT
unzip -q build/libs/backend-0.0.1-SNAPSHOT.jar 'BOOT-INF/classes/*' 'BOOT-INF/lib/*' -d "$workspace"
java -cp "$workspace/BOOT-INF/classes:$workspace/BOOT-INF/lib/*" "$main_class" "$@"
