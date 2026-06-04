# 로그인 로직 문서

## 1. 인증 개요

### 1.1 사용자 신분 (Role)

| 신분 | 식별 수단 | 설명 |
|-----|----------|------|
| **Anonymous** | 없음 | 인증되지 않은 완전한 외부인 |
| **Guest** | ATK/RTK (Cookie) | 게스트 생성 API로 임시 계정 생성 |
| **Member** | ATK/RTK (Cookie) | 명지대 Google 계정으로 인증된 재학생 |
| **Admin** | ATK/RTK (Cookie) | 시스템 관리 권한을 가진 운영자 |

### 1.2 토큰 구조

| 토큰 | 저장 위치 | 만료 시간 | 용도 |
|-----|----------|----------|------|
| **Access Token (ATK)** | HttpOnly Cookie | 1시간 | API 인증 |
| **Refresh Token (RTK)** | HttpOnly Cookie + DB | 7일 | ATK 재발급 |

### 1.3 쿠키 속성

```
HttpOnly: true   (JavaScript 접근 불가)
Secure: true     (HTTPS만 전송)
SameSite: Lax    (CSRF 방지)
Path: /          (전체 도메인)
```

---

## 2. Google OAuth2 로그인 흐름

### 2.1 시퀀스 다이어그램

```
┌─────────┐     ┌─────────────┐     ┌──────────────┐     ┌──────────────┐     ┌────────────┐
│  Client │     │   Backend   │     │ Google OAuth │     │   Database   │     │   Cookie   │
└────┬────┘     └──────┬──────┘     └──────┬───────┘     └──────┬───────┘     └─────┬──────┘
     │                 │                   │                    │                   │
     │ 1. GET /oauth2/authorization/google │                   │                   │
     │────────────────>│                   │                    │                   │
     │                 │                   │                    │                   │
     │ 2. 302 Redirect to Google           │                    │                   │
     │<────────────────│                   │                    │                   │
     │                 │                   │                    │                   │
     │ 3. Google 로그인 진행                │                    │                   │
     │────────────────────────────────────>│                    │                   │
     │                 │                   │                    │                   │
     │ 4. Authorization Code               │                    │                   │
     │<────────────────────────────────────│                    │                   │
     │                 │                   │                    │                   │
     │ 5. GET /login/oauth2/code/google?code=xxx               │                   │
     │────────────────>│                   │                    │                   │
     │                 │                   │                    │                   │
     │                 │ 6. Token Exchange │                    │                   │
     │                 │──────────────────>│                    │                   │
     │                 │                   │                    │                   │
     │                 │ 7. ID Token + User Info                │                   │
     │                 │<──────────────────│                    │                   │
     │                 │                   │                    │                   │
     │                 │ 8. CustomOidcUserService.loadUser()   │                   │
     │                 │───────────────────────────────────────>│                   │
     │                 │                   │                    │                   │
     │                 │                   │   - MJU 도메인 검증 │                   │
     │                 │                   │   - 이름 파싱 (이름/직위/학과)          │
     │                 │                   │   - Member 생성/조회                   │
     │                 │                   │   - MemberAuth 생성                    │
     │                 │                   │                    │                   │
     │                 │ 9. OAuth2LoginSuccessHandler           │                   │
     │                 │───────────────────────────────────────>│                   │
     │                 │                   │                    │                   │
     │                 │                   │   - 게스트 병합 (쿠키 확인)             │
     │                 │                   │   - ATK/RTK 생성                       │
     │                 │                   │   - Device 저장/업데이트               │
     │                 │                   │                    │                   │
     │ 10. Set-Cookie: access_token=xxx    │                    │                   │
     │ 11. Set-Cookie: refresh_token=xxx   │                    │                   │
     │ 12. 302 Redirect to success-redirect-uri                │                   │
     │<────────────────│                   │                    │                   │
     │                 │                   │                    │                   │
```

### 2.2 단계별 설명

