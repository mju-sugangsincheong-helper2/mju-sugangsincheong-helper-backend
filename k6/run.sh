for f in k6/script/singlegame/*.js k6/script/exchange/*.js; do
  duration_flag=""
  [ "$(basename "$f")" = "endurance-test.js" ] && duration_flag="-e DURATION_MINUTES=5"
  k6 run "$f" -e BASE_URL=http://localhost:8080 $duration_flag \
    --out json="k6/report/$(basename "$f" .js).json"
done