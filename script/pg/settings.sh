#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
section "Important PostgreSQL settings"
query_table "SELECT name,setting,unit,source FROM pg_settings WHERE name IN ('max_connections','shared_buffers','work_mem','maintenance_work_mem','effective_cache_size','autovacuum','autovacuum_max_workers','autovacuum_naptime','autovacuum_vacuum_threshold','autovacuum_vacuum_scale_factor','autovacuum_vacuum_cost_delay','autovacuum_vacuum_cost_limit','autovacuum_freeze_max_age','idle_in_transaction_session_timeout','statement_timeout','transaction_timeout','track_io_timing') ORDER BY name;"
