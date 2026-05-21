package com.atoria.backend.domain.auth.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.email.verification-code-store", name = "type", havingValue = "memory")
public class InMemoryEmailVerificationCodeStore implements EmailVerificationCodeStore {

    private final Map<String, VerificationCode> codes = new ConcurrentHashMap<>();

    @Override
    public void save(String email, String code) {
        codes.put(email, new VerificationCode(code, false));
    }

    @Override
    public boolean matches(String email, String code) {
        VerificationCode verificationCode = codes.get(email);
        return verificationCode != null && verificationCode.code().equals(code);
    }

    @Override
    public void markVerified(String email) {
        VerificationCode verificationCode = codes.get(email);
        if (verificationCode != null) {
            codes.put(email, new VerificationCode(verificationCode.code(), true));
        }
    }

    @Override
    public boolean isVerified(String email) {
        VerificationCode verificationCode = codes.get(email);
        return verificationCode != null && verificationCode.verified();
    }

    @Override
    public void remove(String email) {
        codes.remove(email);
    }

    private record VerificationCode(String code, boolean verified) {
    }
}
