package com.ai.gateway.core.routing.scoring.objective.profile;

import com.ai.gateway.core.routing.scoring.objective.RoutingObjective;
import com.ai.gateway.core.routing.scoring.objective.RoutingObjectiveWeights;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of immutable routing optimization profiles.
 *
 * <p>Built-in profiles are registered once at construction time. Custom
 * profiles can be registered explicitly. Runtime lookup is O(1) and does not
 * require a database or remote call.</p>
 */
public final class RoutingOptimizationProfileRegistry {

    public static final String BALANCED = "BALANCED";
    public static final String COST_OPTIMIZED = "COST_OPTIMIZED";
    public static final String QUALITY_OPTIMIZED = "QUALITY_OPTIMIZED";
    public static final String LATENCY_OPTIMIZED = "LATENCY_OPTIMIZED";

    private final Map<String, RoutingOptimizationProfile> profiles =
            new ConcurrentHashMap<>();

    public RoutingOptimizationProfileRegistry() {
        registerBuiltIns();
    }

    public RoutingOptimizationProfile get(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return profiles.get(normalizeName(name));
    }

    public boolean contains(String name) {
        return get(name) != null;
    }

    public void register(RoutingOptimizationProfile profile) {
        Objects.requireNonNull(profile, "Optimization profile is required.");

        String key = normalizeName(profile.name());

        if (isBuiltIn(key) && profile.type() != builtInType(key)) {
            throw new IllegalArgumentException(
                    "Built-in profile type cannot be changed: " + key);
        }

        profiles.put(key, profile);
    }

    public int size() {
        return profiles.size();
    }

    private void registerBuiltIns() {
        register(builtIn(
                BALANCED,
                RoutingOptimizationProfileType.BALANCED,
                Map.of(
                        RoutingObjective.COST, 0.15,
                        RoutingObjective.LATENCY, 0.15,
                        RoutingObjective.QUALITY, 0.20,
                        RoutingObjective.RELIABILITY, 0.20,
                        RoutingObjective.CAPABILITY_FIT, 0.10,
                        RoutingObjective.POLICY_PREFERENCE, 0.05,
                        RoutingObjective.HEALTH, 0.10,
                        RoutingObjective.AVAILABILITY, 0.05)));

        register(builtIn(
                COST_OPTIMIZED,
                RoutingOptimizationProfileType.COST_OPTIMIZED,
                Map.of(
                        RoutingObjective.COST, 0.40,
                        RoutingObjective.LATENCY, 0.10,
                        RoutingObjective.QUALITY, 0.15,
                        RoutingObjective.RELIABILITY, 0.15,
                        RoutingObjective.CAPABILITY_FIT, 0.05,
                        RoutingObjective.POLICY_PREFERENCE, 0.05,
                        RoutingObjective.HEALTH, 0.05,
                        RoutingObjective.AVAILABILITY, 0.05)));

        register(builtIn(
                QUALITY_OPTIMIZED,
                RoutingOptimizationProfileType.QUALITY_OPTIMIZED,
                Map.of(
                        RoutingObjective.COST, 0.05,
                        RoutingObjective.LATENCY, 0.10,
                        RoutingObjective.QUALITY, 0.40,
                        RoutingObjective.RELIABILITY, 0.20,
                        RoutingObjective.CAPABILITY_FIT, 0.10,
                        RoutingObjective.POLICY_PREFERENCE, 0.05,
                        RoutingObjective.HEALTH, 0.05,
                        RoutingObjective.AVAILABILITY, 0.05)));

        register(builtIn(
                LATENCY_OPTIMIZED,
                RoutingOptimizationProfileType.LATENCY_OPTIMIZED,
                Map.of(
                        RoutingObjective.COST, 0.05,
                        RoutingObjective.LATENCY, 0.40,
                        RoutingObjective.QUALITY, 0.15,
                        RoutingObjective.RELIABILITY, 0.15,
                        RoutingObjective.CAPABILITY_FIT, 0.05,
                        RoutingObjective.POLICY_PREFERENCE, 0.05,
                        RoutingObjective.HEALTH, 0.10,
                        RoutingObjective.AVAILABILITY, 0.05)));
    }

    private RoutingOptimizationProfile builtIn(
            String name,
            RoutingOptimizationProfileType type,
            Map<RoutingObjective, Double> weights) {
        return new RoutingOptimizationProfile(
                name,
                type,
                new RoutingObjectiveWeights(
                        new EnumMap<>(weights)));
    }

    private boolean isBuiltIn(String name) {
        return BALANCED.equals(name)
                || COST_OPTIMIZED.equals(name)
                || QUALITY_OPTIMIZED.equals(name)
                || LATENCY_OPTIMIZED.equals(name);
    }

    private RoutingOptimizationProfileType builtInType(String name) {
        return switch (name) {
            case BALANCED -> RoutingOptimizationProfileType.BALANCED;
            case COST_OPTIMIZED -> RoutingOptimizationProfileType.COST_OPTIMIZED;
            case QUALITY_OPTIMIZED -> RoutingOptimizationProfileType.QUALITY_OPTIMIZED;
            case LATENCY_OPTIMIZED -> RoutingOptimizationProfileType.LATENCY_OPTIMIZED;
            default -> throw new IllegalArgumentException(
                    "Unknown built-in profile: " + name);
        };
    }

    static String normalizeName(String name) {
        return name.trim().toUpperCase(Locale.ROOT);
    }
}
