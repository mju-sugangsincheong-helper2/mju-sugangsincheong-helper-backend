# findDeptSequencePercentileStats 분석 (01 — 쿼리 설명 + 인덱스 현황)

> 대상: `SingleGameRepository.findDeptSequencePercentileStats(totalCourses, department)`
> 위치: `src/main/java/com/mjusugangsincheonghelper/database/repository/SingleGameRepository.java` (~L172~)
> 상태: EXPLAIN 실행 전, 코드·DDL 기반 이론 분석. 실측은 02에서 진행.
> 관련 DDL: `src/main/resources/schema-prod.sql` (§8 single_game, §9 single_game_detail, §1 member)

---

## 1. 쿼리 전문

```sql
WITH ranked AS (
    SELECT d.game_id, d.sequence,
           d.t_click_course, d.t_click_yes, d.t_click_ok,
           d.t_click_course + d.t_click_yes + d.t_click_ok AS total,
           ROW_NUMBER() OVER (PARTITION BY d.sequence ORDER BY d.t_click_course) AS rn_cc,
           COUNT(*) OVER (PARTITION BY d.sequence) AS cnt_cc,
           ROW_NUMBER() OVER (PARTITION BY d.sequence ORDER BY d.t_click_yes) AS rn_cy,
           COUNT(*) OVER (PARTITION BY d.sequence) AS cnt_cy,
           ROW_NUMBER() OVER (PARTITION BY d.sequence ORDER BY d.t_click_ok) AS rn_cok,
           COUNT(*) OVER (PARTITION BY d.sequence) AS cnt_cok,
           ROW_NUMBER() OVER (PARTITION BY d.sequence ORDER BY d.t_click_course + d.t_click_yes + d.t_click_ok) AS rn_total,
           COUNT(*) OVER (PARTITION BY d.sequence) AS cnt_total
    FROM single_game_detail d
    JOIN single_game sg ON sg.id = d.game_id
    JOIN member m ON sg.member_id = m.id
    WHERE sg.total_courses = :totalCourses
      AND sg.is_completed = TRUE
      AND m.department = :department
)
SELECT sequence,
       MAX(CASE WHEN metric = 'cc' AND p = 0.10 THEN val END) AS cc_p10,
       ... (지표 4종 x 분위 4종 = 16 컬럼)
FROM (
    SELECT sequence, 'cc' AS metric, 0.10 AS p, t_click_course AS val FROM ranked WHERE rn_cc = CEIL(cnt_cc * 0.10)
    UNION ALL ... x16
    (cc/cy/cok/total 각 p10·p30·p50·p70)
) combined
GROUP BY sequence
ORDER BY sequence ASC;
```

입력: `(totalCourses, department)` / 출력: `sequence`별 1행 (예: totalCourses=6이면 6행, 17컬럼).

---

## 2. 쿼리 설명 (3단계)

### 2-1. `ranked` CTE — 분석 대상 집합 + 순서 매기기

1. 3테이블 조인으로 "해당 과목수·완료게임·해당 학과"의 detail 행만 남긴다.
   행 수 = `(해당 totalCourses 게임 수 / 학과 수) x totalCourses`.
   예: 게임 100만·학과 20개·totalCourses=6 → 약 30만행.
2. `PARTITION BY d.sequence` (파티션 수 = totalCourses, 각 파티션에 위 행의 1/totalCourses) 안에서
   지표 4종(`cc`, `cy`, `cok`, `total`)별로 `ROW_NUMBER() ... ORDER BY 지표`를 매긴다.
   → 서로 다른 ORDER BY가 4개이므로 **정렬이 4회 필요** (이 쿼리의 지배 비용).
3. `COUNT(*) OVER (PARTITION BY d.sequence)` x4는 같은 파티션 카운트라 추가 정렬 없이 재활용됨.
   (4개가 텍스트상 중복 — `cnt_cc/cy/cok/total`은 값이 동일. 아래 §4 참고.)

### 2-2. `UNION ALL x16` — 분위수 행 뽑기

nearest-rank 방식: `rn = CEIL(cnt * p)`인 행 1개를 지표·분위마다 추출.
16개 브랜치가 `ranked`를 16번 스캔한다. PG는 이를 1회 구체화(Materialize) 후
`CTE Scan x16`으로 처리할 가능성이 높다 (EXPLAIN에서 확인 예정).

### 2-3. 바깥 `GROUP BY sequence` — 피벗

16개 행 → `MAX(CASE WHEN metric/p ...)`로 sequence당 1행·16컬럼으로 접기.
입력행이 `sequence 수 x 16` (예: 96행)이라 비용은 무시 가능.
`ORDER BY sequence`도 정렬 대상이 수십 행이라 무시 가능.

**한 줄 요약: 작은 결과(수 행)를 위해 큰 중간재(수십만 행)를 4번 정렬 + 16번 스캔하는 쿼리.**

---

## 3. 현재 인덱스 현황 (schema-prod.sql 기준)

