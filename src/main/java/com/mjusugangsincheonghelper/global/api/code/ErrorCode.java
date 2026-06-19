package com.mjusugangsincheonghelper.global.api.code;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

	GLOBAL_BAD_REQUEST(HttpStatus.BAD_REQUEST, "GLOBAL_001", "Bad request."),
	GLOBAL_VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "GLOBAL_002", "Validation failed."),
	GLOBAL_NOT_FOUND(HttpStatus.NOT_FOUND, "GLOBAL_003", "Resource not found."),
	GLOBAL_INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "GLOBAL_004", "Internal server error."),

	GLOBAL_SECURITY_UNAUTHORIZED_ACCESS(HttpStatus.UNAUTHORIZED, "GLOBAL_SECURITY_001", "Unauthorized access."),
	GLOBAL_SECURITY_FORBIDDEN(HttpStatus.FORBIDDEN, "GLOBAL_SECURITY_002", "Access denied."),

	SYSTEM_CONFIG_NOT_FOUND(HttpStatus.NOT_FOUND, "SYSTEM_001", "System config not found."),

	AUTH_PRIVACY_POLICY_REQUIRED(HttpStatus.FORBIDDEN, "AUTH_001", "Privacy policy agreement is required."),
	AUTH_GOOGLE_AUTH_FAILED(HttpStatus.UNAUTHORIZED, "AUTH_002", "Google authentication failed."),
	AUTH_INVALID_TOKEN_SIGNATURE(HttpStatus.UNAUTHORIZED, "AUTH_003", "Invalid token signature."),
	AUTH_INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_004", "Invalid or expired refresh token."),
	AUTH_MERGE_REQUIRED(HttpStatus.CONFLICT, "AUTH_005", "Guest data merge is required."),
	AUTH_MERGE_TICKET_EXPIRED(HttpStatus.BAD_REQUEST, "AUTH_006", "Merge ticket has expired."),
	AUTH_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH_007", "Member not found."),
	AUTH_GUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH_008", "Guest not found."),
	AUTH_ALREADY_EXISTS(HttpStatus.CONFLICT, "AUTH_009", "Auth key already exists."),
	AUTH_NOT_MJU_DOMAIN(HttpStatus.FORBIDDEN, "AUTH_010", "Only MJU (mju.ac.kr) accounts are allowed."),

	EXAMPLE_NOT_FOUND(HttpStatus.NOT_FOUND, "EXAMPLE_001", "Example not found."),
	EXAMPLE_ALREADY_EXISTS(HttpStatus.CONFLICT, "EXAMPLE_002", "Example already exists."),

	SINGLEGAME_GAME_NOT_FOUND(HttpStatus.NOT_FOUND, "SINGLEGAME_001", "Game not found."),
	SINGLEGAME_INVALID_TOTAL_COURSES(HttpStatus.BAD_REQUEST, "SINGLEGAME_002", "Invalid total courses. Allowed: 1, 3, 6, 7, 8."),
	SINGLEGAME_INVALID_DETAILS_COUNT(HttpStatus.BAD_REQUEST, "SINGLEGAME_003", "Details count does not match total courses or game completion status."),
	SINGLEGAME_INVALID_REACTION_TIME(HttpStatus.BAD_REQUEST, "SINGLEGAME_004", "Reaction time is out of valid range."),

	EXCHANGE_INTENT_NOT_FOUND(HttpStatus.NOT_FOUND, "EXCHANGE_001", "Exchange intent not found."),
	EXCHANGE_INTENT_ALREADY_DELETED(HttpStatus.BAD_REQUEST, "EXCHANGE_002", "Exchange intent already deleted."),
	EXCHANGE_INTENT_NOT_OWNER(HttpStatus.FORBIDDEN, "EXCHANGE_003", "Not the owner of this intent."),
	EXCHANGE_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "EXCHANGE_004", "Exchange room not found."),
	EXCHANGE_ROOM_NOT_MEMBER(HttpStatus.FORBIDDEN, "EXCHANGE_005", "Not a member of this room."),
	EXCHANGE_SAME_COURSE(HttpStatus.BAD_REQUEST, "EXCHANGE_006", "Give and want course numbers cannot be the same."),
	EXCHANGE_DUPLICATE_INTENT(HttpStatus.CONFLICT, "EXCHANGE_007", "Same exchange intent already exists."),
	EXCHANGE_MESSAGE_EMPTY(HttpStatus.BAD_REQUEST, "EXCHANGE_008", "Message content cannot be empty."),

	PGMQ_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "PGMQ_001", "Failed to send message to queue."),
	PGMQ_QUEUE_NOT_FOUND(HttpStatus.NOT_FOUND, "PGMQ_002", "Queue not found."),
	PGMQ_DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "PGMQ_003", "Failed to delete message from queue."),
	PGMQ_ARCHIVE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "PGMQ_004", "Failed to archive message.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
