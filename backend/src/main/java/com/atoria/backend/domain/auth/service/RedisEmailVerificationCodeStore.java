package com.atoria.backend.domain.auth.service;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.email.verification-code-store", name = "type", havingValue = "redis", matchIfMissing = true)
public class RedisEmailVerificationCodeStore implements EmailVerificationCodeStore {

    private static final String CODE_KEY_PREFIX = "auth:email-verification:code:";
    private static final String VERIFIED_KEY_PREFIX = "auth:email-verification:verified:";

    private final StringRedisTemplate redisTemplate;
    private final Duration codeValidity;

    public RedisEmailVerificationCodeStore(
            StringRedisTemplate redisTemplate,
            @Value("${app.email.verification-code-validity-seconds}") long codeValiditySeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.codeValidity = Duration.ofSeconds(codeValiditySeconds);
    }

    @Override
    public void save(String email, String code) {
        redisTemplate.opsForValue().set(codeKey(email), code, codeValidity);
        redisTemplate.delete(verifiedKey(email));
    }

    @Override
    public boolean matches(String email, String code) {
        return code.equals(redisTemplate.opsForValue().get(codeKey(email)));
    }

    @Override
    public void markVerified(String email) {
        String code = redisTemplate.opsForValue().get(codeKey(email));
        if (code == null) {
            return;
        }

        redisTemplate.opsForValue().set(verifiedKey(email), code, remainingTtl(email));
    }

    @Override
    public boolean isVerified(String email) {
        String code = redisTemplate.opsForValue().get(codeKey(email));
        String verifiedCode = redisTemplate.opsForValue().get(verifiedKey(email));
        return code != null && code.equals(verifiedCode);
    }

    @Override
    public void remove(String email) {
        redisTemplate.delete(codeKey(email));
        redisTemplate.delete(verifiedKey(email));
    }

    private Duration remainingTtl(String email) {
        Long ttlSeconds = redisTemplate.getExpire(codeKey(email));
        if (ttlSeconds == null || ttlSeconds <= 0) {
            return codeValidity;
        }
        return Duration.ofSeconds(ttlSeconds);
    }

    private String codeKey(String email) {
        return CODE_KEY_PREFIX + email;
    }

    private String verifiedKey(String email) {
        return VERIFIED_KEY_PREFIX + email;
    }
}
