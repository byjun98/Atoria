package com.atoria.backend.domain.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.email", name = "sender", havingValue = "log", matchIfMissing = true)
public class LoggingPasswordResetTokenSender implements PasswordResetTokenSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingPasswordResetTokenSender.class);

    @Override
    public void send(String email, String token) {
        log.info("Password reset token for {}: {}", email, token);
    }
}
