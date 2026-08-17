# Exchange 도메인 트랜잭션 격리 수준 분석 보고서

## 1. 분석 개요

본 문서는 Exchange 도메인의 모든 DB 접근 경로를 트랜잭션 격리 수준(Isolation Level) 관점에서 분석한 결과입니다.  
대상 DBMS는 **PostgreSQL (MVCC 기반, 기본값: READ COMMITTED)**이며,  
다중 백엔드(Scale-Out) 환경에서 각 격리 수준별로 어떤 비정상 현상이 발생 가능한지를 실험적으로 규명하는 것을 목표로 합니다.

---

## 2. 현재 아키텍처의 트랜잭션 경계 요약

| # | 메서드 / 경로 | 트랜잭션 경계 | 락 전략 | 격리 수준 |
|---|---|---|---|---|
| 1 | `createIntent()` | `@Transactional` (Spring 기본: READ_COMMITTED) | 없음 (중복 체크는 UNIQUE 제약) | READ COMMITTED |
| 2 | `deleteIntent()` | `@Transactional` | `findByIdForUpdate` (room 엔티티만 PESSIMISTIC_WRITE) | READ COMMITTED |
| 3 | `sendMessage()` | `@Transactional` | 없음 | READ COMMITTED |
| 4 | `toggleRoom()` | `@Transactional` | `findByIdForUpdate` (room 엔티티만 PESSIMISTIC_WRITE) | READ COMMITTED |
| 5 | `getMessages()` | `@Transactional` | 없음 (읽기 전용) | READ COMMITTED |
| 6 | `createRoom()` | `@Transactional` | intent 엔티티에 `PESSIMISTIC_WRITE` | READ COMMITTED |
| 7 | `detectCyclesAndCreateRooms()` | **트랜잭션 없음** (statement-level READ COMMITTED) | 없음 (createRoom에서 락) | READ COMMITTED per statement |

---

## 3. 핵심 발견: 격리 수준별 비정상 현상 분석

### 3.1 🔴 [CRITICAL] `updateRoomStatusAndState()` — Non-Repeatable Read로 인한 방 상태 계산 오류

#### 발생 위치
`ExchangeService.updateRoomStatusAndState()` 내부:
```java
// Step 1: room_intent 목록 조회 (일반 SELECT)
List<ExchangeRoomIntentEntity> roomIntents = roomIntentRepository.findByTermAndRoomId(term, roomId);
int n = roomIntents.size();
int d = (int) roomIntents.stream().filter(ExchangeRoomIntentEntity::isDeleted).count();
int o = (int) roomIntents.stream().filter(ri -> !ri.isDeleted() && !ri.isOn()).count();

// Step 2: room 엔티티에 락 획득
ExchangeRoomEntity room = roomRepository.findByIdForUpdate(term, roomId)
        .orElseThrow(...);

// Step 3: n, d, o 값으로 status 계산 후 room 업데이트
```

#### 문제 메커니즘 (READ COMMITTED)
READ COMMITTED에서는 **각 statement가 독립적인 스냅샷**을 생성합니다.  
Step 1의 SELECT와 Step 2의 SELECT FOR UPDATE 사이에 **다른 트랜잭션의 커밋이 끼어들 수 있습니다.**

#### 공격 시나리오

