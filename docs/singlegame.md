# SingleGame Domain

## 수강신청 싱글 게임

UI를 모방하는 기능에서 나아가 사용자의 클릭 반응 속도를 밀리초(ms) 단위로 측정하고, 해당 데이터를 서버에 전송해 사용자 간 순위를 산출하는 기능과 해당 기록을 바탕으로 분석을 제공한다.

### 싱글게임에서 측정할 요소 및 기준

과목 개수 종목은 (1, 3, 6, 7, 8) 5개로 고정한다. 모든 과목을 수강신청 성공한다고 가정한다.

수강신청 flow가 명확하게 있지만, 싱글게임이므로 실제 서버측으로 요청이 가지 않는다. 즉, 지연 또한 없다는 뜻이다. 실제 사용자가 많으면 대기열은 언제 어디서 구현할지가 고민이다.

이것을 해결하기 위해 첫번째 alert "xxxx 과목을 수강신청 하시겠습니까?" 와 수강신청 사이에 서버측 요청 대신 의도적 지연이 필요하다. 단 이때 기존 화면을 loading을 띄워서 동시에 여러개의 요청을 가지 못하게 해야 한다.

또한 대기열의 경우 모든 과목을 신청할때 나오기 보다는 50% 정도만 수강 신청시에 대기열이 뜨게 하는 것이 좋다. 위처럼 이것도 측정시간에서 제외한다.

**측정 시퀀스:**
- 기준 시간이 됨 (대기방에서 main 방 입장 허용 시간이 됨)
- `t_enter_main`: 대기방에서 main 방 입장 사용자 반응 속도
- 사용자가 대기방에 입장
- `t_click_course`: 첫번째 과목 누르는 시간
- 첫번째 과목 신청 버튼을 누름
- alert "xxx 과목을 신청하시겠습니까?"
- `t_click_yes`: 사용자가 첫번째 alert를 처리하는 반응속도
- 가짜 서버측 요청 동기적 지연처리
- alert "수강 신청 되었습니다"
- `t_click_ok`: 사용자가 2번째 alert를 처리하는 반응속도
- 여기서부터는 다시 위 과정 반복

`t_enter_main`만 전체에서 1개가 존재하고, `t_click_course`, `t_click_yes`, `t_click_ok`는 배열이어야 하며 과목 개수와 동일한 개수가 있어야 한다.

과목을 전부 수강신청 완료하거나 60초 타임아웃되면 서버로 결과 전송한다.

---

### 게임 진행 Flow 및 시간 측정 규칙

모든 과목은 최종적으로 수강신청에 성공하는 것으로 가정하며, 총 과목 개수 종목은 **1개, 3개, 6개, 7개, 8개**의 5가지로 고정된다.

```
[대기방 대기] -> (정각 활성화) -> [메인방 진입 버튼 클릭] -> [과목 목록 선택] -> [1차 확인 팝업 승인] -> (가짜 지연) -> [2차 완료 팝업 확인] -> [다음 과목 반복]
```

1. **메인 방 진입 단계**
   * 대기방에서 메인 방 입장 버튼이 활성화되는 기준 시점(0ms)부터 사용자가 버튼을 클릭하는 순간까지의 반응 시간인 `t_enter_main`을 단 1회 측정한다.

2. **과목 신청 반복 단계 (총 N회 반복)**
   * **t_click_course**: 메인방 진입 완료(또는 이전 과목 완료 창 닫기) 시점부터 대상 과목의 '신청' 버튼을 누를 때까지의 조준 시간.
   * **t_click_yes**: "신청하시겠습니까?" 1차 알림창이 나타난 시점부터 사용자가 '확인'을 누르기까지의 반응 시간.
   * **의도적 지연 구간 (측정 제외)**:
     * 실제 서버 요청에 따른 병목을 시뮬레이션하기 위해, 1차 알림창 확인 후 50%의 확률로 '가짜 대기열 컴포넌트'를 1~2초간 띄운다.
     * 대기열이 뜨지 않는 50%의 경우에도 약 200ms의 네트워크 고정 지연 처리를 수행한다.
     * **해당 대기열 및 로딩 시간은 유저의 피지컬 반응 속도 측정치에서 제외된다.**
   * **t_click_ok**: "수강신청 완료되었습니다" 2차 알림창이 나타난 시점부터 사용자가 '확인'을 누르기까지의 연타 속도.

