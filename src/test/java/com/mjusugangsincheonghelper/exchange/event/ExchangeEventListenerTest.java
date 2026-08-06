package com.mjusugangsincheonghelper.exchange.event;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.mjusugangsincheonghelper.database.entity.ExchangeRoomIntentEntity;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomIntentRepository;
import com.mjusugangsincheonghelper.exchange.dto.CycleDetectionMessage;
import com.mjusugangsincheonghelper.exchange.service.ExchangeCacheService;
import com.mjusugangsincheonghelper.exchange.service.ExchangeCycleDetector;
import com.mjusugangsincheonghelper.notification.publisher.NotificationPublisher;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExchangeEventListener 단위 테스트")
class ExchangeEventListenerTest {

	private static final String TERM = "202620";

	@Mock
	private ExchangeCacheService cacheService;

	@Mock
	private ExchangeCycleDetector cycleDetector;

	@Mock
	private ExchangeRoomIntentRepository roomIntentRepository;

	@Mock
	private NotificationPublisher notificationPublisher;

	@InjectMocks
	private ExchangeEventListener listener;

	private static ExchangeRoomIntentEntity roomIntent(Long roomId, Long memberId, boolean isOn, boolean isDeleted) {
		ExchangeRoomIntentEntity entity = ExchangeRoomIntentEntity.builder()
				.term(TERM)
				.roomId(roomId)
				.intentId(memberId * 10)
				.memberId(memberId)
				.build();
		if (!isOn) {
			entity.toggle(false);
		}
		if (isDeleted) {
			entity.markDeleted();
		}
		return entity;
	}

	@Test
	@DisplayName("IntentCreated: 피드/메인/의도 캐시를 evict하고 사이클 탐지를 적재한다")
	void onIntentCreatedEvictsAndEnqueues() {
		listener.onIntentCreated(new ExchangeEvents.IntentCreated(TERM, 100L, 1L, "10001", "10002"));

		verify(cacheService).evictFeed(TERM);
		verify(cacheService).evictMainCache(TERM, 1L);
		verify(cacheService).evictMemberIntents(TERM, 1L);

		ArgumentCaptor<CycleDetectionMessage> captor = ArgumentCaptor.forClass(CycleDetectionMessage.class);
		verify(cycleDetector).enqueueCycleDetection(captor.capture());
		CycleDetectionMessage message = captor.getValue();
		assertThat(message.getTerm()).isEqualTo(TERM);
		assertThat(message.getIntentId()).isEqualTo(100L);
		assertThat(message.getGiveCourseNo()).isEqualTo("10001");
		assertThat(message.getWantCourseNo()).isEqualTo("10002");
	}

	@Test
	@DisplayName("IntentDeleted: 철회자 본인은 방 참여 여부와 무관하게 항상 main 캐시가 evict된다")
	void onIntentDeletedAlwaysEvictsActor() {
		listener.onIntentDeleted(new ExchangeEvents.IntentDeleted(TERM, 1L, List.of(), List.of()));

		verify(cacheService).evictFeed(TERM);
		verify(cacheService).evictMemberIntents(TERM, 1L);
		verify(cacheService).evictMainCache(TERM, 1L);
	}

	@Test
	@DisplayName("IntentDeleted: 영향받은 방 메타와 참여자(본인 제외 중복 없이) 메인 캐시를 evict한다")
	void onIntentDeletedEvictsAffected() {
		listener.onIntentDeleted(new ExchangeEvents.IntentDeleted(TERM, 1L, List.of(200L, 201L), List.of(1L, 2L, 3L)));

		verify(cacheService).evictRoomMeta(TERM, 200L);
		verify(cacheService).evictRoomMeta(TERM, 201L);
		verify(cacheService).evictMainCache(TERM, 1L);
		verify(cacheService).evictMainCache(TERM, 2L);
		verify(cacheService).evictMainCache(TERM, 3L);
	}

	@Test
	@DisplayName("RoomCreated: 참여자 메인 캐시를 evict하고 매칭 성공 알림을 발행한다")
	void onRoomCreatedEvictsAndNotifies() {
		listener.onRoomCreated(new ExchangeEvents.RoomCreated(TERM, 400L, List.of(1L, 2L)));

		verify(cacheService).evictMainCache(TERM, 1L);
		verify(cacheService).evictMainCache(TERM, 2L);
		verify(notificationPublisher).publishToMembers(List.of(1L, 2L), "EXCHANGE_ROOM",
				"/exchange/rooms/400", "수강신청 교환 매칭 성공", "[시스템] 교환 매칭이 성사되었습니다!");
	}

	@Test
	@DisplayName("RoomMessageSent: 방 메타/참여자 메인 캐시를 evict하고 발신자·OFF·삭제 제외 수신자에게만 알림한다")
	void onRoomMessageSentEvictsAndFiltersTargets() {
		given(roomIntentRepository.findByTermAndRoomId(TERM, 400L)).willReturn(List.of(
				roomIntent(400L, 1L, true, false),   // 발신자 — 알림 대상 아님
				roomIntent(400L, 2L, true, false),   // 활성 수신자
				roomIntent(400L, 3L, false, false),  // OFF — 대상 아님
				roomIntent(400L, 4L, true, true)     // 삭제됨 — 대상 아님
		));

		listener.onRoomMessageSent(new ExchangeEvents.RoomMessageSent(TERM, 400L, 1L, "안녕하세요"));

		verify(cacheService).evictRoomMeta(TERM, 400L);
		verify(cacheService).evictMainCache(TERM, 1L);
		verify(cacheService).evictMainCache(TERM, 2L);
		verify(cacheService).evictMainCache(TERM, 3L);
		verify(cacheService).evictMainCache(TERM, 4L);
		verify(notificationPublisher).publishToMembers(List.of(2L), "EXCHANGE_MESSAGE",
				"/exchange/rooms/400", "수강신청 교환 대화방 메시지", "안녕하세요");
	}

	@Test
	@DisplayName("RoomMessageSent: 유효한 수신자가 없으면 알림을 발행하지 않는다")
	void onRoomMessageSentSkipsWhenNoTargets() {
		given(roomIntentRepository.findByTermAndRoomId(TERM, 400L)).willReturn(List.of(
				roomIntent(400L, 1L, true, false)
		));

		listener.onRoomMessageSent(new ExchangeEvents.RoomMessageSent(TERM, 400L, 1L, "혼자 말하는 중"));

		verify(cacheService).evictRoomMeta(TERM, 400L);
		verifyNoInteractions(notificationPublisher);
	}

	@Test
	@DisplayName("RoomToggled: 방 메타와 참여자 메인 캐시를 evict한다")
	void onRoomToggledEvicts() {
		given(roomIntentRepository.findDistinctMemberIdsByTermAndRoomId(TERM, 400L))
				.willReturn(List.of(1L, 2L));

		listener.onRoomToggled(new ExchangeEvents.RoomToggled(TERM, 400L));

		verify(cacheService).evictRoomMeta(TERM, 400L);
		verify(cacheService).evictMainCache(TERM, 1L);
		verify(cacheService).evictMainCache(TERM, 2L);
	}

	@Test
	@DisplayName("RoomViewed: 조회자 본인의 메인 캐시만 evict한다")
	void onRoomViewedEvictsViewer() {
		listener.onRoomViewed(new ExchangeEvents.RoomViewed(TERM, 400L, 7L));

		verify(cacheService).evictMainCache(TERM, 7L);
		verify(notificationPublisher, never()).publishToMembers(
				org.mockito.ArgumentMatchers.anyList(),
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.anyString());
	}
}
