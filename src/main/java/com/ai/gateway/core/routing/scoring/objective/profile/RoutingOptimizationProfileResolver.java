package com.ai.gateway.core.routing.scoring.objective.profile;

import java.util.Objects;

/**
 * Resolves an optimization profile using deterministic precedence.
 *
 * <p>Precedence:
 * explicit request/profile -> tenant policy profile -> configured default
 * -> BALANCED.</p>
 *
 * <p>This resolver is deliberately independent of persistence and request
 * DTOs. Callers can supply profile names from the appropriate policy layer.
 * Profile lookup itself is in-memory and O(1).</p>
 */
public final class RoutingOptimizationProfileResolver {

    private final RoutingOptimizationProfileRegistry registry;
    private final String configuredDefaultProfile;

    public RoutingOptimizationProfileResolver(
            RoutingOptimizationProfileRegistry registry,
            String configuredDefaultProfile) {

        this.registry = Objects.requireNonNull(
                registry,
                "Optimization profile registry is required.");

        if (configuredDefaultProfile == null
                || configuredDefaultProfile.isBlank()) {
            this.configuredDefaultProfile =
                    RoutingOptimizationProfileRegistry.BALANCED;
        } else {
            String normalized =
                    RoutingOptimizationProfileRegistry.normalizeName(
                            configuredDefaultProfile);

            if (!registry.contains(normalized)) {
                throw new IllegalArgumentException(
                        "Configured default optimization profile does not exist: "
                                + configuredDefaultProfile);
            }

            this.configuredDefaultProfile = normalized;
        }
    }

    public RoutingOptimizationProfile resolve(
            String explicitProfile,
            String tenantPolicyProfile) {

        RoutingOptimizationProfile resolved =
                registry.get(explicitProfile);

        if (resolved != null) {
            return resolved;
        }

        resolved = registry.get(tenantPolicyProfile);

        if (resolved != null) {
            return resolved;
        }

        resolved = registry.get(configuredDefaultProfile);

        if (resolved != null) {
            return resolved;
        }

        return Objects.requireNonNull(
                registry.get(RoutingOptimizationProfileRegistry.BALANCED),
                "BALANCED optimization profile must be registered.");
    }

    public RoutingOptimizationProfile resolve() {
        return resolve(null, null);
    }
}
