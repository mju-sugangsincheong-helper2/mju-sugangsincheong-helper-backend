package com.mjusugangsincheonghelper.system.dto;

import java.util.List;

/**
 * 관리자 모니터링(도메인 지표) 응답.
 * 인프라 지표는 Actuator/Prometheus로 분리하고, 이 API는 서비스의 도메인 건강 상태만 다룬다.
 */
public record SystemStatsResponse(
		MemberStats members,
		long newMembersToday,
		long newMembersThisWeek,
		long devices,
		long activeDevicesLast7Days,
		long notices,
		long courseSections,
		long terms,
		List<CourseTermCount> coursesByTerm,
		List<DeviceDistribution> devicesByOs,
		List<DeviceDistribution> devicesByBrowser,
		ExchangeStats exchange,
		SingleGameStats singleGame,
		MultigameStats multigame,
		long notificationQueueLength
) {

	/**
	 * 회원 구성 비율. 정회원 = MEMBER 역할(regular).
	 */
	public record MemberStats(long total, long guest, long regular, long admin) {
	}

	public record CourseTermCount(String term, long count) {
	}

	public record DeviceDistribution(String label, long count) {
	}

	/** 강의 교환(Exchange) 도메인 활성 지표 (현재 학기 기준) */
	public record ExchangeStats(
			long intents,
			long activeRooms,
			long messages,
			long matchedIntents,
			int matchedRate,
			List<RoomStatusCount> roomsByStatus
	) {
	}

	/** 싱글게임 도메인 지표 */
	public record SingleGameStats(
			long total,
			long completed,
			long completedToday,
			long completedThisWeek,
			int completionRate,
			long avgTotalMs,
			long bestTotalMs,
			List<CourseStats> byCourse
	) {
	}

	/** 멀티게임 도메인 지표 */
	public record MultigameStats(
			long rounds,
			long peakParticipants,
			long successCount,
			long failedCount,
			int successRate,
			List<RoundStats> recentRounds,
			List<HourCount> roundsByHour,
			List<DayOfWeekCount> roundsByDayOfWeek,
			List<DailyCount> roundsByDay
	) {
	}

	/** 채팅방 상태(EXCHANGE_ROOM.status) 분포 */
	public record RoomStatusCount(String status, long count) {
	}

	/** 싱글게임 종목(과목 수)별 지표: 기록 규모/완주율/평균·최단 소요시간 */
	public record CourseStats(
			int totalCourses,
			long total,
			long completed,
			int completionRate,
			long avgTotalMs,
			long bestTotalMs
	) {
	}

	/** 멀티게임 진행 시각 분포: 시간대(0~23시)별 실제 진행 라운드 수 */
	public record HourCount(int hour, long count) {
	}

	/** 멀티게임 진행 요일 분포: ISO 요일(1=월 ~ 7=일)별 실제 진행 라운드 수 */
	public record DayOfWeekCount(int dayOfWeek, long count) {
	}

	/** 멀티게임 일자(yyyy-MM-dd)별 실제 진행 라운드 수 */
	public record DailyCount(String day, long count) {
	}

	/** 멀티게임 최근 라운드별 집계 */
	public record RoundStats(
			String startTime,
			long participantCount,
			long capacity,
			long successCount,
			long failedCount
	) {
	}
}