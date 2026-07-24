# Logging Strategy

다중 인스턴스 환경에서의 로깅 전략을 정의합니다.

---

## 1. 로깅 레벨 정책

| 레벨 | 사용 시점 | 예시 |
|------|-----------|------|
| `ERROR` | 예상치 못한 예외, 시스템 장애 | SQLException, Redis 연결 실패, OOM |
| `WARN` | 의도된 비즈니스 예외, 일시적 장애, 느린 요청 | `BaseException`, 캐시 장애, API 응답 > 1초 |
| `INFO` | 시스템 시작/종료, 설정 변경, 주요 이벤트 | `@PostConstruct` 초기화, admin 설정 토글 |
| `DEBUG` | 개발 중 상세 흐름 | SQL 파라미터, 캐시 miss, 요청/응답 디버깅 |

### 원칙

- `ERROR` = "당장 조치가 필요"
- `WARN` = "정상 흐름이지만 주목할 것"
- `INFO` = "운영에서도 확인해야 할 이벤트"
- `DEBUG` = "개발 중에만 사용, prod에서는 비활성화"

---

## 2. 로깅 포맷

### dev

사람이 읽기 편하게 컬러 + 가독성 중심:

```
%d{yyyy-MM-dd HH:mm:ss} %highlight(%-5level) [%thread] %logger{36} - %msg%n
```

### prod

JSON 구조화 (ELK, Datadog 등 로그 수집기가 바로 파싱 가능하도록):

```json
{
  "timestamp": "2026-06-03T19:00:11.161Z",
  "level": "WARN",
  "requestId": "2fb4c91a",
  "instanceId": "web-01",
  "logger": "c.m.g.a.e.GlobalExceptionHandler",
  "message": "BaseException: code=GLOBAL_003, message=Resource not found."
}
```

### MDC 필드

모든 로그 라인에 자동으로 포함:

| 필드 | 출처 | 설정 시점 |
|------|------|-----------|
| `requestId` | `GlobalMetaFilter` UUID | 매 요청 시작 |
| `instanceId` | `InstanceIdProvider` | 앱 시작 1회 |

---

## 3. InstanceId & MDC 설정

### InstanceIdProvider

```java
@Component
public class InstanceIdProvider {
    private final String instanceId;

    public InstanceIdProvider() {
        this.instanceId = resolveInstanceId();
    }

    public String getInstanceId() {
        return instanceId;
    }

    private String resolveInstanceId() {
        String id = System.getenv("INSTANCE_ID");               // 1. 운영자 지정
        if (id != null) return id;

        try {
            return InetAddress.getLocalHost().getHostName();    // 2. hostname
        } catch (Exception ignored) {
            return UUID.randomUUID().toString().substring(0, 8); // 3. fallback
        }
    }
}
```

| 환경 | instanceId 획득 방식 |
|------|---------------------|
| `docker run -e INSTANCE_ID=web-01` | 환경변수 `INSTANCE_ID` |
| VM/베어메탈 직접 실행 | `InetAddress.getLocalHost().getHostName()` |
| 개발 PC | hostname (맥북 이름 등) |

### GlobalMetaFilter (MDC 주입)

```java
@Override
protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
    String requestId = UUID.randomUUID().toString();

    MDC.put("requestId", requestId);
    MDC.put("instanceId", instanceIdProvider.getInstanceId());

    try {
        // ... 기존 로직 ...
        filterChain.doFilter(request, response);
    } finally {
        MDC.clear();
        CustomResponseMetaContextHolder.clear();
    }
}
```

**주의**: `MDC.clear()`는 `finally`에서 반드시 실행해야 ThreadLocal 메모리 누수를 방지합니다.

---

## 4. 성능 로깅

### 느린 API 요청 감지

`GlobalMetaFilter`에서 `durationMs` 기반 임계값 확인:

| 임계값 | 레벨 | 액션 |
|--------|------|------|
| > 1,000ms | WARN | `log.warn("Slow request: {} {} took {}ms", method, path, durationMs)` |
| > 5,000ms | ERROR | `log.error("Very slow request: {} {} took {}ms", method, path, durationMs)` |

임계값은 `application.yml`에서 관리:

```yaml
app:
  performance:
    slow-ms: 1000        # WARN 로그 임계값
    very-slow-ms: 5000   # ERROR 로그 임계값
```

- `slow-ms`: WARN 로그 임계값 (기본 1000ms)
- `very-slow-ms`: ERROR 로그 임계값 (기본 5000ms)

