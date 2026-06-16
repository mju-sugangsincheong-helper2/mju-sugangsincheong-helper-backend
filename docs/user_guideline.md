# User Guideline: 코드 컨벤션 & 신규 도메인 추가 가이드

이 문서는 본 프로젝트의 코드 컨벤션과 신규 패키지(도메인)를 추가할 때 따라야 할 규칙을 정의합니다.

---

## 1. 프로젝트 구조

```
src/main/java/com/mjusugangsincheonghelper/
├── MjusugangsincheonghelperApplication.java   # 진입점
├── database/
│   ├── config/
│   │   └── JpaAuditingConfig.java             # @EnableJpaAuditing
│   ├── entity/
│   │   ├── ConfigType.java                    # SystemConfig 관련 enum
│   │   ├── CourseEntity.java                  # course 테이블 (composite PK)
│   │   ├── ExampleEntity.java                 # 예제 Entity
│   │   ├── SingleGameEntity.java              # single_game 테이블
│   │   ├── SingleGameDetailEntity.java        # single_game_detail 테이블 (composite PK)
│   │   └── SystemConfig.java                  # 시스템 설정 Entity
│   └── repository/
│       ├── CourseRepository.java
│       ├── ExampleRepository.java
│       ├── MemberRepository.java
│       ├── SingleGameRepository.java
│       ├── SingleGameDetailRepository.java
│       └── SystemConfigRepository.java
├── global/
│   ├── annotation/
│   │   └── OperationErrorCodes.java
│   ├── api/
│   │   ├── code/
│   │   │   └── ErrorCode.java
│   │   ├── docs/
│   │   │   └── ErrorResponsesOperationCustomizer.java
│   │   ├── envelope/
│   │   │   ├── ResponseEnvelope.java
│   │   │   ├── SingleSuccessResponseEnvelope.java
│   │   │   ├── PagedSuccessResponseEnvelope.java
│   │   │   └── ErrorResponseEnvelope.java
│   │   ├── exception/
│   │   │   ├── BaseException.java
│   │   │   ├── ErrorDetail.java
│   │   │   └── GlobalExceptionHandler.java
│   │   ├── filter/
│   │   │   └── GlobalMetaFilter.java
│   │   ├── meta/
│   │   │   ├── PageMeta.java
│   │   │   └── ResponseMeta.java
│   │   └── support/
│   │       ├── ClientInfoExtractor.java
│   │       ├── CustomResponseMetaContextHolder.java
│   │       ├── InstanceIdProvider.java
│   │       └── MetaGenerator.java
│   └── config/
│       ├── GlobalAsyncConfig.java
│       ├── GlobalOpenApiConfig.java
│       ├── GlobalSecurityConfig.java
│       ├── GlobalWebMvcConfig.java
│       ├── HibernateStatisticsConfig.java
│       └── RedisConfig.java
├── example/
│   ├── controller/
│   │   └── ExampleController.java
│   ├── dto/
│   │   ├── ExampleCreateRequest.java
│   │   ├── ExampleDetailResponse.java
│   │   ├── ExampleEchoRequest.java
│   │   ├── ExamplePageItem.java
│   │   ├── ExampleResponse.java
│   │   └── ExampleUpdateRequest.java
│   └── service/
│       └── ExampleService.java
├── singlegame/
│   ├── controller/
│   │   └── SingleGameController.java
│   ├── dto/
│   │   ├── AnalysisResponse.java
│   │   ├── MyRecordResponse.java
│   │   ├── RankingResponse.java
│   │   ├── SingleGameDetailRequest.java
│   │   ├── SingleGameSaveRequest.java
│   │   └── SingleGameSaveResponse.java
│   └── service/
│       └── SingleGameService.java
└── system/
    ├── controller/
    │   └── SystemConfigController.java
    ├── dto/
    │   ├── SystemConfigResponse.java
    │   └── SystemConfigUpdateRequest.java
    └── service/
        └── SystemConfigService.java

src/main/resources/
├── application.yml              # spring.profiles.active=dev
├── application-dev.yml          # 개발 환경
├── application-prod.yml         # 운영 환경
└── logback-spring.xml           # 프로파일별 로깅 설정
```

