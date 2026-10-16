#!/usr/bin/env bash
set -euo pipefail
# 조건 하드코딩. 여기만 고침.
API_VERSION="1"
BASE_URL="http://localhost:8080"
LOGIN_NAME="TEST_GUEST"
TOTAL_COURSES="6"
LABELS="2m 4m 6m 8m 10m 12m 14m 16m 18m 20m"
STEPS="20000 40000 60000 80000 100000 120000 140000 160000 180000 200000"

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