| 테이블 | 인덱스 | 이 쿼리에 대한 평가 |
|---|---|---|
| `member` | `PRIMARY KEY (id)` 뿐. **`department` 인덱스 없음** | `m.department = :dept` 필터가 인덱스 없이 수행됨. 아래 조인 순서 문제의 원인 |
| `single_game` | ① `PRIMARY KEY (id)` ② `idx_game_ranking(total_courses, is_completed, t_total)` ③ `idx_game_member(member_id, created_at)` | ②가 `total_courses + is_completed` 필터에 사용 가능. 단 세번째 컬럼 `t_total`은 이 쿼리에서 ORDER BY가 없어 무용지물 (인덱스 스킵 불가, 앞 2컬럼만 범위 스캔). ③은 반대 방향 조인(`member → game`) 시에만 유효 |
| `single_game_detail` | `PRIMARY KEY (game_id, sequence)` 뿐. **`sequence` 단독 인덱스 없음** | `sg.id = d.game_id` Nested Loop 조인은 PK 앞컬럼으로 가능. 그러나 `PARTITION BY sequence` 정렬에는 도움 안 됨 |

정리:

- `WHERE`의 3조건 중 인덱스로 타는 것은 `total_courses, is_completed`뿐.
  `department`는 어느 인덱스의 선행 컬럼에도 없음.
- 조인 키(`sg.id`, `sg.member_id → m.id`, `d.game_id`)는 전부 PK/FK라 Nested Loop 자체는 가능.
  문제는 **어느 테이블에서 먼저 걸러낼 것인가**인데, 선택도가 높은 `department` 쪽에 드라이빙할 인덱스가 없다는 점.

---

## 4. 이 인덱스 상태에서 예상되는 플랜 (EXPLAIN 전 가설, 02에서 검증)

가설 A (유력): `single_game (idx_game_ranking)` → `member (PK lookup + Filter)` → `detail (PK lookup)`

- `idx_game_ranking`으로 `totalCourses` 게임 id를 먼저 뽑고, 건건이 `member`를 뒤져 `department`로 버림.
- 학과가 20개 균등분포면 **뽑은 행의 ~95%를 버리고** detail 조인까지 감.
- EXPLAIN에서 `Rows Removed by Filter: ~95%` + `Heap Fetches` 다량으로 보일 것.

가설 B: `member (Seq Scan + Filter dept)` → `single_game (idx_game_member)` → `detail`

- 학과 인원이 적으면 플래너가 이쪽을 택할 수 있음. 그래도 `member` Seq Scan은 피할 수 없음.

공통 (데이터가 쌓이면 반드시 보일 것):

1. `WindowAgg` 앞 `Sort (PARTITION BY sequence, ORDER BY 지표)` x4. `work_mem`(기본 4MB) 초과 시 `external merge Disk`로 전환 → 이때부터 비선형 급증.
2. `CTE ranked`의 `Materialize` + `CTE Scan x16`.
3. 데이터 소량(dev 초기)에서는 전부 Seq Scan + quicksort로 "빠르게" 나오니, **소량 EXPLAIN은 참고용에 불과** — 반드시 seeded 상태에서 재확인.

관측 포인트 (02 체크리스트):

- [ ] `Sort Method: quicksort vs external merge` + `Sort Space Used`
- [ ] `Buffers: shared hit / read` (캐시 효과와 분리하기 위해)
- [ ] `CTE Scan on ranked` 반복 횟수 (16회인가)
- [ ] `Rows Removed by Filter` 비율 (가설 A 검증)

---

## 5. 참고 — 쿼리 자체의 특이점 (인덱스 이전에 알아둘 것, 튜닝은 별도 문서)

1. `cnt_*` 4개 중복: 값이 전부 `COUNT(*) OVER (PARTITION BY d.sequence)`로 동일. 1개로 줄여도 의미 동일 (가독성 vs 파서 혼란 문제, 수정 시 별도 검토).
2. 전역 통계(`v_sequence_percentile_stats`)는 `PERCENTILE_CONT` 선형보간, 학과 쿼리는 `CEIL` nearest-rank → **같은 p50도 값이 다름**. 성능 비교 시 단순 교체 불가.
3. `total`은 표현식(`cc+cy+cok`)이라 어떤 인덱스로도 정렬 회피 불가. 정렬 자체를 줄이는 방향(집계 방식 변경)이 아니면 인덱스로 해결 안 됨.

---

## 다음 문서

- `singlegame-api-01-structure.md` (별도 트랙): 실API(`SingleGameController` 일대) 구조 검토. 쿼리 손보기 전에 이쪽 §5 순서대로 먼저.
- `02-explain-실측.md` (예정): `EXPLAIN (ANALYZE, BUFFERS)` 결과 기록 + §4 가설 검증.
  실행 쿼리는 이 문서 §1 전문에 `totalCourses=6, department='컴퓨터공학과'` 바인딩.
  실행 전 `VACUUM ANALYZE single_game / single_game_detail / member` 필수 (dev는 `ddl-auto=update`라 통계가 낡기 쉬움).
