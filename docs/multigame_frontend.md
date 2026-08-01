# 멀티게임 프론트엔드 가이드

> `docs/multigame.md` 는 서버 내부 설계 문서입니다. 이 문서는 **프론트엔드 개발자가 화면을 만들 때 실제로 알아야 하는 것들**만 정리한 프론트 전용 가이드입니다.
> 서버의 내부 구현(Cron, Redis, Supply Engine 등)은 이 문서에서 다루지 않습니다.

> 📌 **API 사용 현황 (백엔드 구현 상태 기준)**
>
> - ✅ **사용 중 (앱 연동 완료)** — 게임 세션 API 4종: `GET /session/waiting-room`, `POST /session/enter`, `POST /session/leave`, `POST /session/apply`
> - ⚠️ **백엔드에만 구현됨 (현재 앱에서 사용 안 함)** — 결과 API 3종: `GET /me/results`, `GET /results`, `GET /results/{multigameId}` (라운드 상세는 분석서 + 내 결과 + 내 신청 타임라인을 하나의 응답으로 통합)
> - 랭킹 API(`GET /rankings`)는 백엔드에 구현되어 있으나 **화면 계획이 없어 이 가이드에서는 다루지 않습니다.**
>
> 3장에서 각 API의 경로·응답을 확인할 수 있으며, 미사용 API는 각 절에 ⚠️로 표시했습니다. 이 API들은 언제든 화면에 연결할 수 있는 상태입니다.

---

## 1. 화면 구성

멀티게임 기능은 **5개 화면**으로 구성됩니다. (실제 사용 중: 대기방·게임방 / 기록·결과 화면 2종: 백엔드 준비 완료, 앱 미연동)

```
/multigame                          메인 페이지 (대기방 진입 + 내 참여 기록)
/multigame/waiting-room             대기방 (1초 폴링)
/multigame/game                     게임방 (30초 한정 수강신청)
/multigame/results                  모든 기록 페이지 (내 전체 참여 기록, 페이징)
/multigame/result/{multigameId}     라운드 결과 상세
```

> ⚠️ 현재 앱에서 실제로 동작하는 화면은 **대기방·게임방**입니다. `results`, `result` 2개 화면은
> 백엔드 API가 준비되어 있으나 **아직 화면에 연결되지 않았습니다 (미연동)**.

**화면 간 이동 흐름:**

```
[메인] ──대기방 입장──▶ [대기방] ──PROGRESS + enter──▶ [게임방] ──ENDED──▶ [결과 상세 /result/{T}]
   │                      (1초 폴링)                     (1초 폴링 apply)
   ├── 내 기록 항목 탭 ───────────────▶ [결과 상세 /result/{id}]
   └── 전체 기록 보기 ──▶ [모든 기록 /results] ──항목 탭──▶ [결과 상세 /result/{id}]
```

### 1-1. 메인 페이지 (`/multigame`)

| 영역 | 내용 | 데이터 출처 | 상태 |
|---|---|---|---|
| 현재 게임 상태 배너 | 게임 상태(안내문) + 참여 인원 + 남은 시간 | `GET /session/waiting-room` 폴링 (1초) | ✅ 사용 중 |
| 대기방 입장 버튼 | `WAITING`/`READY` 상태에서 활성화 | 상태 배너 응답의 `state` | ✅ 사용 중 |
| 내 참여 기록 | 최근 참여 라운드 목록 (최신순, 메인 노출용 몇 건) | `GET /me/results` | ⚠️ 미연동 |
| 최근 결과 바로가기 | 내 기록 1번째 항목의 `multigameId`로 결과 상세 이동 | `GET /me/results` | ⚠️ 미연동 |
| 전체 기록 보기 | 모든 기록 페이지로 이동 | `GET /me/results` (페이징) | ⚠️ 미연동 |

**메인 페이지에 필요한 것들 (체크리스트):**
- 대기방 입장 버튼 ✅
- **내 참여 기록** — 최근 몇 건 미리보기 + "전체 보기" 진입점 (⚠️ 미연동 — `GET /me/results`)
- **운영 시간 안내** — 새벽 2시~5시는 `CLOSED`. "운영 시간 아님" 배너
- **최근 결과 확인 진입점** — 게임 직후 메인으로 돌아온 유저를 위해 (⚠️ 미연동)
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
- **30초가 지나면 서버가 자동으로 신청을 막음** → `apply` 응답이 `BLOCKED`+`ENDED`로 바뀜. 이 응답을 받으면 프론트는 "게임 종료"로 인식
- 결과 확인 버튼 → `/multigame/result/{multigameId}` 이동 (⚠️ 결과 화면은 현재 미연동. 버튼 노출은 자유)

