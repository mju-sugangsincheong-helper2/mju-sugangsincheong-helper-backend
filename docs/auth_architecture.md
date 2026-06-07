# Auth Architecture

본 문서는 auth 도메인의 아키텍처 설계 의도와 책임 분리를 설명합니다.

---

## 1. 핵심 설계 원칙

Auth 시스템은 시퀀스 기준으로 네 가지 책임으로 분리됩니다.

| 단계 | 레이어 | 책임 | 질문 |
|------|--------|------|------|
| 1 | **OAuth** (외부 인증 연동) | Google 등 외부 제공자와의 OAuth 흐름 관리 | "외부에서 어떻게 인증 정보를 가져올까?" |
| 2 | **Authentication** (본인인증) | 사용자 신원 확인 및 identity 확립, JWT 생성/검증 | "이 사람이 누구인가?" |
| 3 | **Session** (통행권) | ATK/RTK 발급, 갱신, 회수 및 디바이스 세션 관리 | "통행권을 주고/갱신하고/회수할까?" |
| 4 | **Authorization** (권한) | 역할 기반 접근 제어 + 개인정보 동의 체크 | "이 통행권으로 무엇을 할 수 있는가?" |

---

## 2. 시퀀스 흐름

```
[1] OAuth 인증 (oauth/)
    └→ code → Google 토큰 교환 → ID Token JWKS 검증 → 회원 조회/생성
    └→ AuthenticatedIdentity(memberId)

[2] JWT 발급/검증 (authentication/token/)
    └→ TokenProvider: JWT 생성 (ATK/RTK)
    └→ JwtAuthenticationFilter: 요청 시 ATK 검증 → SecurityContext

[3] 통행권 관리 (session/)
    └→ AuthenticatedIdentity를 받아서
    └→ TokenProvider로 JWT 생성
    └→ DB에 저장 (device session)
    └→ 토큰 전달 (HttpOnly cookie + dev/test 보조 body/header)

[4] 권한 확인 (authorization/)
    ├→ PrivacyConsentFilter: 개인정보 동의 여부 체크 (MEMBER/ADMIN 대상)
    └→ @PreAuthorize + RoleHierarchy: 역할 기반 접근 제어
         └→ ADMIN > MEMBER > GUEST 계층 구조
```

---

## 3. 패키지 구조

```
auth/
├── oauth/                                    # 1. OAuth 인증 (외부 신원 확인)
│   ├── GoogleAuthProvider.java               #    code → ID Token 검증 → 회원 조회/생성
│   ├── OAuthStateService.java                #    state 생성/검증 (Redis 5분 TTL)
│   └── dto/
│       ├── OAuthConfigResponse.java          #    GET /auth/config/google 응답
│       ├── OAuthStartResponse.java           #    POST /auth/oauth/start 응답
│       ├── OAuthTokenRequest.java            #    POST /auth/token 요청
│       └── OAuthTokenResponse.java           #    POST /auth/token 응답
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
├── authorization/                            # 4. 권한 확인
│   └── consent/
│       ├── PrivacyConsentFilter.java         #    개인정보 동의 필터 (JWT 이후 실행)
│       └── MemberAgreementService.java       #    동의 여부 조회/처리
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

## 4. 인증 흐름별 호출 경로

### 4.1 게스트 생성

```
AuthController.createGuest()
  └→ GuestAuthenticationProvider.authenticate()     # member(GUEST) + member_auth 생성
       └→ AuthenticatedIdentity(memberId)
  └→ SessionService.createSession(identity, ...)    # ATK/RTK 발급 + 디바이스 저장
       ├→ TokenProvider.createAccessToken()
       ├→ TokenProvider.createRefreshToken()
       ├→ DeviceSessionService.upsert()
       └→ TokenDeliveryStrategy.deliver()           # 쿠키 발급, dev/test는 헤더도 추가
```

### 4.2 Google OAuth 로그인

```
OAuthController.oauthStart()
  └→ OAuthStateService.createState()                # state 생성, Redis 5분 TTL 저장
  └→ Google Auth URL 생성                           # clientId, redirectUri, state, scope, hd

