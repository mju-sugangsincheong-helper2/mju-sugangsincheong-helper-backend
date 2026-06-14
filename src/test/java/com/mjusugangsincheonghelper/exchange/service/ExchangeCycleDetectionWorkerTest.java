package com.mjusugangsincheonghelper.exchange.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.core.JsonProcessingException;
import tools.jackson.databind.ObjectMapper;
import com.mjusugangsincheonghelper.exchange.dto.CycleDetectionMessage;
import com.mjusugangsincheonghelper.global.config.PgmqMessageDto;
import com.mjusugangsincheonghelper.global.config.PgmqService;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExchangeCycleDetectionWorker 단위 테스트")
class ExchangeCycleDetectionWorkerTest {

	@Mock
	private PgmqService pgmqService;

	@Mock
	private ExchangeCycleDetector cycleDetector;

	@Mock
	private ObjectMapper objectMapper;

	@Mock
	private TaskScheduler pgmqScheduler;

	@InjectMocks
	private ExchangeCycleDetectionWorker worker;

	@Nested
	@DisplayName("start 메서드는")
	class Describe_start {

		@Test
		@DisplayName("PGMQ 큐를 생성하고 스케줄러에 poll을 등록한다")
		void it_creates_queue_and_schedules_polling() {
			// When
			worker.start();

			// Then
			verify(pgmqService).createQueue(ExchangeCycleDetector.QUEUE_NAME);
			verify(pgmqScheduler).scheduleWithFixedDelay(any(Runnable.class), eq(Duration.ofSeconds(1)));
		}

		@Test
		@DisplayName("큐 생성 중 에러가 발생해도 스케줄러 등록을 지속한다")
		void it_continues_to_schedule_when_queue_creation_fails() {
			// Given
			doThrow(new RuntimeException("Queue exists")).when(pgmqService).createQueue(ExchangeCycleDetector.QUEUE_NAME);

			// When
			worker.start();

			// Then
			verify(pgmqScheduler).scheduleWithFixedDelay(any(Runnable.class), eq(Duration.ofSeconds(1)));
		}
	}

	@Nested
	@DisplayName("poll 메서드는")
	class Describe_poll {

		@Test
		@DisplayName("메시지를 정상 수신하고 사이클 탐색을 처리한 후 삭제한다")
		void it_processes_and_deletes_received_message() throws JsonProcessingException {
			// Given
			PgmqMessageDto messageDto = PgmqMessageDto.builder()
					.msgId(123L)
					.readCt(1)
					.message("{\"term\":\"202510\",\"intentId\":10}")
					.build();

			given(pgmqService.read(ExchangeCycleDetector.QUEUE_NAME, 30, 1))
					.willReturn(List.of(messageDto));

			CycleDetectionMessage message = CycleDetectionMessage.builder()
					.term("202510")
					.intentId(10L)
					.memberId(1L)
					.giveCourseNo("10001")
					.wantCourseNo("10002")
					.build();

			given(objectMapper.readValue(messageDto.getMessage(), CycleDetectionMessage.class))
					.willReturn(message);

			// When
			ReflectionTestUtils.invokeMethod(worker, "poll");

			// Then
			verify(cycleDetector).detectCyclesAndCreateRooms(
					"202510", 10L, 1L, "10001", "10002"
			);
			verify(pgmqService).delete(ExchangeCycleDetector.QUEUE_NAME, 123L);
		}

		@Test
		@DisplayName("메시지 파싱 에러가 발생하면 처리하지 않고 넘어간다")
		void it_skips_when_parsing_fails() throws JsonProcessingException {
			// Given
			PgmqMessageDto messageDto = PgmqMessageDto.builder()
					.msgId(123L)
					.readCt(1)
					.message("invalid json")
					.build();

			given(pgmqService.read(ExchangeCycleDetector.QUEUE_NAME, 30, 1))
					.willReturn(List.of(messageDto));

			doThrow(new RuntimeException("JSON error")).when(objectMapper).readValue(messageDto.getMessage(), CycleDetectionMessage.class);

			// When
			ReflectionTestUtils.invokeMethod(worker, "poll");

			// Then
			verifyNoInteractions(cycleDetector);
			verify(pgmqService, never()).delete(any(), any());
		}
	}
}
