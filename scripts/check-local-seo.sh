#!/usr/bin/env bash

set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
FRONTEND_DIR="${REPO_ROOT}/frontend"
SSR_ENTRY="${FRONTEND_DIR}/dist/frontend/server/server.mjs"

BASE_URL="${BASE_URL:-http://localhost:4000}"
EXPECTED_ORIGIN="${EXPECTED_ORIGIN:-$BASE_URL}"
CURL_CONNECT_TIMEOUT="${CURL_CONNECT_TIMEOUT:-3}"
CURL_MAX_TIME="${CURL_MAX_TIME:-15}"
CURL_RETRY_COUNT="${CURL_RETRY_COUNT:-2}"
SSR_START_TIMEOUT="${SSR_START_TIMEOUT:-20}"
AUTO_START_SSR="${AUTO_START_SSR:-1}"
FAILURES=0
SSR_PID=""
SSR_LOG_FILE=""

if ! command -v curl >/dev/null 2>&1; then
  echo "Missing required command: curl"
  exit 1
fi

if ! command -v node >/dev/null 2>&1; then
  echo "Missing required command: node"
  exit 1
fi

CURL_ARGS=(
  --silent
  --show-error
  --connect-timeout "$CURL_CONNECT_TIMEOUT"
  --max-time "$CURL_MAX_TIME"
  --retry "$CURL_RETRY_COUNT"
  --retry-delay 1
  --retry-connrefused
)

cleanup() {
  if [[ -n "$SSR_PID" ]] && kill -0 "$SSR_PID" >/dev/null 2>&1; then
    kill "$SSR_PID" >/dev/null 2>&1 || true
    wait "$SSR_PID" 2>/dev/null || true
  fi

  if [[ -n "$SSR_LOG_FILE" && -f "$SSR_LOG_FILE" ]]; then
    rm -f "$SSR_LOG_FILE"
  fi
}

trap cleanup EXIT INT TERM

is_base_url_reachable() {
  curl "${CURL_ARGS[@]}" -o /dev/null "${BASE_URL}/"
}

