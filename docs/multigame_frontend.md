# 멀티게임 프론트엔드 가이드

> `docs/multigame.md` 는 서버 내부 설계 문서입니다. 이 문서는 **프론트엔드 개발자가 화면을 만들 때 실제로 알아야 하는 것들**만 정리한 프론트 전용 가이드입니다.
> 서버의 내부 구현(Cron, Redis, Supply Engine 등)은 이 문서에서 다루지 않습니다.

---

## 1. 화면 구성

멀티게임 기능은 **4개 화면**으로 구성됩니다.

```
/multigame                          메인 페이지 (대기방 입구 + 참여 기록 + 랭킹)
/multigame/waiting-room             대기방 (1초 폴링)
/multigame/game                     게임방 (30초 한정 수강신청)
/multigame/result/{multigameId}     결과 페이지
```

### 1-1. 메인 페이지 (`/multigame`)

| 영역 | 내용 | 데이터 출처 |
|---|---|---|
| 현재 게임 상태 배너 | 게임 상태(안내문) + 참여 인원 + 남은 시간 | `GET /session/waiting-room` 폴링 (1초) |
| 대기방 입장 버튼 | `WAITING`/`READY` 상태에서 활성화 | 상태 배너 응답의 `state` |
| 나의 참여 기록 | 최근 기록 목록 (페이징) | `GET /results/my` |
| 랭킹 | 학과별 참가 수 / 학과별 성적 + 내 학과 순위 | `GET /rankings` |
| 최근 결과 바로가기 | 가장 최근 참여 라운드의 결과로 이동 | `GET /results/my` 1번째 항목의 `multigameId` |

**메인 페이지에 필요한 것들 (체크리스트):**
- 대기방 입장 버튼
- 나의 참여 기록
- 랭킹
- **운영 시간 안내** — 새벽 2시~5시는 `CLOSED`. "운영 시간 아님" 배너
- **최근 결과 확인 진입점** — 게임 직후 메인으로 돌아온 유저를 위해
- 게임 안내 (이용 방법, 30초 규칙 등)

### 1-2. 대기방 (`/multigame/waiting-room`)

- **1초 간격 폴링**: `GET /session/waiting-room`
- 대기 인원 표시: 응답의 `participation`
- 상태별 안내문 (3장 참고)
- 게임 시작(`PROGRESS`)되면 **"수강신청 입장" 버튼** 활성화 → `POST /session/enter`
- 진입 성공 시 `/multigame/game`으로 이동

### 1-3. 게임방 (`/multigame/game`)

- 게임 시간 **30초** (서버가 `T ~ T+30s`만 허용)
- 남은 시간 표시 + 과목(1~6) 선택 UI
- 신청: `POST /session/apply?subjectId=N` — **1초 폴링으로 PENDING → 결과 갱신**
- **30초가 지나면 서버가 자동으로 신청을 막음** → `apply` 응답이 `BLOCKED`+`ENDED`로 바뀜. 이 응답을 받으면 프론트는 "게임 종료"로 인식하고 **결과 확인 버튼**을 노출
- 결과 확인 → `/multigame/result/{multigameId}` 이동

### 1-4. 결과 페이지 (`/multigame/result/{multigameId}`)

| 영역 | 내용 | 데이터 출처 |
|---|---|---|
| 내 결과 | 최종 상태(SUCCESS/FAIL_SOLDOUT) + 신청 과목 | `GET /results/{multigameId}/my` |
| 내 참가 타임라인 (선택) | 대기열 순번/입장 허용선 변화 | 위 응답의 `log` |
| 라운드 분석서 (선택) | 참여자 수, 과목별 신청/성공/경쟁률 | `GET /results/{multigameId}` |
| 내 기록 목록으로 | `/multigame` 메인으로 | - |

---

## 2. 핵심 개념 (반드시 알아야 할 것)

### 2-1. 게임 식별자 T

- 게임은 **10분 단위**로 열립니다 (`:00, :10, :20, :30, :40, :50`).
- T = `yyyyMMddHHmmss` 형식의 14자리 시작 시각 (예: `20260801120000`).
- **프론트는 T를 계산하지 않습니다.** 서버가 응답의 `multigameId`로 항상 내려줍니다. URL에 넣을 때 그대로 쓰면 됩니다.
- 게임은 **동시에 1개만** 진행됩니다.

