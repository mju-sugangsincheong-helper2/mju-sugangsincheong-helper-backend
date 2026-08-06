package com.mjusugangsincheonghelper.notice.event;

import com.mjusugangsincheonghelper.notification.publisher.NotificationPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 공지 쓰기 부작용 처리. 커밋 후(AFTER_COMMIT)에만 전체 푸시를 발행하므로
 * 롤백된 공지의 알림이 나가지 않는다.
 */
@Component
@RequiredArgsConstructor
public class NoticeEventListener {

	private final NotificationPublisher notificationPublisher;

	@TransactionalEventListener
	public void onNoticeCreated(NoticeCreated event) {
		notificationPublisher.publishToAll("SYSTEM_NOTICE", "/", "공지 알림", event.title());
	}
}
