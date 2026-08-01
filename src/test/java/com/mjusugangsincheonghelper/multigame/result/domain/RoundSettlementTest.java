package com.mjusugangsincheonghelper.multigame.result.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RoundSettlementTest {

	@Test
	void keepsSuccessAsTheFinalMemberResultAndNormalizesUnprocessedAttempts() {
		RoundSettlement settlement = RoundSettlement.from("20260801120000", 2, 1, List.of(
				"1:ENQUEUED:1:1:1:0", "1:SUCCESS:1:2:1:1", "2:ENQUEUED:2:3:2:0"));

		assertThat(settlement.finalStatus(settlement.finalMembers().get(new RoundSettlement.MemberSubject(1L, 1))))
				.isEqualTo("SUCCESS");
		assertThat(settlement.finalStatus(settlement.finalMembers().get(new RoundSettlement.MemberSubject(2L, 2))))
				.isEqualTo("FAIL_SOLDOUT");
	}

	@Test
	void allowsOneSuccessPerSubjectForTheSameMember() {
		RoundSettlement settlement = RoundSettlement.from("20260801120000", 2, 1, List.of(
				"1:SUCCESS:1:1:1:1", "1:SUCCESS:2:2:2:1", "1:FAIL_SOLDOUT:3:3:3:2"));

		assertThat(settlement.finalMembers()).containsOnlyKeys(
				new RoundSettlement.MemberSubject(1L, 1),
				new RoundSettlement.MemberSubject(1L, 2),
				new RoundSettlement.MemberSubject(1L, 3));
		assertThat(settlement.finalStatus(settlement.finalMembers().get(new RoundSettlement.MemberSubject(1L, 1))))
				.isEqualTo("SUCCESS");
		assertThat(settlement.finalStatus(settlement.finalMembers().get(new RoundSettlement.MemberSubject(1L, 2))))
				.isEqualTo("SUCCESS");
		assertThat(settlement.finalStatus(settlement.finalMembers().get(new RoundSettlement.MemberSubject(1L, 3))))
				.isEqualTo("FAIL_SOLDOUT");
	}
}
