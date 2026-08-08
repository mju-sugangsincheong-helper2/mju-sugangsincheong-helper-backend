package com.mjusugangsincheonghelper.singlegame.service;

import com.mjusugangsincheonghelper.database.repository.SingleGameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게스트 회원의 싱글게임 데이터를 인증 회원 계정으로 이관하는 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SingleGameDataMergeService {

	private final SingleGameRepository singleGameRepository;

	/**
	 * 게스트 회원의 모든 싱글게임 데이터를 대상 회원 계정으로 이관한다.
	 * 통계 캐시는 totalCourses 단위로 TTL에 의해 자동 갱신되므로 별도 evict 불필요.
	 */
	@Transactional
	public void transferGuestSingleGameRecordsToMember(Long guestMemberId, Long targetMemberId) {
		singleGameRepository.updateMemberId(guestMemberId, targetMemberId);
		log.debug("Transferred guest single game records. guestId={}, targetId={}", guestMemberId, targetMemberId);
	}
}
