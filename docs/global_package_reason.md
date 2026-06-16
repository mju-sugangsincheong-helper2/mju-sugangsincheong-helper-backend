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
| `ErrorResponseEnvelope` | 예외 발생 시 | `code` + `message`(정의된 메시지) + `details`(원본 예외 상세) |

### 성공 응답 예시

```json
{
  "meta": {
    "requestId": "uuid",
    "apiVersion": "v1",
    "path": "/api/v1/example/1",
    "method": "GET",
    "timestamp": "2026-06-03T10:00:00Z",
    "durationMs": 45,
    "ipAddress": "1.2.3.4",
    "userAgent": "..."
  },
  "data": { ... }
}
```

### 에러 응답 예시

```json
{
  "meta": { ... },
  "error": {
    "code": "GLOBAL_002",
    "message": "Validation failed.",
    "details": [
      {"field": "title",   "message": "title은 필수입니다."},
      {"field": "content", "message": "content는 5000자 이하여야 합니다."}
    ]
  }
}
```

에러 응답 구조:

| 필드 | 의미 | 제어 |
|------|------|------|
| `code` | ErrorCode enum에 정의한 코드 | 항상 노출 |
| `message` | ErrorCode enum에 정의한 메시지 | 항상 노출 |
| `details[]` | raw Java/프레임워크 오류 정보 `{field, message}` | `expose_error_details` 설정으로 on/off |

---

## 2. ErrorCode (에러 코드 체계)

### 구조

```java
@Getter
@AllArgsConstructor
public enum ErrorCode {
    // GLOBAL_: 공통 에러
    GLOBAL_BAD_REQUEST(HttpStatus.BAD_REQUEST, "GLOBAL_001", "Bad request."),
    GLOBAL_VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "GLOBAL_002", "Validation failed."),
    GLOBAL_NOT_FOUND(HttpStatus.NOT_FOUND, "GLOBAL_003", "Resource not found."),
    GLOBAL_INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "GLOBAL_004", "Internal server error."),

    // GLOBAL_SECURITY_: 인증/인가 공통
    GLOBAL_SECURITY_UNAUTHORIZED_ACCESS(HttpStatus.UNAUTHORIZED, "GLOBAL_SECURITY_001", "Unauthorized access."),
    GLOBAL_SECURITY_FORBIDDEN(HttpStatus.FORBIDDEN, "GLOBAL_SECURITY_002", "Access denied."),

    // SYSTEM_: 시스템 설정 도메인
    SYSTEM_CONFIG_NOT_FOUND(HttpStatus.NOT_FOUND, "SYSTEM_001", "System config not found."),

    // {DOMAIN}_*: 각 도메인별 에러 (도메인명 접두사 + 시퀀스 번호)
    AUTH_PRIVACY_POLICY_REQUIRED(HttpStatus.FORBIDDEN, "AUTH_001", "Privacy policy agreement is required."),
    AUTH_GOOGLE_AUTH_FAILED(HttpStatus.UNAUTHORIZED, "AUTH_002", "Google authentication failed."),
    // ...

    SINGLEGAME_GAME_NOT_FOUND(HttpStatus.NOT_FOUND, "SINGLEGAME_001", "Game not found."),
    // ...

    EXAMPLE_NOT_FOUND(HttpStatus.NOT_FOUND, "EXAMPLE_001", "Example not found."),
    EXAMPLE_ALREADY_EXISTS(HttpStatus.CONFLICT, "EXAMPLE_002", "Example already exists.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
```

### 명명 규칙

에러 코드는 **`{도메인명}_{시퀀스}`** 패턴을 따릅니다:

```
GLOBAL_001          → 공통 도메인 1번
GLOBAL_SECURITY_001 → 보안 서브도메인 1번
SYSTEM_001          → 시스템 설정 도메인 1번
AUTH_001            → 인증 도메인 1번
SINGLEGAME_001      → 싱글게임 도메인 1번
```

