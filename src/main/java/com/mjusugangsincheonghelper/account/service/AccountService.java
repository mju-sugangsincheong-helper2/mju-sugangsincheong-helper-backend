package com.mjusugangsincheonghelper.account.service;

import com.mjusugangsincheonghelper.account.dto.AccountDeviceResponse;
import com.mjusugangsincheonghelper.account.dto.AccountMeResponse;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

	private final MemberRepository memberRepository;
	private final MemberAgreementRepository memberAgreementRepository;
	private final MemberAuthRepository memberAuthRepository;
	private final MemberDeviceRepository memberDeviceRepository;

	public AccountMeResponse getMe(Long memberId) {
		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new BaseException(ErrorCode.AUTH_MEMBER_NOT_FOUND));
		boolean privacyPolicyAgreed = memberAgreementRepository.findById(memberId)
				.map(agreement -> agreement.isStatus())
				.orElse(false);
		return AccountMeResponse.from(member, privacyPolicyAgreed);
	}

	public List<AccountDeviceResponse> getDevices(Long memberId, String currentRefreshToken) {
		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new BaseException(ErrorCode.AUTH_MEMBER_NOT_FOUND));

		List<MemberDevice> devices = memberDeviceRepository.findByMemberId(member.getId());
		return devices.stream()
				.map(device -> AccountDeviceResponse.from(device, currentRefreshToken))
				.toList();
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
		log.info("Withdrew account. memberId={}, role={}", memberId, member.getRole());
	}
}
