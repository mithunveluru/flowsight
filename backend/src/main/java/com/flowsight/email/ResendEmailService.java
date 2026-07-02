package com.flowsight.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
@ConditionalOnProperty(name = "application.email.provider", havingValue = "resend")
public class ResendEmailService implements EmailService {

    private static final String RESEND_ENDPOINT = "https://api.resend.com/emails";

    private final RestClient restClient = RestClient.create();

    @Value("${application.email.resend.api-key:}")
    private String apiKey;

    @Value("${application.email.from:FlowSight <noreply@flowsight.app>}")
    private String fromAddress;

    @Value("${application.email.reply-to:}")
    private String replyTo;

    @Override
    @Async
    public void sendPasswordReset(String toEmail, String recipientName, String resetUrl) {
        if (apiKey == null || apiKey.isBlank()) {
            // Development fallback: never silently swallow. Log the link so dev/CI can
            // exercise the reset flow without configuring Resend.
            log.warn("RESEND_API_KEY not set. Password reset link for {} would be sent to {}", recipientName, toEmail);
            log.warn("Reset URL: {}", resetUrl);
            return;
        }

        Map<String, Object> payload = Map.of(
            "from",    fromAddress,
            "to",      List.of(toEmail),
            "subject", PasswordResetEmailTemplate.SUBJECT,
            "html",    PasswordResetEmailTemplate.html(recipientName, resetUrl),
            "text",    PasswordResetEmailTemplate.text(recipientName, resetUrl)
        );

        try {
            restClient.post()
                .uri(RESEND_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(replyTo == null || replyTo.isBlank()
                    ? payload
                    : withReplyTo(payload))
                .retrieve()
                .toBodilessEntity();
            log.info("Password reset email dispatched for user {}", recipientName);
        } catch (RestClientResponseException e) {
            // Surface only at WARN — the user response must not depend on email delivery.
            log.warn("Resend API rejected password reset email ({}): {}",
                e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn("Failed to dispatch password reset email: {}", e.getMessage());
        }
    }

    private Map<String, Object> withReplyTo(Map<String, Object> payload) {
        return Map.of(
            "from",     payload.get("from"),
            "to",       payload.get("to"),
            "subject",  payload.get("subject"),
            "html",     payload.get("html"),
            "text",     payload.get("text"),
            "reply_to", replyTo
        );
    }
}