#### Step 1-2: Google 로그인 페이지로 리다이렉트
- 클라이언트가 `/oauth2/authorization/google` 접근
- Spring Security가 Google OAuth2 인증 요청 생성
- Google 로그인 페이지로 302 리다이렉트

#### Step 3-4: Google 인증
- 사용자가 Google 계정으로 로그인
- Google이 Authorization Code를 반환

#### Step 5-7: 토큰 교환
- Backend가 Authorization Code를 Google에 전송
- Google이 ID Token + Access Token 반환

#### Step 8: CustomOidcUserService.loadUser()
```java
// 1. Google에서 받은 사용자 정보 로드
OidcUser oidcUser = delegate.loadUser(userRequest);

// 2. MJU 도메인 검증 (hd=mju.ac.kr)
validateMjuDomain(oidcUser);

// 3. 이름 파싱: "이름/직위/학과" 형식
ParsedName parsedName = parseName(oidcUser.getFullName());

// 4. 기존 회원 조회 또는 신규 생성
Member member = findOrCreateMember(googleSubId, parsedName);
```

#### Step 9-12: OAuth2LoginSuccessHandler
```java
// 1. OidcUser에서 googleSubId 추출
String googleSubId = oidcUser.getSubject();

// 2. Member 조회
Member member = memberRepository.findById(memberAuth.getMemberId());

// 3. 게스트 병합 (쿠키에 guest_member_id가 있는 경우)
if (guestMemberId != null) {
    mergeGuestToMember(guestMemberId, member);
}

// 4. ATK/RTK 생성
String accessToken = tokenProvider.createAccessToken(member.getId(), member.getRole().name());
String refreshToken = tokenProvider.createRefreshToken();

// 5. Device 저장/업데이트
deviceService.upsert(member.getId(), refreshToken, fcmToken, deviceInfo);

// 6. 쿠키 설정
response.addHeader("Set-Cookie", authCookieProvider.createAccessTokenCookie(...));
response.addHeader("Set-Cookie", authCookieProvider.createRefreshTokenCookie(...));

// 7. 게스트 쿠키 삭제
clearCookie(request, response, "guest_member_id");
clearCookie(request, response, "guest_fcm_token");

// 8. 리다이렉트
getRedirectStrategy().sendRedirect(request, response, successRedirectUri);
```

### 2.3 Google 이름 형식

명지대 Google 계정의 이름은 다음 형식을 따라야 합니다:
```
이름/직위/학과
```

예시:
- `홍길동/학생/컴퓨터정보통신공학부 컴퓨터공학전공`
- `김교수/교수/소프트웨어융합대학`

파싱 결과:
| 필드 | 값 |
|-----|-----|
| name | 홍길동 |
| position | 학생 |
| department | 컴퓨터정보통신공학부 컴퓨터공학전공 |

---

## 3. 게스트 로그인 흐름

### 3.1 시퀀스 다이어그램

```
┌─────────┐     ┌─────────────┐     ┌──────────────┐
│  Client │     │   Backend   │     │   Database   │
└────┬────┘     └──────┬──────┘     └──────┬───────┘
     │                 │                   │
     │ 1. POST /api/v1/auth/guest          │
     │    { fcmToken, device }             │
     │────────────────>│                   │
     │                 │                   │
     │                 │ 2. Member 생성     │
     │                 │   role: GUEST     │
     │                 │   name: 게스트_xxxx│
     │                 │──────────────────>│
     │                 │                   │
     │                 │ 3. MemberAuth 생성 │
     │                 │   authType: GUEST_KEY
     │                 │   authKey: UUID   │
     │                 │──────────────────>│
     │                 │                   │
     │                 │ 4. ATK/RTK 생성   │
     │                 │                   │
     │                 │ 5. Device 저장    │
     │                 │──────────────────>│
     │                 │                   │
     │ 6. Set-Cookie: access_token=xxx     │
     │ 7. Set-Cookie: refresh_token=xxx    │
     │<────────────────│                   │
     │                 │                   │
```

