#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"

section "Important PostgreSQL settings"

query_table "
SELECT
  name,
  setting,
  unit,
  source,
  sourcefile
FROM pg_settings
WHERE name IN (
  'max_connections',
  'shared_buffers',
  'work_mem',
  'maintenance_work_mem',
  'effective_cache_size',
  'random_page_cost',
  'effective_io_concurrency',
  'autovacuum',
  'autovacuum_naptime',
  'autovacuum_vacuum_threshold',
  'autovacuum_vacuum_scale_factor',
  'autovacuum_analyze_scale_factor',
  'autovacuum_vacuum_cost_delay',
  'autovacuum_vacuum_cost_limit',
  'statement_timeout',
  'lock_timeout',
  'idle_in_transaction_session_timeout',
  'transaction_timeout',
  'track_io_timing',
  'shared_preload_libraries',
  'cron.database_name'
)
ORDER BY name;
"
