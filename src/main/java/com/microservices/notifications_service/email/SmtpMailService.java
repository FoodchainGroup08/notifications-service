package com.microservices.notifications_service.email;

import com.microservices.notifications_service.dto.NotificationDtos;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SmtpMailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String defaultFrom;

    public SmtpMailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void send(NotificationDtos.EmailSendEvent event) {
        if (event.getToEmail() == null || event.getToEmail().isBlank()) {
            log.warn("Skip SMTP email send: missing recipient");
            return;
        }
        if (defaultFrom == null || defaultFrom.isBlank()) {
            log.warn("Skip SMTP email send: spring.mail.username is not configured");
            return;
        }
        if (event.getHtmlContent() == null || event.getHtmlContent().isBlank()) {
            log.warn("Skip SMTP email send: empty htmlContent for {}", event.getToEmail());
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(defaultFrom, "FoodChain");
            helper.setTo(event.getToEmail());
            helper.setSubject(event.getSubject() != null ? event.getSubject() : "FoodChain");
            helper.setText(event.getHtmlContent(), true);
            mailSender.send(message);
            log.info("SMTP email sent type={} to={}", event.getEmailType(), event.getToEmail());
        } catch (Exception e) {
            throw new IllegalStateException("SMTP email send failed", e);
        }
    }
}
