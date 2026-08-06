package com.mjusugangsincheonghelper.notification.publisher;

import com.mjusugangsincheonghelper.database.entity.MemberDevice;
import com.mjusugangsincheonghelper.database.repository.MemberDeviceRepository;
import com.mjusugangsincheonghelper.global.config.PgmqProperties;
import com.mjusugangsincheonghelper.global.config.PgmqService;
import com.mjusugangsincheonghelper.notification.consumer.dto.NotificationEventMessage;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 푸시 알림 이벤트를 PGMQ 큐에 발행하는 유일한 출구.
 *
 * <p>"FCM 토큰 조회 → 이벤트 생성 → 큐 적재" 패턴을 여기로 응집하고,
 * 호출부(비즈니스)는 대상과 내용만 결정한다. 발송 실패는 로그로 흡수하며
 * 절대 호출부(비즈니스 트랜잭션)로 전파하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPublisher {

	private final MemberDeviceRepository memberDeviceRepository;
	private final PgmqService pgmqService;
	private final PgmqProperties pgmqProperties;

	/** 회원의 모든 등록 기기에 푸시를 발행한다. */
	public void publishToMember(Long memberId, String type, String path, String title, String body) {
		try {
			List<MemberDevice> devices = memberDeviceRepository.findByMemberId(memberId);
			for (MemberDevice device : devices) {
				String token = device.getFcmToken();
				if (token == null || token.isBlank()) {
					continue;
				}
				enqueue(token, type, path, title, body);
				log.debug("Queued FCM notification. type={}, memberId={}, deviceId={}", type, memberId, device.getId());
			}
		} catch (Exception e) {
			log.warn("Failed to publish FCM notification. type={}, memberId={}", type, memberId, e);
		}
	}

	/** 여러 회원에게 푸시를 발행한다. */
	public void publishToMembers(List<Long> memberIds, String type, String path, String title, String body) {
		for (Long memberId : memberIds) {
			publishToMember(memberId, type, path, title, body);
		}
	}

	/** 등록된 모든 FCM 토큰에 브로드캐스트한다. */
	public void publishToAll(String type, String path, String title, String body) {
		try {
			List<String> tokens = memberDeviceRepository.findAllFcmTokens();
			if (tokens.isEmpty()) {
				log.debug("No FCM tokens found. Skipping broadcast. type={}", type);
				return;
			}
			for (String token : tokens) {
				enqueue(token, type, path, title, body);
			}
			log.info("Queued broadcast: type={}, tokenCount={}", type, tokens.size());
		} catch (Exception e) {
			log.warn("Failed to publish FCM broadcast. type={}", type, e);
		}
	}

	private void enqueue(String token, String type, String path, String title, String body) {
		NotificationEventMessage event = NotificationEventMessage.builder()
				.token(token)
				.notification(NotificationEventMessage.NotificationPayload.builder()
						.title(title)
						.body(body)
						.build())
				.data(Map.of(
						"type", type,
						"path", path,
						"timestamp", String.valueOf(System.currentTimeMillis())
				))
				.build();
		pgmqService.send(pgmqProperties.getNotification().getQueueName(), event);
	}
}
