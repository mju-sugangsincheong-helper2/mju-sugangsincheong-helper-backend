#!/usr/bin/env bash
set -uo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
ENV_FILE="$PROJECT_DIR/.env"

die() { echo "ERROR: $*" >&2; exit 1; }

[[ -f "$ENV_FILE" ]] || die ".env not found: $ENV_FILE"
command -v docker >/dev/null 2>&1 || die "docker command not found"

# IMPORTANT:
# Do NOT `source .env`.
# The Firebase private key contains shell-sensitive characters.
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

# Find the actual running PostgreSQL container.
# No compose filename and no container-name assumption are required.
# Prefer POSTGRES_USER + POSTGRES_DB, then fall back to a container
# running postgres/postmaster.
find_pg_container() {
    local cid envs image
    while IFS= read -r cid; do
        [[ -n "$cid" ]] || continue

        envs="$(docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$cid" 2>/dev/null || true)"
        if printf '%s\n' "$envs" | grep -Fxq "POSTGRES_USER=$DB_USERNAME" &&
           printf '%s\n' "$envs" | grep -Fxq "POSTGRES_DB=$DB_NAME"; then
            echo "$cid"
            return 0
        fi
    done < <(docker ps -q)

    while IFS= read -r cid; do
        [[ -n "$cid" ]] || continue
        image="$(docker inspect --format '{{.Config.Image}}' "$cid" 2>/dev/null || true)"
        case "$image" in
            postgres|postgres:*|*/postgres|*/postgres:*)
                echo "$cid"
                return 0
                ;;
        esac
    done < <(docker ps -q)

    return 1
}

PG_CONTAINER="$(find_pg_container || true)"
[[ -n "$PG_CONTAINER" ]] || die "Running PostgreSQL container not found. Check: docker ps"

PG_CONTAINER_NAME="$(docker inspect --format '{{.Name}}' "$PG_CONTAINER" 2>/dev/null | sed 's#^/##')"

psql() {
    docker exec -i \
        -e "PGPASSWORD=$DB_PASSWORD" \
        "$PG_CONTAINER" \
        psql -X -v ON_ERROR_STOP=1 \
        -U "$DB_USERNAME" \
        -d "$DB_NAME" "$@"
}

query() {
    psql -P pager=off -c "$1"
}

query_table() {
    psql -P pager=off -P border=2 -P null='-' -c "$1"
}

section() {
    printf '\n################################################################\n'
    printf '# %s\n' "$1"
    printf '################################################################\n'
}

show_context() {
    printf '%-16s %s\n' "DB_HOST" "$DB_HOST"
    printf '%-16s %s\n' "DB_PORT" "$DB_PORT"
    printf '%-16s %s\n' "DB_NAME" "$DB_NAME"
    printf '%-16s %s\n' "DB_USER" "$DB_USERNAME"
    printf '%-16s %s\n' "CONTAINER" "$PG_CONTAINER_NAME"
}
