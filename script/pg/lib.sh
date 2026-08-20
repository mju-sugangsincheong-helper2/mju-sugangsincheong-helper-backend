#!/usr/bin/env bash
set -euo pipefail

# PostgreSQL operational inspection helpers.
# Override these environment variables when needed.
PG_CONTAINER="${PG_CONTAINER:-db}"
PG_DATABASE="${PG_DATABASE:-postgres}"
PG_USER="${PG_USER:-postgres}"
PG_PORT="${PG_PORT:-5432}"

die() { echo "ERROR: $*" >&2; exit 1; }
command -v docker >/dev/null 2>&1 || die "docker is required"

psql_exec() {
  docker exec -i \
    -e "PGDATABASE=$PG_DATABASE" \
    -e "PGUSER=$PG_USER" \
    -e "PGPORT=$PG_PORT" \
    "$PG_CONTAINER" psql -X -v ON_ERROR_STOP=1 "$@"
}

query() {
  psql_exec -P pager=off -c "$1"
}

query_table() {
  psql_exec -P pager=off -P border=2 -P null='-' -c "$1"
}

section() {
  printf '\n\033[1m%s\033[0m\n' "$1"
  printf '%s\n' '────────────────────────────────────────────────────────────'
}

warn() { printf '\033[33mWARN:\033[0m %s\n' "$*"; }
ok()   { printf '\033[32mOK:\033[0m %s\n' "$*"; }
bad()  { printf '\033[31mCRITICAL:\033[0m %s\n' "$*"; }

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
