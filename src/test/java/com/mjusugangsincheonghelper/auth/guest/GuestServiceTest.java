package com.mjusugangsincheonghelper.auth.guest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.mjusugangsincheonghelper.auth.common.AuthenticatedIdentity;
import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.entity.Member.Role;
import com.mjusugangsincheonghelper.database.entity.MemberAuth;
import com.mjusugangsincheonghelper.database.entity.MemberAuth.AuthType;
import com.mjusugangsincheonghelper.database.repository.MemberAuthRepository;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("GuestService 테스트")
class GuestServiceTest {

	@InjectMocks
	GuestService guestService;

	@Mock
	MemberRepository memberRepository;

	@Mock
	MemberAuthRepository memberAuthRepository;

	@Nested
	@DisplayName("authenticate 메서드는")
	class Describe_authenticate {

		@Test
		@DisplayName("게스트 계정을 생성하고 AuthenticatedIdentity를 반환한다")
		void it_creates_guest_and_returns_identity() {
			Member guestMember = Member.builder()
					.role(Role.GUEST)
					.name("게스트_abcd")
					.build();
			ReflectionTestUtils.setField(guestMember, "id", 1L);

			MemberAuth guestAuth = MemberAuth.builder()
					.memberId(1L)
					.authType(AuthType.GUEST_KEY)
					.authKey("guest-key-uuid")
					.build();

			given(memberRepository.save(any(Member.class))).willReturn(guestMember);
			given(memberAuthRepository.save(any(MemberAuth.class))).willReturn(guestAuth);

			AuthenticatedIdentity result = guestService.authenticate();

			assertThat(result.getMemberId()).isEqualTo(1L);
			verify(memberRepository).save(any(Member.class));
			verify(memberAuthRepository).save(any(MemberAuth.class));
		}
	}
}
