# Multigame 결과/통계 기능 (P0/P1)

## 개요

멀티게임 도메인에 개인 기록 조회와 학과별 통계 기능을 추가했습니다.

---

## 1. 내 참여 기록 목록 (P0)

### API

```
GET /api/v1/multigame/my/history?page=0&size=10
```

### 설명

내가 참여한 모든 멀티게임 기록을 페이징으로 조회합니다.

### 요청 파라미터

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| `page` | int | 선택 | 0 | 페이지 번호 (0부터 시작) |
| `size` | int | 선택 | 10 | 페이지당 항목 수 |

### 응답 예시

```json
{
  "meta": {
    "requestId": "uuid",
    "apiVersion": "v1",
    "path": "/api/v1/multigame/my/history",
    "method": "GET",
    "timestamp": "2026-07-25T10:00:00Z",
    "durationMs": 45,
    "ipAddress": "0:0:0:0:0:0:0:1",
    "userAgent": "Mozilla/5.0"
  },
  "data": [
    {
      "multigameId": "20260630120000",
      "subjectId": 3,
      "status": "SUCCESS",
      "participantCount": 100,
      "finalizedAt": "2026-06-30T12:00:20Z"
    },
    {
      "multigameId": "20260630121000",
      "subjectId": 1,
      "status": "FAIL_SOLDOUT",
      "participantCount": 80,
      "finalizedAt": "2026-06-30T12:10:20Z"
    }
  ],
  "page": {
    "pageNumber": 0,
    "pageSize": 10,
    "totalElements": 45,
    "totalPages": 5
  }
}
```

### 응답 필드

| 필드 | 타입 | 설명 |
|------|------|------|
| `multigameId` | String | 멀티게임 ID (14자리) |
| `subjectId` | int | 신청한 과목 ID (1~6) |
| `status` | String | 결과 상태 (SUCCESS, FAIL_SOLDOUT, FAIL_DUPLICATE) |
| `participantCount` | int | 해당 게임 참여자 수 |
| `finalizedAt` | Instant | 게임 종료 시각 |

### 정렬

- `createdAt` 내림차순 (최신순)

---

## 2. 내 참여 통계 요약 (P0)

### API

```
GET /api/v1/multigame/my/stats
```

### 설명

내 멀티게임 참여 통계 요약을 조회합니다. 동기부여 및 게임화 요소로 활용됩니다.

### 응답 예시

```json
{
  "meta": { ... },
  "data": {
    "totalGames": 45,
    "successCount": 38,
    "failSoldoutCount": 5,
    "failDuplicateCount": 2,
    "successRate": 84.4,
    "mostRequestedSubject": 3,
    "subjectBreakdown": [
      {
        "subjectId": 3,
        "count": 15,
        "success": 12
      },
      {
        "subjectId": 1,
        "count": 10,
        "success": 8
      },
      {
        "subjectId": 2,
        "count": 8,
        "success": 7
      }
    ]
  }
}
```

### 응답 필드

| 필드 | 타입 | 설명 |
|------|------|------|
| `totalGames` | long | 총 참여 게임 수 |
| `successCount` | long | 성공 횟수 |
| `failSoldoutCount` | long | 정원 초과로 실패한 횟수 |
| `failDuplicateCount` | long | 중복 신청으로 실패한 횟수 |
| `successRate` | double | 성공률 (%) |
| `mostRequestedSubject` | Integer | 가장 많이 신청한 과목 ID (없으면 null) |
| `subjectBreakdown` | List | 과목별 참여 통계 |

#### subjectBreakdown 항목

| 필드 | 타입 | 설명 |
|------|------|------|
| `subjectId` | int | 과목 ID (1~6) |
| `count` | long | 해당 과목 신청 횟수 |
| `success` | long | 해당 과목 성공 횟수 |

### 정렬

- `subjectBreakdown`는 `count` 내림차순

---

## 3. 학과별 참여 횟수 순위 (P1)

### API

```
GET /api/v1/multigame/stats/department/participation
```

### 설명

학과별 총 참여 횟수(절대값) 순위를 조회합니다. 상위 10개 학과와 요청자의 학과 정보를 포함합니다.

