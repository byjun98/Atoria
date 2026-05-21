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
public class SmtpPasswordResetTokenSender implements PasswordResetTokenSender {

    private final JavaMailSender mailSender;
    private final String from;
    private final String confirmUrl;

    public SmtpPasswordResetTokenSender(
            JavaMailSender mailSender,
            @Value("${app.email.from}") String from,
            @Value("${app.password-reset.confirm-url}") String confirmUrl
    ) {
        this.mailSender = mailSender;
        this.from = from;
        this.confirmUrl = confirmUrl;
    }

    @Override
    public void send(String email, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("[Atoria] 비밀번호 재설정 안내");
        message.setText("""
                Atoria 비밀번호 재설정 요청이 접수되었습니다.

                재설정 토큰: %s
                %s

                본인이 요청하지 않았다면 이 메일을 무시해 주세요.
                """.formatted(token, resetLinkMessage(token)));

        try {
            mailSender.send(message);
        } catch (MailException exception) {
            throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    private String resetLinkMessage(String token) {
        if (confirmUrl == null || confirmUrl.isBlank()) {
            return "";
        }

        String separator = confirmUrl.contains("?") ? "&" : "?";
        return "재설정 링크: " + confirmUrl + separator + "token=" + token;
    }
}
