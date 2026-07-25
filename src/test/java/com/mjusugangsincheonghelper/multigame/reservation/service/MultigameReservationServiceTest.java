package com.mjusugangsincheonghelper.multigame.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.mjusugangsincheonghelper.database.entity.MultigameReservationEntity;
import com.mjusugangsincheonghelper.database.repository.MultigameReservationRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.multigame.reservation.dto.MultigameReservationCreateRequest;
import com.mjusugangsincheonghelper.multigame.reservation.dto.MultigameReservationResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MultigameReservationService 테스트")
class MultigameReservationServiceTest {

	@Mock
	private MultigameReservationRepository reservationRepository;

	@InjectMocks
	private MultigameReservationService reservationService;

	private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	@Nested
	@DisplayName("create 메서드는")
	class Describe_create {

		@Test
		@DisplayName("유효한 요청이면 예약을 생성하고 응답을 반환한다")
		void it_creates_reservation_with_valid_request() {
			Long memberId = 1L;
			String futureTime = LocalDateTime.now().plusDays(1).withMinute(10).withSecond(0)
					.format(FORMATTER);
			MultigameReservationCreateRequest request = MultigameReservationCreateRequest.builder()
					.multigameId(futureTime)
					.build();

			MultigameReservationEntity savedEntity = MultigameReservationEntity.builder()
					.memberId(memberId)
					.startTime(futureTime)
					.build();

			given(reservationRepository.existsByStartTimeAndMemberId(futureTime, memberId)).willReturn(false);
			given(reservationRepository.save(any(MultigameReservationEntity.class))).willReturn(savedEntity);

			MultigameReservationResponse response = reservationService.create(memberId, request);

			assertThat(response.getMultigameId()).isEqualTo(futureTime);
			verify(reservationRepository).save(any(MultigameReservationEntity.class));
		}

		@Test
		@DisplayName("게임 시작 10분 미만이면 예외를 발생시킨다")
		void it_throws_exception_when_less_than_10_minutes_before_game() {
			Long memberId = 1L;
			String nearFutureTime = LocalDateTime.now().plusMinutes(5)
					.format(FORMATTER);
			MultigameReservationCreateRequest request = MultigameReservationCreateRequest.builder()
					.multigameId(nearFutureTime)
					.build();

			assertThatThrownBy(() -> reservationService.create(memberId, request))
					.isInstanceOf(BaseException.class)
					.satisfies(ex -> assertThat(((BaseException) ex).getErrorCode())
							.isEqualTo(ErrorCode.MULTIGAME_RESERVATION_INVALID_TIME));
		}

		@Test
		@DisplayName("7일 초과 미래면 예외를 발생시킨다")
		void it_throws_exception_when_more_than_7_days_in_future() {
			Long memberId = 1L;
			LocalDateTime farFuture = LocalDateTime.now().plusDays(10).withHour(12).withMinute(0).withSecond(0);
			String farFutureTime = farFuture.format(FORMATTER);
			MultigameReservationCreateRequest request = MultigameReservationCreateRequest.builder()
					.multigameId(farFutureTime)
					.build();

			assertThatThrownBy(() -> reservationService.create(memberId, request))
					.isInstanceOf(BaseException.class)
					.satisfies(ex -> assertThat(((BaseException) ex).getErrorCode())
							.isEqualTo(ErrorCode.MULTIGAME_RESERVATION_INVALID_TIME));
		}

		@Test
		@DisplayName("중복 예약이면 예외를 발생시킨다")
		void it_throws_exception_when_duplicate_reservation() {
			Long memberId = 1L;
			String futureTime = LocalDateTime.now().plusDays(1).withMinute(10).withSecond(0)
					.format(FORMATTER);
			MultigameReservationCreateRequest request = MultigameReservationCreateRequest.builder()
					.multigameId(futureTime)
					.build();

			given(reservationRepository.existsByStartTimeAndMemberId(futureTime, memberId)).willReturn(true);

			assertThatThrownBy(() -> reservationService.create(memberId, request))
					.isInstanceOf(BaseException.class)
					.satisfies(ex -> assertThat(((BaseException) ex).getErrorCode())
							.isEqualTo(ErrorCode.MULTIGAME_RESERVATION_DUPLICATE));

			verify(reservationRepository, never()).save(any());
		}
	}

	@Nested
	@DisplayName("getMyReservations 메서드는")
	class Describe_getMyReservations {

		@Test
		@DisplayName("memberId로 예약 목록을 조회한다")
		void it_returns_reservations_by_memberId() {
			Long memberId = 1L;
			MultigameReservationEntity entity = MultigameReservationEntity.builder()
					.memberId(memberId)
					.startTime("20260630120000")
					.build();

			given(reservationRepository.findByMemberIdOrderByCreatedAtDesc(memberId))
					.willReturn(List.of(entity));

			List<MultigameReservationResponse> result = reservationService.getMyReservations(memberId);

			assertThat(result).hasSize(1);
			assertThat(result.get(0).getMemberId()).isEqualTo(memberId);
		}
	}

	@Nested
	@DisplayName("getAllReservations 메서드는")
	class Describe_getAllReservations {

		@Test
		@DisplayName("전체 예약 목록을 조회한다")
		void it_returns_all_reservations() {
			MultigameReservationEntity entity1 = MultigameReservationEntity.builder()
					.memberId(1L)
					.startTime("20260630120000")
					.build();
			MultigameReservationEntity entity2 = MultigameReservationEntity.builder()
					.memberId(2L)
					.startTime("20260630121000")
					.build();

			given(reservationRepository.findAll()).willReturn(List.of(entity1, entity2));

			List<MultigameReservationResponse> result = reservationService.getAllReservations();

			assertThat(result).hasSize(2);
		}
	}

	@Nested
	@DisplayName("getReservationsByMultigameId 메서드는")
	class Describe_getReservationsByMultigameId {

		@Test
		@DisplayName("특정 게임의 예약 목록을 조회한다")
		void it_returns_reservations_by_multigameId() {
			String multigameId = "20260630120000";
			MultigameReservationEntity entity = MultigameReservationEntity.builder()
					.memberId(1L)
					.startTime(multigameId)
					.build();

			given(reservationRepository.findByStartTime(multigameId)).willReturn(List.of(entity));

			List<MultigameReservationResponse> result = reservationService.getReservationsByMultigameId(multigameId);

			assertThat(result).hasSize(1);
			assertThat(result.get(0).getMultigameId()).isEqualTo(multigameId);
		}
	}
}