| 접두사 | 용도 |
|--------|------|
| `GLOBAL_` | 공통 에러 (400, 404, 500 등) |
| `GLOBAL_SECURITY_` | 인증/인가 공통 |
| `SYSTEM_` | 시스템 설정 도메인 |
| `AUTH_` | 인증 도메인 (OAuth, JWT, 게스트 병합 등) |
| `SINGLEGAME_` | 싱글게임 도메인 |
| `{DOMAIN}_` | 새 도메인 추가 시 도메인명 대문자 접두사 사용 |

### 도입 의도

**도메인별 에러 코드 네임스페이스**를 분리하여 에러 추적과 국제화(i18n)를 용이하게 합니다.

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

| 예외 타입 | code / message | details 내용 |
|-----------|----------------|-------------|
| `BaseException` | ErrorCode 정의값 | `[{field: null, message: errorCode.message}]` |
| `MethodArgumentNotValidException` | GLOBAL_002 | 프레임워크 필드 에러 (`{field, message}`) |
| `ConstraintViolationException` | GLOBAL_002 | 프레임워크 필드 에러 (`{field, message}`) |
| `HttpMessageNotReadableException` | GLOBAL_001 | `[{field: null, message: ex.getMessage()}]` |
| `MissingServletRequestParameterException` | GLOBAL_001 | `[{field: null, message: ex.getMessage()}]` |
| `MethodArgumentTypeMismatchException` | GLOBAL_001 | `[{field: null, message: ex.getMessage()}]` |
| `NoResourceFoundException` | GLOBAL_003 | `[{field: null, message: ex.getMessage()}]` |
| `Exception` (기타) | GLOBAL_004 | `[{field: null, message: ex.class + ": " + ex.message}]` |

### 도입 의도

**컨트롤러에서 try-catch 제거**로 비즈니스 로직에 집중하고, 예외 처리 로직을 한 곳에서 관리합니다.

### ErrorDetail 구조

```java
public class ErrorDetail {
    private final String code;              // ErrorCode.code
    private final String message;           // ErrorCode.message
    private final List<FieldViolation> details;  // 원본 예외 상세

    public static class FieldViolation {
        private final String field;    // 필드명 (validation only, 그 외 null)
        private final String message;  // 오류 메시지
    }
}
```

### expose_error_details 제어

`details` 필드는 `application.yml`의 `app.expose-error-details` 프로퍼티로 on/off 제어:

```yaml
app:
  expose-error-details: true   # dev
  expose-error-details: false  # prod
```

- `true`: `details`에 원본 오류 포함 (dev)
- `false`: `details` = `null` (prod)

`GlobalExceptionHandler`에서 `@Value("${app.expose-error-details:false}")`로 주입받아 동작합니다.

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

### Meta 필드 구조

| 필드 | 출처 |
|------|------|
| `requestId` | UUID.randomUUID() |
| `apiVersion` | URL path segment 추출 |
| `path` | request.getRequestURI() |
| `method` | request.getMethod() |
| `timestamp` | Instant.now() |
| `durationMs` | 처리 소요 시간 (ms) |
| `ipAddress` | X-Forwarded-For → X-Real-IP → RemoteAddr |
| `userAgent` | User-Agent 헤더 |

`client` 객체 대신 flat 필드로 설계한 의도:
- 2개 필드뿐이라 중첩 불필요
- 뎁스 하나 줄여 파싱 단순화

### HTTP 헤더 자동 설정

```
X-Request-Id: uuid
X-Api-Version: v1
```

### MDC & InstanceId

`GlobalMetaFilter`는 SLF4J MDC(Mapped Diagnostic Context)에 `requestId`와 `instanceId`를 주입하여 모든 로그 라인에 요청 추적 정보가 자동 포함되도록 합니다.

```java
MDC.put("requestId", requestId);
MDC.put("instanceId", instanceIdProvider.getInstanceId());
// ... finally
MDC.clear();
```

### Slow Request 로깅

`GlobalMetaFilter`는 요청 처리 완료 후 소요 시간을 확인하여 임계값 초과 시 로그를 남깁니다:

