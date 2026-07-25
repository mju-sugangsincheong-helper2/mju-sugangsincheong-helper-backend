# Auth Frontend Guide

프론트엔드에서 구현해야 하는 인증/인가 관련 횡단 관심사와 각 기능별 시퀀스를 정리합니다.

---

## 목차

1. [横단 관심사 (Cross-cutting Concerns)](#1-횡단-관심사)
2. [사용자 상태 머신](#2-사용자-상태-머신)
3. [토큰 전달 전략](#3-토큰-전달-전략)
4. [API 레퍼런스](#4-api-레퍼런스)
5. [에러 코드](#5-에러-코드)
6. [구현 체크리스트](#6-구현-체크리스트)

---

## 1. 횡단 관심사

프론트엔드 전체에서 공통으로 처리해야 하는 사항입니다.

### 1.1 토큰 관리

```
┌─────────────────────────────────────────────────────────────────────┐
│                        토큰 관리 정책                               │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  Access Token (ATK)                                                 │
│  ├── 만료: 1시간                                                    │
│  ├── 용도: 모든 인증 요청에 자동 포함                               │
│  ├── prod: HttpOnly Secure Cookie (JS 접근 불가)                   │
│  └── dev:  Cookie + 응답 헤더/바디에서도 읽기 가능                  │
│                                                                     │
│  Refresh Token (RTK)                                                │
│  ├── 만료: 7일                                                      │
│  ├── 용도: ATK 만료 시 자동 재발급                                 │
│  ├── prod: HttpOnly Secure Cookie                                   │
│  └── dev:  Cookie + 응답 헤더/바디에서도 읽기 가능                  │
│                                                                     │
│  핵심 규칙                                                          │
│  ├── ATK는 쿠키로 자동 전송되므로 별도 헤더 설정 불필요 (prod)     │
│  ├── RTK는 오직 /auth/refresh 요청에만 사용                        │
│  └── 두 토큰 모두 프론트에서 직접 파싱할 필요 없음                 │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 1.2 토큰 리프레시 인터셉터

```
┌─────────────────────────────────────────────────────────────────────┐
│                    토큰 리프레시 흐름                                │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  API 요청 → 401 응답 (ATK 만료)                                    │
│       │                                                             │
│       ▼                                                             │
│  POST /api/v1/auth/refresh (RTK는 쿠키로 자동 전송)                │
│       │                                                             │
│       ├── 성공 (200)                                                │
│       │   ├── 새 ATK 쿠키 Set-Cookie                                │
│       │   ├── 새 RTK 쿠키 Set-Cookie                                │
│       │   └── 원래 요청 재시도                                      │
│       │                                                             │
│       └── 실패 (401 AUTH_004)                                       │
│           └── RTK도 만료 → 로그인 페이지로 리다이렉트              │
│                                                                     │
│  주의사항                                                           │
│  ├── 동시에 여러 401이 발생하면 refresh는 1번만 호출               │
│  ├── refresh 중인 동안 다른 요청은 큐에 대기                       │
│  └── refresh 실패 시 모든 대기 요청을 로그인 페이지로 전환         │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 1.3 개인정보 동의 상태 관리

```
┌─────────────────────────────────────────────────────────────────────┐
│                    개인정보 동의 흐름                                │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  JWT ATK에 "agreed" 클레임 포함                                    │
│       │                                                             │
│       ├── agreed=true  → ConsentCheckFilter 통과                   │
│       └── agreed=false → ConsentCheckFilter가 403 차단             │
│                                                                     │
│  403 AUTH_001 응답 수신 시                                          │
│       │                                                             │
│       ├── /auth/privacy/agree 페이지로 이동                        │
│       ├── 동의 후 POST /api/v1/auth/privacy/agree 호출             │
│       └── 성공 시: ATK 재발급 (agreed=true 포함)                   │
│                                                                     │
│  면제 경로 (동의 없이 접근 가능)                                    │
│  ├── /auth/privacy/agree                                            │
│  └── /auth/logout                                                   │
│                                                                     │
│  적용 대상                                                          │
│  ├── ROLE_MEMBER: 동의 필요 (보호된 기능 사용 가능)                │
│  ├── ROLE_ADMIN: 동의 필요                                          │
│  └── ROLE_GUEST: 동의 검사 없음 (제한된 기능만 사용)               │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 1.4 역할별 접근 제어

```
┌─────────────────────────────────────────────────────────────────────┐
│                         역할 계층                                   │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ROLE_ADMIN > ROLE_MEMBER > ROLE_GUEST                              │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ ROLE_GUEST                                                  │   │
│  │ ├── 생성: POST /api/v1/auth/guest                           │   │
│  │ ├── 가능: /accounts/me 조회, /accounts/me 탈퇴             │   │
│  │ ├── 제한: 보호된 기능 접근 시 403                           │   │
│  │ └── 다음 단계: Google OAuth → Merge                        │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                              ↓                                      │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ ROLE_MEMBER                                                 │   │
│  │ ├── 생성: Google OAuth 로그인 (mju.ac.kr 도메인 필수)      │   │
│  │ ├── 가능: 모든 일반 기능                                   │   │
│  │ ├── 전제: 개인정보 동의 완료 (agreed=true)                 │   │
│  │ └── 다음 단계: 관리자 권한 부여 시 ADMIN                   │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                              ↓                                      │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ ROLE_ADMIN                                                  │   │
│  │ ├── 생성: 수동 부여 (DB 직접 수정)                         │   │
│  │ └── 가능: 모든 기능 + 시스템 설정 관리                     │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 1.5 전역 에러 핸들러

```
┌─────────────────────────────────────────────────────────────────────┐
│                     HTTP 상태 코드별 처리                           │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  401 Unauthorized                                                   │
│  ├── 토큰 없음/만료 → POST /api/v1/auth/refresh 시도              │
│  ├── refresh 실패 → 로그인 페이지로 이동                          │
│  └── AUTH_004: RTK까지 만료됨                                     │
│                                                                     │
│  403 Forbidden                                                      │
│  ├── AUTH_001: 개인정보 동의 필요 → 동의 페이지로 이동            │
│  ├── AUTH_010: mju.ac.kr 도메인 아님 → 안내 메시지                │
│  └── GLOBAL_SECURITY_002: 권한 부족 → 접근 불가 알림              │
│                                                                     │
│  409 Conflict                                                       │
│  └── AUTH_005: 게스트 데이터 병합 필요 → Merge 플로우 시작        │
│                                                                     │
│  400 Bad Request                                                    │
│  └── AUTH_006: Merge Ticket 만료 → Google OAuth부터 다시 시작      │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 2. 사용자 상태 머신

```
                    ┌──────────────┐
                    │   익명 상태   │
                    │  (토큰 없음)  │
                    └──────┬───────┘
                           │
              ┌────────────┼────────────┐
              │                         │
              ▼                         ▼
    ┌──────────────────┐     ┌──────────────────┐
    │  게스트 로그인    │     │  Google OAuth     │
    │  POST /auth/guest│     │  POST /auth/token │
    └────────┬─────────┘     └────────┬─────────┘
             │                        │
             ▼                        ▼
    ┌──────────────────┐     ┌──────────────────┐
    │   ROLE_GUEST     │     │   ROLE_MEMBER    │
    │   (임시 세션)    │     │   (신규 가입)    │
    │                  │     │                  │
    │  가능:           │     │  nextState:      │
    │  - /me 조회      │     │  개인정보 동의    │
    │  - /me 탈퇴      │     │                  │
    │  - 수강검색 제한  │     │                  │
    └────────┬─────────┘     └────────┬─────────┘
             │                        │
             │                        ▼
             │               ┌──────────────────┐
             │               │ 개인정보 동의     │
             │               │ POST /privacy/   │
             │               │      agree       │
             │               └────────┬─────────┘
             │                        │
             │                        ▼
             │               ┌──────────────────┐
             │               │ ROLE_MEMBER      │
             │               │ (동의 완료)      │
             │               │                  │
             │               │ 가능: 모든 기능   │
             │               └──────────────────┘
             │
             │  Google OAuth (기존 계정, Guest 상태)
             │  POST /auth/token {code, state, accessToken}
             │
             ▼
    ┌──────────────────┐
    │  409 응답         │     ┌──────────────────┐
    │  MERGE_REQUIRED   │────▶│  mergeTicket     │
    │  + mergeTicket    │     │  (서버가 생성)   │
    └──────────────────┘     └────────┬─────────┘
                                      │
                                      ▼
                             ┌──────────────────┐
                             │ POST /auth/login/ │
                             │ google/merge      │
                             │ {mergeTicket}     │
                             └────────┬─────────┘
                                      │
                                      ▼
                             ┌──────────────────┐
                             │ ROLE_MEMBER      │
                             │ (게스트 데이터   │
                             │  병합 완료)      │
                             └──────────────────┘
```

---

## 3. 토큰 전달 전략

### 3.1 환경별 차이

| 항목 | prod | dev |
|------|------|-----|
| **ATK 전달** | `Set-Cookie`만 | `Set-Cookie` + `Authorization` 헤더 + `X-Access-Token` 헤더 + 응답 바디 |
| **RTK 전달** | `Set-Cookie`만 | `Set-Cookie` + `X-Refresh-Token` 헤더 + 응답 바디 |
| **Cookie Secure** | `true` (HTTPS 필수) | `false` (HTTP 허용) |
| **Cookie HttpOnly** | `true` | `true` |
| **Cookie SameSite** | `Lax` | `Lax` |
| **Cookie Path** | `/` | `/` |
| **응답 바디 토큰** | 없음 (`null`) | 포함 (`app.auth.token-in-response: true`) |

### 3.2 프론트 처리

```
prod 환경:
├── ATK/RTK는 브라우저가 자동으로 쿠키로 관리
├── fetch/axios 요청 시 credentials: 'include' 필수
└── 토큰 값을 JS에서 직접 읽을 수 없음

dev 환경:
├── 쿠키 자동 관리 + 응답 헤더/바디에서도 토큰 확인 가능
├── 디버깅 목적으로 이중 제공
└── credentials: 'include' 설정 권장 (쿠키도 함께 받으려면)
```

---

## 4. API 레퍼런스

### 4.1 게스트 로그인

```
POST /api/v1/auth/guest
인증: 불필요 (Public)
```

**요청**
```json
// Request Body (optional)
{
  "fcmToken": "string (optional)",
  "device": {
    "name": "string (optional)",
    "version": "string (optional)",
    "layout": "string (optional)",
    "prerelease": "string (optional)",
    "os": "string (optional)",
    "manufacturer": "string (optional)",
    "product": "string (optional)",
    "description": "string (optional)",
    "ua": "string (optional)"
  }
}
```

**응답 (201 Created)**
```json
{
  "data": {
    "memberId": 12,
    "role": "GUEST",
    "name": "게스트_a1b2",
    "accessToken": null,
    "refreshToken": null
  }
}
```
> `accessToken`, `refreshToken`은 dev 환경에서만 포함

**시퀀스**
```
┌──────┐                    ┌──────────┐                    ┌───────┐
│Client│                    │  Server  │                    │  DB   │
└──┬───┘                    └────┬─────┘                    └───┬───┘
   │  POST /auth/guest           │                              │
   │  {fcmToken, device}         │                              │
   │────────────────────────────▶│                              │
   │                             │  Member(GUEST) 생성          │
   │                             │─────────────────────────────▶│
   │                             │  MemberAuth(GUEST_KEY) 생성  │
   │                             │─────────────────────────────▶│
   │                             │  DeviceSession upsert        │
   │                             │─────────────────────────────▶│
   │                             │  ATK/RTK 생성                │
   │                             │  Set-Cookie: access_token    │
   │  201 Created                │  Set-Cookie: refresh_token   │
   │◀────────────────────────────│                              │
   │  {memberId, role, name}     │                              │
   │                             │                              │
```

**프론트 처리**
1. 응답 수신 후 `credentials: 'include'`로 후속 요청 시 쿠키 자동 전송
2. `role: "GUEST"` 저장 → UI에서 게스트 상태 표시
3. 게스트는 제한된 기능만 사용 가능 → Google 로그인 유도

---

### 4.2 Google OAuth 로그인

#### 4.2.1 OAuth 설정 조회

```
GET /api/v1/auth/config/google
인증: 불필요 (Public)
```

**응답 (200)**
```json
{
  "data": {
    "clientId": "xxx.apps.googleusercontent.com",
    "scopes": ["openid", "profile", "email"],
    "redirectUri": "https://app.example.com/oauth/callback"
  }
}
```

#### 4.2.2 OAuth 시작

```
POST /api/v1/auth/oauth/start
인증: 불필요 (Public)
```

**응답 (200)**
```json
{
  "data": {
    "googleAuthUrl": "https://accounts.google.com/o/oauth2/v2/auth?client_id=...&state=..."
  }
}
```

#### 4.2.3 토큰 교환

```
POST /api/v1/auth/token
인증: 불필요 (Public)
```

**요청**
```json
{
  "code": "string (Google authorization code)",
  "state": "string (oauth/start에서 받은 state)",
  "accessToken": "string (optional, 게스트 상태에서 병합 시 현재 ATK)"
}
```

**응답 (200) - 기존 회원**
```json
{
  "data": {
    "status": "SUCCESS",
    "newUser": false,
    "memberId": 12,
    "role": "MEMBER",
    "name": "홍길동",
    "position": "학생",
    "department": "컴퓨터공학과",
    "accessToken": null,
    "refreshToken": null
  }
}
```

**응답 (200) - 신규 회원**
```json
{
  "data": {
    "status": "SUCCESS",
    "newUser": true,
    "memberId": 13,
    "role": "MEMBER",
    "name": "홍길동",
    "position": "학생",
    "department": "컴퓨터공학과",
    "accessToken": null,
    "refreshToken": null
  }
}
```

**응답 (409) - 게스트 데이터 병합 필요**
```json
{
  "data": {
    "status": "MERGE_REQUIRED",
    "newUser": false,
    "mergeTicket": "eyJhbGciOiJIUzI1NiJ9..."
  }
}
```
> 게스트 상태에서 기존 Google 계정으로 로그인 시 409 응답과 함께 `mergeTicket`을 반환합니다.

**시퀀스**
```
┌──────┐       ┌──────────┐       ┌──────────┐       ┌────────┐
│Client│       │  Server  │       │  Google  │       │   DB   │
└──┬───┘       └────┬─────┘       └────┬─────┘       └───┬────┘
   │  POST /oauth/start                │                  │
   │──────────────────────────────────▶│                  │
   │◀──────────────────────────────────│                  │
   │  {googleAuthUrl}                  │                  │
   │                                   │                  │
   │  Redirect to googleAuthUrl        │                  │
   │─────────────────────────────────────────────────────▶│
   │                                   │                  │
   │  Google 로그인 & 인가 code 받음    │                  │
   │◀─────────────────────────────────────────────────────│
   │  {code, redirect}                 │                  │
   │                                   │                  │
   │  POST /auth/token                 │                  │
   │  {code, state}                    │                  │
   │──────────────────────────────────▶│                  │
   │                                   │  state 검증     │
   │                                   │  (Redis)        │
   │                                   │                  │
   │                                   │  code → id_token │
   │                                   │─────────────────▶│
   │                                   │◀─────────────────│
   │                                   │                  │
   │                                   │  JWKS 서명 검증  │
   │                                   │  hd=mju.ac.kr   │
   │                                   │  name 파싱       │
   │                                   │                  │
   │                                   │  Member 조회/생성│
   │                                   │─────────────────▶│
   │  200 OK                           │                  │
   │  Set-Cookie: ATK, RTK             │                  │
   │◀──────────────────────────────────│                  │
   │  {newUser, role, name, ...}       │                  │
   │                                   │                  │
```

**프론트 처리**
1. `POST /auth/oauth/start` → `googleAuthUrl` 받기
2. `window.location.href = googleAuthUrl` → Google 로그인 페이지로 리다이렉트
3. Google 로그인 완료 → `redirectUri`로 `code`와 `state`와 함께 콜백
4. 콜백 페이지에서 `POST /auth/token`에 `{code, state}` 전송
5. 응답에서 `newUser` 확인:
   - `true` → 개인정보 동의 페이지로 이동
   - `false` → 메인 페이지로 이동 (이미 동의 완료)

---

### 4.3 개인정보 동의

```
POST /api/v1/auth/privacy/agree
인증: 필요 (ROLE_MEMBER 이상)
```

**요청**: Body 없음

**응답 (200)**
```json
{
  "data": {
    "memberId": 12,
    "privacyPolicyAgreed": true,
    "agreedAt": 1700000000000
  }
}
```

**시퀀스**
```
┌──────┐                    ┌──────────┐                    ┌───────┐
│Client│                    │  Server  │                    │  DB   │
└──┬───┘                    └────┬─────┘                    └───┬───┘
   │  POST /privacy/agree        │                              │
   │  (ATK 쿠키 자동 전송)       │                              │
   │────────────────────────────▶│                              │
   │                             │  MemberAgreement 생성/갱신   │
   │                             │─────────────────────────────▶│
   │                             │  RTK로 ATK 재발급            │
   │                             │  (agreed=true 포함)          │
   │  200 OK                     │  Set-Cookie: 새 ATK          │
   │◀────────────────────────────│  Set-Cookie: 새 RTK          │
   │  {memberId, agreed, ...}    │                              │
   │                             │                              │
```

**프론트 처리**
1. `newUser: true` 응답 후 개인정보 동의 UI 표시
2. 사용자가 동의 버튼 클릭 → `POST /auth/privacy/agree` 호출
3. 성공 시: ATK가 자동으로 재발급되어 `agreed=true` 포함
4. 이후 보호된 기능 접근 가능
5. 메인 페이지로 이동

---

### 4.4 토큰 재발급 (Refresh)

```
POST /api/v1/auth/refresh
인증: 불필요 (Public, RTK 쿠키로 인증)
```

**요청**: Body 없음 (RTK는 쿠키로 자동 전송)

**응답 (200)**
```json
{
  "data": {
    "status": "success",
    "role": "MEMBER",
    "accessToken": null,
    "refreshToken": null
  }
}
```

**시퀀스**
```
┌──────┐                    ┌──────────┐                    ┌───────┐
│Client│                    │  Server  │                    │  DB   │
└──┬───┘                    └────┬─────┘                    └───┬───┘
   │  (ATK 만료로 401 수신)      │                              │
   │                             │                              │
   │  POST /auth/refresh         │                              │
   │  (RTK 쿠키 자동 전송)       │                              │
   │────────────────────────────▶│                              │
   │                             │  RTK 유효성 확인            │
   │                             │─────────────────────────────▶│
   │                             │  만료 체크                   │
   │                             │                              │
   │                             │  새 ATK/RTK 생성             │
   │                             │  RTK 회전 (Rotation)         │
   │                             │─────────────────────────────▶│
   │  200 OK                     │                              │
   │  Set-Cookie: 새 ATK         │                              │
   │  Set-Cookie: 새 RTK         │                              │
   │◀────────────────────────────│                              │
   │  {status, role}             │                              │
   │                             │                              │
```

**프론트 처리**
1. API 요청에서 401 응답 수신 시 자동 호출
2. 동시 다발적 401 → refresh는 1번만 (나머지는 큐 대기)
3. 성공 → 원래 요청 재시도
4. 실패 (AUTH_004) → 로그인 페이지로 리다이렉트

---

### 4.5 게스트 데이터 병합 (Merge)

```
POST /api/v1/auth/login/google/merge
인증: 불필요 (Public)
```

**요청**
```json
{
  "mergeTicket": "string (JWT, 5분 유효)",
  "fcmToken": "string (optional)",
  "device": { ... }
}
```

**응답 (200)**
```json
{
  "data": {
    "memberId": 12,
    "role": "MEMBER",
    "name": "홍길동",
    "position": "학생",
    "department": "컴퓨터공학과",
    "accessToken": null,
    "refreshToken": null
  }
}
```

**시퀀스**
```
┌──────┐                    ┌──────────┐                    ┌───────┐
│Client│                    │  Server  │                    │  DB   │
└──┬───┘                    └────┬─────┘                    └───┬───┘
   │                             │                              │
   │  [게스트 상태에서 Google 로그인 시도]                       │
   │                             │                              │
   │  POST /auth/token           │                              │
   │  {code, state, accessToken} │                              │
   │────────────────────────────▶│                              │
   │                             │  accessToken 파싱            │
   │                             │  → GUEST role 확인           │
   │                             │                              │
   │                             │  Google 인증 성공            │
   │                             │  기존 Google 계정 발견       │
   │                             │                              │
   │                             │  MergeTicket 생성            │
   │                             │  (guestMemberId+googleSubId) │
   │                             │                              │
   │  409 Conflict               │                              │
   │  {status:MERGE_REQUIRED,    │                              │
   │   mergeTicket: "..."}       │                              │
   │◀────────────────────────────│                              │
   │                             │                              │
   │  POST /auth/login/google/merge                            │
   │  {mergeTicket, device, fcmToken}                          │
   │────────────────────────────▶│                              │
   │                             │  MergeTicket 검증            │
   │                             │  SingleGame 기록 이전        │
   │                             │─────────────────────────────▶│
   │                             │  GUEST_KEY 삭제              │
   │                             │─────────────────────────────▶│
   │                             │  Device 이전                 │
   │                             │─────────────────────────────▶│
   │                             │  Guest Member 삭제           │
   │                             │─────────────────────────────▶│
   │                             │  새 세션 생성                │
   │  200 OK                     │                              │
   │  Set-Cookie: ATK, RTK       │                              │
   │◀────────────────────────────│                              │
   │  {memberId, role, ...}      │                              │
   │                             │                              │
```

**프론트 처리**
1. 게스트 상태에서 Google 로그인 시 `POST /auth/token`에 `accessToken` 포함
2. 서버가 기존 Google 계정 발견 → `409` 응답 + `mergeTicket` 반환
3. 사용자에게 병합 여부 확인 UI 표시
4. 병합 선택 → `POST /auth/login/google/merge`에 `{mergeTicket, device, fcmToken}` 전송
5. 성공 시: 게스트 데이터(싱글게임 기록, 디바이스 세션)가 Google 계정으로 병합됨
6. 병합 후 `newUser`가 `true`이면 개인정보 동의 페이지로 이동

> **중요**: `accessToken`을 보내야 서버가 현재 Guest임을 인식하고 Merge 플로우를 시작합니다.

---

### 4.6 로그아웃

```
POST /api/v1/auth/logout
인증: 필요 (GUEST 이상)
```

**요청**
```json
{
  "fcmToken": "string (optional)"
}
```

**응답 (200)**
```json
{
  "data": null
}
```

**시퀀스**
```
┌──────┐                    ┌──────────┐                    ┌───────┐
│Client│                    │  Server  │                    │  DB   │
└──┬───┘                    └────┬─────┘                    └───┬───┘
   │  POST /auth/logout          │                              │
   │  {fcmToken}                 │                              │
   │────────────────────────────▶│                              │
   │                             │  DeviceSession 삭제          │
   │                             │  (fcmToken 기준)             │
   │                             │─────────────────────────────▶│
   │  200 OK                     │                              │
   │  Set-Cookie: access_token=maxAge=0                         │
   │  Set-Cookie: refresh_token=maxAge=0                        │
   │◀────────────────────────────│                              │
   │                             │                              │
```

**프론트 처리**
1. `POST /auth/logout` 호출
2. 성공 시: 쿠키가 자동으로 삭제됨 (`maxAge=0`)
3. 로컬 상태 초기화 (사용자 정보, 역할 등)
4. 로그인 페이지로 리다이렉트

---

### 4.7 회원 탈퇴

```
DELETE /api/v1/accounts/me
인증: 필요 (GUEST 이상)
```

**요청**: Body 없음

**응답 (200)**
```json
{
  "data": null
}
```

**시퀀스**
```
┌──────┐                    ┌──────────┐                    ┌───────┐
│Client│                    │  Server  │                    │  DB   │
└──┬───┘                    └────┬─────┘                    └───┬───┘
   │  DELETE /accounts/me        │                              │
   │────────────────────────────▶│                              │
   │                             │  MemberAgreement 삭제        │
   │                             │─────────────────────────────▶│
   │                             │  MemberDevice 일괄 삭제      │
   │                             │─────────────────────────────▶│
   │                             │  MemberAuth 삭제             │
   │                             │─────────────────────────────▶│
   │                             │  Member 삭제                 │
   │                             │─────────────────────────────▶│
   │  200 OK                     │                              │
   │  Set-Cookie: access_token=maxAge=0                         │
   │  Set-Cookie: refresh_token=maxAge=0                        │
   │◀────────────────────────────│                              │
   │                             │                              │
```

**프론트 처리**
1. 탈퇴 확인 다이얼로그 표시 (복원 불가 안내)
2. 사용자 확인 → `DELETE /accounts/me` 호출
3. 성공 시: 쿠키 삭제 + 로컬 상태 초기화
4. 로그인 페이지로 리다이렉트

---

### 4.8 내 정보 조회

```
GET /api/v1/accounts/me
인증: 필요 (GUEST 이상)
```

**응답 (200)**
```json
{
  "data": {
    "memberId": 12,
    "role": "MEMBER",
    "name": "홍길동",
    "position": "학생",
    "department": "컴퓨터공학과",
    "isPrivacyPolicyAgreed": true,
    "createdAt": "2024-01-01T00:00:00Z"
  }
}
```

**프론트 처리**
1. 앱 시작 시 또는 상태 복원 시 호출
2. `role` 확인 → UI 분기
3. `isPrivacyPolicyAgreed` 확인 → false면 동의 페이지로

---

### 4.9 테스트 인증 (dev 전용)

```
GET  /api/v1/auth/test-accounts
POST /api/v1/auth/test-login?name=홍길동
POST /api/v1/auth/test-accounts  {role: "MEMBER"}
```

> dev 프로파일에서만 사용 가능. 프로덕션에서는 404 반환.

**프론트 처리**
- dev 환경에서만 테스트 로그인 UI 표시
- `GET /test-accounts`로 계정 목록 조회
- `POST /test-login?name=xxx`로 즉시 로그인
- 응답 바디에 토큰 포함 (`token-in-response: true`)

---

## 5. 에러 코드

### 5.1 Auth 관련 에러

| HTTP | 코드 | 메시지 | 프론트 처리 |
|------|------|--------|-------------|
| 403 | AUTH_001 | 개인정보 동의 필요 | 동의 페이지로 이동 |
| 401 | AUTH_002 | Google 인증 실패 | "Google 인증에 실패했습니다" 알림 |
| 401 | AUTH_003 | 토큰 서명 유효하지 않음 | 토큰 삭제 후 로그인 페이지로 |
| 401 | AUTH_004 | RTK 만료/불일치 | 로그인 페이지로 리다이렉트 |
| 409 | AUTH_005 | 게스트 데이터 병합 필요 | Merge 플로우 시작 |
| 400 | AUTH_006 | Merge Ticket 만료 | Google OAuth부터 다시 시작 |
| 404 | AUTH_007 | 회원 없음 | 로그인 페이지로 |
| 404 | AUTH_008 | 게스트 없음 | 로그인 페이지로 |
| 409 | AUTH_009 | 이미 존재하는 인증 키 | "이미 등록된 계정입니다" 알림 |
| 403 | AUTH_010 | mju.ac.kr 아님 | "명지대 이메일만 사용 가능합니다" 알림 |

### 5.2 Security 관련 에러

| HTTP | 코드 | 메시지 | 프론트 처리 |
|------|------|--------|-------------|
| 401 | GLOBAL_SECURITY_001 | 인증 없음 | 로그인 페이지로 |
| 403 | GLOBAL_SECURITY_002 | 권한 부족 | "접근 권한이 없습니다" 알림 |

### 5.3 공통 에러

| HTTP | 코드 | 메시지 | 프론트 처리 |
|------|------|--------|-------------|
| 400 | GLOBAL_001 | 잘못된 요청 | 입력값 검증 안내 |
| 400 | GLOBAL_002 | 유효성 검증 실패 | 필드별 에러 메시지 표시 |
| 404 | GLOBAL_003 | 리소스 없음 | "찾을 수 없습니다" |
| 500 | GLOBAL_004 | 서버 오류 | "일시적 오류, 잠시 후 다시 시도" |

---

## 6. 구현 체크리스트

### 6.1 필수 구현

```
┌─────────────────────────────────────────────────────────────────────┐
│                     MUST HAVE (필수 구현)                           │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  HTTP 클라이언트 설정                                               │
│  ├── credentials: 'include' (쿠키 자동 전송)                       │
│  ├── CORS: 서버가 허용한 헤더 확인                                │
│  └── Content-Type: application/json                                │
│                                                                     │
│  응답 인터셉터                                                      │
│  ├── 401 → 토큰 refresh 시도                                       │
│  ├── 401 (refresh 실패) → 로그인 페이지                           │
│  ├── 403 (AUTH_001) → 개인정보 동의 페이지                        │
│  ├── 409 (AUTH_005) → Merge 플로우                                │
│  └── 403 (GLOBAL_SECURITY_002) → 접근 불가 알림                   │
│                                                                     │
│  토큰 Refresh                                                       │
│  ├── 동시 401 → 1번만 refresh                                      │
│  ├── refresh 중 대기 큐 구현                                        │
│  └── refresh 성공 → 대기 요청 재시도                               │
│                                                                     │
│  상태 관리                                                          │
│  ├── 현재 사용자 role (GUEST/MEMBER/ADMIN)                         │
│  ├── 개인정보 동의 여부 (isPrivacyPolicyAgreed)                    │
│  └── 로그인 상태 (쿠키 존재 여부로 판단)                          │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 6.2 플로우별 구현

```
┌─────────────────────────────────────────────────────────────────────┐
│                   기능별 구현 목록                                  │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  1. 앱 시작                                                         │
│     └── GET /accounts/me → 사용자 상태 확인                         │
│         ├── 토큰 없음 → 로그인 선택 화면                          │
│         ├── GUEST → 게스트 메인 (Google 로그인 유도)               │
│         ├── MEMBER + 미동의 → 개인정보 동의 페이지                 │
│         └── MEMBER + 동의 → 메인 화면                              │
│                                                                     │
│  2. 게스트 로그인                                                   │
│     └── POST /auth/guest → 세션 시작                               │
│                                                                     │
│  3. Google 로그인                                                   │
│     ├── GET /auth/config/google → clientId, redirectUri            │
│     ├── POST /auth/oauth/start → googleAuthUrl                     │
│     ├── Google 리다이렉트 → code 획득                              │
│     ├── POST /auth/token → {code, state, accessToken(Guest이면)}   │
│     │   ├── 200 + newUser=true → 개인정보 동의 페이지              │
│     │   ├── 200 + newUser=false → 메인 화면                        │
│     │   └── 409 + mergeTicket → Merge 플로우                       │
│                                                                     │
│  4. 개인정보 동의                                                   │
│     └── POST /auth/privacy/agree → 동의 완료                       │
│         └── ATK 재발급 (agreed=true)                                │
│                                                                     │
│  5. Merge (게스트 → Google)                                        │
│     └── POST /auth/login/google/merge → {mergeTicket}              │
│         └── 성공 시 newUser 확인 → 동의 또는 메인                  │
│                                                                     │
│  6. 로그아웃                                                        │
│     └── POST /auth/logout → 세션 종료                              │
│                                                                     │
│  7. 회원 탈퇴                                                       │
│     └── DELETE /accounts/me → 완전 삭제                            │
│                                                                     │
│  8. 토큰 갱신 (자동)                                               │
│     └── 401 감지 → POST /auth/refresh → 원래 요청 재시도          │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 6.3 주의사항

```
┌─────────────────────────────────────────────────────────────────────┐
│                     주의사항                                        │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  1. Google OAuth state 관리                                         │
│     ├── POST /oauth/start에서 받은 state를 반드시 저장             │
│     ├── POST /auth/token에 동일한 state 전달                       │
│     └── state는 5분 TTL (Redis) → 만료 시 재시작                   │
│                                                                     │
│  2. Merge Ticket                                                    │
│     ├── POST /auth/token에서 409 응답 시 서버가 생성하여 반환      │
│     ├── 5분 유효                                                    │
│     ├── 일회용 (사용 후 소멸)                                       │
│     └── 만료 시 Google OAuth부터 다시 시작                         │
│                                                                     │
│  3. Google name 형식                                                │
│     ├── 서버는 "이름/직책/학과" 형식으로 파싱                     │
│     └── Google displayName을 이 형식으로 설정해야 함               │
│                                                                     │
│  4. Cookie 관리                                                     │
│     ├── credentials: 'include' 필수                                │
│     ├── prod: Secure 쿠키 → HTTPS 필수                            │
│     └── 로그아웃/탈퇴 시 서버가 쿠키 삭제 (maxAge=0)              │
│                                                                     │
│  5. FCM Token                                                       │
│     ├── 게스트 로그인/merge 시 함께 전달                           │
│     └── 로그아웃 시 해당 기기의 세션만 삭제                       │
│                                                                     │
│  6. role 기반 UI 분기                                               │
│     ├── GUEST: 제한된 기능, Google 로그인 배너 표시               │
│     ├── MEMBER: 모든 기능 (단, 동의 필요)                         │
│     └── ADMIN: 모든 기능 + 시스템 설정                            │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```
