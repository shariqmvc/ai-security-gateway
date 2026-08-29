package com.ai.gateway.service;

import com.ai.gateway.core.model.Provider;
import com.ai.gateway.core.routing.RoutingDecision;
import com.ai.gateway.core.routing.RoutingStrategy;
import com.ai.gateway.core.routing.analytics.RoutingAnalytics;
import com.ai.gateway.core.routing.analytics.RoutingAnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RoutingAnalyticsServiceTest {

    private RoutingAnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new RoutingAnalyticsService();
    }

    @Test
    void shouldStartWithZeroAnalytics() {

        RoutingAnalytics analytics =
                service.getAnalytics();

        assertEquals(0, analytics.totalDecisions());
        assertTrue(analytics.decisionsByStrategy().isEmpty());
        assertTrue(analytics.decisionsByProvider().isEmpty());
        assertTrue(analytics.decisionsByProviderModel().isEmpty());

        assertEquals(0, analytics.failoverAttempts());
        assertEquals(0, analytics.failoverSuccesses());
        assertEquals(0, analytics.failoverFailures());
    }

    @Test
    void shouldRecordTenantDefaultDecision() {

        service.recordDecision(
                decision(
                        Provider.GEMINI,
                        "gemini-test",
                        RoutingStrategy.TENANT_DEFAULT));

        RoutingAnalytics analytics =
                service.getAnalytics();

        assertEquals(1, analytics.totalDecisions());

        assertEquals(
                1L,
                analytics.decisionsByStrategy()
                        .get("TENANT_DEFAULT"));

        assertEquals(
                1L,
                analytics.decisionsByProvider()
                        .get("GEMINI"));

        assertEquals(
                1L,
                analytics.decisionsByProviderModel()
                        .get("GEMINI:gemini-test"));
    }

    @Test
    void shouldRecordExplicitProviderDecision() {

        service.recordDecision(
                decision(
                        Provider.GEMINI,
                        "gemini-test",
                        RoutingStrategy.EXPLICIT_PROVIDER));

        RoutingAnalytics analytics =
                service.getAnalytics();

        assertEquals(1, analytics.totalDecisions());

        assertEquals(
                1L,
                analytics.decisionsByStrategy()
                        .get("EXPLICIT_PROVIDER"));
    }

    @Test
    void shouldRecordExplicitModelDecision() {

        service.recordDecision(
                decision(
                        Provider.GEMINI,
                        "gemini-test",
                        RoutingStrategy.EXPLICIT_MODEL));

        RoutingAnalytics analytics =
                service.getAnalytics();

        assertEquals(
                1L,
                analytics.decisionsByStrategy()
                        .get("EXPLICIT_MODEL"));
    }

    @Test
    void shouldAggregateRepeatedRoutingDecisions() {

        service.recordDecision(
                decision(
                        Provider.GEMINI,
                        "gemini-test",
                        RoutingStrategy.TENANT_DEFAULT));

        service.recordDecision(
                decision(
                        Provider.GEMINI,
                        "gemini-test",
                        RoutingStrategy.EXPLICIT_PROVIDER));

        service.recordDecision(
                decision(
                        Provider.OPENAI,
                        "gpt-test",
                        RoutingStrategy.EXPLICIT_MODEL));

        RoutingAnalytics analytics =
                service.getAnalytics();

        assertEquals(3, analytics.totalDecisions());

        assertEquals(
                2L,
                analytics.decisionsByProvider()
                        .get("GEMINI"));

        assertEquals(
                1L,
                analytics.decisionsByProvider()
                        .get("OPENAI"));

        assertEquals(
                2L,
                analytics.decisionsByProviderModel()
                        .get("GEMINI:gemini-test"));

        assertEquals(
                1L,
                analytics.decisionsByProviderModel()
                        .get("OPENAI:gpt-test"));
    }

    @Test
    void shouldTrackDifferentModelsForSameProvider() {

        service.recordDecision(
                decision(
                        Provider.GEMINI,
                        "gemini-test",
                        RoutingStrategy.EXPLICIT_MODEL));

        service.recordDecision(
                decision(
                        Provider.GEMINI,
                        "gemini-pro",
                        RoutingStrategy.EXPLICIT_MODEL));

        RoutingAnalytics analytics =
                service.getAnalytics();

        assertEquals(
                2L,
                analytics.decisionsByProvider()
                        .get("GEMINI"));

        assertEquals(
                1L,
                analytics.decisionsByProviderModel()
                        .get("GEMINI:gemini-test"));

        assertEquals(
                1L,
                analytics.decisionsByProviderModel()
                        .get("GEMINI:gemini-pro"));
    }

    @Test
    void shouldIgnoreNullRoutingDecision() {

        service.recordDecision(null);

        RoutingAnalytics analytics =
                service.getAnalytics();

        assertEquals(0, analytics.totalDecisions());
        assertTrue(analytics.decisionsByStrategy().isEmpty());
        assertTrue(analytics.decisionsByProvider().isEmpty());
        assertTrue(analytics.decisionsByProviderModel().isEmpty());
    }

    @Test
    void shouldHandleMissingProviderAndModelSafely() {

        RoutingDecision decision =
                new RoutingDecision(
                        null,
                        null,
                        RoutingStrategy.TENANT_DEFAULT);

        service.recordDecision(decision);

        RoutingAnalytics analytics =
                service.getAnalytics();

        assertEquals(1, analytics.totalDecisions());

        assertEquals(
                1L,
                analytics.decisionsByStrategy()
                        .get("TENANT_DEFAULT"));

        assertTrue(
                analytics.decisionsByProvider().isEmpty());

        assertTrue(
                analytics.decisionsByProviderModel().isEmpty());
    }

    @Test
    void shouldRecordFailoverAttempt() {

        service.recordFailoverAttempt();

        RoutingAnalytics analytics =
                service.getAnalytics();

        assertEquals(
                1,
                analytics.failoverAttempts());

        assertEquals(
                0,
                analytics.failoverSuccesses());

        assertEquals(
                0,
                analytics.failoverFailures());
    }

    @Test
    void shouldRecordFailoverSuccess() {

        service.recordFailoverAttempt();
        service.recordFailoverSuccess();

        RoutingAnalytics analytics =
                service.getAnalytics();

        assertEquals(
                1,
                analytics.failoverAttempts());

        assertEquals(
                1,
                analytics.failoverSuccesses());
    }

    @Test
    void shouldRecordFailoverFailure() {

        service.recordFailoverAttempt();
        service.recordFailoverFailure();

        RoutingAnalytics analytics =
                service.getAnalytics();

        assertEquals(
                1,
                analytics.failoverAttempts());

        assertEquals(
                1,
                analytics.failoverFailures());
    }

    @Test
    void shouldReturnImmutableAnalyticsSnapshot() {

        service.recordDecision(
                decision(
                        Provider.GEMINI,
                        "gemini-test",
                        RoutingStrategy.EXPLICIT_PROVIDER));

        RoutingAnalytics analytics =
                service.getAnalytics();

        assertThrows(
                UnsupportedOperationException.class,
                () -> analytics.decisionsByProvider()
                        .put("OPENAI", 10L));
    }

    private RoutingDecision decision(
            Provider provider,
            String model,
            RoutingStrategy strategy) {

        return new RoutingDecision(
                provider,
                model,
                strategy);
    }
}

