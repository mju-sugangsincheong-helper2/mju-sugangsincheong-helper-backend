package com.mjusugangsincheonghelper.singlegame.service;

import com.mjusugangsincheonghelper.database.repository.SingleGameHistogramRepository;
import com.mjusugangsincheonghelper.singlegame.dto.SingleGameDetailRequest;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 싱글게임 버킷 히스토그램 사전집계 쓰기 전담.
 *
 * <p>완료된 게임 1판마다 구간 지표 4종 x sequence + 진입 TOTAL + 피드백 4종을
 * 5ms 버킷 카운터로 누적한다. 조회 쿼리는 이 테이블의 수백 행 누적합만으로 끝나 상수 시간에 동작한다.</p>
 *
 * <p>버킷 규칙(읽기·백필 SQL과 동일): 정수는 (v/5)*5, 평균·표준편차는 floor(v/5)*5.
 * 값은 항상 0 이상이므로 정수 나눗셈 내림과 floor가 일치한다.
 * 구간 평균은 정수 합의 유리수라 버킷 경계와의 거리가 1/n 이상으로
 * double丸め와 SQL numeric 계산이 엇갈리지 않는다.</p>
 */
@Service
@RequiredArgsConstructor
public class SingleGameHistogramService {

	/** 버킷폭. 읽기 쿼리·백필 SQL과 같은 값을 쓴다. */
	public static final int BUCKET_MS = 5;

	private final JdbcTemplate jdbcTemplate;
	private final SingleGameHistogramRepository histogramRepository;

	private static final String UPSERT_SQL = """
			INSERT INTO single_game_histogram
				(total_courses, scope, department, sequence, metric, bucket_ms, cnt)
			VALUES (?, ?, ?, ?, ?, ?, 1)
			ON CONFLICT (total_courses, scope, department, sequence, metric, bucket_ms)
			DO UPDATE SET cnt = single_game_histogram.cnt + 1
			""";

	/** 정수 버킷 하한. (v/5)*5 와 동일하다. */
	public static int bucketize(int value) {
		return Math.floorDiv(value, BUCKET_MS) * BUCKET_MS;
	}

	/** 실수 버킷 하한. floor(v/5)*5 와 동일하다. */
	public static int bucketize(double value) {
		return (int) (Math.floor(value / BUCKET_MS) * BUCKET_MS);
	}

	/**
	 * 완료 판 1건을 히스토그램에 누적한다. 저장 트랜잭션 안에서 호출되어
	 * 게임·디테일 저장과 원자적으로 함께 커밋/롤백된다.
	 */
	public void recordCompletion(int totalCourses, String department, int tEnterMain,
			List<SingleGameDetailRequest> details) {
		String dept = department == null ? "" : department;
		List<Object[]> rows = new ArrayList<>();

		long sumCc = 0;
		long sumBurst = 0;
		int minSequence = Integer.MAX_VALUE;
		int firstTotal = 0;
		int n = details.size();
		double[] totals = new double[n];

		for (int i = 0; i < n; i++) {
			SingleGameDetailRequest d = details.get(i);
			int total = d.getTClickCourse() + d.getTClickYes() + d.getTClickOk();
			totals[i] = total;
			sumCc += d.getTClickCourse();
			sumBurst += d.getTClickYes() + d.getTClickOk();
			if (d.getSequence() < minSequence) {
				minSequence = d.getSequence();
				firstTotal = total;
			}
			int[] buckets = {
					bucketize(d.getTClickCourse()),
					bucketize(d.getTClickYes()),
					bucketize(d.getTClickOk()),
					bucketize(total)};
			for (int m = 0; m < 4; m++) {
				short metric = (short) (m + 1);
				rows.add(new Object[]{totalCourses, (short) 0, "", d.getSequence(), metric, buckets[m]});
				rows.add(new Object[]{totalCourses, (short) 1, dept, d.getSequence(), metric, buckets[m]});
			}
		}

		int enterBucket = bucketize(tEnterMain);
		rows.add(new Object[]{totalCourses, (short) 0, "", 0, (short) 4, enterBucket});
		rows.add(new Object[]{totalCourses, (short) 1, dept, 0, (short) 4, enterBucket});

		double avgCc = (double) sumCc / n;
		double avgBurst = (double) sumBurst / n;
		rows.add(new Object[]{totalCourses, (short) 2, "", 0, (short) 1, bucketize(avgCc)});
		rows.add(new Object[]{totalCourses, (short) 2, "", 0, (short) 2, bucketize(avgBurst)});
		rows.add(new Object[]{totalCourses, (short) 2, "", 0, (short) 3, bucketize(firstTotal)});
		if (n >= 3) {
			double mean = (double) (sumCc + sumBurst) / n;
			double variance = 0;
			for (double t : totals) {
				double diff = t - mean;
				variance += diff * diff;
			}
			variance /= n;
			rows.add(new Object[]{totalCourses, (short) 2, "", 0, (short) 4,
					bucketize(Math.sqrt(variance))});
		}

		jdbcTemplate.batchUpdate(UPSERT_SQL, rows, rows.size(),
				(ps, row) -> {
					ps.setInt(1, (Integer) row[0]);
					ps.setShort(2, (Short) row[1]);
					ps.setString(3, (String) row[2]);
					ps.setInt(4, (Integer) row[3]);
					ps.setShort(5, (Short) row[4]);
					ps.setInt(6, (Integer) row[5]);
				});
	}

	/**
	 * 전수 백필. 기존 detail 전체를 히스토그램으로 다시 채운다.
	 * 시드 적재·시드 삭제(진단용) 뒤 정합성을 맞추는 데 쓴다.
	 */
	@Transactional
	public void rebuild() {
		histogramRepository.truncateAll();
		histogramRepository.backfillFromDetails();
	}
}
