#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

printf '\n=== Backend tests ===\n'
(
  cd "$repo_root/backend"
  ./gradlew test
)

printf '\n=== Frontend tests ===\n'
(
  cd "$repo_root/frontend"
  npm run test:ci
)

printf '\nBackend and frontend tests passed.\n'
