package com.mjusugangsincheonghelper.auth.merge;

import com.mjusugangsincheonghelper.auth.common.AuthenticatedIdentity;
import com.mjusugangsincheonghelper.auth.session.device.DeviceSessionService;
import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.entity.MemberAuth;
import com.mjusugangsincheonghelper.database.entity.MemberAuth.AuthType;
import com.mjusugangsincheonghelper.database.repository.MemberAuthRepository;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
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
	private final DeviceSessionService deviceSessionService;
	private final MergeTicketService mergeTicketService;

	@Transactional
	public AuthenticatedIdentity merge(String mergeTicket) {
		MergeTicketService.MergeTicketClaims claims = mergeTicketService.consume(mergeTicket);

		MemberAuth googleAuth = memberAuthRepository.findByAuthKeyAndAuthType(claims.googleSubId(), AuthType.GOOGLE)
				.orElseThrow(() -> new BaseException(ErrorCode.AUTH_MEMBER_NOT_FOUND));

		Member targetMember = memberRepository.findById(googleAuth.getMemberId())
				.orElseThrow(() -> new BaseException(ErrorCode.AUTH_MEMBER_NOT_FOUND));

		Member guestMember = memberRepository.findById(claims.guestMemberId())
				.orElseThrow(() -> new BaseException(ErrorCode.AUTH_GUEST_NOT_FOUND));

		Long guestId = guestMember.getId();
		Long targetId = targetMember.getId();

		singleGameRepository.updateMemberId(guestId, targetId);

		memberAuthRepository.findByMemberIdAndAuthType(guestId, AuthType.GUEST_KEY)
				.ifPresent(memberAuthRepository::delete);

		deviceSessionService.switchMember(guestId, targetId);

		memberRepository.delete(guestMember);

		return AuthenticatedIdentity.builder()
				.memberId(targetId)
				.build();
	}
}
