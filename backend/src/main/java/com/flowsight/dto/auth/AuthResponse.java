package com.flowsight.dto.auth;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class AuthResponse {

    private String token;
    // opaque rotating refresh token; exchange at /auth/refresh when the access token expires
    private String refreshToken;
    private String tokenType;
    private long expiresIn;
    private UserProfile user;

    @Data
    @Builder
    public static class UserProfile {
        private UUID id;
        private String email;
        private String fullName;
        private String role;
    }
}
