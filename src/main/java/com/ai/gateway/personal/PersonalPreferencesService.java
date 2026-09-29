package com.ai.gateway.personal;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.core.model.Provider;
import com.ai.gateway.personal.dto.PersonalPreferencesResponse;
import com.ai.gateway.personal.dto.PersonalPreferencesUpdateRequest;
import com.ai.gateway.core.contract.ChatRequest;
import com.ai.gateway.personal.entity.PersonalAccount;
import com.ai.gateway.personal.preferences.entity.PersonalAccountPreferences;
import com.ai.gateway.personal.preferences.repository.PersonalAccountPreferencesRepository;
import com.ai.gateway.personal.repository.PersonalAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PersonalPreferencesService {

    private static final Set<String> BILLING_MODES = Set.of("AUTO", "BYOK", "CREDIT", "FREE");
    private static final Set<String> ROUTING_PRIORITIES = Set.of("BALANCED", "COST", "LATENCY", "RELIABILITY");

    private final PersonalAccountRepository accountRepository;
    private final PersonalAccountPreferencesRepository preferencesRepository;

    /**
     * Applies persisted Personal preferences only where the request has not
     * supplied an explicit value. Explicit request fields always win.
     */
    @Transactional(readOnly = true)
    public void applyRequestDefaults(AuthenticationContext context, ChatRequest request) {
        if (context == null || !context.isPersonalPrincipal() || request == null) {
            return;
        }

        PersonalAccount account = requireActiveAccount(context);
        PersonalAccountPreferences preferences = ensurePreferences(account);

        if (request.getProvider() == null && request.getModel() == null) {
            String defaultProvider = normalizeOptional(preferences.getDefaultProvider());
            String defaultModel = normalizeOptional(preferences.getDefaultModel());
            if (defaultProvider != null) {
                request.setProvider(Provider.valueOf(defaultProvider.toUpperCase(Locale.ROOT)));
            }
            if (defaultModel != null) {
                request.setModel(defaultModel);
            }
        } else if (request.getProvider() == null && request.getModel() != null
                && !request.getModel().isBlank()) {
            // An explicit model is sufficient to let the model registry derive
            // its provider. Never inject a provider preference over it.
        } else if (request.getProvider() != null && (request.getModel() == null || request.getModel().isBlank())) {
            // An explicit provider remains authoritative; its registry default
            // model will be selected by the routing strategy.
        }

        if (request.getBillingMode() == null || request.getBillingMode().isBlank()) {
            request.setBillingMode(preferences.getBillingMode());
        }
        if (request.getRoutingPriority() == null || request.getRoutingPriority().isBlank()) {
            request.setRoutingPriority(preferences.getRoutingPriority());
        }
    }

    @Transactional
    public PersonalPreferencesResponse get(AuthenticationContext context) {
        PersonalAccount account = requireActiveAccount(context);
        return toResponse(ensurePreferences(account));
    }

    @Transactional
    public PersonalPreferencesResponse update(
            AuthenticationContext context,
            PersonalPreferencesUpdateRequest request) {

        PersonalAccount account = requireActiveAccount(context);
        PersonalAccountPreferences preferences = ensurePreferences(account);

        if (request.defaultProvider() != null) {
            preferences.setDefaultProvider(normalizeProvider(request.defaultProvider()));
        }
        if (request.defaultModel() != null) {
            preferences.setDefaultModel(normalizeOptional(request.defaultModel()));
        }
        if (request.billingMode() != null) {
            preferences.setBillingMode(normalizeEnum(
                    request.billingMode(), BILLING_MODES, "billingMode"));
        }
        if (request.routingPriority() != null) {
            preferences.setRoutingPriority(normalizeEnum(
                    request.routingPriority(), ROUTING_PRIORITIES, "routingPriority"));
        }

        preferences.setUpdatedAt(LocalDateTime.now());
        return toResponse(preferencesRepository.save(preferences));
    }

    private PersonalAccountPreferences ensurePreferences(PersonalAccount account) {
        return preferencesRepository.findByPersonalAccountId(account.getId())
                .orElseGet(() -> preferencesRepository.save(
                        PersonalAccountPreferences.builder()
                                .personalAccount(account)
                                .billingMode("AUTO")
                                .routingPriority("BALANCED")
                                .updatedAt(LocalDateTime.now())
                                .build()));
    }

    private PersonalAccount requireActiveAccount(AuthenticationContext context) {
        if (context == null
                || !context.isPersonalPrincipal()
                || context.getPersonalAccountId() == null) {
            throw new AccessDeniedException("Personal authentication is required.");
        }

        PersonalAccount account = accountRepository.findById(context.getPersonalAccountId())
                .orElseThrow(() -> new AccessDeniedException("Personal account not found."));

        if (!"ACTIVE".equalsIgnoreCase(account.getStatus())) {
            throw new AccessDeniedException("Personal account is not active.");
        }

        return account;
    }

    private String normalizeProvider(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return null;
        }

        try {
            return Provider.valueOf(normalized.toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported defaultProvider: " + value);
        }
    }

    private String normalizeEnum(String value, Set<String> allowed, String field) {
        String normalized = normalizeOptional(value);
        if (normalized == null || !allowed.contains(normalized.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "Unsupported " + field + ": " + value);
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private PersonalPreferencesResponse toResponse(PersonalAccountPreferences preferences) {
        return new PersonalPreferencesResponse(
                preferences.getDefaultProvider(),
                preferences.getDefaultModel(),
                preferences.getBillingMode(),
                preferences.getRoutingPriority());
    }
}
