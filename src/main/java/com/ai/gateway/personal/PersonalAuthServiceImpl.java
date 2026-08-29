package com.ai.gateway.personal;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.authentication.AuthenticationType;
import com.ai.gateway.personal.dto.*;
import com.ai.gateway.personal.entity.PersonalAccount;
import com.ai.gateway.personal.entity.PersonalSession;
import com.ai.gateway.personal.entity.PersonalUser;
import com.ai.gateway.personal.repository.PersonalAccountRepository;
import com.ai.gateway.personal.repository.PersonalSessionRepository;
import com.ai.gateway.personal.repository.PersonalEmailVerificationTokenRepository;
import com.ai.gateway.personal.entity.PersonalEmailVerificationToken;
import com.ai.gateway.personal.repository.PersonalUserRepository;
import com.ai.gateway.security.SecurityRole;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PersonalAuthServiceImpl implements PersonalAuthService {

    private static final int TOKEN_BYTES = 32;

    private final PersonalUserRepository userRepository;
    private final PersonalAccountRepository accountRepository;
    private final PersonalSessionRepository sessionRepository;
    private final PersonalEmailVerificationTokenRepository verificationTokenRepository;
    private final PasswordEncoder passwordEncoder;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${alroute.personal.auth.session-ttl:30d}")
    private String sessionTtl;

    @Value("${alroute.personal.auth.verification-ttl:24h}")
    private String verificationTtl;

    @Value("${alroute.personal.auth.auto-verify-on-signup:false}")
    private boolean autoVerifyOnSignup;

    @Value("${alroute.personal.auth.expose-verification-token:false}")
    private boolean exposeVerificationToken;

    @Override
    @Transactional
    public PersonalSignupResponse signup(PersonalSignupRequest request) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new PersonalAccountConflictException("An account already exists for this email.");
        }

        LocalDateTime now = LocalDateTime.now();

        PersonalUser user = PersonalUser.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .displayName(normalizeDisplayName(request.displayName()))
                .status("ACTIVE")
                .emailVerified(autoVerifyOnSignup)
                .createdAt(now)
                .updatedAt(now)
                .build();

        user = userRepository.save(user);

        PersonalAccount account = PersonalAccount.builder()
                .user(user)
                .plan("PERSONAL_FREE")
                .status("ACTIVE")
                .createdAt(now)
                .updatedAt(now)
                .build();

        account = accountRepository.save(account);

        String verificationToken = null;

        if (!user.isEmailVerified()) {
            verificationToken = generateToken();

            PersonalEmailVerificationToken token =
                    PersonalEmailVerificationToken.builder()
                            .user(user)
                            .tokenHash(hashToken(verificationToken))
                            .createdAt(now)
                            .expiresAt(now.plus(parseDuration(
                                    verificationTtl, "24h")))
                            .build();

            verificationTokenRepository.save(token);
        }

        return new PersonalSignupResponse(
                user.getId(),
                account.getId(),
                user.getEmail(),
                user.getDisplayName(),
                account.getPlan(),
                account.getStatus(),
                user.isEmailVerified(),
                !user.isEmailVerified(),
                exposeVerificationToken ? verificationToken : null);
    }

    @Override
    @Transactional
    public void verifyEmail(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Verification token is required.");
        }

        PersonalEmailVerificationToken verification =
                verificationTokenRepository.findByTokenHash(hashToken(token))
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Invalid or expired verification token."));

        LocalDateTime now = LocalDateTime.now();

        if (verification.getUsedAt() != null
                || verification.getExpiresAt().isBefore(now)) {
            throw new IllegalArgumentException(
                    "Invalid or expired verification token.");
        }

        PersonalUser user = verification.getUser();
        user.setEmailVerified(true);
        user.setUpdatedAt(now);
        verification.setUsedAt(now);
    }

    @Override
    @Transactional
    public PersonalLoginResponse login(PersonalLoginRequest request) {
        String email = normalizeEmail(request.email());

        PersonalUser user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new AccessDeniedException("Invalid email or password."));

        if (!"ACTIVE".equals(user.getStatus())
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new PersonalAuthenticationException("Invalid email or password.");
        }

        if (!user.isEmailVerified()) {
            throw new PersonalAuthenticationException("Email verification is required.");
        }

        PersonalAccount account = accountRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("Personal account is not configured."));

        if (!"ACTIVE".equals(account.getStatus())) {
            throw new AccessDeniedException("Personal account is not active.");
        }

        String rawToken = generateToken();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plus(parseSessionTtl());

        PersonalSession session = PersonalSession.builder()
                .user(user)
                .tokenHash(hashToken(rawToken))
                .createdAt(now)
                .expiresAt(expiresAt)
                .lastUsedAt(now)
                .build();

        sessionRepository.save(session);

        user.setLastLoginAt(now);
        user.setUpdatedAt(now);

        long expiresInSeconds =
                Math.max(0, expiresAt.toEpochSecond(ZoneOffset.UTC)
                        - now.toEpochSecond(ZoneOffset.UTC));

        return new PersonalLoginResponse(
                rawToken,
                "Bearer",
                expiresInSeconds,
                toUserResponse(user, account));
    }

    @Override
    @Transactional
    public AuthenticationContext authenticateBearer(String token) {
        if (token == null || token.isBlank()) {
            throw new AccessDeniedException("Bearer token is required.");
        }

        PersonalSession session = sessionRepository.findByTokenHash(hashToken(token))
                .orElseThrow(() -> new AccessDeniedException("Invalid or expired session."));

        LocalDateTime now = LocalDateTime.now();

        if (session.getRevokedAt() != null
                || session.getExpiresAt().isBefore(now)
                || !"ACTIVE".equals(session.getUser().getStatus())) {
            throw new AccessDeniedException("Invalid or expired session.");
        }

        PersonalAccount account = accountRepository.findByUserId(session.getUser().getId())
                .orElseThrow(() -> new AccessDeniedException("Personal account is not configured."));

        if (!"ACTIVE".equals(account.getStatus())) {
            throw new AccessDeniedException("Personal account is not active.");
        }

        session.setLastUsedAt(now);

        PersonalUser user = session.getUser();

        return AuthenticationContext.builder()
                .authenticationType(AuthenticationType.PERSONAL_SESSION)
                .apiKeyId(null)
                .clientName("personal")
                .tenantId(null)
                .tenantCode(null)
                .tenantName(null)
                .tenantType(null)
                .defaultProvider(null)
                .defaultModel(null)
                .schemaName(null)
                .role(SecurityRole.TENANT_USER)
                .platformPrincipal(false)
                .personalPrincipal(true)
                .personalUserId(user.getId())
                .personalAccountId(account.getId())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PersonalUserResponse me(AuthenticationContext context) {
        if (!context.isPersonalPrincipal()
                || context.getPersonalUserId() == null
                || context.getPersonalAccountId() == null) {
            throw new AccessDeniedException("Personal authentication is required.");
        }

        PersonalUser user = userRepository.findById(context.getPersonalUserId())
                .orElseThrow(() -> new AccessDeniedException("Personal user not found."));

        PersonalAccount account = accountRepository.findById(context.getPersonalAccountId())
                .orElseThrow(() -> new AccessDeniedException("Personal account not found."));

        return toUserResponse(user, account);
    }

    @Override
    @Transactional
    public void logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");

        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return;
        }

        String token = header.substring(7).trim();

        if (token.isBlank()) {
            return;
        }

        sessionRepository.findByTokenHash(hashToken(token))
                .ifPresent(session -> session.setRevokedAt(LocalDateTime.now()));
    }

    private PersonalUserResponse toUserResponse(
            PersonalUser user,
            PersonalAccount account) {

        return new PersonalUserResponse(
                user.getId(),
                account.getId(),
                user.getEmail(),
                user.getDisplayName(),
                account.getPlan(),
                account.getStatus(),
                user.isEmailVerified());
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String normalizeDisplayName(String displayName) {
        if (displayName == null) {
            return null;
        }

        String normalized = displayName.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return "arp_" + Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));

            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }

    private java.time.Duration parseSessionTtl() {
        return parseDuration(sessionTtl, "30d");
    }

    private java.time.Duration parseDuration(String configured, String fallback) {
        String value = configured == null || configured.isBlank()
                ? fallback
                : configured.trim().toLowerCase(java.util.Locale.ROOT);

        try {
            if (value.endsWith("d")) {
                return java.time.Duration.ofDays(Long.parseLong(
                        value.substring(0, value.length() - 1)));
            }
            if (value.endsWith("h")) {
                return java.time.Duration.ofHours(Long.parseLong(
                        value.substring(0, value.length() - 1)));
            }
            if (value.endsWith("m")) {
                return java.time.Duration.ofMinutes(Long.parseLong(
                        value.substring(0, value.length() - 1)));
            }
            return java.time.Duration.ofSeconds(Long.parseLong(value));
        } catch (RuntimeException ex) {
            throw new IllegalStateException(
                    "Invalid personal duration: " + configured, ex);
        }
    }
}
