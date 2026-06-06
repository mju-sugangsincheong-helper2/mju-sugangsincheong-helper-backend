package com.mjusugangsincheonghelper.auth.service;

import com.mjusugangsincheonghelper.auth.authorization.consent.MemberAgreementService;
import com.mjusugangsincheonghelper.auth.authorization.consent.MemberAgreementService.ConsentStatus;
import com.mjusugangsincheonghelper.auth.dto.MemberMeResponse;
import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

	private final MemberRepository memberRepository;
	private final MemberAgreementService memberAgreementService;

	public MemberMeResponse getMe(Long memberId) {
		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new BaseException(ErrorCode.AUTH_MEMBER_NOT_FOUND));
		ConsentStatus consentStatus = memberAgreementService.getStatus(memberId);
		return MemberMeResponse.from(member, consentStatus);
	}
}
