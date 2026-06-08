package com.mjusugangsincheonghelper.auth.guest;

import com.mjusugangsincheonghelper.auth.common.AuthenticatedIdentity;
import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.entity.Member.Role;
import com.mjusugangsincheonghelper.database.entity.MemberAuth;
import com.mjusugangsincheonghelper.database.entity.MemberAuth.AuthType;
import com.mjusugangsincheonghelper.database.repository.MemberAuthRepository;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuestService {

	private final MemberRepository memberRepository;
	private final MemberAuthRepository memberAuthRepository;

	@Transactional
	public AuthenticatedIdentity authenticate() {
		String guestKey = UUID.randomUUID().toString();
		String guestName = "게스트_" + guestKey.substring(0, 4);

		Member member = Member.builder()
				.role(Role.GUEST)
				.name(guestName)
				.build();
		member = memberRepository.save(member);

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