> 여기서 database 의 도메인과 각 도메인명이 같지 않을 수 있다
>
> auth 의 경우 auth 도메인이지만 table 에서는 member, member_device, member_auth 를 모두 사용한다 즉 database 레이어 에서는 memberDevice 로 처리된다
>
> 현재 예시에서는 따로 명기하지 않았다

### 패키지 규칙

| 패키지 | 목적 | 규칙 |
|--------|------|------|
| `global/` | 공통 인프라 (전역 예외, 응답 봉투, 필터, 보안 등) | 도메인 로직 포함 금지 |
| `database/` | JPA Entity, Repository | SQL 관련 로직만 포함, 비즈니스 로직 금지 |
| `{domain}/` | 도메인 비즈니스 로직 | controller, dto, service 3개 하위 패키지 필수 |

---

## 2. 네이밍 컨벤션

### Java

| 대상 | 규칙 | 예시 |
|------|------|------|
| 패키지 | 소문자, 단일 단어 | `example`, `system` |
| 클래스 | PascalCase | `ExampleController`, `SingleSuccessResponseEnvelope` |
| 서비스 클래스 | PascalCase + 접미사 `Service` | `ExampleService`, `SystemConfigService` |
| DTO | Request/Response 접미사 | `ExampleCreateRequest`, `ExampleDetailResponse` |
| Entity | 단수형 PascalCase | `ExampleEntity`, `SystemConfig` |
| 메서드 | camelCase | `findById()`, `deactivate()` |
| 상수 | UPPER_SNAKE_CASE | `EXPOSE_ERROR_DETAILS_KEY` |
| Enum 값 | UPPER_SNAKE_CASE | `GLOBAL_BAD_REQUEST`, `BOOLEAN` |

### DB / Redis

| 대상 | 규칙 | 예시 |
|------|------|------|
| 테이블명 | snake_case, 단수형 | `examples`, `system_config` |
| 컬럼명 | snake_case | `config_key`, `created_at` |
| Redis 키 | kebab-case, `::` 구분자 | `system-config::expose-error-details` |
| Cache name | kebab-case | `system-config`, `user-intents` |

### API

| 대상 | 규칙 | 예시 |
|------|------|------|
| URL path | kebab-case | `/api/v1/system/configs` |
| Path variable | camelCase | `{configKey}` |
| Query param | camelCase | `?page=0&size=10` |
| JSON field | camelCase | `"requestId"`, `"configKey"` |

---

## 3. Controller 작성 규칙

### 필수 사항

```java
@Tag(name = "Domain", description = "도메인 API")       // Swagger 그룹
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/{version}/domain")               // {version} 반드시 포함
public class DomainController {

    private final DomainService domainService;

    @PreAuthorize("hasRole('ADMIN')")                  // 권한 체계 ADMIN > MEMBER > GUEST, 달지 않아도 됨
    @GetMapping(value = "/{id}", version = "1+")       // version = "1+" 필수
    @Operation(
        summary = "Domain detail",                      // Swagger 요약
        description = "도메인 단건 조회 API",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "조회 성공"               // content 하드코딩 금지
            )
        }
    )
    @OperationErrorCodes({                              // 발생 가능한 에러 코드 나열
        ErrorCode.DOMAIN_NOT_FOUND,
        ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
    })
    public ResponseEntity<SingleSuccessResponseEnvelope<DomainDetailResponse>> detail(
        @Parameter(description = "도메인 ID", example = "1")
        @PathVariable Long id) {
        DomainDetailResponse response = domainService.findById(id);
        return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(response));
    }
}
```

### 규칙 요약

