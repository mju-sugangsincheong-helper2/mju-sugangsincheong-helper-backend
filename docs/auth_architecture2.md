# Auth & Security Architecture v2

본 문서는 인증(Auth) 및 보안(Security), 그리고 회원(Member) 관리 시스템의 전체 코드베이스 구조와 구체적인 동작 메커니즘을 상세히 설명합니다.

---

## 목차

1. [핵심 설계 원칙](#1-핵심-설계-원칙)
2. [Security 레이어 (Policy)](#2-security-레이어-policy)
3. [Auth & Member 레이어 (Mechanism)](#3-auth--member-레이어-mechanism)
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

| 구분 | GlobalSecurityConfig (Policy) | Auth/Member 메커니즘 (Mechanism) |
|------|------------------------------|----------------------|
| **위치** | `global.security` | `auth/`, `member/` |
| **관점** | 외부 요청 제어, 인가 규칙, 필터 체인 | 내부 비즈니스 로직, 데이터 조작 |
| **핵심 키워드** | SecurityFilterChain, RoleHierarchy | GuestService, GoogleOAuthService, MemberService |
| **비유** | 성벽의 출입문 통제 | 신분증 발급소와 정보 관리국 |

### 1.2 책임 분리 (Security, Auth, Member)
1. **Security (보안)**: HTTP 요청 레벨에서 JWT를 파싱하고 서명을 검증하여 사용자의 신원(Principal)을 SecurityContext에 확립합니다.
2. **Auth (인증)**: 각 인증 수단(게스트 로그인, 구글 로그인)별 검증과 데이터 병합, 쿠키/헤더 세션 관리 및 토큰 회수/재발급을 담당합니다.
3. **Member (회원)**: 인증된 회원 본인의 리소스 관리(프로필 조회, 탈퇴 처리, 규제 동의 감사 기록 관리)를 전담합니다.

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

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

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
                .requestMatchers("/api/*/example/**").permitAll()
                .requestMatchers("/api/*/system/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/*.html").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("ROLE_ADMIN > ROLE_MEMBER > ROLE_GUEST");
    }
}
```

### 2.2 필터 체인 순서 및 동작 방식

```
요청 → [JwtAuthenticationFilter] → DispatcherServlet → [@PreAuthorize] → Controller
```

- **JwtAuthenticationFilter**는 DB 접근 없이 JWT 내부 Claims(`memberId`, `role`)만으로 Spring Security의 `Authentication` 객체를 만듭니다.
- 세부 인가는 컨트롤러 메서드에 정의된 `@PreAuthorize("hasRole('MEMBER')")`에 의해 스프링 AOP 단에서 최종 검증됩니다.

---

## 3. Auth & Member 레이어 (Mechanism)

### 3.1 세부 패키지 및 주요 클래스 매핑

```
com.mjusugangsincheonghelper/
├── global/security/                          # [보안 인프라 패키지]
│   ├── GlobalSecurityConfig.java             #   - 보안 설정
│   ├── filter/
│   │   └── JwtAuthenticationFilter.java      #   - 무상태 JWT 인증 필터
│   └── token/
│       ├── TokenExtractor.java               #   - 토큰 추출 인터페이스
│       ├── BearerTokenExtractor.java         #   - dev/test용 추출기
│       └── CookieTokenExtractor.java         #   - prod용 추출기
│
├── member/                                   # [회원 리소스 패키지]
│   ├── controller/
│   │   ├── MemberController.java             #   - 프로필 조회, 회원 탈퇴 컨트롤러
│   │   └── MemberAgreementController.java    #   - 개인정보 동의 컨트롤러
│   ├── service/
│   │   ├── MemberService.java                #   - 프로필 조회 및 회원 탈퇴 서비스
│   │   └── MemberAgreementService.java       #   - 개인정보 동의서 기록 서비스
│   └── dto/
│       ├── MemberMeResponse.java
│       └── PrivacyAgreementResponse.java
│
└── auth/                                     # [인증 & 세션 패키지]
    ├── common/
    │   ├── AuthenticatedIdentity.java        #   - 인증 완료 후 전달용 VO
    │   └── dto/
    │       └── DeviceInfo.java
    ├── guest/
    │   ├── GuestController.java              #   - POST /auth/guest
    │   └── GuestService.java                 #   - 게스트 계정 생성
    ├── oauth/
    │   ├── GoogleOAuthController.java        #   - config/google, oauth/start, token
    │   ├── GoogleOAuthService.java           #   - Google ID Token 검증 및 회원 가입
    │   └── OAuthStateService.java            #   - CSRF 방지용 state 검증 (Redis)
    ├── merge/
    │   ├── MergeController.java              #   - POST /login/google/merge
    │   ├── MergeService.java                 #   - 게스트 데이터 병합 서비스
    │   └── MergeTicketService.java           #   - 일회성 병합 티켓 JWT 생성/파싱
    └── session/
        ├── SessionController.java            #   - POST /auth/refresh, POST /auth/logout
        ├── SessionService.java               #   - 세션 생성, 갱신, 파괴
        ├── device/
        │   └── DeviceSessionService.java     #   - member_device CRUD 관리
        ├── token/
        │   └── TokenProvider.java            #   - JWT 서명/발행 및 복호화
        └── delivery/
            ├── TokenDeliveryStrategy.java    #   - 쿠키/헤더 토큰 전달 전략
            ├── CookieTokenDelivery.java      #   - 운영 환경 전용 전달
            └── HeaderTokenDelivery.java      #   - 개발 환경용 헤더 동시 노출
```

---

## 4. 단계별 시퀀스 흐름

### 4.1 전체 흐름 요약

```
[1] 신원 확인 및 계정 준비 (auth/guest, auth/oauth)
    └→ 게스트 로그인 시: Member(GUEST) 생성 후 ATK/RTK 발급
    └→ 구글 로그인 시: Google ID Token 서명 검증 후 Member(MEMBER) 생성 후 ATK/RTK 발급
    
[2] 약관 동의 (member/agreement)
    └→ 신규 가입 시 프론트 주도로 POST /auth/privacy/agree 호출하여 감사 로그(agreedAt) 생성

[3] 세션 관리 (auth/session)
    └→ 갱신 요청 시 기존 RTK 검증 후 회전(Rotation)하여 신규 ATK/RTK 쿠키 발급
    └→ 로그아웃 요청 시 특정 FCM 토큰 기기의 디바이스 세션 파괴 및 쿠키 초기화

[4] 회원 탈퇴 (member/withdrawal)
    └→ 탈퇴 요청 시 관련 DB(agreements, device, member_auth, member) 일괄 데이터 삭제 및 쿠키 초기화
```

---

## 5. 역할 계층 (Role Hierarchy)

### 5.1 역할별 설명 및 생성 시점
- **ROLE_GUEST**: 게스트 생성(/auth/guest)을 통해 생성된 임시 권한.
- **ROLE_MEMBER**: Google 계정으로 정상 가입 완료하여 MJU 도메인이 검증된 학생 권한.
- **ROLE_ADMIN**: 관리자 권한 (수동 부여).

### 5.2 계층 관계
스프링 시큐리티 계층 설정을 통해 상위 권한은 하위 권한을 자동으로 포함합니다.
```
ROLE_ADMIN > ROLE_MEMBER > ROLE_GUEST
```
`@PreAuthorize("hasRole('MEMBER')")` 지정 시, ADMIN과 MEMBER는 접근을 허용하고 GUEST는 차단됩니다.

---

## 6. 개인정보 동의

개인정보 처리 방침 동의는 OAuth 로그인 프로세스와 **완전히 분리**되어 실행됩니다.

1. `/auth/token` API 응답에서 `newUser: true`를 반환받으면 프론트엔드가 자체적으로 개인정보 동의 UI를 노출합니다.
2. 사용자가 동의를 수락하면 `POST /auth/privacy/agree` 엔드포인트를 호출합니다.
3. `MemberAgreementService`는 `member_agreements` 테이블에 동의 감사 기록(Agreed Log)을 생성하거나 동의 시각(`agreed_at`)을 갱신합니다.

---

## 7. JWT 토큰 구조

### 7.1 Access Token (ATK)
- **용도**: API 호출 시 매번 보안 필터가 요구하는 만료 1시간짜리 서명 토큰.
- **Payload**:
  ```json
  {
    "sub": "12",
    "role": "MEMBER",
    "iat": 1700000000,
    "exp": 1700003600
  }
  ```

### 7.2 Refresh Token (RTK)
- **용도**: Access Token 만료 시 재발급을 요청하기 위한 만료 7일짜리 난수 UUID.
- **특징**: 데이터가 없는 무작위 문자열이며 DB `member_device` 테이블의 `refresh_token` 컬럼과 매핑하여 유효성을 검증합니다.

### 7.3 Merge Ticket
- **용도**: 게스트 상태에서 소셜 로그인 계정으로 데이터를 병합하기 위해 발급하는 10분 만료의 서명 토큰.
- **Payload**:
  ```json
  {
    "sub": "12",              // 게스트 멤버 ID
    "googleSubId": "12345...", // 연동될 구글 Sub ID
    "type": "merge"
  }
  ```

---

## 8. DB 테이블 구조

```
┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
│     member      │       │  member_auth    │       │member_agreements│
├─────────────────┤       ├─────────────────┤       ├─────────────────┤
│ id (PK)         │◄──────│ member_id       │       │ member_id (PK)  │
│ role            │       │ auth_type       │       │ status          │
│ name            │       │ auth_key        │       │ agreed_at       │
│ position        │       │ last_login_at   │       └─────────────────┘
│ department      │       └─────────────────┘
└─────────────────┘
        ▲
        │
┌─────────────────┐
│ member_device   │
├─────────────────┤
│ id (PK)         │
│ member_id (FK)  │
│ refresh_token   │
│ expires_at      │
└─────────────────┘
```

- 각 외래키 필드(`member_id`)는 JPA 엔티티상에서 관계 객체 대신 기본 `Long` 타입 필드로 소유하여 패키지/도메인 간의 물리적 테이블 참조 결합도를 완화합니다.

---

## 9. 인증 흐름별 상세 호출 경로

### 9.1 게스트 생성
```
POST /api/v1/auth/guest
 └→ GuestController.createGuest()
     └→ GuestService.authenticate()
         ├→ Member.builder().role(GUEST).name("게스트_xxxx").build() 저장
         ├→ MemberAuth.builder().authType(GUEST_KEY).authKey(UUID).build() 저장
         └→ AuthenticatedIdentity 반환
     └→ SessionService.createSession()
         ├→ TokenProvider.createAccessToken(memberId, "GUEST")
         ├→ TokenProvider.createRefreshToken()
         ├→ DeviceSessionService.upsert() (기기 세션 등록)
         └→ TokenDeliveryStrategy.deliver() (쿠키 세팅)
```

### 9.2 Google OAuth 로그인
```
POST /api/v1/auth/token
 └→ GoogleOAuthController.tokenExchange()
     ├→ OAuthStateService.consumeState(state) (Redis 검증 및 소비)
     ├→ GoogleOAuthService.authenticate(code)
     │   ├→ exchangeCodeForIdToken(code) (Google 토큰 API 호출)
     │   ├→ verifyAndParseIdToken(idToken) (JWKS 검증 및 Claim 파싱)
     │   └→ authenticateOrCreateMember()
     │       ├→ [기존] MemberAuth 조회 후 로그인 시각 갱신
     │       └→ [신규] Member(MEMBER) 및 MemberAuth(GOOGLE) 생성 저장 (newUser=true)
     └→ SessionService.createSession() (ATK/RTK 생성, 기기 등록 및 쿠키 세팅)
```

### 9.3 회원 탈퇴 (Account Withdrawal)
```
DELETE /api/v1/members/me
 └→ MemberController.withdraw()
     ├→ MemberService.withdraw(memberId)
     │   ├→ memberAgreementRepository.deleteById(memberId)
     │   ├→ memberDeviceRepository.deleteAllInBatch(memberDevices) (기기 세션 모두 파괴)
     │   ├→ memberAuthRepository.deleteByMemberId()
     │   └→ memberRepository.delete(member)
     └→ TokenDeliveryStrategy.clear(response) (HTTP 쿠키 완전히 삭제)
```

---

## 10. 토큰 전달 전략

쿠키 발행 옵션은 환경 프로파일별로 상이합니다.

- **Cookie 설정 공통 규칙**:
  - `access_token`: 1시간 만료, Path=/
  - `refresh_token`: 7일 만료, Path=/
  - `HttpOnly`: 스크립트 접근 불허 (XSS 방지)
  - `SameSite`: Lax

- **환경별 차이**:
  - **prod**: `Secure` 플래그 활성화(HTTPS 필수), 응답 바디나 헤더에 토큰 값을 추가 노출하지 않음.
  - **dev / test**: `Secure` 플래그 비활성화 가능(HTTP 개발 허용), `token-in-response=true` 설정을 통해 API 응답 바디 및 `Authorization` 헤더에 토큰 문자열을 병행 전달.

---

## 11. 핵심 타입 정의

### 11.1 AuthenticatedIdentity
- **위치**: `com/mjusugangsincheonghelper/auth/common/AuthenticatedIdentity.java`
- **역할**: 인증 레이어가 완료된 후 세션 레이어로 회원 ID를 안전하게 넘겨주기 위한 VO.

### 11.2 SessionResult
- **위치**: `com/mjusugangsincheonghelper/auth/session/SessionResult.java`
- **역할**: 발급된 세션의 토큰 상태 및 대상 사용자의 직책, 부서, 권한 정보를 전달하는 통합 결과 체인 객체.

---

## 12. ErrorCode 정의

### 12.1 주요 비즈니스 에러
| 에러코드 | HTTP 상태 | 코드 | 설명 |
|---|---|---|---|
| `AUTH_GOOGLE_AUTH_FAILED` | 401 | AUTH_002 | Google 서버 연동 실패 혹은 토큰 무효 |
| `AUTH_INVALID_REFRESH_TOKEN` | 401 | AUTH_004 | DB 세션 기한 만료 혹은 일치하지 않는 RTK |
| `AUTH_MERGE_TICKET_EXPIRED` | 400 | AUTH_006 | 데이터 병합용 일회성 JWT 만료 |
| `AUTH_MEMBER_NOT_FOUND` | 404 | AUTH_007 | 지정된 ID의 회원이 시스템 내 존재하지 않음 |
| `AUTH_NOT_MJU_DOMAIN` | 403 | AUTH_010 | Google ID Token의 hd 클레임이 `mju.ac.kr`이 아님 |
| `GLOBAL_SECURITY_UNAUTHORIZED_ACCESS` | 401 | GLOBAL_SECURITY_001 | 인증 헤더 혹은 쿠키가 유실된 무인증 상태 요청 |
| `GLOBAL_SECURITY_FORBIDDEN` | 403 | GLOBAL_SECURITY_002 | 요구하는 역할 등급(예: ROLE_MEMBER) 미달 |
