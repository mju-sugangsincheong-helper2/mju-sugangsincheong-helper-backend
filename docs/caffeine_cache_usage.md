# Caffeine Cache Usage

로컬 캐시가 필요할 때 사용합니다. Redis와 달리 별도 인프라 없이 JVM 메모리에 저장됩니다.

---

## 1. 구조 및 캐시 매니저 설정

### A. 공통 AOP 기반 어노테이션 (`@CaffeineCache`)
- **하나의** Caffeine 인스턴스만 생성되어 aspect 방식으로 동작합니다.
- `key`가 곧 캐시 키이며, 전체 캐시 최대 크기는 2000 entries 입니다.

### B. system_config 전용 Caffeine Cache Manager (`caffeineCacheManager`)
- 데이터 변경이 매우 드문 `system-config`에 한해 메모리 오버헤드를 아끼고 읽기 효율을 극대화하기 위해 JVM 내에 캐싱합니다.
- 스프링 `@Cacheable` 및 `@CacheEvict` 어노테이션에 `cacheManager = "caffeineCacheManager"`를 명시하여 적용합니다.
- TTL은 24시간이며 최대 크기는 100 entries 입니다.

---

## 2. 사용법

### 2.1. 공통 AOP 어노테이션 방식

#### 캐싱
```java
import com.mjusugangsincheonghelper.global.config.CaffeineConfig.CaffeineCache;

@CaffeineCache(key = "blocked_apis", ttl = 30)
public List<String> getBlockedApis() {
    // ...
}
```

#### 캐시 비우기
```java
import com.mjusugangsincheonghelper.global.config.CaffeineConfig.CaffeineCacheEvict;

@CaffeineCacheEvict(key = "blocked_apis")
public void refresh() {
    // 해당 key의 캐시가 제거됨
}
```

### 2.2. system_config 전용 캐시 매니저 방식

#### 캐싱 및 비우기
```java
// caffeineCacheManager 사용을 명시
@Cacheable(value = "system-config", key = "'current_term:' + 'cache'", cacheManager = "caffeineCacheManager")
public String getCurrentTerm() { ... }

@CacheEvict(value = "system-config", key = "'current_term:' + 'cache'", condition = "#configKey.equals('current_term')", cacheManager = "caffeineCacheManager")
public SystemConfigResponse update(String configKey, SystemConfigUpdateRequest request) { ... }
```

---

## 3. 다중 WAS 노드 정합성 처리 (system_config)

`system_config`의 데이터는 JVM 메모리에 직접 저장(L1 캐시)되므로, 다중 WAS 환경에서 인스턴스 간 데이터 불일치가 발생할 수 있습니다. 이를 방지하기 위해 **Redis Pub/Sub을 활용한 실시간 캐시 전파 무효화** 메커니즘을 사용합니다.

```
[WAS 1 (설정 수정)] 
  ├── 1. DB에 값 업데이트 & 트랜잭션 커밋
  ├── 2. 로컬 Caffeine 캐시 즉시 비우기 (Evict)
  └── 3. Redis Pub/Sub 채널("system-config-evict-topic")에 메시지 발행 (instanceId:cacheKey)
          │
          ▼ (브로드캐스트)
[WAS 2 (다른 인스턴스)]
  └── 1. 메시지 수신 (발행자가 자신이 아님을 확인)
  └── 2. local caffeineCacheManager에서 해당 캐시 키 강제 무효화 (Evict)
```

- **트랜잭션 정합성**: 이벤트 발행은 데이터베이스 커밋 완료 후(`TransactionSynchronization.afterCommit`)에 실행되어 데이터 정합성을 보장합니다.
- **중복 처리 방지**: 각 WAS 노드는 구동 시 고유 UUID(`instanceId`)를 생성하며, Pub/Sub 수신 시 발행한 instanceId가 자신과 동일할 경우 로컬 무효화 처리를 건너뜁니다.
