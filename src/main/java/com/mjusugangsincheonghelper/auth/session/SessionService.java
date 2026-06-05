package com.mjusugangsincheonghelper.auth.session;

import com.mjusugangsincheonghelper.auth.authentication.identity.AuthenticatedIdentity;
import com.mjusugangsincheonghelper.auth.dto.DeviceInfo;
import com.mjusugangsincheonghelper.auth.session.delivery.TokenDeliveryStrategy;
import com.mjusugangsincheonghelper.auth.session.device.DeviceSessionService;
import com.mjusugangsincheonghelper.auth.authentication.token.TokenProvider;
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
public class SessionService {

	private final TokenProvider tokenProvider;
	private final TokenDeliveryStrategy tokenDeliveryStrategy;
	private final DeviceSessionService deviceSessionService;
	private final MemberRepository memberRepository;
	private final MemberDeviceRepository memberDeviceRepository;

	@Transactional
	public SessionResult createSession(AuthenticatedIdentity identity, DeviceInfo device, String fcmToken,
			HttpServletResponse response) {
		Member member = memberRepository.findById(identity.getMemberId())
				.orElseThrow(() -> new BaseException(ErrorCode.AUTH_MEMBER_NOT_FOUND));

		String accessToken = tokenProvider.createAccessToken(member.getId(), member.getRole().name());
		String refreshToken = tokenProvider.createRefreshToken();

		deviceSessionService.upsert(member.getId(), refreshToken, fcmToken, device,
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

		String newAccessToken = tokenProvider.createAccessToken(member.getId(), member.getRole().name());

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
	public void destroySession(String refreshToken, String fcmToken, Long memberId, HttpServletResponse response) {
		deviceSessionService.deleteByFcmToken(memberId, fcmToken);
		tokenDeliveryStrategy.clear(response);
	}
}
