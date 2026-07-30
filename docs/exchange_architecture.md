### 수강신청 과목 교환 시스템 설계 문서 (Exchange Architecture)

본 문서는 수강신청 과목 교환 매칭 도메인의 아키텍처 설계, 비동기 그래프 사이클 탐색 엔진, 파티셔닝 데이터베이스 및 성능 최적화 인덱스, Redis 캐시 제어 전략(Double Eviction), 상태 전이 및 비즈니스 플로우 상세 이정표, 그리고 API 명세를 기술합니다.

---

#### 1. 도메인의 목적과 핵심 원리

##### **[목적]**

본 시스템의 유일한 목적은 "과목을 교환하고자 하는 사용자들의 교환 의사(Intent)를 수집하여 사이클(Cycle)을 탐색하고, 교환 가능성이 있는 사용자들을 하나의 채팅방(Room)으로 연결해 주는 것"입니다.

##### **[주의점 1: 교환 상태 관리의 부재]**

본 시스템은 실제 수강신청 시스템과 연동되어 과목을 교환해 주는 자동화 시스템이 아닙니다. 교환의 최종 실행 여부는 사용자들이 채팅방에서 협의 후 각자 수강신청 시스템에서 직접(예: "지금 제가 취소할 테니 바로 주우세요") 진행해야 합니다.
따라서 이 도메인에는 일반적인 거래 시스템에 존재하는 **[교환 성공, 교환 실패, 교환 취소, 교환 진행 중, 교환 완료]와 같은 상태(Status) 개념이 존재하지 않습니다.** 별도의 매칭(Match) 테이블이나 상태를 기록하는 컬럼 또한 두지 않는 것이 핵심입니다.
추가적으로 방을 완전히 나가는 행동은 없으며, 유저가 개별적으로 방을 알림 수신 거부 및 목록 비노출 상태로 전환하는 **OFF(알림 수신 X, 해당 방 진입 및 UI 갱신 X) 처리** 개념만 존재합니다.

##### **[주의점 2: 과목 식별은 과목명이 아닌 '개설 강좌 식별 코드(coursecls)'로 진행]**

사용자는 '운영체제(OS)', '알고리즘(ALGO)'과 같은 모호한 문자열이 아닌, 개설 강좌 식별 코드(`coursecls`, 예: "0001", "0002" 등 개설 강좌별 고유 식별 코드)를 등록하여 교환을 진행합니다. 과목명으로 매칭할 경우 교수진, 시간대, 분반이 달라 발생하는 오차를 방지하기 위함입니다. 그래프 탐색 역시 철저하게 이 개설 강좌 식별 코드(`coursecls`)를 노드(Node)로 삼아 동작합니다.

##### **[핵심 원리]**

1. **Term(학기) 중심 설계:**
모든 데이터베이스 PK와 Redis Key는 `term`(예: `202620` - 26년도 1학기)을 기준으로 설계됩니다. `PARTITION BY LIST(term)`을 적용하여 학기별로 테이블을 물리적으로 분리하여 대용량 트래픽 상황에서도 높은 조회 성능을 보장합니다.
2. **그래프 사이클 탐색 (Graph Cycle Detection):**
사용자의 '버릴 개설 강좌 식별 코드(coursecls) -> 원하는 개설 강좌 식별 코드(coursecls)'는 방향 그래프의 간선(Directed Edge)이 됩니다. (예: `0001 -> 0005`) 시스템은 의사가 등록될 때마다 비동기로 그래프를 탐색하여 `0001 -> 0005 -> 0012 -> 0001`과 같은 닫힌 사이클(Cycle)이 발견되면 해당 간선을 생성한 사용자들을 묶어 채팅방(Room)을 생성합니다.
* 만약 2개 이상의 사이클이 잡히면 모두 등록하여 사용자의 선택권을 보장합니다.
3. **유연한 Room 유효성 검증 (Soft Delete & 단일 상태 전이):**
사용자가 교환 의사를 철회하더라도 채팅방(Room) 자체가 폭파되거나 메시지가 삭제되지 않습니다. 단지 해당 유저의 의사(Intent) 및 참여 매핑 정보가 Soft Delete(`is_deleted = true`) 처리될 뿐입니다. 채팅방의 상태는 중복 플래그(`is_active`) 없이 단일 `status` 컬럼(`ACTIVE`, `PARTIAL_OFF`, `PARTIAL_DELETE`, `ALL_DELETE`)으로 통합 관리되며, 상태 변경 시에는 시스템 메시지가 기록됩니다.
4. **앱 활동성(Liveliness) 강화를 위한 실시간 피드:**
앱이 활성화되어 있음을 유저에게 보여주기 위해, 최근 등록된 교환 의사들을 실시간 피드 형태로 노출합니다. 5초 주기의 클라이언트 폴링을 통해 효율적으로 갱신됩니다.

---

#### 2. 비동기 사이클 탐색 및 동시성 제어 아키텍처

본 시스템은 신규 Intent 생성과 무거운 연산이 필요한 그래프 탐색 프로세스를 분리하여 애플리케이션의 응답성을 보장합니다.

```mermaid
sequenceDiagram
    actor User as 사용자
    participant Controller as ExchangeController
    participant Service as ExchangeService
    participant PGMQ as Postgres Message Queue
    participant Worker as ExchangeCycleDetectionWorker
    participant Detector as ExchangeCycleDetector
    participant Creation as ExchangeRoomCreationService
    database DB as RDB (PostgreSQL)

    User->>Controller: Intent 등록 요청 (give, want)
    Controller->>Service: createIntent() 호출
    Service->>DB: EXCHANGE_INTENT 삽입
    Note over Service, DB: 트랜잭션 Commit 완료 후 비동기 처리 예약
    Service-->>PGMQ: [afterCommit] CycleDetectionMessage 발행
    Service-->>Controller: Intent 등록 응답 반환
    Controller-->>User: 응답 완료
    
    loop 1초 주기로 폴링
        Worker->>PGMQ: 메시지 수신 (Visibility Timeout 적용)
    end
    
    PGMQ-->>Worker: CycleDetectionMessage 획득
    Worker->>Detector: detectCyclesAndCreateRooms() 실행
    Detector->>DB: 활성 Intent 목록 조회 (is_deleted = FALSE)
    DB-->>Detector: active intents 목록 반환
    Note over Detector: 메모리 내 방향 그래프 구성 후 DFS 사이클 탐색
    
    alt 사이클 발견 및 신규 해시 확인
        Detector->>Creation: createRoom() 호출 (트랜잭션 시작)
        Note over Creation, DB: 사이클을 구성하는 각 Intent에 대해<br/>SELECT FOR UPDATE 비관적 락 획득
        Creation->>DB: EXCHANGE_ROOM 생성 (status='ACTIVE', cycle_hash unique 제약)
        Creation->>DB: EXCHANGE_ROOM_INTENT 매핑 삽입
        Creation->>DB: EXCHANGE_ROOM_READ_STATUS 초기화
        Creation->>DB: EXCHANGE_ROOM_MESSAGE 웰컴 시스템 메시지(message_type='SYSTEM') 작성
        Note over Creation, DB: 트랜잭션 Commit 및 Redis 캐시 Evict
        Creation-->>Worker: 방 생성 성공
    end
    Worker->>PGMQ: 메시지 DELETE (처리 완료)
```

