# System Config Architecture

본 문서는 시스템 설정의 아키텍처를 설명합니다. 설정은 **변경 빈도**와 **운영 방식**에 따라 정적 설정과 동적 설정으로 구분됩니다.

---

## 1. 설정 분류

```
┌─────────────────────────────────────────────────────────────────┐
│                    시스템 설정 (System Config)                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────┐    ┌─────────────────────────┐    │
│  │     정적 설정 (Static)    │    │     동적 설정 (Dynamic)   │    │
│  │                         │    │                         │    │
│  │  • application.yml      │    │  • DB (system_config)   │    │
│  │  • @Value 주입          │    │  • Redis 캐시           │    │
│  │  • 재시작으로 변경      │    │  • Admin API로 실시간 변경│    │
│  │  • 환경별 다른 값       │    │  • SettingDefinition    │    │
│  │                         │    │                         │    │
│  │  [종류]                 │    │  [종류]                 │    │
│  │  • 에러 상세 노출 여부  │    │  • 현재 학기            │    │
│  │  • 성능 임계값          │    │  • 공지사항             │    │
│  │  • JWT 만료 시간        │    │  • 배너 공지            │    │
│  │  • 게임 반응시간 범위   │    │                         │    │
│  └─────────────────────────┘    └─────────────────────────┘    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. 정적 설정 (Static Configuration)

### 2.1 개요

정적 설정은 **배포 환경별로 고정된 값**이 필요한 설정입니다. `application.yml`에서 관리되며, 애플리케이션 시작 시 `@Value`로 주입됩니다. 변경 시 **재시작이 필요**합니다.

### 2.2 특징

| 특징 | 설명 |
|------|------|
| **저장 위치** | `application.yml` (프로파일별) |
| **주입 방식** | `@Value("${app.xxx}")` |
| **변경 방법** | yml 수정 후 재시작 |
| **장점** | 타입 안전, IDE 자동완성, 환경별 분리 용이 |
| **단점** | 변경 시 재시작 필요 |

### 2.3 설정 목록

#### 에러 상세 노출

| yml 키 | 주입 대상 | 기본값 | 설명 |
|--------|----------|--------|------|
| `app.expose-error-details` | `GlobalExceptionHandler` | `false` | 에러 응답에 원본 예외 정보 포함 여부 |

```yaml
# application-dev.yml
app:
  expose-error-details: true

# application-prod.yml
app:
  expose-error-details: false
```

```java
// GlobalExceptionHandler.java
public GlobalExceptionHandler(
    @Value("${app.expose-error-details:false}") boolean exposeErrorDetails) {
    this.exposeErrorDetails = exposeErrorDetails;
}
```

#### 성능 임계값

| yml 키 | 주입 대상 | 기본값 | 설명 |
|--------|----------|--------|------|
| `app.performance.slow-ms` | `GlobalMetaFilter` | `1000` | WARN 로그 임계값 (ms) |
| `app.performance.very-slow-ms` | `GlobalMetaFilter` | `5000` | ERROR 로그 임계값 (ms) |

```yaml
app:
  performance:
    slow-ms: 1000
    very-slow-ms: 5000
```

```java
// GlobalMetaFilter.java
public GlobalMetaFilter(
    @Value("${app.performance.slow-ms:1000}") long slowMs,
    @Value("${app.performance.very-slow-ms:5000}") long verySlowMs) {
    this.slowMs = slowMs;
    this.verySlowMs = verySlowMs;
}
```

#### JWT 만료 시간

| yml 키 | 주입 대상 | 기본값 | 설명 |
|--------|----------|--------|------|
| `app.jwt.access-token-expiry-ms` | `TokenProvider` | `3600000` | Access Token 만료 (1시간) |
| `app.jwt.refresh-token-expiry-ms` | `TokenProvider` | `604800000` | Refresh Token 만료 (7일) |
| `app.jwt.merge-ticket-expiry-ms` | `TokenProvider` | `300000` | Merge Ticket 만료 (5분) |

```yaml
app:
  jwt:
    access-token-expiry-ms: 3600000
    refresh-token-expiry-ms: 604800000
    merge-ticket-expiry-ms: 300000