```java
private void logSlowRequest(String method, String path, long durationMs) {
    if (durationMs > verySlowMs) {
        log.error("Very slow request: {} {} took {}ms", method, path, durationMs);
    } else if (durationMs > slowMs) {
        log.warn("Slow request: {} {} took {}ms", method, path, durationMs);
    }
}
```

임계값은 `application.yml`의 `app.performance` 프로퍼티로 설정합니다:

```yaml
app:
  performance:
    slow-ms: 1000      # 경고 로그 임계값 (기본 1000ms)
    very-slow-ms: 5000 # 에러 로그 임계값 (기본 5000ms)
```

`InstanceIdProvider`는 앱 시작 시 한 번만 인스턴스 식별자를 결정:

1. 환경변수 `INSTANCE_ID` (운영자가 명시적 지정)
2. `InetAddress.getLocalHost().getHostName()` (hostname)
3. UUID short fallback (최후 수단)

**설계 의도**:
- 다중 인스턴스 환경에서 "어느 인스턴스의 어떤 요청"인지 로그로 즉시 식별
- 클라이언트 응답 헤더 `X-Request-Id` = 로그 `requestId`로 1:1 매칭
- MDC는 ThreadLocal 기반이므로 `GlobalMetaFilter.finally`에서 반드시 `clear()` 호출

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

### Swagger 성공 응답 DTO

컨트롤러에서 `@ApiResponse`에 `content = @Content(schema = @Schema(implementation = ...))`를 **하드코딩하지 않음**으로써, SpringDoc이 메서드 리턴 타입(`ResponseEntity<SingleSuccessResponseEnvelope<ExampleDetailResponse>>`)에서 제네릭을 자동 해석하여 실제 data 타입까지 Swagger에 표시되도록 합니다.

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
	@Order(1)
	public SecurityFilterChain publicSecurityFilterChain(HttpSecurity http) throws Exception {
		http
				.securityMatchers(matchers -> matchers.requestMatchers(PUBLIC_URLS))
				.csrf(csrf -> csrf.disable())
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

		return http.build();
	}

	@Bean
	@Order(2)
	public SecurityFilterChain securedSecurityFilterChain(HttpSecurity http) throws Exception {
		http
				.securityMatchers(matchers -> matchers.requestMatchers("/api/**"))
				.csrf(csrf -> csrf.disable())
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterAfter(consentCheckFilter, JwtAuthenticationFilter.class);

		return http.build();
	}

@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}

