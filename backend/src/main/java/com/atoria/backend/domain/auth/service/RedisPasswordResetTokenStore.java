package com.atoria.backend.domain.auth.service;

import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisPasswordResetTokenStore implements PasswordResetTokenStore {

    private static final String TOKEN_KEY_PREFIX = "auth:password-reset:token:";
    private static final String USER_KEY_PREFIX = "auth:password-reset:user:";

    private final StringRedisTemplate redisTemplate;

    public RedisPasswordResetTokenStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(String token, Long userId, long validitySeconds) {
        String userKey = USER_KEY_PREFIX + userId;
        String oldToken = redisTemplate.opsForValue().get(userKey);

        if (oldToken != null) {
            redisTemplate.delete(TOKEN_KEY_PREFIX + oldToken);
        }

        redisTemplate.opsForValue()
                .set(TOKEN_KEY_PREFIX + token, String.valueOf(userId), Duration.ofSeconds(validitySeconds));
        redisTemplate.opsForValue()
                .set(userKey, token, Duration.ofSeconds(validitySeconds));
    }

    @Override
    public Optional<Long> findUserId(String token) {
        String userId = redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + token);
        return Optional.ofNullable(userId).map(Long::valueOf);
    }

    @Override
    public Optional<Long> consume(String token) {
        String tokenKey = TOKEN_KEY_PREFIX + token;
        String userId = redisTemplate.opsForValue().getAndDelete(tokenKey);

        if (userId != null) {
            redisTemplate.delete(USER_KEY_PREFIX + userId);
        }

        return Optional.ofNullable(userId).map(Long::valueOf);
    }
}
