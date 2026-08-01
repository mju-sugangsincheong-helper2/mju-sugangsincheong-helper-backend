# 멀티게임 프론트엔드 연동 명세

이 문서는 멀티게임을 구현·QA하는 프론트엔드 개발자를 위한 **독립 문서**입니다. 이 문서만으로 화면 흐름, API, 상태별 처리, 폴링 중단 시점까지 구현할 수 있도록 작성했습니다.

멀티게임은 여러 사용자가 같은 시각에 과목을 신청하는 짧은 게임입니다. 사용자는 먼저 원하는 게임 시각을 예약하고, 시작 전에는 대기방에 머물며, 게임이 시작되면 선택한 과목을 신청합니다. 게임이 끝나면 결과는 일반 조회 화면처럼 확인합니다.

## 1. 한눈에 보는 구조

| 영역 | 사용자 관점 | 프론트의 책임 | 실시간 처리 |
| --- | --- | --- | --- |
| 예약 및 결과 리스트 | 미래 게임 참가를 예약한다 | 예약 생성·목록 표시 | 없음 |
| 게임 중 | 대기방에 접속하고 과목을 신청한다 | 상태 폴링, 신청 재시도, 타이머 정리 | 있음 |
| 결과/통계 | 종료된 게임의 결과를 본다 | 결과·기록·통계 조회 | 없음 |

중요한 원칙은 다음과 같습니다.

1. 예약과 결과는 서로 독립적입니다. 예약했다고 결과가 즉시 생기지 않으며, 결과는 게임 종료 후에만 조회할 수 있습니다.
2. 실시간 화면은 게임 중 화면뿐입니다. 대기방에서는 3초 폴링이 필요합니다.
3. 신청은 한 번의 호출로 끝나지 않을 수 있습니다. `PENDING`이면 같은 요청을 반복해 최종 결과를 받아야 합니다.
4. 현재 대상 게임의 식별자는 서버가 시간으로 계산합니다. 게임 중 API에는 `multigameId`를 보내지 않습니다.

```text
예약 목록/예약 생성
        │
        ▼
대기방 진입 ── 3초 폴링 ──> 게임 상태 확인
        │                        │
        │                  PROGRESS가 되면
        ▼                        ▼
  WAITING / READY           과목 선택·신청 반복
                                 │
                            SUCCESS / 실패 / 종료
                                 │
                                 ▼
                         결과·기록·통계 조회
```

## 2. 공통 API 규칙

### 2.1 기본 주소와 인증

- 기본 경로: `/api/v1/multigame`
- 멀티게임 API는 로그인한 사용자만 호출할 수 있습니다.
- 인증 토큰 전달 방식은 프로젝트의 공통 인증 방식을 따릅니다. 이 문서의 요청 예시에서는 인증 헤더/쿠키를 생략합니다.
- 모든 시간은 서버가 저장한 ISO-8601 UTC 문자열로 반환됩니다. 예: `2026-07-31T03:30:20Z`

### 2.2 성공 응답 형식

성공 응답은 항상 최상위에 `meta`, `data`를 가집니다. 화면에서 주로 사용하는 값은 `data`입니다.

```json
{
  "meta": {
    "requestId": "a1b2c3d4",
    "apiVersion": "v1",
    "path": "/api/v1/multigame/...",
    "method": "GET",
    "timestamp": "2026-07-31T03:30:20Z",
    "durationMs": 8,
    "ipAddress": "...",
    "userAgent": "..."
  },
  "data": {}
}
```

문서의 이후 응답 예시는 읽기 쉽게 `data` 내부만 표시합니다. 실제 구현에서는 반드시 `response.data.data`처럼 한 단계 안쪽의 데이터를 읽어야 합니다(사용 중인 HTTP 클라이언트 구조에 따라 달라질 수 있음).

### 2.3 실패 응답 형식

실패 응답은 HTTP 상태 코드와 `error`를 가집니다.

```json
{
  "meta": { "requestId": "a1b2c3d4" },
  "error": {
    "code": "MULTIGAME_006",
    "message": "Game has been cancelled.",
    "details": []
  }
}
```

- 화면 분기는 HTTP 상태 코드와 `error.code`를 함께 사용합니다.
- `error.message`는 영어 문구이므로, 사용자 문구는 프론트에서 `error.code` 기준으로 관리하는 것을 권장합니다.
- 검증 오류의 `details`는 서버 설정에 따라 없을 수 있으므로 필수값으로 가정하지 않습니다.

### 2.4 `multigameId` 형식

