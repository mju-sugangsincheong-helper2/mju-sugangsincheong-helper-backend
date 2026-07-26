# Multigame Appendix (개발 및 구현 보충 명세)

본 문서는 [`docs/multigame.md`](file:///Users/shinnk/source/project/mju-sugangsincheong-heler/mju-sugangsincheong-helper-backend/docs/multigame.md) 단일 명세서에 표기되지 않은 백엔드 소스코드 차원의 **구현 체범 및 개발 환경 전용 유틸리티** 정보를 보충합니다.

---

## 1. 개발 환경 전용 게임 초기화 유틸리티 (`DevGameInitializer`)

- **파일 위치**: [`DevGameInitializer.java`](file:///Users/shinnk/source/project/mju-sugangsincheong-heler/mju-sugangsincheong-helper-backend/src/main/java/com/mjusugangsincheonghelper/multigame/session/service/DevGameInitializer.java)
- **적용 프로필**: `@Profile("dev")` (로컬 및 테스트 개발 환경에서만 활성화)
- **도입 목적**:
  - 운영/운영 유사 환경(`!dev`)에서는 `MultigameLifecycleScheduler`가 10분 마크 Cron 스케줄러(T-5m)에 따라 게임을 자동 초기화합니다.
  - 그러나 로컬 개발 환경에서는 스케줄러 대기 없이 예약 생성 시 즉시 세션을 테스트하기 위해 `DevGameInitializer`가 게임 세션을 `WAITING` 상태로 즉시 초기화하고 수동 전이를 지원합니다.
- **주요 기능**:
  - `initializeGame(multigameId, participantCount)`: 예약 즉시 Redis 키 세팅 및 `WAITING` 상태 초기화.
  - `transitionState(multigameId, targetState)`: 수동 상태 전이 (테스트용).
  - `getState(multigameId)`: 현재 상태 조회.

---

## 2. 고응집(High-Cohesion) 5대 엔진 컴포넌트 아키텍처

코드 복잡도를 낮추고 각 클래스가 단 1가지 책임만 소유하도록 구현된 5대 전용 컴포넌트 구조입니다.

```
                         MultigameLifecycleScheduler
                        (오직 시간 계산 & 구동 트리거)
                                     │
         ┌───────────────────────────┼───────────────────────────┐
         ▼                           ▼                           ▼
MultigameStateEngine        MultigameSupplyEngine       MultigameResultFinalizer
(Lock & State Machine)     (20s 적응형 공급 알고리즘)      (DB 멱등 영속화)
         │                                                       │
         └───────────────────────────┬───────────────────────────┘
                                     ▼
                              HeartbeatLedger
                       (Heartbeat SCAN & Snapshot)
```

### 컴포넌트별 소유 책임

1. **`HeartbeatLedger`** ([HeartbeatLedger.java](file:///Users/shinnk/source/project/mju-sugangsincheong-heler/mju-sugangsincheong-helper-backend/src/main/java/com/mjusugangsincheonghelper/multigame/session/domain/HeartbeatLedger.java)):
   - Redis Heartbeat 키(TTL 6s) 세팅, SCAN 인원 카운팅 및 `participant_count` 스냅샷 조회를 100% 캡슐화한 원장 컴포넌트.
2. **`MultigameStateEngine`** ([MultigameStateEngine.java](file:///Users/shinnk/source/project/mju-sugangsincheong-heler/mju-sugangsincheong-helper-backend/src/main/java/com/mjusugangsincheonghelper/multigame/session/domain/MultigameStateEngine.java)):
   - PostgreSQL Advisory Lock 획득, GameState 전이 유효성 검증 및 `CANCELLED` self-healing 처리를 100% 캡슐화한 엔진 컴포넌트.
3. **`SupplyEngineService`** ([SupplyEngineService.java](file:///Users/shinnk/source/project/mju-sugangsincheong-heler/mju-sugangsincheong-helper-backend/src/main/java/com/mjusugangsincheonghelper/multigame/session/service/SupplyEngineService.java)):
   - 20초간 매 초 대기열($L$)을 피드백받아 `admission_limit`을 적응형으로 공급하는 알고리즘 전담 서비스.
4. **`MultigameFinalizeService`** ([MultigameFinalizeService.java](file:///Users/shinnk/source/project/mju-sugangsincheong-heler/mju-sugangsincheong-helper-backend/src/main/java/com/mjusugangsincheonghelper/multigame/session/service/MultigameFinalizeService.java)):
   - Spring의 `TransactionTemplate` (`PROPAGATION_REQUIRES_NEW`) 기반으로 Redis history를 스캔하여 DB에 멱등하게 영속화하는 서비스.
5. **`MultigameLifecycleScheduler`** ([MultigameLifecycleScheduler.java](file:///Users/shinnk/source/project/mju-sugangsincheong-heler/mju-sugangsincheong-helper-backend/src/main/java/com/mjusugangsincheonghelper/multigame/session/service/MultigameLifecycleScheduler.java)):
   - 오직 시간 계산 및 4개 스케줄(T-5m, T-10s, T, T+20s)에 따른 엔진 트리거링 역할만 수행하는 얇은 오케스트레이터.

---

## 3. 정적 유틸리티 헬퍼 (`MultigameRedisKeyProvider`)

- **파일 위치**: [`MultigameRedisKeyProvider.java`](file:///Users/shinnk/source/project/mju-sugangsincheong-heler/mju-sugangsincheong-helper-backend/src/main/java/com/mjusugangsincheonghelper/multigame/common/MultigameRedisKeyProvider.java)
- **추가 헬퍼 메서드**:
  - `countHeartbeats(redisTemplate, t)`: SCAN 기반 유효 Heartbeat 카운트.
  - `initializeGameSession(redisTemplate, stateEngine, t, participantCount)`: WAITING 상태의 9개 Redis 키 일괄 초기화.
