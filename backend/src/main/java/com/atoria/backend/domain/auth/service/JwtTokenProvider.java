package com.atoria.backend.domain.auth.service;

import com.atoria.backend.domain.user.entity.User;
import com.atoria.backend.global.exception.BusinessException;
import com.atoria.backend.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long accessTokenValiditySeconds;
    private final long refreshTokenValiditySeconds;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-validity-seconds}") long accessTokenValiditySeconds,
            @Value("${app.jwt.refresh-token-validity-seconds}") long refreshTokenValiditySeconds
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenValiditySeconds = accessTokenValiditySeconds;
        this.refreshTokenValiditySeconds = refreshTokenValiditySeconds;
    }

    public String createAccessToken(User user) {
        return createToken(user, accessTokenValiditySeconds, "access");
    }

    public String createRefreshToken(User user) {
        return createToken(user, refreshTokenValiditySeconds, "refresh");
    }

    public long getRefreshTokenValiditySeconds() {
        return refreshTokenValiditySeconds;
    }

    public Long getUserIdFromRefreshToken(String refreshToken) {
        Claims claims = parseRefreshToken(refreshToken);
        return Long.valueOf(claims.getSubject());
    }

    public Long getUserIdFromAccessToken(String accessToken) {
        Claims claims = parseToken(accessToken, "access", ErrorCode.ACCESS_TOKEN_INVALID);
        return Long.valueOf(claims.getSubject());
    }

    private Claims parseRefreshToken(String refreshToken) {
        return parseToken(refreshToken, "refresh", ErrorCode.REFRESH_TOKEN_INVALID);
    }

    private Claims parseToken(String token, String tokenType, ErrorCode errorCode) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!tokenType.equals(claims.get("tokenType", String.class))) {
                throw new BusinessException(errorCode);
            }

            return claims;
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(errorCode);
        }
    }

    private String createToken(User user, long validitySeconds, String tokenType) {
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(validitySeconds);

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("nickname", user.getNickname())
                .claim("tokenType", tokenType)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }
}
