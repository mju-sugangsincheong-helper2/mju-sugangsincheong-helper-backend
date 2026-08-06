package com.mjusugangsincheonghelper.system.controller;

import com.mjusugangsincheonghelper.global.api.exception.GlobalExceptionHandler;
import com.mjusugangsincheonghelper.global.api.filter.GlobalMetaFilter;
import com.mjusugangsincheonghelper.global.api.support.ClientInfoExtractor;
import com.mjusugangsincheonghelper.global.api.support.InstanceIdProvider;
import com.mjusugangsincheonghelper.global.security.filter.JwtAuthenticationFilter;
import com.mjusugangsincheonghelper.system.dto.SystemStatsResponse;
import com.mjusugangsincheonghelper.system.dto.SystemStatsResponse.CourseTermCount;
import com.mjusugangsincheonghelper.system.dto.SystemStatsResponse.DeviceDistribution;
import com.mjusugangsincheonghelper.system.dto.SystemStatsResponse.ExchangeStats;
import com.mjusugangsincheonghelper.system.dto.SystemStatsResponse.CourseCount;
import com.mjusugangsincheonghelper.system.dto.SystemStatsResponse.MultigameStats;
import com.mjusugangsincheonghelper.system.dto.SystemStatsResponse.RoomStatusCount;
import com.mjusugangsincheonghelper.system.dto.SystemStatsResponse.RoundStats;
import com.mjusugangsincheonghelper.system.dto.SystemStatsResponse.SingleGameStats;
import com.mjusugangsincheonghelper.system.dto.SystemStatsResponse.MemberStats;
import com.mjusugangsincheonghelper.system.service.SystemStatsService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemStatsController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, GlobalMetaFilter.class, ClientInfoExtractor.class})
@DisplayName("SystemStatsController 테스트")
class SystemStatsControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SystemStatsService systemStatsService;

	@MockitoBean
	private InstanceIdProvider instanceIdProvider;

	@MockitoBean
	private JwtAuthenticationFilter jwtAuthenticationFilter;

	@BeforeEach
	void setUp() {
		Authentication authentication = mock(Authentication.class);
		given(authentication.getPrincipal()).willReturn(1L);

		SecurityContext securityContext = mock(SecurityContext.class);
		given(securityContext.getAuthentication()).willReturn(authentication);

		SecurityContextHolder.setContext(securityContext);
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("도메인 지표 조회 API가 200 OK와 회원 구성 데이터를 반환한다")
	void shouldReturnDomainStats() throws Exception {
		SystemStatsResponse stats = new SystemStatsResponse(
				new MemberStats(100, 60, 35, 5),
				3,
				12,
				150,
				80,
				4,
				1200,
				2,
				List.of(new CourseTermCount("20262", 700), new CourseTermCount("20261", 500)),
				List.of(new DeviceDistribution("iOS", 60), new DeviceDistribution("Android", 40)),
				List.of(new DeviceDistribution("Chrome", 90), new DeviceDistribution("Safari", 10)),
				new ExchangeStats(30, 5, 120, 12, 40, List.of(new RoomStatusCount("ACTIVE", 5), new RoomStatusCount("PARTIAL_OFF", 2))),
				new SingleGameStats(220, 200, 5, 12, 91, 41234, 30000,
						List.of(new CourseCount(1, 30), new CourseCount(8, 170))),
				new MultigameStats(8, 120, 900, 300, 75,
						List.of(new RoundStats("202604020010", 120, 60, 90, 30))),
				17
		);

		given(systemStatsService.getStats()).willReturn(stats);

		mockMvc.perform(get("/api/v1/system/stats"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.members.total").value(100))
				.andExpect(jsonPath("$.data.members.guest").value(60))
				.andExpect(jsonPath("$.data.members.regular").value(35))
				.andExpect(jsonPath("$.data.members.admin").value(5))
				.andExpect(jsonPath("$.data.newMembersToday").value(3))
				.andExpect(jsonPath("$.data.coursesByTerm[0].term").value("20262"))
				.andExpect(jsonPath("$.data.coursesByTerm[0].count").value(700))
				.andExpect(jsonPath("$.data.activeDevicesLast7Days").value(80))
				.andExpect(jsonPath("$.data.devicesByOs[0].label").value("iOS"))
				.andExpect(jsonPath("$.data.devicesByOs[0].count").value(60))
				.andExpect(jsonPath("$.data.exchange.intents").value(30))
				.andExpect(jsonPath("$.data.exchange.activeRooms").value(5))
				.andExpect(jsonPath("$.data.exchange.messages").value(120))
				.andExpect(jsonPath("$.data.exchange.matchedIntents").value(12))
				.andExpect(jsonPath("$.data.exchange.matchedRate").value(40))
				.andExpect(jsonPath("$.data.exchange.roomsByStatus[0].status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.singleGame.completed").value(200))
				.andExpect(jsonPath("$.data.singleGame.completionRate").value(91))
				.andExpect(jsonPath("$.data.singleGame.byCourseCount[0].totalCourses").value(1))
				.andExpect(jsonPath("$.data.multigame.peakParticipants").value(120))
				.andExpect(jsonPath("$.data.multigame.successRate").value(75))
				.andExpect(jsonPath("$.data.multigame.recentRounds[0].participantCount").value(120))
				.andExpect(jsonPath("$.data.notificationQueueLength").value(17));
	}
}