[프론트엔드가 Google로 리다이렉트]
[Google이 프론트로 code + state와 함께 리다이렉트]

OAuthController.tokenExchange()
  └→ OAuthStateService.consumeState(state)          # state 검증 + 소비
  └→ GoogleAuthProvider.authenticate(code)
  │   ├→ RestClient로 POST /token (code ↔ id_token 교환)
  │   ├→ ID Token JWKS 서명 검증 (RSA256)
  │   ├→ hd=mju.ac.kr 검증
  │   ├→ name 파싱 (name/position/department)
  │   └→ member 조회/생성 → AuthenticatedIdentity
  └→ SessionService.createSession()                 # ATK/RTK 발급
       └→ TokenDeliveryStrategy.deliver()
```

### 4.3 게스트 → 멤버 병합

```
AuthController.merge()
  └→ MergeService.merge(mergeTicket)                # 병합 티켓 소비 + 데이터 이관
       ├→ MergeTicketService.consume()              # JWT 파싱 → guestMemberId, googleSubId
       ├→ guest member_auth 삭제
       ├→ DeviceSessionService.switchMember()       # 디바이스 소유권 이전
       ├→ guest member 삭제
       └→ AuthenticatedIdentity(targetMemberId)
  └→ SessionService.createSession(identity, ...)    # 새 세션 발급
```

### 4.4 토큰 재발급

```
AuthController.refreshToken()
  └→ SessionService.refreshSession(rtk, ...)
       ├→ MemberDevice 조회 (RTK 기준)
       ├→ 만료 검증
       ├→ TokenProvider.createAccessToken()         # 새 ATK
       ├→ TokenProvider.createRefreshToken()         # 새 RTK (rotation)
       └→ TokenDeliveryStrategy.deliver()
```

### 4.5 로그아웃

```
AuthController.logout()
  └→ SessionService.destroySession(rtk, fcmToken, memberId, ...)
       ├→ DeviceSessionService.deleteByFcmToken()
       └→ TokenDeliveryStrategy.clear()             # ATK/RTK 쿠키 삭제, dev/test는 헤더도 초기화
```

---

## 5. 토큰 전달 전략

기본 인증 수단은 모든 환경에서 `access_token`, `refresh_token` HttpOnly 쿠키입니다.
`dev/test`는 Swagger와 수동 테스트 편의를 위해 같은 토큰을 응답 body와 header에도 추가로 노출합니다.

| 환경 | ATK 전달 | RTK 전달 | 추가 노출 | Swagger 스킴 |
|------|---------|---------|-----------|---------------|
| **dev/test** | HttpOnly `access_token` 쿠키 | HttpOnly `refresh_token` 쿠키 | body `accessToken`/`refreshToken`, `Authorization`, `X-Access-Token`, `X-Refresh-Token` 헤더 | `bearerAuth` + 쿠키 인증 가능 |
| **prod** | HttpOnly Secure `access_token` 쿠키 | HttpOnly Secure `refresh_token` 쿠키 | 없음 | `cookieAuth` |

### 구현 메커니즘

- `TokenDeliveryStrategy` 인터페이스의 `@Profile` 기반 구현체 전환
- DTO의 토큰 필드에 `@JsonInclude(JsonInclude.Include.NON_NULL)` 적용
- Controller가 `app.auth.token-in-response=true`일 때만 DTO에 토큰 값 주입
- dev/test의 `HeaderTokenDelivery`는 쿠키를 발급하면서 테스트용 헤더도 세팅
- prod의 `CookieTokenDelivery`는 Secure HttpOnly 쿠키만 세팅

```java
// dev/test 응답 body 예시
{
  "meta": { ... },
  "data": {
    "memberId": 123,
    "role": "GUEST",
    "name": "게스트_a8f3",
    "accessToken": "eyJ...",
    "refreshToken": "uuid-string"
  }
}
```

```http
Set-Cookie: access_token=...; Path=/; HttpOnly; SameSite=Lax
Set-Cookie: refresh_token=...; Path=/; HttpOnly; SameSite=Lax
Authorization: Bearer eyJ...
X-Access-Token: eyJ...
X-Refresh-Token: uuid-string
```

---

## 6. 핵심 타입

### AuthenticatedIdentity

Authentication 레이어의 출력. Session 레이어의 입력.

```java
public class AuthenticatedIdentity {
    private final Long memberId;  // 항상 non-null
}
```

- 게스트 생성: 새 member 저장 후 ID 반환
- Google 로그인: 기존 member 조회 후 ID 반환
- 병합: target member ID 반환

### SessionResult

Session 레이어의 출력. Controller가 DTO로 변환.

```java
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

