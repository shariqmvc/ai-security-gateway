package com.ai.gateway.service;


import com.ai.gateway.enums.Provider;
import com.ai.gateway.routing.policy.RoutingPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoutingPolicyTest {

    @Test
    void shouldAllowAnyProviderWhenAllowedProvidersAreEmpty() {

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(),
                        List.of(),
                        Provider.GEMINI,
                        "gemini-test");

        assertTrue(
                policy.allowsProvider(Provider.GEMINI));

        assertTrue(
                policy.allowsProvider(Provider.OPENAI));
    }

    @Test
    void shouldRejectProviderNotIncludedInPolicy() {

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(Provider.GEMINI),
                        List.of(),
                        Provider.GEMINI,
                        "gemini-test");

        assertTrue(
                policy.allowsProvider(Provider.GEMINI));

        assertFalse(
                policy.allowsProvider(Provider.OPENAI));
    }

    @Test
    void shouldAllowAnyModelWhenAllowedModelsAreEmpty() {

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(),
                        List.of(),
                        Provider.GEMINI,
                        "gemini-test");

        assertTrue(
                policy.allowsModel("gemini-test"));

        assertTrue(
                policy.allowsModel("another-model"));
    }

    @Test
    void shouldRejectModelNotIncludedInPolicy() {

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(),
                        List.of("gemini-test"),
                        Provider.GEMINI,
                        "gemini-test");

        assertTrue(
                policy.allowsModel("gemini-test"));

        assertFalse(
                policy.allowsModel("other-model"));
    }

    @Test
    void shouldExposePreferredProviderAndModel() {

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(Provider.GEMINI),
                        List.of("gemini-test"),
                        Provider.GEMINI,
                        "gemini-test");

        assertTrue(policy.hasPreferredProvider());
        assertTrue(policy.hasPreferredModel());

        assertEquals(
                Provider.GEMINI,
                policy.preferredProvider());

        assertEquals(
                "gemini-test",
                policy.preferredModel());
    }

    @Test
    void shouldNormalizeNullCollections() {

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        null,
                        null,
                        null,
                        null);

        assertNotNull(policy.allowedProviders());
        assertNotNull(policy.allowedModels());

        assertTrue(policy.allowedProviders().isEmpty());
        assertTrue(policy.allowedModels().isEmpty());

        assertFalse(policy.hasPreferredProvider());
        assertFalse(policy.hasPreferredModel());
    }
}