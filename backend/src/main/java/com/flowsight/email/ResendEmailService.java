package com.flowsight.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
            "subject", "Reset your FlowSight password",
            "html",    renderHtml(recipientName, resetUrl),
            "text",    renderPlainText(recipientName, resetUrl)
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

    private String renderHtml(String name, String resetUrl) {
        String safeName = htmlEscape(name);
        String safeUrl  = htmlEscape(resetUrl);
        return """
            <!DOCTYPE html>
            <html>
              <body style="margin:0;padding:0;background:#f7f6f1;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Helvetica,Arial,sans-serif;color:#0f172a;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f7f6f1;padding:48px 16px;">
                  <tr>
                    <td align="center">
                      <table role="presentation" width="100%%" style="max-width:520px;background:#ffffff;border-radius:14px;border:1px solid rgba(15,23,42,0.06);box-shadow:0 1px 1px rgba(15,23,42,0.025),0 2px 6px -2px rgba(15,23,42,0.04);" cellpadding="0" cellspacing="0">
                        <tr>
                          <td style="padding:40px 40px 8px;">
                            <p style="margin:0;font-size:13px;font-weight:600;letter-spacing:0.04em;color:#475569;text-transform:uppercase;">FlowSight</p>
                            <h1 style="margin:18px 0 0;font-size:22px;font-weight:600;letter-spacing:-0.015em;color:#0f172a;">Reset your password</h1>
                            <p style="margin:14px 0 0;font-size:15px;line-height:1.55;color:#475569;">Hi %s, we received a request to reset your password. Use the button below to set a new one.</p>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:28px 40px 8px;">
                            <a href="%s" style="display:inline-block;background:#0f172a;color:#ffffff;text-decoration:none;font-size:14px;font-weight:500;padding:12px 22px;border-radius:10px;">Reset password</a>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:16px 40px 36px;">
                            <p style="margin:0;font-size:12px;line-height:1.6;color:#64748b;">If the button does not work, paste this link into your browser:</p>
                            <p style="margin:6px 0 0;font-size:12px;line-height:1.6;color:#475569;word-break:break-all;"><a href="%s" style="color:#475569;text-decoration:underline;">%s</a></p>
                            <p style="margin:20px 0 0;font-size:12px;line-height:1.6;color:#94a3b8;">This link is valid for 30 minutes and can be used once. If you did not request a password reset, you can ignore this message.</p>
                          </td>
                        </tr>
                      </table>
                      <p style="margin:24px 0 0;font-size:11px;color:#94a3b8;">FlowSight will never ask you to share your password.</p>
                    </td>
                  </tr>
                </table>
              </body>
            </html>
            """.formatted(safeName, safeUrl, safeUrl, safeUrl);
    }

    private String renderPlainText(String name, String resetUrl) {
        return """
            Hi %s,

            We received a request to reset your FlowSight password.
            Open this link to set a new one:

              %s

            This link is valid for 30 minutes and can be used once.
            If you did not request a password reset, you can ignore this email.

            — FlowSight
            """.formatted(name, resetUrl);
    }

    private static String htmlEscape(String input) {
        if (input == null) return "";
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }
}
