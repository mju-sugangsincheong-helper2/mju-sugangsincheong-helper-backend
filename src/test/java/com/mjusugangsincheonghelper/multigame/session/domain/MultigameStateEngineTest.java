package com.mjusugangsincheonghelper.multigame.session.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.mjusugangsincheonghelper.global.config.AdvisoryLockService;
import com.mjusugangsincheonghelper.multigame.common.MultigameRedisKeyProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@DisplayName("MultigameStateEngine 테스트")
class MultigameStateEngineTest {

	@Mock
	private StringRedisTemplate stringRedisTemplate;

	@Mock
	private AdvisoryLockService advisoryLockService;

	@Mock
	private ValueOperations<String, String> valueOperations;

	@InjectMocks
	private MultigameStateEngine stateEngine;

	private final String t = "20260726100000";

	@Test
	@DisplayName("getState는 Redis에서 GameState를 읽어온다")
	void getState() {
		given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
		given(valueOperations.get(MultigameRedisKeyProvider.state(t))).willReturn("WAITING");

		GameState state = stateEngine.getState(t);

		assertThat(state).isEqualTo(GameState.WAITING);
	}

	@Test
	@DisplayName("transitionTo는 유효한 전이 시 성공한다")
	void transitionTo_success() {
		given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
		given(valueOperations.get(MultigameRedisKeyProvider.state(t))).willReturn("WAITING");

		boolean result = stateEngine.transitionTo(t, GameState.READY);

		assertThat(result).isTrue();
		verify(valueOperations).set(MultigameRedisKeyProvider.state(t), "READY");
	}

	@Test
	@DisplayName("cancelGame은 상태를 CANCELLED로 세팅한다")
	void cancelGame() {
		given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);

		stateEngine.cancelGame(t);

		verify(valueOperations).set(MultigameRedisKeyProvider.state(t), "CANCELLED");
	}
}
