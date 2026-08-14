package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.LatencyEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LatencyRepository extends JpaRepository<LatencyEntity, Long> {

	/** 회원별 최신 결과 조회 (랭킹/분포 계산용) */
	Optional<LatencyEntity> findTopByMemberIdOrderByCreatedAtDesc(Long memberId);

	/** 내 히스토리 페이징 조회 */
	Page<LatencyEntity> findByMemberIdOrderByCreatedAtDesc(Long memberId, Pageable pageable);

	/**
	 * Median 히스토그램 집계 (width_bucket 사용)
	 * @param bucketSizeMs 버킷 크기 (ms)
	 * @param maxMs 최대값 (버킷 범위 상한)
	 */
	@Query(value = """
			SELECT
			    (bucket_id - 1) * :bucketSizeMs AS bucket_start,
			    bucket_id * :bucketSizeMs AS bucket_end,
			    COUNT(*) AS count
			FROM (
			    SELECT width_bucket(median_ms, 0, :maxMs, :maxMs / :bucketSizeMs) AS bucket_id
			    FROM latency
			) AS buckets
			WHERE bucket_id > 0 AND bucket_id <= :maxMs / :bucketSizeMs
			GROUP BY bucket_id
			ORDER BY bucket_id
			""", nativeQuery = true)
	List<Object[]> findMedianHistogram(@Param("bucketSizeMs") int bucketSizeMs,
	                                    @Param("maxMs") int maxMs);

	/**
	 * Max (Worst) 히스토그램 집계
	 */
	@Query(value = """
			SELECT
			    (bucket_id - 1) * :bucketSizeMs AS bucket_start,
			    bucket_id * :bucketSizeMs AS bucket_end,
			    COUNT(*) AS count
			FROM (
			    SELECT width_bucket(max_ms, 0, :maxMs, :maxMs / :bucketSizeMs) AS bucket_id
			    FROM latency
			) AS buckets
			WHERE bucket_id > 0 AND bucket_id <= :maxMs / :bucketSizeMs
			GROUP BY bucket_id
			ORDER BY bucket_id
			""", nativeQuery = true)
	List<Object[]> findMaxHistogram(@Param("bucketSizeMs") int bucketSizeMs,
	                                 @Param("maxMs") int maxMs);

	/**
	 * StdDev (Jitter) 히스토그램 집계
	 */
	@Query(value = """
			SELECT
			    (bucket_id - 1) * :bucketSizeMs AS bucket_start,
			    bucket_id * :bucketSizeMs AS bucket_end,
			    COUNT(*) AS count
			FROM (
			    SELECT width_bucket(std_dev_ms, 0, :maxMs, :maxMs / :bucketSizeMs) AS bucket_id
			    FROM latency
			) AS buckets
			WHERE bucket_id > 0 AND bucket_id <= :maxMs / :bucketSizeMs
			GROUP BY bucket_id
			ORDER BY bucket_id
			""", nativeQuery = true)
	List<Object[]> findStdDevHistogram(@Param("bucketSizeMs") int bucketSizeMs,
	                                    @Param("maxMs") int maxMs);

	/** 전체 레코드 수 (모든 측정 기록) */
	@Query(value = """
			SELECT COUNT(*) FROM latency
			""", nativeQuery = true)
	long countAllRecords();

	/**
	 * Median 기준 내 순위 계산 (오름차순: 낮은 값이 좋음)
	 * 모든 레코드를 기준으로 비교
	 */
	@Query(value = """
			SELECT COUNT(*) + 1
			FROM latency
			WHERE median_ms < :value
			""", nativeQuery = true)
	long countBetterThanMedian(@Param("value") double value);

	/**
	 * Max (Worst) 기준 내 순위 계산
	 * 모든 레코드를 기준으로 비교
	 */
	@Query(value = """
			SELECT COUNT(*) + 1
			FROM latency
			WHERE max_ms < :value
			""", nativeQuery = true)
	long countBetterThanMax(@Param("value") double value);

	/**
	 * StdDev (Jitter) 기준 내 순위 계산
	 * 모든 레코드를 기준으로 비교
	 */
	@Query(value = """
			SELECT COUNT(*) + 1
			FROM latency
			WHERE std_dev_ms < :value
			""", nativeQuery = true)
	long countBetterThanStdDev(@Param("value") double value);

	/** 전체 통계 (평균, 중앙값, P90) */
	@Query(value = """
			SELECT
			    AVG(latest.median_ms) AS avg_median,
			    PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY latest.median_ms) AS p50_median,
			    PERCENTILE_CONT(0.90) WITHIN GROUP (ORDER BY latest.median_ms) AS p90_median,
			    AVG(latest.max_ms) AS avg_max,
			    PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY latest.max_ms) AS p50_max,
			    PERCENTILE_CONT(0.90) WITHIN GROUP (ORDER BY latest.max_ms) AS p90_max,
			    AVG(latest.std_dev_ms) AS avg_stddev,
			    PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY latest.std_dev_ms) AS p50_stddev,
			    PERCENTILE_CONT(0.90) WITHIN GROUP (ORDER BY latest.std_dev_ms) AS p90_stddev
			FROM (
			    SELECT l.median_ms, l.max_ms, l.std_dev_ms,
			           ROW_NUMBER() OVER (PARTITION BY l.member_id ORDER BY l.created_at DESC) AS rn
			    FROM latency l
			) latest
			WHERE latest.rn = 1
			""", nativeQuery = true)
	List<Object[]> findOverallStats();
}
