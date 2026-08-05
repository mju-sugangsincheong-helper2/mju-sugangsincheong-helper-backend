# 공개 API (Public API) 아키텍처 및 보안 체인 설계 가이드

이 문서는 시스템 내 인증 없이 접근 가능한 **공개 API(Public API)**를 관리함에 있어 **보안성(Security)**, **개발자 경험(DX)**, **유지보수성(Maintainability)** 사이의 균형점(Sweet Spot)을 정의하고 설계 방향을 제시합니다.

---

## 1. 핵심 문제 (Core Conflict)

Spring Security 구조상 **Filter Chain (URL 단위)**과 **Method Security (어노테이션 단위)**는 실행 시점과 역할이 완전히 다릅니다.

```
[요청 Request] 
      │
      ▼
┌──────────────────────────────────────────────────────────┐
│ 1. Filter Chain (GlobalSecurityConfig)                  │
│    - securityMatchers 및 authorizeHttpRequests 검사    │
│    - securedSecurityFilterChain: anyRequest().authenticated() │ ◄── [익명 요청 401/403 차단]
└──────────────────────────┬───────────────────────────────┘
                           │ (통과 시에만)
                           ▼
┌──────────────────────────────────────────────────────────┐
│ 2. DispatcherServlet & Controller                       │
│    - @PreAuthorize("permitAll()") 수신                   │
└──────────────────────────────────────────────────────────┘
```

- **문제점**: `securedSecurityFilterChain`이 `anyRequest().authenticated()`로 막혀있는 한, 컨트롤러 메서드에 `@PreAuthorize("permitAll()")`을 붙여도 **필터 체인 단계에서 401/403으로 먼저 차단**됩니다.
- **결과**: 공개 API를 등록하려면 반드시 **필터 체인 레벨의 `requestMatchers`**와 **컨트롤러 레벨의 설정**이 일치해야 합니다.

---

## 2. 3가지 설계 트레이드오프 비교

| 평가 항목 | 방안 1. 수동 중앙집중 관리 (현재 방식) | 방안 2. 어노테이션 기반 자동 스캐닝 (`@PublicEndpoint`) | 방안 3. 경로 네임스페이스 분리 (`/public/`) |
| :--- | :--- | :--- | :--- |
| **설정 방식** | `GlobalSecurityConfig`에 HTTP 메서드+경로 수동 등록 | 컨트롤러 메서드에 `@PublicEndpoint`만 부여하면 자동 인식 | `/api/*/public/**` 패턴으로 1줄 일괄 허용 |
| **GET / POST / DELETE 지원** | HTTP 메서드별 배열 (`PUBLIC_GET_URLS` 등) 각각 관리 | 어노테이션의 `@GetMapping`, `@DeleteMapping` 자동 감지 | 경로 기반이므로 모든 메서드 자동 적용 |
| **개발자 경험 (DX)** | 2곳 수정 필요 (Config + Controller) | **최상** (컨트롤러 메서드 1곳만 수정) | **상** (경로 규칙만 준수) |
| **보안 가시성 (Auditability)** | **최상** (`GlobalSecurityConfig` 한 파일로 전수 파악) | 중 (프로젝트 전체 검색 필요) | **상** (경로로 즉시 식별) |
| **실수 방지 (Fail-Closed)** | **안전** (실수로 어노테이션 붙여도 필터가 보호) | 위험 (실수로 어노테이션 붙이면 바로 외부에 뚫림) | **안전** (경로가 곧 정책) |
| **구조적 단순성 (Zero Magic)** | **단순함** (스캐닝/리플렉션 없음) | 복잡함 (시작 시점 핸들러 매핑 스캔 & 타이밍 처리) | **최고 단순** |
| **프론트 API 계약 변경** | 변경 없음 | 변경 없음 | **경로 변경 필요** (`/public/` 추가) |

---

## 3. 현 시점의 아키텍처적 균형점 (The Sweet Spot)

### Situation A: 프론트엔드 API 경로 변경이 가능한 경우 ➔ **[최고의 균형점: 방안 3]**
- API 경로에 `/public/` 네임스페이스를 부여합니다. (예: `GET /api/1/public/course/sections`)
- **이유**: Magic(스캐닝 코드)이 0줄이며, 설정도 1줄이고, 프론트/백엔드 개발자 모두 경로만 보고 100% 공개 여부를 직관적으로 알 수 있습니다.

### Situation B: 프론트엔드 API 경로가 고정된 경우 (현재 상태)
공개 API의 개수와 서비스 성격에 따라 균형점이 달라집니다.

