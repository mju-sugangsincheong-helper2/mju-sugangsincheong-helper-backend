# Caffeine Cache Usage

로컬 캐시가 필요할 때 사용합니다. Redis와 달리 별도 인프라 없이 JVM 메모리에 저장됩니다.

---

## 1. 구조

- **하나의** Caffeine 인스턴스만 생성됩니다.
- `key`가 곧 캐시 키입니다.

## 2. 사용법

### 캐싱

```java
import com.mjusugangsincheonghelper.global.config.CaffeineConfig.CaffeineCache;

@CaffeineCache(key = "blocked_apis", ttl = 30)
public List<String> getBlockedApis() {
    // ...
}
```

### 캐시 비우기

```java
import com.mjusugangsincheonghelper.global.config.CaffeineConfig.CaffeineCacheEvict;

@CaffeineCacheEvict(key = "blocked_apis")
public void refresh() {
    // 해당 key의 캐시가 제거됨
}
```

## 3. 속성

| 애노테이션 | 속성 | 필수 | 기본값 | 설명 |
|-----------|------|------|--------|------|
| `@CaffeineCache` | `key` | O | - | 캐시 키 |
| `@CaffeineCache` | `ttl` | X | 10 | 만료 시간 (초) |
| `@CaffeineCacheEvict` | `key` | O | - | 제거할 캐시 키 |

## 4. 참고

- `key`가 곧 키이므로, 다른 메서드가 같은 `key`를 사용하면 같은 캐시를 공유합니다.
- `ttl`은 엔트리마다 개별 적용됩니다 (Caffeine `Expiry` 기반).
- 전체 캐시 최대 크기는 2000 entries 입니다.
- 서버 재시작 시 캐시가 모두 소멸됩니다.