`multigameId`는 게임 시작 시각을 나타내는 14자리 문자열입니다.

```text
yyyyMMddHHmmss
20260731123000 = 2026-07-31 12:30:00 시작 게임
```

- 게임 시작 시각은 10분 단위입니다: `:00`, `:10`, `:20`, `:30`, `:40`, `:50`.
- 예약 생성에서는 이 값을 보냅니다.
- 대기방과 신청 API에서는 보내지 않습니다. 서버가 현재 시각으로 대상 게임을 자동 선택합니다.
- 표시용 시각은 문자열을 파싱해 사용자의 시간대에 맞게 렌더링합니다. 서비스의 기준 시간대가 필요한 경우 백엔드와 별도로 확인하세요.

## 3. 사용자 여정과 게임 시간표

게임 하나는 시작 시각을 `T`라고 부릅니다. 프론트가 알아야 하는 핵심 시간은 다음과 같습니다.

| 시점 | 서버 상태 | 사용자 경험 | 프론트 동작 |
| --- | --- | --- | --- |
| `T - 5분` | `WAITING` | 대기방 입장 가능 | 대기방 폴링 시작 |
| `T - 10초` | `READY` 또는 `CANCELLED` | 참여자 수를 확정 | 안내 문구 변경, 폴링 유지 |
| `T` | `PROGRESS` | 20초간 신청 가능 | 신청 UI 활성화 |
| `T + 20초` 직후 | `ENDED` | 신청 종료, 결과 저장 시작 | 신청 반복 중단 |
| 결과 저장 완료 후 | `FINALIZE` | 결과 조회 가능 | 결과 화면으로 이동/조회 |

대기방 API가 인식하는 게임 시간 창은 대략 게임 시작 5분 전부터 시작 5분 후 전까지입니다. 예를 들어 12:10 게임은 12:05부터 대상이 되며, 12:15가 지나면 서버는 다음 12:20 게임을 대상으로 계산합니다. 따라서 화면이 오래 열려 있는 경우 응답의 `multigameId`가 바뀔 수 있음을 고려하세요.

### 3.1 상태의 의미

| 상태 | 뜻 | 사용자가 할 수 있는 일 | 프론트 처리 |
| --- | --- | --- | --- |
| `WAITING` | 게임 시작 전 참가자를 기다리는 중 | 대기방 유지 | 인원·대기 안내 표시 |
| `READY` | 게임 성립, 시작 직전 | 대기방 유지 | “곧 시작합니다” 표시 |
| `PROGRESS` | 게임 진행 중 | 과목 신청 | 신청 UI 활성화 |
| `ENDED` | 신청 시간 종료, 결과 저장 시작 | 신청 불가 | 요청 반복 중단, 결과 준비 표시 |
| `FINALIZE` | 결과 저장 완료 | 결과 확인 | 결과 조회 또는 결과 화면 이동 |
| `CANCELLED` | 게임 취소 | 진행 불가 | 폴링/요청 중단, 취소 화면 |

프론트가 상태를 직접 바꾸지 않습니다. 상태는 서버가 시간과 참가자 수에 따라 바꾸며, 프론트는 응답을 보고 화면만 바꿉니다.

### 3.2 게임 취소 조건

`READY`가 되기 직전 실제 대기방 접속자가 2명 미만이면 게임은 취소됩니다. 예약자가 2명 이상이어도, 시작 전 대기방 폴링을 유지한 사용자가 2명 미만이면 취소될 수 있습니다.

따라서 예약만 해 두고 대기방에 들어오지 않는 사용자는 게임 성립 인원으로 계산되지 않습니다. 대기방 화면에는 “게임이 성립하려면 시작 직전까지 접속을 유지해 주세요”라는 안내를 제공하는 것을 권장합니다.

## 4. 화면별 구현 가이드

### 4.1 예약 화면

예약은 단순한 데이터 저장입니다. 예약 API는 현재 진행 중인 게임 상태와 관계없이 동작합니다.

#### 예약 생성

`POST /api/v1/multigame/reservations`

요청 본문:

```json
{
  "multigameId": "20260731123000"
}
```

성공 응답 `data`:

```json
{
  "id": 31,
  "memberId": 12,
  "multigameId": "20260731123000",
  "createdAt": "2026-07-31T02:00:00Z"
}
```

