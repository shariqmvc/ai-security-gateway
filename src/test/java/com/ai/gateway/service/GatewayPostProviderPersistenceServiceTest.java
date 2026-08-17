package com.ai.gateway.service;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.cost.service.CostService;
import com.ai.gateway.dto.AIRequest;
import com.ai.gateway.dto.AIResponse;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.metrics.GatewayMetricsService;
import com.ai.gateway.routing.RoutingDecision;
import com.ai.gateway.routing.RoutingStrategy;
import com.ai.gateway.routing.health.RoutingOutcomeService;
import com.ai.gateway.routing.intelligence.RoutingRuntimeSignalService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GatewayPostProviderPersistenceServiceTest {

    @Mock private RoutingRuntimeSignalService routingRuntimeSignalService;
    @Mock private RoutingOutcomeService routingOutcomeService;
    @Mock private TokenUsageService tokenUsageService;
    @Mock private CostService costService;
    @Mock private AuditService auditService;
    @Mock private GatewayMetricsService metricsService;

    @InjectMocks
    private GatewayPostProviderPersistenceService service;

    @Test
    void persistsSuccessOutsideCriticalPathContract() {
        UUID requestId = UUID.randomUUID();
        AuthenticationContext auth = AuthenticationContext.builder()
                .tenantId(UUID.randomUUID())
                .tenantCode("TEST")
                .build();
        AIRequest request = AIRequest.builder()
                .provider(Provider.GEMINI)
                .model("gemini-test")
                .prompt("hello")
                .build();
        AIResponse response = AIResponse.builder()
                .provider(Provider.GEMINI)
                .model("gemini-test")
                .response("hello")
                .build();
        RoutingDecision decision = new RoutingDecision(
                Provider.GEMINI, "gemini-test", RoutingStrategy.EXPLICIT_PROVIDER);

        service.persistSuccess(
                requestId, auth, request, response, decision, 100L, "hello", 120L);

        verify(routingRuntimeSignalService).recordSuccess(any(), eq(100L));
        verify(routingOutcomeService).recordSuccess(
                eq(requestId), eq(auth), eq(request), eq(decision), eq(100L));
        verify(auditService).save(
                eq(requestId), eq("hello"), eq("hello"), eq(120L),
                eq("gemini-test"), eq("GEMINI"), any());
        verify(metricsService).addLatency(120L);
    }

    @Test
    void persistsFailureAuditAndProviderHealth() {
        UUID requestId = UUID.randomUUID();
        AuthenticationContext auth = AuthenticationContext.builder()
                .tenantId(UUID.randomUUID())
                .tenantCode("TEST")
                .build();
        AIRequest request = AIRequest.builder()
                .provider(Provider.GEMINI)
                .model("gemini-test")
                .prompt("hello")
                .build();
        RoutingDecision decision = new RoutingDecision(
                Provider.GEMINI, "gemini-test", RoutingStrategy.EXPLICIT_PROVIDER);

        service.persistFailure(
                requestId, auth, request, decision, 500L, "hello", 510L, true, "TIMEOUT");

        verify(routingRuntimeSignalService).recordFailure(any(), eq("TIMEOUT"));
        verify(routingOutcomeService).recordFailure(
                eq(requestId), eq(auth), eq(request), eq(decision), eq(500L), any());
        verify(auditService).save(
                eq(requestId), eq("hello"), isNull(), eq(510L),
                eq("gemini-test"), eq("GEMINI"), any());
    }
}
