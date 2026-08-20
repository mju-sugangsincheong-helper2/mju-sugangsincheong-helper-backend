#!/usr/bin/env bash
set -euo pipefail

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

for script in "${scripts[@]}"; do
  echo
  echo "################################################################"
  echo "# $script"
  echo "################################################################"
  "$SCRIPT_DIR/$script"
done