3. **종료 조건**
   * N개의 과목을 모두 성공적으로 신청 완료하거나, 플레이 시간 기준 60초가 경과(타임아웃, `is_completed = false`)하면 게임이 종료되고 결과 데이터가 서버로 전송된다.

---

### 등수 및 백분위 (Ranking & Percentile)

동일한 `totalCourses` 종목의 전체 완료 게임 중 `totalTime` 기준으로 순위와 백분위를 산출한다.

- **백분위 공식:** $Percentile = \frac{rank - 1}{totalParticipants} \times 100$ (0% = 최고, 100% = 최하)
- `detail`의 각 이벤트는 `percentile` 값을 포함하여 전체 유저 대비 자신의 위치를 제공한다.

---

### 피드백 코드 및 메시지 정의 (Feedback Rules)

분석 페이지에서 사용자에게 제공될 맞춤형 피드백 코드는 다음 3가지 축을 기준으로 평가되어 반환된다.

평가 기준은 전체 통계를 바탕으로 환산된 **상위 백분위(Percentile, %)** 를 사용한다. **(0%에 가까울수록 가장 빠름/우수함, 100%에 가까울수록 가장 느림/취약함)**

* **판단 기준:** `Percentile <= 30` (잘함/빠름), `30 < Percentile < 70` (보통), `Percentile >= 70` (못함/느림)
* **조건 적용:** 각 축에서 1번부터 5번 순서대로 조건을 검사하여, **가장 먼저 만족하는 단 1개의 코드만 반환**한다. (상호 배타적 보장)

#### 1. 피지컬 밸런스 (Aiming vs. Burst) - *순수 total 속도를 기준*

**지표 정의:**
* $Aim_{p}$: 과목 조준 속도(t_click_course) 평균의 상위 백분위 (%)
* $Burst_{p}$: 팝업 연타 속도(t_click_yes + t_click_ok) 평균의 상위 백분위 (%)

**분석 코드 (우선순위 검사):**

| 순위 | 코드 | 조건 | 메시지 |
|------|------|------|--------|
| 1순위 | `GOD_TIER_PHYSICAL` | $Aim_{p} \le 30$ AND $Burst_{p} \le 30$ | 압도적이고 완벽한 피지컬! 에이밍과 팝업 연타 모두 최상위권입니다. 수강신청 실패는 당신의 사전에 없습니다. |
| 2순위 | `PHYSICAL_UPGRADE_NEEDED` | $Aim_{p} \ge 70$ AND $Burst_{p} \ge 70$ | 전체적인 피지컬 반응 속도가 아쉽습니다. 꾸준한 연습을 통해 마우스 에임과 키보드 반응 속도를 모두 끌어올려 보세요. |
| 3순위 | `FAST_BUT_INACCURATE` | $Aim_{p} \ge 70$ AND $Burst_{p} \le 30$ | 팝업을 넘기는 손놀림은 최상위권이지만, 마우스 에임이 크게 흔들려 시간을 뺏기고 있습니다. 침착하게 다음 과목을 조준해 보세요. |
| 4순위 | `SLOW_AIM` | 위 조건에 해당하지 않으며, $Aim_{p} > Burst_{p}$ | 팝업 연타 속도에 비해 리스트에서 다음 과목을 찾아 조준하는 에임(Aim) 속도가 상대적으로 지체됩니다. 다음 마우스 위치를 미리 예측하세요! |
| 5순위 | `SLOW_BURST` | 위 1~4순위 조건에 모두 해당하지 않는 나머지 | 과목 조준은 안정적이지만, 팝업창을 처리하는 연타 반응이 상대적으로 아쉽습니다. 엔터키나 마우스 좌클릭을 더 빠르게 누르는 감각을 익혀보세요. |

#### 2. 진입 및 초반 (Entry & Start) - *대기방 진입 속도와 1순위 과목 선점력 평가*

**지표 정의:**
* $E_{p}$: 메인방 진입 반응속도의 상위 백분위 (%)
* $Start_{p}$: 1순위 과목 총 처리 속도의 상위 백분위 (%)

**분석 코드 (우선순위 검사):**

