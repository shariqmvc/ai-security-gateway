package com.ai.gateway.service;


import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.dto.ChatRequest;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.routing.policy.RoutingPolicy;
import com.ai.gateway.routing.policy.RoutingPolicyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoutingPolicyServiceImplTest {

    private RoutingPolicyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RoutingPolicyServiceImpl();
    }

    @Test
    void shouldResolvePolicyFromTenantDefaults() {

        AuthenticationContext authenticationContext =
                AuthenticationContext.builder()
                        .defaultProvider(Provider.GEMINI)
                        .defaultModel("gemini-test")
                        .build();

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingPolicy policy =
                service.resolve(
                        request,
                        authenticationContext);

        assertNotNull(policy);

        assertTrue(policy.enabled());

        assertEquals(
                Provider.GEMINI,
                policy.preferredProvider());

        assertEquals(
                "gemini-test",
                policy.preferredModel());
    }

    @Test
    void shouldResolvePolicyWithoutTenantDefaults() {

        AuthenticationContext authenticationContext =
                AuthenticationContext.builder()
                        .defaultProvider(null)
                        .defaultModel(null)
                        .build();

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingPolicy policy =
                service.resolve(
                        request,
                        authenticationContext);

        assertNotNull(policy);

        assertTrue(policy.enabled());

        assertNull(policy.preferredProvider());
        assertNull(policy.preferredModel());
    }

    @Test
    void shouldRejectMissingAuthenticationContext() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        assertThrows(
                IllegalArgumentException.class,
                () -> service.resolve(
                        request,
                        null));
    }
}