### 응답 예시

```json
{
  "meta": { ... },
  "data": {
    "rankings": [
      {
        "rank": 1,
        "department": "컴퓨터공학과",
        "participationCount": 245
      },
      {
        "rank": 2,
        "department": "전자공학과",
        "participationCount": 180
      },
      {
        "rank": 3,
        "department": "소프트웨어학과",
        "participationCount": 150
      }
    ],
    "myDepartment": {
      "department": "소프트웨어학과",
      "participationCount": 150,
      "rank": 3
    }
  }
}
```

### 응답 필드

#### rankings

| 필드 | 타입 | 설명 |
|------|------|------|
| `rank` | int | 순위 (1부터 시작) |
| `department` | String | 학과명 |
| `participationCount` | long | 총 참여 횟수 |

#### myDepartment

| 필드 | 타입 | 설명 |
|------|------|------|
| `department` | String | 내 학과명 |
| `participationCount` | long | 내 학과 총 참여 횟수 |
| `rank` | int | 내 학과 순위 (목록에 없으면 전체 학과 수 + 1) |

### 캐시

- **TTL**: 5분
- **Cache Name**: `multigame-department-participation`
- **Key**: `"all"` (모든 사용자에게 동일한 결과)

---

## 4. 학과별 성공률 순위 (P1)

### API

```
GET /api/v1/multigame/stats/department/success-rate
```

### 설명

학과별 성공률 순위를 조회합니다. 상위 10개 학과와 요청자의 학과 정보를 포함합니다.

### 응답 예시

```json
{
  "meta": { ... },
  "data": {
    "rankings": [
      {
        "rank": 1,
        "department": "컴퓨터공학과",
        "totalCount": 245,
        "successCount": 200,
        "successRate": 81.6
      },
      {
        "rank": 2,
        "department": "전자공학과",
        "totalCount": 180,
        "successCount": 140,
        "successRate": 77.8
      }
    ],
    "myDepartment": {
      "department": "소프트웨어학과",
      "totalCount": 150,
      "successCount": 120,
      "successRate": 80.0,
      "rank": 3
    }
  }
}
```

### 응답 필드

#### rankings

| 필드 | 타입 | 설명 |
|------|------|------|
| `rank` | int | 순위 (1부터 시작) |
| `department` | String | 학과명 |
| `totalCount` | long | 총 참여 횟수 |
| `successCount` | long | 성공 횟수 |
| `successRate` | double | 성공률 (%) |

#### myDepartment

| 필드 | 타입 | 설명 |
|------|------|------|
| `department` | String | 내 학과명 |
| `totalCount` | long | 내 학과 총 참여 횟수 |
| `successCount` | long | 내 학과 성공 횟수 |
| `successRate` | double | 내 학과 성공률 (%) |
| `rank` | int | 내 학과 순위 |

### 캐시

- **TTL**: 5분
- **Cache Name**: `multigame-department-success-rate`
- **Key**: `"all"` (모든 사용자에게 동일한 결과)

---

## 5. 대시보드 (P2)

### API

```
GET /api/v1/multigame/dashboard
```

### 설명

멀티게임 대시보드를 조회합니다. 오늘의 게임 목록, 내 최근 기록, 전체 통계 요약을 포함합니다.

### 응답 예시

```json
{
  "meta": { ... },
  "data": {
    "todayGames": [
      {
        "multigameId": "20260725120000",
        "participantCount": 100,
        "capacity": 50,
        "finalizedAt": "2026-07-25T12:00:20Z"
      },
      {
        "multigameId": "20260725121000",
        "participantCount": 80,
        "capacity": 40,
        "finalizedAt": "2026-07-25T12:10:20Z"
      }
    ],
    "myRecentResults": [
      {
        "multigameId": "20260725120000",
        "subjectId": 3,
        "status": "SUCCESS",
        "finalizedAt": "2026-07-25T12:00:20Z"
      },
      {
        "multigameId": "20260725121000",
        "subjectId": 1,
        "status": "FAIL_SOLDOUT",
        "finalizedAt": "2026-07-25T12:10:20Z"
      }
    ],
    "overallStats": {
      "totalGames": 156,
      "totalParticipants": 12480,
      "averageParticipants": 80.0
    }
  }
}
```

