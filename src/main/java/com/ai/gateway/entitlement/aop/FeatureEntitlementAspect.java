package com.ai.gateway.entitlement.aop;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.entitlement.annotation.RequiresFeature;
import com.ai.gateway.entitlement.security.AuthenticationContextResolver;
import com.ai.gateway.entitlement.service.EntitlementService;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.core.metrics.GatewayMetricsService;
import com.ai.gateway.core.metrics.MetricsConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class FeatureEntitlementAspect {

    private final EntitlementService entitlementService;

    private final AuthenticationContextResolver contextResolver;

    @Around("@annotation(requiresFeature)")
    public Object checkFeature(
            ProceedingJoinPoint joinPoint,
            RequiresFeature requiresFeature)
            throws Throwable {

        AuthenticationContext context =
                contextResolver.resolve();

        entitlementService.validateFeature(
                context.getTenantId(),
                requiresFeature.value());

        return joinPoint.proceed();
    }
}