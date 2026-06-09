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
| Redis 키 | snake_case, `::` 구분자 | `system_config::expose_error_details` |
| Cache name | snake_case | `system_config` |

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