### 1-4. 라운드 결과 상세 (`/multigame/result/{multigameId}`) — ⚠️ 미연동

한 라운드(T)의 **내 결과 + 라운드 분석서**를 보여주는 화면입니다. 진입 경로: ① 게임 종료 후 결과 확인 버튼, ② 메인의 내 참여 기록/모든 기록 페이지에서 항목 탭, ③ 메인의 최근 결과 바로가기.

**데이터는 상세 API 하나(`GET /results/{multigameId}`)로 모두 해결됩니다.** 별도로 `/me/results` 목록을 필터하거나 로그 API를 호출할 필요가 없습니다.

| 영역 | 내용 | 데이터 출처 |
|---|---|---|
| 라운드 메타 | T(14자리), 참여자 수 `participantCount`, 좌석 수 `capacity`, 종료 시각 `createdAt` | `GET /results/{multigameId}` |
| 내 결과 | 최종 상태(`SUCCESS`/`FAIL_SOLDOUT`) + 신청 과목(`subjectId`) — `participated=true`일 때만 `myResult` | `GET /results/{multigameId}` |
| 내 참가 타임라인 (접이식) | 대기열 순번 `seq` / 입장 허용선 `limit` 변화 — `myLog` | `GET /results/{multigameId}` |
| 라운드 분석서 | 과목 1~6 `applied`/`succeeded`/`competitionRate` | `GET /results/{multigameId}` |

**화면 상태 처리 (이 화면의 핵심):**

| 상황 | 처리 |
|---|---|
| 로딩 | `GET /results/{id}` 1개 호출, 스켈레톤 표시 |
| 아직 정산 중 (ENDED 직후) | `/results/{id}`가 `404 MULTIGAME_004` → "정산 중입니다" 안내 후 **1~2초 뒤 자동 재시도** (수동 새로고침 유도 금지) |
| 미참여 라운드 | 200 OK + `participated=false` → **내 결과/타임라인만 숨기고** "이 라운드에 참여하지 않았습니다" 안내. 분석서(`subjects`)는 정상 표시 |
| 참여한 라운드 | 200 OK + `participated=true` → `myResult`로 내 결과 강조 표시, `myLog`로 타임라인 렌더링 |
| 라운드 자체가 없음 | `/results/{id}` 404 → "존재하지 않는 라운드입니다" + 모든 기록/메인 복귀 버튼 |
| 내 기록이 오래된 라운드 | 상세 API가 내 결과를 직접 포함하므로 목록 페이징과 무관하게 항상 정확한 `myResult`/`myLog` 표시 가능 |

> **주의:** 상세 API의 `404 MULTIGAME_004`는 **라운드 자체가 없거나 아직 정산 중**인 경우에만 발생합니다.
> "내가 참여했는가"는 404가 아니라 `participated` 필드로 판단하세요 (미참여도 200 OK입니다).

### 1-5. 모든 기록 페이지 (`/multigame/results`) — ⚠️ 미연동

내가 참여한 **전체 라운드 기록**을 페이징으로 보여주는 화면입니다. 진입 경로: 메인 페이지의 "전체 기록 보기".

- 데이터: `GET /me/results?page=&size=`
- 행: `multigameId`(T), 신청 과목 `subjectId`, 최종 상태 `status`(`SUCCESS`/`FAIL_SOLDOUT`), 참여 시각 `createdAt`
- 최신순(`start_time` 내림차순), 페이징 — `page`(0부터)/`size`(기본 10), 응답 `page.hasNext`로 "더 보기" 버튼 노출 여부 결정
- 빈 상태: "아직 참여한 라운드가 없습니다" + 대기방 진입 유도 버튼
- 행 탭 → `/multigame/result/{multigameId}` 이동

> (선택) 모든 종료 라운드의 **익명 목록**(`GET /results`)이 필요해지면 이 화면에 "라운드 아카이브" 탭으로 추가할 수 있습니다. 백엔드 API는 준비되어 있습니다.

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

