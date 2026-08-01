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
	void neverOpensMoreAttemptSlotsThanSixTimesTheEnteredParticipants() {
		// P=23, 상한 6×23=138. supply = ceil(80/4) = 20 → 20+20=40
		assertThat(SupplyCalculator.nextLimit(20, 23, 80, 20)).isEqualTo(40);
		// 상한(138)에 도달하면 더 이상 올리지 않는다
		assertThat(SupplyCalculator.nextLimit(130, 23, 80, 20)).isEqualTo(138);
	}

	@Test
	void drainsTheQueueAcrossTheRemainingCriticalSeconds() {
		assertThat(SupplyCalculator.nextLimit(80, 100, 15, 3)).isEqualTo(85);
	}
}
