# Auth & Member Architecture

본 문서는 서비스의 인증(Auth), 인가(Authorization), 세션(Session) 및 회원(Member) 도메인의 아키텍처 설계 의도와 개념적 구조를 정의합니다.

---

## 1. 핵심 설계 원칙 및 책임 분리

인증 및 회원 시스템은 책임을 기준으로 크게 **Security**, **Auth**, **Member** 3대 영역으로 분리하여 설계합니다.

| 도메인 영역 | 패키지 위치 | 주 책임 | 핵심 역할 |
|:---|:---|:---|:---|
| **Security** (보안 필터) | `global.security` | 외부 요청의 일차적 관문 통제 및 신원 확립 | 쿠키/헤더에서 JWT를 추출하고 서명을 무상태(Stateless)로 검증하여 SecurityContext에 인증 정보 등록 |
| **Auth** (인증 메커니즘) | `auth/` | 인증 수단별 신원 검증 및 세션/토큰 관리 | 게스트 생성, Google OAuth 연동, 게스트 데이터 병합, ATK/RTK 발급/회수 및 디바이스 세션 관리 |
| **Member** (회원 리소스) | `member/` | 회원 프로필 데이터 및 부가 상태 관리 | 본인 프로필 조회, 개인정보 동의 감사 기록(Consent Log) 생성/갱신, 회원 탈퇴(Withdrawal) 시 데이터 일괄 정리 |

---

## 2. 도메인별 세부 구조 및 책임

### 2.1 Security (보안 및 필터)
- **JwtAuthenticationFilter**: 모든 인증이 필요한 요청에 대해 JWT 서명과 유효성을 검증합니다. DB 조회를 일절 배제하여 무상태성을 보장합니다.
- **TokenExtractor**: 환경별(개발/운영) 토큰 추출 전략을 캡슐화합니다.
- **GlobalSecurityConfig**: CORS, CSRF, URL 접근 권한 규칙 및 역할 계층(Role Hierarchy)을 설정합니다.

### 2.2 Auth (인증 기능 분리 - Feature-driven)
- **guest**: 서버측에서 고유 임의 키를 발급하여 임시 게스트 회원 세션을 형성합니다.
- **oauth**: Google 제공자로부터 ID Token을 발급받아 명지대 도메인(`mju.ac.kr`)을 검증하고 멤버 신원을 확립합니다.
- **merge**: 게스트 이용 데이터(디바이스 세션 등)를 구글 계정 신원으로 안전하게 이관하고 기존 게스트 데이터를 제거합니다.
- **session**: JWT 토큰 발급 및 파싱(`TokenProvider`), Redis/RDB 기반 디바이스 세션 관리 및 로그인 상태 회수를 총괄합니다.

### 2.3 Member (회원 데이터 및 생명주기)
- **profile**: 회원 본인의 정보(`me`) 조회 및 회원 탈퇴(`withdraw`)를 처리합니다.
- **consent**: 규제 준수 조항에 따른 개인정보 제공 동의서 감사 로그를 기록하고 보관합니다.

---

## 3. 시퀀스 흐름

### 3.1 Google OAuth 로그인 및 가입
```
[1] GET /auth/config/google  -> Google Client ID 및 Scope 조회
[2] POST /auth/oauth/start   -> CSRF 방지용 state 생성 및 Google 로그인 URL 반환
[3] POST /auth/token         -> Authorization Code로 ID Token 교환 및 검증
                                신규 회원인 경우(newUser: true) Member 및 MemberAuth 신설
                                ATK/RTK 쿠키 발급 및 디바이스 세션 등록
```

### 3.2 개인정보 동의서 작성
```
[1] 신규 가입(newUser: true) 시 프론트엔드가 개인정보 동의 안내 UI 노출
[2] 사용자가 동의 완료 시 POST /auth/privacy/agree 호출
[3] MemberAgreementService를 통해 member_agreements 테이블에 감사 기록(status=true, agreedAt=now) 저장
```

### 3.3 게스트 → 구글 계정 데이터 병합
```
[1] 게스트 로그인 상태에서 Google 로그인 시도
[2] 병합 일회성 티켓(Merge Ticket) 발급
[3] POST /auth/login/google/merge {mergeTicket, device, fcmToken} 호출
[4] 게스트 MemberAuth 제거, 디바이스 세션의 소유권을 구글 Member로 이전 후 게스트 Member 레코드 제거
```