### 2-2. 상태(State) 7종

서버는 시간 + Redis 상태를 조합해 항상 아래 7가지 중 하나를 응답합니다.

| state | 의미 | 안내문 예시 | 진입/신청 버튼 |
|---|---|---|---|
| `CLOSED` | 새벽 2시~5시 미운영 | "운영 시간이 아닙니다" | 비활성 |
| `WAITING` | 대기 모집 중 (T-9m ~ T-5s) | "게임이 곧 시작됩니다" | 대기방만 가능 |
| `READY` | 게임 확정, 시작 직전 (T-5s ~ T) | "곧 시작됩니다" | 대기방만 가능 |
| `STARTING` | 시작 준비 중 (과도기) | "게임 시작 준비 중입니다. 잠시만 기다려주세요" | 비활성 (수 초 내 해소) |
| `PROGRESS` | 게임 진행 중 (T ~ T+30s) | "게임 진행 중" | **활성화** |
| `ENDED` | 게임 종료·정산 중 (T+30s ~ T+1m) | "게임이 종료되었습니다" | 비활성 → 결과 확인 버튼 |
| `CANCELLED` | 최소 인원(2명) 미달로 취소 | "최소 인원 미달로 취소되었습니다" | 비활성 |

> `STARTING`은 서버 Cron 지연으로 인한 수 초 과도기 상태입니다. **자동으로 곧 해소**되므로 유저에게 "잠시만 기다려주세요"만 보여주고 재시도/새로고침을 유도하지 마세요.

### 2-3. 폴링 = 생존 신호 (heartbeat)

- 대기방에서 **1초 폴링 자체가 서버의 생존 신호**입니다. 별도 heartbeat API가 없습니다.
- `WAITING`/`READY` 상태의 폴링만 생존 신호로 기록됩니다.
- **폴링을 3초 이상 멈추면(백그라운드 탭, 화면 이탈 등) 대기 인원에서 제외**되어 게임이 시작됐을 때 진입 자격이 없을 수 있습니다. 앱/탭이 백그라운드로 가도 폴링을 유지해야 합니다.

### 2-4. 권한

| 리소스 | 최소 권한 | 비고 |
|---|---|---|
| 게임 세션, 결과 | `GUEST` 이상 | JWT 인증 필요 |
| 랭킹 | `MEMBER` 이상 | 학과 집계 데이터 노출이라 게스트 제한 |

### 2-5. 응답 봉투

모든 응답은 공통 봉투입니다. **프론트는 `data`만 읽으면 됩니다.**

```json
// 단건
{ "meta": { "requestId": "...", "timestamp": "...", ... }, "data": { ... } }

// 목록 (페이징)
{ "meta": { ... }, "data": [ ... ], "page": { "pageNumber": 0, "pageSize": 10, "totalElements": 42, "totalPages": 5, "hasNext": true, "hasPrevious": false } }
```

에러 응답:

```json
{ "meta": { ... }, "error": { "code": "MULTIGAME_002", "message": "Game is not in a valid state for this operation.", "details": [...] } }
```

---

## 3. API 명세

### 3-1. 게임 세션 (게임중)

#### `GET /api/v1/multigame/session/waiting-room` — 대기방 상태 조회 (1초 폴링)

```json
// 200 OK
{
  "meta": { "...": "..." },
  "data": {
    "multigameId": "20260801120000",
    "state": "WAITING",
    "participation": 23
  }
}
```

| 필드 | 설명 |
|---|---|
| `multigameId` | 타겟 게임 T (14자리) |
| `state` | 위 7종 중 하나 |
| `participation` | `WAITING` = 실시간 대기 인원 / `READY` = 게임 확정 시점 스냅샷 / `PROGRESS` = 현재 진입 인원 / 그 외 = 0 |

- `WAITING`/`READY`일 때만 서버가 heartbeat를 갱신합니다. (폴링 유지 필수)
- `CLOSED`일 때는 `participation`이 0입니다.

#### `POST /api/v1/multigame/session/enter` — 게임 입장

