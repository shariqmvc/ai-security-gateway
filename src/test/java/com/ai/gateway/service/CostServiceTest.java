package com.ai.gateway.service;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.budget.BudgetExceededException;
import com.ai.gateway.budget.service.BudgetService;
import com.ai.gateway.cost.dto.CostRequest;
import com.ai.gateway.cost.dto.CostResponse;
import com.ai.gateway.cost.entity.RequestCost;
import com.ai.gateway.cost.repository.RequestCostRepository;
import com.ai.gateway.cost.service.CostCalculator;
import com.ai.gateway.cost.service.CostService;
import com.ai.gateway.cost.service.impl.CostServiceImpl;
import com.ai.gateway.dto.AIRequest;
import com.ai.gateway.dto.AIResponse;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.tenant.TenantAccessGuard;
import com.ai.gateway.tenant.TenantSchemaRoutingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CostServiceTest {

    @Mock
    private CostCalculator costCalculator;

    @Mock
    private RequestCostRepository requestCostRepository;

    @Mock
    private BudgetService budgetService;

    @Mock
    private TenantSchemaRoutingService tenantSchemaRoutingService;

    @Mock
    private TenantAccessGuard tenantAccessGuard;

    @Mock
    private AuthenticationContext authenticationContext;

    @Mock
    private AIRequest aiRequest;

    @Mock
    private AIResponse response;

    private CostService costService;

    private UUID requestId;
    private UUID tenantId;

    @BeforeEach
    void setUp() {

        costService =
                new CostServiceImpl(
                        costCalculator,
                        requestCostRepository,
                        budgetService,
                        tenantSchemaRoutingService,
                        tenantAccessGuard);

        requestId = UUID.randomUUID();
        tenantId = UUID.randomUUID();

        // Keep common test setup free of stubs that are only needed by the
        // save() scenarios. Mockito strictness should catch genuinely
        // unnecessary stubbing in authorization-only tests.

    }

    @Test
    void shouldConsumeBudgetUsingCalculatedCost() {

        when(authenticationContext.getTenantId())
                .thenReturn(tenantId);
        when(aiRequest.getProvider())
                .thenReturn(Provider.OPENAI);
        when(aiRequest.getModel())
                .thenReturn("gpt-test");

        when(response.getUsage())
                .thenReturn(
                        com.ai.gateway.dto.Usage.builder()
                                .inputTokens(100)
                                .outputTokens(200)
                                .build());

        when(costCalculator.calculate(any(CostRequest.class)))
                .thenReturn(
                        CostResponse.builder()
                                .inputCost(new BigDecimal("0.05"))
                                .outputCost(new BigDecimal("0.10"))
                                .totalCost(new BigDecimal("0.15"))
                                .build());

        costService.save(
                requestId,
                authenticationContext,
                aiRequest,
                response);

        verify(budgetService)
                .consume(
                        tenantId,
                        new BigDecimal("0.15"));

        verify(tenantSchemaRoutingService)
                .useTenantSchema();

        verify(requestCostRepository)
                .save(any(RequestCost.class));
    }

    @Test
    void shouldNotPersistCostWhenBudgetExceeded() {

        when(authenticationContext.getTenantId())
                .thenReturn(tenantId);
        when(aiRequest.getProvider())
                .thenReturn(Provider.OPENAI);
        when(aiRequest.getModel())
                .thenReturn("gpt-test");

        when(response.getUsage())
                .thenReturn(
                        com.ai.gateway.dto.Usage.builder()
                                .inputTokens(100)
                                .outputTokens(200)
                                .build());

        when(costCalculator.calculate(any(CostRequest.class)))
                .thenReturn(
                        CostResponse.builder()
                                .inputCost(new BigDecimal("0.05"))
                                .outputCost(new BigDecimal("0.10"))
                                .totalCost(new BigDecimal("0.15"))
                                .build());

        doThrow(
                new BudgetExceededException(
                        "Monthly budget exceeded."))
                .when(budgetService)
                .consume(
                        tenantId,
                        new BigDecimal("0.15"));

        assertThrows(
                BudgetExceededException.class,
                () ->
                        costService.save(
                                requestId,
                                authenticationContext,
                                aiRequest,
                                response));

        verify(requestCostRepository, never())
                .save(any(RequestCost.class));
    }

    @Test
    void shouldAuthorizeRequestedTenantBeforeRoutingTenantSchema() {

        when(requestCostRepository.getTenantSummary(tenantId))
                .thenReturn(null);

        costService.getTenantSummary(tenantId);

        verify(tenantAccessGuard)
                .requireAccess(tenantId);
        verify(tenantSchemaRoutingService)
                .useTenantSchema();
        verify(requestCostRepository)
                .getTenantSummary(tenantId);
    }

    @Test
    void shouldNotRouteOrQueryWhenTenantAuthorizationFails() {

        doThrow(new RuntimeException("denied"))
                .when(tenantAccessGuard)
                .requireAccess(tenantId);

        assertThrows(
                RuntimeException.class,
                () -> costService.getTenantSummary(tenantId));

        verify(tenantSchemaRoutingService, never())
                .useTenantSchema();
        verify(requestCostRepository, never())
                .getTenantSummary(any(UUID.class));
    }
}