* **비동기 큐잉:** ExchangeService는 Intent 저장 커밋이 완료된 후, PGMQ(Postgres Message Queue)를 통해 탐색 요청 메시지를 송신합니다.
* **백그라운드 스케줄링:** ExchangeCycleDetectionWorker는 1초 간격으로 `exchange_cycle_detection` 큐를 읽어, ExchangeCycleDetector를 통해 DFS 기반 탐색을 수행합니다.
* **비관적 락(Pessimistic Lock):**
1. ExchangeRoomCreationService는 사이클에 속한 각 `ExchangeIntentEntity`를 `PESSIMISTIC_WRITE` 락으로 확보해, 탐색 시점과 방 생성 트랜잭션 커밋 사이에 유저가 Intent를 철회하여 매칭 정합성이 깨지는 것을 원천 차단합니다.
2. 대화방 상태 갱신(`updateRoomStatusAndState`) 시점에도 `roomRepository.findByIdForUpdate(term, roomId)`를 호출하여 `ExchangeRoomEntity`에 대해 `PESSIMISTIC_WRITE` 락을 획득함으로써, 분산 환경에서 여러 사용자가 동시에 방 토글이나 이탈을 처리할 때 발생할 수 있는 갱신 분실(Lost Update) 및 경합 문제를 완전히 방지합니다.
* **방 중복 생성 방지:** 사이클을 구성하는 `intent_id`를 정렬 후 SHA-256으로 해싱한 `cycle_hash` 값을 생성하여 `EXCHANGE_ROOM` 테이블의 `UNIQUE` 제약 조건을 통해 동일 사이클로 방이 중복 생성되는 문제를 물리적으로 방지합니다.

---

#### 3. 사용자 행동과 데이터베이스/캐시 처리 흐름 및 상태 전이 방식

##### **[처리 흐름]**

* **Intent 등록:** DB 저장 (`is_deleted = FALSE`) -> PGMQ 큐 발행 (`CycleDetectionMessage`) -> 연관 캐시 Evict
* **비동기 큐 수신 & 그래프 사이클 탐색 (백그라운드):** Worker가 PGMQ 메시지 수신(인지) -> 활성 Intent 스캔 (`is_deleted = FALSE`) & DFS 사이클 탐색 -> 사이클 발견 시 `PESSIMISTIC_WRITE` 락 획득 -> `EXCHANGE_ROOM` 생성 (`status = 'ACTIVE'`) -> `EXCHANGE_ROOM_INTENT` 매핑 적재 (`is_deleted = FALSE`, `is_on = TRUE`) -> `EXCHANGE_ROOM_READ_STATUS` 초기화 -> `EXCHANGE_ROOM_MESSAGE` 웰컴 SYSTEM 메시지 작성 (`message_type = 'SYSTEM'`) -> 참여자 전원 캐시 Evict -> PGMQ 메시지 DELETE
* **사용자의 Main 폴링 (`GET /api/v1/exchange/main`):** 5초 주기 폴링 요청 -> Redis 캐시 조회 (MyIntents, Feed, Rooms 캐시 조립) -> 캐시 미스 시 DB 조회 후 캐시 재적재 -> 내 Intent 목록, 연관 방 목록 및 각 방의 상세 정보(방 단일 status `ACTIVE`/`PARTIAL_OFF`/`PARTIAL_DELETE`/`ALL_DELETE`, 내 토글 `isOn`, 안 읽은 메시지 수 `unreadCount`, 최근 메시지 이력, 방 구성원/참여 Intent 카드 목록 등)를 단일 API 응답으로 풍부하게 일괄 반환
* **사용자의 Room 메시지 전송:** DB 삽입 (`message_type = 'TALK'`) -> 발신자 `last_read_message_id` 즉시 갱신 -> 참여자 전원 캐시 Evict
* **사용자의 Intent 철회:** Intent/Mapping Soft Delete (`is_deleted = TRUE`) -> `PESSIMISTIC_WRITE` 락 기반 방 `status` 재계산 및 갱신 (`PARTIAL_DELETE` / `ALL_DELETE`) -> `EXCHANGE_ROOM_MESSAGE` 이탈 안내 SYSTEM 메시지 일괄 작성 -> 참여자 전원 캐시 Evict
* **사용자의 Room 토글(OFF/ON):** `is_on` 변경 (`TRUE` / `FALSE`) -> `PESSIMISTIC_WRITE` 락 기반 방 `status` 재계산 및 갱신 -> 필요 시 SYSTEM 메시지 작성 -> 연관 캐시 Evict
* **사용자의 Room 메시지 읽음 처리:** `EXCHANGE_ROOM_READ_STATUS` 내 `last_read_message_id` DB 갱신 -> 본인 캐시 Evict

##### **[상태 변경 및 시스템 메시지 연동 방식]**

1. **단일 상태 축적 관리:** `EXCHANGE_ROOM`의 `status` 컬럼 하나로 방 전체 상태를 나타냅니다.
* `ACTIVE`: 모든 유저가 참여 중 및 알림 ON 상태
* `PARTIAL_OFF`: 1명 이상의 유저가 방을 OFF(알림 수신 거부) 처리함 (삭제된 의사는 없음)
* `PARTIAL_DELETE`: 1명 이상의 유저가 Intent를 철회함 (전체 참여자 중 일부 삭제, `0 < d < n`)
* `ALL_DELETE`: 참여자 전체가 Intent를 철회함 (`d == n`)

