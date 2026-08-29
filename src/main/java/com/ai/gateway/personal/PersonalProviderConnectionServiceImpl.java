package com.ai.gateway.personal;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.core.model.Provider;
import com.ai.gateway.personal.dto.*;
import com.ai.gateway.personal.entity.PersonalAccount;
import com.ai.gateway.personal.entity.PersonalProviderConnection;
import com.ai.gateway.personal.repository.PersonalAccountRepository;
import com.ai.gateway.personal.repository.PersonalProviderConnectionRepository;
import com.ai.gateway.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PersonalProviderConnectionServiceImpl
        implements PersonalProviderConnectionService {

    private final PersonalAccountRepository accountRepository;
    private final PersonalProviderConnectionRepository connectionRepository;
    private final EncryptionUtil encryptionUtil;

    @Value("${alroute.personal.providers.openai.base-url:https://api.openai.com}")
    private String openAiBaseUrl;

    @Value("${alroute.personal.providers.gemini.base-url:https://generativelanguage.googleapis.com}")
    private String geminiBaseUrl;

    @Value("${alroute.personal.providers.anthropic.base-url:https://api.anthropic.com}")
    private String anthropicBaseUrl;

    @Override
    @Transactional(readOnly = true)
    public List<PersonalProviderConnectionResponse> list(AuthenticationContext context) {
        java.util.UUID accountId = requireAccount(context);

        return connectionRepository
                .findAllByPersonalAccountIdOrderByProviderAsc(accountId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public PersonalProviderConnectionResponse connect(
            AuthenticationContext context,
            PersonalProviderConnectRequest request) {

        java.util.UUID accountId = requireAccount(context);
        Provider provider = request.provider();

        if (connectionRepository
                .findByPersonalAccountIdAndProvider(accountId, provider)
                .isPresent()) {
            throw new PersonalAccountConflictException(
                    "A Personal connection already exists for "
                            + provider + ".");
        }

        String apiKey = request.apiKey().trim();

        /*
         * Validate before persisting. A provider secret is never written to
         * the database when the initial connection is rejected.
         */
        PersonalProviderValidationResponse validation =
                validateCredential(provider, apiKey);

        if (!validation.valid()) {
            throw new PersonalProviderConnectionException(
                    "Provider credential validation failed: "
                            + validation.message());
        }

        PersonalAccount account =
                accountRepository.findById(accountId)
                        .orElseThrow(() ->
                                new PersonalProviderConnectionException(
                                        "Personal account not found."));

        LocalDateTime now = LocalDateTime.now();

        PersonalProviderConnection connection =
                PersonalProviderConnection.builder()
                        .personalAccount(account)
                        .provider(provider)
                        .displayName(request.displayName().trim())
                        .encryptedApiKey(encryptionUtil.encrypt(apiKey))
                        .status("ACTIVE")
                        .lastValidatedAt(now)
                        .validationMessage("Validated successfully.")
                        .createdAt(now)
                        .updatedAt(now)
                        .build();

        return toResponse(connectionRepository.save(connection));
    }

    @Override
    @Transactional
    public PersonalProviderValidationResponse validate(
            AuthenticationContext context,
            Provider provider) {

        java.util.UUID accountId = requireAccount(context);

        PersonalProviderConnection connection =
                connectionRepository
                        .findByPersonalAccountIdAndProvider(accountId, provider)
                        .orElseThrow(() ->
                                new PersonalProviderConnectionException(
                                        "No Personal connection exists for "
                                                + provider + "."));

        String apiKey =
                encryptionUtil.decrypt(connection.getEncryptedApiKey());

        PersonalProviderValidationResponse result =
                validateCredential(provider, apiKey);

        LocalDateTime now = LocalDateTime.now();
        connection.setLastValidatedAt(now);
        connection.setUpdatedAt(now);
        connection.setStatus(result.valid() ? "ACTIVE" : "INVALID");
        connection.setValidationMessage(result.message());

        return result;
    }

    @Override
    @Transactional
    public void disconnect(
            AuthenticationContext context,
            Provider provider) {

        java.util.UUID accountId = requireAccount(context);

        PersonalProviderConnection connection =
                connectionRepository
                        .findByPersonalAccountIdAndProvider(accountId, provider)
                        .orElseThrow(() ->
                                new PersonalProviderConnectionException(
                                        "No Personal connection exists for "
                                                + provider + "."));

        connection.setStatus("DISCONNECTED");
        connection.setUpdatedAt(LocalDateTime.now());
        connection.setValidationMessage("Disconnected by user.");
    }

    private PersonalProviderValidationResponse validateCredential(
            Provider provider,
            String apiKey) {

        if (apiKey == null || apiKey.isBlank()) {
            return invalid(provider, "Provider API key is required.");
        }

        try {
            switch (provider) {
                case OPENAI -> validateOpenAi(apiKey);
                case GEMINI -> validateGemini(apiKey);
                case CLAUDE -> validateAnthropic(apiKey);
                case OLLAMA -> throw new PersonalProviderConnectionException(
                        "OLLAMA is local infrastructure and is not a Personal BYOK provider.");
                default -> throw new PersonalProviderConnectionException(
                        "Unsupported Personal provider: " + provider);
            }

            return new PersonalProviderValidationResponse(
                    provider,
                    true,
                    "ACTIVE",
                    "Validated successfully.",
                    LocalDateTime.now());

        } catch (PersonalProviderConnectionException ex) {
            throw ex;
        } catch (Exception ex) {
            return invalid(provider, safeMessage(ex));
        }
    }

    private void validateOpenAi(String apiKey) {
        RestClient.create(openAiBaseUrl)
                .get()
                .uri("/v1/models")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .retrieve()
                .toBodilessEntity();
    }

    private void validateGemini(String apiKey) {
        RestClient.create(geminiBaseUrl)
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1beta/models")
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .toBodilessEntity();
    }

    private void validateAnthropic(String apiKey) {
        RestClient.create(anthropicBaseUrl)
                .get()
                .uri("/v1/models")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .retrieve()
                .toBodilessEntity();
    }

    private PersonalProviderValidationResponse invalid(
            Provider provider,
            String message) {
        return new PersonalProviderValidationResponse(
                provider,
                false,
                "INVALID",
                message,
                LocalDateTime.now());
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();

        if (message == null || message.isBlank()) {
            return "Provider credential validation failed.";
        }

        /*
         * Never return an exception message containing a provider secret.
         * Provider SDK/HTTP implementations normally don't echo API keys,
         * but the response boundary remains deliberately generic.
         */
        return "Provider credential validation failed.";
    }

    private PersonalProviderConnectionResponse toResponse(
            PersonalProviderConnection connection) {

        String encrypted = connection.getEncryptedApiKey();
        String masked = "••••••••";
        if (encrypted != null && !encrypted.isBlank()) {
            masked = "Connected";
        }

        return new PersonalProviderConnectionResponse(
                connection.getId(),
                connection.getProvider(),
                connection.getDisplayName(),
                connection.getStatus(),
                masked,
                connection.getLastValidatedAt(),
                connection.getValidationMessage(),
                connection.getCreatedAt(),
                connection.getUpdatedAt());
    }

    private java.util.UUID requireAccount(AuthenticationContext context) {
        if (context == null
                || !context.isPersonalPrincipal()
                || context.getPersonalAccountId() == null) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Personal authentication is required.");
        }

        return context.getPersonalAccountId();
    }
}
