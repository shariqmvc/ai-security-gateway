package com.ai.gateway.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ProviderRequestBudgetTest {

    @AfterEach
    void cleanup() {
        ProviderRequestBudget.clear();
    }

    @Test
    void shouldExposeRemainingBudget() {
        ProviderRequestBudget.start(Duration.ofMillis(200));

        assertTrue(ProviderRequestBudget.isActive());
        assertTrue(ProviderRequestBudget.remainingMillis() > 0);
        assertTrue(ProviderRequestBudget.remainingMillis() <= 200);
    }

    @Test
    void shouldExpireBudget() throws InterruptedException {
        ProviderRequestBudget.start(Duration.ofMillis(25));

        Thread.sleep(75);

        assertEquals(0, ProviderRequestBudget.remainingMillis());
    }

    @Test
    void shouldClearBudget() {
        ProviderRequestBudget.start(Duration.ofSeconds(1));
        assertTrue(ProviderRequestBudget.isActive());

        ProviderRequestBudget.clear();

        assertFalse(ProviderRequestBudget.isActive());
        assertEquals(Long.MAX_VALUE, ProviderRequestBudget.remainingMillis());
    }
}
