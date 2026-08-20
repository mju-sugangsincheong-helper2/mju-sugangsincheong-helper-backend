#!/usr/bin/env bash
set -euo pipefail

# Project-specific PostgreSQL operations.
# No additional environment variables are required.
# The script reads ../.env and uses the container names from docker-compose-prod.yml.

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
ENV_FILE="$PROJECT_DIR/.env"

[[ -f "$ENV_FILE" ]] || {
  echo "ERROR: .env not found: $ENV_FILE" >&2
  exit 1
}

# shellcheck disable=SC1090
set -a
source "$ENV_FILE"
set +a

DB_CONTAINER="${DB_CONTAINER:-mju-sugangsincheong-helper-db}"
DB_HOST="${DB_HOST:-db}"
DB_PORT="${DB_PORT:-5432}"

: "${DB_URL:?DB_URL is required in .env}"
: "${DB_USERNAME:?DB_USERNAME is required in .env}"
: "${DB_PASSWORD:?DB_PASSWORD is required in .env}"

# Extract database name from:
# jdbc:postgresql://db:5432/mjusugangsincheonghelperdb_prod
DB_NAME="$(printf '%s\n' "$DB_URL" | sed -E 's#^jdbc:postgresql://[^/]+/([^?]+).*#\1#')"

[[ "$DB_NAME" != "$DB_URL" && -n "$DB_NAME" ]] || {
  echo "ERROR: Cannot extract database name from DB_URL" >&2
  exit 1
}

command -v docker >/dev/null 2>&1 || {
  echo "ERROR: docker is required" >&2
  exit 1
}

docker inspect "$DB_CONTAINER" >/dev/null 2>&1 || {
  echo "ERROR: PostgreSQL container not found: $DB_CONTAINER" >&2
  echo "Run: docker compose -f docker-compose-prod.yml ps" >&2
  exit 1
}

psql() {
  docker exec -i \
    -e "PGPASSWORD=$DB_PASSWORD" \
    "$DB_CONTAINER" \
    psql -X -v ON_ERROR_STOP=1 \
      -U "$DB_USERNAME" \
      -d "$DB_NAME" \
      "$@"
}

query() {
  psql -P pager=off -c "$1"
}

query_table() {
  psql -P pager=off -P border=2 -P null='-' -c "$1"
}

section() {
  printf '\n\033[1;36m%s\033[0m\n' "$1"
  printf '%s\n' '────────────────────────────────────────────────────────────'
}

warn() { printf '\033[1;33mWARN:\033[0m %s\n' "$*" >&2; }
ok()   { printf '\033[1;32mOK:\033[0m %s\n' "$*"; }
bad()  { printf '\033[1;31mCRITICAL:\033[0m %s\n' "$*" >&2; }

show_context() {
  echo "container : $DB_CONTAINER"
  echo "database  : $DB_NAME"
  echo "user      : $DB_USERNAME"
}
