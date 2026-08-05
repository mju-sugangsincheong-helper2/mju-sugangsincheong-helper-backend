package com.mjusugangsincheonghelper.notice.service;

import com.mjusugangsincheonghelper.database.entity.NoticeEntity;
import com.mjusugangsincheonghelper.database.repository.MemberDeviceRepository;
import com.mjusugangsincheonghelper.database.repository.NoticeRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.global.config.PgmqService;
import com.mjusugangsincheonghelper.notice.dto.NoticeRequest;
import com.mjusugangsincheonghelper.notice.dto.NoticeResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

	@Mock
	private NoticeRepository noticeRepository;

	@Mock
	private MemberDeviceRepository memberDeviceRepository;

	@Mock
	private PgmqService pgmqService;

	private NoticeService noticeService;

	@BeforeEach
	void setUp() {
		noticeService = new NoticeService(noticeRepository, memberDeviceRepository, pgmqService);
	}

	private static NoticeRequest request() {
		return NoticeRequest.builder()
				.type("general")
				.title("신규 공지")
				.content("내용")
				.build();
	}

	@Test
	@DisplayName("공지 생성 시 전체 FCM 토큰 수만큼 알림 이벤트를 큐에 발행한다")
	void shouldBroadcastNoticeToAllFcmTokens() {
		given(memberDeviceRepository.findAllFcmTokens())
				.willReturn(List.of("token-1", "token-2", "token-3"));
		given(noticeRepository.save(any(NoticeEntity.class)))
				.willAnswer(invocation -> invocation.getArgument(0));

		NoticeResponse response = noticeService.create(request());

		assertThat(response.getTitle()).isEqualTo("신규 공지");
		verify(pgmqService, times(3)).send(any(), any());
	}

	@Test
	@DisplayName("FCM 토큰이 없으면 알림 발행 없이 공지 생성만 한다")
	void shouldSkipBroadcastWhenNoFcmTokens() {
		given(memberDeviceRepository.findAllFcmTokens()).willReturn(List.of());
		given(noticeRepository.save(any(NoticeEntity.class)))
				.willAnswer(invocation -> invocation.getArgument(0));

		noticeService.create(request());

		verify(pgmqService, never()).send(any(), any());
	}

	@Test
	@DisplayName("알림 발행 실패가 공지 생성 자체를 실패시키지 않는다")
	void shouldNotFailNoticeCreateWhenBroadcastFails() {
		given(memberDeviceRepository.findAllFcmTokens())
				.willThrow(new RuntimeException("pgmq down"));
		given(noticeRepository.save(any(NoticeEntity.class)))
				.willAnswer(invocation -> invocation.getArgument(0));

		NoticeResponse response = noticeService.create(request());

		assertThat(response.getTitle()).isEqualTo("신규 공지");
	}

	@Test
	@DisplayName("존재하지 않는 공지 삭제 시 NOTICE_NOT_FOUND 예외가 발생한다")
	void shouldThrowWhenNoticeNotFound() {
		given(noticeRepository.findById(99L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> noticeService.delete(99L))
				.isInstanceOf(BaseException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.NOTICE_NOT_FOUND);
	}
}
