# Latency (핑 테스트) 도메인

## 1. 목적

사용자가 명지대 수강신청 서버(`class.mju.ac.kr`)까지의 HTTP 응답 지연 시간(latency)을 측정하고, 그 결과를 백엔드 서버에 제출하여 전체 사용자 중 자신의 위치를 확인할 수 있는 기능이다.

UI에서는 사용자에게 익숙한 **"핑 테스트"** 라는 명칭으로 노출하지만, 기술적으로는 ICMP ping이 아닌 **HTTP fetch 기반 RTT(Round-Trip Time) 측정**이다.

수강신청이라는 도메인 특성상 "평균 속도"보다는 **"평소 속도(Median)", "최악의 지연(Worst)", "들쑥날쑥 정도(Jitter)"** 3가지 지표를 핵심으로 다룬다.

---

## 2. 관련 서버 구분

본 도메인은 3개 서버가 각각 다른 역할을 수행한다.

| 서버 | 주체 | 역할 | 본 도메인에서의 관심사 |
|------|------|------|----------------------|
| **프론트 서버 (nginx)** | 우리 관리 | 정적 파일 서빙, `/api/*` 리버스 프록시 | Vue SPA 서빙. CORS 설정 불필요 (핑 측정은 `no-cors`로 동작) |
| **명지대 서버** | 명지대학교 | 수강신청 서버 (`class.mju.ac.kr`) | 핑 측정 대상. `robots.txt` (26 bytes)에 HTTP fetch하여 RTT 측정 |
| **백엔드 서버 (Spring)** | 우리 관리 | API 제공, 통계 계산, DB 저장 | 측정 결과 수신 및 저장, 3가지 지표에 대한 히스토그램 분포 데이터 생성 |

```
┌──────────────────────────────────────────────────────────────┐
│                        사용자 브라우저                         │
│                    (측정 주체, JavaScript 실행)                 │
└──────┬───────────────────────────────────────────┬───────────┘
       │                                           │
       │  ① 정적 파일 요청 (HTML/CSS/JS)            │  ④ 핑 측정 요청
       │     GET /latency (Vue 페이지)              │     fetch(no-cors) → robots.txt
       │     GET /assets/*.js                       │     10회 반복, RTT 측정 및 통계 계산
       ▼                                           ▼
┌──────────────────────┐               ┌──────────────────────┐
│   프론트 서버 (nginx)  │               │  명지대 서버           │
│ · 정적 파일 서빙      │               │ class.mju.ac.kr       │
│ · 리버스 프록시       │               │ · robots.txt (26B)    │
│   /api/* → 백엔드     │               │ · CORS 미허용          │
└──────────┬───────────┘               └──────────────────────┘
           │
           │  ② API 요청 (리버스 프록시)
           │     POST /api/1/latency (측정 통계 전송)
           │     GET  /api/1/latency/distribution (분포 조회)
           ▼
┌──────────────────────────────────┐
│        백엔드 서버 (Spring)        │
│ · 핑 결과 저장 (RDB)             │
│ · 3가지 히스토그램 분포 데이터 생성│
│ · 분포 데이터 캐싱 (TTL 5분)     │
└──────────────┬───────────────────┘
               ▼
       ┌───────────────┐
       │  PostgreSQL    │
       │  latency 테이블 │
       └───────────────┘
```

---

## 3. 측정 방식

### 3.1 타겟 (명지대 서버)

| 항목 | 값 |
|------|-----|
| URL | `https://class.mju.ac.kr/robots.txt` |
| 파일 크기 | 26 bytes |
| 서버 응답 | `Cache-Control: no-store` (캐시 금지 이미 설정됨) |
| CORS | 미허용 (응답 바디 읽기 불가, RTT 측정에는 영향 없음) |

### 3.2 클라이언트 측정 메커니즘 (브라우저 → 명지대 서버)

