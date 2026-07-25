package com.mjusugangsincheonghelper.multigame.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.multigame.session.dto.GameRequestResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("GameQueueService 테스트")
class GameQueueServiceTest {

	@Mock
	private StringRedisTemplate stringRedisTemplate;

	@InjectMocks
	private GameQueueService gameQueueService;

	@Nested
	@DisplayName("mapResult 메서드는")
	class Describe_mapResult {

		@Test
		@DisplayName("BLOCKED 상태를 올바르게 매핑한다")
		void it_maps_blocked_status() throws Exception {
			// Given
			List<Object> result = List.of("BLOCKED", "WAITING");

			// When
			java.lang.reflect.Method method = GameQueueService.class.getDeclaredMethod("mapResult", List.class);
			method.setAccessible(true);
			GameRequestResponse response = (GameRequestResponse) method.invoke(gameQueueService, result);

			// Then
			assertThat(response.getStatus()).isEqualTo("BLOCKED");
			assertThat(response.getCurrentState()).isEqualTo("WAITING");
		}

		@Test
		@DisplayName("PENDING 상태를 올바르게 매핑한다")
		void it_maps_pending_status() throws Exception {
			// Given
			List<Object> result = List.of("PENDING", 5L, 3L);

			// When
			java.lang.reflect.Method method = GameQueueService.class.getDeclaredMethod("mapResult", List.class);
			method.setAccessible(true);
			GameRequestResponse response = (GameRequestResponse) method.invoke(gameQueueService, result);

			// Then
			assertThat(response.getStatus()).isEqualTo("PENDING");
			assertThat(response.getSeq()).isEqualTo(5);
			assertThat(response.getLimit()).isEqualTo(3);
		}

		@Test
		@DisplayName("SUCCESS 상태를 올바르게 매핑한다")
		void it_maps_success_status() throws Exception {
			// Given
			List<Object> result = List.of("SUCCESS", 3L, 2L);

			// When
			java.lang.reflect.Method method = GameQueueService.class.getDeclaredMethod("mapResult", List.class);
			method.setAccessible(true);
			GameRequestResponse response = (GameRequestResponse) method.invoke(gameQueueService, result);

			// Then
			assertThat(response.getStatus()).isEqualTo("SUCCESS");
			assertThat(response.getSubjectId()).isEqualTo(3);
			assertThat(response.getRemaining()).isEqualTo(2);
		}

		@Test
		@DisplayName("FAIL_SOLDOUT 상태를 올바르게 매핑한다")
		void it_maps_fail_soldout_status() throws Exception {
			// Given
			List<Object> result = List.of("FAIL_SOLDOUT", 2L);

			// When
			java.lang.reflect.Method method = GameQueueService.class.getDeclaredMethod("mapResult", List.class);
			method.setAccessible(true);
			GameRequestResponse response = (GameRequestResponse) method.invoke(gameQueueService, result);

			// Then
			assertThat(response.getStatus()).isEqualTo("FAIL_SOLDOUT");
			assertThat(response.getSubjectId()).isEqualTo(2);
		}

		@Test
		@DisplayName("FAIL_DUPLICATE 상태를 올바르게 매핑한다")
		void it_maps_fail_duplicate_status() throws Exception {
			// Given
			List<Object> result = List.of("FAIL_DUPLICATE");

			// When
			java.lang.reflect.Method method = GameQueueService.class.getDeclaredMethod("mapResult", List.class);
			method.setAccessible(true);
			GameRequestResponse response = (GameRequestResponse) method.invoke(gameQueueService, result);

			// Then
			assertThat(response.getStatus()).isEqualTo("FAIL_DUPLICATE");
		}

		@Test
		@DisplayName("null 결과이면 예외를 발생시킨다")
		void it_throws_exception_when_result_is_null() {
			// When & Then
			assertThatThrownBy(() -> {
				java.lang.reflect.Method method = GameQueueService.class.getDeclaredMethod("mapResult", List.class);
				method.setAccessible(true);
				method.invoke(gameQueueService, (List<Object>) null);
			}).hasCauseInstanceOf(BaseException.class);
		}

		@Test
		@DisplayName("빈 결과이면 예외를 발생시킨다")
		void it_throws_exception_when_result_is_empty() {
			// When & Then
			assertThatThrownBy(() -> {
				java.lang.reflect.Method method = GameQueueService.class.getDeclaredMethod("mapResult", List.class);
				method.setAccessible(true);
				method.invoke(gameQueueService, List.of());
			}).hasCauseInstanceOf(BaseException.class);
		}

		@Test
		@DisplayName("알 수 없는 상태이면 예외를 발생시킨다")
		void it_throws_exception_for_unknown_status() {
			// Given
			List<Object> result = List.of("UNKNOWN_STATUS");

			// When & Then
			assertThatThrownBy(() -> {
				java.lang.reflect.Method method = GameQueueService.class.getDeclaredMethod("mapResult", List.class);
				method.setAccessible(true);
				method.invoke(gameQueueService, result);
			}).hasCauseInstanceOf(BaseException.class);
		}
	}

	@Nested
	@DisplayName("toInt 메서드는")
	class Describe_toInt {

		@Test
		@DisplayName("Number 타입을 Integer로 변환한다")
		void it_converts_number_to_integer() throws Exception {
			// Given
			Long value = 42L;

			// When
			java.lang.reflect.Method method = GameQueueService.class.getDeclaredMethod("toInt", Object.class);
			method.setAccessible(true);
			Integer result = (Integer) method.invoke(gameQueueService, value);

			// Then
			assertThat(result).isEqualTo(42);
		}

		@Test
		@DisplayName("문자열을 Integer로 변환한다")
		void it_converts_string_to_integer() throws Exception {
			// Given
			String value = "123";

			// When
			java.lang.reflect.Method method = GameQueueService.class.getDeclaredMethod("toInt", Object.class);
			method.setAccessible(true);
			Integer result = (Integer) method.invoke(gameQueueService, value);

			// Then
			assertThat(result).isEqualTo(123);
		}

		@Test
		@DisplayName("null이면 null을 반환한다")
		void it_returns_null_for_null() throws Exception {
			// When
			java.lang.reflect.Method method = GameQueueService.class.getDeclaredMethod("toInt", Object.class);
			method.setAccessible(true);
			Integer result = (Integer) method.invoke(gameQueueService, (Object) null);

			// Then
			assertThat(result).isNull();
		}

		@Test
		@DisplayName("변환 불가 문자열이면 null을 반환한다")
		void it_returns_null_for_invalid_string() throws Exception {
			// Given
			String value = "not_a_number";

			// When
			java.lang.reflect.Method method = GameQueueService.class.getDeclaredMethod("toInt", Object.class);
			method.setAccessible(true);
			Integer result = (Integer) method.invoke(gameQueueService, value);

			// Then
			assertThat(result).isNull();
		}
	}
}
