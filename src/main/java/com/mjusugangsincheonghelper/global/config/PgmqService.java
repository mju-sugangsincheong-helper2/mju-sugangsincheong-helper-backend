package com.mjusugangsincheonghelper.global.config;

import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PgmqService {

	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper;

	public void createQueue(String queueName) {
		jdbcTemplate.execute("SELECT pgmq.create('" + queueName + "');");

		try {
			String qTable = "pgmq.q_" + queueName;
			String aTable = "pgmq.a_" + queueName;

			String alterSql = "ALTER TABLE %s SET ("
					+ "fillfactor = 80, "
					+ "autovacuum_vacuum_scale_factor = 0.01, "
					+ "autovacuum_vacuum_threshold = 100, "
					+ "autovacuum_vacuum_cost_limit = 2000"
					+ ");";

			jdbcTemplate.execute(String.format(alterSql, qTable));
			jdbcTemplate.execute(String.format(alterSql, aTable));

			log.info("PGMQ queue '{}' created with autovacuum tuning", queueName);
		} catch (Exception e) {
			log.warn("PGMQ queue table tuning skipped (queue itself was created): {}", e.getMessage());
		}
	}

	public Long send(String queueName, Object payload) {
		try {
			String jsonPayload = objectMapper.writeValueAsString(payload);
			return jdbcTemplate.queryForObject(
					"SELECT pgmq.send(?::text, ?::jsonb)",
					Long.class,
					queueName,
					jsonPayload
			);
		} catch (JacksonException e) {
			throw new BaseException(ErrorCode.PGMQ_SEND_FAILED, e);
		}
	}

	public List<PgmqMessageDto> read(String queueName, int visibilityTimeout, int limit) {
		String sql = "SELECT msg_id, read_ct, message::text FROM pgmq.read(?, ?, ?)";
		return jdbcTemplate.query(sql, (rs, rowNum) -> PgmqMessageDto.builder()
				.msgId(rs.getLong("msg_id"))
				.readCt(rs.getInt("read_ct"))
				.message(rs.getString("message"))
				.build(), queueName, visibilityTimeout, limit);
	}

	public void delete(String queueName, Long msgId) {
		jdbcTemplate.queryForObject(
				"SELECT pgmq.delete(?, ?)",
				Boolean.class,
				queueName,
				msgId
		);
	}

	public void archive(String queueName, Long msgId) {
		jdbcTemplate.queryForObject(
				"SELECT pgmq.archive(?, ?)",
				Boolean.class,
				queueName,
				msgId
		);
	}
}
