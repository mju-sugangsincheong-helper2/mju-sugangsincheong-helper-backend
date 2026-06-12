package com.mjusugangsincheonghelper.exchange.service;

import com.mjusugangsincheonghelper.database.repository.ExchangeIntentRepository;
import com.mjusugangsincheonghelper.exchange.dto.cache.RecentIntentDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExchangePageCacheService {

	private final ExchangeIntentRepository intentRepository;

	@Cacheable(value = "recent-intents-page", key = "#term + ':recent_intents:lastId:' + #lastIntentId + ':limit:' + #limit + ':cache'", sync = true)
	public List<RecentIntentDto> getRecentIntentsPage(String term, Long lastIntentId, int limit) {
		PageRequest pageable = PageRequest.of(0, limit);
		return intentRepository.findByTermAndIdGreaterThanOrderByIdAsc(term, lastIntentId, pageable).stream()
				.map(RecentIntentDto::from)
				.toList();
	}
}
