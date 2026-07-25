package com.mjusugangsincheonghelper.global.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdvisoryLockService {

	private final JdbcTemplate jdbcTemplate;

	public boolean tryXactLock(String action, String t) {
		Boolean result = jdbcTemplate.queryForObject(
				"SELECT pg_try_advisory_xact_lock(?, ?)",
				Boolean.class,
				action.hashCode(),
				t.hashCode()
		);
		return Boolean.TRUE.equals(result);
	}

	public boolean trySessionLock(String action, String t) {
		Boolean result = jdbcTemplate.queryForObject(
				"SELECT pg_try_advisory_lock(?, ?)",
				Boolean.class,
				action.hashCode(),
				t.hashCode()
		);
		return Boolean.TRUE.equals(result);
	}

	public void releaseSessionLock(String action, String t) {
		jdbcTemplate.queryForObject(
				"SELECT pg_advisory_unlock(?, ?)",
				Boolean.class,
				action.hashCode(),
				t.hashCode()
		);
	}
}
