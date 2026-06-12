# Redis Key Naming Rules

Redis 키의 접미사는 자료구조(List, Hash, Stream)를 표현하기 위한 것이 아니라, 해당 데이터의 **책임(Responsibility), 정합성 수준(Consistency), 운영 위험도(Risk)** 를 나타내기 위해 사용한다.

## Convention

```
kebab-case, :: 구분자
```

| 대상 | 규칙 | 예시 |
|------|------|------|
| **Cache name** (Spring `@Cacheable value`) | kebab-case | `user-intents`, `room-static-meta` |
| **Redis key** | kebab-case, `::` 구분자 | `user-intents::202525:member:42:intents:cache` |

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
| `cache_name` | Spring cache region (kebab-case) | `user-intents` |
| `::` | Spring Cache 기본 separator | |
| `term` | 학기 식별자 | `202525` |
| `entity_type` | 엔티티 종류 | `member`, `room` |
| `entity_id` | 엔티티 ID | `42` |
| `data_type` | 저장 데이터 종류 | `intents`, `static_meta` |
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
| `user-intents` | `{term}:member:{memberId}:intents:cache` | `user-intents::202525:member:42:intents:cache` |
| `user-room-ids` | `{term}:member:{memberId}:room_ids:cache` | `user-room-ids::202525:member:42:room_ids:cache` |
| `user-unread-counts` | `{term}:member:{memberId}:unread_counts:cache` | `user-unread-counts::202525:member:42:unread_counts:cache` |
| `room-static-meta` | `{term}:room:{roomId}:static_meta:cache` | `room-static-meta::202525:room:7:static_meta:cache` |
| `room-dynamic-meta` | `{term}:room:{roomId}:dynamic_meta:cache` | `room-dynamic-meta::202525:room:7:dynamic_meta:cache` |
| `room-active-intents` | `{term}:room:{roomId}:active_intents:cache` | `room-active-intents::202525:room:7:active_intents:cache` |
| `recent-intents-page` | `{term}:recent_intents:lastId:{lastIntentId}:limit:{limit}:cache` | `recent-intents-page::202525:recent_intents:lastId:100:limit:20:cache` |

### SingleGame Domain

| Cache name | Key pattern | 예시 |
|---|---|---|
| `singlegame-rank` | `{totalCourses}:{scope}:cache` | `singlegame-rank::6:GLOBAL:cache` |
| `singlegame-records` | `{memberId}:page:0:size:10:cache` | `singlegame-records::42:page:0:size:10:cache` |
| `singlegame-analysis` | `{gameId}:cache` | `singlegame-analysis::123:cache` |

> `scope` = `GLOBAL` \| `DEPARTMENT`

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
| `room-static-meta` | `0s` (만료 없음) |
| `singlegame-rank` | `5m` |
| `singlegame-records` | `5m` |
| `singlegame-analysis` | `5m` |
| 그 외 나머지 | `24h` |
| OAuth state | `300s` (코드 레벨) |

## Eviction 전략

- Redis를 read-through cache로 사용, RDB를 single source of truth로 유지
- 쓰기 트랜잭션 커밋 후 `TransactionSynchronization.afterCommit()` 에서 관련 cache evict
- `recent-intests-page` 는 개별 키 evict 가 아닌 전체 clear (`cache.clear()`)
- Redis 장애는 WARN 로깅 후 무시 (graceful degradation)
