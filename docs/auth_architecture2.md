# Auth & Security Architecture v2

본 문서는 인증(Auth) 및 보안(Security), 그리고 회원(Account) 관리 시스템의 전체 코드베이스 구조와 구체적인 동작 메커니즘을 상세히 설명합니다.

---

## 목차

1. [핵심 설계 원칙](#1-핵심-설계-원칙)
2. [Security 레이어 (Policy)](#2-security-레이어-policy)
3. [Auth & Account 레이어 (Mechanism)](#3-auth--account-레이어-mechanism)
4. [단계별 시퀀스 흐름](#4-단계별-시퀀스-흐름)
5. [역할 계층 (Role Hierarchy)](#5-역할-계층-role-hierarchy)
6. [개인정보 동의](#6-개인정보-동의)
7. [JWT 토큰 구조](#7-jwt-토큰-구조)
8. [DB 테이블 구조](#8-db-테이블-구조)
9. [인증 흐름별 상세 호출 경로](#9-인증-흐름별-상세-호출-경로)
10. [토큰 전달 전략](#10-토큰-전달-전략)
11. [핵심 타입 정의](#11-핵심-타입-정의)
12. [ErrorCode 정의](#12-errorcode-정의)

---

## 1. 핵심 설계 원칙

### 1.1 Policy vs Mechanism 분리

| 구분 | GlobalSecurityConfig (Policy) | Auth/Account 메커니즘 (Mechanism) |
|------|------------------------------|----------------------|
| **위치** | `global.security` | `auth/`, `account/` |
| **관점** | 외부 요청 제어, 인가 규칙, 필터 체인 | 내부 비즈니스 로직, 데이터 조작 |
| **핵심 키워드** | SecurityFilterChain, RoleHierarchy | GuestService, GoogleOAuthService, AccountService |
| **비유** | 성벽의 출입문 통제 | 신분증 발급소와 정보 관리국 |

### 1.2 책임 분리 (Security, Auth, Account)
1. **Security (보안)**: HTTP 요청 레벨에서 JWT를 파싱하고 서명을 검증하여 사용자의 신원(Principal)을 SecurityContext에 확립합니다. 그 후 ConsentCheckFilter로 MEMBER/ADMIN 사용자의 동의 감사 여부를 추가 강제합니다.
2. **Auth (인증)**: 각 인증 수단(게스트 로그인, 구글 로그인, 테스트 로그인)별 검증과 데이터 병합, 쿠키/헤더 세션 관리 및 토큰 회수/재발급을 담당합니다.
3. **Account (회원)**: 인증된 회원 본인의 리소스 관리(프로필 조회, 탈퇴 처리, 규제 동의 감사 기록 관리)를 전담합니다.

---

## 2. Security 레이어 (Policy)

### 2.1 GlobalSecurityConfig

**파일 위치**: `com/mjusugangsincheonghelper/global/security/GlobalSecurityConfig.java`

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // @PreAuthorize 활성화
@RequiredArgsConstructor
public class GlobalSecurityConfig {

    public static final String[] PUBLIC_URLS = {
            "/api/*/auth/guest",
            "/api/*/auth/refresh",
            "/api/*/auth/login/google/merge",
            "/api/*/auth/oauth/start",
            "/api/*/auth/token",
            "/api/*/auth/config/google",
            "/api/*/auth/test-**",
            "/api/*/example/**",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/actuator/**",
            "/*.html"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ConsentCheckFilter consentCheckFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

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
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("ROLE_ADMIN > ROLE_MEMBER > ROLE_GUEST");
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setExposedHeaders(Arrays.asList(
                "Authorization", "X-Access-Token", "X-Refresh-Token",
                "X-Request-Id", "X-Api-Version", "Set-Cookie"
        ));
        configuration.setMaxAge(3600L);
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
```

### 2.2 필터 체인 순서 및 동작 방식

```
요청 → [JwtAuthenticationFilter] → [ConsentCheckFilter] → DispatcherServlet → [@PreAuthorize] → Controller
```

- **JwtAuthenticationFilter**는 `shouldNotFilter()`로 `/api/`로 시작하지 않는 요청을 건너뛰며, DB 접근 없이 JWT 내부 Claims(`memberId`, `role`, `agreed`)만으로 Spring Security의 `Authentication` 객체를 만듭니다. `agreed` 클레임은 `request.setAttribute("privacyAgreed", ...)`로 후속 필터에 전달됩니다.
- **ConsentCheckFilter**는 SecurityContext의 인증 객체가 `Long`(memberId) principal을 가지며 `ROLE_MEMBER`/`ROLE_ADMIN` 권한을 가질 때만 `privacyAgreed` 플래그를 검사합니다. 동의하지 않은 사용자가 비면제 경로로 접근하면 `AUTH_PRIVACY_POLICY_REQUIRED`(403) 응답을 즉시 반환합니다. 면제 경로는 `/auth/privacy/agree`, `/auth/logout`입니다.
- 세부 인가는 컨트롤러/메서드에 정의된 `@PreAuthorize("hasRole('MEMBER')")` 등에 의해 스프링 AOP 단에서 최종 검증됩니다.

---

## 3. Auth & Account 레이어 (Mechanism)

### 3.1 세부 패키지 및 주요 클래스 매핑

```
com.mjusugangsincheonghelper/
├── global/security/                          # [보안 인프라 패키지]
│   ├── GlobalSecurityConfig.java             #   - 2개 SecurityFilterChain (public/secured) 설정
│   ├── filter/
│   │   ├── JwtAuthenticationFilter.java      #   - 무상태 JWT 인증 필터 (agreed 클레임 전파)
│   │   └── ConsentCheckFilter.java           #   - MEMBER+ 사용자 동의 감사 강제
│   └── token/
│       ├── TokenExtractor.java               #   - 토큰 추출 인터페이스
│       ├── BearerTokenExtractor.java         #   - dev/test용 추출기 (Authorization 헤더 + 쿠키)
│       └── CookieTokenExtractor.java         #   - prod용 추출기 (쿠키만)
│
├── account/                                  # [회원 리소스 패키지]
│   ├── controller/
│   │   ├── AccountController.java            #   - GET/DELETE /api/{version}/accounts/me (@PreAuthorize hasRole('GUEST'))
│   │   └── AccountAgreementController.java   #   - POST /api/{version}/auth/privacy/agree
│   ├── service/
│   │   ├── AccountService.java               #   - 프로필 조회 및 회원 탈퇴 서비스
│   │   └── AccountAgreementService.java      #   - 동의 감사 기록 + isAgreed 조회
│   └── dto/
│       ├── AccountMeResponse.java            #   - 내 정보 응답 (isPrivacyPolicyAgreed 포함)
│       └── PrivacyAgreementResponse.java     #   - 동의 응답
│
└── auth/                                     # [인증 & 세션 패키지]
    ├── common/
    │   ├── AuthenticatedIdentity.java        #   - 인증 완료 후 전달용 VO (memberId)
    │   └── dto/
    │       └── DeviceInfo.java               #   - 플랫폼/UA/FCM 정보를 담은 DTO
    ├── guest/
    │   ├── GuestController.java              #   - POST /api/{version}/auth/guest
    │   ├── GuestService.java                 #   - 게스트 계정 + GUEST_KEY UUID 발급
    │   └── dto/
    │       ├── GuestCreateRequest.java       #   - fcmToken, device
    │       └── GuestResponse.java            #   - memberId, role, name, [accessToken, refreshToken]
    ├── oauth/
    │   ├── GoogleOAuthController.java        #   - config/google, oauth/start, token
    │   ├── GoogleOAuthService.java           #   - Google ID Token 검증(JWKS 1h 캐시), name/position/department 파싱
    │   ├── OAuthStateService.java            #   - state 생성/소비 (Redis, 5분 TTL)
    │   ├── OAuthAuthenticationResult.java    #   - 인증 결과(identity, newUser)
    │   └── dto/
    │       ├── OAuthConfigResponse.java
    │       ├── OAuthStartResponse.java
    │       ├── OAuthTokenRequest.java        #   - code, state
    │       └── OAuthTokenResponse.java       #   - status, newUser, memberId, role, name, position, department, [tokens]
    ├── merge/
    │   ├── MergeController.java              #   - POST /api/{version}/auth/login/google/merge
    │   ├── MergeService.java                 #   - 게스트 데이터 병합 서비스
    │   ├── MergeTicketService.java           #   - 일회성 병합 티켓 JWT 생성/파싱
    │   └── dto/
    │       ├── MergeRequest.java             #   - mergeTicket, fcmToken, device
    │       └── MergeResponse.java            #   - memberId, role, name, position, department, [tokens]
    ├── session/
    │   ├── SessionController.java            #   - POST /api/{version}/auth/refresh, POST .../auth/logout
    │   ├── SessionService.java               #   - createSession/refreshSession/reissueToken/destroySession
    │   ├── SessionResult.java                #   - ATK/RTK + 회원 프로필 통합 결과 VO
    │   ├── device/
    │   │   └── DeviceSessionService.java     #   - member_device upsert/switchMember/deleteByFcmToken
    │   ├── token/
    │   │   └── TokenProvider.java            #   - JWT 서명/발급 (ATK/RTK/MergeTicket) + TokenClaims VO
    │   ├── delivery/
    │   │   ├── TokenDeliveryStrategy.java    #   - 쿠키/헤더 토큰 전달 전략 인터페이스
    │   │   ├── CookieTokenDelivery.java      #   - prod 프로파일 (Secure 쿠키)
    │   │   └── HeaderTokenDelivery.java      #   - dev/test 프로파일 (쿠키 + 헤더 동시 노출)
    │   └── dto/
    │       ├── LogoutRequest.java
    │       └── RefreshResponse.java
    └── test/                                 #   - dev 프로파일 한정 테스트 인증
        ├── TestAuthController.java           #   - GET/POST /api/{version}/auth/test-accounts, POST .../test-login
        ├── TestAccountInitializer.java       #   - application.yml 기반 Member+MemberAuth(TEST)+MemberAgreement 시드
        ├── CreateTestAccountRequest.java     #   - role
        ├── TestAccountResponse.java          #   - name, role
        └── TestLoginResponse.java            #   - memberId, role, name, position, department, accessToken, refreshToken
```

---

## 4. 단계별 시퀀스 흐름

### 4.1 전체 흐름 요약

```
[1] 신원 확인 및 계정 준비 (auth/guest, auth/oauth, auth/test)
    └→ 게스트 로그인 시: Member(GUEST) + MemberAuth(GUEST_KEY) 생성 후 ATK/RTK 발급
    └→ 구글 로그인 시: Google ID Token(JWKS) 서명 검증 후 Member(MEMBER) 생성 후 ATK/RTK 발급
    └→ (dev) 테스트 로그인 시: 사전 시드된 Member(TEST) 또는 자동 생성 계정으로 ATK/RTK 발급

[2] 약관 동의 (account/agreement)
    └→ 신규 가입 시 프론트 주도로 POST /api/{version}/auth/privacy/agree 호출하여 감사 로그(agreedAt) 생성/갱신
    └→ SessionService.reissueToken() 호출로 ATK에 agreed=true 반영 (CookieTokenDelivery로 재발급)

[3] 세션 관리 (auth/session)
    └→ 갱신 요청 시 기존 RTK 검증 후 회전(Rotation)하여 신규 ATK/RTK 쿠키 발급
    └→ 로그아웃 요청 시 특정 FCM 토큰 기기의 디바이스 세션 파괴 및 쿠키 초기화

[4] 회원 탈퇴 (account/withdrawal)
    └→ 탈퇴 요청 시 관련 DB(agreements, device, member_auth, member) 일괄 데이터 삭제 및 쿠키 초기화
```

---

## 5. 역할 계층 (Role Hierarchy)

### 5.1 역할별 설명 및 생성 시점
- **ROLE_GUEST**: 게스트 생성(/auth/guest)을 통해 생성된 임시 권한.
- **ROLE_MEMBER**: Google 계정으로 정상 가입 완료하여 MJU 도메인이 검증된 학생 권한. `/auth/privacy/agree` 호출 시점부터 ConsentCheckFilter를 통과합니다.
- **ROLE_ADMIN**: 관리자 권한 (수동 부여).

### 5.2 계층 관계
스프링 시큐리티 계층 설정을 통해 상위 권한은 하위 권한을 자동으로 포함합니다.
```
ROLE_ADMIN > ROLE_MEMBER > ROLE_GUEST
```
`@PreAuthorize("hasRole('MEMBER')")` 지정 시, ADMIN과 MEMBER는 접근을 허용하고 GUEST는 차단됩니다.
`@PreAuthorize("hasRole('GUEST')")`는 모든 인증된 사용자에게 허용됩니다(AccountController의 `/accounts/me` 조회/탈퇴).

---

## 6. 개인정보 동의

개인정보 처리 방침 동의는 OAuth 로그인 프로세스와 **완전히 분리**되어 실행되며, `ConsentCheckFilter`에 의해 사후 강제됩니다.

1. `/api/{version}/auth/token` API 응답에서 `newUser: true`를 반환받으면 프론트엔드가 자체적으로 개인정보 동의 UI를 노출합니다.
2. 사용자가 동의를 수락하면 `POST /api/{version}/auth/privacy/agree` 엔드포인트를 호출합니다. (Role: MEMBER 이상)
3. `AccountAgreementService`는 `member_agreements` 테이블에 동의 감사 기록(Agreed Log)을 생성하거나 동의 시각(`agreedAt`)을 갱신합니다.
4. `SessionService.reissueToken()`이 호출되어 ATK/RTK를 재발급하며, 새 ATK에는 `agreed=true` 클레임이 포함됩니다.
5. 이후 모든 요청에서 `JwtAuthenticationFilter`가 `privacyAgreed` 플래그를 request attribute에 세팅하고, `ConsentCheckFilter`가 MEMBER/ADMIN 사용자에 대해 동의 여부를 검증하여 미동의 시 즉시 403을 반환합니다.

---

## 7. JWT 토큰 구조

### 7.1 Access Token (ATK)
- **용도**: API 호출 시 매번 보안 필터가 요구하는 서명 토큰. 만료 시간은 `system_config`의 `jwt_expiry_config.accessTokenExpiryMs`(기본 1시간).
- **Payload**:
  ```json
  {
    "sub": "12",
    "role": "MEMBER",
    "agreed": true,
    "iat": 1700000000,
    "exp": 1700003600
  }
  ```

### 7.2 Refresh Token (RTK)
- **용도**: Access Token 만료 시 재발급을 요청하기 위한 만료 7일짜리 난수 UUID. 만료 시간은 `system_config`의 `jwt_expiry_config.refreshTokenExpiryMs`(기본 7일).
- **특징**: 데이터가 없는 무작위 문자열이며 DB `member_device` 테이블의 `refresh_token` 컬럼과 매핑하여 유효성을 검증합니다.

### 7.3 Merge Ticket
- **용도**: 게스트 상태에서 소셜 로그인 계정으로 데이터를 병합하기 위해 발급하는 일회성 서명 토큰. 만료 시간은 `system_config`의 `jwt_expiry_config.mergeTicketExpiryMs`(기본 5분).
- **Payload**:
  ```json
  {
    "sub": "12",              // 게스트 멤버 ID
    "googleSubId": "12345...", // 연동될 구글 Sub ID
    "type": "merge",
    "iat": 1700000000,
    "exp": 1700000600
  }
  ```

---

## 8. DB 테이블 구조

```
┌─────────────────┐       ┌─────────────────┐       ┌─────────────────────┐
│     member      │       │  member_auth    │       │ member_agreements   │
├─────────────────┤       ├─────────────────┤       ├─────────────────────┤
│ id (PK)         │◄──────│ member_id (UQ)  │       │ member_id (PK)      │
│ role            │       │ auth_type       │       │ status              │
│ name            │       │ auth_key (UQ)   │       │ agreed_at (Long, ms) │
│ position        │       │ last_login_at   │       └─────────────────────┘
│ department      │       │ created_at      │
│ created_at      │       │ updated_at      │
│ updated_at      │       └─────────────────┘
└─────────────────┘
         ▲
         │
┌──────────────────────────┐
│      member_device       │
├──────────────────────────┤
│ id (PK)                  │
│ member_id (FK)           │
│ refresh_token (UQ)       │
│ fcm_token                │
│ platformjs_name          │
│ platformjs_version       │
│ platformjs_layout        │
│ platformjs_prerelease    │
│ platformjs_os            │
│ platformjs_manufacturer  │
│ platformjs_product       │
│ platformjs_description   │
│ platformjs_ua            │
│ last_accessed_at         │
│ expires_at               │
│ created_at, updated_at   │
└──────────────────────────┘
```

- `member_auth.auth_type`: `GUEST_KEY`, `GOOGLE`, `TEST` 중 하나.
- `member_auth.member_id`와 `member_auth.auth_key`는 unique 제약.
- `member_agreements.agreed_at`은 epoch millis 기반 `Long`.
- `member_device.refresh_token`은 unique, `fcm_token`은 nullable.
- 각 외래키 필드(`member_id`)는 JPA 엔티티상에서 관계 객체 대신 기본 `Long` 타입 필드로 소유하여 패키지/도메인 간의 물리적 테이블 참조 결합도를 완화합니다.

---

## 9. 인증 흐름별 상세 호출 경로

### 9.1 게스트 생성
```
POST /api/{version}/auth/guest
 └→ GuestController.createGuest()
     └→ GuestService.authenticate()
         ├→ Member.builder().role(GUEST).name("게스트_xxxx").build() 저장
         ├→ MemberAuth.builder().authType(GUEST_KEY).authKey(UUID).build() 저장
         └→ AuthenticatedIdentity 반환
     └→ SessionService.createSession(identity, device, fcmToken, response)
         ├→ accountAgreementService.isAgreed(memberId)  (게스트는 항상 false)
         ├→ TokenProvider.createAccessToken(memberId, "GUEST", false)
         ├→ TokenProvider.createRefreshToken()  (UUID)
         ├→ DeviceSessionService.upsert(memberId, refreshToken, fcmToken, device, refreshExpiryMs)
         └→ TokenDeliveryStrategy.deliver(atk, rtk, response) (쿠키 세팅)
```

### 9.2 Google OAuth 로그인
```
POST /api/{version}/auth/token
 └→ GoogleOAuthController.tokenExchange()
     ├→ OAuthStateService.consumeState(state) (Redis 5분 TTL 검증 및 소비)
     ├→ GoogleOAuthService.authenticate(code)
     │   ├→ exchangeCodeForIdToken(code) (Google token 엔드포인트 호출)
     │   ├→ verifyAndParseIdToken(idToken) (JWKS 캐시 1시간 TTL로 서명 검증, Claims 파싱)
     │   ├→ validateMjuDomain(claims) (hd == "mju.ac.kr")
     │   ├→ parseName(claims.name)  ("이름/직책/학과" 형식 파싱)
     │   └→ authenticateOrCreateMember(googleSubId, parsedName)
     │       ├→ 기존 회원이면: lastLoginAt 갱신 + member.promoteToMember(name, position, department)
     │       └→ 신규 회원이면: Member(MEMBER) + MemberAuth(GOOGLE) 생성
     └→ SessionService.createSession(identity, null, null, httpResponse)  (OAuth는 device/fcmToken 미사용)
```

### 9.3 게스트 → 구글 계정 데이터 병합
```
POST /api/{version}/auth/login/google/merge {mergeTicket, fcmToken, device}
 └→ MergeController.merge()
     └→ MergeService.merge(mergeTicket)
         ├→ MergeTicketService.consume(ticket) -> guestMemberId, googleSubId
         ├→ MemberAuth(GOOGLE) 조회로 target Member 식별
         ├→ 게스트 MemberAuth(GUEST_KEY) 삭제
         ├→ DeviceSessionService.switchMember(guestId, targetId)  (디바이스 소유권 이전)
         └→ guest Member 레코드 삭제
     └→ SessionService.createSession(identity, device, fcmToken, response)
```

### 9.4 토큰 재발급
```
POST /api/{version}/auth/refresh
 └→ SessionController.refreshToken()
     └→ SessionService.refreshSession(refreshToken, response)
         ├→ MemberDevice 조회 (refresh_token 매핑)
         ├→ 만료된 디바이스면 삭제 후 AUTH_INVALID_REFRESH_TOKEN
         ├→ accountAgreementService.isAgreed(memberId)  (재발급 ATK에 반영)
         ├→ tokenProvider.createRefreshToken() (UUID 회전)
         ├→ device.updateRefreshToken(newRtk)
         ├→ tokenProvider.createAccessToken(memberId, role, privacyAgreed)
         └→ TokenDeliveryStrategy.deliver(...)
```

### 9.5 개인정보 동의 및 토큰 재발급
```
POST /api/{version}/auth/privacy/agree  (PreAuthorize: hasRole('MEMBER'))
 └→ AccountAgreementController.agreePrivacyPolicy()
     ├→ AccountAgreementService.agree(memberId)  (member_agreements 기록 생성/갱신)
     └→ SessionService.reissueToken(memberId, refreshToken, response)
         ├→ MemberDevice 조회 (refresh_token 매핑)
         ├→ tokenProvider.createRefreshToken() (UUID 회전)
         ├→ device.updateRefreshToken(newRtk)
         ├→ tokenProvider.createAccessToken(memberId, role, true)  (agreed=true)
         └→ TokenDeliveryStrategy.deliver(...)
```

### 9.6 회원 탈퇴 (Account Withdrawal)
```
DELETE /api/{version}/accounts/me  (PreAuthorize: hasRole('GUEST'))
 └→ AccountController.withdraw()
     └→ AccountService.withdraw(memberId)
         ├→ memberAgreementRepository.deleteById(memberId)
         ├→ memberDeviceRepository.deleteAllInBatch(devices)  (모든 기기 세션 파괴)
         ├→ memberAuthRepository.delete(memberAuth)  (소셜 연동 해제)
         └→ memberRepository.delete(member)
     └→ TokenDeliveryStrategy.clear(response)  (HTTP 쿠키 Set-Cookie maxAge=0)
```

### 9.7 로그아웃
```
POST /api/{version}/auth/logout  (PreAuthorize: hasRole('GUEST'))
 └→ SessionController.logout()
     └→ SecurityContextHolder에서 memberId 추출
     └→ SessionService.destroySession(refreshToken, fcmToken, memberId, response)
         ├→ DeviceSessionService.deleteByFcmToken(memberId, fcmToken)
         └→ TokenDeliveryStrategy.clear(response)
```

### 9.8 dev 환경 테스트 인증
```
GET  /api/{version}/auth/test-accounts
 └→ TestAccountInitializer.getTestAccounts() -> application.yml app.auth.test-accounts 목록 반환

POST /api/{version}/auth/test-login?name=<이름>
 └→ TestAuthController.testLogin(name)
     ├→ MemberAuth(TEST) 조회
     ├→ 없으면: Member(MEMBER) + MemberAgreement(true) + MemberAuth(TEST) 자동 생성
     └→ SessionService.createSession(identity, null, null, response)

POST /api/{version}/auth/test-accounts {role}
 └→ TestAuthController.createTestAccount(request)
     ├→ role 결정 (요청 없으면 MEMBER)
     ├→ Member + MemberAgreement + MemberAuth(TEST) 신규 생성
     └→ SessionService.createSession(identity, null, null, response)
```

---

## 10. 토큰 전달 전략

쿠키 발행 옵션은 환경 프로파일별로 상이합니다.

- **Cookie 설정 공통 규칙**:
  - `access_token`: `accessTokenExpiryMs` 만료 (기본 1시간), Path=/
  - `refresh_token`: `refreshTokenExpiryMs` 만료 (기본 7일), Path=/
  - `HttpOnly`: 스크립트 접근 불허 (XSS 방지)
  - `SameSite`: Lax

- **환경별 차이**:
  - **prod**: `Secure` 플래그 활성화(HTTPS 필수), 응답 바디나 헤더에 토큰 값을 추가 노출하지 않음.
  - **dev / test**: `Secure` 플래그 비활성화(HTTP 개발 허용), `app.auth.token-in-response=true` 설정을 통해 API 응답 바디에 토큰 문자열을, 응답 헤더에 `Authorization: Bearer <ATK>`, `X-Access-Token: <ATK>`, `X-Refresh-Token: <RTK>`를 병행 노출.

- CORS Exposed Headers: `Authorization`, `X-Access-Token`, `X-Refresh-Token`, `X-Request-Id`, `X-Api-Version`, `Set-Cookie` (프론트에서 헤더로 토큰을 읽을 수 있도록 노출).

---

## 11. 핵심 타입 정의

### 11.1 AuthenticatedIdentity
- **위치**: `com/mjusugangsincheonghelper/auth/common/AuthenticatedIdentity.java`
- **역할**: 인증 레이어가 완료된 후 세션 레이어로 회원 ID를 안전하게 넘겨주기 위한 VO (`memberId` 단일 필드).

### 11.2 SessionResult
- **위치**: `com/mjusugangsincheonghelper/auth/session/SessionResult.java`
- **역할**: 발급된 세션의 토큰 상태 및 대상 사용자의 권한·이름·직책·부서 정보를 전달하는 통합 결과 체인 객체.

### 11.3 OAuthAuthenticationResult
- **위치**: `com/mjusugangsincheonghelper/auth/oauth/OAuthAuthenticationResult.java`
- **역할**: GoogleOAuthService가 인증 후 컨트롤러로 반환하는 결과 VO (`identity`, `newUser`).

### 11.4 TokenClaims
- **위치**: `com/mjusugangsincheonghelper/auth/session/token/TokenProvider.java` (record `TokenProvider.TokenClaims`)
- **역할**: JwtAuthenticationFilter가 SecurityContext 인증 객체를 구성할 때 사용하는 claims record (`memberId`, `role`, `agreed`).

### 11.5 MergeTicketClaims
- **위치**: `com/mjusugangsincheonghelper/auth/merge/MergeTicketService.java` (record `MergeTicketService.MergeTicketClaims`)
- **역할**: MergeTicketService.consume()이 토큰에서 추출한 (`guestMemberId`, `googleSubId`) 페어.

### 11.6 ConsentStatus
- **위치**: `com/mjusugangsincheonghelper/account/service/AccountAgreementService.java` (record)
- **역할**: 동의 처리 후 `status`/`agreedAt`을 컨트롤러로 반환하기 위한 record.

---

## 12. ErrorCode 정의

### 12.1 주요 비즈니스 에러
| 에러코드 | HTTP 상태 | 코드 | 설명 |
|---|---|---|---|
| `AUTH_PRIVACY_POLICY_REQUIRED` | 403 | AUTH_001 | MEMBER+ 사용자가 개인정보 동의 없이 보호된 리소스 접근 시도 (ConsentCheckFilter) |
| `AUTH_GOOGLE_AUTH_FAILED` | 401 | AUTH_002 | Google 서버 연동 실패 / JWKS 조회 실패 / ID Token 파싱 실패 |
| `AUTH_INVALID_TOKEN_SIGNATURE` | 401 | AUTH_003 | JWT 서명 검증 실패 |
| `AUTH_INVALID_REFRESH_TOKEN` | 401 | AUTH_004 | DB 세션 기한 만료 혹은 일치하지 않는 RTK |
| `AUTH_MERGE_REQUIRED` | 409 | AUTH_005 | 게스트 데이터를 구글 계정으로 병합해야 함 |
| `AUTH_MERGE_TICKET_EXPIRED` | 400 | AUTH_006 | 데이터 병합용 일회성 JWT 만료/파싱 실패 |
| `AUTH_MEMBER_NOT_FOUND` | 404 | AUTH_007 | 지정된 ID의 회원이 시스템 내 존재하지 않음 |
| `AUTH_GUEST_NOT_FOUND` | 404 | AUTH_008 | 병합 대상 게스트 회원이 존재하지 않음 |
| `AUTH_ALREADY_EXISTS` | 409 | AUTH_009 | 이미 존재하는 인증 키 |
| `AUTH_NOT_MJU_DOMAIN` | 403 | AUTH_010 | Google ID Token의 hd 클레임이 `mju.ac.kr`이 아님 |
| `GLOBAL_SECURITY_UNAUTHORIZED_ACCESS` | 401 | GLOBAL_SECURITY_001 | 인증 헤더 혹은 쿠키가 유실된 무인증 상태 요청 |
| `GLOBAL_SECURITY_FORBIDDEN` | 403 | GLOBAL_SECURITY_002 | 요구하는 역할 등급(예: ROLE_MEMBER) 미달 |