> **대화방 비활성화(Deactivation) 기준:**
> - 활성 Intent가 1개 이하(`n - d < 2`)가 되면 대화방은 비활성화되어 추가 메시지 전송이 차단됩니다.
> - 따라서 `PARTIAL_DELETE` 상태이더라도 남은 활성 Intent가 1개인 경우 대화방은 비활성화 상태가 됩니다.
> - `ALL_DELETE`는 전원 철회 시 기록 보관용으로 전환되며 적절한 비활성화 시스템 메시지가 남습니다.

2. **메시지 테이블 내 시스템 메시지 통합:** `EXCHANGE_ROOM_MESSAGE` 테이블에 `message_type` (`TALK`, `SYSTEM`) 컬럼을 도입합니다.
* 유저 행동(Intent 철회, 방 토글 등)으로 인해 방 상태 변경이 일어날 때, 동시성 락(`PESSIMISTIC_WRITE`) 내에서 **Room status 업데이트**와 함께 **SYSTEM 타입 메시지(`member_id`, `intent_id`는 NULL 허용)를 일괄 INSERT**합니다.

---

#### 4. 데이터베이스 ERD & DDL 명세

일반적으로 member가 주축이 되어서 처리되는 것을 생각할 수 있지만 여기서는 사용자의 intent가 실질적인 주체 역할을 합니다. 즉 관계들은 모두 intent와 필수적으로 관계를 가지고 있습니다. 또한 설계 시 등에서는 intent가 실질적인 식별자라고 생각해야 합니다.

> 그럼에도 member를 FK로 잡은 이유는 향후 member 안에 포함된 내용이 필요한 경우 JOIN을 통해 쉽게 접근 가능하도록 하기 위함입니다.

##### **[ERD 다이어그램]**

상태 업데이트로 인한 Lock이나 복잡한 Join을 최소화하기 위해 철저하게 이력(History)과 기본 정보 위주로 테이블을 구성합니다. 개설 강좌 식별 코드(`coursecls`)는 앞자리에 0이 포함될 수 있는 점(예: `"0001"`)을 고려하여 `varchar`로 선언하여 관리합니다.

```mermaid
erDiagram
    MEMBER {
        BIGINT id PK "사용자 고유 ID"
    }

    EXCHANGE_ROOM {
        VARCHAR term PK "학기 (예: 202620)"
        BIGINT id PK "방 고유 ID"
        VARCHAR cycle_hash "UNIQUE (Intent 조합 해시)"
        VARCHAR status "방 상태 (ACTIVE, PARTIAL_OFF, PARTIAL_DELETE, ALL_DELETE)"
        TIMESTAMP created_at
    }

    EXCHANGE_INTENT {
        VARCHAR term PK "학기"
        BIGINT id PK "의사 고유 ID"
        BIGINT member_id FK "MEMBER(id)"
        VARCHAR give_course_no "버릴 과목"
        VARCHAR want_course_no "원하는 과목"
        BOOLEAN is_deleted "의사 취소 여부"
        TIMESTAMP created_at
        TIMESTAMP deleted_at
    }

    EXCHANGE_ROOM_INTENT {
        VARCHAR term PK "학기"
        BIGINT room_id PK, FK "EXCHANGE_ROOM(term, id)"
        BIGINT intent_id PK, FK "EXCHANGE_INTENT(term, id)"
        BIGINT member_id FK "MEMBER(id)"
        BOOLEAN is_deleted "매핑 내 intent 삭제 여부"
        BOOLEAN is_on "방 활성 토글 상태 (알림 수신 및 화면 표시 여부)"
        TIMESTAMP joined_at
    }

    EXCHANGE_ROOM_MESSAGE {
        VARCHAR term PK "학기"
        BIGINT id PK "메시지 고유 ID"
        BIGINT room_id FK "EXCHANGE_ROOM(term, id)"
        BIGINT member_id FK "MEMBER(id), NULLABLE(SYSTEM 메시지)"
        BIGINT intent_id FK "EXCHANGE_INTENT(term, id), NULLABLE(SYSTEM 메시지)"
        VARCHAR message_type "메시지 유형 (TALK, SYSTEM)"
        TEXT content "메시지 본문"
        TIMESTAMP created_at
    }

    EXCHANGE_ROOM_READ_STATUS {
        VARCHAR term PK "학기"
        BIGINT room_id PK, FK "EXCHANGE_ROOM(term, id)"
        BIGINT member_id PK, FK "MEMBER(id)"
        BIGINT intent_id PK, FK "EXCHANGE_INTENT(term, id)"
        BIGINT last_read_message_id FK "EXCHANGE_ROOM_MESSAGE(term, id)"
        TIMESTAMP last_read_at
    }

    %% [관계선 설정]
    MEMBER ||--o{ EXCHANGE_INTENT : "owns"
    MEMBER ||--o{ EXCHANGE_ROOM_INTENT : "participates"
    MEMBER ||--o{ EXCHANGE_ROOM_MESSAGE : "sends"
    MEMBER ||--o{ EXCHANGE_ROOM_READ_STATUS : "reads"

    EXCHANGE_ROOM ||--o{ EXCHANGE_ROOM_INTENT : "groups"
    EXCHANGE_ROOM ||--o{ EXCHANGE_ROOM_MESSAGE : "contains"
    EXCHANGE_ROOM ||--o{ EXCHANGE_ROOM_READ_STATUS : "tracks"

    EXCHANGE_INTENT ||--o{ EXCHANGE_ROOM_INTENT : "linked_by"
    EXCHANGE_INTENT ||--o{ EXCHANGE_ROOM_MESSAGE : "author_card"
    EXCHANGE_INTENT ||--o{ EXCHANGE_ROOM_READ_STATUS : "reader_card"

    EXCHANGE_ROOM_MESSAGE ||--o{ EXCHANGE_ROOM_READ_STATUS : "last_read_pointer(term, id)"
```

##### **[DDL 명세 (PostgreSQL)]**

