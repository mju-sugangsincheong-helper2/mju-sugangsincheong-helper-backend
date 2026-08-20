#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"

section "PGMQ extension"
query_table "
SELECT
  extname,
  extversion
FROM pg_extension
WHERE extname = 'pgmq';
"

section "PGMQ queues"
if query_table "
SELECT *
FROM pgmq.list_queues()
ORDER BY queue_name;
"; then
    :
else
    echo "PGMQ queue listing unavailable (extension/function may be absent)."
fi
