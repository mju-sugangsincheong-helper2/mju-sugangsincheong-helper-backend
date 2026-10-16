# CONTRACT (v1) — URL 기준 JFR 콜스택 도구의 동결 인터페이스

## 티어

- **팀 공용** (`<exp>/tools/`, `global/api/filter/JfrWindowFilter.java`):
  우리 팀 프로젝트끼리 돌려씀. 한국어·Spring 전제 정당. 세상 공용 아님.
- **사례** (`diagnostics/<도메인>/exp<NN>/`): 값·시나리오·그림 전부 소유. 다음 실험엔 갈아끼움.

## 동결 목록 (바뀌면 메이저 버전)

### 1. 태그 헤더

- 이름: `X-JFR-TAG`
- 값: 불투명 문자열. 서버는 해석 금지, 그대로 기록. 없으면 `""`.
- 정의 위치 3곳은 값을 같이 바꿔야 함:
  `JfrWindowFilter.TAG_HEADER` / 사례 `run.sh` CONFIG / `jfr-lib.sh` 기본값.

### 2. JFR 창 이벤트

- 이름: `http.server.request` (조인키. 바꾸면 분석기 전체 사망)
- 카테고리: `{"HTTP", "Server"}`, 스택 OFF, 명시 ENABLE.
- 필드 6개 (additive-only: 추가는 마이너, 삭제·개명은 메이저):

| 필드 | 출처 | 규칙 |
|---|---|---|
| `method` | 요청 메소드 | 그대로 |
| `route` | 요청 URI | 숫자 세그먼트 → `{id}` 정규화 |
| `query` | 쿼리스트링 | raw, 512자 cap |
| `jfrTag` | X-JFR-TAG | 그대로, 256자 cap |
| `requestId` | X-Request-Id → MDC → UUID 순 fallback | 재사용 우선, 신규 생성 금지 아님 |
| `status` | `finally`에서 `response.getStatus()` | 실패 구간 제외용 |

- JFR이 자동 첨부 (`startTime`, `duration`, `eventThread`)는 필드로 만들지 않음.

### 3. 분석기 (`tools/jfr-analyze.py`)

- 옵션: `--input`, `--report-dir`, `--group-by {tag(기본), window}`.
- 창 판정: 창별 스레드 일치 + 시간 겹침 (`[start-0.05, end+0.05]`, `MARGIN=0.05` 고정).
  전 trial 포함(창 집합 기준). 백그라운드 스레드는 창 집합에 없어 자동 제외.
- 표시 루트 = Controller 앵커 (스택에서 `Controller.` 첫 프레임부터. 컨테이너 프레임 제거).
- trie 키 = 메소드. trial들은 같은 요청 스택이므로 합산, 스레드는 `{t:c}` 분포로.
- 행렬 컬럼: 기존 + `thread`(분포) + `method`(method가 맨 뒤 유지). summary 끝에 `{분포}`.
- 빈 태그 창은 제외 (`INCLUDE_UNTAGGED=False` 고정).
- 요약 규칙: `[(부모와 숫자 다름) AND (점유율>0%)]` + 조상(문맥). 자식은 시간 내림차순.
  한 줄 + 자체 0(모든 샘플이 유일 자식으로 통과)은 접음(`[+N]`, 최상단 제외).
  시간 쓴 곳·분기점은 살림. 목록·설정 없음.
  행렬은 이름순 유지.
- 출력 3종 (컬럼명 동결):
  - `calltree-<group>.txt` — 합산 trie 행렬 + summary.
    `%` 분모 = `elapsed_sum` (반복 합산 trie의 유일한 무난한 분모).
    다른 분모를 원하면 `--group-by window`로 탈출.
  - `rollup_windows.csv` — 1행=1창.
    컬럼: `group,trial,route,query,status,elapsed,thread` + 메트릭(`<event>#cnt`, `<event>#total`).
  - `rollup_tags.csv` — 1행=1그룹.
    컬럼: `group,n,route,query,status,elapsed_mean,elapsed_min,elapsed_max,threads` + 메트릭(합산).

### 4. 라이브러리 (`tools/jfr-lib.sh`)

- 함수명·인자 순서 동결. env로만 제어 (`BASE_URL`, `RAW`, `TAG_HEADER` 등). 파일 상단 주석 참조.
- `jfr print` 기본 이벤트 목록에 `http.server.request` 포함 (빠지면 창 상실).

## 버전 규칙

- 필드·컬럼·옵션 **추가**: 마이너. 기존 사례 무수정 동작.
- 이름 변경·삭제·의미 변경: 메이저. 사례 전체 수정.