```
Timeline:
─────────────────────────────────────────────────────────────────────
TX-A (User A intent 삭제)          │ TX-B (User B 같은 방 intent 삭제)
─────────────────────────────────────────────────────────────────────
                                   │
Step1: findByTermAndRoomId(Room-1) │
  → [A(not-del), B(not-del), C(not-del)]
  → n=3, d=0, o=0                  │
                                   │ Step1: findByTermAndRoomId(Room-1)
                                   │   → [A(not-del), B(not-del), C(not-del)]
                                   │   → n=3, d=0, o=0
                                   │
Step2: findByIdForUpdate(Room-1)   │
  → 락 획득 ✓                      │ Step2: findByIdForUpdate(Room-1)
                                   │   → TX-A가 홀딩 중 → BLOCKED
                                   │
ri.markDeleted() (A의 것)          │
flush → DB 반영                    │
                                   │
Step3: d=1 → PARTIAL_DELETE 저장   │
COMMIT → 락 해제                   │
                                   │ (이제 TX-B 진행)
                                   │ Step1의 스냅샷은 이미 고정됨!
                                   │ → n=3, d=0 (TX-B는 A의 삭제를 모름)
                                   │ → BUT ri.markDeleted() (B의 것)는 flush됨
                                   │ → Step3: d=0 → ACTIVE 저장  ← ❌ 잘못된 상태!
                                   │ COMMIT
```

**결과**: 실제로 A, B 둘 다 삭제되어 `PARTIAL_DELETE` 또는 `ALL_DELETE`가 되어야 하지만,  
TX-B는 TX-A의 변경을 보지 못해 `ACTIVE`로 잘못 설정합니다.

#### REPEATABLE READ에서는?
PostgreSQL의 REPEATABLE READ는 **트랜잭션 시작 시점의 단일 스냅샷**을 유지합니다.
- TX-A와 TX-B 모두 트랜잭션 시작 시점의 동일한 데이터를 봄
- 단, TX-B가 `findByIdForUpdate`에서 TX-A의 변경과 충돌 → **First-committer-wins 롤백** (`could not serialize access due to concurrent update`)
- 애플리케이션 재시도 로직이 필요하지만, **데이터 정합성은 보장**됨

#### SERIALIZABLE에서는?
- SSI가 감지하여 TX-B를 **serialization failure**로 롤백
- 재시도가 필요하지만 정합성 보장

---

### 3.2 🔴 [CRITICAL] `deleteIntent()` — Phantom Read로 인한 영향받는 방 누락

#### 발생 위치
`ExchangeService.deleteIntent()`:
```java
// Step 1: 이 intent가 속한 방 목록 조회
List<ExchangeRoomIntentEntity> affectedRoomIntents = 
    roomIntentRepository.findByTermAndIntentId(term, intentId);

// Step 2: 각 방에 대해 상태 업데이트
for (ExchangeRoomIntentEntity ri : affectedRoomIntents) {
    ri.markDeleted();
    updateRoomStatusAndState(term, ri.getRoomId(), intentId, memberId);
}
```

#### 문제 메커니즘 (READ COMMITTED)
Step 1에서 방 목록을 조회한 후, Step 2의 for-loop 동안 다른 트랜잭션이 **새로운 room_intent 레코드를 삽입**할 수 있습니다.

#### 공격 시나리오

```
Timeline:
─────────────────────────────────────────────────────────────────────
TX-A (User A intent 삭제)          │ TX-C (방 생성 Worker)
─────────────────────────────────────────────────────────────────────
Step1: findByTermAndIntentId(A)    │
  → [Room-1]                       │
                                   │ createRoom():
                                   │   Room-2에 A의 intent 매핑 삽입
                                   │   COMMIT
                                   │
Step2: Room-1만 업데이트           │
  → Room-2는 처리하지 않음 ← ❌    │
```

**결과**: Room-2에 A가 참여 중인데, A의 intent 삭제 시 Room-2의 상태 갱신이 누락됩니다.

**실제 발생 가능성**: 현재 아키텍처에서는 Worker가 createRoom()을 호출할 때 `PESSIMISTIC_WRITE`로 intent를 잠그므로,  
TX-A가 intent를 삭제 중(아직 커밋 전)이라면 Worker는 락 대기 → TX-A 커밋 후 Worker가 진행하면서  
intent의 `is_deleted=true`를 발견하고 스킵하므로 **이 시나리오는 실제로는 방어됩니다.**