| 순위 | 코드 | 조건 | 메시지 |
|------|------|------|--------|
| 1순위 | `PERFECT_ENTRY_START` | $E_{p} \le 30$ AND $Start_{p} \le 30$ | 완벽에 가까운 정각 진입과 압도적인 1순위 과목 선점! 수강신청 도입부의 지배자입니다. |
| 2순위 | `ENTRY_MASTER_START_NOVICE` | $E_{p} \le 30$ AND $Start_{p} \ge 70$ | 메인방 진입 타이밍은 완벽했으나, 정작 가장 중요한 1순위 과목 클릭에서 크게 머뭇거렸습니다. 진입 후 첫 클릭까지의 동선을 최소화하세요. |
| 3순위 | `ENTRY_LATE_START_MASTER` | $E_{p} \ge 70$ AND $Start_{p} \le 30$ | 진입 타이밍은 다소 늦었지만 경이로운 반응속도로 1순위 과목을 낚아챘습니다. 시작 알림에 조금만 더 귀를 기울여 진입 속도를 보완해 보세요. |
| 4순위 | `NEED_FASTER_ENTRY` | $E_{p} \ge 70$ AND $Start_{p} > 30$ | 메인방 진입 속도가 늦어 시작부터 남들보다 불리한 포지션에 놓였습니다. 버튼이 활성화되는 즉시 반응하는 훈련이 필요합니다. |
| 5순위 | `START_HESITATION` | 위 1~4순위 조건에 모두 해당하지 않는 나머지 | 진입 타이밍은 보통 수준으로 무난했으나 1순위 과목을 선점하는 속도가 폭발적이지 못합니다. 가장 치열한 첫 과목에 모든 집중을 쏟으세요! |

#### 3. 페이스 및 멘탈 제어 (Pace & Focus) - *집중력 유지, 템포의 기복 평가 (N ≥ 3 이상일 때 활성화)*

**지표 정의:**
* $\sigma_{p}$: 페이스 유지도(표준편차)의 상위 백분위 (%) *(0%에 가까울수록 편차가 적고 일정함)*
* $\mu$: 과목별 처리 시간($T_i$)의 평균값
* $T_{\text{first\_half}}$: 전반부 과목 평균 시간, $T_{\text{second\_half}}$: 후반부 과목 평균 시간

**분석 코드 (우선순위 검사):**

| 순위 | 코드 | 조건 | 메시지 |
|------|------|------|--------|
| 1순위 | `MACHINE_LIKE_PACE` | $\sigma_{p} \le 30$ | 기복이 거의 없는 완벽한 페이스! 흔들리지 않는 멘탈로 모든 과목을 기계처럼 정교하게 처리했습니다. |
| 2순위 | `EASY_PANIC` | 가짜 지연을 겪은 직후의 과목 순서 i에서 $T_i > \mu + 1.5\sigma$ 발생 시 | 중간에 가짜 대기열이나 딜레이를 겪은 직후 템포가 무너지는 경향이 있습니다. 어떠한 변수에도 침착하게 다음 과목을 준비하는 멘탈 관리가 필요합니다. |
| 3순위 | `STRONG_FINISHER` | $T_{\text{first\_half}} - T_{\text{second\_half}} \ge 100\text{ms}$ | 초반보다 후반부 과목으로 갈수록 오히려 속도가 빨라지는 강력한 뒷심을 보여주었습니다. 초반의 긴장감만 극복해 보세요. |
| 4순위 | `WEAK_FINISHER` | $T_{\text{second\_half}} - T_{\text{first\_half}} \ge 100\text{ms}$ | 시작은 좋았으나 후반부로 갈수록 집중력이 급격히 떨어지는 페이스 저하가 보입니다. 마지막 과목을 끝낼 때까지 긴장의 끈을 놓지 마세요. |
| 5순위 | `FLUCTUATING_PACE` | 위 1~4순위 조건에 모두 해당하지 않는 나머지 | 과목별 소요 시간의 기복이 다소 존재합니다. 일정한 리듬을 찾지 못하고 특정 과목에서 당황했을 가능성이 높습니다. |

---

## 도메인 개념

### 게임 플레이 흐름

