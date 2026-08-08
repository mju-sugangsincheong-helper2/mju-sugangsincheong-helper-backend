package com.mjusugangsincheonghelper.singlegame.service;

import static org.mockito.BDDMockito.then;

import com.mjusugangsincheonghelper.database.repository.SingleGameRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SingleGameDataMergeServiceTest {

	@Mock
	private SingleGameRepository singleGameRepository;

	@InjectMocks
	private SingleGameDataMergeService singleGameDataMergeService;

	@Test
	@DisplayName("게스트 싱글게임 데이터를 회원 계정으로 이관한다")
	void transferGuestSingleGameRecordsToMember_success() {
		// given
		Long guestId = 1L;
		Long targetId = 2L;

		// when
		singleGameDataMergeService.transferGuestSingleGameRecordsToMember(guestId, targetId);

		// then
		then(singleGameRepository).should().updateMemberId(guestId, targetId);
	}
}