하지만 `deleteIntent`에서 intent에 락을 걸지 않으므로, **반대 방향**의 레이스가 가능합니다:
- Worker가 intent를 lock하고 createRoom 진행 중
- 동시에 User A가 deleteIntent 호출 → intent를 삭제 (Worker가 걸지 않은 상태)
- Worker가 커밋 → Room 생성 완료
- User A의 deleteIntent는 이미 조회한 방 목록(Room-1)만 업데이트 → Room-2 누락

→ **실제 발생 가능한 시나리오입니다.**

---

### 3.3 🟡 [MEDIUM] `sendMessage()` — Non-Repeatable Read로 인한 활성 카운트 무력화

#### 발생 위치
`ExchangeService.sendMessage()`:
```java
// Step 1: 활성 참여자 수 체크
List<ExchangeRoomIntentEntity> allRoomIntents = 
    roomIntentRepository.findByTermAndRoomId(term, roomId);
long activeCount = allRoomIntents.stream().filter(ri -> !ri.isDeleted()).count();
if (activeCount < 2) throw new BaseException(...);

// Step 2: 메시지 저장
ExchangeRoomMessageEntity saved = messageRepository.save(...);
```

#### 문제 메커니즘
Step 1에서 activeCount=2였지만, Step 2 저장 사이에 다른 트랜잭션이 intent를 삭제하면  
활성 참여자가 1명 이하인 방에 메시지가 전송될 수 있습니다.

**영향도**: 비즈니스 규칙 위반이지만, 데이터 무결성보다는 UX 문제 수준.  
방의 활성 인원이 1명인데 메시지가 전송되는 경계 케이스.

---

### 3.4 🟡 [MEDIUM] `toggleRoom()` — 3.1과 동일한 Non-Repeatable Read

`toggleRoom()`도 `updateRoomStatusAndState()`를 호출하므로 3.1과 동일한 문제가 발생합니다.

추가적으로:
```java
for (ExchangeRoomIntentEntity ri : myRoomIntents) {
    ri.toggle(request.isOn());  // JPA 1차 캐시에서 수정
}
updateRoomStatusAndState(term, roomId, ...);
```
자기 자신의 toggle 변경은 auto-flush로 `updateRoomStatusAndState` 내부 SELECT에 반영되지만,  
**다른 트랜잭션의 동시 변경**은 READ COMMITTED에서 보장이 안 됩니다.

---

### 3.5 🟢 [LOW] `detectCyclesAndCreateRooms()` — Stale Read (완전히 방어됨)

```java
List<ExchangeIntentEntity> allActive = intentRepository.findByTermAndIsDeletedFalse(term);
// 메모리에서 DFS → 사이클 발견
roomCreationService.createRoom(term, cycle, cycleHash);
```

allActive를 읽은 시점과 createRoom 실행 사이에 intent가 삭제될 수 있습니다.  
하지만 `createRoom()` 내부에서 **PESSIMISTIC_WRITE로 intent를 재검증**하므로:
- 삭제된 intent 발견 → null 반환 (안전)
- **이 경로는 현재 설계에서 올바르게 방어되고 있습니다.**

---

### 3.6 🟢 [LOW] `createIntent()` — 안전

중복 체크는 `findBy...AndIsDeletedFalse` + UNIQUE 제약으로 이중 방어됩니다.  
READ COMMITTED에서 중복 INSERT가 동시에 발생해도 UNIQUE 제약이 차단합니다.

---

## 4. 격리 수준별 종합 비교표

| 비정상 현상 | READ COMMITTED<br>(현재) | REPEATABLE READ<br>(PostgreSQL) | SERIALIZABLE<br>(PostgreSQL SSI) |
|---|:---:|:---:|:---:|
| **3.1 방 상태 계산 오류** (Non-Repeatable Read) | 🔴 발생 | 🟡 동시 수정 시 롤백 (재시도 필요) | 🟢 방지 (롤백 + 재시도) |
| **3.2 영향받는 방 누락** (Phantom Read) | 🔴 발생 | 🟡 동시 수정 시 롤백 | 🟢 방지 |
| **3.3 활성 카운트 무력화** | 🟡 발생 (경계) | 🟢 방지 | 🟢 방지 |
| **3.4 토글 상태 불일치** | 🔴 발생 | 🟡 동시 수정 시 롤백 | 🟢 방지 |
| **3.5 사이클 탐색 Stale Read** | 🟢 방어됨 | 🟢 방어됨 | 🟢 방어됨 |
| **성능 오버헤드** | ✅ 최소 | ⚠️ 동시 수정 시 롤백 비용 | ⚠️ 직렬화 실패 빈도↑ |
| **Deadlock 위험** | ✅ 낮음 (락 범위 좁음) | ⚠️ 보통 | ⚠️ 높음 (범위 락) |