| 필드 | 타입 | 설명 | 화면 사용처 |
| --- | --- | --- | --- |
| `id` | number | 예약 레코드 ID | 목록 key 등 필요 시 사용 |
| `memberId` | number | 예약한 사용자 ID | 내 예약 화면에서는 보통 표시 불필요 |
| `multigameId` | string | 예약한 게임 시작 시각 | 일정·입장 버튼의 기준 값 |
| `createdAt` | string | 예약 생성 시각 | 필요 시 보조 정보로 표시 |

예약 가능 조건:

- 시작 10분 미만 남은 게임은 예약할 수 없습니다.
- 현재 시각으로부터 7일을 초과한 게임은 예약할 수 없습니다.
- 같은 사용자가 같은 게임을 두 번 예약할 수 없습니다.

권장 UI:

1. 10분 단위 게임 시각만 선택할 수 있는 날짜/시간 선택기를 사용합니다.
2. 선택 시점에도 10분 전·7일 제한을 미리 검증해 버튼을 비활성화합니다.
3. 서버 응답 오류도 반드시 처리합니다. 클라이언트 시계와 서버 시계가 다를 수 있습니다.
4. 성공 시 토스트를 표시하고 내 예약 목록을 갱신합니다.

#### 내 예약 목록

`GET /api/v1/multigame/reservations/my`

성공 응답 `data`는 `MultigameReservationResponse` 배열입니다.

```json
[
  {
    "id": 31,
    "memberId": 12,
    "multigameId": "20260731123000",
    "createdAt": "2026-07-31T02:00:00Z"
  }
]
```

현재 API에는 예약 취소 API가 없습니다. 프론트에서 취소 버튼을 제공하지 마세요.

#### 전체 예약 목록

`GET /api/v1/multigame/reservations`

선택적으로 특정 게임만 필터링할 수 있습니다.

```text
GET /api/v1/multigame/reservations?multigameId=20260731123000
```

이 API는 다른 사용자 예약 정보도 포함할 수 있으므로, 공개 화면이나 일반 사용자 화면에 노출할지 제품 정책을 먼저 확인해야 합니다.

### 4.2 대기방 화면

대기방은 단순 상태 조회가 아니라 **참가 의사를 유지하는 신호**입니다. 이 화면에서는 반드시 폴링 수명주기를 관리해야 합니다.

#### 대기방 조회·접속 유지

`GET /api/v1/multigame/session/waiting-room`

요청 파라미터와 본문은 없습니다.

성공 응답 `data`:

```json
{
  "multigameId": "20260731123000",
  "state": "WAITING",
  "participation": 23
}
```

| 필드 | 타입 | 의미 | UI 사용법 |
| --- | --- | --- | --- |
| `multigameId` | string | 서버가 현재 대상으로 판단한 게임 | 제목/카운트다운의 기준 |
| `state` | string | 현재 게임 상태 | 아래 상태 표에 따라 화면 전환 |
| `participation` | number | 현재 대기방에 살아 있는 접속자 수 | “현재 n명 참여 중” 표시 |

#### 폴링 규칙

- 대기방 진입 직후 한 번 즉시 호출합니다.
- 이후 **3초마다** 같은 API를 호출합니다.
- 대기방 화면이 보이는 동안에는 `WAITING`, `READY`, `PROGRESS`에서도 계속 호출합니다.
- `FINALIZE`, `ENDED`, `CANCELLED`, 410 오류, 화면 이탈, 로그아웃에서는 interval을 반드시 정리합니다.
- 네트워크 일시 실패는 바로 게임 취소로 간주하지 말고 사용자에게 재연결 안내를 보이며 다음 폴링을 시도할 수 있습니다. 단, 410 응답은 취소가 확정된 경우입니다.

서버는 이 요청을 받은 사용자를 약 6초 동안 접속자로 봅니다. 3초 폴링은 그 시간 안에 접속 상태를 갱신하기 위한 주기입니다. 브라우저가 백그라운드 탭에서 타이머를 강하게 지연시키면 게임 성립 인원에서 빠질 수 있으므로, 가능하다면 사용자에게 게임 시작 전 앱을 활성 상태로 유지하도록 안내하세요.

#### 상태별 화면 전환 상세

| 응답 상태 | 제목 예시 | 과목 버튼 | 유지할 작업 | 다음 행동 |
| --- | --- | --- | --- | --- |
| `WAITING` | “참가자를 기다리고 있어요” | 비활성 | 3초 폴링 | `READY`/`PROGRESS` 대기 |
| `READY` | “게임이 곧 시작돼요” | 비활성 | 3초 폴링 | `PROGRESS` 대기 |
| `PROGRESS` | “지금 신청할 수 있어요” | 활성 | 대기방 폴링 | 사용자가 과목 선택 |
| `ENDED` | “신청 시간이 종료되었어요” | 비활성 | 대기방 폴링은 중단 | 결과 준비 또는 결과 조회 |
| `FINALIZE` | “결과가 준비되었어요” | 비활성 | 대기방 폴링 중단 | 결과 화면 이동 |

