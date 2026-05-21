package com.atoria.backend.domain.auth.service;

import java.util.Optional;

public interface PasswordResetTokenStore {

    void save(String token, Long userId, long validitySeconds);

    Optional<Long> findUserId(String token);

    Optional<Long> consume(String token);
}
