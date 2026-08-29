package com.ai.gateway.personal;

import com.ai.gateway.core.model.Provider;
import com.ai.gateway.personal.entity.PersonalProviderConnection;
import com.ai.gateway.personal.repository.PersonalProviderConnectionRepository;
import com.ai.gateway.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Shared gateway boundary for Personal BYOK credentials.
 *
 * Personal provider credentials are resolved from the database only.
 * Business/tenant provider configuration is intentionally not consulted.
 */
@Service
@RequiredArgsConstructor
public class PersonalProviderCredentialResolver {

    private final PersonalProviderConnectionRepository repository;
    private final EncryptionUtil encryptionUtil;

    @Transactional(readOnly = true)
    public String resolveApiKey(UUID personalAccountId, Provider provider) {
        if (personalAccountId == null || provider == null) {
            throw new PersonalProviderConnectionException(
                    "Personal account and provider are required.");
        }

        PersonalProviderConnection connection =
                repository.findByPersonalAccountIdAndProvider(
                                personalAccountId, provider)
                        .orElseThrow(() ->
                                new PersonalProviderConnectionException(
                                        "No Personal provider connection exists for "
                                                + provider + "."));

        if (!"ACTIVE".equals(connection.getStatus())) {
            throw new PersonalProviderConnectionException(
                    "Personal provider connection is not active for "
                            + provider + ".");
        }

        return encryptionUtil.decrypt(connection.getEncryptedApiKey());
    }
}
