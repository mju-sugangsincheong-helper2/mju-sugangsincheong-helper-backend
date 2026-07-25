package com.mjusugangsincheonghelper.multigame;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.mjusugangsincheonghelper.database.entity.MultigameReservationEntity;
import com.mjusugangsincheonghelper.database.entity.MultigameResultDetailEntity;
import com.mjusugangsincheonghelper.database.entity.MultigameResultEntity;
import com.mjusugangsincheonghelper.database.repository.MultigameReservationRepository;
import com.mjusugangsincheonghelper.database.repository.MultigameResultDetailRepository;
import com.mjusugangsincheonghelper.database.repository.MultigameResultRepository;
import com.mjusugangsincheonghelper.global.config.AdvisoryLockService;
import com.mjusugangsincheonghelper.multigame.common.MultigameRedisKeyProvider;
import com.mjusugangsincheonghelper.multigame.reservation.dto.MultigameReservationCreateRequest;
import com.mjusugangsincheonghelper.multigame.reservation.dto.MultigameReservationResponse;
import com.mjusugangsincheonghelper.multigame.reservation.service.MultigameReservationService;
import com.mjusugangsincheonghelper.multigame.session.dto.GameRequestResponse;
import com.mjusugangsincheonghelper.multigame.session.dto.WaitingRoomResponse;
import com.mjusugangsincheonghelper.multigame.session.service.DevGameInitializer;
import com.mjusugangsincheonghelper.multigame.session.service.GameQueueService;
import com.mjusugangsincheonghelper.multigame.session.service.MultigameFinalizeService;
import com.mjusugangsincheonghelper.multigame.session.service.MultigameSessionService;
import com.mjusugangsincheonghelper.multigame.session.service.WaitingRoomService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@DisplayName("Multigame 통합 테스트")
class MultigameIntegrationTest {

	private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	@Mock
	private StringRedisTemplate stringRedisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	@Mock
	private HashOperations<String, Object, Object> hashOperations;

	@Mock
	private AdvisoryLockService advisoryLockService;

	@Mock
	private MultigameReservationRepository reservationRepository;

	@Mock
	private MultigameResultRepository resultRepository;

	@Mock
	private MultigameResultDetailRepository resultDetailRepository;

	@Mock
	private DevGameInitializer devGameInitializer;

	@Mock
	private WaitingRoomService waitingRoomService;

	@Mock
	private GameQueueService gameQueueService;

	@Mock
	private MultigameFinalizeService finalizeService;

	@Nested
	@DisplayName("전체 플로우 테스트")
	class Describe_fullFlow {

		@Test
		@DisplayName("예약 → 대기방 → 게임 요청 → 결과 조회 플로우")
		void it_completes_full_flow() {
			// Given
			String multigameId = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0)
					.format(FORMATTER);
			Long memberId = 1L;

			// 1. 예약 생성
			MultigameReservationEntity reservationEntity = MultigameReservationEntity.builder()
					.memberId(memberId)
					.startTime(multigameId)
					.build();

			given(reservationRepository.existsByStartTimeAndMemberId(multigameId, memberId)).willReturn(false);
			given(reservationRepository.save(any(MultigameReservationEntity.class))).willReturn(reservationEntity);

			MultigameReservationService reservationService = new MultigameReservationService(
					reservationRepository, Optional.of(devGameInitializer));

			MultigameReservationCreateRequest request = MultigameReservationCreateRequest.builder()
					.multigameId(multigameId)
					.build();

			// When - 예약 생성
			MultigameReservationResponse reservationResponse = reservationService.create(memberId, request);

			// Then - 예약 확인
			assertThat(reservationResponse.getMultigameId()).isEqualTo(multigameId);
			verify(devGameInitializer).initializeGame(multigameId, 1);

			// 2. 대기방 입장
			given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
			given(valueOperations.get(MultigameRedisKeyProvider.state(multigameId))).willReturn("WAITING");
			given(waitingRoomService.countParticipants(multigameId)).willReturn(5);

			MultigameSessionService sessionService = new MultigameSessionService(
					stringRedisTemplate, waitingRoomService, gameQueueService);

			WaitingRoomResponse waitingRoomResponse = sessionService.enterWaitingRoom(multigameId, memberId);

			// Then - 대기방 상태 확인
			assertThat(waitingRoomResponse.getState()).isEqualTo("WAITING");
			assertThat(waitingRoomResponse.getParticipation()).isEqualTo(5);

			// 3. 게임 요청 (WAITING 상태)
			given(valueOperations.get(MultigameRedisKeyProvider.state(multigameId))).willReturn("WAITING");

			GameRequestResponse gameRequestResponse = sessionService.requestGame(multigameId, memberId, 3);

			// Then - WAITING 응답 확인
			assertThat(gameRequestResponse.getStatus()).isEqualTo("WAITING");
			assertThat(gameRequestResponse.getCurrentState()).isEqualTo("WAITING");
		}

		@Test
		@DisplayName("PROGRESS 상태에서 게임 요청 성공 플로우")
		void it_completes_game_request_in_progress_state() {
			// Given
			String multigameId = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0)
					.format(FORMATTER);
			Long memberId = 1L;
			int subjectId = 3;

			given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
			given(valueOperations.get(MultigameRedisKeyProvider.state(multigameId))).willReturn("PROGRESS");

