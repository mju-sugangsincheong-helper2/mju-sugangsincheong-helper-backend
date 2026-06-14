package com.mjusugangsincheonghelper.singlegame;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.entity.SingleGameEntity;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import com.mjusugangsincheonghelper.database.repository.SingleGameDetailRepository;
import com.mjusugangsincheonghelper.database.repository.SingleGameRepository;
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
@DisplayName("SingleGame 통합 테스트")
class SingleGameIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	private SingleGameRepository singleGameRepository;

	@Autowired
	private SingleGameDetailRepository singleGameDetailRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private StringRedisTemplate stringRedisTemplate;

	@Autowired
	private CacheManager cacheManager;

	private Member testMember;

	@BeforeEach
	void setUp() {
		singleGameDetailRepository.deleteAll();
		singleGameRepository.deleteAll();
		memberRepository.deleteAll();

		stringRedisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
			connection.serverCommands().flushDb();
			return null;
		});

		testMember = memberRepository.save(Member.builder()
				.role(Member.Role.MEMBER)
				.name("테스트유저")
				.department("컴퓨터공학과")
				.build());

		UsernamePasswordAuthenticationToken authentication =
				new UsernamePasswordAuthenticationToken(testMember.getId(), null, null);
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
		singleGameDetailRepository.deleteAll();
		singleGameRepository.deleteAll();
		memberRepository.deleteAll();
		stringRedisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
			connection.serverCommands().flushDb();
			return null;
		});
	}

	@Nested
	@DisplayName("게임 결과 저장 → 분석 조회 흐름은")
	class Describe_saveAndAnalysis {

		@Test
		@DisplayName("전체 흐름이 정상 동작한다")
		void it_works_end_to_end() throws Exception {
			String saveRequest = """
					{
						"totalCourses": 6,
						"isCompleted": true,
						"tEnterMain": 245,
						"details": [
							{"sequence": 1, "tClickCourse": 450, "tClickYes": 180, "tClickOk": 200},
							{"sequence": 2, "tClickCourse": 320, "tClickYes": 150, "tClickOk": 190},
							{"sequence": 3, "tClickCourse": 280, "tClickYes": 140, "tClickOk": 170}
						]
					}
					""";

			String saveResponse = mockMvc.perform(post("/api/v1/singlegame")
							.contentType(MediaType.APPLICATION_JSON)
							.content(saveRequest))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.gameId").exists())
					.andExpect(jsonPath("$.data.message").value("게임 결과가 성공적으로 기록되었습니다."))
					.andExpect(jsonPath("$.meta").exists())
					.andReturn()
					.getResponse()
					.getContentAsString();

			Long gameId = objectMapper.readTree(saveResponse).get("data").get("gameId").asLong();

			mockMvc.perform(get("/api/v1/singlegame/" + gameId + "/analysis"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.gameId").value(gameId))
					.andExpect(jsonPath("$.data.totalCourses").value(6))
					.andExpect(jsonPath("$.data.completed").value(true))
					.andExpect(jsonPath("$.data.summary").exists())
					.andExpect(jsonPath("$.data.details").isArray())
					.andExpect(jsonPath("$.data.details").isNotEmpty());
		}
	}

	@Nested
	@DisplayName("게임 결과 저장 → 랭킹 조회 흐름은")
	class Describe_saveAndRanking {

		@Test
		@DisplayName("완료된 게임이 랭킹에 포함된다")
		void it_appears_in_ranking() throws Exception {
			String saveRequest = """
					{
						"totalCourses": 6,
						"isCompleted": true,
						"tEnterMain": 200,
						"details": [
							{"sequence": 1, "tClickCourse": 400, "tClickYes": 150, "tClickOk": 180}
						]
					}
					""";

			mockMvc.perform(post("/api/v1/singlegame")
							.contentType(MediaType.APPLICATION_JSON)
							.content(saveRequest))
					.andExpect(status().isCreated());

			mockMvc.perform(get("/api/v1/singlegame/rank")
							.param("totalCourses", "6")
							.param("scope", "GLOBAL"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.totalCourses").value(6))
					.andExpect(jsonPath("$.data.scope").value("GLOBAL"))
					.andExpect(jsonPath("$.data.rankings").isArray())
					.andExpect(jsonPath("$.data.rankings[0].name").value("테스트유저"));
		}
	}

	@Nested
	@DisplayName("게임 결과 저장 → 내 기록 조회 흐름은")
	class Describe_saveAndMyRecords {

		@Test
		@DisplayName("저장된 게임이 내 기록에 포함된다")
		void it_appears_in_my_records() throws Exception {
			String saveRequest = """
					{
						"totalCourses": 6,
						"isCompleted": true,
						"tEnterMain": 200,
						"details": [
							{"sequence": 1, "tClickCourse": 400, "tClickYes": 150, "tClickOk": 180}
						]
					}
					""";

			mockMvc.perform(post("/api/v1/singlegame")
							.contentType(MediaType.APPLICATION_JSON)
							.content(saveRequest))
					.andExpect(status().isCreated());

			mockMvc.perform(get("/api/v1/singlegame/my")
							.param("page", "0")
							.param("size", "10"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.content").isArray())
					.andExpect(jsonPath("$.data.content[0].totalCourses").value(6))
					.andExpect(jsonPath("$.data.content[0].completed").value(true))
					.andExpect(jsonPath("$.data.content[0].ranking.global.rank").exists());
		}
	}

	@Nested
	@DisplayName("응답 메타데이터는")
	class Describe_responseMeta {

		@Test
		@DisplayName("requestId, apiVersion, path, method, timestamp, durationMs를 포함한다")
		void it_includes_all_meta_fields() throws Exception {
			mockMvc.perform(get("/api/v1/singlegame/rank")
							.param("totalCourses", "6")
							.param("scope", "GLOBAL"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.meta.requestId").exists())
					.andExpect(jsonPath("$.meta.apiVersion").value("v1"))
					.andExpect(jsonPath("$.meta.path").value("/api/v1/singlegame/rank"))
					.andExpect(jsonPath("$.meta.method").value("GET"))
					.andExpect(jsonPath("$.meta.timestamp").exists())
					.andExpect(jsonPath("$.meta.durationMs").isNumber());
		}
	}

	@Nested
	@DisplayName("검증 실패는")
	class Describe_validation {

		@Test
		@DisplayName("허용되지 않은 totalCourses이면 400 응답을 반환한다")
		void it_returns_400_for_invalid_total_courses() throws Exception {
			String invalidRequest = """
					{
						"totalCourses": 4,
						"isCompleted": true,
						"tEnterMain": 200,
						"details": [
							{"sequence": 1, "tClickCourse": 400, "tClickYes": 150, "tClickOk": 180}
						]
					}
					""";

			mockMvc.perform(post("/api/v1/singlegame")
							.contentType(MediaType.APPLICATION_JSON)
							.content(invalidRequest))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code").value("SINGLEGAME_002"));
		}

		@Test
		@DisplayName("빈 details이면 400 응답을 반환한다")
		void it_returns_400_for_empty_details() throws Exception {
			String invalidRequest = """
					{
						"totalCourses": 6,
						"isCompleted": true,
						"tEnterMain": 200,
						"details": []
					}
					""";

			mockMvc.perform(post("/api/v1/singlegame")
							.contentType(MediaType.APPLICATION_JSON)
							.content(invalidRequest))
					.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("존재하지 않는 gameId로 분석 조회하면 404 응답을 반환한다")
		void it_returns_404_for_nonexistent_game() throws Exception {
			mockMvc.perform(get("/api/v1/singlegame/999999/analysis"))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code").value("SINGLEGAME_001"));
		}
	}

	@Nested
	@DisplayName("학과별 랭킹은")
	class Describe_departmentRanking {

		@Test
		@DisplayName("특정 학과 유저만 포함한다")
		void it_filters_by_department() throws Exception {
			Member otherMember = memberRepository.save(Member.builder()
					.role(Member.Role.MEMBER)
					.name("다른학과유저")
					.department("경영학과")
					.build());

			singleGameRepository.save(SingleGameEntity.builder()
					.memberId(testMember.getId()).tTotal(5000).tEnterMain(200)
					.isCompleted(true).totalCourses(6).build());
			singleGameRepository.save(SingleGameEntity.builder()
					.memberId(otherMember.getId()).tTotal(3000).tEnterMain(150)
					.isCompleted(true).totalCourses(6).build());

			mockMvc.perform(get("/api/v1/singlegame/rank")
							.param("totalCourses", "6")
							.param("scope", "DEPARTMENT"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.scope").value("DEPARTMENT"))
					.andExpect(jsonPath("$.data.rankings").isArray())
					.andExpect(jsonPath("$.data.rankings.length()").value(1))
					.andExpect(jsonPath("$.data.rankings[0].name").value("테스트유저"));
		}
	}

	@Nested
	@DisplayName("Redis 캐시 동작은")
	class Describe_redisCache {

		@Test
		@DisplayName("랭킹 조회 시 Redis 캐시가 생성된다")
		void it_creates_redis_cache_on_ranking_query() throws Exception {
			singleGameRepository.save(SingleGameEntity.builder()
					.memberId(testMember.getId()).tTotal(5000).tEnterMain(200)
					.isCompleted(true).totalCourses(6).build());

			Integer keysBefore = stringRedisTemplate.keys("*").size();

			mockMvc.perform(get("/api/v1/singlegame/rank")
							.param("totalCourses", "6")
							.param("scope", "GLOBAL"))
					.andExpect(status().isOk());

			Integer keysAfter = stringRedisTemplate.keys("*").size();

			assertThat(keysAfter).isGreaterThan(keysBefore);
		}

		@Test
		@DisplayName("캐시 히트 시에도 동일한 데이터를 응답한다")
		void it_responds_same_data_on_cache_hit() throws Exception {
			singleGameRepository.save(SingleGameEntity.builder()
					.memberId(testMember.getId()).tTotal(5000).tEnterMain(200)
					.isCompleted(true).totalCourses(6).build());

			mockMvc.perform(get("/api/v1/singlegame/rank")
							.param("totalCourses", "6")
							.param("scope", "GLOBAL"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.rankings[0].name").value("테스트유저"));

			mockMvc.perform(get("/api/v1/singlegame/rank")
							.param("totalCourses", "6")
							.param("scope", "GLOBAL"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.rankings[0].name").value("테스트유저"))
					.andExpect(jsonPath("$.data.rankings[0].tTotal").value(5000));
		}

		@Test
		@DisplayName("CacheManager가 Redis 캐시를 사용한다")
		void it_uses_redis_cache_manager() {
			assertThat(cacheManager).isNotNull();
			assertThat(cacheManager.getCacheNames()).contains("singlegame-rank");
		}
	}
}
