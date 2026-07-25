package com.mjusugangsincheonghelper.multigame.reservation.service;

import com.mjusugangsincheonghelper.database.entity.MultigameReservationEntity;
import com.mjusugangsincheonghelper.database.repository.MultigameReservationRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.multigame.common.GameTimeCalculator;
import com.mjusugangsincheonghelper.multigame.reservation.dto.MultigameReservationCreateRequest;
import com.mjusugangsincheonghelper.multigame.reservation.dto.MultigameReservationResponse;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MultigameReservationService {

	private static final long MIN_MINUTES_BEFORE_GAME = 10;
	private static final long MAX_DAYS_BEFORE_GAME = 7;

	private final MultigameReservationRepository reservationRepository;

	@Transactional
	public MultigameReservationResponse create(Long memberId, MultigameReservationCreateRequest request) {
		String multigameId = request.getMultigameId();
		LocalDateTime gameStart = GameTimeCalculator.parseT(multigameId);
		LocalDateTime now = LocalDateTime.now();

		long minutesBefore = ChronoUnit.MINUTES.between(now, gameStart);
		if (minutesBefore < MIN_MINUTES_BEFORE_GAME) {
			throw new BaseException(ErrorCode.MULTIGAME_RESERVATION_INVALID_TIME);
		}

		long daysBefore = ChronoUnit.DAYS.between(now, gameStart);
		if (daysBefore > MAX_DAYS_BEFORE_GAME) {
			throw new BaseException(ErrorCode.MULTIGAME_RESERVATION_INVALID_TIME);
		}

		if (reservationRepository.existsByStartTimeAndMemberId(multigameId, memberId)) {
			throw new BaseException(ErrorCode.MULTIGAME_RESERVATION_DUPLICATE);
		}

		MultigameReservationEntity entity = MultigameReservationEntity.builder()
				.memberId(memberId)
				.startTime(multigameId)
				.build();

		MultigameReservationEntity saved = reservationRepository.save(entity);
		return MultigameReservationResponse.from(saved);
	}

	public List<MultigameReservationResponse> getMyReservations(Long memberId) {
		return reservationRepository.findByMemberIdOrderByCreatedAtDesc(memberId)
				.stream()
				.map(MultigameReservationResponse::from)
				.toList();
	}

	public List<MultigameReservationResponse> getAllReservations() {
		return reservationRepository.findAll()
				.stream()
				.map(MultigameReservationResponse::from)
				.toList();
	}

	public List<MultigameReservationResponse> getReservationsByMultigameId(String multigameId) {
		return reservationRepository.findByStartTime(multigameId)
				.stream()
				.map(MultigameReservationResponse::from)
				.toList();
	}
}
