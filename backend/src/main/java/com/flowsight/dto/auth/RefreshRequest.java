package com.flowsight.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

// Body for /auth/refresh and /auth/logout.
@Data
public class RefreshRequest {

    @NotBlank(message = "Refresh token is required")
    @Size(max = 128, message = "Invalid refresh token")
    private String refreshToken;
}
