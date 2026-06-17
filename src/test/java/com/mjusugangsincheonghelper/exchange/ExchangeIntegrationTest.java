package com.mjusugangsincheonghelper.exchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import com.mjusugangsincheonghelper.database.entity.ExchangeIntentEntity;
import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.repository.ExchangeIntentRepository;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import com.mjusugangsincheonghelper.exchange.dto.IntentCreateRequest;
import com.mjusugangsincheonghelper.system.service.SystemConfigService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Exchange 통합 테스트")
class ExchangeIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ExchangeIntentRepository intentRepository;

	@Autowired
	private SystemConfigService systemConfigService;

	@Autowired
	private MemberRepository memberRepository;

	private Member testMember;

	@BeforeEach
	void setUp() {
		intentRepository.deleteAll();
		memberRepository.deleteAll();

		testMember = memberRepository.save(Member.builder()
				.role(Member.Role.MEMBER)
				.name("테스트유저")
				.department("컴퓨터공학과")
				.build());

		UsernamePasswordAuthenticationToken authentication =
				new UsernamePasswordAuthenticationToken(testMember.getId(), null,
						java.util.Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_MEMBER")));
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
		intentRepository.deleteAll();
		memberRepository.deleteAll();
	}

	@Nested
	@DisplayName("교환 의도 등록 API는")
	class Describe_createIntent {

		@Test
		@DisplayName("유효한 요청으로 의도를 등록할 수 있다")
		void it_creates_intent() throws Exception {
			// Given
			IntentCreateRequest request = IntentCreateRequest.builder()
					.giveCourseNo("10001")
					.wantCourseNo("10002")
					.build();

			// When & Then
			mockMvc.perform(post("/api/v1/exchange/intents")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.giveCourseNo").value("10001"))
					.andExpect(jsonPath("$.data.wantCourseNo").value("10002"))
					.andExpect(jsonPath("$.data.deleted").value(false));

			// Verify DB
			String term = systemConfigService.getCurrentTerm();
			List<ExchangeIntentEntity> intents = intentRepository.findByTermAndIsDeletedFalse(term);
			assertThat(intents).hasSize(1);
			assertThat(intents.get(0).getGiveCourseNo()).isEqualTo("10001");
		}

		@Test
		@DisplayName("같은 과목 교환은 불가능하다")
		void it_rejects_same_course() throws Exception {
			// Given
			IntentCreateRequest request = IntentCreateRequest.builder()
					.giveCourseNo("10001")
					.wantCourseNo("10001")
					.build();

			// When & Then
			mockMvc.perform(post("/api/v1/exchange/intents")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code").value("EXCHANGE_006"));
		}
	}

	@Nested
	@DisplayName("메인 화면 조회 API는")
	class Describe_getMain {

		@Test
		@DisplayName("나의 의도와 방 목록을 반환한다")
		void it_returns_my_intents_and_rooms() throws Exception {
			// Given
			String term = systemConfigService.getCurrentTerm();
			ExchangeIntentEntity intent = ExchangeIntentEntity.builder()
					.term(term)
					.memberId(testMember.getId())
					.giveCourseNo("10001")
					.wantCourseNo("10002")
					.build();
			intentRepository.save(intent);

			// When & Then
			mockMvc.perform(get("/api/v1/exchange/main"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.myIntents").isArray())
					.andExpect(jsonPath("$.data.myRooms").isArray())
					.andExpect(jsonPath("$.data.recentIntents").isArray());
		}
	}
}
