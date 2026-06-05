package com.mjusugangsincheonghelper.auth.session.device;

import com.mjusugangsincheonghelper.auth.dto.DeviceInfo;
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
	public void upsert(Long memberId, String refreshToken, String fcmToken, DeviceInfo deviceInfo, long expiryMs) {
		if (deviceInfo == null) {
			deviceInfo = DeviceInfo.builder().build();
		}

		Optional<MemberDevice> existingDevice = fcmToken != null
				? memberDeviceRepository.findByMemberIdAndFcmToken(memberId, fcmToken)
				: Optional.empty();

		if (existingDevice.isPresent()) {
			existingDevice.get().updateAccessInfo(
					refreshToken, fcmToken,
					deviceInfo.getName(), deviceInfo.getVersion(),
					deviceInfo.getLayout(), deviceInfo.getPrerelease(),
					deviceInfo.getOs(), deviceInfo.getManufacturer(),
					deviceInfo.getProduct(), deviceInfo.getDescription(),
					deviceInfo.getUa()
			);
		} else {
			MemberDevice device = MemberDevice.builder()
					.memberId(memberId)
					.refreshToken(refreshToken)
					.fcmToken(fcmToken)
					.platformjsName(deviceInfo.getName())
					.platformjsVersion(deviceInfo.getVersion())
					.platformjsLayout(deviceInfo.getLayout())
					.platformjsPrerelease(deviceInfo.getPrerelease())
					.platformjsOs(deviceInfo.getOs())
					.platformjsManufacturer(deviceInfo.getManufacturer())
					.platformjsProduct(deviceInfo.getProduct())
					.platformjsDescription(deviceInfo.getDescription())
					.platformjsUa(deviceInfo.getUa())
					.expiresAt(Instant.now().plusMillis(expiryMs))
					.build();
			memberDeviceRepository.save(device);
		}
	}

	@Transactional
	public void deleteByFcmToken(Long memberId, String fcmToken) {
		if (fcmToken != null) {
			memberDeviceRepository.deleteByMemberIdAndFcmToken(memberId, fcmToken);
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
