package com.mjusugangsincheonghelper.multigame.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.mjusugangsincheonghelper.multigame.common.MultigameRedisKeyProvider;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@DisplayName("WaitingRoomService 테스트")
class WaitingRoomServiceTest {

	@Mock
	private StringRedisTemplate stringRedisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	@InjectMocks
	private WaitingRoomService waitingRoomService;

	@SuppressWarnings("unchecked")
	private Cursor<String> cursorOf(String... keys) {
		Iterator<String> it = List.of(keys).iterator();
		Cursor<String> cursor = mock(Cursor.class);
		lenient().when(cursor.hasNext()).thenAnswer(invocation -> it.hasNext());
		lenient().when(cursor.next()).thenAnswer(invocation -> it.next());
		return cursor;
	}

	@Nested
	@DisplayName("updateHeartbeat 메서드는")
	class Describe_updateHeartbeat {

		@Test
		@DisplayName("heartbeat 키를 TTL 6초로 설정한다")
		void it_sets_heartbeat_key_with_6_second_ttl() {
			// Given
			String t = "20260726100000";
			Long memberId = 1L;
			String expectedKey = MultigameRedisKeyProvider.heartbeat(t, memberId);

			given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);

			// When
			waitingRoomService.updateHeartbeat(t, memberId);

			// Then
			ArgumentCaptor<Duration> durationCaptor = ArgumentCaptor.forClass(Duration.class);
			verify(valueOperations).set(eq(expectedKey), eq("1"), durationCaptor.capture());
			assertThat(durationCaptor.getValue().getSeconds()).isEqualTo(6);
		}

		@Test
		@DisplayName("heartbeat 키 값은 '1'이다")
		void it_sets_heartbeat_value_to_one() {
			// Given
			String t = "20260726100000";
			Long memberId = 1L;
			String expectedKey = MultigameRedisKeyProvider.heartbeat(t, memberId);

			given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);

			// When
			waitingRoomService.updateHeartbeat(t, memberId);

			// Then
			verify(valueOperations).set(eq(expectedKey), eq("1"), eq(Duration.ofSeconds(6)));
		}
	}

	@Nested
	@DisplayName("countParticipants 메서드는")
	class Describe_countParticipants {

		@Test
		@DisplayName("heartbeat 키 개수를 반환한다")
		void it_returns_heartbeat_key_count() {
			// Given
			String t = "20260726100000";
			Cursor<String> cursor = cursorOf(
					MultigameRedisKeyProvider.heartbeat(t, 1L),
					MultigameRedisKeyProvider.heartbeat(t, 2L),
					MultigameRedisKeyProvider.heartbeat(t, 3L)
			);
			doReturn(cursor).when(stringRedisTemplate).scan(any(ScanOptions.class));

			// When
			int count = waitingRoomService.countParticipants(t);

			// Then
			assertThat(count).isEqualTo(3);
		}

		@Test
		@DisplayName("heartbeat 키가 없으면 0을 반환한다")
		void it_returns_zero_when_no_keys() {
			// Given
			String t = "20260726100000";
			doReturn(cursorOf()).when(stringRedisTemplate).scan(any(ScanOptions.class));

			// When
			int count = waitingRoomService.countParticipants(t);

			// Then
			assertThat(count).isEqualTo(0);
		}

		@Test
		@DisplayName("scan 예외 발생 시 0을 반환한다")
		void it_returns_zero_when_scan_throws_exception() {
			// Given
			String t = "20260726100000";
			given(stringRedisTemplate.scan(any(ScanOptions.class))).willThrow(new RuntimeException("Redis error"));

			// When
			int count = waitingRoomService.countParticipants(t);

			// Then
			assertThat(count).isEqualTo(0);
		}

		@Test
		@DisplayName("100명의 참여자를 정확히 카운트한다")
		void it_counts_100_participants_correctly() {
			// Given
			String t = "20260726100000";
			String[] keys = new String[100];
			for (int i = 0; i < 100; i++) {
				keys[i] = MultigameRedisKeyProvider.heartbeat(t, (long) (i + 1));
			}
			doReturn(cursorOf(keys)).when(stringRedisTemplate).scan(any(ScanOptions.class));

			// When
			int count = waitingRoomService.countParticipants(t);

			// Then
			assertThat(count).isEqualTo(100);
		}
	}
}
