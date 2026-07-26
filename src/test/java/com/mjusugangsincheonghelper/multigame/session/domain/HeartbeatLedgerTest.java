package com.mjusugangsincheonghelper.multigame.session.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.mjusugangsincheonghelper.multigame.common.MultigameRedisKeyProvider;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@DisplayName("HeartbeatLedger 테스트")
class HeartbeatLedgerTest {

	@Mock
	private StringRedisTemplate stringRedisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	@InjectMocks
	private HeartbeatLedger heartbeatLedger;

	private final String t = "20260726100000";

	@Test
	@DisplayName("updateHeartbeat는 TTL 6초로 heartbeat 키를 저장한다")
	void updateHeartbeat() {
		given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);

		heartbeatLedger.updateHeartbeat(t, 1L);

		verify(valueOperations).set(eq(MultigameRedisKeyProvider.heartbeat(t, 1L)), eq("1"), eq(Duration.ofSeconds(6)));
	}

	@Test
	@DisplayName("saveParticipantSnapshot은 스냅샷 카운트를 저장한다")
	void saveParticipantSnapshot() {
		given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);

		heartbeatLedger.saveParticipantSnapshot(t, 10);

		verify(valueOperations).set(eq(MultigameRedisKeyProvider.participantCount(t)), eq("10"));
	}

	@Test
	@DisplayName("getParticipantSnapshot은 스냅샷 카운트를 조회한다")
	void getParticipantSnapshot() {
		given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
		given(valueOperations.get(MultigameRedisKeyProvider.participantCount(t))).willReturn("15");

		int count = heartbeatLedger.getParticipantSnapshot(t);

		assertThat(count).isEqualTo(15);
	}
}
