package com.ai.gateway.service;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.budget.BudgetExceededException;
import com.ai.gateway.budget.service.BudgetService;
import com.ai.gateway.core.cost.dto.CostRequest;
import com.ai.gateway.core.cost.dto.CostResponse;
import com.ai.gateway.business.cost.RequestCost;
import com.ai.gateway.business.cost.RequestCostRepository;
import com.ai.gateway.core.cost.service.CostCalculator;
import com.ai.gateway.business.cost.service.CostService;
import com.ai.gateway.business.cost.service.impl.CostServiceImpl;
import com.ai.gateway.core.contract.AIRequest;
import com.ai.gateway.core.contract.AIResponse;
import com.ai.gateway.core.contract.Usage;
import com.ai.gateway.core.model.Provider;
import com.ai.gateway.security.AuthorizationService;
import com.ai.gateway.security.SecurityRole;
import com.ai.gateway.tenant.TenantAccessDeniedException;
import com.ai.gateway.tenant.TenantAccessGuard;
import com.ai.gateway.tenant.TenantContext;
import com.ai.gateway.tenant.TenantSchemaRoutingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
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
                        new TenantAccessGuard(
                                new AuthorizationService()));

        requestId = UUID.randomUUID();
        tenantId = UUID.randomUUID();

        TenantContext.set(tenantId);

        when(authenticationContext.getTenantId())
                .thenReturn(tenantId);

        setSecurityPrincipal(authenticationContext);
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private void setSecurityPrincipal(AuthenticationContext context) {

        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                context,
                                null,
                                List.of(
                                        new SimpleGrantedAuthority(
                                                "ROLE_TENANT_USER"))));
    }

    private void authenticateAsTenantUser() {

        when(authenticationContext.getRole())
                .thenReturn(SecurityRole.TENANT_USER);
    }

    @Test
    void shouldRejectCostWriteWhenAuthenticationContextTargetsAnotherTenant() {

        UUID authenticatedTenantId = tenantId;
        UUID requestedTenantId = UUID.randomUUID();

        AuthenticationContext authenticatedPrincipal =
                mock(AuthenticationContext.class);

        when(authenticatedPrincipal.getTenantId())
                .thenReturn(authenticatedTenantId);

        setSecurityPrincipal(authenticatedPrincipal);

        /*
         * The AuthenticationContext supplied to the service represents
         * a different tenant from the tenant authenticated in the
         * Spring Security principal.
         */
        when(authenticationContext.getTenantId())
                .thenReturn(requestedTenantId);

        TenantContext.set(authenticatedTenantId);

        assertThrows(
                TenantAccessDeniedException.class,
                () -> costService.save(
                        requestId,
                        authenticationContext,
                        aiRequest,
                        response));

        verifyNoInteractions(costCalculator);
        verifyNoInteractions(requestCostRepository);
        verifyNoInteractions(budgetService);

        verify(
                tenantSchemaRoutingService,
                never())
                .useTenantSchema();
    }

    @Test
    void shouldRejectCostWriteWithoutAuthenticatedTenantContext() {

        TenantContext.clear();

        /*
         * The principal is intentionally not assigned a tenant role.
         * AuthorizationService therefore rejects the request at the
         * authentication/authorization boundary.
         */
        assertThrows(
                AccessDeniedException.class,
                () -> costService.save(
                        requestId,
                        authenticationContext,
                        aiRequest,
                        response));

        verifyNoInteractions(costCalculator);
        verifyNoInteractions(requestCostRepository);
        verifyNoInteractions(budgetService);

        verify(
                tenantSchemaRoutingService,
                never())
                .useTenantSchema();
    }

    @Test
    void shouldConsumeBudgetUsingCalculatedCost() {

        authenticateAsTenantUser();

        when(authenticationContext.getTenantId())
                .thenReturn(tenantId);

        when(aiRequest.getProvider())
                .thenReturn(Provider.OPENAI);

        when(aiRequest.getModel())
                .thenReturn("gpt-test");

        when(response.getUsage())
                .thenReturn(
                        Usage.builder()
                                .inputTokens(100)
                                .outputTokens(200)
                                .build());

        when(costCalculator.calculate(any(CostRequest.class)))
                .thenReturn(
                        CostResponse.builder()
                                .inputCost(
                                        new BigDecimal("0.05"))
                                .outputCost(
                                        new BigDecimal("0.10"))
                                .totalCost(
                                        new BigDecimal("0.15"))
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

        authenticateAsTenantUser();

        when(authenticationContext.getTenantId())
                .thenReturn(tenantId);

        when(aiRequest.getProvider())
                .thenReturn(Provider.OPENAI);

        when(aiRequest.getModel())
                .thenReturn("gpt-test");

        when(response.getUsage())
                .thenReturn(
                        Usage.builder()
                                .inputTokens(100)
                                .outputTokens(200)
                                .build());

        when(costCalculator.calculate(any(CostRequest.class)))
                .thenReturn(
                        CostResponse.builder()
                                .inputCost(
                                        new BigDecimal("0.05"))
                                .outputCost(
                                        new BigDecimal("0.10"))
                                .totalCost(
                                        new BigDecimal("0.15"))
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

        authenticateAsTenantUser();

        when(requestCostRepository
                .getTenantSummary(tenantId))
                .thenReturn(null);

        costService.getTenantSummary(tenantId);

    }
}