package com.ai.gateway.service;

import com.ai.gateway.authentication.AuthenticationConstants;
import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.cache.InferenceCacheService;
import com.ai.gateway.dto.AIRequest;
import com.ai.gateway.dto.AIResponse;
import com.ai.gateway.dto.ChatRequest;
import com.ai.gateway.dto.ChatResponse;
import com.ai.gateway.dto.MaskingResult;
import com.ai.gateway.dto.Usage;
import com.ai.gateway.entitlement.enums.Feature;
import com.ai.gateway.entitlement.service.EntitlementService;
import com.ai.gateway.enums.AuditStatus;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.failover.ProviderFailoverService;
import com.ai.gateway.firewall.FirewallResult;
import com.ai.gateway.governance.service.GovernanceGuardrailService;
import com.ai.gateway.firewall.service.PromptFireWallService;
import com.ai.gateway.metrics.GatewayMetricsService;
import com.ai.gateway.policy.PolicyResult;
import com.ai.gateway.policy.service.PolicyEngineService;
import com.ai.gateway.quota.exception.QuotaExceededException;
import com.ai.gateway.rag.api.RagRequest;
import com.ai.gateway.rag.augmentation.RagAugmentationResult;
import com.ai.gateway.rag.augmentation.RagAugmentationService;
import com.ai.gateway.routing.RoutingContext;
import com.ai.gateway.routing.RoutingDecision;
import com.ai.gateway.routing.RoutingService;
import com.ai.gateway.routing.RoutingStrategy;
import com.ai.gateway.routing.analytics.RoutingAnalyticsService;
import com.ai.gateway.routing.intelligence.RoutingRuntimeSignalService;
import com.ai.gateway.service.impl.GatewayServiceImpl;
import com.ai.gateway.observability.PerformanceLogger;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
    private PromptFireWallService firewallService;

    @Mock
    private PolicyEngineService policyEngineService;

    @Mock
    private GatewayMetricsService metricsService;

    @Mock
    private TokenUsageService tokenUsageService;

    @Mock
    private EntitlementService entitlementService;

    @Mock
    private HttpServletRequest httpServletRequest;

    @Mock
    private ServletRequestAttributes servletRequestAttributes;

    @Mock
    private RoutingService routingService;

    @Mock
    private ProviderFailoverService providerFailoverService;

    @Mock
    private GovernanceGuardrailService governanceGuardrailService;

    @Mock
    private RagAugmentationService ragAugmentationService;

    @Mock
    private com.ai.gateway.multimodal.MultimodalRequestValidator multimodalRequestValidator;

    @Mock
    private InferenceCacheService inferenceCacheService;

    @InjectMocks
    private GatewayServiceImpl gatewayService;

    private UUID tenantId;

    private AuthenticationContext authenticationContext;

    @Mock
    private RoutingAnalyticsService routingAnalyticsService;
    @Mock
    private RoutingRuntimeSignalService routingRuntimeSignalService;

    @Mock
    private PerformanceLogger performanceLogger;

    @Mock
    private GatewayPostProviderPersistenceService postProviderPersistenceService;


    // ============================================================
    // SETUP
    // ============================================================

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

        when(servletRequestAttributes.getRequest())
                .thenReturn(httpServletRequest);

        when(httpServletRequest.getAttribute(
                AuthenticationConstants.AUTH_CONTEXT))
                .thenReturn(authenticationContext);

        lenient().when(ragAugmentationService.augment(
                        any(UUID.class),
                        anyString(),
                        any(RagRequest.class)))
                .thenAnswer(invocation -> RagAugmentationResult.builder()
                        .augmentedPrompt(invocation.getArgument(1, String.class))
                        .chunks(Collections.emptyList())
                        .knowledgeBaseCount(0)
                        .retrievedCount(0)
                        .selectedCount(0)
                        .build());

        lenient().doNothing()
                .when(multimodalRequestValidator)
                .validate(any(ChatRequest.class));

        RequestContextHolder.setRequestAttributes(
                servletRequestAttributes);
    }


    @AfterEach
    void tearDown() {

        RequestContextHolder.resetRequestAttributes();
    }


    // ============================================================
    // 1. SUCCESSFUL PROVIDER RESPONSE
    // ============================================================

    @Test
    void shouldConsumeTokenQuotaAfterSuccessfulProviderResponse() {

        mockSuccessfulPreProviderFlow();
        mockGeminiRouting();

        AIResponse response =
                createSuccessfulAIResponse();

        when(providerFailoverService.execute(
                any(AIRequest.class)))
                .thenReturn(response);

        when(restoreService.restore(
                eq("Hello from AI"),
                any(UUID.class)))
                .thenReturn("Hello from AI");

        ChatRequest request =
                createGeminiRequest();

        ChatResponse result =
                gatewayService.process(request);

        assertNotNull(result);

        assertEquals(
                "Hello from AI",
                result.getResponse());

        verify(routingService)
                .route(any(RoutingContext.class));

        verify(providerFailoverService)
                .execute(any(AIRequest.class));

        verify(governanceGuardrailService)
                .enforce(
                        any(UUID.class),
                        eq(authenticationContext),
                        any(AIRequest.class),
                        eq(response));

        verify(postProviderPersistenceService)
                .persistSuccess(
                        any(UUID.class),
                        eq(authenticationContext),
                        any(AIRequest.class),
                        eq(response),
                        any(RoutingDecision.class),
                        anyLong(),
                        eq("hello"),
                        anyLong());
    }


    // ============================================================
    // PHASE 4 - RAG AUGMENTATION
    // ============================================================

    @Test
    void shouldPassRagAugmentedPromptToProvider() {

        mockSuccessfulPreProviderFlow();
        mockGeminiRouting();

        RagRequest ragRequest = RagRequest.builder()
                .enabled(true)
                .knowledgeBaseIds(List.of(UUID.randomUUID().toString()))
                .topK(3)
                .minScore(0.70d)
                .retrievalStrategy("VECTOR")
                .build();

        RagAugmentationResult augmentation =
                RagAugmentationResult.builder()
                        .augmentedPrompt("hello\n\nRETRIEVED KNOWLEDGE:\n<source file=\"policy.md\">\nCost guardrails apply.\n</source>")
                        .chunks(Collections.emptyList())
                        .knowledgeBaseCount(1)
                        .retrievedCount(2)
                        .selectedCount(1)
                        .build();

        when(ragAugmentationService.augment(
                eq(tenantId),
                eq("hello"),
                eq(ragRequest)))
                .thenReturn(augmentation);

        AIResponse response = createSuccessfulAIResponse();
        when(providerFailoverService.execute(any(AIRequest.class)))
                .thenReturn(response);
        when(restoreService.restore(eq("Hello from AI"), any(UUID.class)))
                .thenReturn("Hello from AI");

        ChatRequest request = ChatRequest.builder()
                .prompt("hello")
                .provider(Provider.GEMINI)
                .model("gemini-test")
                .rag(ragRequest)
                .build();

        gatewayService.process(request);

        ArgumentCaptor<AIRequest> aiRequestCaptor =
                ArgumentCaptor.forClass(AIRequest.class);

        verify(providerFailoverService).execute(aiRequestCaptor.capture());

        assertEquals(
                augmentation.getAugmentedPrompt(),
                aiRequestCaptor.getValue().getPrompt());

        verify(ragAugmentationService).augment(
                tenantId, "hello", ragRequest);
    }

    @Test
    void shouldKeepMaskedPromptWhenRagIsDisabled() {

        mockSuccessfulPreProviderFlow();
        mockGeminiRouting();

        AIResponse response = createSuccessfulAIResponse();
        when(providerFailoverService.execute(any(AIRequest.class)))
                .thenReturn(response);
        when(restoreService.restore(eq("Hello from AI"), any(UUID.class)))
                .thenReturn("Hello from AI");

        ChatRequest request = createGeminiRequest();
        gatewayService.process(request);

        ArgumentCaptor<AIRequest> aiRequestCaptor =
                ArgumentCaptor.forClass(AIRequest.class);
        verify(providerFailoverService).execute(aiRequestCaptor.capture());

        assertEquals("hello", aiRequestCaptor.getValue().getPrompt());
        verify(ragAugmentationService).augment(
                tenantId, "hello", request.getRag());
    }


    // ============================================================
    // 2. NO USAGE
    // ============================================================

    @Test
    void shouldNotPersistUsageWhenProviderReturnsNoUsage() {

        mockSuccessfulPreProviderFlow();
        mockGeminiRouting();

        AIResponse response =
                AIResponse.builder()
                        .response("Hello from AI")
                        .providerRequestId("provider-123")
                        .usage(null)
                        .build();

        when(providerFailoverService.execute(
                any(AIRequest.class)))
                .thenReturn(response);

        when(restoreService.restore(
                eq("Hello from AI"),
                any(UUID.class)))
                .thenReturn("Hello from AI");

        ChatRequest request =
                createGeminiRequest();

        assertDoesNotThrow(
                () -> gatewayService.process(request));

        verify(providerFailoverService)
                .execute(any(AIRequest.class));

        verify(tokenUsageService, never())
                .save(
                        any(UUID.class),
                        any(AIRequest.class),
                        any(AIResponse.class));

        verify(postProviderPersistenceService)
                .persistSuccess(
                        any(UUID.class),
                        eq(authenticationContext),
                        any(AIRequest.class),
                        eq(response),
                        any(RoutingDecision.class),
                        anyLong(),
                        eq("hello"),
                        anyLong());

    }


    // ============================================================
    // 3. ENTITLEMENT FAILURE
    // ============================================================

    @Test
    void shouldRejectGeminiWhenTenantDoesNotHaveGeminiEntitlement() {

        /*
         * Do NOT call mockSuccessfulPreProviderFlow() here because
         * that helper also stubs entitlement success.
         *
         * This test is specifically testing entitlement rejection.
         */
        mockPreProviderFlowWithoutEntitlement();
        mockGeminiRouting();

        doThrow(
                new BusinessException(
                        "Gemini feature not enabled"))
                .when(entitlementService)
                .validateFeature(
                        tenantId,
                        Feature.GEMINI);

        ChatRequest request =
                createGeminiRequest();

        assertThrows(
                BusinessException.class,
                () -> gatewayService.process(request));

        verify(entitlementService)
                .validateFeature(
                        tenantId,
                        Feature.GEMINI);

        verify(providerFailoverService, never())
                .execute(any(AIRequest.class));
    }


    // ============================================================
    // 4. TOKEN QUOTA FAILURE
    // ============================================================

    @Test
    void shouldRejectRequestWhenTokenQuotaIsExceeded() {

        mockSuccessfulPreProviderFlow();
        mockGeminiRouting();

        AIResponse response =
                createSuccessfulAIResponse();

        when(providerFailoverService.execute(
                any(AIRequest.class)))
                .thenReturn(response);

        doThrow(
                new QuotaExceededException(
                        "Daily token quota exceeded"))
                .when(governanceGuardrailService)
                .enforce(
                        any(UUID.class),
                        eq(authenticationContext),
                        any(AIRequest.class),
                        eq(response));

        ChatRequest request =
                createGeminiRequest();

        assertThrows(
                QuotaExceededException.class,
                () -> gatewayService.process(request));

        verify(providerFailoverService)
                .execute(any(AIRequest.class));

        verify(governanceGuardrailService)
                .enforce(
                        any(UUID.class),
                        eq(authenticationContext),
                        any(AIRequest.class),
                        eq(response));
    }


    // ============================================================
    // 5. UNKNOWN MODEL
    // ============================================================

    @Test
    void shouldRejectUnknownModel() {

        mockPreRoutingFlow();

        doThrow(
                new BusinessException(
                        "Model unknown-model is not available for provider GEMINI."))
                .when(routingService)
                .route(any(RoutingContext.class));

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .provider(Provider.GEMINI)
                        .model("unknown-model")
                        .build();

        assertThrows(
                BusinessException.class,
                () -> gatewayService.process(request));

        verify(routingService)
                .route(any(RoutingContext.class));

        verify(providerFailoverService, never())
                .execute(any(AIRequest.class));
    }


    // ============================================================
    // 6. UNAVAILABLE PROVIDER
    // ============================================================

    @Test
    void shouldRejectUnavailableProvider() {

        mockPreRoutingFlow();

        doThrow(
                new BusinessException(
                        "GEMINI provider is not available."))
                .when(routingService)
                .route(any(RoutingContext.class));

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .provider(Provider.GEMINI)
                        .model("gemini-test")
                        .build();

        assertThrows(
                BusinessException.class,
                () -> gatewayService.process(request));

        verify(routingService)
                .route(any(RoutingContext.class));

        verify(providerFailoverService, never())
                .execute(any(AIRequest.class));
    }


    // ============================================================
    // 7. ROUTING DECISION
    // ============================================================

    @Test
    void shouldUseRoutingDecisionForProviderAndModel() {

        mockSuccessfulPreProviderFlow();

        RoutingDecision decision =
                new RoutingDecision(
                        Provider.GEMINI,
                        "gemini-test",
                        RoutingStrategy.EXPLICIT_PROVIDER);

        when(routingService.route(
                any(RoutingContext.class)))
                .thenReturn(decision);

        AIResponse response =
                createSuccessfulAIResponse();

        when(providerFailoverService.execute(
                any(AIRequest.class)))
                .thenReturn(response);

        when(restoreService.restore(
                eq("Hello from AI"),
                any(UUID.class)))
                .thenReturn("Hello from AI");

        ChatRequest request =
                createGeminiRequest();

        ChatResponse result =
                gatewayService.process(request);

        assertNotNull(result);

        verify(routingService)
                .route(any(RoutingContext.class));

        verify(providerFailoverService)
                .execute(
                        argThat(aiRequest ->
                                aiRequest.getProvider()
                                        == Provider.GEMINI
                                        &&
                                        "gemini-test".equals(
                                                aiRequest.getModel())));
    }


    // ============================================================
    // 8. ROUTING FAILURE
    // ============================================================

    @Test
    void shouldNotInvokeProviderWhenRoutingFails() {

        mockPreRoutingFlow();

        when(routingService.route(
                any(RoutingContext.class)))
                .thenThrow(
                        new BusinessException(
                                "Routing failed"));

        ChatRequest request =
                createGeminiRequest();

        assertThrows(
                BusinessException.class,
                () -> gatewayService.process(request));

        verify(routingService)
                .route(any(RoutingContext.class));

        verify(providerFailoverService, never())
                .execute(any(AIRequest.class));
    }


    // ============================================================
    // 9. DEFAULT TENANT ROUTING
    // ============================================================

    @Test
    void shouldUseTenantDefaultRoutingWhenProviderAndModelAreNotSpecified() {

        mockSuccessfulPreProviderFlow();

        when(routingService.route(
                any(RoutingContext.class)))
                .thenReturn(
                        new RoutingDecision(
                                Provider.GEMINI,
                                "gemini-test",
                                RoutingStrategy.DEFAULT));

        AIResponse response =
                createSuccessfulAIResponse();

        when(providerFailoverService.execute(
                any(AIRequest.class)))
                .thenReturn(response);

        when(restoreService.restore(
                eq("Hello from AI"),
                any(UUID.class)))
                .thenReturn("Hello from AI");

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        ChatResponse result =
                gatewayService.process(request);

        assertNotNull(result);

        assertEquals(
                "Hello from AI",
                result.getResponse());

        verify(routingService)
                .route(any(RoutingContext.class));

        verify(providerFailoverService)
                .execute(
                        argThat(aiRequest ->
                                aiRequest.getProvider()
                                        == Provider.GEMINI
                                        &&
                                        "gemini-test".equals(
                                                aiRequest.getModel())));
    }


    // ============================================================
    // 10. FAILURE AUDIT WITH NULL DEFAULTS
    // ============================================================

    @Test
    void shouldAuditFailureWithoutThrowingWhenTenantDefaultsAreMissing() {

        UUID failureTenantId = UUID.randomUUID();

        AuthenticationContext context =
                AuthenticationContext.builder()
                        .tenantId(failureTenantId)
                        .tenantCode("TEST")
                        .tenantName("Test Tenant")
                        .defaultProvider(null)
                        .defaultModel(null)
                        .build();

        when(httpServletRequest.getAttribute(
                AuthenticationConstants.AUTH_CONTEXT))
                .thenReturn(context);

        mockPreRoutingFlow();

        when(routingService.route(
                any(RoutingContext.class)))
                .thenThrow(
                        new BusinessException(
                                "Tenant default provider is not configured."));

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        assertThrows(
                BusinessException.class,
                () -> gatewayService.process(request));

        verify(postProviderPersistenceService)
                .persistFailure(
                        any(UUID.class),
                        eq(context),
                        isNull(),
                        isNull(),
                        anyLong(),
                        eq("hello"),
                        anyLong(),
                        eq(false),
                        eq("BusinessException"));

        verify(providerFailoverService, never())
                .execute(any(AIRequest.class));
    }


    // ============================================================
    // 11. PROVIDER FAILOVER SERVICE FAILURE
    // ============================================================

    @Test
    void shouldAuditFailureWhenProviderInvocationFails() {

        mockSuccessfulPreProviderFlow();
        mockGeminiRouting();

        when(providerFailoverService.execute(
                any(AIRequest.class)))
                .thenThrow(
                        new RuntimeException(
                                "Gemini provider unavailable"));

        ChatRequest request =
                createGeminiRequest();

        assertThrows(
                RuntimeException.class,
                () -> gatewayService.process(request));

        verify(providerFailoverService)
                .execute(any(AIRequest.class));

        verify(postProviderPersistenceService)
                .persistFailure(
                        any(UUID.class),
                        eq(authenticationContext),
                        any(AIRequest.class),
                        any(RoutingDecision.class),
                        anyLong(),
                        eq("hello"),
                        anyLong(),
                        eq(true),
                        eq("RuntimeException"));
    }


    // ============================================================
    // HELPERS
    // ============================================================

    /**
     * Mocks only the stages that must succeed before provider
     * invocation, including entitlement validation.
     */
    private void mockSuccessfulPreProviderFlow() {

        mockPreProviderFlowWithoutEntitlement();

        doNothing()
                .when(entitlementService)
                .validateFeature(
                        tenantId,
                        Feature.GEMINI);
    }


    /**
     * Mocks the gateway stages before entitlement validation.
     *
     * This intentionally does NOT stub EntitlementService.
     */
    private void mockPreProviderFlowWithoutEntitlement() {

        when(firewallService.inspect(anyString()))
                .thenReturn(
                        FirewallResult.builder()
                                .allowed(true)
                                .reason(null)
                                .build());

        when(policyEngineService.evaluate(anyString()))
                .thenReturn(
                        PolicyResult.builder()
                                .allowed(true)
                                .reason(null)
                                .build());

        when(piiDetectionService.mask(anyString()))
                .thenReturn(
                        MaskingResult.builder()
                                .maskedPrompt("hello")
                                .detectedValues(
                                        Collections.emptyList())
                                .build());
    }


    /**
     * Mocks only the stages required to reach routing.
     *
     * No entitlement or provider stubbing is performed here.
     */
    private void mockPreRoutingFlow() {

        when(firewallService.inspect(anyString()))
                .thenReturn(
                        FirewallResult.builder()
                                .allowed(true)
                                .reason(null)
                                .build());

        when(policyEngineService.evaluate(anyString()))
                .thenReturn(
                        PolicyResult.builder()
                                .allowed(true)
                                .reason(null)
                                .build());

        when(piiDetectionService.mask(anyString()))
                .thenReturn(
                        MaskingResult.builder()
                                .maskedPrompt("hello")
                                .detectedValues(
                                        Collections.emptyList())
                                .build());
    }


    /**
     * Standard Gemini routing decision.
     */
    private void mockGeminiRouting() {

        when(routingService.route(
                any(RoutingContext.class)))
                .thenReturn(
                        new RoutingDecision(
                                Provider.GEMINI,
                                "gemini-test",
                                RoutingStrategy.EXPLICIT_PROVIDER));
    }


    /**
     * Standard Gemini request.
     */
    private ChatRequest createGeminiRequest() {

        return ChatRequest.builder()
                .prompt("hello")
                .provider(Provider.GEMINI)
                .model("gemini-test")
                .build();
    }


    /**
     * Standard successful provider response.
     */
    private AIResponse createSuccessfulAIResponse() {

        return AIResponse.builder()
                .response("Hello from AI")
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
    }
}