# Exchange 도메인 트랜잭션 격리 수준 분석 및 동시성 결함 수정

본 문서는 표준 트랜잭션 격리 개념(ANSI SQL / Berenson et al. 의 anomaly 분류)을 기준으로
Exchange 도메인의 동시성 결함을 정확히 진단하고, 최소 변경(READ COMMITTED 유지)으로 수정한 내용을 기술한다.
다중 백엔드(scale-out) 환경에서 DB 락/유니크 제약이 어떻게 정합성을 보장하는지가 핵심이다.

---

## 1. 현재 트랜잭션/잠금 현황

| 항목 | 값 |
|---|---|
| DBMS | PostgreSQL (MVCC) |
| 기본 격리 수준 | **READ COMMITTED** (애플리케이션에 `isolation=` 오버라이드 없음) |
| 낙관적 락(`@Version`) | 사용处 없음 |
| 비관적 락(`PESSIMISTIC_WRITE`) | `createRoom` 의 intent 행, `updateRoomStatusAndState` 의 room 행 (2곳) |
| 유니크 제약(방어) | `udix_active_intent`(부분유니크), `uniq_term_cycle_hash` |
| 비동기 큐 | PGMQ (visibility timeout + 멱등) |
| 캐시 | Redis read-through + Double Eviction(afterCommit) |

---

## 2. 결함의 정확한 표준 분류

`updateRoomStatusAndState` 는 자식 행 `exchange_room_intent` 의 집계(`n=전체, d=삭제, o=OFF`)로
부모 행 `exchange_room.status` 라는 **파생값(derived value)** 을 갱신한다.

수정 전 코드(결함):

```java
List<ExchangeRoomIntentEntity> roomIntents = roomIntentRepository.findByTermAndRoomId(term, roomId); // (1) 락 없는 읽기
int n = ...; int d = ...; int o = ...;                                                                  // (2) 집계 산출
ExchangeRoomEntity room = roomRepository.findByIdForUpdate(term, roomId)...;                           // (3) 락 획득(이미 늦음)
room.updateStatus(newStatus);                                                                           // (4) 갱신
```

(1)의 읽기와 (3)의 락 사이에 시차가 존재하고, (1)은 자식 행을 잠그지 않는다. 따라서 다른 트랜잭션이
자식 행을 변경해도 (3)의 부모 행 락은 그것을 막지 못한다.

표준 anomaly 분류:

| anomaly (표준) | 본 케이스 해당 여부 | 근거 |
|---|---|---|
| Dirty Read (P1) | ❌ | PG 는 READ UNCOMMITTED 여도 READ COMMITTED 로 승격되어 발생 불가 |
| Non-Repeatable Read (P2, Fuzzy Read) | △ 부분 | 같은 행 재읽기가 아니라 1회 읽기 후 갱신이므로 엄밀한 P2 는 아님 |
| Phantom Read (P3) | ❌ | 범위 재질의가 아님 |
| **Lost Update (P4)** | ✅ | T1 이 갱신한 `room.status` 를 T2 가 stale 읽기 기반으로 덮어쓴다(갱신 분실). 단, 본 케이스는 "같은 행 읽고 갱신"하는 전형적 P4 가 아니라 **파생값(자식 집계 → 부모 갱신) 형태** 의 Lost Update |
| **Read Skew (A5A)** | ✅(원인) | T2 가 자식 행(`room_intent`)은 락 전 스냅샷, 부모 행(`room.status`)은 락 후 최신 커밋으로 읽어 일관되지 않은 두 읽기를 혼용 |

**정확한 한 줄 진단**:
> 파생된 상태값(자식 행 `exchange_room_intent` 집계로 산출되는 부모 행 `exchange_room.status`)을
> 갱신하는 과정에서 읽기-갱신 구간이 잠금으로 보호되지 않아, READ COMMITTED 격리 수준에서
> **Lost Update(P4)** 와 **Read Skew(A5A)** 가 발생한다.

