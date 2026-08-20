#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"

section "pg_cron extension"

query "
SELECT extname, extversion
FROM pg_extension
WHERE extname = 'pg_cron';
"

section "pg_cron jobs"

query_table "
SELECT
  jobid,
  schedule,
  command,
  nodename,
  nodeport,
  database,
  username,
  active
FROM cron.job
ORDER BY jobid;
" || warn "cron.job unavailable."

section "Recent pg_cron runs"

query_table "
SELECT
  runid,
  jobid,
  database,
  username,
  command,
  status,
  return_message,
  start_time,
  end_time
FROM cron.job_run_details
ORDER BY start_time DESC
LIMIT 50;
" || warn "cron.job_run_details unavailable."
