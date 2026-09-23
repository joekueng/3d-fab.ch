#!/usr/bin/env bash
set -euo pipefail

suite="${1:-fullstack}"
if [[ "$suite" != fullstack && "$suite" != ui-states && "$suite" != headed ]]; then
  echo 'Usage: bash scripts/e2e/run.sh [fullstack|ui-states|headed]' >&2
  exit 2
fi

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
compose_file="$repo_root/docker-compose.e2e.yml"
run_dir="$(mktemp -d "${TMPDIR:-/tmp}/printcalc-e2e.XXXXXX")"
run_id="e2e-$(basename "$run_dir" | tr '[:upper:].' '[:lower:]-')"
export COMPOSE_PROJECT_NAME="$run_id"
export E2E_PORT="$(python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1",0)); print(s.getsockname()[1]); s.close()')"
export E2E_MAIL_PORT="$(python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1",0)); print(s.getsockname()[1]); s.close()')"
export E2E_ADMIN_PASSWORD="$(openssl rand -hex 24)"
export E2E_SESSION_SECRET="$(openssl rand -hex 32)"
export E2E_BASE_URL="http://127.0.0.1:${E2E_PORT}"
export E2E_DISPOSABLE_STACK=1

compose() {
  docker compose --progress quiet --project-name "$run_id" -f "$compose_file" "$@"
}

cleanup() {
  result=$?
  trap - EXIT INT TERM
  mkdir -p "$repo_root/frontend/test-results"
  cp "$run_dir/run.txt" "$repo_root/frontend/test-results/e2e-run.txt" 2>/dev/null || true
  cp "$run_dir/build.log" "$repo_root/frontend/test-results/e2e-build.log" 2>/dev/null || true
  compose logs --no-color > "$repo_root/frontend/test-results/e2e-services.log" 2>&1 || true
  compose down --volumes --remove-orphans >/dev/null 2>&1 || true
  rm -rf "$run_dir"
  exit "$result"
}
trap cleanup EXIT INT TERM

if ! docker info >/dev/null 2>&1; then
  echo 'Docker daemon is unavailable. Start Docker and grant access to its socket.' >&2
  exit 2
fi

printf 'project=%s\nurl=%s\nmail_url=http://127.0.0.1:%s\n' \
  "$run_id" "$E2E_BASE_URL" "$E2E_MAIL_PORT" \
  > "$run_dir/run.txt"
echo "Disposable E2E stack: $run_id at $E2E_BASE_URL (Mailpit on port $E2E_MAIL_PORT)"

compose up --build --detach --wait --wait-timeout 600 2>&1 | tee "$run_dir/build.log"

# The harness only seeds databases created under its own generated Compose project.
db_container="$(compose ps -q db)"
if [[ -z "$db_container" ]] || [[ "$(docker inspect -f '{{ index .Config.Labels "com.docker.compose.project" }}' "$db_container")" != "$run_id" ]]; then
  echo 'Disposable database identity check failed.' >&2
  exit 3
fi
if [[ "$(compose exec -T db psql -U e2e -d printcalc_e2e -tAc 'select current_database()')" != 'printcalc_e2e' ]]; then
  echo 'Refusing to seed a database with an unexpected name.' >&2
  exit 3
fi

compose exec -T db psql -v ON_ERROR_STOP=1 -U e2e -d printcalc_e2e \
  < "$repo_root/scripts/e2e/seed.sql"

export E2E_ORDER_PENDING_ID="$(compose exec -T db psql -U e2e -d printcalc_e2e -tAc \
  "select order_id from orders where customer_email = 'pending-order-chromium-0@example.test'")"
export E2E_ORDER_PENDING_IDS="$(compose exec -T db psql -U e2e -d printcalc_e2e -tAc \
  "select json_object_agg(customer_email, order_id) from orders where customer_email LIKE 'pending-order-%@example.test'")"
export E2E_CAD_SESSION_ID="$(compose exec -T db psql -U e2e -d printcalc_e2e -tAc \
  "select quote_session_id from quote_sessions where notes = 'E2E CAD fixture'")"
export E2E_ORDER_CAD_PENDING_ID="$(compose exec -T db psql -U e2e -d printcalc_e2e -tAc \
  "select order_id from orders where customer_email = 'cad-order@example.test'")"
export E2E_SHOP_PRODUCT_SLUG=e2e-cube
export E2E_SHOP_UNIT_PRICE_CHF=12.50
export E2E_SHOP_TWO_ITEM_TOTAL_CHF=29.00
export E2E_ORDER_CAD_TOTAL_CHF=50.00
if [[ -z "$E2E_ORDER_PENDING_ID" || -z "$E2E_CAD_SESSION_ID" || -z "$E2E_ORDER_CAD_PENDING_ID" ]]; then
  echo 'Required order/CAD fixtures were not created.' >&2
  exit 3
fi

while IFS= read -r relative_path; do
  [[ -n "$relative_path" ]] || continue
  printf 'solid e2e-fixture\nendsolid e2e-fixture\n' | \
    compose exec -T backend sh -c 'mkdir -p "$(dirname "$1")"; cat > "$1"' sh \
      "/app/storage_orders/$relative_path"
done < <(compose exec -T db psql -U e2e -d printcalc_e2e -tAc \
  "select stored_relative_path from order_items where order_id = '$E2E_ORDER_CAD_PENDING_ID'")

printf 'public E2E media\n' | compose exec -T backend sh -c \
  'mkdir -p /app/storage_media/public; cat > /app/storage_media/public/e2e-public-marker.txt'
printf 'private E2E media\n' | compose exec -T backend sh -c \
  'mkdir -p /app/storage_media/private; cat > /app/storage_media/private/e2e-private-marker.txt'

for attempt in $(seq 1 30); do
  if curl --fail --silent --max-time 5 "$E2E_BASE_URL/it" >/dev/null && \
     curl --fail --silent --max-time 5 "$E2E_BASE_URL/api/shop/categories" >/dev/null; then
    break
  fi
  if [[ "$attempt" -eq 30 ]]; then
    echo 'E2E proxy/backend did not become ready within 150 seconds.' >&2
    exit 4
  fi
  sleep 5
done

cd "$repo_root/frontend"
npm run e2e:typecheck
case "$suite" in
  fullstack)
    if [[ -n "${E2E_TEST_GREP:-}" ]]; then
      npm run e2e:full -- --grep "$E2E_TEST_GREP"
    else
      npm run e2e:full
    fi
    ;;
  ui-states) npm run e2e:states ;;
  headed) npm run e2e:local ;;
esac
