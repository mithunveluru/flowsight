package com.flowsight.controller;

import com.flowsight.dto.auth.AuthResponse;
import com.flowsight.dto.auth.ForgotPasswordRequest;
import com.flowsight.dto.auth.LoginRequest;
import com.flowsight.dto.auth.RegisterRequest;
import com.flowsight.dto.auth.ResetPasswordRequest;
import com.flowsight.entity.User;
import com.flowsight.security.RateLimiter;
import com.flowsight.service.AuthService;
import com.flowsight.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    // Identical response body for every forgot-password request — never reveals
    // whether the email belongs to a registered account.
    private static final Map<String, String> FORGOT_PASSWORD_RESPONSE = Map.of(
        "message",
        "If an account exists for that email, we have sent a link to reset your password."
    );

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final RateLimiter rateLimiter;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
        @Valid @RequestBody ForgotPasswordRequest request,
        HttpServletRequest httpRequest
    ) {
        rateLimiter.checkPasswordResetRequest(clientIp(httpRequest));
        passwordResetService.requestReset(request.getEmail());
        return ResponseEntity.ok(FORGOT_PASSWORD_RESPONSE);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
        @Valid @RequestBody ResetPasswordRequest request,
        HttpServletRequest httpRequest
    ) {
        rateLimiter.checkPasswordResetConfirm(clientIp(httpRequest));
        passwordResetService.consumeReset(request.getToken(), request.getPassword());
        return ResponseEntity.ok(Map.of("message", "Your password has been updated."));
    }

    // Returns the profile of the currently authenticated user from the JWT principal.
    @GetMapping("/me")
    public ResponseEntity<AuthResponse.UserProfile> me(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(
            AuthResponse.UserProfile.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .build()
        );
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        String addr = request.getRemoteAddr();
        return addr != null ? addr : "unknown";
    }
}
