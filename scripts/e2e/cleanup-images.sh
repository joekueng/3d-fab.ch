#!/usr/bin/env bash
set -euo pipefail

mode="${1:---list}"
if [[ $# -gt 1 || ( "$mode" != --list && "$mode" != --delete ) ]]; then
  echo 'Usage: bash scripts/e2e/cleanup-images.sh [--list|--delete]' >&2
  exit 2
fi

# Run during a pause in E2E builds: a newly built image may not have a container yet.
images="$(docker image ls --format '{{.Repository}}:{{.Tag}}')"
failed=0
while IFS= read -r tag; do
  [[ "$tag" =~ ^e2e-printcalc-e2e-[a-z0-9-]+-(backend|frontend|proxy):latest$ ]] || continue
  image_id="$(docker image inspect --format '{{.Id}}' "$tag")"
  containers="$(docker ps -aq --filter "ancestor=$image_id")"
  if [[ -n "$containers" ]]; then
    printf 'IN USE (running or stopped container): %s\n' "$tag"
    continue
  fi
  if [[ "$mode" == --list ]]; then
    printf 'REMOVABLE: %s\n' "$tag"
  elif ! docker image rm "$tag"; then
    printf 'Removal failed: %s\n' "$tag" >&2
    failed=1
  fi
done <<< "$images"
exit "$failed"
