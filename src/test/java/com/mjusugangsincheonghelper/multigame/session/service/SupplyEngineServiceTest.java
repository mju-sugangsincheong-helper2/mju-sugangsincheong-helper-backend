package com.mjusugangsincheonghelper.multigame.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.mjusugangsincheonghelper.multigame.common.MultigameRedisKeyProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

@ExtendWith(MockitoExtension.class)
@DisplayName("SupplyEngineService 테스트")
class SupplyEngineServiceTest {

	@Mock
	private StringRedisTemplate stringRedisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	@Mock
	private ZSetOperations<String, String> zSetOperations;

	@InjectMocks
	private SupplyEngineService supplyEngineService;

	@Nested
	@DisplayName("execute 메서드는")
	class Describe_execute {

		@Test
		@DisplayName("초기 limit을 전체 참여자의 20%로 설정한다 (최소 1명)")
		void it_sets_initial_limit_to_20_percent() {
			String t = "20260630120000";
			int totalParticipants = 100;
			String limitKey = MultigameRedisKeyProvider.admissionLimit(t);
			String queueKey = MultigameRedisKeyProvider.queue(t);

			given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
			given(stringRedisTemplate.opsForZSet()).willReturn(zSetOperations);
			given(zSetOperations.size(queueKey)).willReturn(0L);

			supplyEngineService.execute(t, totalParticipants);

			ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
			verify(valueOperations, atLeastOnce()).set(eq(limitKey), captor.capture());

			assertThat(captor.getAllValues().get(0)).isEqualTo("20");
		}

		@Test
		@DisplayName("참여자가 2명일 때 최소 1명이 즉시 입장한다")
		void it_ensures_at_least_one_entry_for_small_group() {
			String t = "20260630120000";
			int totalParticipants = 2;
			String limitKey = MultigameRedisKeyProvider.admissionLimit(t);
			String queueKey = MultigameRedisKeyProvider.queue(t);

			given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
			given(stringRedisTemplate.opsForZSet()).willReturn(zSetOperations);
			given(zSetOperations.size(queueKey)).willReturn(0L);

			supplyEngineService.execute(t, totalParticipants);

			ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
			verify(valueOperations, atLeastOnce()).set(eq(limitKey), captor.capture());

			assertThat(captor.getAllValues().get(0)).isEqualTo("1");
		}

		@Test
		@DisplayName("대기자가 없으면 supply를 0으로 유지한다")
		void it_keeps_supply_zero_when_no_waiters() {
			String t = "20260630120000";
			int totalParticipants = 100;
			String limitKey = MultigameRedisKeyProvider.admissionLimit(t);
			String queueKey = MultigameRedisKeyProvider.queue(t);

			given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
			given(stringRedisTemplate.opsForZSet()).willReturn(zSetOperations);
			given(zSetOperations.size(queueKey)).willReturn(0L);

			supplyEngineService.execute(t, totalParticipants);

			ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
			verify(valueOperations, atLeastOnce()).set(eq(limitKey), captor.capture());

			for (String value : captor.getAllValues()) {
				assertThat(value).isEqualTo("20");
			}
		}

		@Test
		@DisplayName("임계 구간(잔여 4초 이하)에서 공급량을 증가시킨다")
		void it_increases_supply_in_critical_zone() {
			String t = "20260630120000";
			int totalParticipants = 100;
			String limitKey = MultigameRedisKeyProvider.admissionLimit(t);
			String queueKey = MultigameRedisKeyProvider.queue(t);

			given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
			given(stringRedisTemplate.opsForZSet()).willReturn(zSetOperations);
			given(zSetOperations.size(queueKey)).willReturn(80L);

			supplyEngineService.execute(t, totalParticipants);

			ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
			verify(valueOperations, atLeastOnce()).set(eq(limitKey), captor.capture());

			assertThat(captor.getAllValues().get(0)).isEqualTo("20");
			assertThat(captor.getAllValues().get(19)).isNotNull();
		}

		private String eq(String value) {
			return org.mockito.ArgumentMatchers.eq(value);
		}
	}
}
