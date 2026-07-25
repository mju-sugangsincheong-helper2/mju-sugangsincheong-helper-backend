#!/bin/bash

# 최대 부하 테스트 (large 단계만 실행)
# 사용법: ./max_run.sh [도메인]
# 예시:
#   ./max_run.sh              # 전체 도메인 large 테스트
#   ./max_run.sh multigame    # multigame large 테스트만

DOMAIN=${1:-"all"}
VU_MAX=${VU_MAX:-4000}

echo "================================================================="
echo " Starting MAX load test with VU_MAX = $VU_MAX"
echo " Domain: $DOMAIN"
echo "================================================================="

mkdir -p k6/report

SCRIPTS=()

if [ "$DOMAIN" = "all" ] || [ "$DOMAIN" = "singlegame" ]; then
  SCRIPTS+=("k6/script/singlegame/large-test.js")
fi

if [ "$DOMAIN" = "all" ] || [ "$DOMAIN" = "exchange" ]; then
  SCRIPTS+=("k6/script/exchange/large-test.js")
fi

if [ "$DOMAIN" = "all" ] || [ "$DOMAIN" = "multigame" ]; then
  SCRIPTS+=("k6/script/multigame/large-test.js")
fi

for script in "${SCRIPTS[@]}"; do
  if [ -f "$script" ]; then
    echo "-----------------------------------------------------------------"
    echo " Running: $script"
    echo "-----------------------------------------------------------------"
    
    k6 run "$script" \
      -e BASE_URL=http://localhost:8080 \
      -e VU_MAX=$VU_MAX \
      --out json="k6/report/max_$(basename "$script" .js).json"
  else
    echo "Warning: $script not found, skipping..."
  fi
done

echo "================================================================="
echo " Max load test completed."
echo " Reports generated in k6/report/max_*.json"
echo "================================================================="
