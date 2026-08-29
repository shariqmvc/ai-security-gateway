package com.ai.gateway.core.routing.selection;

/**
 * Immutable request for terminal candidate selection.
 *
 * <p>Profile resolution/scoring remains upstream of this contract. The
 * selection layer decides how many of the already scored candidates should
 * be returned.</p>
 */
public record RoutingSelectionRequest(
        RoutingSelectionMode mode,
        int topN,
        String primaryProfile,
        String escalationProfile) {

    public RoutingSelectionRequest {
        if (mode == null) {
            throw new IllegalArgumentException("Routing selection mode is required.");
        }
        primaryProfile = normalize(primaryProfile);
        escalationProfile = normalize(escalationProfile);
        if (mode == RoutingSelectionMode.PRIMARY_ESCALATION
                && escalationProfile != null
                && primaryProfile == null) {
            throw new IllegalArgumentException(
                    "Escalation profile requires a primary profile.");
        }
        if (mode == RoutingSelectionMode.TOP_N && topN < 1) {
            throw new IllegalArgumentException("TOP_N requires topN >= 1.");
        }
        if (mode != RoutingSelectionMode.TOP_N && topN < 0) {
            throw new IllegalArgumentException("topN cannot be negative.");
        }
    }

    public static RoutingSelectionRequest single() {
        return new RoutingSelectionRequest(RoutingSelectionMode.SINGLE, 1, null, null);
    }

    public static RoutingSelectionRequest topN(int topN) {
        return new RoutingSelectionRequest(RoutingSelectionMode.TOP_N, topN, null, null);
    }

    public static RoutingSelectionRequest primaryEscalation() {
        return new RoutingSelectionRequest(
                RoutingSelectionMode.PRIMARY_ESCALATION,
                2,
                null,
                null);
    }

    public static RoutingSelectionRequest primaryEscalation(
            String primaryProfile,
            String escalationProfile) {
        return new RoutingSelectionRequest(
                RoutingSelectionMode.PRIMARY_ESCALATION,
                2,
                primaryProfile,
                escalationProfile);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
