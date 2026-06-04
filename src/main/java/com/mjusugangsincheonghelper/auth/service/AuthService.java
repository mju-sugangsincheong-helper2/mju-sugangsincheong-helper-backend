package com.mjusugangsincheonghelper.auth.service;

import com.mjusugangsincheonghelper.auth.dto.GuestCreateRequest;
import com.mjusugangsincheonghelper.auth.dto.GuestResponse;
import com.mjusugangsincheonghelper.auth.dto.LogoutRequest;
import com.mjusugangsincheonghelper.auth.dto.MemberMeResponse;
import com.mjusugangsincheonghelper.auth.dto.MergeRequest;
import com.mjusugangsincheonghelper.auth.dto.MergeResponse;
import com.mjusugangsincheonghelper.auth.dto.RefreshResponse;
import com.mjusugangsincheonghelper.auth.infrastructure.AuthCookieProvider;
import com.mjusugangsincheonghelper.auth.infrastructure.TokenProvider;
import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.entity.Member.Role;
import com.mjusugangsincheonghelper.database.entity.MemberAuth;
import com.mjusugangsincheonghelper.database.entity.MemberAuth.AuthType;
import com.mjusugangsincheonghelper.database.entity.MemberDevice;
import com.mjusugangsincheonghelper.database.repository.MemberAuthRepository;
import com.mjusugangsincheonghelper.database.repository.MemberDeviceRepository;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

	private final MemberRepository memberRepository;
	private final MemberAuthRepository memberAuthRepository;
	private final MemberDeviceRepository memberDeviceRepository;
	private final TokenProvider tokenProvider;
	private final AuthCookieProvider authCookieProvider;
	private final DeviceService deviceService;

	@Value("${app.jwt.refresh-token-expiry-ms}")
	private long refreshTokenExpiryMs;

	@Transactional
	public GuestResponse createGuest(GuestCreateRequest request, HttpServletResponse response) {
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

		String accessToken = tokenProvider.createAccessToken(member.getId(), Role.GUEST.name());
		String refreshToken = tokenProvider.createRefreshToken();

		deviceService.upsert(member.getId(), refreshToken, request.getFcmToken(), request.getDevice());

		response.addHeader("Set-Cookie", authCookieProvider.createAccessTokenCookie(accessToken, 3600000).toString());
		response.addHeader("Set-Cookie", authCookieProvider.createRefreshTokenCookie(refreshToken, refreshTokenExpiryMs).toString());

		return GuestResponse.builder()
				.memberId(member.getId())
				.role(Role.GUEST.name())
				.name(guestName)
				.build();
	}

	@Transactional
	public RefreshResponse refreshToken(String refreshTokenCookie, HttpServletResponse response) {
		MemberDevice device = memberDeviceRepository.findByRefreshToken(refreshTokenCookie)
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
		response.addHeader("Set-Cookie", authCookieProvider.createAccessTokenCookie(newAccessToken, 3600000).toString());
		response.addHeader("Set-Cookie", authCookieProvider.createRefreshTokenCookie(newRefreshToken, refreshTokenExpiryMs).toString());

		return RefreshResponse.builder()
				.status("success")
				.role(member.getRole().name())
				.build();
	}

	@Transactional
	public void logout(Long memberId, LogoutRequest request, HttpServletResponse response) {
		if (request.getFcmToken() != null) {
			memberDeviceRepository.deleteByMemberIdAndFcmToken(memberId, request.getFcmToken());
		}

		response.addHeader("Set-Cookie", authCookieProvider.clearAccessTokenCookie().toString());
		response.addHeader("Set-Cookie", authCookieProvider.clearRefreshTokenCookie().toString());
	}

	@Transactional
	public MergeResponse mergeGuestToMember(MergeRequest request, HttpServletResponse response) {
		Claims claims;
		try {
			claims = tokenProvider.parseMergeTicket(request.getMergeTicket());
		} catch (Exception e) {
			throw new BaseException(ErrorCode.AUTH_MERGE_TICKET_EXPIRED);
		}

		String googleSubId = claims.get("googleSubId", String.class);
		Long guestMemberId = Long.parseLong(claims.getSubject());

		MemberAuth googleAuth = memberAuthRepository.findByAuthKeyAndAuthType(googleSubId, AuthType.GOOGLE)
				.orElseThrow(() -> new BaseException(ErrorCode.AUTH_MEMBER_NOT_FOUND));

		Member targetMember = memberRepository.findById(googleAuth.getMemberId())
				.orElseThrow(() -> new BaseException(ErrorCode.AUTH_MEMBER_NOT_FOUND));

		Member guestMember = memberRepository.findById(guestMemberId)
				.orElseThrow(() -> new BaseException(ErrorCode.AUTH_GUEST_NOT_FOUND));

		Optional<MemberDevice> existingDevice = request.getFcmToken() != null
				? memberDeviceRepository.findByMemberIdAndFcmToken(targetMember.getId(), request.getFcmToken())
				: Optional.empty();

		if (existingDevice.isPresent()) {
			memberDeviceRepository.deleteByMemberIdAndFcmToken(guestMemberId, request.getFcmToken());
			String newRefreshToken = tokenProvider.createRefreshToken();
			existingDevice.get().updateAccessInfo(
					newRefreshToken, request.getFcmToken(),
					request.getDevice().getName(), request.getDevice().getVersion(),
					request.getDevice().getLayout(), request.getDevice().getPrerelease(),
					request.getDevice().getOs(), request.getDevice().getManufacturer(),
					request.getDevice().getProduct(), request.getDevice().getDescription(),
					request.getDevice().getUa()
			);
		} else {
			memberDeviceRepository.findByMemberId(guestMemberId).forEach(device -> {
				device.switchMember(targetMember.getId());
			});
		}

		memberRepository.delete(guestMember);

		String accessToken = tokenProvider.createAccessToken(targetMember.getId(), targetMember.getRole().name());
		String refreshToken = tokenProvider.createRefreshToken();

		deviceService.upsert(targetMember.getId(), refreshToken, request.getFcmToken(), request.getDevice());

		response.addHeader("Set-Cookie", authCookieProvider.createAccessTokenCookie(accessToken, 3600000).toString());
		response.addHeader("Set-Cookie", authCookieProvider.createRefreshTokenCookie(refreshToken, refreshTokenExpiryMs).toString());

		return MergeResponse.builder()
				.memberId(targetMember.getId())
				.role(targetMember.getRole().name())
				.name(targetMember.getName())
				.position(targetMember.getPosition())
				.department(targetMember.getDepartment())
				.build();
	}
}
