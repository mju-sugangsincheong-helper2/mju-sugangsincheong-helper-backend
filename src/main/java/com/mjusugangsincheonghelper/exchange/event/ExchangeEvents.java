package com.mjusugangsincheonghelper.exchange.event;

import java.util.List;

/**
 * 교환 쓰기 경로의 도메인 이벤트.
 *
 * <p>트랜잭션 안에서 발행되고, 커밋 후 {@link ExchangeEventListener}가
 * 캐시 evict·큐 적재·Firebase Cloud Messaging 발행 등 부작용을 수행한다. 롤백된 데이터에 대해
 * 부작용이 일어나지 않도록 Spring의 {@code @TransactionalEventListener} 기본
 * 위임(AFTER_COMMIT) 메커니즘을 그대로 사용한다.
 */
public final class ExchangeEvents {

	private ExchangeEvents() {
	}

	/** 교환 의도 등록. 피드/회원 캐시 갱신과 사이클 탐지 큐 적재가 필요하다. */
	public record IntentCreated(String term, Long intentId, Long memberId, String giveCourseNo, String wantCourseNo) {
	}

	/** 교환 의도 철회. 영향받은 방 메타와 참여자 메인 캐시 갱신이 필요하다. */
	public record IntentDeleted(String term, Long memberId, List<Long> roomIds, List<Long> memberIds) {
	}

	/** 교환 방 생성. 참여자 메인 캐시 갱신과 매칭 성공 알림이 필요하다. */
	public record RoomCreated(String term, Long roomId, List<Long> memberIds) {
	}

	/** 대화방 메시지 전송. 방 메타/참여자 메인 캐시 갱신과 수신자 알림이 필요하다. */
	public record RoomMessageSent(String term, Long roomId, Long senderMemberId, String content) {
	}

	/** 대화방 알림 ON/OFF 전환. 방 메타/참여자 메인 캐시 갱신이 필요하다. */
	public record RoomToggled(String term, Long roomId) {
	}

	/** 대화방 메시지 조회(읽음 처리). 해당 회원의 메인 캐시 갱신이 필요하다. */
	public record RoomViewed(String term, Long roomId, Long memberId) {
	}
}
