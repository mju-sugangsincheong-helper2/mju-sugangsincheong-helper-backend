#!/usr/bin/env bash
set -euo pipefail
# zsh로 실행해도 되도록 (zsh는 기본 word-split 안 함)
if [ -n "${ZSH_VERSION:-}" ]; then setopt SH_WORD_SPLIT; fi
# exp01 run.sh — 위에서 아래로 읽으면 끝. 함수 정의·source 없음. 직접 호출만.
#
# 구역 규칙:
#   [EDITABLE] 값+로직. 이번 실험의 가설·순서. 맘대로 고침.
#   [FROZEN v1] 서버와 약속된 측정 메커니즘. 바꾸면 서버도 같이 바꿔야 함.
#               위치: src/main/java/.../global/api/filter/JfrWindowFilter.java

# ================================================================
# [EDITABLE 1] 이번 실험값 — 맘대로 고침
# ================================================================
API_VERSION="1"
BASE_URL="http://localhost:8080"
LOGIN_NAME="TEST_GUEST"
TOTAL_COURSES="6"
LABELS="5m 10m 15m 20m 25m 30m 100m"               # 데이터량 라벨
STEPS="50000 100000 150000 200000 250000 300000 1000000" # 라벨별 누적 row 수
TRIALS="1"                                     # 태그당 반복 횟수
LOGIN_PATH="/auth/test-login"
SEED_PATH="/diagnostics/singlegame/seed"
MEASURE_PATH="/diagnostics/singlegame/analysis/api"
APP_JPS_PATTERN="MjusugangsincheonghelperApplication"

# ================================================================
# [FROZEN v1] 측정 메커니즘 — 아래 4줄은 수정 금지
#  - TAG_HEADER: 서버 JfrWindowFilter.TAG_HEADER와 동일해야 함. 다르면 창 상실.
#  - JFR_EVENTS: 첫 항목 http.server.request는 창 조인키. 빼면 분석기 전체 사망.
#  - REC_NAME/JFR_SETTINGS: JFR 레코딩 이름·프로파일. 바꾸면 dump/stop 짝이 어긋남.
# ================================================================
TAG_HEADER="X-JFR-TAG"
JFR_EVENTS="http.server.request,jdk.ThreadStart,jdk.ExecutionSample,jdk.NativeMethodSample,jdk.SocketRead,jdk.SocketWrite,jdk.JavaMonitorWait,jdk.JavaMonitorEnter,jdk.ThreadPark,jdk.ThreadSleep,jdk.FileRead,jdk.FileWrite,jdk.JavaExceptionThrow"
REC_NAME="tool"
JFR_SETTINGS="profile"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RAW="$SCRIPT_DIR/raw"
HTTP="$RAW/http"
REPORT="$SCRIPT_DIR/report"

# ---- 도구 확인 (FROZEN: 그대로 실행) ----
command -v curl >/dev/null || { echo "curl 필요" >&2; exit 1; }
command -v jq >/dev/null || { echo "jq 필요" >&2; exit 1; }
command -v uv >/dev/null || { echo "uv 필요" >&2; exit 1; }
if ! command -v jcmd >/dev/null || ! command -v jfr >/dev/null; then
  if [ -x "/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin/jcmd" ]; then
    export PATH="/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin:$PATH"
  else
    echo "jcmd/jfr 필요" >&2; exit 1
  fi
fi
mkdir -p "$HTTP" "$REPORT"
find "$HTTP" -maxdepth 1 -name '*.json' -delete 2>/dev/null || true

# ---- 서버 확인 (FROZEN: 자동기동 없음. 켜져있다고 전제, health만 체크) ----
if ! curl -sS -o /dev/null -w "%{http_code}" "$BASE_URL/actuator/health" 2>/dev/null | grep -q "200"; then
  echo "서버 없음: $BASE_URL (SPRING_PROFILES_ACTIVE=dev 로 먼저 기동)" >&2
  exit 1
fi

APP_PID="$(jps -l 2>/dev/null | awk -v pat="$APP_JPS_PATTERN" '$0 ~ pat {print $1; exit}')"
[ -n "${APP_PID:-}" ] || { echo "앱 PID 없음 (jps)" >&2; exit 1; }
echo "APP_PID: $APP_PID"

# ================================================================
# [EDITABLE 2] 이번 시나리오 — 값뿐 아니라 로직(순서·반복·sleep)도 맘대로 고침
# 흐름: 로그인 → 시드 리셋 → JFR.start → 시드 누적+태그당 TRIALS 반복 →
#       시드 정리 → dump/stop → json 변환 → 분석 → 그림
# ================================================================
login_resp="$(curl -sS -f -X POST "$BASE_URL/api/$API_VERSION$LOGIN_PATH?name=$LOGIN_NAME")"
TOKEN="$(echo "$login_resp" | jq -r '.data.sessionAccessToken // empty')"
[ -n "$TOKEN" ] || { echo "로그인 실패" >&2; exit 1; }

curl -sS -f -X DELETE "$BASE_URL/api/$API_VERSION$SEED_PATH" \
  -H "Authorization: Bearer $TOKEN" > /dev/null

jcmd "$APP_PID" JFR.stop name="$REC_NAME" >/dev/null 2>&1 || true
jcmd "$APP_PID" JFR.start name="$REC_NAME" settings="$JFR_SETTINGS"
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
    curl -sS -f --max-time 900 -G \
      "$BASE_URL/api/$API_VERSION$MEASURE_PATH" \
      --data-urlencode "totalCourses=$TOTAL_COURSES" \
      -H "Authorization: Bearer $TOKEN" \
      -H "$TAG_HEADER: $tag" \
      -o "$HTTP/${tag}-t${t}.json"
    echo "저장: $HTTP/${tag}-t${t}.json (tag=$tag trial=$t)"
    sleep 1
  done
  sleep 2
  prev=$target
done

# curl -sS -f -X DELETE "$BASE_URL/api/$API_VERSION$SEED_PATH" \
#   -H "Authorization: Bearer $TOKEN" > /dev/null || true

jcmd "$APP_PID" JFR.dump name="$REC_NAME" filename="$RAW/trace.jfr"
jcmd "$APP_PID" JFR.stop name="$REC_NAME" >/dev/null 2>&1 || true
echo "JFR 저장: $RAW/trace.jfr"

[ -f "$RAW/trace.jfr" ] || { echo "$RAW/trace.jfr 없음" >&2; exit 1; }
jfr print --json --stack-depth 256 --events "$JFR_EVENTS" "$RAW/trace.jfr" > "$RAW/trace.json.tmp"
mv "$RAW/trace.json.tmp" "$RAW/trace.json"
echo "저장: $RAW/trace.json"

uv run "$SCRIPT_DIR/analyze.py" --input "$RAW/trace.json" --report-dir "$REPORT"
uv run "$SCRIPT_DIR/plot.py"
