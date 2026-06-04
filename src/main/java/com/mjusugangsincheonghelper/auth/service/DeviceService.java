package com.mjusugangsincheonghelper.auth.service;

import com.mjusugangsincheonghelper.auth.dto.DeviceInfo;
import com.mjusugangsincheonghelper.database.entity.MemberDevice;
import com.mjusugangsincheonghelper.database.repository.MemberDeviceRepository;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeviceService {

	private final MemberDeviceRepository memberDeviceRepository;

	@Value("${app.jwt.refresh-token-expiry-ms}")
	private long refreshTokenExpiryMs;

	@Transactional
	public void upsert(Long memberId, String refreshToken, String fcmToken, DeviceInfo deviceInfo) {
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
					.expiresAt(Instant.now().plusMillis(refreshTokenExpiryMs))
					.build();
			memberDeviceRepository.save(device);
		}
	}
}
