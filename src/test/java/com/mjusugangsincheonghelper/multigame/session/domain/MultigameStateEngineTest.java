package com.mjusugangsincheonghelper.multigame.session.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
	private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

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

	@Test
	@DisplayName("tryExecuteWithLock은 TransactionTemplate 내부에서 Advisory Lock을 시도하고 action을 실행한다")
	void tryExecuteWithLock_executes_action_within_transaction() {
		org.mockito.BDDMockito.willAnswer(invocation -> {
			java.util.function.Consumer<org.springframework.transaction.TransactionStatus> action = invocation.getArgument(0);
			action.accept(null);
			return null;
		}).given(transactionTemplate).executeWithoutResult(any());

		given(advisoryLockService.tryXactLock("job", t)).willReturn(true);

		boolean[] ran = new boolean[]{false};
		stateEngine.tryExecuteWithLock("job", t, () -> ran[0] = true);

		assertThat(ran[0]).isTrue();
	}
}
