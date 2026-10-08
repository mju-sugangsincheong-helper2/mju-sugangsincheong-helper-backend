# 싱글게임 API별 DB 조회 목록 (02)

> 5개 API 유지 전제. 각 API가 치는 repository 쿼리 전수 + 단독/공유 구분.
> 코드는 `SingleGameService`·`SingleGameStatsService` 기준.

---

## 1. POST /singlegame (저장)

- `memberRepository.existsById` — 저장 전 존재 확인
- `singleGameRepository.save` + `singleGameDetailRepository.saveAll` — 쓰기
- 통계 캐시는 건드리지 않음 (확정). 저장은 저장만 하고 신선도는 TTL에만 맡겨 쿼리 시간을 일정하게 유지한다.
  (`StatsService.evict` 삭제됨, 01-structure §4).

## 2. GET /{gameId}/analysis (분석 — 수정됨)

원본 2 + 주인 1 + 보는사람 1 + COUNT 5(조건부 2) + 통계 5종(캐시 뒤):

| 쿼리 | 용도 | 빈도 |
|---|---|---|
| `findById(gameId)` | 판 1행 (없으면 404) | 1 |
| `findByGameIdOrderBySequenceAsc` | 디테일 N행 | 1 |
| `member.findById(판주인)` | 학과 통계·학과 순위 기준 | 1 |
| `member.findById(보는사람)` | `isOwner` + 게스트 가리기용 | 조건부 1 |
| `countBetterPersons` | 전체 순위 (`+1`, 사람 기준) | 1 |
| `countDistinctPersons` | 전체 순위 분모 (사람 수) | 1 |
| `countBetterDeptPersons(주인학과)` | 학과 순위 (`+1`) | 조건부 1 |
| `countDistinctDeptPersons(주인학과)` | 학과 순위 분모 | 조건부 1 |
| `countEnterMainBetterOrEqual` | 입장 백분위 (분자) | 1 |
| `countCompletedGames` | 입장 백분위 분모 (판 수) | 1 |
| `findSequencePercentileStats` 등 5종 | detail·타임라인 분포 (캐시 뒤) | 미스 시 |
| `findAllDetailsByTotalCourses` | 피드백 재료 (캐시 뒤, 전체 로드 — 성능 과제 잔류) | 미스 시 |
| 삭제됨 | `findGameIds...`×2·`findDeptRankedGameIds`·`computeRank`·`RankingSummary`·`BasicEvent` | — |

## 3. GET /rank (랭킹판 — 수정됨)

| 쿼리 | 용도 | 빈도 |
|---|---|---|
| `member.findById` | 내 학과 (scope 정규화) | 1 |
| `findRankingRaw(tc, 20)` / `findDeptRankingRaw(tc, dept, 20)` | 스냅샷 — **DB에서 20줄만 (캐시 뒤)** | 미스 시 |
| `findMyBestGame` | 내 대표판 1행 (없으면 myRank null) | 조건부 1 |
| `countBetterPersons` / `countBetterDeptPersons` | myRank (scope 기준) | 조건부 1 |
| 삭제됨 | `findFirstClickRaw`·`findTopByMember...`·subRankings 로직·`findGameIds...`(myRank용) | — |

scope 규칙: DEPARTMENT + department 지정 → 그 학과. department 생략 → 요청자 본인 학과.
학과 없는 요청자의 DEPARTMENT 사용은 400 `SINGLEGAME_INVALID_RANK_DEPARTMENT` (폴백 없음).
응답 `department`에 적용 학과 명시 (GLOBAL이면 null).
과목 수(totalCourses)마다 랭킹이 따로 집계된다. 목록·myRank 모두 사람 기준 최고기록 + 동점 동순위 (00 §5·§7).

## 4. GET /my (내 기록 — 순위 제거됨)

- 응답에서 `ranking`·`tEnterMain` 삭제. `gameId·totalCourses·completed·tTotal·createdAt`만 나간다.
- 순위 계산이 통째로 빠지면서 판마다 치던 쿼리(ID 로드 2종 + COUNT 2종)도 삭제.
  남는 건 페이징 1방(`findByMemberIdOrderByCreatedAtDesc`)뿐이라 DB 추가 조회 없이 끝난다.
- `MyRecordResponse.RecordRanking`·`RankInfo` 중첩 클래스 삭제.
- 미리 만든 COUNT 3종(`countBetterTTotal`·`countBetterDeptTTotal`·`countDeptGames`)은
  analysis·rank 수정 때 재사용 예정이라 repository에 유지한다.

## 5. GET /departments (학과 목록)

- `findDistinctDepartments` 1방. 문제없음.

## 6. 단독 / 공유 정리

- 단독: 저장 3종, departments 1종, my 페이징 1종, 최신 1판 1종.
- 공유: `member.findById` (rank·my·analysis — PK 단건이라 무해),
  tTotal ID 로드 (rank·my·analysis), enterMain ID 로드 (analysis ×2),
  학과 ID 로드 (analysis·my 판마다), 완료판 COUNT (analysis·my 판마다).
- 결론: **ID 뭉치 3종(tTotal·enterMain·학과)을 COUNT로 바꾸면 rank·my·analysis가 한 번에 풀린다.**
  my는 교체 완료. analysis·rank의 동일 패턴은 그때 같이 바꾼다.
  rank 스냅샷 전체 로드는 별개 작업으로 남는다.