```sql
-- 1. EXCHANGE_INTENT (교환 의사)
CREATE TABLE exchange_intent (
    term VARCHAR(10) NOT NULL,
    id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    give_course_no VARCHAR(20) NOT NULL,
    want_course_no VARCHAR(20) NOT NULL,
    is_deleted BOOLEAN DEFAULT FALSE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_exchange_intent PRIMARY KEY (term, id)
) PARTITION BY LIST (term);

-- 2. EXCHANGE_ROOM (교환 채팅방)
-- status 단일 컬럼으로 방 전체 상태 관리 (is_active 삭제 및 CHECK 제약 조건 적용)
CREATE TABLE exchange_room (
    term VARCHAR(10) NOT NULL,
    id BIGINT NOT NULL,
    cycle_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL, -- ACTIVE, PARTIAL_OFF, PARTIAL_DELETE, ALL_DELETE
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_exchange_room PRIMARY KEY (term, id),
    CONSTRAINT uk_exchange_room_cycle_hash UNIQUE (term, cycle_hash),
    CONSTRAINT chk_exchange_room_status CHECK (status IN ('ACTIVE', 'PARTIAL_OFF', 'PARTIAL_DELETE', 'ALL_DELETE'))
) PARTITION BY LIST (term);

-- 3. EXCHANGE_ROOM_INTENT (채팅방-의사 매핑)
CREATE TABLE exchange_room_intent (
    term VARCHAR(10) NOT NULL,
    room_id BIGINT NOT NULL,
    intent_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    is_deleted BOOLEAN DEFAULT FALSE NOT NULL,
    is_on BOOLEAN DEFAULT TRUE NOT NULL,
    joined_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_exchange_room_intent PRIMARY KEY (term, room_id, intent_id)
) PARTITION BY LIST (term);

-- 4. EXCHANGE_ROOM_MESSAGE (채팅 및 시스템 메시지)
-- message_type 도입 및 SYSTEM/TALK 메시지 데이터 무결성 강화를 위해 CHECK 제약 조건 적용
CREATE TABLE exchange_room_message (
    term VARCHAR(10) NOT NULL,
    id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    member_id BIGINT,              -- SYSTEM 메시지 시 NULL
    intent_id BIGINT,              -- SYSTEM 메시지 시 NULL
    message_type VARCHAR(10) DEFAULT 'TALK' NOT NULL, -- TALK, SYSTEM
    content TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_exchange_room_message PRIMARY KEY (term, id),
    CONSTRAINT chk_exchange_room_message_type CHECK (
        (message_type = 'TALK' AND member_id IS NOT NULL AND intent_id IS NOT NULL) OR
        (message_type = 'SYSTEM' AND member_id IS NULL AND intent_id IS NULL)
    )
) PARTITION BY LIST (term);

-- 5. EXCHANGE_ROOM_READ_STATUS (안읽은 메시지 읽음 처리)
CREATE TABLE exchange_room_read_status (
    term VARCHAR(10) NOT NULL,
    room_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    intent_id BIGINT NOT NULL,
    last_read_message_id BIGINT DEFAULT 0 NOT NULL,
    last_read_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_exchange_room_read_status PRIMARY KEY (term, room_id, intent_id)
) PARTITION BY LIST (term);
```

##### **[성능 최적화 인덱스 설계]**

```sql
-----------------------------------------------------------
-- [1] EXCHANGE_INTENT 관련 인덱스
-----------------------------------------------------------

-- idx_intent_member_active: 유저 메인 화면 / 마이페이지 조회 최적화
-- 특정 member_id에 대해 삭제되지 않은(is_deleted = FALSE) 카드 목록만 추출하는 부분 인덱스(Partial Index).
-- SELECT * FROM EXCHANGE_INTENT WHERE member_id = :myId AND is_deleted = FALSE;
CREATE INDEX idx_intent_member_active 
    ON EXCHANGE_INTENT(member_id) 
    WHERE is_deleted = FALSE;

-- idx_intent_matching_pool: 매칭 엔진(그래프 사이클 탐색) 스캔 최적화
-- 아직 유효한(is_deleted = FALSE) 교환 의사의 과목 매핑 정보를 인덱스 풀로 빠르게 확보.
-- SELECT * FROM EXCHANGE_INTENT WHERE give_course_no = :give AND want_course_no = :want AND is_deleted = FALSE;
CREATE INDEX idx_intent_matching_pool 
    ON EXCHANGE_INTENT(give_course_no, want_course_no) 
    WHERE is_deleted = FALSE;


-----------------------------------------------------------
-- [2] EXCHANGE_ROOM_INTENT 관련 인덱스
-----------------------------------------------------------

-- idx_room_intent_member: 유저의 대화방 리스트 폴링 최적화
-- 사용자가 앱에 진입하거나 5초 주기로 메인 화면을 폴링할 때 참여 중인 방의 ID를 빠르게 획득.
-- SELECT room_id FROM EXCHANGE_ROOM_INTENT WHERE member_id = :myId;
CREATE INDEX idx_room_intent_member 
    ON EXCHANGE_ROOM_INTENT(member_id, room_id);

-- idx_room_intent_reverse: 특정 카드 취소 시 연관 대화방 역방향 추적 최적화
-- 유저가 의사를 삭제(취소)했을 때 해당 카드가 연결되어 활성화 중이던 방 목록을 역방향으로 조회해 방 비활성화 또는 시스템 알림 전송.
-- SELECT room_id FROM EXCHANGE_ROOM_INTENT WHERE intent_id = :deletedIntentId;
CREATE INDEX idx_room_intent_reverse 
    ON EXCHANGE_ROOM_INTENT(intent_id, room_id);


-----------------------------------------------------------
-- [3] EXCHANGE_ROOM_MESSAGE 관련 인덱스
-----------------------------------------------------------

-- idx_message_room_id_pagination: 대화방 내부 페이징 및 폴링 최적화
-- 특정 방의 메시지를 최신순(id DESC)으로 조회하거나 마지막 확인 메시지 ID 이후의 새로운 메시지만 가져오는 실시간 채팅의 핵심 인덱스.
-- SELECT * FROM EXCHANGE_ROOM_MESSAGE WHERE room_id = :roomId AND id > :lastViewedId ORDER BY id DESC;
CREATE INDEX idx_message_room_id_pagination 
    ON EXCHANGE_ROOM_MESSAGE(room_id, id DESC);


-----------------------------------------------------------
-- [4] EXCHANGE_ROOM_READ_STATUS 관련 인덱스
-----------------------------------------------------------

-- idx_read_status_member: 채팅 탭의 '안 읽은 메시지 수(빨간 배지)' 계산 오버헤드 최소화
-- 메인화면/목록 조회 시 내가 참여하는 각 방의 "마지막 읽음 메시지 위치"를 한 번에 조회하여 안 읽은 메시지 수를 신속히 집계.
-- SELECT room_id, last_read_message_id FROM EXCHANGE_ROOM_READ_STATUS WHERE member_id = :myId;
CREATE INDEX idx_read_status_member 
    ON EXCHANGE_ROOM_READ_STATUS(member_id, room_id);
```