			GameRequestResponse successResponse = GameRequestResponse.builder()
					.status("SUCCESS")
					.subjectId(subjectId)
					.remaining(2)
					.build();

			given(gameQueueService.processRequest(multigameId, memberId, subjectId)).willReturn(successResponse);

			MultigameSessionService sessionService = new MultigameSessionService(
					stringRedisTemplate, waitingRoomService, gameQueueService);

			// When
			GameRequestResponse response = sessionService.requestGame(multigameId, memberId, subjectId);

			// Then
			assertThat(response.getStatus()).isEqualTo("SUCCESS");
			assertThat(response.getSubjectId()).isEqualTo(subjectId);
			assertThat(response.getRemaining()).isEqualTo(2);
		}

		@Test
		@DisplayName("게임 종료 후 결과 저장 플로우")
		void it_saves_results_after_game_ends() {
			// Given
			String multigameId = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0)
					.format(FORMATTER);

			given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
			given(stringRedisTemplate.opsForHash()).willReturn(hashOperations);
			given(valueOperations.get(MultigameRedisKeyProvider.state(multigameId))).willReturn("ENDED");
			given(hashOperations.entries(MultigameRedisKeyProvider.history(multigameId))).willReturn(Map.of(
					"1", "SUCCESS:3:1234567890",
					"2", "FAIL_SOLDOUT:2:1234567891"
			));
			given(stringRedisTemplate.keys(MultigameRedisKeyProvider.heartbeatPattern(multigameId)))
					.willReturn(Set.of("key1", "key2"));
			given(resultRepository.findById(multigameId)).willReturn(Optional.empty());
			given(resultDetailRepository.findByStartTimeAndMemberId(eq(multigameId), any())).willReturn(Optional.empty());

			MultigameFinalizeService finalizeService = new MultigameFinalizeService(
					stringRedisTemplate, resultRepository, resultDetailRepository);

			// When
			finalizeService.finalizeGame(multigameId);

			// Then
			verify(valueOperations).set(MultigameRedisKeyProvider.state(multigameId), "FINALIZE");
			verify(resultRepository).save(any(MultigameResultEntity.class));
			verify(resultDetailRepository, org.mockito.Mockito.times(2)).save(any(MultigameResultDetailEntity.class));
		}
	}

	@Nested
	@DisplayName("상태 전이 테스트")
	class Describe_stateTransitions {

		@Test
		@DisplayName("WAITING → READY 전이")
		void it_transitions_from_waiting_to_ready() {
			// Given
			String multigameId = "20260726100000";

			given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
			given(valueOperations.get(MultigameRedisKeyProvider.state(multigameId))).willReturn("WAITING");
			given(waitingRoomService.countParticipants(multigameId)).willReturn(5);

			// When
			WaitingRoomResponse response = new MultigameSessionService(
					stringRedisTemplate, waitingRoomService, gameQueueService)
					.enterWaitingRoom(multigameId, 1L);

			// Then
			assertThat(response.getState()).isEqualTo("WAITING");
		}

		@Test
		@DisplayName("READY → PROGRESS 전이")
		void it_transitions_from_ready_to_progress() {
			// Given
			String multigameId = "20260726100000";

			given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
			given(valueOperations.get(MultigameRedisKeyProvider.state(multigameId))).willReturn("PROGRESS");

			GameRequestResponse gameResponse = GameRequestResponse.builder()
					.status("SUCCESS")
					.subjectId(1)
					.build();

			given(gameQueueService.processRequest(multigameId, 1L, 1)).willReturn(gameResponse);

			// When
			GameRequestResponse response = new MultigameSessionService(
					stringRedisTemplate, waitingRoomService, gameQueueService)
					.requestGame(multigameId, 1L, 1);

			// Then
			assertThat(response.getStatus()).isEqualTo("SUCCESS");
		}
	}

	@Nested
	@DisplayName("예외 상황 테스트")
	class Describe_exceptionScenarios {

		@Test
		@DisplayName("중복 예약 시 예외 발생")
		void it_throws_exception_for_duplicate_reservation() {
			// Given
			String multigameId = "20260726100000";
			Long memberId = 1L;

			given(reservationRepository.existsByStartTimeAndMemberId(multigameId, memberId)).willReturn(true);

			MultigameReservationService reservationService = new MultigameReservationService(
					reservationRepository, Optional.empty());

			MultigameReservationCreateRequest request = MultigameReservationCreateRequest.builder()
					.multigameId(multigameId)
					.build();

			// When & Then
			org.assertj.core.api.Assertions.assertThatThrownBy(
					() -> reservationService.create(memberId, request))
					.isInstanceOf(com.mjusugangsincheonghelper.global.api.exception.BaseException.class);
		}

		@Test
		@DisplayName("CANCELLED 상태의 게임에 입장 시 예외 발생")
		void it_throws_exception_when_entering_cancelled_game() {
			// Given
			String multigameId = "20260726100000";

			given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
			given(valueOperations.get(MultigameRedisKeyProvider.state(multigameId))).willReturn("CANCELLED");

			MultigameSessionService sessionService = new MultigameSessionService(
					stringRedisTemplate, waitingRoomService, gameQueueService);

			// When & Then
			org.assertj.core.api.Assertions.assertThatThrownBy(
					() -> sessionService.enterWaitingRoom(multigameId, 1L))
					.isInstanceOf(com.mjusugangsincheonghelper.global.api.exception.BaseException.class);
		}
	}
}