---

## 3. 재현 시나리오 (2인 방 A, B 동시 철회)

수정 전 (READ COMMITTED):

1. T1(A 철회): (1) `room_intent` 읽음 → A,B 활성 + 자기 A삭제 → **d=1 → PARTIAL_DELETE** 산출 → (3) Room 락 → (4) 갱신 → 커밋.
2. T2(B 철회, T1 커밋 **전**에 (1) 읽기 시작): 같은 시점 스냅샷 → A,B 활성 + 자기 B삭제 → **d=1 → PARTIAL_DELETE** 산출.
3. T2 는 Room 락 대기 → T1 커밋 후 락 획득. 하지만 (1)에서 구한 d=1 을 **재계산하지 않음**.
4. T2 가 PARTIAL_DELETE 로 커밋.
5. 실제 DB: A,B 모두 삭제 → **ALL_DELETE** 가 맞음. 결과: 방 상태가 `PARTIAL_DELETE` 로 오기록 + 시스템 메시지도 "일부 참여자…"로 오남발.

→ **조용한 부정합(silent corruption)**. 사용자에게 에러 없이 잘못된 상태가 노출된다.

---

## 4. 격리 수준별 표준 동작 (PostgreSQL 기준)

| 시나리오 | READ UNCOMMITTED | READ COMMITTED (수정 전) | REPEATABLE READ (PG) | SERIALIZABLE (PG SSI) |
|---|---|---|---|---|
| 중복 Intent 등록 | 발생 | 발생(앱체크 무력) → 부분유니크인덱스가 커밋 단계에서 한 쪽 중단 → 방어 | 동일(인덱스 방어) | 동일 + SSI 직렬화 충돌로 한 쪽 중단 |
| **Room status Lost Update** (본 결함) | 조용한 오기록 | **조용한 오기록(버그)** | Room 행 동시 UPDATE → T2 에 `could not serialize access due to concurrent update` 에러 → 명시적 실패로 전환(재시도 필요) | SSI 가 rw 충돌 감지 → T2 중단 |
| createRoom 중복 사이클 | 발생 | 발생 → `uniq_term_cycle_hash` 유니크제약으로 한 쪽 `DataIntegrityViolation` → 방어 | 동일 | 동일 + SSI 추가 방어 |
| 탐색-생성 사이 Intent 철회 | 사이클 끊김 | `createRoom` 이 intent 행 `SELECT FOR UPDATE` 로 `isDeleted` 재검증 → 안전 | 안전 | 안전 |
| sendMessage activeCount≥2 TOCTOU (소프트) | 좀비 메시지 가능 | 좀비 메시지 가능 | 탐지 어려움 | SSI predicate 충돌로 중단 가능 |
| 데드락 | — | `createRoom` 은 intent id 정렬순 락 → 회피 | 동일 | 동일 |

**요점**:
- READ COMMITTED(수정 전)에서는 유니크 제약/인덱스가 잡아주는 중복 문제는 방어되지만,
  **파생값 갱신의 Lost Update 는 방어되지 않아 조용히 부정합**이 생긴다.
- PG 에서 READ COMMITTED → REPEATABLE READ 로 올리면 이 Lost Update 가 *조용한 부정합*에서
  *명시적 에러(재시도 필요)* 로 바뀐다(first-committer-wins). SERIALIZABLE(SSI)은 predicate 기반까지 잡지만 비용↑.
- 본 수정은 격리 수준을 올리지 않고 **잠금 범위 확장**으로 동일 정합성을 READ COMMITTED 에서 달성한다.

---

## 5. 수정: 잠금 범위 확장 (최소 변경, READ COMMITTED 유지)

선택: 사용자 제시 옵션 중 (b) **"Room 락 획득 후 room_intent 재읽기"**.

