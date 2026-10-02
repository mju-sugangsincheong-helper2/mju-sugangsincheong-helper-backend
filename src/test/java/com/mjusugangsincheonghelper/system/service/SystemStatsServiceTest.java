package com.mjusugangsincheonghelper.system.service;

import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.entity.MultigameRoundEntity;
import com.mjusugangsincheonghelper.database.repository.SystemRepository;
import com.mjusugangsincheonghelper.global.config.PgmqProperties;
import com.mjusugangsincheonghelper.global.config.PgmqService;
import com.mjusugangsincheonghelper.system.dto.SystemStatsResponse;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import org.mockito.ArgumentMatchers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("SystemStatsService 단위 테스트")
class SystemStatsServiceTest {

	@Mock
	private SystemRepository systemRepository;

	@Mock
	private SystemConfigService systemConfigService;

	@Mock
	private PgmqService pgmqService;

	@Spy
	private PgmqProperties pgmqProperties = new PgmqProperties();

	@InjectMocks
	private SystemStatsService systemStatsService;

	@Test
	@DisplayName("회원 역할별 수를 집계하고 도메인 지표를 모두 계산한다")
	void aggregatesAllDomainStats() {
		given(systemConfigService.getCurrentTerm()).willReturn("202620");
		given(systemRepository.countMembersByRole()).willReturn(List.of(
				new Object[] {Member.Role.GUEST, 60L},
				new Object[] {Member.Role.MEMBER, 35L},
				new Object[] {Member.Role.ADMIN, 5L}
		));
		given(systemRepository.countMembersCreatedSince(any(Instant.class))).willReturn(3L);
		given(systemRepository.countDevices()).willReturn(150L);
		given(systemRepository.countActiveDevicesSince(any(Instant.class))).willReturn(80L);
		given(systemRepository.countDevicesByOs()).willReturn(List.of(
				new Object[] {"iOS", 60L},
				new Object[] {"Android", 40L}
		));
		given(systemRepository.countDevicesByBrowser()).willReturn(List.of(
				new Object[] {"Chrome", 90L},
				new Object[] {"Safari", 10L}
		));
		given(systemRepository.countNotices()).willReturn(4L);
		given(systemRepository.countCourses()).willReturn(1200L);
		given(systemRepository.countDistinctCourseTerms()).willReturn(2L);
		given(systemRepository.countCoursesByTerm()).willReturn(List.of(
				new Object[] {"20262", 700L},
				new Object[] {"20261", 500L}
		));
		given(systemRepository.countExchangeIntents("202620")).willReturn(30L);
		given(systemRepository.countExchangeRoomsByStatus("202620", "ACTIVE")).willReturn(5L);
		given(systemRepository.countExchangeRoomsGroupByStatus("202620")).willReturn(List.of(
				new Object[] {"ACTIVE", 5L},
				new Object[] {"PARTIAL_OFF", 2L}
		));
		given(systemRepository.countMatchedIntents("202620")).willReturn(12L);
		given(systemRepository.countExchangeMessages("202620")).willReturn(120L);
		given(systemRepository.countSingleGames()).willReturn(220L);
		given(systemRepository.countCompletedSingleGames()).willReturn(200L);
		given(systemRepository.countCompletedSingleGamesSince(any(Instant.class))).willReturn(5L);
		given(systemRepository.avgCompletedSingleGameTTotal()).willReturn(41234.0);
		given(systemRepository.minCompletedSingleGameTTotal()).willReturn(30000);
		given(systemRepository.aggregateSingleGamesByTotalCourses()).willReturn(List.of(
				new Object[] {1, 220L, 200L, 41234.0, 30000},
				new Object[] {8, 220L, 200L, 41234.0, 30000}
		));
		given(systemRepository.countNonEmptyRounds()).willReturn(8L);
		given(systemRepository.findMaxRoundParticipants()).willReturn(Optional.of(120));
		given(systemRepository.findRecentRounds(10)).willReturn(List.of(
				MultigameRoundEntity.builder().startTime("202604020010").participantCount(120).capacity(60).build()
		));
		given(systemRepository.countRoundsByHour()).willReturn(List.of(
				new Object[] {10, 14L},
				new Object[] {20, 5L}
		));
		given(systemRepository.countRoundsByDayOfWeek()).willReturn(List.of(
				new Object[] {3, 8L},
				new Object[] {5, 6L}
		));
		given(systemRepository.countRoundsByDaySince(any(String.class))).willReturn(List.of(
				new Object[] {"2026-03-25", 4L},
				new Object[] {"2026-04-02", 2L}
		));
		List<Object[]> byStartTimeRows = new java.util.ArrayList<>();
		byStartTimeRows.add(new Object[] {"202604020010", 90L, 30L});
		given(systemRepository.aggregateMultigameByStartTimes(ArgumentMatchers.<Collection<String>>any())).willReturn(byStartTimeRows);
		List<Object[]> overallRows = new java.util.ArrayList<>();
		overallRows.add(new Object[] {900L, 300L});
		given(systemRepository.aggregateMultigameOverall()).willReturn(overallRows);
		given(pgmqService.queueLength("notification_queue")).willReturn(17L);

		SystemStatsResponse stats = systemStatsService.getStats();

		assertThat(stats.members().total()).isEqualTo(100);
		assertThat(stats.members().guest()).isEqualTo(60);
		assertThat(stats.members().regular()).isEqualTo(35);
		assertThat(stats.members().admin()).isEqualTo(5);
		assertThat(stats.newMembersToday()).isEqualTo(3);
		assertThat(stats.newMembersThisWeek()).isEqualTo(3);
		assertThat(stats.devices()).isEqualTo(150);
		assertThat(stats.activeDevicesLast7Days()).isEqualTo(80);
		assertThat(stats.notices()).isEqualTo(4);
		assertThat(stats.courseSections()).isEqualTo(1200);
		assertThat(stats.terms()).isEqualTo(2);
		assertThat(stats.coursesByTerm()).hasSize(2);
		assertThat(stats.coursesByTerm().get(0).term()).isEqualTo("20262");

		assertThat(stats.devicesByOs()).hasSize(2);
		assertThat(stats.devicesByOs().get(0).label()).isEqualTo("iOS");
		assertThat(stats.devicesByOs().get(0).count()).isEqualTo(60);
		assertThat(stats.devicesByBrowser().get(0).label()).isEqualTo("Chrome");
		assertThat(stats.devicesByBrowser().get(0).count()).isEqualTo(90);

		assertThat(stats.exchange().intents()).isEqualTo(30);
		assertThat(stats.exchange().activeRooms()).isEqualTo(5);
		assertThat(stats.exchange().messages()).isEqualTo(120);
		assertThat(stats.exchange().matchedIntents()).isEqualTo(12);
		assertThat(stats.exchange().matchedRate()).isEqualTo(40);
		assertThat(stats.exchange().roomsByStatus()).hasSize(2);
		assertThat(stats.exchange().roomsByStatus().get(0).status()).isEqualTo("ACTIVE");
		assertThat(stats.exchange().roomsByStatus().get(0).count()).isEqualTo(5);

		assertThat(stats.singleGame().total()).isEqualTo(220);
		assertThat(stats.singleGame().completed()).isEqualTo(200);
		assertThat(stats.singleGame().completedToday()).isEqualTo(5);
		assertThat(stats.singleGame().completedThisWeek()).isEqualTo(5);
		assertThat(stats.singleGame().completionRate()).isEqualTo(91);
		assertThat(stats.singleGame().avgTotalMs()).isEqualTo(41234);
		assertThat(stats.singleGame().bestTotalMs()).isEqualTo(30000);
		assertThat(stats.singleGame().byCourse()).hasSize(2);
		assertThat(stats.singleGame().byCourse().get(0).totalCourses()).isEqualTo(1);
		assertThat(stats.singleGame().byCourse().get(0).completed()).isEqualTo(200);
		assertThat(stats.singleGame().byCourse().get(0).completionRate()).isEqualTo(91);
		assertThat(stats.singleGame().byCourse().get(0).avgTotalMs()).isEqualTo(41234);
		assertThat(stats.singleGame().byCourse().get(0).bestTotalMs()).isEqualTo(30000);

		assertThat(stats.multigame().rounds()).isEqualTo(8);
		assertThat(stats.multigame().peakParticipants()).isEqualTo(120);
		assertThat(stats.multigame().successCount()).isEqualTo(900);
		assertThat(stats.multigame().failedCount()).isEqualTo(300);
		assertThat(stats.multigame().successRate()).isEqualTo(75);
		assertThat(stats.multigame().recentRounds()).hasSize(1);
		assertThat(stats.multigame().recentRounds().get(0).successCount()).isEqualTo(90);
		assertThat(stats.multigame().recentRounds().get(0).failedCount()).isEqualTo(30);
		assertThat(stats.multigame().roundsByHour()).hasSize(2);
		assertThat(stats.multigame().roundsByHour().get(0).hour()).isEqualTo(10);
		assertThat(stats.multigame().roundsByHour().get(0).count()).isEqualTo(14);
		assertThat(stats.multigame().roundsByDayOfWeek().get(0).dayOfWeek()).isEqualTo(3);
		assertThat(stats.multigame().roundsByDayOfWeek().get(0).count()).isEqualTo(8);
		assertThat(stats.multigame().roundsByDay().get(0).day()).isEqualTo("2026-03-25");
		assertThat(stats.multigame().roundsByDay().get(0).count()).isEqualTo(4);
		assertThat(stats.notificationQueueLength()).isEqualTo(17);
	}

