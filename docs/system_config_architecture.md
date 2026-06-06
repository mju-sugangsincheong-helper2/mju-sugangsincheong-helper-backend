# System Config Architecture

본 문서는 system config 도메인의 아키텍처 설계 의도와 책임 분리를 설명합니다.

---

## 1. 핵심 설계 원칙

시스템 설정은 **설정 정의(Setting Definition)** 패턴을 사용합니다.

| 레이어 | 책임 | 질문 |
|--------|------|------|
| **SettingDefinition** (설정 정의) | 타입, 기본값, 파싱, 검증을 한 곳에 정의 | "이 설정은 어떤 타입이고, 어떻게 파싱하고, 검증할까?" |
| **SystemConfigService** (설정 저장/조회) | DB 저장, 조회, 초기화 | "설정 값을 어떻게 저장하고 가져올까?" |
| **SystemConfigController** (HTTP API) | 설정 목록/단건/수정 API | "클라이언트가 설정을 어떻게 조회/수정할까?" |

서비스는 **전용 getter를 만들지 않습니다**. 모든 파싱과 검증은 `SettingDefinition`이 담당합니다.

---

## 2. DB 테이블

### system_config

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `config_key` | VARCHAR(100) PK | 설정 키 |
| `config_value` | TEXT NOT NULL | 현재 값 (문자열) |
| `config_type` | VARCHAR(20) NOT NULL | 타입 (STRING, JSON, BOOLEAN) |
| `description` | TEXT | 설명 |
| `created_at` | TIMESTAMP NOT NULL | 생성 시각 |
| `updated_at` | TIMESTAMP NOT NULL | 수정 시각 |

---

## 3. 코드베이스 아키텍처

### 3.1 패키지 구조

```
system/
├── controller/
│   ── SystemConfigController.java          # GET /, GET /{key}, PUT /{key}
│
├── definition/
│   └── SettingDefinition.java               # 설정 정의 enum
│       ├── key, type, description, defaultValue
│       ├── parser: String → Object
│       ├── validator: String → boolean
│       ├── parse(rawValue)                  # 파싱 (실패 시 defaultValue)
│       ├── validate(rawValue)               # 검증
│       └── getFrom(service)                 # 서비스에서 raw 가져와 파싱
│
├── service/
│   └── SystemConfigService.java             # 설정 저장/조회
│       ├── initDefaultConfigs()             # @PostConstruct 초기화
│       ├── findAll()                        # 전체 목록
│       ├── find(key)                        # 단건 조회
│       ├── update(key, request)             # 수정
│       └── getRaw(key)                      # raw value 조회 (내부용)
│
── dto/
    ├── SystemConfigResponse.java            # 응답 DTO (정의+현재값+파싱값)
    └── SystemConfigUpdateRequest.java       # 수정 요청 DTO
```

### 3.2 시퀀스 흐름

#### 애플리케이션 시작 시 초기화

```
@PostConstruct initDefaultConfigs()
  └→ SettingDefinition.values() 순회
  └→ DB에 해당 key가 없으면 defaultValue로 저장
```

#### 설정 조회 (API)

```
GET /api/v1/system/configs
  └→ SystemConfigService.findAll()
  └→ SystemConfigResponse.from(entity)
       ├→ SettingDefinition.findByKey(key)
       ├→ defaultValue 주입
       └→ parsedValue 주입 (Definition.parser 적용)
```

#### 설정 사용 (내부)

```
GlobalMetaFilter.logSlowRequest()
  └→ SettingDefinition.PERFORMANCE_THRESHOLDS.getFrom(service)
       └→ service.getRaw(key)
       └→ Definition.parse(rawValue)
       └→ PerformanceThresholds 반환

GlobalExceptionHandler.isExposeErrorDetails()
  └→ SettingDefinition.EXPOSE_ERROR_DETAILS.getFrom(service)
       └→ service.getRaw(key)
       └→ Definition.parse(rawValue)
       └→ Boolean 반환
```

---

## 4. API 엔드포인트

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/api/v1/system/configs` | 모든 설정 목록 (정의 + 현재값 + 파싱값) |
| `GET` | `/api/v1/system/configs/{key}` | 단건 조회 |
| `PUT` | `/api/v1/system/configs/{key}` | 수정 |

### 응답 예시

```json
{
  "data": [
    {
      "configKey": "expose_error_details",
      "configValue": "true",
      "configType": "BOOLEAN",
      "description": "에러 응답에 원본 예외 상세 정보 포함 여부",
      "updatedAt": "2026-06-05T08:00:00Z",
      "defaultValue": "true",
      "parsedValue": true
    },
    {
      "configKey": "performance_thresholds",
      "configValue": "{\"slow_ms\":1000,\"very_slow_ms\":5000}",
      "configType": "JSON",
      "description": "성능 임계값 설정 (slow_ms, very_slow_ms)",
      "updatedAt": "2026-06-05T08:00:00Z",
      "defaultValue": "{\"slow_ms\":1000,\"very_slow_ms\":5000}",
      "parsedValue": { "slowMs": 1000, "verySlowMs": 5000 }
    }
  ]
}
```

---

## 5. 확장 가이드

### 새 설정 추가 단계

1. `SettingDefinition.java`에 enum 항목 추가
2. 플리케이션 재시작 → 자동으로 DB에 기본값 저장
3. 필요한 곳에서 `SettingDefinition.XXX.getFrom(service)` 호출

### 설정 정의 등록 형식

```java
NEW_SETTING(
    "new_setting_key",           // key
    ConfigType.STRING,           // type (STRING, JSON, BOOLEAN)
    "설정 설명",                  // description
    "default_value",             // defaultValue
    raw -> parseLogic(raw),      // parser: String → Object
    raw -> validateLogic(raw)    // validator: String → boolean
);
```

### 예시: 새로운 JSON 설정 추가

```java
RATE_LIMIT_CONFIG(
    "rate_limit_config",
    ConfigType.JSON,
    "API 요청 제한 설정 (requests_per_minute, burst_size)",
    "{\"requests_per_minute\":60,\"burst_size\":10}",
    raw -> {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(raw);
        return new RateLimitConfig(
            node.path("requests_per_minute").asInt(60),
            node.path("burst_size").asInt(10)
        );
    },
    raw -> {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(raw);
            return node.has("requests_per_minute") && node.has("burst_size");
        } catch (Exception e) {
            return false;
        }
    }
);

public record RateLimitConfig(int requestsPerMinute, int burstSize) {}
```
