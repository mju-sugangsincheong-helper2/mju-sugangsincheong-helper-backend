package com.mjusugangsincheonghelper.multigame.reservation.service;

import com.mjusugangsincheonghelper.database.entity.MultigameReservationEntity;
import com.mjusugangsincheonghelper.database.repository.MultigameReservationRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.multigame.common.GameTimeCalculator;
import com.mjusugangsincheonghelper.multigame.reservation.dto.MultigameReservationCreateRequest;
import com.mjusugangsincheonghelper.multigame.reservation.dto.MultigameReservationResponse;
import com.mjusugangsincheonghelper.multigame.session.service.DevGameInitializer;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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
	
	// ===== 운영 환경: null (빈 Optional) =====
	// ===== 개발 환경: DevGameInitializer 빈 주입 =====
	private final Optional<DevGameInitializer> devGameInitializer;

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

		MultigameReservationEntity saved;
		try {
			saved = reservationRepository.saveAndFlush(MultigameReservationEntity.builder()
					.memberId(memberId)
					.startTime(multigameId)
					.build());
		} catch (DataIntegrityViolationException e) {
			throw new BaseException(ErrorCode.MULTIGAME_RESERVATION_DUPLICATE);
		}

		// ===== 개발 환경 전용 로직 =====
		// dev 프로필에서만 DevGameInitializer 빈이 존재하므로,
		// 예약 생성 시 즉시 WAITING 상태로 초기화하여 테스트 가능하게 함
		// 운영 환경에서는 LifecycleScheduler가 T-5m에 자동으로 초기화
		devGameInitializer.ifPresent(initializer -> {
			int participantCount = reservationRepository.findByStartTime(multigameId).size();
			initializer.initializeGame(multigameId, participantCount);
			log.info("[DEV] 예약 생성 시 게임 자동 초기화: multigameId={}, participantCount={}", multigameId, participantCount);
		});

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
