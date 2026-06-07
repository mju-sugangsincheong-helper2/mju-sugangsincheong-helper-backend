package com.mjusugangsincheonghelper.auth.service;

import com.mjusugangsincheonghelper.auth.dto.MemberMeResponse;
import com.mjusugangsincheonghelper.database.repository.MemberAgreementRepository;
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
	private final MemberAgreementRepository memberAgreementRepository;

	public MemberMeResponse getMe(Long memberId) {
		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new BaseException(ErrorCode.AUTH_MEMBER_NOT_FOUND));
		boolean privacyPolicyAgreed = memberAgreementRepository.findById(memberId)
				.map(agreement -> agreement.isStatus())
				.orElse(false);
		return MemberMeResponse.from(member, privacyPolicyAgreed);
	}
}
