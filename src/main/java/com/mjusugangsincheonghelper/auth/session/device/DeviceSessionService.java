package com.mjusugangsincheonghelper.auth.session.device;

import com.mjusugangsincheonghelper.auth.common.dto.DeviceInfo;
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

		Optional<MemberDevice> existing = findExistingDevice(memberId, info);

		return existing
				.map(device -> {
					device.updateAccessInfo(
							refreshToken,
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
							.refreshToken(refreshToken)
							.platformjsName(info.getName())
							.platformjsVersion(info.getVersion())
							.platformjsLayout(info.getLayout())
							.platformjsPrerelease(info.getPrerelease())
							.platformjsOs(info.getOs())
							.platformjsManufacturer(info.getManufacturer())
							.platformjsProduct(info.getProduct())
							.platformjsDescription(info.getDescription())
							.platformjsUa(info.getUa())
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
			return memberDeviceRepository.findByMemberIdAndPlatformjsUaAndFcmToken(
					memberId, deviceInfo.getUa(), deviceInfo.getFcmToken());
		}
		return memberDeviceRepository.findByMemberIdAndPlatformjsUa(memberId, deviceInfo.getUa());
	}

	@Transactional
	public void deleteByRefreshToken(String refreshToken) {
		if (refreshToken != null) {
			memberDeviceRepository.deleteByRefreshToken(refreshToken);
		}
	}

	@Transactional(readOnly = true)
	public List<MemberDevice> findByMemberId(Long memberId) {
		return memberDeviceRepository.findByMemberId(memberId);
	}

	@Transactional
	public void switchMember(Long memberId, Long newMemberId) {
		memberDeviceRepository.findByMemberId(memberId).forEach(device -> device.switchMember(newMemberId));
	}
}
