package com.ai.gateway.routing.intelligence;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.dto.ChatRequest;
import com.ai.gateway.routing.RoutingContext;
import com.ai.gateway.routing.engine.RoutingCandidate;
import com.ai.gateway.routing.policy.RoutingPolicy;
import com.ai.gateway.routing.scoring.CandidateScoringContext;
import com.ai.gateway.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RoutingDecisionIntelligenceService implements RoutingDecisionIntelligence {

    private final CandidateCapabilityMatcher capabilityMatcher;
    private final AdaptiveRoutingScoringService adaptiveScoringService;
    private final RoutingRuntimeSignalService runtimeSignalService;
    private final UnityRoutingProperties unityRoutingProperties;

    public RoutingDecisionContext context(RoutingContext routingContext) {
        ChatRequest request = routingContext.request();
        AuthenticationContext auth = routingContext.authenticationContext();
        boolean unityRequested = request != null && request.isExtensiveResearch();
        if (unityRequested && !unityRoutingProperties.isEnabled()) {
            throw new BusinessException("Unity Extensive Research is disabled by platform configuration.");
        }
        boolean unity = unityRequested;
        RoutingPriority priority = parsePriority(request == null ? null : request.getRoutingPriority());
        return new RoutingDecisionContext(
                auth == null ? null : auth.getTenantId(),
                auth == null ? null : auth.getTenantCode(),
                auth == null ? null : auth.getDefaultProvider(),
                auth == null ? null : auth.getDefaultModel(),
                request == null ? Set.of() : request.getRequiredCapabilities(),
                unityRequested,
                unity,
                request == null ? null : request.getExecutionRole(),
                priority);
    }

    public List<RoutingCandidate> applyCapabilityMatching(List<RoutingCandidate> candidates, RoutingDecisionContext context) {
        return capabilityMatcher.filter(candidates, context);
    }

    public CandidateScoringContext scoringContext(RoutingPolicy policy, RoutingDecisionContext context) {
        return new CandidateScoringContext(
                policy, 1_000, 1_000,
                context != null && context.extensiveResearchEnabled(),
                context == null ? null : context.executionRole(),
                context,
                context == null ? java.util.Map.of() : adaptiveScoringService.adapt(context),
                context == null ? RoutingRuntimeSignals.empty() : runtimeSignalService.snapshot());
    }

    public RoutingDecisionExplanation explain(RoutingDecisionContext context, int candidateCount, String reason) {
        List<String> signals = new ArrayList<>(List.of("cost", "latency", "availability", "policy-preference"));
        if (context != null && context.routingPriority() != RoutingPriority.BALANCED) signals.add("routing-priority:" + context.routingPriority());
        if (context != null && context.extensiveResearchEnabled()) signals.add("unity:extensive-research");
        return new RoutingDecisionExplanation(
                reason, signals, List.of(),
                context == null ? List.of() : context.requiredCapabilities().stream().toList());
    }

    private RoutingPriority parsePriority(String value) {
        if (value == null || value.isBlank()) return RoutingPriority.BALANCED;
        try { return RoutingPriority.valueOf(value.trim().toUpperCase()); }
        catch (IllegalArgumentException ignored) { return RoutingPriority.BALANCED; }
    }
}
