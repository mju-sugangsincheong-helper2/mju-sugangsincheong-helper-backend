# Global Package Design Rationale

이 문서는 `global` 패키지에 포함된 각 컴포넌트의 도입 의도와 설계 결정을 설명합니다.

---

## 1. Response Envelope (응답 봉투)

### 구조

```
ResponseEnvelope (abstract)
├── SingleSuccessResponseEnvelope<T>  → 단건 성공 응답
├── PagedSuccessResponseEnvelope<T>   → 페이징 성공 응답
└── ErrorResponseEnvelope             → 에러 응답
```

### 도입 의도

**일관된 API 응답 구조**를 강제하여 클라이언트 파싱 로직을 단순화합니다.

| Envelope | 사용 시점 | 획득 이점 |
|----------|-----------|-----------|
| `SingleSuccessResponseEnvelope<T>` | 단건 조회, 생성, 수정, 삭제 | 모든 응답에 `meta` 포함으로 추적성 확보 |
| `PagedSuccessResponseEnvelope<T>` | 목록 조회 (Page 사용 시) | `page` 메타데이터 자동 생성, 프론트엔드 페이징 UI 연동 표준화 |
| `ErrorResponseEnvelope` | 예외 발생 시 | 에러 코드 + 필드별 검증 오류를 구조화 |

### 응답 예시

```json
{
  "meta": {
    "requestId": "uuid",
    "apiVersion": "v1",
    "path": "/api/v1/example",
    "method": "GET",
    "timestamp": "2026-06-03T10:00:00Z",
    "durationMs": 45,
    "ipAddress": "1.2.3.4",
    "userAgent": "..."
  },
  "data": { ... }
}
```

---

## 2. ErrorCode (에러 코드 체계)

### 구조

```java
public enum ErrorCode {
    GLOBAL_BAD_REQUEST(HttpStatus.BAD_REQUEST, "GLOBAL_001", "Bad request."),
    GLOBAL_VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "GLOBAL_002", "Validation failed."),
    GLOBAL_NOT_FOUND(HttpStatus.NOT_FOUND, "GLOBAL_003", "Resource not found."),
    GLOBAL_INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "GLOBAL_004", "Internal server error."),

    GLOBAL_SECURITY_UNAUTHORIZED_ACCESS(HttpStatus.UNAUTHORIZED, "GLOBAL_SECURITY_001", "Unauthorized access."),
    GLOBAL_SECURITY_FORBIDDEN(HttpStatus.FORBIDDEN, "GLOBAL_SECURITY_002", "Access denied."),

    EXAMPLE_NOT_FOUND(HttpStatus.NOT_FOUND, "EXAMPLE_001", "Example not found."),
    EXAMPLE_ALREADY_EXISTS(HttpStatus.CONFLICT, "EXAMPLE_002", "Example already exists.");
}
```

### 도입 의도

**도메인별 에러 코드 네임스페이스**를 분리하여 에러 추적과 국제화(i18n)를 용이하게 합니다.

| 접두사 | 용도 |
|--------|------|
| `GLOBAL_` | 공통 에러 (400, 404, 500 등) |
| `GLOBAL_SECURITY_` | 인증/인가 관련 |
| `EXAMPLE_` | Example 도메인 비즈니스 로직 |
| `{DOMAIN}_` | 향후 추가될 도메인별 에러 |

### 이점

- 에러 코드로 정확한 발생 위치 파악 가능
- 클라이언트가 에러 코드 기반으로 분기 처리
- 로깅 시 에러 코드 검색 용이

---

## 3. BaseException (예외 계층)

### 구조

```java
public class BaseException extends RuntimeException {
    private final ErrorCode errorCode;
}
```

### 도입 의도

**비즈니스 예외를 RuntimeException으로 통일**하여 체크드 예외의 번거로움을 제거하고, `GlobalExceptionHandler`에서 일괄 처리합니다. 단 추가적인 예외는 최대한 보수적으로 도입합니다

### 사용 패턴

```java
// 서비스 레이어
throw new BaseException(ErrorCode.EXAMPLE_NOT_FOUND);

// 도메인별 커스텀 예외 확장 가능 (추가 예외는 최대한 보수적 도입 errorcode 로 사용)
public class ExampleException extends BaseException {
    public ExampleException(ErrorCode errorCode) {
        super(errorCode);
    }
}
```

---

## 4. GlobalExceptionHandler (전역 예외 처리)

### 처리 대상

| 예외 타입 | 처리 방식 |
|-----------|-----------|
| `BaseException` | ErrorCode 기반 ErrorResponseEnvelope 반환 |
| `MethodArgumentNotValidException` | @Valid 검증 실패 → 필드별 오류 포함 |
| `ConstraintViolationException` | @Validated 검증 실패 |
| `HttpMessageNotReadableException` | JSON 파싱 실패 |
| `Exception` (기타) | 500 Internal Server Error + 로깅 |

### 도입 의도

**컨트롤러에서 try-catch 제거**로 비즈니스 로직에 집중하고, 예외 처리 로직을 한 곳에서 관리합니다.

---

## 5. ResponseMeta & GlobalMetaFilter (요청 추적)

