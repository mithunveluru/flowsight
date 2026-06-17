package com.flowsight.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityEnvironmentValidatorTest {

    // 65-byte Base64 secret (well above the 32-byte / 256-bit minimum).
    private static final String VALID_SECRET =
        "dGVzdF9zZWNyZXRfa2V5X3RoYXRfaXNfbG9uZ19lbm91Z2hfZm9yX0hTMjU2X2FsZ29yaXRobV8xMjM0NTY3ODk=";
    // Base64 of "short" -> 5 bytes, below the minimum.
    private static final String SHORT_SECRET = "c2hvcnQ=";

    private SecurityEnvironmentValidator validator(String[] profiles, String secret, String cors) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        SecurityEnvironmentValidator v = new SecurityEnvironmentValidator(env);
        ReflectionTestUtils.setField(v, "jwtSecret", secret);
        ReflectionTestUtils.setField(v, "corsAllowedOrigins", cors);
        return v;
    }

    @Test
    void validSecret_noActiveProfile_passes() {
        var v = validator(new String[]{}, VALID_SECRET, "");
        assertThatCode(v::validate).doesNotThrowAnyException();
    }

    @Test
    void blankSecret_failsFast() {
        var v = validator(new String[]{"dev"}, "  ", "http://localhost:3007");
        assertThatThrownBy(v::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("JWT secret is not configured");
    }

    @Test
    void invalidBase64Secret_failsFast() {
        var v = validator(new String[]{"dev"}, "@@@not-base64@@@", "http://localhost:3007");
        assertThatThrownBy(v::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("valid Base64");
    }

    @Test
    void tooShortSecret_failsFast() {
        var v = validator(new String[]{"dev"}, SHORT_SECRET, "http://localhost:3007");
        assertThatThrownBy(v::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("too weak");
    }

    @Test
    void productionProfile_wildcardCors_failsFast() {
        var v = validator(new String[]{"prod"}, VALID_SECRET, "https://app.flowsight.io,*");
        assertThatThrownBy(v::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("wildcard");
    }

    @Test
    void productionProfile_blankCors_failsFast() {
        var v = validator(new String[]{"prod"}, VALID_SECRET, "");
        assertThatThrownBy(v::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("must be set explicitly");
    }

    @Test
    void productionProfile_explicitCors_passes() {
        var v = validator(new String[]{"prod"}, VALID_SECRET,
            "https://app.flowsight.io,https://flowsight.io");
        assertThatCode(v::validate).doesNotThrowAnyException();
    }

    @Test
    void developmentProfile_blankCors_isAllowed() {
        // CORS strictness only applies to non-dev profiles.
        var v = validator(new String[]{"dev"}, VALID_SECRET, "");
        assertThatCode(v::validate).doesNotThrowAnyException();
    }
}