`CANCELLED`은 정상 응답으로 전달되기보다 대기방 API의 `410 MULTIGAME_006` 오류로 처리될 수 있습니다. 오류 처리도 상태 전환의 일부로 구현해야 합니다.

### 4.3 신청 화면

신청은 일반적인 “버튼 한 번 클릭 → 완료” API가 아닙니다. 사용자의 요청이 대기열에서 순서를 기다릴 수 있기 때문에, 최종 상태가 나올 때까지 동일한 요청을 반복 호출해야 합니다.

#### 신청 요청

`POST /api/v1/multigame/session/request?subjectId={subjectId}`

- `subjectId`: 정수, 1~6
- 요청 본문 없음
- 게임 ID를 보내지 않음

예시:

```text
POST /api/v1/multigame/session/request?subjectId=1
```

#### 신청 응답 공통 필드

```ts
type GameRequestResponse = {
  status: 'WAITING' | 'PENDING' | 'SUCCESS' | 'FAIL_SOLDOUT' | 'FAIL_DUPLICATE' | 'BLOCKED';
  seq?: number;
  limit?: number;
  subjectId?: number;
  remaining?: number;
  currentState?: string;
};
```

응답 상태별 상세:

| `status` | 언제 오는가 | 함께 오는 필드 | 반드시 할 일 |
| --- | --- | --- | --- |
| `WAITING` | 아직 게임 시작 전 | `currentState` | 요청 반복을 시작하지 말고 대기 안내. 대기방 상태를 계속 확인 |
| `PENDING` | 대기열에 등록됐지만 아직 차례가 아님 | `seq`, `limit` | 같은 과목으로 다시 요청 |
| `SUCCESS` | 신청 성공 | `subjectId`, `remaining` | 성공 화면 표시, 반복 종료 |
| `FAIL_SOLDOUT` | 해당 과목 정원 마감 | `subjectId` | 마감 화면 표시, 반복 종료 |
| `FAIL_DUPLICATE` | 이번 게임에서 이미 다른 과목 신청 성공 | `subjectId` | 중복 신청 안내, 반복 종료 |
| `BLOCKED` | 게임 종료 후 또는 신청 불가 상태 | `currentState` | 반복 종료, 결과 흐름으로 이동 |

`WAITING`은 상태가 아니라 신청 API의 응답 상태입니다. 예를 들어 대기방 상태가 `READY`여도 신청 API는 `WAITING`을 반환할 수 있습니다. 신청 버튼은 원칙적으로 대기방 상태가 `PROGRESS`일 때만 활성화하세요.

#### 권장 신청 알고리즘

다음 절차를 한 번의 신청 세션으로 구현합니다.

1. 사용자가 과목을 선택한다.
2. 선택한 `subjectId`를 신청 세션 상태에 고정한다.
3. 과목 버튼을 모두 비활성화해 신청 과목을 바꾸지 못하게 한다.
4. 신청 API를 즉시 한 번 호출한다.
5. `PENDING`이면 0.5~1초 뒤 같은 `subjectId`로 다시 호출한다.
6. `SUCCESS`, `FAIL_SOLDOUT`, `FAIL_DUPLICATE`, `BLOCKED`, 오류 중 하나가 오면 타이머를 정리한다.
7. 최종 결과를 화면에 보여 준다.

간단한 의사 코드:

```ts
let stopped = false;

async function requestSubject(subjectId: number) {
  lockSubjectButtons(subjectId);

  while (!stopped) {
    const result = await postGameRequest(subjectId);

    if (result.status === 'PENDING') {
      showQueueWaiting(result.seq, result.limit);
      await delay(700);
      continue;
    }

    finishRequestLoop();
    handleFinalGameRequestResult(result);
    return;
  }
}
```

실제 구현에서는 다음도 필요합니다.

- 동일 버튼을 빠르게 여러 번 눌러 중복 루프가 생기지 않도록 `isRequesting` 플래그 또는 `AbortController`를 둡니다.
- 컴포넌트 언마운트, 라우트 변경, 로그아웃, 게임 종료, 410 오류 시 `stopped = true`와 타이머 정리를 수행합니다.
- 요청이 진행 중일 때는 다른 과목을 선택할 수 없게 합니다.
- `PENDING` 상태에서 다른 과목으로 바꾸는 UX는 제공하지 않는 것을 권장합니다. 서버는 한 게임에서 사용자당 하나의 최종 결과만 남깁니다.

