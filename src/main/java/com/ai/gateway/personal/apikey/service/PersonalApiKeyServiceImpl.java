package com.ai.gateway.personal.apikey.service;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.authentication.AuthenticationType;
import com.ai.gateway.personal.apikey.dto.*;
import com.ai.gateway.personal.apikey.entity.PersonalApiKey;
import com.ai.gateway.personal.apikey.repository.PersonalApiKeyRepository;
import com.ai.gateway.personal.entity.PersonalAccount;
import com.ai.gateway.personal.entity.PersonalUser;
import com.ai.gateway.personal.repository.PersonalAccountRepository;
import com.ai.gateway.personal.repository.PersonalUserRepository;
import com.ai.gateway.security.SecurityRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class PersonalApiKeyServiceImpl implements PersonalApiKeyService {
    private static final int KEY_BYTES = 32;
    private static final Set<String> ALLOWED_SCOPES = Set.of("chat", "rag", "models");
    private static final Set<String> DEFAULT_SCOPES = Set.of("chat", "models");

    private final PersonalApiKeyRepository keyRepository;
    private final PersonalAccountRepository accountRepository;
    private final PersonalUserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public PersonalApiKeyServiceImpl(PersonalApiKeyRepository keyRepository,
                                     PersonalAccountRepository accountRepository,
                                     PersonalUserRepository userRepository) {
        this.keyRepository = keyRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public PersonalApiKeyCreateResponse create(AuthenticationContext context, PersonalApiKeyCreateRequest request) {
        requireSession(context);
        PersonalAccount account = accountRepository.findById(context.getPersonalAccountId())
                .orElseThrow(() -> new AccessDeniedException("Personal account not found."));
        Set<String> scopes = normalizeScopes(request.scopes());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expires = request.expiresInDays() == null ? null : now.plusDays(request.expiresInDays());
        return createInternal(account, request.name().trim(), scopes, now, expires);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PersonalApiKeyResponse> list(AuthenticationContext context) {
        requireSession(context);
        return keyRepository.findByPersonalAccountIdOrderByCreatedAtDesc(context.getPersonalAccountId())
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public void revoke(AuthenticationContext context, UUID keyId) {
        requireSession(context);
        PersonalApiKey key = keyRepository.findByIdAndPersonalAccountId(keyId, context.getPersonalAccountId())
                .orElseThrow(() -> new AccessDeniedException("Personal API key not found."));
        if (key.getRevokedAt() == null) key.setRevokedAt(LocalDateTime.now());
    }

    @Override
    @Transactional
    public PersonalApiKeyCreateResponse rotate(AuthenticationContext context, UUID keyId) {
        requireSession(context);
        PersonalApiKey old = keyRepository.findByIdAndPersonalAccountId(keyId, context.getPersonalAccountId())
                .orElseThrow(() -> new AccessDeniedException("Personal API key not found."));
        if (old.getRevokedAt() == null) old.setRevokedAt(LocalDateTime.now());
        PersonalAccount account = old.getPersonalAccount();
        Set<String> scopes = parseScopes(old.getScopes());
        return createInternal(account, old.getName(), scopes, LocalDateTime.now(), old.getExpiresAt());
    }

    @Override
    @Transactional
    public AuthenticationContext authenticate(String rawKey) {
        if (rawKey == null || !rawKey.startsWith("arpk_") || rawKey.length() < 20) {
            throw new AccessDeniedException("Invalid Personal API key.");
        }
        PersonalApiKey key = keyRepository.findByKeyHash(hash(rawKey))
                .orElseThrow(() -> new AccessDeniedException("Invalid Personal API key."));
        LocalDateTime now = LocalDateTime.now();
        if (key.getRevokedAt() != null || (key.getExpiresAt() != null && key.getExpiresAt().isBefore(now))) {
            throw new AccessDeniedException("Invalid or expired Personal API key.");
        }
        PersonalAccount account = key.getPersonalAccount();
        PersonalUser user = account.getUser();
        if (!"ACTIVE".equals(account.getStatus()) || !"ACTIVE".equals(user.getStatus())) {
            throw new AccessDeniedException("Personal account is not active.");
        }
        key.setLastUsedAt(now);
        return AuthenticationContext.builder()
                .authenticationType(AuthenticationType.PERSONAL_API_KEY)
                .apiKeyId(key.getId())
                .clientName(key.getName())
                .personalUserId(user.getId())
                .personalAccountId(account.getId())
                .tenantId(null).tenantCode(null).tenantName(null).tenantType(null)
                .defaultProvider(null).defaultModel(null).schemaName(null)
                .role(SecurityRole.TENANT_USER)
                .platformPrincipal(false).personalPrincipal(true)
                .personalApiKeyScopes(parseScopes(key.getScopes()))
                .build();
    }

    @Override public boolean hasScope(AuthenticationContext context, String scope) {
        return scopes(context).contains(scope.toLowerCase(Locale.ROOT));
    }
    @Override public Set<String> scopes(AuthenticationContext context) {
        return context == null || context.getPersonalApiKeyScopes() == null
                ? Set.of() : context.getPersonalApiKeyScopes();
    }

    private PersonalApiKeyCreateResponse createInternal(PersonalAccount account, String name, Set<String> scopes,
                                                         LocalDateTime now, LocalDateTime expires) {
        for (int attempt = 0; attempt < 5; attempt++) {
            String raw = generateKey();
            String hash = hash(raw);
            if (keyRepository.findByKeyHash(hash).isPresent()) continue;
            PersonalApiKey key = PersonalApiKey.builder()
                    .personalAccount(account).name(name).keyHash(hash)
                    .keyPrefix(raw.substring(0, Math.min(16, raw.length())))
                    .scopes(String.join(",", new TreeSet<>(scopes)))
                    .createdAt(now).expiresAt(expires).build();
            keyRepository.save(key);
            return new PersonalApiKeyCreateResponse(key.getId(), key.getName(), raw,
                    key.getKeyPrefix(), scopes, key.getCreatedAt(), key.getExpiresAt());
        }
        throw new IllegalStateException("Unable to generate a unique Personal API key.");
    }

    private Set<String> normalizeScopes(Set<String> requested) {
        if (requested == null || requested.isEmpty()) return DEFAULT_SCOPES;
        Set<String> result = new TreeSet<>();
        for (String value : requested) {
            if (value == null || value.isBlank()) continue;
            String scope = value.trim().toLowerCase(Locale.ROOT);
            if (!ALLOWED_SCOPES.contains(scope)) throw new IllegalArgumentException("Unsupported Personal API key scope: " + scope);
            result.add(scope);
        }
        if (result.isEmpty()) throw new IllegalArgumentException("At least one API key scope is required.");
        return Set.copyOf(result);
    }
    private Set<String> parseScopes(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return Set.of(value.split(","));
    }
    private void requireSession(AuthenticationContext context) {
        if (context == null || !context.isPersonalPrincipal()
                || context.getAuthenticationType() != AuthenticationType.PERSONAL_SESSION
                || context.getPersonalAccountId() == null) {
            throw new AccessDeniedException("Personal session authentication is required.");
        }
    }
    private PersonalApiKeyResponse toResponse(PersonalApiKey key) {
        boolean active = key.getRevokedAt() == null &&
                (key.getExpiresAt() == null || key.getExpiresAt().isAfter(LocalDateTime.now()));
        return new PersonalApiKeyResponse(key.getId(), key.getName(), key.getKeyPrefix(),
                parseScopes(key.getScopes()), key.getCreatedAt(), key.getLastUsedAt(),
                key.getExpiresAt(), key.getRevokedAt(), active);
    }
    private String generateKey() {
        byte[] bytes = new byte[KEY_BYTES]; secureRandom.nextBytes(bytes);
        return "arpk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { throw new IllegalStateException("SHA-256 is unavailable.", e); }
    }
}