### TokenDeliveryStrategy

프로파일 기반 토큰 전달 전략.

```java
public interface TokenDeliveryStrategy {
    void deliver(String accessToken, String refreshToken, HttpServletResponse response);
    void clear(HttpServletResponse response);
}
```

---

## 7. DB 테이블과 레이어 매핑

| 테이블 | 관련 레이어 | 담당 클래스 |
|--------|-----------|------------|
| `member` | oauth, authentication, session | `GoogleAuthProvider`, `GuestAuthenticationProvider`, `SessionService` |
| `member_auth` | oauth, authentication | `GoogleAuthProvider`, `GuestAuthenticationProvider`, `MergeService` |
| `member_device` | session | `DeviceSessionService`, `SessionService` |
| `member_agreements` | authorization | `MemberAgreementService`, `PrivacyConsentFilter` |

---

## 8. 권한 모델 (Authorization)

### 8.1 역할 계층 (RoleHierarchy)

```
ROLE_ADMIN > ROLE_MEMBER > ROLE_GUEST
```

- `@PreAuthorize("hasRole('MEMBER')")` → MEMBER, ADMIN 모두 접근 가능
- `@PreAuthorize("hasRole('ADMIN')")` → ADMIN만 접근 가능

### 8.2 개인정보 동의 체크 (PrivacyConsentFilter)

JWT 인증 이후 실행되는 필터. MEMBER/ADMIN 역할 사용자의 개인정보 동의를 전역 체크.

| 조건 | 결과 |
|------|------|
| 미인증 | 통과 (SecurityConfig가 처리) |
| GUEST | 통과 (동의 대상 아님) |
| MEMBER/ADMIN + 동의 완료 | 통과 |
| MEMBER/ADMIN + 미동의 | 403 `AUTH_PRIVACY_POLICY_REQUIRED` |

### 8.3 member_agreements 테이블

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `member_id` (PK) | Long | member.id와 1:1 공유 PK |
| `status` | boolean | 동의 여부 |
| `agreed_at` | Instant | 동의 시각 (증빙) |

### 8.4 사용 예시

```java
@RestController
@RequestMapping("/api/{version}/members")
public class MemberController {

    @GetMapping("/me")
    public MemberMeResponse getMe() { ... }  // 인증만 필요 (Guest+)

    @PreAuthorize("hasRole('MEMBER')")
    @GetMapping("/courses")
    public List<CourseResponse> getCourses() { ... }  // MEMBER+ 필요

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/system/config")
    public void updateConfig() { ... }  // ADMIN만
}
```

---

## 9. Google OAuth 설정

### Google Console 등록 (승인된 리디렉션 URI)

```
https://myapp.com/auth/callback        # 실제 프론트엔드
http://localhost:3000/auth/callback    # 개발용 프론트엔드
```

### 환경별 redirect-uri 설정

| 환경 | application-*.yml | 용도 |
|------|-------------------|------|
| **dev** | `http://localhost:3000/auth/callback` | 로컬 개발 (프론트 연동) |
| **prod** | `https://myapp.com/auth/callback` | 운영 환경 |

---

## 10. Swagger에서 OAuth 테스트

현재 설계(프론트 경유형)에서는 Swagger의 Authorize 버튼으로 직접 OAuth를 처리할 수 없습니다.

### 수동 테스트 순서

1. `GET /api/v1/auth/config/google` → clientId 확인
2. `POST /api/v1/auth/oauth/start` → googleAuthUrl 받기
3. 브라우저에서 googleAuthUrl 열기 → Google 로그인
4. 리다이렉트 URL에서 code, state 복사
5. `POST /api/v1/auth/token` {code, state} → accessToken 받기
6. Swagger Authorize → Bearer Auth에 accessToken 입력
