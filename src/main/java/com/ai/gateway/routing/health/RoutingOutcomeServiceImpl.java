package com.ai.gateway.routing.health;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.dto.AIRequest;
import com.ai.gateway.routing.RoutingDecision;
import com.ai.gateway.routing.RoutingDecisionMetadata;
import com.ai.gateway.routing.health.entity.RoutingOutcome;
import com.ai.gateway.routing.health.repository.RoutingOutcomeRepository;
import com.ai.gateway.tenant.TenantSchemaRoutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoutingOutcomeServiceImpl implements RoutingOutcomeService {

    private final RoutingOutcomeRepository repository;
    private final TenantSchemaRoutingService
            tenantSchemaRoutingService;

    @Override
    @Transactional
    public void recordSuccess(UUID requestId, AuthenticationContext auth, AIRequest request,
                              RoutingDecision decision, long latencyMs) {
        save(requestId, auth, request, decision, latencyMs, true, null);
    }

    @Override
    @Transactional
    public void recordFailure(UUID requestId, AuthenticationContext auth, AIRequest request,
                              RoutingDecision decision, long latencyMs, Throwable error) {
        save(requestId, auth, request, decision, latencyMs, false,
                error == null ? "UNKNOWN" : error.getClass().getSimpleName());
    }

    private void save(UUID requestId, AuthenticationContext auth, AIRequest request,
                      RoutingDecision decision, long latencyMs, boolean success, String failureCategory) {

        if (requestId == null || request == null
                || request.getProvider() == null
                || request.getModel() == null
                || auth == null
                || auth.getTenantId() == null) {
            return;
        }

        tenantSchemaRoutingService.useTenantSchema(auth.getTenantId());

        RoutingDecisionMetadata metadata = decision == null ? null : decision.metadata();

        String priority = null;
        String role = null;
        boolean unity = false;
        if (metadata != null) {
            unity = metadata.extensiveResearchEnabled();
            role = metadata.executionRole();
            if (metadata.explanation() != null) {
                priority = metadata.explanation().appliedSignals().stream()
                        .filter(s -> s.startsWith("routing-priority:"))
                        .map(s -> s.substring("routing-priority:".length()))
                        .findFirst().orElse(null);
            }
        }

        repository.save(RoutingOutcome.builder()
                .requestId(requestId)
                .tenantId(auth == null ? null : auth.getTenantId())
                .provider(request.getProvider())
                .model(request.getModel())
                .routingStrategy(decision == null ? null : decision.strategy())
                .selectedScore(metadata == null ? null : metadata.selectedScore())
                .selectedRank(metadata == null ? null : metadata.selectedRank())
                .candidateCount(metadata == null ? null : metadata.candidateCount())
                .selectionReason(metadata == null ? null : metadata.selectionReason())
                .routingPriority(priority)
                .extensiveResearch(unity)
                .executionRole(role)
                .success(success)
                .failureCategory(failureCategory)
                .latencyMs(Math.max(0L, latencyMs))
                .build());
    }
}
