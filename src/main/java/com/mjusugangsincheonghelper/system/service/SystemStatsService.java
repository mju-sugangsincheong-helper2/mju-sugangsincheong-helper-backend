package com.mjusugangsincheonghelper.system.service;

import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.repository.CourseRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeIntentRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomMessageRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomRepository;
import com.mjusugangsincheonghelper.database.repository.MemberDeviceRepository;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import com.mjusugangsincheonghelper.database.repository.MultigameRoundRepository;
import com.mjusugangsincheonghelper.database.repository.NoticeRepository;
import com.mjusugangsincheonghelper.database.repository.SingleGameRepository;
import com.mjusugangsincheonghelper.global.config.PgmqService;
import com.mjusugangsincheonghelper.notification.consumer.NotificationConsumerWorker;import com.mjusugangsincheonghelper.database.entity.MultigameRoundEntity;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomIntentRepository;
import com.mjusugangsincheonghelper.database.repository.MultigameRoundMemberRepository;
import com.mjusugangsincheonghelper.system.dto.SystemStatsResponse;
import com.mjusugangsincheonghelper.system.dto.SystemStatsResponse.CourseCount;
import com.mjusugangsincheonghelper.system.dto.SystemStatsResponse.CourseTermCount;
import com.mjusugangsincheonghelper.system.dto.SystemStatsResponse.DeviceDistribution;
import com.mjusugangsincheonghelper.system.dto.SystemStatsResponse.ExchangeStats;
import com.mjusugangsincheonghelper.system.dto.SystemStatsResponse.MemberStats;
import com.mjusugangsincheonghelper.system.dto.SystemStatsResponse.MultigameStats;
import com.mjusugangsincheonghelper.system.dto.SystemStatsResponse.RoomStatusCount;
import com.mjusugangsincheonghelper.system.dto.SystemStatsResponse.RoundStats;
import com.mjusugangsincheonghelper.system.dto.SystemStatsResponse.SingleGameStats;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 모니터링용 도메인 지표 조회.
 * 인프라 지표(메모리/CPU 등)는 별도로 Actuator + Prometheus(VictoriaMetrics)에서 담당하므로
 * 여기서는 서비스의 실제 사용자/데이터 규모만 집계한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemStatsService {

	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
	private static final String EXCHANGE_ROOM_ACTIVE = "ACTIVE";

	private final MemberRepository memberRepository;
	private final MemberDeviceRepository memberDeviceRepository;
	private final NoticeRepository noticeRepository;
	private final CourseRepository courseRepository;
	private final ExchangeIntentRepository exchangeIntentRepository;
	private final ExchangeRoomRepository exchangeRoomRepository;
	private final ExchangeRoomIntentRepository exchangeRoomIntentRepository;
	private final ExchangeRoomMessageRepository exchangeRoomMessageRepository;
	private final SingleGameRepository singleGameRepository;
	private final MultigameRoundRepository multigameRoundRepository;
	private final MultigameRoundMemberRepository multigameRoundMemberRepository;
	private final SystemConfigService systemConfigService;
	private final PgmqService pgmqService;

	@Transactional(readOnly = true)
	public SystemStatsResponse getStats() {
		Instant now = Instant.now();
		Instant startOfToday = LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant();
		Instant startOfThisWeek = LocalDate.now(ZONE).minusDays(6).atStartOfDay(ZONE).toInstant();
		String currentTerm = systemConfigService.getCurrentTerm();

		Map<Member.Role, Long> byRole = new EnumMap<>(Member.Role.class);
		for (Object[] row : memberRepository.countByRole()) {
			byRole.put((Member.Role) row[0], (Long) row[1]);
		}

		long total = byRole.values().stream().mapToLong(Long::longValue).sum();
		MemberStats memberStats = new MemberStats(
				total,
				byRole.getOrDefault(Member.Role.GUEST, 0L),
				byRole.getOrDefault(Member.Role.MEMBER, 0L),
				byRole.getOrDefault(Member.Role.ADMIN, 0L)
		);

		List<CourseTermCount> coursesByTerm = courseRepository.countByTerm().stream()
				.map(row -> new CourseTermCount((String) row[0], ((Number) row[1]).longValue()))
				.toList();

		List<DeviceDistribution> devicesByOs = memberDeviceRepository.countByPlatformjsOs().stream()
				.map(row -> new DeviceDistribution((String) row[0], ((Number) row[1]).longValue()))
				.toList();
		List<DeviceDistribution> devicesByBrowser = memberDeviceRepository.countByPlatformjsName().stream()
				.map(row -> new DeviceDistribution((String) row[0], ((Number) row[1]).longValue()))
				.toList();

		return new SystemStatsResponse(
				memberStats,
				memberRepository.countByCreatedAtGreaterThanEqual(startOfToday),
				memberRepository.countByCreatedAtGreaterThanEqual(startOfThisWeek),
				memberDeviceRepository.count(),
				memberDeviceRepository.countByLastAccessedAtGreaterThanEqual(startOfThisWeek),
				noticeRepository.count(),
				courseRepository.count(),
				courseRepository.countDistinctTerms(),
				coursesByTerm,
				devicesByOs,
				devicesByBrowser,
				buildExchangeStats(currentTerm),
				buildSingleGameStats(startOfToday, startOfThisWeek),
				buildMultigameStats(),
				notificationQueueLength()
		);
	}

	/** 교환(Exchange) 지표: 활성 의도/방, 매칭률, 방 상태 분포 (현재 학기) */
	private ExchangeStats buildExchangeStats(String currentTerm) {
		long intents = exchangeIntentRepository.countByTermAndIsDeletedFalse(currentTerm);
		long activeRooms = exchangeRoomRepository.countByTermAndStatus(currentTerm, EXCHANGE_ROOM_ACTIVE);
		long messages = exchangeRoomMessageRepository.countByTerm(currentTerm);
		long matchedIntents = exchangeRoomIntentRepository.countDistinctIntentIdByTermAndIsDeletedFalse(currentTerm);
		int matchedRate = intents > 0 ? (int) Math.round(matchedIntents * 100.0 / intents) : 0;

		List<RoomStatusCount> roomsByStatus = exchangeRoomRepository.countByTermGroupByStatus(currentTerm).stream()
				.map(row -> new RoomStatusCount((String) row[0], ((Number) row[1]).longValue()))
				.toList();

		return new ExchangeStats(intents, activeRooms, messages, matchedIntents, matchedRate, roomsByStatus);
	}

	/** 싱글게임 지표: 기록 규모/완주율/속도/종목별 분포 */
	private SingleGameStats buildSingleGameStats(Instant startOfToday, Instant startOfThisWeek) {
		long total = singleGameRepository.count();
		long completed = singleGameRepository.countByIsCompletedTrue();
		long completedToday = singleGameRepository.countByIsCompletedTrueAndCreatedAtGreaterThanEqual(startOfToday);
		long completedThisWeek = singleGameRepository.countByIsCompletedTrueAndCreatedAtGreaterThanEqual(startOfThisWeek);
		int completionRate = total > 0 ? (int) Math.round(completed * 100.0 / total) : 0;

		Double avgMs = singleGameRepository.averageTTotalByIsCompletedTrue();
		Integer bestMs = singleGameRepository.minTTotalByIsCompletedTrue();

		List<CourseCount> byCourseCount = singleGameRepository.countByIsCompletedTrueGroupByTotalCourses().stream()
				.map(row -> new CourseCount(((Number) row[0]).intValue(), ((Number) row[1]).longValue()))
				.toList();

		return new SingleGameStats(
				total,
				completed,
				completedToday,
				completedThisWeek,
				completionRate,
				avgMs == null ? 0L : Math.round(avgMs),
				bestMs == null ? 0L : bestMs.longValue(),
				byCourseCount
		);
	}

	/** 멀티게임 지표: 라운드 규모/피크 참여자/성공률/최근 라운드별 집계 */
	private MultigameStats buildMultigameStats() {
		long rounds = multigameRoundRepository.countByParticipantCountGreaterThan(0);
		long peakParticipants = multigameRoundRepository.findMaxParticipantCount().orElse(0);

		// 최근 10개 라운드 (참여 인원순 제한 없이 최신순)
		List<MultigameRoundEntity> recent = multigameRoundRepository
				.findAllByOrderByStartTimeDesc(PageRequest.of(0, 10))
				.getContent()
				.stream()
				.filter(round -> round.getParticipantCount() > 0)
				.toList();

		Set<String> recentStartTimes = recent.stream()
				.map(MultigameRoundEntity::getStartTime)
				.collect(Collectors.toSet());

		Map<String, long[]> resultByStartTime = new HashMap<>();
		if (!recentStartTimes.isEmpty()) {
			for (Object[] row : multigameRoundMemberRepository.aggregateResultByStartTimes(recentStartTimes)) {
				resultByStartTime.put((String) row[0], new long[] {
						nvl(row[1]), nvl(row[2])
				});
			}
		}

		List<RoundStats> recentRounds = recent.stream()
				.map(round -> {
					long[] sr = resultByStartTime.getOrDefault(round.getStartTime(), new long[] {0, 0});
					return new RoundStats(
							round.getStartTime(),
							round.getParticipantCount(),
							round.getCapacity(),
							sr[0],
							sr[1]
					);
				})
				.toList();

		long successCount = 0;
		long failedCount = 0;
		List<Object[]> overall = multigameRoundMemberRepository.aggregateOverallResult();
		if (!overall.isEmpty() && overall.get(0) != null) {
			successCount = nvl(overall.get(0)[0]);
			failedCount = nvl(overall.get(0)[1]);
		}
		int successRate = (successCount + failedCount) > 0
				? (int) Math.round(successCount * 100.0 / (successCount + failedCount))
				: 0;

		return new MultigameStats(rounds, peakParticipants, successCount, failedCount, successRate, recentRounds);
	}

	private long nvl(Object value) {
		return value == null ? 0L : ((Number) value).longValue();
	}

	/**
	 * PGMQ notification_queue 의 현재 대기(백로그) 건수.
	 * 큐가 아직 생성되지 않은 환경(테스트 등)에서는 0을 반환한다.
	 */
	private long notificationQueueLength() {
		try {
			return pgmqService.queueLength(NotificationConsumerWorker.QUEUE_NAME);
		} catch (Exception e) {
			log.warn("PGMQ queue length 조회 실패 (큐 미생성 등): {}", e.getMessage());
			return 0L;
		}
	}
}