1. **진입 단계**: 수강신청 메인방에 진입하는 데 걸린 시간 측정 (`tEnterMain`)
2. **과목 선택 단계**: 각 과목별로 다음 순서로 진행
   - 과목 리스트에서 목표 과목을 찾아 클릭 (`tClickCourse`)
   - 팝업창에서 "예" 버튼 클릭 (`tClickYes`)
   - 확인 팝업에서 "확인" 버튼 클릭 (`tClickOk`)
3. **반복**: 설정된 과목 수(`totalCourses`)만큼 위 과정 반복
4. **완료**: 모든 과목 선택 완료 또는 중도 포기

### 측정 지표

4가지 원시 측정값은 사용자의 게임 경험 단위(진입 → 과목별 조준 → 확인 → 완료)를 그대로 반영한다.
분석 화면에서 사용자에게 보여줄 때는 내부 필드명(`tEnterMain`) 대신 **사용자향 표시명(`entrySpeed`)** 을 사용한다.

| 내부 필드명 | 사용자향 표시명 | 설명 | 게임 내 발생 위치 | 측정 횟수 | 단위 |
|------------|---------------|------|----------------|----------|------|
| `tEnterMain` | `entrySpeed` | 메인방 진입 반응 속도 | 게임 시작 직후, 진입 버튼 클릭 | 전체 1회 | ms |
| `tClickCourse` | `aimSpeed` | 과목 찾아서 클릭하는 조준 속도 | 과목 리스트에서 '신청' 버튼 클릭 | 과목별 1회 (총 N회) | ms |
| `tClickYes` | `confirmSpeed` | '수강신청 하시겠습니까?' 팝업 반응 속도 | 1차 확인 팝업 '예' 버튼 클릭 | 과목별 1회 (총 N회) | ms |
| `tClickOk` | `completeSpeed` | '수강신청 되었습니다' 팝업 반응 속도 | 2차 완료 팝업 '확인' 버튼 클릭 | 과목별 1회 (총 N회) | ms |

**보조 메트릭:**

| 메트릭 | 설명 | 산출 방식 | 단위 |
|--------|------|----------|------|
| `totalTime` | 게임 전체 순수 플레이 시간 | `tEnterMain` + 모든 과목의 (tClickCourse + tClickYes + tClickOk) 합계 (의도적 지연 제외) | ms |
| `purePhysicalAverage` | 과목당 평균 피지컬 소요 시간 | 과목별 (tClickCourse + tClickYes + tClickOk)의 평균값 | ms |

---

## 데이터 모델

### SingleGameEntity

게임 한 판에 대한 메타데이터를 저장합니다.

| 필드 | 타입 | 설명 |
|------|------|------|
| `id` | Long | 게임 고유 식별자 |
| `memberId` | Long | 플레이어 식별자 |
| `tTotal` | int | 총 소요 시간 (ms) |
| `tEnterMain` | int | 메인방 진입 시간 (ms) |
| `isCompleted` | boolean | 게임 완료 여부 |
| `totalCourses` | int | 선택한 과목 수 (1, 3, 6, 7, 8 중 하나) |
| `createdAt` | Instant | 게임 생성 시각 |
| `updatedAt` | Instant | 마지막 수정 시각 |

### SingleGameDetailEntity

각 과목별 상세 반응 시간을 저장합니다. 복합 기본키(`gameId`, `sequence`)를 사용합니다.

| 필드 | 타입 | 설명 |
|------|------|------|
| `gameId` | Long | 부모 게임 ID (FK) |
| `sequence` | int | 과목 순서 (1부터 시작) |
| `tClickCourse` | int | 과목 클릭 시간 (ms) |
| `tClickYes` | int | "예" 버튼 클릭 시간 (ms) |
| `tClickOk` | int | "확인" 버튼 클릭 시간 (ms) |

### 관계

```
SingleGameEntity (1) ──────< (N) SingleGameDetailEntity
     │                              │
     └── Cascade Delete ────────────┘
```

> **참고:** `department`(학과) 정보는 `SingleGameEntity`에 직접 저장되지 않으며, `Member` 엔티티를 통해 조회한다.
> 게스트 회원의 경우 `department`는 `null`이므로 DEPARTMENT 랭킹 집계 및 조회에서 제외된다.

---

## 회원 유형별 동작 차이

