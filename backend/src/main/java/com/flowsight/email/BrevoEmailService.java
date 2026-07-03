package com.flowsight.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

// HTTPS provider; works where outbound SMTP is blocked. Single-sender, no domain.
@Service
@Slf4j
@ConditionalOnProperty(name = "application.email.provider", havingValue = "brevo")
public class BrevoEmailService implements EmailService {

    private static final String BREVO_ENDPOINT = "https://api.brevo.com/v3/smtp/email";

    private final RestClient restClient;

    @Value("${application.email.brevo.api-key:}")
    private String apiKey;

    // must be a verified sender in Brevo
    @Value("${application.email.brevo.sender-email:}")
    private String senderEmail;

    @Value("${application.email.brevo.sender-name:FlowSight}")
    private String senderName;

    BrevoEmailService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    @Async
    public void sendPasswordReset(String toEmail, String recipientName, String resetUrl) {
        if (apiKey.isBlank() || senderEmail.isBlank()) {
            // dev fallback: log the link when unconfigured
            log.warn("Brevo not configured (BREVO_API_KEY/BREVO_SENDER_EMAIL empty). "
                + "Password reset link for {} would be sent to {}", recipientName, toEmail);
            log.warn("Reset URL: {}", resetUrl);
            return;
        }

        Map<String, Object> payload = Map.of(
            "sender",      Map.of("name", senderName, "email", senderEmail),
            "to",          List.of(Map.of("email", toEmail, "name", recipientName)),
            "subject",     PasswordResetEmailTemplate.SUBJECT,
            "htmlContent", PasswordResetEmailTemplate.html(recipientName, resetUrl),
            "textContent", PasswordResetEmailTemplate.text(recipientName, resetUrl)
        );

        try {
            restClient.post()
                .uri(BREVO_ENDPOINT)
                .header("api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
            log.info("Password reset email sent to {} via Brevo", toEmail);
        } catch (RestClientResponseException e) {
            // delivery must not affect the API response
            log.warn("Brevo API rejected password reset email ({}): {}",
                e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn("Failed to dispatch password reset email via Brevo: {}", e.getMessage());
        }
    }
}
