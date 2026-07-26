package com.mjusugangsincheonghelper.multigame.common;

import java.util.List;
import org.springframework.data.redis.core.script.DefaultRedisScript;

public final class MultigameLuaScript {

	private MultigameLuaScript() {
	}

	/**
	 * Redis Lua Script - 게임 신청 처리
	 *
	 * 반환 형식 (Positional Array):
	 * - BLOCKED:      {'BLOCKED', current_state}
	 * - PENDING:      {'PENDING', seq, limit}
	 * - SUCCESS:      {'SUCCESS', subject_id, remaining}
	 * - FAIL_SOLDOUT: {'FAIL_SOLDOUT', subject_id}
	 * - FAIL_DUPLICATE: {'FAIL_DUPLICATE', subject_id}
	 *
	 * 참고: Redis Lua에서 associative array({key=value})를 반환하면
	 * RESP2 프로토콜에서 numeric index만 포함하고 string key는 무시됩니다.
	 * 따라서 positional array를 사용하여 모든 값을 안전하게 반환합니다.
	 */
	public static final String SCRIPT = """
			-- 파라미터: KEYS[1]=state_key, KEYS[2]=queue_key, KEYS[3]=seq_key,
			--           KEYS[4]=limit_key, KEYS[5]=seats_key, KEYS[6]=history_key,
			--           KEYS[7]=success_members_key
			--           ARGV[1]=member(유저ID), ARGV[2]=subject_id(과목 1~6), ARGV[3]=ts(현재 타임스탬프)

			-- 1. 상태 검문
			local state = redis.call('GET', KEYS[1])
			if state ~= 'PROGRESS' then
			    return {'BLOCKED', state}
			end

			-- ==========================================
			-- 책임 1: 대기열 등록 (큐 진입 제한)
			-- ==========================================
			-- 2. 대기열 재진입 및 기존 순번 확인
			local seq = redis.call('ZSCORE', KEYS[2], ARGV[1])
			if not seq then
			    -- 큐에 없으면 신규 등록
			    seq = redis.call('INCR', KEYS[3])
			    redis.call('ZADD', KEYS[2], seq, ARGV[1])
			end

			-- 3. 진입 허용선 확인 (기존 대기자 & 신규 대기자 공통)
			local limit = tonumber(redis.call('GET', KEYS[4]))
			if tonumber(seq) > limit then
			    -- 아직 내 차례가 안 왔으면 계속 대기
			    return {'PENDING', seq, limit}
			end

			-- ==========================================
			-- 책임 2: 과목 완료 등록 (큐 통과 후 처리)
			-- ==========================================

			-- 2-A. 이미 수강신청된 과목을 재등록하는지 검증
			if redis.call('SISMEMBER', KEYS[7], ARGV[1]) == 1 then 
			    redis.call('ZREM', KEYS[2], ARGV[1])
			    redis.call('HSET', KEYS[6], ARGV[1], 'FAIL_DUPLICATE:'..ARGV[2]..':'..ARGV[3])
			    return {'FAIL_DUPLICATE', ARGV[2]}
			end

			-- 2-B. 정원이 가득 찬건지 검증 (좌석 차감)
			local remaining = redis.call('HINCRBY', KEYS[5], ARGV[2], -1)

			if remaining >= 0 then
			    -- 성공
			    redis.call('HSET', KEYS[6], ARGV[1], 'SUCCESS:'..ARGV[2]..':'..ARGV[3])
			    redis.call('SADD', KEYS[7], ARGV[1])
			    redis.call('ZREM', KEYS[2], ARGV[1])
			    return {'SUCCESS', ARGV[2], remaining}
			else
			    -- 정원 초과 (차감 복구)
			    redis.call('HINCRBY', KEYS[5], ARGV[2], 1)
			    redis.call('HSET', KEYS[6], ARGV[1], 'FAIL_SOLDOUT:'..ARGV[2]..':'..ARGV[3])
			    redis.call('ZREM', KEYS[2], ARGV[1])
			    return {'FAIL_SOLDOUT', ARGV[2]}
			end
			""";

	@SuppressWarnings({"rawtypes", "unchecked"})
	public static final DefaultRedisScript<List> REDIS_SCRIPT = new DefaultRedisScript<>(SCRIPT, (Class<List>) (Class<?>) List.class);
}
