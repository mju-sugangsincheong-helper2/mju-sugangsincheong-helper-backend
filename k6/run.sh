#!/bin/bash

# 사용법: ./run.sh [도메인] [단계]
# 예시:
#   ./run.sh                    # 전체 도메인, 모든 단계
#   ./run.sh multigame          # multigame 도메인만
#   ./run.sh multigame small    # multigame small 단계만
#   ./run.sh singlegame middle  # singlegame middle 단계만

DOMAIN=${1:-"all"}
PHASE=${2:-"all"}

echo "================================================================="
echo " K6 Load Test Runner"
echo " Domain: $DOMAIN | Phase: $PHASE"
echo "================================================================="

mkdir -p k6/report

# 실행할 스크립트 목록 결정
SCRIPTS=()

if [ "$DOMAIN" = "all" ] || [ "$DOMAIN" = "singlegame" ]; then
  if [ "$PHASE" = "all" ] || [ "$PHASE" = "small" ]; then
    SCRIPTS+=("k6/script/singlegame/small-test.js")
  fi
  if [ "$PHASE" = "all" ] || [ "$PHASE" = "middle" ]; then
    SCRIPTS+=("k6/script/singlegame/middle-test.js")
  fi
  if [ "$PHASE" = "all" ] || [ "$PHASE" = "large" ]; then
    SCRIPTS+=("k6/script/singlegame/large-test.js")
  fi
fi

if [ "$DOMAIN" = "all" ] || [ "$DOMAIN" = "exchange" ]; then
  if [ "$PHASE" = "all" ] || [ "$PHASE" = "small" ]; then
    SCRIPTS+=("k6/script/exchange/small-test.js")
  fi
  if [ "$PHASE" = "all" ] || [ "$PHASE" = "middle" ]; then
    SCRIPTS+=("k6/script/exchange/middle-test.js")
  fi
  if [ "$PHASE" = "all" ] || [ "$PHASE" = "large" ]; then
    SCRIPTS+=("k6/script/exchange/large-test.js")
  fi
fi

if [ "$DOMAIN" = "all" ] || [ "$DOMAIN" = "multigame" ]; then
  if [ "$PHASE" = "all" ] || [ "$PHASE" = "small" ]; then
    SCRIPTS+=("k6/script/multigame/small-test.js")
  fi
  if [ "$PHASE" = "all" ] || [ "$PHASE" = "middle" ]; then
    SCRIPTS+=("k6/script/multigame/middle-test.js")
  fi
  if [ "$PHASE" = "all" ] || [ "$PHASE" = "large" ]; then
    SCRIPTS+=("k6/script/multigame/large-test.js")
  fi
fi

# 스크립트 실행
for script in "${SCRIPTS[@]}"; do
  if [ -f "$script" ]; then
    echo "-----------------------------------------------------------------"
    echo " Running: $script"
    echo "-----------------------------------------------------------------"
    
    k6 run "$script" \
      -e BASE_URL=http://localhost:8080 \
      --out json="k6/report/$(basename "$script" .js).json"
  else
    echo "Warning: $script not found, skipping..."
  fi
done

echo "================================================================="
echo " Test completed. Reports in k6/report/"
echo "================================================================="
