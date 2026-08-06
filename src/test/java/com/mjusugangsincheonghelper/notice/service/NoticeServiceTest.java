package com.mjusugangsincheonghelper.notice.service;

import com.mjusugangsincheonghelper.database.entity.NoticeEntity;
import com.mjusugangsincheonghelper.database.repository.NoticeRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.notice.dto.NoticeRequest;
import com.mjusugangsincheonghelper.notice.dto.NoticeResponse;
import com.mjusugangsincheonghelper.notice.event.NoticeCreated;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

	@Mock
	private NoticeRepository noticeRepository;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	private NoticeService noticeService;

	@BeforeEach
	void setUp() {
		noticeService = new NoticeService(noticeRepository, eventPublisher);
	}

	private static NoticeRequest request() {
		return NoticeRequest.builder()
				.type("general")
				.title("신규 공지")
				.content("내용")
				.broadcast(true)
				.build();
	}

	private static NoticeRequest requestWithoutBroadcast() {
		return NoticeRequest.builder()
				.type("general")
				.title("등록만 하는 공지")
				.content("내용")
				.build();
	}

	@Test
	@DisplayName("broadcast=true 로 생성 시 NoticeCreated 이벤트가 발행된다 (커밋 후 리스너가 푸시 발행)")
	void shouldPublishNoticeCreatedEventWhenBroadcast() {
		given(noticeRepository.save(any(NoticeEntity.class)))
				.willAnswer(invocation -> invocation.getArgument(0));

		NoticeResponse response = noticeService.create(request());

		assertThat(response.getTitle()).isEqualTo("신규 공지");
		ArgumentCaptor<NoticeCreated> captor = ArgumentCaptor.forClass(NoticeCreated.class);
		verify(eventPublisher).publishEvent(captor.capture());
		assertThat(captor.getValue().title()).isEqualTo("신규 공지");
	}

	@Test
	@DisplayName("broadcast가 null/false 이면 이벤트 발행 없이 공지 등록만 한다")
	void shouldNotPublishEventWhenBroadcastNotSet() {
		given(noticeRepository.save(any(NoticeEntity.class)))
				.willAnswer(invocation -> invocation.getArgument(0));

		NoticeResponse response = noticeService.create(requestWithoutBroadcast());

		assertThat(response.getTitle()).isEqualTo("등록만 하는 공지");
		verifyNoInteractions(eventPublisher);
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
