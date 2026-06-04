package com.mjusugangsincheonghelper.auth.infrastructure;

import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.entity.Member.Role;
import com.mjusugangsincheonghelper.database.entity.MemberAuth;
import com.mjusugangsincheonghelper.database.entity.MemberAuth.AuthType;
import com.mjusugangsincheonghelper.database.repository.MemberAuthRepository;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import java.util.Collections;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

	private static final String MJU_DOMAIN = "mju.ac.kr";

	private final MemberRepository memberRepository;
	private final MemberAuthRepository memberAuthRepository;
	private final OidcUserService delegate = new OidcUserService();

	@Override
	@Transactional
	public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
		OidcUser oidcUser = delegate.loadUser(userRequest);

		validateMjuDomain(oidcUser);

		String googleSubId = oidcUser.getSubject();
		ParsedName parsedName = parseName(oidcUser.getFullName());

		Member member = findOrCreateMember(googleSubId, parsedName);

		return new DefaultOidcUser(
				Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + member.getRole().name())),
				oidcUser.getIdToken(),
				oidcUser.getUserInfo()
		);
	}

	private void validateMjuDomain(OidcUser oidcUser) {
		String hostedDomain = oidcUser.getClaim("hd");
		if (hostedDomain == null || !MJU_DOMAIN.equals(hostedDomain)) {
			log.warn("Non-MJU domain attempted login: hd={}", hostedDomain);
			throw new BaseException(ErrorCode.AUTH_NOT_MJU_DOMAIN);
		}
	}

	private ParsedName parseName(String rawName) {
		if (rawName == null || rawName.isBlank()) {
			throw new BaseException(ErrorCode.AUTH_GOOGLE_AUTH_FAILED);
		}
		String[] parts = rawName.split("/");
		if (parts.length < 3) {
			log.warn("Invalid name format: expected 'name/position/department', got '{}'", rawName);
			throw new BaseException(ErrorCode.AUTH_GOOGLE_AUTH_FAILED);
		}
		String name = parts[0].trim();
		String position = parts[1].trim();
		String department = parts[2].trim();
		if (name.isBlank() || position.isBlank() || department.isBlank()) {
			throw new BaseException(ErrorCode.AUTH_GOOGLE_AUTH_FAILED);
		}
		return new ParsedName(name, position, department);
	}

	private Member findOrCreateMember(String googleSubId, ParsedName parsedName) {
		Optional<MemberAuth> existingAuth = memberAuthRepository.findByAuthKeyAndAuthType(googleSubId, AuthType.GOOGLE);

		if (existingAuth.isPresent()) {
			Member member = memberRepository.findById(existingAuth.get().getMemberId())
					.orElseThrow(() -> new BaseException(ErrorCode.AUTH_MEMBER_NOT_FOUND));
			existingAuth.get().updateLastLoginAt();
			return member;
		}

		Member newMember = Member.builder()
				.role(Role.MEMBER)
				.name(parsedName.name())
				.position(parsedName.position())
				.department(parsedName.department())
				.privacyPolicyAgreed(true)
				.build();
		newMember = memberRepository.save(newMember);

		MemberAuth memberAuth = MemberAuth.builder()
				.memberId(newMember.getId())
				.authType(AuthType.GOOGLE)
				.authKey(googleSubId)
				.build();
		memberAuthRepository.save(memberAuth);

		return newMember;
	}

	private record ParsedName(String name, String position, String department) {
	}
}
