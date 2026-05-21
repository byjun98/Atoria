package com.atoria.backend.domain.auth.service;

public interface EmailVerificationCodeSender {

    void send(String email, String code);
}
