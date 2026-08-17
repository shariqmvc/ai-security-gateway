package com.ai.gateway.routing.health;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.dto.AIRequest;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.routing.RoutingDecision;
import com.ai.gateway.routing.RoutingStrategy;
import com.ai.gateway.routing.RoutingDecisionMetadata;
import com.ai.gateway.routing.health.entity.RoutingOutcome;
import com.ai.gateway.routing.health.repository.RoutingOutcomeRepository;
import com.ai.gateway.tenant.TenantContext;
import com.ai.gateway.tenant.TenantAccessGuard;
import com.ai.gateway.security.AuthorizationService;
import com.ai.gateway.tenant.TenantSchemaRoutingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
@ActiveProfiles("test")
class RoutingOutcomeServiceImplTest {

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void persistsSuccessOutcomeWithDecisionMetadata() {
        RoutingOutcomeRepository repository = mock(RoutingOutcomeRepository.class);
        TenantSchemaRoutingService schemaRoutingService =
                mock(TenantSchemaRoutingService.class);
        TenantAccessGuard accessGuard = new TenantAccessGuard(new AuthorizationService());
        RoutingOutcomeServiceImpl service =
                new RoutingOutcomeServiceImpl(
                        repository,
                        schemaRoutingService,
                        accessGuard);

        UUID requestId = UUID.randomUUID();
        AIRequest request = AIRequest.builder()
                .provider(Provider.OPENAI)
                .model("gpt-5")
                .prompt("test")
                .build();

        RoutingDecisionMetadata metadata =
                new RoutingDecisionMetadata(
                        0.91, 1, 2, "HIGHEST_SCORE",
                        false, "standard",
                        java.util.List.of(
                                new RoutingDecisionMetadata.RoutingCandidateMetadata(
                                        "OPENAI", "gpt-5", 0.91, 1)),
                        new com.ai.gateway.routing.intelligence.RoutingDecisionExplanation(
                                "HIGHEST_SCORE",
                                java.util.List.of("runtime-health"),
                                java.util.List.of(),
                                java.util.List.of()));

        UUID tenantId = UUID.randomUUID();
        TenantContext.set(tenantId);

        AuthenticationContext auth =
                AuthenticationContext.builder()
                        .tenantId(tenantId)
                        .schemaName("tenant_test")
                        .build();

        service.recordSuccess(
                requestId, auth, request,
                new RoutingDecision(Provider.OPENAI, "gpt-5",
                        RoutingStrategy.POLICY_BASED, metadata),
                320);

        var captor = org.mockito.ArgumentCaptor.forClass(RoutingOutcome.class);
        verify(schemaRoutingService)
                .useTenantSchema();

        verify(repository).save(captor.capture());

        RoutingOutcome outcome = captor.getValue();
        assertTrue(outcome.isSuccess());
        assertEquals(0.91, outcome.getSelectedScore());
        assertEquals(1, outcome.getSelectedRank());
        assertEquals(2, outcome.getCandidateCount());
        assertEquals(320L, outcome.getLatencyMs());
    }
}