| # | 규칙 | 강제 |
|---|------|------|
| 1 | `@RequestMapping("/api/{version}/domain")` 사용 | 필수 |
| 2 | 모든 메서드에 `version = "1+"` 명시 | 필수 |
| 3 | `@Operation`에 `responses` 명시 (content 하드코딩 금지) | 필수 |
| 4 | `@OperationErrorCodes`로 에러 코드 문서화 | 필수 |
| 5 | `@Parameter`에 `description`, `example` 추가 | 필수 |
| 6 | 리턴 타입: `SingleSuccessResponseEnvelope<T>` 또는 `PagedSuccessResponseEnvelope<T>` | 필수 |
| 7 | 응답 생성 시 `SingleSuccessResponseEnvelope.of(data)` 사용 | 필수 |
| 8 | `ResponseEntity.ok(...)` 로 감싸서 반환 | 필수 |
| 9 | @PreAuthorize("hasRole('ADMIN')"), ADMIN, MEMBER, GEUST | 선택 |

### 금지 사항

- `@ApiResponse`에 `content = @Content(schema = @Schema(implementation = ...))` 하드코딩
  - SpringDoc이 메서드 리턴 타입에서 제네릭을 자동 해석함
  - 하드코딩하면 제네릭 타입 정보가 소실되어 DTO 스키마가 문서에 표시되지 않음

---

## 4. Service 작성 규칙

### 필수 사항

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)                          // 클래스 레벨 기본: 읽기 전용
public class DomainService {

    private final DomainRepository domainRepository;

    public DomainDetailResponse findById(Long id) {
        DomainEntity entity = domainRepository.findById(id)
            .orElseThrow(() -> new BaseException(ErrorCode.DOMAIN_NOT_FOUND));
        return DomainDetailResponse.from(entity);
    }

