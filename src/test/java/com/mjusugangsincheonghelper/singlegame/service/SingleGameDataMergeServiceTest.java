package com.mjusugangsincheonghelper.singlegame.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.mjusugangsincheonghelper.database.repository.SingleGameRepository;
import com.mjusugangsincheonghelper.global.config.CacheProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

@ExtendWith(MockitoExtension.class)
class SingleGameDataMergeServiceTest {

	@Mock
	private SingleGameRepository singleGameRepository;

	@Mock
	private CacheManager cacheManager;

	@InjectMocks
	private SingleGameDataMergeService singleGameDataMergeService;

	@Test
	@DisplayName("게스트 싱글게임 데이터를 회원 계정으로 이관하고 record 캐시를 Evict 한다")
	void transferGuestSingleGameRecordsToMember_success() {
		// given
		Long guestId = 1L;
		Long targetId = 2L;
		Cache cache = mock(Cache.class);
		given(cacheManager.getCache(CacheProperties.SINGLEGAME_RECORDS)).willReturn(cache);

		// when
		singleGameDataMergeService.transferGuestSingleGameRecordsToMember(guestId, targetId);

		// then
		then(singleGameRepository).should().updateMemberId(guestId, targetId);
		then(cache).should().evict(targetId + ":page:0:size:10:cache");
	}
}
