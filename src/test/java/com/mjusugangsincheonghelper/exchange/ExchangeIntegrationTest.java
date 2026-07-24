package com.mjusugangsincheonghelper.exchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import com.mjusugangsincheonghelper.database.entity.ExchangeIntentEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomIntentEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomMessageEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomReadStatusEntity;
import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.repository.ExchangeIntentRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomIntentRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomMessageRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomReadStatusRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomRepository;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import com.mjusugangsincheonghelper.exchange.dto.IntentCreateRequest;
import com.mjusugangsincheonghelper.exchange.dto.MessageSendRequest;
import com.mjusugangsincheonghelper.exchange.dto.RoomToggleRequest;
import com.mjusugangsincheonghelper.exchange.service.ExchangeCycleDetector;
import com.mjusugangsincheonghelper.exchange.service.ExchangeService;
import com.mjusugangsincheonghelper.system.service.SystemConfigService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Exchange 통합 테스트")
class ExchangeIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ExchangeIntentRepository intentRepository;

	@Autowired
	private ExchangeRoomRepository roomRepository;

	@Autowired
	private ExchangeRoomIntentRepository roomIntentRepository;

	@Autowired
	private ExchangeRoomMessageRepository messageRepository;

	@Autowired
	private ExchangeRoomReadStatusRepository readStatusRepository;

	@Autowired
	private SystemConfigService systemConfigService;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private ExchangeService exchangeService;

	@Autowired
	private ExchangeCycleDetector cycleDetector;

	@Autowired
	private StringRedisTemplate stringRedisTemplate;

	private Member testMember;

	@BeforeEach
	void setUp() {
		readStatusRepository.deleteAll();
		messageRepository.deleteAll();
		roomIntentRepository.deleteAll();
		roomRepository.deleteAll();
		intentRepository.deleteAll();
		memberRepository.deleteAll();

		stringRedisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
			connection.serverCommands().flushDb();
			return null;
		});

		testMember = memberRepository.save(Member.builder()
				.role(Member.Role.MEMBER)
				.name("테스트유저")
				.department("컴퓨터공학과")
				.build());

		UsernamePasswordAuthenticationToken authentication =
				new UsernamePasswordAuthenticationToken(testMember.getId(), null,
						java.util.Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_MEMBER")));
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
		readStatusRepository.deleteAll();
		messageRepository.deleteAll();
		roomIntentRepository.deleteAll();
		roomRepository.deleteAll();
		intentRepository.deleteAll();
		memberRepository.deleteAll();
	}

	@Nested
	@DisplayName("교환 의도 등록 API는")
	class Describe_createIntent {

		@Test
		@DisplayName("유효한 요청으로 의도를 등록할 수 있다")
		void it_creates_intent() throws Exception {
			// Given
			IntentCreateRequest request = IntentCreateRequest.builder()
					.giveCourseNo("10001")
					.wantCourseNo("10002")
					.build();

			// When & Then
			mockMvc.perform(post("/api/v1/exchange/intents")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.giveCourseNo").value("10001"))
					.andExpect(jsonPath("$.data.wantCourseNo").value("10002"))
					.andExpect(jsonPath("$.data.deleted").value(false));

			// Verify DB
			String term = systemConfigService.getCurrentTerm();
			List<ExchangeIntentEntity> intents = intentRepository.findByTermAndIsDeletedFalse(term);
			assertThat(intents).hasSize(1);
			assertThat(intents.get(0).getGiveCourseNo()).isEqualTo("10001");
		}

		@Test
		@DisplayName("같은 과목 교환은 불가능하다")
		void it_rejects_same_course() throws Exception {
			// Given
			IntentCreateRequest request = IntentCreateRequest.builder()
					.giveCourseNo("10001")
					.wantCourseNo("10001")
					.build();

			// When & Then
			mockMvc.perform(post("/api/v1/exchange/intents")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code").value("EXCHANGE_006"));
		}
	}

	@Nested
	@DisplayName("메인 화면 조회 API는")
	class Describe_getMain {

		@Test
		@DisplayName("나의 의도와 방 목록을 반환한다")
		void it_returns_my_intents_and_rooms() throws Exception {
			// Given
			String term = systemConfigService.getCurrentTerm();
			ExchangeIntentEntity intent = ExchangeIntentEntity.builder()
					.term(term)
					.memberId(testMember.getId())
					.giveCourseNo("10001")
					.wantCourseNo("10002")
					.build();
			intentRepository.save(intent);

			// When & Then
			mockMvc.perform(get("/api/v1/exchange/main"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.myIntents").isArray())
					.andExpect(jsonPath("$.data.myRooms").isArray())
					.andExpect(jsonPath("$.data.recentIntents").isArray());
		}
	}

	@Nested
	@DisplayName("3자 간 순환 매칭 통합 테스트")
	class Describe_threePartyCycleMatching {

		@Test
		@DisplayName("3명이 순환 매칭되면 방이 생성되고 참여자 매핑/읽음 상태/웰컴 메시지가 생성된다")
		void it_creates_room_with_all_related_data() {
			// Given
			String term = systemConfigService.getCurrentTerm();

			Member memberA = memberRepository.save(Member.builder().role(Member.Role.MEMBER).name("A").department("A").build());
			Member memberB = memberRepository.save(Member.builder().role(Member.Role.MEMBER).name("B").department("B").build());
			Member memberC = memberRepository.save(Member.builder().role(Member.Role.MEMBER).name("C").department("C").build());

			ExchangeIntentEntity intentA = intentRepository.save(ExchangeIntentEntity.builder()
					.term(term).memberId(memberA.getId()).giveCourseNo("10001").wantCourseNo("10002").build());
			ExchangeIntentEntity intentB = intentRepository.save(ExchangeIntentEntity.builder()
					.term(term).memberId(memberB.getId()).giveCourseNo("10002").wantCourseNo("10003").build());

			// When: C가 등록하여 사이클 완성 (10003 -> 10001)
			ExchangeIntentEntity intentC = ExchangeIntentEntity.builder()
					.term(term).memberId(memberC.getId()).giveCourseNo("10003").wantCourseNo("10001").build();
			intentC = intentRepository.save(intentC);

			cycleDetector.detectCyclesAndCreateRooms(term, intentC.getId(), memberC.getId(), "10003", "10001");

			// Then
			List<ExchangeRoomEntity> rooms = roomRepository.findAll();
			assertThat(rooms).hasSize(1);
			ExchangeRoomEntity room = rooms.get(0);
			assertThat(room.getStatus()).isEqualTo("ACTIVE");
			assertThat(room.isActive()).isTrue();

			List<ExchangeRoomIntentEntity> roomIntents = roomIntentRepository.findByTermAndRoomId(term, room.getId());
			assertThat(roomIntents).hasSize(3);
			assertThat(roomIntents).allMatch(ri -> !ri.isDeleted() && ri.isOn());

			List<ExchangeRoomMessageEntity> messages = messageRepository.findAll();
			assertThat(messages).anyMatch(m -> m.getContent().contains("[시스템] 교환 매칭이 성사되었습니다!"));

			List<ExchangeRoomReadStatusEntity> readStatuses = readStatusRepository.findAll();
			assertThat(readStatuses).hasSize(3);
		}
	}

	@Nested
	@DisplayName("대화방 메시지 전송 및 읽음 상태 동기화 통합 테스트")
	class Describe_messageAndReadStatusSync {

		@Test
		@DisplayName("메시지 전송 시 발신자 읽음 상태가 갱신되고 수신자 안읽음 수가 증가한다")
		void it_updates_read_status_on_message_send() throws Exception {
			// Given
			String term = systemConfigService.getCurrentTerm();
			Member memberA = memberRepository.save(Member.builder().role(Member.Role.MEMBER).name("A").department("A").build());
			Member memberB = memberRepository.save(Member.builder().role(Member.Role.MEMBER).name("B").department("B").build());

			ExchangeRoomEntity room = roomRepository.save(ExchangeRoomEntity.builder()
					.term(term).cycleHash("hash1").status("ACTIVE").isActive(true).build());

			ExchangeIntentEntity intentA = intentRepository.save(ExchangeIntentEntity.builder()
					.term(term).memberId(memberA.getId()).giveCourseNo("10001").wantCourseNo("10002").build());
			ExchangeIntentEntity intentB = intentRepository.save(ExchangeIntentEntity.builder()
					.term(term).memberId(memberB.getId()).giveCourseNo("10002").wantCourseNo("10001").build());

			roomIntentRepository.save(ExchangeRoomIntentEntity.builder()
					.term(term).roomId(room.getId()).intentId(intentA.getId()).memberId(memberA.getId()).build());
			roomIntentRepository.save(ExchangeRoomIntentEntity.builder()
					.term(term).roomId(room.getId()).intentId(intentB.getId()).memberId(memberB.getId()).build());

			readStatusRepository.save(ExchangeRoomReadStatusEntity.builder()
					.term(term).roomId(room.getId()).memberId(memberA.getId()).intentId(intentA.getId()).build());
			readStatusRepository.save(ExchangeRoomReadStatusEntity.builder()
					.term(term).roomId(room.getId()).memberId(memberB.getId()).intentId(intentB.getId()).build());

			// When: A가 메시지 전송
			SecurityContextHolder.getContext().setAuthentication(
					new UsernamePasswordAuthenticationToken(memberA.getId(), null,
							List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_MEMBER"))));

			MessageSendRequest request = MessageSendRequest.builder().content("안녕하세요").build();
			mockMvc.perform(post("/api/v1/exchange/rooms/" + room.getId() + "/messages")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isCreated());

			// Then
			ExchangeRoomReadStatusEntity aReadStatus = readStatusRepository.findById(
					new ExchangeRoomReadStatusEntity.ExchangeRoomReadStatusId(term, room.getId(), memberA.getId())).orElseThrow();
			assertThat(aReadStatus.getLastReadMessageId()).isGreaterThan(0L);

			int unreadForB = messageRepository.countByTermAndRoomIdAndIdGreaterThan(
					term, room.getId(),
					readStatusRepository.findById(
							new ExchangeRoomReadStatusEntity.ExchangeRoomReadStatusId(term, room.getId(), memberB.getId())).orElseThrow().getLastReadMessageId());
			assertThat(unreadForB).isEqualTo(1);
		}
	}

	@Nested
	@DisplayName("의사 철회 및 대화방 순차 비활성화 통합 테스트")
	class Describe_intentDeleteAndRoomDeactivation {

		@Test
		@DisplayName("2인 방에서 1명 철회 시 방이 ALL_DELETE로 비활성화된다")
		void it_deactivates_2_person_room_on_intent_delete() {
			// Given
			String term = systemConfigService.getCurrentTerm();
			Member memberA = memberRepository.save(Member.builder().role(Member.Role.MEMBER).name("A").department("A").build());
			Member memberB = memberRepository.save(Member.builder().role(Member.Role.MEMBER).name("B").department("B").build());

			ExchangeIntentEntity intentA = intentRepository.save(ExchangeIntentEntity.builder()
					.term(term).memberId(memberA.getId()).giveCourseNo("10001").wantCourseNo("10002").build());
			ExchangeIntentEntity intentB = intentRepository.save(ExchangeIntentEntity.builder()
					.term(term).memberId(memberB.getId()).giveCourseNo("10002").wantCourseNo("10001").build());

			ExchangeRoomEntity room = roomRepository.save(ExchangeRoomEntity.builder()
					.term(term).cycleHash("hash1").status("ACTIVE").isActive(true).build());

			roomIntentRepository.save(ExchangeRoomIntentEntity.builder()
					.term(term).roomId(room.getId()).intentId(intentA.getId()).memberId(memberA.getId()).build());
			roomIntentRepository.save(ExchangeRoomIntentEntity.builder()
					.term(term).roomId(room.getId()).intentId(intentB.getId()).memberId(memberB.getId()).build());

			// When: A가 의사 철회
			SecurityContextHolder.getContext().setAuthentication(
					new UsernamePasswordAuthenticationToken(memberA.getId(), null,
							List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_MEMBER"))));

			exchangeService.deleteIntent(memberA.getId(), intentA.getId());

			// Then
			ExchangeRoomEntity updatedRoom = roomRepository.findById(
					new ExchangeRoomEntity.ExchangeRoomId(term, room.getId())).orElseThrow();
			assertThat(updatedRoom.getStatus()).isEqualTo("ALL_DELETE");
			assertThat(updatedRoom.isActive()).isFalse();

			List<ExchangeRoomIntentEntity> roomIntents = roomIntentRepository.findByTermAndRoomId(term, room.getId());
			assertThat(roomIntents).anyMatch(ri -> ri.getIntentId().equals(intentA.getId()) && ri.isDeleted());
		}

		@Test
		@DisplayName("3인 방에서 1명 철회 시 방이 PARTIAL_DELETE로 유지된다")
		void it_keeps_3_person_room_active_on_one_intent_delete() {
			// Given
			String term = systemConfigService.getCurrentTerm();
			Member memberA = memberRepository.save(Member.builder().role(Member.Role.MEMBER).name("A").department("A").build());
			Member memberB = memberRepository.save(Member.builder().role(Member.Role.MEMBER).name("B").department("B").build());
			Member memberC = memberRepository.save(Member.builder().role(Member.Role.MEMBER).name("C").department("C").build());

			ExchangeIntentEntity intentA = intentRepository.save(ExchangeIntentEntity.builder()
					.term(term).memberId(memberA.getId()).giveCourseNo("10001").wantCourseNo("10002").build());
			ExchangeIntentEntity intentB = intentRepository.save(ExchangeIntentEntity.builder()
					.term(term).memberId(memberB.getId()).giveCourseNo("10002").wantCourseNo("10003").build());
			ExchangeIntentEntity intentC = intentRepository.save(ExchangeIntentEntity.builder()
					.term(term).memberId(memberC.getId()).giveCourseNo("10003").wantCourseNo("10001").build());

			ExchangeRoomEntity room = roomRepository.save(ExchangeRoomEntity.builder()
					.term(term).cycleHash("hash1").status("ACTIVE").isActive(true).build());

			roomIntentRepository.save(ExchangeRoomIntentEntity.builder()
					.term(term).roomId(room.getId()).intentId(intentA.getId()).memberId(memberA.getId()).build());
			roomIntentRepository.save(ExchangeRoomIntentEntity.builder()
					.term(term).roomId(room.getId()).intentId(intentB.getId()).memberId(memberB.getId()).build());
			roomIntentRepository.save(ExchangeRoomIntentEntity.builder()
					.term(term).roomId(room.getId()).intentId(intentC.getId()).memberId(memberC.getId()).build());

			// When: A가 의사 철회
			SecurityContextHolder.getContext().setAuthentication(
					new UsernamePasswordAuthenticationToken(memberA.getId(), null,
							List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_MEMBER"))));

			exchangeService.deleteIntent(memberA.getId(), intentA.getId());

			// Then
			ExchangeRoomEntity updatedRoom = roomRepository.findById(
					new ExchangeRoomEntity.ExchangeRoomId(term, room.getId())).orElseThrow();
			assertThat(updatedRoom.getStatus()).isEqualTo("PARTIAL_DELETE");
			assertThat(updatedRoom.isActive()).isTrue();
		}
	}

	@Nested
	@DisplayName("피드에서 삭제된 카드 배제 테스트")
	class Describe_deletedCardExcludedFromFeed {

		@Test
		@DisplayName("철회된 카드는 최근 피드 API에서 조회되지 않는다")
		void it_excludes_deleted_cards_from_feed() throws Exception {
			// Given
			String term = systemConfigService.getCurrentTerm();

			ExchangeIntentEntity activeIntent = intentRepository.save(ExchangeIntentEntity.builder()
					.term(term).memberId(testMember.getId()).giveCourseNo("10001").wantCourseNo("10002").build());

			ExchangeIntentEntity deletedIntent = intentRepository.save(ExchangeIntentEntity.builder()
					.term(term).memberId(testMember.getId()).giveCourseNo("10003").wantCourseNo("10004").build());
			deletedIntent.markDeleted();
			intentRepository.save(deletedIntent);

			// When & Then
			mockMvc.perform(get("/api/v1/exchange/intents/recent"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.intents").isArray())
					.andExpect(jsonPath("$.data.intents[0].intentId").value(activeIntent.getId()));

			List<ExchangeIntentEntity> allIntents = intentRepository.findByTermAndIsDeletedFalse(term);
			assertThat(allIntents).hasSize(1);
			assertThat(allIntents.get(0).getId()).isEqualTo(activeIntent.getId());
		}
	}

	@Nested
	@DisplayName("시나리오 1: 교환 성공 및 실시간 조율 여정")
	class Describe_scenario1_exchangeSuccessAndRealtimeCoordination {

		@Test
		@DisplayName("2인 매칭 → 방 생성 → A 메시지 전송 → B 읽음 처리 → B 답장 → A 읽음 처리까지 전체 흐름")
		void it_completes_full_exchange_flow() throws Exception {
			// Given
			String term = systemConfigService.getCurrentTerm();
			Member memberA = memberRepository.save(Member.builder().role(Member.Role.MEMBER).name("A").department("A").build());
			Member memberB = memberRepository.save(Member.builder().role(Member.Role.MEMBER).name("B").department("B").build());

			ExchangeIntentEntity intentA = intentRepository.save(ExchangeIntentEntity.builder()
					.term(term).memberId(memberA.getId()).giveCourseNo("10023").wantCourseNo("40101").build());
			ExchangeIntentEntity intentB = intentRepository.save(ExchangeIntentEntity.builder()
					.term(term).memberId(memberB.getId()).giveCourseNo("40101").wantCourseNo("10023").build());

			// When: 사이클 감지 → 방 생성
			cycleDetector.detectCyclesAndCreateRooms(term, intentB.getId(), memberB.getId(), "40101", "10023");

			List<ExchangeRoomEntity> rooms = roomRepository.findAll();
			assertThat(rooms).hasSize(1);
			ExchangeRoomEntity room = rooms.get(0);
			assertThat(room.getStatus()).isEqualTo("ACTIVE");

			// Then: 웰컴 메시지 확인
			List<ExchangeRoomMessageEntity> welcomeMessages = messageRepository.findAll();
			assertThat(welcomeMessages).anyMatch(m -> m.getContent().contains("[시스템] 교환 매칭이 성사되었습니다!"));

			// When: A가 첫 메시지 전송
			exchangeService.sendMessage(memberA.getId(), room.getId(),
					MessageSendRequest.builder().content("안녕하세요. 혹시 지금 교환 가능하신가요?").build());

			// Then: B의 안읽음 메시지 수 = 1
			int unreadForB = messageRepository.countByTermAndRoomIdAndIdGreaterThan(
					term, room.getId(),
					readStatusRepository.findById(
							new ExchangeRoomReadStatusEntity.ExchangeRoomReadStatusId(term, room.getId(), memberB.getId())).orElseThrow().getLastReadMessageId());
			assertThat(unreadForB).isEqualTo(1);

			// When: B가 방 입장 (getMessages = 읽음 처리)
			exchangeService.getMessages(memberB.getId(), room.getId(), null, 20);

			// Then: B의 읽음 상태가 최신 메시지로 갱신됨
			ExchangeRoomReadStatusEntity bReadStatus = readStatusRepository.findById(
					new ExchangeRoomReadStatusEntity.ExchangeRoomReadStatusId(term, room.getId(), memberB.getId())).orElseThrow();
			assertThat(bReadStatus.getLastReadMessageId()).isGreaterThan(0L);

			// B의 안읽음 메시지 수 = 0
			int unreadForBAfterEntry = messageRepository.countByTermAndRoomIdAndIdGreaterThan(
					term, room.getId(), bReadStatus.getLastReadMessageId());
			assertThat(unreadForBAfterEntry).isEqualTo(0);

			// When: B가 답장 전송
			exchangeService.sendMessage(memberB.getId(), room.getId(),
					MessageSendRequest.builder().content("네, 지금 에브리타임 확인하면서 바로 진행해요").build());

			// Then: A의 안읽음 메시지 수 = 1
			int unreadForA = messageRepository.countByTermAndRoomIdAndIdGreaterThan(
					term, room.getId(),
					readStatusRepository.findById(
							new ExchangeRoomReadStatusEntity.ExchangeRoomReadStatusId(term, room.getId(), memberA.getId())).orElseThrow().getLastReadMessageId());
			assertThat(unreadForA).isEqualTo(1);

			// When: A가 방 입장
			exchangeService.getMessages(memberA.getId(), room.getId(), null, 20);

			// Then: A의 안읽음 메시지 수 = 0
			int unreadForAAfterEntry = messageRepository.countByTermAndRoomIdAndIdGreaterThan(
					term, room.getId(),
					readStatusRepository.findById(
							new ExchangeRoomReadStatusEntity.ExchangeRoomReadStatusId(term, room.getId(), memberA.getId())).orElseThrow().getLastReadMessageId());
			assertThat(unreadForAAfterEntry).isEqualTo(0);
		}
	}

	@Nested
	@DisplayName("시나리오 2: 다자간 매칭 중 일부 이탈 여정")
	class Describe_scenario2_multiPartyPartialWithdrawal {

		@Test
		@DisplayName("3인 방에서 A철회(PARTIAL_DELETE+시스템메시지) → B철회(ALL_DELETE+시스템메시지) 연속 흐름")
		void it_cascades_partial_then_all_delete_with_system_messages() {
			// Given
			String term = systemConfigService.getCurrentTerm();
			Member memberA = memberRepository.save(Member.builder().role(Member.Role.MEMBER).name("A").department("A").build());
			Member memberB = memberRepository.save(Member.builder().role(Member.Role.MEMBER).name("B").department("B").build());
			Member memberC = memberRepository.save(Member.builder().role(Member.Role.MEMBER).name("C").department("C").build());

			ExchangeIntentEntity intentA = intentRepository.save(ExchangeIntentEntity.builder()
					.term(term).memberId(memberA.getId()).giveCourseNo("10001").wantCourseNo("10002").build());
			ExchangeIntentEntity intentB = intentRepository.save(ExchangeIntentEntity.builder()
					.term(term).memberId(memberB.getId()).giveCourseNo("10002").wantCourseNo("10003").build());
			ExchangeIntentEntity intentC = intentRepository.save(ExchangeIntentEntity.builder()
					.term(term).memberId(memberC.getId()).giveCourseNo("10003").wantCourseNo("10001").build());

			ExchangeRoomEntity room = roomRepository.save(ExchangeRoomEntity.builder()
					.term(term).cycleHash("hash_scenario2").status("ACTIVE").isActive(true).build());

			roomIntentRepository.save(ExchangeRoomIntentEntity.builder()
					.term(term).roomId(room.getId()).intentId(intentA.getId()).memberId(memberA.getId()).build());
			roomIntentRepository.save(ExchangeRoomIntentEntity.builder()
					.term(term).roomId(room.getId()).intentId(intentB.getId()).memberId(memberB.getId()).build());
			roomIntentRepository.save(ExchangeRoomIntentEntity.builder()
					.term(term).roomId(room.getId()).intentId(intentC.getId()).memberId(memberC.getId()).build());

			// When 1: A가 철회
			SecurityContextHolder.getContext().setAuthentication(
					new UsernamePasswordAuthenticationToken(memberA.getId(), null,
							List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_MEMBER"))));
			exchangeService.deleteIntent(memberA.getId(), intentA.getId());

			// Then 1: PARTIAL_DELETE + 시스템 메시지
			ExchangeRoomEntity afterA = roomRepository.findById(
					new ExchangeRoomEntity.ExchangeRoomId(term, room.getId())).orElseThrow();
			assertThat(afterA.getStatus()).isEqualTo("PARTIAL_DELETE");
			assertThat(afterA.isActive()).isTrue();

			List<ExchangeRoomMessageEntity> msgsAfterA = messageRepository.findAll();
			assertThat(msgsAfterA).anyMatch(m -> m.getContent().contains("[시스템] 일부 참여자가 교환 의사를 철회하였습니다."));

			// When 2: B마저 철회
			SecurityContextHolder.getContext().setAuthentication(
					new UsernamePasswordAuthenticationToken(memberB.getId(), null,
							List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_MEMBER"))));
			exchangeService.deleteIntent(memberB.getId(), intentB.getId());

			// Then 2: ALL_DELETE + 시스템 종료 메시지
			ExchangeRoomEntity afterB = roomRepository.findById(
					new ExchangeRoomEntity.ExchangeRoomId(term, room.getId())).orElseThrow();
			assertThat(afterB.getStatus()).isEqualTo("ALL_DELETE");
			assertThat(afterB.isActive()).isFalse();

			List<ExchangeRoomMessageEntity> msgsAfterB = messageRepository.findAll();
			assertThat(msgsAfterB).anyMatch(m -> m.getContent().contains("[시스템] 참여자의 교환 의사 철회로 인해 대화방이 비활성화되었습니다."));
		}
	}

	@Nested
	@DisplayName("시나리오 3: 방 숨기기(OFF) 토글 후 상대방 인지 여정")
	class Describe_scenario3_roomHideToggleAndAwareness {

		@Test
		@DisplayName("A가 OFF → PARTIAL_OFF 전이 → A가 ON 복귀 → ACTIVE 전이")
		void it_transitions_partial_off_then_back_to_active() {
			// Given
			String term = systemConfigService.getCurrentTerm();
			Member memberA = memberRepository.save(Member.builder().role(Member.Role.MEMBER).name("A").department("A").build());
			Member memberB = memberRepository.save(Member.builder().role(Member.Role.MEMBER).name("B").department("B").build());

			ExchangeIntentEntity intentA = intentRepository.save(ExchangeIntentEntity.builder()
					.term(term).memberId(memberA.getId()).giveCourseNo("10001").wantCourseNo("10002").build());
			ExchangeIntentEntity intentB = intentRepository.save(ExchangeIntentEntity.builder()
					.term(term).memberId(memberB.getId()).giveCourseNo("10002").wantCourseNo("10001").build());

			ExchangeRoomEntity room = roomRepository.save(ExchangeRoomEntity.builder()
					.term(term).cycleHash("hash_scenario3").status("ACTIVE").isActive(true).build());

			ExchangeRoomIntentEntity riA = roomIntentRepository.save(ExchangeRoomIntentEntity.builder()
					.term(term).roomId(room.getId()).intentId(intentA.getId()).memberId(memberA.getId()).build());
			roomIntentRepository.save(ExchangeRoomIntentEntity.builder()
					.term(term).roomId(room.getId()).intentId(intentB.getId()).memberId(memberB.getId()).build());

			// When 1: A가 방 OFF
			SecurityContextHolder.getContext().setAuthentication(
					new UsernamePasswordAuthenticationToken(memberA.getId(), null,
							List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_MEMBER"))));
			exchangeService.toggleRoom(memberA.getId(), room.getId(), RoomToggleRequest.builder().isOn(false).build());

			// Then 1: PARTIAL_OFF
			ExchangeRoomEntity afterOff = roomRepository.findById(
					new ExchangeRoomEntity.ExchangeRoomId(term, room.getId())).orElseThrow();
			assertThat(afterOff.getStatus()).isEqualTo("PARTIAL_OFF");
			assertThat(afterOff.isActive()).isTrue();

			ExchangeRoomIntentEntity riAAfterOff = roomIntentRepository.findByTermAndRoomIdAndMemberId(term, room.getId(), memberA.getId()).get(0);
			assertThat(riAAfterOff.isOn()).isFalse();

			// When 2: A가 방 ON 복귀
			exchangeService.toggleRoom(memberA.getId(), room.getId(), RoomToggleRequest.builder().isOn(true).build());

			// Then 2: ACTIVE
			ExchangeRoomEntity afterOn = roomRepository.findById(
					new ExchangeRoomEntity.ExchangeRoomId(term, room.getId())).orElseThrow();
			assertThat(afterOn.getStatus()).isEqualTo("ACTIVE");
			assertThat(afterOn.isActive()).isTrue();

			ExchangeRoomIntentEntity riAAfterOn = roomIntentRepository.findByTermAndRoomIdAndMemberId(term, room.getId(), memberA.getId()).get(0);
			assertThat(riAAfterOn.isOn()).isTrue();
		}
	}

	@Nested
	@DisplayName("무한 스크롤(cursor 기반 피드 페이징) 테스트")
	class Describe_infiniteScrollFeedPagination {

		@Test
		@DisplayName("cursor 파라미터로 피드를 조회하면 빈 결과가 아닌 데이터를 반환한다")
		void it_loads_feed_with_cursor_pagination() {
			// Given
			String term = systemConfigService.getCurrentTerm();

			for (int i = 1; i <= 5; i++) {
				intentRepository.save(ExchangeIntentEntity.builder()
						.term(term)
						.memberId(testMember.getId())
						.giveCourseNo("100" + String.format("%02d", i))
						.wantCourseNo("200" + String.format("%02d", i))
						.build());
			}

			// When: 첫 페이지 조회
			var firstPage = exchangeService.getRecentIntents(null, 2);

			// Then: 결과가 반환되고 cursor가 존재한다
			assertThat(firstPage.getIntents()).isNotEmpty();
			assertThat(firstPage.getNextLastIntentId()).isGreaterThan(0L);
		}
	}

	@Nested
	@DisplayName("방 입장 시 읽음 처리 통합 테스트")
	class Describe_roomEntryReadStatusUpdate {

		@Test
		@DisplayName("안읽음 메시지가 있는 방에 입장하면 lastReadMessageId가 최신 메시지로 갱신된다")
		void it_updates_last_read_message_id_on_room_entry() throws Exception {
			// Given
			String term = systemConfigService.getCurrentTerm();
			Member memberA = memberRepository.save(Member.builder().role(Member.Role.MEMBER).name("A").department("A").build());
			Member memberB = memberRepository.save(Member.builder().role(Member.Role.MEMBER).name("B").department("B").build());

			ExchangeRoomEntity room = roomRepository.save(ExchangeRoomEntity.builder()
					.term(term).cycleHash("hash_read").status("ACTIVE").isActive(true).build());

			ExchangeIntentEntity intentA = intentRepository.save(ExchangeIntentEntity.builder()
					.term(term).memberId(memberA.getId()).giveCourseNo("10001").wantCourseNo("10002").build());
			ExchangeIntentEntity intentB = intentRepository.save(ExchangeIntentEntity.builder()
					.term(term).memberId(memberB.getId()).giveCourseNo("10002").wantCourseNo("10001").build());

			roomIntentRepository.save(ExchangeRoomIntentEntity.builder()
					.term(term).roomId(room.getId()).intentId(intentA.getId()).memberId(memberA.getId()).build());
			roomIntentRepository.save(ExchangeRoomIntentEntity.builder()
					.term(term).roomId(room.getId()).intentId(intentB.getId()).memberId(memberB.getId()).build());

			readStatusRepository.save(ExchangeRoomReadStatusEntity.builder()
					.term(term).roomId(room.getId()).memberId(memberA.getId()).intentId(intentA.getId()).build());
			readStatusRepository.save(ExchangeRoomReadStatusEntity.builder()
					.term(term).roomId(room.getId()).memberId(memberB.getId()).intentId(intentB.getId()).build());

			// A가 메시지 3개 전송
			SecurityContextHolder.getContext().setAuthentication(
					new UsernamePasswordAuthenticationToken(memberA.getId(), null,
							List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_MEMBER"))));

			for (int i = 1; i <= 3; i++) {
				MessageSendRequest req = MessageSendRequest.builder().content("메시지 " + i).build();
				mockMvc.perform(post("/api/v1/exchange/rooms/" + room.getId() + "/messages")
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(req)))
						.andExpect(status().isCreated());
			}

			// Then: B의 안읽음 수 = 3
			int unreadBefore = messageRepository.countByTermAndRoomIdAndIdGreaterThan(
					term, room.getId(),
					readStatusRepository.findById(
							new ExchangeRoomReadStatusEntity.ExchangeRoomReadStatusId(term, room.getId(), memberB.getId())).orElseThrow().getLastReadMessageId());
			assertThat(unreadBefore).isEqualTo(3);

			// When: B가 방 입장
			exchangeService.getMessages(memberB.getId(), room.getId(), null, 20);

			// Then: B의 안읽음 수 = 0
			int unreadAfter = messageRepository.countByTermAndRoomIdAndIdGreaterThan(
					term, room.getId(),
					readStatusRepository.findById(
							new ExchangeRoomReadStatusEntity.ExchangeRoomReadStatusId(term, room.getId(), memberB.getId())).orElseThrow().getLastReadMessageId());
			assertThat(unreadAfter).isEqualTo(0);
		}
	}
}