##### **[동적 학기별 파티션 테이블 생성 스크립트]**

```sql
DO $$
DECLARE
    start_year INT := 2026;
    end_year INT := 2100;
    current_year INT;
    terms TEXT[] := ARRAY['10', '15', '20', '25']; -- 10: 1학기, 15: 여름학기, 20: 2학기, 25: 겨울학기
    t TEXT;
    target_term TEXT;
BEGIN
    FOR current_year IN start_year..end_year LOOP
        FOREACH t IN ARRAY terms LOOP
            target_term := current_year::TEXT || t;
            
            EXECUTE format('CREATE TABLE IF NOT EXISTS exchange_room_%I PARTITION OF exchange_room FOR VALUES IN (%L)', target_term, target_term);
            EXECUTE format('CREATE TABLE IF NOT EXISTS exchange_intent_%I PARTITION OF exchange_intent FOR VALUES IN (%L)', target_term, target_term);
            EXECUTE format('CREATE TABLE IF NOT EXISTS exchange_room_intent_%I PARTITION OF exchange_room_intent FOR VALUES IN (%L)', target_term, target_term);
            EXECUTE format('CREATE TABLE IF NOT EXISTS exchange_room_message_%I PARTITION OF exchange_room_message FOR VALUES IN (%L)', target_term, target_term);
            EXECUTE format('CREATE TABLE IF NOT EXISTS exchange_room_read_status_%I PARTITION OF exchange_room_read_status FOR VALUES IN (%L)', target_term, target_term);
            
        END LOOP;
    END LOOP;
END $$;
```

---

#### 5. Redis 캐시 전략 및 정합성 보장

본 시스템은 분산 서버 환경에서의 빠른 실시간 5초 폴링을 지원하기 위해, **RDB를 단일 신뢰 원천(Single Source of Truth)으로 삼고, Redis 캐시를 조작하여 캐시 정합성을 유지**하는 방식으로 설계되었습니다.

##### **[Redis 캐시 키 정의]**

| 캐시 종류 | Redis Key 형식 | 데이터 구조 | TTL | 설명 |
| :--- | :--- | :--- | :--- | :--- |
| **CACHE 1** (공용 피드) | `exchange::{term}:feed:cache` | List | 10시간 | 실시간 전체 공용 피드로 사용되는 최근 50개의 교환 의사 DTO 리스트. |
| **CACHE 2** (유저별 Intent) | `exchange::{term}:member:{member_id}:intents:cache` | List | 10분 | 해당 유저가 등록한 미삭제 상태의 활성 Intent DTO 리스트. |
| **CACHE 3** (유저별 방 목록) | `exchange::{term}:member:{member_id}:rooms:cache` | List | 10분 | 해당 유저가 참여 중인 방의 상세 요약 정보 목록 (방 status, isOn 토글 상태, lastReadMessageId 위치, 안 읽은 메시지 수, 최근 메시지, 방 참여 구성원 목록 등 포함). |

- 모든 캐시 객체의 날짜/시간 필드는 직렬화 일관성을 위해 ISO-8601 문자열 대신 **밀리초 단위 epoch timestamp (Long)** 형식으로 직렬화합니다.

##### **[정합성 보장: 이중 무효화 (Double Eviction) 전략]**

RDB 트랜잭션 도중 예외가 발생할 경우 캐시만 일방적으로 오염되는 문제를 막기 위해, 모든 캐시 무효화는 `TransactionSynchronizationManager.registerSynchronization`을 통해 **RDB 트랜잭션이 성공적으로 COMMIT된 후(afterCommit)에 비동기로 실행**됩니다. 

또한, 데이터 커밋 시점과 WAS 조회 시점 사이의 분산 환경 레이스 컨디션을 방지하기 위해 **Double Eviction(이중 무효화)** 기법을 적용합니다:
1. 트랜잭션 커밋 완료 직후 `redisTemplate.delete(key)`를 즉시 1차 수행합니다.
2. `TaskScheduler`를 활용하여 2초의 딜레이(`DOUBLE_EVICT_DELAY`) 이후 해당 키에 대한 `delete(key)`를 한 번 더 비동기로 수행하여 찰나의 순간에 복구된 정합성 어긋난 캐시 데이터를 완전히 무효화합니다.

```java
// [ExchangeCacheService.java] 이중 무효화 메커니즘 일부
private void scheduleDoubleEvict(String key) {
    taskScheduler.schedule(() -> {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Double evict failed for key={}: {}", key, e.getMessage());
        }
    }, java.time.Instant.now().plus(Duration.ofSeconds(2)));
}
```

##### **[캐시 미스 시 자동 복구 로직]**

캐시 미스(Cache Miss) 발생 시 WAS는 RDB를 직접 질의하여 최신 정합성 데이터를 가져오고, Redis에 데이터를 재적재하여 성능을 최적화합니다.

- **CACHE 1 복구 쿼리:**
  ```sql
  SELECT * FROM EXCHANGE_INTENT 
  WHERE term = :term AND is_deleted = FALSE 
  ORDER BY id DESC LIMIT 50;
  ```
- **CACHE 2 복구 쿼리:**
  ```sql
  SELECT * FROM EXCHANGE_INTENT 
  WHERE term = :term AND member_id = :member_id AND is_deleted = FALSE 
  ORDER BY id DESC;
  ```
- **CACHE 3 복구 쿼리:**
  ```sql
  SELECT
      r.id                          AS room_id,
      r.term                        AS term,
      r.cycle_hash                  AS cycle_hash,
      r.status                      AS room_status,
      ri.is_on                      AS is_on,
      COALESCE(rs.last_read_message_id, 0) AS last_read_message_id,
      msg.id                        AS last_message_id,
      msg.message_type              AS last_message_type,
      msg.member_id                 AS last_message_sender_id,
      msg.content                   AS last_message_content,
      msg.created_at                AS last_message_at,
      COUNT(new_msg.id)             AS unread_count,
      r.created_at                  AS created_at
  FROM EXCHANGE_ROOM_INTENT ri
  JOIN EXCHANGE_ROOM r
      ON r.term = ri.term AND r.id = ri.room_id
  LEFT JOIN EXCHANGE_ROOM_MESSAGE msg
      ON msg.term = ri.term AND msg.id = (
          SELECT id FROM EXCHANGE_ROOM_MESSAGE
          WHERE term = ri.term AND room_id = ri.room_id
          ORDER BY id DESC LIMIT 1
      )
  LEFT JOIN EXCHANGE_ROOM_READ_STATUS rs
      ON rs.term = ri.term AND rs.room_id = ri.room_id AND rs.member_id = :member_id
  LEFT JOIN EXCHANGE_ROOM_MESSAGE new_msg
      ON new_msg.term = ri.term
      AND new_msg.room_id = ri.room_id
      AND new_msg.id > COALESCE(rs.last_read_message_id, 0)
  WHERE ri.term      = :term
    AND ri.member_id = :member_id
  GROUP BY r.id, r.term, r.cycle_hash, r.status, ri.is_on, rs.last_read_message_id, msg.id, msg.message_type, msg.member_id, msg.content, msg.created_at, r.created_at;
  ```

