package com.mjusugangsincheonghelper.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mjusugangsincheonghelper.database.entity.ExchangeIntentEntity;
import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.repository.ExchangeIntentRepository;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import com.mjusugangsincheonghelper.system.service.SystemConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Exchange 통합 테스트")
class ExchangeIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	private ExchangeIntentRepository exchangeIntentRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private StringRedisTemplate stringRedisTemplate;

	@Autowired
	private CacheManager cacheManager;

	@Autowired
	private SystemConfigService systemConfigService;

	private Member testMember;
	private String term;

	@BeforeEach
	void setUp() {
		exchangeIntentRepository.deleteAll();
		memberRepository.deleteAll();

		stringRedisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
			connection.flushDb();
			return null;
		});

		testMember = memberRepository.save(Member.builder()
				.role(Member.Role.MEMBER)
				.name("테스트유저")
				.department("컴퓨터공학과")
				.build());

		term = systemConfigService.getCurrentTerm();

		UsernamePasswordAuthenticationToken authentication =
				new UsernamePasswordAuthenticationToken(testMember.getId(), null, null);
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
		exchangeIntentRepository.deleteAll();
		memberRepository.deleteAll();
		stringRedisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
			connection.flushDb();
			return null;
		});
	}

	@Nested
	@DisplayName("Redis 캐시 동작은")
	class Describe_redisCache {

		@Test
		@DisplayName("메인 화면 조회 시 Redis 캐시가 생성된다")
		void it_creates_redis_cache_on_main_query() throws Exception {
			exchangeIntentRepository.save(ExchangeIntentEntity.builder()
					.term(term)
					.memberId(testMember.getId())
					.giveCourseNo("10001")
					.wantCourseNo("10002")
					.build());

			Integer keysBefore = stringRedisTemplate.keys("*").size();

			mockMvc.perform(get("/api/v1/exchange/main"))
					.andExpect(status().isOk());

			Integer keysAfter = stringRedisTemplate.keys("*").size();

			assertThat(keysAfter).isGreaterThan(keysBefore);
		}

		@Test
		@DisplayName("CacheManager가 Redis 캐시를 사용한다")
		void it_uses_redis_cache_manager() {
			assertThat(cacheManager).isNotNull();
			assertThat(cacheManager.getCacheNames()).contains("user-intents");
		}

		@Test
		@DisplayName("의도 등록 시 캐시가 무효화된다")
		void it_invalidates_cache_on_intent_create() throws Exception {
			String request = """
					{
						"giveCourseNo": "10001",
						"wantCourseNo": "10002"
					}
					""";

			mockMvc.perform(post("/api/v1/exchange/intents")
							.contentType(MediaType.APPLICATION_JSON)
							.content(request))
					.andExpect(status().isCreated());

			mockMvc.perform(get("/api/v1/exchange/main"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.myIntents").isArray())
					.andExpect(jsonPath("$.data.myIntents[0].giveCourseNo").value("10001"));
		}
	}
}
