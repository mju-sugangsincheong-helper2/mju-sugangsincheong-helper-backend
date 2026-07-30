package com.mjusugangsincheonghelper.notification.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.mjusugangsincheonghelper.database.entity.MemberDevice;
import com.mjusugangsincheonghelper.database.repository.MemberDeviceRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.notification.dto.NotificationTokenDeleteRequest;
import com.mjusugangsincheonghelper.notification.dto.NotificationTokenRegisterRequest;
import com.mjusugangsincheonghelper.notification.dto.NotificationTokenResponse;
import com.mjusugangsincheonghelper.notification.dto.NotificationTestRequest;
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
	}

	public void sendTestNotification(Long memberId, Long deviceId, NotificationTestRequest request) {
		MemberDevice device = memberDeviceRepository.findById(deviceId)
				.orElseThrow(() -> new BaseException(ErrorCode.NOTIFICATION_TOKEN_NOT_FOUND));

		if (!device.getMemberId().equals(memberId)) {
			throw new BaseException(ErrorCode.GLOBAL_SECURITY_FORBIDDEN);
		}

		String fcmToken = device.getFcmToken();
		if (fcmToken == null || fcmToken.isBlank()) {
			throw new BaseException(ErrorCode.NOTIFICATION_TOKEN_NOT_FOUND);
		}

		if (FirebaseApp.getApps().isEmpty()) {
			throw new BaseException(ErrorCode.NOTIFICATION_SEND_FAILED);
		}

		try {
			Message message = Message.builder()
					.setToken(fcmToken)
					.setNotification(Notification.builder()
							.setTitle(request.getTitle())
							.setBody(request.getBody())
							.build())
					.putAllData(Map.of(
							"type", "GENERAL",
							"path", "/",
							"timestamp", String.valueOf(System.currentTimeMillis())
					))
					.build();
			FirebaseMessaging.getInstance().send(message);
			log.info("Test notification sent to deviceId={}, memberId={}", deviceId, memberId);
		} catch (Exception e) {
			log.error("Failed to send test notification", e);
			throw new BaseException(ErrorCode.NOTIFICATION_SEND_FAILED, e);
		}
	}
}
