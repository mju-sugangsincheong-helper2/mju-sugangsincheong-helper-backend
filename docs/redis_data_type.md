# Redis 데이터 바라보기 — 프로젝트 키 지도 (redis_data_type)

> 이 문서는 TTL/evict 같은 세부 규칙을 담는 문서가 아니라, **"이 키는 어떤 시점(렌즈)으로 바라봐야 하는가"**만 정의한 지도입니다.
> 프로젝트의 Redis 키는 같은 인스턴스에 섞여 있지만 성격이 전부 다릅니다. 키를 특정 지점 하나로 보고 하지 않고, 아래 **5개 렌즈** 중 어느 것에 해당하는지로 파악합니다.

---

## 1. Redis를 볼 때 쓰는 5개 렌즈

| 렌즈 | 질문 | 판정 결과 |
|---|---|---|
| **L1. 즉시성** | "누구의 쓰기 결과가 즉시 반영되어야 하는가?" | → `evict 필요` (본인 쓰기 직후) |
| **L2. 스냅샷 허용** | "낡아도 틀리지 않는 값인가?" | → evict 불필요, TTL이 신선도 기계 |
| **L3. 원본 여부** | "Redis가 원본(source of truth)인가? DB 백업이 있는가?" | → 원본이면 **eviction 금지**, 영속/복구 관점 |
| **L4. 키 공간** | "엔트리가 사용자·게임·조회수로 무한 늘어나는가?" | → TTL·trim이 메모리 절규의 수단 |
| **L5. 재생성 비용** | "캐시 미스 시 다시 만드는 DB 비용이 큰가?" | → sync + evict 남용 금지, TTL 위주 |

각 키를 볼 때는 위 렌즈 **하나로만** 판단하지 말고, 주요 렌즈 하나 + 부차 렌즈(L4는 항상 점검) 조합으로 봅니다.

---

## 2. 도메인별 키 지도

### 🔐 auth — 일회성 인증 상태

| 키 | 성격 | 바라보는 시점 |
|---|---|---|
| `oauth:state:{state}:session` | OAuth 인증 중간 단계용 임시 키 (TTL 300s, DB 백업 없음) | **L1 완화 + L3 일회성 원본**. TTL이 생명 주기 그 자체 → evict 불필요. 유실되어도 피쳐 범위가 "해당 로그인 1회"여서 리스크 낮음. **보면: "TTL 안에 상태가 소진되는가"**만 확인 |

### system — 전역 설정 복사본

| 키 | 성격 | 바라보는 시점 |
|---|---|---|
| `system-config:{key}:cache`, `system-config:current_term:cache` | DB 설정의 전역 복사본 | **L1 + L2**: 값이 "낡아도 무해한 화면"이 아니라 공용 설정이므로, 조회용으로는 TTL 충분하되 **설정 update 즉시 해당 키 evict**가 실질 정합성. 봐야 하는 점은 "**설정 변경 이벤트가 evict로 연결 되어 있는가**" |

### exchange — 대화방/교환 매칭 캐시

| 키 | 성격 | 바라보는 시점 |
|---|---|---|
| `exchange-feed` (`{term}`) | 공용 피드(최대 50) | **L1(공용 쓰기 즉시)**: 신규 신청이 목록에 바로 보여야 함 → evict 필수. 다만 낡아도 위험하지 않으므로 TTL은 보조 |
| `exchange-user-intents` (`{term}:member:{memberId}:intents`) | 각자의 의도 목록 | **L1(Read-Your-Writes)**: 본인 등록/철회 직후 본인 화면 반영 → evict 필수 |
| `exchange-room-meta` (`{term}:room:{roomId}:meta`) | 방 동적 상태(메시지/토글/참여자) | **L1(잦은 쓰기)**: 이벤트 빈도가 높아 실질 신선도는 **evict가 아니라 캐시 조회 시작점 확보**가 핵심. "어떤 쓰기 후 evict 호출이 빠졌는가"를 봐야 함 |
| `exchange-main` (`{term}:member:{memberId}:...`) | 회원별 화면 전체 렌더 스냅샷 (수동 Redis, double-evict) | **L5 우선**: 재생성이 무거움 → evict 타이밍 정밀화 + `doubleEvictDelay`(재캐시 역전 방지)가 있는지, **L4**: 회원 수에 비례한 유계. 뷰: "**evict와 재캐시 쓰기 경합을 어떻게 막았는가**" |

### singlegame — 랭킹/통계

