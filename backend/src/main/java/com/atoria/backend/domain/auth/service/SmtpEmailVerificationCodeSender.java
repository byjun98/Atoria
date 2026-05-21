package com.atoria.backend.domain.auth.service;

import com.atoria.backend.global.exception.BusinessException;
import com.atoria.backend.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.email", name = "sender", havingValue = "smtp")
public class SmtpEmailVerificationCodeSender implements EmailVerificationCodeSender {

    private final JavaMailSender mailSender;
    private final String from;
    private final long codeValiditySeconds;

    public SmtpEmailVerificationCodeSender(
            JavaMailSender mailSender,
            @Value("${app.email.from}") String from,
            @Value("${app.email.verification-code-validity-seconds}") long codeValiditySeconds
    ) {
        this.mailSender = mailSender;
        this.from = from;
        this.codeValiditySeconds = codeValiditySeconds;
    }

    @Override
    public void send(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("[Atoria] 이메일 인증코드");
        message.setText("""
                Atoria 이메일 인증코드입니다.

                인증코드: %s

                인증코드는 %d분 동안 유효합니다.
                본인이 요청하지 않았다면 이 메일을 무시해 주세요.
                """.formatted(code, Math.max(1, codeValiditySeconds / 60)));

        try {
            mailSender.send(message);
        } catch (MailException exception) {
            throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }
}
