package com.mjusugangsincheonghelper.auth.merge;

import com.mjusugangsincheonghelper.auth.common.AuthenticatedIdentity;
import com.mjusugangsincheonghelper.auth.session.device.DeviceSessionService;
import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.entity.MemberAuth.AuthType;
import com.mjusugangsincheonghelper.database.repository.MemberAuthRepository;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import com.mjusugangsincheonghelper.database.repository.MultigameResultDetailRepository;
import com.mjusugangsincheonghelper.database.repository.MultigameReservationRepository;
import com.mjusugangsincheonghelper.database.repository.SingleGameRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MergeService {

	private final MemberRepository memberRepository;
	private final MemberAuthRepository memberAuthRepository;
	private final SingleGameRepository singleGameRepository;
	private final MultigameResultDetailRepository multigameResultDetailRepository;
	private final MultigameReservationRepository multigameReservationRepository;
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

		singleGameRepository.updateMemberId(guestId, targetId);
		multigameResultDetailRepository.deleteConflicting(guestId, targetId);
		multigameResultDetailRepository.updateMemberId(guestId, targetId);
		multigameReservationRepository.deleteConflicting(guestId, targetId);
		multigameReservationRepository.updateMemberId(guestId, targetId);

		memberAuthRepository.findByMemberIdAndAuthType(guestId, AuthType.GUEST_KEY)
				.ifPresent(memberAuthRepository::delete);

		deviceSessionService.switchMember(guestId, targetId);

		memberRepository.delete(guestMember);

		return AuthenticatedIdentity.builder()
				.memberId(targetId)
				.build();
	}
}