	@Test
	@DisplayName("회원이 없으면 모든 수가 0으로 집계된다")
	void returnsZeroWhenNoMembers() {
		given(systemConfigService.getCurrentTerm()).willReturn("202620");
		given(systemRepository.countMembersByRole()).willReturn(List.of());
		given(systemRepository.countMembersCreatedSince(any(Instant.class))).willReturn(0L);
		given(systemRepository.countDevices()).willReturn(0L);
		given(systemRepository.countActiveDevicesSince(any(Instant.class))).willReturn(0L);
		given(systemRepository.countDevicesByOs()).willReturn(List.of());
		given(systemRepository.countDevicesByBrowser()).willReturn(List.of());
		given(systemRepository.countNotices()).willReturn(0L);
		given(systemRepository.countCourses()).willReturn(0L);
		given(systemRepository.countDistinctCourseTerms()).willReturn(0L);
		given(systemRepository.countCoursesByTerm()).willReturn(List.of());
		given(systemRepository.countExchangeIntents("202620")).willReturn(0L);
		given(systemRepository.countExchangeRoomsByStatus("202620", "ACTIVE")).willReturn(0L);
		given(systemRepository.countExchangeRoomsGroupByStatus("202620")).willReturn(List.of());
		given(systemRepository.countMatchedIntents("202620")).willReturn(0L);
		given(systemRepository.countExchangeMessages("202620")).willReturn(0L);
		given(systemRepository.countSingleGames()).willReturn(0L);
		given(systemRepository.countCompletedSingleGames()).willReturn(0L);
		given(systemRepository.countCompletedSingleGamesSince(any(Instant.class))).willReturn(0L);
		given(systemRepository.avgCompletedSingleGameTTotal()).willReturn(null);
		given(systemRepository.minCompletedSingleGameTTotal()).willReturn(null);
		given(systemRepository.aggregateSingleGamesByTotalCourses()).willReturn(List.of());
		given(systemRepository.countNonEmptyRounds()).willReturn(0L);
		given(systemRepository.findMaxRoundParticipants()).willReturn(Optional.empty());
		given(systemRepository.findRecentRounds(10)).willReturn(List.of());
		given(systemRepository.countRoundsByHour()).willReturn(List.of());
		given(systemRepository.countRoundsByDayOfWeek()).willReturn(List.of());
		given(systemRepository.countRoundsByDaySince(any(String.class))).willReturn(List.of());
		given(systemRepository.aggregateMultigameOverall()).willReturn(List.of());
		given(pgmqService.queueLength("notification_queue")).willReturn(0L);

		SystemStatsResponse stats = systemStatsService.getStats();

		assertThat(stats.members().total()).isZero();
		assertThat(stats.activeDevicesLast7Days()).isZero();
		assertThat(stats.devicesByOs()).isEmpty();
		assertThat(stats.exchange().intents()).isZero();
		assertThat(stats.exchange().matchedRate()).isZero();
		assertThat(stats.singleGame().completionRate()).isZero();
		assertThat(stats.multigame().successRate()).isZero();
		assertThat(stats.notificationQueueLength()).isZero();
	}
}
