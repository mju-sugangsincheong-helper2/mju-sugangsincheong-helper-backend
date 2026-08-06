package com.mjusugangsincheonghelper.auth.merge;

import com.mjusugangsincheonghelper.account.service.AccountAgreementMergeService;
import com.mjusugangsincheonghelper.auth.common.AuthenticatedIdentity;
import com.mjusugangsincheonghelper.auth.session.device.DeviceSessionService;
import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.entity.MemberAuth.AuthType;
import com.mjusugangsincheonghelper.database.repository.MemberAuthRepository;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.multigame.service.MultigameDataMergeService;
import com.mjusugangsincheonghelper.singlegame.service.SingleGameDataMergeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MergeService {

	private final MemberRepository memberRepository;
	private final MemberAuthRepository memberAuthRepository;
	private final SingleGameDataMergeService singleGameDataMergeService;
	private final MultigameDataMergeService multigameDataMergeService;
	private final AccountAgreementMergeService accountAgreementMergeService;
	private final DeviceSessionService deviceSessionService;
	private final MergeTicketService mergeTicketService;

	@Transactional
	public AuthenticatedIdentity merge(String mergeTicket) {
		MergeTicketService.MergeTicketClaims claims = mergeTicketService.consume(mergeTicket);

		Long guestId = claims.guestMemberId();
		Long targetId = claims.targetMemberId();

		if (guestId.equals(targetId)) {
			throw new BaseException(ErrorCode.AUTH_MERGE_TICKET_EXPIRED);
		}

		Member guestMember = memberRepository.findById(guestId)
				.orElseThrow(() -> new BaseException(ErrorCode.AUTH_GUEST_NOT_FOUND));

		memberRepository.findById(targetId)
				.orElseThrow(() -> new BaseException(ErrorCode.AUTH_MEMBER_NOT_FOUND));

		singleGameDataMergeService.transferGuestSingleGameRecordsToMember(guestId, targetId);
		multigameDataMergeService.transferGuestMultigameRecordsToMember(guestId, targetId);
		accountAgreementMergeService.transferGuestAgreementStatusToMember(guestId, targetId);

		memberAuthRepository.findByMemberIdAndAuthType(guestId, AuthType.GUEST_KEY)
				.ifPresent(memberAuthRepository::delete);

		deviceSessionService.switchMember(guestId, targetId);

		memberRepository.delete(guestMember);

		log.info("Merged guest account to target member. guestId={}, targetId={}", guestId, targetId);

		return AuthenticatedIdentity.builder()
				.memberId(targetId)
				.build();
	}
}
