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

// Default provider. Fails on hosts that block outbound SMTP (e.g. Render free).
@Service
@Slf4j
@ConditionalOnProperty(name = "application.email.provider", havingValue = "gmail", matchIfMissing = true)
public class GmailEmailService implements EmailService {

    // null only if spring.mail.host is unset; then we log the link instead
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    // Gmail rejects a From that is not the authenticated account
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
            // dev fallback: log the link when unconfigured
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
            // plain text + html alternative
            helper.setText(
                PasswordResetEmailTemplate.text(recipientName, resetUrl),
                PasswordResetEmailTemplate.html(recipientName, resetUrl));

            mailSender.send(message);
            log.info("Password reset email sent to {} via Gmail SMTP", toEmail);
        } catch (MailAuthenticationException e) {
            log.error("Gmail SMTP authentication failed for {}: {}", toEmail, e.getMessage());
        } catch (MailException | MessagingException e) {
            log.error("Failed to send password reset email to {} via Gmail SMTP: {}", toEmail, e.getMessage());
        }
    }
}