#### `PENDING`의 의미와 UI

`seq`는 대기열에 처음 들어갈 때 부여된 내 순번이며, `limit`은 현재 처리 가능한 마지막 순번입니다.

```json
{
  "status": "PENDING",
  "seq": 19,
  "limit": 15
}
```

위 경우 아직 내 순번 19가 허용선 15보다 뒤이므로 대기 중입니다. `seq - limit`을 “앞에 남은 정확한 인원”으로 표시하지 마세요. 이 값은 동시에 처리되는 요청에 따라 변하므로, “신청 처리 대기 중” 정도의 안내가 안전합니다.

게임이 진행되는 20초 동안 서버는 대기열을 순차적으로 풀어 줍니다. 초기에는 일부만 즉시 처리되고, 나머지는 보통 수 초 안에 처리됩니다. 프론트는 대기 시간을 직접 계산하거나 보장하지 말고 `PENDING`을 기준으로만 반복 호출하세요.

#### 중복 요청이 안전한 이유

사용자가 `PENDING`일 때 같은 과목으로 다시 요청하면 서버는 같은 대기열 순번을 유지합니다. 즉, 반복 호출은 새로 줄을 서는 행동이 아니라 기존 신청 상태를 다시 확인하는 동작입니다. 이것이 신청 API와 별도의 대기열 조회 API가 분리되지 않은 이유입니다.

단, 이미 성공한 사용자가 다시 신청하면 `FAIL_DUPLICATE`가 반환됩니다. 이는 사용자가 한 게임에서 여러 과목을 성공할 수 없다는 규칙입니다.

### 4.4 게임 종료와 결과 화면 전환

게임은 `PROGRESS`가 된 뒤 20초 후 종료됩니다. 종료 직후 서버가 결과를 저장하므로, 매우 짧은 시간 동안 아직 조회 결과가 없을 수 있습니다.

결과 화면으로 가는 안전한 방법:

1. 대기방 응답에서 `FINALIZE`를 받으면 결과 API를 호출한다.
2. 신청 응답에서 `BLOCKED`와 `ENDED` 또는 `FINALIZE`를 받으면 신청을 중단하고 결과 준비 화면으로 전환한다.
3. 결과 API가 `404 MULTIGAME_007`이면 결과 저장 중일 수 있으므로 짧은 간격으로 제한된 횟수만 재시도한다.
4. 재시도에도 없으면 “결과를 찾을 수 없습니다”를 보여 주고 무한 폴링하지 않는다.

결과 조회 재시도는 대기방/신청 폴링과 다릅니다. 결과는 저장이 끝나면 변하지 않는 데이터이므로, 짧은 전환 구간에서만 사용하세요.

## 5. 결과 API

### 5.1 특정 게임의 전체 결과

`GET /api/v1/multigame/results/{multigameId}`

예시:

```text
GET /api/v1/multigame/results/20260731123000
```

성공 응답 `data`:

