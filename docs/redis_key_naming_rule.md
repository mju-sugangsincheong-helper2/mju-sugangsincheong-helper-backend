# Redis Key Naming Rules

Redis 키의 접미사는 자료구조(List, Hash, Stream)를 표현하기 위한 것이 아니라, 해당 데이터의 **책임(Responsibility), 정합성 수준(Consistency), 운영 위험도(Risk)** 를 나타내기 위해 사용한다.

## Convention

```
kebab-case, :: 구분자
```

| 대상 | 규칙 | 예시 |
|------|------|------|
| **Cache name** (Spring `@Cacheable value`) | kebab-case, 도메인명 접두사 | `exchange-feed`, `multigame-rank` |
| **Redis key** | kebab-case, `::` 구분자 | `exchange-feed::202525:cache` |

## 표준 접미사 (Suffix)

| 접미사 | 의미 | 위험도 |
|--------|------|--------|
| `:cache` | 성능 최적화를 위한 임시 데이터 (RDB 복사본, 연산 결과) | 낮음 |
| `:session` | 인증 및 사용자 상태 데이터 (토큰, 세션, OTP) | 중간 |
| `:control` | 분산 환경의 동시성 및 흐름 제어 (Lock, Rate Limiter) | 높음 |
| `:ledger` | Redis가 원본(Source of Truth)인 비즈니스 데이터 | 매우 높음 |
| `:stream` | 재처리 가능한 비동기 파이프라인 (Queue, Event) | 중간~높음 |
| `:pubsub` | 휘발성 실시간 신호 전달 (Broadcast, WebSocket) | 낮음 |

## Key Structure

`term` (학기 식별자) 은 **Exchange 도메인에만** 포함된다. Exchange는 매 학기 데이터가 분리되므로 term 으로 namespace 를 구분해야 한다.
SingleGame, System Config, OAuth 는 term 에 종속되지 않으므로 key 에 term 이 없다.

### Exchange Domain (term-scoped, suffix: `:cache`)

```
{cache_name}::{term}:{entity_type}:{entity_id}:{data_type}:cache
```

| Segment | 의미 | 예시 |
|---------|------|------|
| `cache_name` | Spring cache region (kebab-case, 도메인명 접두사) | `exchange-user-intents` |
| `::` | Spring Cache 기본 separator | |
| `term` | 학기 식별자 | `202525` |
| `entity_type` | 엔티티 종류 | `member`, `room` |
| `entity_id` | 엔티티 ID | `42` |
| `data_type` | 저장 데이터 종류 | `intents`, `meta` |
| `:cache` | 접미사 (성능 최적화 임시 데이터) | |

### Non-term Domains (cross-term, suffix: `:cache` or `:session`)

```
{cache_name}::{key_specific_segments}:{suffix}
```

### 직접 Key 구성 방식 (`StringRedisTemplate`)

```
oauth:state:{uuid}:session
```

## 전체 Key Map

### Exchange Domain

| Cache name | Key pattern | 예시 |
|---|---|---|
| `exchange-feed` | `{term}:cache` | `exchange-feed::202525:cache` |
| `exchange-main` | `{term}:member:{memberId}:main:cache` | `exchange-main::202525:member:42:main:cache` |
| `exchange-user-intents` | `{term}:member:{memberId}:intents:cache` | `exchange-user-intents::202525:member:42:intents:cache` |
| `exchange-room-meta` | `{term}:room:{roomId}:meta:cache` | `exchange-room-meta::202525:room:7:meta:cache` |

> `exchange-main`은 `RedisTemplate`으로 직접 읽기/쓰기/evict(더블 evict)하며, 나머지는 `@Cacheable`/`@CacheEvict`로 관리한다.

### SingleGame Domain

| Cache name | Key pattern | 예시 |
|---|---|---|
| `singlegame-rank` | `{totalCourses}:{scope}:{department}:cache` | `singlegame-rank::6:GLOBAL:cache` |
| `singlegame-records` | `{memberId}:page:0:size:10:cache` | `singlegame-records::42:page:0:size:10:cache` |
| `singlegame-analysis` | `{gameId}:{memberId}:cache` | `singlegame-analysis::123:42:cache` |

> `scope` = `GLOBAL` \| `DEPARTMENT`, `department` = `ALL` 또는 학과명

### MultiGame Domain

| Cache name | Key pattern | 예시 |
|---|---|---|
| `multigame-rank` | `department:rates:cache` | `multigame-rank::department:rates:cache` |

> 랭킹 API에 파라미터가 없으므로 캐시 키는 도메인 전체 공유 단일 값이다. 집계 원본(학과별 성공률)만 캐시하고 참여 수/성공률/myDepartment는 요청마다 파생한다.

### System Domain

| Cache name | Key pattern | 예시 |
|---|---|---|
| `system-config` | `current_term:cache` | `system-config::current_term:cache` |

### OAuth (직접 Redis 사용)

| 구분 | Key pattern | 예시 |
|---|---|---|
| OAuth state | `oauth:state:{uuid}:session` | `oauth:state:a1b2c3d4-e5f6-...:session` |

## TTL

| Cache name | TTL |
|---|---|
| `exchange-feed` | `24h` |
| `exchange-main` | `24h` |
| `exchange-user-intents` | `24h` |
| `exchange-room-meta` | `24h` |
| `system-config` | `24h` |
| `singlegame-rank` | `5m` |
| `singlegame-records` | `5m` |
| `singlegame-analysis` | `5m` |
| `multigame-rank` | `5m` |
| 그 외 (YAML 미등록 캐시) | `24h` (default-ttl) |
| OAuth state | `300s` (코드 레벨) |

## Eviction 전략

- Redis를 read-through cache로 사용, RDB를 single source of truth로 유지
- **Exchange**: 쓰기 트랜잭션 커밋 후 `TransactionSynchronization.afterCommit()` 에서 관련 캐시를 명시적으로 evict (메시지 전송/의도 등록·철회/방 토글/방 생성). `exchange-main`은 2초 지연 더블 evict. TTL(24h)은 evict 누락 시 안전망.
- **SingleGame**: 게임 저장 트랜잭션 커밋 후 `afterCommit()` 에서 rank/analysis 전체 clear + records evict. TTL(5m)은 안전망.
- **MultiGame**: 랭킹 원본 집계만 캐시하며, 데이터 변경이 게임 정산(주기적 일괄 배치)에서만 발생하므로 **명시적 evict 없이 TTL(5m)만** 사용.
- Redis 장애는 WARN 로깅 후 무시 (graceful degradation)