@Bean
public RoleHierarchy roleHierarchy() {
    return RoleHierarchyImpl.fromHierarchy("ROLE_ADMIN > ROLE_MEMBER > ROLE_GUEST");
}
```

**설계 결정**:
- **CORS**: 모든 Origin 허용, `Authorization`, `X-Access-Token`, `X-Refresh-Token`, `X-Request-Id`, `X-Api-Version`, `Set-Cookie` 헤더 노출, `maxAge=3600`, `allowCredentials=true`
- **CSRF**: REST API이므로 비활성화
- **세션**: Stateless (JWT 토큰 기반 인증)
- **경로 및 필터 분리**: 공개 API인 `PUBLIC_URLS`는 `publicSecurityFilterChain`에서 인증 없이 전면 통과시키고, 그 외의 `/api/**`는 `securedSecurityFilterChain`에서 인증을 강제합니다.
- **JWT 및 약관 동의 검증**: `JwtAuthenticationFilter`로 무상태 인증을 수행한 후, `ConsentCheckFilter`를 거쳐 일반 회원의 약관 동의 상태를 필터 단에서 즉시 차단/인가합니다.
- **PasswordEncoder**: `BCryptPasswordEncoder` 사용
- **Method Security**: `@EnableMethodSecurity`로 메서드 수준 인가 활성화
- **Role Hierarchy**: `ROLE_ADMIN > ROLE_MEMBER > ROLE_GUEST` 계층 정의

### JwtAuthenticationFilter & TokenExtractor

```
TokenExtractor (interface)
├── BearerTokenExtractor  (@Profile({"dev", "test"}))  → Authorization: Bearer 헤더 + access_token 쿠키
└── CookieTokenExtractor   (@Profile("prod"))            → access_token 쿠키만

JwtAuthenticationFilter (OncePerRequestFilter)
    → shouldNotFilter: /api/** 외에는 skip
    → TokenExtractor로 토큰 추출
    → TokenProvider.parseAccessToken() 검증
    → SecurityContextHolder에 인증 정보 설정 + request attribute에 agreed 저장
    → filterChain.doFilter() (절대 막지 않음)
```

**JwtAuthenticationFilter**:
- `UsernamePasswordAuthenticationFilter` 이전에 실행
- `shouldNotFilter()`: `/api/` 경로만 필터링 (정적 리소스, Swagger 등은 skip)
- 토큰 추출 → 파싱 → `UsernamePasswordAuthenticationToken` 생성 (principal=`memberId`, authority=`ROLE_{role}`)
- 토큰 없거나 유효하지 않으면 그냥 통과 (인증 없이 요청 진행)
- 요청 차단하지 않음 — 항상 `filterChain.doFilter()` 호출
- 동의 상태는 `request.setAttribute("privacyAgreed", claims.agreed())`로 저장

**Access Token JWT Claims**:
| 클레임 | 타입 | 설명 |
|--------|------|------|
| `sub` | String | memberId (회원 ID) |
| `role` | String | 권한 (`GUEST`, `MEMBER`, `ADMIN`) |
| `agreed` | boolean | 개인정보 동의 여부 |
| `iat` | Date | 발급 시간 |
| `exp` | Date | 만료 시간 |

**TokenExtractor 전략**:
- **dev/test**: `BearerTokenExtractor` — `Authorization: Bearer {token}` 헤더 우선, 없으면 `access_token` 쿠키 확인
- **prod**: `CookieTokenExtractor` — `access_token` 쿠키만 확인 (보안 강화)

### ConsentCheckFilter

```
Request → JwtAuthenticationFilter (인증 + agreed 저장)
       → ConsentCheckFilter (secured 체인인 경우 동작하여 약관 동의 검증)
       → DispatcherServlet
       → Controller
```

**ConsentCheckFilter** (`global/security/filter/`):
- `OncePerRequestFilter` 구현, `securedSecurityFilterChain`에서 `JwtAuthenticationFilter` 바로 뒤에 위치
- `doFilterInternal()`에서 `SecurityContextHolder`의 인증 정보 + `request.getAttribute("privacyAgreed")` 확인
- 조건: MEMBER 이상 + `agreed=false` + 면제 경로가 아님 → `ObjectMapper`를 사용하여 `SC_FORBIDDEN (403)` 상태코드와 함께 `ErrorResponseEnvelope (AUTH_001)` JSON 응답을 서블릿에 직접 작성하고 필터 체인 차단
- GUEST 및 비인가 공개 경로는 역할/필터 체인 구성에 따라 제외됨
- 면제 경로 (동의 없이 접근 가능):
  - `/auth/privacy/agree` — 동의 API 자체
  - `/auth/logout` — 로그아웃

**설계 의도**:
- **인가 및 보안 정책의 단일화**: 스프링 MVC 인터셉터(`ConsentCheckInterceptor`)를 제거하고 모든 보안 정책을 스프링 시큐리티 필터 체인(`ConsentCheckFilter`)으로 이관하여 보안 관심사를 하나의 인프라 레이어로 일치시킵니다.
- **비인가 공개 API 효율적 우회**: 공개 API(`PUBLIC_URLS`가 타는 `publicSecurityFilterChain`)에는 해당 필터가 존재하지 않으므로, 이전 로그인 세션이나 쿠키 잔재 여부와 관계없이 어떤 검증도 거치지 않고 빠르게 무조건 통과합니다.
- **예외 발생 시 서블릿 직접 직렬화**: 디스패처 서블릿 도달 전에 에러를 차단하므로, `ObjectMapper`를 주입받아 애플리케이션 공통 규격의 `ErrorResponseEnvelope` 응답 데이터를 직접 반환합니다.

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

### HibernateStatisticsConfig

```java
@Configuration
@RequiredArgsConstructor
public class HibernateStatisticsConfig {

    private final EntityManagerFactory entityManagerFactory;

    @PostConstruct
    public void enableStatistics() {
        entityManagerFactory.unwrap(SessionFactory.class)
                .getStatistics()
                .setStatisticsEnabled(true);
    }
}
```

**도입 의도**: Hibernate Statistics를 활성화하여 느린 DB 쿼리 감지를 가능하게 합니다. Slow query 임계값은 `application.yml`의 `hibernate.log_slow_query` 프로퍼티로 설정합니다.

### GlobalOpenApiConfig

```java
@Bean
public OperationCustomizer errorResponsesOperationCustomizer() {
    return new ErrorResponsesOperationCustomizer();
}

@Bean
public OpenApiCustomizer errorEnvelopeSchemaCustomizer() {
    return openApi -> {
        ModelConverters.getInstance().read(ErrorResponseEnvelope.class)
                .forEach(components::addSchemas);
        ModelConverters.getInstance().read(ErrorDetail.class)
                .forEach(components::addSchemas);
        ModelConverters.getInstance().read(ErrorDetail.FieldViolation.class)
                .forEach(components::addSchemas);
    };
}

@Bean @Profile("prod")
public OpenAPI prodOpenAPI() {
    return new OpenAPI()
            .components(new Components()
                    .addSecuritySchemes("cookieAuth", new SecurityScheme()
                            .type(SecurityScheme.Type.APIKEY)
                            .in(SecurityScheme.In.COOKIE)
                            .name("access_token")))
            .addSecurityItem(new SecurityRequirement().addList("cookieAuth"))
            // ...
}

@Bean @Profile("dev")
public OpenAPI devOpenAPI() {
    return new OpenAPI()
            .components(new Components()
                    .addSecuritySchemes("bearerAuth", new SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            // ...
}
```

**도입 의도**:
- `ErrorResponsesOperationCustomizer`: `@OperationErrorCodes` 애너테이션을 기반으로 각 API operation에 에러 응답 스키마를 자동 추가
- `ErrorResponseEnvelope` + `ErrorDetail` + `FieldViolation` 스키마: OpenAPI `components/schemas`에 세 클래스를 모두 등록하여 Swagger UI에서 `$ref` 참조가 해석되도록 함
  - `ErrorResponseEnvelope`: `{ meta, error }` 구조의 에러 응답 봉투
  - `ErrorDetail`: `error` 필드의 상세 구조 `{ code, message, details }`
  - `FieldViolation`: `details[]` 배열의 요소 `{ field, message }`
  - 세 클래스 모두 등록해야 Swagger UI에서 unresolved reference 오류가 발생하지 않음

**Profile별 Swagger 인증 방식**:
- `prod`: Cookie 기반 (`access_token` 쿠키)
- `dev`: Bearer JWT 기반 (Swagger UI에서 토큰 직접 입력)

### RedisConfig

```java
@Slf4j
@EnableCaching
@Configuration
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

    @Primary
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(24))
                .serializeValuesWith(
                    RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.json())
                );
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }

    @Bean
    public CacheErrorHandler cacheErrorHandler() {
        // Redis 장애 시 예외를 삼키고 로깅만 수행 (graceful degradation)
        return new CacheErrorHandler() { ... };
    }
}
```

**설계 결정**:
- `@EnableCaching`: Spring Cache 추상화 활성화 (`@Cacheable`, `@CacheEvict` 사용)
- `RedisCacheManager`를 명시적 `@Bean`으로 등록하여 Spring Boot auto-configuration 대신 사용 (`@Primary`로 우선 지정)
- JSON 직렬화: `RedisSerializer.json()`으로 Java 객체를 JSON으로 변환하여 Redis에 저장
- TTL 24시간: 극저빈도 쓰기 패턴이므로 긴 TTL 유지
- `CacheErrorHandler`를 `@Bean`으로 등록: Redis 장애 시에도 예외를 무시하고 로깅만 수행하여 캐시 장애가 서비스 장애로 전파되지 않도록 함
- `CachingConfigurer` 인터페이스를 구현하지 않음: `cacheManager()` 기본값 null 반환 충돌을 방지하고 `@Bean` 방식으로 모든 빈 등록

**Redis 캐시 사용처**: `SystemConfigService.getCurrentTerm()`에 `@Cacheable("system_config", key = "'current_term'")`로 고빈도 읽기 최적화, `update()`에 `@CacheEvict`로 쓰기 시 캐시 무효화.

### CaffeineConfig

**도입 의도**: Redis조차 느린 경우를 대비한 **인메모리 로컬 캐시**입니다. `current_term`이나 에러 상세 노출 여부처럼 **매우 고빈도로 읽히고 거의 변하지 않는 값**을 Redis 네트워크 왕복 없이 즉시 반환하기 위해 도입했습니다.

```
요청 → CaffeineCache (인메모리, ~μs)
     → miss 시에만 실제 로직 수행 → 결과 캐싱
     → TTL 만료 후 다음 요청에서 재조회
```

```java
@Configuration
public class CaffeineConfig {

    record TimestampedValue(Object value, int ttlSeconds) {}

    @Bean
    public Cache<String, TimestampedValue> caffeineCache() {
        return Caffeine.newBuilder()
                .maximumSize(2000)
                .expireAfter(new Expiry<String, TimestampedValue>() {
                    @Override
                    public long expireAfterCreate(String key, TimestampedValue value, long currentTime) {
                        return TimeUnit.SECONDS.toNanos(value.ttlSeconds());
                    }
                    // ... update/read 시 currentDuration 유지
                })
                .build();
    }
}
```

#### 커스텀 애너테이션

```java
@CaffeineCache(key = "someKey", ttl = 10)   // TTL 10초 (기본값 10초)
public SomeResult expensiveComputation() { ... }

@CaffeineCacheEvict(key = "someKey")
public void invalidateCache() { ... }
```

**설계 결정**:
- `maximumSize=2000`: LRU 기반 최대 2000개 엔트리 보관
- per-key TTL: `TimestampedValue` 레코드로 엔트리마다 독립 TTL 관리
- AOP(`@Aspect`) 기반: `@CaffeineCache` / `@CaffeineCacheEvict` 애너테이션으로 선언적 캐싱

### JpaAuditingConfig

```java
@EnableJpaAuditing
@Configuration
public class JpaAuditingConfig {
}
```

`database/config/`에 위치하며, `@CreatedDate` / `@LastModifiedDate` 애너테이션을 활성화하여 Entity의 `createdAt`, `updatedAt` 필드가 자동으로 관리되도록 합니다.

---

## 8. System Domain (시스템 설정)

### 개요

`system` 패키지는 애플리케이션 설정을 DB 기반으로 관리하는 도메인입니다.

### SystemConfig Entity

```sql
-- system_config 테이블
    config_key   VARCHAR(100) PRIMARY KEY,    -- 설정 키 (ex: "current_term")
    config_value TEXT NOT NULL,               -- 설정 값 (ex: "202510")
config_type  VARCHAR(20) NOT NULL,        -- STRING, JSON, BOOLEAN
description  TEXT,                        -- 설정 설명
created_at   TIMESTAMP WITH TIME ZONE,
updated_at   TIMESTAMP WITH TIME ZONE
```

### API

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/v1/system/configs` | 시스템 설정 전체 목록 조회 |
| GET | `/api/v1/system/configs/{key}` | 설정 단건 조회 |
| PUT | `/api/v1/system/configs/{key}` | 설정 수정 (실시간 반영) |

### 초기화

`@PostConstruct`에서 `SettingDefinition` enum에 정의된 키들이 없으면 기본값으로 INSERT. 앱 시작 시 자동 초기화.

| 키 | 기본값 | 타입 | 설명 |
|----|--------|------|------|
| `current_term` | `"202510"` | STRING | 현재 학기 설정 (YYYY + 학기코드: 10=1학기, 15=여름학기, 20=2학기, 25=겨울학기) |
| `notices` | `"[]"` | JSON | 공지사항 목록 |
| `announcement` | `""` | STRING | 상단 배너 공지 텍스트 |

> `expose_error_details`와 `performance_thresholds`는 더 이상 DB 기반 시스템 설정이 아닌 `application.yml` 프로퍼티로 관리됩니다. 각각 `app.expose-error-details`, `app.performance.slow-ms` / `app.performance.very-slow-ms`를 참조하세요.

### SettingDefinition (타입 안전 설정 정의)

```java
public enum SettingDefinition {
    CURRENT_TERM("current_term", ConfigType.STRING, "현재 학기 설정", "202510",
            raw -> new TermCode(raw),
            raw -> raw != null && raw.matches("^20\\d{2}(10|15|20|25)$")),
    NOTICES("notices", ConfigType.JSON, "공지사항 목록", "[]", ...),
    ANNOUNCEMENT("announcement", ConfigType.STRING, "상단 배너 공지 텍스트", "", ...);
}
```

**도입 의도**:
- 시스템 설정의 키/타입/기본값/파서/검증기를 한 곳에서 관리
- `parse()`: raw 문자열을 타입 안전 객체로 변환 (ex: `TermCode` 레코드)
- `validate()`: 설정 값 유효성 검증
- `getFrom(SystemConfigService)`: 서비스에서 직접 파싱된 값 획득

### 캐시 전략

```
Read:  Service → Redis(hit) → 반환
       Service → Redis(miss) → DB → Redis 적재 → 반환

Write: Admin PUT → DB UPDATE + @CacheEvict → Redis 무효화
       → 다음 읽기 시 DB에서 새 값 → Redis 재적재
```

설계 의도:
- 고빈도 읽기(`current_term` 조회)를 Redis로 최적화
- 극저빈도 쓰기(admin이 가끔 수정)는 DB 업데이트 + 캐시 무효화
- 모든 인스턴스가 Redis 하나를 바라보므로 다중 인스턴스에서도 즉시 동기화

---

## 9. 레이어별 책임

```
┌─────────────────────────────────────────────────────────────┐
│  Controller Layer                                           │
│  - @RestController, @RequestMapping                         │
│  - 요청 검증 (@Valid), 응답 Envelope 래핑                   │
│  - @OperationErrorCodes로 문서화                            │
│  - 성공 응답 @ApiResponse에 content 하드코딩 금지            │
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
│  - @Entity, 각 Entity가 필요 필드를 직접 정의               │
│  - 상속 강제 없음 (테이블마다 요구사항이 다름)              │
└─────────────────────────────────────────────────────────────┘
```

---

## 10. 새 도메인 추가 체크리스트

1. **ErrorCode** enum에 `{DOMAIN}_` 접두사로 에러 코드 추가
2. **database/entity/** 에 Entity 생성 (필요한 필드를 직접 정의, 상속 강제 없음)
3. **database/repository/** 에 JpaRepository 생성
4. **{domain}/controller/** 에 Controller 생성
   - `@Tag(name = "Domain", description = "도메인 API")` 추가
   - `@RequestMapping("/api/{version}/domain")` 설정
   - **모든 메서드에 `version = "1+"` 명시** (필수)
   - `@Operation`에 `responses` 명시 (content 하드코딩 금지)
   - `@Parameter`에 `description`, `example` 추가
   - `@OperationErrorCodes`로 에러 코드 문서화
5. **{domain}/dto/** 에 Request/Response DTO 생성
6. **{domain}/service/** 에 Service 클래스 생성
7. **GlobalSecurityConfig** 에 permitAll 경로 추가 (필요시)

---

## 11. 설계 원칙 요약

| 원칙 | 적용 |
|------|------|
| **관심사 분리** | global(공통) / database(DB) / system(설정) / {domain}(비즈니스) |
| **일관성** | 모든 응답은 Envelope, 모든 예외는 ErrorCode + details |
| **추적성** | requestId, durationMs, ipAddress, userAgent 자동 포함 |
| **확장성** | 새 도메인은 패턴 복사로 즉시 추가 가능 |
| **문서화** | Swagger와 코드 동기화 자동화 |
| **캐시** | 고빈도 읽기/저빈도 쓰기 패턴에 Redis 중앙 캐시 사용 |
