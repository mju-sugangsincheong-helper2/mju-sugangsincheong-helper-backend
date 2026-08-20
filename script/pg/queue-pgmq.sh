#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"

section "PGMQ extension"

query "
SELECT extname, extversion
FROM pg_extension
WHERE extname = 'pgmq';
"

section "PGMQ queues"

query_table "
SELECT *
FROM pgmq.list_queues()
ORDER BY queue_name;
" || warn "pgmq.list_queues() unavailable."

section "PGMQ queue metrics"

# PGMQ versions differ in available helper functions.
# Inspect the queue list first rather than assuming a version-specific API.
query "
SELECT nspname AS schema_name, proname AS function_name
FROM pg_proc
JOIN pg_namespace ON pg_namespace.oid = pg_proc.pronamespace
WHERE nspname = 'pgmq'
ORDER BY proname;
" || true
