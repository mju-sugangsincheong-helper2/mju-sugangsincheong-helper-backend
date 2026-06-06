package com.mjusugangsincheonghelper.auth.authentication.guest;

import com.mjusugangsincheonghelper.auth.authentication.identity.AuthenticatedIdentity;
import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.entity.Member.Role;
import com.mjusugangsincheonghelper.database.entity.MemberAgreement;
import com.mjusugangsincheonghelper.database.entity.MemberAuth;
import com.mjusugangsincheonghelper.database.entity.MemberAuth.AuthType;
import com.mjusugangsincheonghelper.database.repository.MemberAgreementRepository;
import com.mjusugangsincheonghelper.database.repository.MemberAuthRepository;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GuestAuthenticationProvider {

	private final MemberRepository memberRepository;
	private final MemberAuthRepository memberAuthRepository;
	private final MemberAgreementRepository memberAgreementRepository;

	@Transactional
	public AuthenticatedIdentity authenticate() {
		String guestKey = UUID.randomUUID().toString();
		String guestName = "게스트_" + guestKey.substring(0, 4);

		Member member = Member.builder()
				.role(Role.GUEST)
				.name(guestName)
				.build();
		member = memberRepository.save(member);

		MemberAgreement agreement = new MemberAgreement(member.getId());
		memberAgreementRepository.save(agreement);

		MemberAuth memberAuth = MemberAuth.builder()
				.memberId(member.getId())
				.authType(AuthType.GUEST_KEY)
				.authKey(guestKey)
				.build();
		memberAuthRepository.save(memberAuth);

		return AuthenticatedIdentity.builder()
				.memberId(member.getId())
				.build();
	}
}
