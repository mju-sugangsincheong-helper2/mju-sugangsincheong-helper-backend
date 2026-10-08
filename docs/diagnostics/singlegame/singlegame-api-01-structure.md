# 싱글게임 실API 구조 검토 (01)

> 대상: `singlegame/controller/SingleGameController.java` + `SingleGameService` + `SingleGameStatsService`.
> 00(도메인)의 "낱개 1개 + 통계 첨부"가 코드에서 어떻게 조립되는지, 그 구조가 적절한지 본다.
> 진단은 dev API가 아니라 이 실API를 본다.

---

## 1. 엔드포인트 5개

- `POST /singlegame` — 저장 (`saveGame`)
- `GET /singlegame/{gameId}/analysis` — 판 분석 (핫패스, 00의 경우 4개가 여기로 옴)
- `GET /singlegame/rank?totalCourses&scope&department` — 랭킹판
- `GET /singlegame/my` — 내 기록 (페이징, 무난)
- `GET /singlegame/departments` — 학과 목록 (무난)

아래는 문제 있는 3개만 다룬다. `my`·`departments`는 구조상 문제없다.

## 2. 분석 API: 조립은 맞고 랭킹이 문제다

`getAnalysis` (Service L366~)의 뼈대는 00과 일치한다. 원본은 PK 단건 조회,
통계는 `SingleGameStatsService` 캐시에서. 이 부분은 적절하다.

문제는 맨 밑 `buildRankingSummary`다. 요청마다 DB에서 ID 목록 전체를 끌어와 자바로 순위를 센다.

- `computeRank` (L704): `findGameIdsWithBetterOrEqualTTotal` 전체 로드 → `.size()`가 순위.
- 학과 랭크 (L430~): `findDeptRankedGameIds` 전체 로드 → for문으로 내 게임 위치 탐색.
- 둘 다 캐시 없음. 100만이면 **분석 1회에 수십만 ID 전송**이다. 학과 통계 2.4초보다 이쪽이 클 수 있다.
- 덤으로 학과 기준이 보는 사람이다. 00 §6(판 주인 기준) 위반이라 어차피 고쳐야 한다.

방향 (00 §7과 동일): ID 목록 로드 삭제, `나보다 앞선 판 수 + 1` COUNT(*) 쿼리로. 학과 랭크도 COUNT로. 푸는 김에 기준도 판 주인으로.

## 3. 랭킹판 API: 고침 완료

- 스냅샷 쿼리에 `LIMIT 20`을 넣어 DB에서 20줄만 가져온다. 자바에서 자르던 코드 삭제.
  학과 범위에서 전체를 한 번 더 뜨던 중복 쿼리도 삭제.
- `subRankings` 응답 통째로 삭제 (`RankingResponse.SubRankings`·`SubEntry` 클래스 삭제,
  `findFirstClickRaw` 쿼리 삭제).
- `myRank` 수정: 최신판 기준 → **대표판(인당 최고 기록) 기준**, scope 무시 → **scope 기준**
  (GLOBAL이면 전체 순위, DEPARTMENT면 해당 학과 내 순위). 완료판 없으면 null.
  새 쿼리: `findMyBestGame` + `countBetterPersons` + `countBetterDeptPersons`.
- 목록·myRank 모두 사람 기준 + 동점자는 앞선 사람 수 + 1 (00 §5·§7).

## 4. 저장 시 캐시를 지우지 않는다 (확정)

한때 `saveGame`이 global 통계 캐시를 지웠는데, evict 때마다 캐시 미스가 터져 쿼리 시간이 들쭉날쭉해진다.
그래서 저장은 저장만 하고, 신선도는 TTL 만료에만 맡긴다. (`StatsService.evict` 삭제됨)

대신 응답 note나 운영 문서에 "통계·랭킹은 TTL까지 stale"을 명시한다. 실시간성이 필요해지면 그때
별도 무효화 키를 설계한다. 지금은 예측 가능한 쿼리 시간이 우선이다.

## 5. 순서 정리

1. 분석 랭킹 COUNT화 (§2) — 핫패스 + 00 §6·§7과 한 번에 해결. (my는 선적용됨, 같은 쿼리 재사용)
2. 랭킹판 DB TOP N (§3) — 미스 비용 상수화.
3. 그 다음 01의 `02-explain-실측`으로 쿼리 플랜 검증. 순서를 바꾸지 말 것 (구조를 먼저 싸게 만들고 잰다).

저장 시 evict는 폐지됨 (§4). TTL만으로 신선도 관리.