```

```java
// TokenProvider.java
public TokenProvider(
    @Value("${app.jwt.secret}") String secret,
    @Value("${app.jwt.access-token-expiry-ms:3600000}") long accessTokenExpiryMs,
    @Value("${app.jwt.refresh-token-expiry-ms:604800000}") long refreshTokenExpiryMs,
    @Value("${app.jwt.merge-ticket-expiry-ms:300000}") long mergeTicketExpiryMs) {
    // ...
}
```

#### 싱글게임 반응시간 범위

| yml 키 | 주입 대상 | 기본값 | 설명 |
|--------|----------|--------|------|
| `app.singlegame.reaction-time-min-ms` | `SingleGameService` | `1` | 최소 반응시간 (ms) |
| `app.singlegame.reaction-time-max-ms` | `SingleGameService` | `60000` | 최대 반응시간 (ms) |

```yaml
app:
  singlegame:
    reaction-time-min-ms: 1
    reaction-time-max-ms: 60000
```

```java
// SingleGameService.java
public SingleGameService(
    // ...
    @Value("${app.singlegame.reaction-time-min-ms:1}") int reactionTimeMinMs,
    @Value("${app.singlegame.reaction-time-max-ms:60000}") int reactionTimeMaxMs) {
    // ...
}
```

---

## 3. 동적 설정 (Dynamic Configuration)

### 3.1 개요

동적 설정은 **운영 중 실시간 변경**이 필요한 설정입니다. DB에 저장되고 Redis 캐시를 통해 빠르게 조회됩니다. Admin API를 통해 변경할 수 있으며, **재시작 없이 즉시 반영**됩니다.

### 3.2 특징

| 특징 | 설명 |
|------|------|
| **저장 위치** | `system_config` 테이블 |
| **캐시** | Redis (`system-config` cache) |
| **변경 방법** | Admin API (`PUT /api/v1/system/configs/{key}`) |
| **정합성** | Redis 공유로 다중 인스턴스 자동 동기화 |
| **장점** | 실시간 변경, 재시작 불필요 |
| **단점** | DB + Redis 오버헤드 |

### 3.3 아키텍처

```
┌──────────────────────────────────────────────────────────────────┐
│                         동적 설정 흐름                            │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  [읽기]                                                          │
│  Service → Redis Cache (hit) → 반환                              │
│           Redis Cache (miss) → DB → Redis 적재 → 반환            │
│                                                                  │
│  [쓰기]                                                          │
│  Admin API → DB UPDATE → Redis Evict → 다음 읽기 시 재적재       │
│                                                                  │
│  [캐시 키 규칙]                                                   │
│  system-config::{configKey}:cache                                │
│  예: system-config::current_term:cache                           │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

### 3.4 설정 목록

| 설정 키 | 타입 | 기본값 | 설명 | 파싱 타입 |
|---------|------|--------|------|-----------|
| `current_term` | STRING | `"202620"` | 현재 학기 (YYYY+학기코드) | `TermCode` |
| `notices` | JSON | `[]` | 공지사항 목록 | `JsonNode` |
| `announcement` | STRING | `""` | 상단 배너 공지 | `String` |

### 3.5 코드 구조

#### SettingDefinition (설정 정의)

```java
public enum SettingDefinition {
    CURRENT_TERM(
        "current_term",
        ConfigType.STRING,
        "현재 학기 설정 (YYYY + 학기코드: 10=1학기, 15=여름학기, 20=2학기, 25=겨울학기)",
        "202620",
        raw -> new TermCode(raw),
        raw -> raw != null && raw.matches("^20\\d{2}(10|15|20|25)$")
    ),
    NOTICES(
        "notices",
        ConfigType.JSON,
        "공지사항 목록",
        "[]",
        raw -> new ObjectMapper().readTree(raw),
        raw -> { try { new ObjectMapper().readTree(raw); return true; } catch { return false; } }
    ),
    ANNOUNCEMENT(
        "announcement",
        ConfigType.STRING,
        "상단 배너 공지 텍스트",
        "",
        raw -> raw,
        raw -> true
    );

    private final String key;
    private final ConfigType type;
    private final String description;
    private final String defaultValue;
    private final Function<String, Object> parser;
    private final Function<String, Boolean> validator;
}
```

#### SystemConfigService