회원은 **인증 회원(명지대 구글 로그인, 학과 정보 있음)** 과 **게스트(학과 정보 없음)** 으로 구분된다.
게스트는 학과 정보가 없으므로 DEPARTMENT 관련 기능에 제한이 있으며, 아래 표에 따라 동작한다.

| 기능 | 인증 회원 (학과 O) | 게스트 (학과 NULL) |
|------|-------------------|-------------------|
| GLOBAL 랭킹 참여 | ⭕ 정상 집계 | ⭕ 정상 집계 |
| DEPARTMENT 랭킹 참여 | ⭕ 소속 학과에 집계 | ❌ 집계 제외 |
| 학과 목록 조회 | ⭕ 전체 학과 목록 조회 가능 | ⭕ 전체 학과 목록 조회 가능 |
| DEPARTMENT 랭킹 조회 | ⭕ 본인 학과 기준 조회 가능 | ❌ 불가 (에러 응답 또는 GLOBAL fallback 권장) |
| 내 기록/분석의 `ranking.department` | ⭕ 제공 | ❌ `null` 반환 |

**처리 원칙:**
1. 게스트의 게임 데이터는 `SingleGameEntity`에 정상 저장되며 GLOBAL 랭킹 집계에 포함된다.
2. 게스트의 `department`는 `null`이므로 DEPARTMENT 랭킹에서 제외된다. (`WHERE department IS NOT NULL`)
3. 클라이언트는 게스트 여부를 사전에 알 수 없으므로, 서버는 각 API 호출 시점에 요청한 회원의 유형을 감지하여 적절히 응답해야 한다.

---

## API 엔드포인트

### 1. 게임 결과 저장

```
POST /api/{version}/singlegame
```

**요청 본문:**
```json
{
  "totalCourses": 6,
  "isCompleted": true,
  "tEnterMain": 1500,
  "details": [
    {
      "sequence": 1,
      "tClickCourse": 800,
      "tClickYes": 200,
      "tClickOk": 150
    },
    ...
  ]
}
```

**유효성 검증 규칙:**
- `totalCourses`는 `[1, 3, 6, 7, 8]` 중 하나여야 함
- 완료된 게임(`isCompleted=true`)인 경우 `details` 개수는 `totalCourses`와 동일해야 함
- 미완료 게임인 경우 `details` 개수는 `totalCourses`보다 적어야 함
- 모든 시간 값은 `reactionTimeMinMs`(기본값: 1) ~ `reactionTimeMaxMs`(기본값: 60000) 범위 내여야 함

**처리 절차:**
1. 회원 존재 여부 확인
2. 요청 데이터 유효성 검증
3. `tTotal` 자동 계산 (모든 시간 값의 합)
4. `SingleGameEntity` 저장
5. `SingleGameDetailEntity` 일괄 저장
6. 트랜잭션 커밋 후 랭킹/기록 캐시 무효화
7. 생성된 게임 ID 반환

---

### 2. 학과 목록 조회

```
GET /api/{version}/singlegame/departments
```

**설명:** 싱글게임 데이터에 존재하는 모든 학과 목록을 조회합니다. DEPARTMENT 랭킹 조회 시 사용할 학과명을 확인할 수 있습니다.
> 게스트(department = null)의 게임 데이터는 학과 목록에서 제외된다.

**응답 구조:**
```json
{
  "departments": ["간호학과", "경영학과", "건축학과", "컴퓨터공학과", ...]
}
```

**처리 절차:**
1. 완료된 게임이 있는 학과 목록을 DISTINCT로 조회
2. 알파벳(가나다) 순으로 정렬
3. 결과 반환

---

### 3. 랭킹 조회

```
GET /api/{version}/singlegame/rank?totalCourses={totalCourses}&scope={scope}&department={department}
```

**파라미터:**
| 파라미터 | 필수 | 설명 |
|----------|------|------|
| `totalCourses` | O | 과목 수 (1, 3, 6, 7, 8) |
| `scope` | O | 조회 범위 (`GLOBAL` 또는 `DEPARTMENT`) |
| `department` | X | 학과명 (DEPARTMENT일 때, 없으면 본인 학과) |

