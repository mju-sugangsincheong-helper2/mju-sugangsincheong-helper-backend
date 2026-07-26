#!/usr/bin/env bash

# zsh로 실행되면 자동으로 bash로 재실행
if [ -n "$ZSH_VERSION" ]; then
  exec bash "$0" "$@"
fi

# 사용법:
#   ./run.sh <domain.scenario> <load-tier>   # 특정 시나리오만 실행
#   ./run.sh <domain> <load-tier>             # 도메인 전체 시나리오 실행
#   ./run.sh <domain>                         # 도메인 전체, 기본 부하(small)
#
# 예시:
#   ./run.sh multigame.session small
#   ./run.sh multigame middle
#   ./run.sh exchange.intent large
#   ./run.sh singlegame
#
# 도메인: multigame | exchange | singlegame
# 시나리오: 도메인별로 다름 (아래 참조)
# 부하 티어: small | middle | large (기본: small)
#
# multigame 시나리오:
#   reservation   - 예약 생성/조회
#   session       - 대기방 입장 + 게임 신청 (폴링)
#   result        - 결과/통계/대시보드 조회
#   full-flow     - 예약 → 대기방 → 게임 → 결과 전체
#
# exchange 시나리오:
#   main          - 메인 화면 폴링
#   intent        - 의사 등록/철회
#   room          - 방 메시지 조회/전송/토글
#   full-flow     - 전체 플로우
#
# singlegame 시나리오:
#   save          - 게임 결과 저장
#   rank          - 랭킹 조회
#   full-flow     - 저장 → 랭킹 → 내 기록

set -e

# --max 플래그 처리
MAX_MODE=false
ARGS=()
for arg in "$@"; do
  if [ "$arg" = "--max" ]; then
    MAX_MODE=true
  else
    ARGS+=("$arg")
  fi
done

INPUT=${ARGS[0]:-""}
LOAD_TIER=${ARGS[1]:-"small"}

# --max 모드이면 large로 강제 + VU_MAX 적용
if [ "$MAX_MODE" = true ]; then
  LOAD_TIER="large"
  export VU_MAX=${VU_MAX:-4000}
  echo "[MAX MODE] VU_MAX=$VU_MAX"
fi

# 입력 파싱
if [[ "$INPUT" == *"."* ]]; then
  DOMAIN="${INPUT%%.*}"
  SCENARIO="${INPUT#*.}"
else
  DOMAIN="$INPUT"
  SCENARIO="all"
fi

# 유효성 검사
if [ -z "$DOMAIN" ]; then
  echo "Usage: $0 <domain.scenario|domain> [small|middle|large]"
  echo ""
  echo "Domains: multigame, exchange, singlegame"
  echo "Load tiers: small, middle, large (default: small)"
  exit 1
fi

if [[ "$LOAD_TIER" != "small" && "$LOAD_TIER" != "middle" && "$LOAD_TIER" != "large" ]]; then
  echo "Error: Invalid load tier '$LOAD_TIER'. Must be: small, middle, large"
  exit 1
fi

SCRIPT_DIR="k6/script"
REPORT_DIR="k6/report"
mkdir -p "$REPORT_DIR"

# 실행할 시나리오 파일 목록 결정
SCRIPTS=()

resolve_scripts() {
  local domain=$1
  local scenario=$2
  local base_dir="$SCRIPT_DIR/$domain/scenarios"

  if [ "$scenario" = "all" ]; then
    for f in "$base_dir"/*.js; do
      [ -f "$f" ] && SCRIPTS+=("$f")
    done
  else
    local target="$base_dir/$scenario.js"
    if [ -f "$target" ]; then
      SCRIPTS+=("$target")
    else
      echo "Error: Scenario '$scenario' not found for domain '$domain'"
      echo "Available scenarios:"
      ls -1 "$base_dir"/*.js 2>/dev/null | sed 's|.*/||;s|\.js||' | sed 's/^/  /'
      exit 1
    fi
  fi
}

case "$DOMAIN" in
  multigame|exchange|singlegame)
    resolve_scripts "$DOMAIN" "$SCENARIO"
    ;;
  all)
    for d in multigame exchange singlegame; do
      resolve_scripts "$d" "$SCENARIO"
    done
    ;;
  *)
    echo "Error: Unknown domain '$DOMAIN'"
    echo "Available domains: multigame, exchange, singlegame"
    exit 1
    ;;
esac

if [ ${#SCRIPTS[@]} -eq 0 ]; then
  echo "Error: No scripts found to run"
  exit 1
fi

echo "================================================================="
echo " K6 Load Test Runner"
echo " Domain: $DOMAIN | Scenario: $SCENARIO | Load tier: $LOAD_TIER"
echo "================================================================="
echo ""

for script in "${SCRIPTS[@]}"; do
  scenario_name=$(basename "$script" .js)
  domain_name=$(echo "$script" | sed "s|$SCRIPT_DIR/||;s|/scenarios/.*||")
  report_name="${domain_name}_${scenario_name}_${LOAD_TIER}"

  echo "-----------------------------------------------------------------"
  echo " Running: $script (tier=$LOAD_TIER)"
  echo "-----------------------------------------------------------------"

  k6 run "$script" \
    -e BASE_URL=http://localhost:8080 \
    -e LOAD_TIER="$LOAD_TIER" \
    --out json="$REPORT_DIR/${report_name}.json"

  echo ""
done

echo "================================================================="
echo " Test completed. Reports in $REPORT_DIR/"
echo "================================================================="
