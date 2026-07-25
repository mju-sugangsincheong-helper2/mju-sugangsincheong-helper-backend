package com.mjusugangsincheonghelper.multigame.reservation.controller;

import com.mjusugangsincheonghelper.global.annotation.OperationErrorCodes;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.envelope.SingleSuccessResponseEnvelope;
import com.mjusugangsincheonghelper.multigame.reservation.dto.MultigameReservationCreateRequest;
import com.mjusugangsincheonghelper.multigame.reservation.dto.MultigameReservationResponse;
import com.mjusugangsincheonghelper.multigame.reservation.service.MultigameReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Multigame Reservation", description = "멀티게임 예약 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/{version}/multigame")
@PreAuthorize("hasRole('GUEST')")
public class MultigameReservationController {

	private final MultigameReservationService reservationService;

	@PostMapping(value = "/reservations", version = "1+")
	@Operation(
			summary = "Create reservation",
			description = "멀티게임 예약 생성 API",
			responses = {
					@ApiResponse(responseCode = "200", description = "예약 성공")
			}
	)
	@OperationErrorCodes({
			ErrorCode.MULTIGAME_RESERVATION_INVALID_TIME,
			ErrorCode.MULTIGAME_RESERVATION_DUPLICATE,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<MultigameReservationResponse>> create(
			@Valid @RequestBody MultigameReservationCreateRequest request) {
		Long memberId = (Long) org.springframework.security.core.context.SecurityContextHolder.getContext()
				.getAuthentication().getPrincipal();
		MultigameReservationResponse response = reservationService.create(memberId, request);
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(response));
	}

	@GetMapping(value = "/reservations/my", version = "1+")
	@Operation(
			summary = "Get my reservations",
			description = "내 멀티게임 예약 목록 조회 API",
			responses = {
					@ApiResponse(responseCode = "200", description = "조회 성공")
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<List<MultigameReservationResponse>>> getMyReservations() {
		Long memberId = (Long) org.springframework.security.core.context.SecurityContextHolder.getContext()
				.getAuthentication().getPrincipal();
		List<MultigameReservationResponse> response = reservationService.getMyReservations(memberId);
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(response));
	}

	@GetMapping(value = "/reservations", version = "1+")
	@Operation(
			summary = "Get all reservations",
			description = "전체 멀티게임 예약 목록 조회 API (특정 게임 필터링 가능)",
			responses = {
					@ApiResponse(responseCode = "200", description = "조회 성공")
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<List<MultigameReservationResponse>>> getAllReservations(
			@Parameter(description = "멀티게임 ID (14자리, 선택적 필터)", example = "20260630120000")
			@RequestParam(required = false) String multigameId) {
		List<MultigameReservationResponse> response = (multigameId != null)
				? reservationService.getReservationsByMultigameId(multigameId)
				: reservationService.getAllReservations();
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(response));
	}
}