---

#### 6. 상태 및 비즈니스 플로우 상세 이정표

---

- **① INTENT 등록**
    - **트리거:** 사용자가 버릴 개설 강좌 식별 코드(`coursecls`)와 원하는 개설 강좌 식별 코드(`coursecls`)를 입력하여 카드를 등록할 때.
    - **타겟 테이블:** `EXCHANGE_INTENT`
    - **상세 처리 로직:**
        1. 동일 학기에 중복된 활성 의사(`is_deleted = FALSE`)가 존재하는지 확인 후, 새 레코드를 생성합니다 (`is_deleted = FALSE`).
        2. 트랜잭션이 완결되면 `afterCommit` 훅을 통해 PGMQ 비동기 큐에 이벤트를 전송하고 **[② 사이클 탐색]**을 스케줄링합니다.
    - **캐시 조작:**
        1. `exchange::{term}:feed:cache`: 새 Intent DTO를 `LPUSH` 후 `LTRIM`으로 최대 50개 유지하고 만료시간 갱신.
        2. `exchange::{term}:member:{member_id}:intents:cache`: 이중 무효화(`evictIntents`) 실행.

---

- **② 사이클 탐색**
    - **트리거:** PGMQ로부터 `CycleDetectionMessage` 이벤트를 수신했을 때.
    - **타겟 테이블:** `EXCHANGE_INTENT` (Read Only)
    - **상세 처리 로직:**
        1. `idx_intent_matching_pool` 인덱스를 이용해 현재 학기 내의 모든 활성 의사 목록(`is_deleted = FALSE`)을 스캔합니다.
        2. 과목 식별자 쌍(`give_course_no` -> `want_course_no`)을 방향 그래프의 간선으로 메모리에 구축한 후 DFS 알고리즘을 사용해 순환 고리(Cycle)를 탐색합니다.
        3. 발견된 사이클의 `intent_id` 목록을 정렬한 뒤 SHA-256으로 해싱하여 고유 `cycle_hash`를 추출합니다.
        4. 동일한 `cycle_hash`를 가진 `EXCHANGE_ROOM`이 존재하지 않을 때에만 **[③ 방 생성 및 초기화]**를 개시합니다.
    - **캐시 조작:** 없음.

---

- **③ 방 생성 및 초기화**
    - **트리거:** 신규 사이클 발견 및 고유 `cycle_hash` 검증 완료 시.
    - **타겟 테이블:** `EXCHANGE_ROOM`, `EXCHANGE_ROOM_INTENT`, `EXCHANGE_ROOM_READ_STATUS`, `EXCHANGE_ROOM_MESSAGE`
    - **상세 처리 로직 (단일 트랜잭션):**
        1. **비관적 락 획득:** 사이클 내의 `intent_id` 레코드들을 `SELECT FOR UPDATE`로 락을 걸고 삭제 여부를 재검증합니다.
        2. **방 생성:** `EXCHANGE_ROOM` 테이블에 신규 행을 저장합니다 (`status = 'ACTIVE'`, `cycle_hash` 포함).
        3. **참여자 매핑:** `EXCHANGE_ROOM_INTENT` 테이블에 모든 참여자의 정보를 적재합니다 (`is_deleted = FALSE`, `is_on = TRUE`).
        4. **읽음 포인터 초기화:** `EXCHANGE_ROOM_READ_STATUS` 테이블에 참여자별 초기 읽음 상태를 저장합니다.
        5. **시스템 메시지 전송:** 매칭 완료 안내 웰컴 메시지를 `EXCHANGE_ROOM_MESSAGE`에 삽입(`message_type = 'SYSTEM'`, `member_id`, `intent_id`는 NULL)하고, 이 메시지의 고유 ID로 각 참여자의 `last_read_message_id`를 최신화하여 읽음 처리합니다.
    - **캐시 조작:**
        1. 방 참여자 **전원**의 `exchange::{term}:member:{member_id}:rooms:cache`에 대해 이중 무효화(`evictRooms`) 처리합니다.

---

- **④ INTENT 삭제**
    - **트리거:** 유저가 메인 화면이나 마이페이지에서 자신이 등록한 카드를 삭제(철회)할 때.
    - **타겟 테이블:** `EXCHANGE_INTENT`, `EXCHANGE_ROOM_INTENT`, `EXCHANGE_ROOM`, `EXCHANGE_ROOM_MESSAGE`
    - **상세 처리 로직 (단일 트랜잭션):**
        1. **의사 삭제:** `EXCHANGE_INTENT` 테이블에서 대상 카드를 `is_deleted = TRUE` 및 `deleted_at = NOW()`로 갱신합니다.
        2. **영향 받는 대화방 파악:** 역방향 인덱스(`idx_room_intent_reverse`)를 통해 해당 카드가 참여 중이던 대화방 목록을 조회합니다.
        3. **매핑 동기화:** 식별된 대화방들의 해당 멤버 매핑 정보(`EXCHANGE_ROOM_INTENT`) 내 상태를 `is_deleted = TRUE`, `is_on = FALSE`로 갱신합니다.
        4. **방 상태 재계산:**
           - `PESSIMISTIC_WRITE` 락을 확보한 상태에서 대화방의 활성 Intent 수 및 참여자 상태를 재계산합니다.
           - 활성 Intent가 2개 이상 유지되면 `EXCHANGE_ROOM.status`를 `PARTIAL_DELETE`로 갱신하고, 활성 Intent가 1개 이하가 되어 사이클이 깨지면 `status`를 `ALL_DELETE`로 갱신합니다.
        5. **시스템 메시지 작성:** 동시성 락 안에서 대화방에 사용자의 이탈 알림 및 방 상태 변경 안내 시스템 메시지(`message_type = 'SYSTEM'`, `member_id`, `intent_id`는 NULL)를 일괄 INSERT합니다.
    - **캐시 조작:**
        1. 탈퇴자 본인의 `exchange::{term}:member:{member_id}:intents:cache` 이중 무효화.
        2. 연관된 방에 속해 있는 **참여자 전원**의 `exchange::{term}:member:{member_id}:rooms:cache` 이중 무효화.