### 3.2 게스트 계정 생성 로직

```java
// 1. 게스트 키 생성
String guestKey = UUID.randomUUID().toString();
String guestName = "게스트_" + guestKey.substring(0, 4);

// 2. Member 생성
Member member = Member.builder()
    .role(Role.GUEST)
    .name(guestName)
    .build();

// 3. MemberAuth 생성
MemberAuth memberAuth = MemberAuth.builder()
    .memberId(member.getId())
    .authType(AuthType.GUEST_KEY)
    .authKey(guestKey)
    .build();

// 4. ATK/RTK 생성 및 쿠키 설정
// ...
```

---

## 4. 게스트 → 회원 병합 흐름

게스트로 사용하던 사용자가 Google 로그인을 하면, 게스트 데이터가 Google 계정으로 병합됩니다.

### 4.1 병합 조건

- 로그인 전 `guest_member_id` 쿠키가 설정되어 있어야 함
- Google 로그인 성공 시 `OAuth2LoginSuccessHandler`에서 자동 병합

### 4.2 병합 로직

```java
private void mergeGuestToMember(Long guestMemberId, Member member) {
    // 1. 게스트의 MemberAuth 삭제
    MemberAuth guestAuth = memberAuthRepository
        .findByMemberIdAndAuthType(guestMemberId, AuthType.GUEST_KEY)
        .orElse(null);
    
    if (guestAuth != null) {
        memberAuthRepository.delete(guestAuth);
    }
    
    // 2. 게스트 Member 삭제
    memberRepository.deleteById(guestMemberId);
}
```

---

## 5. 토큰 재발급 흐름

### 5.1 시퀀스 다이어그램

```
┌─────────┐     ┌─────────────┐     ┌──────────────┐
│  Client │     │   Backend   │     │   Database   │
└────┬────┘     └──────┬──────┘     └──────┬───────┘
     │                 │                   │
     │ 1. POST /api/v1/auth/refresh        │
     │    Cookie: refresh_token=xxx        │
     │────────────────>│                   │
     │                 │                   │
     │                 │ 2. RTK로 Device 조회
     │                 │──────────────────>│
     │                 │                   │
     │                 │ 3. Device 반환    │
     │                 │<──────────────────│
     │                 │                   │
     │                 │ 4. RTK 만료 확인  │
     │                 │                   │
     │                 │ 5. 새 ATK/RTK 생성│
     │                 │                   │
     │                 │ 6. Device 업데이트│
     │                 │──────────────────>│
     │                 │                   │
     │ 7. Set-Cookie: access_token=new_xxx │
     │ 8. Set-Cookie: refresh_token=new_xxx│
     │<────────────────│                   │
     │                 │                   │
```

---

## 6. 요청 인증 흐름

### 6.1 JwtAuthenticationFilter

```java
@Override
protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
    // 1. TokenExtractor로 토큰 추출
    String accessToken = tokenExtractor.extract(request);
    
    if (accessToken != null) {
        try {
            // 2. ATK 파싱
            TokenClaims claims = tokenProvider.parseAccessToken(accessToken);
            
            // 3. SecurityContext에 인증 정보 설정
            UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(
                    claims.memberId(),
                    null,
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + claims.role()))
                );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception e) {
            log.debug("Invalid access token: {}", e.getMessage());
        }
    }
    
    filterChain.doFilter(request, response);
}
```

### 6.2 TokenExtractor 전략 패턴

```
auth/infrastructure/
├── TokenExtractor.java              (인터페이스)
├── CookieTokenExtractor.java        (prod 전용, @Profile("prod"))
├── BearerTokenExtractor.java        (dev 전용, @Profile("dev"))
└── JwtAuthenticationFilter.java     (TokenExtractor 주입)
```

| 프로파일 | TokenExtractor | Swagger SecurityScheme |
|---------|----------------|----------------------|
| **prod** | `CookieTokenExtractor` | `cookieAuth` |
| **dev** | `BearerTokenExtractor` | `bearerAuth` |

