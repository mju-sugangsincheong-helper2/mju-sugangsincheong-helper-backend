# Auth & Member Architecture

본 문서는 서비스의 인증(Auth), 인가(Authorization), 세션(Session) 및 회원(Account) 도메인의 아키텍처 설계 의도와 개념적 구조를 정의합니다.

---

## 1. 핵심 설계 원칙 및 책임 분리

인증 및 회원 시스템은 책임을 기준으로 크게 **Security**, **Auth**, **Account** 3대 영역으로 분리하여 설계합니다.

| 도메인 영역 | 패키지 위치 | 주 책임 | 핵심 역할 |
|:---|:---|:---|:---|
| **Security** (보안 필터) | `global.security` | 외부 요청의 일차적 관문 통제 및 신원 확립 | 쿠키/헤더에서 JWT를 추출하고 서명을 무상태(Stateless)로 검증하여 SecurityContext에 인증 정보 등록, 개인정보 동의 감사 여부 사전 차단 |
| **Auth** (인증 메커니즘) | `auth/` | 인증 수단별 신원 검증 및 세션/토큰 관리 | 게스트 생성, Google OAuth 연동, 게스트 데이터 병합, ATK/RTK 발급/회수 및 디바이스 세션 관리, dev 환경 한정 테스트 계정 |
| **Account** (회원 리소스) | `account/` | 회원 프로필 데이터 및 부가 상태 관리 | 본인 프로필 조회, 개인정보 동의 감사 기록(Consent Log) 생성/갱신, 회원 탈퇴(Withdrawal) 시 데이터 일괄 정리 |

---

## 2. 도메인별 세부 구조 및 책임

### 2.1 Security (보안 및 필터)
- **JwtAuthenticationFilter**: 모든 인증이 필요한 요청에 대해 JWT 서명과 유효성을 검증합니다. DB 조회를 일절 배제하여 무상태성을 보장하며, `agreed` 클레임을 요청 attribute에 저장합니다.
- **ConsentCheckFilter**: SecurityContext의 권한과 `privacyAgreed` 플래그를 종합해 MEMBER/ADMIN 권한 사용자가 동의하지 않은 경우 403(`AUTH_PRIVACY_POLICY_REQUIRED`)을 반환합니다. 단, `/auth/privacy/agree`, `/auth/logout` 경로는 차단을 면제합니다.
- **TokenExtractor**: 환경별(개발/운영) 토큰 추출 전략을 캡슐화합니다.
- **GlobalSecurityConfig**: ① 공개 URL용 SecurityFilterChain, ② `/api/**` 인증 필수 SecurityFilterChain 두 개의 체인을 등록하며 CORS, CSRF, 세션 정책 및 역할 계층(Role Hierarchy)을 설정합니다.

### 2.2 Auth (인증 기능 분리 - Feature-driven)
- **guest**: 서버측에서 고유 임의 키(UUID)를 발급하여 임시 게스트 회원 세션을 형성합니다.
- **oauth**: Google 제공자로부터 ID Token을 발급받아 명지대 도메인(`mju.ac.kr`)을 검증하고 멤버 신원을 확립합니다. Google `name` 클레임은 `이름/직책/학과` 형식으로 파싱됩니다.
- **merge**: 게스트 이용 데이터(디바이스 세션 등)를 구글 계정 신원으로 안전하게 이관하고 기존 게스트 데이터를 제거합니다.
- **session**: JWT 토큰 발급 및 파싱(`TokenProvider`), DB 기반 디바이스 세션 관리 및 로그인 상태 회수를 총괄합니다.
- **test**: `dev` 프로파일 한정 테스트 계정 자동 시드 및 테스트 로그인 엔드포인트를 제공합니다.

### 2.3 Account (회원 데이터 및 생명주기)
- **profile**: 회원 본인의 정보(`me`) 조회 및 회원 탈퇴(`withdraw`)를 처리합니다. 컨트롤러 레벨에서 `@PreAuthorize("hasRole('GUEST')")`로 최소 GUEST 등급을 보장합니다.
- **consent**: 규제 준수 조항에 따른 개인정보 제공 동의서 감사 로그를 기록하고, 동의 완료 시 ATK를 재발급하여 `agreed` 클레임을 갱신합니다.

---

## 3. 역할 계층 (Role Hierarchy)

스프링 시큐리티 계층 설정을 통해 상위 권한은 하위 권한을 자동으로 포함합니다.
```
ROLE_ADMIN > ROLE_MEMBER > ROLE_GUEST
```
- **ROLE_GUEST**: 임시 게스트 회원 세션 권한
- **ROLE_MEMBER**: Google 계정 도메인이 검증되고 가입이 완료된 학생 권한. 약관 동의를 마친 후 보호된 모든 리소스에 접근 가능
- **ROLE_ADMIN**: 관리자 권한

---

## 4. 개념적 패키지 구조

인증/회원 도메인은 명확한 책임 분리를 위해 계층형이 아닌 책임/피처 기반의 구조를 가집니다.

```
src/main/java/com/mjusugangsincheonghelper/
│
├── global/
│   └── security/                             # 보안 필터 및 인프라 영역 (Security Policy)
│
├── account/                                  # 회원 정보 및 규제 준수 영역 (Account Resource)
│
└── auth/                                     # 다양한 신원 확인 및 토큰/세션 라이프사이클 영역 (Auth Mechanism)
    ├── common/                               # 공통 도메인/DTO
    ├── guest/                                # 임시 세션 발급 기능
    ├── oauth/                                # Google 소셜 연동 로그인
    ├── merge/                                # 게스트 -> 멤버 데이터 병합
    ├── session/                              # 토큰 발급/파싱 및 디바이스 세션 관리
    └── test/                                 # 개발 환경 전용 테스트 인증 (Profile="dev")
```

---

## 5. 상세 구현 및 명세 레퍼런스

실제 API 스펙, 메서드 호출 시퀀스, JWT 토큰 페이로드 및 DB ERD, 에러 코드 등에 대한 상세 구현 명세는 아래 문서를 참고하십시오.

* 👉 **[Auth & Security Architecture v2 (상세 구현 명세서)](file:///Users/shinnk/source/project/mju-sugangsincheong-helper/mju-sugangsincheong-helper-backend/docs/auth_architecture2.md)**
