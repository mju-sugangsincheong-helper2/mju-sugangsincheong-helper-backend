package com.mjusugangsincheonghelper.account.service;

import com.mjusugangsincheonghelper.database.entity.MemberAgreement;
import com.mjusugangsincheonghelper.database.repository.MemberAgreementRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountAgreementService {

	private final MemberAgreementRepository memberAgreementRepository;

	@Transactional(readOnly = true)
	public boolean isAgreed(Long memberId) {
		return memberAgreementRepository.findById(memberId)
				.map(MemberAgreement::isStatus)
				.orElse(false);
	}

	@Transactional
	public ConsentStatus agree(Long memberId) {
		MemberAgreement agreement = memberAgreementRepository.findById(memberId)
				.orElseGet(() -> new MemberAgreement(memberId));
		agreement.agree();
		memberAgreementRepository.save(agreement);
		return new ConsentStatus(agreement.isStatus(), toEpochMillis(agreement.getAgreedAt()));
	}

	private static Long toEpochMillis(Instant instant) {
		return instant == null ? null : instant.toEpochMilli();
	}

	public record ConsentStatus(boolean status, Long agreedAt) {
	}
}
