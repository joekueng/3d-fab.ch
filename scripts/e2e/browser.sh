#!/usr/bin/env bash
set -euo pipefail

suite="${1:?Missing suite}"
proxy="${2:?Missing disposable proxy container}"
case "$suite" in
  fullstack) command=(npm run e2e:full) ;;
  ui-states) command=(npm run e2e:states) ;;
  *) echo 'Container browsers support fullstack and ui-states only.' >&2; exit 2 ;;
esac
if [[ "$suite" == fullstack && -n "${E2E_TEST_GREP:-}" ]]; then
  command+=(-- --grep "$E2E_TEST_GREP")
fi

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
browser_dir="$(mktemp -d "${TMPDIR:-/tmp}/printcalc-browser.XXXXXX")"
browser_container=""
cleanup() {
  result=$?
  trap - EXIT INT TERM
  if [[ -n "$browser_container" ]]; then
    mkdir -p "$repo_root/frontend/test-results" "$repo_root/frontend/playwright-report"
    docker cp "$browser_container:/app/test-results/." "$repo_root/frontend/test-results/" 2>/dev/null || true
    docker cp "$browser_container:/app/playwright-report/." "$repo_root/frontend/playwright-report/" 2>/dev/null || true
    docker rm -f "$browser_container" >/dev/null 2>&1 || true
  fi
  rm -rf "$browser_dir"
  exit "$result"
}
trap cleanup EXIT INT TERM

# Build context and reports travel through the Docker API, never host bind mounts.
docker build --iidfile "$browser_dir/image" \
  -f "$repo_root/scripts/e2e/browser.Dockerfile" "$repo_root/frontend"
env_args=()
while IFS= read -r name; do
  case "$name" in
    E2E_*|CI) env_args+=(--env "$name") ;;
  esac
done < <(compgen -e)
browser_container="$(docker create --network "container:$proxy" --shm-size 1g \
  "${env_args[@]}" "$(cat "$browser_dir/image")" \
  bash -c 'npm run e2e:typecheck && exec "$@"' bash "${command[@]}")"
docker start --attach "$browser_container"
exit "$(docker inspect -f '{{.State.ExitCode}}' "$browser_container")"
