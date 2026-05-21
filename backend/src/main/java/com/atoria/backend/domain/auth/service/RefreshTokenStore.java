package com.atoria.backend.domain.auth.service;

public interface RefreshTokenStore {

    void save(Long userId, String refreshToken, long validitySeconds);

    boolean matches(Long userId, String refreshToken);

    void delete(Long userId);
}
