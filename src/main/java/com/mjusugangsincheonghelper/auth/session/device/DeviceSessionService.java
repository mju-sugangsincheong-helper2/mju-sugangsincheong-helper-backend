package com.mjusugangsincheonghelper.auth.session.device;

import com.mjusugangsincheonghelper.auth.common.dto.DeviceInfo;
import com.mjusugangsincheonghelper.auth.session.token.RefreshTokenHasher;
import com.mjusugangsincheonghelper.database.entity.MemberDevice;
import com.mjusugangsincheonghelper.database.repository.MemberDeviceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeviceSessionService {

	private final MemberDeviceRepository memberDeviceRepository;

	@Transactional
	public MemberDevice upsert(Long memberId, String refreshToken, DeviceInfo deviceInfo, long expiryMs) {
		final DeviceInfo info = deviceInfo != null ? deviceInfo : DeviceInfo.builder().build();
		final String refreshTokenHash = RefreshTokenHasher.hash(refreshToken);

		Optional<MemberDevice> existing = findExistingDevice(memberId, info);

		return existing
				.map(device -> {
					device.updateAccessInfo(
							refreshTokenHash,
							info.getName(),
							info.getVersion(),
							info.getLayout(),
							info.getPrerelease(),
							info.getOs(),
							info.getManufacturer(),
							info.getProduct(),
							info.getDescription(),
							info.getUa()
					);
					if (info.getFcmToken() != null) {
						device.updateFcmToken(info.getFcmToken());
					}
					return device;
				})
				.orElseGet(() -> {
					MemberDevice device = MemberDevice.builder()
							.memberId(memberId)
							.refreshTokenHash(refreshTokenHash)
							.platformJsName(info.getName())
							.platformJsVersion(info.getVersion())
							.platformJsLayout(info.getLayout())
							.platformJsPrerelease(info.getPrerelease())
							.platformJsOs(info.getOs())
							.platformJsManufacturer(info.getManufacturer())
							.platformJsProduct(info.getProduct())
							.platformJsDescription(info.getDescription())
							.platformJsUa(info.getUa())
							.expiresAt(Instant.now().plusMillis(expiryMs))
							.build();
					if (info.getFcmToken() != null) {
						device.updateFcmToken(info.getFcmToken());
					}
					return memberDeviceRepository.save(device);
				});
	}

	private Optional<MemberDevice> findExistingDevice(Long memberId, DeviceInfo deviceInfo) {
		if (deviceInfo.getFcmToken() != null) {
			return memberDeviceRepository.findTopByMemberIdAndPlatformJsUaAndFcmTokenOrderByLastAccessedAtDesc(
					memberId, deviceInfo.getUa(), deviceInfo.getFcmToken());
		}
		if (deviceInfo.getUa() == null || deviceInfo.getUa().isBlank()) {
			return Optional.empty();
		}
		return memberDeviceRepository.findTopByMemberIdAndPlatformJsUaOrderByLastAccessedAtDesc(memberId, deviceInfo.getUa());
	}

	@Transactional
	public void deleteByRefreshToken(String refreshToken) {
		if (refreshToken != null) {
			memberDeviceRepository.deleteByRefreshTokenHash(RefreshTokenHasher.hash(refreshToken));
		}
	}

	@Transactional(readOnly = true)
	public List<MemberDevice> findByMemberId(Long memberId) {
		return memberDeviceRepository.findByMemberId(memberId);
	}

	@Transactional
	public void switchMember(Long memberId, Long newMemberId) {
		List<MemberDevice> guestDevices = memberDeviceRepository.findByMemberId(memberId);
		List<MemberDevice> targetDevices = memberDeviceRepository.findByMemberId(newMemberId);

		for (MemberDevice guestDevice : guestDevices) {
			boolean duplicateExists = targetDevices.stream().anyMatch(td ->
					(td.getPlatformJsUa() != null && td.getPlatformJsUa().equals(guestDevice.getPlatformJsUa()))
							|| (td.getFcmToken() != null && td.getFcmToken().equals(guestDevice.getFcmToken()))
			);
			if (duplicateExists) {
				memberDeviceRepository.delete(guestDevice);
			} else {
				guestDevice.switchMember(newMemberId);
			}
		}
	}

	/**
	 * 만료된 기기 세션(세션 만료 시각이 지난 기기, FCM 토큰 포함)을 일괄 삭제한다.
	 * 관리자 정리 버튼용: 삭제된 개수를 반환한다.
	 */
	@Transactional
	public long deleteExpired() {
		return memberDeviceRepository.deleteExpired(java.time.Instant.now());
	}
}
