package com.mjusugangsincheonghelper.auth.merge;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.mjusugangsincheonghelper.account.service.AccountAgreementMergeService;
import com.mjusugangsincheonghelper.auth.common.AuthenticatedIdentity;
import com.mjusugangsincheonghelper.auth.session.device.DeviceSessionService;
import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.entity.Member.Role;
import com.mjusugangsincheonghelper.database.entity.MemberAuth;
import com.mjusugangsincheonghelper.database.entity.MemberAuth.AuthType;
import com.mjusugangsincheonghelper.database.repository.MemberAuthRepository;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import com.mjusugangsincheonghelper.multigame.service.MultigameDataMergeService;
import com.mjusugangsincheonghelper.singlegame.service.SingleGameDataMergeService;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MergeServiceTest {

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private MemberAuthRepository memberAuthRepository;

	@Mock
	private SingleGameDataMergeService singleGameDataMergeService;

	@Mock
	private MultigameDataMergeService multigameDataMergeService;

	@Mock
	private AccountAgreementMergeService accountAgreementMergeService;

	@Mock
	private DeviceSessionService deviceSessionService;

	@Mock
	private MergeTicketService mergeTicketService;

	@InjectMocks
	private MergeService mergeService;

	@Test
	@DisplayName("게스트 데이터를 구글 회원으로 이관할 때 도메인별 Merge 서비스가 순서대로 호출된다")
	void merge_delegatesToDomainMergeServices() {
		// given
		String ticket = "valid-merge-ticket";
		Long guestId = 10L;
		Long targetId = 20L;

		given(mergeTicketService.consume(ticket))
				.willReturn(new MergeTicketService.MergeTicketClaims(guestId, targetId));

		Member guestMember = Member.builder().role(Role.GUEST).build();
		Member targetMember = Member.builder().role(Role.MEMBER).build();

		given(memberRepository.findById(guestId)).willReturn(Optional.of(guestMember));
		given(memberRepository.findById(targetId)).willReturn(Optional.of(targetMember));

		MemberAuth guestAuth = MemberAuth.builder().memberId(guestId).authType(AuthType.GUEST_KEY).authKey("gk").build();
		given(memberAuthRepository.findByMemberIdAndAuthType(guestId, AuthType.GUEST_KEY))
				.willReturn(Optional.of(guestAuth));

		// when
		AuthenticatedIdentity identity = mergeService.merge(ticket);

		// then
		assertThat(identity.getMemberId()).isEqualTo(targetId);

		then(singleGameDataMergeService).should().transferGuestSingleGameRecordsToMember(guestId, targetId);
		then(multigameDataMergeService).should().transferGuestMultigameRecordsToMember(guestId, targetId);
		then(accountAgreementMergeService).should().transferGuestAgreementStatusToMember(guestId, targetId);

		then(memberAuthRepository).should().delete(guestAuth);
		then(deviceSessionService).should().switchMember(guestId, targetId);
		then(memberRepository).should().delete(guestMember);
	}
}