### 응답 필드

#### todayGames

오늘 진행된 게임 목록 (startTime이 오늘인 게임)

| 필드 | 타입 | 설명 |
|------|------|------|
| `multigameId` | String | 멀티게임 ID (14자리) |
| `participantCount` | int | 참여자 수 |
| `capacity` | int | 과목별 정원 |
| `finalizedAt` | Instant | 게임 종료 시각 |

#### myRecentResults

내 최근 참여 기록 (최대 5개)

| 필드 | 타입 | 설명 |
|------|------|------|
| `multigameId` | String | 멀티게임 ID (14자리) |
| `subjectId` | int | 신청한 과목 ID (1~6) |
| `status` | String | 결과 상태 (SUCCESS, FAIL_SOLDOUT, FAIL_DUPLICATE) |
| `finalizedAt` | Instant | 게임 종료 시각 |

#### overallStats

전체 통계 요약

| 필드 | 타입 | 설명 |
|------|------|------|
| `totalGames` | long | 총 진행 게임 수 |
| `totalParticipants` | long | 총 참여자 수 (누적) |
| `averageParticipants` | double | 게임당 평균 참여자 수 |

### 정렬

- `todayGames`: `multigameId` 오름차순 (시간순)
- `myRecentResults`: `createdAt` 내림차순 (최신순)

---

## 구현 파일 구조

```
multigame/
├── my/                                     # 내 참여 기록
│   ├── controller/
│   │   └── MultigameMyController.java      # 내 기록, 내 통계 API
│   ├── dto/
│   │   └── MyHistoryResponse.java          # 내 참여 기록 DTO
│   └── service/
│       └── MultigameMyHistoryService.java
│
├── stats/                                  # 통계 (전체 + 내 통계)
│   ├── controller/
│   │   └── MultigameStatsController.java   # 학과별 통계 API
│   ├── dto/
│   │   ├── MyStatsResponse.java            # 내 통계 요약 DTO
│   │   ├── DepartmentParticipationStatsResponse.java  # 학과별 참여 횟수 DTO
│   │   └── DepartmentSuccessRateStatsResponse.java    # 학과별 성공률 DTO
│   └── service/
│       ├── MultigameMyStatsService.java
│       └── MultigameDepartmentStatsService.java
│
├── dashboard/                              # 대시보드
│   ├── controller/
│   │   └── MultigameDashboardController.java   # 대시보드 API
│   ├── dto/
│   │   └── DashboardResponse.java              # 대시보드 DTO
│   └── service/
│       └── MultigameDashboardService.java
│
└── result/                                 # 각 게임의 세부 결과
    ├── controller/
    │   └── MultigameResultController.java
    ├── dto/
    │   ├── MultigameResultResponse.java
    │   └── MultigameResultDetailResponse.java
    └── service/
        └── MultigameResultService.java

database/repository/
├── MultigameResultDetailRepository.java    # 쿼리 메서드 추가
├── MultigameResultRepository.java          # 대시보드용 쿼리 추가
└── MemberRepository.java                   # 학과별 회원 수 쿼리 추가
```

---

## Repository 메서드

### MultigameResultDetailRepository

