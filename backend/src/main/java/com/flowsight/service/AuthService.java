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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private final com.flowsight.security.ClientIpResolver clientIpResolver;
    private final RefreshTokenService refreshTokenService;

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

        // audit after commit: the REQUIRES_NEW write can't see the uncommitted user (FK)
        final User registered = user;
        auditAfterCommit(() -> auditLogService.log(
            registered, AuditLogService.ACTION_USER_REGISTERED, "User", registered.getId().toString()));

        String token = jwtService.generateToken(buildExtraClaims(user), user);
        return toAuthResponse(token, refreshTokenService.issue(user), user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        rateLimiter.checkAuthAttempt(clientId());

        String normalizedEmail = request.getEmail().toLowerCase().trim();

        // both failures collapse to BadCredentials, so emails can't be enumerated
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
        return toAuthResponse(token, refreshTokenService.issue(user), user);
    }

    // Exchange a live refresh token for a new access + refresh pair (rotation).
    public AuthResponse refresh(String rawRefreshToken) {
        rateLimiter.checkAuthAttempt(clientId());
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(rawRefreshToken);
        User user = rotation.user();
        String token = jwtService.generateToken(buildExtraClaims(user), user);
        return toAuthResponse(token, rotation.rawToken(), user);
    }

    // Server-side logout: the refresh token dies now; the access token expires
    // on its own within the (short) JWT lifetime.
    public void logout(String rawRefreshToken) {
        rateLimiter.checkAuthAttempt(clientId());
        refreshTokenService.revoke(rawRefreshToken);
    }

    // run after commit so a REQUIRES_NEW audit write sees this txn's rows
    private void auditAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    // rate-limit key: client IP (spoofing-resistant resolution in ClientIpResolver)
    private String clientId() {
        return clientIpResolver.resolve(currentRequest);
    }

    private Map<String, Object> buildExtraClaims(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId().toString());
        claims.put("role", user.getRole().name());
        return claims;
    }

    private AuthResponse toAuthResponse(String token, String refreshToken, User user) {
        return AuthResponse.builder()
            .token(token)
            .refreshToken(refreshToken)
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
