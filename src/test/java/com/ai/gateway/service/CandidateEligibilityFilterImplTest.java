package com.ai.gateway.service;


import com.ai.gateway.enums.Provider;
import com.ai.gateway.routing.engine.CandidateEligibilityFilter;
import com.ai.gateway.routing.engine.CandidateEligibilityFilterImpl;
import com.ai.gateway.routing.engine.RoutingCandidate;
import com.ai.gateway.routing.policy.RoutingPolicy;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CandidateEligibilityFilterImplTest {

    private final CandidateEligibilityFilter filter =
            new CandidateEligibilityFilterImpl();

    @Test
    void shouldReturnEligibleCandidates() {

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(
                                Provider.OPENAI,
                                Provider.GEMINI),
                        List.of(
                                "gpt-test",
                                "gemini-test"),
                        null,
                        null);

        List<RoutingCandidate> candidates =
                List.of(
                        new RoutingCandidate(
                                Provider.OPENAI,
                                "gpt-test"),
                        new RoutingCandidate(
                                Provider.GEMINI,
                                "gemini-test"));

        List<RoutingCandidate> result =
                filter.filter(
                        candidates,
                        policy);

        assertEquals(
                candidates,
                result);
    }

    @Test
    void shouldRemoveProviderNotAllowedByPolicy() {

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(Provider.GEMINI),
                        List.of(),
                        null,
                        null);

        List<RoutingCandidate> candidates =
                List.of(
                        new RoutingCandidate(
                                Provider.OPENAI,
                                "gpt-test"),
                        new RoutingCandidate(
                                Provider.GEMINI,
                                "gemini-test"));

        List<RoutingCandidate> result =
                filter.filter(
                        candidates,
                        policy);

        assertEquals(
                List.of(
                        new RoutingCandidate(
                                Provider.GEMINI,
                                "gemini-test")),
                result);
    }

    @Test
    void shouldRemoveModelNotAllowedByPolicy() {

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(Provider.GEMINI),
                        List.of("gemini-test"),
                        null,
                        null);

        List<RoutingCandidate> candidates =
                List.of(
                        new RoutingCandidate(
                                Provider.GEMINI,
                                "gemini-test"),
                        new RoutingCandidate(
                                Provider.GEMINI,
                                "gemini-pro"));

        List<RoutingCandidate> result =
                filter.filter(
                        candidates,
                        policy);

        assertEquals(
                List.of(
                        new RoutingCandidate(
                                Provider.GEMINI,
                                "gemini-test")),
                result);
    }

    @Test
    void shouldAllowAllProvidersWhenProviderListIsEmpty() {

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(),
                        List.of(),
                        null,
                        null);

        List<RoutingCandidate> candidates =
                List.of(
                        new RoutingCandidate(
                                Provider.OPENAI,
                                "gpt-test"),
                        new RoutingCandidate(
                                Provider.GEMINI,
                                "gemini-test"));

        List<RoutingCandidate> result =
                filter.filter(
                        candidates,
                        policy);

        assertEquals(
                candidates,
                result);
    }

    @Test
    void shouldAllowAllModelsWhenModelListIsEmpty() {

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(Provider.GEMINI),
                        List.of(),
                        null,
                        null);

        List<RoutingCandidate> candidates =
                List.of(
                        new RoutingCandidate(
                                Provider.GEMINI,
                                "gemini-test"),
                        new RoutingCandidate(
                                Provider.GEMINI,
                                "gemini-pro"));

        List<RoutingCandidate> result =
                filter.filter(
                        candidates,
                        policy);

        assertEquals(
                candidates,
                result);
    }

    @Test
    void shouldReturnEmptyWhenPolicyIsNull() {

        List<RoutingCandidate> candidates =
                List.of(
                        new RoutingCandidate(
                                Provider.GEMINI,
                                "gemini-test"));

        assertTrue(
                filter.filter(
                                candidates,
                                null)
                        .isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenPolicyIsDisabled() {

        RoutingPolicy policy =
                new RoutingPolicy(
                        false,
                        List.of(Provider.GEMINI),
                        List.of("gemini-test"),
                        null,
                        null);

        List<RoutingCandidate> candidates =
                List.of(
                        new RoutingCandidate(
                                Provider.GEMINI,
                                "gemini-test"));

        assertTrue(
                filter.filter(
                                candidates,
                                policy)
                        .isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenCandidatesAreNull() {

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(),
                        List.of(),
                        null,
                        null);

        assertTrue(
                filter.filter(
                                null,
                                policy)
                        .isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenCandidatesAreEmpty() {

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(),
                        List.of(),
                        null,
                        null);

        assertTrue(
                filter.filter(
                                List.of(),
                                policy)
                        .isEmpty());
    }

    @Test
    void shouldIgnoreNullCandidates() {

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(Provider.GEMINI),
                        List.of("gemini-test"),
                        null,
                        null);

        List<RoutingCandidate> candidates =
                Arrays.asList(
                        null,
                        new RoutingCandidate(
                                Provider.GEMINI,
                                "gemini-test"),
                        null);

        List<RoutingCandidate> result =
                filter.filter(
                        candidates,
                        policy);

        assertEquals(
                List.of(
                        new RoutingCandidate(
                                Provider.GEMINI,
                                "gemini-test")),
                result);
    }

    @Test
    void shouldRemoveDuplicateCandidates() {

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(Provider.GEMINI),
                        List.of("gemini-test"),
                        null,
                        null);

        List<RoutingCandidate> candidates =
                List.of(
                        new RoutingCandidate(
                                Provider.GEMINI,
                                "gemini-test"),
                        new RoutingCandidate(
                                Provider.GEMINI,
                                "gemini-test"));

        List<RoutingCandidate> result =
                filter.filter(
                        candidates,
                        policy);

        assertEquals(
                1,
                result.size());
    }

    @Test
    void shouldPreserveCandidateOrder() {

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(
                                Provider.OPENAI,
                                Provider.GEMINI),
                        List.of(
                                "gpt-test",
                                "gemini-test"),
                        null,
                        null);

        List<RoutingCandidate> candidates =
                List.of(
                        new RoutingCandidate(
                                Provider.GEMINI,
                                "gemini-test"),
                        new RoutingCandidate(
                                Provider.OPENAI,
                                "gpt-test"));

        List<RoutingCandidate> result =
                filter.filter(
                        candidates,
                        policy);

        assertEquals(
                candidates,
                result);
    }
}