### 2-5. 응답 봉투

모든 응답은 공통 봉투입니다. **프론트는 `data`만 읽으면 됩니다.**

```json
// 단건
{ "meta": { "requestId": "...", "apiVersion": "1", "timestamp": "...", ... }, "data": { ... } }

// 목록 (페이징)
{ "meta": { "...": "..." }, "data": [ ... ], "page": { "pageNumber": 0, "pageSize": 10, "totalElements": 42, "totalPages": 5, "hasNext": true, "hasPrevious": false } }
```

에러 응답:

```json
{ "meta": { "...": "..." }, "error": { "code": "MULTIGAME_002", "message": "Game is not in a valid state for this operation.", "details": [...] } }
```

---

## 3. API 명세

> **경로 버전**: 모든 API는 `/api/{version}/multigame/...` 형식이며, **현재 버전 세그먼트는 `1`** 입니다.
> (예: `/api/1/multigame/session/waiting-room`). `apiVersion` 응답 메타도 `"1"`로 내려옵니다.

### 3-1. 게임 세션 (게임중) — ✅ 앱 사용 중

#### `GET /api/1/multigame/session/waiting-room` — 대기방 상태 조회 (1초 폴링)

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

#### `POST /api/1/multigame/session/enter` — 게임 입장

- **`PROGRESS` 상태에서만 성공**합니다.
- 200 OK → `{ "multigameId": "T", "state": "PROGRESS", "participation": P }`
- 실패:
  - `410` `MULTIGAME_003` — 게임 취소됨 (`CANCELLED`)
  - `409` `MULTIGAME_002` — 게임 진행 중 아님 (그 외 상태)

#### `POST /api/1/multigame/session/leave` — 이탈

- 항상 `200 OK` (빈 data). 대기열에도 있으면 함께 제거됩니다. (화면 이탈 시 호출 권장)

#### `POST /api/1/multigame/session/apply?subjectId=N` — 과목 신청 (N: 1~6)

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
  - `400` `GLOBAL_002` — `subjectId`가 1~6 범위 밖
  - `409` `MULTIGAME_002` — **게임에 진입(`enter`)하지 않은 유저의 신청 거부** (진입 먼저)
  - `500` `MULTIGAME_005` — 서버 스크립트 오류 (잠시 후 재시도)

---

### 3-2. 결과 (게임 종료 후) — ⚠️ 현재 앱 미사용

> 백엔드에 구현 완료된 API입니다. 현재 앱에서는 호출하지 않으며, 결과 화면 연동 시 사용합니다.
> 결과 API는 **3종**입니다: 전체 라운드 목록(`GET /results`), 내 참여 이력(`GET /me/results`),
> **라운드 상세**(`GET /results/{multigameId}`). 상세 API가 분석서 + 내 결과 + 내 신청 타임라인을
> 하나의 응답으로 통합하므로, 결과 화면은 상세 API 1개만 호출하면 됩니다.

#### `GET /api/1/multigame/me/results?page=0&size=10` — 나의 참여 이력 목록 (페이징)

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
- 각 행의 `multigameId`를 결과 상세 페이지 URL에 그대로 사용합니다.
- 라운드별 상세(분석서 + 내 결과 + 내 신청 타임라인)는 `GET /results/{multigameId}` 하나로 확인 가능합니다.

#### `GET /api/1/multigame/results/{multigameId}` — 라운드 상세 (분석서 + 내 참여 정보 통합)

```json
{
  "meta": { "...": "..." },
  "data": {
    "multigameId": "20260801120000",
    "participantCount": 12,
    "capacity": 6,
    "createdAt": "2026-08-01T12:00:35Z",
    "participated": true,
    "myResult": { "subjectId": 1, "status": "SUCCESS", "createdAt": "2026-08-01T12:00:35Z" },
    "myLog": [
      { "status": "ENQUEUED", "seq": 3, "limit": 2, "attemptedAt": "2026-08-01T12:00:02Z" },
      { "status": "SUCCESS", "seq": 3, "limit": 4, "attemptedAt": "2026-08-01T12:00:05Z" }
    ],
    "subjects": [
      { "subjectId": 1, "applied": 8, "succeeded": 3, "competitionRate": 1.3 },
      { "subjectId": 2, "applied": 2, "succeeded": 2, "competitionRate": 0.3 }
    ]
  }
}
```