### 구조

```
GlobalMetaFilter (OncePerRequestFilter)
    ↓ ThreadLocal 저장
CustomResponseMetaContextHolder
    ↓ 참조
MetaGenerator.generate()
    ↓ 포함
ResponseEnvelope.meta
```

### 도입 의도

**모든 API 응답에 요청 컨텍스트를 자동 포함**하여:

1. **분산 추적**: `requestId`로 로그 correlation
2. **디버깅**: `durationMs`로 느린 API 식별
3. **클라이언트 정보**: `ipAddress`, `userAgent`로 보안 감사
4. **API 버전**: `apiVersion`으로 버전별 응답 구분

### HTTP 헤더 자동 설정

```
X-Request-Id: uuid
X-Api-Version: v1
```

---

## 6. @OperationErrorCodes (Swagger 문서화)

### 구조

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationErrorCodes {
    ErrorCode[] value();
}
```

### 도입 의도

**API 문서와 실제 에러 코드의 동기화**를 자동화합니다.

```java
@GetMapping("/{id}")
@OperationErrorCodes({
    ErrorCode.EXAMPLE_NOT_FOUND,
    ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
})
public ResponseEntity<...> detail(@PathVariable Long id) { ... }
```

Swagger UI에 해당 에러 코드가 자동으로 문서화됩니다.

---

## 7. Config 클래스들

### GlobalWebMvcConfig

```java
@Override
public void configureApiVersioning(ApiVersionConfigurer configurer) {
    configurer.usePathSegment(1);  // /api/{version}/... 에서 segment index 1 추출
}

@Override
public void addViewControllers(ViewControllerRegistry registry) {
    registry.addRedirectViewController("/swagger-ui", "/swagger-ui/index.html");
}
```

**도입 의도**: Spring Framework 7의 네이티브 API 버전 기능을 활성화하여 URL 경로 기반 버전 라우팅을 지원합니다.

#### API 버전 v 접두사 자동 처리

`usePathSegment(1)`로 추출된 경로 세그먼트는 기본 `SemanticApiVersionParser`가 파싱합니다. 이 파서의 `skipNonDigits()` 메서드가 `v1` 같은 선행 비숫자 문자를 자동으로 건너뛰므로, `version = "1+"`로 선언한 상태에서도 `/api/v1/example` 요청이 정상 매칭됩니다.

```
요청 URL: /api/v1/example/hello
    → PathApiVersionResolver: segment[1] = "v1"
    → SemanticApiVersionParser.skipNonDigits("v1") = "1"
    → version = "1+" 매칭 성공
```

코드에서는 `version = "1"` 또는 `version = "1+"` 같이 숫자만 작성하고, 실제 URL에는 `v1` 같이 `v` 접두사를 붙여서 사용하면 됩니다. 별도 설정은 필요하지 않습니다.

#### Swagger UI 리다이렉트

`addRedirectViewController("/swagger-ui", "/swagger-ui/index.html")`로 `/swagger-ui` 접근 시 Swagger UI 페이지로 리다이렉트합니다. SpringDoc이 등록하는 기본 리다이렉트가 Spring Boot 4.x에서 정상 동작하지 않을 수 있어 명시적으로 추가했습니다.

#### API 버전 명시 규칙

모든 컨트롤러 메서드에 `version` 속성을 명시해야 합니다:

```java
@GetMapping(value = "/{id}", version = "1+")
public ResponseEntity<...> detail(@PathVariable Long id) { ... }

@PostMapping(version = "1+")
public ResponseEntity<...> create(@RequestBody Request request) { ... }
```

| 버전 문법 | 의미 |
|-----------|------|
| `"1"` | 버전 1만 매칭 |
| `"1+"` | 버전 1 이상 모두 매칭 (권장) |
| `"2"` | 버전 2만 매칭 (v2 추가 시) |

**이점**:
- 각 메서드가 어떤 버전을 지원하는지 코드에서 즉시 파악
- v2 추가 시 기존 메서드는 `"1"`, 새 메서드는 `"2"` 또는 `"1+"`로 명확히 구분
- 버전별 동작 변경 시 같은 메서드명으로 오버로딩 가능

### GlobalSecurityConfig

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/v1/auth/**").permitAll()
            .requestMatchers("/api/*/example/**").permitAll()
            .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
            .requestMatchers("/actuator/**").permitAll()
            .anyRequest().authenticated()
        );
    return http.build();
}
```

**설계 결정**:
- **CORS**: 모든 Origin 허용, `Authorization`, `X-Request-Id`, `X-Api-Version` 헤더 노출
- **CSRF**: REST API이므로 비활성화
- **세션**: Stateless (JWT/OAuth 토큰 기반 인증 예정)
- **경로**: Swagger UI(`/swagger-ui/**`), API docs(`/v3/api-docs/**`), Actuator(`/actuator/**`), 예제 API(`/api/*/example/**`)는 인증 없이 허용
- **OAuth2 Client**: `spring-boot-starter-security-oauth2-client` 의존성 포함 (향후 소셜 로그인 지원)

### GlobalAsyncConfig

