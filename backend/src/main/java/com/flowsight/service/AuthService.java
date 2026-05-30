package com.flowsight.service;

import com.flowsight.dto.auth.AuthResponse;
import com.flowsight.dto.auth.LoginRequest;
import com.flowsight.dto.auth.RegisterRequest;
import com.flowsight.entity.Role;
import com.flowsight.entity.User;
import com.flowsight.exception.DuplicateEmailException;
import com.flowsight.repository.UserRepository;
import com.flowsight.security.JwtService;
import com.flowsight.security.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RateLimiter rateLimiter;
    private final AuditLogService auditLogService;

    @Autowired(required = false)
    private HttpServletRequest currentRequest;

    @Value("${application.security.jwt.expiration:86400000}")
    private long jwtExpiration;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        rateLimiter.checkAuthAttempt(clientId());

        String normalizedEmail = request.getEmail().toLowerCase().trim();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateEmailException(normalizedEmail);
        }

        User user = User.builder()
            .email(normalizedEmail)
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .fullName(request.getFullName().trim())
            .role(Role.USER)
            .active(true)
            .build();

        user = userRepository.save(user);
        log.info("Registered new user: {}", user.getEmail());
        auditLogService.log(user, AuditLogService.ACTION_USER_REGISTERED, "User", user.getId().toString());

        String token = jwtService.generateToken(buildExtraClaims(user), user);
        return toAuthResponse(token, user);
    }

    public AuthResponse login(LoginRequest request) {
        rateLimiter.checkAuthAttempt(clientId());

        String normalizedEmail = request.getEmail().toLowerCase().trim();

        // AuthenticationManager handles both UsernameNotFound and BadCredentials.
        // hideUserNotFoundExceptions=true (Spring default) ensures both collapse to
        // BadCredentialsException, so callers cannot enumerate registered emails.
        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.getPassword())
            );
        } catch (BadCredentialsException e) {
            auditLogService.logFailedLogin(normalizedEmail);
            throw e;
        }

        User user = userRepository.findByEmail(normalizedEmail)
            .orElseThrow();

        auditLogService.log(user, AuditLogService.ACTION_USER_LOGIN, "User", user.getId().toString());
        String token = jwtService.generateToken(buildExtraClaims(user), user);
        return toAuthResponse(token, user);
    }

    /** Use the client IP as the rate-limit key (or "unknown" if not in a request). */
    private String clientId() {
        if (currentRequest == null) return "unknown";
        try {
            String forwarded = currentRequest.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
            return currentRequest.getRemoteAddr();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private Map<String, Object> buildExtraClaims(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId().toString());
        claims.put("role", user.getRole().name());
        return claims;
    }

    private AuthResponse toAuthResponse(String token, User user) {
        return AuthResponse.builder()
            .token(token)
            .tokenType("Bearer")
            .expiresIn(jwtExpiration / 1000)
            .user(AuthResponse.UserProfile.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .build())
            .build();
    }
}
