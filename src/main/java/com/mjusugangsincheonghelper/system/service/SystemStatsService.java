package com.mjusugangsincheonghelper.system.service;

import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.repository.SystemRepository;
import com.mjusugangsincheonghelper.global.config.PgmqProperties;
import com.mjusugangsincheonghelper.global.config.PgmqService;
import com.mjusugangsincheonghelper.database.entity.MultigameRoundEntity;
import com.mjusugangsincheonghelper.system.dto.SystemStatsResponse;
import com.mjusugangsincheonghelper.system.dto.SystemStatsResponse.CourseStats;
import com.mjusugangsincheonghelper.system.dto.SystemStatsResponse.CourseTermCount;
import com.mjusugangsincheonghelper.system.dto.SystemStatsResponse.DailyCount;
import com.mjusugangsincheonghelper.system.dto.SystemStatsResponse.DayOfWeekCount;
import com.mjusugangsincheonghelper.system.dto.SystemStatsResponse.DeviceDistribution;
import com.mjusugangsincheonghelper.system.dto.SystemStatsResponse.ExchangeStats;
import com.mjusugangsincheonghelper.system.dto.SystemStatsResponse.HourCount;
import com.mjusugangsincheonghelper.system.dto.SystemStatsResponse.MemberStats;
import com.mjusugangsincheonghelper.system.dto.SystemStatsResponse.MultigameStats;
import com.mjusugangsincheonghelper.system.dto.SystemStatsResponse.RoomStatusCount;
import com.mjusugangsincheonghelper.system.dto.SystemStatsResponse.RoundStats;
import com.mjusugangsincheonghelper.system.dto.SystemStatsResponse.SingleGameStats;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 모니터링용 도메인 지표 조회.
 * 인프라 지표(메모리/CPU 등)는 별도로 Actuator + Prometheus(VictoriaMetrics)에서 담당하므로
 * 여기서는 서비스의 실제 사용자/데이터 규모만 집계한다.
 * DB 접근은 {@link SystemRepository} 단일 파일에만 둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemStatsService {

	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
	private static final String EXCHANGE_ROOM_ACTIVE = "ACTIVE";

	private final SystemRepository systemRepository;
	private final SystemConfigService systemConfigService;
	private final PgmqService pgmqService;
	private final PgmqProperties pgmqProperties;

	@Transactional(readOnly = true)
	public SystemStatsResponse getStats() {
		Instant now = Instant.now();
		Instant startOfToday = LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant();
		Instant startOfThisWeek = LocalDate.now(ZONE).minusDays(6).atStartOfDay(ZONE).toInstant();
		String currentTerm = systemConfigService.getCurrentTerm();

		Map<Member.Role, Long> byRole = new EnumMap<>(Member.Role.class);
		for (Object[] row : systemRepository.countMembersByRole()) {
			byRole.put((Member.Role) row[0], (Long) row[1]);
		}

		long total = byRole.values().stream().mapToLong(Long::longValue).sum();
		MemberStats memberStats = new MemberStats(
				total,
				byRole.getOrDefault(Member.Role.GUEST, 0L),
				byRole.getOrDefault(Member.Role.MEMBER, 0L),
				byRole.getOrDefault(Member.Role.ADMIN, 0L)
		);

		List<CourseTermCount> coursesByTerm = systemRepository.countCoursesByTerm().stream()
				.map(row -> new CourseTermCount((String) row[0], ((Number) row[1]).longValue()))
				.toList();

		List<DeviceDistribution> devicesByOs = systemRepository.countDevicesByOs().stream()
				.map(row -> new DeviceDistribution((String) row[0], ((Number) row[1]).longValue()))
				.toList();
		List<DeviceDistribution> devicesByBrowser = systemRepository.countDevicesByBrowser().stream()
				.map(row -> new DeviceDistribution((String) row[0], ((Number) row[1]).longValue()))
				.toList();

		return new SystemStatsResponse(
				memberStats,
				systemRepository.countMembersCreatedSince(startOfToday),
				systemRepository.countMembersCreatedSince(startOfThisWeek),
				systemRepository.countDevices(),
				systemRepository.countActiveDevicesSince(startOfThisWeek),
				systemRepository.countNotices(),
				systemRepository.countCourses(),
				systemRepository.countDistinctCourseTerms(),
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
		long intents = systemRepository.countExchangeIntents(currentTerm);
		long activeRooms = systemRepository.countExchangeRoomsByStatus(currentTerm, EXCHANGE_ROOM_ACTIVE);
		long messages = systemRepository.countExchangeMessages(currentTerm);
		long matchedIntents = systemRepository.countMatchedIntents(currentTerm);
		int matchedRate = intents > 0 ? (int) Math.round(matchedIntents * 100.0 / intents) : 0;

		List<RoomStatusCount> roomsByStatus = systemRepository.countExchangeRoomsGroupByStatus(currentTerm).stream()
				.map(row -> new RoomStatusCount((String) row[0], ((Number) row[1]).longValue()))
				.toList();

		return new ExchangeStats(intents, activeRooms, messages, matchedIntents, matchedRate, roomsByStatus);
	}

	/** 싱글게임 지표: 기록 규모/완주율/속도/종목별 분포 */
	private SingleGameStats buildSingleGameStats(Instant startOfToday, Instant startOfThisWeek) {
		long total = systemRepository.countSingleGames();
		long completed = systemRepository.countCompletedSingleGames();
		long completedToday = systemRepository.countCompletedSingleGamesSince(startOfToday);
		long completedThisWeek = systemRepository.countCompletedSingleGamesSince(startOfThisWeek);
		int completionRate = total > 0 ? (int) Math.round(completed * 100.0 / total) : 0;

		Double avgMs = systemRepository.avgCompletedSingleGameTTotal();
		Integer bestMs = systemRepository.minCompletedSingleGameTTotal();

		List<CourseStats> byCourse = systemRepository.aggregateSingleGamesByTotalCourses().stream()
				.map(row -> {
					int totalCourses = ((Number) row[0]).intValue();
					long courseTotal = ((Number) row[1]).longValue();
					long courseCompleted = nvl(row[2]);
					int courseRate = courseTotal > 0 ? (int) Math.round(courseCompleted * 100.0 / courseTotal) : 0;
					return new CourseStats(
							totalCourses,
							courseTotal,
							courseCompleted,
							courseRate,
							row[3] == null ? 0L : Math.round(((Number) row[3]).doubleValue()),
							row[4] == null ? 0L : ((Number) row[4]).longValue()
					);
				})
				.toList();

		return new SingleGameStats(
				total,
				completed,
				completedToday,
				completedThisWeek,
				completionRate,
				avgMs == null ? 0L : Math.round(avgMs),
				bestMs == null ? 0L : bestMs.longValue(),
				byCourse
		);
	}

	/** 멀티게임 지표: 라운드 규모/피크 참여자/성공률/최근 라운드별 집계 */
	private MultigameStats buildMultigameStats() {
		long rounds = systemRepository.countNonEmptyRounds();
		long peakParticipants = systemRepository.findMaxRoundParticipants().orElse(0);

		// 최근 10개 라운드 (참여 인원순 제한 없이 최신순)
		List<MultigameRoundEntity> recent = systemRepository
				.findRecentRounds(10)
				.stream()
				.filter(round -> round.getParticipantCount() > 0)
				.toList();

		Set<String> recentStartTimes = recent.stream()
				.map(MultigameRoundEntity::getStartTime)
				.collect(Collectors.toSet());

		Map<String, long[]> resultByStartTime = new HashMap<>();
		if (!recentStartTimes.isEmpty()) {
			for (Object[] row : systemRepository.aggregateMultigameByStartTimes(recentStartTimes)) {
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
		List<Object[]> overall = systemRepository.aggregateMultigameOverall();
		if (!overall.isEmpty() && overall.get(0) != null) {
			successCount = nvl(overall.get(0)[0]);
			failedCount = nvl(overall.get(0)[1]);
		}
		int successRate = (successCount + failedCount) > 0
				? (int) Math.round(successCount * 100.0 / (successCount + failedCount))
				: 0;

		List<HourCount> roundsByHour = systemRepository.countRoundsByHour().stream()
				.map(row -> new HourCount(((Number) row[0]).intValue(), ((Number) row[1]).longValue()))
				.toList();
		List<DayOfWeekCount> roundsByDayOfWeek = systemRepository.countRoundsByDayOfWeek().stream()
				.map(row -> new DayOfWeekCount(((Number) row[0]).intValue(), ((Number) row[1]).longValue()))
				.toList();
		String from = LocalDate.now(ZONE).minusDays(13).format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "000000";
		List<DailyCount> roundsByDay = systemRepository.countRoundsByDaySince(from).stream()
				.map(row -> new DailyCount((String) row[0], ((Number) row[1]).longValue()))
				.toList();

		return new MultigameStats(rounds, peakParticipants, successCount, failedCount, successRate, recentRounds,
				roundsByHour, roundsByDayOfWeek, roundsByDay);
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
			return pgmqService.queueLength(pgmqProperties.getNotification().getQueueName());
		} catch (Exception e) {
			log.warn("PGMQ queue length 조회 실패 (큐 미생성 등): {}", e.getMessage());
			return 0L;
		}
	}
}
