package com.atoria.backend.domain.auth.service;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String REFRESH_TOKEN_KEY_PREFIX = "auth:refresh-token:";

    private final StringRedisTemplate redisTemplate;

    public RedisRefreshTokenStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(Long userId, String refreshToken, long validitySeconds) {
        redisTemplate.opsForValue()
                .set(REFRESH_TOKEN_KEY_PREFIX + userId, refreshToken, Duration.ofSeconds(validitySeconds));
    }

    @Override
    public boolean matches(Long userId, String refreshToken) {
        String savedToken = redisTemplate.opsForValue().get(REFRESH_TOKEN_KEY_PREFIX + userId);
        return refreshToken.equals(savedToken);
    }

    @Override
    public void delete(Long userId) {
        redisTemplate.delete(REFRESH_TOKEN_KEY_PREFIX + userId);
    }
}
