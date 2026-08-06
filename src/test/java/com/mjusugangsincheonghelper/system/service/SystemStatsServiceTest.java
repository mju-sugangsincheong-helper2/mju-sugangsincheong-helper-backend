package com.mjusugangsincheonghelper.system.service;

import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.repository.CourseRepository;
import com.mjusugangsincheonghelper.database.entity.MultigameRoundEntity;
import com.mjusugangsincheonghelper.database.repository.ExchangeIntentRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomIntentRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomMessageRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomRepository;
import com.mjusugangsincheonghelper.database.repository.MemberDeviceRepository;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import com.mjusugangsincheonghelper.database.repository.MultigameRoundMemberRepository;
import com.mjusugangsincheonghelper.database.repository.MultigameRoundRepository;
import com.mjusugangsincheonghelper.database.repository.NoticeRepository;
import com.mjusugangsincheonghelper.database.repository.SingleGameRepository;
import com.mjusugangsincheonghelper.global.config.PgmqProperties;
import com.mjusugangsincheonghelper.global.config.PgmqService;
import com.mjusugangsincheonghelper.system.dto.SystemStatsResponse;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("SystemStatsService 단위 테스트")
class SystemStatsServiceTest {

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private MemberDeviceRepository memberDeviceRepository;

	@Mock
	private NoticeRepository noticeRepository;

	@Mock
	private CourseRepository courseRepository;

	@Mock
	private ExchangeIntentRepository exchangeIntentRepository;

	@Mock
	private ExchangeRoomRepository exchangeRoomRepository;

	@Mock
	private ExchangeRoomIntentRepository exchangeRoomIntentRepository;

	@Mock
	private ExchangeRoomMessageRepository exchangeRoomMessageRepository;

	@Mock
	private SingleGameRepository singleGameRepository;

	@Mock
	private MultigameRoundRepository multigameRoundRepository;

