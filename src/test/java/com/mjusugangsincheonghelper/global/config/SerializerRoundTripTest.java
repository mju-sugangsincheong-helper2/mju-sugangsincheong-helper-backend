package com.mjusugangsincheonghelper.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.mjusugangsincheonghelper.exchange.dto.cache.FeedCacheDto;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;

/**
 * Redis 캐시 직렬화 회귀 테스트.
 *
 * <p>Spring Data Redis 4.1(Jackson 3)의 기본 writer는 캐시 루트 값(특히 {@code List})에 타입 정보를
 * 남기지 않아, {@code enableUnsafeDefaultTyping()}만으로는 쓰기([])와 읽기(["type", ...] 기대)가
 * 어긋나서 매번 역직렬화에 실패한다({@link RedisConfig} 참고).</p>
 *
 * <p>{@code RedisConfig}는 이 문제를 루트를 {@code Object}로 선언해 쓰도록 writer를 커스텀해 해결한다.
 * 이 테스트는 그 설정이 빈/비어있지 않은 List와 단일 객체 모두를 정상 round-trip 함을 보장한다.</p>
 */
class SerializerRoundTripTest {

	/** RedisConfig.redisSerializer() 와 동일한 설정 */
	private GenericJacksonJsonRedisSerializer serializer() {
		return GenericJacksonJsonRedisSerializer.builder()
				.enableUnsafeDefaultTyping()
				.customize(builder -> builder.findAndAddModules())
				.writer((mapper, value) -> mapper.writerFor(Object.class).writeValueAsBytes(value))
				.build();
	}

	@Test
	void emptyListRoundTrips() {
		GenericJacksonJsonRedisSerializer s = serializer();

		byte[] bytes = s.serialize(List.of());
		System.out.println("empty list bytes: " + new String(bytes, StandardCharsets.UTF_8));

		Object back = s.deserialize(bytes, Object.class);
		assertThat(back).isInstanceOf(List.class);
		assertThat((List<?>) back).isEmpty();
	}

	@Test
	void nonEmptyListRoundTripsWithConcreteElements() {
		GenericJacksonJsonRedisSerializer s = serializer();

		byte[] bytes = s.serialize(List.of(
				FeedCacheDto.builder().intentId(1L).giveCourseNo("A").wantCourseNo("B")
						.createdAt(java.time.Instant.now()).build()));
		System.out.println("non-empty bytes  : " + new String(bytes, StandardCharsets.UTF_8));

		Object back = s.deserialize(bytes, Object.class);
		assertThat(back).isInstanceOf(List.class);
		List<?> list = (List<?>) back;
		assertThat(list).hasSize(1);
		assertThat(list.get(0)).isInstanceOf(FeedCacheDto.class);
		assertThat(((FeedCacheDto) list.get(0)).getGiveCourseNo()).isEqualTo("A");
	}

	@Test
	void singleObjectRoundTrips() {
		GenericJacksonJsonRedisSerializer s = serializer();

		byte[] bytes = s.serialize(FeedCacheDto.builder().intentId(7L).giveCourseNo("A").wantCourseNo("B").build());
		System.out.println("single obj bytes : " + new String(bytes, StandardCharsets.UTF_8));

		Object back = s.deserialize(bytes, Object.class);
		assertThat(back).isInstanceOf(FeedCacheDto.class);
		assertThat(((FeedCacheDto) back).getIntentId()).isEqualTo(7L);
	}
}