---

- **⑤ 방 ON/OFF 토글**
    - **트리거:** 사용자가 특정 대화방 목록에서 알림 수신을 일시정지하거나 방을 목록에서 숨기기 위해 토글 스위치를 켰다 껄 때.
    - **타겟 테이블:** `EXCHANGE_ROOM_INTENT`, `EXCHANGE_ROOM`, `EXCHANGE_ROOM_MESSAGE`
    - **상세 처리 로직 (단일 트랜잭션):**
        1. 해당 유저의 `EXCHANGE_ROOM_INTENT` 테이블 내 `is_on` 컬럼을 입력받은 값(`TRUE` or `FALSE`)으로 업데이트합니다.
        2. `updateRoomStatusAndState`를 호출하여 `PESSIMISTIC_WRITE` 락을 걸고 방 상태를 재계산(상태 전이 규칙 A, B, C, D 적용)한 뒤 `EXCHANGE_ROOM` 테이블의 `status`를 업데이트합니다.
        3. 방 상태가 변경되는 경우(`PARTIAL_OFF`로 전이되거나 `ACTIVE`로 복귀 시) 동시성 락 안에서 방 상태 변경에 대한 SYSTEM 메시지(`message_type = 'SYSTEM'`)를 작성하여 삽입합니다. (예: `"[시스템] 일부 참여자가 대화방 알림을 OFF 하였습니다."`, `"[시스템] 모든 참여자가 대화방 알림을 ON으로 전환하였습니다."`)
        4. 토글이 `FALSE`가 된 유저의 경우에도 메인/채팅방 목록 쿼리(polling)에서 방은 삭제되거나 숨겨지지 않고 `isOn: false` 상태로 목록에 계속 노출되어 방 상태를 확인할 수 있습니다.
    - **캐시 조작:**
        1. 토글을 조작한 유저 **본인**의 `exchange::{term}:member:{member_id}:rooms:cache` 이중 무효화.

---

- **⑥ MESSAGE 전송**
    - **트리거:** 사용자가 활성화된 대화방 내부에서 텍스트를 작성하여 전송할 때.
    - **타겟 테이블:** `EXCHANGE_ROOM_MESSAGE`, `EXCHANGE_ROOM_READ_STATUS`
    - **상세 처리 로직 (단일 트랜잭션):**
        1. `EXCHANGE_ROOM_MESSAGE` 테이블에 해당 학기, 방 ID, 발신자 고유 ID와 Intent ID, `message_type = 'TALK'`, 그리고 본문 텍스트를 저장합니다.
        2. 발신자 본인은 메시지를 전송하자마자 읽은 것이 되므로, 생성된 메시지 ID로 본인의 `EXCHANGE_ROOM_READ_STATUS.last_read_message_id`를 즉시 갱신합니다.
    - **캐시 조작:**
        1. 대화방에 속한 **참여자 전원**의 `exchange::{term}:member:{member_id}:rooms:cache` 이중 무효화 (안 읽은 개수 및 최근 글 갱신 목적).

---

- **⑦ MESSAGE 읽음**
    - **트리거:** 사용자가 대화방 내부에 접속하거나, 대화방을 켜둔 채로 5초 폴링을 통해 신규 메시지를 확인했을 때.
    - **타겟 테이블:** `EXCHANGE_ROOM_READ_STATUS`
    - **상세 처리 로직:**
        1. 해당 방의 가장 큰 메시지 ID를 조회한 뒤, `EXCHANGE_ROOM_READ_STATUS` 테이블 내 본인 레코드의 `last_read_message_id` 필드를 해당 ID로 업데이트합니다.
    - **캐시 조작:**
        1. 본인의 `exchange::{term}:member:{member_id}:rooms:cache` 이중 무효화 (안 읽은 메시지 수 `0` 갱신 목적).

---

#### 7. API 명세 (API Specifications)

##### **[공통 응답 구조]**
성공 시 모든 응답은 `SingleSuccessResponseEnvelope` 표준 규격으로 감싸져 반환됩니다. `meta` 필드는 글로벌 필터에 의해 공통 주입됩니다.

```json
{
  "data": { ... },
  "meta": {
    "requestId": "req-77821",
    "apiVersion": "v1",
    "path": "/api/v1/exchange/...",
    "method": "GET | POST | DELETE | PATCH",
    "timestamp": 1749518850000,
    "durationMs": 45,
    "ipAddress": "127.0.0.1",
    "userAgent": "Mozilla/5.0..."
  }
}
```

> 가독성을 위해 아래 개별 명세에서는 `data` 객체의 필드 위주로 기술합니다.

---

##### **1. 교환 의사(Intent) 등록**
- **Method & Path:** `POST /api/v1/exchange/intents`
- **Description:** 버릴 개설 강좌 식별 코드(`coursecls`)와 원하는 개설 강좌 식별 코드(`coursecls`)를 지정하여 매칭 풀에 등록합니다.
- **Request Body:**
  ```json
  {
    "giveCourseNo": "10023",
    "wantCourseNo": "40101"
  }
  ```
- **Response JSON (201 Created):**
  ```json
  {
    "data": {
      "intentId": 10524,
      "memberId": 9931,
      "giveCourseNo": "10023",
      "wantCourseNo": "40101",
      "deleted": false,
      "createdAt": 1749518850000
    }
  }
  ```

---

##### **2. 교환 의사(Intent) 철회**
- **Method & Path:** `DELETE /api/v1/exchange/intents/{intentId}`
- **Description:** 등록했던 카드를 철회합니다. 연관 대화방의 인원이 부족할 경우 방 상태는 자동으로 `PARTIAL_DELETE`, `ALL_DELETE` 으로 전환됩니다.
- **Response JSON (200 OK):**
  ```json
  {
    "data": {
      "intentId": 10524,
      "deleted": true,
      "deletedAt": 1749519312000
    }
  }
  ```

---

