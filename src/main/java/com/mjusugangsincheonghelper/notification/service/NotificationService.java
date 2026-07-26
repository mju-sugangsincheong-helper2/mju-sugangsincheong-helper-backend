package com.mjusugangsincheonghelper.notification.service;

import com.mjusugangsincheonghelper.database.entity.MemberDevice;
import com.mjusugangsincheonghelper.database.repository.MemberDeviceRepository;
import com.mjusugangsincheonghelper.notification.dto.NotificationTokenDeleteRequest;
import com.mjusugangsincheonghelper.notification.dto.NotificationTokenRegisterRequest;
import com.mjusugangsincheonghelper.notification.dto.NotificationTokenResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

	private final MemberDeviceRepository memberDeviceRepository;

	@Transactional
	public NotificationTokenResponse registerToken(Long memberId, NotificationTokenRegisterRequest request) {
		String newFcmToken = request.getFcmToken();

		memberDeviceRepository.findByFcmToken(newFcmToken).ifPresent(existingDevice -> {
			if (!existingDevice.getMemberId().equals(memberId)) {
				existingDevice.clearFcmToken();
			}
		});

		MemberDevice device = memberDeviceRepository.findFirstByMemberIdOrderByLastAccessedAtDesc(memberId)
				.orElseGet(() -> memberDeviceRepository.save(MemberDevice.builder()
						.memberId(memberId)
						.refreshToken("fcm-only-ref-" + System.currentTimeMillis())
						.build()));

		device.updateFcmToken(newFcmToken);
		return NotificationTokenResponse.from(device);
	}

	@Transactional
	public void deleteToken(Long memberId, NotificationTokenDeleteRequest request) {
		String targetFcmToken = request.getFcmToken();
		List<MemberDevice> devices = memberDeviceRepository.findByMemberIdAndFcmToken(memberId, targetFcmToken);

		if (devices.isEmpty()) {
			memberDeviceRepository.findByFcmToken(targetFcmToken).ifPresent(MemberDevice::clearFcmToken);
			return;
		}

		for (MemberDevice device : devices) {
			device.clearFcmToken();
		}
	}
}