- **`PROGRESS` 상태에서만 성공**합니다.
- 200 OK → `{ "multigameId": "T", "state": "PROGRESS", "participation": P }`
- 실패:
  - `410` `MULTIGAME_003` — 게임 취소됨 (`CANCELLED`)
  - `409` `MULTIGAME_002` — 게임 진행 중 아님 (그 외 상태)

#### `POST /api/v1/multigame/session/leave` — 이탈

- 항상 `200 OK` (빈 data). 대기열에도 있으면 함께 제거됩니다. (화면 이탈 시 호출 권장)

#### `POST /api/v1/multigame/session/apply?subjectId=N` — 과목 신청 (N: 1~6)

- **HTTP 상태 코드가 아니라 응답 `data.status`로 판단**해야 합니다. 정상 흐름은 전부 `200 OK`입니다.

```json
// PENDING (대기열 등록됨. 1초 후 재시도)
{ "data": { "status": "PENDING", "seq": 7, "limit": 5 } }

// SUCCESS (신청 성공. 남은 좌석 수 포함)
{ "data": { "status": "SUCCESS", "subjectId": 1, "remaining": 3 } }

// FAIL_SOLDOUT (정원 초과 → 재시도 가능)
{ "data": { "status": "FAIL_SOLDOUT", "subjectId": 1 } }

// FAIL_DUPLICATE (이미 성공한 과목을 다시 신청 → 신청 중지)
{ "data": { "status": "FAIL_DUPLICATE", "subjectId": 1 } }

// BLOCKED (게임 상태가 PROGRESS가 아님. currentState로 사유 확인)
{ "data": { "status": "BLOCKED", "currentState": "ENDED" } }
```

| status | 프론트 액션 |
|---|---|
| `PENDING` | 1초 후 같은 요청 재시도 (대기 UI 표시, `seq`/`limit`로 "내 순번" 표시 가능) |
| `SUCCESS` | **성공 토스트/애니메이션, 신청 완료 처리** (더 이상 재시도 금지) |
| `FAIL_SOLDOUT` | "매진" 안내. 다른 과목 재신청 가능 (과목별 좌석은 독립적) |
| `FAIL_DUPLICATE` | "이미 신청 완료된 과목" 안내. 이 과목은 그만 |
| `BLOCKED` | `currentState` 보고 안내: `ENDED` → 결과 확인 버튼 / `STARTING` → 잠시 대기 / 그 외 → 안내문 |

- HTTP 에러:
  - `409` `MULTIGAME_002` — **게임에 진입(`enter`)하지 않은 유저의 신청 거부** (진입 먼저)
  - `500` `MULTIGAME_005` — 서버 스크립트 오류 (잠시 후 재시도)

---

### 3-2. 결과 (게임 종료 후)

#### `GET /api/v1/multigame/results/my?page=0&size=10` — 나의 참여 이력 목록 (페이징)

```json
{
  "meta": { "...": "..." },
  "data": [
    { "multigameId": "20260801120000", "subjectId": 1, "status": "SUCCESS", "createdAt": "2026-08-01T12:00:35Z" },
    { "multigameId": "20260801110000", "subjectId": 3, "status": "FAIL_SOLDOUT", "createdAt": "2026-08-01T11:00:35Z" }
  ],
  "page": { "pageNumber": 0, "pageSize": 10, "totalElements": 2, "totalPages": 1, "hasNext": false, "hasPrevious": false }
}
```

- 최신순(`start_time` 내림차순)으로 반환됩니다.
- `status`: `SUCCESS` 또는 `FAIL_SOLDOUT`만 존재합니다.

#### `GET /api/v1/multigame/results/{multigameId}/my` — 특정 게임에서 나의 상세 기록

```json
{
  "meta": { "...": "..." },
  "data": {
    "multigameId": "20260801120000",
    "participantCount": 12,
    "capacity": 6,
    "createdAt": "2026-08-01T12:00:35Z",
    "myStatus": "SUCCESS",
    "mySubjectId": 1,
    "log": [
      { "status": "ENQUEUED", "seq": 3, "limit": 2, "attemptedAt": "2026-08-01T12:00:02Z" },
      { "status": "SUCCESS", "seq": 3, "limit": 4, "attemptedAt": "2026-08-01T12:00:05Z" }
    ]
  }
}
```

