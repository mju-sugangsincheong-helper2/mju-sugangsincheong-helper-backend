package com.mjusugangsincheonghelper.multigame.service;

import com.mjusugangsincheonghelper.database.repository.MultigameRoundMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MultigameDataMergeService {

	private final MultigameRoundMemberRepository multigameRoundMemberRepository;

	@Transactional
	public void transferGuestMultigameRecordsToMember(Long guestMemberId, Long targetMemberId) {
		multigameRoundMemberRepository.deleteConflicting(guestMemberId, targetMemberId);
		multigameRoundMemberRepository.updateMemberId(guestMemberId, targetMemberId);
	}
}
