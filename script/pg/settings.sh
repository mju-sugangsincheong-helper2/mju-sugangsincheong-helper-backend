#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
section "Important PostgreSQL settings"
query_table "
SELECT name, setting, unit, source
FROM pg_settings
WHERE name IN (
'max_connections','shared_buffers','work_mem','maintenance_work_mem',
'effective_cache_size','autovacuum','autovacuum_naptime',
'autovacuum_vacuum_threshold','autovacuum_vacuum_scale_factor',
'autovacuum_vacuum_cost_delay','autovacuum_vacuum_cost_limit',
'statement_timeout','idle_in_transaction_session_timeout',
'transaction_timeout','track_activities','track_counts',
'shared_preload_libraries'
)
ORDER BY name;
"
