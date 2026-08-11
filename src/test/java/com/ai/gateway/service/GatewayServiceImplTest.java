package com.ai.gateway.service;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.cost.service.CostService;
import com.ai.gateway.dto.AIResponse;
import com.ai.gateway.dto.Usage;
import com.ai.gateway.entitlement.service.EntitlementService;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.firewall.service.PromptFireWallService;
import com.ai.gateway.metrics.GatewayMetricsService;
import com.ai.gateway.policy.service.PolicyEngineService;
import com.ai.gateway.provider.AIProvider;
import com.ai.gateway.provider.AIProviderFactory;
import com.ai.gateway.quota.service.QuotaService;
import com.ai.gateway.service.impl.GatewayServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class GatewayServiceImplTest {

    @Mock
    private PIIDetectionService piiDetectionService;

    @Mock
    private TokenVaultService tokenVaultService;

    @Mock
    private RestoreService restoreService;

    @Mock
    private AuditService auditService;

    @Mock
    private AIProviderFactory providerFactory;

    @Mock
    private PromptFireWallService firewallService;

    @Mock
    private PolicyEngineService policyEngineService;

    @Mock
    private GatewayMetricsService metricsService;

    @Mock
    private TokenUsageService tokenUsageService;

    @Mock
    private CostService costService;

    @Mock
    private QuotaService quotaService;

    @Mock
    private EntitlementService entitlementService;

    @Mock
    private AIProvider provider;

    @InjectMocks
    private GatewayServiceImpl gatewayService;

    private UUID tenantId;

    private AuthenticationContext authenticationContext;

    @BeforeEach
    void setUp() {

        tenantId = UUID.randomUUID();

        authenticationContext =
                AuthenticationContext.builder()
                        .tenantId(tenantId)
                        .tenantCode("TEST")
                        .tenantName("Test Tenant")
                        .defaultProvider(Provider.GEMINI)
                        .defaultModel("gemini-test")
                        .build();
    }

    @Test
    void shouldConsumeMonthlyTokenQuotaUsingTotalTokens()
            throws Exception {

        AIResponse response =
                AIResponse.builder()
                        .response("Hello")
                        .providerRequestId("provider-123")
                        .usage(
                                Usage.builder()
                                        .inputTokens(100)
                                        .outputTokens(50)
                                        .totalTokens(150)
                                        .latencyMs(200L)
                                        .reasoningTokens(0)
                                        .build())
                        .build();

        // Remaining provider/filter/security
        // mocks will be completed once the test
        // is aligned with your actual service
        // implementations.
    }
}