| 키 | 성격 | 바라보는 시점 |
|---|---|---|
| `singlegame-rank` (`{totalCourses}:{scope}:{dept}:cache`) | 전역/학과 리더보드 집계의 스냅샷 | **L2(집계 스냅샷)**: 단건 저장으로 값이 "틀리지" 않고 그저 낡는다 → **evict 불필요**, TTL(5m)이 유일한 신선도 수단. 응답에 `myRank`(개인 스코프)가 포함되어 키에 memberId 없이 공유되므로, myRank는 요청마다 별도 계산 |
| `singlegame-stats` (`{totalCourses}:global:cache`, `{totalCourses}:dept:{dept}:cache`) | 통계 데이터 (percentile, aggregates) | **L2 + L5**: 통계 계산 비용이 매우 높으므로 캐시 필수. 새 게임 저장 시 해당 totalCourses의 캐시 evict. 키 공간은 totalCourses(5개) × (1 + 학과 수)로 유계 |

**캐시하지 않는 데이터:**
- **내 기록 (my records)**: 페이징 API로 부하 제한 + L5 재생성 비용 낮음 → 캐시 비효율
- **분석 (analysis)**: 원본(game, details)은 불변 + stats 이미 캐시 + 조합 비용 낮음 → 매번 조합

### multigame — 

| 키 | 성격 | 바라보는 시점 |
|---|---|---|
| `multigame-rank` (`department:rates`) | 부서별 수강률 집계 스냅샷 | singlegame-rank와 동일 프레임: **L2 집계 스냅샷 → evict 불필요, sync + TTL** |
| `multigame:round:{state,limit}:control` | 라운드 전역 상태/입장 한도 | **L3(원본 제어)**: 동시성 흐름 제어 키, Redis가 원본 → **eviction 금지**. 키는 라운드당 고정 1개로 L4 위험 없음. "상태 기계 전이 시 다수 키를 함께 초기화/정리하는가"를 봐야 함 |
| `multigame:round:{participants,queue,seq,seats,success_members}:ledger` | 대기열/좌석/합격 결과의 **Redis 원본** | **L3 결정**: DB 백업이 없는 즉전 게임 산출 값 → eviction 금지, 복구/태버랜리(Failover) 관점으로 보아야 하고, **Lua 스크립트 원자성이 전부** (돌케이트: GameApplyScript). 봐야 할 지점: "결과가 DB에 안녕가진, 재시작/분산 시 어떤 보장을 갖는가" |
| `multigame:round:heartbeat` (ZSet, timestamp score) | 참여자 활성 신호 | **L1+L2**: 4초 무효 만료 기반 카운트 → 만기분 삭제가 "캐시"가 아니라 **데이터 클린**임. "주기 trim이 정확히 흘러가는가" 관찰 |
| `multigame:round:event_log` | 라운드 이벤트 로그(모니터용) | **L3 그룹 이벤트 단순 로그**: 인프라에서 재처리 대상이 아니므로 유실 허용 쪽으로 보고, **길이 상한(trim)** 만 안전장치로 확인 |

---

## 3. 도메인별 단일 관찰 포인트 (요약)

| 도메인 | 하나만 기억할 렌즈 |
|---|---|
| auth | 일회성 → TTL이 생명주기, evict 는 볼 필요 없음 |
| system | 설정은 쓰기 이벤트 → evict, 조회는 스냅샷 → TTL |
| exchange | feed/main은 공용·렌더(쓰기즉시+무거움), intents/room은 개인·동적(쓰기 즉시 반영) — **evict가 정합성의 본체** |
| singlegame | rank=집계 L2(evict 불요, TTL 5m), stats=L2+L5(계산 비용 매우 높음, evict 필요) |
| multigame | 실제 실시간 도메인 — **Redis가 원본(ledger/control)이므로 eviction 금지 + 원자성(Lua)·복구 관점**이 최우선 |

---

## 4. 결정을 내릴 때 최종 규칙 (3줄)

1. **stale해도 무해하면(집계/스냅샷) → evict 부정, TTL이 신선도의 유일한 기계**
2. **stale하면 사용자에게 "틀린 내 기록"이 보이면(본인 쓰기) → 쓰기 트리거 즉시 evict**
3. **어떤 경우든 키가 유한이 아니면(게임×조회자 등) → TTL/trim이 메모리 경지의 필수선**
4. **Redis가 원본(control/ledger) → eviction 금지 원칙, 원자성(Lua)·재시작품의 형질