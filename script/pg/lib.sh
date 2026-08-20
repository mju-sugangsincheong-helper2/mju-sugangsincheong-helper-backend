#!/usr/bin/env bash
# Common helpers for script/pg.
# IMPORTANT: do not `source .env`.
# The project's .env contains values (Firebase private key) that are not valid
# shell assignments because they contain unquoted spaces.
set -u

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
ENV_FILE="$PROJECT_DIR/.env"

die() {
    echo "ERROR: $*" >&2
    return 1
}

[[ -f "$ENV_FILE" ]] || die ".env not found: $ENV_FILE"

# Read one dotenv assignment without executing the file.
dotenv_get() {
    local key="$1"
    awk -v key="$key" '
        /^[[:space:]]*#/ { next }
        {
            line=$0
            sub(/^[[:space:]]*/, "", line)
            if (line ~ ("^" key "[[:space:]]*=")) {
                sub(("^" key "[[:space:]]*="), "", line)
                print line
                exit
            }
        }
    ' "$ENV_FILE"
}

DB_URL="$(dotenv_get DB_URL)"
DB_USERNAME="$(dotenv_get DB_USERNAME)"
DB_PASSWORD="$(dotenv_get DB_PASSWORD)"

[[ -n "$DB_URL" ]] || die "DB_URL is missing in $ENV_FILE"
[[ -n "$DB_USERNAME" ]] || die "DB_USERNAME is missing in $ENV_FILE"
[[ -n "$DB_PASSWORD" ]] || die "DB_PASSWORD is missing in $ENV_FILE"

# jdbc:postgresql://db:5432/database
DB_HOST="$(printf '%s\n' "$DB_URL" | sed -nE 's#^jdbc:postgresql://([^:/]+)(:[0-9]+)?/.*#\1#p')"
DB_PORT="$(printf '%s\n' "$DB_URL" | sed -nE 's#^jdbc:postgresql://[^:/]+:([0-9]+)/.*#\1#p')"
DB_NAME="$(printf '%s\n' "$DB_URL" | sed -nE 's#^jdbc:postgresql://[^/]+/([^?]+).*#\1#p')"

[[ -n "$DB_HOST" ]] || die "Cannot extract DB host from DB_URL"
[[ -n "$DB_NAME" ]] || die "Cannot extract DB name from DB_URL"

DB_PORT="${DB_PORT:-5432}"

# DB_URL's host is the Docker Compose service name in this project (db).
# Use docker compose exec rather than docker exec so we do not assume the
# generated container name.
if [[ -f "$PROJECT_DIR/compose.yml" ]]; then
    COMPOSE_FILE="$PROJECT_DIR/compose.yml"
elif [[ -f "$PROJECT_DIR/docker-compose.yml" ]]; then
    COMPOSE_FILE="$PROJECT_DIR/docker-compose.yml"
elif [[ -f "$PROJECT_DIR/compose.yaml" ]]; then
    COMPOSE_FILE="$PROJECT_DIR/compose.yaml"
elif [[ -f "$PROJECT_DIR/docker-compose.yaml" ]]; then
    COMPOSE_FILE="$PROJECT_DIR/docker-compose.yaml"
else
    die "Docker Compose file not found in $PROJECT_DIR"
fi

command -v docker >/dev/null 2>&1 || die "docker command not found"

# Verify that the DB service exists. DB_HOST comes directly from DB_URL.
docker compose -f "$COMPOSE_FILE" config --services 2>/dev/null |
    grep -Fxq "$DB_HOST" ||
    die "Docker Compose service '$DB_HOST' from DB_URL was not found"

psql() {
    docker compose -f "$COMPOSE_FILE" exec -T \
        -e "PGPASSWORD=$DB_PASSWORD" \
        "$DB_HOST" \
        psql -X -v ON_ERROR_STOP=1 \
        -U "$DB_USERNAME" -d "$DB_NAME" "$@"
}

query() {
    psql -P pager=off -c "$1"
}

query_table() {
    psql -P pager=off -P border=2 -P null='-' -c "$1"
}

section() {
    printf '\n\033[1;36m%s\033[0m\n' "$1"
    printf '%s\n' '────────────────────────────────────────────────────────────────'
}

show_context() {
    echo "compose   : $COMPOSE_FILE"
    echo "service   : $DB_HOST"
    echo "database  : $DB_NAME"
    echo "user      : $DB_USERNAME"
}