base_url_looks_local() {
  [[ "$BASE_URL" =~ ^http://(localhost|127\.0\.0\.1)(:[0-9]+)?(/.*)?$ ]]
}

base_url_port() {
  if [[ "$BASE_URL" =~ ^https?://[^/:]+:([0-9]+)($|/) ]]; then
    printf '%s\n' "${BASH_REMATCH[1]}"
  elif [[ "$BASE_URL" =~ ^https:// ]]; then
    printf '443\n'
  else
    printf '80\n'
  fi
}

start_local_ssr() {
  local port
  local attempt

  if [[ ! -f "$SSR_ENTRY" ]]; then
    cat <<EOF
Cannot auto-start the frontend SSR server because the build is missing:
  ${SSR_ENTRY}

Run:
  cd ${FRONTEND_DIR}
  npm run build
EOF
    return 1
  fi

  SSR_LOG_FILE="$(mktemp -t check-local-seo-ssr.XXXXXX.log)"
  port="$(base_url_port)"

  (
    cd "$FRONTEND_DIR" || exit 1
    PORT="$port" node "$SSR_ENTRY"
  ) >"$SSR_LOG_FILE" 2>&1 &
  SSR_PID=$!

  for ((attempt = 1; attempt <= SSR_START_TIMEOUT; attempt++)); do
    if is_base_url_reachable; then
      echo "Started local frontend SSR on ${BASE_URL}"
      return 0
    fi

    if ! kill -0 "$SSR_PID" >/dev/null 2>&1; then
      echo "Local frontend SSR exited before becoming ready."
      if [[ -s "$SSR_LOG_FILE" ]]; then
        echo "SSR log:"
        sed -n '1,120p' "$SSR_LOG_FILE"
      fi
      return 1
    fi

    sleep 1
  done

  echo "Timed out waiting for the local frontend SSR server on ${BASE_URL}."
  if [[ -s "$SSR_LOG_FILE" ]]; then
    echo "SSR log:"
    sed -n '1,120p' "$SSR_LOG_FILE"
  fi
  return 1
}

ensure_base_url_ready() {
  if is_base_url_reachable; then
    return 0
  fi

  if [[ "$AUTO_START_SSR" == "1" ]] && base_url_looks_local; then
    echo "Local frontend SSR not reachable on ${BASE_URL}, attempting auto-start..."
    if start_local_ssr; then
      return 0
    fi
  fi

  cat <<EOF
Cannot reach ${BASE_URL}.

This script checks the frontend SSR server, not ng serve.
Start it from ${FRONTEND_DIR} with:
  PORT=$(base_url_port) node dist/frontend/server/server.mjs

Then rerun:
  BASE_URL=${BASE_URL} EXPECTED_ORIGIN=${EXPECTED_ORIGIN} bash scripts/check-local-seo.sh
EOF
  return 1
}

if ! ensure_base_url_ready; then
  exit 1
fi

pass() {
  printf 'PASS %s\n' "$1"
}

fail() {
  printf 'FAIL %s\n' "$1"
  FAILURES=$((FAILURES + 1))
}

status_from_headers() {
  awk 'toupper($1) ~ /^HTTP\// { code = $2 } END { print code }' "$1"
}

location_from_headers() {
  awk 'BEGIN { IGNORECASE = 1 }
    /^Location:/ {
      sub(/\r$/, "", $0)
      sub(/^Location:[[:space:]]*/, "", $0)
      print
      exit
    }' "$1"
}

header_contains() {
  local file="$1"
  local header_name="$2"
  local expected="$3"

  awk -v name="$header_name" -v expected="$expected" '
    BEGIN {
      IGNORECASE = 1
      found = 0
    }
    {
      line = $0
      sub(/\r$/, "", line)
      split(line, parts, ":")
      if (tolower(parts[1]) == tolower(name)) {
        value = substr(line, length(parts[1]) + 2)
        if (index(tolower(value), tolower(expected)) > 0) {
          found = 1
          exit
        }
      }
    }
    END { exit(found ? 0 : 1) }
  ' "$file"
}

fetch_headers() {
  local path="$1"
  shift
  local header_file
  header_file="$(mktemp)"
  if ! curl "${CURL_ARGS[@]}" -D "$header_file" -o /dev/null "$@" "${BASE_URL}${path}"; then
    rm -f "$header_file"
    return 1
  fi
  printf '%s\n' "$header_file"
}

assert_redirect() {
  local description="$1"
  local path="$2"
  local expected_status="$3"
  local expected_location="$4"
  shift 4

  local header_file
  header_file="$(fetch_headers "$path" "$@")" || {
    fail "${description}: request failed"
    return
  }

  local status
  local location
  status="$(status_from_headers "$header_file")"
  location="$(location_from_headers "$header_file")"

  if [[ "$status" == "$expected_status" && "$location" == "$expected_location" ]]; then
    pass "${description}: ${status} -> ${location}"
  else
    fail "${description}: expected ${expected_status} -> ${expected_location}, got ${status:-<none>} -> ${location:-<none>}"
  fi

  rm -f "$header_file"
}

assert_header_contains() {
  local description="$1"
  local path="$2"
  local header_name="$3"
  local expected="$4"
  shift 4

  local header_file
  header_file="$(fetch_headers "$path" "$@")" || {
    fail "${description}: request failed"
    return
  }

  if header_contains "$header_file" "$header_name" "$expected"; then
    pass "${description}: ${header_name} contains ${expected}"
  else
    fail "${description}: ${header_name} does not contain ${expected}"
  fi

  rm -f "$header_file"
}

assert_status() {
  local description="$1"
  local path="$2"
  local expected_status="$3"

  local header_file
  header_file="$(fetch_headers "$path")" || {
    fail "${description}: request failed"
    return
  }

  local status
  status="$(status_from_headers "$header_file")"
  if [[ "$status" == "$expected_status" ]]; then
    pass "${description}: ${status}"
  else
    fail "${description}: expected ${expected_status}, got ${status:-<none>}"
  fi

  rm -f "$header_file"
}

assert_html_contains() {
  local description="$1"
  local path="$2"
  local expected="$3"
  local html

  if ! html="$(curl "${CURL_ARGS[@]}" "${BASE_URL}${path}")"; then
    fail "${description}: request failed"
    return
  fi

  if [[ "$html" == *"$expected"* ]]; then
    pass "${description}"
  else
    fail "${description}: missing ${expected}"
  fi
}

echo "Checking local SEO behavior against ${BASE_URL}"

assert_redirect "root uses German browser preference" "/" "302" "/de" \
  -H "Accept-Language: de-CH,de;q=0.9,en;q=0.8"
assert_redirect "root uses French browser preference" "/" "302" "/fr" \
  -H "Accept-Language: fr-CH,fr;q=0.9,en;q=0.8"
assert_redirect "root uses English browser preference" "/" "302" "/en" \
  -H "Accept-Language: en-GB,en;q=0.9"
assert_header_contains "root varies on browser language" "/" "Vary" "Accept-Language" \
  -H "Accept-Language: de-CH,de;q=0.9,en;q=0.8"
assert_header_contains "root varies on crawler detection" "/" "Vary" "User-Agent" \
  -H "Accept-Language: de-CH,de;q=0.9,en;q=0.8"
assert_redirect "root without browser language redirects to stable default" "/" "308" "/it"
assert_redirect "root for Googlebot redirects to stable default" "/" "308" "/it" \
  -H "User-Agent: Googlebot/2.1 (+http://www.google.com/bot.html)"

assert_redirect "about redirects to default language" "/about" "308" "/it/about"
assert_redirect "contact redirects to default language" "/contact" "308" "/it/contact"
assert_redirect "privacy redirects to default language" "/privacy" "308" "/it/privacy"
assert_redirect "terms redirects to default language" "/terms" "308" "/it/terms"
assert_redirect "calculator redirects to canonical basic path" "/calculator" "308" "/it/calculator/basic"
assert_redirect "shop redirects to default language" "/shop" "308" "/it/shop"

assert_redirect "language calculator alias redirects to basic" "/de/calculator" "308" "/de/calculator/basic"
assert_redirect "legacy shop product alias redirects to canonical product route" \
  "/de/shop/zubehor/demo-produkt" "308" "/de/shop/p/demo-produkt"

assert_status "prefixed about returns 200" "/de/about" "200"
assert_status "prefixed contact returns 200" "/fr/contact" "200"
assert_status "prefixed shop returns 200" "/en/shop" "200"

assert_html_contains "German about exposes regional html lang" "/de/about" 'lang="de-CH"'
assert_html_contains "German about exposes canonical URL" "/de/about" \
  "<link rel=\"canonical\" href=\"${EXPECTED_ORIGIN}/de/about\""
assert_html_contains "German about exposes Swiss hreflang alternate" "/de/about" \
  'hreflang="it-CH"'
assert_html_contains "German about exposes x-default alternate" "/de/about" \
  'hreflang="x-default"'

if (( FAILURES > 0 )); then
  printf '\nSEO checks failed: %d\n' "$FAILURES"
  exit 1
fi

printf '\nAll SEO checks passed.\n'
