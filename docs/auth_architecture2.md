# Auth & Security Architecture v2

본 문서는 인증(Auth) 및 보안(Security) 시스템의 전체 아키텍처를 상세히 설명합니다.

---

## 목차

1. [핵심 설계 원칙](#1-핵심-설계-원칙)
2. [Security 레이어 (Policy)](#2-security-레이어-policy)
3. [Auth 레이어 (Mechanism)](#3-auth-레이어-mechanism)
4. [4단계 시퀀스 흐름](#4-4단계-시퀀스-흐름)
5. [역할 계층 (Role Hierarchy)](#5-역할-계층-role-hierarchy)
6. [개인정보 동의 체크](#6-개인정보-동의-체크)
7. [JWT 토큰 구조](#7-jwt-토큰-구조)
8. [DB 테이블 구조](#8-db-테이블-구조)
9. [인증 흐름별 호출 경로](#9-인증-흐름별-호출-경로)
10. [토큰 전달 전략](#10-토큰-전달-전략)
11. [핵심 타입](#11-핵심-타입)
12. [ErrorCode](#12-errorcode)

---

## 1. 핵심 설계 원칙

### 1.1 Policy vs Mechanism 분리

| 구분 | GlobalSecurityConfig (Policy) | Auth 메커니즘 (auth/) |
|------|------------------------------|----------------------|
| **관점** | 외부 (요청, URL, 필터 체인) | 내부 (인증 로직, 부품) |
| **핵심 키워드** | SecurityFilterChain, CORS, CSRF | AuthenticationManager, TokenProvider |
| **비유** | 성벽의 출입문 통제 | 신분증 발급소와 검사기 |
| **질문** | "Admin 페이지는 누가 들어올 수 있는가?" | "JWT는 어떻게 검증하는가?" |

### 1.2 4단계 책임 분리

Auth 시스템은 시퀀스 기준으로 네 가지 책임으로 분리됩니다.

| 단계 | 레이어 | 책임 | 핵심 질문 |
|------|--------|------|----------|
| 1 | **OAuth** (외부 인증 연동) | Google 등 외부 제공자와의 OAuth 흐름 관리 | "외부에서 어떻게 인증 정보를 가져올까?" |
| 2 | **Authentication** (본인인증) | 사용자 신원 확인 및 identity 확립, JWT 생성/검증 | "이 사람이 누구인가?" |
| 3 | **Session** (통행권) | ATK/RTK 발급, 갱신, 회수 및 디바이스 세션 관리 | "통행권을 주고/갱신하고/회수할까?" |
| 4 | **Authorization** (권한) | 역할 기반 접근 제어 + 개인정보 동의 체크 | "이 통행권으로 무엇을 할 수 있는가?" |

---

## 2. Security 레이어 (Policy)

### 2.1 GlobalSecurityConfig

**파일 위치**: `global/config/GlobalSecurityConfig.java`

**책임**:
- SecurityFilterChain 정의
- CORS/CSRF 설정
- URL 기반 권한 규칙
- 필터 순서 정의
- RoleHierarchy 정의

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // @PreAuthorize 활성화
@RequiredArgsConstructor
public class GlobalSecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final PrivacyConsentFilter privacyConsentFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/*/auth/guest").permitAll()
                .requestMatchers("/api/*/auth/refresh").permitAll()
                .requestMatchers("/api/*/auth/login/google/merge").permitAll()
                .requestMatchers("/api/*/auth/oauth/start").permitAll()
                .requestMatchers("/api/*/auth/token").permitAll()
                .requestMatchers("/api/*/auth/config/google").permitAll()
                .requestMatchers("/api/*/auth/privacy/**").permitAll()
                .requestMatchers("/api/*/example/**").permitAll()
                .requestMatchers("/api/*/system/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/*.html").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(privacyConsentFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("ROLE_ADMIN > ROLE_MEMBER > ROLE_GUEST");
    }
}
```

### 2.2 필터 체인 순서

```
요청 → [JwtAuthenticationFilter] → DispatcherServlet → [@PreAuthorize] → Controller
         (인증)                                        (역할 체크)
```

| 필터 | 위치 | 책임 |
|------|------|------|
| `JwtAuthenticationFilter` | `UsernamePasswordAuthenticationFilter` 이전 | 토큰 검증 → SecurityContext 설정 |

**참고**: 개인정보 동의는 필터에서 체크하지 않습니다. "계정 존재 = 동의 완료" 원칙에 따라, 계정이 있는 사용자는 이미 동의를 받은 것으로 간주합니다.

### 2.3 URL 권한 규칙

| URL 패턴 | 권한 | 설명 |
|----------|------|------|
| `/api/*/auth/guest` | permitAll | 게스트 생성 (누구나) |
| `/api/*/auth/refresh` | permitAll | 토큰 재발급 (누구나) |
| `/api/*/auth/login/google/merge` | permitAll | 게스트→멤버 병합 |
| `/api/*/auth/oauth/start` | permitAll | OAuth 시작 |
| `/api/*/auth/token` | permitAll | OAuth 토큰 교환 |
| `/api/*/auth/config/google` | permitAll | Google OAuth 설정 조회 |
| `/api/*/auth/privacy/**` | permitAll | 개인정보 동의 관련 |
| `/api/*/example/**` | permitAll | 예제 API |
| `/api/*/system/**` | permitAll | 시스템 설정 API |
| `/swagger-ui/**`, `/v3/api-docs/**` | permitAll | Swagger UI |
| `/actuator/**` | permitAll | Actuator 엔드포인트 |
| `/*.html` | permitAll | 정적 HTML |
| 그 외 | authenticated | 인증 필요 |

---

## 3. Auth 레이어 (Mechanism)

### 3.1 패키지 구조

```
auth/
├── oauth/                                    # 1. OAuth 인증 (외부 신원 확인)
│   ├── GoogleAuthProvider.java               #    code → ID Token 검증 → 회원 조회/생성
│   ├── OAuthStateService.java                #    state 생성/검증 (Redis 5분 TTL)
│   └── dto/
│       ├── OAuthConfigResponse.java          #    GET /auth/config/google 응답
│       ├── OAuthStartResponse.java           #    POST /auth/oauth/start 응답
│       ├── OAuthTokenRequest.java            #    POST /auth/token 요청
│       └── OAuthTokenResponse.java           #    POST /auth/token 응답 (status: SUCCESS)
│
├── authentication/                           # 2. 본인인증 (신원 확인 + JWT)
│   ├── identity/
│   │   └── AuthenticatedIdentity.java        #    인증 결과 VO (항상 memberId 보유)
│   ├── guest/
│   │   └── GuestAuthenticationProvider.java  #    게스트 신원 생성
│   ├── merge/
│   │   ├── MergeTicketService.java           #    병합 JWT 발급/소비
│   │   └── MergeService.java                 #    게스트 → 멤버 데이터 이관
│   └── token/
│       ├── TokenProvider.java                #    JWT 생성/파싱 (ATK, RTK, MergeTicket)
│       ├── TokenExtractor.java               #    요청에서 토큰 추출 인터페이스
│       ├── BearerTokenExtractor.java         #    Authorization: Bearer 우선, access_token 쿠키 fallback (dev/test)
│       ├── CookieTokenExtractor.java         #    access_token 쿠키 (prod)
│       └── JwtAuthenticationFilter.java      #    ATK 검증 → SecurityContext
│
├── session/                                  # 3. 통행권 관리 (JWT 저장/갱신/회수)
│   ├── SessionService.java                   #    세션 오케스트레이션
│   ├── SessionResult.java                    #    세션 생성 결과 VO
│   ├── delivery/
│   │   ├── TokenDeliveryStrategy.java        #    토큰 전달 전략 인터페이스
│   │   ├── CookieTokenDelivery.java          #    HttpOnly Secure 쿠키 (prod)
│   │   └── HeaderTokenDelivery.java          #    HttpOnly 쿠키 + 응답 헤더 (dev/test)
│   └── device/
│       └── DeviceSessionService.java         #    디바이스 세션 CRUD
│
├── controller/
│   ├── AuthController.java                   #    guest, refresh, logout, merge
│   ├── OAuthController.java                  #    config/google, oauth/start, token
│   └── MemberController.java                 #    /members/me
│
├── dto/                                      # 공통 DTO
│   ├── DeviceInfo.java
│   ├── GuestCreateRequest.java
│   ├── GuestResponse.java
│   ├── LogoutRequest.java
│   ├── MemberMeResponse.java
│   ├── MergeRequest.java
│   ├── MergeResponse.java
│   └── RefreshResponse.java
│
└── service/
    └── MemberService.java                    #    회원 정보 조회
```

---

## 4. 4단계 시퀀스 흐름

### 4.1 단계별 흐름 요약

```
[1] OAuth 인증 (oauth/)
    └→ code → Google 토큰 교환 → ID Token JWKS 검증
    └→ 기존 회원: 회원 정보 갱신 후 AuthenticatedIdentity(memberId) 반환
    └→ 신규 회원: Member + MemberAuth 생성 후 AuthenticatedIdentity(memberId) 반환

[2] JWT 발급/검증 (authentication/token/)
    └→ TokenProvider: JWT 생성 (ATK/RTK, MergeTicket)
    └→ JwtAuthenticationFilter: 요청 시 ATK 검증 → SecurityContext

[3] 통행권 관리 (session/)
    └→ AuthenticatedIdentity를 받아서
    └→ TokenProvider로 JWT 생성
    └→ DB에 저장 (device session)
    └→ 토큰 전달 (cookie/body)

[4] 권한 확인 (authorization/)
    └→ @PreAuthorize + RoleHierarchy: 역할 기반 접근 제어
         └→ ADMIN > MEMBER > GUEST 계층 구조
```

### 4.2 OAuth 토큰 교환 흐름

```
POST /auth/token {code, state}
   └→ state 검증
   └→ Google token endpoint에 code 전달
   └→ Google ID Token JWKS 서명 검증
   └→ 기존 회원이면 정보 갱신
   └→ 신규 회원이면 Member + MemberAuth 생성
   └→ 응답: { status: "SUCCESS", memberId, role, accessToken, refreshToken }
```

**핵심 원칙**: OAuth 토큰 교환과 개인정보 동의 응답은 통합하지 않습니다.
- `/auth/token`은 Google 인증과 세션 발급만 담당합니다.
- 개인정보 동의는 별도 흐름에서 처리합니다.

---

## 5. 역할 계층 (Role Hierarchy)

### 5.1 역할 정의

| 역할 | 설명 | 생성 시점 |
|------|------|----------|
| **GUEST** | 게스트 생성 버튼으로 계정 생성 | `GuestAuthenticationProvider.authenticate()` |
| **MEMBER** | 명지대 Google 계정으로 인증을 완료한 재학생 | `GoogleAuthProvider.findOrCreateMember()` |
| **ADMIN** | 시스템 관리 권한을 부여받은 운영자 | 수동 부여 |

### 5.2 계층 구조

```
ROLE_ADMIN > ROLE_MEMBER > ROLE_GUEST
```

**정의 위치**: `GlobalSecurityConfig.roleHierarchy()`

```java
@Bean
public RoleHierarchy roleHierarchy() {
    return RoleHierarchyImpl.fromHierarchy("ROLE_ADMIN > ROLE_MEMBER > ROLE_GUEST");
}
```

### 5.3 @PreAuthorize 사용

`@EnableMethodSecurity`가 활성화되어 있으므로 컨트롤러 메서드에서 역할 기반 접근 제어가 가능합니다.

```java
@RestController
@RequestMapping("/api/{version}/members")
public class MemberController {

    @GetMapping("/me")
    public MemberMeResponse getMe() { ... }  // 인증만 필요 (Guest+)

    @PreAuthorize("hasRole('MEMBER')")
    @GetMapping("/courses")
    public List<CourseResponse> getCourses() { ... }  // MEMBER+ 필요 (MEMBER, ADMIN)

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/system/config")
    public void updateConfig() { ... }  // ADMIN만
}
```

### 5.4 역할 확인 흐름

```
1. JwtAuthenticationFilter
   └→ 토큰에서 role claim 읽기
   └→ "ROLE_" + role 로 authority 생성
   └→ SecurityContext에 설정

2. @PreAuthorize("hasRole('MEMBER')")
   └→ RoleHierarchy가 적용됨
   └→ ROLE_ADMIN도 ROLE_MEMBER를 포함
   └→ ROLE_GUEST는 차단됨
```

**중요**: 역할은 JWT에서 읽으며, 매 요청마다 DB를 조회하지 않습니다.

---

## 6. 개인정보 동의

### 6.1 핵심 원칙: "계정 존재 = 동의 완료"

개인정보 동의는 **가입 시점에 한 번만** 받으며, 필터에서 매 요청마다 체크하지 않습니다.

| 원칙 | 설명 |
|------|------|
| **계정 존재 = 동의 완료** | 계정이 있는 사용자는 이미 동의를 받은 것으로 간주 |
| **JWT 무상태성 유지** | 매 요청마다 DB를 조회하지 않음 |
| **감사 로그만 저장** | `member_agreements` 테이블은 동의 이력(언제 동의했는지)만 보관 |

### 6.2 OAuth 토큰 교환 흐름

```
[1] POST /auth/token {code, state}
    │
    ├→ state 검증
    ├→ Google token endpoint에 code 전달
    ├→ Google ID Token JWKS 검증
    ├→ member_auth 테이블에서 기존 회원 확인
    │
    ├→ [기존 회원] → Member 정보 갱신
    │
    └→ [신규 회원] → Member + MemberAuth 생성
    │
    └→ SessionService.createSession()
        └→ 응답: { status: "SUCCESS", memberId, role, accessToken, refreshToken }
```

### 6.3 member_agreements 테이블 (감사 로그용)

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| `member_id` | BIGINT | PK | member.id와 1:1 공유 PK |
| `status` | BOOLEAN | NOT NULL | 항상 true (동의 완료만 기록) |
| `agreed_at` | TIMESTAMP | NOT NULL | 동의 시각 |

**참고**: 이 테이블은 필터에서 조회하지 않습니다. 동의 이력을 감사 목적으로만 보관합니다.

### 6.5 시나리오별 처리

| 시나리오 | 처리 |
|----------|------|
| 게스트 생성 | member_agreements 레코드 없음 (게스트는 동의 불필요) |
| Google OAuth 기존 회원 | 바로 JWT 발급 (이미 동의 완료) |
| Google OAuth 신규 회원 | 계정 생성 + JWT 발급. 개인정보 동의는 별도 흐름에서 처리 |
| 게스트 → 멤버 병합 | 기존 member_agreements 삭제 후 MEMBER로 승격 |

---

## 7. JWT 토큰 구조

### 7.1 Access Token (ATK)

**용도**: API 요청 인증

**생성**: `TokenProvider.createAccessToken(memberId, role)`

**구조**:
```json
{
  "sub": "123",           // memberId (문자열)
  "role": "MEMBER",       // 역할 (GUEST, MEMBER, ADMIN)
  "iat": 1698765432,      // 발급 시각
  "exp": 1698769032       // 만료 시각
}
```

**서명**: HMAC-SHA256 (`app.jwt.secret` 기반)

**만료 시간**: `app.jwt.access-token-expiry-ms` (기본값: 1시간)

**추출 방식**:
- dev/test: `Authorization: Bearer <token>` 헤더 우선, 없으면 `access_token` 쿠키
- prod: `access_token` 쿠키

### 7.2 Refresh Token (RTK)

**용도**: Access Token 재발급

**생성**: `TokenProvider.createRefreshToken()` → `UUID.randomUUID()`

**특징**: JWT가 아닌 랜덤 UUID

**저장**: DB `member_device` 테이블

**만료 시간**: `app.jwt.refresh-token-expiry-ms` (기본값: 7일)

**추출 방식**: `refresh_token` 쿠키

### 7.3 Merge Ticket

**용도**: 게스트→멤버 병합 시 일회성 토큰

**생성**: `TokenProvider.createMergeTicket(memberId, googleSubId)`

**구조**:
```json
{
  "sub": "123",                    // guestMemberId
  "googleSubId": "google-sub-id",  // Google 사용자 ID
  "type": "merge",                 // 토큰 타입
  "iat": 1698765432,
  "exp": 1698766032
}
```

**만료 시간**: `app.jwt.merge-ticket-expiry-ms` (기본값: 10분)

## 8. DB 테이블 구조

### 8.1 테이블 관계도

```
┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
│     member      │       │  member_auth    │       │member_agreements│
├─────────────────┤       ├─────────────────┤       ├─────────────────┤
│ id (PK)         │◄──────│ member_id (FK)  │       │ member_id (PK)  │
│ role            │       │ auth_type       │       │ status          │
│ name            │       │ auth_key        │       │ agreed_at       │
│ position        │       │ last_login_at   │       └─────────────────┘
│ department      │       └─────────────────┘
│ created_at      │
│ updated_at      │       ┌─────────────────┐
└─────────────────┘       │ member_device   │
                          ├─────────────────┤
                          │ id (PK)         │
                          │ member_id (FK)  │
                          │ refresh_token   │
                          │ fcm_token       │
                          │ platformjs_*    │
                          │ last_accessed_at│
                          │ expires_at      │
                          │ created_at      │
                          │ updated_at      │
                          └─────────────────┘
```

### 8.2 테이블 상세

#### member

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | 회원 ID |
| `role` | VARCHAR(20) | NOT NULL | 역할 (GUEST, MEMBER, ADMIN) |
| `name` | VARCHAR(50) | NULLABLE | 이름 |
| `position` | VARCHAR(50) | NULLABLE | 직책 |
| `department` | VARCHAR(50) | NULLABLE | 학과 |
| `created_at` | TIMESTAMP | NOT NULL | 생성 시각 |
| `updated_at` | TIMESTAMP | NOT NULL | 수정 시각 |

#### member_auth

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | 인증 정보 ID |
| `member_id` | BIGINT | NOT NULL | 회원 ID |
| `auth_type` | VARCHAR(20) | NOT NULL | 인증 유형 (GUEST_KEY, GOOGLE) |
| `auth_key` | VARCHAR(255) | UNIQUE | 인증 키 (게스트 키 또는 Google sub) |
| `last_login_at` | TIMESTAMP | NULLABLE | 마지막 로그인 시각 |
| `created_at` | TIMESTAMP | NOT NULL | 생성 시각 |
| `updated_at` | TIMESTAMP | NOT NULL | 수정 시각 |

#### member_device

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | 디바이스 ID |
| `member_id` | BIGINT | NOT NULL | 회원 ID |
| `refresh_token` | VARCHAR(512) | UNIQUE | RTK |
| `fcm_token` | VARCHAR(512) | NULLABLE | FCM 토큰 |
| `platformjs_name` | VARCHAR(100) | NULLABLE | 플랫폼 이름 |
| `platformjs_version` | VARCHAR(50) | NULLABLE | 플랫폼 버전 |
| `platformjs_layout` | VARCHAR(50) | NULLABLE | 레이아웃 |
| `platformjs_prerelease` | VARCHAR(50) | NULLABLE | 프리릴리즈 |
| `platformjs_os` | VARCHAR(100) | NULLABLE | OS |
| `platformjs_manufacturer` | VARCHAR(100) | NULLABLE | 제조사 |
| `platformjs_product` | VARCHAR(100) | NULLABLE | 제품 |
| `platformjs_description` | TEXT | NULLABLE | 설명 |
| `platformjs_ua` | TEXT | NULLABLE | User-Agent |
| `last_accessed_at` | TIMESTAMP | NULLABLE | 마지막 접근 시각 |
| `expires_at` | TIMESTAMP | NULLABLE | 만료 시각 |
| `created_at` | TIMESTAMP | NOT NULL | 생성 시각 |
| `updated_at` | TIMESTAMP | NOT NULL | 수정 시각 |

#### member_agreements

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| `member_id` | BIGINT | PK | 회원 ID (member.id와 공유) |
| `status` | BOOLEAN | NOT NULL | 동의 여부 |
| `agreed_at` | TIMESTAMP | NULLABLE | 동의 시각 |

### 8.3 테이블-레이어 매핑

| 테이블 | 관련 레이어 | 담당 클래스 |
|--------|-----------|------------|
| `member` | oauth, authentication, session | `GoogleAuthProvider`, `GuestAuthenticationProvider`, `SessionService` |
| `member_auth` | oauth, authentication | `GoogleAuthProvider`, `GuestAuthenticationProvider`, `MergeService` |
| `member_device` | session | `DeviceSessionService`, `SessionService` |
| `member_agreements` | authorization | `MemberAgreementService`, `PrivacyConsentFilter` |

---

## 9. 인증 흐름별 호출 경로

### 9.1 게스트 생성

```
POST /api/v1/auth/guest
    │
    ▼
AuthController.createGuest()
    │
    ├→ GuestAuthenticationProvider.authenticate()
    │       │
    │       ├→ Member.builder().role(GUEST).name("게스트_xxxx").build()
    │       ├→ memberRepository.save(member)
    │       ├→ MemberAgreement(memberId) 생성 (status=false)
    │       ├→ memberAgreementRepository.save(agreement)
    │       ├→ MemberAuth.builder().authType(GUEST_KEY).authKey(UUID).build()
    │       ├→ memberAuthRepository.save(memberAuth)
    │       └→ AuthenticatedIdentity(memberId) 반환
    │
    └→ SessionService.createSession(identity, device, fcmToken, response)
            │
            ├→ memberRepository.findById(memberId)
            ├→ TokenProvider.createAccessToken(memberId, role)
            ├→ TokenProvider.createRefreshToken() → UUID
            ├→ DeviceSessionService.upsert(memberId, rtk, fcmToken, device, expiry)
            ├→ TokenDeliveryStrategy.deliver(accessToken, refreshToken, response)
            └→ SessionResult 반환
```

### 9.2 Google OAuth 로그인

```
[1] OAuth 시작
POST /api/v1/auth/oauth/start
    │
    ▼
OAuthController.oauthStart()
    │
    ├→ OAuthStateService.createState()
    │       └→ Redis에 state 저장 (TTL 5분)
    │
    └→ Google Auth URL 생성 및 반환

[2] Google 리다이렉트 후 토큰 교환
POST /api/v1/auth/token
    │
    ▼
OAuthController.tokenExchange()
    │
    ├→ OAuthStateService.consumeState(state)
    │       └→ Redis에서 state 삭제 + 검증
    │
    ├→ GoogleAuthProvider.authenticate(code)
    │       │
    │       ├→ exchangeCodeForIdToken(code)
    │       │       └→ POST https://oauth2.googleapis.com/token
    │       │           └→ id_token 획득
    │       │
    │       ├→ verifyAndParseIdToken(idToken)
    │       │       ├→ Google JWKS에서 공개키 획득
    │       │       └→ JWT 서명 검증 + Claims 파싱
    │       │
    │       ├→ validateMjuDomain(claims)
    │       │       └→ hd=mju.ac.kr 검증
    │       │
    │       ├→ parseName(claims.get("name"))
    │       │       └→ "이름/직책/학과" 파싱
    │       │
    │       └→ findOrCreateMember(googleSubId, parsedName)
    │               │
    │               ├→ [기존 회원] memberAuthRepository.findByAuthKeyAndAuthType()
    │               │       └→ lastLoginAt 갱신
    │               │
    │               └→ [신규 회원]
    │                       ├→ Member.builder().role(MEMBER).name(...).build()
    │                       ├→ memberRepository.save(member)
    │                       ├→ MemberAgreement(memberId) 생성 (status=false)
    │                       ├→ memberAgreementRepository.save(agreement)
    │                       ├→ MemberAuth.builder().authType(GOOGLE).authKey(googleSubId).build()
    │                       └→ memberAuthRepository.save(memberAuth)
    │
    └→ SessionService.createSession(identity, null, null, response)
```

### 9.3 게스트 → 멤버 병합

```
POST /api/v1/auth/login/google/merge
    │
    ▼
AuthController.merge()
    │
    ├→ MergeService.merge(mergeTicket)
    │       │
    │       ├→ MergeTicketService.consume(ticket)
    │       │       └→ JWT 파싱 → guestMemberId, googleSubId 추출
    │       │
    │       ├→ memberAuthRepository.findByAuthKeyAndAuthType(googleSubId, GOOGLE)
    │       │       └→ Google 계정 소유자 확인
    │       │
    │       ├→ memberRepository.findById(googleAuth.memberId)
    │       │       └→ targetMember (병합 대상)
    │       │
    │       ├→ memberRepository.findById(guestMemberId)
    │       │       └→ guestMember (삭제될 게스트)
    │       │
    │       ├→ memberAuthRepository.findByMemberIdAndAuthType(guestMemberId, GUEST_KEY)
    │       │       └→ guestAuth 삭제
    │       │
    │       ├→ memberAgreementRepository.deleteById(guestMemberId)
    │       │       └→ 게스트의 동의 정보 삭제
    │       │
    │       ├→ DeviceSessionService.switchMember(guestMemberId, targetMemberId)
    │       │       └→ 게스트의 디바이스 세션을 target으로 이전
    │       │
    │       └→ memberRepository.delete(guestMember)
    │
    └→ SessionService.createSession(identity, device, fcmToken, response)
```

### 9.4 토큰 재발급

```
POST /api/v1/auth/refresh
    │
    ▼
AuthController.refreshToken()
    │
    ├→ extractRefreshToken(request)
    │       └→ refresh_token 쿠키에서 RTK 추출
    │
    └→ SessionService.refreshSession(rtk, response)
            │
            ├→ memberDeviceRepository.findByRefreshToken(rtk)
            │       └→ device 조회
            │
            ├→ device.expiresAt 검증
            │       └→ 만료 시 삭제 + 예외
            │
            ├→ memberRepository.findById(device.memberId)
            │       └→ member 조회
            │
            ├→ TokenProvider.createRefreshToken() → 새 RTK
            ├→ device.updateRefreshToken(newRtk)
            │
            ├→ TokenProvider.createAccessToken(memberId, role) → 새 ATK
            │
            └→ TokenDeliveryStrategy.deliver(newAtk, newRtk, response)
```

### 9.5 로그아웃

```
POST /api/v1/auth/logout
    │
    ▼
AuthController.logout()
    │
    ├→ SecurityContextHolder.getContext().getAuthentication()
    │       └→ memberId 추출
    │
    ├→ extractRefreshToken(request)
    │       └→ refresh_token 쿠키에서 RTK 추출
    │
    └→ SessionService.destroySession(rtk, fcmToken, memberId, response)
            │
            ├→ DeviceSessionService.deleteByFcmToken(memberId, fcmToken)
            │       └→ FCM 토큰 기반 디바이스 세션 삭제
            │
            └→ TokenDeliveryStrategy.clear(response)
                    └→ ATK/RTK 쿠키 삭제, dev/test는 헤더도 초기화
```

---

## 10. 토큰 전달 전략

### 10.1 환경별 차이

기본 인증 수단은 모든 환경에서 `access_token`, `refresh_token` HttpOnly 쿠키입니다.
`dev/test`는 Swagger와 수동 테스트 편의를 위해 같은 토큰을 응답 body와 header에도 추가로 노출합니다.

| 환경 | ATK 전달 | RTK 전달 | 추가 노출 | Swagger 스킴 |
|------|---------|---------|-----------|---------------|
| **dev/test** | HttpOnly `access_token` 쿠키 | HttpOnly `refresh_token` 쿠키 | body `accessToken`/`refreshToken`, `Authorization`, `X-Access-Token`, `X-Refresh-Token` 헤더 | `bearerAuth` + 쿠키 인증 가능 |
| **prod** | HttpOnly Secure `access_token` 쿠키 | HttpOnly Secure `refresh_token` 쿠키 | 없음 | `cookieAuth` |

### 10.2 구현 메커니즘

**TokenDeliveryStrategy 인터페이스**:
```java
public interface TokenDeliveryStrategy {
    void deliver(String accessToken, String refreshToken, HttpServletResponse response);
    void clear(HttpServletResponse response);
}
```

**프로파일 기반 구현체 전환**:

| 구현체 | 프로파일 | 동작 |
|--------|---------|------|
| `CookieTokenDelivery` | `prod` | HttpOnly Secure 쿠키 설정 |
| `HeaderTokenDelivery` | `dev`, `test` | HttpOnly 쿠키 설정 + 테스트용 응답 헤더 설정 |

**쿠키 설정**:
```java
ResponseCookie.from("access_token", accessToken)
    .httpOnly(true)
    .secure(true)  // prod: true, dev/test: false
    .sameSite("Lax")
    .path("/")
    .maxAge(Duration.ofMillis(3600000))  // 1시간
    .build();
```

**dev/test 추가 헤더**:
```http
Authorization: Bearer <accessToken>
X-Access-Token: <accessToken>
X-Refresh-Token: <refreshToken>
```

### 10.3 토큰 추출

**TokenExtractor 인터페이스**:
```java
public interface TokenExtractor {
    String extract(HttpServletRequest request);
}
```

**프로파일 기반 구현체**:

| 구현체 | 프로파일 | 추출 위치 |
|--------|---------|----------|
| `BearerTokenExtractor` | `dev`, `test` | `Authorization: Bearer <token>` 헤더 우선, 없으면 `access_token` 쿠키 |
| `CookieTokenExtractor` | `prod` | `access_token` 쿠키 |

---

## 11. 핵심 타입

### 11.1 AuthenticatedIdentity

**위치**: `auth/authentication/identity/AuthenticatedIdentity.java`

**설명**: Authentication 레이어의 출력. Session 레이어의 입력.

```java
@Getter
@Builder
@AllArgsConstructor
public class AuthenticatedIdentity {
    private final Long memberId;  // 항상 non-null
}
```

**생성 시점**:
- 게스트 생성: 새 member 저장 후 ID 반환
- Google 로그인: 기존 member 조회 후 ID 반환
- 병합: target member ID 반환

### 11.2 SessionResult

**위치**: `auth/session/SessionResult.java`

**설명**: Session 레이어의 출력. Controller가 DTO로 변환.

```java
@Getter
@Builder
@AllArgsConstructor
public class SessionResult {
    private final String accessToken;
    private final String refreshToken;
    private final Long memberId;
    private final String role;
    private final String name;
    private final String position;
    private final String department;
}
```

### 11.3 TokenClaims

**위치**: `auth/authentication/token/TokenProvider.java`

**설명**: JWT 파싱 결과

```java
public record TokenClaims(Long memberId, String role) {}
```

### 11.4 ConsentStatus

**위치**: `auth/authorization/consent/MemberAgreementService.java`

**설명**: 개인정보 동의 상태

```java
public record ConsentStatus(boolean status, Instant agreedAt) {}
```

---

## 12. ErrorCode

### 12.1 Auth 관련 에러코드

| 에러코드 | HTTP 상태 | 코드 | 메시지 | 발생 시점 |
|----------|----------|------|--------|----------|
| `AUTH_PRIVACY_POLICY_REQUIRED` | 403 FORBIDDEN | AUTH_001 | Privacy policy agreement is required. | 개인정보 미동의자가 MEMBER/ADMIN 엔드포인트 접근 시 |
| `AUTH_GOOGLE_AUTH_FAILED` | 401 UNAUTHORIZED | AUTH_002 | Google authentication failed. | Google OAuth 인증 실패 시 |
| `AUTH_INVALID_TOKEN_SIGNATURE` | 401 UNAUTHORIZED | AUTH_003 | Invalid token signature. | JWT 서명 검증 실패 시 |
| `AUTH_INVALID_REFRESH_TOKEN` | 401 UNAUTHORIZED | AUTH_004 | Invalid or expired refresh token. | RTK가 유효하지 않거나 만료된 경우 |
| `AUTH_MERGE_REQUIRED` | 409 CONFLICT | AUTH_005 | Guest data merge is required. | 게스트 데이터 병합이 필요한 경우 |
| `AUTH_MERGE_TICKET_EXPIRED` | 400 BAD_REQUEST | AUTH_006 | Merge ticket has expired. | 병합 티켓이 만료된 경우 |
| `AUTH_MEMBER_NOT_FOUND` | 404 NOT_FOUND | AUTH_007 | Member not found. | 회원을 찾을 수 없는 경우 |
| `AUTH_GUEST_NOT_FOUND` | 404 NOT_FOUND | AUTH_008 | Guest not found. | 게스트를 찾을 수 없는 경우 |
| `AUTH_ALREADY_EXISTS` | 409 CONFLICT | AUTH_009 | Auth key already exists. | 인증 키가 이미 존재하는 경우 |
| `AUTH_NOT_MJU_DOMAIN` | 403 FORBIDDEN | AUTH_010 | Only MJU (mju.ac.kr) accounts are allowed. | 명지대 도메인이 아닌 계정으로 로그인 시도 시 |

### 12.2 Security 관련 에러코드

| 에러코드 | HTTP 상태 | 코드 | 메시지 | 발생 시점 |
|----------|----------|------|--------|----------|
| `GLOBAL_SECURITY_UNAUTHORIZED_ACCESS` | 401 UNAUTHORIZED | GLOBAL_SECURITY_001 | Unauthorized access. | 인증되지 않은 요청 시 |
| `GLOBAL_SECURITY_FORBIDDEN` | 403 FORBIDDEN | GLOBAL_SECURITY_002 | Access denied. | 권한이 없는 엔드포인트 접근 시 (@PreAuthorize 차단) |

---

## 부록 A: 설정 프로퍼티

```yaml
app:
  jwt:
    secret: ${JWT_SECRET}                              # HMAC-SHA 서명 키
    access-token-expiry-ms: 3600000                    # 1시간
    refresh-token-expiry-ms: 604800000                 # 7일
    merge-ticket-expiry-ms: 600000                     # 10분
  
  auth:
    token-in-response: ${TOKEN_IN_RESPONSE:false}      # dev/test에서 true
  
  oauth2:
    google:
      client-id: ${GOOGLE_CLIENT_ID}
      client-secret: ${GOOGLE_CLIENT_SECRET}
      redirect-uri: ${GOOGLE_REDIRECT_URI}
```

---

## 부록 B: Swagger에서 OAuth 테스트

현재 설계(프론트 경유형)에서는 Swagger의 Authorize 버튼으로 직접 OAuth를 처리할 수 없습니다.

### 수동 테스트 순서

1. `GET /api/v1/auth/config/google` → clientId 확인
2. `POST /api/v1/auth/oauth/start` → googleAuthUrl 받기
3. 브라우저에서 googleAuthUrl 열기 → Google 로그인
4. 리다이렉트 URL에서 code, state 복사
5. `POST /api/v1/auth/token` {code, state} → accessToken 받기
6. Swagger Authorize → Bearer Auth에 accessToken 입력

---

## 부록 C: 확장 포인트

### C.1 새로운 OAuth 제공자 추가

1. `OAuthProvider` 인터페이스 정의 (현재는 `GoogleAuthProvider`만 존재)
2. `OAuthProviderFactory` 또는 전략 패턴으로 분기
3. `AuthType` enum에 새 제공자 추가

### C.2 새로운 역할 추가

1. `Member.Role` enum에 새 역할 추가
2. `RoleHierarchy` 문자열에 계층 관계 추가
3. 필요시 새 `@PreAuthorize` 표현식 사용

### C.3 개인정보 동의 버전 관리

현재는 단순 boolean만 저장. 추후 약관 버전 관리가 필요하면:

```java
// member_agreements 테이블 확장
@Column(name = "policy_version", length = 20)
private String policyVersion;

// 동의 시 버전 기록
public void agree(String version) {
    this.status = true;
    this.agreedAt = Instant.now();
    this.policyVersion = version;
}
```
