package com.atoria.backend.domain.auth.service;

public interface EmailVerificationCodeStore {

    void save(String email, String code);

    boolean matches(String email, String code);

    void markVerified(String email);

    boolean isVerified(String email);

    void remove(String email);
}
