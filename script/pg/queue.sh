#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"

section "pgmq queues"

query_table "
SELECT
  *
FROM pgmq.list_queues()
ORDER BY queue_name;
" 2>/dev/null || warn "pgmq.list_queues() is unavailable or pgmq is not installed."

section "pgmq extension"

query "
SELECT extname, extversion
FROM pg_extension
WHERE extname = 'pgmq';
"
