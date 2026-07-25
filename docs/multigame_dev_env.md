# Multigame 개발/운영 환경 분리 설계

## 개요

멀티게임 도메인은 **예약 → 게임 진행 → 결과**의 3단계 라이프사이클을 가집니다.
운영 환경에서는 스케줄러가 자동으로 게임을 초기화하지만, 개발 환경에서는 테스트 용이성을 위해 예약 생성 시 즉시 게임이 초기화됩니다.

---

## 환경별 동작 차이

| 항목 | 운영 환경 (prod) | 개발 환경 (dev) |
|------|-----------------|----------------|
| **게임 초기화 시점** | T-5m (스케줄러) | 예약 생성 즉시 |
| **LifecycleScheduler** | 활성화 | 비활성화 |
| **DevGameInitializer** | 빈 없음 | 빈 주입 |
| **LifeCycleControlController** | 비활성화 | 활성화 |
| **상태 수동 전이** | 불가 | 가능 |

---

## 아키텍처

```
┌─────────────────────────────────────────────────────────────────┐
│                    MultigameReservationService                   │
│                         (순수 CRUD, 공통)                        │
│                                                                  │
│  create() ──► 예약 저장 ──► devGameInitializer.ifPresent(...)   │
└───────────────────────────┬──────────────────────────────────────┘
                            │
              ┌─────────────┴─────────────┐
              │                           │
    ┌─────────▼─────────┐      ┌──────────▼──────────┐
    │   prod 환경       │      │    dev 환경         │
    │                   │      │                     │
    │ Optional.empty()  │      │ DevGameInitializer  │
    │ (빈 없음)         │      │ - initializeGame()  │
    │                   │      │ - transitionState() │
    │ LifecycleScheduler│      │ - getState()        │
    │ - WaitingJob      │      │                     │
    │ - ReadyJob        │      │ LifeCycleControl    │
    │ - ProgressJob     │      │ Controller          │
    │ - EndingJob       │      │ - GET /state        │
    │                   │      │ - POST /transition  │
    └───────────────────┘      └─────────────────────┘
```

---

## 핵심 구현

### 1. MultigameReservationService (공통)

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MultigameReservationService {

    private final MultigameReservationRepository reservationRepository;
    
    // ===== 환경별 주입 차이 =====
    // prod: Optional.empty()
    // dev:  DevGameInitializer 빈 주입
    private final Optional<DevGameInitializer> devGameInitializer;

    @Transactional
    public MultigameReservationResponse create(Long memberId, MultigameReservationCreateRequest request) {
        // ... 순수 CRUD 로직 (시간 검증, 중복 체크, 저장) ...
        
        MultigameReservationEntity saved = reservationRepository.save(entity);

        // ===== 개발 환경 전용 로직 =====
        // dev 프로필에서만 DevGameInitializer 빈이 존재하므로,
        // 예약 생성 시 즉시 WAITING 상태로 초기화하여 테스트 가능하게 함
        // 운영 환경에서는 LifecycleScheduler가 T-5m에 자동으로 초기화
        devGameInitializer.ifPresent(initializer -> {
            initializer.initializeGame(multigameId, 1);
            log.info("[DEV] 예약 생성 시 게임 자동 초기화: multigameId={}", multigameId);
        });

        return MultigameReservationResponse.from(saved);
    }
}
```

### 2. MultigameLifecycleScheduler (prod 전용)

```java
@Slf4j
@Service
@RequiredArgsConstructor
@Profile("!dev")  // ← prod 환경에서만 활성화
public class MultigameLifecycleScheduler {

    private static final String WAITING_CRON = "0 5/10 * * * *";  // T-5m
    private static final String READY_CRON = "50 9/10 * * * *";   // T-10s
    private static final String PROGRESS_CRON = "0 0/10 * * * *"; // T
    private static final String ENDING_CRON = "20 0/10 * * * *";  // T+20s

    @PostConstruct
    public void init() {
        // 4개의 Cron Job 스케줄링
        multigameScheduler.schedule(this::waitingJob, new CronTrigger(WAITING_CRON));
        multigameScheduler.schedule(this::readyJob, new CronTrigger(READY_CRON));
        multigameScheduler.schedule(this::progressJob, new CronTrigger(PROGRESS_CRON));
        multigameScheduler.schedule(this::endingJob, new CronTrigger(ENDING_CRON));
    }

    private void waitingJob() {
        // T-5m: 예약 확인 → 게임 초기화 (WAITING 상태)
    }
    // ...
}
```

### 3. DevGameInitializer (dev 전용)

```java
@Slf4j
@Component
@RequiredArgsConstructor
@Profile("dev")  // ← dev 환경에서만 활성화
public class DevGameInitializer {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 게임을 WAITING 상태로 초기화
     * dev 환경에서 예약 생성 시 자동 호출됨
     */
    public void initializeGame(String multigameId, int participantCount) {
        // Redis 키 초기화: state, seq, admissionLimit, seats
        stringRedisTemplate.opsForValue().set(stateKey, "WAITING");
        // ...
    }

    /**
     * 게임 상태를 수동으로 전이 (테스트용)
     */
    public void transitionState(String multigameId, String targetState) {
        // WAITING → READY → PROGRESS → ENDED → FINALIZE
    }

