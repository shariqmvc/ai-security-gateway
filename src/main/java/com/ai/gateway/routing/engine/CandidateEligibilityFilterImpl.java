package com.ai.gateway.routing.engine;

import com.ai.gateway.enums.Provider;
import com.ai.gateway.routing.policy.RoutingPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CandidateEligibilityFilterImpl
        implements CandidateEligibilityFilter {

    @Override
    public List<RoutingCandidate> filter(
            List<RoutingCandidate> candidates,
            RoutingPolicy policy) {

        if (policy == null || !policy.enabled()) {
            return List.of();
        }

        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        return candidates.stream()
                .filter(Objects::nonNull)
                .filter(candidate ->
                        candidate.provider() != null)
                .filter(candidate ->
                        candidate.model() != null
                                && !candidate.model().isBlank())
                .filter(candidate ->
                        policy.allowsProvider(
                                candidate.provider()))
                .filter(candidate ->
                        policy.allowsModel(
                                candidate.model()))
                .distinct()
                .toList();
    }
}