- `log` = 내 신청 시도의 상태 전이 타임라인 (대기열 순번 `seq`, 그 시점 입장 허용선 `limit`).
- `404` `MULTIGAME_004` — 해당 라운드에 내 기록이 없음 (미참여 라운드).

#### `GET /api/v1/multigame/results/{multigameId}` — 라운드 분석서 (집계, 개인정보 없음)

```json
{
  "meta": { "...": "..." },
  "data": {
    "multigameId": "20260801120000",
    "participantCount": 12,
    "capacity": 6,
    "createdAt": "2026-08-01T12:00:35Z",
    "subjects": [
      { "subjectId": 1, "applied": 8, "succeeded": 3, "competitionRate": 1.3 },
      { "subjectId": 2, "applied": 2, "succeeded": 2, "competitionRate": 0.3 }
    ]
  }
}
```

- `competitionRate = applied / capacity` (소수 1자리). **타 유저의 memberId는 절대 포함되지 않습니다.**
- `404` `MULTIGAME_004` — 해당 라운드 결과 없음.

#### `GET /api/v1/multigame/results?page=0&size=10` — 라운드 목록 (선택)

```json
{ "meta": { "...": "..." },
  "data": [ { "multigameId": "20260801120000", "participantCount": 12, "capacity": 6, "createdAt": "..." } ],
  "page": { "pageNumber": 0, "pageSize": 10, "totalElements": 3, "totalPages": 1, "hasNext": false, "hasPrevious": false } }
```

- 최신순. 결과 화면의 "이전 라운드 보기" 등에 사용.

---

### 3-3. 랭킹 (MEMBER 이상)

#### `GET /api/v1/multigame/rankings` — 학과 랭킹

```json
{
  "meta": { "...": "..." },
  "data": {
    "participation": [
      { "department": "컴퓨터공학과", "participantCount": 152 },
      { "department": "전자공학과", "participantCount": 118 }
    ],
    "performance": [
      { "department": "컴퓨터공학과", "top70AvgSuccessRate": 62.5, "participantCount": 152 },
      { "department": "경영학과", "top70AvgSuccessRate": 55.1, "participantCount": 90 }
    ],
    "myDepartment": {
      "department": "컴퓨터공학과",
      "participationRank": 1,
      "performanceRank": 1,
      "participantCount": 152,
      "top70AvgSuccessRate": 62.5
    }
  }
}
```

**집계 규칙 (프론트에 보여줄 때 그대로 안내 가능):**
- `participation` — 전체 기간 동안 **참가자 수(SUCCESS/FAIL_SOLDOUT 모두 포함)**가 많은 학과 순.
- `performance` — 학과별로 유저를 **성공률(SUCCESS 라운드 수 ÷ 참가 라운드 수 × 100) 내림차순**으로 정렬한 뒤, **상위 70% 인원의 평균 성공률**이 높은 학과 순.
- 학과가 등록되지 않은 유저는 집계에서 제외됩니다. (게스트 계정 등)
- `myDepartment` — 내 학과의 두 순위와 수치. 학과 미등록 시 `null`.

> 랭킹은 `MEMBER` 이상만 호출 가능합니다. `401/403`이 나면 로그인/권한 승격 안내를 노출하세요.

---

## 4. 주요 시나리오

### 시나리오 1: 정상 게임 (대기 → 게임 → 결과)

```
T-9m ~ T-5s   [대기방]  폴링 → WAITING, "게임이 곧 시작됩니다" + 대기 인원 23명
T-5s ~ T      [대기방]  폴링 → READY, "곧 시작됩니다"
T             [대기방]  폴링 → PROGRESS → "수강신청 입장" 버튼 활성화
T ~ T+30s     [게임방]  POST /enter → 200, 30초 타이머 시작
                        과목 1 선택 → POST /apply → PENDING(seq=7, limit=5) → 1초 후 재시도
                        → SUCCESS(subjectId=1, remaining=3) → 성공 처리, 신청 중지
T+30s         [게임방]  (자동 차단) /apply → BLOCKED(currentState=ENDED)
                        → "게임 종료" → 결과 확인 버튼 노출
T+30s ~ T+1m  [결과]    GET /results/{T}/my → myStatus=SUCCESS
```

### 시나리오 2: 최소 인원 미달 취소

