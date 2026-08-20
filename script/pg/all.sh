#!/usr/bin/env bash
set -u
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
scripts=(
  health.sh
  activity.sh
  locks.sh
  long-transactions.sh
  dead-tuples.sh
  vacuum.sh
  bloat.sh
  xid-age.sh
  connections.sh
  slow-queries.sh
  query-stats.sh
  tables.sh
  indexes.sh
  unused-indexes.sh
  queue-pgmq.sh
  queue-health.sh
  cron.sh
  replication.sh
  settings.sh
)

failed=0
for script in "${scripts[@]}"; do
    printf '\n################################################################\n'
    printf '# %s\n' "$script"
    printf '################################################################\n'
    if bash "$SCRIPT_DIR/$script"; then
        :
    else
        echo "[FAILED] $script"
        failed=$((failed+1))
    fi
done

printf '\n################################################################\n'
printf '# SUMMARY\n'
printf '################################################################\n'
if (( failed == 0 )); then
    echo "ALL CHECKS PASSED"
else
    echo "$failed CHECK(S) FAILED"
    exit 1
fi
