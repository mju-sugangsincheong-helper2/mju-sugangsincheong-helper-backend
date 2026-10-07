package com.mjusugangsincheonghelper.diagnostics.service;

import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import com.mjusugangsincheonghelper.database.repository.SingleGameRepository;
import com.mjusugangsincheonghelper.diagnostics.dto.QueryTimingResponse;
import com.mjusugangsincheonghelper.diagnostics.dto.SeedResultResponse;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.global.config.CacheProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * dev 전용 진단 측정/시드 서비스.
 *
 * <p>진입점({@code DiagnosticsController})과 마찬가지로 {@code dev} 프로파일에서만
 * 빈으로 등록된다. 측정은 Repository 직접 호출(서비스 캐시 우회)로 순수 DB 실행 시간을 잰다.</p>
 */
@Slf4j
@Service
@Profile("dev")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiagnosticsService {

	private static final List<Integer> ALLOWED_TOTAL_COURSES = List.of(1, 3, 6, 7, 8);
	private static final int MAX_SEED_COUNT = 1_000_000;
	private static final int SEED_GAMES_CHUNK = 5000;
	private static final int DETAIL_ROWS_PER_STATEMENT = 10000;

	/**
	 * 시드 학과 풀. 게임과 멤버를 라운드로빈으로 균등 분산한다.
	 * 학과마다 실력 계수가 달라 학과별 percentile 통계가 유의미하게 갈린다.
	 */
	private static final List<String> SEED_DEPARTMENTS = List.of(
			"컴퓨터공학과", "정보통신공학과", "전자공학과", "기계공학과",
			"화학공학과", "신소재공학과", "토목교통공학과", "건축학과",
			"경영학과", "경제학과", "법학과", "행정학과",
			"영어영문학과", "일어일문학과", "중어중문학과", "수학과",
			"물리학과", "화학과", "생명과학정보학과", "체육학과"
	);

	private final SingleGameRepository singleGameRepository;
	private final MemberRepository memberRepository;
	private final JdbcTemplate jdbcTemplate;
	private final CacheManager cacheManager;
	private final PlatformTransactionManager transactionManager;

	/**
	 * findDeptSequencePercentileStats 쿼리를 전 학과에 대해 실행 시간을 측정한다.
	 * SingleGameStatsService를 경유하지 않으므로 통계 캐시의 영향을 받지 않는다.
	 * warmup은 첫 학과에서만 수행한다 (학과마다 하면 측정 비용이 학과 수에 비례해 폭증하므로).
	 */
	public QueryTimingResponse timeDeptSequenceStats(int totalCourses, int warmup, int repeats) {
		if (warmup < 0 || warmup > 5 || repeats < 1 || repeats > 10) {
			throw new BaseException(ErrorCode.GLOBAL_VALIDATION_ERROR);
		}

		List<String> departments = singleGameRepository.findDistinctDepartments();

		List<QueryTimingResponse.DeptTiming> breakdown = new ArrayList<>(departments.size());
		List<Double> allRuns = new ArrayList<>();
		int totalRows = 0;
		boolean warmedUp = false;
		for (String department : departments) {
			if (!warmedUp) {
				for (int i = 0; i < warmup; i++) {
					singleGameRepository.findDeptSequencePercentileStats(totalCourses, department);
				}
				warmedUp = true;
			}
			List<Double> runs = new ArrayList<>(repeats);
			int rowCount = 0;
			for (int i = 0; i < repeats; i++) {
				long start = System.nanoTime();
				List<Object[]> rows = singleGameRepository.findDeptSequencePercentileStats(totalCourses, department);
				double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
				runs.add(elapsedMs);
				rowCount = rows.size();
			}
			double deptAvg = runs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
			breakdown.add(new QueryTimingResponse.DeptTiming(department, List.copyOf(runs), deptAvg, rowCount));
			allRuns.addAll(runs);
			totalRows += rowCount;
		}

		double min = allRuns.stream().mapToDouble(Double::doubleValue).min().orElse(0);
		double max = allRuns.stream().mapToDouble(Double::doubleValue).max().orElse(0);
		double avg = allRuns.stream().mapToDouble(Double::doubleValue).average().orElse(0);

		log.info("Dev diagnostics timing. query=findDeptSequencePercentileStats totalCourses={} departments={} warmup={} runs={} avgMs={}",
				totalCourses, departments.size(), warmup, allRuns.size(), avg);

		Map<String, Object> params = new LinkedHashMap<>();
		params.put("totalCourses", totalCourses);
		params.put("departmentCount", departments.size());

		return new QueryTimingResponse(
				"findDeptSequencePercentileStats",
				params,
			warmup,
			List.copyOf(allRuns),
				min,
				avg,
				max,
			totalRows,
			List.copyOf(breakdown),
				"전 학과 측정 (warmup은 첫 학과에서만, Repository 직접 호출로 캐시 우회). 순수 DB 실행 시간."
		);
	}

	/**
	 * 쿼리 속도 테스트용 싱글게임 데이터를 대량으로 쌓는다. 게임 수와 과목 수만 지정하면
	 * 학과·멤버·분포는 자동으로 분산 생성된다.
	 *
	 * <p>실서비스 저장({@code SingleGameService.saveGame})을 거치지 않고 JDBC 배치로 직접 넣는다.
	 * 단건 저장 경로(검증·캐시 evict)를 N번 반복하면 대량 시드에 부적합하기 때문이다.
	 * 데이터 정합성은 맞춘다: 완료 게임(is_completed=TRUE)만 만들고 detail 수는 totalCourses와 같으며,
	 * t_total = t_enter_main + detail 합이다.</p>
	 *
	 * <p>대량 적재를 위해 청크(5000게임)마다 독립 트랜잭션으로 커밋한다. 단일 트랜잭션으로
	 * 수백만 행을 쌓으면 WAL 폭증·장시간 락으로 dev DB가 멈출 수 있기 때문이다.</p>
	 */
	@Transactional
	public SeedResultResponse seedSingleGames(int count, int totalCourses) {
		if (count < 1 || count > MAX_SEED_COUNT) {
			throw new BaseException(ErrorCode.GLOBAL_VALIDATION_ERROR);
		}
		if (!ALLOWED_TOTAL_COURSES.contains(totalCourses)) {
			throw new BaseException(ErrorCode.SINGLEGAME_INVALID_TOTAL_COURSES);
		}

		int gameCount = count;
		int poolSize = Math.clamp(gameCount / 10, 50, 2000);

		TransactionTemplate chunkTx = new TransactionTemplate(transactionManager);
		chunkTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

		long start = System.nanoTime();
		long seedTag = System.currentTimeMillis() % 100000;

		// 1. 멤버 풀 생성 (학과 라운드로빈으로 균등 분산)
		List<Member> members = new ArrayList<>(poolSize);
		for (int i = 0; i < poolSize; i++) {
			members.add(Member.builder()
					.role(Member.Role.MEMBER)
					.name("SEED_" + seedTag + "_" + i)
					.position("SEED")
					.department(SEED_DEPARTMENTS.get(i % SEED_DEPARTMENTS.size()))
					.build());
		}
		List<Long> memberIds = chunkTx.execute(
				status -> memberRepository.saveAll(members).stream().map(Member::getId).toList());

		// 2. 게임 + 디테일 대량 삽입 (청크별 독립 트랜잭션)
		int inserted = 0;
		int detailCount = 0;
		int chunkIndex = 0;
		for (int offset = 0; offset < gameCount; offset += SEED_GAMES_CHUNK) {
			int chunk = Math.min(SEED_GAMES_CHUNK, gameCount - offset);
			final int currentOffset = offset;
			int details = chunkTx.execute(status -> insertGameChunk(chunk, totalCourses, memberIds, currentOffset));
			detailCount += details;
			inserted += chunk;
			chunkIndex++;
			if (chunkIndex % 10 == 0 || inserted == gameCount) {
				log.info("Dev diagnostics seeding. progress={}/{} games, {} details", inserted, gameCount, detailCount);
			}
		}

		// 3. 데이터가 대규모로 바뀌었으므로 통계 캐시 전체 무효화 (global/dept 구분 없이 clear)
		Cache cache = cacheManager.getCache(CacheProperties.SINGLEGAME_STATS);
		if (cache != null) {
			cache.clear();
		}

		double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
		log.info("Dev diagnostics seed done. games={} details={} totalCourses={} members={} elapsedMs={}",
				inserted, detailCount, totalCourses, poolSize, elapsedMs);

		return new SeedResultResponse(inserted, detailCount, totalCourses, SEED_DEPARTMENTS, poolSize, elapsedMs);
	}

	/**
	 * 게임 chunk개를 multi-row INSERT로 넣고 생성된 id를 반환받은 뒤, detail을
	 * multi-row INSERT(PG 파라미터 상한 65535를 넘지 않게 분할)로 넣는다.
	 * 시퀀스명을 하드코딩하지 않기 위해 nextval 대신 INSERT ... RETURNING id를 사용한다
	 * (dev는 ddl-auto=update라 시퀀스명이 환경마다 다를 수 있음).
	 *
	 * @return 삽입된 detail 행 수
	 */
	private int insertGameChunk(int chunk, int totalCourses, List<Long> memberIds, int offset) {
		ThreadLocalRandom random = ThreadLocalRandom.current();

		// 게임별 detail 타이밍을 먼저 뽑아 t_total 정합성을 맞춘다.
		// 분포는 실제 analysis 응답의 percentile을 참고한 로그정규분포이며,
		// 학과별 실력 계수(0.8~1.5)를 곱해 학과 간 통계가 갈리도록 한다.
		List<List<int[]>> allTimings = new ArrayList<>(chunk);
		StringBuilder gameSql = new StringBuilder(
				"INSERT INTO single_game (member_id, t_total, t_enter_main, is_completed, total_courses, created_at, updated_at) VALUES ");
		List<Object> gameArgs = new ArrayList<>(chunk * 4);
		for (int i = 0; i < chunk; i++) {
			if (i > 0) {
				gameSql.append(',');
			}
			gameSql.append("(?,?,?,TRUE,?,now(),now())");
			int memberIdx = (offset + i) % memberIds.size();
			double skill = skillFactor(memberIdx);
			int tEnterMain = lognormal(random, 210 * skill, 0.6, 5, 3000);
			List<int[]> timings = new ArrayList<>(totalCourses);
			int sum = 0;
			for (int seq = 1; seq <= totalCourses; seq++) {
				int cc = lognormal(random, (seq == 1 ? 700 : 250) * skill, 0.55, 10, 5000);
				int cy = lognormal(random, 135 * skill, 0.7, 5, 2000);
				int cok = lognormal(random, 170 * skill, 0.7, 5, 2000);
				timings.add(new int[]{seq, cc, cy, cok});
				sum += cc + cy + cok;
			}
			allTimings.add(timings);
			gameArgs.add(memberIds.get(memberIdx));
			gameArgs.add(tEnterMain + sum);
			gameArgs.add(tEnterMain);
			gameArgs.add(totalCourses);
		}
		List<Long> gameIds = jdbcTemplate.query(gameSql.append(" RETURNING id").toString(),
				(rs, rowNum) -> rs.getLong(1), gameArgs.toArray());

		StringBuilder detailSql = new StringBuilder(
				"INSERT INTO single_game_detail (game_id, sequence, t_click_course, t_click_yes, t_click_ok) VALUES ");
		List<Object> detailArgs = new ArrayList<>(DETAIL_ROWS_PER_STATEMENT * 5);
		int rowsInStatement = 0;
		int totalRows = 0;
		for (int g = 0; g < chunk; g++) {
			for (int[] t : allTimings.get(g)) {
				if (rowsInStatement > 0) {
					detailSql.append(',');
				}
				detailSql.append("(?,?,?,?,?)");
				detailArgs.add(gameIds.get(g));
				detailArgs.add(t[0]);
				detailArgs.add(t[1]);
				detailArgs.add(t[2]);
				detailArgs.add(t[3]);
				rowsInStatement++;
				totalRows++;
				if (rowsInStatement == DETAIL_ROWS_PER_STATEMENT) {
					jdbcTemplate.update(detailSql.toString(), detailArgs.toArray());
					detailSql.setLength(0);
					detailSql.append(
							"INSERT INTO single_game_detail (game_id, sequence, t_click_course, t_click_yes, t_click_ok) VALUES ");
					detailArgs.clear();
					rowsInStatement = 0;
				}
			}
		}
		if (rowsInStatement > 0) {
			jdbcTemplate.update(detailSql.toString(), detailArgs.toArray());
		}
		return totalRows;
	}

	private double skillFactor(int memberIdx) {
		int deptIdx = memberIdx % SEED_DEPARTMENTS.size();
		return 0.8 + 0.7 * deptIdx / (SEED_DEPARTMENTS.size() - 1);
	}

	private int lognormal(ThreadLocalRandom random, double median, double sigma, int min, int max) {
		double value = Math.exp(Math.log(median) + sigma * random.nextGaussian());
		return Math.clamp((int) Math.round(value), min, max);
	}
}
