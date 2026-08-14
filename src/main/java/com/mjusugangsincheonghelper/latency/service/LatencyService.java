package com.mjusugangsincheonghelper.latency.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.mjusugangsincheonghelper.database.entity.LatencyEntity;
import com.mjusugangsincheonghelper.database.repository.LatencyRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.global.config.CacheProperties;
import com.mjusugangsincheonghelper.latency.config.LatencyProperties;
import com.mjusugangsincheonghelper.latency.dto.LatencyDistributionResponse;
import com.mjusugangsincheonghelper.latency.dto.LatencyDistributionResponse.DistributionData;
import com.mjusugangsincheonghelper.latency.dto.LatencyDistributionResponse.HistogramBucket;
import com.mjusugangsincheonghelper.latency.dto.LatencyDistributionResponse.SummaryData;
import com.mjusugangsincheonghelper.latency.dto.LatencyMyRecordResponse;
import com.mjusugangsincheonghelper.latency.dto.LatencySubmitRequest;
import com.mjusugangsincheonghelper.latency.dto.LatencySubmitResponse;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LatencyService {

	private final LatencyRepository latencyRepository;
	private final LatencyProperties latencyProperties;
	private final CacheManager cacheManager;
	private final ObjectMapper objectMapper;

	@Transactional
	public LatencySubmitResponse submit(Long memberId, LatencySubmitRequest request, boolean isMember) {
		validateRequest(request);

		String samplesJson;
		try {
			samplesJson = objectMapper.writeValueAsString(request.getSamples());
		} catch (JacksonException e) {
			throw new BaseException(ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR, e);
		}

		LatencyEntity entity = LatencyEntity.builder()
			.memberId(memberId)
			.medianMs(request.getMedianMs())
			.maxMs(request.getMaxMs())
			.minMs(request.getMinMs())
			.stdDevMs(request.getStdDevMs())
			.sampleCount(request.getSampleCount())
			.samples(samplesJson)
			.build();

		LatencyEntity saved = latencyRepository.save(entity);

		// 저장된 결과를 포함한 분포 조회 (방금 제출한 값으로 myValue 계산)
		LatencyDistributionResponse distribution = getDistribution(memberId, isMember, saved);

		return LatencySubmitResponse.builder()
			.record(new LatencySubmitResponse.RecordInfo(saved.getId(), saved.getCreatedAt()))
			.distribution(distribution)
			.build();
	}

	private void validateRequest(LatencySubmitRequest request) {
		if (request.getSamples() == null || request.getSamples().isEmpty()) {
			throw new BaseException(ErrorCode.LATENCY_EMPTY_SAMPLES);
		}

		if (!request.getSampleCount().equals(request.getSamples().size())) {
			throw new BaseException(ErrorCode.LATENCY_SAMPLE_COUNT_MISMATCH);
		}

		for (Double sample : request.getSamples()) {
			if (sample < latencyProperties.getSampleMinMs() ||
			    sample > latencyProperties.getSampleMaxMs()) {
				throw new BaseException(ErrorCode.LATENCY_INVALID_SAMPLE_VALUE);
			}
		}
	}

	@Transactional(readOnly = true)
	public Page<LatencyMyRecordResponse> getMyHistory(Long memberId, Pageable pageable) {
		Page<LatencyEntity> page = latencyRepository.findByMemberIdOrderByCreatedAtDesc(memberId, pageable);

		return page.map(entity -> {
			List<Double> samples;
			try {
				samples = objectMapper.readValue(entity.getSamples(), new TypeReference<>() {});
			} catch (JacksonException e) {
				log.error("Failed to parse samples JSON: {}", entity.getSamples(), e);
				samples = List.of();
			}

			return new LatencyMyRecordResponse(
				entity.getId(),
				entity.getMedianMs(),
				entity.getMaxMs(),
				entity.getMinMs(),
				entity.getStdDevMs(),
				entity.getSampleCount(),
				samples,
				entity.getCreatedAt()
			);
		});
	}

	@Transactional(readOnly = true)
	public LatencyDistributionResponse getDistribution(Long memberId, boolean isMember, LatencyEntity justSubmitted) {
		CachedDistributionData cached = getCachedDistribution();

		// 익명 사용자도 방금 제출한 값이 있으면 myValue로 사용
		Double submittedMedian = justSubmitted != null ? justSubmitted.getMedianMs() : null;
		Double submittedMax = justSubmitted != null ? justSubmitted.getMaxMs() : null;
		Double submittedStdDev = justSubmitted != null ? justSubmitted.getStdDevMs() : null;

		// memberId가 null (익명)인 경우, 방금 제출한 값으로 분포만 반환
		if (memberId == null) {
			DistributionData medianData = buildDistributionData(
				cached.medianHistogram,
				cached.medianSummary,
				cached.totalParticipants,
				submittedMedian,
				submittedMedian != null ? latencyRepository.countBetterThanMedian(submittedMedian) : null
			);

			return LatencyDistributionResponse.builder()
				.median(medianData)
				.build();
		}

		// 인증된 사용자: DB에서 최신 값 조회 (justSubmitted가 있으면 그 값 우선)
		Double myMedian = submittedMedian != null ? submittedMedian : getMyMedianValue(memberId);
		DistributionData medianData = buildDistributionData(
			cached.medianHistogram,
			cached.medianSummary,
			cached.totalParticipants,
			myMedian,
			myMedian != null ? latencyRepository.countBetterThanMedian(myMedian) : null
		);

		// Worst, Jitter는 MEMBER만 볼 수 있음
		if (!isMember) {
			return LatencyDistributionResponse.builder()
				.median(medianData)
				.build();
		}

		Double myWorst = submittedMax != null ? submittedMax : getMyMaxValue(memberId);
		Double myJitter = submittedStdDev != null ? submittedStdDev : getMyStdDevValue(memberId);

		DistributionData worstData = buildDistributionData(
			cached.maxHistogram,
			cached.maxSummary,
			cached.totalParticipants,
			myWorst,
			myWorst != null ? latencyRepository.countBetterThanMax(myWorst) : null
		);

		DistributionData jitterData = buildDistributionData(
			cached.stdDevHistogram,
			cached.stdDevSummary,
			cached.totalParticipants,
			myJitter,
			myJitter != null ? latencyRepository.countBetterThanStdDev(myJitter) : null
		);

		return LatencyDistributionResponse.builder()
			.median(medianData)
			.worst(worstData)
			.jitter(jitterData)
			.build();
	}

	@Cacheable(value = CacheProperties.LATENCY_DISTRIBUTION, key = "'cache'", sync = true)
	@Transactional(readOnly = true)
	public CachedDistributionData getCachedDistribution() {
		log.debug("Cache miss for latency distribution, computing from DB");

		int medianBucketSize = latencyProperties.getMedianBucketSizeMs();
		int medianMaxMs = latencyProperties.getMedianMaxMs();
		int worstBucketSize = latencyProperties.getWorstBucketSizeMs();
		int worstMaxMs = latencyProperties.getWorstMaxMs();
		int jitterBucketSize = latencyProperties.getJitterBucketSizeMs();
		int jitterMaxMs = latencyProperties.getJitterMaxMs();

		List<Object[]> medianRaw = latencyRepository.findMedianHistogram(medianBucketSize, medianMaxMs);
		List<Object[]> maxRaw = latencyRepository.findMaxHistogram(worstBucketSize, worstMaxMs);
		List<Object[]> stdDevRaw = latencyRepository.findStdDevHistogram(jitterBucketSize, jitterMaxMs);
		List<Object[]> statsRaw = latencyRepository.findOverallStats();

		long totalParticipants = latencyRepository.countAllRecords();

		List<HistogramBucket> medianHistogram = buildHistogramBuckets(medianRaw, totalParticipants, medianBucketSize, medianMaxMs);
		List<HistogramBucket> maxHistogram = buildHistogramBuckets(maxRaw, totalParticipants, worstBucketSize, worstMaxMs);
		List<HistogramBucket> stdDevHistogram = buildHistogramBuckets(stdDevRaw, totalParticipants, jitterBucketSize, jitterMaxMs);

		SummaryData medianSummary = extractSummary(statsRaw, "median");
		SummaryData maxSummary = extractSummary(statsRaw, "max");
		SummaryData stdDevSummary = extractSummary(statsRaw, "stddev");

		return new CachedDistributionData(
			medianHistogram, medianSummary,
			maxHistogram, maxSummary,
			stdDevHistogram, stdDevSummary,
			totalParticipants
		);
	}

	private List<HistogramBucket> buildHistogramBuckets(List<Object[]> raw, long totalParticipants, int bucketSizeMs, int maxMs) {
		// 전체 범위의 버킷 맵 생성 (0 ~ maxMs)
		int bucketCount = maxMs / bucketSizeMs;
		long[] counts = new long[bucketCount];
		long totalCount = 0;

		// DB에서 온 데이터로 채움
		for (Object[] row : raw) {
			int bucketStart = ((Number) row[0]).intValue();
			long count = ((Number) row[2]).longValue();
			int bucketIndex = bucketStart / bucketSizeMs;
			if (bucketIndex >= 0 && bucketIndex < bucketCount) {
				counts[bucketIndex] = count;
				totalCount += count;
			}
		}

		// 모든 버킷을 리스트로 변환 (빈 버킷 포함)
		List<HistogramBucket> buckets = new ArrayList<>();
		for (int i = 0; i < bucketCount; i++) {
			int bucketStart = i * bucketSizeMs;
			int bucketEnd = (i + 1) * bucketSizeMs;
			long count = counts[i];
			double percentage = totalCount > 0
				? Math.round(count * 10000.0 / totalCount) / 100.0  // 소수점 2자리
				: 0.0;
			buckets.add(new HistogramBucket(bucketStart, bucketEnd, count, percentage));
		}
		return buckets;
	}

	private SummaryData extractSummary(List<Object[]> statsRaw, String metric) {
		if (statsRaw.isEmpty()) {
			return SummaryData.builder().averageMs(0.0).p50Ms(0.0).p90Ms(0.0).build();
		}
		Object[] row = statsRaw.get(0);
		int offset = switch (metric) {
			case "median" -> 0;
			case "max" -> 3;
			case "stddev" -> 6;
			default -> 0;
		};
		return SummaryData.builder()
			.averageMs(toDouble(row[offset]))
			.p50Ms(toDouble(row[offset + 1]))
			.p90Ms(toDouble(row[offset + 2]))
			.build();
	}

	private Double toDouble(Object obj) {
		return obj != null ? ((Number) obj).doubleValue() : 0.0;
	}

	private DistributionData buildDistributionData(
		List<HistogramBucket> histogram,
		SummaryData summary,
		long totalParticipants,
		Double myValue,
		Long myRank
	) {
		Double myPercentile = null;
		if (myRank != null && totalParticipants > 0) {
			myPercentile = Math.round((myRank - 1) * 1000.0 / totalParticipants) / 10.0;
		}

		return DistributionData.builder()
			.histogram(histogram)
			.summary(summary)
			.myValue(myValue)
			.myRank(myRank)
			.totalParticipants(totalParticipants)
			.myPercentile(myPercentile)
			.build();
	}

	private Double getMyMedianValue(Long memberId) {
		return latencyRepository.findTopByMemberIdOrderByCreatedAtDesc(memberId)
			.map(LatencyEntity::getMedianMs)
			.orElse(null);
	}

	private Double getMyMaxValue(Long memberId) {
		return latencyRepository.findTopByMemberIdOrderByCreatedAtDesc(memberId)
			.map(LatencyEntity::getMaxMs)
			.orElse(null);
	}

	private Double getMyStdDevValue(Long memberId) {
		return latencyRepository.findTopByMemberIdOrderByCreatedAtDesc(memberId)
			.map(LatencyEntity::getStdDevMs)
			.orElse(null);
	}

	public record CachedDistributionData(
		List<HistogramBucket> medianHistogram,
		SummaryData medianSummary,
		List<HistogramBucket> maxHistogram,
		SummaryData maxSummary,
		List<HistogramBucket> stdDevHistogram,
		SummaryData stdDevSummary,
		long totalParticipants
	) {}
}
