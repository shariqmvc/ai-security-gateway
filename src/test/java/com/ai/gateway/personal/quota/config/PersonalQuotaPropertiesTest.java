package com.ai.gateway.personal.quota.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonalQuotaPropertiesTest {

    @Test
    void defaultsKeepPolicyExplicitlyUnconfiguredUntilLimitsAreFrozen() {
        PersonalQuotaProperties properties = new PersonalQuotaProperties();

        assertTrue(properties.isEnabled());
        assertEquals(0L, properties.getRequestsPerMinute());
        assertEquals(0L, properties.getRequestsPerDay());
        assertEquals(0L, properties.getMonthlyTokenQuota());
        assertEquals(0L, properties.getMaxInputTokens());
        assertEquals(0L, properties.getMaxOutputTokens());
        assertEquals(0L, properties.getMaxConcurrentRequests());
    }
}
