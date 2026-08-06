package com.mjusugangsincheonghelper.global.api.code;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

	// ========================== 공통 (GLOBAL) ==========================
	/** 잘못된 요청 (400): 요청 형식이 올바르지 않을 때 */
	GLOBAL_BAD_REQUEST(HttpStatus.BAD_REQUEST, "GLOBAL_001", "Bad request."),
	/** 요청 파라미터 검증 실패 (400): @Valid 검증에 걸린 경우 */
	GLOBAL_VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "GLOBAL_002", "Validation failed."),
	/** 리소스를 찾을 수 없음 (404) */
	GLOBAL_NOT_FOUND(HttpStatus.NOT_FOUND, "GLOBAL_003", "Resource not found."),
	/** 서버 내부 오류 (500): 처리되지 않은 예외 발생 시 */
	GLOBAL_INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "GLOBAL_004", "Internal server error."),

	// ========================== 공통 보안 (GLOBAL_SECURITY) ==========================
	/** 인증되지 않은 접근 (401): 로그인/토큰 없이 접근 */
	GLOBAL_SECURITY_UNAUTHORIZED_ACCESS(HttpStatus.UNAUTHORIZED, "GLOBAL_SECURITY_001", "Unauthorized access."),
	/** 접근 권한 없음 (403): 인증은 되었으나 권한이 없는 리소스 접근 */
	GLOBAL_SECURITY_FORBIDDEN(HttpStatus.FORBIDDEN, "GLOBAL_SECURITY_002", "Access denied."),

	// ========================== 시스템 설정 (SYSTEM) ==========================
	/** 시스템 설정(Config)을 찾을 수 없음 (404) */
	SYSTEM_CONFIG_NOT_FOUND(HttpStatus.NOT_FOUND, "SYSTEM_001", "System config not found."),

	// ========================== 인증/회원 (AUTH) ==========================
	/** 개인정보 처리방침 동의 필요 (403) */
	AUTH_PRIVACY_POLICY_REQUIRED(HttpStatus.FORBIDDEN, "AUTH_001", "Privacy policy agreement is required."),
	/** Google OAuth 인증 실패 (401) */
	AUTH_GOOGLE_AUTH_FAILED(HttpStatus.UNAUTHORIZED, "AUTH_002", "Google authentication failed."),
	/** 리프레시 토큰이 유효하지 않거나 만료됨 (401) */
	AUTH_INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_004", "Invalid or expired refresh token."),
	/** 게스트 데이터 병합 필요 (409): 로그인 시 게스트 데이터가 남아 있는 경우 */
	AUTH_MERGE_REQUIRED(HttpStatus.CONFLICT, "AUTH_005", "Guest data merge is required."),
	/** 병합 티켓 만료 (400) */
	AUTH_MERGE_TICKET_EXPIRED(HttpStatus.BAD_REQUEST, "AUTH_006", "Merge ticket has expired."),
	/** 회원(Member)을 찾을 수 없음 (404) */
	AUTH_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH_007", "Member not found."),
	/** 게스트(Guest)를 찾을 수 없음 (404) */
	AUTH_GUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH_008", "Guest not found."),
	/** mju.ac.kr 도메인이 아닌 계정 (403): 명지대 계정만 허용 */
	AUTH_NOT_MJU_DOMAIN(HttpStatus.FORBIDDEN, "AUTH_010", "Only MJU (mju.ac.kr) accounts are allowed."),

	// ========================== 예시 (EXAMPLE) ==========================
	/** 예시 리소스를 찾을 수 없음 (404) */
	EXAMPLE_NOT_FOUND(HttpStatus.NOT_FOUND, "EXAMPLE_001", "Example not found."),
	/** 예시 리소스가 이미 존재함 (409) */
	EXAMPLE_ALREADY_EXISTS(HttpStatus.CONFLICT, "EXAMPLE_002", "Example already exists."),

	// ========================== 싱글 게임 (SINGLEGAME) ==========================
	/** 게임(세션)을 찾을 수 없음 (404) */
	SINGLEGAME_GAME_NOT_FOUND(HttpStatus.NOT_FOUND, "SINGLEGAME_001", "Game not found."),
	/** 총 과목 수가 허용 범위가 아님 (400): 허용 값 1, 3, 6, 7, 8 */
	SINGLEGAME_INVALID_TOTAL_COURSES(HttpStatus.BAD_REQUEST, "SINGLEGAME_002", "Invalid total courses. Allowed: 1, 3, 6, 7, 8."),
	/** 상세(details) 개수가 총 과목 수 또는 게임 완료 상태와 일치하지 않음 (400) */
	SINGLEGAME_INVALID_DETAILS_COUNT(HttpStatus.BAD_REQUEST, "SINGLEGAME_003", "Details count does not match total courses or game completion status."),
	/** 반응 시간이 유효 범위를 벗어남 (400) */
	SINGLEGAME_INVALID_REACTION_TIME(HttpStatus.BAD_REQUEST, "SINGLEGAME_004", "Reaction time is out of valid range."),

	// ========================== 수업 교환 (EXCHANGE) ==========================
	/** 교환 의도를 찾을 수 없음 (404) */
	EXCHANGE_INTENT_NOT_FOUND(HttpStatus.NOT_FOUND, "EXCHANGE_001", "Exchange intent not found."),
	/** 이미 삭제된 교환 의도에 접근 (400) */
	EXCHANGE_INTENT_ALREADY_DELETED(HttpStatus.BAD_REQUEST, "EXCHANGE_002", "Exchange intent already deleted."),
	/** 교환 의도의 소유자가 아님 (403) */
	EXCHANGE_INTENT_NOT_OWNER(HttpStatus.FORBIDDEN, "EXCHANGE_003", "Not the owner of this intent."),
	/** 교환 방을 찾을 수 없음 (404) */
	EXCHANGE_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "EXCHANGE_004", "Exchange room not found."),
	/** 교환 방의 멤버가 아님 (403) */
	EXCHANGE_ROOM_NOT_MEMBER(HttpStatus.FORBIDDEN, "EXCHANGE_005", "Not a member of this room."),
	/** 주고받을 과목 코드가 동일함 (400) */
	EXCHANGE_SAME_COURSE(HttpStatus.BAD_REQUEST, "EXCHANGE_006", "Give and want course numbers cannot be the same."),
	/** 동일한 교환 의도가 이미 존재함 (409) */
	EXCHANGE_DUPLICATE_INTENT(HttpStatus.CONFLICT, "EXCHANGE_007", "Same exchange intent already exists."),
	/** 메시지 내용이 비어 있음 (400) */
	EXCHANGE_MESSAGE_EMPTY(HttpStatus.BAD_REQUEST, "EXCHANGE_008", "Message content cannot be empty."),

	// ========================== PGMQ 메시지 큐 (PGMQ) ==========================
	/** 큐에 메시지 전송 실패 (500) */
	PGMQ_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "PGMQ_001", "Failed to send message to queue."),

	// ========================== 멀티 게임 (MULTIGAME) ==========================
	/** 게임 세션을 찾을 수 없음 (404) */
	MULTIGAME_GAME_NOT_FOUND(HttpStatus.NOT_FOUND, "MULTIGAME_001", "Game session not found."),
	/** 게임 상태가 해당 작업에 적합하지 않음 (409) */
	MULTIGAME_GAME_INVALID_STATE(HttpStatus.CONFLICT, "MULTIGAME_002", "Game is not in a valid state for this operation."),
	/** 게임이 취소됨 (410) */
	MULTIGAME_GAME_CANCELLED(HttpStatus.GONE, "MULTIGAME_003", "Game has been cancelled."),
	/** 게임 결과를 찾을 수 없음 (404) */
	MULTIGAME_RESULT_NOT_FOUND(HttpStatus.NOT_FOUND, "MULTIGAME_004", "Game result not found."),
	/** 게임 로직(Lua 스크립트) 실행 실패 (500) */
	MULTIGAME_LUA_SCRIPT_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "MULTIGAME_005", "Failed to execute game logic."),

	// ========================== 알림 (NOTIFICATION) ==========================
	/** FCM 알림 전송 실패 (500) */
	NOTIFICATION_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "NOTIFICATION_001", "Failed to send notification via FCM."),
	/** FCM 토큰을 찾을 수 없음 (404) */
	NOTIFICATION_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION_002", "FCM token not found."),

	// ========================== 공지 (NOTICE) ==========================
	/** 공지사항을 찾을 수 없음 (404) */
	NOTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTICE_001", "Notice not found.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
