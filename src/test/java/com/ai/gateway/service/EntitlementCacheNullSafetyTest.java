package com.ai.gateway.service;

import com.ai.gateway.entitlement.cache.EntitlementCache;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;

class EntitlementCacheNullSafetyTest {

    @Test
    void nullTenantIdMustNeverReachConcurrentHashMap() {
        EntitlementCache cache = new EntitlementCache();

        assertNull(cache.get(null));
        assertDoesNotThrow(() -> cache.put(null, null));
        assertDoesNotThrow(() -> cache.evict(null));
    }
}
