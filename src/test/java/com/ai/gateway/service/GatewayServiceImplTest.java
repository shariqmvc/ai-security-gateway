package com.ai.gateway.service;

import com.ai.gateway.authentication.AuthenticationConstants;
import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.budget.service.BudgetService;
import com.ai.gateway.cost.service.CostService;
import com.ai.gateway.dto.*;
import com.ai.gateway.entitlement.enums.Feature;
import com.ai.gateway.entitlement.service.EntitlementService;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.firewall.FirewallResult;
import com.ai.gateway.firewall.service.PromptFireWallService;
import com.ai.gateway.metrics.GatewayMetricsService;
import com.ai.gateway.policy.PolicyResult;
import com.ai.gateway.policy.service.PolicyEngineService;
import com.ai.gateway.provider.AIProvider;
import com.ai.gateway.provider.AIProviderFactory;
import com.ai.gateway.quota.exception.QuotaExceededException;
import com.ai.gateway.quota.service.QuotaService;
import com.ai.gateway.service.impl.GatewayServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
    private BudgetService budgetService;

    @Mock
    private EntitlementService entitlementService;

    @Mock
    private AIProvider provider;

    @Mock
    private HttpServletRequest httpServletRequest;

    @Mock
    private ServletRequestAttributes servletRequestAttributes;

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

        when(servletRequestAttributes.getRequest())
                .thenReturn(httpServletRequest);

        when(httpServletRequest.getAttribute(
                AuthenticationConstants.AUTH_CONTEXT))
                .thenReturn(authenticationContext);

        RequestContextHolder.setRequestAttributes(
                servletRequestAttributes);
    }

    @AfterEach
    void tearDown() {

        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldConsumeTokenQuotaAfterSuccessfulProviderResponse() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .provider(Provider.GEMINI)
                        .model("gemini-test")
                        .build();

        MaskingResult maskingResult =
                MaskingResult.builder()
                        .maskedPrompt("hello")
                        .detectedValues(
                                Collections.emptyList())
                        .build();

        FirewallResult firewallResult =
                FirewallResult.builder()
                        .allowed(true)
                        .reason(null)
                        .build();

        PolicyResult policyResult =
                PolicyResult.builder()
                        .allowed(true)
                        .reason(null)
                        .build();

        AIResponse response =
                AIResponse.builder()
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

        when(firewallService.inspect("hello"))
                .thenReturn(firewallResult);

        when(policyEngineService.evaluate("hello"))
                .thenReturn(policyResult);

        when(piiDetectionService.mask("hello"))
                .thenReturn(maskingResult);

        when(providerFactory.getProvider(
                Provider.GEMINI))
                .thenReturn(provider);


        when(provider.chat(any(AIRequest.class)))
                .thenReturn(response);

        when(costService.save(
                any(UUID.class),
                eq(authenticationContext),
                any(AIRequest.class),
                eq(response)))
                .thenReturn(
                        new BigDecimal("0.15"));

        when(restoreService.restore(
                eq("Hello from AI"),
                any(UUID.class)))
                .thenReturn("Hello from AI");

        doNothing()
                .when(entitlementService)
                .validateFeature(
                        tenantId,
                        Feature.GEMINI);

        var result =
                gatewayService.process(request);

        assertEquals(
                "Hello from AI",
                result.getResponse());

        verify(quotaService)
                .consumeTokens(
                        eq(tenantId),
                        eq(150L));

        verify(costService)
                .save(
                        any(UUID.class),
                        eq(authenticationContext),
                        any(AIRequest.class),
                        eq(response));

        verify(tokenUsageService)
                .save(
                        any(UUID.class),
                        any(AIRequest.class),
                        eq(response));
    }

    @Test
    void shouldNotPersistUsageWhenProviderReturnsNoUsage() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .provider(Provider.GEMINI)
                        .model("gemini-test")
                        .build();

        MaskingResult maskingResult =
                MaskingResult.builder()
                        .maskedPrompt("hello")
                        .detectedValues(
                                Collections.emptyList())
                        .build();

        FirewallResult firewallResult =
                FirewallResult.builder()
                        .allowed(true)
                        .reason(null)
                        .build();

        PolicyResult policyResult =
                PolicyResult.builder()
                        .allowed(true)
                        .reason(null)
                        .build();

        AIResponse response =
                AIResponse.builder()
                        .response("Hello from AI")
                        .providerRequestId("provider-123")
                        .usage(null)
                        .build();

        when(firewallService.inspect("hello"))
                .thenReturn(firewallResult);

        when(policyEngineService.evaluate("hello"))
                .thenReturn(policyResult);

        when(piiDetectionService.mask("hello"))
                .thenReturn(maskingResult);

        when(providerFactory.getProvider(
                Provider.GEMINI))
                .thenReturn(provider);

        when(provider.chat(any(AIRequest.class)))
                .thenReturn(response);

        doNothing()
                .when(entitlementService)
                .validateFeature(
                        tenantId,
                        Feature.GEMINI);

        assertDoesNotThrow(
                () -> gatewayService.process(request));

        verify(quotaService, never())
                .consumeTokens(
                        any(UUID.class),
                        anyLong());

        verify(tokenUsageService, never())
                .save(
                        any(UUID.class),
                        any(AIRequest.class),
                        any(AIResponse.class));

        verify(costService, never())
                .save(
                        any(UUID.class),
                        any(AuthenticationContext.class),
                        any(AIRequest.class),
                        any(AIResponse.class));

        verify(budgetService, never())
                .consume(
                        any(UUID.class),
                        any(BigDecimal.class));
    }

    @Test
    void shouldRejectGeminiWhenTenantDoesNotHaveGeminiEntitlement()
            throws Exception {

        when(firewallService.inspect(anyString()))
                .thenReturn(
                        FirewallResult.builder()
                                .allowed(true)
                                .build());

        when(policyEngineService.evaluate(anyString()))
                .thenReturn(
                        PolicyResult.builder()
                                .allowed(true)
                                .build());

        when(piiDetectionService.mask(anyString()))
                .thenReturn(
                        MaskingResult.builder()
                                .maskedPrompt("hello")
                                .detectedValues(List.of())
                                .build());

        when(providerFactory.getProvider(
                Provider.GEMINI))
                .thenReturn(provider);

        doThrow(new BusinessException(
                "Gemini feature not enabled"))
                .when(entitlementService)
                .validateFeature(
                        tenantId,
                        Feature.GEMINI);

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .provider(Provider.GEMINI)
                        .model("gemini-test")
                        .build();

        assertThrows(
                BusinessException.class,
                () -> gatewayService.process(request));

        verify(entitlementService)
                .validateFeature(
                        tenantId,
                        Feature.GEMINI);

        verify(provider, never())
                .chat(any(AIRequest.class));
    }

    @Test
    void shouldRejectRequestWhenTokenQuotaIsExceeded() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .provider(Provider.GEMINI)
                        .model("gemini-test")
                        .build();

        MaskingResult maskingResult =
                MaskingResult.builder()
                        .maskedPrompt("hello")
                        .detectedValues(
                                Collections.emptyList())
                        .build();

        FirewallResult firewallResult =
                FirewallResult.builder()
                        .allowed(true)
                        .reason(null)
                        .build();

        PolicyResult policyResult =
                PolicyResult.builder()
                        .allowed(true)
                        .reason(null)
                        .build();

        AIResponse response =
                AIResponse.builder()
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

        when(firewallService.inspect("hello"))
                .thenReturn(firewallResult);

        when(policyEngineService.evaluate("hello"))
                .thenReturn(policyResult);

        when(piiDetectionService.mask("hello"))
                .thenReturn(maskingResult);

        when(providerFactory.getProvider(
                Provider.GEMINI))
                .thenReturn(provider);

        when(provider.chat(any(AIRequest.class)))
                .thenReturn(response);

        doNothing()
                .when(entitlementService)
                .validateFeature(
                        tenantId,
                        Feature.GEMINI);

        doThrow(new QuotaExceededException(
                "Daily token quota exceeded"))
                .when(quotaService)
                .consumeTokens(
                        tenantId,
                        150L);

        assertThrows(
                QuotaExceededException.class,
                () ->
                        gatewayService.process(request));

        verify(entitlementService)
                .validateFeature(
                        tenantId,
                        Feature.GEMINI);

        verify(provider)
                .chat(any(AIRequest.class));

        verify(quotaService)
                .consumeTokens(
                        tenantId,
                        150L);

        verify(costService, never())
                .save(
                        any(UUID.class),
                        eq(authenticationContext),
                        any(AIRequest.class),
                        eq(response));
    }
}