| 필드 | 설명 |
|---|---|
| `participated` | **현재 로그인한 사용자가 이 라운드에 참여했는지** (라운드 최종 결과 레코드 존재 여부). `false`면 `myResult`는 `null`, `myLog`는 `[]` |
| `myResult` | 내 최종 결과 — `subjectId`, `status`(`SUCCESS`/`FAIL_SOLDOUT`), `createdAt`. 미참여 시 `null` |
| `myLog` | 내 신청 시도 타임라인 (시각 오름차순) — 상태 전이 시점 이벤트만 기록: `ENQUEUED`, `SUCCESS`, `FAIL_SOLDOUT`, `FAIL_DUPLICATE`. 각 이벤트는 대기열 순번 `seq`와 그 시점의 입장 허용선 `limit` 포함. 미참여 시 `[]` |
| `subjects` | 과목 1~6 **전체가 항상 포함** (신청 0건이어도 `applied: 0`). `competitionRate = applied / capacity` (소수 1자리). **타 유저의 memberId는 절대 포함되지 않음** |

- **프론트 분기:** `participated=true`면 `myResult`/`myLog`로 내 결과 강조 + 타임라인 표시, `false`면 "이 라운드에 참여하지 않았습니다" 안내 (분석서는 그대로 표시).
- `404` `MULTIGAME_004` — **라운드 자체가 없거나 아직 정산 중**인 경우만. (미참여는 404가 아니라 `participated=false`)

#### `GET /api/1/multigame/results?page=0&size=10` — 전체 라운드 목록 (진행된 라운드만)

```json
{ "meta": { "...": "..." },
  "data": [ { "multigameId": "20260801120000", "participantCount": 12, "capacity": 6, "createdAt": "..." } ],
  "page": { "pageNumber": 0, "pageSize": 10, "totalElements": 3, "totalPages": 1, "hasNext": false, "hasPrevious": false } }
```

- 최신순. 결과 화면의 "이전 라운드 보기" 등에 사용.
- **취소된 라운드(최소 인원 미달)는 DB에 저장되지 않으므로 이 목록에 나타나지 않습니다** (진행된 라운드만 조회).

---

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
T+30s ~ T+1m  [결과 상세]  (⚠️ 미연동 — 추후 연동 시)
                        /multigame/result/{T} 진입
                        GET /results/{T}        → 라운드 메타 + 분석서 + participated + myResult + myLog (1콜)
                        (ENDED 직후 바로 진입 시 404 → 1~2초 후 자동 재시도)
```

### 시나리오 2: 최소 인원 미달 취소

```
T-5s ~ T      [대기방]  폴링 → CANCELLED → "최소 인원 미달로 취소되었습니다"
                        결과/기록 화면에 이 라운드는 기록되지 않음 (결과 404)
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

### 시나리오 5: 정산 중 결과 조회 (ENDED 직후)

```
T+30s      [결과 상세]  /multigame/result/{T} 진입
T+30s~     [결과 상세]  GET /results/{T} → 404 (아직 DB 영속화 전)
                        → "정산 중입니다" 안내, 1~2초 후 자동 재시도 (수동 새로고침 유도 금지)
T+30s+α    [결과 상세]  재시도 성공 → 라운드 메타/분석서/내 결과(`participated=true`) 정상 표시
```

### 시나리오 6: 미참여 라운드의 결과 상세 (직접 URL 진입 등)

```
[결과 상세]  GET /results/{T} → 200 (라운드는 존재)
             data.participated = false, myResult = null, myLog = []
             → 내 결과/타임라인만 숨기고 "이 라운드에 참여하지 않았습니다" 표시
               분석서(subjects)는 그대로 노출
```

---

## 5. 에러 코드

