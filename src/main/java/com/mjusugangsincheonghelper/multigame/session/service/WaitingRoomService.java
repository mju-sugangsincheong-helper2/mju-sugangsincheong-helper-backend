package com.mjusugangsincheonghelper.multigame.session.service;

import com.mjusugangsincheonghelper.multigame.session.domain.HeartbeatLedger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaitingRoomService {

	private final HeartbeatLedger heartbeatLedger;

	public void updateHeartbeat(String t, Long memberId) {
		heartbeatLedger.updateHeartbeat(t, memberId);
	}

	public int countParticipants(String t) {
		return heartbeatLedger.countActiveHeartbeats(t);
	}
}
