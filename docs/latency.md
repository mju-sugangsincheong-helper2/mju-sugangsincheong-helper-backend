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
// 10회 반복 측정 로직
for (let i = 0; i < 10; i++) {
  t1 = performance.now();
  await fetch(`https://class.mju.ac.kr/robots.txt?_=${Date.now()}`, {
    mode: 'no-cors',
    cache: 'no-store'
  });
  RTT = performance.now() - t1;
  samples.push(RTT);
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
| 핑 측정 + 결과 제출 | **ROLE_GUEST** 이상 | 측정은 누구나 가능. 게스트도 자신의 데이터를 백엔드 서버에 저장할 수 있음 |
| 내 히스토리 조회 | **ROLE_GUEST** 이상 | 자신의 기록은 게스트도 조회 가능 |
| Median 분포 조회 | **ROLE_GUEST** 이상 | 평소 속도(Median)에 대한 전체 분포 히스토그램은 게스트도 조회 가능. 단, 자신의 위치(`myValue` 등)는 노출되지 않음 |
| Worst, Jitter 분포 및 내 위치 조회 | **ROLE_MEMBER** 이상 | 상세 지연 분포 및 히스토그램 상의 자신의 위치(순위, 백분위 등)는 정식 회원만 조회 가능 |

본 도메인은 GLOBAL 랭킹만 존재한다. 학과(department) 기반 랭킹은 제공하지 않는다.

---

## 5. API 엔드포인트

### 5.1 핑 결과 제출

프론트엔드에서 10회 측정 후 통계를 계산하여 백엔드로 전송합니다. 백엔드는 랭킹이나 복잡한 응답을 주지 않고, 데이터를 잘 저장했는지 여부만 반환합니다.

```
POST /api/{version}/latency
인증: GUEST 이상
```

**요청 본문:**
```json
{
  "medianMs": 49.5,
  "maxMs": 120,
  "minMs": 45,
  "stdDevMs": 21.4,
  "sampleCount": 10,
  "samples": [45, 52, 48, 120, 47, 50, 49, 55, 46, 51]
}
```

**유효성 검증 규칙**
- `samples` 배열의 길이는 1 이상이어야 함
- 각 샘플 값은 `latency-sample-min-ms`(기본 1) ~ `latency-sample-max-ms`(기본 30000) 범위 내여야 함
- `medianMs`, `maxMs`, `minMs`는 음수 불가
- `sampleCount`는 `samples` 배열 길이와 일치해야 

**응답 구조 (단순 성공 응답)**
```json
{
  "data": {
    "id": 123,
    "createdAt": "2025-08-14T10:30:00Z"
  }
}
```

**처리 절차**
1. 회원 존재 여부 확인
2. 요청 데이터 유효성 검증
3. `latency` 테이블에 통계 및 원천 샘플(JSONB) 저장
4. 성공 응답 반환

---

### 5.2 내 히스토리 조회

```
GET /api/{version}/latency/my?page={page}&size={size}
인증: GUEST 이상
```

**응답 구조**
```json
{
  "data": [
    {
      "id": 123,
      "medianMs": 49.5,
      "maxMs": 120,
      "minMs": 45,
      "stdDevMs": 21.4,
      "sampleCount": 10,
      "samples": [45, 52, 48, 120, 47, 50, 49, 55, 46, 51],
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

### 5.3 전체 분포 조회 (상세 히스토그램)

**Median, Worst, Jitter 각각에 대한 독립적인 히스토그램**을 제공합니다.
전체 사용자가 공유하는 히스토그램 데이터는 캐싱되며, 요청자의 위치(`myPosition`)는 캐싱되지 않고 매 요청마다 실시간으로 계산되어 병합됩니다.

```
GET /api/{version}/latency/distribution
인증: GUEST 이상 (단, GUEST는 Median 분포만 조회 가능하며 myValue 등은 null 반환)
```

**파라미터:** 없음

**응답 구조 (MEMBER 기준)**
```json
{
  "data": {
    "median": {
      "histogram": [
        { "bucketStart": 0,  "bucketEnd": 10, "count": 45,  "percentage": 3.0 },
        { "bucketStart": 10, "bucketEnd": 20, "count": 100, "percentage": 6.7 },
        { "bucketStart": 20, "bucketEnd": 30, "count": 250, "percentage": 16.7 },
        { "bucketStart": 30, "bucketEnd": 40, "count": 380, "percentage": 25.3 },
        { "bucketStart": 40, "bucketEnd": 50, "count": 320, "percentage": 21.3 },
        { "bucketStart": 50, "bucketEnd": 60, "count": 200, "percentage": 13.3 },
        { "bucketStart": 60, "bucketEnd": 70, "count": 105, "percentage": 7.0 },
        { "bucketStart": 70, "bucketEnd": 80, "count": 60,  "percentage": 4.0 },
        { "bucketStart": 80, "bucketEnd": 90, "count": 25,  "percentage": 1.7 },
        { "bucketStart": 90, "bucketEnd": 100,"count": 15,  "percentage": 1.0 }
      ],
      "myValue": 49.5,
      "myRank": 180,
      "totalParticipants": 1500,
      "myPercentile": 12.0
    },
    "worst": {
      "histogram": [ ... ],
      "myValue": 120,
      "myRank": 950,
      "totalParticipants": 1500,
      "myPercentile": 63.3
    },
    "jitter": {
      "histogram": [ ... ],
      "myValue": 21.4,
      "myRank": 1450,
      "totalParticipants": 1500,
      "myPercentile": 96.6
    }
  }
}
```

> *(참고: GUEST 사용자가 호출할 경우, 응답의 `data`에는 `median` 객체만 존재하며, `myValue`, `myRank`, `myPercentile` 필드는 `null`로 내려갑니다. `worst`, `jitter` 객체는 아예 응답에서 제외됩니다.)*

**필드 설명**

| 필드 | 설명 | 프론트엔드 활용 |
|------|------|----------------|
| `*.histogram[]` | 해당 지표의 전체 사용자 분포 (전체 데이터 기준) | 막대 그래프(히스토그램) 시각화 |
| `*.histogram[].percentage` | 해당 구간의 인원 비율 (소수점 1자리) | 막대 그래프의 높이를 `count` 대신 `percentage`로 매핑하여 정규화된 그래프 출력 |
| `*.summary.averageMs` | 전체 사용자의 평균 값 | 그래프 위에 "전체 평균" 수직선 표시 |
| `*.summary.p50Ms` | 전체 사용자의 중앙값 (50백분위수) | 그래프 위에 "전체 중앙값" 수직선 표시 |
| `*.summary.p90Ms` | 전체 사용자의 90백분위수 (느린 그룹 기준선) | "상위 10%는 이 정도 느림" 등의 가이드라인 표시 |
| `*.myValue` | 내 최신 측정 결과의 해당 지표 값 | 그래프 위에 수직선/점으로 표시 |
| `*.myRank` | 해당 지표 기준 내 순위 (1위가 가장 좋음) | UI 텍스트 표시 |
| `*.myPercentile` | 상위 퍼센트 (낮을수록 좋음) | UI 텍스트 표시 |

> **랭킹 기준 (오름차순 정렬):** 
> Median, Worst, Jitter 모두 값이 낮을수록(빠르고 안정적) 좋은 것이므로, 오름차순 정렬하여 순위를 매깁니다.

**처리 절차 (백엔드)**
1. 캐시에서 `median`, `worst`, `jitter` 3개의 히스토그램 및 `summary` 조회 (TTL 5분)
2. 캐시 미스 시:
   - `latency` 테이블에서 **전체 데이터**를 대상으로 PostgreSQL의 `width_bucket` 함수를 사용해 버킷 분할 및 카운트 집계 수행
   - 전체 통계(average, p50, p90) 계산
   - 캐시에 저장
3. 요청자의 권한 확인:
   - GUEST: `median` 데이터만 제공하며, `myValue` 등은 `null` 처리
   - MEMBER: 3가지 데이터 모두 제공
4. 요청자(Member)의 최신 `latency` 결과를 조회하여 `myValue` 계산
5. `myValue`가 히스토그램 전체 데이터 중 어디 위치하는지 계산하여 `myRank`, `myPercentile` 산출
6. 캐시된 히스토그램과 요청자 위치 정보를 병합하여 반환

---

## 6. 데이터 모델

단일 테이블(`latency`)에 통계 요약과 원천 샘플 데이터를 함께 저장합니다.

### RDB 스키마

```sql
CREATE TABLE IF NOT EXISTS latency (
    id            BIGSERIAL    PRIMARY KEY,
    member_id     BIGINT       NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    median_ms     DOUBLE PRECISION NOT NULL,  -- 평소 속도 (랭킹 기준)
    max_ms        INT          NOT NULL,      -- 최악의 지연
    min_ms        INT          NOT NULL,      -- 최고의 속도
    std_dev_ms    DOUBLE PRECISION NOT NULL,  -- 들쑥날쑥 정도 (Jitter)
    sample_count  INT          NOT NULL,      -- 샘플 개수 (보통 10)
    samples       JSONB        NOT NULL,      -- 원천 샘플 배열 [45, 52, 48, 120, ...]
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
| `app.latency.histogram-bucket-size-ms` | 10 | Median/Worst 히스토그램 버킷 간격 (ms). 이상치로 인한 그래프 붕괴를 막고 도메인에 맞는 고정된 시각적 해상도를 제공하기 위해 수동 설정. |
| `app.latency.jitter-bucket-size-ms` | 2 | Jitter 히스토그램 버킷 간격 (ms). 표준편차 값이 작게 몰려있는 특성을 반영하기 위해 별도로 수동 설정. |

```yaml
app:
  latency:
    sample-min-ms: 1
    sample-max-ms: 30000
    distribution-cache-ttl: 5m
    histogram-bucket-size-ms: 10
    jitter-bucket-size-ms: 2
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
│  │  [GUEST] 로그인 시 전체 사용자와 비교 가능                      │ │
│  └──────────────────────────────────────────────────────────────┘ │
│                                                                    │
│  ┌─ 내 히스토리 ────────────────────────────────────────────────┐ │
│  │  제출 기록 데이터                                              │ │
│  └──────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
```

### UI 시각화 포인트
1. **권한별 렌더링 차이**
   - **GUEST**: [Median 분포 - 평소 속도] 그래프만 렌더링됩니다. 그래프 내에 "로그인하면 내 위치와 상세 지연(Worst, Jitter) 통계를 볼 수 있습니다" 라는 CTA(Call To Action) 오버레이를 띄워 회원가입/로그인을 유도합니다.
   - **MEMBER**: 3개의 그래프가 모두 렌더링됩니다.
2. **상세 히스토그램 시각화**
   - `percentage`를 이용해 막대 그래프의 높이를 매핑합니다.
   - 각 그래프에는 `myValue` 위치에 마커를 표시합니다.
   - `summary` 데이터를 활용해 "전체 평균", "전체 중앙값" 등을 점선으로 표시해주면 사용자가 자신의 위치를 더 입체적으로 파악할 수 있습니다.
   - 분포 속에서 자신이 우측(느리거나 불안정)에 치우쳐 있을수록 수강신청 경쟁에 불리하다는 것을 직관적으로 전달합니다.