| code | HTTP | 의미 | 프론트 대응 |
|---|---|---|---|
| `MULTIGAME_001` | 404 | 게임 세션 없음 | 안내 후 메인 복귀 |
| `MULTIGAME_002` | 409 | 게임 상태가 유효하지 않음 (미진입 신청 포함) | "먼저 게임에 입장해주세요" |
| `MULTIGAME_003` | 410 | 게임 취소됨 | "게임이 취소되었습니다" |
| `MULTIGAME_004` | 404 | 결과 없음 (라운드 자체가 없거나 아직 정산 중) | "참여 기록이 없습니다" (아직 정산 중일 수 있으니 1~2초 후 재시도). **미참여 라운드는 404가 아니라 `participated=false`** |
| `MULTIGAME_005` | 500 | 게임 스크립트 오류 | "잠시 후 다시 시도해주세요" |
| `MULTIGAME_006` | 500 | 분산 락 획득 실패 | "잠시 후 다시 시도해주세요" (서버 내부 문제) |
| `GLOBAL_002` | 400 | 요청 검증 실패 (`subjectId` 1~6 밖 등) | 입력값 확인 |
| `GLOBAL_004` | 500 | 서버 내부 오류 | "잠시 후 다시 시도해주세요" |
| `GLOBAL_SECURITY_001` | 401 | 인증되지 않은 접근 (로그인/토큰 없음) | 로그인 유도 |
| `GLOBAL_SECURITY_002` | 403 | 접근 권한 없음 | 권한 승격/로그인 안내 |

---

## 6. 암묵지 & 주의사항

1. **`apply`는 HTTP 200이어도 `status` 필드로 판단**하세요. `BLOCKED`/`PENDING`/`SUCCESS`/`FAIL_*`는 전부 200으로 옵니다. HTTP 상태코드로 분기하지 마세요.

2. **"30초 후 자연 차단"은 서버가 처리합니다.** 프론트에서 강제로 막을 필요 없고, `apply` 응답의 `BLOCKED`+`currentState=ENDED`를 받으면 종료 UI를 띄우면 됩니다.

3. **폴링 간격은 1초 고정**입니다. 대기방(`waiting-room`)과 게임방(`apply`) 모두 1초 폴링입니다. 폴링을 멈추면 heartbeat가 끊겨 대기 인원에서 제외됩니다(3초 기준).

4. **`STARTING`은 오류가 아닙니다.** 수 초 내 자동 해소됩니다. 유저에게 재시도 버튼 대신 "잠시만 기다려주세요"를 보여주세요.

5. **SUCCESS 후 재신청하면 `FAIL_DUPLICATE`** 입니다. 성공했다면 해당 과목 신청은 중단하세요. `FAIL_SOLDOUT`은 재시도/과목 변경이 가능합니다 (과목별 좌석 독립).

6. **결과는 `T+30s` 이후 몇 초 내에 DB에 영속화**됩니다. `ENDED` 직후 바로 결과 조회 시 `404`가 나올 수 있으니, 404를 받으면 1~2초 뒤 재시도하는 로직이 필요합니다.

7. **결과 페이지 URL의 `{multigameId}`** 는 `waiting-room`/`enter` 응답의 `multigameId`를 그대로 사용합니다. (직접 계산 금지)

8. **기록 목록(메인 내 기록, 모든 기록 페이지)은 최신순·전체 기간 누적**입니다. 라운드 단위 필터는 없습니다.

9. **멀티게임은 동시 1개**입니다. 내 화면의 상태가 다른 사용자와 다른 경우는 없고, 폴링 응답만 믿고 UI를 그리면 됩니다. 클라이언트 시계(clock)는 신뢰하지 마세요 — 모든 판정은 서버 시간 기준입니다.

10. **`leave`는 게임방 이탈 시에 호출**하면 대기열 순서에서도 빠집니다. (게임방에서 뒤로가기/종료 시 호출 권장)

11. **과목 ID는 1~6 하드코딩**입니다. 과목명 매핑은 프론트가 자체적으로 관리하세요.

12. **`participation`의 의미가 상태마다 다릅니다.** (3-1 표 참고) 대기방에서 "참여자 수"라고 보여줄 때는 `WAITING`일 때만 실시간 수치입니다.

13. **기록·결과 화면 2종(모든 기록 `/results`, 결과 상세 `/result/{id}`)은 아직 앱에서 미연동**입니다. 연동 전까지는 메인에서 해당 영역을 숨기거나 "준비 중"으로 처리해도 됩니다. 연동 시 1-4/1-5의 데이터 출처와 3-2의 경로(`/api/1/...`) 및 응답 필드를 그대로 사용하세요.
