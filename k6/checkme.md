# k6 부하 테스트 및 도메인 시나리오 가이드

본 문서는 프로젝트의 부하 테스트 스크립트(k6) 구성, 최신 도메인 명세([`docs/multigame.md`](file:///Users/shinnk/source/project/mju-sugangsincheong-heler/mju-sugangsincheong-helper-backend/docs/multigame.md)), 그리고 표준 k6 실행 규칙을 정의합니다.

---

## 1. k6 기본 작성 규범 (k6 Standard Rules)

k6 성능 테스트 작성 시 반드시 준수하는 5대 표준 규칙입니다:

1. **옵션 정의 (`options`)**: `stages`(Ramp-up -> Steady -> Ramp-down)와 `thresholds`(SLA 성능 목표)를 필수 정의합니다.
2. **응답 검증 (`check`)**: 모든 HTTP 요청에 대해 상태 코드(`200 OK`) 및 응답 메타데이터를 `check()` 함수로 검증합니다.
3. **요청 태깅 (`tags`)**: HTTP 메트릭 구분을 위해 모든 요청 옵션에 `tags: { name: 'API_NAME' }`을 명시합니다.
4. **생존 주기 (Life Cycle)**:
   - `Init Phase`: 환경 변수(`__ENV`), 모듈 import 및 스크립트 로드
   - `Setup Phase`: 테스트 전역 공통 토큰/데이터 생성 (`setup()`)
   - `VU Execution Phase`: 가상 유저(VU) 동작 반복 (`export default function()`)
   - `Teardown Phase`: 테스트 후 정리 (`teardown()`)
5. **실시간성 조율 (`sleep`)**: 실제 유저 동작 패턴을 모사하기 위해 폴링/신청 주기 간 적절한 `sleep(duration)`을 부여합니다.

---

## 2. k6 디렉터리 및 실행 구조

```
k6/
├── run.sh                          # 통합 부하 테스트 실행 스크립트
├── report/                         # 테스트 실행 결과 JSON 저장소
└── script/
    ├── common/
    │   └── login.js                # 게스트/회원 인증 유틸리티 (guestLogin 등)
    ├── multigame/
    │   ├── load-profiles.js        # Multigame 전용 VU stage & threshold 프로필
    │   ├── helpers.js              # Multigame API 호출 래퍼
    │   └── scenarios/
    │       ├── session.js          # 실시간 대기방 폴링 + 게임 수강신청
    │       ├── reservation.js      # 7일~10분 전 사전 예약 CRUD
    │       ├── result.js           # 내 게임 기록 & 통계 조회
    │       └── full-flow.js        # 전체 세션 라이프사이클 통합 시나리오
    ├── exchange/
    │   ├── load-profiles.js
    │   ├── helpers.js
    │   └── scenarios/
    └── singlegame/
        ├── load-profiles.js
        ├── helpers.js
        └── scenarios/
```

---

## 3. 멀티게임(`multigame`) 도메인 시나리오 명세

최신 백엔드 아키텍처([`docs/multigame.md`](file:///Users/shinnk/source/project/mju-sugangsincheong-heler/mju-sugangsincheong-helper-backend/docs/multigame.md))와 1:1로 대응되는 시나리오 및 성능 지표입니다.

### 3.1 세션 및 실시간 게임 신청 (`scenarios/session.js`)
- **도메인 특징**: 
  - `T` 10분 마크 세션 (`:00`, `:10`, `:20` ...)
  - 3초 폴링 대기방 (`GET /api/v1/multigame/session/waiting-room`) → Heartbeat 갱신 (TTL 6s)
  - 20초간 공급 엔진 진행 시 통합 수강신청 API (`POST /api/v1/multigame/session/request`) 지속 연타
- **SLA Thresholds**:
  - 대기방 폴링: `http_req_duration: ['p(95)<300']` (300ms 이내)
  - 수강신청: `http_req_duration: ['p(95)<200']` (200ms 이내 - Lua 스크립트 처리)
  - 에러율: `http_req_failed: ['rate<0.01']` (1% 미만)

### 3.2 사전 예약 (`scenarios/reservation.js`)
- **도메인 특징**: 게임 7일 전 ~ 10분 전 DB 사전 예약 CRUD (`MULTIGAME_RESERVATION` 테이블)
- **SLA Thresholds**: `http_req_duration: ['p(95)<500']`

### 3.3 정산 및 통계 (`scenarios/result.js`)
- **도메인 특징**: 내 참여 기록 조회 (`GET /api/v1/multigame/my/history`) 및 통계 조회 (`GET /api/v1/multigame/my/stats`)

---

## 4. k6 시나리오 코드 표준 작성 예시

### `script/multigame/scenarios/session.js`
```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';
import { guestLogin } from '../../common/login.js';
import { profiles, thresholds } from '../load-profiles.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const tier = __ENV.LOAD_TIER || 'small';

export const options = {
  ...profiles[tier],
  thresholds: thresholds.session,
};

export function setup() {
  // 1. 게스트 로그인 인증 토큰 발급
  const token = guestLogin();
  return { token: token };
}

export default function (data) {
  const params = {
    headers: {
      'Authorization': `Bearer ${data.token}`,
      'Content-Type': 'application/json',
    },
    tags: { name: 'GET_WaitingRoom' },
  };

  // 2. 대기방 3초 주기 폴링 (Heartbeat 갱신)
  const waitRes = http.get(`${BASE_URL}/api/v1/multigame/session/waiting-room`, params);
  
  check(waitRes, {
    'waiting room status is 200': (r) => r.status === 200,
    'has valid state': (r) => r.json('data.state') !== undefined,
  });

  const state = waitRes.json('data.state');

  // 3. PROGRESS 상태 전이 시 수강신청 연타
  if (state === 'PROGRESS') {
    const requestParams = {
      ...params,
      tags: { name: 'POST_GameRequest' },
    };
    const payload = JSON.stringify({ subjectId: Math.floor(Math.random() * 6) + 1 });
    
    const reqRes = http.post(`${BASE_URL}/api/v1/multigame/session/request`, payload, requestParams);
    
    check(reqRes, {
      'game request handled': (r) => r.status === 200,
    });
  }

  sleep(3); // 3초 대기방 폴링 주기 모사
}
```

---

## 5. 실행 및 부하 프로필 (`run.sh`)

### 5.1 명령어 가이드
```bash
# 멀티게임 세션 시나리오 실행 (small 부하)
./run.sh multigame.session small

# 멀티게임 전체 시나리오 실행 (middle 부하)
./run.sh multigame middle

# 최대 스트레스 테스트 (large 부하 + VU 4000)
VU_MAX=4000 ./run.sh multigame.session --max
```

### 5.2 부하 티어 정의
- **`small`**: Stage 10 VU, 지속시간 2분 (CI/CD 및 경량 기능 검증용)
- **`middle`**: Stage 30 VU, 지속시간 4분 (일반 부하 시뮬레이션)
- **`large`**: Stage 100 VU, 지속시간 7분 (부하 테스트)

