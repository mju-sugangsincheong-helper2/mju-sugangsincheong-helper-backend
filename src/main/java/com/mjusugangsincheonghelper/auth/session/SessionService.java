package com.mjusugangsincheonghelper.auth.session;

import com.mjusugangsincheonghelper.account.service.AccountAgreementService;
import com.mjusugangsincheonghelper.auth.common.AuthenticatedIdentity;
import com.mjusugangsincheonghelper.auth.common.dto.DeviceInfo;
import com.mjusugangsincheonghelper.auth.session.delivery.TokenDeliveryStrategy;
import com.mjusugangsincheonghelper.auth.session.device.DeviceSessionService;
import com.mjusugangsincheonghelper.auth.session.token.TokenProvider;
import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.entity.MemberDevice;
import com.mjusugangsincheonghelper.database.repository.MemberDeviceRepository;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionService {

	private final TokenProvider tokenProvider;
	private final TokenDeliveryStrategy tokenDeliveryStrategy;
	private final DeviceSessionService deviceSessionService;
	private final MemberRepository memberRepository;
	private final MemberDeviceRepository memberDeviceRepository;
	private final AccountAgreementService accountAgreementService;

	@Transactional
	public SessionResult createSession(AuthenticatedIdentity identity, DeviceInfo device,
			HttpServletResponse response) {
		Member member = memberRepository.findById(identity.getMemberId())
				.orElseThrow(() -> new BaseException(ErrorCode.AUTH_MEMBER_NOT_FOUND));

		boolean privacyAgreed = accountAgreementService.isAgreed(member.getId());
		String accessToken = tokenProvider.createAccessToken(member.getId(), member.getRole().name(), privacyAgreed);
		String refreshToken = tokenProvider.createRefreshToken();

		deviceSessionService.upsert(member.getId(), refreshToken, device,
				tokenProvider.getRefreshTokenExpiryMs());

		tokenDeliveryStrategy.deliver(accessToken, refreshToken, response);

		return SessionResult.builder()
				.accessToken(accessToken)
				.refreshToken(refreshToken)
				.memberId(member.getId())
				.role(member.getRole().name())
				.name(member.getName())
				.position(member.getPosition())
				.department(member.getDepartment())
				.build();
	}

	@Transactional
	public SessionResult refreshSession(String refreshToken, HttpServletResponse response) {
		MemberDevice device = memberDeviceRepository.findByRefreshToken(refreshToken)
				.orElseThrow(() -> new BaseException(ErrorCode.AUTH_INVALID_REFRESH_TOKEN));

		if (device.getExpiresAt() != null && device.getExpiresAt().isBefore(Instant.now())) {
			memberDeviceRepository.delete(device);
			throw new BaseException(ErrorCode.AUTH_INVALID_REFRESH_TOKEN);
		}

		Member member = memberRepository.findById(device.getMemberId())
				.orElseThrow(() -> new BaseException(ErrorCode.AUTH_MEMBER_NOT_FOUND));

		String newRefreshToken = tokenProvider.createRefreshToken();
		device.updateRefreshToken(newRefreshToken);

		boolean privacyAgreed = accountAgreementService.isAgreed(member.getId());
		String newAccessToken = tokenProvider.createAccessToken(member.getId(), member.getRole().name(), privacyAgreed);

		tokenDeliveryStrategy.deliver(newAccessToken, newRefreshToken, response);

		return SessionResult.builder()
				.accessToken(newAccessToken)
				.refreshToken(newRefreshToken)
				.memberId(member.getId())
				.role(member.getRole().name())
				.name(member.getName())
				.position(member.getPosition())
				.department(member.getDepartment())
				.build();
	}

	@Transactional
	public void reissueToken(Long memberId, String refreshToken, HttpServletResponse response) {
		MemberDevice device = memberDeviceRepository.findByRefreshToken(refreshToken)
				.orElseThrow(() -> new BaseException(ErrorCode.AUTH_INVALID_REFRESH_TOKEN));

		if (device.getExpiresAt() != null && device.getExpiresAt().isBefore(Instant.now())) {
			memberDeviceRepository.delete(device);
			throw new BaseException(ErrorCode.AUTH_INVALID_REFRESH_TOKEN);
		}

		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new BaseException(ErrorCode.AUTH_MEMBER_NOT_FOUND));

		String newRefreshToken = tokenProvider.createRefreshToken();
		device.updateRefreshToken(newRefreshToken);

		String newAccessToken = tokenProvider.createAccessToken(member.getId(), member.getRole().name(), true);

		tokenDeliveryStrategy.deliver(newAccessToken, newRefreshToken, response);
	}

	@Transactional
	public void destroySession(String refreshToken, Long memberId, HttpServletResponse response) {
		deviceSessionService.deleteByRefreshToken(refreshToken);
		tokenDeliveryStrategy.clear(response);
	}
}
