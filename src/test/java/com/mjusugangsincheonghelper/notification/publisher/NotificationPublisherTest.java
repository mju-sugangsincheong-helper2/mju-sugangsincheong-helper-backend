package com.mjusugangsincheonghelper.notification.publisher;

import com.mjusugangsincheonghelper.database.entity.MemberDevice;
import com.mjusugangsincheonghelper.database.repository.MemberDeviceRepository;
import com.mjusugangsincheonghelper.global.config.PgmqService;
import com.mjusugangsincheonghelper.notification.consumer.NotificationConsumerWorker;
import com.mjusugangsincheonghelper.notification.consumer.dto.NotificationEventMessage;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationPublisher 단위 테스트")
class NotificationPublisherTest {

	@Mock
	private MemberDeviceRepository memberDeviceRepository;

	@Mock
	private PgmqService pgmqService;

	@InjectMocks
	private NotificationPublisher publisher;

	private static MemberDevice device(Long id, String token) {
		MemberDevice device = MemberDevice.builder()
				.memberId(1L)
				.build();
		device.updateFcmToken(token);
		ReflectionTestUtils.setField(device, "id", id);
		return device;
	}

	@Test
	@DisplayName("publishToMember는 회원의 모든 유효 토큰에 이벤트를 발행한다")
	void publishToMemberSendsToAllValidTokens() {
		given(memberDeviceRepository.findByMemberId(1L))
				.willReturn(List.of(device(10L, "token-1"), device(11L, "token-2")));

		publisher.publishToMember(1L, "EXCHANGE_MESSAGE", "/exchange/rooms/1", "제목", "본문");

		ArgumentCaptor<NotificationEventMessage> captor = ArgumentCaptor.forClass(NotificationEventMessage.class);
		verify(pgmqService, times(2)).send(eq(NotificationConsumerWorker.QUEUE_NAME), captor.capture());
		NotificationEventMessage first = captor.getAllValues().get(0);
		assertThat(first.getToken()).isEqualTo("token-1");
		assertThat(first.getNotification().getTitle()).isEqualTo("제목");
		assertThat(first.getNotification().getBody()).isEqualTo("본문");
		assertThat(first.getData()).containsEntry("type", "EXCHANGE_MESSAGE")
				.containsEntry("path", "/exchange/rooms/1")
				.containsKey("timestamp");
	}

	@Test
	@DisplayName("publishToMember는 빈 토큰 기기를 건너뛴다")
	void publishToMemberSkipsBlankTokens() {
		given(memberDeviceRepository.findByMemberId(1L))
				.willReturn(List.of(device(10L, null), device(11L, "  ")));

		publisher.publishToMember(1L, "EXCHANGE_MESSAGE", "/p", "제목", "본문");

		verify(pgmqService, never()).send(any(), any());
	}

	@Test
	@DisplayName("publishToMember는 조회 실패가 호출부로 전파되지 않는다")
	void publishToMemberSwallowsFailure() {
		given(memberDeviceRepository.findByMemberId(1L))
				.willThrow(new RuntimeException("db down"));

		assertThatCode(() -> publisher.publishToMember(1L, "T", "/p", "제목", "본문"))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("publishToAll은 등록된 모든 토큰에 브로드캐스트한다")
	void publishToAllSendsToEveryToken() {
		given(memberDeviceRepository.findAllFcmTokens())
				.willReturn(List.of("token-1", "token-2", "token-3"));

		publisher.publishToAll("SYSTEM_NOTICE", "/", "공지 알림", "공지 제목");

		verify(pgmqService, times(3)).send(eq(NotificationConsumerWorker.QUEUE_NAME), any(NotificationEventMessage.class));
	}

	@Test
	@DisplayName("publishToAll은 토큰이 없으면 아무것도 발행하지 않는다")
	void publishToAllSkipsWhenNoTokens() {
		given(memberDeviceRepository.findAllFcmTokens()).willReturn(List.of());

		publisher.publishToAll("SYSTEM_NOTICE", "/", "공지 알림", "공지 제목");

		verify(pgmqService, never()).send(any(), any());
	}

	@Test
	@DisplayName("publishToAll은 발행 실패가 호출부로 전파되지 않는다")
	void publishToAllSwallowsFailure() {
		given(memberDeviceRepository.findAllFcmTokens())
				.willThrow(new RuntimeException("pgmq down"));

		assertThatCode(() -> publisher.publishToAll("SYSTEM_NOTICE", "/", "공지 알림", "공지 제목"))
				.doesNotThrowAnyException();
	}
}