---

## 7. 프로파일별 차이점

### 7.1 TokenExtractor 동작

| 프로파일 | 구현체 | 추출 소스 | 설명 |
|---------|--------|----------|------|
| **prod** | `CookieTokenExtractor` | Cookie만 | 프로덕션 환경, HttpOnly 쿠키 |
| **dev** | `BearerTokenExtractor` | Authorization 헤더만 | Swagger Authorize 버튼 사용 |

### 7.2 Swagger SecurityScheme

| 프로파일 | SecurityScheme | 설명 |
|---------|----------------|------|
| **prod** | `cookieAuth` (APIKEY, COOKIE) | 쿠키 기반 인증 |
| **dev** | `bearerAuth` (HTTP, Bearer) | Authorization 헤더 기반 인증 |

---

## 8. 관련 파일 목록

### 8.1 Infrastructure

| 파일 | 역할 |
|-----|------|
| `JwtAuthenticationFilter.java` | 요청에서 토큰 추출, SecurityContext 설정 |
| `TokenExtractor.java` | 토큰 추출 인터페이스 |
| `CookieTokenExtractor.java` | 쿠키에서 토큰 추출 (prod) |
| `BearerTokenExtractor.java` | Authorization 헤더에서 토큰 추출 (dev) |
| `CompositeTokenExtractor.java` | Cookie + Bearer 추출 (dev) |
| `CustomOidcUserService.java` | Google OIDC 사용자 정보 처리, MJU 도메인 검증 |
| `OAuth2LoginSuccessHandler.java` | 로그인 성공 후 처리 (토큰 발급, 게스트 병합) |
| `TokenProvider.java` | JWT 토큰 생성/파싱 |
| `AuthCookieProvider.java` | 인증 쿠키 생성/삭제 |

### 8.2 Service

| 파일 | 역할 |
|-----|------|
| `AuthService.java` | 게스트 생성, 토큰 재발급, 로그아웃, 병합 |
| `DeviceService.java` | 디바이스 정보 저장/업데이트 |

### 8.3 Entity

| 엔티티 | 테이블 | 설명 |
|-------|-------|------|
| `Member` | member | 회원 정보 (id, role, name, position, department) |
| `MemberAuth` | member_auth | 인증 정보 (memberId, authType, authKey) |
| `MemberDevice` | member_device | 디바이스 정보 (memberId, refreshToken, fcmToken, ...) |

### 8.4 Config

| 파일 | 역할 |
|-----|------|
| `GlobalSecurityConfig.java` | Spring Security 설정 |
| `GlobalOpenApiConfig.java` | Swagger/OpenAPI 설정 |

---

## 9. API 엔드포인트

| 메서드 | 경로 | 인증 | 설명 |
|-------|------|-----|------|
| POST | `/api/v1/auth/guest` | 불필요 | 게스트 계정 생성 |
| POST | `/api/v1/auth/refresh` | RTK (Cookie) | 토큰 재발급 |
| POST | `/api/v1/auth/logout` | ATK (Cookie) | 로그아웃 |
| POST | `/api/v1/auth/login/google/merge` | 불필요 | 게스트 데이터 병합 |
| GET | `/oauth2/authorization/google` | 불필요 | Google 로그인 시작 |
| GET | `/login/oauth2/code/google` | 불필요 | Google 콜백 |
| GET | `/api/v1/members/me` | ATK (Cookie) | 내 정보 조회 |

---

## 10. 에러 코드

| 코드 | 설명 |
|-----|------|
| `AUTH_001` | Validation error |
| `AUTH_002` | Not MJU domain |
| `AUTH_003` | Google auth failed |
| `AUTH_004` | Invalid refresh token |
| `AUTH_005` | Member not found |
| `AUTH_006` | Guest not found |
| `AUTH_007` | Merge ticket expired |
