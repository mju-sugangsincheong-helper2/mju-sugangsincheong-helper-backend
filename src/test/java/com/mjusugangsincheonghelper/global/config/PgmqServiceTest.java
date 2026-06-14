package com.mjusugangsincheonghelper.global.config;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("PgmqService 통합 테스트")
class PgmqServiceTest {

	@Autowired
	private PgmqService pgmqService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private static final String TEST_QUEUE = "test_queue";

	@BeforeEach
	void setUp() {
		try {
			jdbcTemplate.execute("SELECT pgmq.drop_queue('" + TEST_QUEUE + "');");
		} catch (Exception ignored) {
		}
	}

	@AfterEach
	void tearDown() {
		try {
			jdbcTemplate.execute("SELECT pgmq.drop_queue('" + TEST_QUEUE + "');");
		} catch (Exception ignored) {
		}
	}

	@Nested
	@DisplayName("createQueue 메서드는")
	class CreateQueueTest {

		@Test
		@DisplayName("큐를 생성한다")
		void createQueue() {
			pgmqService.createQueue(TEST_QUEUE);

			Long result = jdbcTemplate.queryForObject(
					"SELECT COUNT(*) FROM pgmq.q_" + TEST_QUEUE,
					Long.class
			);

			assertThat(result).isZero();
		}
	}

	@Nested
	@DisplayName("send 메서드는")
	class SendTest {

		@Test
		@DisplayName("메시지를 발송하고 msgId를 반환한다")
		void send() {
			pgmqService.createQueue(TEST_QUEUE);

			Map<String, String> payload = Map.of("key", "value");
			Long msgId = pgmqService.send(TEST_QUEUE, payload);

			assertThat(msgId).isNotNull();
			assertThat(msgId).isGreaterThan(0);
		}
	}

	@Nested
	@DisplayName("read 메서드는")
	class ReadTest {

		@Test
		@DisplayName("발송한 메시지를 읽을 수 있다")
		void read() {
			pgmqService.createQueue(TEST_QUEUE);

			Map<String, String> payload = Map.of("name", "test");
			pgmqService.send(TEST_QUEUE, payload);

			List<PgmqMessageDto> messages = pgmqService.read(TEST_QUEUE, 30, 1);

			assertThat(messages).hasSize(1);
			assertThat(messages.get(0).getMsgId()).isNotNull();
			assertThat(messages.get(0).getReadCt()).isEqualTo(1);
			assertThat(messages.get(0).getMessage()).contains("name");
			assertThat(messages.get(0).getMessage()).contains("test");
		}

		@Test
		@DisplayName("빈 큐에서는 빈 리스트를 반환한다")
		void readEmpty() {
			pgmqService.createQueue(TEST_QUEUE);

			List<PgmqMessageDto> messages = pgmqService.read(TEST_QUEUE, 30, 1);

			assertThat(messages).isEmpty();
		}

		@Test
		@DisplayName("limit 개수만큼만 읽는다")
		void readWithLimit() {
			pgmqService.createQueue(TEST_QUEUE);

			pgmqService.send(TEST_QUEUE, Map.of("id", 1));
			pgmqService.send(TEST_QUEUE, Map.of("id", 2));
			pgmqService.send(TEST_QUEUE, Map.of("id", 3));

			List<PgmqMessageDto> messages = pgmqService.read(TEST_QUEUE, 30, 2);

			assertThat(messages).hasSize(2);
		}
	}

	@Nested
	@DisplayName("delete 메서드는")
	class DeleteTest {

		@Test
		@DisplayName("메시지를 삭제한다")
		void delete() {
			pgmqService.createQueue(TEST_QUEUE);

			Long msgId = pgmqService.send(TEST_QUEUE, Map.of("key", "value"));
			List<PgmqMessageDto> messages = pgmqService.read(TEST_QUEUE, 30, 1);
			assertThat(messages).hasSize(1);

			pgmqService.delete(TEST_QUEUE, msgId);

			List<PgmqMessageDto> afterDelete = pgmqService.read(TEST_QUEUE, 30, 1);
			assertThat(afterDelete).isEmpty();
		}
	}

	@Nested
	@DisplayName("archive 메서드는")
	class ArchiveTest {

		@Test
		@DisplayName("메시지를 아카이브하면 원본 큐에서 제거된다")
		void archive() {
			pgmqService.createQueue(TEST_QUEUE);

			Long msgId = pgmqService.send(TEST_QUEUE, Map.of("key", "value"));
			List<PgmqMessageDto> messages = pgmqService.read(TEST_QUEUE, 30, 1);
			assertThat(messages).hasSize(1);

			pgmqService.archive(TEST_QUEUE, msgId);

			List<PgmqMessageDto> afterArchive = pgmqService.read(TEST_QUEUE, 30, 1);
			assertThat(afterArchive).isEmpty();

			Long archiveCount = jdbcTemplate.queryForObject(
					"SELECT COUNT(*) FROM pgmq.a_" + TEST_QUEUE,
					Long.class
			);
			assertThat(archiveCount).isEqualTo(1);
		}
	}
}
