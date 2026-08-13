# Auth & Security Architecture v3

본 문서는 FID(Firebase Installation ID) 기반의 기기 식별 아키텍처로 재설계한 인증 시스템을 설명합니다.

---

## 목차

1. [네이밍 체계](#1-네이밍-체계)
2. [설계 원칙](#2-설계-원칙)
3. [Firebase Identity 3계층](#3-firebase-identity-3계층)
4. [OAuth 토큰 흐름 정리](#4-oauth-토큰-흐름-정리)
5. [전체 인증 아키텍처](#5-전체-인증-아키텍처)
6. [DB 스키마](#6-db-스키마)
7. [기기 식별 메커니즘](#7-기기-식별-메커니즘)
8. [시퀀스 흐름](#8-시퀀스-흐름)
9. [마이그레이션 계획](#9-마이그레이션-계획)

---

## 1. 네이밍 체계

### 1.1 접두사/접미사 규칙

| 구분 | 접두사 | 의미 |
|------|--------|------|
| **우리 서비스** | `session` | 우리 서비스가 발급하는 세션 토큰 |
| **Google** | `google` | Google이 발급하는 OAuth 토큰 |
| **Firebase** | `firebase` | Firebase가 발급하는 토큰/식별자 |

| 접미사 | 의미 |
|--------|------|
| `Token` | 인증/인가에 사용되는 토큰 |
| `Code` | 일회용 교환 코드 |
| `Id` | 고유 식별자 |
| `State` | CSRF 방어용 일회성 난수 |
| `Ticket` | 일회용 작업 티켓 |

### 1.2 전체 토큰/식별자 목록

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         전체 네이밍 체계                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  session*  ── 우리 서비스가 발급                                             │
│  ─────────────────────────────────────────────────────────────────────      │
│  sessionAccessToken   (JWT, 1h)     API 요청 인증                          │
│  sessionRefreshToken  (UUID, 7d)    ATK 재발급                             │
│  mergeTicket          (JWT, 5m)     게스트→멤버 병합 (일회용)              │
│                                                                             │
│  google*  ── Google이 발급                                                  │
│  ─────────────────────────────────────────────────────────────────────      │
│  googleAuthCode       (일회용)       ID Token 교환용                        │
│  googleIdToken        (JWT)          사용자 신원 증명                       │
│  googleState          (UUID, 5m)     CSRF 방어 (Redis)                     │
│                                                                             │
│  firebase*  ── Firebase가 발급                                              │
│  ─────────────────────────────────────────────────────────────────────      │
│  firebaseInstallationId             (FID)         기기 식별                │
│  firebaseCloudMessagingRegistrationToken (FCM)    푸시 발송 주소           │
│                                                                             │
│  (참고: Firebase Installation Auth Token은 SDK 내부에서만 사용,             │
│         우리는 직접 사용하지 않으므로 네이밍 체계에 포함하지 않음)           │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.3 변경 전후 비교

| 기존 이름 | 새 이름 | 비고 |
|-----------|---------|------|
| `accessToken` | `sessionAccessToken` | Google과 구분 |
| `refreshToken` | `sessionRefreshToken` | 세션 갱신용 명시 |
| `fcmToken` | `firebaseCloudMessagingRegistrationToken` | 공식 명칭 사용 |
| `fid` | `firebaseInstallationId` | 풀네임 사용 |
| `state` (OAuth) | `googleState` | Google OAuth 전용 명시 |
| `mergeTicket` | `mergeTicket` | 유지 (이미 명확) |

### 1.4 DB/코드 변경 범위

```
DB 컬럼:
  member_device.fcm_token        → member_device.firebase_cloud_messaging_registration_token
  member_device.fid              → member_device.firebase_installation_id

Java 필드:
  DeviceInfo.fcmToken            → DeviceInfo.firebaseCloudMessagingRegistrationToken
  DeviceInfo.fid                 → DeviceInfo.firebaseInstallationId
  MemberDevice.fcmToken          → MemberDevice.firebaseCloudMessagingRegistrationToken
  SessionResult.accessToken      → SessionResult.sessionAccessToken
  SessionResult.refreshToken     → SessionResult.sessionRefreshToken

쿠키 이름:
  access_token                   → session_access_token
  refresh_token                  → session_refresh_token

Redis 키:
  oauth:state:{uuid}:session     → google:state:{uuid}
```

---

## 2. 설계 원칙

### 2.1 단순성 (Simplicity)

| 원칙 | 설명 |
|------|------|
| **단일 식별자** | 기기 식별은 `firebaseInstallationId` 하나만 사용. UA, `firebaseCloudMessagingRegistrationToken` 등 다른 요소로 기기 매칭하지 않음 |
| **명확한 책임 분리** | 식별(`firebaseInstallationId`), 인증(Installation Auth Token), 푸시(`firebaseCloudMessagingRegistrationToken`) 각 기능은 독립적 |
| **폴백 없음** | `firebaseInstallationId`가 없으면 새 기기로 간주. UA 기반 폴백 제거 |

### 2.2 Firebase Identity 3계층 명확 구분

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Firebase Identity Architecture                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ 1️⃣ firebaseInstallationId (FID)                                    │   │
│  │    ─────────────────────────────────────────────────────────────    │   │
│  │    용도:   기기 식별 (Device Fingerprint)                           │   │
│  │    발급:   Firebase 앱 초기화만으로 가능 (알림 권한 불필요)         │   │
│  │    저장:   IndexedDB (Web) / 내부 저장소 (Mobile)                   │   │
│  │    유지:   앱 삭제/재설치 또는 브라우저 데이터 초기화 전까지        │   │
│  │    특징:   브라우저 업데이트해도 변경되지 않음                      │   │
│  │    API:    getInstallations(app) → getId()                          │   │
│  │    활용:   member_device.firebase_installation_id                   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ 2️⃣ firebaseCloudMessagingRegistrationToken                         │   │
│  │    ─────────────────────────────────────────────────────────────    │   │
│  │    용도:   푸시 알림 발송 주소                                      │   │
│  │    발급:   알림 권한(Notification.permission='granted') 필요        │   │
│  │    저장:   로컬 스토리지 + 서버 DB                                  │   │
│  │    유지:   주기적으로 갱신됨 (수명 제한 있음)                       │   │
│  │    API:    getMessaging() → getToken()                              │   │
│  │    활용:   member_device.firebase_cloud_messaging_registration_token│   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ 3️⃣ Firebase Installation Auth Token                                │   │
│  │    ─────────────────────────────────────────────────────────────    │   │
│  │    용도:   Firebase 서버 통신 인증 (내부용)                         │   │
│  │    발급:   SDK가 자동으로 처리                                      │   │
│  │    활용:   우리가 직접 사용하지 않음 (SDK 내부 처리)                │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.3 3가지 토큰의 관계

```
                    ┌─────────────────────┐
                    │  Firebase 앱 초기화  │
                    └──────────┬──────────┘
                               │
                    ┌──────────▼──────────┐
                    │ firebaseInstallation│ ◄── 알림 권한 불필요
                    │ Id 발급             │     항상 가능
                    └──────────┬──────────┘
                               │
              ┌────────────────┴────────────────┐
              │                                 │
   ┌──────────▼──────────┐           ┌──────────▼──────────┐
   │ Installation Auth   │           │ firebaseCloudMes-   │
   │ Token (내부 인증)   │           │ sagingRegistration  │
   │ SDK 자동 처리       │           │ Token 발급          │
   │ 우리가 사용 안 함   │           │ 알림 권한 필요      │
   └─────────────────────┘           └─────────────────────┘
```

---

## 3. Firebase Identity 3계층

### 3.1 firebaseInstallationId - 기기 식별

| 항목 | 설명 |
|------|------|
| **본질** | Firebase 앱 설치 건당 고유 식별자 |
| **형식** | 영문 대소문자 + 숫자 + 하이픈 (예: `fGk8Xm2pQr5...`) |
| **발급 시점** | `getInstallations(app)` 최초 호출 시 |
| **재발급 조건** | 앱 삭제 후 재설치, 브라우저 데이터 전체 삭제, `deleteInstallations()` 명시 호출 |
| **브라우저 업데이트** | 영향 없음 (UA와 무관) |
| **시크릿 모드** | 시크릿 모드 종료 시 소실 → 새 FID 발급 |
| **PWA** | PWA 설치 시 고유 FID, 삭제 시 소실 |

**firebaseInstallationId가 변경되는 경우:**
```
✅ 유지되는 경우:
   - 브라우저 업데이트 (Chrome 150 → 151)
   - OS 업데이트
   - 일반 브라우저 재시작
   - 탭 닫고 다시 열기

❌ 변경되는 경우:
   - 브라우저 데이터 전체 삭제 (쿠키, 로컬스토리지 등)
   - PWA 삭제 후 재설치
   - 시크릿 모드 사용 후 종료
   - 다른 브라우저 사용
   - 다른 기기 사용
```

### 3.2 firebaseCloudMessagingRegistrationToken - 푸시 주소

| 항목 | 설명 |
|------|------|
| **본질** | FCM v1 API가 푸시를 보낼 대상 주소 |
| **발급 조건** | 알림 권한 허용 + `getToken()` 호출 |
| **갱신** | 주기적으로 자동 갱신됨 (SDK가 `onTokenRefresh` 이벤트 발생) |
| **유효성** | 만료 가능, 갱신 시 기존 토큰 무효화 |

### 3.3 Firebase Installation Auth Token - 내부 인증 (미사용)

| 항목 | 설명 |
|------|------|
| **본질** | `firebaseInstallationId`를 기반으로 Firebase 서버와 통신할 때 사용하는 임시 인증 토큰 |
| **발급** | SDK가 자동으로 관리 |
| **만료** | 주기적으로 만료되어 자동 갱신 |
| **우리의 역할** | 없음. SDK가 내부적으로 처리하므로 우리가 직접 사용할 일 없음 |

---

## 4. OAuth 토큰 흐름 정리

### 4.1 OAuth 관련 토큰 종류와 역할

OAuth 흐름에는 여러 토큰이 등장합니다. 각각의 역할과 수명을 명확히 이해해야 합니다.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         OAuth 토큰 흐름 전체도                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  [google* 토큰들] ──────────────────────────────────────────────────────   │
│                                                                             │
│  ┌─────────────────┐     ┌─────────────────┐                               │
│  │ googleAuthCode  │     │ googleIdToken   │                               │
│  │                 │────▶│                 │                               │
│  │ - 일회용 코드   │     │ - 서명된 신원   │                               │
│  │ - 10분 유효     │     │ - 사용자 정보   │                               │
│  │ - ID Token 교환 │     │ - hd: mju.ac.kr │                               │
│  └─────────────────┘     └─────────────────┘                               │
│         │                       │                                           │
│         ▼                       ▼                                           │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                         우리 서비스 처리                            │   │
│  │                                                                     │   │
│  │  1. googleAuthCode → Google token endpoint → googleIdToken 획득     │   │
│  │  2. googleIdToken 서명 검증 (JWKS)                                  │   │
│  │  3. hd 클레임에서 mju.ac.kr 도메인 검증                            │   │
│  │  4. sub 추출 → MemberAuth.auth_key로 회원 매칭                     │   │
│  │  5. 회원 확인/생성 후 → session 토큰 발급                          │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  [session* 토큰들] ─────────────────────────────────────────────────────   │
│                                                                             │
│  ┌─────────────────┐     ┌─────────────────┐     ┌─────────────────────┐   │
│  │ sessionAccess   │     │ sessionRefresh  │     │ mergeTicket         │   │
│  │ Token           │     │ Token           │     │                     │   │
│  │                 │     │                 │     │                     │   │
│  │ - JWT 서명      │     │ - UUID 난수     │     │ - 일회용 JWT        │   │
│  │ - 1시간 유효    │     │ - 7일 유효      │     │ - 5분 유효         │   │
│  │ - API 인증용    │     │ - ATK 재발급용  │     │ - 게스트→멤버 병합  │   │
│  │ - deviceId 포함 │     │ - DB에 해시 저장│     │                     │   │
│  └─────────────────┘     └─────────────────┘     └─────────────────────┘   │
│                                                                             │
│  [기타]                                                                     │
│  ┌─────────────────┐                                                        │
│  │ googleState     │  CSRF 방어용 일회성 난수 (Redis, 5분 TTL)             │
│  └─────────────────┘                                                        │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.2 토큰별 상세 설명

#### google* 토큰 (Google이 발급)

| 토큰 | 용도 | 수명 | 우리 서비스에서의 역할 |
|------|------|------|----------------------|
| **googleAuthCode** | googleIdToken 교환용 | ~10분 | code → idToken 교환 후 폐기 |
| **googleIdToken** | 사용자 신원 증명 | ~1시간 (JWT) | 서명 검증 → 도메인 검증 → 회원 매칭 |
| **googleState** | CSRF 방어 | 5분 (Redis) | OAuth 요청/응답 매칭, 일회용 |

#### session* 토큰 (우리 서비스가 발급)

| 토큰 | 용도 | 수명 | 저장 위치 |
|------|------|------|----------|
| **sessionAccessToken** | API 요청 인증 | 1시간 | 쿠키 (HttpOnly) |
| **sessionRefreshToken** | ATK 재발급 | 7일 | 쿠키 (HttpOnly) + DB (해시) |
| **mergeTicket** | 게스트→멤버 데이터 병합 | 5분 | 일회용, 사용 후 폐기 |

### 4.3 OAuth 흐름에서 토큰 수명 주기

```
Browser                    Google                     Backend
   │                         │                           │
   │  [1] OAuth 시작         │                           │
   │────────────────────────────────────────────────────▶│
   │                         │                           │ googleState 생성 (Redis, 5분)
   │◀────────────────────────────────────────────────────│
   │                         │                           │
   │  [2] Google 인증        │                           │
   │────────────────────────▶│                           │
   │                         │                           │
   │◀────────────────────────│                           │
   │   googleAuthCode        │                           │
   │   + googleState         │                           │
   │                         │                           │
   │  [3] 토큰 교환          │                           │
   │────────────────────────────────────────────────────▶│
   │                         │                           │
   │                         │◀──────────────────────────│
   │                         │   POST /token             │
   │                         │   {googleAuthCode, ...}   │
   │                         │                           │
   │                         │──────────────────────────▶│
   │                         │   googleIdToken (JWT)     │
   │                         │                           │
   │                         │                           │ [4] googleIdToken 검증
   │                         │                           │ - JWKS 서명 검증
   │                         │                           │ - hd: mju.ac.kr 확인
   │                         │                           │ - sub 추출
   │                         │                           │
   │                         │                           │ [5] googleState 검증
   │                         │                           │ - Redis에서 조회
   │                         │                           │ - 일치하면 소비 (삭제)
   │                         │                           │
   │                         │                           │ [6] 회원 매칭/생성
   │                         │                           │ - MemberAuth(GOOGLE, sub) 조회
   │                         │                           │ - 있으면: 기존 회원
   │                         │                           │ - 없으면: 신규 생성
   │                         │                           │
   │                         │                           │ [7] session 토큰 발급
   │                         │                           │ - sessionAccessToken (1h)
   │                         │                           │ - sessionRefreshToken (7d)
   │                         │                           │ - member_device 생성/업데이트
   │                         │                           │
   │◀────────────────────────────────────────────────────│
   │   Set-Cookie: sessionAccessToken, sessionRefreshToken│
   │                         │                           │
   │  ※ googleAuthCode는 이 시점에서 이미 폐기됨         │
   │  ※ googleIdToken도 검증 후 폐기 (보관하지 않음)     │
   │  ※ googleApiToken은 응답에 포함되지만 미사용        │
```

### 4.4 OAuth 토큰 vs session 토큰 관계

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                             │
│   google* 토큰                        session* 토큰                        │
│   ────────────                        ──────────────                        │
│                                                                             │
│   ┌──────────────┐                   ┌──────────────┐                      │
│   │ googleIdToken│                   │ sessionAccess│                      │
│   │ (Google 발급)│                   │ Token        │                      │
│   │              │    ┌──────────┐   │ (우리가 발급) │                      │
│   │ iss: Google  │───▶│ 회원 매칭 │──▶│              │                      │
│   │ aud: 우리app │    │ (1회용)  │   │ sub: memberId│                      │
│   │ sub: googleId│    └──────────┘   │ role: MEMBER │                      │
│   │ hd: mju.ac.kr│                   │ deviceId: N  │                      │
│   │ exp: +1h     │                   │ exp: +1h     │                      │
│   └──────────────┘                   └──────────────┘                      │
│         │                                     │                            │
│         │ 검증 후 폐기                        │ API 요청마다 사용          │
│         │ (보관 안 함)                        │ (쿠키로 자동 전송)         │
│                                               │                            │
│                                    ┌──────────────┐                       │
│                                    │ sessionRefresh│                      │
│                                    │ Token        │                       │
│                                    │ (우리가 발급) │                       │
│                                    │              │                       │
│  ┌──────────────┐                  │ 만료: 7일    │                       │
│  │ googleAuth   │    ┌──────────┐  │ DB 저장: 해시 │                       │
│  │ Code         │───▶│ googleId │  │              │                       │
│  │              │    │ Token    │  │ sessionAccess│                       │
│  │ (Google 발급)│    │ 교환용   │  │ Token 만료 시 │                       │
│  │              │    │ (1회용)  │  │ 재발급 요청  │                       │
│  │ 만료: ~10분  │    └──────────┘  └──────────────┘                       │
│  │ 사용: 1회    │                                                         │
│  └──────────────┘                                                         │
│                                                                             │
│   ※ google* 토큰은 "신원 확인"에만 사용                                    │
│   ※ 확인 완료 후 session* 토큰으로 세션 관리                               │
│   ※ 두 토큰 시스템은 완전히 독립적                                         │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 5. 전체 인증 아키텍처

### 5.1 레이어 구조

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              요청 (HTTP Request)                            │
└────────────────────────────────────────┬────────────────────────────────────┘
                                         │
┌────────────────────────────────────────▼────────────────────────────────────┐
│                         Security Layer (Policy)                             │
│  ─────────────────────────────────────────────────────────────────────────  │
│  JwtAuthenticationFilter:  sessionAccessToken 서명 검증 → SecurityContext   │
│  ConsentCheckFilter:       MEMBER+ 개인정보 동의 여부 검사                  │
│  GlobalSecurityConfig:     필터 체인 구성, 401/403 시멘틱 응답             │
└────────────────────────────────────────┬────────────────────────────────────┘
                                         │
┌────────────────────────────────────────▼────────────────────────────────────┐
│                          Auth Layer (Mechanism)                             │
│  ─────────────────────────────────────────────────────────────────────────  │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐   │
│  │   Guest     │  │   OAuth     │  │   Merge     │  │    Session      │   │
│  │             │  │             │  │             │  │                 │   │
│  │ 게스트 생성 │  │ Google 인증 │  │ 게스트→멤버 │  │ session 토큰    │   │
│  │             │  │ 도메인 검증 │  │ 데이터 병합 │  │ 발급/관리       │   │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────────┘   │
│                                                        ┌─────────────────┐ │
│                                                        │    Device       │ │
│                                                        │                 │ │
│                                                        │ firebaseInstall │ │
│                                                        │ ationId 기반    │ │
│                                                        │ 기기 식별/매칭  │ │
│                                                        └─────────────────┘ │
└────────────────────────────────────────┬────────────────────────────────────┘
                                         │
┌────────────────────────────────────────▼────────────────────────────────────┐
│                         Account Layer (Resource)                            │
│  ─────────────────────────────────────────────────────────────────────────  │
│  프로필 조회, 개인정보 동의 감사, 회원 탈퇴                                  │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 5.2 토큰 흐름 요약

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              토큰 흐름                                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  [인증 수단]              [session 토큰]          [firebase 식별]           │
│  ──────────              ──────────────          ──────────────            │
│                                                                             │
│  게스트 로그인 ──┐                                                        │
│                  │    ┌─────────────────┐                                │
│  Google OAuth  ──┼───▶│ sessionAccess   │◄─── firebaseInstallationId     │
│                  │    │ Token           │     (기기 식별)                 │
│  테스트 로그인 ──┘    │ sessionRefresh  │                                │
│                       │ Token           │◄─── firebaseCloudMessaging     │
│                       └─────────────────┘     RegistrationToken         │
│                                               (푸시 주소, 선택적)        │
│                                                                             │
│  ※ 인증 수단은 무엇이든 상관없이                                          │
│    session 토큰과 firebase 식별자는 동일한 방식으로 동작                   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 6. DB 스키마

### 6.1 테이블 구조

```
┌─────────────────┐       ┌─────────────────┐       ┌──────────────────────────────────┐
│     member      │       │  member_auth    │       │         member_device            │
├─────────────────┤       ├─────────────────┤       ├──────────────────────────────────┤
│ id (PK)         │◄──────│ member_id (UQ)  │       │ id (PK)                          │
│ role            │       │ auth_type       │       │ member_id (FK)                   │
│ name            │       │ auth_key (UQ)   │       │ firebase_installation_id (UQ) ★  │
│ position        │       │ last_login_at   │       │ refresh_token_hash (UQ)          │
│ department      │       │ created_at      │       │ firebase_cloud_messaging_        │
│ created_at      │       │ updated_at      │       │   registration_token             │
│ updated_at      │       └─────────────────┘       │ platformjs_*                     │
└─────────────────┘                                 │ last_accessed_at                 │
         ▲                                          │ expires_at                       │
         │                                          │ created_at, updated_at           │
         │                                          └──────────────────────────────────┘
         │                                                        ▲
         └────────────────────────────────────────────────────────┘
                              1:N (한 회원, 여러 기기)

★ = v3에서 추가/변경된 핵심 필드
```

### 6.2 member_device 변경사항

| 필드 | v2 (기존) | v3 (변경) |
|------|-----------|-----------|
| `firebase_installation_id` | ❌ 없음 | ✅ `VARCHAR(255) UNIQUE` - **기기 식별의 유일한 기준** |
| `firebase_cloud_messaging_registration_token` | `fcm_token` | ✅ 이름 변경 - 푸시 발송용으로만 사용 |
| `platformjs_ua` | ✅ 기기 매칭에 사용 | ⚠️ 정보 표시용으로만 사용 (매칭에 사용하지 않음) |

### 6.3 인덱스

```sql
-- firebaseInstallationId 기반 기기 조회 (메인 식별 경로)
CREATE UNIQUE INDEX idx_member_device_firebase_installation_id 
ON member_device(firebase_installation_id) 
WHERE firebase_installation_id IS NOT NULL;

-- 회원별 기기 목록 조회
CREATE INDEX idx_member_device_member ON member_device(member_id);

-- sessionRefreshToken 기반 세션 조회 (refresh 시 사용)
CREATE UNIQUE INDEX idx_member_device_rtk ON member_device(refresh_token_hash);
```

---

## 7. 기기 식별 메커니즘

### 7.1 v2 vs v3 비교

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           v2 (기존) 기기 식별                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  findExistingDevice(memberId, deviceInfo):                                  │
│    1. fcmToken 있으면 → (memberId + UA + fcmToken) 매칭                    │
│    2. 없으면 → (memberId + UA) 매칭                                         │
│                                                                             │
│  문제점:                                                                    │
│  - UA는 브라우저 업데이트 시 변경됨 (Chrome 150 → 151)                      │
│  - 같은 기기인데 새 기기로 인식됨                                           │
│  - 폴백 로직이 복잡하고 예측 불가능                                         │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                           v3 (신규) 기기 식별                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  findExistingDevice(memberId, deviceInfo):                                  │
│    1. firebaseInstallationId 있으면 → (memberId + firebaseInstallationId)   │
│    2. 없으면 → 새 기기로 생성                                               │
│                                                                             │
│  장점:                                                                      │
│  - firebaseInstallationId는 브라우저 업데이트해도 변경 안 됨                │
│  - 단순하고 예측 가능한 동작                                                │
│  - 폴백 없음 = 복잡성 제거                                                  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 7.2 v3 기기 식별 로직

```java
// DeviceSessionService.java

@Transactional
public MemberDevice upsert(Long memberId, String sessionRefreshToken, DeviceInfo deviceInfo, long expiryMs) {
    final String firebaseInstallationId = deviceInfo.getFirebaseInstallationId();
    final String refreshTokenHash = RefreshTokenHasher.hash(sessionRefreshToken);

    // firebaseInstallationId 기반 기기 조회 (유일한 식별 기준)
    Optional<MemberDevice> existing = (firebaseInstallationId != null && !firebaseInstallationId.isBlank())
        ? memberDeviceRepository.findByMemberIdAndFirebaseInstallationId(memberId, firebaseInstallationId)
        : Optional.empty();

    return existing
        .map(device -> {
            // 기존 기기 업데이트
            device.updateAccessInfo(refreshTokenHash, deviceInfo);
            device.extendExpiry(Instant.now().plusMillis(expiryMs));
            if (deviceInfo.getFirebaseCloudMessagingRegistrationToken() != null) {
                device.updateFirebaseCloudMessagingRegistrationToken(
                    deviceInfo.getFirebaseCloudMessagingRegistrationToken());
            }
            return device;
        })
        .orElseGet(() -> {
            // 새 기기 생성
            MemberDevice device = MemberDevice.builder()
                .memberId(memberId)
                .firebaseInstallationId(firebaseInstallationId)  // ★ FID 저장
                .refreshTokenHash(refreshTokenHash)
                // platformjs_* 필드들 (정보 표시용)
                .platformJsName(deviceInfo.getName())
                .platformJsVersion(deviceInfo.getVersion())
                .platformJsUa(deviceInfo.getUa())
                // ...
                .expiresAt(Instant.now().plusMillis(expiryMs))
                .build();
            if (deviceInfo.getFirebaseCloudMessagingRegistrationToken() != null) {
                device.updateFirebaseCloudMessagingRegistrationToken(
                    deviceInfo.getFirebaseCloudMessagingRegistrationToken());
            }
            return memberDeviceRepository.save(device);
        });
}

// firebaseInstallationId가 없으면 항상 새 기기 생성
private Optional<MemberDevice> findExistingDevice(Long memberId, DeviceInfo deviceInfo) {
    if (deviceInfo.getFirebaseInstallationId() == null || deviceInfo.getFirebaseInstallationId().isBlank()) {
        return Optional.empty();
    }
    return memberDeviceRepository.findByMemberIdAndFirebaseInstallationId(
        memberId, deviceInfo.getFirebaseInstallationId());
}
```

### 7.3 기기 식별 시나리오

| 시나리오 | firebaseInstallationId | 결과 |
|----------|------------------------|------|
| Chrome 150 → 151 업데이트 | 동일 | ✅ 같은 기기 인식 |
| macOS 업데이트 | 동일 | ✅ 같은 기기 인식 |
| 브라우저 데이터 삭제 | 새로 발급 | 새 기기 생성 (의도된 동작) |
| 시크릿 모드 → 종료 | 새로 발급 | 새 기기 생성 (의도된 동작) |
| 다른 브라우저 사용 | 다름 | 새 기기 생성 (의도된 동작) |
| 다른 기기 사용 | 다름 | 새 기기 생성 (의도된 동작) |
| PWA 삭제 후 재설치 | 새로 발급 | 새 기기 생성 (의도된 동작) |

---

## 8. 시퀀스 흐름

### 8.1 게스트 로그인 (firebaseInstallationId 기반)

```
Browser                                    Backend
   │                                          │
   │  [1] Firebase 초기화                     │
   │  getInstallations(app)                   │
   │  → firebaseInstallationId 발급           │
   │    (IndexedDB에 저장)                    │
   │                                          │
   │  [2] 게스트 로그인 요청                  │
   │─────────────────────────────────────────▶│
   │  POST /auth/guest                        │
   │  { device: {                             │
   │      firebaseInstallationId: "fGk8...",  │
   │      firebaseCloudMessagingRegistration  │
   │        Token: "...",                     │
   │      ...                                 │
   │  }}                                      │
   │                                          │
   │                          ┌───────────────│
   │                          │ Member(GUEST) 생성
   │                          │ MemberAuth(GUEST_KEY) 생성
   │                          │               │
   │                          │ DeviceSessionService.upsert()
   │                          │   → findByMemberIdAndFirebaseInstallationId()
   │                          │   → firebaseInstallationId로 매칭
   │                          │   → 없으면 새 member_device 생성
   │                          │               │
   │                          │ sessionAccessToken/sessionRefreshToken 발급
   │                          └───────────────│
   │                                          │
   │◀─────────────────────────────────────────│
   │  Set-Cookie: session_access_token,       │
   │              session_refresh_token       │
```

### 8.2 Google OAuth 로그인 (firebaseInstallationId 기반)

```
Browser                    Google                    Backend
   │                         │                          │
   │  [1] OAuth 시작         │                          │
   │───────────────────────────────────────────────────▶│
   │◀───────────────────────────────────────────────────│
   │  { googleAuthUrl, googleState }                    │
   │                         │                          │
   │  [2] Google 인증        │                          │
   │────────────────────────▶│                          │
   │◀────────────────────────│                          │
   │  googleAuthCode         │                          │
   │  + googleState          │                          │
   │                         │                          │
   │  [3] 토큰 교환          │                          │
   │───────────────────────────────────────────────────▶│
   │  { googleAuthCode,                               │
   │    googleState,                                   │
   │    device: {                                      │
   │      firebaseInstallationId: "fGk8...",           │
   │      firebaseCloudMessagingRegistrationToken: ... │
   │  }}                                               │
   │                         │                          │
   │                         │◀─────────────────────────│
   │                         │  POST /token             │
   │                         │◀─────────────────────────│
   │                         │  googleIdToken           │
   │                         │                          │
   │                          ┌─────────────────────────│
   │                          │ 1. googleIdToken 검증   │
   │                          │ 2. hd: mju.ac.kr 확인   │
   │                          │ 3. sub 추출             │
   │                          │ 4. googleState 검증     │
   │                          │ 5. 회원 매칭/생성       │
   │                          │ 6. session 토큰 생성    │
   │                          │    → firebaseInstallationId로 기기 매칭
   │                          │    → sessionAccessToken/sessionRefreshToken 발급
   │                          └─────────────────────────│
   │                                                   │
   │◀───────────────────────────────────────────────────│
   │  Set-Cookie: session_access_token,                │
   │              session_refresh_token                │
```

### 8.3 토큰 갱신 (Refresh)

```
Browser                                    Backend
   │                                          │
   │  [1] sessionAccessToken 만료 감지        │
   │  또는 선제 갱신 (50분 경과)              │
   │                                          │
   │  [2] Refresh 요청                        │
   │─────────────────────────────────────────▶│
   │  POST /auth/refresh                      │
   │  Cookie: session_refresh_token           │
   │                                          │
   │                          ┌───────────────│
   │                          │ sessionRefreshToken 해시 매칭으로 device 조회
   │                          │ 만료 확인     │
   │                          │               │
   │                          │ 새 sessionRefreshToken 발급
   │                          │ device.updateRefreshTokenHash()
   │                          │ device.extendExpiry()
   │                          │               │
   │                          │ 새 sessionAccessToken 발급
   │                          │ (deviceId = device.id)
   │                          └───────────────│
   │                                          │
   │◀─────────────────────────────────────────│
   │  Set-Cookie: 새 sessionAccessToken,      │
   │              새 sessionRefreshToken      │
```

---

## 9. 마이그레이션 계획

### 9.1 인프라 환경 분석

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           인프라 환경                                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ DEV 환경                                                            │   │
│  │ ─────────                                                           │   │
│  │ • docker-compose-dev.yml                                            │   │
│  │ • PostgreSQL 17 + pgmq (named volume: postgres_data)                │   │
│  │ • ddl-auto: update → 엔티티 변경 시 자동 스키마 반영               │   │
│  │ • schema-view.sql 실행 (뷰만)                                      │   │
│  │ • 로컬 실행: ./run-dev.sh                                           │   │
│  │                                                                     │   │
│  │ 마이그레이션: 엔티티 수정만으로 자동 반영                           │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ PROD 환경                                                           │   │
│  │ ─────────                                                           │   │
│  │ • docker-compose-prod.yml                                           │   │
│  │ • PostgreSQL 17 + pgmq (bind mount: ./data/postgres)                │   │
│  │ • ddl-auto: validate → 스키마 자동 수정 금지                       │   │
│  │ • schema-prod.sql을 db-setup 컨테이너에서 psql로 직접 실행          │   │
│  │ • 서버 배포: ./run-prod.sh (docker compose up -d --build)           │   │
│  │                                                                     │   │
│  │ 마이그레이션:                                                       │   │
│  │   1. schema-prod.sql 수정 (신규 배포용)                            │   │
│  │   2. 기존 DB에 ALTER TABLE 수동 실행 (무중단)                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 9.2 마이그레이션 단계

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           마이그레이션 단계                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Phase 1: DB 스키마 변경 (무중단)                                          │
│  ├─ [DEV]  엔티티 수정 → ddl-auto: update로 자동 반영                      │
│  ├─ [PROD] schema-prod.sql 수정 + 기존 DB에 ALTER TABLE 실행               │
│  └─ 검증: pgweb/pgadmin으로 컬럼 확인                                      │
│                                                                             │
│  Phase 2: 백엔드 배포 (firebaseInstallationId 지원)                        │
│  ├─ DeviceInfo, MemberDevice, DeviceSessionService 수정                    │
│  ├─ [DEV]  로컬 테스트                                                     │
│  └─ [PROD] docker compose up -d --build                                    │
│                                                                             │
│  Phase 3: 프론트엔드 배포 (FID 발급)                                       │
│  ├─ firebase/installations 모듈 추가                                       │
│  ├─ getDeviceInfo() async 변경, firebaseInstallationId 포함                │
│  └─ 프론트엔드 배포                                                        │
│                                                                             │
│  Phase 4: 기존 데이터 정리 (선택적, 운영 안정화 후)                        │
│  ├─ firebaseInstallationId=NULL인 중복 기기 분석                           │
│  ├─ 중복 기기 정리 SQL 실행                                                │
│  └─ UNIQUE 제약 추가                                                       │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 9.3 Phase 1: DB 스키마 변경

#### DEV 환경

```bash
# 엔티티에 필드 추가 후 로컬 실행
# ddl-auto: update가 자동 반영
./run-dev.sh
```

#### PROD 환경

**Step 1: 기존 DB에 ALTER TABLE 실행 (무중단)**

```bash
# 서버에 SSH 접속 후
# pgweb (http://localhost:10023) 또는 psql로 직접 실행

# 방법 1: pgweb 웹 UI 사용
# http://localhost:10023 접속 → SQL 탭 → 아래 SQL 실행

# 방법 2: psql 직접 실행
docker exec -it mju-sugangsincheong-helper-db psql -U mjusugangsincheonghelperuser -d mjusugangsincheonghelperdb_prod
```

```sql
-- Step 1: firebase_installation_id 컬럼 추가 (NULL 허용, 기존 데이터 영향 없음)
ALTER TABLE member_device ADD COLUMN IF NOT EXISTS firebase_installation_id VARCHAR(255);

-- Step 2: fcm_token 컬럼 이름 변경
ALTER TABLE member_device RENAME COLUMN fcm_token TO firebase_cloud_messaging_registration_token;

-- Step 3: firebase_installation_id 부분 인덱스 (NULL이 아닌 값만 UNIQUE)
CREATE UNIQUE INDEX IF NOT EXISTS idx_member_device_firebase_installation_id 
ON member_device(firebase_installation_id) 
WHERE firebase_installation_id IS NOT NULL;

-- Step 4: 확인
\d member_device
```

**Step 2: schema-prod.sql 수정 (신규 배포용)**

```sql
-- schema-prod.sql의 member_device 테이블 정의 수정
CREATE TABLE IF NOT EXISTS member_device (
    id                                              BIGSERIAL    PRIMARY KEY,
    member_id                                       BIGINT       NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    firebase_installation_id                        VARCHAR(255),  -- ★ 추가: Firebase Installation ID
    refresh_token_hash                              VARCHAR(64)  NOT NULL UNIQUE,
    firebase_cloud_messaging_registration_token     VARCHAR(512),  -- ★ 이름 변경
    platformjs_name                                 VARCHAR(100),
    platformjs_version                              VARCHAR(50),
    platformjs_layout                               VARCHAR(50),
    platformjs_prerelease                           VARCHAR(50),
    platformjs_os                                   VARCHAR(100),
    platformjs_manufacturer                         VARCHAR(100),
    platformjs_product                              VARCHAR(100),
    platformjs_description                          TEXT,
    platformjs_ua                                   TEXT,
    last_accessed_at                                TIMESTAMP,
    expires_at                                      TIMESTAMP,
    created_at                                      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at                                      TIMESTAMP    NOT NULL DEFAULT now()
);

-- ★ 추가: firebaseInstallationId 부분 인덱스 (NULL이 아닌 값만 UNIQUE)
CREATE UNIQUE INDEX IF NOT EXISTS idx_member_device_firebase_installation_id 
ON member_device(firebase_installation_id) 
WHERE firebase_installation_id IS NOT NULL;
```

**DB 행 변화:**

```
[마이그레이션 전]
┌────┬───────────┬──────────────────┬─────────────────────┬───────────┐
│ id │ member_id │ refresh_token_hash│ platformjs_ua       │ fcm_token │
├────┼───────────┼──────────────────┼─────────────────────┼───────────┤
│ 1  │ 100       │ abc123...        │ Chrome/150.0.0.0    │ fcm_xxx   │
│ 2  │ 100       │ def456...        │ Chrome/151.0.0.0    │ NULL      │  ← 브라우저 업데이트로 생긴 중복
│ 3  │ 100       │ ghi789...        │ Safari/17.0         │ fcm_yyy   │
└────┴───────────┴──────────────────┴─────────────────────┴───────────┘

[Phase 1 배포 직후 - 컬럼 추가/이름 변경]
┌────┬───────────┬──────────────────┬─────────────────────┬─────────────────────────────────────┬──────────────────────┐
│ id │ member_id │ refresh_token_hash│ platformjs_ua       │ firebase_cloud_messaging_           │ firebase_installation│
│    │           │                  │                     │ registration_token                  │ _id                  │
├────┼───────────┼──────────────────┼─────────────────────┼─────────────────────────────────────┼──────────────────────┤
│ 1  │ 100       │ abc123...        │ Chrome/150.0.0.0    │ fcm_xxx                             │ NULL                 │
│ 2  │ 100       │ def456...        │ Chrome/151.0.0.0    │ NULL                                │ NULL                 │
│ 3  │ 100       │ ghi789...        │ Safari/17.0         │ fcm_yyy                             │ NULL                 │
└────┴───────────┴──────────────────┴─────────────────────┴─────────────────────────────────────┴──────────────────────┘

[Phase 2+3 배포 후 - 사용자 재로그인 시 FID 발급]
┌────┬───────────┬──────────────────┬─────────────────────┬─────────────────────────────────────┬──────────────────────┐
│ id │ member_id │ refresh_token_hash│ platformjs_ua       │ firebase_cloud_messaging_           │ firebase_installation│
│    │           │                  │                     │ registration_token                  │ _id                  │
├────┼───────────┼──────────────────┼─────────────────────┼─────────────────────────────────────┼──────────────────────┤
│ 1  │ 100       │ abc123...        │ Chrome/150.0.0.0    │ fcm_xxx                             │ NULL                 │  ← 만료 예정
│ 2  │ 100       │ def456...        │ Chrome/151.0.0.0    │ NULL                                │ NULL                 │  ← 만료 예정
│ 3  │ 100       │ ghi789...        │ Safari/17.0         │ fcm_yyy                             │ NULL                 │  ← 만료 예정
│ 4  │ 100       │ jkl012...        │ Chrome/151.0.0.0    │ fcm_zzz                             │ fGk8Xm2pQr5          │  ← 새 기기 (FID 기반)
└────┴───────────┴──────────────────┴─────────────────────┴─────────────────────────────────────┴──────────────────────┘

[Phase 4 정리 후 - firebaseInstallationId=NULL인 만료 기기 삭제]
┌────┬───────────┬──────────────────┬─────────────────────┬─────────────────────────────────────┬──────────────────────┐
│ id │ member_id │ refresh_token_hash│ platformjs_ua       │ firebase_cloud_messaging_           │ firebase_installation│
│    │           │                  │                     │ registration_token                  │ _id                  │
├────┼───────────┼──────────────────┼─────────────────────┼─────────────────────────────────────┼──────────────────────┤
│ 4  │ 100       │ jkl012...        │ Chrome/151.0.0.0    │ fcm_zzz                             │ fGk8Xm2pQr5          │  ← FID 기반 유일한 기기
└────┴───────────┴──────────────────┴─────────────────────┴─────────────────────────────────────┴──────────────────────┘
```

### 9.4 Phase 2: 백엔드 코드 변경

```java
// DeviceInfo.java - 필드 이름 변경
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceInfo {
    private String firebaseInstallationId;                    // ★ 변경
    private String firebaseCloudMessagingRegistrationToken;   // ★ 변경
    private String name;
    private String version;
    // ... 기존 필드들
}

// MemberDevice.java - 필드 이름 변경
@Column(name = "firebase_installation_id", length = 255)
private String firebaseInstallationId;

@Column(name = "firebase_cloud_messaging_registration_token", length = 512)
private String firebaseCloudMessagingRegistrationToken;

// MemberDeviceRepository.java - 조회 메서드 추가
Optional<MemberDevice> findByMemberIdAndFirebaseInstallationId(Long memberId, String firebaseInstallationId);

// DeviceSessionService.java - firebaseInstallationId 기반 식별로 변경
private Optional<MemberDevice> findExistingDevice(Long memberId, DeviceInfo deviceInfo) {
    if (deviceInfo.getFirebaseInstallationId() != null && !deviceInfo.getFirebaseInstallationId().isBlank()) {
        return memberDeviceRepository.findByMemberIdAndFirebaseInstallationId(
            memberId, deviceInfo.getFirebaseInstallationId());
    }
    return Optional.empty();
}
```

**PROD 배포:**

```bash
# schema-prod.sql 수정 후
cd /path/to/backend
./run-prod.sh

# 로그 확인
docker logs -f mju-sugangsincheong-helper-backend

# 헬스체크
curl http://localhost:10020/actuator/health
```

### 9.5 Phase 3: 프론트엔드 코드 변경

```typescript
// shared/services/firebase.ts - FID 발급 함수 추가
import { getInstallations, getId } from 'firebase/installations'

export async function getFirebaseInstallationId(): Promise<string | null> {
  try {
    const installations = getInstallations(firebaseAppInstance)
    const fid = await getId(installations)
    return fid
  } catch (err) {
    console.error('[Firebase] FID Error:', err)
    return null
  }
}

// shared/utils/deviceInfo.ts - 필드 이름 변경
export interface DeviceInfoPayload {
  firebaseInstallationId?: string;                    // ★ 변경
  firebaseCloudMessagingRegistrationToken?: string;   // ★ 변경
  name?: string
  version?: string
  // ... 기존 필드들
}

export async function getDeviceInfo(): Promise<DeviceInfoPayload> {
  if (typeof navigator === 'undefined' || !navigator.userAgent) {
    return {}
  }
  const parsed = platform.parse(navigator.userAgent)
  
  // firebaseInstallationId 발급 (알림 권한 불필요, 항상 가능)
  const fid = await getFirebaseInstallationId()
  
  return {
    firebaseInstallationId: fid || undefined,         // ★ 변경
    firebaseCloudMessagingRegistrationToken: getCachedFcmTokenFromLocalStorage() || undefined,
    name: parsed.name || undefined,
    version: parsed.version || undefined,
    // ... 기존 필드들
    ua: parsed.ua || undefined,
  }
}
```

### 9.6 Phase 4: 기존 데이터 정리 (선택적)

**운영 안정화 후, firebaseInstallationId=NULL인 만료 기기 정리:**

```sql
-- Step 1: firebaseInstallationId=NULL인 기기 현황 확인
SELECT 
    member_id, 
    COUNT(*) as device_count,
    MAX(last_accessed_at) as last_access
FROM member_device
WHERE firebase_installation_id IS NULL
GROUP BY member_id
ORDER BY device_count DESC;

-- Step 2: 만료된 firebaseInstallationId=NULL 기기 삭제
DELETE FROM member_device
WHERE firebase_installation_id IS NULL 
AND (expires_at IS NULL OR expires_at < now());

-- Step 3: 여전히 NULL인 활성 기기 확인
SELECT member_id, id, platformjs_ua, last_accessed_at, expires_at
FROM member_device
WHERE firebase_installation_id IS NULL
ORDER BY last_accessed_at DESC;
```

### 9.7 배포 순서 요약

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           배포 순서                                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  1. [DB] PROD 기존 DB에 ALTER TABLE 실행                                   │
│     └─ pgweb 또는 psql로 컬럼 추가/이름 변경 + 인덱스 추가                 │
│     └─ 무중단, 기존 데이터 영향 없음                                       │
│                                                                             │
│  2. [Backend] schema-prod.sql 수정 + 코드 변경                             │
│     └─ MemberDevice 엔티티 필드 이름 변경                                  │
│     └─ DeviceInfo DTO 필드 이름 변경                                       │
│     └─ DeviceSessionService.findExistingDevice() FID 기반으로 변경         │
│     └─ MemberDeviceRepository에 findByMemberIdAndFirebaseInstallationId()  │
│                                                                             │
│  3. [Backend] PROD 배포                                                    │
│     └─ ./run-prod.sh                                                       │
│     └─ docker compose up -d --build                                        │
│     └─ ddl-auto: validate이므로 schema-prod.sql과 엔티티 일치 필수         │
│                                                                             │
│  4. [Frontend] FID 발급 로직 구현 + 배포                                   │
│     └─ firebase/installations 모듈 설치                                    │
│     └─ getFirebaseInstallationId() 함수 구현                               │
│     └─ getDeviceInfo() async 변경, 필드 이름 변경                          │
│     └─ auth store 호출부 async 처리                                        │
│                                                                             │
│  5. [운영] 모니터링                                                        │
│     └─ 새 기기 생성 시 firebase_installation_id 컬럼 확인                  │
│     └─ 브라우저 업데이트 후 같은 기기로 인식되는지 확인                    │
│                                                                             │
│  6. [선택] Phase 4 정리 (1~2주 후)                                         │
│     └─ firebaseInstallationId=NULL인 만료 기기 삭제                        │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 9.8 주의사항

| 항목 | 내용 |
|------|------|
| **PROD ddl-auto: validate** | schema-prod.sql과 엔티티가 일치하지 않으면 앱 기동 실패 |
| **기존 데이터** | firebaseInstallationId=NULL인 기존 기기는 FID 매칭 불가 → 새 기기로 생성됨 |
| **일시적 중복** | Phase 2~3 배포 후 기존 사용자는 일시적으로 기기가 1개 늘어날 수 있음 |
| **자연 정화** | 기존 기기(firebaseInstallationId=NULL)는 7일 후 만료되어 자동 정화됨 |
| **롤백** | 컬럼 추가는 기존 로직에 영향 없으므로 롤백 가능 |

### 9.9 마이그레이션 체크리스트

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           마이그레이션 체크리스트                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Phase 1: DB 준비                                                           │
│  ──────────────                                                             │
│  □ [PROD] pgweb/psql로 firebase_installation_id 컬럼 추가                  │
│  □ [PROD] fcm_token → firebase_cloud_messaging_registration_token 이름 변경│
│  □ [PROD] UNIQUE 부분 인덱스 생성 (WHERE firebase_installation_id IS NOT NULL)│
│  □ [DEV]  로컬 DB 확인 (ddl-auto: update로 자동 반영)                      │
│                                                                             │
│  Phase 2: 백엔드 준비                                                       │
│  ──────────────────                                                         │
│  □ schema-prod.sql 수정                                                    │
│  □ DeviceInfo DTO 필드 이름 변경                                           │
│  □ MemberDevice 엔티티 필드 이름 변경                                      │
│  □ MemberDeviceRepository에 findByMemberIdAndFirebaseInstallationId() 추가 │
│  □ DeviceSessionService.findExistingDevice() FID 기반으로 변경             │
│  □ [DEV] 로컬 테스트                                                       │
│                                                                             │
│  Phase 3: 백엔드 배포                                                       │
│  ──────────────                                                             │
│  □ [PROD] ./run-prod.sh 실행                                               │
│  □ [PROD] 헬스체크 확인 (curl /actuator/health)                            │
│  □ [PROD] 로그 확인 (에러 없음)                                            │
│                                                                             │
│  Phase 4: 프론트엔드 업데이트                                               │
│  ──────────────────────────                                                 │
│  □ firebase/installations 모듈 설치 (npm install firebase)                 │
│  □ getFirebaseInstallationId() 함수 구현                                   │
│  □ getDeviceInfo()를 async로 변경, 필드 이름 변경                          │
│  □ DeviceInfoPayload 인터페이스 업데이트                                   │
│  □ auth store의 세션 생성 호출부 async 처리                                │
│  □ [DEV] 로컬 테스트                                                       │
│  □ [PROD] 프론트엔드 배포                                                  │
│                                                                             │
│  Phase 5: 검증                                                              │
│  ──────────                                                                 │
│  □ 브라우저 업데이트 후에도 같은 기기로 인식되는지 확인                    │
│  □ 다른 브라우저/기기에서는 새 기기로 생성되는지 확인                      │
│  □ 토큰 갱신 시 deviceId가 올바르게 유지되는지 확인                       │
│  □ pgweb으로 member_device 테이블 확인                                     │
│                                                                             │
│  Phase 6: 정리 (선택적, 1~2주 후)                                          │
│  ──────────────────────────────                                             │
│  □ firebaseInstallationId=NULL인 만료 기기 현황 확인                       │
│  □ 만료 기기 삭제 SQL 실행                                                 │
│  □ 최종 확인: 모든 활성 기기에 firebaseInstallationId 설정됨               │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 부록: 용어 정리

| 용어 | 설명 |
|------|------|
| **firebaseInstallationId** | Firebase Installation ID. 앱 설치 건당 고유 식별자 |
| **firebaseCloudMessagingRegistrationToken** | FCM Registration Token. 푸시 알림 발송 주소 |
| **sessionAccessToken** | Access Token. API 요청 인증용 JWT (1시간) |
| **sessionRefreshToken** | Refresh Token. ATK 재발급용 UUID (7일) |
| **googleIdToken** | Google이 발급하는 JWT. 사용자 신원 증명 |
| **googleAuthCode** | Google Authorization Code. ID Token 교환용 일회성 코드 |
| **googleState** | OAuth State. CSRF 방어용 일회성 난수 |
| **mergeTicket** | 게스트→멤버 데이터 병합용 일회성 JWT (5분) |
| **deviceId** | member_device.id. sessionAccessToken에 포함되어 요청 기기 식별 |
