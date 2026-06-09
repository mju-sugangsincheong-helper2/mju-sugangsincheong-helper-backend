package com.mjusugangsincheonghelper.account;

import com.mjusugangsincheonghelper.account.dto.AccountMeResponse;
import com.mjusugangsincheonghelper.account.service.AccountService;
import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.entity.Member.Role;
import com.mjusugangsincheonghelper.database.entity.MemberAgreement;
import com.mjusugangsincheonghelper.database.entity.MemberAuth;
import com.mjusugangsincheonghelper.database.entity.MemberDevice;
import com.mjusugangsincheonghelper.database.repository.MemberAgreementRepository;
import com.mjusugangsincheonghelper.database.repository.MemberAuthRepository;
import com.mjusugangsincheonghelper.database.repository.MemberDeviceRepository;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService 단위 테스트")
class AccountServiceTest {

	@InjectMocks
	private AccountService accountService;

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private MemberAgreementRepository memberAgreementRepository;

	@Mock
	private MemberAuthRepository memberAuthRepository;

	@Mock
	private MemberDeviceRepository memberDeviceRepository;

	@Nested
	@DisplayName("getMe 메소드는")
	class Describe_getMe {

		@Test
		@DisplayName("존재하는 회원의 정보를 조회하면 해당 정보와 개인정보 수집 동의 여부를 반환한다")
		void it_returns_member_info_and_agreement_status() {
			// given
			Long memberId = 1L;
			Member member = Member.builder()
					.role(Role.MEMBER)
					.name("홍길동")
					.build();
			MemberAgreement agreement = MemberAgreement.agree(memberId);

			given(memberRepository.findById(memberId)).willReturn(Optional.of(member));
			given(memberAgreementRepository.findById(memberId)).willReturn(Optional.of(agreement));

			// when
			AccountMeResponse response = accountService.getMe(memberId);

			// then
			assertThat(response.getMemberId()).isEqualTo(member.getId());
			assertThat(response.getName()).isEqualTo("홍길동");
			assertThat(response.getRole()).isEqualTo("MEMBER");
			assertThat(response.isPrivacyPolicyAgreed()).isTrue();
		}

		@Test
		@DisplayName("존재하지 않는 회원의 정보를 조회하면 BaseException을 던진다")
		void it_throws_base_exception_when_member_not_found() {
			// given
			Long memberId = 999L;
			given(memberRepository.findById(memberId)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> accountService.getMe(memberId))
					.isInstanceOf(BaseException.class)
					.hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_MEMBER_NOT_FOUND);
		}
	}

	@Nested
	@DisplayName("withdraw 메소드는")
	class Describe_withdraw {

		@Test
		@DisplayName("존재하는 회원이 탈퇴를 요청하면 관련 데이터를 모두 삭제한다")
		void it_deletes_all_related_member_data() {
			// given
			Long memberId = 1L;
			Member member = Member.builder()
					.role(Role.MEMBER)
					.name("홍길동")
					.build();
			MemberDevice device = MemberDevice.builder()
					.memberId(memberId)
					.refreshToken("some_refresh_token")
					.build();
			MemberAuth memberAuth = MemberAuth.builder()
					.memberId(memberId)
					.authKey("sub_id")
					.build();

			given(memberRepository.findById(memberId)).willReturn(Optional.of(member));
			given(memberDeviceRepository.findByMemberId(memberId)).willReturn(List.of(device));
			given(memberAuthRepository.findByMemberId(memberId)).willReturn(Optional.of(memberAuth));

			// when
			accountService.withdraw(memberId);

			// then
			verify(memberAgreementRepository).deleteById(memberId);
			verify(memberDeviceRepository).deleteAllInBatch(anyList());
			verify(memberAuthRepository).delete(memberAuth);
			verify(memberRepository).delete(member);
		}

		@Test
		@DisplayName("존재하지 않는 회원이 탈퇴를 요청하면 BaseException을 던진다")
		void it_throws_base_exception_when_member_not_found_on_withdrawal() {
			// given
			Long memberId = 999L;
			given(memberRepository.findById(memberId)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> accountService.withdraw(memberId))
					.isInstanceOf(BaseException.class)
					.hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_MEMBER_NOT_FOUND);
		}
	}
}