##### **3. 메인 화면 상태 조회 (5초 주기 Polling 용도)**
- **Method & Path:** `GET /api/v1/exchange/main`
- **Description:** 5초 주기로 메인 화면을 갱신하는 통합 조회 API입니다. 클라이언트가 개별 방 진입이나 추가 API 호출 없이도 메인 화면과 각 내 Intent(카드)별 매칭 대화방 목록 및 구성원 정보를 한눈에 렌더링할 수 있도록, 내 Intent 객체 내부(`myIntents[].rooms`)에 연관 대화방 상세 목록(방 단일 status, 토글 state, unreadCount, lastReadMessageId 읽음 위치, 최근 메시지 lastMessage, 방 구성원 카드 목록 participants 등)을 중첩 배치하고 최신 피드(`recentIntents`)와 함께 일괄 반환합니다.
- **Response JSON (200 OK):**
  ```json
  {
    "data": {
      "myIntents": [
        {
          "intentId": 10524,
          "giveCourseNo": "0001",
          "wantCourseNo": "0005",
          "deleted": false,
          "createdAt": 1749518850000,
          "rooms": [
            {
              "roomId": 402,
              "term": "202620",
              "cycleHash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
              "status": "ACTIVE",
              "on": true,
              "unreadCount": 2,
              "lastReadMessageId": 55100,
              "lastMessage": {
                "messageId": 55102,
                "senderId": 4412,
                "messageType": "TALK",
                "content": "네 그럼 3시에 맞춰서 동시에 취소할까요?",
                "createdAt": 1749519015000
              },
              "participants": [
                {
                  "memberId": 9931,
                  "intentId": 10524,
                  "giveCourseNo": "0001",
                  "wantCourseNo": "0005",
                  "deleted": false,
                  "on": true
                },
                {
                  "memberId": 4412,
                  "intentId": 10525,
                  "giveCourseNo": "0005",
                  "wantCourseNo": "0012",
                  "deleted": false,
                  "on": true
                },
                {
                  "memberId": 7710,
                  "intentId": 10526,
                  "giveCourseNo": "0012",
                  "wantCourseNo": "0001",
                  "deleted": false,
                  "on": true
                }
              ],
              "createdAt": 1749518900000
            }
          ]
        }
      ],
      "recentIntents": [
        { "intentId": 10529, "giveCourseNo": "5020", "wantCourseNo": "1004", "createdAt": 1749519730000 },
        { "intentId": 10528, "giveCourseNo": "3012", "wantCourseNo": "2044", "createdAt": 1749519712000 },
        { "intentId": 10527, "giveCourseNo": "1102", "wantCourseNo": "3022", "createdAt": 1749519644000 },
        { "intentId": 10526, "giveCourseNo": "4001", "wantCourseNo": "9930", "createdAt": 1749519601000 },
        { "intentId": 10525, "giveCourseNo": "2039", "wantCourseNo": "0001", "createdAt": 1749519520000 }
      ]
    }
  }
  ```

---

##### **4. 최근 등록된 교환 의사 피드 조회**
- **Method & Path:** `GET /api/v1/exchange/intents/recent`
- **Description:** 최근 등록된 활성 교환 의사 리스트(최대 50개)를 단순 조회합니다. Redis 피드 캐시(`exchange::{term}:feed:cache`)에서 최근 50개 데이터를 즉시 반환하여 초고속 조회를 보장합니다.
- **Query Parameters:** 없음
- **Response JSON (200 OK):**
  ```json
  {
    "data": {
      "recentIntents": [
        { "intentId": 10529, "giveCourseNo": "5020", "wantCourseNo": "1004", "createdAt": 1749519730000 },
        { "intentId": 10528, "giveCourseNo": "3012", "wantCourseNo": "2044", "createdAt": 1749519712000 },
        { "intentId": 10527, "giveCourseNo": "1102", "wantCourseNo": "3022", "createdAt": 1749519644000 }
      ]
    }
  }
  ```

---

##### **5. 채팅방 메시지 내역 조회**
- **Method & Path:** `GET /api/v1/exchange/rooms/{roomId}/messages?beforeMessageId={beforeMessageId}&size={size}`
- **Description:** 해당 방의 이전 메시지 이력을 역방향 무한 스크롤(Previous Scroll, `id < beforeMessageId` 최신순 정렬) 방식으로 조회합니다. 호출 시 자동으로 읽음 처리 스키마(`EXCHANGE_ROOM_READ_STATUS`)가 갱신됩니다.
- **Query Parameters:**
  | 파라미터 | 타입 | 필수 여부 | 설명 |
  | :--- | :--- | :--- | :--- |
  | `beforeMessageId` | Long | N | 지정한 메시지 ID보다 더 과거(작은 ID)의 메시지를 최신순으로 조회. 미지정 시 가장 최신 메시지부터 반환. |
  | `size` | Integer | N | 한 번에 가져올 메시지 수. 기본값 20. |

- **Response JSON (200 OK):**
  ```json
  {
    "data": {
      "roomId": 402,
      "messages": [
        { "messageId": 55102, "senderId": 4412, "messageType": "TALK", "content": "네 그럼 3시에 맞춰서 동시에 취소할까요?", "createdAt": 1749519015000 },
        { "messageId": 55101, "senderId": 9931, "messageType": "TALK", "content": "저는 0001 과목 버립니다.", "createdAt": 1749518940000 }
      ],
      "nextBeforeMessageId": 55101,
      "hasNext": true
    }
  }
  ```

---

##### **6. 채팅방 메시지 전송**
- **Method & Path:** `POST /api/v1/exchange/rooms/{roomId}/messages`
- **Description:** 해당 방의 참여자에게 메시지를 전송하고 발신자 본인은 즉시 읽음 처리합니다.
- **Request Body:**
  ```json
  {
    "content": "좋습니다. 대기하겠습니다."
  }
  ```
- **Response JSON (201 Created):**
  ```json
  {
    "data": {
      "messageId": 55103,
      "roomId": 402,
      "senderId": 9931,
      "messageType": "TALK",
      "content": "좋습니다. 대기하겠습니다.",
      "createdAt": 1749520580000
    }
  }
  ```

---

##### **7. 방 ON/OFF 토글**
- **Method & Path:** `PATCH /api/v1/exchange/rooms/{roomId}/toggle`
- **Description:** 특정 방의 알림 수신 상태 및 ON/OFF 토글 상태를 전환합니다.
- **Request Body:**
  ```json
  {
    "isOn": false
  }
  ```
- **Response JSON (200 OK):**
  ```json
  {
    "data": {
      "roomId": 402,
      "isOn": false
    }
  }
  ```