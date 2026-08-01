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
			local seq = redis.call('ZSCORE', KEYS[2], ARGV[1])
			if not seq then
				seq = redis.call('INCR', KEYS[3])
				redis.call('ZADD', KEYS[2], seq, ARGV[1])
				redis.call('RPUSH', KEYS[7], ARGV[1]..':ENQUEUED:'..ARGV[2]..':'..ARGV[3]..':'..seq..':0')
			end
			local limit = tonumber(redis.call('GET', KEYS[4]) or '0')
			if tonumber(seq) > limit then return {'PENDING', seq, limit} end
			if redis.call('SISMEMBER', KEYS[6], ARGV[1]) == 1 then
				redis.call('ZREM', KEYS[2], ARGV[1])
				redis.call('RPUSH', KEYS[7], ARGV[1]..':FAIL_DUPLICATE:'..ARGV[2]..':'..ARGV[3]..':'..seq..':'..limit)
				return {'FAIL_DUPLICATE', ARGV[2]}
			end
			local remaining = redis.call('HINCRBY', KEYS[5], ARGV[2], -1)
			if remaining >= 0 then
				redis.call('SADD', KEYS[6], ARGV[1])
				redis.call('ZREM', KEYS[2], ARGV[1])
				redis.call('RPUSH', KEYS[7], ARGV[1]..':SUCCESS:'..ARGV[2]..':'..ARGV[3]..':'..seq..':'..limit)
				return {'SUCCESS', ARGV[2], remaining}
			end
			redis.call('HINCRBY', KEYS[5], ARGV[2], 1)
			redis.call('ZREM', KEYS[2], ARGV[1])
			redis.call('RPUSH', KEYS[7], ARGV[1]..':FAIL_SOLDOUT:'..ARGV[2]..':'..ARGV[3]..':'..seq..':'..limit)
			return {'FAIL_SOLDOUT', ARGV[2]}
			""", (Class<List>) (Class<?>) List.class);
}