```java
@EnableAsync
@Configuration
public class GlobalAsyncConfig implements AsyncConfigurer {
    @Bean(name = "globalTaskExecutor")
    public Executor globalTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("global-async-");
        executor.initialize();
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return globalTaskExecutor();
    }
}
```

**주의**: `globalTaskExecutor`는 `CustomResponseMetaContextHolder`의 ThreadLocal 컨텍스트를 전파하지 않으므로, `@Async` 메서드 내에서는 `MetaGenerator.generate()` 호출 시 null-safe fallback 처리(임의 UUID, durationMs=0)로 대응합니다.

### GlobalOpenApiConfig

```java
@Bean
public OperationCustomizer errorResponsesOperationCustomizer() {
    return new ErrorResponsesOperationCustomizer();
}

@Bean
public OpenApiCustomizer errorEnvelopeSchemaCustomizer() {
    return openApi -> {
        Components components = openApi.getComponents();
        if (components == null) {
            components = new Components();
            openApi.setComponents(components);
        }

        Map<String, Schema> errorEnvelopeSchemas = ModelConverters.getInstance().read(ErrorResponseEnvelope.class);
        errorEnvelopeSchemas.forEach(components::addSchemas);

        Map<String, Schema> errorDetailSchemas = ModelConverters.getInstance().read(ErrorDetail.class);
        errorDetailSchemas.forEach(components::addSchemas);
    };
}
```

**도입 의도**:
- `ErrorResponsesOperationCustomizer`: `@OperationErrorCodes` 애너테이션을 기반으로 각 API operation에 에러 응답 스키마를 자동 추가
- `ErrorResponseEnvelope` + `ErrorDetail` 스키마: OpenAPI `components/schemas`에 두 클래스를 모두 등록하여 Swagger UI에서 `$ref` 참조가 해석되도록 함
  - `ErrorResponseEnvelope`: `{ meta, error }` 구조의 에러 응답 봉투
  - `ErrorDetail`: `error` 필드의 상세 구조 `{ code, message, fields }`
  - 둘 다 등록해야 Swagger UI에서 `ErrorDetail` unresolved reference 오류가 발생하지 않음

### RedisConfig

```java
@Configuration
@ConditionalOnBean(RedisConnectionFactory.class)
public class RedisConfig {
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(RedisSerializer.json());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(RedisSerializer.json());
        return template;
    }
}
```

**설계 결정**:
- `@ConditionalOnBean(RedisConnectionFactory.class)`: Redis가 연결되지 않은 환경(예: 테스트, 로컬 미실행)에서도 애플리케이션이 시작되도록 방어
- JSON 직렬화: `RedisSerializer.json()`으로 Java 객체를 JSON으로 변환하여 Redis에 저장
- Key는 문자열, Value/HashValue는 JSON으로 직렬화

---

## 8. 레이어별 책임

```
┌─────────────────────────────────────────────────────────────┐
│  Controller Layer                                           │
│  - @RestController, @RequestMapping                         │
│  - 요청 검증 (@Valid), 응답 Envelope 래핑                   │
│  - @OperationErrorCodes로 문서화                            │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│  Service Layer                                              │
│  - 비즈니스 로직                                            │
│  - @Transactional 트랜잭션 관리                             │
│  - BaseException으로 비즈니스 예외 발생                      │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│  Repository Layer (database)                                │
│  - JpaRepository<Entity, ID>                                │
│  - Spring Data JPA 자동 쿼리 생성                           │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│  Entity Layer (database)                                    │
│  - @Entity, @MappedSuperclass                               │
│  - BaseEntity: id, createdAt, updatedAt 자동 관리           │
└─────────────────────────────────────────────────────────────┘
```

---

## 9. 새 도메인 추가 체크리스트

1. **ErrorCode** enum에 `{DOMAIN}_` 접두사로 에러 코드 추가
2. **database/entity/** 에 Entity 생성 (`BaseEntity` 상속)
3. **database/repository/** 에 JpaRepository 생성
4. **{domain}/controller/** 에 Controller 생성
   - `@Tag(name = "Domain", description = "도메인 API")` 추가
   - `@RequestMapping("/api/{version}/domain")` 설정
   - **모든 메서드에 `version = "1+"` 명시** (필수)
   - `@Operation`에 `responses` 명시
   - `@Parameter`에 `description`, `example` 추가
   - `@OperationErrorCodes`로 에러 코드 문서화
5. **{domain}/dto/** 에 Request/Response DTO 생성
6. **{domain}/service/** 에 Service 인터페이스 + 구현체 생성
7. **GlobalSecurityConfig** 에 permitAll 경로 추가 (필요시)

---

## 10. 설계 원칙 요약

| 원칙 | 적용 |
|------|------|
| **관심사 분리** | global(공통) / database(DB) / {domain}(비즈니스) |
| **일관성** | 모든 응답은 Envelope, 모든 예외는 ErrorCode |
| **추적성** | requestId, durationMs, ipAddress, userAgent 자동 포함 |
| **확장성** | 새 도메인은 패턴 복사로 즉시 추가 가능 |
| **문서화** | Swagger와 코드 동기화 자동화 |
