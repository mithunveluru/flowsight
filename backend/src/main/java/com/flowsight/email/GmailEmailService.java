package com.flowsight.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Sends the password-reset email over Gmail SMTP (or any SMTP host configured
 * via spring.mail.*) using an App Password.
 *
 * Selected when {@code application.email.provider=gmail}, which is the default.
 * Swap providers by setting {@code application.email.provider=resend} (see
 * {@link ResendEmailService}) without touching any business logic.
 *
 * Delivery never affects the API response: any SMTP failure is logged and
 * swallowed so the caller-facing "if an account exists" message is identical
 * regardless of outcome (no user enumeration via timing or errors).
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "application.email.provider", havingValue = "gmail", matchIfMissing = true)
public class GmailEmailService implements EmailService {

    // Optional: absent only if spring.mail.host is unset. When null we fall back
    // to logging the link (dev/CI without SMTP configured).
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    // From header. Gmail rewrites/rejects a From that is not the authenticated
    // account or an authorized alias, so default to the account username.
    @Value("${application.email.gmail-from:}")
    private String configuredFrom;

    GmailEmailService(@Autowired(required = false) JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    @Async
    public void sendPasswordReset(String toEmail, String recipientName, String resetUrl) {
        log.info("Password reset email send initiated for {}", toEmail);

        if (mailSender == null || mailUsername.isBlank() || mailPassword.isBlank()) {
            // Development fallback: never silently swallow. Log the link so dev/CI
            // can exercise the reset flow without configuring Gmail SMTP.
            log.warn("Gmail SMTP not configured (MAIL_USERNAME/MAIL_PASSWORD empty). "
                + "Password reset link for {} would be sent to {}", recipientName, toEmail);
            log.warn("Reset URL: {}", resetUrl);
            return;
        }

        String from = configuredFrom.isBlank() ? mailUsername : configuredFrom;

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(toEmail);
            helper.setSubject(PasswordResetEmailTemplate.SUBJECT);
            // (plainText, html) — clients prefer the HTML part, plain text is the fallback.
            helper.setText(
                PasswordResetEmailTemplate.text(recipientName, resetUrl),
                PasswordResetEmailTemplate.html(recipientName, resetUrl));

            mailSender.send(message);
            log.info("Password reset email sent to {} via Gmail SMTP", toEmail);
        } catch (MailAuthenticationException e) {
            log.error("Gmail SMTP authentication failed for {} — verify MAIL_USERNAME and the "
                + "MAIL_PASSWORD App Password: {}", toEmail, e.getMessage());
        } catch (MailException | MessagingException e) {
            log.error("Failed to send password reset email to {} via Gmail SMTP: {}", toEmail, e.getMessage());
        }
    }
}