---

## 5. PostgreSQL 특이사항과 프로젝트 영향

### 5.1 PostgreSQL READ COMMITTED의 특징
- **Dirty Read**: 절대 발생 안 함 (MVCC)
- **Non-Repeatable Read**: 각 statement마다 새 스냅샷 → **발생**
- **Phantom Read**: 발생
- **Write Skew**: 발생 가능

### 5.2 PostgreSQL REPEATABLE READ의 특징
- 트랜잭션 시작 시점의 **단일 스냅샷 유지**
- **Phantom Read까지 MVCC로 방지** (표준 RR과 다름)
- 동시 수정 시 **first-committer-wins**: 먼저 커밋한 쪽이 승리, 나중 쪽은 롤백
  - 에러: `ERROR: could not serialize access due to concurrent update`
  - → 애플리케이션 **재시도 로직 필수**

### 5.3 PostgreSQL SERIALIZABLE의 특징
- **SSI (Serializable Snapshot Isolation)** 알고리즘
- 락 없이도 직렬화 가능성 보장
- **Serialization failure** 감지 시 롤백
  - 에러: `ERROR: could not serialize access due to read-write dependencies among transactions`
  - → 애플리케이션 **재시도 로직 필수**
- 읽기 전용 트랜잭션은 절대 충돌 안 함

---

## 6. 수정 방안 (격리 수준별)

### 6.1 READ COMMITTED 유지 시 (락 보강)

**핵심 수정: `updateRoomStatusAndState()`에서 room_intent 조회에도 락 적용**

```java
private void updateRoomStatusAndState(String term, Long roomId, Long triggerIntentId, Long triggerMemberId) {
    // 1. room에 먼저 락 (순서 일관성)
    ExchangeRoomEntity room = roomRepository.findByIdForUpdate(term, roomId)
            .orElseThrow(() -> new BaseException(ErrorCode.EXCHANGE_ROOM_NOT_FOUND));
    
    // 2. 락 획득 후에 room_intent 조회 → 일관성 보장
    List<ExchangeRoomIntentEntity> roomIntents = roomIntentRepository.findByTermAndRoomId(term, roomId);
    int n = roomIntents.size();
    int d = (int) roomIntents.stream().filter(ExchangeRoomIntentEntity::isDeleted).count();
    // ...
}
```

**추가 수정: `deleteIntent()`에서 intent에도 락 적용**

```java
@Transactional
public IntentDeleteResponse deleteIntent(Long memberId, Long intentId) {
    // intent를 PESSIMISTIC_WRITE로 잠금
    ExchangeIntentEntity intent = intentRepository.findByIdForUpdate(term, intentId)
            .orElseThrow(...);
    // ...
}
```

**장점**: 격리 수준 변경 없이 현재 성능 유지  
**단점**: 락 순서 관리 필요 (Deadlock 방지 위해 항상 room → room_intent 순서로 락)

### 6.2 REPEATABLE READ로 승격 시

```java
@Transactional(isolation = Isolation.REPEATABLE_READ)
public IntentDeleteResponse deleteIntent(Long memberId, Long intentId) { ... }
```

**필수 대응**:
- 동시 수정 충돌 시 `PessimisticLockingFailureException` / `CannotAcquireLockException` 발생
- **재시도 로직** 구현 필요 (최대 3회, exponential backoff)
- `createRoom()`의 PESSIMISTIC_WRITE는 RR에서도 정상 동작

