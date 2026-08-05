package com.mjusugangsincheonghelper.multigame.game.runtime;

import java.util.List;
import org.springframework.data.redis.core.script.DefaultRedisScript;

public final class GameApplyScript {

	private GameApplyScript() {
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	public static final DefaultRedisScript<List> SCRIPT = new DefaultRedisScript<>("""
			local state = redis.call('GET', KEYS[1])
			if state ~= 'PROGRESS' then return {'BLOCKED', state or ''} end
			-- 대기열 키는 (유저, 과목) 단위: 한 라운드에서 과목별로 각각 신청/성공이 가능하다
			local attempt = ARGV[1] .. ':' .. ARGV[2]
			local seq = redis.call('ZSCORE', KEYS[2], attempt)
			if not seq then
				seq = redis.call('INCR', KEYS[3])
				redis.call('ZADD', KEYS[2], seq, attempt)
				redis.call('RPUSH', KEYS[7], ARGV[1]..':ENQUEUED:'..ARGV[2]..':'..ARGV[3]..':'..seq..':0')
			end
			local limit = tonumber(redis.call('GET', KEYS[4]) or '0')
			if tonumber(seq) > limit then
				-- rank: 현재 큐에서 내 앞에 있는 시도 수 (상대값, 실시간 감소)
				local rank = redis.call('ZRANK', KEYS[2], attempt)
				return {'PENDING', seq, limit, rank}
			end
			-- 같은 과목은 1회만 성공 가능(중복 수강 방지). 다른 과목은 별도로 성공 가능
			if redis.call('SISMEMBER', KEYS[6], attempt) == 1 then
				redis.call('ZREM', KEYS[2], attempt)
				redis.call('RPUSH', KEYS[7], ARGV[1]..':FAIL_DUPLICATE:'..ARGV[2]..':'..ARGV[3]..':'..seq..':'..limit)
				return {'FAIL_DUPLICATE', ARGV[2]}
			end
			local remaining = redis.call('HINCRBY', KEYS[5], ARGV[2], -1)
			if remaining >= 0 then
				redis.call('SADD', KEYS[6], attempt)
				redis.call('ZREM', KEYS[2], attempt)
				redis.call('RPUSH', KEYS[7], ARGV[1]..':SUCCESS:'..ARGV[2]..':'..ARGV[3]..':'..seq..':'..limit)
				return {'SUCCESS', ARGV[2], remaining}
			end
			redis.call('HINCRBY', KEYS[5], ARGV[2], 1)
			redis.call('ZREM', KEYS[2], attempt)
			redis.call('RPUSH', KEYS[7], ARGV[1]..':FAIL_SOLDOUT:'..ARGV[2]..':'..ARGV[3]..':'..seq..':'..limit)
			return {'FAIL_SOLDOUT', ARGV[2]}
			""", (Class<List>) (Class<?>) List.class);
}
