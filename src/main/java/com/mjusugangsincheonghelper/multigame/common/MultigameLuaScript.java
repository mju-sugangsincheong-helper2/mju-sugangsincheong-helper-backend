package com.mjusugangsincheonghelper.multigame.common;

import java.util.List;
import org.springframework.data.redis.core.script.DefaultRedisScript;

public final class MultigameLuaScript {

	private MultigameLuaScript() {
	}

	public static final String SCRIPT = """
			local state = redis.call('GET', KEYS[1])
			if state ~= 'PROGRESS' then
			    return {status='BLOCKED', current_state=state}
			end
			
			local seq = redis.call('ZSCORE', KEYS[2], ARGV[1])
			if not seq then
			    seq = redis.call('INCR', KEYS[3])
			    redis.call('ZADD', KEYS[2], seq, ARGV[1])
			end
			
			local limit = tonumber(redis.call('GET', KEYS[4]))
			if tonumber(seq) > limit then
			    return {status='PENDING', seq=seq, limit=limit}
			end
			
			if redis.call('SISMEMBER', KEYS[7], ARGV[1]) == 1 then
			    redis.call('ZREM', KEYS[2], ARGV[1])
			    return {status='FAIL_DUPLICATE'}
			end
			
			local remaining = redis.call('HINCRBY', KEYS[5], ARGV[2], -1)
			
			if remaining >= 0 then
			    redis.call('HSET', KEYS[6], ARGV[1], 'SUCCESS:'..ARGV[2]..':'..ARGV[3])
			    redis.call('SADD', KEYS[7], ARGV[1])
			    redis.call('ZREM', KEYS[2], ARGV[1])
			    return {status='SUCCESS', subject_id=ARGV[2], remaining=remaining}
			else
			    redis.call('HINCRBY', KEYS[5], ARGV[2], 1)
			    redis.call('HSET', KEYS[6], ARGV[1], 'FAIL_SOLDOUT:'..ARGV[2]..':'..ARGV[3])
			    redis.call('ZREM', KEYS[2], ARGV[1])
			    return {status='FAIL_SOLDOUT', subject_id=ARGV[2]}
			end
			""";

	public static final DefaultRedisScript<List> REDIS_SCRIPT = new DefaultRedisScript<>(SCRIPT, List.class);
}
