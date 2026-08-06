package com.mjusugangsincheonghelper.notice.event;

import static org.mockito.Mockito.verify;

import com.mjusugangsincheonghelper.notification.publisher.NotificationPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("NoticeEventListener 단위 테스트")
class NoticeEventListenerTest {

	@Mock
	private NotificationPublisher notificationPublisher;

	@InjectMocks
	private NoticeEventListener listener;

	@Test
	@DisplayName("NoticeCreated: 전체 토큰 대상 SYSTEM_NOTICE 브로드캐스트를 발행한다")
	void onNoticeCreatedBroadcasts() {
		listener.onNoticeCreated(new NoticeCreated(1L, "점검 안내"));

		verify(notificationPublisher).publishToAll("SYSTEM_NOTICE", "/", "공지 알림", "점검 안내");
	}
}
