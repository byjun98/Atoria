package com.atoria.backend.domain.auth.service;

public interface PasswordResetTokenSender {

    void send(String email, String token);
}
