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
| 4 | **Authorization** (권한) | 역할 기반 접근 제어 (향후 확장) | "이 통행권으로 무엇을 할 수 있는가?" |

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
    └→ 토큰 전달 (cookie/body)

[4] 권한 확인 (authorization/) - 향후 확장
    └→ SecurityContext의 role 확인
    └→ 접근 권한 체크
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
│       ├── BearerTokenExtractor.java         #    Authorization: Bearer (dev/test)
│       ├── CookieTokenExtractor.java         #    access_token 쿠키 (prod)
│       └── JwtAuthenticationFilter.java      #    ATK 검증 → SecurityContext
│
├── session/                                  # 3. 통행권 관리 (JWT 저장/갱신/회수)
│   ├── SessionService.java                   #    세션 오케스트레이션
│   ├── SessionResult.java                    #    세션 생성 결과 VO
│   ├── delivery/
│   │   ├── TokenDeliveryStrategy.java        #    토큰 전달 전략 인터페이스
│   │   ├── CookieTokenDelivery.java          #    HttpOnly 쿠키 (prod)
│   │   └── HeaderTokenDelivery.java          #    no-op (dev/test)
│   └── device/
│       └── DeviceSessionService.java         #    디바이스 세션 CRUD
│
├── authorization/                            # 4. 권한 확인 (향후 확장)
│   └── (Role 기반 권한 체크 등)
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
       └→ TokenDeliveryStrategy.deliver()           # prod: 쿠키, dev: no-op
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
       └→ TokenDeliveryStrategy.clear()             # prod: 쿠키 삭제, dev: no-op
```

---

## 5. 토큰 전달 전략 (dev vs prod)

| 환경 | ATK 전달 | RTK 전달 | Swagger 스킴 |
|------|---------|---------|---------------|
| **dev** | 응답 body `accessToken` 필드 | 응답 body `refreshToken` 필드 | `bearerAuth` (Authorization 헤더) |
| **prod** | HttpOnly Secure 쿠키 | HttpOnly Secure 쿠키 | `cookieAuth` (APIKEY 쿠키) |

### 구현 메커니즘

- `TokenDeliveryStrategy` 인터페이스의 `@Profile` 기반 구현체 전환
- DTO의 토큰 필드에 `@JsonInclude(JsonInclude.Include.NON_NULL)` 적용
- Controller가 profile을 확인하여 dev일 때만 DTO에 토큰 값 주입

```java
// dev 응답 body 예시
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

---

## 8. Google OAuth 설정

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

## 9. Swagger에서 OAuth 테스트

현재 설계(프론트 경유형)에서는 Swagger의 Authorize 버튼으로 직접 OAuth를 처리할 수 없습니다.

### 수동 테스트 순서

1. `GET /api/v1/auth/config/google` → clientId 확인
2. `POST /api/v1/auth/oauth/start` → googleAuthUrl 받기
3. 브라우저에서 googleAuthUrl 열기 → Google 로그인
4. 리다이렉트 URL에서 code, state 복사
5. `POST /api/v1/auth/token` {code, state} → accessToken 받기
6. Swagger Authorize → Bearer Auth에 accessToken 입력
