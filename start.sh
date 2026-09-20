#!/usr/bin/env bash

set -u

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOCAL_PIDS=()
LOCAL_NAMES=()

log() {
  printf '[dev] %s\n' "$*"
}

fail() {
  printf '[dev] Error: %s\n' "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

docker_is_ready() {
  docker info >/dev/null 2>&1
}

ensure_docker() {
  if docker_is_ready; then
    return
  fi

  if [ "$(uname -s)" = "Darwin" ] && command -v open >/dev/null 2>&1; then
    log "Docker is not running; opening Docker Desktop..."
    open -a Docker || fail "Could not open Docker Desktop."

    attempt=0
    while ! docker_is_ready; do
      attempt=$((attempt + 1))
      if [ "$attempt" -ge 120 ]; then
        fail "Docker did not become ready within 120 seconds."
      fi
      sleep 1
    done
    return
  fi

  fail "Docker is not running. Start the Docker daemon and retry."
}

wait_for_postgres() {
  log "Waiting for PostgreSQL..."
  attempt=0
  until docker compose exec -T db pg_isready -U printcalc -d printcalc >/dev/null 2>&1; do
    attempt=$((attempt + 1))
    if [ "$attempt" -ge 60 ]; then
      fail "PostgreSQL did not become ready within 60 seconds."
    fi
    sleep 1
  done
}

wait_for_clamav() {
  if ! command -v nc >/dev/null 2>&1; then
    log "ClamAV was started; readiness check skipped because nc is unavailable."
    return
  fi

  log "Waiting for ClamAV (the first start can take a while)..."
  attempt=0
  until nc -z 127.0.0.1 3310 >/dev/null 2>&1; do
    attempt=$((attempt + 1))
    if [ "$attempt" -ge 180 ]; then
      fail "ClamAV did not become ready within 180 seconds. Check: docker compose logs clamav"
    fi
    sleep 1
  done
}

cleanup() {
  status=$?
  trap - EXIT INT TERM

  if [ "${#LOCAL_PIDS[@]}" -gt 0 ]; then
    log "Stopping local services..."
    for pid in "${LOCAL_PIDS[@]}"; do
      kill "$pid" >/dev/null 2>&1 || true
    done
    for pid in "${LOCAL_PIDS[@]}"; do
      wait "$pid" >/dev/null 2>&1 || true
    done
  fi

  log "PostgreSQL and ClamAV remain running in Docker. Stop them with: docker compose down"
  exit "$status"
}

start_local_service() {
  name="$1"
  directory="$2"
  shift 2

  log "Starting $name..."
  (
    cd "$directory" || exit 1
    exec "$@"
  ) &
  LOCAL_PIDS+=("$!")
  LOCAL_NAMES+=("$name")
}

require_command docker
require_command java
require_command npm
require_command python3

cd "$PROJECT_ROOT" || fail "Could not enter project directory."
ensure_docker

log "Starting PostgreSQL and ClamAV containers if needed..."
docker compose up -d db clamav || fail "Could not start Docker services."
wait_for_postgres
wait_for_clamav

trap cleanup EXIT INT TERM

log "Starting backend with the local Spring profile and ClamAV on localhost..."
(
  cd "$PROJECT_ROOT/backend" || exit 1
  export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-local}"
  export CLAMAV_ENABLED="${CLAMAV_ENABLED:-true}"
  export CLAMAV_HOST="${CLAMAV_HOST:-localhost}"
  exec ./gradlew bootRun
) &
LOCAL_PIDS+=("$!")
LOCAL_NAMES+=("backend")

start_local_service "frontend" "$PROJECT_ROOT/frontend" npm start
start_local_service "image server" "$PROJECT_ROOT/frontend" python3 -m http.server 8081

log "Services are starting:"
log "  frontend:     http://localhost:4200"
log "  backend:      http://localhost:8000"
log "  image server: http://localhost:8081"
log "Press Ctrl+C to stop the three local services."

while true; do
  index=0
  while [ "$index" -lt "${#LOCAL_PIDS[@]}" ]; do
    pid="${LOCAL_PIDS[$index]}"
    if ! kill -0 "$pid" >/dev/null 2>&1; then
      wait "$pid"
      status=$?
      fail "${LOCAL_NAMES[$index]} stopped unexpectedly with status $status."
    fi
    index=$((index + 1))
  done
  sleep 1
done