```java
@Service
public class SystemConfigService {
    private final SystemConfigRepository repository;
    private final CacheManager cacheManager;

    @PostConstruct
    public void initDefaultConfigs() {
        for (SettingDefinition def : SettingDefinition.values()) {
            if (!repository.existsById(def.getKey())) {
                repository.save(SystemConfig.builder()
                    .configKey(def.getKey())
                    .configValue(def.getDefaultValue())
                    .configType(def.getType())
                    .description(def.getDescription())
                    .build());
            }
        }
    }

    @Cacheable(value = "system-config", key = "'current_term:cache'")
    public String getCurrentTerm() {
        return getRaw("current_term");
    }

    @Cacheable(value = "system-config", key = "#configKey + ':cache'")
    public String getRaw(String configKey) {
        return repository.findById(configKey)
            .map(SystemConfig::getConfigValue)
            .orElseGet(() -> {
                SettingDefinition def = SettingDefinition.findByKey(configKey);
                return def != null ? def.getDefaultValue() : null;
            });
    }

    @Transactional
    public SystemConfigResponse update(String configKey, SystemConfigUpdateRequest request) {
        SystemConfig config = repository.findById(configKey)
            .orElseThrow(() -> new BaseException(ErrorCode.SYSTEM_CONFIG_NOT_FOUND));
        config.updateValue(request.getConfigValue(), request.getDescription());

        // Redis 캐시 무효화
        var cache = cacheManager.getCache("system-config");
        if (cache != null) {
            cache.evict(configKey + ":cache");
        }
        return SystemConfigResponse.from(config);
    }
}
```

### 3.6 API

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/api/v1/system/configs` | 설정 전체 목록 조회 |
| `GET` | `/api/v1/system/configs/{key}` | 설정 단건 조회 |
| `PUT` | `/api/v1/system/configs/{key}` | 설정 수정 (Admin 전용) |

---

## 4. DB 테이블

```sql
CREATE TABLE system_config (
    config_key   VARCHAR(100) PRIMARY KEY,
    config_value TEXT NOT NULL,
    config_type  VARCHAR(20) NOT NULL,  -- STRING, JSON, BOOLEAN
    description  TEXT,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW()
);
```

---

## 5. Redis 캐시

### 5.1 캐시 설정

```yaml
# application.yml
app:
  cache:
    ttls:
      system-config: 24h
```

### 5.2 캐시 키 규칙

```
system-config::{configKey}:cache
```

| 예시 | 설명 |
|------|------|
| `system-config::current_term:cache` | 현재 학기 |
| `system-config::notices:cache` | 공지사항 |
| `system-config::announcement:cache` | 배너 공지 |

### 5.3 캐시 무효화

- `update()` 호출 시 `CacheManager.getCache("system-config").evict(key + ":cache")`로 즉시 무효화
- Redis 공유로 다중 인스턴스 환경에서도 정합성 보장

---

## 6. 확장 가이드

### 6.1 정적 설정 추가

1. `application.yml`에 키 추가
2. 소비 클래스에서 `@Value`로 주입

```yaml
# application.yml
app:
  my-new-config: value
```

```java
@Value("${app.my-new-config:default}")
private String myNewConfig;
```

### 6.2 동적 설정 추가

1. `SettingDefinition` enum에 항목 추가
2. 애플리케이션 시작 시 DB에 자동 초기화
3. 비즈니스 로직에서 `SettingDefinition.NEW_KEY.getFrom(systemConfigService)` 호출

```java
public enum SettingDefinition {
    // 기존 항목들...
    
    MY_NEW_SETTING(
        "my_new_setting",
        ConfigType.STRING,
        "새로운 설정 설명",
        "default_value",
        raw -> raw,
        raw -> true
    );
}
```

---

## 7. 설계 원칙

| 원칙 | 설명 |
|------|------|
| **변경 빈도로 분류** | 재시작 가능한가? → 정적 / 실시간 변경 필요? → 동적 |
| **타입 안전** | 정적: `@Value` 타입 검증 / 동적: `SettingDefinition` 파서 |
| **환경 분리** | 정적 설정은 프로파일별 yml로 관리 |
| **캐시 일관성** | 동적 설정은 Redis 공유로 다중 인스턴스 자동 동기화 |
| **장애 허용** | Redis 장애 시 `CacheErrorHandler`가 로깅 후 무시 |
