package com.microservices.notifications_service.email;

import com.microservices.notifications_service.dto.NotificationDtos;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends transactional HTML email via Brevo (api.brevo.com). Configure {@code BREVO_API_KEY} and a verified sender.
 */
@Slf4j
@Service
public class BrevoMailService {

    private static final String BREVO_SMTP_URL = "https://api.brevo.com/v3/smtp/email";

    private final RestClient restClient = RestClient.create();

    @Value("${app.brevo.api-key:}")
    private String apiKey;

    @Value("${app.brevo.sender-email:}")
    private String senderEmail;

    @Value("${app.brevo.sender-name:FoodChain}")
    private String senderName;

    public void send(NotificationDtos.EmailSendEvent event) {
        if (event.getToEmail() == null || event.getToEmail().isBlank()) {
            log.warn("Skip email send: missing recipient");
            return;
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("BREVO_API_KEY is not set; skip sending {} to {}", event.getEmailType(), event.getToEmail());
            return;
        }
        if (senderEmail == null || senderEmail.isBlank()) {
            log.warn("app.brevo.sender-email is not set; skip sending to {}", event.getToEmail());
            return;
        }
        if (event.getHtmlContent() == null || event.getHtmlContent().isBlank()) {
            log.warn("Skip email send: empty htmlContent for {}", event.getToEmail());
            return;
        }

        String displayName = (event.getToName() != null && !event.getToName().isBlank())
                ? event.getToName()
                : event.getToEmail();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sender", Map.of("name", senderName, "email", senderEmail));
        body.put("to", List.of(Map.of("email", event.getToEmail(), "name", displayName)));
        body.put("subject", event.getSubject() != null ? event.getSubject() : "FoodChain");
        body.put("htmlContent", event.getHtmlContent());

        try {
            restClient.post()
                    .uri(BREVO_SMTP_URL)
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Brevo email sent type={} to={}", event.getEmailType(), event.getToEmail());
        } catch (RestClientResponseException e) {
            log.error("Brevo API rejected email type={} to={} — status={} body={}",
                    event.getEmailType(), event.getToEmail(), e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Failed to send email type={} to={}: {}", event.getEmailType(), event.getToEmail(), e.getMessage());
        }
    }
}
