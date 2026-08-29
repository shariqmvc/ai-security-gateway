package com.ai.gateway.core.routing;

import com.ai.gateway.core.model.Provider;
import com.ai.gateway.core.routing.engine.RoutingCandidate;

import java.util.List;

public record RoutingDecision(
        Provider provider,
        String model,
        RoutingStrategy strategy,
        RoutingDecisionMetadata metadata,
        List<RoutingCandidate> selectedCandidates) {

    public RoutingDecision {

        /*
         * A RoutingCandidate represents an executable routing target
         * and therefore requires both provider and model.
         *
         * However, RoutingDecision is also used by analytics and
         * historical/diagnostic paths where provider/model may be
         * intentionally absent.
         *
         * Therefore:
         *
         *   valid provider + model
         *       -> create the implicit primary candidate
         *
         *   missing provider/model
         *       -> keep selectedCandidates empty
         *
         * This preserves the strong RoutingCandidate invariant
         * without breaking decision-level null-safe analytics.
         */
        if (selectedCandidates == null) {

            if (provider != null
                    && model != null
                    && !model.isBlank()) {

                selectedCandidates =
                        List.of(
                                new RoutingCandidate(
                                        provider,
                                        model));
            } else {

                selectedCandidates =
                        List.of();
            }

        } else {

            selectedCandidates =
                    List.copyOf(selectedCandidates);
        }
    }

    public RoutingDecision(
            Provider provider,
            String model,
            RoutingStrategy strategy,
            RoutingDecisionMetadata metadata) {

        this(
                provider,
                model,
                strategy,
                metadata,
                null);
    }

    public RoutingDecision(
            Provider provider,
            String model,
            RoutingStrategy strategy) {

        this(
                provider,
                model,
                strategy,
                RoutingDecisionMetadata.empty(),
                null);
    }
}