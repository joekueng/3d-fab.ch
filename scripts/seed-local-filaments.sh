#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -n "${DOCKER_CONTEXT:-}" ]]; then
  docker_endpoint="$(docker context inspect "$DOCKER_CONTEXT" --format '{{.Endpoints.docker.Host}}')"
else
  docker_endpoint="${DOCKER_HOST:-$(docker context inspect --format '{{.Endpoints.docker.Host}}')}"
fi
if [[ "$docker_endpoint" != unix://* ]]; then
  echo 'Refusing to seed through a remote Docker endpoint. Use local Docker Desktop.' >&2
  exit 2
fi

compose() {
  docker compose --project-name print-calculator -f "$repo_root/docker-compose.yml" "$@"
}

db_container="$(compose ps -q db)"
if [[ -z "$db_container" ]] || [[ "$(docker inspect --format '{{.Name}}' "$db_container")" != /print-calculator-db ]]; then
  echo 'Local database is unavailable. Start the local environment with ./start.sh first.' >&2
  exit 2
fi
if [[ "$(compose exec -T db psql -X -U printcalc -d printcalc -tAc 'select current_database()')" != printcalc ]]; then
  echo 'Refusing to seed an unexpected database.' >&2
  exit 2
fi

compose exec -T db psql -X -v ON_ERROR_STOP=1 -U printcalc -d printcalc \
  < "$repo_root/scripts/local-filaments.sql"