    @Transactional                                        // 쓰기 메서드만 명시적 선언
    public DomainDetailResponse create(DomainCreateRequest request) {
        DomainEntity entity = DomainEntity.builder()
            .name(request.getName())
            .build();
        DomainEntity saved = domainRepository.save(entity);
        return DomainDetailResponse.from(saved);
    }
}
```

### 규칙 요약

| # | 규칙 |
|---|------|
| 1 | 인터페이스 없이 바로 서비스 클래스 작성 |
| 2 | 클래스 레벨 `@Transactional(readOnly = true)`, 쓰기 메서드만 `@Transactional` |
| 3 | 비즈니스 예외는 `BaseException(ErrorCode.DOMAIN_*)`로 throw |
| 4 | 조회 실패 시 `orElseThrow(() -> new BaseException(ErrorCode.DOMAIN_NOT_FOUND))` |
| 5 | `@RequiredArgsConstructor` + `private final Repository` 주입 |

---

## 5. DTO 작성 규칙

### Request DTO

```java
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DomainCreateRequest {

    @NotBlank(message = "name은 필수입니다.")
    @Size(max = 200, message = "name은 200자 이하여야 합니다.")
    private String name;

    @Size(max = 5000)
    private String description;
}
```

### Response DTO

```java
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DomainDetailResponse {

    private final Long id;
    private final String name;
    private final String description;
    private final Instant createdAt;
    private final Instant updatedAt;

    public static DomainDetailResponse from(DomainEntity entity) {
        return DomainDetailResponse.builder()
            .id(entity.getId())
            .name(entity.getName())
            .description(entity.getDescription())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }
}
```

### 규칙 요약

| # | 규칙 |
|---|------|
| 1 | `@Getter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor` |
| 2 | Request: Bean Validation 애너테이션으로 검증 (`@NotBlank`, `@Size` 등) |
| 3 | Response: `from(Entity)` 정적 팩토리 메서드 제공 |
| 4 | 모든 필드는 `private final` |
| 5 | 파일명: `{Domain}{Action}Request` / `{Domain}{Type}Response` |

---

## 6. Entity 작성 규칙

### 필수 사항

```java
@Entity
@Table(name = "domains")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class DomainEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Builder
    public DomainEntity(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void update(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
```

### 규칙 요약

| # | 규칙 |
|---|------|
| 1 | `@Table(name = "snake_case_table")` |
| 2 | `@NoArgsConstructor(access = AccessLevel.PROTECTED)` |
| 3 | `@EntityListeners(AuditingEntityListener.class)` + `@CreatedDate`/`@LastModifiedDate` |
| 4 | `@Id` + `@GeneratedValue(strategy = GenerationType.IDENTITY)` |
| 5 | `@Builder` 생성자로 초기화 |
| 6 | **상속 강제 없음**: 필요 필드는 Entity가 직접 정의 |
| 7 | `@Column`에 `nullable`, `length`, `columnDefinition` 명시 |
| 8 | 상태 변경은 명시적 메서드로 (`update()`, `deactivate()` 등) setter 미사용 |

### 금지 사항

- `@Setter` 사용 금지 (의도를 드러내는 메서드로 대체)
- BaseEntity 강제 상속 금지 (테이블마다 요구사항이 다름)

---

## 7. ErrorCode 등록 규칙

### 접두사 체계

| 접두사 | 용도 | 예시 |
|--------|------|------|
| `GLOBAL_` | 공통 HTTP 오류 | `GLOBAL_BAD_REQUEST`, `GLOBAL_NOT_FOUND` |
| `GLOBAL_SECURITY_` | 인증/인가 | `GLOBAL_SECURITY_UNAUTHORIZED_ACCESS` |
| `SYSTEM_` | 시스템 설정 | `SYSTEM_CONFIG_NOT_FOUND` |
| `{DOMAIN}_` | 도메인 비즈니스 로직 | `{DOMAIN}_NOT_FOUND`, `{DOMAIN}_ALREADY_EXISTS` |

### 등록 방법

```java
public enum ErrorCode {
    // ... 기존 코드 ...

    // 신규 도메인 에러 코드
    DOMAIN_NOT_FOUND(HttpStatus.NOT_FOUND, "DOMAIN_001", "Domain not found."),
    DOMAIN_ALREADY_EXISTS(HttpStatus.CONFLICT, "DOMAIN_002", "Domain already exists.");
}
```

| # | 규칙 |
|---|------|
| 1 | `{DOMAIN}_` 접두사 + `Enum명`은 UPPER_SNAKE_CASE |
| 2 | code 형식: `{DOMAIN}_{3자리_일련번호}` (ex: `DOMAIN_001`) |
| 3 | message는 영어로 작성 (클라이언트에서 i18n 처리) |
| 4 | 적절한 HttpStatus 매핑 |

---

## 8. 응답 구조

### 성공 응답

```json
{
  "meta": {
    "requestId": "uuid",
    "apiVersion": "v1",
    "path": "/api/v1/example/1",
    "method": "GET",
    "timestamp": "2026-06-03T10:00:00Z",
    "durationMs": 45,
    "ipAddress": "0:0:0:0:0:0:0:1",
    "userAgent": "curl/8.7.1"
  },
  "data": { ... }
}
```

### 에러 응답

```json
{
  "meta": { ... },
  "error": {
    "code": "DOMAIN_001",
    "message": "Domain not found.",
    "details": [
      {"field": null, "message": "Domain not found."}
    ]
  }
}
```

| 사용 | 메서드 |
|------|--------|
| 단건 성공 | `SingleSuccessResponseEnvelope.of(data)` |
| 빈 응답 | `SingleSuccessResponseEnvelope.empty()` |
| 페이징 성공 | `PagedSuccessResponseEnvelope.from(page)` |
| 에러 | `GlobalExceptionHandler`가 자동 처리 |

---

## 9. Security 경로 등록 규칙

신규 도메인 추가 시 `GlobalSecurityConfig.java`에 permitAll 경로 추가:

```java
.requestMatchers("/api/*/domain/**").permitAll()     // {domain} 부분 추가
```

경로 패턴: `/api/*/{domain}/**` (버전 무관하게 허용)

---

## 10. Test 작성 규칙

### 테스트 구조

```
src/test/java/com/mjusugangsincheonghelper/
├── {domain}/
│   ├── controller/
│   │   └── {Domain}ControllerTest.java    # @WebMvcTest
│   ├── service/
│   │   └── {Domain}ServiceTest.java      # 순수 단위 테스트
│   └── {Domain}IntegrationTest.java      # @SpringBootTest
└── database/
    ├── entity/
    │   └── {Entity}Test.java
    └── repository/
        └── {Entity}RepositoryTest.java
```

### Controller Test 예시

```java
@WebMvcTest(DomainController.class)
@Import({GlobalExceptionHandler.class, GlobalMetaFilter.class, ClientInfoExtractor.class})
class DomainControllerTest {

    @MockBean
    private DomainService domainService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnDetail() throws Exception { ... }
}
```

---

## 11. 신규 도메인 추가 체크리스트

순서대로 진행:

- [ ] 1. `ErrorCode` enum에 `{DOMAIN}_` 접두사로 에러 코드 추가
- [ ] 2. `database/entity/` 에 Entity 생성
- [ ] 3. `database/repository/` 에 JpaRepository 생성
- [ ] 4. `{domain}/dto/` 에 Request DTO + Response DTO 생성
- [ ] 5. `{domain}/service/` 에 Service 클래스 생성
- [ ] 6. `{domain}/controller/` 에 Controller 생성 (모든 규칙 준수)
- [ ] 7. `GlobalSecurityConfig` 에 `permitAll` 경로 추가
- [ ] 8. Test 작성 (Controller, Service, Entity, Repository)
- [ ] 9. Swagger UI에서 문서 정상 표시 확인

---

## 12. 파일 템플릿 모음

### Controller

```
{domain}/controller/{Domain}Controller.java
```

- `@Tag(name = "Domain")`
- `@RequestMapping("/api/{version}/domain")`
- `version = "1+"`
- `@OperationErrorCodes`
- `@Parameter(description, example)`

### Service

```
{domain}/service/{Domain}Service.java
```

- `@Service`, `@RequiredArgsConstructor`
- 클래스 레벨 `@Transactional(readOnly = true)`
- 쓰기 메서드 `@Transactional`

### Request DTO

```
{domain}/dto/{Domain}{Action}Request.java
```

- `@Getter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`
- Bean Validation 애너테이션

### Response DTO

```
{domain}/dto/{Domain}{Type}Response.java
```

- `@Getter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`
- 정적 팩토리 `from(Entity)` 포함

### Entity

```
database/entity/{Entity}.java
```

- `@Entity`, `@Table`, `@Getter`
- `@NoArgsConstructor(PROTECTED)`
- `@EntityListeners(AuditingEntityListener.class)`
- `id`, `createdAt`, `updatedAt` 직접 정의 (상속 없음)
- `@Builder` + update/deactivate 등 상태 변경 메서드

### Repository

```
database/repository/{Entity}Repository.java
```

- `JpaRepository<Entity, ID>` 확장

---

## 13. BaseException 사용 규칙

### 생성자 3가지

```java
// 1. 기본: ErrorCode의 기본 메시지만 사용
throw new BaseException(ErrorCode.EXCHANGE_INTENT_NOT_FOUND);

// 2. 비즈니스 에러 + 상황별 상세 메시지
throw new BaseException(ErrorCode.EXCHANGE_INTENT_NOT_FOUND, "의도 ID 123을 찾을 수 없습니다.");

// 3. 기술적 에러 + 원본 예외 (catch 블록에서)
catch (Exception e) {
    throw new BaseException(ErrorCode.AUTH_GOOGLE_AUTH_FAILED, e);
}
```

### GlobalExceptionHandler 처리 우선순위

| 우선순위 | 조건 | details 내용 |
|----------|------|--------------|
| 1 | `detailMessage` 있음 | `[{message: detailMessage}]` |
| 2 | `cause` 있음 | `[{message: "ExceptionClass: message"}]` |
| 3 | 둘 다 없음 | `[{message: ErrorCode.message}]` |

### expose_error_details 설정

| 설정 | `details` 필드 |
|------|----------------|
| `true` (dev) | 위 우선순위에 따라 채워짐 |
| `false` (prod) | `null` (보안상 안전) |

### 사용 패턴

```java
// 비즈니스 로직: 조회 실패
public Domain findById(Long id) {
    return repository.findById(id)
            .orElseThrow(() -> new BaseException(ErrorCode.DOMAIN_NOT_FOUND));
}

// 비즈니스 로직: 상세 메시지 포함
public void validateOwnership(Long ownerId, Long requesterId) {
    if (!ownerId.equals(requesterId)) {
        throw new BaseException(ErrorCode.DOMAIN_NOT_OWNER,
                "소유자 ID: " + ownerId + ", 요청자 ID: " + requesterId);
    }
}

// 기술적 에러: 원본 예외 포함
public String callExternalApi(String param) {
    try {
        return externalClient.fetch(param);
    } catch (IOException e) {
        throw new BaseException(ErrorCode.EXTERNAL_API_FAILED, e);
    }
}
```

---

## 14. PGMQ 작업(Task) 추가 방법

### 개요

본 프로젝트는 PostgreSQL 기반 메시지 큐(PGMQ)를 사용합니다. 별도 인프라 없이 Postgres를 큐로 활용하여 트랜잭셔널 일관성을 보장합니다.

### 글로벌 인프라

| 파일 | 위치 | 역할 |
|------|------|------|
| `PgmqService.java` | `global/config/` | 큐 생성, 메시지 발송/읽기/삭제/아카이브 |
| `PgmqMessageDto.java` | `global/config/` | 메시지 DTO (msgId, readCt, message) |
| `GlobalSchedulingConfig.java` | `global/config/` | `@EnableScheduling` + 2개 스케줄러 빈 |

### 스케줄러 구성

```java
@EnableScheduling
@Configuration
public class GlobalSchedulingConfig {

    @Bean
    @Primary
    public TaskScheduler taskScheduler() {
        // 범용 스케줄러 (@Scheduled 애너테이션 사용 시)
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("global-scheduler-");
        return scheduler;
    }

    @Bean("pgmqScheduler")
    public TaskScheduler pgmqScheduler() {
        // PGMQ 워커 전용 스케줄러
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("pgmq-worker-");
        return scheduler;
    }
}
```

### PgmqService 사용법

```java
@Service
@RequiredArgsConstructor
public class MyDomainService {

    private final PgmqService pgmqService;

    // 큐 생성 (한 번만 호출, 보통 초기화 시점)
    public void initializeQueue() {
        pgmqService.createQueue("my_queue");
    }

    // 메시지 발송
    public Long enqueue(MyEvent event) {
        return pgmqService.send("my_queue", event);
    }

    // 메시지 읽기 (가시성 타임아웃 30초, 최대 10개)
    public List<PgmqMessageDto> dequeue(int visibilityTimeout, int limit) {
        return pgmqService.read("my_queue", visibilityTimeout, limit);
    }

    // 처리 완료 후 삭제
    public void complete(Long msgId) {
        pgmqService.delete("my_queue", msgId);
    }

    // 아카이브 (이력 보관용)
    public void archive(Long msgId) {
        pgmqService.archive("my_queue", msgId);
    }
}
```

### 워커 작성 패턴

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class MyQueueWorker {

    private final PgmqService pgmqService;
    private final MyDomainService myDomainService;

    @Qualifier("pgmqScheduler")
    @Autowired
    private TaskScheduler pgmqScheduler;

    private static final String QUEUE_NAME = "my_queue";
    private static final int VISIBILITY_TIMEOUT = 30; // 초
    private static final int BATCH_SIZE = 1;

    @PostConstruct
    public void start() {
        pgmqScheduler.scheduleWithFixedDelay(this::poll, Duration.ofSeconds(1));
    }

    private void poll() {
        List<PgmqMessageDto> messages = pgmqService.read(QUEUE_NAME, VISIBILITY_TIMEOUT, BATCH_SIZE);

        for (PgmqMessageDto msg : messages) {
            try {
                MyEvent event = deserialize(msg.getMessage());
                myDomainService.process(event);
                pgmqService.delete(QUEUE_NAME, msg.getMsgId());
            } catch (Exception e) {
                log.error("큐 메시지 처리 실패: msgId={}", msg.getMsgId(), e);
                // 가시성 타임아웃 후 자동 재시도됨
            }
        }
    }
}
```

### 워커 작성 규칙

| # | 규칙 | 이유 |
|---|------|------|
| 1 | 워커 메서드에 `@Transactional` 금지 | Long-running transaction 방지 |
| 2 | 메시지 읽기/삭제는 별도 트랜잭션으로 | 커넥션 점유 시간 최소화 |
| 3 | 처리 실패 시 delete/archive 호출 금지 | 가시성 타임아웃 후 자동 재시도 |
| 4 | `pgmqScheduler` 빈 사용 | PGMQ 전용 스레드 풀 분리 |
| 5 | 에러 발생 시 로그만 남김 | 원본 예외는 로그로 추적 |
| 6 | 독약 메시지(Poison Message) 방지를 위해 재시도 제한 | 무한 루프 및 리소스 고갈 방지 |
| 7 | 비즈니스 로직(서비스/디텍터 등)에서 예외 삼킴(Swallow) 금지 | 실패 시 큐 재시도 작동을 위한 필수 조건 |

### 에러 처리

```java
// PgmqService 내부에서 JacksonException 발생 시
catch (JacksonException e) {
    throw new BaseException(ErrorCode.PGMQ_SEND_FAILED, e);
}

// 워커에서 처리 실패 시
catch (Exception e) {
    log.error("큐 메시지 처리 실패: msgId={}, readCt={}", msg.getMsgId(), msg.getReadCt(), e);
    // delete/archive 호출하지 않음 → 가시성 타임아웃 후 자동 재시도
}

// 독약 메시지(Poison Message) 방지를 위해 루프 시작 시 처리
if (msg.getReadCt() > MAX_RETRY_COUNT) {
    log.error("메시지 재시도 횟수 초과로 아카이브 처리: msgId={}, readCt={}", msg.getMsgId(), msg.getReadCt());
    pgmqService.archive(QUEUE_NAME, msg.getMsgId());
    continue;
}
```

### 운영 가이드 (Bloat 방지)

`PgmqService.createQueue()` 호출 시 다음 설정이 자동 적용됩니다:

```sql
ALTER TABLE pgmq.q_<queue_name> SET (
    fillfactor = 80,                              -- HOT update를 위한 공간 확보
    autovacuum_vacuum_scale_factor = 0.01,        -- 1% 변경 시 vacuum
    autovacuum_vacuum_threshold = 100,            -- 100행 변경 시 vacuum
    autovacuum_vacuum_cost_limit = 2000           -- vacuum 가속
);
```

### 참고 문서

- [PostgreSQL SKIP LOCKED](https://www.postgresql.org/docs/current/sql-select.html#SQL-FOR-UPDATE-SHARE)
- [PGMQ GitHub](https://github.com/pgmq/pgmq)
- [Brandur Leach - Postgres Queues](https://brandur.org/blog/postgres-queues)
