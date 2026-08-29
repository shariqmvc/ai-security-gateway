package com.ai.gateway.core.routing.intelligence;

import com.ai.gateway.core.routing.engine.RoutingCandidate;
import com.ai.gateway.core.routing.registry.ModelDefinition;
import com.ai.gateway.core.routing.registry.ModelRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CandidateCapabilityMatcherImpl implements CandidateCapabilityMatcher {

    private final ModelRegistry modelRegistry;

    @Override
    public List<RoutingCandidate> filter(List<RoutingCandidate> candidates, RoutingDecisionContext context) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        if (context == null || context.requiredCapabilities().isEmpty()) return candidates.stream().filter(c -> c != null).toList();

        return candidates.stream()
                .filter(c -> c != null)
                .filter(c -> modelRegistry.find(c.provider(), c.model())
                        .map(ModelDefinition::capabilities)
                        .map(capabilities -> capabilities.containsAll(context.requiredCapabilities()))
                        .orElse(false))
                .distinct()
                .toList();
    }
}