### 3.4 회원 탈퇴 (Account Withdrawal)
```
[1] DELETE /members/me 호출
[2] MemberService.withdraw() 실행
    - member_agreements 삭제
    - member_device 삭제 (모든 로그인 기기 세션 만료)
    - member_auth 삭제 (소셜 연동 해제)
    - member 레코드 영구 삭제
[3] TokenDeliveryStrategy.clear() 호출을 통한 브라우저 쿠키(access_token, refresh_token) 일괄 삭제
```

---

## 4. 패키지 아키텍처

```
src/main/java/com/mjusugangsincheonghelper/
│
├── global/
│   └── security/                             # 보안 인프라 영역 (Security)
│       ├── GlobalSecurityConfig.java         # 보안 필터 체인 및 CORS 설정
│       ├── filter/
│       │   └── JwtAuthenticationFilter.java  # 무상태 JWT 인증 필터
│       └── token/
│           ├── TokenExtractor.java           # 토큰 추출 인터페이스
│           ├── BearerTokenExtractor.java     # 개발/테스트용 헤더+쿠키 추출
│           └── CookieTokenExtractor.java     # 운영용 쿠키 추출
│
├── member/                                   # 회원 영역 (Member)
│   ├── controller/
│   │   ├── MemberController.java             # GET /members/me, DELETE /members/me
│   │   └── MemberAgreementController.java    # POST /auth/privacy/agree (하위 호환성 유지)
│   ├── service/
│   │   ├── MemberService.java                # 프로필 조회 및 회원 탈퇴 오케스트레이션
│   │   └── MemberAgreementService.java       # 개인정보 동의 감사 기록 관리
│   └── dto/
│       ├── MemberMeResponse.java
│       └── PrivacyAgreementResponse.java
│
└── auth/                                     # 인증 및 세션 메커니즘 영역 (Auth)
    ├── common/
    │   ├── AuthenticatedIdentity.java        # 신원 정보 VO
    │   └── dto/
    │       └── DeviceInfo.java               # 디바이스 정보 공통 DTO
    │
    ├── guest/                                # 게스트 로그인 피처
    │   ├── GuestController.java              # POST /auth/guest
    │   ├── GuestService.java                 # 게스트 임시 계정 및 인증 키 발급
    │   └── dto/
    │       ├── GuestCreateRequest.java
    │       └── GuestResponse.java
    │
    ├── oauth/                                # 구글 로그인 피처
    │   ├── GoogleOAuthController.java        # config/google, oauth/start, token
    │   ├── GoogleOAuthService.java           # Google ID Token 검증 및 회원 조회/가입
    │   ├── OAuthStateService.java            # state 검증 (Redis)
    │   ├── OAuthAuthenticationResult.java
    │   └── dto/                              # Google OAuth 전용 DTO
    │
    ├── merge/                                # 계정 데이터 병합 피처
    │   ├── MergeController.java              # POST /login/google/merge
    │   ├── MergeService.java                 # 게스트 -> 멤버 데이터 이관
    │   ├── MergeTicketService.java           # 일회성 병합 티켓(JWT) 발행/소비
    │   └── dto/
    │       ├── MergeRequest.java
    │       └── MergeResponse.java
    │
    └── session/                              # 토큰 및 기기 세션 관리 피처
        ├── SessionController.java            # POST /auth/refresh, POST /auth/logout
        ├── SessionService.java               # 세션 생성, 갱신, 파괴
        ├── device/
        │   └── DeviceSessionService.java     # member_device 데이터 핸들링
        ├── token/
        │   └── TokenProvider.java            # JWT 토큰 생성, 검증 및 파싱
        ├── delivery/
        │   ├── TokenDeliveryStrategy.java    # 토큰 전달 전략 인터페이스
        │   ├── CookieTokenDelivery.java      # 운영용 쿠키 발급
        │   └── HeaderTokenDelivery.java      # 개발용 쿠키 + 헤더 노출
        └── dto/
            ├── LogoutRequest.java
            └── RefreshResponse.java
```

---

## 5. 토큰 전달 전략 (Token Delivery Strategy)

기본 토큰 인증 수단은 모든 환경에서 HttpOnly 쿠키(`access_token`, `refresh_token`)를 활용하여 XSS 공격을 방어합니다.

| 환경 | ATK 전달 방식 | RTK 전달 방식 | 추가 노출 필드 (dev/test 한정) |
|:---|:---|:---|:---|
| **dev / test** | HttpOnly `access_token` 쿠키 | HttpOnly `refresh_token` 쿠키 | Response Body (`accessToken`/`refreshToken`), `Authorization` 헤더, `X-Access-Token` 헤더 |
| **prod** | HttpOnly Secure `access_token` 쿠키 | HttpOnly Secure `refresh_token` 쿠키 | 없음 |