```java
private void updateRoomStatusAndState(String term, Long roomId, Long triggerIntentId, Long triggerMemberId) {
    // (3) 부모 행 락을 먼저 획득 → 이 방에 대한 모든 writer 를 직렬화
    ExchangeRoomEntity room = roomRepository.findByIdForUpdate(term, roomId)
            .orElseThrow(() -> new BaseException(ErrorCode.EXCHANGE_ROOM_NOT_FOUND));

    // (1) 락 확보 이후 자식 행을 읽어 집계 → 읽기-갱신 구간이 동시 트랜잭션에 끼어들 수 없음
    List<ExchangeRoomIntentEntity> roomIntents = roomIntentRepository.findByTermAndRoomId(term, roomId);
    int n = roomIntents.size();
    int d = (int) roomIntents.stream().filter(ExchangeRoomIntentEntity::isDeleted).count();
    int o = (int) roomIntents.stream().filter(ri -> !ri.isDeleted() && !ri.isOn()).count();
    int activeCount = n - d;
    ...
}
```

### 왜 (a) `room_intent` 행 `FOR UPDATE` 가 아니라 (b) 인가 (정확한 근거)

1. `exchange_room` 은 방 1개당 1행 → 단일 행 `SELECT FOR UPDATE` 로 해당 방에 대한 모든 writer
   (`deleteIntent`/`toggleRoom` 모두 `updateRoomStatusAndState` 경유)를 직렬화할 수 있다.
2. 옵션 (a) 처럼 `room_intent` 행에 `FOR UPDATE` 를 걸면, `markDeleted()` setter 의 **auto-flush UPDATE**
   가 정렬되지 않은 순서로 행 잠금을 먼저 선점한다. 두 멤버가 동시 철회할 때
   `T1: UPDATE(R,A)→FOR UPDATE[(R,A),(R,B)]` 와 `T2: UPDATE(R,B)→FOR UPDATE[(R,A),(R,B)]` 가
   **A-B 데드락** 을 유발한다. (b) 는 부모 단일 행이므로 데드락이 없다.
3. 서로 다른 멤버는 서로 다른 `room_intent` 행을 건드린다. 따라서 T2 가 미리 로드하지 않은 상대 행은
   락 후 JPA 질의로 DB 최신 커밋을 fresh-load 하므로, JPA 영속컨텍스트 캐싱 우려에도 정합하게 보인다.
   (같은 트랜잭션의 자기 쓰기 `markDeleted/toggle` 도 auto-flush 로 이 질의 직전에 반영된다.)

### 수정 후 시나리오 재확인 (2인 방 A, B 동시 철회)

1. T1(A): `markDeleted(R,A)` setter → `updateRoomStatusAndState`: **Room 락 획득** → `room_intent` 재읽기
   (auto-flush 로 (R,A) 삭제 반영, (R,B) 는 DB 최신=활성) → d=1 → PARTIAL_DELETE → 커밋.
2. T2(B): `markDeleted(R,B)` setter → `updateRoomStatusAndState`: **Room 락 대기(T1 보유)** →
   T1 커밋 후 락 획득 → `room_intent` 재읽기 ((R,B) 자기삭제, (R,A) DB 최신=T1 커밋 삭제) → **d=2 → ALL_DELETE** → 커밋.
3. 결과: 정확히 `ALL_DELETE` + "모든 참여자 철회" 시스템 메시지. **정합.**

---

## 6. 본 수정이 커버하는 것과 커버하지 않는 것 (정직한 한계)

### 커버 (수정 효과)
- 서로 다른 멤버의 동시 철회/토글로 인한 `room.status` Lost Update / Read Skew → **해결**.
- 멀티 인스턴스(scale-out) 환경에서도 DB 레벨 단일 행 락으로 직렬화되므로 인스턴스 수와 무관하게 정합.

### 커버하지 않는 잔존 엣지케이스 (별개의 표준 해법 권장)
- **동일 멤버가 같은 `room_intent` 행에 대해 동시에 deleteIntent + toggleRoom** 처럼
  두 트랜잭션이 *같은 행* 을 수정하는 경우, JPA 영속컨텍스트가 미리 로드한 엔티티를 갱신해
  동시 커밋을 마스킹할 수 있다(같은 행 Lost Update).
