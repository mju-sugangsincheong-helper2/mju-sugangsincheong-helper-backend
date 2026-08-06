package com.mjusugangsincheonghelper.singlegame.service;

import com.mjusugangsincheonghelper.database.repository.SingleGameRepository;
import com.mjusugangsincheonghelper.global.config.CacheProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SingleGameDataMergeService {

	private final SingleGameRepository singleGameRepository;
	private final CacheManager cacheManager;

	@Transactional
	public void transferGuestSingleGameRecordsToMember(Long guestMemberId, Long targetMemberId) {
		singleGameRepository.updateMemberId(guestMemberId, targetMemberId);
		evictSingleGameRecordCacheForMember(targetMemberId);
	}

	public void evictSingleGameRecordCacheForMember(Long memberId) {
		if (memberId == null) {
			return;
		}
		try {
			Cache cache = cacheManager.getCache(CacheProperties.SINGLEGAME_RECORDS);
			if (cache != null) {
				String cacheKey = memberId + ":page:0:size:10:cache";
				cache.evict(cacheKey);
				log.info("Evicted single game records cache for memberId: {}, key: {}", memberId, cacheKey);
			}
		} catch (Exception e) {
			log.warn("Failed to evict single game records cache for memberId: {}", memberId, e);
		}
	}
}
