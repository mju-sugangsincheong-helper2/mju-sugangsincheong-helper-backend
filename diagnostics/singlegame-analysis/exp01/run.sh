#!/usr/bin/env bash
set -euo pipefail
# zsh로 실행해도 되도록 (zsh는 기본 word-split 안 함)
if [ -n "${ZSH_VERSION:-}" ]; then setopt SH_WORD_SPLIT; fi
# exp01 run.sh — 사례 전용. 바꿀 수 있는 건 여기뿐.
# 순서: lib 기동 → 시나리오(시드 누적+반복 측정) → dump → 일반 분석 → 사례 그림.
#
# ================================================================
# CONFIG: 이번 사례값. 값·로직 전부 여기 소유. 다음 실험엔 갈아끼움.
# ================================================================
API_VERSION="1"
BASE_URL="http://localhost:8080"
LOGIN_NAME="TEST_GUEST"
TOTAL_COURSES="6"
LABELS="5m 10m 15m 20m 25m 30m"               # 데이터량 라벨
STEPS="50000 100000 150000 200000 250000 300000" # 라벨별 누적 row 수
TRIALS="10"                                    # 태그당 반복 횟수
LOGIN_PATH="/auth/test-login"
SEED_PATH="/diagnostics/singlegame/seed"
MEASURE_PATH="/diagnostics/singlegame/analysis/api"
TAG_HEADER="X-JFR-TAG"
REC_NAME="tool"
JFR_SETTINGS="profile"
APP_JPS_PATTERN="MjusugangsincheonghelperApplication"
# ================================================================

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJ_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
TOOLS="$SCRIPT_DIR/tools"
RAW="$SCRIPT_DIR/raw"
HTTP="$RAW/http"
REPORT="$SCRIPT_DIR/report"

export BASE_URL RAW PROJ_ROOT TAG_HEADER
# shellcheck source=tools/jfr-lib.sh
source "$TOOLS/jfr-lib.sh"

jfr_require_tools
mkdir -p "$HTTP" "$REPORT"
find "$HTTP" -maxdepth 1 -name '*.json' -delete 2>/dev/null || true

trap jfr_server_down EXIT INT TERM
jfr_server_up

APP_PID="$(jfr_app_pid "$APP_JPS_PATTERN")"
[ -n "${APP_PID:-}" ] || { echo "앱 PID 없음 (jps)" >&2; exit 1; }
echo "APP_PID: $APP_PID"

login_resp="$(curl -sS -f -X POST "$BASE_URL/api/$API_VERSION$LOGIN_PATH?name=$LOGIN_NAME")"
TOKEN="$(echo "$login_resp" | jq -r '.data.sessionAccessToken // empty')"
[ -n "$TOKEN" ] || { echo "로그인 실패" >&2; exit 1; }

curl -sS -f -X DELETE "$BASE_URL/api/$API_VERSION$SEED_PATH" \
  -H "Authorization: Bearer $TOKEN" > /dev/null

# ---------- 사례 시나리오: 시드 누적 + 태그당 TRIALS 반복 ----------
jfr_start "$APP_PID" "$REC_NAME" "$JFR_SETTINGS"
echo "JFR 시작: name=$REC_NAME"

prev=0
i=0
for target in $STEPS; do
  i=$((i + 1))
  nnn=$(printf "%03d" "$i")
  label="$(echo "$LABELS" | cut -d' ' -f"$i")"
  tag="${nnn}-${label}"
  delta=$((target - prev))
  curl -sS -f --max-time 900 -X POST \
    "$BASE_URL/api/$API_VERSION$SEED_PATH?count=$delta&totalCourses=$TOTAL_COURSES" \
    -H "Authorization: Bearer $TOKEN" > /dev/null
  for t in $(seq 1 "$TRIALS"); do
    jfr_tagged_curl "$tag" "$HTTP/${tag}-t${t}.json" -G \
      "$BASE_URL/api/$API_VERSION$MEASURE_PATH" \
      --data-urlencode "totalCourses=$TOTAL_COURSES" \
      -H "Authorization: Bearer $TOKEN"
    echo "저장: $HTTP/${tag}-t${t}.json (tag=$tag trial=$t)"
    sleep 1
  done
  sleep 2
  prev=$target
done

curl -sS -f -X DELETE "$BASE_URL/api/$API_VERSION$SEED_PATH" \
  -H "Authorization: Bearer $TOKEN" > /dev/null || true

jfr_dump "$APP_PID" "$RAW/trace.jfr" "$REC_NAME"
jfr_stop "$APP_PID" "$REC_NAME"
echo "JFR 저장: $RAW/trace.jfr"
jfr_server_down
trap - EXIT INT TERM
# ---------- 시나리오 끝 ----------

jfr_dump_json "$RAW/trace.jfr" "$RAW/trace.json"
echo "저장: $RAW/trace.json"

uv run "$TOOLS/jfr-analyze.py" --input "$RAW/trace.json" --report-dir "$REPORT"
uv run "$SCRIPT_DIR/plot.py"