- 표준 해법: `ExchangeRoomIntentEntity` 에 `@Version` 컬럼(낙관적 락)을 추가하면
  동일 행 동시 갱신 시 `OptimisticLockException` 으로 변환되어 재시도로 해결된다.
  본 수정은 "최소 변경" 원칙에 따라 이 엣지케이스는 별도 후속 작업으로 둔다.

---

## 7. 이력서 방향 (정확한 표준 개념 우선, 겉멋 배제)

### 3줄 요약
> PostgreSQL 파티셔닝 + PGMQ 비동기 그래프 사이클 탐색 기반의 수강신청 교환 매칭 시스템을
> 다중 백엔드(scale-out) 정합성을 고려해 설계·구현했다.
>
> 자식 행 집계로 부모 상태값을 갱신하는 트랜잭션에서, 읽기-갱신 구간이 잠금 미보호로 인해
> READ COMMITTED 에서 **Lost Update(P4)/Read Skew(A5A)** 가 발생함을 표준 격리 개념으로 진단하고,
> 부모 행 `SELECT FOR UPDATE` 락 순서를 읽기 *전* 으로 이동해 최소 변경으로 해결했다.
>
> 중복 Intent/중복 방 생성은 부분 유니크 인덱스·`cycle_hash` 유니크 제약 + intent 행 정렬순 비관적 락으로
> DB 레벨에서 방어해 애플리케이션 레벨 TOCTOU 를 물리적으로 차단했다.

### 세부 설명 (면접용 불릿, 표준 용어 정확)
- **격리 수준의 정확한 이해**: ANSI 4단계 격리 수준과 Dirty Read/Non-Repeatable Read/Phantom Read,
  그리고 Berenson et al. 의 확장 anomaly(Lost Update P4, Read Skew A5A, Write Skew A5B) 를 구분해
  실제 결함이 P4 + A5A 임을 명확히 진단.
- **DBMS별 구현 차이 인지**: PG 는 READ UNCOMMITTED 를 READ COMMITTED 로 승격, REPEATABLE READ 에서
  MVCC 로 Phantom Read 까지 방지(단 first-committer-wins 롤백), SERIALIZABLE 은 SSI 알고리즘.
- **진단한 결함**: 파생값(자식 집계 → 부모 갱신) 형태의 Lost Update. 읽기-갱신 구간 잠금 미보호가 원인.
- **해결**: 격리 수준 상향(REPEATABLE READ/SERIALIZABLE) 없이, READ COMMITTED 유지하며
  부모 단일 행 `SELECT FOR UPDATE` 락을 읽기 전으로 이동. 옵션 (a) 의 A-B 데드락 회피 근거까지 서술.
- **방어 깊이**: 중복 Intent → 부분 유니크 인덱스 `WHERE is_deleted=FALSE`, 중복 방 → `cycle_hash`
  유니크 제약 + intent id 정렬순 비관적 락(데드락 회피), 비동기 큐 → PGMQ visibility timeout + 멱등.
- **정직한 한계**: 동일 행 동시 갱신 엣지케이스는 `@Version` 낙관적 락이 표준 해법임을 명시.

---

## 8. 후속 권장 (별도 작업)
1. `ExchangeRoomIntentEntity` `@Version` 컬럼 도입 → 동일 행 Lost Update 를 `OptimisticLockException`+재시도로 해결.
2. 본 시나리오(2인 동시 철회)를 재현하는 동시성 통합 테스트(`@SpringBootTest` + `CountDownLatch`/멀티스레드) 작성,
   수정 전/후 동작 차이 관측. 추가로 `@Transactional(isolation=REPEATABLE_READ)` 전환 시
   `could not serialize access` 에러로 전환되는 것까지 비교 측정하면 근거가 더 강력해진다.