**응답 구조:**
```json
{
  "totalCourses": 6,
  "scope": "GLOBAL",
  "rankings": [
    {
      "rank": 1,
      "gameId": 1234,
      "name": "사용자명",
      "department": "학과명",
      "tTotal": 8500,
      "tEnterMain": 1200
    }
  ],
  "myRank": {
    "rank": 42,
    "gameId": 5678,
    "tTotal": 12000,
    "tEnterMain": 1800
  },
  "subRankings": {
    "enterMainTop3": [...],
    "firstClickTop3": [...]
  }
}
```

**처리 절차:**
1. 캐시 확인 (키: `{totalCourses}:{scope}:cache`)
2. 캐시 미스 시 DB 조회
   - `GLOBAL`: 전체 완료 게임 조회 (게스트 포함)
   - `DEPARTMENT`: 현재 사용자의 학과와 동일한 게임만 조회
     - **게스트 요청 시:** `department = null`이므로 DEPARTMENT 조회가 불가능하다. 게스트에게 DEPARTMENT 랭킹을 제공할 수 없음을 응답하고 GLOBAL 랭킹을 대신 반환하거나 에러 응답을 반환한다.
3. 랭킹 계산 (tTotal 기준 오름차순)
4. 상위 20개 랭킹 추출
5. 내 최신 완료 게임의 랭킹 확인
6. `totalCourses >= 3`인 경우 보조 랭킹 생성
   - `enterMainTop3`: 메인방 진입 시간 상위 3명
   - `firstClickTop3`: 첫 과목 클릭 시간 상위 3명
7. 결과 반환 및 캐시 저장

---

### 4. 내 기록 조회

```
GET /api/{version}/singlegame/my?page={page}&size={size}
```

**응답 구조:**
```json
{
  "content": [
    {
      "gameId": 1234,
      "totalCourses": 6,
      "completed": true,
      "tTotal": 8500,
      "tEnterMain": 1200,
      "createdAt": "2024-01-15T10:30:00Z",
      "ranking": {
        "global": {
          "rank": 42,
          "totalParticipants": 1000,
          "percentile": 4.2
        },
        "department": {
          "rank": 3,
          "totalParticipants": 50,
          "percentile": 4.0
        }
      }
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 25
}
```

**처리 절차:**
1. 첫 페이지 요청(`page=0`, `size=10`)인 경우 캐시 확인
2. 캐시 미스 시 DB에서 페이징 조회
3. 각 게임에 대해 전체/학과 랭킹 계산
4. 퍼센타일 계산: `(rank - 1) / totalParticipants * 100`
5. 결과 반환

> **게스트 처리:** 게스트의 경우 `ranking.department`는 `null`로 응답한다.
> 프론트는 `department`가 `null`일 때 "명지대 구글 로그인시 학과내 랭킹도 볼 수 있어요"와 같은 안내를 표시할 수 있다.

---

### 5. 게임 상세 분석

```
GET /api/{version}/singlegame/{gameId}/analysis
```

**설명:**
응답은 `basic`(자신의 기록), `detail`(상세 분석), `feedbacks`(피드백) 3개 영역으로 구성된다.

| 응답 영역 | 설명 |
|----------|------|
| `basic` | 게임 전체 흐름을 플랫한 이벤트 배열로 표현 (sequence, type, label, durationMs) |
| `detail` | 각 이벤트별 percentile, grade, population 통계 포함 |
| `feedbacks` | 종합 평가 코드 및 메시지 (primary, secondary) |

