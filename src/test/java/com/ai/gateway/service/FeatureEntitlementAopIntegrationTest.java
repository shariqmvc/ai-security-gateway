package com.ai.gateway.service;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.entitlement.annotation.RequiresFeature;
import com.ai.gateway.entitlement.aop.FeatureEntitlementAspect;
import com.ai.gateway.entitlement.enums.Feature;
import com.ai.gateway.entitlement.security.AuthenticationContextResolver;
import com.ai.gateway.entitlement.service.EntitlementService;
import com.ai.gateway.core.model.Provider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Service;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@SpringJUnitConfig
@Import({
        FeatureEntitlementAspect.class,
        AuthenticationContextResolver.class,
        FeatureEntitlementAopIntegrationTest.Config.class
})
class FeatureEntitlementAopIntegrationTest {

    @Autowired
    private TestProtectedService protectedService;

    @MockitoBean
    private EntitlementService entitlementService;

    @MockitoBean
    private AuthenticationContextResolver contextResolver;

    @Test
    void shouldInterceptAnnotatedMethod()
            throws Exception {

        UUID tenantId = UUID.randomUUID();

        AuthenticationContext context =
                AuthenticationContext.builder()
                        .tenantId(tenantId)
                        .tenantCode("TEST")
                        .tenantName("Test Tenant")
                        .tenantType(null)
                        .defaultProvider(Provider.GEMINI)
                        .defaultModel("gemini-test")
                        .build();

        when(contextResolver.resolve())
                .thenReturn(context);

        String result =
                protectedService.execute();

        assertEquals(
                "executed",
                result);
    }

    @Test
    void shouldBlockAnnotatedMethodWhenFeatureDisabled()
            throws Exception {

        UUID tenantId = UUID.randomUUID();

        AuthenticationContext context =
                AuthenticationContext.builder()
                        .tenantId(tenantId)
                        .tenantCode("TEST")
                        .tenantName("Test Tenant")
                        .tenantType(null)
                        .defaultProvider(Provider.GEMINI)
                        .defaultModel("gemini-test")
                        .build();

        when(contextResolver.resolve())
                .thenReturn(context);

        doThrow(
                new RuntimeException(
                        "CHAT is disabled for this tenant."))
                .when(entitlementService)
                .validateFeature(
                        tenantId,
                        Feature.CHAT);

        assertThrows(
                RuntimeException.class,
                () -> protectedService.execute());
    }

    @TestConfiguration
    @EnableAspectJAutoProxy
    static class Config {

        @Bean
        TestProtectedService testProtectedService() {
            return new TestProtectedService();
        }
    }

    @Service
    static class TestProtectedService {

        @RequiresFeature(Feature.CHAT)
        public String execute() {
            return "executed";
        }
    }
}
