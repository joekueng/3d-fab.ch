#!/usr/bin/env bash
set -euo pipefail

# Explicit project selection protects other jobs sharing the Unraid Docker daemon.
mode=--list
project=""
case "${1:-}" in
  --project) project="${2:?Missing project}"; shift 2 ;;
  --project-file)
    manifest="${2:?Missing project file}"; shift 2
    [[ -f "$manifest" ]] || exit 0
    project="$(cat "$manifest")"
    ;;
  --list|'') ;;
  *) echo 'Usage: cleanup-stack.sh [--list | --project NAME [--delete] | --project-file FILE [--delete]]' >&2; exit 2 ;;
esac
if [[ $# -gt 0 ]]; then
  mode="$1"; shift
fi
[[ $# -eq 0 && ( "$mode" == --list || "$mode" == --delete ) ]] || exit 2
if [[ -z "$project" ]]; then
  [[ "$mode" == --list ]] || { echo 'Deletion requires an explicit E2E project.' >&2; exit 2; }
  # Include volumes/networks so a partially removed stack is still discoverable.
  container_labels="$(docker ps -a --filter label=com.docker.compose.project --format '{{.Label "com.docker.compose.project"}}')"
  volume_labels="$(docker volume ls --filter label=com.docker.compose.project --format '{{.Labels}}')"
  network_labels="$(docker network ls --filter label=com.docker.compose.project --format '{{.Labels}}')"
  printf '%s\n' "$container_labels" "$volume_labels" "$network_labels" | \
    tr ',' '\n' | sed 's/^com.docker.compose.project=//' | \
    sed -n '/^e2e-printcalc-e2e-[a-z0-9][a-z0-9]*$/p' | sort -u
  exit 0
fi
[[ "$project" =~ ^e2e-printcalc-e2e-[a-z0-9]+$ ]] || {
  echo "Refusing non-harness project: $project" >&2; exit 2;
}
filter="label=com.docker.compose.project=$project"
containers="$(docker ps -aq --filter "$filter")"
volumes="$(docker volume ls -q --filter "$filter")"
networks="$(docker network ls -q --filter "$filter")"
printf 'Project: %s\nContainers:\n%s\nVolumes:\n%s\nNetworks:\n%s\n' "$project" "$containers" "$volumes" "$networks"
[[ "$mode" == --delete ]] || exit 0

failed=0
# Browser containers use the proxy network namespace; remove them first.
browser="$(docker ps -aq --filter "$filter" --filter label=ch.3d-fab.e2e.browser=true)"
while IFS= read -r id; do
  [[ -n "$id" ]] || continue
  docker rm -f "$id" || failed=1
done <<< "$browser"
containers="$(docker ps -aq --filter "$filter")"
while IFS= read -r id; do
  [[ -n "$id" ]] || continue
  docker rm -f "$id" || failed=1
done <<< "$containers"
# Volumes and networks are never force-removed; Docker rejects resources still in use.
for kind in volume network; do
  resources="$(docker "$kind" ls -q --filter "$filter")"
  while IFS= read -r id; do
    [[ -n "$id" ]] || continue
    docker "$kind" rm "$id" || failed=1
  done <<< "$resources"
done
for service in backend frontend proxy; do
  tag="$project-$service:latest"
  if docker image inspect "$tag" >/dev/null 2>&1; then
    docker image rm "$tag" || failed=1
  fi
done
exit "$failed"
