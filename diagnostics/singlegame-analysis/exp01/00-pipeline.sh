#!/usr/bin/env bash
set -euo pipefail
# 조건 하드코딩. 여기만 고침.
API_VERSION="1"
BASE_URL="http://localhost:8080"
LOGIN_NAME="TEST_GUEST"
TOTAL_COURSES="6"
LABELS="5m 10m 15m 20m 25m 30m 35m 40m 45m 50m 55m 60m 65m 70m 75m 80m 85m 90m 95m 100m"
STEPS="50000 100000 150000 200000 250000 300000 350000 400000 450000 500000 550000 600000 650000 700000 750000 800000 850000 900000 950000 1000000"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
export API_VERSION BASE_URL LOGIN_NAME TOTAL_COURSES LABELS STEPS SCRIPT_DIR

"$SCRIPT_DIR/10-drive.sh"
# raw/trace.jfr 수동복사 (10-drive.sh가 이미 시도, 실패시 아래 재시도)
PROJ_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
if [ ! -f "$SCRIPT_DIR/raw/trace.jfr" ]; then
  JFR_SRC="$(ls -t "$PROJ_ROOT"/build/jfr/*.jfr 2>/dev/null | head -n 1 || true)"
  if [ -n "${JFR_SRC:-}" ]; then cp "$JFR_SRC" "$SCRIPT_DIR/raw/trace.jfr"; fi
fi
"$SCRIPT_DIR/20-dump.sh"
uv run "$SCRIPT_DIR/30-analyze.py"
