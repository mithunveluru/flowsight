package com.flowsight.email;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class BrevoEmailServiceTest {

    private static final String ENDPOINT = "https://api.brevo.com/v3/smtp/email";

    private BrevoEmailService service(MockRestServiceServer[] out, String apiKey, String senderEmail) {
        RestClient.Builder builder = RestClient.builder();
        out[0] = MockRestServiceServer.bindTo(builder).build();
        BrevoEmailService s = new BrevoEmailService(builder);
        ReflectionTestUtils.setField(s, "apiKey", apiKey);
        ReflectionTestUtils.setField(s, "senderEmail", senderEmail);
        ReflectionTestUtils.setField(s, "senderName", "FlowSight");
        return s;
    }

    @Test
    void configured_postsToBrevoWithApiKeyAndRecipientAndResetUrl() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        BrevoEmailService service = service(server, "test-key", "noreply@flowsight.app");

        server[0].expect(requestTo(ENDPOINT))
            .andExpect(method(POST))
            .andExpect(header("api-key", "test-key"))
            .andExpect(content().string(containsString("\"email\":\"user@example.com\"")))
            .andExpect(content().string(containsString("noreply@flowsight.app")))
            .andExpect(content().string(containsString("token=RAW")))
            .andRespond(withSuccess("{\"messageId\":\"1\"}", org.springframework.http.MediaType.APPLICATION_JSON));

        service.sendPasswordReset("user@example.com", "Ada",
            "https://app.example/auth/reset-password?token=RAW");

        server[0].verify();
    }

    @Test
    void unconfigured_apiKeyBlank_makesNoHttpCall() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        BrevoEmailService service = service(server, "", "noreply@flowsight.app");

        // No server.expect(...): any HTTP call would fail verification.
        service.sendPasswordReset("user@example.com", "Ada", "https://app.example/reset");

        server[0].verify();
    }
}
