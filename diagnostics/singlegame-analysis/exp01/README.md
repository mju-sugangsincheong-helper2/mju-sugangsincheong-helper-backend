# exp01 — URL 기준 콜스택 측정

## 한 줄

`run.sh` 한 방. 서버가 요청 구간을 JFR에 직접 기록하고, 분석기는 그 구간만 잘라 콜스택을 뽑는다.

## 동작

```
run.sh → jcmd JFR.start → curl -H "X-JFR-TAG: <태그>" → jcmd dump → jfr-analyze → plot
```

1. **기록은 서버가**: `JfrWindowFilter`가 요청을 `http.server.request` 이벤트
   (메소드·route·쿼리·태그·상태)로 JFR에 박는다. URL-시간 매칭 휴리스틱 없음.
2. **창은 태그로**: 같은 태그 N회 반복은 합산 집계. `windows=태그당 구간, tags=합산`.
3. **읽는 순서**: `rollup_tags.csv`(추세) → `calltree-<태그>.txt` summary(원인) → 행렬(상세).

## 요약 규칙

`[(부모와 숫자 다름) AND (점유율>0%)]` + 조상. 한 줄 비-도메인 래퍼는 접음(`[+N]`).
상세는 행렬에 그대로. 기준 바꾸면 `run.sh` CONFIG만.

## 파일

- `run.sh` — 사례 전부 (CONFIG + 시나리오). 여기만 고침.
- `plot.py` — 이번 기준 그림. 다음 실험엔 갈아끼움.
- `tools/` — 일반. 수정 금지. 계약은 `tools/CONTRACT.md`.
