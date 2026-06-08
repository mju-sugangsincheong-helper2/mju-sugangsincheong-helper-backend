package com.mjusugangsincheonghelper.member.service;

import com.mjusugangsincheonghelper.member.dto.MemberMeResponse;
import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.entity.MemberDevice;
import com.mjusugangsincheonghelper.database.repository.MemberAgreementRepository;
import com.mjusugangsincheonghelper.database.repository.MemberAuthRepository;
import com.mjusugangsincheonghelper.database.repository.MemberDeviceRepository;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

	private final MemberRepository memberRepository;
	private final MemberAgreementRepository memberAgreementRepository;
	private final MemberAuthRepository memberAuthRepository;
	private final MemberDeviceRepository memberDeviceRepository;

	public MemberMeResponse getMe(Long memberId) {
		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new BaseException(ErrorCode.AUTH_MEMBER_NOT_FOUND));
		boolean privacyPolicyAgreed = memberAgreementRepository.findById(memberId)
				.map(agreement -> agreement.isStatus())
				.orElse(false);
		return MemberMeResponse.from(member, privacyPolicyAgreed);
	}

	@Transactional
	public void withdraw(Long memberId) {
		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new BaseException(ErrorCode.AUTH_MEMBER_NOT_FOUND));

		memberAgreementRepository.deleteById(memberId);

		
		List<MemberDevice> devices = memberDeviceRepository.findByMemberId(memberId);
		memberDeviceRepository.deleteAllInBatch(devices);

		memberAuthRepository.findByMemberId(memberId)
				.ifPresent(memberAuthRepository::delete);

		memberRepository.delete(member);
	}
}
