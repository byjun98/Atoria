package com.atoria.backend.domain.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.email", name = "sender", havingValue = "log", matchIfMissing = true)
public class LoggingEmailVerificationCodeSender implements EmailVerificationCodeSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailVerificationCodeSender.class);

    @Override
    public void send(String email, String code) {
        log.info("Email verification code issued. email={}, code={}", email, code);
    }
}