환경별 프로파일(`application-dev.yml`, `application-prod.yml`)에서 다르게 설정 가능.

### 느린 DB 쿼리

Hibernate `log_slow_query` 프로퍼티로 감지:

```yaml
spring:
  jpa:
    properties:
      hibernate:
        generate_statistics: true
        log_slow_query: 1000
```

임계값은 `application.yml`에서 설정하며, 변경 시 재시작이 필요합니다. 느린 쿼리는 `org.hibernate.SQL_SLOW` 로거에 기록됩니다.

---

## 5. 서비스 레이어 로깅

"로깅 과잉"을 피하고 필요한 정보만:

| 상황 | 레벨 | 내용 | 비고 |
|------|------|------|------|
| 쓰기 작업 완료 | `DEBUG` | 엔티티 ID, 작업 타입 | create/update/delete |
| 외부 API 호출 | `INFO` | 대상, 소요 시간, 응답 코드 | - |
| 예외 발생 | — | 서비스에서는 로깅 금지 | `GlobalExceptionHandler`가 일괄 처리 |

### 금지 사항

- **DTO/Entity 전체를 로깅하지 않음**: 민감 정보 + 로그 폭발
- **비즈니스 예외를 서비스에서 로깅하지 않음**: `GlobalExceptionHandler`에 위임
- **요청 파라미터 전체 logging 금지**: 비밀번호, 토큰 등 유출 위험

---

## 6. 예외 로깅

`GlobalExceptionHandler`에서 중앙 집중 처리:

| 예외 | 레벨 | Stacktrace | 로깅 내용 |
|------|------|------------|-----------|
| `BaseException` | `WARN` | X | `code={}, message={}` |
| `MethodArgumentNotValidException` | `WARN` | X | `details={}` (필드 개수) |
| `ConstraintViolationException` | `WARN` | X | `details={}` (필드 개수) |
| `HttpMessageNotReadableException` | `WARN` | X | `{}` (원본 메시지) |
| `MissingServletRequestParameterException` | `WARN` | X | `{}` (원본 메시지) |
| `MethodArgumentTypeMismatchException` | `WARN` | X | `{}` (원본 메시지) |
| `NoResourceFoundException` | `WARN` | X | `{}` (원본 메시지) |
| `Exception` (기타) | `ERROR` | O | 전체 스택 (`log.error("...", exception)`) |

### 원칙

- 의도된 예외(`BaseException`, validation)는 stacktrace 없이 로깅
- 예상치 못한 예외만 stacktrace 포함
- 민감 정보 마스킹: `GlobalExceptionHandler`에서 응답에 포함하지 않음 (`expose_error_details` off)

---

## 7. 로그 파일 관리

`logback-spring.xml` 구성:

| 설정 | dev | prod |
|------|-----|------|
| 콘솔 출력 | O (컬러) | X |
| 파일 출력 | X | O |
| 파일 경로 | — | `logs/${INSTANCE_ID:-app}-application.log` |
| Rolling 정책 | — | 일별 + 100MB 초과 |
| 압축 | — | gzip |
| 최대 보관 | — | 30일 |
| 총 용량 제한 | — | 10GB |

### prod logback-spring.xml 예시

```xml
<configuration>
    <springProperty name="INSTANCE_ID" source="app.instance-id" defaultValue="app"/>

    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/${INSTANCE_ID}-application.log</file>
        <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>logs/${INSTANCE_ID}-application.%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
            <maxFileSize>100MB</maxFileSize>
            <maxHistory>30</maxHistory>
            <totalSizeCap>10GB</totalSizeCap>
        </rollingPolicy>
    </appender>

    <root level="INFO">
        <appender-ref ref="FILE"/>
    </root>
</configuration>
```

### 의존성

`build.gradle`에 Logstash encoder 추가 (prod JSON 로깅용):

```groovy
implementation 'net.logstash.logback:logstash-logback-encoder:8.0'
```

---

## 8. 다중 인스턴스 로그 식별

로그 한 줄로 어느 인스턴스의 어떤 요청인지 식별 가능:

```
# dev 콘솔
2026-06-03 19:00:11 WARN  [req=2fb4c91a] [instance=web-01] BaseException: code=GLOBAL_003

# prod JSON
{"requestId":"2fb4c91a","instanceId":"web-01","level":"WARN","message":"BaseException: ..."}
```

클라이언트가 받은 `X-Request-Id` 응답 헤더와 로그의 `requestId`가 일치하므로, 운영 중 "이 에러 뭐예요?" 질문에 로그에서 바로 추적 가능.
