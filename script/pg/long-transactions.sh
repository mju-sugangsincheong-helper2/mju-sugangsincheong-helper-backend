#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
section "Long-running transactions"
query_table "
SELECT pid, usename, application_name, state,
       now() - xact_start AS transaction_age,
       now() - query_start AS query_age,
       backend_xmin,
       LEFT(query, 200) AS query
FROM pg_stat_activity
WHERE datname = current_database()
  AND xact_start IS NOT NULL
ORDER BY xact_start;
"
