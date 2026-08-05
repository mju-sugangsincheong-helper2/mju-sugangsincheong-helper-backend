package com.mjusugangsincheonghelper.notice.service;

import com.mjusugangsincheonghelper.database.entity.NoticeEntity;
import com.mjusugangsincheonghelper.database.repository.MemberDeviceRepository;
import com.mjusugangsincheonghelper.database.repository.NoticeRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.global.config.PgmqService;
import com.mjusugangsincheonghelper.notice.dto.NoticeRequest;
import com.mjusugangsincheonghelper.notice.dto.NoticeResponse;
import com.mjusugangsincheonghelper.notification.consumer.NotificationConsumerWorker;
import com.mjusugangsincheonghelper.notification.consumer.dto.NotificationEventMessage;
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
public class NoticeService {

	private final NoticeRepository noticeRepository;
	private final MemberDeviceRepository memberDeviceRepository;
	private final PgmqService pgmqService;

	public List<NoticeResponse> findAll() {
		return noticeRepository.findAllByOrderByCreatedAtDesc().stream()
				.map(NoticeResponse::from)
				.toList();
	}

	@Transactional
	public NoticeResponse create(NoticeRequest request) {
		NoticeEntity notice = noticeRepository.save(NoticeEntity.builder()
				.type(request.getType())
				.title(request.getTitle())
				.content(request.getContent())
				.build());
		broadcastNotice(notice);
		return NoticeResponse.from(notice);
	}

	@Transactional
	public NoticeResponse update(Long id, NoticeRequest request) {
		NoticeEntity notice = noticeRepository.findById(id)
				.orElseThrow(() -> new BaseException(ErrorCode.NOTICE_NOT_FOUND));
		notice.update(request.getType(), request.getTitle(), request.getContent());
		return NoticeResponse.from(notice);
	}

	@Transactional
	public void delete(Long id) {
		NoticeEntity notice = noticeRepository.findById(id)
				.orElseThrow(() -> new BaseException(ErrorCode.NOTICE_NOT_FOUND));
		noticeRepository.delete(notice);
	}

	/**
	 * 공지 생성 시 전체 사용자(모든 FCM 토큰) 대상 푸시 알림을 발행한다.
	 * 알림 발행 실패가 공지 저장 자체를 실패시키지 않도록 예외는 여기서 흡수한다.
	 */
	private void broadcastNotice(NoticeEntity notice) {
		try {
			List<String> fcmTokens = memberDeviceRepository.findAllFcmTokens();
			if (fcmTokens.isEmpty()) {
				log.info("No FCM tokens found. Skipping notice broadcast: noticeId={}", notice.getId());
				return;
			}

			String timestamp = String.valueOf(System.currentTimeMillis());
			for (String token : fcmTokens) {
				NotificationEventMessage event = NotificationEventMessage.builder()
						.token(token)
						.notification(NotificationEventMessage.NotificationPayload.builder()
								.title("공지 알림")
								.body(notice.getTitle())
								.build())
						.data(Map.of(
								"type", "SYSTEM_NOTICE",
								"path", "/",
								"timestamp", timestamp
						))
						.build();
				pgmqService.send(NotificationConsumerWorker.QUEUE_NAME, event);
			}

			log.info("Queued notice broadcast: noticeId={}, tokenCount={}", notice.getId(), fcmTokens.size());
		} catch (Exception e) {
			log.warn("공지 푸시 알림 발송 중 오류 발생: noticeId={}", notice.getId(), e);
		}
	}
}