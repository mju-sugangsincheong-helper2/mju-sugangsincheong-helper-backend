#!/usr/bin/env bash
set -uo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
ENV_FILE="$PROJECT_DIR/.env"

die() { echo "ERROR: $*" >&2; exit 1; }
[[ -f "$ENV_FILE" ]] || die ".env not found: $ENV_FILE"
command -v docker >/dev/null 2>&1 || die "docker command not found"

# NEVER source .env: Firebase private key contains literal \n and is not shell-safe.
env_value() {
    local key="$1"
    awk -v k="$key" '
        /^[[:space:]]*#/ { next }
        {
            line=$0
            sub(/^[[:space:]]*/, "", line)
            if (index(line, k "=") == 1) {
                sub("^[^=]*=", "", line)
                print line
                exit
            }
        }
    ' "$ENV_FILE"
}

DB_URL="$(env_value DB_URL)"
DB_USERNAME="$(env_value DB_USERNAME)"
DB_PASSWORD="$(env_value DB_PASSWORD)"

[[ -n "$DB_URL" ]] || die "DB_URL missing in .env"
[[ -n "$DB_USERNAME" ]] || die "DB_USERNAME missing in .env"
[[ -n "$DB_PASSWORD" ]] || die "DB_PASSWORD missing in .env"

DB_HOST="$(printf '%s\n' "$DB_URL" | sed -nE 's#^jdbc:postgresql://([^:/?]+)(:[0-9]+)?/.*#\1#p')"
DB_PORT="$(printf '%s\n' "$DB_URL" | sed -nE 's#^jdbc:postgresql://[^:/?]+:([0-9]+)/.*#\1#p')"
DB_NAME="$(printf '%s\n' "$DB_URL" | sed -nE 's#^jdbc:postgresql://[^/]+/([^?]+).*#\1#p')"
DB_PORT="${DB_PORT:-5432}"

[[ -n "$DB_HOST" ]] || die "Cannot parse DB_HOST from DB_URL"
[[ -n "$DB_NAME" ]] || die "Cannot parse DB_NAME from DB_URL"

# Do not assume image == postgres:*.
# Do not assume container == db.
# Find a running container that actually accepts the credentials from .env.
container_candidates() {
    local cid name
    while IFS= read -r cid; do
        [[ -n "$cid" ]] || continue
        name="$(docker inspect --format '{{.Name}}' "$cid" 2>/dev/null | sed 's#^/##')"
        case "$name" in
            *-db|*_db|db) echo "$cid" ;;
        esac
    done < <(docker ps -q)

    while IFS= read -r cid; do
        [[ -n "$cid" ]] || continue
        if docker inspect --format '{{json .NetworkSettings.Ports}}' "$cid" 2>/dev/null |
           grep -qE '"5432/tcp"'; then
            echo "$cid"
        fi
    done < <(docker ps -q)

    docker ps -q
}

find_pg_container() {
    local cid
    declare -A seen=()

    while IFS= read -r cid; do
        [[ -n "$cid" ]] || continue
        [[ -n "${seen[$cid]+x}" ]] && continue
        seen["$cid"]=1

        docker exec "$cid" sh -c 'command -v psql >/dev/null 2>&1' >/dev/null 2>&1 || continue

        if docker exec -i \
            -e "PGPASSWORD=$DB_PASSWORD" \
            "$cid" \
            psql -X -v ON_ERROR_STOP=1 -Atqc "SELECT 1" \
            -U "$DB_USERNAME" -d "$DB_NAME" >/dev/null 2>&1; then
            echo "$cid"
            return 0
        fi
    done < <(container_candidates)

    return 1
}

PG_CONTAINER="$(find_pg_container || true)"

[[ -n "$PG_CONTAINER" ]] || {
    echo "ERROR: PostgreSQL container could not be identified." >&2
    echo >&2
    docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}' >&2
    exit 1
}

PG_CONTAINER_NAME="$(docker inspect --format '{{.Name}}' "$PG_CONTAINER" | sed 's#^/##')"

psql() {
    docker exec -i \
        -e "PGPASSWORD=$DB_PASSWORD" \
        "$PG_CONTAINER" \
        psql -X -v ON_ERROR_STOP=1 \
        -U "$DB_USERNAME" -d "$DB_NAME" "$@"
}

query_table() {
    psql -P pager=off -P border=2 -P null='-' -c "$1"
}

section() {
    printf '\n################################################################\n# %s\n################################################################\n' "$1"
}

show_context() {
    printf '%-16s %s\n' "DB_HOST" "$DB_HOST"
    printf '%-16s %s\n' "DB_PORT" "$DB_PORT"
    printf '%-16s %s\n' "DB_NAME" "$DB_NAME"
    printf '%-16s %s\n' "DB_USER" "$DB_USERNAME"
    printf '%-16s %s\n' "CONTAINER" "$PG_CONTAINER_NAME"
}
