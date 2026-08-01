package com.mjusugangsincheonghelper.multigame.result.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RoundSettlementTest {

	@Test
	void keepsSuccessAsTheFinalMemberResultAndNormalizesUnprocessedAttempts() {
		RoundSettlement settlement = RoundSettlement.from("20260801120000", 2, 1, List.of(
				"1:ENQUEUED:1:1:1:0", "1:SUCCESS:1:2:1:1", "2:ENQUEUED:2:3:2:0"));

		assertThat(settlement.finalStatus(settlement.finalMembers().get(1L))).isEqualTo("SUCCESS");
		assertThat(settlement.finalStatus(settlement.finalMembers().get(2L))).isEqualTo("FAIL_SOLDOUT");
	}
}
