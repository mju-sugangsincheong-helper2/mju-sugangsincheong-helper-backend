package com.mjusugangsincheonghelper.account.service;

import com.mjusugangsincheonghelper.database.entity.MemberAgreement;
import com.mjusugangsincheonghelper.database.repository.MemberAgreementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountAgreementMergeService {

	private final MemberAgreementRepository memberAgreementRepository;

	@Transactional
	public void transferGuestAgreementStatusToMember(Long guestMemberId, Long targetMemberId) {
		memberAgreementRepository.findById(guestMemberId).ifPresent(guestAgreement -> {
			if (guestAgreement.isStatus()) {
				MemberAgreement targetAgreement = memberAgreementRepository.findById(targetMemberId)
						.orElseGet(() -> MemberAgreement.agree(targetMemberId));
				if (!targetAgreement.isStatus()) {
					targetAgreement.agree();
					memberAgreementRepository.save(targetAgreement);
				}
			}
			memberAgreementRepository.delete(guestAgreement);
		});
	}
}
