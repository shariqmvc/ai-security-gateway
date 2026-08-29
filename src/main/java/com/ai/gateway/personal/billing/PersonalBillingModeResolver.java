package com.ai.gateway.personal.billing;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.core.model.Provider;
import com.ai.gateway.personal.repository.PersonalProviderConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PersonalBillingModeResolver {
    private final PersonalProviderConnectionRepository connections;
    private final PersonalBillingProperties properties;

    public PersonalBillingMode resolve(AuthenticationContext context, Provider provider,
                                       String model, String requestedMode) {
        if (context == null || !context.isPersonalPrincipal())
            throw new PersonalBillingModeException("Personal authentication is required.");
        UUID accountId = context.getPersonalAccountId();
        if (accountId == null) throw new PersonalBillingModeException("Personal account is required.");
        if (provider == null || model == null || model.isBlank())
            throw new PersonalBillingModeException("Provider and model are required.");

        PersonalBillingMode requested;
        try {
            requested = requestedMode == null || requestedMode.isBlank()
                    ? PersonalBillingMode.AUTO
                    : PersonalBillingMode.valueOf(requestedMode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new PersonalBillingModeException("Unsupported Personal billing mode: " + requestedMode);
        }

        return switch (requested) {
            case BYOK -> { requireByok(accountId, provider); yield PersonalBillingMode.BYOK; }
            case CREDIT -> PersonalBillingMode.CREDIT;
            case FREE -> { requireFree(provider, model); yield PersonalBillingMode.FREE; }
            case AUTO -> hasByok(accountId, provider) ? PersonalBillingMode.BYOK : PersonalBillingMode.CREDIT;
        };
    }

    private boolean hasByok(UUID accountId, Provider provider) {
        return connections.findByPersonalAccountIdAndProvider(accountId, provider)
                .map(c -> "ACTIVE".equalsIgnoreCase(c.getStatus())).orElse(false);
    }
    private void requireByok(UUID accountId, Provider provider) {
        if (!hasByok(accountId, provider))
            throw new PersonalBillingModeException("No active Personal BYOK connection exists for " + provider + ".");
    }
    private void requireFree(Provider provider, String model) {
        String key = provider.name() + ":" + model;
        if (!properties.getFreeModels().contains(key) && !properties.getFreeModels().contains(model))
            throw new PersonalBillingModeException("Selected model is not configured as a Personal free model.");
    }
}
