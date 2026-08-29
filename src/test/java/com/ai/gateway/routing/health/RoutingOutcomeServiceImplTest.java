package com.ai.gateway.routing.health;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.business.routing.health.RoutingOutcomeServiceImpl;
import com.ai.gateway.business.routing.health.entity.RoutingOutcome;
import com.ai.gateway.business.routing.health.repository.RoutingOutcomeRepository;
import com.ai.gateway.core.contract.AIRequest;
import com.ai.gateway.core.model.Provider;
import com.ai.gateway.core.routing.RoutingDecision;
import com.ai.gateway.core.routing.RoutingDecisionMetadata;
import com.ai.gateway.core.routing.RoutingStrategy;
import com.ai.gateway.core.routing.intelligence.RoutingDecisionExplanation;
import com.ai.gateway.security.AuthorizationService;
import com.ai.gateway.security.SecurityRole;
import com.ai.gateway.tenant.TenantAccessGuard;
import com.ai.gateway.tenant.TenantContext;
import com.ai.gateway.tenant.TenantSchemaRoutingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ActiveProfiles("test")
class RoutingOutcomeServiceImplTest {

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void persistsSuccessOutcomeWithDecisionMetadata() {

        RoutingOutcomeRepository repository =
                mock(RoutingOutcomeRepository.class);

        TenantSchemaRoutingService schemaRoutingService =
                mock(TenantSchemaRoutingService.class);

        TenantAccessGuard accessGuard =
                new TenantAccessGuard(
                        new AuthorizationService());

        RoutingOutcomeServiceImpl service =
                new RoutingOutcomeServiceImpl(
                        repository,
                        schemaRoutingService,
                        accessGuard);

        UUID requestId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        TenantContext.set(tenantId);

        AuthenticationContext auth =
                AuthenticationContext.builder()
                        .tenantId(tenantId)
                        .schemaName("tenant_test")
                        .role(SecurityRole.TENANT_USER)
                        .build();

        authenticate(auth);

        AIRequest request =
                AIRequest.builder()
                        .provider(Provider.OPENAI)
                        .model("gpt-5")
                        .prompt("test")
                        .build();

        RoutingDecisionMetadata metadata =
                new RoutingDecisionMetadata(
                        0.91,
                        1,
                        2,
                        "HIGHEST_SCORE",
                        false,
                        "standard",
                        List.of(
                                new RoutingDecisionMetadata.RoutingCandidateMetadata(
                                        "OPENAI",
                                        "gpt-5",
                                        0.91,
                                        1)),
                        new RoutingDecisionExplanation(
                                "HIGHEST_SCORE",
                                List.of("runtime-health"),
                                List.of(),
                                List.of()));

        RoutingDecision decision =
                new RoutingDecision(
                        Provider.OPENAI,
                        "gpt-5",
                        RoutingStrategy.POLICY_BASED,
                        metadata);

        service.recordSuccess(
                requestId,
                auth,
                request,
                decision,
                320);

        var captor =
                forClass(RoutingOutcome.class);

        verify(schemaRoutingService)
                .useTenantSchema();

        verify(repository)
                .save(captor.capture());

        RoutingOutcome outcome =
                captor.getValue();

        assertTrue(outcome.isSuccess());
        assertEquals(0.91, outcome.getSelectedScore());
        assertEquals(1, outcome.getSelectedRank());
        assertEquals(2, outcome.getCandidateCount());
        assertEquals(320L, outcome.getLatencyMs());
    }

    private void authenticate(AuthenticationContext context) {

        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                context,
                                null,
                                List.of(
                                        new SimpleGrantedAuthority(
                                                "ROLE_TENANT_USER"))));
    }
}