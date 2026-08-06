package com.mjusugangsincheonghelper.notification.service;

import com.google.firebase.FirebaseApp;
import com.mjusugangsincheonghelper.database.entity.MemberDevice;
import com.mjusugangsincheonghelper.database.repository.MemberDeviceRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.global.config.PgmqService;
import com.mjusugangsincheonghelper.notification.consumer.NotificationConsumerWorker;
import com.mjusugangsincheonghelper.notification.consumer.dto.NotificationEventMessage;
import com.mjusugangsincheonghelper.notification.dto.NotificationTokenDeleteRequest;
import com.mjusugangsincheonghelper.notification.dto.NotificationTokenRegisterRequest;
import com.mjusugangsincheonghelper.notification.dto.NotificationTokenResponse;
import com.mjusugangsincheonghelper.notification.dto.NotificationTestRequest;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

	private final MemberDeviceRepository memberDeviceRepository;
	private final PgmqService pgmqService;

	@Transactional
	public NotificationTokenResponse registerToken(Long memberId, Long deviceId,
			NotificationTokenRegisterRequest request) {
		String newFcmToken = request.getFcmToken();

		MemberDevice device = memberDeviceRepository.findById(deviceId)
				.orElseThrow(() -> new BaseException(ErrorCode.NOTIFICATION_TOKEN_NOT_FOUND));

		if (!device.getMemberId().equals(memberId)) {
			throw new BaseException(ErrorCode.GLOBAL_SECURITY_FORBIDDEN);
		}

		memberDeviceRepository.findByFcmToken(newFcmToken).ifPresent(existingDevice -> {
			if (!existingDevice.getMemberId().equals(memberId)) {
				existingDevice.clearFcmToken();
			}
		});

		device.updateFcmToken(newFcmToken);
		log.debug("Registered FCM token. memberId={}, deviceId={}", memberId, deviceId);
		return NotificationTokenResponse.from(device);
	}

	@Transactional
	public void deleteToken(Long memberId, Long deviceId, NotificationTokenDeleteRequest request) {
		MemberDevice device = memberDeviceRepository.findById(deviceId)
				.orElseThrow(() -> new BaseException(ErrorCode.NOTIFICATION_TOKEN_NOT_FOUND));

		if (!device.getMemberId().equals(memberId)) {
			throw new BaseException(ErrorCode.GLOBAL_SECURITY_FORBIDDEN);
		}

		device.clearFcmToken();
		log.debug("Deleted FCM token. memberId={}, deviceId={}", memberId, deviceId);
	}

	@Transactional
	public void sendTestNotification(Long memberId, NotificationTestRequest request) {
		List<MemberDevice> devices = memberDeviceRepository.findByMemberId(memberId);
		if (devices.isEmpty()) {
			throw new BaseException(ErrorCode.NOTIFICATION_TOKEN_NOT_FOUND);
		}

		if (FirebaseApp.getApps().isEmpty()) {
			throw new BaseException(ErrorCode.NOTIFICATION_SEND_FAILED);
		}

		String timestamp = String.valueOf(System.currentTimeMillis());
		int queuedCount = 0;

		// 테스트 알림은 실제 알림과 동일하게 PGMQ 큐를 통해 발송되며,
		// 현재 기기가 아닌 유저의 모든 기기로 전송된다.
		for (MemberDevice device : devices) {
			String fcmToken = device.getFcmToken();
			if (fcmToken == null || fcmToken.isBlank()) {
				continue;
			}

			NotificationEventMessage event = NotificationEventMessage.builder()
					.token(fcmToken)
					.notification(NotificationEventMessage.NotificationPayload.builder()
							.title(request.getTitle())
							.body("이것은 테스트 알림입니다.")
							.build())
					.data(Map.of(
							"type", "GENERAL",
							"path", "/notification",
							"timestamp", timestamp
					))
					.build();
			pgmqService.send(NotificationConsumerWorker.QUEUE_NAME, event);
			queuedCount++;
			log.debug("Queued test notification. memberId={}, deviceId={}", memberId, device.getId());
		}

		if (queuedCount == 0) {
			throw new BaseException(ErrorCode.NOTIFICATION_TOKEN_NOT_FOUND);
		}
	}
}