	@Mock
	private MultigameRoundMemberRepository multigameRoundMemberRepository;

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
		given(memberRepository.countByRole()).willReturn(List.of(
				new Object[] {Member.Role.GUEST, 60L},
				new Object[] {Member.Role.MEMBER, 35L},
				new Object[] {Member.Role.ADMIN, 5L}
		));
		given(memberRepository.countByCreatedAtGreaterThanEqual(any(Instant.class))).willReturn(3L);
		given(memberDeviceRepository.count()).willReturn(150L);
		given(memberDeviceRepository.countByLastAccessedAtGreaterThanEqual(any(Instant.class))).willReturn(80L);
		given(memberDeviceRepository.countByPlatformJsOs()).willReturn(List.of(
				new Object[] {"iOS", 60L},
				new Object[] {"Android", 40L}
		));
		given(memberDeviceRepository.countByPlatformJsName()).willReturn(List.of(
				new Object[] {"Chrome", 90L},
				new Object[] {"Safari", 10L}
		));
		given(noticeRepository.count()).willReturn(4L);
		given(courseRepository.count()).willReturn(1200L);
		given(courseRepository.countDistinctTerms()).willReturn(2L);
		given(courseRepository.countByTerm()).willReturn(List.of(
				new Object[] {"20262", 700L},
				new Object[] {"20261", 500L}
		));
		given(exchangeIntentRepository.countByTermAndIsDeletedFalse("202620")).willReturn(30L);
		given(exchangeRoomRepository.countByTermAndStatus("202620", "ACTIVE")).willReturn(5L);
		given(exchangeRoomRepository.countByTermGroupByStatus("202620")).willReturn(List.of(
				new Object[] {"ACTIVE", 5L},
				new Object[] {"PARTIAL_OFF", 2L}
		));
		given(exchangeRoomIntentRepository.countDistinctIntentIdByTermAndIsDeletedFalse("202620")).willReturn(12L);
		given(exchangeRoomMessageRepository.countByTerm("202620")).willReturn(120L);
		given(singleGameRepository.count()).willReturn(220L);
		given(singleGameRepository.countByIsCompletedTrue()).willReturn(200L);
		given(singleGameRepository.countByIsCompletedTrueAndCreatedAtGreaterThanEqual(any(Instant.class))).willReturn(5L);
		given(singleGameRepository.averageTTotalByIsCompletedTrue()).willReturn(41234.0);
		given(singleGameRepository.minTTotalByIsCompletedTrue()).willReturn(30000);
		given(singleGameRepository.aggregateByTotalCourses()).willReturn(List.of(
				new Object[] {1, 220L, 200L, 41234.0, 30000},
				new Object[] {8, 220L, 200L, 41234.0, 30000}
		));
		given(multigameRoundRepository.countByParticipantCountGreaterThan(0)).willReturn(8L);
		given(multigameRoundRepository.findMaxParticipantCount()).willReturn(Optional.of(120));
		given(multigameRoundRepository.findAllByOrderByStartTimeDesc(PageRequest.of(0, 10))).willReturn(new PageImpl<>(List.of(
				MultigameRoundEntity.builder().startTime("202604020010").participantCount(120).capacity(60).build()
		)));
		given(multigameRoundRepository.countRoundsByHour()).willReturn(List.of(
				new Object[] {10, 14L},
				new Object[] {20, 5L}
		));
		given(multigameRoundRepository.countRoundsByDayOfWeek()).willReturn(List.of(
				new Object[] {3, 8L},
				new Object[] {5, 6L}
		));
		given(multigameRoundRepository.countRoundsByDaySince(any(String.class))).willReturn(List.of(
				new Object[] {"2026-03-25", 4L},
				new Object[] {"2026-04-02", 2L}
		));
		List<Object[]> byStartTimeRows = new java.util.ArrayList<>();
		byStartTimeRows.add(new Object[] {"202604020010", 90L, 30L});
		given(multigameRoundMemberRepository.aggregateResultByStartTimes(any(Collection.class))).willReturn(byStartTimeRows);
		List<Object[]> overallRows = new java.util.ArrayList<>();
		overallRows.add(new Object[] {900L, 300L});
		given(multigameRoundMemberRepository.aggregateOverallResult()).willReturn(overallRows);
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
		given(memberRepository.countByRole()).willReturn(List.of());
		given(memberRepository.countByCreatedAtGreaterThanEqual(any(Instant.class))).willReturn(0L);
		given(memberDeviceRepository.count()).willReturn(0L);
		given(memberDeviceRepository.countByLastAccessedAtGreaterThanEqual(any(Instant.class))).willReturn(0L);
		given(memberDeviceRepository.countByPlatformJsOs()).willReturn(List.of());
		given(memberDeviceRepository.countByPlatformJsName()).willReturn(List.of());
		given(noticeRepository.count()).willReturn(0L);
		given(courseRepository.count()).willReturn(0L);
		given(courseRepository.countDistinctTerms()).willReturn(0L);
		given(courseRepository.countByTerm()).willReturn(List.of());
		given(exchangeIntentRepository.countByTermAndIsDeletedFalse("202620")).willReturn(0L);
		given(exchangeRoomRepository.countByTermAndStatus("202620", "ACTIVE")).willReturn(0L);
		given(exchangeRoomRepository.countByTermGroupByStatus("202620")).willReturn(List.of());
		given(exchangeRoomIntentRepository.countDistinctIntentIdByTermAndIsDeletedFalse("202620")).willReturn(0L);
		given(exchangeRoomMessageRepository.countByTerm("202620")).willReturn(0L);
		given(singleGameRepository.count()).willReturn(0L);
		given(singleGameRepository.countByIsCompletedTrue()).willReturn(0L);
		given(singleGameRepository.countByIsCompletedTrueAndCreatedAtGreaterThanEqual(any(Instant.class))).willReturn(0L);
		given(singleGameRepository.averageTTotalByIsCompletedTrue()).willReturn(null);
		given(singleGameRepository.minTTotalByIsCompletedTrue()).willReturn(null);
		given(singleGameRepository.aggregateByTotalCourses()).willReturn(List.of());
		given(multigameRoundRepository.countByParticipantCountGreaterThan(0)).willReturn(0L);
		given(multigameRoundRepository.findMaxParticipantCount()).willReturn(Optional.empty());
		given(multigameRoundRepository.findAllByOrderByStartTimeDesc(PageRequest.of(0, 10))).willReturn(new PageImpl<>(List.of()));
		given(multigameRoundRepository.countRoundsByHour()).willReturn(List.of());
		given(multigameRoundRepository.countRoundsByDayOfWeek()).willReturn(List.of());
		given(multigameRoundRepository.countRoundsByDaySince(any(String.class))).willReturn(List.of());
		given(multigameRoundMemberRepository.aggregateOverallResult()).willReturn(List.of());
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