```java
// 내 참여 기록 페이징 조회
Page<MultigameResultDetailEntity> findByMemberIdOrderByCreatedAtDesc(Long memberId, Pageable pageable);

// 내 참여 통계
long countByMemberId(Long memberId);
long countByMemberIdAndStatus(Long memberId, String status);

// 과목별 참여 통계
@Query(value = """
    SELECT d.subject_id, COUNT(*) as total,
           SUM(CASE WHEN d.status = 'SUCCESS' THEN 1 ELSE 0 END) as success
    FROM multigame_result_detail d
    WHERE d.member_id = :memberId
    GROUP BY d.subject_id
    ORDER BY total DESC
    """, nativeQuery = true)
List<Object[]> findSubjectBreakdownByMemberId(@Param("memberId") Long memberId);

// 학과별 참여 횟수 통계
@Query(value = """
    SELECT m.department, COUNT(*) as participation_count,
           SUM(CASE WHEN d.status = 'SUCCESS' THEN 1 ELSE 0 END) as success_count
    FROM multigame_result_detail d
    JOIN member m ON d.member_id = m.id
    WHERE m.department IS NOT NULL
    GROUP BY m.department
    ORDER BY participation_count DESC
    """, nativeQuery = true)
List<Object[]> findDepartmentParticipationStats();

// 학과별 성공률 통계
@Query(value = """
    SELECT m.department, COUNT(*) as total_count,
           SUM(CASE WHEN d.status = 'SUCCESS' THEN 1 ELSE 0 END) as success_count
    FROM multigame_result_detail d
    JOIN member m ON d.member_id = m.id
    WHERE m.department IS NOT NULL
    GROUP BY m.department
    ORDER BY success_count DESC, total_count DESC
    """, nativeQuery = true)
List<Object[]> findDepartmentSuccessRateStats();
```

### MemberRepository

```java
// 학과별 회원 수
@Query("SELECT m.department, COUNT(m) FROM Member m WHERE m.department IS NOT NULL GROUP BY m.department")
List<Object[]> countMembersByDepartment();
```

### MultigameResultRepository

```java
// 특정 날짜의 게임 목록 조회
@Query(value = """
    SELECT * FROM multigame_result
    WHERE start_time LIKE :datePattern
    ORDER BY start_time ASC
    """, nativeQuery = true)
List<MultigameResultEntity> findByDate(@Param("datePattern") String datePattern);

// 전체 통계
@Query("SELECT COUNT(r) FROM MultigameResultEntity r")
long countTotalGames();

@Query("SELECT COALESCE(SUM(r.participantCount), 0) FROM MultigameResultEntity r")
long countTotalParticipants();

@Query("SELECT COALESCE(AVG(r.participantCount), 0) FROM MultigameResultEntity r")
double calculateAverageParticipants();
```

---

## 캐시 설정

### application-dev.yml / application-prod.yml

```yaml
app:
  cache:
    ttls:
      # 기존 캐시...
      multigame-department-participation: 5m
      multigame-department-success-rate: 5m
```

---

## 테스트 코드

```
test/java/com/mjusugangsincheonghelper/multigame/
├── my/service/
│   └── MultigameMyHistoryServiceTest.java
├── stats/service/
│   ├── MultigameMyStatsServiceTest.java
│   └── MultigameDepartmentStatsServiceTest.java
└── dashboard/service/
    └── MultigameDashboardServiceTest.java
```

### 테스트 커버리지

- 내 참여 기록 목록 조회
- 내 참여 통계 요약 조회 (정상/빈 데이터)
- 학과별 참여 횟수 순위 조회 (내 학과 포함/미포함)
- 학과별 성공률 순위 조회 (내 학과 포함/미포함)
- 대시보드 조회 (오늘 게임/내 기록/전체 통계)

---

## 권한

모든 API는 `@PreAuthorize("hasRole('GUEST')")`로 보호됩니다.
- GUEST 이상 권한 필요 (GUEST, MEMBER, ADMIN)

---

## 에러 코드

기존 에러 코드 재사용:
- `AUTH_MEMBER_NOT_FOUND` - 회원 정보를 찾을 수 없음
- `GLOBAL_INTERNAL_SERVER_ERROR` - 서버 내부 오류

---

## 성능 고려사항

1. **학과별 통계 캐시**: 5분 TTL로 설정하여 DB 부하 감소
2. **Native Query**: 복잡한 집계 쿼리는 Native Query로 최적화
3. **페이징**: 내 기록 목록은 페이징으로 대량 데이터 처리

---

## 향후 확장 가능성

1. **과목별 인기 순위**: 어떤 과목이 가장 많이 신청되었는지
2. **시간대별 참여 통계**: 시간대별 평균 참여자 수
3. **전체 게임 목록**: FINALIZE된 게임 목록 페이징 조회
4. **대시보드 캐시**: 성능 최적화를 위한 Redis 캐시 적용
