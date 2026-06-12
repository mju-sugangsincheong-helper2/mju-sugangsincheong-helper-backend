package com.mjusugangsincheonghelper.auth.oauth;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OAuthStateService {
    private static final String KEY_PREFIX = "oauth:state:";
    private static final long STATE_TTL_SECONDS = 300;

    private final StringRedisTemplate redisTemplate;

    public String createState() {
        String state = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(KEY_PREFIX + state + ":session", "1", STATE_TTL_SECONDS, TimeUnit.SECONDS);
        return state;
    }

    public boolean consumeState(String state) {
        Boolean deleted = redisTemplate.delete(KEY_PREFIX + state + ":session");
        return Boolean.TRUE.equals(deleted);
    }
}
