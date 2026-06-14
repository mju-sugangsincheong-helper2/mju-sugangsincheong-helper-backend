# System Config Architecture

본 문서는 system config 도메인의 아키텍처 설계 의도, 책임 분리, 로컬 캐싱 및 동기화 전략을 설명합니다.

---

## 1. 핵심 설계 원칙

시스템 설정은 **설정 정의(Setting Definition)** 패턴을 사용합니다.

| 레이어 | 책임 | 질문 |
|--------|------|------|
| **SettingDefinition** (설정 정의) | 타입, 기본값, 파싱, 검증을 한 곳에 정의 | "이 설정은 어떤 타입이고, 어떻게 파싱하고, 검증할까?" |
| **SystemConfigService** (설정 저장/조회) | DB 저장, 조회, 초기화, JVM 로컬 캐싱 및 무효화 전파 | "설정 값을 어떻게 저장/조회하고, 로컬 캐시를 어떻게 일관성 있게 비울까?" |
| **SystemConfigController** (HTTP API) | 설정 목록/단건/수정 API | "클라이언트가 설정을 어떻게 조회/수정할까?" |

서비스는 **전용 getter를 만들지 않고** `SettingDefinition`이 파싱과 검증을 담당하게 설계되었으나, WAS 전체에서 극도로 빈번하게 조회되는 `current_term` 정보에 한해서만 전용 캐시 메서드(`getCurrentTerm()`)를 두었습니다.

---

## 2. DB 테이블 (`system_config`)

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `config_key` | VARCHAR(100) PK | 설정 키 |
| `config_value` | TEXT NOT NULL | 현재 값 (문자열) |
| `config_type` | VARCHAR(20) NOT NULL | 타입 (STRING, JSON, BOOLEAN) |
| `description` | TEXT | 설명 |
| `created_at` | TIMESTAMP NOT NULL | 생성 시각 |
| `updated_at` | TIMESTAMP NOT NULL | 수정 시각 |

---

## 3. 로컬 캐싱 및 동기화 전략 (Caffeine + Redis Pub/Sub)

시스템 설정은 조회 빈도가 매우 높고 수정 빈도가 거의 없으므로 **JVM 내 메모리 캐시(Caffeine)**에 저장하여 DB 조회를 극적으로 줄입니다. 다중 WAS 환경의 정합성을 맞추기 위해 **Redis Pub/Sub**을 통해 무효화(Evict) 신호를 즉시 전파합니다.

```
[WAS 1 (설정 수정)] 
  ├── 1. DB에 값 업데이트 & 트랜잭션 커밋 완료
  ├── 2. 로컬 Caffeine 캐시 즉시 비우기 (Evict)
  └── 3. Redis Pub/Sub 채널("system-config-evict-topic")에 메시지 발행 (instanceId:cacheKey)
          │
          ▼ (브로드캐스트)
[WAS 2 (다른 인스턴스)]
  └── 1. 메시지 수신 (발행자가 자신이 아님을 확인)
  └── 2. local caffeineCacheManager에서 해당 캐시 키 강제 무효화 (Evict)
```

- **성능 비약 상승**: 설정 데이터가 로컬 메모리에 상주하므로 1회 조회 이후에는 DB 쿼리나 Redis 네트워크 통신 없이 수십 나노초(ns) 만에 데이터를 반환합니다.
- **트랜잭션 정합성**: 캐시 무효화 전파는 반드시 DB 트랜잭션이 성공적으로 완결된 이후(`TransactionSynchronization.afterCommit`)에만 발행되어, 타 노드가 이전 데이터를 다시 읽어 캐싱하는 레이스 컨디션을 방지합니다.
- **자가 전파 방지**: 각 WAS 노드는 시작 시 고유 UUID(`instanceId`)를 생성하며, 수신된 메시지의 발행자가 자신일 경우 로컬 무효화 처리를 스킵합니다.

---

## 4. 코드베이스 아키텍처

### 4.1 패키지 구조

```
system/
├── controller/
│   └── SystemConfigController.java          # GET /, GET /{key}, PUT /{key}
│
├── definition/
│   └── SettingDefinition.java               # 설정 정의 enum 및 DTO 레코드
│       ├── key, type, description, defaultValue
│       ├── parser: String → Object
│       ├── validator: String → boolean
│       ├── parse(rawValue)                  # 파싱 (실패 시 defaultValue)
│       └── getFrom(service)                 # 서비스에서 raw(캐시 적용)를 가져와 파싱
│
├── service/
│   └── SystemConfigService.java             # 설정 저장/조회/동기화
│       ├── initDefaultConfigs()             # 스프링 프로퍼티(@Value)를 활용한 DB 초기 레코드 세팅
│       ├── getRaw(key)                      # Caffeine 캐시(@Cacheable) 기반의 raw value 조회
│       ├── update(key, request)             # DB 업데이트 + 로컬 Evict + Redis Pub/Sub 발행
│       ├── findAll() / find(key)            # 어드민용 조회 API 기능
│       └── getCurrentTerm()                 # current_term 전용 단건 캐시 메서드
│
└── dto/
    ├── SystemConfigResponse.java            # 응답 DTO (정의+현재값+파싱값)
    └── SystemConfigUpdateRequest.java       # 수정 요청 DTO
```

---

## 5. 관리되는 설정 키 목록 (SettingDefinition)

| 설정 키 (config_key) | 타입 | 기본값 (yml/DB) | 설명 | 파싱 대상 (parsedValue) |
|---|---|---|---|---|
| `current_term` | STRING | `"202510"` | 현재 활성화된 학기 설정 (YYYY+학기코드) | `TermCode` 레코드 |
| `notices` | JSON | `[]` | 시스템 공지사항 목록 | `JsonNode` 트리 |
| `announcement` | STRING | `""` | 상단 배너 공지 텍스트 | `String` |
| `expose_error_details` | BOOLEAN | `false` (dev: `true`) | 에러 응답에 원본 예외 상세 정보 포함 여부 | `Boolean` |
| `performance_thresholds` | JSON | `{"slowMs":1000,"verySlowMs":5000}` | 성능 임계값 설정 | `PerformanceThresholds` 레코드 |
| `jwt_expiry_config` | JSON | `{"accessTokenExpiryMs":3600000,...}` | JWT 토큰 만료 시간 설정 (ms) | `JwtExpiryConfig` 레코드 |

---

## 6. 확장 가이드: 새 설정 추가 단계

1. `SettingDefinition.java`에 새 enum 항목과 필요시 파싱 대상 레코드를 정의합니다.
2. 기본값을 스프링 프로퍼티 파일(`application.yml`)과 연동하고 싶을 경우, `SystemConfigService`에 `@Value` 필드를 추가하고 `initDefaultConfigs()` 내부 포맷터에 해당 분기를 매핑해 줍니다.
3. 애플리케이션을 실행하면 `@PostConstruct`에 의해 DB에 값이 자동으로 적재됩니다.
4. 비즈니스 로직에서 `SettingDefinition.NEW_SETTING.getFrom(systemConfigService)`를 호출해 즉시 읽어 사용합니다.