```json
{
  "multigameId": "20260731123000",
  "participantCount": 23,
  "capacity": 11,
  "finalizedAt": "2026-07-31T03:30:20Z",
  "details": [
    {
      "memberId": 12,
      "subjectId": 1,
      "status": "SUCCESS"
    },
    {
      "memberId": 13,
      "subjectId": 2,
      "status": "FAIL_SOLDOUT"
    }
  ]
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `multigameId` | string | 게임 시작 시각 ID |
| `participantCount` | number | 게임 시작 직전에 확정된 참여자 수 |
| `capacity` | number | 이 게임에서 과목마다 적용된 정원 |
| `finalizedAt` | string | 결과 저장 완료 시각 |
| `details` | array | 참가자별 최종 결과 |
| `details[].memberId` | number | 참가자 ID |
| `details[].subjectId` | number | 신청한 과목 ID (1~6) |
| `details[].status` | string | 최종 신청 결과 |

이 API는 참가자별 `memberId`를 포함합니다. 공개 랭킹·공유 화면에서 그대로 노출할지는 개인정보 정책에 맞춰 결정해야 합니다.

### 5.2 내 특정 게임 결과

`GET /api/v1/multigame/results/my?multigameId={multigameId}`

예시:

```text
GET /api/v1/multigame/results/my?multigameId=20260731123000
```

성공 응답 `data`:

```json
{
  "memberId": 12,
  "subjectId": 1,
  "status": "SUCCESS"
}
```

게임 종료 직후 개인 결과 화면은 이 API를 사용하는 것이 적합합니다. 전체 결과를 가져와 클라이언트에서 내 ID를 찾는 방식보다 목적이 분명합니다.

### 5.3 최종 결과 상태

결과 API에 저장되는 상태는 세 가지뿐입니다.

| 상태 | 뜻 | 사용자 문구 예시 |
| --- | --- | --- |
| `SUCCESS` | 과목 신청 성공 | “신청에 성공했어요.” |
| `FAIL_SOLDOUT` | 과목 정원이 이미 마감됨 | “아쉽게도 정원이 마감되었어요.” |
| `FAIL_DUPLICATE` | 이 게임에서 이미 다른 과목 신청을 성공함 | “한 게임에서는 한 과목만 신청할 수 있어요.” |

`PENDING`, `WAITING`, `BLOCKED`는 게임 중 통신 상태이므로 결과 API에는 저장되지 않습니다.

## 6. 기록·대시보드·통계 API

이 API들은 모두 결과 데이터만 읽습니다. 폴링이 필요하지 않으며, 화면 진입 또는 사용자의 새로고침 동작에 맞춰 호출하면 됩니다.

### 6.1 내 참여 기록

`GET /api/v1/multigame/my/history?page=0&size=10`

- `page`: 0부터 시작하는 페이지 번호
- `size`: 페이지 크기
- `size`를 생략하면 기본값은 10입니다.

성공 응답:

```json
{
  "data": [
    {
      "multigameId": "20260731123000",
      "subjectId": 1,
      "status": "SUCCESS",
      "participantCount": 23,
      "finalizedAt": "2026-07-31T03:30:20Z"
    }
  ],
  "page": {
    "pageNumber": 0,
    "pageSize": 10,
    "totalElements": 27,
    "totalPages": 3,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

| 필드 | 설명 |
| --- | --- |
| `multigameId` | 참여한 게임 |
| `subjectId` | 선택한 과목 |
| `status` | 최종 결과 |
| `participantCount` | 해당 게임의 확정 참가자 수 |
| `finalizedAt` | 결과 확정 시각 |

무한 스크롤은 `page.hasNext`가 `false`가 되면 중단합니다. 페이지 번호는 `page.pageNumber`를 기준으로 다음 값을 계산합니다.

### 6.2 내 통계

`GET /api/v1/multigame/my/stats`

성공 응답 `data`:

```json
{
  "totalGames": 10,
  "successCount": 4,
  "failSoldoutCount": 5,
  "failDuplicateCount": 1,
  "successRate": 40.0,
  "mostRequestedSubject": 1,
  "subjectBreakdown": [
    { "subjectId": 1, "count": 5, "success": 3 }
  ]
}
```

| 필드 | 설명 |
| --- | --- |
| `totalGames` | 결과가 저장된 내 참여 게임 수 |
| `successCount` | 성공 횟수 |
| `failSoldoutCount` | 정원 마감 실패 횟수 |
| `failDuplicateCount` | 중복 신청 실패 횟수 |
| `successRate` | 성공률. 퍼센트 값(0~100) |
| `mostRequestedSubject` | 가장 많이 신청한 과목. 기록이 없으면 `null` |
| `subjectBreakdown` | 과목별 총 신청·성공 횟수 |

성공률은 이미 퍼센트 단위이므로 프론트에서 다시 100을 곱하지 마세요.

### 6.3 대시보드

`GET /api/v1/multigame/dashboard`

성공 응답 `data`:

```json
{
  "recentGames": [
    {
      "multigameId": "20260731123000",
      "participantCount": 23,
      "capacity": 11,
      "finalizedAt": "2026-07-31T03:30:20Z"
    }
  ],
  "myRecentResults": [
    {
      "multigameId": "20260731123000",
      "subjectId": 1,
      "status": "SUCCESS",
      "finalizedAt": "2026-07-31T03:30:20Z"
    }
  ],
  "overallStats": {
    "totalGames": 100,
    "totalParticipants": 2100,
    "averageParticipants": 21.0
  }
}
```

- `recentGames`: 최근 종료된 게임 목록입니다.
- `myRecentResults`: 내 최근 결과 최대 5개입니다.
- `averageParticipants`: 소수점 둘째 자리까지 반올림된 평균 참가자 수입니다.

### 6.4 학과별 참여 순위

`GET /api/v1/multigame/stats/department/participation`

성공 응답 `data`:

```json
{
  "rankings": [
    { "rank": 1, "department": "컴퓨터공학과", "participationCount": 120 }
  ],
  "myDepartment": {
    "department": "컴퓨터공학과",
    "participationCount": 120,
    "rank": 1
  }
}
```

- `rankings`는 상위 10개 학과입니다.
- `myDepartment`는 내 학과 정보이며, 내 학과 정보가 없으면 `null`일 수 있습니다.
- 내 학과가 상위 10위 밖이어도 `myDepartment`는 별도로 표시할 수 있습니다.

### 6.5 학과별 성공률 순위

`GET /api/v1/multigame/stats/department/success-rate`

성공 응답 `data`:

```json
{
  "rankings": [
    {
      "rank": 1,
      "department": "컴퓨터공학과",
      "totalCount": 120,
      "successCount": 80,
      "successRate": 66.6666666667
    }
  ],
  "myDepartment": {
    "department": "컴퓨터공학과",
    "totalCount": 120,
    "successCount": 80,
    "successRate": 66.6666666667,
    "rank": 1
  }
}
```

`successRate`는 퍼센트(0~100)입니다. 화면에는 예를 들어 소수점 첫째 자리 또는 둘째 자리로 포맷해 표시하면 됩니다.

## 7. 에러 처리 명세

| HTTP 상태 | 코드 | 발생 상황 | 필수 처리 |
| --- | --- | --- | --- |
| 400 | `MULTIGAME_003` | 예약 시각이 10분 전보다 가깝거나 7일보다 멀다 | 예약 버튼 복구, 가능한 시각 안내 |
| 404 | `MULTIGAME_001` | 존재하지 않는 예약을 대상으로 한 요청 | 현재 제공된 프론트 흐름에서는 일반적으로 발생하지 않음 |
| 404 | `MULTIGAME_007` | 결과가 없거나 아직 저장되지 않음 | 결과 전환 직후라면 제한적으로 재시도, 그 외 결과 없음 안내 |
| 409 | `MULTIGAME_002` | 같은 게임을 중복 예약 | “이미 예약한 게임입니다” 안내 |
| 409 | `MULTIGAME_005` | 현재 상태에서 허용되지 않는 동작 | 화면 상태를 새로고침하고 사용자에게 안내 |
| 410 | `MULTIGAME_006` | 게임이 취소됨 | 대기방·신청 타이머 전부 중단, 취소 화면으로 이동 |
| 500 | `MULTIGAME_008` | 게임 처리 로직 실행 실패 | 신청 반복 중단, 재시도 버튼 또는 일반 오류 안내 |

공통 네트워크 오류(오프라인, 타임아웃, 5xx)는 서버의 게임 취소와 다릅니다. 다음처럼 구분하세요.

- **410**: 게임 취소가 확정됨. 절대 자동 재시도하지 말고 흐름을 종료합니다.
- **일시적 네트워크 오류**: 현재 신청 루프는 중복 실행하지 않도록 멈춘 뒤, 사용자가 재시도할 수 있게 합니다. 게임 진행 시간이 짧으므로 백그라운드 무한 재시도는 피합니다.
- **결과 조회의 404**: 종료 직후라면 서버 저장 중일 수 있으므로 짧은 재시도는 가능합니다.

## 8. 타이머와 요청 정리 규칙

멀티게임 화면에서 가장 흔한 문제는 화면을 떠난 뒤에도 폴링이 계속되는 것입니다. 아래 리소스 정리 표를 구현 기준으로 사용하세요.

| 작업 | 시작 시점 | 반복 주기 | 중단 시점 |
| --- | --- | --- | --- |
| 대기방 폴링 | 대기방 진입 직후 | 3초 | `ENDED`, `FINALIZE`, 410, 화면 이탈, 로그아웃 |
| 신청 반복 | 과목 선택 후 첫 `PENDING` | 권장 0.5~1초 | 최종 상태, `BLOCKED`, 오류, 화면 이탈, 로그아웃 |
| 결과 조회 재시도 | 종료 직후 결과가 404일 때만 | 제품 정책에 따른 짧은 간격 | 성공, 제한 횟수 도달, 화면 이탈 |

대기방 폴링과 신청 반복은 동시에 존재할 수 있습니다. 예를 들어 `PROGRESS`에서 과목을 신청하는 동안에도 대기방 폴링은 접속 상태를 유지하기 위해 계속 돌 수 있습니다. 다만 종료가 감지되면 두 작업을 모두 중단해야 합니다.

권장 상태 모델:

```ts
type MultigameUiState = {
  gameId: string | null;
  gameState: 'WAITING' | 'READY' | 'PROGRESS' | 'ENDED' | 'FINALIZE' | null;
  participation: number;
  selectedSubjectId: number | null;
  requestStatus: GameRequestResponse['status'] | null;
  isWaitingRoomPolling: boolean;
  isRequesting: boolean;
};
```

`CANCELLED`은 성공 응답 상태로 받지 못할 수 있으므로, 별도 화면 상태 `isCancelled` 또는 에러 상태를 두는 편이 안전합니다.

## 9. QA 시나리오

### 예약

- [ ] 10분 단위의 유효한 미래 게임을 예약하면 성공한다.
- [ ] 같은 게임을 다시 예약하면 `409 MULTIGAME_002`를 안내한다.
- [ ] 시작 10분 이내 게임을 예약하면 `400 MULTIGAME_003`을 안내한다.
- [ ] 7일 초과 게임을 예약하면 `400 MULTIGAME_003`을 안내한다.
- [ ] 성공한 예약이 내 예약 목록에 보인다.

### 대기방

- [ ] 진입 시 즉시 대기방 API를 호출한다.
- [ ] 대기방이 열려 있는 동안 3초 간격으로 호출한다.
- [ ] `WAITING`, `READY`, `PROGRESS`마다 안내와 버튼 상태가 올바르게 바뀐다.
- [ ] `PROGRESS`에서만 과목 버튼이 활성화된다.
- [ ] 410 응답을 받으면 모든 관련 타이머가 멈추고 취소 화면으로 이동한다.
- [ ] 라우트 이동/언마운트 후 네트워크 요청이 더 발생하지 않는다.

### 신청

- [ ] 과목 선택 뒤 첫 신청 요청이 한 번만 시작된다.
- [ ] `PENDING`이면 같은 `subjectId`로 다시 요청한다.
- [ ] 대기 중 다른 과목을 선택할 수 없다.
- [ ] `SUCCESS`에서 성공 UI가 표시되고 반복이 멈춘다.
- [ ] `FAIL_SOLDOUT`, `FAIL_DUPLICATE`에서 각각 맞는 안내가 표시되고 반복이 멈춘다.
- [ ] `BLOCKED` 또는 종료 상태에서 반복이 멈춘다.
- [ ] 화면 이탈 중에는 신청 반복이 멈춘다.

### 결과·기록

- [ ] `FINALIZE` 이후 개인 결과를 조회해 표시한다.
- [ ] 종료 직후 404 결과는 제한적으로 재시도한다.
- [ ] 기록 목록은 `hasNext`가 false가 되면 추가 요청하지 않는다.
- [ ] 성공률이 0~100 퍼센트 값으로 표시된다.
- [ ] 학과 정보가 `null`이어도 화면이 깨지지 않는다.

## 10. 개발 환경 전용 API

아래 API는 `dev` 프로필과 관리자 권한에서만 제공됩니다. 일반 사용자용 프론트나 운영 배포본에서는 사용하지 마세요.

| 목적 | 메서드·경로 |
| --- | --- |
| 특정 게임 상태 조회 | `GET /api/v1/multigame/lifecycle/state/{multigameId}` |
| 특정 게임 상태 수동 전이 | `POST /api/v1/multigame/lifecycle/transition/{multigameId}?targetState=READY` |

수동 전이의 `targetState`는 `WAITING`, `READY`, `PROGRESS`, `ENDED`, `FINALIZE`, `CANCELLED` 중 하나입니다. 이 API는 프론트 QA에서 상태별 UI를 빠르게 확인할 때만 사용합니다.

## 11. 구현 전 최종 체크리스트

- [ ] 모든 응답에서 최상위 `data`를 읽는다.
- [ ] 예약에서는 14자리 `multigameId`를 보낸다.
- [ ] 게임 중 API에는 `multigameId`를 보내지 않는다.
- [ ] 대기방 화면에서 3초 폴링을 유지한다.
- [ ] `PROGRESS`일 때만 신청 UI를 연다.
- [ ] `PENDING`이면 같은 과목으로 요청을 반복한다.
- [ ] `SUCCESS`, 실패, `BLOCKED`, 오류에서는 신청 반복을 멈춘다.
- [ ] 410 취소 응답에서 모든 타이머를 정리한다.
- [ ] 결과는 종료 직후 바로 없을 수 있음을 처리한다.
- [ ] 기록·통계 화면에는 실시간 폴링을 붙이지 않는다.
- [ ] 개발 전용 lifecycle API를 운영 화면에서 호출하지 않는다.
