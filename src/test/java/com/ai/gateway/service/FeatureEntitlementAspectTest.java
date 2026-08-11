package com.ai.gateway.service;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.entitlement.annotation.RequiresFeature;
import com.ai.gateway.entitlement.aop.FeatureEntitlementAspect;
import com.ai.gateway.entitlement.enums.Feature;
import com.ai.gateway.entitlement.security.AuthenticationContextResolver;
import com.ai.gateway.entitlement.service.EntitlementService;
import com.ai.gateway.exception.BusinessException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeatureEntitlementAspectTest {

    @Mock
    private EntitlementService entitlementService;

    @Mock
    private AuthenticationContextResolver contextResolver;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private RequiresFeature requiresFeature;

    @Mock
    private AuthenticationContext context;

    @InjectMocks
    private FeatureEntitlementAspect aspect;

    @Test
    void shouldProceedWhenFeatureIsEnabled()
            throws Throwable {

        UUID tenantId = UUID.randomUUID();

        when(contextResolver.resolve())
                .thenReturn(context);

        when(context.getTenantId())
                .thenReturn(tenantId);

        when(requiresFeature.value())
                .thenReturn(Feature.CHAT);

        when(joinPoint.proceed())
                .thenReturn("success");

        Object result =
                aspect.checkFeature(
                        joinPoint,
                        requiresFeature);

        verify(entitlementService)
                .validateFeature(
                        tenantId,
                        Feature.CHAT);

        verify(joinPoint)
                .proceed();

        // Since validateFeature() doesn't throw,
        // execution is allowed.
        org.junit.jupiter.api.Assertions.assertEquals(
                "success",
                result);
    }

    @Test
    void shouldNotProceedWhenFeatureIsDisabled()
            throws Throwable {

        UUID tenantId = UUID.randomUUID();

        when(contextResolver.resolve())
                .thenReturn(context);

        when(context.getTenantId())
                .thenReturn(tenantId);

        when(requiresFeature.value())
                .thenReturn(Feature.CHAT);

        doThrow(
                new BusinessException(
                        "CHAT is disabled for this tenant."))
                .when(entitlementService)
                .validateFeature(
                        tenantId,
                        Feature.CHAT);

        assertThrows(
                BusinessException.class,
                () -> aspect.checkFeature(
                        joinPoint,
                        requiresFeature));

        verify(joinPoint, never())
                .proceed();
    }
}