```
T-5s ~ T      [대기방]  폴링 → CANCELLED → "최소 인원 미달로 취소되었습니다"
                        결과 페이지/랭킹에 이 라운드는 기록되지 않음 (결과 404)
```

### 시나리오 3: 운영 시간 (새벽 2~5시)

```
폴링 → CLOSED → "운영 시간이 아닙니다" 배너. 모든 버튼 비활성.
```

### 시나리오 4: 트래픽 지연 (늦은 입장)

```
T+15s [게임방]  참여자 수(P)는 유동적. 늦게 들어와도 남은 좌석이 있으면 신청 가능.
                서버는 진입 인원 P를 상한으로 공급량을 조절하므로 30초 내 처리됨.
```

---

## 5. 에러 코드

| code | HTTP | 의미 | 프론트 대응 |
|---|---|---|---|
| `MULTIGAME_001` | 404 | 게임 세션 없음 | 안내 후 메인 복귀 |
| `MULTIGAME_002` | 409 | 게임 상태가 유효하지 않음 (미진입 신청 포함) | "먼저 게임에 입장해주세요" |
| `MULTIGAME_003` | 410 | 게임 취소됨 | "게임이 취소되었습니다" |
| `MULTIGAME_004` | 404 | 결과 없음 | "참여 기록이 없습니다" (아직 정산 중일 수 있으니 1~2초 후 재시도) |
| `MULTIGAME_005` | 500 | 게임 스크립트 오류 | "잠시 후 다시 시도해주세요" |
| `GLOBAL_401/403` | 401/403 | 인증/권한 부족 | 로그인 유도 (랭킹은 MEMBER 이상) |

---

## 6. 암묵지 & 주의사항

1. **`apply`는 HTTP 200이어도 `status` 필드로 판단**하세요. `BLOCKED`/`PENDING`/`SUCCESS`/`FAIL_*`는 전부 200으로 옵니다. HTTP 상태코드로 분기하지 마세요.

2. **"30초 후 자연 차단"은 서버가 처리합니다.** 프론트에서 강제로 막을 필요 없고, `apply` 응답의 `BLOCKED`+`currentState=ENDED`를 받으면 종료 UI를 띄우면 됩니다.

3. **폴링 간격은 1초 고정**입니다. 대기방(`waiting-room`)과 게임방(`apply`) 모두 1초 폴링입니다. 폴링을 멈추면 heartbeat가 끊겨 대기 인원에서 제외됩니다(3초 기준).

4. **`STARTING`은 오류가 아닙니다.** 수 초 내 자동 해소됩니다. 유저에게 재시도 버튼 대신 "잠시만 기다려주세요"를 보여주세요.

5. **SUCCESS 후 재신청하면 `FAIL_DUPLICATE`** 입니다. 성공했다면 해당 과목 신청은 중단하세요. `FAIL_SOLDOUT`은 재시도/과목 변경이 가능합니다 (과목별 좌석 독립).

6. **결과는 `T+30s` 이후 몇 초 내에 DB에 영속화**됩니다. `ENDED` 직후 바로 결과 조회 시 `404`가 나올 수 있으니, 404를 받으면 1~2초 뒤 재시도하는 로직이 필요합니다.

7. **결과 페이지 URL의 `{multigameId}`** 는 `waiting-room`/`enter` 응답의 `multigameId`를 그대로 사용합니다. (직접 계산 금지)

8. **랭킹 수치는 전체 기간 누적**입니다. 라운드 단위 필터는 없습니다.

9. **멀티게임은 동시 1개**입니다. 내 화면의 상태가 다른 사용자와 다른 경우는 없고, 폴링 응답만 믿고 UI를 그리면 됩니다. 클라이언트 시계(clock)는 신뢰하지 마세요 — 모든 판정은 서버 시간 기준입니다.

10. **`leave`는 게임방 이탈 시에 호출**하면 대기열 순서에서도 빠집니다. (게임방에서 뒤로가기/종료 시 호출 권장)

11. **과목 ID는 1~6 하드코딩**입니다. 과목명 매핑은 프론트가 자체적으로 관리하세요.

12. **`participation`의 의미가 상태마다 다릅니다.** (3-1 표 참고) 대기방에서 "참여자 수"라고 보여줄 때는 `WAITING`일 때만 실시간 수치입니다.
