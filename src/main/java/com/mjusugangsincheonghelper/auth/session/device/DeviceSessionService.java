package com.mjusugangsincheonghelper.auth.session.device;

import com.mjusugangsincheonghelper.auth.common.dto.DeviceInfo;
import com.mjusugangsincheonghelper.database.entity.MemberDevice;
import com.mjusugangsincheonghelper.database.repository.MemberDeviceRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeviceSessionService {

	private final MemberDeviceRepository memberDeviceRepository;

	@Transactional
	public MemberDevice upsert(Long memberId, String refreshToken, DeviceInfo deviceInfo, long expiryMs) {
		if (deviceInfo == null) {
			deviceInfo = DeviceInfo.builder().build();
		}

		MemberDevice device = MemberDevice.builder()
				.memberId(memberId)
				.refreshToken(refreshToken)
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
		return memberDeviceRepository.save(device);
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