#### 1) 공개 API가 몇 개 없고 보안이 최우선인 경우 ➔ **[방안 1: 수동 중앙집중 관리]**
- **이유**: 공개 API는 시스템에서 **보안 사고 위험이 가장 높은 접점**입니다. 
- 새로운 공개 API를 만드는 과정의 "불편함(2곳 수정)"은 오히려 **"의도치 않은 보안 구멍(Fail-Open)을 막아주는 안전장치"**로 작동합니다.
- `PUBLIC_GET_URLS`, `PUBLIC_DELETE_URLS` 등으로 HTTP 메서드를 명확히 분리하여 수동 선언하는 것이 가장 가시성이 높습니다.

#### 2) 공개 API가 자주 추가되고 DX(생산성)가 중요한 경우 ➔ **[방안 2: `@PublicEndpoint` 자동 스캐닝]**
- 단순 `@PreAuthorize("permitAll()")`을 스캔하는 것은 인가(Authorization)와 라우팅(Routing)의 개념을 혼용하므로, **전용 메타 어노테이션 `@PublicEndpoint`**를 도입합니다.
- Spring MVC의 `RequestMappingHandlerMapping`을 애플리케이션 시작 시점에 스캔하여, `@PublicEndpoint`가 붙은 메서드의 HTTP 메서드(GET, POST, DELETE 등)와 URL 패턴을 추출해 `RequestMatcher`를 동적 생성합니다.

---

## 4. 방안 2 (어노테이션 기반 자동 스캐닝) 구체적 구현 구상

만약 방안 2를 도입하기로 결정할 경우의 기술적 구현 구조입니다.

### 4.1 전용 어노테이션 정의
```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("permitAll()") // Method Security 레벨 허용
public @interface PublicEndpoint {
    String reason() default ""; // 공개 이유 문서화
}
```

### 4.2 스캐너 및 RequestMatcher 동적 생성 컴포넌트
```java
@Component
public class PublicEndpointRegistry implements SmartInitializingSingleton {
    private final RequestMappingHandlerMapping handlerMapping;
    private final List<RequestMatcher> matchers = new CopyOnWriteArrayList<>();

    @Override
    public void afterSingletonsInstantiated() {
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = handlerMapping.getHandlerMethods();
        for (var entry : handlerMethods.entrySet()) {
            HandlerMethod handlerMethod = entry.getValue();
            if (handlerMethod.hasMethodAnnotation(PublicEndpoint.class)) {
                RequestMappingInfo info = entry.getKey();
                Set<String> patterns = info.getPatternValues();
                Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
                
                for (String pattern : patterns) {
                    if (methods.isEmpty()) {
                        matchers.add(new PathPatternRequestMatcher(pattern));
                    } else {
                        for (RequestMethod m : methods) {
                            matchers.add(new PathPatternRequestMatcher(HttpMethod.valueOf(m.name()), pattern));
                        }
                    }
                }
            }
        }
    }

    public RequestMatcher getRequestMatcher() {
        return request -> matchers.stream().anyMatch(m -> m.matches(request));
    }
}
```

### 4.3 GlobalSecurityConfig 연동
```java
@Bean
@Order(1)
public SecurityFilterChain publicSecurityFilterChain(HttpSecurity http, PublicEndpointRegistry registry) throws Exception {
    http
        .securityMatchers(matchers -> matchers
            .requestMatchers(PUBLIC_URLS) // Swagger, Healthcheck 등 기존 정적 공개 경로
            .requestMatcher(registry.getRequestMatcher())) // 어노테이션 기반 동적 공개 경로
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    return http.build();
}
```

---

## 5. 최종 권장사항 (Summary)

1. **단기적/현재 상태**: 공개 API가 단 3개(`findSections`, `findDepartments`, `getRecentIntents`)뿐이므로, **방안 1(수동 중앙집중 관리)**을 유지하는 것이 프로젝트 복잡도를 올리지 않고 가장 안전합니다.
2. **장기적/확장 시**: 공개 API가 10개 이상으로 늘어나고 DELETE/POST 등 다양한 메서드의 공개 API가 빈번히 추가된다면 **방안 2(`@PublicEndpoint` 자동 스캐닝)** 패턴 도입을 검토합니다.
3. **가장 우아한 해결책**: 가능만 하다면 프론트엔드와 협의하여 `/api/1/public/...` **경로 네임스페이스 방식(방안 3)**으로 전환하는 것이 아키텍처적으로 가장 깔끔한 균형점입니다.