**응답 구조:**
```json
{
  "gameId": 1234,
  "isOwner": true,
  "isMember": true,
  "totalCourses": 6,
  "totalTime": 8500,
  "ranking": {
    "global": { "rank": 42, "totalParticipants": 1000, "percentile": 4.2 },
    "department": { "rank": 3, "totalParticipants": 50, "percentile": 4.0 }
  },

  "basic": [
    { "sequence": 0, "type": "ENTRY",   "label": "메인방 진입",  "durationMs": 1500 },
    { "sequence": 1, "type": "AIM",     "label": "1순위 과목 조준", "durationMs": 800 },
    { "sequence": 1, "type": "CONFIRM", "label": "신청 확인",    "durationMs": 200 },
    { "sequence": 1, "type": "COMPLETE","label": "완료 확인",    "durationMs": 150 },
    { "sequence": 2, "type": "AIM",     "label": "2순위 과목 조준", "durationMs": 350 },
    { "sequence": 2, "type": "CONFIRM", "label": "신청 확인",    "durationMs": 180 },
    { "sequence": 2, "type": "COMPLETE","label": "완료 확인",    "durationMs": 140 }
  ],

  "detail": [
    {
      "sequence": 0, "type": "ENTRY", "label": "메인방 진입", "durationMs": 1500,
      "percentile": 15.2, "grade": "A",
      "global_population":     { "p10": 800, "p30": 1200, "p50": 1600, "p70": 2200 },
      "department_population": { "p10": 900, "p30": 1300, "p50": 1700, "p70": 2400 }
    },
    {
      "sequence": 1, "type": "AIM", "label": "1순위 과목 조준", "durationMs": 800,
      "percentile": 22.0, "grade": "A",
      "global_population":     { "p10": 400, "p30": 600, "p50": 850, "p70": 1200 },
      "department_population": { "p10": 450, "p30": 650, "p50": 900, "p70": 1300 }
    },
    {
      "sequence": 1, "type": "CONFIRM", "label": "신청 확인", "durationMs": 200,
      "percentile": 60.0, "grade": "B",
      "global_population":     { "p10": 100, "p30": 150, "p50": 210, "p70": 300 },
      "department_population": { "p10": 110, "p30": 160, "p50": 220, "p70": 320 }
    },
    {
      "sequence": 1, "type": "COMPLETE", "label": "완료 확인", "durationMs": 150,
      "percentile": 55.0, "grade": "B",
      "global_population":     { "p10": 80, "p30": 120, "p50": 160, "p70": 220 },
      "department_population": { "p10": 90, "p30": 130, "p50": 170, "p70": 240 }
    }
  ],

  "feedbacks": {
    "primary": {
      "code": "SLOW_BURST",
      "message": "과목 조준은 안정적이지만, 팝업창을 처리하는 연타 반응이 상대적으로 아쉽습니다.",
      "axis": "PHYSICAL"
    },
    "secondary": {
      "code": "WEAK_FINISHER",
      "message": "후반부로 갈수록 집중력이 떨어지는 페이스 저하가 보입니다.",
      "axis": "PACE"
    }
  }
}
```

**필드 설명:**
- `isOwner`: 요청한 사람이 이 게임의 소유자인지 여부
- `isMember`: 게임 소유주가 인증 회원인지 여부. `false`면 게스트의 게임

**`basic` 배열 규칙:**
- `sequence = 0`: ENTRY (메인방 진입, 전체 1개)
- `sequence = 1..N`: 각 과목별 AIM → CONFIRM → COMPLETE (과목 수 = totalCourses)
- 총 이벤트 수: `1 + (totalCourses * 3)`

**`detail` 배열 규칙:**
- `basic`과 동일한 순서/개수
- 각 이벤트에 `percentile`, `grade`, `global_population`, `department_population` 추가
- `department_population`: 게스트(department = null)인 경우 `null`

**grade 산출 기준 (percentile 기반):**

| 등급 | percentile 범위 |
|------|----------------|
| S | ≤ 5 |
| A | 5 < x ≤ 30 |
| B | 30 < x < 70 |
| C | 70 ≤ x < 95 |
| D | ≥ 95 |

**population percentiles:**
- `p10`: 전체(또는 학과) 하위 10%의 값
- `p30`: 하위 30%의 값
- `p50`: 중앙값
- `p70`: 하위 70%의 값

**피드백:**
- `primary`: 가장 우선순위가 높은 축의 피드백 (1순위)
- `secondary`: 1순위 축 다음으로 우선순위가 높은 축의 피드백 (2순위)
- 최대 2개 반환

> **게스트 처리:** 게스트의 경우 `ranking.department`, `detail[].department_population` 모두 `null`이다.

---

## 피드백 엔진

`SingleGameFeedbackEngine`은 사용자의 성능을 다차원으로 분석하여 적절한 피드백을 제공합니다.

### 분석 축

| 축 | 설명 | 관련 지표 |
|----|------|-----------|
| **Physical Axis** | 마우스 에임 및 팝업 연타 속도 | `aimP`(에임 퍼센타일), `burstP`(연타 퍼센타일) |
| **Entry & Start Axis** | 진입 타이밍 및 첫 과목 선점 | `eP`(진입 퍼센타일), `startP`(첫 클릭 퍼센타일) |
| **Pace & Focus Axis** | 일관성 및 멘탈 관리 | `paceP`(페이스 퍼센타일), `paceStddev`(표준편차) |

