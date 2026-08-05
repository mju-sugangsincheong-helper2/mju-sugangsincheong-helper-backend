package com.mjusugangsincheonghelper.exchange.controller;

import com.mjusugangsincheonghelper.exchange.dto.IntentCreateRequest;
import com.mjusugangsincheonghelper.exchange.dto.IntentCreateResponse;
import com.mjusugangsincheonghelper.exchange.dto.IntentDeleteResponse;
import com.mjusugangsincheonghelper.exchange.dto.MainResponse;
import com.mjusugangsincheonghelper.exchange.dto.MessageResponse;
import com.mjusugangsincheonghelper.exchange.dto.MessageSendRequest;
import com.mjusugangsincheonghelper.exchange.dto.MessageSendResponse;
import com.mjusugangsincheonghelper.exchange.dto.RecentIntentsResponse;
import com.mjusugangsincheonghelper.exchange.dto.RoomToggleRequest;
import com.mjusugangsincheonghelper.exchange.dto.RoomToggleResponse;
import com.mjusugangsincheonghelper.exchange.service.ExchangeService;
import com.mjusugangsincheonghelper.global.annotation.OperationErrorCodes;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.envelope.SingleSuccessResponseEnvelope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Exchange", description = "수강신청 과목 교환 API")
@RestController
@PreAuthorize("hasRole('MEMBER')")
@RequiredArgsConstructor
@RequestMapping("/api/{version}/exchange")
public class ExchangeController {

	private final ExchangeService exchangeService;

	@PostMapping(value = "/intents", version = "1+")
	@Operation(
			summary = "Exchange intent create",
			description = "교환 의사를 등록합니다. 버릴 개설 강좌 식별 코드와 원하는 개설 강좌 식별 코드를 지정합니다.",
			responses = {
					@ApiResponse(responseCode = "201", description = "등록 성공")
			}
	)
	@OperationErrorCodes({
			ErrorCode.EXCHANGE_SAME_COURSE,
			ErrorCode.EXCHANGE_DUPLICATE_INTENT,
			ErrorCode.GLOBAL_VALIDATION_ERROR,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<IntentCreateResponse>> createIntent(
			@Valid @RequestBody IntentCreateRequest request) {
		Long memberId = getCurrentMemberId();
		IntentCreateResponse response = exchangeService.createIntent(memberId, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(SingleSuccessResponseEnvelope.of(response));
	}

	@DeleteMapping(value = "/intents/{intentId}", version = "1+")
	@Operation(
			summary = "Exchange intent delete",
			description = "등록한 교환 의사를 철회합니다.",
			responses = {
					@ApiResponse(responseCode = "200", description = "철회 성공")
			}
	)
	@OperationErrorCodes({
			ErrorCode.EXCHANGE_INTENT_NOT_FOUND,
			ErrorCode.EXCHANGE_INTENT_NOT_OWNER,
			ErrorCode.EXCHANGE_INTENT_ALREADY_DELETED,
			ErrorCode.EXCHANGE_ROOM_NOT_FOUND,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<IntentDeleteResponse>> deleteIntent(
			@Parameter(description = "교환 의도 ID", example = "10524")
			@PathVariable("intentId") Long intentId) {
		Long memberId = getCurrentMemberId();
		IntentDeleteResponse response = exchangeService.deleteIntent(memberId, intentId);
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(response));
	}

	@GetMapping(value = "/main", version = "1+")
	@Operation(
			summary = "Exchange main status",
			description = "메인 화면 상태를 5초 주기로 조회합니다. 내 의도 목록 및 각 의도에 연결된 채팅방 상세 목록, 최근 피드를 통합 반환합니다.",
			responses = {
					@ApiResponse(responseCode = "200", description = "조회 성공")
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<MainResponse>> getMain() {
		Long memberId = getCurrentMemberId();
		MainResponse response = exchangeService.getMain(memberId);
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(response));
	}

	@GetMapping(value = "/intents/recent", version = "1+")
	@PreAuthorize("permitAll()") // 공개 GET API (PUBLIC_GET_URLS에 등록됨, 클래스 레벨 hasRole('MEMBER') 무시)
	@Operation(
			summary = "Recent exchange intents",
			description = "최근 등록된 교환 의사 리스트(최대 50개)를 단순 조회합니다.",
			responses = {
					@ApiResponse(responseCode = "200", description = "조회 성공")
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<RecentIntentsResponse>> getRecentIntents() {
		RecentIntentsResponse response = exchangeService.getRecentIntents();
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(response));
	}

	@GetMapping(value = "/rooms/{roomId}/messages", version = "1+")
	@Operation(
			summary = "Room messages",
			description = "채팅방 이전 메시지 내역을 역방향 무한 스크롤(beforeMessageId 커서) 방식으로 조회합니다. 조회 시 읽음 처리도 함께 수행됩니다.",
			responses = {
					@ApiResponse(responseCode = "200", description = "조회 성공")
			}
	)
	@OperationErrorCodes({
			ErrorCode.EXCHANGE_ROOM_NOT_MEMBER,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<MessageResponse>> getMessages(
			@Parameter(description = "채팅방 ID", example = "402")
			@PathVariable("roomId") Long roomId,
			@Parameter(description = "기준 메시지 ID (미전달 시 최신 메시지부터)", example = "55102")
			@RequestParam(name = "beforeMessageId", required = false) Long beforeMessageId,
			@Parameter(description = "조회할 개수", example = "20")
			@RequestParam(name = "size", defaultValue = "20") int size) {
		Long memberId = getCurrentMemberId();
		MessageResponse response = exchangeService.getMessages(memberId, roomId, beforeMessageId, size);
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(response));
	}

	@PostMapping(value = "/rooms/{roomId}/messages", version = "1+")
	@Operation(
			summary = "Send room message",
			description = "채팅방에 메시지를 전송합니다.",
			responses = {
					@ApiResponse(responseCode = "201", description = "전송 성공")
			}
	)
	@OperationErrorCodes({
			ErrorCode.EXCHANGE_ROOM_NOT_MEMBER,
			ErrorCode.EXCHANGE_INTENT_ALREADY_DELETED,
			ErrorCode.GLOBAL_VALIDATION_ERROR,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<MessageSendResponse>> sendMessage(
			@Parameter(description = "채팅방 ID", example = "402")
			@PathVariable("roomId") Long roomId,
			@Valid @RequestBody MessageSendRequest request) {
		Long memberId = getCurrentMemberId();
		MessageSendResponse response = exchangeService.sendMessage(memberId, roomId, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(SingleSuccessResponseEnvelope.of(response));
	}

	@PatchMapping(value = "/rooms/{roomId}/toggle", version = "1+")
	@Operation(
			summary = "Room toggle ON/OFF",
			description = "특정 방의 알림 수신 및 목록 노출 여부를 ON/OFF로 전환합니다.",
			responses = {
					@ApiResponse(responseCode = "200", description = "토글 성공")
			}
	)
	@OperationErrorCodes({
			ErrorCode.EXCHANGE_ROOM_NOT_MEMBER,
			ErrorCode.EXCHANGE_ROOM_NOT_FOUND,
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<RoomToggleResponse>> toggleRoom(
			@Parameter(description = "채팅방 ID", example = "402")
			@PathVariable("roomId") Long roomId,
			@RequestBody RoomToggleRequest request) {
		Long memberId = getCurrentMemberId();
		RoomToggleResponse response = exchangeService.toggleRoom(memberId, roomId, request);
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(response));
	}

	private Long getCurrentMemberId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return (Long) authentication.getPrincipal();
	}
}
