package com.mjusugangsincheonghelper.multigame.game.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SupplyCalculatorTest {

	@Test
	void opensTwentyPercentOfWaitingMembersInitially() {
		assertThat(SupplyCalculator.initialLimit(100)).isEqualTo(20);
		assertThat(SupplyCalculator.initialLimit(2)).isEqualTo(1);
	}

	@Test
	void neverOpensMoreSlotsThanEnteredParticipants() {
		assertThat(SupplyCalculator.nextLimit(20, 23, 80, 20)).isEqualTo(23);
	}

	@Test
	void drainsTheQueueAcrossTheRemainingCriticalSeconds() {
		assertThat(SupplyCalculator.nextLimit(80, 100, 15, 3)).isEqualTo(85);
	}
}