### 피드백 규칙 (우선순위 순)

#### 1. Physical Axis Extreme Rules

| 조건 | 코드 | 설명 |
|------|------|------|
| aimP ≤ 30 && burstP ≤ 30 | `GOD_TIER_PHYSICAL` | 최상위 피지컬 |
| aimP ≥ 70 && burstP ≥ 70 | `PHYSICAL_UPGRADE_NEEDED` | 피지컬 향상 필요 |
| aimP ≥ 70 && burstP ≤ 30 | `FAST_BUT_INACCURATE` | 빠르지만 부정확 |

#### 2. Entry & Start Axis Priority Rules

| 조건 | 코드 | 설명 |
|------|------|------|
| eP ≤ 30 && startP ≤ 30 | `PERFECT_ENTRY_START` | 완벽한 진입과 시작 |
| eP ≤ 30 && startP ≥ 70 | `ENTRY_MASTER_START_NOVICE` | 진입은 좋으나 시작이 느림 |
| eP ≥ 70 && startP ≤ 30 | `ENTRY_LATE_START_MASTER` | 진입은 늦으나 시작이 빠름 |
| eP ≥ 70 && startP > 30 | `NEED_FASTER_ENTRY` | 빠른 진입 필요 |

#### 3. Pace & Focus Axis Rules (N ≥ 3)

| 조건 | 코드 | 설명 |
|------|------|------|
| paceP ≤ 30 | `MACHINE_LIKE_PACE` | 기계적인 일관성 |
| panic 조건 만족 | `EASY_PANIC` | 변수에 취약 |
| 전반부 - 후반부 ≥ 100ms | `STRONG_FINISHER` | 강력한 뒷심 |
| 후반부 - 전반부 ≥ 100ms | `WEAK_FINISHER` | 집중력 저하 |

#### 4. Physical Difference Rules

| 조건 | 코드 | 설명 |
|------|------|------|
| aimP > burstP | `SLOW_AIM` | 에임 속도 개선 필요 |
| burstP > aimP | `SLOW_BURST` | 연타 속도 개선 필요 |

#### 5. Fallback Rules

| 조건 | 코드 | 설명 |
|------|------|------|
| paceStddev > 0 | `FLUCTUATING_PACE` | 기복 있는 페이스 |
| startP ≥ 50 | `START_HESITATION` | 시작 망설임 |
| 기타 | `SLOW_BURST` | 기본 피드백 |

---

## 캐싱 전략

### 캐시 키 구조

| 캐시 이름 | 키 패턴 | 설명 |
|-----------|---------|------|
| `singlegame-rank` | `{totalCourses}:{scope}:cache` | 랭킹 조회 결과 |
| `singlegame-records` | `{memberId}:page:0:size:10:cache` | 내 기록 첫 페이지 |
| `singlegame-analysis` | `{gameId}:cache` | 게임 상세 분석 |

### 캐시 무효화 시점

게임 저장 트랜잭션 커밋 후 다음 캐시를 무효화합니다:
1. 해당 `totalCourses`의 모든 랭킹 캐시 (`GLOBAL`, `DEPARTMENT`)
2. 해당 회원의 내 기록 첫 페이지 캐시

---

## 설정값

| 설정 | 기본값 | 설명 |
|------|--------|------|
| `app.singlegame.reaction-time-min-ms` | 1 | 최소 반응 시간 (ms) |
| `app.singlegame.reaction-time-max-ms` | 60000 | 최대 반응 시간 (ms) |

---

## 에러 코드

| 코드 | 설명 |
|------|------|
| `SINGLEGAME_INVALID_TOTAL_COURSES` | 유효하지 않은 과목 수 |
| `SINGLEGAME_INVALID_DETAILS_COUNT` | 상세 데이터 개수 불일치 |
| `SINGLEGAME_INVALID_REACTION_TIME` | 반응 시간 범위 초과 |
| `SINGLEGAME_GAME_NOT_FOUND` | 게임을 찾을 수 없음 |
| `AUTH_MEMBER_NOT_FOUND` | 회원을 찾을 수 없음 |
