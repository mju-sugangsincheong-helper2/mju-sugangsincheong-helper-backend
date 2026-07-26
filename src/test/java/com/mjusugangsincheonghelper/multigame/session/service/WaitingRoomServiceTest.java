package com.mjusugangsincheonghelper.multigame.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.mjusugangsincheonghelper.multigame.session.domain.HeartbeatLedger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("WaitingRoomService 테스트")
class WaitingRoomServiceTest {

	@Mock
	private HeartbeatLedger heartbeatLedger;

	@InjectMocks
	private WaitingRoomService waitingRoomService;

	@Nested
	@DisplayName("updateHeartbeat 메서드는")
	class Describe_updateHeartbeat {

		@Test
		@DisplayName("heartbeatLedger의 updateHeartbeat를 호출한다")
		void it_calls_heartbeatLedger_updateHeartbeat() {
			// Given
			String t = "20260726100000";
			Long memberId = 1L;

			// When
			waitingRoomService.updateHeartbeat(t, memberId);

			// Then
			verify(heartbeatLedger).updateHeartbeat(t, memberId);
		}
	}

	@Nested
	@DisplayName("countParticipants 메서드는")
	class Describe_countParticipants {

		@Test
		@DisplayName("heartbeatLedger의 countActiveHeartbeats를 호출하여 인원수를 반환한다")
		void it_returns_active_heartbeats_count() {
			// Given
			String t = "20260726100000";
			given(heartbeatLedger.countActiveHeartbeats(t)).willReturn(5);

			// When
			int count = waitingRoomService.countParticipants(t);

			// Then
			assertThat(count).isEqualTo(5);
			verify(heartbeatLedger).countActiveHeartbeats(t);
		}
	}
}
