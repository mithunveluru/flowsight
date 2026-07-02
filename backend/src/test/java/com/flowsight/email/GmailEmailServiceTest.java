package com.flowsight.email;

import jakarta.mail.Message;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GmailEmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private GmailEmailService service(String username, String password, String from) {
        GmailEmailService s = new GmailEmailService(mailSender);
        ReflectionTestUtils.setField(s, "mailUsername", username);
        ReflectionTestUtils.setField(s, "mailPassword", password);
        ReflectionTestUtils.setField(s, "configuredFrom", from);
        return s;
    }

    @Test
    void configured_sendsMessageWithCorrectHeadersAndBothBodyParts() throws Exception {
        // createMimeMessage returns a real (offline) MimeMessage; send is stubbed
        // so nothing hits the network.
        when(mailSender.createMimeMessage())
            .thenReturn(new JavaMailSenderImpl().createMimeMessage());

        GmailEmailService service = service("flowsight@gmail.com", "app-password", "");
        service.sendPasswordReset("user@example.com", "Ada",
            "https://app.example/auth/reset-password?token=RAW");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage msg = captor.getValue();

        assertThat(msg.getSubject()).isEqualTo(PasswordResetEmailTemplate.SUBJECT);
        assertThat(msg.getRecipients(Message.RecipientType.TO)[0].toString())
            .isEqualTo("user@example.com");
        // From defaults to the authenticated username when gmail-from is empty.
        assertThat(msg.getFrom()[0].toString()).isEqualTo("flowsight@gmail.com");
        // Multipart body carries the reset URL (in both HTML and plain-text parts).
        assertThat(bodyText(msg)).contains("token=RAW");
    }

    // Walks the MIME tree collecting every String part so we can assert on the
    // rendered body regardless of the multipart/alternative nesting.
    private static String bodyText(Part part) throws Exception {
        Object content = part.getContent();
        if (content instanceof String s) {
            return s;
        }
        if (content instanceof MimeMultipart mp) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mp.getCount(); i++) {
                sb.append(bodyText(mp.getBodyPart(i)));
            }
            return sb.toString();
        }
        return "";
    }

    @Test
    void unconfigured_credentialsBlank_logsLink_neverSends() {
        GmailEmailService service = service("", "", "");
        service.sendPasswordReset("user@example.com", "Ada", "https://app.example/reset");

        verify(mailSender, never()).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));
    }
}