    /**
     * 현재 게임 상태 조회
     */
    public String getState(String multigameId) {
        return stringRedisTemplate.opsForValue().get(stateKey);
    }
}
```

### 4. MultigameLifeCycleControlController (dev 전용)

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/{version}/multigame/lifecycle")
@Profile("dev")  // ← dev 환경에서만 활성화
@PreAuthorize("hasRole('ADMIN')")
public class MultigameLifeCycleControlController {

    private final DevGameInitializer devGameInitializer;

    @GetMapping("/state/{multigameId}")
    public ResponseEntity<SingleSuccessResponseEnvelope<String>> getState(
            @PathVariable String multigameId) {
        return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(
            devGameInitializer.getState(multigameId)
        ));
    }

    @PostMapping("/transition/{multigameId}")
    public ResponseEntity<SingleSuccessResponseEnvelope<String>> transitionState(
            @PathVariable String multigameId,
            @RequestParam String targetState) {
        devGameInitializer.transitionState(multigameId, targetState);
        return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(
            devGameInitializer.getState(multigameId)
        ));
    }
}
```

---

## 상태 머신 (State Machine)

```
                    ┌──────────────────────────────────────┐
                    │                                      │
                    ▼                                      │
WAITING ──► READY ──► PROGRESS ──► ENDED ──► FINALIZE    │
   │           │          │          │                     │
   │           │          │          │                     │
   └───────────┴──────────┴──────────┴──────────► CANCELLED
                    │
            (참여자 < 2명)
```

| 상태 | 의미 | dev 수동 전이 |
|------|------|--------------|
| `WAITING` | 게임 대기 중 (예약 있음) | ✓ |
| `READY` | 게임 확정 (참여자 ≥ 2명) | ✓ |
| `PROGRESS` | 게임 진행 중 (신청 가능) | ✓ |
| `ENDED` | 게임 종료 | ✓ |
| `FINALIZE` | 결과 정산 완료 | ✓ |
| `CANCELLED` | 게임 취소 | ✓ |

---

## dev 환경 테스트 시나리오

### 시나리오 1: 예약 → 대기방 → 게임 진행

```bash
# 1. 게스트 로그인
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/guest | jq -r '.data.accessToken')

# 2. 예약 생성 (자동으로 WAITING 상태)
curl -X POST http://localhost:8080/api/v1/multigame/reservations \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"multigameId": "20260726100000"}'

# 3. 상태 확인 (WAITING)
curl http://localhost:8080/api/v1/multigame/lifecycle/state/20260726100000 \
  -H "Authorization: Bearer $TOKEN"

# 4. 대기방 입장
curl -X POST http://localhost:8080/api/v1/multigame/session/waiting-room \
  -H "Authorization: Bearer $TOKEN"

# 5. 상태 전이: WAITING → READY
curl -X POST "http://localhost:8080/api/v1/multigame/lifecycle/transition/20260726100000?targetState=READY" \
  -H "Authorization: Bearer $TOKEN"

# 6. 상태 전이: READY → PROGRESS
curl -X POST "http://localhost:8080/api/v1/multigame/lifecycle/transition/20260726100000?targetState=PROGRESS" \
  -H "Authorization: Bearer $TOKEN"

# 7. 게임 신청
curl -X POST "http://localhost:8080/api/v1/multigame/session/request?subjectId=1" \
  -H "Authorization: Bearer $TOKEN"
```

### 시나리오 2: k6 부하 테스트

```bash
# small 테스트 (50 VU, 예약 + 대기방)
zsh k6/run.sh multigame small

# large 테스트 (1000 VU, 게임 세션 집중)
zsh k6/run.sh multigame large
```

---

## 파일 구조

```
src/main/java/com/mjusugangsincheonghelper/multigame/
├── common/
│   ├── GameTimeCalculator.java
│   ├── MultigameLuaScript.java
│   └── MultigameRedisKeyProvider.java
│
├── reservation/
│   ├── controller/MultigameReservationController.java
│   ├── dto/
│   └── service/MultigameReservationService.java  ← Optional<DevGameInitializer>
│
├── session/
│   ├── controller/
│   │   ├── MultigameSessionController.java
│   │   └── MultigameLifeCycleControlController.java  ← @Profile("dev")
│   ├── dto/
│   └── service/
│       ├── MultigameLifecycleScheduler.java  ← @Profile("!dev")
│       ├── DevGameInitializer.java           ← @Profile("dev")
│       ├── MultigameSessionService.java
│       ├── WaitingRoomService.java
│       ├── GameQueueService.java
│       ├── SupplyEngineService.java
│       └── MultigameFinalizeService.java
│
├── my/                                 # 내 참여 기록
│   ├── controller/MultigameMyController.java
│   ├── dto/
│   └── service/MultigameMyHistoryService.java
│
├── result/                             # 각 게임의 세부 결과
│   ├── controller/MultigameResultController.java
│   ├── dto/
│   └── service/MultigameResultService.java
│
├── stats/                              # 통계 (전체 + 내 통계)
│   ├── controller/MultigameStatsController.java
│   ├── dto/
│   └── service/
│       ├── MultigameMyStatsService.java
│       └── MultigameDepartmentStatsService.java
│
└── dashboard/                          # 대시보드
    ├── controller/MultigameDashboardController.java
    ├── dto/
    └── service/MultigameDashboardService.java
```

---

## 설계 원칙

| 원칙 | 설명 |
|------|------|
| **관심사 분리** | 운영 로직과 개발 편의 로직을 명확히 분리 |
| **조건부 빈** | `@Profile`과 `Optional` injection으로 런타임 분기 |
| **코드 중복 최소화** | `MultigameReservationService`는 환경에 관계없이 동일한 CRUD 로직 |
| **테스트 용이성** | dev 환경에서 스케줄러 대기 없이 즉시 테스트 가능 |
| **운영 안전성** | prod 환경에서 dev 전용 코드가 완전히 제거됨 |

---

## 참고

- [multigame.md](./multigame.md) - 멀티게임 전체 아키텍처
- [multigame_append.md](./multigame_append.md) - 결과/통계 기능
- [auth_architecture.md](./auth_architecture.md) - 인증 아키텍처 (환경 분리 패턴 참고)