**장점**: Non-Repeatable Read, Phantom Read 완전 방지  
**단점**: 동시성 높은 상황에서 롤백 빈도 증가

### 6.3 SERIALIZABLE로 승격 시

```java
@Transactional(isolation = Isolation.SERIALIZABLE)
public IntentDeleteResponse deleteIntent(Long memberId, Long intentId) { ... }
```

**필수 대응**:
- Serialization failure 재시도 로직 필수
- 모든 읽기 전용 트랜잭션은 충돌 없음 (성능 영향 최소화)
- 쓰기 트랜잭션이 많을수록 롤백 빈도 급증

---

## 7. 권장 전략

### 7.1 하이브리드 접근 (권장)

| 작업 유형 | 권장 격리 수준 | 근거 |
|---|---|---|
| Intent 등록 (`createIntent`) | READ COMMITTED | UNIQUE 제약으로 충분 |
| Intent 삭제 (`deleteIntent`) | **REPEATABLE READ** | 여러 테이블에 걸친 상태 재계산 정합성 필요 |
| 방 생성 (`createRoom`) | READ COMMITTED | PESSIMISTIC_WRITE로 이미 방어됨 |
| 메시지 전송 (`sendMessage`) | READ COMMITTED | 단순 INSERT, 경계 케이스 허용 가능 |
| 방 토글 (`toggleRoom`) | **REPEATABLE READ** | 상태 재계산 정합성 필요 |
| 메시지 조회 (`getMessages`) | READ COMMITTED | 읽기 전용, 캐시로 보호됨 |

### 7.2 재시도 로직 (공통)

```java
@Aspect
@Component
public class RetryOnIsolationException {
    @Around("@annotation(org.springframework.transaction.annotation.Transactional)")
    public Object retry(ProceedingJoinPoint pjp) throws Throwable {
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            try {
                return pjp.proceed();
            } catch (PessimisticLockingFailureException | CannotAcquireLockException e) {
                if (i == maxRetries - 1) throw e;
                Thread.sleep((long) Math.pow(2, i) * 50); // 50ms, 100ms, 200ms
            }
        }
        throw new IllegalStateException("Unreachable");
    }
}
```

---

## 8. 이력서 기록용 요약

### 3줄 요약

> **수강신청 교환 시스템의 그래프 사이클 탐색·채팅방 상태 관리 도메인에서,  
> PostgreSQL MVCC의 격리 수준별 동시성 결함(Non-Repeatable Read, Phantom Read, Write Skew)을 분석하고,  
> 하이브리드 격리 수준(REPEATABLE READ + PESSIMISTIC_WRITE)과 재시도 로직으로 다중 백엔드 환경의 데이터 정합성을 보장.**

### 세부 키워드 (이력서 기술용)

- **트랜잭션 격리 수준 설계**: READ COMMITTED / REPEATABLE READ / SERIALIZABLE 각 수준에서의 비정상 현상(Non-Repeatable Read, Phantom Read, Lost Update)을 실험·분석
- **PostgreSQL MVCC 동작 원리 이해**: statement-level snapshot vs transaction-level snapshot, first-committer-wins 충돌 메커니즘, SSI(Serializable Snapshot Isolation) 알고리즘
- **비관적 락(Pessimistic Lock) 전략**: SELECT FOR UPDATE를 활용한 Read-Modify-Write 패턴 보호, 락 순서 일관화로 Deadlock 방지
- **하이브리드 격리 수준 적용**: 읽기 중심 경로(READ COMMITTED)와 정합성 필수 경로(REPEATABLE READ)의 분리 적용
- **분산 환경 캐시 정합성**: Double Eviction 전략, afterCommit 훅 기반 캐시 무효화, RDB를 Single Source of Truth로 유지
- **재시도 로직 구현**: Isolation 충돌 시 Exponential Backoff 기반 자동 재시도
