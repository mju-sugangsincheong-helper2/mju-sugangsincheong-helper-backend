package com.mjusugangsincheonghelper.global.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdvisoryLockService {

	private final JdbcTemplate jdbcTemplate;
	private final DataSource dataSource;

	public boolean tryXactLock(String action, String t) {
		Boolean result = jdbcTemplate.queryForObject(
				"SELECT pg_try_advisory_xact_lock(?, ?)",
				Boolean.class,
				action.hashCode(),
				t.hashCode()
		);
		return Boolean.TRUE.equals(result);
	}

	/**
	 * Keeps the database connection open for the lifetime of a PostgreSQL session lock.
	 * A pooled JdbcTemplate connection cannot safely own a session-level lock after the
	 * query returns to the pool.
	 */
	public SessionLock trySessionLockHeld(String action, String t) {
		Connection connection = null;
		try {
			connection = dataSource.getConnection();
			try (PreparedStatement statement = connection.prepareStatement("SELECT pg_try_advisory_lock(?, ?)")) {
				statement.setInt(1, action.hashCode());
				statement.setInt(2, t.hashCode());
				try (ResultSet result = statement.executeQuery()) {
					if (result.next() && result.getBoolean(1)) {
						return new SessionLock(connection, action.hashCode(), t.hashCode());
					}
				}
			}
			connection.close();
			return null;
		} catch (SQLException exception) {
			if (connection != null) {
				try {
					connection.close();
				} catch (SQLException closeException) {
					exception.addSuppressed(closeException);
				}
			}
			throw new IllegalStateException("Failed to acquire PostgreSQL session lock", exception);
		}
	}

	public static final class SessionLock implements AutoCloseable {
		private final Connection connection;
		private final int actionKey;
		private final int roundKey;

		private SessionLock(Connection connection, int actionKey, int roundKey) {
			this.connection = connection;
			this.actionKey = actionKey;
			this.roundKey = roundKey;
		}

		@Override
		public void close() {
			try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_unlock(?, ?)")) {
				statement.setInt(1, actionKey);
				statement.setInt(2, roundKey);
				statement.execute();
			} catch (SQLException exception) {
				throw new IllegalStateException("Failed to release PostgreSQL session lock", exception);
			} finally {
				try {
					connection.close();
				} catch (SQLException exception) {
					log.warn("Failed to return PostgreSQL session lock connection", exception);
				}
			}
		}
	}
}
