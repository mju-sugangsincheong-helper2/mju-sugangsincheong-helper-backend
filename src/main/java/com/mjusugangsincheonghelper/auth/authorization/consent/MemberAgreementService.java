package com.mjusugangsincheonghelper.auth.authorization.consent;

import com.mjusugangsincheonghelper.database.entity.MemberAgreement;
import com.mjusugangsincheonghelper.database.repository.MemberAgreementRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberAgreementService {

	private final MemberAgreementRepository memberAgreementRepository;

	public boolean isAgreed(Long memberId) {
		return memberAgreementRepository.findById(memberId)
				.map(MemberAgreement::isStatus)
				.orElse(false);
	}

	public ConsentStatus getStatus(Long memberId) {
		return memberAgreementRepository.findById(memberId)
				.map(agreement -> new ConsentStatus(agreement.isStatus(), agreement.getAgreedAt()))
				.orElse(new ConsentStatus(false, null));
	}

	@Transactional
	public void agree(Long memberId) {
		MemberAgreement agreement = memberAgreementRepository.findById(memberId)
				.orElseGet(() -> new MemberAgreement(memberId));
		agreement.agree();
		memberAgreementRepository.save(agreement);
	}

	public record ConsentStatus(boolean status, Instant agreedAt) {}
}