```javascript
// 10회 반복 측정 로직 (0.5초 간격)
for (let i = 0; i < 10; i++) {
  t1 = performance.now();
  await fetch(`https://class.mju.ac.kr/robots.txt?_=${Date.now()}`, {
    mode: 'no-cors',
    cache: 'no-store'
  });
  RTT = performance.now() - t1;
  samples.push(RTT);
  
  // 다음 측정 전 0.5초 대기 (서버 부담 감소 + UI 가시성 개선)
  if (i < 9) await sleep(500);
}
```

- `no-cors` 모드: 명지대 서버가 CORS를 허용하지 않으므로 preflight 없이 요청 전송. opaque response여도 promise resolve 시점으로 RTT 측정 가능.
- `cache: 'no-store'`: 브라우저 캐시 우회.
- `?_={timestamp}`: CDN/프록시 캐시 우회.

측정 후, 브라우저(프론트엔드)에서 10개의 샘플을 바탕으로 다음 3가지 통계를 계산한다.
1. **Median (평소 속도)**: 10개 샘플의 중앙값 (오름차순 정렬 후 중간값). 1번의 스파이크에 영향을 받지 않는 유저의 "기본 실력".
2. **Worst (최악의 지연)**: 10개 샘플 중 최대값. 수강신청 버튼을 누를 때 "이만큼 느릴 수 있다"는 위험도.
3. **Jitter (들쑥날쑥 정도)**: 10개 샘플의 표준편차. 네트워크 불안정성(들쑥날쑥함) 척도.

---

## 4. 인증 및 접근 정책

| 기능 | 최소 권한 | 설명 |
|------|-----------|------|
| 핑 측정 + 결과 제출 | **PUBLIC** | 인증 없이 누구나 가능. 익명 제출도 DB에 저장됨 (member_id=NULL) |
| 내 히스토리 조회 | **ROLE_MEMBER** 이상 | 자신의 기록은 정식 회원만 조회 가능 |
| Median 분포 조회 | **PUBLIC** | 평소 속도(Median)에 대한 전체 분포 히스토그램은 누구나 조회 가능. 단, 자신의 위치(`myValue` 등)는 인증된 사용자만 노출 |
| Worst, Jitter 분포 및 내 위치 조회 | **ROLE_MEMBER** 이상 | 상세 지연 분포 및 히스토그램 상의 자신의 위치(순위, 백분위 등)는 정식 회원만 조회 가능 |

본 도메인은 GLOBAL 랭킹만 존재한다. 학과(department) 기반 랭킹은 제공하지 않는다.

---

## 5. API 엔드포인트

### 5.1 핑 결과 제출 + 분포 조회

프론트엔드에서 10회 측정 후 통계를 계산하여 백엔드로 전송합니다. 백엔드는 결과를 저장하고, **해당 결과를 포함한 전체 분포**를 반환합니다.

```
POST /api/{version}/latency
인증: PUBLIC (인증 불필요)
```

**요청 본문:**
```json
{
  "medianMs": 49.523,
  "maxMs": 120.847,
  "minMs": 45.123,
  "stdDevMs": 21.456,
  "sampleCount": 10,
  "samples": [45.123, 52.456, 48.789, 120.847, 47.234, 50.567, 49.890, 55.123, 46.456, 51.789]
}
```

**응답 구조 (인증된 MEMBER 기준):**
```json
{
  "data": {
    "record": {
      "id": 123,
      "createdAt": "2025-08-14T10:30:00Z"
    },
    "distribution": {
      "median": {
        "histogram": [
          { "bucketStart": 0, "bucketEnd": 2, "count": 5, "percentage": 0.3 },
          { "bucketStart": 2, "bucketEnd": 4, "count": 15, "percentage": 1.0 },
          ...
        ],
        "summary": { "averageMs": 52.345, "p50Ms": 48.123, "p90Ms": 95.678 },
        "myValue": 49.523,
        "myRank": 180,
        "totalParticipants": 1500,
        "myPercentile": 12.0
      },
      "worst": { ... },
      "jitter": { ... }
    }
  }
}
```

> **인증되지 않은 사용자의 경우:** `distribution.median`만 포함되며, `myValue`, `myRank`, `myPercentile`은 모두 `null`입니다. `worst`, `jitter`는 응답에서 제외됩니다.
>
> **GUEST 사용자의 경우:** `distribution.median`만 포함되며, `myValue`, `myRank`, `myPercentile`은 자신의 최신 측정값이 포함됩니다. `worst`, `jitter`는 응답에서 제외됩니다.

**처리 절차:**
1. 인증 확인 (인증된 경우 memberId 추출, 익명 경우 memberId=NULL)
2. 요청 데이터 유효성 검증
3. `latency` 테이블에 통계 및 원천 샘플(JSONB) 저장
4. 저장된 결과를 포함한 전체 분포 조회 (캐시 + 실시간 myValue 계산)
5. 통합 응답 반환

**분포 데이터 필드 설명:**

| 필드 | 설명 |
|------|------|
| `*.histogram[]` | 해당 지표의 전체 레코드 분포 (모든 측정 기록 기준) |
| `*.histogram[].percentage` | 해당 구간의 레코드 비율 (소수점 2자리) |
| `*.summary.averageMs` | 전체 레코드의 평균 값 |
| `*.summary.p50Ms` | 전체 레코드의 중앙값 (50백분위수) |
| `*.summary.p90Ms` | 전체 레코드의 90백분위수 |
| `*.myValue` | 내 최신 측정 결과의 해당 지표 값 |
| `*.myRank` | 전체 레코드 중 내 순위 (1위가 가장 좋음) |
| `*.myPercentile` | 상위 퍼센트 (낮을수록 좋음) |

> **랭킹 기준 (오름차순 정렬):** Median, Worst, Jitter 모두 값이 낮을수록(빠르고 안정적) 좋은 것입니다.

**분포 계산 방식 (백엔드):**
1. 캐시에서 히스토그램 조회 (TTL 5분)
2. 캐시 미스 시: `latency` 테이블 전체 레코드를 `width_bucket`으로 버킷 분할 및 집계
3. 요청자의 최신 측정값으로 `myValue`, `myRank`, `myPercentile` 실시간 계산 후 병합

> **참고:** 모든 순위와 비율은 **사용자별 최신 레코드**가 아닌 **전체 측정 레코드**를 기준으로 계산됩니다.

---

### 5.3 내 히스토리 조회

```
GET /api/{version}/latency/my?page={page}&size={size}
인증: MEMBER 이상
```

**응답 구조**
```json
{
  "data": [
    {
      "id": 123,
      "medianMs": 49.523,
      "maxMs": 120.847,
      "minMs": 45.123,
      "stdDevMs": 21.456,
      "sampleCount": 10,
      "samples": [45.123, 52.456, 48.789, 120.847, 47.234, 50.567, 49.890, 55.123, 46.456, 51.789],
      "createdAt": "2025-08-14T10:30:00Z"
    }
  ],
  "page": {
    "pageNumber": 0,
    "pageSize": 20,
    "totalElements": 25,
    "totalPages": 2,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

---

## 6. 데이터 모델

단일 테이블(`latency`)에 통계 요약과 원천 샘플 데이터를 함께 저장합니다.

### RDB 스키마

```sql
CREATE TABLE IF NOT EXISTS latency (
    id            BIGSERIAL    PRIMARY KEY,
    member_id     BIGINT       REFERENCES member(id) ON DELETE CASCADE,  -- NULL 허용 (익명 제출)
    median_ms     DOUBLE PRECISION NOT NULL,  -- 평소 속도 (랭킹 기준)
    max_ms        DOUBLE PRECISION NOT NULL,  -- 최악의 지연
    min_ms        DOUBLE PRECISION NOT NULL,  -- 최고의 속도
    std_dev_ms    DOUBLE PRECISION NOT NULL,  -- 들쑥날쑥 정도 (Jitter)
    sample_count  INT          NOT NULL,      -- 샘플 개수 (보통 10)
    samples       JSONB        NOT NULL,      -- 원천 샘플 배열 [45.123, 52.456, ...]
    created_at    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_latency_member ON latency (member_id, created_at DESC);
CREATE INDEX idx_latency_median ON latency (median_ms);
CREATE INDEX idx_latency_max ON latency (max_ms);
CREATE INDEX idx_latency_stddev ON latency (std_dev_ms);
```

**인덱스 설명**
- `idx_latency_member`: 내 히스토리 페이징 조회용
- `idx_latency_median`, `idx_latency_max`, `idx_latency_stddev`: 분포 및 랭킹 계산용

### 히스토그램 집계 쿼리 예시 (width_bucket 활용)

사용자별 최신 데이터 추출 로직 없이, 전체 데이터를 바로 버킷팅합니다. PostgreSQL의 `width_bucket` 함수를 사용하면 애플리케이션 단에서 루프를 돌지 않고 DB에서 바로 히스토그램 카운트를 뽑아낼 수 있습니다.

```sql
-- Median 히스토그램 예시 (버킷 사이즈 10ms 가정, 0~30000ms 범위)
SELECT 
    (bucket_id - 1) * 10 AS bucket_start,
    bucket_id * 10 AS bucket_end,
    COUNT(*) AS count
FROM (
    SELECT width_bucket(median_ms, 0, 30000, 3000) AS bucket_id
    FROM latency
) AS buckets
WHERE bucket_id > 0
GROUP BY bucket_id
ORDER BY bucket_id;
```

> **`width_bucket` 설명:**
> `width_bucket(operand, low, high, count)` 형태로 동작합니다.
> 위 쿼리에서는 `0ms`부터 `30000ms`까지의 범위를 `3000`개의 버킷으로 나눕니다. (버킷 1개당 `10ms` 간격)
> 이를 통해 메모리로 모든 데이터를 끌어올리지 않고, DB 레벨에서 빠르게 분포도를 계산할 수 있습니다.

---

## 7. 에러 코드

| 코드 | HTTP 상태 | 설명 |
|------|-----------|------|
| `LATENCY_001` | 400 | samples 배열이 비어있음 |
| `LATENCY_002` | 400 | 샘플 값이 유효 범위를 벗어남 |
| `LATENCY_003` | 400 | sampleCount와 samples 배열 길이 불일치 |

---

## 8. 설정값

| 설정 | 기본값 | 설명 |
|------|--------|------|
| `app.latency.sample-min-ms` | 1 | 최소 샘플 값 (ms) |
| `app.latency.sample-max-ms` | 30000 | 최대 샘플 값 (ms) |
| `app.latency.distribution-cache-ttl` | 5m | 3가지 히스토그램 캐시 TTL |
| `app.latency.histogram-bucket-size-ms` | 2 | Median/Worst 히스토그램 버킷 간격 (ms). 이상치로 인한 그래프 붕괴를 막고 도메인에 맞는 고정된 시각적 해상도를 제공하기 위해 수동 설정. |
| `app.latency.jitter-bucket-size-ms` | 1 | Jitter 히스토그램 버킷 간격 (ms). 표준편차 값이 작게 몰려있는 특성을 반영하기 위해 별도로 수동 설정. |

```yaml
app:
  latency:
    sample-min-ms: 1
    sample-max-ms: 30000
    distribution-cache-ttl: 5m
    histogram-bucket-size-ms: 2
    jitter-bucket-size-ms: 1
```

---

## 9. 패키지 구조

```
latency/
├── controller/
│   └── LatencyController.java
├── dto/
│   ├── LatencySubmitRequest.java
│   ├── LatencySubmitResponse.java
│   ├── LatencyMyRecordResponse.java
│   └── LatencyDistributionResponse.java
├── entity/
│   └── LatencyEntity.java
├── repository/
│   └── LatencyRepository.java
└── service/
    └── LatencyService.java
```

---

## 10. 캐싱 전략

| 데이터 | 캐시 여부 | 이유 |
|--------|-----------|------|
| **3가지 히스토그램 및 요약 (median, worst, jitter)** | TTL 5분 캐시 | 전체 집계 스냅샷이므로 5분 지연 허용. `myValue`, `myRank`, `myPercentile`은 공유 캐시에 포함하지 않고 요청마다 별도 계산하여 병합 |
| **내 히스토리** | 캐시 안 함 | 페이징 API로 부하 제한 + 재생성 비용 낮음 |

---

## 11. 프론트엔드 연동

### 도메인명 규칙

| 구분 | 명칭 | 예시 |
|------|------|------|
| 백엔드 도메인/패키지 | `latency` | `latency/controller/LatencyController.java` |
| API 경로 | `/api/1/latency` | `POST /api/1/latency` |
| 프론트 피처 디렉토리 | `latency` | `src/features/latency/` |
| UI 노출 명칭 | "핑 테스트" | 페이지 제목, 버튼 텍스트 등 |
| 라우트 경로 | `/latency`  | TBD |

UI에서만 "핑 테스트"라는 명칭을 사용하며, 프론트엔드의 나머지 모든 부분은 `latency`라는 명칭을 사용합니다. 동일하게 백엔드에서도 `latency`라는 명칭을 사용합니다.

### UI 구성

```text
┌──────────────────────────────────────────────────────────────────┐
│  [latency · 네트워크 진단]      ← eyebrow                         │
│  명지대 서버 핑 테스트           ← h1                              │
│  class.mju.ac.kr 서버까지의 응답속도를 측정합니다                   │
│                                                                    │
│  [테스트 시작]  ← bg-[#00205B] 버튼 (누르면 동적으로 아래에 진행상황 표시)      │
│   
|   (측정이 끝나면 생기는 부분)                                           │
│  ┌─ 측정 결과 ─────────────────────────────────────┐ │
│  │  #1  ██████░░░░░░░░  45ms                                     │ │
│  │  #2  ███████░░░░░░░░  52ms                                     │ │
│  │  #3  ██████░░░░░░░░░  48ms                                     │ │
│  │  #4  ████████████████ 120ms ⚠️                                 │ │
│  │  ...                                                          │ │
│  │  평소 속도 (Median)  : 49.5ms                                  │ │
│  │  최악의 지연 (Worst) : 120ms (위험 구간)                       │ │
│  │  들쑥날쑥 정도 (Jitter): 21.4ms                                │ │
│  └──────────────────────────────────────────────────────────────┘ │
│                                                                    │
│  ┌─ 전체 분포 속 내 위치 (실제로는 chart.js 그래프로 그려짐) ───────────────┐ │
│  │                                                                │ │
│  │  [Median 분포 - 평소 속도]                                     │ │
│  │   ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓                                            │ │
│  │   ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓  ▼ (49.5ms, 상위 12%)                     │ │
│  │   ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓                                            │ │
│  │   0  20  40  49  60  80 100                                   │ │
│  │                                                                │ │
│  │  [Worst 분포 - 최악의 지연]                                    │ │
│  │   ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓                                            │ │
│  │   ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓                                            │ │
│  │   ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓  ▼ (120ms, 상위 63%)                      │ │
│  │   0  50 100 120 150 200                                       │ │
│  │                                                                │ │
│  │  [Jitter 분포 - 들쑥날쑥 정도]                                 │ │
│  │   ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓                                            │ │
│  │   ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓                                            │ │
│  │   ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓                                            │ │
│  │   ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓  ▼ (21.4, 상위 96%)                       │ │
│  │   0   5  10  15  20  25                                       │ │
│  │                                                                │ │
│  │  [익명] 로그인 시 내 위치 표시 + Worst/Jitter 분포 확인 가능     │ │
│  └──────────────────────────────────────────────────────────────┘ │
│                                                                    │
│  ┌─ 내 히스토리 ────────────────────────────────────────────────┐ │
│  │  제출 기록 데이터                                              │ │
│  └──────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
```

### UI 시각화 포인트
1. **측정 흐름**
   - 테스트 시작 버튼 클릭 → 0.5초 간격으로 10회 측정 → 결과 제출 → 분포 조회
   - 측정 중에는 진행 바와 현재 측정 횟수 표시
   - 제출 완료 후 바로 분포 그래프 표시

2. **권한별 렌더링 차이**
   - **비회원 (익명 + GUEST)**: [Median 분포] 그래프만 렌더링됩니다. "로그인하면 Worst, Jitter 분포와 내 위치를 확인할 수 있어요" 안내 표시
   - **MEMBER**: 3개의 그래프가 모두 렌더링되며, 각 그래프에 내 위치 마커 표시

3. **그래프 시각화**
   - `percentage`를 이용해 막대 그래프의 높이를 매핑
   - 각 그래프에 `myValue` 위치에 빨간색 점선 마커 표시
   - `summary` 데이터를 활용해 평균, 중앙값, P90을 하단에 텍스트로 표시
   - 막대 간격 없이 연속적으로 표시하여 부드러운 분포 곡선 느낌
   - 그래프 상단 패딩으로 마커 텍스트가 잘리지 않도록 처리

4. **결과 카드 디자인**
   - 측정 결과는 카드 형태로 구분
   - 샘플별 진행 바는 최대값 기준으로 비율 표시
   - 통계 요약은 그라데이션 배경으로 강조
   - 속도 평가 라벨 (매우 빠름/빠름/보통/느림/매우 느림) 색상으로 구분