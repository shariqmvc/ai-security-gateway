package com.ai.gateway.core.routing.policy;

import com.ai.gateway.core.model.Provider;

import java.util.List;
import java.util.Objects;

/**
 * Declarative routing policy describing provider/model eligibility
 * and routing preferences.
 *
 * This class intentionally contains policy data only.
 * Evaluation is handled by RoutingPolicyService.
 */
public record RoutingPolicy(

        boolean enabled,

        List<Provider> allowedProviders,

        List<String> allowedModels,

        Provider preferredProvider,

        String preferredModel

) {

    public RoutingPolicy {
        allowedProviders =
                allowedProviders == null
                        ? List.of()
                        : List.copyOf(
                        allowedProviders.stream()
                                .filter(Objects::nonNull)
                                .toList());

        allowedModels =
                allowedModels == null
                        ? List.of()
                        : List.copyOf(
                        allowedModels.stream()
                                .filter(Objects::nonNull)
                                .filter(model -> !model.isBlank())
                                .toList());
    }

    public boolean allowsProvider(Provider provider) {

        if (provider == null) {
            return false;
        }

        return allowedProviders.isEmpty()
                || allowedProviders.contains(provider);
    }

    public boolean allowsModel(String model) {

        if (model == null || model.isBlank()) {
            return false;
        }

        return allowedModels.isEmpty()
                || allowedModels.contains(model);
    }

    public boolean hasPreferredProvider() {
        return preferredProvider != null;
    }

    public boolean hasPreferredModel() {
        return preferredModel != null
                && !preferredModel.isBlank();
    }
}