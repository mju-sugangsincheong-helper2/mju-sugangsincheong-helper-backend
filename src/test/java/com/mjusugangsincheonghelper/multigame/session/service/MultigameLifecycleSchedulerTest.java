package com.mjusugangsincheonghelper.multigame.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.mjusugangsincheonghelper.database.repository.MultigameReservationRepository;
import com.mjusugangsincheonghelper.global.config.AdvisoryLockService;
import com.mjusugangsincheonghelper.multigame.common.MultigameRedisKeyProvider;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@DisplayName("MultigameLifecycleScheduler 테스트")
class MultigameLifecycleSchedulerTest {

	@Mock
	private StringRedisTemplate stringRedisTemplate;

	@Mock
	private AdvisoryLockService advisoryLockService;

	@Mock
	private SupplyEngineService supplyEngineService;

	@Mock
	private MultigameFinalizeService finalizeService;

	@Mock
	private MultigameReservationRepository reservationRepository;

	@Mock
	private ValueOperations<String, String> valueOperations;

	@Mock
	private HashOperations<String, Object, Object> hashOperations;

	@SuppressWarnings("unchecked")
	private Cursor<String> cursorOf(String... keys) {
		Iterator<String> it = List.of(keys).iterator();
		Cursor<String> cursor = mock(Cursor.class);
		lenient().when(cursor.hasNext()).thenAnswer(invocation -> it.hasNext());
		lenient().when(cursor.next()).thenAnswer(invocation -> it.next());
		return cursor;
	}

	@Nested
	@DisplayName("initializeGame 메서드는")
	class Describe_initializeGame {

		@Test
		@DisplayName("참여자 10명일 때 capacity=5, initialLimit=2로 설정한다")
		void it_initializes_with_correct_capacity_and_limit() throws Exception {
			// Given
			String t = "20260726100000";
			int participantCount = 10;

			given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
			given(stringRedisTemplate.opsForHash()).willReturn(hashOperations);

			MultigameLifecycleScheduler scheduler = createScheduler();

			// When
			java.lang.reflect.Method method = MultigameLifecycleScheduler.class.getDeclaredMethod(
					"initializeGame", String.class, int.class);
			method.setAccessible(true);
			method.invoke(scheduler, t, participantCount);

			// Then
			verify(valueOperations).set(MultigameRedisKeyProvider.state(t), "WAITING");
			verify(valueOperations).set(MultigameRedisKeyProvider.seq(t), "0");
			verify(valueOperations).set(MultigameRedisKeyProvider.admissionLimit(t), "2");
			verify(hashOperations).putAll(eq(MultigameRedisKeyProvider.seats(t)), any());
		}

		@Test
		@DisplayName("참여자 1명일 때 capacity=1, initialLimit=1로 설정한다 (최소값)")
		void it_ensures_minimum_capacity_and_limit() throws Exception {
			// Given
			String t = "20260726100000";
			int participantCount = 1;

			given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
			given(stringRedisTemplate.opsForHash()).willReturn(hashOperations);

			MultigameLifecycleScheduler scheduler = createScheduler();

			// When
			java.lang.reflect.Method method = MultigameLifecycleScheduler.class.getDeclaredMethod(
					"initializeGame", String.class, int.class);
			method.setAccessible(true);
			method.invoke(scheduler, t, participantCount);

			// Then
			verify(valueOperations).set(MultigameRedisKeyProvider.admissionLimit(t), "1");
		}

		@Test
		@DisplayName("6개 과목에 대해 seats를 초기화한다")
		void it_initializes_seats_for_6_subjects() throws Exception {
			// Given
			String t = "20260726100000";
			int participantCount = 10;

			given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
			given(stringRedisTemplate.opsForHash()).willReturn(hashOperations);

			MultigameLifecycleScheduler scheduler = createScheduler();

			// When
			java.lang.reflect.Method method = MultigameLifecycleScheduler.class.getDeclaredMethod(
					"initializeGame", String.class, int.class);
			method.setAccessible(true);
			method.invoke(scheduler, t, participantCount);

			// Then
			@SuppressWarnings("unchecked")
			ArgumentCaptor<java.util.Map<String, String>> seatsCaptor = ArgumentCaptor.forClass(java.util.Map.class);
			verify(hashOperations).putAll(eq(MultigameRedisKeyProvider.seats(t)), seatsCaptor.capture());

			assertThat(seatsCaptor.getValue()).hasSize(6);
			assertThat(seatsCaptor.getValue()).containsKeys("1", "2", "3", "4", "5", "6");
		}
	}

	@Nested
	@DisplayName("countHeartbeats 메서드는")
	class Describe_countHeartbeats {

		@Test
		@DisplayName("heartbeat 키 개수를 반환한다")
		void it_returns_heartbeat_count() throws Exception {
			// Given
			String t = "20260726100000";
			Cursor<String> cursor = cursorOf(
					MultigameRedisKeyProvider.heartbeat(t, 1L),
					MultigameRedisKeyProvider.heartbeat(t, 2L),
					MultigameRedisKeyProvider.heartbeat(t, 3L)
			);
			doReturn(cursor).when(stringRedisTemplate).scan(any(ScanOptions.class));

			MultigameLifecycleScheduler scheduler = createScheduler();

			// When
			java.lang.reflect.Method method = MultigameLifecycleScheduler.class.getDeclaredMethod(
					"countHeartbeats", String.class);
			method.setAccessible(true);
			int result = (int) method.invoke(scheduler, t);

			// Then
			assertThat(result).isEqualTo(3);
		}

		@Test
		@DisplayName("heartbeat 키가 없으면 0을 반환한다")
		void it_returns_zero_when_no_heartbeats() throws Exception {
			// Given
			String t = "20260726100000";
			doReturn(cursorOf()).when(stringRedisTemplate).scan(any(ScanOptions.class));

			MultigameLifecycleScheduler scheduler = createScheduler();

			// When
			java.lang.reflect.Method method = MultigameLifecycleScheduler.class.getDeclaredMethod(
					"countHeartbeats", String.class);
			method.setAccessible(true);
			int result = (int) method.invoke(scheduler, t);

			// Then
			assertThat(result).isEqualTo(0);
		}

		@Test
		@DisplayName("scan 예외 발생 시 0을 반환한다")
		void it_returns_zero_when_scan_throws_exception() throws Exception {
			// Given
			String t = "20260726100000";
			given(stringRedisTemplate.scan(any(ScanOptions.class))).willThrow(new RuntimeException("Redis error"));

			MultigameLifecycleScheduler scheduler = createScheduler();

			// When
			java.lang.reflect.Method method = MultigameLifecycleScheduler.class.getDeclaredMethod(
					"countHeartbeats", String.class);
			method.setAccessible(true);
			int result = (int) method.invoke(scheduler, t);

			// Then
			assertThat(result).isEqualTo(0);
		}
	}

	private MultigameLifecycleScheduler createScheduler() {
		return new MultigameLifecycleScheduler(
				stringRedisTemplate,
				advisoryLockService,
				supplyEngineService,
				finalizeService,
				reservationRepository,
				null
		);
	}
}
